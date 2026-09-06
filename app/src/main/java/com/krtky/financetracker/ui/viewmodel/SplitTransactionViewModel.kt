package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.SplitPart
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
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
    val tabs = tabsState(transactionRepository)

    @OptIn(ExperimentalCoroutinesApi::class)
    val splits: StateFlow<List<SplitPart>> = txnIdFlow
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptyList())
            else transactionRepository.observeSplitGroup(id).map { parts ->
                parts.map {
                    SplitPart(
                        amountPaise = it.amountPaise,
                        categoryId = it.categoryId,
                        counterparty = it.counterparty,
                        tabId = it.tabId,
                        note = it.note,
                        type = it.type,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Always the parent bank amount (not abs-sum of children). */
    val parentAmountPaise: StateFlow<Long> = _txn
        .map { txn -> txn?.amountPaise?.let { kotlin.math.abs(it) } ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val parentType: StateFlow<TransactionType> = _txn
        .map { it?.type ?: TransactionType.DEBIT }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionType.DEBIT)

    fun load(id: String) {
        txnIdFlow.value = id
        viewModelScope.launch {
            val t = transactionRepository.getById(id) ?: return@launch
            // Always hold the soft-deleted or live parent for amount/type.
            val parentId = t.splitGroupId ?: t.id
            _txn.value = transactionRepository.getById(parentId) ?: t
            txnIdFlow.value = parentId
        }
    }

    suspend fun saveSplit(parts: List<SplitPart>): Result<Unit> {
        val id = _txn.value?.id ?: return Result.failure(IllegalStateException("No transaction"))
        val result = transactionRepository.saveSplit(id, parts)
        if (result.isSuccess) {
            val groupId = result.getOrThrow()
            val parent = transactionRepository.getById(groupId)
            _txn.value = parent
            txnIdFlow.value = groupId
        }
        return result.map { }
    }

    suspend fun mergeSplitGroup(): Result<Unit> {
        val id = _txn.value?.id ?: return Result.failure(IllegalStateException("No transaction"))
        return transactionRepository.mergeSplitGroup(id)
    }
}
