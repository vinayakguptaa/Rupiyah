package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.SplitPart
import com.krtky.financetracker.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplitTransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {
    private val txnIdFlow = MutableStateFlow<String?>(null)
    private val _txn = MutableStateFlow<Transaction?>(null)
    val transaction: StateFlow<Transaction?> = _txn

    val categories = categoriesState(categoryRepository, transactionRepository)
    val funds = fundsState(transactionRepository)

    @OptIn(ExperimentalCoroutinesApi::class)
    val splits: StateFlow<List<SplitPart>> = txnIdFlow
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptyList())
            else transactionRepository.observeSplitGroup(id).map { parts ->
                parts.map { SplitPart(it.amountPaise, it.categoryId, it.counterparty, it.fundId, it.note) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val parentAmountPaise: StateFlow<Long> = txnIdFlow
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(0L)
            else transactionRepository.observeSplitGroup(id).map { parts ->
                parts.sumOf { it.amountPaise }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun load(id: String) {
        txnIdFlow.value = id
        viewModelScope.launch { _txn.value = transactionRepository.getById(id) }
    }

    suspend fun saveSplit(parts: List<SplitPart>): Result<Unit> {
        val id = _txn.value?.id ?: return Result.failure(IllegalStateException("No transaction"))
        val result = transactionRepository.saveSplit(id, parts)
        if (result.isSuccess) {
            val groupId = result.getOrThrow()
            val parent = transactionRepository.getById(groupId)
            _txn.value = parent
        }
        return result.map { }
    }

    suspend fun mergeSplitGroup(): Result<Unit> {
        val id = _txn.value?.id ?: return Result.failure(IllegalStateException("No transaction"))
        return transactionRepository.mergeSplitGroup(id)
    }
}
