package com.krtky.financetracker.data.classify

import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.LearningRow
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.TransactionType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Category guesses without AI: first from the user's own past choices for the same payee,
 * then from built-in merchant rules. [Guess.confident] guesses are safe to apply directly;
 * weaker ones are passed to the AI as hints.
 */
@Singleton
class LocalClassifier @Inject constructor(
    db: AppDatabase,
) {
    private val learningDao = db.learningDao()
    private val mutex = Mutex()
    private var index: Map<String, Map<Long, Int>> = emptyMap()
    private var guideById: Map<Long, List<String>> = emptyMap()
    private var builtAt = 0L

    data class Guess(
        val categoryId: Long,
        val categoryName: String,
        val confidence: Double,
        /** Short, user-facing: "you filed 16 like this as Investment". */
        val reason: String,
    ) {
        val confident: Boolean get() = confidence >= CONFIDENT
    }

    /**
     * "How this user categorises": per category, the payees they filed there most often.
     * Sent to the AI so it follows the user's habits (e.g. a brother under Family).
     */
    suspend fun categoryGuide(categories: List<Category>, perCategory: Int = 6): Map<String, List<String>> {
        history()
        val byId = guideById
        return categories
            .mapNotNull { c -> byId[c.id]?.take(perCategory)?.takeIf { it.isNotEmpty() }?.let { c.name to it } }
            .toMap()
            .toSortedMap()
    }

    /** Call after the user classifies something so the next guess sees it. */
    fun invalidate() {
        builtAt = 0L
    }

    suspend fun guess(
        counterparty: String?,
        text: String?,
        type: TransactionType,
        categories: List<Category>,
    ): Guess? {
        if (categories.isEmpty()) return null
        val idx = history()
        fromHistory(PayeeKeys.of(counterparty, text), idx, categories)?.let { return it }
        return fromRules(listOfNotNull(counterparty, text).joinToString(" "), type, categories)
    }

    private suspend fun history(): Map<String, Map<Long, Int>> = mutex.withLock {
        val now = System.currentTimeMillis()
        if (now - builtAt > CACHE_MS) {
            val rows = learningDao.recentClassified(HISTORY_LIMIT)
            index = buildIndex(rows)
            guideById = buildGuide(rows)
            builtAt = now
        }
        index
    }

    companion object {
        const val CONFIDENT = 0.7
        private const val CACHE_MS = 60_000L
        private const val HISTORY_LIMIT = 3_000

        fun buildIndex(rows: List<LearningRow>): Map<String, Map<Long, Int>> {
            val out = HashMap<String, HashMap<Long, Int>>()
            for (r in rows) {
                for (k in PayeeKeys.of(r.counterparty, r.rawDescription)) {
                    val votes = out.getOrPut(k) { HashMap() }
                    votes[r.categoryId] = (votes[r.categoryId] ?: 0) + 1
                }
            }
            return out
        }

        /** categoryId → payee display names, most frequent first. */
        fun buildGuide(rows: List<LearningRow>): Map<Long, List<String>> =
            rows.mapNotNull { r -> PayeeKeys.displayName(r.counterparty, r.rawDescription)?.let { r.categoryId to it } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, names) ->
                    names.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(12)
                }

        fun fromHistory(keys: Set<String>, index: Map<String, Map<Long, Int>>, categories: List<Category>): Guess? {
            if (keys.isEmpty()) return null
            val votes = HashMap<Long, Int>()
            // Strongest key wins: a VPA / UPI-name match is more specific than a fuzzy name.
            val best = keys.mapNotNull { k -> index[k]?.let { k to it } }
                .maxByOrNull { (_, v) -> v.values.sum() } ?: return null
            best.second.forEach { (cat, n) -> votes[cat] = (votes[cat] ?: 0) + n }
            val total = votes.values.sum()
            val (catId, n) = votes.maxByOrNull { it.value } ?: return null
            val category = categories.firstOrNull { it.id == catId } ?: return null
            val share = n.toDouble() / total
            val confidence = when {
                n >= 2 && share >= 0.75 -> 0.9
                n >= 2 && share >= 0.6 -> 0.72
                else -> 0.55 * share + 0.05
            }
            val reason = if (n == 1) "you filed one like this as ${category.name}"
            else "you filed $n like this as ${category.name}"
            return Guess(catId, category.name, confidence, reason)
        }

        fun fromRules(text: String, type: TransactionType, categories: List<Category>): Guess? {
            val t = " " + text.lowercase().replace(Regex("[^a-z0-9.@ ]"), " ") + " "
            for (rule in Rules.ALL) {
                if (rule.type != null && rule.type != type) continue
                if (rule.words.none { t.contains(it) }) continue
                val category = rule.categories.firstNotNullOfOrNull { name ->
                    categories.firstOrNull { it.name.equals(name, true) }
                } ?: continue
                return Guess(category.id, category.name, 0.75, "looks like ${rule.label}")
            }
            return null
        }
    }
}

/** Stable payee identifiers from a counterparty name and a bank narration / SMS body. */
object PayeeKeys {
    private val UPI_NARRATION = Regex("""UPI/(?:DR|CR)/\d+/([^/]+)/[^/]+/([^/]*/?[^/]*)""", RegexOption.IGNORE_CASE)
    private val KOTAK_NARRATION = Regex("""^UPI/([^/]+)/[A-Z]{4}/\d+""", RegexOption.IGNORE_CASE)
    private val VPA = Regex("""([a-z0-9._-]{3,})@[a-z]{2,}""", RegexOption.IGNORE_CASE)
    private val NEFT = Regex("""NEFT\*\w+\*\w+\*(\w+)""", RegexOption.IGNORE_CASE)
    private val VPA_TAIL = Regex("""(paid|noti|pay|upi|merc|request|veri)\w*$""")
    private val COMPANY_SUFFIX = Regex("""(private|pvt|limited|ltd|llp|india|technologies|solutions)""")

    fun of(counterparty: String?, text: String?): Set<String> {
        val out = linkedSetOf<String>()
        val compact = (text ?: "").replace(Regex("\\s+"), "")
        UPI_NARRATION.find(compact)?.let { m ->
            name(m.groupValues[1])?.let { out += "upi:$it" }
            val vpa = m.groupValues[2].replace("/", "").lowercase().replace(VPA_TAIL, "")
            if (vpa.length >= 3) out += "vpa:" + vpa.substringBefore('@').take(12)
        }
        KOTAK_NARRATION.find(compact)?.let { m -> name(m.groupValues[1])?.let { out += "upi:$it" } }
        NEFT.find(compact)?.let { m -> name(m.groupValues[1])?.let { out += "neft:$it" } }
        (text?.let { VPA.find(it) } ?: counterparty?.let { VPA.find(it) })?.let { m ->
            out += "vpa:" + m.groupValues[1].lowercase().take(12)
        }
        name(counterparty)?.let { out += "cp:$it" }
        return out
    }

    /** Readable payee for prompts: counterparty, else the UPI name in a narration; title case, no noise. */
    fun displayName(counterparty: String?, text: String?): String? {
        val raw = counterparty?.takeIf { it.isNotBlank() }
            ?: UPI_NARRATION.find((text ?: "").replace(Regex("\\s+"), ""))?.groupValues?.get(1)
            ?: return null
        val cleaned = raw
            .replace(Regex("""(?i)\s+on\s+\d.*$"""), "")
            .replace(Regex("""(?i)\b(pvt|private|ltd|limited|llp)\b\.?"""), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', ',', '-')
        if (cleaned.length < 3 || cleaned.count(Char::isDigit) > 4) return null
        return cleaned.split(' ').joinToString(" ") { w ->
            if ('@' in w || '.' in w) w.lowercase() else w.lowercase().replaceFirstChar(Char::uppercase)
        }.take(28)
    }

    /** Lower-case alphanumerics without company suffixes; SBI truncates names to 8 chars. */
    private fun name(raw: String?): String? {
        val n = raw?.lowercase()?.replace(Regex("[^a-z0-9]"), "")?.replace(COMPANY_SUFFIX, "") ?: return null
        return n.take(8).takeIf { it.length >= 3 && !it.all(Char::isDigit) }
    }
}

private object Rules {
    class Rule(
        val label: String,
        val categories: List<String>,
        val words: List<String>,
        val type: TransactionType? = TransactionType.DEBIT,
    )

    val ALL = listOf(
        Rule("a card bill / self transfer", listOf("Transfer"),
            listOf("cred club", "cred.club", "credit card bill", "bbps repayment", "sweep trf", "fd premat", "self transfer", "own account"),
            type = null),
        Rule("an investment", listOf("Investment", "Investments"),
            listOf("zerodha", "groww", "indian clearing", "indian c ", "bse clearing", "nsdl", "cdsl", "kuvera", "mutual fund", " sip ", "ppf", "smallcase"),
            type = null),
        Rule("a dividend", listOf("Dividend", "Dividends"), listOf("dividend", "fnldiv", "intdiv", " div "), TransactionType.CREDIT),
        Rule("interest", listOf("Interest"), listOf("interest", "int.pd", " int cr"), TransactionType.CREDIT),
        Rule("salary", listOf("Salary", "Income"), listOf("salary", " sal "), TransactionType.CREDIT),
        Rule("groceries", listOf("Groceries", "Grocery"),
            listOf("blinkit", "blink commerce", "zepto", "instamart", "bigbasket", "dmart", "jiomart", "grofers", "modernbasket")),
        Rule("food", listOf("Food", "Food & Dining", "Dining"),
            listOf("swiggy", "zomato", "eternal", "mcdonald", "domino", "kfc", "pizza", "restaurant", "cafe", "bistro", "haldiram", "burger", "eatclub", "starbucks", "chaayos", "bakery", "sweets")),
        Rule("travel", listOf("Travel", "Transport", "Commute"),
            listOf("uber", " ola ", "rapido", "irctc", "metro", "nmrcl", "dmrc", "indigo", "makemytrip", "redbus", "fastag", "railway")),
        Rule("fuel", listOf("Fuel"), listOf("petrol", "petroleum", "hpcl", "iocl", "bpcl", "indian oil", "fuel", " cng ")),
        Rule("a subscription", listOf("Subscriptions", "Subscription"),
            listOf("netflix", "spotify", "youtube", "openai", "chatgpt", "linkedin", "hotstar", "prime video", "grok", "x.ai", "apple.com", "google play", "jio cinema")),
        Rule("a utility bill", listOf("Utilities", "Bills"),
            listOf("electricity", "bescom", "airtel", " jio ", "vodafone", "broadband", "recharge", "gas bill", "water bill")),
        Rule("health", listOf("Health", "Medical"),
            listOf("pharmacy", "apollo", "1mg", "netmeds", "pharmeasy", "lalpath", "hospital", "clinic", "cult.fit", "cultfit")),
        Rule("electronics", listOf("Electronics"), listOf("vijay sales", "vijaysales", "croma", "reliance digital")),
        Rule("fees / charges", listOf("Fees & Charges", "Fees", "Charges"),
            listOf("charges", "penalty", "bounce", "annual fee", "late fee", "gst on")),
    )
}
