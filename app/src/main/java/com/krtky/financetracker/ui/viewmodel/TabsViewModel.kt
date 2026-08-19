package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TabsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {
    val tabs = tabsState(transactionRepository)
    val archivedTabs = transactionRepository.observeArchivedTabs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // One-shot ledger rebuild: align fund_ledger with current transactions
        viewModelScope.launch {
            runCatching { transactionRepository.repairAllTabLedgers() }
        }
    }

    suspend fun create(name: String) {
        if (name.isBlank()) return
        transactionRepository.addTab(name.trim())
    }

    suspend fun delete(tabId: Long) {
        transactionRepository.deleteTab(tabId)
    }

    suspend fun restore(tabId: Long) {
        transactionRepository.restoreTab(tabId)
    }

    suspend fun rename(tabId: Long, name: String) {
        transactionRepository.renameTab(tabId, name)
    }
}