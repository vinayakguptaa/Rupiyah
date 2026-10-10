package com.krtky.financetracker.data.llm

import com.krtky.financetracker.data.classify.LocalClassifier
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.data.sms.SmsRedactor
import com.krtky.financetracker.data.sms.TransactionParser
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.Transaction
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Batch category classification shared by Auto-Classify and statement import.
 *
 * Items are sent with numeric ids and mapped back by id, so the model never has to
 * echo free text exactly. Only short, redacted text is sent (merchant + one line of narration).
 */
@Singleton
class TransactionClassifier @Inject constructor(
    private val llmClient: LlmClient,
    private val transactionRepository: TransactionRepository,
    private val parser: TransactionParser,
    private val localClassifier: LocalClassifier,
) {
    data class Outcome<K>(
        val matches: Map<K, Category>,
        /** First failure seen; [matches] may still hold results from earlier batches. */
        val error: LlmError? = null,
    )

    fun isConfigured(): Boolean = llmClient.isConfigured()

    /**
     * Classify [texts] (keyed by caller ids) into [categories].
     * Identical texts are sent once.
     */
    suspend fun <K> classify(texts: Map<K, String>, categories: List<Category>): Outcome<K> {
        if (!llmClient.isConfigured()) return Outcome(emptyMap(), LlmError.NotConfigured)
        if (texts.isEmpty() || categories.isEmpty()) return Outcome(emptyMap())

        val distinct = texts.values.distinct()
        val byText = mutableMapOf<String, Category>()
        var error: LlmError? = null
        for (batch in distinct.chunked(BATCH_SIZE)) {
            when (val r = classifyBatch(batch, categories)) {
                is LlmResult.Ok -> r.value.forEach { (i, cat) -> byText[batch[i]] = cat }
                is LlmResult.Failed -> {
                    error = r.error
                    break
                }
            }
        }
        val matches = texts.mapNotNull { (k, t) -> byText[t]?.let { k to it } }.toMap()
        return Outcome(matches, error)
    }

    /**
     * Classify and persist, cheapest first:
     * pass 0 applies confident guesses from the user's own history / merchant rules (no AI);
     * pass 1 is one batch AI call, with weaker local guesses attached as hints;
     * pass 2 runs the full SMS parser on up to [DEEP_LIMIT] rows still unresolved.
     */
    suspend fun classifyAndSave(txns: List<Transaction>, categories: List<Category>): Outcome<String> {
        val start = System.currentTimeMillis()
        val outcome = classifyAndSaveInner(txns, categories, start)
        Log.i(
            TAG,
            "auto-classify: ${outcome.matches.size}/${txns.size} classified in ${System.currentTimeMillis() - start}ms" +
                (outcome.error?.let { " — stopped: $it" } ?: ""),
        )
        return outcome
    }

    private suspend fun classifyAndSaveInner(
        txns: List<Transaction>,
        categories: List<Category>,
        start: Long,
    ): Outcome<String> {
        val matches = java.util.concurrent.ConcurrentHashMap<String, Category>()
        val hints = mutableMapOf<String, String>()
        for (t in txns) {
            val g = localClassifier.guess(t.counterparty, t.rawDescription ?: t.note, t.type, categories) ?: continue
            if (g.confident) {
                val cat = categories.firstOrNull { it.id == g.categoryId } ?: continue
                transactionRepository.classify(t.id, cat.id, null, null)
                matches[t.id] = cat
            } else {
                hints[t.id] = g.categoryName
            }
        }
        Log.i(TAG, "auto-classify pass 0 (local): ${matches.size} in ${System.currentTimeMillis() - start}ms")
        val rest = txns.filter { !matches.containsKey(it.id) }
        if (rest.isEmpty()) return Outcome(matches.toMap())
        if (!llmClient.isConfigured()) return Outcome(matches.toMap(), LlmError.NotConfigured)

        val texts = rest.mapNotNull { t ->
            describe(t)?.let { d -> t.id to (hints[t.id]?.let { "$d [local guess: $it]" } ?: d) }
        }.toMap()
        val batch = classify(texts, categories)
        batch.matches.forEach { (id, cat) -> transactionRepository.classify(id, cat.id, null, null) }
        matches.putAll(batch.matches)
        Log.i(TAG, "auto-classify pass 1 (batch AI): ${batch.matches.size} of ${texts.size}")
        if (batch.error != null) return Outcome(matches.toMap(), batch.error)

        // Pass 2: full parser per row, a few calls at a time.
        val deep = txns
            .filter { !matches.containsKey(it.id) && !it.rawDescription.isNullOrBlank() }
            .take(DEEP_LIMIT)
        val firstError = java.util.concurrent.atomic.AtomicReference<LlmError?>(null)
        val permits = Semaphore(DEEP_CONCURRENCY)
        coroutineScope {
            deep.map { t ->
                async {
                    permits.withPermit {
                        if (firstError.get()?.isTransient == false) return@withPermit
                        when (val r = parser.suggestFor(t)) {
                            is LlmResult.Ok -> {
                                val cat = categories.firstOrNull { it.id == r.value.categoryId } ?: return@withPermit
                                transactionRepository.applyAiSuggestion(t.id, cat.id, r.value.counterparty)
                                matches[t.id] = cat
                            }
                            is LlmResult.Failed -> if (r.error !is LlmError.NoInput) firstError.compareAndSet(null, r.error)
                        }
                    }
                }
            }.awaitAll()
        }
        Log.i(TAG, "auto-classify pass 2 (full parser): ${deep.size} tried")
        return Outcome(matches.toMap(), firstError.get())
    }

    /** Full-parser suggestion for one transaction (not saved). */
    suspend fun suggest(txn: Transaction): LlmResult<TransactionParser.AiSuggestion> {
        if (!llmClient.isConfigured()) return LlmResult.Failed(LlmError.NotConfigured)
        return parser.suggestFor(txn)
    }

    suspend fun applySuggestion(txnId: String, suggestion: TransactionParser.AiSuggestion) =
        transactionRepository.applyAiSuggestion(txnId, suggestion.categoryId, suggestion.counterparty)

    private suspend fun classifyBatch(batch: List<String>, categories: List<Category>): LlmResult<Map<Int, Category>> {
        val guide = LlmClient.guideLines(localClassifier.categoryGuide(categories, perCategory = 8), perCategory = 8)
        val user = buildString {
            appendLine("CATEGORIES: " + categories.joinToString(" | ") { it.name })
            if (guide.isNotEmpty()) {
                appendLine("HOW THIS USER CATEGORISES (payee examples per category):")
                guide.forEach { appendLine(it) }
            }
            appendLine()
            appendLine("TRANSACTIONS:")
            batch.forEachIndexed { i, d -> appendLine("${i + 1}: $d") }
        }
        var attempt = 0
        while (true) {
            when (val r = llmClient.completeJson(SYSTEM, user)) {
                is LlmResult.Ok -> return LlmResult.Ok(parseResults(r.value, batch.size, categories))
                is LlmResult.Failed -> {
                    if (!r.error.isTransient || attempt >= 1) return r
                    attempt++
                    delay(RETRY_DELAY_MS)
                }
            }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Returns 0-based batch index -> category. Unknown ids / names are ignored. */
        internal fun parseResults(raw: String, size: Int, categories: List<Category>): Map<Int, Category> {
            val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyMap()
            val array = (root["results"] ?: root["classifications"])
                ?.let { runCatching { it.jsonArray }.getOrNull() } ?: JsonArray(emptyList())
            val out = mutableMapOf<Int, Category>()
            for (elem in array) {
                val obj = runCatching { elem.jsonObject }.getOrNull() ?: continue
                val id = obj["id"]?.jsonPrimitive?.contentOrNull?.trim()?.toIntOrNull() ?: continue
                if (id !in 1..size) continue
                val name = obj["category"]?.jsonPrimitive?.contentOrNull?.trim() ?: continue
                val cat = categories.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: continue
                out[id - 1] = cat
            }
            return out
        }

        private const val BATCH_SIZE = 25
        private const val RETRY_DELAY_MS = 3_000L
        /** One LLM call per row in pass 2 — keep it small for free-tier rate limits. */
        private const val DEEP_LIMIT = 15
        private const val DEEP_CONCURRENCY = 3
        private const val TAG = "TransactionClassifier"

        private val SYSTEM = """You file the user's bank transactions into THEIR categories. Each item is "id: payee — bank narration", sometimes with [local guess: X] from the user's own history or a keyword rule.

For each id choose exactly one category, copied verbatim from CATEGORIES:
1. If the payee (or its UPI id) appears under HOW THIS USER CATEGORISES, use that category. The user's own habits beat general knowledge (e.g. a person they always file as Family stays Family).
2. Otherwise infer from the merchant or purpose: food delivery/restaurants, groceries, cabs/metro/fuel, subscriptions, brokers/mutual funds, salary, dividends, interest, card-bill payments (Transfer), bank charges, etc.
3. A [local guess] is usually right; override it only when the text clearly says otherwise.
4. Use null when you cannot tell, e.g. a person-to-person UPI with no history.

Common Indian patterns (map onto the closest name in CATEGORIES; skip any that has no match there):
- Credit-card bill payments: CRED / "cred.club", BBPS, "payment received against your card", "bill payment" -> Transfer (moving money to your own card, not a spend or subscription).
- ACH / NACH / "CEMTEX DEP" credits from a listed company, "FnlDiv", "IntDiv" -> Dividend.
- Interest credits, FD interest, SGB / RBI bond coupons -> Interest.
- Broker or fund payments (Zerodha, Groww, "Indian Clearing Corp", BSE / NSE clearing, mutual funds, PPF) -> Investment, in either direction.
- Food delivery (Swiggy, Zomato, Eternal), restaurants, cafes -> Food; quick-commerce groceries (Blinkit, Zepto, Instamart) -> Groceries unless the user's examples say otherwise.
- Depository (CDSL / NSDL) messages about units are not money movements.
SBI narrations look like "UPI/DR/<ref>/<first 8 letters of name>/<bank>/<upi id>"; "Indian C" + zerodha.ic is Indian Clearing Corp (Zerodha mutual funds).
Reply with JSON only: {"results":[{"id":1,"category":"<category or null>"}]} — one entry per id, in order."""

        /**
         * Short, single-line, redacted text for one transaction: counterparty first,
         * then the first line of narration / SMS body. Null when there is nothing to send.
         */
        fun describe(t: Transaction): String? = describe(t.counterparty, t.rawDescription ?: t.note)

        fun describe(counterparty: String?, narration: String?): String? {
            val party = counterparty?.trim()?.takeIf { it.isNotBlank() }
            val line = narration
                ?.let { SmsRedactor.redact(it) }
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.take(160)
                ?.takeIf { it.isNotBlank() }
            return when {
                party != null && line != null && !line.contains(party, ignoreCase = true) -> "$party — $line"
                line != null -> line
                else -> party
            }
        }
    }
}
