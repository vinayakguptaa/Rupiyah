package com.krtky.financetracker.data.sms

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.data.classify.LocalClassifier
import com.krtky.financetracker.data.classify.PayeeKeys
import com.krtky.financetracker.data.local.db.LearningRow
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.TransactionType
import org.junit.Test

class SmsPipelinePartsTest {

    private val kotakUpi =
        "Sent Rs.88.00 from Kotak Bank AC X1234 to 8368860776@ptyes on 04-08-26.UPI Ref 658276725698. Not you, https://kotak.com/KBANKT/Fraud"
    private val sbiCredit = "Dear SBI UPI User, ur A/cX2095 credited by Rs1432 on 06Aug26 by MR MUKESH GUPTA (Ref no 658414307460)"

    // ---- filter ----

    @Test
    fun `bank debit and credit SMS are candidates with bank from sender`() {
        val v = SmsFilter.check("AX-KOTAKB-S", kotakUpi, userBanks = listOf("SBI", "Kotak", "OneCard"))
        assertThat(v).isEqualTo(SmsFilter.Verdict.Candidate("Kotak"))
        val s = SmsFilter.check("JD-SBIUPI-T", sbiCredit, userBanks = listOf("SBI", "Kotak"))
        assertThat(s).isEqualTo(SmsFilter.Verdict.Candidate("SBI"))
    }

    @Test
    fun `otp, promo, reminders and amount-less messages are ignored with a reason`() {
        fun reason(sender: String, body: String) = (SmsFilter.check(sender, body) as SmsFilter.Verdict.Ignore).reason
        assertThat(reason("AX-HDFCBK-S", "OTP for txn of Rs 500 at Amazon is 123456. Do not share."))
            .isEqualTo("OTP / verification code")
        assertThat(reason("VM-AMAZON-P", "Flat Rs 200 cashback on your next order"))
            .isEqualTo("promotional sender")
        assertThat(reason("AX-ONECRD-S", "Your OneCard bill of Rs 11,852.75 is due on 10 Oct. Please pay by then."))
            .isEqualTo("bill, due or reminder")
        assertThat(reason("AX-KOTAKB-S", "Your Kotak account statement for September is ready"))
            .isEqualTo("no amount")
    }

    @Test
    fun `allow list blocks other senders unless a keyword matches`() {
        val v = SmsFilter.check("AX-RANDOM-S", kotakUpi, allowSenders = listOf("sbiupi"), keywords = emptyList())
        assertThat(v).isEqualTo(SmsFilter.Verdict.Ignore("sender not in your allowed list"))
        val k = SmsFilter.check("AX-RANDOM-S", kotakUpi, allowSenders = listOf("sbiupi"), keywords = listOf("upi ref"))
        assertThat(k).isInstanceOf(SmsFilter.Verdict.Candidate::class.java)
    }

    @Test
    fun `SBI UPI debit without currency marker is a candidate`() {
        val body = "Dear UPI user A/C X1111 debited by 170.00 on date 10Oct26 trf to TEST MALL Refno 600000000001 If not u? call-1800111109 for other services-18001234-SBI"
        assertThat(SmsFilter.check("JK-SBIUPI-S", body, userBanks = listOf("SBI", "Kotak")))
            .isEqualTo(SmsFilter.Verdict.Candidate("SBI"))
    }

    @Test
    fun `OneCard SMS from BOBCARD sender maps to OneCard`() {
        val body = "That's a hit! Rs. 296.36 spent at Test Merchant with your BOBCARD One Credit Card xxXX0000. Reward points added."
        assertThat(SmsFilter.check("TX-BOBONE-S", body, userBanks = listOf("SBI", "Kotak", "OneCard")))
            .isEqualTo(SmsFilter.Verdict.Candidate("OneCard"))
        assertThat(TransactionParser.cleanCounterparty("Test Merchant with your BOBCARD One Credit Card xxXX0000"))
            .isEqualTo("Test Merchant")
    }

    @Test
    fun `reminders and investment confirmations are not transactions`() {
        fun reason(sender: String, body: String) = (SmsFilter.check(sender, body) as? SmsFilter.Verdict.Ignore)?.reason
        assertThat(reason("TX-BOBONE-S", "Rs.25845.4800 will be debited from a/c ending 0000 on 06-10-2026 for your BOBCARD One Credit Card bill."))
            .isEqualTo("bill, due or reminder")
        assertThat(reason("AD-SBYONO-T", "Dear Customer, your PPF A/c ending 0000 credited with Rs 7,500 -YONO SBI"))
            .startsWith("investment confirmation")
        assertThat(reason("AD-MOAMCL-S", "Dear Investor, We have received your subscription in Test Fund under folio 1 for Rs.3000.00. Units will be allotted after necessary validation."))
            .startsWith("investment confirmation")
        // A real debit that mentions a future date is still a debit.
        assertThat(SmsFilter.check("JD-SBIUPI-S", "Dear UPI user A/C X1111 debited by 50.00 on date 10Oct26 trf to TEST Refno 600000000002"))
            .isInstanceOf(SmsFilter.Verdict.Candidate::class.java)
    }

    @Test
    fun `comma before currency is not an amount`() {
        assertThat(SmsFilter.firstAmount("Dear Customer, INR 1,25,000.00 credited to your A/c")).isEqualTo("1,25,000.00")
    }

    @Test
    fun `bill regex matches its alternatives`() {
        assertThat(SmsFilter.NON_MOVEMENT.containsMatchIn("Payment due on 12 Oct")).isTrue()
        assertThat(SmsFilter.NON_MOVEMENT.containsMatchIn("This is a reminder")).isTrue()
        assertThat(SmsFilter.NON_MOVEMENT.containsMatchIn("Rs 88 debited from A/c")).isFalse()
        assertThat(SmsFilter.NON_MOVEMENT.containsMatchIn("Electricity bill payment of Rs 500 done")).isFalse()
    }

    // ---- counterparty clean-up ----

    @Test
    fun `counterparty stops before date and ref noise`() {
        assertThat(TransactionParser.cleanCounterparty("8368860776@ptyes on 04-08-26.UPI Ref 658276725698"))
            .isEqualTo("8368860776@ptyes")
        assertThat(TransactionParser.cleanCounterparty("SWIGGY via UPI")).isEqualTo("SWIGGY")
        assertThat(TransactionParser.cleanCounterparty("Zomato. Avl bal Rs 1,000")).isEqualTo("Zomato")
        assertThat(TransactionParser.cleanCounterparty("Mr. TEST USER. Avl Balance INR 1,07,362.82-SBI")).isEqualTo("TEST USER")
        assertThat(TransactionParser.cleanCounterparty("NACH- TEST LIMITED of Rs 60.00 on 09/10/26")).isEqualTo("TEST LIMITED")
    }

    // ---- local classifier ----

    private val food = Category(id = 1, name = "Food")
    private val investment = Category(id = 2, name = "Investment")
    private val travel = Category(id = 3, name = "Travel")
    private val dividend = Category(id = 4, name = "Dividend")
    private val cats = listOf(food, investment, travel, dividend)

    @Test
    fun `payee keys match SBI narration across rows`() {
        val a = PayeeKeys.of(null, "WDL TFR   UPI/DR/627335777046/Indian C/HDFC/z /  erodha.ic/Noti   0097")
        val b = PayeeKeys.of("Mutual funds", "WDL TFR   UPI/DR/626692562047/Indian C/HDFC/z /  erodha.ic/Noti   0097")
        assertThat(a).contains("vpa:zerodha.ic")
        assertThat(a.intersect(b)).isNotEmpty()
    }

    @Test
    fun `history guess is confident only with repeated agreement`() {
        val narration = "WDL TFR   UPI/DR/626692562047/Indian C/HDFC/z /  erodha.ic/Noti   0097"
        val index = LocalClassifier.buildIndex(List(3) { LearningRow(null, narration, investment.id, "DEBIT") })
        val g = LocalClassifier.fromHistory(PayeeKeys.of(null, narration), index, cats)!!
        assertThat(g.categoryId).isEqualTo(investment.id)
        assertThat(g.confident).isTrue()

        val once = LocalClassifier.buildIndex(listOf(LearningRow("Rabreez", null, food.id, "DEBIT")))
        val weak = LocalClassifier.fromHistory(PayeeKeys.of("Rabreez", null), once, cats)!!
        assertThat(weak.confident).isFalse()
    }

    @Test
    fun `rules respect direction and only use existing categories`() {
        assertThat(LocalClassifier.fromRules("UBER INDIA SYSTE", TransactionType.DEBIT, cats)?.categoryId)
            .isEqualTo(travel.id)
        assertThat(LocalClassifier.fromRules("ACHCr ICIC021870 Everest K FnlDiv2526", TransactionType.CREDIT, cats)?.categoryId)
            .isEqualTo(dividend.id)
        // A credit from Swiggy is a refund, not food spend.
        assertThat(LocalClassifier.fromRules("Swiggy refund", TransactionType.CREDIT, cats)).isNull()
        // No "Groceries" category configured → no guess.
        assertThat(LocalClassifier.fromRules("Blinkit order", TransactionType.DEBIT, cats)).isNull()
    }

    @Test
    fun `hints list each local guess on its own line`() {
        val h = SmsPipeline.hintsFor(
            SmsPipeline.LocalSnapshot(
                type = "DEBIT", amountPaise = 8800, counterparty = "8368860776@ptyes",
                account = "Kotak", categoryName = "Travel", categoryConfidence = 0.55,
                categoryReason = "you filed one like this as Travel",
            ),
        )
        assertThat(h.lines()).containsExactly(
            "type: DEBIT",
            "amount: ₹88.00",
            "counterparty: 8368860776@ptyes",
            "account: Kotak",
            "category: Travel (you filed one like this as Travel)",
        ).inOrder()
    }
}
