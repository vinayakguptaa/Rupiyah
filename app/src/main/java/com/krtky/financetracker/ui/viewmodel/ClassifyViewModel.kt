package com.krtky.financetracker.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.receipt.ReceiptStore
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClassifyViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    private val receiptStore: ReceiptStore,
) : ViewModel() {
    private val _txnId = MutableStateFlow<String?>(null)
    private val _txn = MutableStateFlow<Transaction?>(null)
    val transaction: StateFlow<Transaction?> = _txn
    val categories = categoriesState(categoryRepository, transactionRepository)
    val tabs = tabsState(transactionRepository)

    fun open(id: String) {
        _txnId.value = id
        viewModelScope.launch { _txn.value = transactionRepository.getById(id) }
    }

    fun clear() {
        _txnId.value = null
        _txn.value = null
    }

    suspend fun save(
        categoryId: Long?,
        tabId: Long?,
        note: String,
        receiptLocalUri: Uri? = null,
    ) {
        val id = _txnId.value ?: return
        val receiptPath = receiptLocalUri?.let { receiptStore.persistFromUri(it, id) }
        transactionRepository.classify(
            id,
            categoryId,
            note.ifBlank { null },
            tabId,
            receiptUri = receiptPath,
        )
        clear()
    }
}
