package com.krtky.financetracker.data.sms

/**
 * Step 1 of the SMS pipeline: is this message a completed money movement worth parsing?
 * Pure and cheap — no AI. Every rejection carries a reason that is shown in the SMS inbox.
 */
object SmsFilter {

    sealed interface Verdict {
        /** Worth parsing. [bank] is the bank / card guessed from the sender id, if any. */
        data class Candidate(val bank: String?) : Verdict
        data class Ignore(val reason: String) : Verdict
    }

    /**
     * Amount next to a currency marker, or SBI's bare "debited by 170.00". Numbers must start
     * with a digit: a bare "," used to match ("Dear Customer, INR 1,25,000") and sink the parse.
     * Groups 1–3 hold the number for whichever form matched.
     */
    val AMOUNT = Regex(
        """(?:₹|Rs\.?|INR)\s*([0-9][0-9,]*(?:\.[0-9]{1,4})?)|([0-9][0-9,]*(?:\.[0-9]{1,4})?)\s*(?:₹|Rs\.?|INR)\b|\b(?:debited|credited)\s+(?:by|for|with)\s+([0-9][0-9,]*(?:\.[0-9]{1,4})?)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Future / conditional wording: "will be debited" is a reminder, not a movement. */
    private val FUTURE = Regex(
        """\b(will\s+be|to\s+be|shall\s+be|would\s+be|is\s+scheduled\s+to\s+be)\s+(debited|credited|charged|deducted|paid)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Confirmations of money already recorded elsewhere (bank debit is the real transaction). */
    private val CONFIRMATION_ONLY = Regex(
        """\b(units?\s+(will\s+be\s+|are\s+|have\s+been\s+)?allotted|received\s+your\s+(subscription|purchase|sip)|purchase\s+request|sip\s+(registration|instalment\s+due)|ppf\s+a/?c(count)?\s+(no\.?\s*)?(ending\s+\d+\s+)?(is\s+)?credited)\b""",
        RegexOption.IGNORE_CASE,
    )

    val NON_MOVEMENT = Regex(
        """\b(
            bill\s+(is\s+)?(generated|ready|due)|
            your\s+(credit\s+card\s+)?bill\b|
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
            will\s+be\s+(debited|charged|credited)
        )\b""".lines().joinToString("") { it.trim() }, // trimIndent() left 12-space prefixes on every alternative
        RegexOption.IGNORE_CASE,
    )

    val MOVEMENT_CONFIRM = Regex(
        """\b(debited|credited|spent|withdrawn|deducted|transferred|successful(?:ly)?\s+paid|payment\s+successful|upi-?ref|txn\s*id|utr)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val MOVEMENT_WORDS = Regex(
        """\b(debited|credited|spent|paid|sent|received|withdrawn|deducted|transferred|purchase|txn|transaction|refund(ed)?|deposited|(a\s+)?credit\s+(by|of)|(a\s+)?debit\s+(by|of))\b""",
        RegexOption.IGNORE_CASE,
    )

    private val OTP = Regex(
        """\b(otp|one[\s-]?time\s+password|verification\s+code|security\s+code|code\s+is)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val PROMO = Regex(
        """\b(offer|cashback\s+up\s+to|win\b|pre-?approved|apply\s+now|limit\s+(increased|enhanced)|loan\s+of|click\s+here|congratulations|eligible)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** DLT headers end in -P for promotional traffic (e.g. "VM-AMAZON-P"). */
    private val PROMO_SENDER = Regex("""-P$""", RegexOption.IGNORE_CASE)

    /** Sender-id fragments → bank / card label. Matched against the user's own account names. */
    private val SENDER_BANKS = listOf(
        "SBI" to listOf("SBIUPI", "SBIINB", "SBIPSG", "ATMSBI", "SBMSMS", "CBSSBI", "SBICRD", "SBYONO"),
        "Kotak" to listOf("KOTAKB", "KOTAK"),
        "HDFC" to listOf("HDFCBK", "HDFCBN", "HDFC"),
        "ICICI" to listOf("ICICIB", "ICICIT", "ICICI"),
        "Axis" to listOf("AXISBK", "AXISBN", "AXIS"),
        // OneCard is issued with BOBCARD; its SMS come from e.g. TX-BOBONE-S.
        "OneCard" to listOf("ONECRD", "ONECARD", "1CARD", "BOBONE"),
        "IDFC" to listOf("IDFCFB", "IDFC"),
        "Yes Bank" to listOf("YESBNK", "YESBK"),
        "PNB" to listOf("PNBSMS", "PUNBNK"),
        "BOB" to listOf("BOBTXN", "BOBSMS"),
        "Canara" to listOf("CANBNK"),
        "IndusInd" to listOf("INDUSB"),
        "AU Bank" to listOf("AUBANK"),
        "Paytm" to listOf("PAYTMB", "PYTMBK"),
        "Amazon Pay" to listOf("AMZPAY", "APAYIN"),
        "PhonePe" to listOf("PHONPE"),
    )

    /** Bill / due / reminder wording without a confirmation of money that already moved. */
    fun isNonMovement(text: String): Boolean {
        if (FUTURE.containsMatchIn(text) && !MOVEMENT_CONFIRM.containsMatchIn(text.replace(FUTURE, " "))) return true
        return NON_MOVEMENT.containsMatchIn(text) && !MOVEMENT_CONFIRM.containsMatchIn(text.replace(FUTURE, " "))
    }

    /** First valid positive amount in rupees-string form, or null. */
    fun firstAmount(text: String): String? = AMOUNT.findAll(text)
        .mapNotNull { m -> m.groupValues.drop(1).firstOrNull { it.isNotBlank() } }
        .firstOrNull { s -> s.replace(",", "").toDoubleOrNull()?.let { it > 0 } == true }

    fun check(
        sender: String,
        body: String,
        allowSenders: List<String> = emptyList(),
        keywords: List<String> = emptyList(),
        userBanks: List<String> = emptyList(),
    ): Verdict {
        val confirm = MOVEMENT_CONFIRM.containsMatchIn(body.replace(FUTURE, " "))
        if (PROMO_SENDER.containsMatchIn(sender.trim())) return Verdict.Ignore("promotional sender")
        if (OTP.containsMatchIn(body) && !confirm) return Verdict.Ignore("OTP / verification code")

        val normalizedSender = sender.trim().lowercase()
        val text = body.lowercase()
        val senderAllowed = allowSenders.isEmpty() || allowSenders.any {
            normalizedSender == it || normalizedSender.contains(it) || it.contains(normalizedSender)
        }
        val keywordMatched = keywords.isNotEmpty() && keywords.any { text.contains(it) }
        if (!senderAllowed && !keywordMatched) return Verdict.Ignore("sender not in your allowed list")

        if (firstAmount(body) == null) return Verdict.Ignore("no amount")
        if (isNonMovement(body)) return Verdict.Ignore("bill, due or reminder")
        if (CONFIRMATION_ONLY.containsMatchIn(body)) {
            return Verdict.Ignore("investment confirmation — the bank debit is the transaction")
        }
        if (PROMO.containsMatchIn(body) && !confirm) return Verdict.Ignore("offer / promotion")
        if (!MOVEMENT_WORDS.containsMatchIn(body)) return Verdict.Ignore("no debit / credit wording")
        return Verdict.Candidate(bankForSender(sender, userBanks))
    }

    /** Best-effort bank label from the sender id, preferring the user's own account names. */
    fun bankForSender(sender: String, userBanks: List<String> = emptyList()): String? {
        val s = sender.uppercase().replace(Regex("[^A-Z0-9]"), "")
        val label = SENDER_BANKS.firstOrNull { (_, codes) -> codes.any { s.contains(it) } }?.first ?: return null
        return userBanks.firstOrNull { it.equals(label, true) }
            ?: userBanks.firstOrNull { it.contains(label, true) || label.contains(it, true) }
            ?: label
    }
}
