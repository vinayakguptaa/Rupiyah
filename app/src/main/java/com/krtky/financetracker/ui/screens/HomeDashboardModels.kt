package com.krtky.financetracker.ui.screens

import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.FundBalance
import com.krtky.financetracker.domain.model.MonthlyTrend
import com.krtky.financetracker.domain.model.Transaction

/**
 * Shared data model for the Home dashboard sections.
 * Constructed in [HomeScreen] and consumed by the section composables
 * in [HomeDashboardSections], [HomeTiles], [HomeCategoryRing],
 * [HomeTrendChart], [HomeRecent].
 */
internal data class HomeDashboardData(
    val heroVisible: Boolean,
    val net: Long,
    val income: Long,
    val spent: Long,
    val monthLabel: String,
    val isNetHidden: Boolean,
    val mom: MomMetrics,
    val funds: List<FundBalance>,
    val fundBalance: Long,
    val accountsTotal: Long,
    val cashBal: Long,
    val digitalBal: Long,
    val topCategory: CategorySpend?,
    val topCategoryPct: Int?,
    val categorySpend: List<CategorySpend>,
    val monthlyTrend: List<MonthlyTrend>,
    val filtered: List<Transaction>,
    val selectedCategoryFilter: CategorySpend?,
    val investedPaise: Long = 0L,
    val redeemedPaise: Long = 0L,
) {
    val netInvested: Long get() = investedPaise - redeemedPaise
}

/** Month-over-month metrics shown on the hero card. */
internal data class MomMetrics(
    val incomePct: Float?,
    val expensePct: Float?,
    val lastIncomeLabel: String?,
    val lastExpenseLabel: String?,
)
