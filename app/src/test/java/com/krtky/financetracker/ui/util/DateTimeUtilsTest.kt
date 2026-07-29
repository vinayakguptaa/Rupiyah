package com.krtky.financetracker.ui.util

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.ui.viewmodel.TimeRange
import org.junit.Test
import java.util.Calendar

class DateTimeUtilsTest {

    @Test
    fun `startOfDayMillis resets time fields`() {
        val result = startOfDayMillis(1_234_567_890_000L)
        val cal = Calendar.getInstance().apply { timeInMillis = result }
        assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(0)
        assertThat(cal.get(Calendar.MINUTE)).isEqualTo(0)
        assertThat(cal.get(Calendar.SECOND)).isEqualTo(0)
        assertThat(cal.get(Calendar.MILLISECOND)).isEqualTo(0)
    }

    @Test
    fun `endOfDayMillis sets end of day`() {
        val result = endOfDayMillis(1_234_567_890_000L)
        val cal = Calendar.getInstance().apply { timeInMillis = result }
        assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(23)
        assertThat(cal.get(Calendar.MINUTE)).isEqualTo(59)
    }

    @Test
    fun `startOfMonthMillis sets first day`() {
        val result = startOfMonthMillis(1_234_567_890_000L)
        val cal = Calendar.getInstance().apply { timeInMillis = result }
        assertThat(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(1)
        assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(0)
    }

    @Test
    fun `timeRangeSubtitle returns correct text`() {
        assertThat(timeRangeSubtitle(TimeRange.TODAY, 0L, 0L)).isEqualTo("Today")
        assertThat(timeRangeSubtitle(TimeRange.WEEK, 0L, 0L)).isEqualTo("This week")
        assertThat(timeRangeSubtitle(TimeRange.MONTH, 0L, 0L)).isEqualTo("This month")
        assertThat(timeRangeSubtitle(TimeRange.YEAR, 0L, 0L)).isEqualTo("This year")
        assertThat(timeRangeSubtitle(TimeRange.ALL, 0L, 0L)).isEqualTo("All time")
    }

    @Test
    fun `timeRangeSubtitle shows custom range`() {
        val result = timeRangeSubtitle(TimeRange.CUSTOM, 1_234_567_890_000L, 1_234_567_890_000L)
        assertThat(result).contains("–")
    }
}
