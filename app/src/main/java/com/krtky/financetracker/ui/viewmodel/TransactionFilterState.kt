package com.krtky.financetracker.ui.viewmodel

import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.util.TransactionSortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared mutable filter state for transaction list screens.
 * Defaults match previous ViewModel behavior; call sites override where needed.
 */
class TransactionFilterState(
    initialType: TransactionType? = null,
    initialSort: TransactionSortOrder = TransactionSortOrder.NEWEST,
    initialRange: TimeRange = TimeRange.MONTH,
) {
    private val _type = MutableStateFlow(initialType)
    private val _payment = MutableStateFlow<String?>(null)
    private val _categoryId = MutableStateFlow<Long?>(null)
    private val _tabId = MutableStateFlow<Long?>(null)
    private val _sort = MutableStateFlow(initialSort)
    private val _range = MutableStateFlow(initialRange)
    private val _customFrom = MutableStateFlow(startOfCurrentMonthMillis())
    private val _customTo = MutableStateFlow(System.currentTimeMillis())

    val type: StateFlow<TransactionType?> = _type
    val payment: StateFlow<String?> = _payment
    val categoryId: StateFlow<Long?> = _categoryId
    val tabId: StateFlow<Long?> = _tabId
    val sort: StateFlow<TransactionSortOrder> = _sort
    val range: StateFlow<TimeRange> = _range
    val customFrom: StateFlow<Long> = _customFrom
    val customTo: StateFlow<Long> = _customTo

    // Internal flows for combine
    val typeFlow get() = _type
    val paymentFlow get() = _payment
    val categoryIdFlow get() = _categoryId
    val tabIdFlow get() = _tabId
    val sortFlow get() = _sort
    val rangeFlow get() = _range
    val customFromFlow get() = _customFrom
    val customToFlow get() = _customTo

    fun setType(t: TransactionType?) { _type.value = t }
    fun setPayment(p: String?) { _payment.value = p }
    fun setCategory(id: Long?) { _categoryId.value = id }
    fun setTab(id: Long?) { _tabId.value = id }
    fun setSortOrder(order: TransactionSortOrder) { _sort.value = order }
    fun setTimeRange(r: TimeRange) { _range.value = r }
    fun setCustomRange(fromMillis: Long, toMillis: Long) {
        _customFrom.value = fromMillis
        _customTo.value = toMillis
        _range.value = TimeRange.CUSTOM
    }

    fun clear(
        type: TransactionType? = null,
        clearCategory: Boolean = true,
        clearTab: Boolean = true,
        clearQuery: (() -> Unit)? = null,
    ) {
        _type.value = type
        _payment.value = null
        if (clearCategory) _categoryId.value = null
        if (clearTab) _tabId.value = null
        _range.value = TimeRange.MONTH
        clearQuery?.invoke()
    }
}
