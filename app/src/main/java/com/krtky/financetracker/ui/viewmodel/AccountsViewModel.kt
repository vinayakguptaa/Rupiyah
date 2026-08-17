package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.domain.model.Account
import com.krtky.financetracker.domain.model.AccountBalance
import com.krtky.financetracker.data.repository.UnassignedDigital
import com.krtky.financetracker.domain.model.AccountKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {
    val accounts = accountRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archivedAccounts = accountRepository.observeArchived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accountBalancesDetail = accountRepository.observeBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allBalancesDetail = accountRepository.observeAllBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val defaultDigitalAccount = userPreferences.defaultDigitalAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val unassignedDigital = accountRepository.observeUnassignedDigital()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UnassignedDigital(count = 0, netPaise = 0L),
        )

    fun addAccount(name: String, kind: AccountKind = AccountKind.BANK) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            accountRepository.addOrRestore(trimmed, kind)
            mirrorBankPrefs()
        }
    }

    fun archiveAccount(id: Long) {
        viewModelScope.launch {
            accountRepository.archive(id)
            mirrorBankPrefs()
        }
    }

    fun restoreAccount(id: Long) {
        viewModelScope.launch {
            accountRepository.unarchive(id)
            mirrorBankPrefs()
        }
    }

    private suspend fun mirrorBankPrefs() {
        val joined = accountRepository.activeBankNames().joinToString(",")
        userPreferences.setBankAccounts(joined)
    }
}
