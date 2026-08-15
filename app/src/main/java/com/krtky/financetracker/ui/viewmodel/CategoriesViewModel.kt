package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.repository.CashflowRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.CategorySpend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val cashflowRepository: CashflowRepository,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    val categorySpend: StateFlow<List<CategorySpend>> = refresh.map {
        cashflowRepository.categorySpend()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalExpense: StateFlow<Long> = categorySpend.map { list ->
        list.sumOf { it.totalPaise }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    init {
        viewModelScope.launch {
            transactionRepository.observeTransactions().collect { refresh.value++ }
        }
    }
}
