package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.CategoryRepository
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    private val filters = TransactionFilterState()

    val query: StateFlow<String> = _query
    val typeFilter: StateFlow<TransactionType?> = filters.type
    val paymentFilter: StateFlow<String?> = filters.payment
    val categoryFilter: StateFlow<Long?> = filters.categoryId
    val fundFilter: StateFlow<Long?> = filters.fundId
    val sortOrder: StateFlow<TransactionSortOrder> = filters.sort
    val timeRange: StateFlow<TimeRange> = filters.range
    val customFrom: StateFlow<Long> = filters.customFrom
    val customTo: StateFlow<Long> = filters.customTo

    val categories = categoriesState(categoryRepository, transactionRepository)
    val funds = fundsState(transactionRepository)
    /** Active + archived account names for filters (history on old banks stays findable). */
    val bankAccounts = filterAccountNamesState(accountRepository, transactionRepository)

    private data class Head(
        val q: String,
        val t: TransactionType?,
        val pay: String?,
        val cat: Long?,
        val fund: Long?,
    )
    private data class Tail(
        val range: TimeRange,
        val from: Long,
        val to: Long,
        val sort: TransactionSortOrder,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<Transaction>> =
        combine(
            combine(
                _query,
                filters.typeFlow,
                filters.paymentFlow,
                filters.categoryIdFlow,
                filters.fundIdFlow,
            ) { q, t, pay, cat, fund -> Head(q, t, pay, cat, fund) },
            combine(
                filters.rangeFlow,
                filters.customFromFlow,
                filters.customToFlow,
                filters.sortFlow,
            ) { r, from, to, sort -> Tail(r, from, to, sort) },
        ) { head, tail -> head to tail }
            .flatMapLatest { (head, tail) ->
                val (from, to) = tail.range.toMillisRange(tail.from, tail.to)
                transactionRepository.observeFiltered(head.q, head.t, head.cat, head.fund, from, to)
                    .map { applyPaymentAndSort(it, head.pay, tail.sort) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { _query.value = q }
    fun setType(t: TransactionType?) = filters.setType(t)
    fun setPayment(p: String?) = filters.setPayment(p)
    fun setCategory(id: Long?) = filters.setCategory(id)
    fun setFund(id: Long?) = filters.setFund(id)
    fun setSortOrder(order: TransactionSortOrder) = filters.setSortOrder(order)
    fun setTimeRange(r: TimeRange) = filters.setTimeRange(r)
    fun setCustomRange(fromMillis: Long, toMillis: Long) = filters.setCustomRange(fromMillis, toMillis)
    fun clearFilters() = filters.clear(type = null, clearQuery = { _query.value = "" })
    fun delete(ids: Set<String>) = viewModelScope.launch {
        ids.forEach { transactionRepository.delete(it) }
    }

    suspend fun merge(ids: Set<String>): String? =
        transactionRepository.mergeTransactions(ids)
}
