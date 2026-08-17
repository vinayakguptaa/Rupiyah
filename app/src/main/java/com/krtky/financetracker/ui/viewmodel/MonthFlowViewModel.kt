package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.repository.CashflowRepository
import com.krtky.financetracker.data.repository.HomeCashflowSnapshot
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.CashflowMetrics
import com.krtky.financetracker.domain.model.MonthlySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MonthFlowViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val cashflowRepository: CashflowRepository,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    val snapshot: StateFlow<HomeCashflowSnapshot> = refresh.map {
        cashflowRepository.homeCashflowSnapshot()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeCashflowSnapshot(
            MonthlySummary(0, 0),
            CashflowMetrics(0, 0, 0, 0),
            emptyList(),
            emptyList(),
        ),
    )

    init {
        viewModelScope.launch {
            transactionRepository.observeTransactions().collect { refresh.value++ }
        }
    }
}
