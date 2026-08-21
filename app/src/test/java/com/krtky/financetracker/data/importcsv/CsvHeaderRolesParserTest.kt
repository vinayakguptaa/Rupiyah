package com.krtky.financetracker.data.importcsv

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CsvHeaderRolesParserTest {

    @Test
    fun `fromJson maps header names to indices`() {
        val headers = listOf("Txn Date", "Narration", "Withdrawal Amt.", "Deposit Amt.", "Balance")
        val json = """
            {"date":"Txn Date","description":"Narration","debit":"Withdrawal Amt.","credit":"Deposit Amt.","amount":null,"balance":"ignored"}
        """.trimIndent()
        val roles = CsvHeaderRolesParser.fromJson(json)!!
        val mapping = CsvHeaderRolesParser.toMapping(headers, roles)
        assertThat(mapping.date).isEqualTo(0)
        assertThat(mapping.description).isEqualTo(1)
        assertThat(mapping.debit).isEqualTo(2)
        assertThat(mapping.credit).isEqualTo(3)
        assertThat(mapping.amount).isNull()
        assertThat(CsvHeaderRolesParser.isUsable(mapping)).isTrue()
    }

    @Test
    fun `custom investment and transfer columns`() {
        val headers = listOf("Date", "Note", "Income", "Expense", "Investment Amount", "Transfer")
        val json = """
            {"date":"Date","description":"Note","credit":"Income","debit":"Expense","investment":"Investment Amount","transfer":"Transfer"}
        """.trimIndent()
        val roles = CsvHeaderRolesParser.fromJson(json)!!
        val mapping = CsvHeaderRolesParser.toMapping(headers, roles)
        assertThat(mapping.credit).isEqualTo(2)
        assertThat(mapping.debit).isEqualTo(3)
        assertThat(mapping.investment).isEqualTo(4)
        assertThat(mapping.transfer).isEqualTo(5)
        assertThat(mapping.amount).isNull()
    }

    @Test
    fun `unknown header names are not usable without date`() {
        val headers = listOf("Foo", "Bar")
        val roles = CsvHeaderRoles(
            date = "Txn Date",
            amount = "Amount",
        )
        val mapping = CsvHeaderRolesParser.toMapping(headers, roles)
        assertThat(CsvHeaderRolesParser.isUsable(mapping)).isFalse()
    }

    @Test
    fun `parse uses injected mapping not heuristics`() {
        val csv = """
            Foo,Bar,Baz,Qux
            15-01-2025,UPI ZOMATO,450.50,
            16-01-2025,SALARY,,85000
        """.trimIndent()
        val mapping = CsvStatementParser.ColumnMapping(
            presetName = "AI column map",
            date = 0,
            description = 1,
            debit = 2,
            credit = 3,
        )
        val result = CsvStatementParser.parse(csv, mapping)
        assertThat(result.presetName).isEqualTo("AI column map")
        assertThat(result.rows).hasSize(2)
        assertThat(result.rows[0].amountPaise).isEqualTo(450_50L)
        assertThat(result.rows[1].amountPaise).isEqualTo(8_500_000L)
    }
}
