package com.krtky.financetracker.ui.util

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import org.junit.Before
import org.junit.Test

class TransactionSortTest {

    private lateinit var txns: List<Transaction>

    @Before
    fun setUp() {
        txns = listOf(
            Transaction(id = "a", type = TransactionType.EXPENSE, amountPaise = 5000L, occurredAt = 3000L, counterparty = "Amazon"),
            Transaction(id = "b", type = TransactionType.EXPENSE, amountPaise = 10000L, occurredAt = 1000L, counterparty = "Swiggy"),
            Transaction(id = "c", type = TransactionType.INCOME, amountPaise = 50000L, occurredAt = 2000L, counterparty = "Salary"),
        )
    }

    @Test
    fun `NEWEST sorts by occurredAt descending`() {
        val sorted = txns.sortedWithOrder(TransactionSortOrder.NEWEST)
        assertThat(sorted.map { it.id }).containsExactly("a", "c", "b").inOrder()
    }

    @Test
    fun `OLDEST sorts by occurredAt ascending`() {
        val sorted = txns.sortedWithOrder(TransactionSortOrder.OLDEST)
        assertThat(sorted.map { it.id }).containsExactly("b", "c", "a").inOrder()
    }

    @Test
    fun `AMOUNT_HIGH sorts by amountPaise descending`() {
        val sorted = txns.sortedWithOrder(TransactionSortOrder.AMOUNT_HIGH)
        assertThat(sorted.map { it.id }).containsExactly("c", "b", "a").inOrder()
    }

    @Test
    fun `AMOUNT_LOW sorts by amountPaise ascending`() {
        val sorted = txns.sortedWithOrder(TransactionSortOrder.AMOUNT_LOW)
        assertThat(sorted.map { it.id }).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `NAME_AZ sorts by counterparty ascending`() {
        val sorted = txns.sortedWithOrder(TransactionSortOrder.NAME_AZ)
        assertThat(sorted.map { it.id }).containsExactly("a", "c", "b").inOrder()
    }

    @Test
    fun `NAME_ZA sorts by counterparty descending`() {
        val sorted = txns.sortedWithOrder(TransactionSortOrder.NAME_ZA)
        assertThat(sorted.map { it.id }).containsExactly("b", "c", "a").inOrder()
    }
}
