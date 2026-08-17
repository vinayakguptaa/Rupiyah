package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.navigation.UNASSIGNED_DIGITAL_ACCOUNT_ID
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
class AccountDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {
    private val scopeKey = MutableStateFlow(UNASSIGNED_DIGITAL_ACCOUNT_ID to "Digital (no bank)")
    private val filters = TransactionFilterState()

    val title: StateFlow<String> = scopeKey.map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val typeFilter: StateFlow<TransactionType?> = filters.type
    val categoryFilter: StateFlow<Long?> = filters.categoryId
    val fundFilter: StateFlow<Long?> = filters.fundId
    val sortOrder: StateFlow<TransactionSortOrder> = filters.sort
    val timeRange: StateFlow<TimeRange> = filters.range
    val customFrom: StateFlow<Long> = filters.customFrom
    val customTo: StateFlow<Long> = filters.customTo

    val funds = fundsState(transactionRepository)
    val categories = categoriesState(categoryRepository, transactionRepository)

    private data class Head(
        val key: Pair<Long, String>,
        val t: TransactionType?,
        val cat: Long?,
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
                filters.categoryIdFlow,
                filters.fundIdFlow,
                filters.rangeFlow,
            ) { key, t, cat, fund, r -> Head(key, t, cat, fund, r) },
            combine(
                filters.customFromFlow,
                filters.customToFlow,
                filters.sortFlow,
            ) { from, to, sort -> Tail(from, to, sort) },
        ) { head, tail -> head to tail }
            .flatMapLatest { (head, tail) ->
                val accountId = head.key.first
                val (from, to) = head.range.toMillisRange(tail.from, tail.to)
                val sqlAccount = accountId.takeIf { it > 0L }
                transactionRepository.observeFiltered(
                    query = "",
                    type = head.t,
                    categoryId = head.cat,
                    fundId = head.fund,
                    fromTs = from,
                    toTs = to,
                    accountId = sqlAccount,
                ).map { list ->
                    val scoped = if (accountId == UNASSIGNED_DIGITAL_ACCOUNT_ID) {
                        list.filter {
                            !it.isCash &&
                                it.accountId == null &&
                                !it.accountName.equals("Cash", true)
                        }
                    } else {
                        list
                    }
                    applyPaymentAndSort(scoped, null, tail.sort)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val netPaise: StateFlow<Long> = transactions.map { list ->
        list.sumOf { if (it.type == TransactionType.CREDIT) it.amountPaise else -it.amountPaise }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun load(accountId: Long, accountName: String) {
        scopeKey.value = accountId to accountName
    }

    fun setType(t: TransactionType?) = filters.setType(t)
    fun setCategory(id: Long?) = filters.setCategory(id)
    fun setFund(id: Long?) = filters.setFund(id)
    fun setSortOrder(order: TransactionSortOrder) = filters.setSortOrder(order)
    fun setTimeRange(r: TimeRange) = filters.setTimeRange(r)
    fun setCustomRange(fromMillis: Long, toMillis: Long) = filters.setCustomRange(fromMillis, toMillis)
    fun clearFilters() = filters.clear(type = null, clearCategory = true)
}
