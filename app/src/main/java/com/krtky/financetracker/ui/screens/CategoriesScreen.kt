package com.krtky.financetracker.ui.screens

import androidx.compose.runtime.Composable
import com.krtky.financetracker.domain.model.TransactionType

/**
 * Legacy entry: this month’s expenses by category.
 */
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onOpenCategory: (categoryId: Long?, categoryName: String) -> Unit,
    onAddTransaction: () -> Unit = {},
) {
    MonthFlowScreen(
        direction = TransactionType.DEBIT,
        group = MonthFlowGroup.Category,
        onBack = onBack,
        onOpenCategory = onOpenCategory,
        onOpenSource = { _, _ -> },
        onAddTransaction = onAddTransaction,
    )
}
