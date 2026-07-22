package com.krtky.financetracker.ui.util

import android.net.Uri
import com.krtky.financetracker.domain.model.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.formatDateTime(): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(this))

fun Long.formatDate(): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(this))

fun Long.inr(): String = Money(this).formatInr()

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
