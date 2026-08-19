package com.krtky.financetracker.ui.util

import android.net.Uri
import com.krtky.financetracker.domain.model.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.formatDateTime(): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(this))

fun Long.formatDate(): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(this))

fun Long.formatShortDate(): String =
    SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(this))

fun Long.formatYear(): String =
    SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(this))

fun Long.formatMonthName(): String =
    SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(this))

fun Long.inr(): String = Money(this).formatInr()

/** Compact display like ₹16.5K / ₹1.2L for summary chips. */
fun Long.inrCompact(): String {
    val rupees = this / 100.0
    val abs = kotlin.math.abs(rupees)
    val sign = if (rupees < 0) "-" else ""
    return when {
        abs >= 10_000_000 -> "$sign₹${"%.1f".format(abs / 10_000_000)}Cr"
        abs >= 100_000 -> "$sign₹${"%.1f".format(abs / 100_000)}L"
        abs >= 1_000 -> "$sign₹${"%.1f".format(abs / 1_000)}K"
        else -> Money(this).formatInr()
    }
}

fun mapsUri(latitude: Double, longitude: Double, label: String? = null): Uri {
    val query = buildString {
        append(latitude)
        append(',')
        append(longitude)
        if (!label.isNullOrBlank()) {
            append('(')
            append(label)
            append(')')
        }
    }
    return Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
}
