package com.krtky.financetracker.ui.screens

import com.krtky.financetracker.domain.model.CategoryNetSpend
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.SourceNetSpend
import com.krtky.financetracker.domain.model.SourceSpend
import com.krtky.financetracker.domain.model.TabBalance

/** Shared data for the fixed Home dashboard sections. */
internal data class HomeDashboardData(
    val heroVisible: Boolean,
    val availableBalance: Long,
    val income: Long,
    val spent: Long,
    val monthLabel: String,
    val isNetHidden: Boolean,
    val tabs: List<TabBalance>,
    val fundBalance: Long,
    val cashBal: Long,
    val digitalBal: Long,
    val categoryNetSpend: List<CategoryNetSpend> = emptyList(),
    val sourceNetSpend: List<SourceNetSpend> = emptyList(),
    val expenseByCategory: List<CategorySpend> = emptyList(),
    val expenseBySource: List<SourceSpend> = emptyList(),
    val incomeByCategory: List<CategorySpend> = emptyList(),
    val incomeBySource: List<SourceSpend> = emptyList(),
    val activeAccountIds: Set<Long> = emptySet(),
)
