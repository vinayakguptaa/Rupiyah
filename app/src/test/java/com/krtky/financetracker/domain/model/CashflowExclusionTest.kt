package com.krtky.financetracker.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Mirrors [com.krtky.financetracker.data.repository.CashflowRepository.homeCashflowSnapshot]
 * exclusion rules without Room: self/tab transfers out; Investment is a normal category.
 */
class CashflowExclusionTest {

    @Test
    fun `self and tab transfers are excluded from cashflow`() {
        assertThat(isExcludedFromCashflow(TransactionKind.SELF_TRANSFER.name)).isTrue()
        assertThat(isExcludedFromCashflow(TransactionKind.TAB_TRANSFER.name)).isTrue()
        assertThat(isExcludedFromCashflow("self_transfer")).isTrue()
        assertThat(isExcludedFromCashflow("tab_transfer")).isTrue()
    }

    @Test
    fun `normal rows are included in cashflow`() {
        assertThat(isExcludedFromCashflow(TransactionKind.NORMAL.name)).isFalse()
        assertThat(isExcludedFromCashflow(null)).isFalse()
        assertThat(isExcludedFromCashflow("")).isFalse()
    }

    @Test
    fun `snapshot totals exclude transfers and treat Investment as normal spend`() {
        data class Row(
            val type: String,
            val amount: Long,
            val kind: String,
            val categoryName: String,
        )

        val investment = "Investment"
        val food = "Food"
        val rows = listOf(
            Row("DEBIT", 500_00L, "NORMAL", food),
            Row("DEBIT", 2_000_00L, "NORMAL", investment),
            Row("CREDIT", 800_00L, "NORMAL", "Salary"),
            Row("DEBIT", 300_00L, "SELF_TRANSFER", "Transfer"),
            Row("CREDIT", 300_00L, "SELF_TRANSFER", "Transfer"),
            Row("DEBIT", 100_00L, "TAB_TRANSFER", "Settlement"),
            Row("CREDIT", 1_000_00L, "NORMAL", investment),
        )

        val included = rows.filter { !isExcludedFromCashflow(it.kind) }
        val expense = included.filter { it.type == "DEBIT" }.sumOf { it.amount }
        val income = included.filter { it.type == "CREDIT" }.sumOf { it.amount }
        val debitByCat = included
            .filter { it.type == "DEBIT" }
            .groupBy { it.categoryName }
            .mapValues { (_, g) -> g.sumOf { it.amount } }

        assertThat(included).hasSize(4)
        assertThat(expense).isEqualTo(500_00L + 2_000_00L)
        assertThat(income).isEqualTo(800_00L + 1_000_00L)
        assertThat(debitByCat[investment]).isEqualTo(2_000_00L)
        assertThat(debitByCat[food]).isEqualTo(500_00L)
        assertThat(debitByCat).doesNotContainKey("Transfer")
        assertThat(debitByCat).doesNotContainKey("Settlement")
    }

    @Test
    fun `category net spend offsets debits with credits`() {
        val foodNet = CategoryNetSpend(
            categoryId = 1L,
            categoryName = "Food",
            debitPaise = 3_000_00L,
            creditPaise = 2_000_00L,
        )
        // Net is credit - debit = -1,000.00 (net spend of 1000)
        assertThat(foodNet.netPaise).isEqualTo(-1_000_00L)

        val salaryNet = CategoryNetSpend(
            categoryId = 2L,
            categoryName = "Salary",
            debitPaise = 0L,
            creditPaise = 50_000_00L,
        )
        assertThat(salaryNet.netPaise).isEqualTo(50_000_00L)

        val refundNet = CategoryNetSpend(
            categoryId = 3L,
            categoryName = "Shopping",
            debitPaise = 2_000_00L,
            creditPaise = 2_500_00L,
        )
        // More refunds than spends
        assertThat(refundNet.netPaise).isEqualTo(500_00L)
    }
}
