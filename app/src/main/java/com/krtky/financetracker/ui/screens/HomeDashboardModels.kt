package com.krtky.financetracker.ui.screens

import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.FundBalance
import com.krtky.financetracker.domain.model.SourceSpend
import com.krtky.financetracker.domain.model.Transaction

/**
 * Shared data model for the Home dashboard sections.
 */
internal data class HomeDashboardData(
    val heroVisible: Boolean,
    val availableBalance: Long,
    val income: Long,
    val spent: Long,
    val monthLabel: String,
    val isNetHidden: Boolean,
    val funds: List<FundBalance>,
    val fundBalance: Long,
    val cashBal: Long,
    val digitalBal: Long,
    val expenseByCategory: List<CategorySpend>,
    val expenseBySource: List<SourceSpend>,
    val incomeByCategory: List<CategorySpend>,
    val incomeBySource: List<SourceSpend>,
    val filtered: List<Transaction>,
)
