package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionSplit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    val splits: StateFlow<List<TransactionSplit>> = txnIdFlow
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptyList())
            else transactionRepository.observeSplits(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(id: String) {
        txnIdFlow.value = id
        viewModelScope.launch { _txn.value = transactionRepository.getById(id) }
    }

    suspend fun saveSplits(lines: List<TransactionSplit>): Result<Unit> {
        val id = _txn.value?.id ?: return Result.failure(IllegalStateException("No transaction"))
        val result = transactionRepository.setSplits(id, lines)
        if (result.isSuccess) {
            _txn.value = transactionRepository.getById(id)
        }
        return result
    }

    suspend fun clearSplits(): Result<Unit> {
        val id = _txn.value?.id ?: return Result.failure(IllegalStateException("No transaction"))
        val result = transactionRepository.clearSplits(id)
        if (result.isSuccess) {
            _txn.value = transactionRepository.getById(id)
        }
        return result
    }
}
