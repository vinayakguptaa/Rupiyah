package com.krtky.financetracker.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TabBalanceTest {

    private val tab = Tab(id = 1, name = "Test")

    private fun fb(balancePaise: Long, creditedPaise: Long = 0L, debitedPaise: Long = 0L) =
        TabBalance(tab = tab, balancePaise = balancePaise, creditedPaise = creditedPaise, debitedPaise = debitedPaise)

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
            TabBalance(
                tab = tab,
                balancePaise = 0L,
                creditedPaise = 10_00_00L,
                debitedPaise = 10_00_00L,
            ).isSettled(),
        ).isTrue()
    }
}