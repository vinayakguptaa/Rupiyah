package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FundsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {
    val funds = fundsState(transactionRepository)

    init {
        // One-shot ledger repair: restore wiped openings and re-link txn debits/credits
        viewModelScope.launch {
            runCatching { transactionRepository.repairAllFundLedgers() }
        }
    }

    suspend fun create(name: String, openingAmountText: String = "") {
        if (name.isBlank()) return
        val opening = Money.fromRupeesString(openingAmountText)
        val amount = opening?.paise?.coerceAtLeast(0L) ?: 0L
        transactionRepository.addFund(name.trim(), budgetPaise = amount)
    }

    /** Set fund amount absolutely (restarts baseline; linked txns still apply). */
    suspend fun adjust(fundId: Long, amountText: String) {
        val money = Money.fromRupeesString(amountText) ?: return
        transactionRepository.setFundBudget(fundId, money.paise.coerceAtLeast(0L))
    }

    suspend fun delete(fundId: Long) {
        transactionRepository.deleteFund(fundId)
    }
}
