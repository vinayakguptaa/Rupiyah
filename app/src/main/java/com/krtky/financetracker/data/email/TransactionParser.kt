package com.krtky.financetracker.data.email

import com.krtky.financetracker.data.llm.ExtractedTransaction
import com.krtky.financetracker.data.llm.LlmClient
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.ClassificationStatus
import com.krtky.financetracker.domain.model.Money
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionSource
import com.krtky.financetracker.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Shared envelope for SMS (and legacy paste) body parsing. */
data class RawEmail(
    val messageId: String,
    val sender: String,
    val subject: String?,
    val body: String,
    val receivedAt: Long,
)

@Singleton
class TransactionParser @Inject constructor(
    private val llmClient: LlmClient,
    private val categoryRepository: CategoryRepository,
    private val userPreferences: UserPreferences,
    private val accountRepository: AccountRepository,
) {
    private val amountRegex = Regex(
        """(?:₹|Rs\.?|INR|Rs)\s*([0-9,]+(?:\.[0-9]{1,2})?)|([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:₹|Rs\.?|INR)""",
        RegexOption.IGNORE_CASE,
    )
    private val debited = Regex(
        """\b(debited|spent|paid|sent|payment of|withdrawn|deducted|purchase of|txn of|has been paid)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val credited = Regex("""\b(credited|received|added|deposit|refund|got)\b""", RegexOption.IGNORE_CASE)
    private val nonMovement = Regex(
        """\b(
            bill\s+(is\s+)?(generated|ready|due)|
            payment\s+due|
            due\s+(on|by|date|amount)|
            outstanding(\s+amount)?|
            amount\s+due|
            total\s+due|
            minimum\s+due|
            please\s+pay|
            pay\s+by|
            emi\s+due|
            autopay\s+(scheduled|reminder)|
            reminder|
            statement(\s+generated)?|
            request\s+to\s+pay|
            collect\s+payment|
            unpaid|
            overdue
        )\b""".trimIndent().replace("\n", ""),
        RegexOption.IGNORE_CASE,
    )
    private val movementConfirm = Regex(
        """\b(debited|credited|spent|withdrawn|deducted|transferred|successful(?:ly)?\s+paid|payment\s+successful|upi-?ref|txn\s*id|utr)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val refRegex = Regex(
        """(?:UPI|Ref|Reference|Txn|Transaction|UTR)[\s#:.\-]*([A-Za-z0-9]{6,})""",
        RegexOption.IGNORE_CASE,
    )
    private val bankHints = listOf(
        "HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "YES BANK", "IDFC", "PNB", "BOB", "CANARA",
        "FamPay", "PhonePe", "GPay", "Google Pay", "Paytm", "Amazon Pay", "CRED",
    )

    suspend fun parseSms(sender: String, body: String, receivedAt: Long): Transaction? =
        parseSource(
            RawEmail("sms-$receivedAt-${body.hashCode()}", sender, null, body, receivedAt),
            "Digital",
            TransactionSource.SMS,
        )

    private suspend fun parseSource(email: RawEmail, walletLabel: String, source: TransactionSource): Transaction? {
        val text = buildString {
            if (!email.subject.isNullOrBlank()) appendLine(email.subject)
            append(EmailRedactor.stripHtml(email.body))
        }
        val categories = categoryRepository.getAll()
        // Prefer live active accounts; fall back to prefs mirror.
        val banks = accountRepository.activeBankNames()
            .ifEmpty { userPreferences.parseBankList(userPreferences.bankAccounts.first()) }
        val defaultDigital = userPreferences.resolveDigitalPaymentMethod(null)
            .takeIf { name -> banks.any { it.equals(name, true) } || name.equals("Cash", true) }
            .orEmpty()
            .ifBlank { banks.firstOrNull().orEmpty() }
        val deterministic = parseDeterministic(
            text, email, walletLabel, source, categories, banks, defaultDigital,
        )

        val extracted = if (llmClient.isConfigured()) {
            val redacted = EmailRedactor.redact(text)
            runCatching {
                llmClient.extractTransaction(
                    redactedEmailBody = redacted,
                    subject = email.subject,
                    sender = email.sender,
                    categories = categories.map { it.name },
                    banks = banks,
                )
            }.getOrNull()
        } else null

        val fromLlm = extracted?.let {
            mapExtracted(it, email, walletLabel, source, categories, banks, defaultDigital)
        }
        val merged = merge(deterministic, fromLlm, categories, banks, defaultDigital)
        return merged?.let { attachAccount(it) }
    }

    /** Link paymentMethod label to accounts table (active first, then any name match). */
    private suspend fun attachAccount(txn: Transaction): Transaction {
        val label = txn.paymentMethod?.trim().orEmpty()
        if (label.isBlank() || label.equals("Digital", true) || label.equals("UPI", true)) {
            return txn
        }
        val account = accountRepository.getByName(label) ?: return txn
        return txn.copy(
            accountId = account.id,
            paymentMethod = account.name,
            isCash = account.kind.name == "CASH" || account.name.equals("Cash", true),
            accountName = account.name,
        )
    }

    private fun merge(
        base: Transaction?,
        llm: Transaction?,
        categories: List<com.krtky.financetracker.domain.model.Category>,
        banks: List<String>,
        defaultDigital: String,
    ): Transaction? {
        if (base == null) return llm
        if (llm == null) return base
        val party = llm.counterparty ?: llm.merchant ?: base.counterparty ?: base.merchant
        val rawMethod = llm.paymentMethod?.takeIf { it.isNotBlank() } ?: base.paymentMethod
        val method = resolveMethodLabel(rawMethod, banks, defaultDigital)
        val catId = llm.categoryId ?: base.categoryId
        val catName = categories.firstOrNull { it.id == catId }?.name
        val classified = catId != null
        return base.copy(
            type = llm.type,
            amountPaise = if (llm.amountPaise > 0) llm.amountPaise else base.amountPaise,
            merchant = party,
            counterparty = party,
            categoryId = catId,
            categoryName = catName ?: base.categoryName,
            paymentMethod = method,
            isCash = method.equals("Cash", true),
            externalRefId = llm.externalRefId ?: base.externalRefId,
            note = llm.note ?: base.note,
            classificationStatus = if (classified) ClassificationStatus.CLASSIFIED else ClassificationStatus.PENDING,
            contentHash = TransactionRepository.contentHash(
                llm.type,
                if (llm.amountPaise > 0) llm.amountPaise else base.amountPaise,
                base.occurredAt,
                party,
                llm.externalRefId ?: base.externalRefId,
                base.emailMessageId,
            ),
        )
    }

    private fun parseDeterministic(
        text: String,
        email: RawEmail,
        walletLabel: String,
        source: TransactionSource,
        categories: List<com.krtky.financetracker.domain.model.Category>,
        banks: List<String>,
        defaultDigital: String,
    ): Transaction? {
        val combined = buildString {
            if (!email.subject.isNullOrBlank()) appendLine(email.subject)
            append(text)
        }
        if (nonMovement.containsMatchIn(combined) && !movementConfirm.containsMatchIn(combined)) {
            return null
        }

        val amountMatch = amountRegex.find(text) ?: amountRegex.find(email.subject.orEmpty()) ?: return null
        val amountStr = amountMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return null
        val money = Money.fromRupeesString(amountStr) ?: return null
        if (money.paise <= 0) return null

        val type = when {
            credited.containsMatchIn(text) && !debited.containsMatchIn(text) -> TransactionType.CREDIT
            debited.containsMatchIn(text) || movementConfirm.containsMatchIn(text) -> TransactionType.DEBIT
            email.subject?.contains("received", true) == true -> TransactionType.CREDIT
            email.subject?.contains("paid", true) == true ||
                email.subject?.contains("sent", true) == true -> TransactionType.DEBIT
            else -> return null
        }

        val ref = refRegex.find(text)?.groupValues?.getOrNull(1)
        val counterparty = extractCounterparty(text, type)
        val bank = detectBank(combined, email.sender, banks)
            ?: walletLabel.takeIf {
                it.isNotBlank() &&
                    !it.equals("Email", true) &&
                    !it.equals("Digital", true)
            }
        val method = resolveMethodLabel(bank, banks, defaultDigital)
        val categoryId = if (type == TransactionType.CREDIT) {
            categories.firstOrNull { it.name.contains("Salary", true) || it.name.contains("Income", true) }?.id
        } else null
        val occurred = email.receivedAt
        val hash = TransactionRepository.contentHash(type, money.paise, occurred, counterparty, ref, email.messageId)

        return Transaction(
            id = UUID.randomUUID().toString(),
            type = type,
            amountPaise = money.paise,
            occurredAt = occurred,
            merchant = counterparty,
            counterparty = counterparty,
            categoryId = categoryId,
            paymentMethod = method,
            isCash = false,
            source = source,
            emailMessageId = email.messageId,
            externalRefId = ref,
            contentHash = hash,
            classificationStatus = if (categoryId != null) ClassificationStatus.CLASSIFIED else ClassificationStatus.PENDING,
            note = null,
            categoryName = categories.firstOrNull { it.id == categoryId }?.name,
        )
    }

    private fun mapExtracted(
        e: ExtractedTransaction,
        email: RawEmail,
        walletLabel: String,
        source: TransactionSource,
        categories: List<com.krtky.financetracker.domain.model.Category>,
        banks: List<String>,
        defaultDigital: String,
    ): Transaction? {
        val amount = e.amount ?: return null
        if (amount <= 0) return null
        val conf = e.confidence ?: 0.5
        if (conf < 0.35) return null
        val money = Money.fromRupees(amount)
        val type = when (e.type?.lowercase(Locale.US)) {
            "received", "income", "credit", "credited" -> TransactionType.CREDIT
            "sent", "expense", "debit", "debited", "paid" -> TransactionType.DEBIT
            "none", "null", "ignore", "bill", "reminder" -> return null
            else -> return null
        }
        val occurred = parseTime(e.occurredAt) ?: email.receivedAt
        val party = e.counterparty?.takeIf { it.isNotBlank() } ?: e.merchant?.takeIf { it.isNotBlank() }
        val bank = e.bank?.takeIf { it.isNotBlank() }
            ?: detectBank("${email.subject.orEmpty()} ${e.note.orEmpty()} ${e.paymentMethod.orEmpty()}", email.sender, banks)
            ?: e.paymentMethod?.takeIf { m ->
                !m.equals("Cash", true) && !m.equals("Digital", true) && !m.equals("UPI", true)
            }
            ?: walletLabel.takeIf {
                it.isNotBlank() && !it.equals("Email", true) && !it.equals("Digital", true)
            }
        val method = when {
            e.paymentMethod.equals("Cash", true) -> "Cash"
            else -> resolveMethodLabel(bank ?: e.paymentMethod, banks, defaultDigital)
        }
        val categoryId = matchCategory(e.category, categories)
            ?: if (type == TransactionType.CREDIT) {
                categories.firstOrNull { it.name.contains("Salary", true) || it.name.contains("Income", true) }?.id
            } else null
        val hash = TransactionRepository.contentHash(
            type, money.paise, occurred, party, e.referenceId, email.messageId,
        )
        return Transaction(
            id = UUID.randomUUID().toString(),
            type = type,
            amountPaise = money.paise,
            occurredAt = occurred,
            merchant = party,
            counterparty = party,
            categoryId = categoryId,
            paymentMethod = method,
            isCash = method.equals("Cash", true),
            source = source,
            emailMessageId = email.messageId,
            externalRefId = e.referenceId,
            contentHash = hash,
            classificationStatus = if (categoryId != null) ClassificationStatus.CLASSIFIED else ClassificationStatus.PENDING,
            note = e.note,
            categoryName = categories.firstOrNull { it.id == categoryId }?.name,
        )
    }

    /**
     * Map a detected bank/wallet string onto a configured account, else [defaultDigital].
     * Prefer exact user-list labels so balances stay on the right account.
     */
    private fun resolveMethodLabel(
        detected: String?,
        banks: List<String>,
        defaultDigital: String,
    ): String {
        val cleaned = detected?.trim()?.takeIf { it.isNotBlank() }
        if (cleaned != null) {
            if (cleaned.equals("Cash", true)) return "Cash"
            if (!cleaned.equals("Digital", true) && !cleaned.equals("UPI", true)) {
                matchBankToList(cleaned, banks)?.let { return it }
                // Unknown free-form label — keep it so user can still see AI guess
                return cleaned
            }
        }
        return defaultDigital.ifBlank { banks.firstOrNull() ?: "Digital" }
    }

    /** Map free-form bank/wallet text onto the closest configured account label. */
    private fun matchBankToList(raw: String, banks: List<String>): String? {
        if (banks.isEmpty()) return null
        val needle = raw.trim()
        banks.firstOrNull { it.equals(needle, true) }?.let { return it }
        banks.firstOrNull {
            needle.contains(it, true) || it.contains(needle, true)
        }?.let { return it }
        // Alias normalization (Google Pay → GPay, etc.) against user labels
        val aliases = bankAliasKeys(needle)
        for (bank in banks) {
            val bankKeys = bankAliasKeys(bank)
            if (aliases.any { a -> bankKeys.any { b -> a.equals(b, true) || a.contains(b, true) || b.contains(a, true) } }) {
                return bank
            }
        }
        return null
    }

    private fun bankAliasKeys(label: String): List<String> {
        val n = label.trim().lowercase()
            .replace("bank", "")
            .replace("limited", "")
            .replace("ltd", "")
            .replace(".", "")
            .replace("-", " ")
            .trim()
        val keys = mutableListOf(n, n.replace(" ", ""))
        when {
            n.contains("google pay") || n == "gpay" || n.contains("g pay") -> {
                keys += listOf("gpay", "google pay", "googlepay")
            }
            n.contains("phonepe") || n.contains("phone pe") -> keys += listOf("phonepe", "phone pe")
            n.contains("paytm") -> keys += listOf("paytm")
            n.contains("amazon pay") -> keys += listOf("amazon pay", "amazonpay")
            n.contains("fampay") || n.contains("fam pay") -> keys += listOf("fampay", "fam")
            n.contains("hdfc") -> keys += listOf("hdfc")
            n.contains("icici") -> keys += listOf("icici")
            n.contains("sbi") || n.contains("state bank") -> keys += listOf("sbi", "state bank")
            n.contains("axis") -> keys += listOf("axis")
            n.contains("kotak") -> keys += listOf("kotak")
            n.contains("yes bank") || n == "yes" -> keys += listOf("yes", "yes bank")
            n.contains("idfc") -> keys += listOf("idfc")
            n.contains("cred") -> keys += listOf("cred")
        }
        return keys.distinct()
    }

    private fun matchCategory(
        raw: String?,
        categories: List<com.krtky.financetracker.domain.model.Category>,
    ): Long? {
        if (raw.isNullOrBlank() || categories.isEmpty()) return null
        val needle = raw.trim().lowercase()
        // Exact
        categories.firstOrNull { it.name.equals(needle, true) }?.id?.let { return it }
        // Contained either way
        categories.firstOrNull {
            val n = it.name.lowercase()
            n.contains(needle) || needle.contains(n)
        }?.id?.let { return it }
        // Token overlap (e.g. "Food & Dining" vs "Food")
        val tokens = needle.split(' ', '/', '&', '-', '_').filter { it.length >= 3 }
        if (tokens.isNotEmpty()) {
            categories.firstOrNull { cat ->
                val cn = cat.name.lowercase()
                tokens.any { t -> cn.contains(t) || t.contains(cn) }
            }?.id?.let { return it }
        }
        return null
    }

    private fun detectBank(text: String, sender: String, banks: List<String>): String? {
        val blob = "$sender $text"
        // Prefer user's configured accounts first
        banks.firstOrNull { bank -> blob.contains(bank, ignoreCase = true) }?.let { return it }
        // Hint hits mapped back onto user list when possible
        for (hint in bankHints) {
            if (!blob.contains(hint, ignoreCase = true)) continue
            matchBankToList(hint, banks)?.let { return it }
            if (banks.isEmpty()) return hint
        }
        return null
    }

    private fun extractCounterparty(text: String, type: TransactionType): String? {
        val patterns = if (type == TransactionType.CREDIT) {
            listOf(
                Regex("""(?:received from|from|credited by|sender)[:\s]+([A-Za-z0-9 &._@-]{2,50})""", RegexOption.IGNORE_CASE),
                Regex("""(?:by)\s+([A-Za-z][A-Za-z0-9 &._-]{1,40})""", RegexOption.IGNORE_CASE),
            )
        } else {
            listOf(
                Regex("""(?:paid to|sent to|to|towards|at|merchant)[:\s]+([A-Za-z0-9 &._@-]{2,50})""", RegexOption.IGNORE_CASE),
                Regex("""(?:to)\s+([A-Za-z][A-Za-z0-9 &._-]{1,40})""", RegexOption.IGNORE_CASE),
            )
        }
        for (p in patterns) {
            val m = p.find(text)?.groupValues?.getOrNull(1)?.trim()?.trimEnd('.', ',', ';')
            if (!m.isNullOrBlank() &&
                !m.equals("your", true) &&
                !m.equals("you", true) &&
                !m.contains("account", true) &&
                !m.contains("bank", true)
            ) return m.take(50)
        }
        return null
    }

    private fun parseTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return try {
            Instant.parse(raw).toEpochMilli()
        } catch (_: Exception) {
            try {
                val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                LocalDateTime.parse(raw.trim().take(19).replace('T', ' '), fmt)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
}
