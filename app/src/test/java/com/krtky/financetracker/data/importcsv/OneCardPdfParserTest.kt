package com.krtky.financetracker.data.importcsv

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.domain.model.TransactionType
import org.junit.Test
import java.io.File

class OneCardPdfParserTest {

    @Test
    fun parsesOneCardStatementText() {
        val snippet = """
            BOBCARD One Credit Card Statement (22 Aug 2026 - 21 Sep 2026)
            SUMMARY
            TRANSACTION HISTORY
            Date     Merchant Name     Transaction Type     Reward Points     Amount (Rs.)
                     Grok Xai                                                   708.26
            22 Aug   Forex Fee = 7.00  Electronics          ECOM        14.00
                     GST @ 18% = 1.26                                           null 700.00
            22 Aug Cred                Bills and Utilities  TOKEN_ECOM  15.64   781.82
            04 Sep Paid Via Upi Bbps   Repayments                               11,852.75
                     Cult.fit Emi - (5/6)
                     EMI Principal = 3,487.87
            11 Sep   EMI Interest = 93.63                   EMI                 3,598.36
                     GST @ 18% = 16.86
            11 Sep Zomato              Food & Dining        TOKEN_ECOM  23.55   1,177.48
            EMI SUMMARY
            Ongoing EMIs
        """.trimIndent()

        val result = OneCardPdfParser.parse(snippet)
        assertThat(result.rows).hasSize(5)
        assertThat(result.rows[0].description).contains("Grok Xai")
        assertThat(result.rows[0].amountPaise).isEqualTo(708_26L)
        assertThat(result.rows[0].type).isEqualTo(TransactionType.DEBIT)

        assertThat(result.rows[1].description).contains("Cred")
        assertThat(result.rows[1].amountPaise).isEqualTo(781_82L)

        // Repayment should be CREDIT
        assertThat(result.rows[2].description).contains("Paid Via Upi Bbps")
        assertThat(result.rows[2].amountPaise).isEqualTo(11852_75L)
        assertThat(result.rows[2].type).isEqualTo(TransactionType.CREDIT)

        // EMI
        assertThat(result.rows[3].description).contains("Cult.fit Emi")
        assertThat(result.rows[3].amountPaise).isEqualTo(3598_36L)

        // Zomato
        assertThat(result.rows[4].description).contains("Zomato")
        assertThat(result.rows[4].amountPaise).isEqualTo(1177_48L)
    }

    @Test
    fun parsesFullOneCardStatement() {
        val stream = javaClass.classLoader?.getResourceAsStream("onecard_sample.txt") ?: return
        val text = stream.bufferedReader().readText()
        val result = OneCardPdfParser.parse(text)
        assertThat(result.rows).hasSize(29)
        assertThat(result.errors).isEmpty()
    }
}
