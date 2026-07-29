package com.krtky.financetracker.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class TimeRangeTest {

    @Test
    fun `toMillisRange TODAY returns same day range`() {
        val now = System.currentTimeMillis()
        val (from, to) = TimeRange.TODAY.toMillisRange()
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(0)
        assertThat(cal.get(Calendar.MINUTE)).isEqualTo(0)
        assertThat(to).isGreaterThan(from)
    }

    @Test
    fun `toMillisRange WEEK returns 7 day range`() {
        val now = System.currentTimeMillis()
        val (from, to) = TimeRange.WEEK.toMillisRange()
        assertThat(to - from).isAtLeast(6 * 86_400_000L)
    }

    @Test
    fun `toMillisRange MONTH returns ~30 day range`() {
        val (from, to) = TimeRange.MONTH.toMillisRange()
        assertThat(to - from).isAtLeast(28 * 86_400_000L)
    }

    @Test
    fun `toMillisRange YEAR returns ~365 day range`() {
        val (from, to) = TimeRange.YEAR.toMillisRange()
        assertThat(to - from).isAtLeast(364 * 86_400_000L)
    }

    @Test
    fun `toMillisRange ALL returns 10 year range`() {
        val (from, to) = TimeRange.ALL.toMillisRange()
        assertThat(to - from).isAtLeast(10 * 364 * 86_400_000L)
    }

    @Test
    fun `toMillisRange CUSTOM uses provided millis`() {
        val customFrom = 1_000_000_000L
        val customTo = 2_000_000_000L
        val (from, to) = TimeRange.CUSTOM.toMillisRange(customFrom, customTo)
        assertThat(from).isAtMost(to)
    }

    @Test
    fun `toMillisRange CUSTOM normalizes reversed inputs`() {
        val (from, to) = TimeRange.CUSTOM.toMillisRange(2_000_000_000L, 1_000_000_000L)
        assertThat(from).isAtMost(to)
    }
}
