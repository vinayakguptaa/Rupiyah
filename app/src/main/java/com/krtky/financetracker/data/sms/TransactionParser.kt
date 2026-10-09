package com.krtky.financetracker.data.sms

import com.krtky.financetracker.data.llm.ExtractedTransaction
import com.krtky.financetracker.data.llm.LlmClient
import com.krtky.financetracker.data.llm.LlmError
import com.krtky.financetracker.data.llm.LlmResult
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.Account
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

/** Envelope for one SMS message to be parsed. */
data class RawSms(
    val messageId: String,
    val sender: String,
    val body: String,
    val receivedAt: Long,
)

/** Resolved account linkage from a free-text bank/wallet label. */
data class AccountLink(
    val accountId: Long?,
    val accountName: String?,
    val isCash: Boolean,
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
            (your\s+)?(credit\s+card\s+)?bill\b|
            bill\s+of\s+rs|
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
            overdue|
            scheduled\s+(for|on)|
            will\s+be\s+(debited|charged)
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

    /** Result of one parse: the transaction (if any) and why the AI pass failed (if it did). */
    data class SmsParse(
        val transaction: Transaction?,
        /** Non-null when the AI call failed; [transaction] is then regex-only (or null). */
        val llmError: LlmError?,
    )

    /**
     * Deterministic regex parse, enriched by the LLM when configured.
     * Works without AI; pass [useLlm] = false to force the regex-only path.
     */
    suspend fun parseSms(sender: String, body: String, receivedAt: Long, useLlm: Boolean = true): SmsParse {
        val d = parseSourceDetailed(
            RawSms("sms-$receivedAt-${body.hashCode()}", sender, body, receivedAt),
            TransactionSource.SMS,
            useLlm,
        )
        return SmsParse(d.outcome?.transaction, d.llmError)
    }

    data class PasteParse(val movement: ParsedMovement?, val llmError: LlmError?)

    /**
     * Paste/share review path: one completed movement, plus optional self-transfer
     * account pair from LLM hints or text heuristics.
     */
    suspend fun parsePastedMovement(
        body: String,
        receivedAt: Long = System.currentTimeMillis(),
    ): PasteParse {
        val trimmed = body.trim()
        if (trimmed.isBlank()) return PasteParse(null, null)
        val sms = RawSms("paste-$receivedAt-${trimmed.hashCode()}", "paste", trimmed, receivedAt)
        val detailed = parseSourceDetailed(sms, TransactionSource.PASTE, useLlm = true)
        val outcome = detailed.outcome ?: return PasteParse(null, detailed.llmError)
        val accounts = accountRepository.observeActive().first()
        val heuristic = inferSelfTransfer(trimmed, accounts)
        val llmPair = outcome.selfTransfer?.let { (fromName, toName) ->
            val from = accounts.firstOrNull { it.name.equals(fromName, true) }
                ?: accounts.firstOrNull {
                    it.name.contains(fromName, true) || fromName.contains(it.name, true)
                }
            val to = accounts.firstOrNull { it.name.equals(toName, true) }
                ?: accounts.firstOrNull {
                    it.name.contains(toName, true) || toName.contains(it.name, true)
                }
            if (from != null && to != null && from.id != to.id) from.id to to.id else null
        }
        return PasteParse(
            ParsedMovement(
                transaction = outcome.transaction,
                transferFromAccountId = llmPair?.first ?: heuristic?.first,
                transferToAccountId = llmPair?.second ?: heuristic?.second,
            ),
            detailed.llmError,
        )
    }

    data class ParsedMovement(
        val transaction: Transaction,
        val transferFromAccountId: Long? = null,
        val transferToAccountId: Long? = null,
    )

    private data class ParseOutcome(
        val transaction: Transaction,
        /** Source/destination account labels when LLM marks a self-transfer. */
        val selfTransfer: Pair<String, String>? = null,
    )

    private data class Detailed(val outcome: ParseOutcome?, val llmError: LlmError? = null)

    /**
     * Self-transfer when the note names two of the user's accounts
     * (or says transferred / NEFT / IMPS / RTGS between them).
     * Bank SMS about paying a merchant only names one account — that stays a debit.
     */
    fun inferSelfTransfer(text: String, accounts: List<Account>): Pair<Long, Long>? {
        if (accounts.size < 2) return null
        val lower = text.lowercase(Locale.US)
        val hits = accounts
            .filter { it.name.isNotBlank() && it.name.length >= 3 }
            .filter { lower.contains(it.name.lowercase(Locale.US)) }
            .sortedByDescending { it.name.length }
            .distinctBy { it.id }
        if (hits.size < 2) return null
        val transferish = Regex(
            """\b(transfer(?:red)?|neft|imps|rtgs|self[\s-]?transfer|own\s+account|to\s+self)\b""",
            RegexOption.IGNORE_CASE,
        )
        val fromTo = Regex(
            """from\s+(.{2,48}?)\s+to\s+(.{2,48}?)(?:[.\n]|$)""",
            RegexOption.IGNORE_CASE,
        ).find(text)
        if (fromTo != null) {
            val fromHit = hits.firstOrNull { fromTo.groupValues[1].contains(it.name, true) }
            val toHit = hits.firstOrNull { fromTo.groupValues[2].contains(it.name, true) }
            if (fromHit != null && toHit != null && fromHit.id != toHit.id) {
                return fromHit.id to toHit.id
            }
        }
        if (!transferish.containsMatchIn(text) && hits.size < 2) return null
        val ordered = accounts.filter { acc -> hits.any { it.id == acc.id } }
            .sortedBy { acc -> lower.indexOf(acc.name.lowercase(Locale.US)).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        if (ordered.size < 2) return null
        return ordered[0].id to ordered[1].id
    }

    private suspend fun parseSourceDetailed(
        sms: RawSms,
        source: TransactionSource,
        useLlm: Boolean,
    ): Detailed {
        val text = SmsRedactor.stripHtml(sms.body)
        // Bills / dues / reminders never become transactions — even if an amount is present.
        if (looksLikeNonMovement(text)) return Detailed(null)

        val categories = categoryRepository.getAll()
        // Prefer live active accounts; fall back to prefs mirror.
        val banks = accountRepository.activeBankNames()
            .ifEmpty { userPreferences.parseBankList(userPreferences.bankAccounts.first()) }
        val defaultDigital = userPreferences.resolveDigitalPaymentMethod(null)
            .takeIf { name -> banks.any { it.equals(name, true) } || name.equals("Cash", true) }
            .orEmpty()
            .ifBlank { banks.firstOrNull().orEmpty() }

        val llmResult = if (useLlm && llmClient.isConfigured()) {
            llmClient.extractTransaction(
                messageBody = SmsRedactor.redact(text),
                subject = null,
                sender = sms.sender,
                categories = categories.map { it.name },
                banks = banks,
            )
        } else {
            null
        }
        val extracted = llmResult?.getOrNull()
        val llmError = llmResult?.errorOrNull()

        // When AI explicitly refuses, do not fall back to deterministic amount scraping.
        if (extracted != null && isRejectedExtract(extracted)) return Detailed(null)

        val deterministic = parseDeterministic(
            text, sms, source, categories, banks, defaultDigital,
        )
        val fromLlm = extracted?.let {
            mapExtracted(it, sms, source, categories, banks, defaultDigital)
        }
        val merged = merge(deterministic, fromLlm, categories, banks, defaultDigital)
            ?: return Detailed(null, llmError)

        val selfTransfer = extracted?.takeIf { it.isSelfTransfer == true }?.let { e ->
            val from = e.bank?.trim()?.takeIf { it.isNotBlank() }
            val to = e.toBank?.trim()?.takeIf { it.isNotBlank() }
            if (from != null && to != null && !from.equals(to, true)) from to to else null
        }
        return Detailed(ParseOutcome(merged, selfTransfer), llmError)
    }

    private fun looksLikeNonMovement(text: String): Boolean =
        nonMovement.containsMatchIn(text) && !movementConfirm.containsMatchIn(text)

    private fun isRejectedExtract(e: ExtractedTransaction): Boolean {
        val t = e.type?.trim()?.lowercase(Locale.US) ?: return false
        return t in setOf(
            "none", "null", "ignore", "bill", "reminder", "due", "statement", "request",
        )
    }

    /**
     * Resolve a free-text bank/wallet label onto the accounts table.
     * Unmatched / Digital / UPI labels map to no account (displayed under "Digital");
     * the guessed name is still kept on the row for display.
     */
    private suspend fun resolveAccount(label: String?): AccountLink {
        val trimmed = label?.trim().orEmpty()
        if (trimmed.isBlank() || trimmed.equals("Digital", true) || trimmed.equals("UPI", true)) {
            return AccountLink(null, null, isCash = false)
        }
        if (trimmed.equals("Cash", true)) {
            return AccountLink(
                accountId = accountRepository.getByName("Cash")?.id,
                accountName = "Cash",
                isCash = true,
            )
        }
        val acc = accountRepository.getByName(trimmed)
        return if (acc != null) {
            AccountLink(acc.id, acc.name, isCash = acc.kind.name == "CASH")
        } else {
            AccountLink(null, trimmed, isCash = false)
        }
    }

    private suspend fun merge(
        base: Transaction?,
        llm: Transaction?,
        categories: List<com.krtky.financetracker.domain.model.Category>,
        banks: List<String>,
        defaultDigital: String,
    ): Transaction? {
        if (base == null) return llm
        if (llm == null) return base
        val party = llm.counterparty ?: base.counterparty
        val rawMethod = llm.accountName?.takeIf { it.isNotBlank() }
            ?: base.accountName
        val method = resolveMethodLabel(rawMethod, banks, defaultDigital)
        val link = resolveAccount(method)
        val catId = llm.categoryId ?: base.categoryId
        val catName = categories.firstOrNull { it.id == catId }?.name
        val classified = catId != null
        // Regex reads the unredacted text, so its ref is authoritative; the LLM only sees masked digits.
        val ref = base.externalRefId ?: llm.externalRefId
        val occurred = pickOccurredAt(receivedAt = base.occurredAt, llmTime = llm.occurredAt)
        return base.copy(
            occurredAt = occurred,
            type = llm.type,
            amountPaise = if (llm.amountPaise > 0) llm.amountPaise else base.amountPaise,
            counterparty = party,
            categoryId = catId,
            categoryName = catName ?: base.categoryName,
            accountId = link.accountId,
            accountName = link.accountName,
            isCash = link.isCash,
            externalRefId = ref,
            note = llm.note ?: base.note,
            rawDescription = base.rawDescription ?: llm.rawDescription,
            classificationStatus = if (classified) ClassificationStatus.CLASSIFIED else ClassificationStatus.PENDING,
            contentHash = TransactionRepository.contentHash(
                llm.type,
                if (llm.amountPaise > 0) llm.amountPaise else base.amountPaise,
                occurred,
                party,
                ref,
                base.smsMessageId,
            ),
        )
    }

    private suspend fun parseDeterministic(
        text: String,
        sms: RawSms,
        source: TransactionSource,
        categories: List<com.krtky.financetracker.domain.model.Category>,
        banks: List<String>,
        defaultDigital: String,
    ): Transaction? {
        if (nonMovement.containsMatchIn(text) && !movementConfirm.containsMatchIn(text)) {
            return null
        }

        val amountMatch = amountRegex.find(text) ?: return null
        val amountStr = amountMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return null
        val money = Money.fromRupeesString(amountStr) ?: return null
        if (money.paise <= 0) return null

        val type = when {
            credited.containsMatchIn(text) && !debited.containsMatchIn(text) -> TransactionType.CREDIT
            debited.containsMatchIn(text) || movementConfirm.containsMatchIn(text) -> TransactionType.DEBIT
            else -> return null
        }

        val ref = refRegex.find(text)?.groupValues?.getOrNull(1)
        val counterparty = extractCounterparty(text, type)
        val bank = detectBank(text, sms.sender, banks)
        val method = resolveMethodLabel(bank, banks, defaultDigital)
        val link = resolveAccount(method)
        // No guessing from type alone: a credit can be salary, a refund or a friend paying back.
        val categoryId: Long? = null
        val occurred = sms.receivedAt
        val hash = TransactionRepository.contentHash(type, money.paise, occurred, counterparty, ref, sms.messageId)

        return Transaction(
            id = UUID.randomUUID().toString(),
            type = type,
            amountPaise = money.paise,
            occurredAt = occurred,
            counterparty = counterparty,
            categoryId = categoryId,
            accountId = link.accountId,
            accountName = link.accountName,
            isCash = link.isCash,
            source = source,
            smsMessageId = sms.messageId,
            externalRefId = ref,
            contentHash = hash,
            classificationStatus = if (categoryId != null) ClassificationStatus.CLASSIFIED else ClassificationStatus.PENDING,
            note = null,
            rawDescription = sms.body,
            categoryName = categories.firstOrNull { it.id == categoryId }?.name,
        )
    }

    private suspend fun mapExtracted(
        e: ExtractedTransaction,
        sms: RawSms,
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
        val type = when (e.type?.trim()?.lowercase(Locale.US)) {
            "credit", "credited", "received", "income", "cr" -> TransactionType.CREDIT
            "debit", "debited", "sent", "expense", "paid", "dr" -> TransactionType.DEBIT
            "none", "null", "ignore", "bill", "reminder" -> return null
            else -> return null
        }
        val occurred = parseTime(e.occurredAt) ?: sms.receivedAt
        val party = e.counterparty?.takeIf { it.isNotBlank() } ?: e.merchant?.takeIf { it.isNotBlank() }
        val bank = e.bank?.takeIf { it.isNotBlank() }
            ?: detectBank("${e.note.orEmpty()} ${e.paymentMethod.orEmpty()}", sms.sender, banks)
            ?: e.paymentMethod?.takeIf { m ->
                !m.equals("Cash", true) && !m.equals("Digital", true) && !m.equals("UPI", true)
            }
        val method = when {
            e.paymentMethod.equals("Cash", true) -> "Cash"
            else -> resolveMethodLabel(bank ?: e.paymentMethod, banks, defaultDigital)
        }
        val link = resolveAccount(method)
        val categoryId = matchCategory(e.category, categories)
        val ref = e.referenceId?.trim()?.takeIf { it.isNotBlank() && '*' !in it }
        val hash = TransactionRepository.contentHash(
            type, money.paise, occurred, party, ref, sms.messageId,
        )
        return Transaction(
            id = UUID.randomUUID().toString(),
            type = type,
            amountPaise = money.paise,
            occurredAt = occurred,
            counterparty = party,
            categoryId = categoryId,
            accountId = link.accountId,
            accountName = link.accountName,
            isCash = link.isCash,
            source = source,
            smsMessageId = sms.messageId,
            externalRefId = ref,
            contentHash = hash,
            classificationStatus = if (categoryId != null) ClassificationStatus.CLASSIFIED else ClassificationStatus.PENDING,
            note = e.note,
            rawDescription = sms.body,
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
        val cleaned = raw.trim()
            .removeSurrounding("\"")
            .replace('\u00a0', ' ')
            .trim()
        // Instant / OffsetDateTime style
        runCatching { Instant.parse(cleaned).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching {
            java.time.OffsetDateTime.parse(cleaned).toInstant().toEpochMilli()
        }.getOrNull()?.let { return it }
        runCatching {
            java.time.ZonedDateTime.parse(cleaned).toInstant().toEpochMilli()
        }.getOrNull()?.let { return it }

        val zone = ZoneId.of("Asia/Kolkata")
        val withSpace = cleaned.replace('T', ' ').trim()
        val slashToDash = withSpace.replace('/', '-')
        val candidates = listOf(cleaned, withSpace, slashToDash).distinct()
        val dateTimePatterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yy HH:mm:ss",
            "dd-MM-yy HH:mm",
            "dd-MMM-yyyy HH:mm:ss",
            "dd-MMM-yyyy HH:mm",
            "dd-MMM-yy HH:mm",
        )
        for (value in candidates) {
            for (pattern in dateTimePatterns) {
                runCatching {
                    val fmt = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
                    LocalDateTime.parse(value, fmt).atZone(zone).toInstant().toEpochMilli()
                }.getOrNull()?.let { return it }
            }
        }
        val dateOnlyPatterns = listOf(
            "yyyy-MM-dd",
            "dd-MM-yyyy",
            "dd-MM-yy",
            "dd-MMM-yyyy",
            "dd-MMM-yy",
        )
        for (value in candidates) {
            val datePart = value.takeWhile { it != ' ' }.trim()
            for (pattern in dateOnlyPatterns) {
                runCatching {
                    val fmt = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
                    java.time.LocalDate.parse(datePart, fmt)
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli()
                }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    companion object {
        private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

        /**
         * Choose between the time the message arrived and the date the AI read from its text.
         * The AI date wins only when it names a different day (an older SMS or a pasted note);
         * on the same day the arrival time is more precise. Future or implausibly old dates are ignored.
         */
        fun pickOccurredAt(receivedAt: Long, llmTime: Long): Long {
            if (llmTime == receivedAt) return receivedAt
            if (llmTime > receivedAt + 60 * 60_000L) return receivedAt
            if (receivedAt - llmTime > 400L * 24 * 60 * 60_000L) return receivedAt
            val llmDay = Instant.ofEpochMilli(llmTime).atZone(IST).toLocalDate()
            val receivedDay = Instant.ofEpochMilli(receivedAt).atZone(IST).toLocalDate()
            return if (llmDay == receivedDay) receivedAt else llmTime
        }
    }
}