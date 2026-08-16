package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.domain.model.MonthlyTrend
import com.krtky.financetracker.ui.components.MonthlyExpenseChart

@Composable
internal fun HomeTrendSection(
    monthlyTrend: List<MonthlyTrend>,
    compact: Boolean,
    halfWidth: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (compact) scheme.surfaceContainerLowest else scheme.surfaceContainerHigh,
    ) {
        MonthlyExpenseChart(
            data = monthlyTrend,
            modifier = Modifier.padding(
                horizontal = if (halfWidth) 10.dp else 16.dp,
                vertical = if (halfWidth) 12.dp else 16.dp,
            ),
        )
    }
}
