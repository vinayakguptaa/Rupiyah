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
    fun `remainingRatio returns correct ratio`() {
        assertThat(fb(balancePaise = 5_00_00L).remainingRatio()).isEqualTo(0.5f)
    }

    @Test
    fun `remainingRatio clamps to 0-1`() {
        assertThat(fb(balancePaise = -1_00_00L).remainingRatio()).isEqualTo(0f)
        assertThat(fb(balancePaise = 20_00_00L).remainingRatio()).isEqualTo(1f)
    }

    @Test
    fun `spentRatio is inverse of remainingRatio`() {
        assertThat(fb(balancePaise = 3_00_00L).spentRatio()).isWithin(0.001f).of(0.7f)
    }

    @Test
    fun `isOverspent when balance negative`() {
        assertThat(fb(balancePaise = -100L).isOverspent()).isTrue()
    }

    @Test
    fun `settled tab is not overspent`() {
        assertThat(
            FundBalance(
                fund = fund,
                balancePaise = 0L,
                creditedPaise = 10_00_00L,
                debitedPaise = 10_00_00L,
                openingPaise = 0L,
            ).isOverspent(),
        ).isFalse()
        assertThat(
            FundBalance(
                fund = fund,
                balancePaise = 0L,
                creditedPaise = 0L,
                debitedPaise = 0L,
                openingPaise = 0L,
            ).isSettled(),
        ).isTrue()
    }

    @Test
    fun `isOverspent false when they owe you`() {
        assertThat(fb(balancePaise = 3_00_00L).isOverspent()).isFalse()
        assertThat(fb(balancePaise = 3_00_00L).theyOweYou()).isTrue()
    }
}
