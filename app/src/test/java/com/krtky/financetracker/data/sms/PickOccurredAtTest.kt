package com.krtky.financetracker.data.sms

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PickOccurredAtTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min).atZone(ist).toInstant().toEpochMilli()

    private val received = at(2026, 10, 9, 14, 30)

    @Test
    fun `older date from the text wins (pasted note, delayed SMS)`() {
        val llm = at(2026, 10, 2, 0, 0)
        assertThat(TransactionParser.pickOccurredAt(received, llm)).isEqualTo(llm)
    }

    @Test
    fun `same day keeps the precise arrival time`() {
        val llmMidnight = at(2026, 10, 9, 0, 0)
        assertThat(TransactionParser.pickOccurredAt(received, llmMidnight)).isEqualTo(received)
    }

    @Test
    fun `future or implausibly old dates are ignored`() {
        assertThat(TransactionParser.pickOccurredAt(received, at(2026, 10, 12, 9, 0))).isEqualTo(received)
        assertThat(TransactionParser.pickOccurredAt(received, at(2024, 1, 1, 9, 0))).isEqualTo(received)
    }

    @Test
    fun `no llm date means arrival time`() {
        assertThat(TransactionParser.pickOccurredAt(received, received)).isEqualTo(received)
    }
}
