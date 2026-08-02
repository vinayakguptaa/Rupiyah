package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.FundBalance
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.util.TransactionSortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FundDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
) : ViewModel() {
    private val fundIdFlow = MutableStateFlow<Long?>(null)
    private val _fund = MutableStateFlow<FundBalance?>(null)
    private val filters = TransactionFilterState()

    val fund: StateFlow<FundBalance?> = _fund
    val allFunds = fundsState(transactionRepository)
    val typeFilter: StateFlow<TransactionType?> = filters.type
    val paymentFilter: StateFlow<String?> = filters.payment
    val categoryFilter: StateFlow<Long?> = filters.categoryId
    val sortOrder: StateFlow<TransactionSortOrder> = filters.sort
    val timeRange: StateFlow<TimeRange> = filters.range
    val customFrom: StateFlow<Long> = filters.customFrom
    val customTo: StateFlow<Long> = filters.customTo

    val categories = categoriesState(categoryRepository, transactionRepository)
    val bankAccounts = filterAccountNamesState(accountRepository, transactionRepository)

    private data class Head(
        val id: Long?,
        val t: TransactionType?,
        val pay: String?,
        val cat: Long?,
        val range: TimeRange,
    )
    private data class Tail(val from: Long, val to: Long, val sort: TransactionSortOrder)

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<Transaction>> =
        combine(
            combine(
                fundIdFlow,
                filters.typeFlow,
                filters.paymentFlow,
                filters.categoryIdFlow,
                filters.rangeFlow,
            ) { id, t, pay, cat, r -> Head(id, t, pay, cat, r) },
            combine(
                filters.customFromFlow,
                filters.customToFlow,
                filters.sortFlow,
            ) { from, to, sort -> Tail(from, to, sort) },
        ) { head, tail -> head to tail }
            .flatMapLatest { (head, tail) ->
                val id = head.id
                if (id == null) flowOf(emptyList())
                else {
                    val (from, to) = head.range.toMillisRange(tail.from, tail.to)
                    transactionRepository.observeForTab(id, head.t, head.cat, from, to)
                        .map { applyPaymentAndSort(it, head.pay, tail.sort) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(id: Long) {
        fundIdFlow.value = id
        viewModelScope.launch {
            _fund.value = transactionRepository.observeFunds().first().firstOrNull { it.fund.id == id }
        }
    }

    fun setType(t: TransactionType?) = filters.setType(t)
    fun setPayment(p: String?) = filters.setPayment(p)
    fun setCategory(id: Long?) = filters.setCategory(id)
    fun setSortOrder(order: TransactionSortOrder) = filters.setSortOrder(order)
    fun setTimeRange(r: TimeRange) = filters.setTimeRange(r)
    fun setCustomRange(fromMillis: Long, toMillis: Long) = filters.setCustomRange(fromMillis, toMillis)
    fun clearFilters() = filters.clear(type = null, clearFund = false)

    suspend fun deleteFund(): Boolean {
        val id = fundIdFlow.value ?: return false
        transactionRepository.deleteFund(id)
        return true
    }

    suspend fun transferBetweenFunds(
        fromFundId: Long,
        toFundId: Long,
        amountPaise: Long,
        note: String,
    ): Boolean {
        transactionRepository.transferBetweenFunds(fromFundId, toFundId, amountPaise, note.ifBlank { null })
        return true
    }
}
