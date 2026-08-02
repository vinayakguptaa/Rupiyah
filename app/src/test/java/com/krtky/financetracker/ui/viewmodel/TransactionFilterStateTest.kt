package com.krtky.financetracker.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.util.TransactionSortOrder
import org.junit.Before
import org.junit.Test

class TransactionFilterStateTest {

    private lateinit var state: TransactionFilterState

    @Before
    fun setUp() {
        state = TransactionFilterState()
    }

    @Test
    fun `default values are set`() {
        assertThat(state.type.value).isNull()
        assertThat(state.payment.value).isNull()
        assertThat(state.categoryId.value).isNull()
        assertThat(state.fundId.value).isNull()
        assertThat(state.sort.value).isEqualTo(TransactionSortOrder.NEWEST)
        assertThat(state.range.value).isEqualTo(TimeRange.MONTH)
    }

    @Test
    fun `setType updates type`() {
        state.setType(TransactionType.CREDIT)
        assertThat(state.type.value).isEqualTo(TransactionType.CREDIT)
    }

    @Test
    fun `setPayment updates payment`() {
        state.setPayment("HDFC")
        assertThat(state.payment.value).isEqualTo("HDFC")
    }

    @Test
    fun `setCategory updates category`() {
        state.setCategory(5L)
        assertThat(state.categoryId.value).isEqualTo(5L)
    }

    @Test
    fun `setFund updates fund`() {
        state.setFund(3L)
        assertThat(state.fundId.value).isEqualTo(3L)
    }

    @Test
    fun `setSortOrder updates sort`() {
        state.setSortOrder(TransactionSortOrder.AMOUNT_LOW)
        assertThat(state.sort.value).isEqualTo(TransactionSortOrder.AMOUNT_LOW)
    }

    @Test
    fun `setTimeRange updates range`() {
        state.setTimeRange(TimeRange.YEAR)
        assertThat(state.range.value).isEqualTo(TimeRange.YEAR)
    }

    @Test
    fun `setCustomRange sets range to CUSTOM`() {
        state.setCustomRange(1000L, 2000L)
        assertThat(state.range.value).isEqualTo(TimeRange.CUSTOM)
        assertThat(state.customFrom.value).isEqualTo(1000L)
        assertThat(state.customTo.value).isEqualTo(2000L)
    }

    @Test
    fun `clear resets filters`() {
        state.setType(TransactionType.DEBIT)
        state.setPayment("ICICI")
        state.setCategory(1L)
        state.setFund(2L)
        var queryCleared = false
        state.clear(type = null, clearQuery = { queryCleared = true })
        assertThat(state.type.value).isNull()
        assertThat(state.payment.value).isNull()
        assertThat(state.categoryId.value).isNull()
        assertThat(state.fundId.value).isNull()
        assertThat(state.range.value).isEqualTo(TimeRange.MONTH)
        assertThat(queryCleared).isTrue()
    }
}
