package com.krtky.financetracker.data.repository

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.domain.model.TransactionType
import org.junit.Test
import java.util.Calendar

class TransactionRepositoryTest {

    @Test
    fun `contentHash is deterministic`() {
        val hash1 = TransactionRepository.contentHash(
            TransactionType.DEBIT, 5_00_00L, 1_000_000L, "Swiggy", "ref123", "extra"
        )
        val hash2 = TransactionRepository.contentHash(
            TransactionType.DEBIT, 5_00_00L, 1_000_000L, "Swiggy", "ref123", "extra"
        )
        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `contentHash changes with different input`() {
        val hash1 = TransactionRepository.contentHash(
            TransactionType.DEBIT, 5_00_00L, 1_000_000L, "Swiggy", "ref123", "extra"
        )
        val hash2 = TransactionRepository.contentHash(
            TransactionType.CREDIT, 5_00_00L, 1_000_000L, "Swiggy", "ref123", "extra"
        )
        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `contentHash returns hex string of correct length`() {
        val hash = TransactionRepository.contentHash(
            TransactionType.DEBIT, 100L, System.currentTimeMillis(), null, null, null
        )
        assertThat(hash).matches("^[a-f0-9]{64}$")
    }

    @Test
    fun `monthBounds returns start and end of current month`() {
        val now = System.currentTimeMillis()
        val (from, to) = CashflowRepository.monthBounds(now)
        assertThat(from).isLessThan(to)

        val cal = Calendar.getInstance().apply { timeInMillis = from }
        assertThat(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(1)
        assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(0)
        assertThat(cal.get(Calendar.MINUTE)).isEqualTo(0)

        val calTo = Calendar.getInstance().apply { timeInMillis = to }
        assertThat(calTo.get(Calendar.DAY_OF_MONTH)).isAtLeast(28)
        assertThat(calTo.get(Calendar.HOUR_OF_DAY)).isEqualTo(23)
        assertThat(calTo.get(Calendar.MINUTE)).isEqualTo(59)
    }

    @Test
    fun `monthBounds for specific timestamp`() {
        // March 15, 2025
        val cal = Calendar.getInstance().apply {
            set(2025, Calendar.MARCH, 15, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val (from, to) = CashflowRepository.monthBounds(cal.timeInMillis)

        val fromCal = Calendar.getInstance().apply { timeInMillis = from }
        assertThat(fromCal.get(Calendar.MONTH)).isEqualTo(Calendar.MARCH)
        assertThat(fromCal.get(Calendar.DAY_OF_MONTH)).isEqualTo(1)

        val toCal = Calendar.getInstance().apply { timeInMillis = to }
        assertThat(toCal.get(Calendar.MONTH)).isEqualTo(Calendar.MARCH)
        assertThat(toCal.get(Calendar.DAY_OF_MONTH)).isEqualTo(31)
    }
}
