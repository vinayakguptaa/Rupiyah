package com.krtky.financetracker.data.repository

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.data.local.db.TransactionEntity
import com.krtky.financetracker.domain.model.TransactionType
import org.junit.Test

/**
 * Pure unit coverage for split-part allocation expansion and report aggregation.
 * Splits are parent-replacement rows sharing a splitGroupId; the parent itself is
 * soft-deleted, so only the child rows contribute to reports.
 */
class EffectiveAllocationTest {

    @Test
    fun `unsplit parent becomes single allocation`() {
        val parent = sampleTxn(id = "p1", amount = 500_00L, type = "DEBIT", categoryId = 1L)
        val allocs = expand(listOf(parent))
        assertThat(allocs).hasSize(1)
        assertThat(allocs[0].amountPaise).isEqualTo(500_00L)
        assertThat(allocs[0].isSplit).isFalse()
        assertThat(allocs[0].categoryId).isEqualTo(1L)
    }

    @Test
    fun `split children replace soft-deleted parent`() {
        val parent = sampleTxn(
            id = "p1", amount = 1050_00L, type = "CREDIT", categoryId = null, deletedAt = 2_000L,
        )
        val children = listOf(
            sampleChild(id = "s1", group = "p1", amount = 1000_00L, type = "CREDIT", categoryId = 10L, counterparty = "FD"),
            sampleChild(id = "s2", group = "p1", amount = 50_00L, type = "CREDIT", categoryId = 11L, counterparty = "Bank"),
        )
        val allocs = expand(listOf(parent) + children)
        assertThat(allocs).hasSize(2)
        assertThat(allocs.sumOf { it.amountPaise }).isEqualTo(1050_00L)
        assertThat(allocs.all { it.isSplit }).isTrue()
        assertThat(allocs.map { it.categoryId }).containsExactly(10L, 11L)
    }

    @Test
    fun `self transfer parents produce no allocations`() {
        val parent = sampleTxn(
            id = "st",
            amount = 100_00L,
            type = "DEBIT",
            kind = "SELF_TRANSFER",
        )
        val allocs = expand(listOf(parent))
        assertThat(allocs).isEmpty()
    }

    @Test
    fun `tab transfer parents produce no allocations`() {
        val parent = sampleTxn(
            id = "tt",
            amount = 50_00L,
            type = "CREDIT",
            kind = "TAB_TRANSFER",
        )
        val allocs = expand(listOf(parent))
        assertThat(allocs).isEmpty()
    }

    @Test
    fun `lifestyle excludes investment split lines`() {
        val parent = sampleTxn(id = "p1", amount = 1050_00L, type = "CREDIT", deletedAt = 2_000L)
        val children = listOf(
            sampleChild(id = "s1", group = "p1", amount = 1000_00L, type = "CREDIT", categoryId = 99L, counterparty = "Zerodha"),
            sampleChild(id = "s2", group = "p1", amount = 50_00L, type = "CREDIT", categoryId = 5L, counterparty = "SBI"),
        )
        val allocs = expand(listOf(parent) + children)
        val investmentIds = setOf(99L)
        val lifestyleDebits = allocs.filter {
            it.type == TransactionType.DEBIT &&
                (it.categoryId == null || it.categoryId !in investmentIds)
        }
        // credit parent — lifestyle debits empty
        assertThat(lifestyleDebits).isEmpty()

        val debitParent = sampleTxn(id = "d1", amount = 200_00L, type = "DEBIT", deletedAt = 2_000L)
        val debitChildren = listOf(
            sampleChild(id = "a", group = "d1", amount = 150_00L, categoryId = 1L),
            sampleChild(id = "b", group = "d1", amount = 50_00L, categoryId = 99L),
        )
        val debitAllocs = expand(listOf(debitParent) + debitChildren)
        val lifestyle = debitAllocs.filter {
            it.type == TransactionType.DEBIT &&
                (it.categoryId == null || it.categoryId !in investmentIds)
        }
        assertThat(lifestyle.sumOf { it.amountPaise }).isEqualTo(150_00L)
        val invested = debitAllocs.filter {
            it.type == TransactionType.DEBIT && it.categoryId in investmentIds
        }
        assertThat(invested.sumOf { it.amountPaise }).isEqualTo(50_00L)
    }

    @Test
    fun `lifestyle credits exclude investment redemptions`() {
        val investmentIds = setOf(99L)
        val parent = sampleTxn(id = "c1", amount = 1100_00L, type = "CREDIT", deletedAt = 2_000L)
        val children = listOf(
            sampleChild(id = "s1", group = "c1", amount = 1000_00L, type = "CREDIT", categoryId = 99L, counterparty = "Zerodha"),
            sampleChild(id = "s2", group = "c1", amount = 100_00L, type = "CREDIT", categoryId = 5L, counterparty = "Salary"),
        )
        val allocs = expand(listOf(parent) + children)
        // Mirrors TransactionRepository.cashflowMetricsFromAllocations credit filter
        val credits = allocs.filter {
            it.type == TransactionType.CREDIT &&
                (it.categoryId == null || it.categoryId !in investmentIds)
        }
        val redeemed = allocs.filter {
            it.type == TransactionType.CREDIT && it.categoryId in investmentIds
        }
        assertThat(credits.sumOf { it.amountPaise }).isEqualTo(100_00L)
        assertThat(redeemed.sumOf { it.amountPaise }).isEqualTo(1000_00L)
    }

    @Test
    fun `deleted split children are excluded`() {
        val parent = sampleTxn(id = "p1", amount = 100_00L, type = "DEBIT", deletedAt = 2_000L)
        val children = listOf(
            sampleChild(id = "s1", group = "p1", amount = 60_00L, categoryId = 1L),
            sampleChild(id = "s2", group = "p1", amount = 40_00L, categoryId = 2L, deletedAt = 3_000L),
        )
        val allocs = expand(listOf(parent) + children)
        assertThat(allocs).hasSize(1)
        assertThat(allocs[0].amountPaise).isEqualTo(60_00L)
    }

    private data class Alloc(
        val amountPaise: Long,
        val categoryId: Long?,
        val type: TransactionType,
        val isSplit: Boolean,
        val counterparty: String?,
    )

    private fun expand(entities: List<TransactionEntity>): List<Alloc> {
        val out = mutableListOf<Alloc>()
        for (e in entities) {
            if (e.deletedAt != null) continue
            if (e.kind == "SELF_TRANSFER" || e.kind == "TAB_TRANSFER") continue
            val type = when (e.type.uppercase()) {
                "CREDIT", "INCOME" -> TransactionType.CREDIT
                else -> TransactionType.DEBIT
            }
            out.add(
                Alloc(
                    amountPaise = e.amountPaise,
                    categoryId = e.categoryId,
                    type = type,
                    isSplit = e.splitGroupId != null,
                    counterparty = e.counterparty,
                ),
            )
        }
        return out
    }

    private fun sampleChild(
        id: String,
        group: String,
        amount: Long,
        categoryId: Long? = null,
        type: String = "DEBIT",
        counterparty: String? = null,
        deletedAt: Long? = null,
    ) = TransactionEntity(
        id = id,
        type = type,
        amountPaise = amount,
        occurredAt = 1_000L,
        recordedAt = 1_000L,
        categoryId = categoryId,
        counterparty = counterparty,
        source = "MANUAL",
        kind = "NORMAL",
        splitGroupId = group,
        updatedAt = 1_000L,
        deletedAt = deletedAt,
    )

    private fun sampleTxn(
        id: String,
        amount: Long,
        type: String,
        categoryId: Long? = null,
        kind: String = "NORMAL",
        deletedAt: Long? = null,
    ) = TransactionEntity(
        id = id,
        type = type,
        amountPaise = amount,
        occurredAt = 1_000L,
        recordedAt = 1_000L,
        categoryId = categoryId,
        source = "MANUAL",
        kind = kind,
        updatedAt = 1_000L,
        deletedAt = deletedAt,
    )
}
