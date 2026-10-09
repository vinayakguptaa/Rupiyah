package com.krtky.financetracker.data.llm

import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.data.sms.SmsRedactor
import com.krtky.financetracker.data.sms.TransactionParser
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.Transaction
import kotlinx.coroutines.delay
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
     * Classify and persist. Pass 1 is the cheap batch call; pass 2 runs the full SMS parser
     * on up to [DEEP_LIMIT] rows that are still unresolved but have raw SMS / narration text.
     */
    suspend fun classifyAndSave(txns: List<Transaction>, categories: List<Category>): Outcome<String> {
        val texts = txns.mapNotNull { t -> describe(t)?.let { t.id to it } }.toMap()
        val batch = classify(texts, categories)
        batch.matches.forEach { (id, cat) -> transactionRepository.classify(id, cat.id, null, null) }
        if (batch.error != null) return batch

        val matches = batch.matches.toMutableMap()
        val deep = txns
            .filter { it.id !in matches && !it.rawDescription.isNullOrBlank() }
            .take(DEEP_LIMIT)
        for (t in deep) {
            when (val r = parser.suggestFor(t)) {
                is LlmResult.Ok -> {
                    val cat = categories.firstOrNull { it.id == r.value.categoryId } ?: continue
                    transactionRepository.applyAiSuggestion(t.id, cat.id, r.value.counterparty)
                    matches[t.id] = cat
                }
                is LlmResult.Failed -> {
                    if (r.error is LlmError.NoInput) continue
                    return Outcome(matches, r.error)
                }
            }
        }
        return Outcome(matches)
    }

    /** Full-parser suggestion for one transaction (not saved). */
    suspend fun suggest(txn: Transaction): LlmResult<TransactionParser.AiSuggestion> {
        if (!llmClient.isConfigured()) return LlmResult.Failed(LlmError.NotConfigured)
        return parser.suggestFor(txn)
    }

    suspend fun applySuggestion(txnId: String, suggestion: TransactionParser.AiSuggestion) =
        transactionRepository.applyAiSuggestion(txnId, suggestion.categoryId, suggestion.counterparty)

    private suspend fun classifyBatch(batch: List<String>, categories: List<Category>): LlmResult<Map<Int, Category>> {
        val user = buildString {
            appendLine("ALLOWED CATEGORIES (one per line):")
            categories.forEach { appendLine(it.name) }
            appendLine()
            appendLine("TRANSACTIONS (id: text):")
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

        private val SYSTEM = """You are an expert personal finance transaction classifier for India.
You receive ALLOWED CATEGORIES and a numbered list of transactions (merchant / counterparty and bank narration such as UPI, POS, card, NEFT, IMPS, salary).
For each transaction id:
1. Identify the merchant, counterparty or purpose (e.g. 'UPI/DR/Swiggy/ICICI' -> Swiggy -> food).
2. Choose EXACTLY ONE category name copied verbatim from ALLOWED CATEGORIES.
3. If unsure, or it is an ambiguous person-to-person transfer, use null.
Respond with a JSON object: {"results": [{"id": 1, "category": "<allowed category or null>"}]} with one entry per id."""

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
