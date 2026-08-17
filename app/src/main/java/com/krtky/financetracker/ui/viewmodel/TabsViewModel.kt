package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TabsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {
    val tabs = tabsState(transactionRepository)

    init {
        // One-shot ledger repair: restore wiped openings and re-link txn debits/credits
        viewModelScope.launch {
            runCatching { transactionRepository.repairAllTabLedgers() }
        }
    }

    suspend fun create(name: String, openingAmountText: String = "") {
        if (name.isBlank()) return
        val opening = Money.fromRupeesString(openingAmountText)
        val amount = opening?.paise?.coerceAtLeast(0L) ?: 0L
        transactionRepository.addTab(name.trim(), budgetPaise = amount)
    }

    /** Set tab amount absolutely (restarts baseline; linked txns still apply). */
    suspend fun adjust(tabId: Long, amountText: String) {
        val money = Money.fromRupeesString(amountText) ?: return
        transactionRepository.setTabBudget(tabId, money.paise.coerceAtLeast(0L))
    }

    suspend fun delete(tabId: Long) {
        transactionRepository.deleteTab(tabId)
    }
}
