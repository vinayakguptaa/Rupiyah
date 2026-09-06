package com.krtky.financetracker.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TabOnlyBookkeepingTest {

    @Test
    fun `TAB_TRANSFER is tab-only bookkeeping`() {
        assertThat(isTabOnlyBookkeeping(TransactionKind.TAB_TRANSFER.name)).isTrue()
        assertThat(isTabOnlyBookkeeping("tab_transfer")).isTrue()
    }

    @Test
    fun `NORMAL and SELF_TRANSFER are not tab-only`() {
        assertThat(isTabOnlyBookkeeping(TransactionKind.NORMAL.name)).isFalse()
        assertThat(isTabOnlyBookkeeping(TransactionKind.SELF_TRANSFER.name)).isFalse()
        assertThat(isTabOnlyBookkeeping(null)).isFalse()
        assertThat(isTabOnlyBookkeeping("")).isFalse()
    }

    @Test
    fun `unassigned Digital aggregate skips tab-only rows`() {
        // Mirrors AccountRepository.observeUnassignedDigital / Cashflow Digital bucket.
        data class Row(val accountId: Long?, val isCash: Boolean, val kind: String, val type: String, val amount: Long)

        val rows = listOf(
            Row(null, false, "NORMAL", "DEBIT", 100_00L),
            Row(null, false, "TAB_TRANSFER", "CREDIT", 500_00L), // opening / they-paid style
            Row(null, false, "TAB_TRANSFER", "DEBIT", 200_00L),
            Row(null, false, "NORMAL", "CREDIT", 40_00L),
            Row(1L, false, "NORMAL", "DEBIT", 10_00L),
        )
        val digital = rows.filter {
            it.accountId == null && !it.isCash && !isTabOnlyBookkeeping(it.kind)
        }
        val net = digital.sumOf { if (it.type == "CREDIT") it.amount else -it.amount }
        assertThat(digital).hasSize(2)
        assertThat(net).isEqualTo(-100_00L + 40_00L)
    }

    @Test
    fun `they covered lowers open tab balance like a credit`() {
        // Open = debits − credits. After Debit 3350 then They-covered Credit 2000 → +1350.
        var balance = 0L
        fun apply(type: String, amount: Long, kind: String = "NORMAL") {
            if (kind == "SELF_TRANSFER") return
            balance += if (type == "CREDIT") -amount else amount
        }
        apply("DEBIT", 3350_00L)
        apply("CREDIT", 2000_00L, kind = "TAB_TRANSFER") // They covered
        assertThat(balance).isEqualTo(1350_00L)
        apply("CREDIT", 1350_00L) // real settle
        assertThat(balance).isEqualTo(0L)
    }
}
