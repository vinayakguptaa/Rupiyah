package com.krtky.financetracker.ui.screens

import androidx.compose.runtime.Composable
import com.krtky.financetracker.domain.model.TransactionType

/**
 * Legacy entry: expenses by category (month-steppable via Month Flow).
 */
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onOpenCategory: (categoryId: Long?, categoryName: String, fromMillis: Long, toMillis: Long) -> Unit,
    onAddTransaction: () -> Unit = {},
) {
    MonthFlowScreen(
        direction = TransactionType.DEBIT,
        group = MonthFlowGroup.Category,
        onBack = onBack,
        onOpenCategory = onOpenCategory,
        onOpenSource = { _, _, _, _ -> },
        onAddTransaction = onAddTransaction,
    )
}
