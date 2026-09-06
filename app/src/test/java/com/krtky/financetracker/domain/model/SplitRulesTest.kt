package com.krtky.financetracker.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SplitRulesTest {

    private fun part(amount: Long, type: TransactionType, tabId: Long? = null) =
        SplitPart(amountPaise = amount, type = type, tabId = tabId)

    @Test
    fun `empty splits is valid clear`() {
        assertThat(
            SplitRules.validateParts(TransactionType.DEBIT, 10_000L, emptyList()),
        ).isNull()
    }

    @Test
    fun `same-direction debit split is valid`() {
        assertThat(
            SplitRules.validateParts(
                TransactionType.DEBIT,
                10_500_00L,
                listOf(part(10_000_00L, TransactionType.DEBIT), part(5_00_00L, TransactionType.DEBIT)),
            ),
        ).isNull()
    }

    @Test
    fun `mixed credit parent nets correctly`() {
        // Bank Credit 1350 = Credit 3350 + Debit 2000
        assertThat(
            SplitRules.validateParts(
                TransactionType.CREDIT,
                1350_00L,
                listOf(
                    part(3350_00L, TransactionType.CREDIT),
                    part(2000_00L, TransactionType.DEBIT),
                ),
            ),
        ).isNull()
    }

    @Test
    fun `mixed debit parent nets correctly`() {
        // Bank Debit 1000 = Debit 1200 + Credit 200
        assertThat(
            SplitRules.validateParts(
                TransactionType.DEBIT,
                1000_00L,
                listOf(
                    part(1200_00L, TransactionType.DEBIT),
                    part(200_00L, TransactionType.CREDIT),
                ),
            ),
        ).isNull()
    }

    @Test
    fun `mixed mismatch fails`() {
        assertThat(
            SplitRules.validateParts(
                TransactionType.CREDIT,
                1350_00L,
                listOf(
                    part(3000_00L, TransactionType.CREDIT),
                    part(2000_00L, TransactionType.DEBIT),
                ),
            ),
        ).isNotNull()
    }

    @Test
    fun `under sum fails`() {
        assertThat(
            SplitRules.validateParts(
                TransactionType.DEBIT,
                100L,
                listOf(part(40L, TransactionType.DEBIT), part(50L, TransactionType.DEBIT)),
            ),
        ).isNotNull()
    }

    @Test
    fun `zero line fails`() {
        assertThat(
            SplitRules.validateParts(
                TransactionType.DEBIT,
                100L,
                listOf(part(100L, TransactionType.DEBIT), part(0L, TransactionType.DEBIT)),
            ),
        ).isNotNull()
    }

    @Test
    fun `remainingSignedPaise tracks leftover`() {
        val parentType = TransactionType.DEBIT
        val parent = 1000L
        assertThat(
            SplitRules.remainingSignedPaise(
                parentType,
                parent,
                listOf(part(300L, TransactionType.DEBIT), part(200L, TransactionType.DEBIT)),
            ),
        ).isEqualTo(500L)
        assertThat(
            SplitRules.remainingSignedPaise(
                parentType,
                parent,
                listOf(part(1200L, TransactionType.DEBIT), part(200L, TransactionType.CREDIT)),
            ),
        ).isEqualTo(0L)
    }

    @Test
    fun `signedPaise debit positive credit negative`() {
        assertThat(SplitRules.signedPaise(TransactionType.DEBIT, 100L)).isEqualTo(100L)
        assertThat(SplitRules.signedPaise(TransactionType.CREDIT, 100L)).isEqualTo(-100L)
    }
}
