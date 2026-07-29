package com.krtky.financetracker.ui.viewmodel

import java.util.Calendar

enum class TimeRange { TODAY, WEEK, MONTH, YEAR, ALL, CUSTOM }

fun TimeRange.toMillisRange(
    customFromMillis: Long = 0L,
    customToMillis: Long = System.currentTimeMillis(),
): Pair<Long, Long> {
    if (this == TimeRange.CUSTOM) {
        val start = Calendar.getInstance().apply {
            timeInMillis = customFromMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            timeInMillis = customToMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return minOf(start, end) to maxOf(start, end)
    }
    val cal = Calendar.getInstance()
    val to = cal.timeInMillis + 86_400_000L
    when (this) {
        TimeRange.TODAY -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        TimeRange.WEEK -> cal.add(Calendar.DAY_OF_YEAR, -7)
        TimeRange.MONTH -> cal.add(Calendar.MONTH, -1)
        TimeRange.YEAR -> cal.add(Calendar.YEAR, -1)
        TimeRange.ALL -> cal.add(Calendar.YEAR, -10)
        TimeRange.CUSTOM -> Unit
    }
    return cal.timeInMillis to to
}

/** First millisecond of the current calendar month. */
fun startOfCurrentMonthMillis(): Long =
    Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
