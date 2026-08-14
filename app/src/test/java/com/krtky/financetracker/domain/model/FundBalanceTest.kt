package com.krtky.financetracker.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FundBalanceTest {

    private val fund = Fund(id = 1, name = "Test", budgetPaise = 10_00_00L)

    private fun fb(balancePaise: Long, creditedPaise: Long = 0L, debitedPaise: Long = 0L, openingPaise: Long = 10_00_00L) =
        FundBalance(fund = fund, balancePaise = balancePaise, creditedPaise = creditedPaise, debitedPaise = debitedPaise, openingPaise = openingPaise)

    @Test
    fun `limitPaise uses budgetPaise when set`() {
        assertThat(fb(balancePaise = 5_00_00L, openingPaise = 10_00_00L).limitPaise()).isEqualTo(10_00_00L)
    }

    @Test
    fun `limitPaise falls back to openingPaise when budgetPaise is zero`() {
        val f = fund.copy(budgetPaise = 0L)
        val fbObj = FundBalance(fund = f, balancePaise = 5_00_00L, creditedPaise = 0L, debitedPaise = 0L, openingPaise = 10_00_00L)
        assertThat(fbObj.limitPaise()).isEqualTo(10_00_00L)
    }

    @Test
    fun `limitPaise falls back to balance when both budget and opening are zero`() {
        val f = fund.copy(budgetPaise = 0L)
        val fbObj = FundBalance(fund = f, balancePaise = 3_00_00L, creditedPaise = 0L, debitedPaise = 0L, openingPaise = 0L)
        assertThat(fbObj.limitPaise()).isEqualTo(3_00_00L)
    }

    @Test
    fun `they owe you when positive`() {
        assertThat(fb(balancePaise = 3_00_00L).theyOweYou()).isTrue()
        assertThat(fb(balancePaise = 3_00_00L).youOweThem()).isFalse()
    }

    @Test
    fun `you owe them when negative`() {
        assertThat(fb(balancePaise = -100L).youOweThem()).isTrue()
        assertThat(fb(balancePaise = -100L).theyOweYou()).isFalse()
    }

    @Test
    fun `settled tab is neither owed nor owing`() {
        assertThat(
            FundBalance(
                fund = fund,
                balancePaise = 0L,
                creditedPaise = 10_00_00L,
                debitedPaise = 10_00_00L,
                openingPaise = 0L,
            ).isSettled(),
        ).isTrue()
    }
}
