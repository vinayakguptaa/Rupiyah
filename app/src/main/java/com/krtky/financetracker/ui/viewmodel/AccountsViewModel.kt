package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    userPreferences: UserPreferences,
    transactionRepository: TransactionRepository,
) : ViewModel() {
    val bankAccounts = userPreferences.bankAccounts
        .map { userPreferences.parseBankList(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accountBalances = transactionRepository.observeAccountBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val defaultDigitalAccount = userPreferences.defaultDigitalAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
}
