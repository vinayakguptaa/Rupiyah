package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.util.TransactionSortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
) : ViewModel() {
    private val scopeKey = MutableStateFlow<Pair<Long?, String>>(null to "")
    private val filters = TransactionFilterState(initialType = TransactionType.DEBIT)

    val title: StateFlow<String> = scopeKey.map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val typeFilter: StateFlow<TransactionType?> = filters.type
    val paymentFilter: StateFlow<String?> = filters.payment
    val tabFilter: StateFlow<Long?> = filters.tabId
    val sortOrder: StateFlow<TransactionSortOrder> = filters.sort
    val timeRange: StateFlow<TimeRange> = filters.range
    val customFrom: StateFlow<Long> = filters.customFrom
    val customTo: StateFlow<Long> = filters.customTo

    val tabs = tabsState(transactionRepository)
    val bankAccounts = filterAccountNamesState(accountRepository, transactionRepository)

    private data class Head(
        val key: Pair<Long?, String>,
        val t: TransactionType?,
        val pay: String?,
        val tab: Long?,
        val range: TimeRange,
    )
    private data class Tail(val from: Long, val to: Long, val sort: TransactionSortOrder)

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<Transaction>> =
        combine(
            combine(
                scopeKey,
                filters.typeFlow,
                filters.paymentFlow,
                filters.tabIdFlow,
                filters.rangeFlow,
            ) { key, t, pay, tab, r -> Head(key, t, pay, tab, r) },
            combine(
                filters.customFromFlow,
                filters.customToFlow,
                filters.sortFlow,
            ) { from, to, sort -> Tail(from, to, sort) },
        ) { head, tail -> head to tail }
            .flatMapLatest { (head, tail) ->
                val (categoryId, _) = head.key
                val (from, to) = head.range.toMillisRange(tail.from, tail.to)
                // observeForTab: null categoryId = any category. Uncategorized uses observeForCategory.
                val base = when {
                    categoryId != null && head.tab != null ->
                        transactionRepository.observeForTab(
                            tabId = head.tab,
                            type = head.t,
                            categoryId = categoryId,
                            fromTs = from,
                            toTs = to,
                        )
                    categoryId != null ->
                        transactionRepository.observeForCategory(
                            categoryId = categoryId,
                            type = head.t,
                            fromTs = from,
                            toTs = to,
                        )
                    head.tab != null ->
                        // Uncategorized on a tab: allocation amounts with null category on that tab.
                        transactionRepository.observeForCategory(
                            categoryId = null,
                            type = head.t,
                            fromTs = from,
                            toTs = to,
                        ).map { list -> list.filter { it.tabId == head.tab } }
                    else ->
                        transactionRepository.observeForCategory(
                            categoryId = null,
                            type = head.t,
                            fromTs = from,
                            toTs = to,
                        )
                }
                base.map { applyPaymentAndSort(it, head.pay, tail.sort) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalPaise: StateFlow<Long> = transactions.map { list ->
        list.sumOf { it.amountPaise }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun load(
        categoryId: Long?,
        categoryName: String,
        type: TransactionType = TransactionType.DEBIT,
    ) {
        filters.setType(type)
        scopeKey.value = categoryId to categoryName
    }

    fun setType(t: TransactionType?) = filters.setType(t)
    fun setPayment(p: String?) = filters.setPayment(p)
    fun setTab(id: Long?) = filters.setTab(id)
    fun setSortOrder(order: TransactionSortOrder) = filters.setSortOrder(order)
    fun setTimeRange(r: TimeRange) = filters.setTimeRange(r)
    fun setCustomRange(fromMillis: Long, toMillis: Long) = filters.setCustomRange(fromMillis, toMillis)
    fun clearFilters() = filters.clear(type = TransactionType.DEBIT, clearCategory = false)
}
