package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.EmptyState
import com.krtky.financetracker.ui.components.chrome.StackTopBar
import com.krtky.financetracker.ui.theme.Dimens
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.viewmodel.MonthFlowViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class MonthFlowGroup { Category, Source }

/**
 * Expenses / income / investments for a calendar month, cut by category or source.
 * Home always opens the current month; use the chevrons to step backward.
 */
@Composable
fun MonthFlowScreen(
    direction: TransactionType,
    group: MonthFlowGroup,
    onBack: () -> Unit,
    onOpenCategory: (categoryId: Long?, categoryName: String, fromMillis: Long, toMillis: Long) -> Unit,
    onOpenSource: (accountId: Long?, accountName: String, fromMillis: Long, toMillis: Long) -> Unit,
    onAddTransaction: () -> Unit = {},
    investment: Boolean = false,
    vm: MonthFlowViewModel = hiltViewModel(),
) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val selectedMonth by vm.selectedMonthMillis.collectAsStateWithLifecycle()
    val isCurrentMonth by vm.isCurrentMonth.collectAsStateWithLifecycle()
    val bounds by vm.monthBounds.collectAsStateWithLifecycle()
    val monthLabel = remember(selectedMonth) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(selectedMonth))
    }
    val isExpense = !investment && direction == TransactionType.DEBIT
    val rows = when {
        investment && group == MonthFlowGroup.Category ->
            snapshot.investmentByCategory.map { FlowRow(it.categoryId, it.categoryName, it.totalPaise) }
        investment && group == MonthFlowGroup.Source ->
            snapshot.investmentBySource.map { FlowRow(it.accountId, it.accountName, it.totalPaise) }
        isExpense && group == MonthFlowGroup.Category ->
            snapshot.categorySpend.map { FlowRow(it.categoryId, it.categoryName, it.totalPaise) }
        isExpense && group == MonthFlowGroup.Source ->
            snapshot.expenseBySource.map { FlowRow(it.accountId, it.accountName, it.totalPaise) }
        !isExpense && group == MonthFlowGroup.Category ->
            snapshot.incomeByCategory.map { FlowRow(it.categoryId, it.categoryName, it.totalPaise) }
        else ->
            snapshot.incomeBySource.map { FlowRow(it.accountId, it.accountName, it.totalPaise) }
    }.filter { it.totalPaise > 0 }
    val total = when {
        investment -> snapshot.investedPaise + snapshot.redeemedPaise
        isExpense -> snapshot.summary.expensePaise
        else -> snapshot.summary.incomePaise
    }
    val title = when {
        investment && group == MonthFlowGroup.Category -> "Investments by category"
        investment && group == MonthFlowGroup.Source -> "Investments by source"
        isExpense && group == MonthFlowGroup.Category -> "Expenses by category"
        isExpense && group == MonthFlowGroup.Source -> "Expenses by source"
        !isExpense && group == MonthFlowGroup.Category -> "Income by category"
        else -> "Income by source"
    }
    val totalLabel = when {
        investment -> monthLabel
        isExpense -> "Total spent"
        else -> "Total received"
    }
    val noun = if (group == MonthFlowGroup.Category) {
        if (rows.size == 1) "category" else "categories"
    } else {
        if (rows.size == 1) "source" else "sources"
    }
    val scheme = MaterialTheme.colorScheme
    val (fromMillis, toMillis) = bounds

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = Dimens.ScreenHorizontal,
            end = Dimens.ScreenHorizontal,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            StackTopBar(
                title = title,
                subtitle = null,
                onBack = onBack,
            )
        }
        item {
            MonthStepper(
                label = monthLabel,
                canGoForward = !isCurrentMonth,
                onPrevious = { vm.shiftMonth(-1) },
                onNext = { vm.shiftMonth(1) },
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = scheme.primaryContainer,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        totalLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    Text(
                        total.inr(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                    )
                    Text(
                        "${rows.size} $noun",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
        }
        if (rows.isEmpty()) {
            item {
                EmptyState(
                    icon = if (group == MonthFlowGroup.Category) {
                        Icons.Default.Category
                    } else {
                        Icons.Default.Payments
                    },
                    title = when {
                        investment -> stringResource(R.string.home_no_investments_yet)
                        isExpense -> stringResource(R.string.empty_categories_title)
                        else -> stringResource(R.string.empty_income_flow_title)
                    },
                    body = when {
                        investment -> stringResource(R.string.home_no_investments_yet)
                        isExpense -> stringResource(R.string.empty_categories_body)
                        else -> stringResource(R.string.empty_income_flow_body)
                    },
                    actionLabel = stringResource(R.string.empty_categories_action),
                    onAction = onAddTransaction,
                )
            }
        } else {
            items(rows, key = { "${it.id}-${it.name}" }) { row ->
                FlowSliceRow(
                    name = row.name,
                    totalPaise = row.totalPaise,
                    periodTotal = total,
                    periodLabel = monthLabel,
                    icon = if (group == MonthFlowGroup.Category) {
                        CategoryIcons.iconFor(null, row.name)
                    } else {
                        Icons.Default.Payments
                    },
                    onClick = {
                        if (group == MonthFlowGroup.Category) {
                            onOpenCategory(row.id, row.name, fromMillis, toMillis)
                        } else {
                            onOpenSource(row.id, row.name, fromMillis, toMillis)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MonthStepper(
    label: String,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
                tint = scheme.onSurface,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                tint = if (canGoForward) scheme.onSurface else scheme.onSurface.copy(alpha = 0.32f),
            )
        }
    }
}

private data class FlowRow(
    val id: Long?,
    val name: String,
    val totalPaise: Long,
)

@Composable
private fun FlowSliceRow(
    name: String,
    totalPaise: Long,
    periodTotal: Long,
    periodLabel: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val pct = if (periodTotal > 0) {
        ((totalPaise * 100.0) / periodTotal).roundToInt()
    } else {
        0
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$pct% of $periodLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Text(
                totalPaise.inr(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
