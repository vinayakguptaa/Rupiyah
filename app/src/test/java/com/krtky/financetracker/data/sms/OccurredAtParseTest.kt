package com.krtky.financetracker.data.sms

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId

/**
 * Mirrors [TransactionParser] occurredAt parsing so we can lock formats without
 * spinning up Hilt. Keep in sync with parseTime() there.
 */
class OccurredAtParseTest {

    private fun parseTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .removeSurrounding("\"")
            .replace('\u00a0', ' ')
            .trim()
        runCatching { java.time.Instant.parse(cleaned).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching {
            java.time.OffsetDateTime.parse(cleaned).toInstant().toEpochMilli()
        }.getOrNull()?.let { return it }
        val zone = ZoneId.of("Asia/Kolkata")
        val withSpace = cleaned.replace('T', ' ').trim()
        val slashToDash = withSpace.replace('/', '-')
        val candidates = listOf(cleaned, withSpace, slashToDash).distinct()
        val dateTimePatterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yy HH:mm:ss",
            "dd-MM-yy HH:mm",
            "dd-MMM-yyyy HH:mm:ss",
            "dd-MMM-yyyy HH:mm",
            "dd-MMM-yy HH:mm",
        )
        for (value in candidates) {
            for (pattern in dateTimePatterns) {
                runCatching {
                    val fmt = java.time.format.DateTimeFormatter.ofPattern(
                        pattern,
                        java.util.Locale.ENGLISH,
                    )
                    java.time.LocalDateTime.parse(value, fmt)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli()
                }.getOrNull()?.let { return it }
            }
        }
        val dateOnlyPatterns = listOf(
            "yyyy-MM-dd",
            "dd-MM-yyyy",
            "dd-MM-yy",
            "dd-MMM-yyyy",
            "dd-MMM-yy",
        )
        for (value in candidates) {
            val datePart = value.takeWhile { it != ' ' }.trim()
            for (pattern in dateOnlyPatterns) {
                runCatching {
                    val fmt = java.time.format.DateTimeFormatter.ofPattern(
                        pattern,
                        java.util.Locale.ENGLISH,
                    )
                    java.time.LocalDate.parse(datePart, fmt)
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli()
                }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    @Test
    fun `iso with offset`() {
        val ms = parseTime("2026-09-03T14:32:10+05:30")
        assertThat(ms).isNotNull()
    }

    @Test
    fun `indian sms style day-month-year`() {
        val ms = parseTime("03-09-2026 14:32")
        assertThat(ms).isNotNull()
        val zdt = java.time.Instant.ofEpochMilli(ms!!)
            .atZone(ZoneId.of("Asia/Kolkata"))
        assertThat(zdt.year).isEqualTo(2026)
        assertThat(zdt.monthValue).isEqualTo(9)
        assertThat(zdt.dayOfMonth).isEqualTo(3)
        assertThat(zdt.hour).isEqualTo(14)
    }

    @Test
    fun `slash dates normalize`() {
        assertThat(parseTime("03/09/2026 09:15")).isNotNull()
    }

    @Test
    fun `blank is null`() {
        assertThat(parseTime(null)).isNull()
        assertThat(parseTime("")).isNull()
        assertThat(parseTime("not a date")).isNull()
    }
}
