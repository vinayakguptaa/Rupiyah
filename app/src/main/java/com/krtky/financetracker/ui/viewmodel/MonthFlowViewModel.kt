package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.repository.CashflowRepository
import com.krtky.financetracker.data.repository.HomeCashflowSnapshot
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.CashflowMetrics
import com.krtky.financetracker.domain.model.MonthlySummary
import com.krtky.financetracker.ui.util.startOfMonthMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class MonthFlowViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val cashflowRepository: CashflowRepository,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private val selectedMonth = MutableStateFlow(startOfMonthMillis())

    val selectedMonthMillis: StateFlow<Long> = selectedMonth

    val isCurrentMonth: StateFlow<Boolean> = selectedMonth.map { month ->
        month >= startOfMonthMillis()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Inclusive calendar-month bounds for the selected month. */
    val monthBounds: StateFlow<Pair<Long, Long>> = selectedMonth.map { month ->
        CashflowRepository.monthBounds(month)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CashflowRepository.monthBounds(selectedMonth.value),
    )

    val snapshot: StateFlow<HomeCashflowSnapshot> = combine(refresh, selectedMonth) { _, month ->
        cashflowRepository.homeCashflowSnapshot(month)
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

    fun shiftMonth(delta: Int) {
        if (delta == 0) return
        val currentStart = startOfMonthMillis()
        val next = Calendar.getInstance().apply {
            timeInMillis = selectedMonth.value
            add(Calendar.MONTH, delta)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        selectedMonth.value = next.coerceAtMost(currentStart)
    }
}
