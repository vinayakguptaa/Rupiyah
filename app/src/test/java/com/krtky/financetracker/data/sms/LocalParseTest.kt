package com.krtky.financetracker.data.sms

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.data.llm.LlmClient
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.domain.model.TransactionType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Regex-only parse of real-world bank SMS formats (numbers changed). */
class LocalParseTest {
    private val llm = mockk<LlmClient> { every { isConfigured() } returns false }
    private val categories = mockk<CategoryRepository> { coEvery { getAll() } returns emptyList() }
    private val prefs = mockk<UserPreferences>(relaxed = true) {
        every { bankAccounts } returns flowOf("SBI,Kotak,OneCard")
        coEvery { resolveDigitalPaymentMethod(any()) } returns "SBI"
    }
    private val accounts = mockk<AccountRepository>(relaxed = true) {
        coEvery { activeBankNames() } returns listOf("SBI", "Kotak", "OneCard")
        coEvery { getByName(any()) } returns null
    }
    private val parser = TransactionParser(llm, categories, prefs, accounts)

    private suspend fun parse(sender: String, body: String) =
        parser.parseSms(sender, body, receivedAt = 1_760_000_000_000L, useLlm = false).transaction

    @Test
    fun `SBI NEFT credit with lakh-formatted amount`() = runTest {
        val t = parse(
            "AX-SBIPSG-S",
            "Dear Customer, INR 1,25,000.00 credited to your A/c No XX1111 on 09/10/2026 through NEFT with UTR " +
                "CHASH00000000001 by TestCorp INC, INFO: BATCHID:0002 ACC/PURPOSE/IN P0802 US  BRANCH S BIN0011476  " +
                "FROM TESTCORP INC/ CONTRACTOR:MONTHLY CONSULTING FEE FOR REMOTE SOFTWARE ENGINEERING SERVICE-SBI",
        )
        assertThat(t).isNotNull()
        assertThat(t!!.type).isEqualTo(TransactionType.CREDIT)
        assertThat(t.amountPaise).isEqualTo(12_500_000L)
    }

    @Test
    fun `SBI UPI debit without currency marker`() = runTest {
        val t = parse("JK-SBIUPI-S", "Dear UPI user A/C X1111 debited by 170.00 on date 10Oct26 trf to TEST MALL Refno 600000000001 If not u? call-1800111109 for other services-18001234-SBI")
        assertThat(t!!.type).isEqualTo(TransactionType.DEBIT)
        assertThat(t.amountPaise).isEqualTo(17_000L)
        assertThat(t.counterparty).isEqualTo("TEST MALL")
    }

    @Test
    fun `future debit reminder is not a transaction`() = runTest {
        assertThat(
            parse("TX-BOBONE-S", "Rs.25845.4800 will be debited from a/c ending 0000 on 06-10-2026 for your BOBCARD One Credit Card bill. Ensure sufficient balance."),
        ).isNull()
    }

    @Test
    fun `NACH credit worded as a credit by`() = runTest {
        val t = parse("VM-CBSSBI-S", "Dear Customer,Your A/C XXXXX000000 has a credit by NACH- TEST LIMITED of Rs 60.00 on 09/10/26. Avl Bal Rs 1,70,626.11. Download YONO-SBI")
        assertThat(t!!.type).isEqualTo(TransactionType.CREDIT)
        assertThat(t.amountPaise).isEqualTo(6_000L)
    }
}
