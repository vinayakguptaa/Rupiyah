package com.krtky.financetracker.ui.util

import com.krtky.financetracker.ui.viewmodel.TimeRange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun startOfDayMillis(millis: Long = System.currentTimeMillis()): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

fun endOfDayMillis(millis: Long = System.currentTimeMillis()): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

fun startOfMonthMillis(millis: Long = System.currentTimeMillis()): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** Subtitle for filtered lists (category/tab detail headers). */
fun timeRangeSubtitle(
    range: TimeRange,
    customFrom: Long,
    customTo: Long,
): String {
    val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
    return when (range) {
        TimeRange.TODAY -> "Today"
        TimeRange.WEEK -> "This week"
        TimeRange.MONTH -> "This month"
        TimeRange.YEAR -> "This year"
        TimeRange.ALL -> "All time"
        TimeRange.CUSTOM -> "${fmt.format(Date(customFrom))} – ${fmt.format(Date(customTo))}"
    }
}

/** Form date pattern used by Add / Edit transaction screens. */
fun formDateFormatter(): SimpleDateFormat =
    SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())

/** Form time pattern used by Add / Edit transaction screens. */
fun formTimeFormatter(): SimpleDateFormat =
    SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
