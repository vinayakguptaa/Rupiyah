package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.prefs.UserPreferences
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
    userPreferences: UserPreferences,
) : ViewModel() {
    private val scopeKey = MutableStateFlow<Pair<Long?, String>>(null to "")
    private val filters = TransactionFilterState(initialType = TransactionType.EXPENSE)

    val title: StateFlow<String> = scopeKey.map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val typeFilter: StateFlow<TransactionType?> = filters.type
    val paymentFilter: StateFlow<String?> = filters.payment
    val fundFilter: StateFlow<Long?> = filters.fundId
    val sortOrder: StateFlow<TransactionSortOrder> = filters.sort
    val timeRange: StateFlow<TimeRange> = filters.range
    val customFrom: StateFlow<Long> = filters.customFrom
    val customTo: StateFlow<Long> = filters.customTo

    val funds = fundsState(transactionRepository)
    val bankAccounts = bankAccountsState(userPreferences, transactionRepository, includeUsageExtras = true)

    private data class Head(
        val key: Pair<Long?, String>,
        val t: TransactionType?,
        val pay: String?,
        val fund: Long?,
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
                filters.fundIdFlow,
                filters.rangeFlow,
            ) { key, t, pay, fund, r -> Head(key, t, pay, fund, r) },
            combine(
                filters.customFromFlow,
                filters.customToFlow,
                filters.sortFlow,
            ) { from, to, sort -> Tail(from, to, sort) },
        ) { head, tail -> head to tail }
            .flatMapLatest { (head, tail) ->
                val (categoryId, _) = head.key
                val (from, to) = head.range.toMillisRange(tail.from, tail.to)
                val base = if (categoryId != null) {
                    transactionRepository.observeFiltered(
                        query = "",
                        type = head.t,
                        categoryId = categoryId,
                        fundId = head.fund,
                        fromTs = from,
                        toTs = to,
                    )
                } else {
                    transactionRepository.observeFiltered(
                        query = "",
                        type = head.t,
                        categoryId = null,
                        fundId = head.fund,
                        fromTs = from,
                        toTs = to,
                    ).map { list -> list.filter { txn -> txn.categoryId == null } }
                }
                base.map { applyPaymentAndSort(it, head.pay, tail.sort) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalPaise: StateFlow<Long> = transactions.map { list ->
        list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPaise }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun load(categoryId: Long?, categoryName: String) {
        scopeKey.value = categoryId to categoryName
    }

    fun setType(t: TransactionType?) = filters.setType(t)
    fun setPayment(p: String?) = filters.setPayment(p)
    fun setFund(id: Long?) = filters.setFund(id)
    fun setSortOrder(order: TransactionSortOrder) = filters.setSortOrder(order)
    fun setTimeRange(r: TimeRange) = filters.setTimeRange(r)
    fun setCustomRange(fromMillis: Long, toMillis: Long) = filters.setCustomRange(fromMillis, toMillis)
    fun clearFilters() = filters.clear(type = TransactionType.EXPENSE, clearCategory = false)
}
