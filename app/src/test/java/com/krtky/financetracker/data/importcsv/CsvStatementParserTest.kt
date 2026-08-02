package com.krtky.financetracker.data.importcsv

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.domain.model.TransactionType
import org.junit.Test

class CsvStatementParserTest {

    @Test
    fun `parses debit credit columns`() {
        val csv = """
            Date,Description,Debit,Credit,Ref
            15-01-2025,UPI-ZOMATO-BLR,450.50,,UTR123
            16-01-2025,SALARY ACME,,85000.00,NEFT99
        """.trimIndent()

        val result = CsvStatementParser.parse(csv)
        assertThat(result.rows).hasSize(2)
        assertThat(result.presetName).contains("Debit")
        assertThat(result.rows[0].type).isEqualTo(TransactionType.DEBIT)
        assertThat(result.rows[0].amountPaise).isEqualTo(450_50L)
        assertThat(result.rows[0].externalRef).isEqualTo("UTR123")
        assertThat(result.rows[1].type).isEqualTo(TransactionType.CREDIT)
        assertThat(result.rows[1].amountPaise).isEqualTo(8_500_000L)
    }

    @Test
    fun `parses rupiyah export shape`() {
        val csv = """
            Date,Time,Type,Amount (INR),Name,Counterparty,Category,Fund,Account,Cash vs Digital,Note,Place,Source,Transaction ID
            2025-03-01,10:00:00,DEBIT,120.00,Cafe,Cafe,Food,,Kotak,Digital,lunch,,MANUAL,abc-1
        """.trimIndent()

        val result = CsvStatementParser.parse(csv)
        assertThat(result.rows).hasSize(1)
        assertThat(result.presetName).isEqualTo("Rupiyah export")
        assertThat(result.rows[0].type).isEqualTo(TransactionType.DEBIT)
        assertThat(result.rows[0].amountPaise).isEqualTo(12_000L)
        assertThat(result.rows[0].counterparty).isEqualTo("Cafe")
    }

    @Test
    fun `parses amount and type`() {
        val csv = """
            Txn Date,Narration,Amount,Type
            01/02/2025,Paid to merchant,99.00,DR
            02/02/2025,Received refund,50,CR
        """.trimIndent()

        val result = CsvStatementParser.parse(csv)
        assertThat(result.rows).hasSize(2)
        assertThat(result.rows[0].type).isEqualTo(TransactionType.DEBIT)
        assertThat(result.rows[0].amountPaise).isEqualTo(9900L)
        assertThat(result.rows[1].type).isEqualTo(TransactionType.CREDIT)
    }

    @Test
    fun `parseCsvLine handles quotes and commas`() {
        val cells = CsvStatementParser.parseCsvLine("""15-01-2025,"Food, drinks",100.00,""")
        assertThat(cells[0]).isEqualTo("15-01-2025")
        assertThat(cells[1]).isEqualTo("Food, drinks")
        assertThat(cells[2]).isEqualTo("100.00")
    }

    @Test
    fun `parseMoneyPaise strips currency`() {
        assertThat(CsvStatementParser.parseMoneyPaise("₹1,234.56")).isEqualTo(123_456L)
        assertThat(CsvStatementParser.parseMoneyPaise("Rs. 10")).isEqualTo(1000L)
        assertThat(CsvStatementParser.parseMoneyPaise("")).isNull()
    }

    @Test
    fun `resolveDirection prefers debit credit columns`() {
        val pair = CsvStatementParser.resolveDirectionAndAmount(
            debitPaise = 5000L,
            creditPaise = 0L,
            amountRaw = null,
            typeHint = null,
            description = null,
        )
        assertThat(pair?.first).isEqualTo(TransactionType.DEBIT)
        assertThat(pair?.second).isEqualTo(5000L)
    }
}
