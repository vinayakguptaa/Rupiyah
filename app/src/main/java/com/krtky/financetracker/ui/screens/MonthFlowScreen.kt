package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.CategoryNetSpend
import com.krtky.financetracker.domain.model.SourceNetSpend
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.EmptyState
import com.krtky.financetracker.ui.components.chrome.StackTopBar
import com.krtky.financetracker.ui.theme.Dimens
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.categoryColor
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.viewmodel.MonthFlowViewModel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class MonthFlowGroup { Category, Source }
enum class MonthFlowMode { NET, DEBIT, CREDIT }

/**
 * Monthly Exchange screen: net cash flow analysis by category or account,
 * with month selector and category/account segmented button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthFlowScreen(
    initialMode: MonthFlowMode = MonthFlowMode.NET,
    initialGroup: MonthFlowGroup = MonthFlowGroup.Category,
    onBack: () -> Unit,
    onOpenCategory: (categoryId: Long?, categoryName: String, type: TransactionType?, fromMillis: Long, toMillis: Long) -> Unit,
    onOpenSource: (accountId: Long?, accountName: String, type: TransactionType?, fromMillis: Long, toMillis: Long) -> Unit,
    onAddTransaction: () -> Unit = {},
    vm: MonthFlowViewModel = hiltViewModel(),
) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val selectedMonth by vm.selectedMonthMillis.collectAsStateWithLifecycle()
    val isCurrentMonth by vm.isCurrentMonth.collectAsStateWithLifecycle()
    val bounds by vm.monthBounds.collectAsStateWithLifecycle()
    val monthLabel = remember(selectedMonth) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(selectedMonth))
    }

    var mode by rememberSaveable(initialMode) { mutableStateOf(initialMode) }
    var group by rememberSaveable(initialGroup) { mutableStateOf(initialGroup) }

    val scheme = MaterialTheme.colorScheme
    val (fromMillis, toMillis) = bounds

    val netPaise = snapshot.summary.incomePaise - snapshot.summary.expensePaise
    val netFormatted = when {
        netPaise < 0 -> "-${abs(netPaise).inr()}"
        netPaise > 0 -> "+${netPaise.inr()}"
        else -> netPaise.inr()
    }

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StackTopBar(
                title = "Monthly Exchange",
                subtitle = monthLabel,
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
            // Mode Selector: Net vs Debits vs Credits
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = listOf(
                    MonthFlowMode.NET to stringResource(R.string.month_flow_net),
                    MonthFlowMode.DEBIT to stringResource(R.string.month_flow_debits),
                    MonthFlowMode.CREDIT to stringResource(R.string.month_flow_credits),
                )
                modes.forEachIndexed { index, (m, label) ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }
        }
        item {
            // Group Selector: Category vs Account
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val groups = listOf(
                    MonthFlowGroup.Category to "By Category",
                    MonthFlowGroup.Source to "By Account",
                )
                groups.forEachIndexed { index, (g, label) ->
                    SegmentedButton(
                        selected = group == g,
                        onClick = { group = g },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = groups.size),
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = scheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (mode) {
                        MonthFlowMode.NET -> {
                            Text(
                                "Net Cash Flow",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurfaceVariant,
                            )
                            Text(
                                netFormatted,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (netPaise >= 0) scheme.primary else scheme.error,
                            )
                            Text(
                                "Income ${snapshot.summary.incomePaise.inr()} · Expenses ${snapshot.summary.expensePaise.inr()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        MonthFlowMode.DEBIT -> {
                            Text(
                                "Total Debits",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurfaceVariant,
                            )
                            Text(
                                snapshot.summary.expensePaise.inr(),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            )
                            val count = if (group == MonthFlowGroup.Category) {
                                snapshot.categorySpend.count { it.totalPaise > 0 }
                            } else {
                                snapshot.expenseBySource.count { it.totalPaise > 0 }
                            }
                            val noun = if (group == MonthFlowGroup.Category) {
                                if (count == 1) "category" else "categories"
                            } else {
                                if (count == 1) "account" else "accounts"
                            }
                            Text(
                                "$count $noun",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        MonthFlowMode.CREDIT -> {
                            Text(
                                "Total Credits",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurfaceVariant,
                            )
                            Text(
                                snapshot.summary.incomePaise.inr(),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                            )
                            val count = if (group == MonthFlowGroup.Category) {
                                snapshot.incomeByCategory.count { it.totalPaise > 0 }
                            } else {
                                snapshot.incomeBySource.count { it.totalPaise > 0 }
                            }
                            val noun = if (group == MonthFlowGroup.Category) {
                                if (count == 1) "category" else "categories"
                            } else {
                                if (count == 1) "account" else "accounts"
                            }
                            Text(
                                "$count $noun",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (mode == MonthFlowMode.NET) {
            if (group == MonthFlowGroup.Category) {
                val activeCats = snapshot.categoryNetSpend
                    .filter { it.debitPaise > 0 || it.creditPaise > 0 }
                    .sortedBy { it.netPaise }
                if (activeCats.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Category,
                            title = stringResource(R.string.home_no_category_net),
                            body = stringResource(R.string.empty_categories_body),
                            actionLabel = stringResource(R.string.empty_categories_action),
                            onAction = onAddTransaction,
                        )
                    }
                } else {
                    items(activeCats, key = { "${it.categoryId}-${it.categoryName}" }) { cat ->
                        CategoryNetFlowRow(
                            item = cat,
                            onClick = {
                                onOpenCategory(cat.categoryId, cat.categoryName, null, fromMillis, toMillis)
                            },
                        )
                    }
                }
            } else {
                val activeSources = snapshot.sourceNetSpend
                    .filter { it.debitPaise > 0 || it.creditPaise > 0 }
                    .sortedBy { it.netPaise }
                val sourcesToShow = activeSources.ifEmpty { snapshot.sourceNetSpend }
                if (sourcesToShow.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Payments,
                            title = stringResource(R.string.home_no_source_net),
                            body = stringResource(R.string.empty_income_flow_body),
                            actionLabel = stringResource(R.string.empty_categories_action),
                            onAction = onAddTransaction,
                        )
                    }
                } else {
                    items(sourcesToShow, key = { "${it.accountId}-${it.accountName}" }) { src ->
                        SourceNetFlowRow(
                            item = src,
                            onClick = {
                                onOpenSource(src.accountId, src.accountName, null, fromMillis, toMillis)
                            },
                        )
                    }
                }
            }
        } else {
            val isDebit = mode == MonthFlowMode.DEBIT
            val txnType = if (isDebit) TransactionType.DEBIT else TransactionType.CREDIT
            val periodTotal = if (isDebit) snapshot.summary.expensePaise else snapshot.summary.incomePaise

            if (group == MonthFlowGroup.Category) {
                val list = (if (isDebit) snapshot.categorySpend else snapshot.incomeByCategory)
                    .filter { it.totalPaise > 0 }
                if (list.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Category,
                            title = if (isDebit) stringResource(R.string.home_no_expenses_yet) else stringResource(R.string.home_no_income_yet),
                            body = stringResource(R.string.empty_categories_body),
                            actionLabel = stringResource(R.string.empty_categories_action),
                            onAction = onAddTransaction,
                        )
                    }
                } else {
                    items(list, key = { "${it.categoryId}-${it.categoryName}" }) { cat ->
                        val pct = if (periodTotal > 0) ((cat.totalPaise * 100.0) / periodTotal).roundToInt() else 0
                        SingleDirectionFlowRow(
                            title = cat.categoryName,
                            subtitle = "$pct% of $monthLabel",
                            amountPaise = cat.totalPaise,
                            icon = CategoryIcons.iconFor(null, cat.categoryName),
                            iconColor = categoryColor(cat.color) ?: scheme.primary,
                            onClick = {
                                onOpenCategory(cat.categoryId, cat.categoryName, txnType, fromMillis, toMillis)
                            },
                        )
                    }
                }
            } else {
                val list = (if (isDebit) snapshot.expenseBySource else snapshot.incomeBySource)
                    .filter { it.totalPaise > 0 }
                if (list.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Payments,
                            title = if (isDebit) stringResource(R.string.home_no_expenses_yet) else stringResource(R.string.home_no_income_yet),
                            body = stringResource(R.string.empty_income_flow_body),
                            actionLabel = stringResource(R.string.empty_categories_action),
                            onAction = onAddTransaction,
                        )
                    }
                } else {
                    items(list, key = { "${it.accountId}-${it.accountName}" }) { src ->
                        val pct = if (periodTotal > 0) ((src.totalPaise * 100.0) / periodTotal).roundToInt() else 0
                        val isCash = src.accountName.equals("Cash", ignoreCase = true)
                        SingleDirectionFlowRow(
                            title = src.accountName,
                            subtitle = "$pct% of $monthLabel",
                            amountPaise = src.totalPaise,
                            icon = if (isCash) Icons.Default.Payments else Icons.Default.AccountBalance,
                            iconColor = scheme.primary,
                            onClick = {
                                onOpenSource(src.accountId, src.accountName, txnType, fromMillis, toMillis)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Legacy signature for backwards compatibility. */
@Composable
fun MonthFlowScreen(
    direction: TransactionType,
    group: MonthFlowGroup,
    onBack: () -> Unit,
    onOpenCategory: (categoryId: Long?, categoryName: String, fromMillis: Long, toMillis: Long) -> Unit,
    onOpenSource: (accountId: Long?, accountName: String, fromMillis: Long, toMillis: Long) -> Unit,
    onAddTransaction: () -> Unit = {},
    vm: MonthFlowViewModel = hiltViewModel(),
) {
    MonthFlowScreen(
        initialMode = when (direction) {
            TransactionType.DEBIT -> MonthFlowMode.DEBIT
            TransactionType.CREDIT -> MonthFlowMode.CREDIT
        },
        initialGroup = group,
        onBack = onBack,
        onOpenCategory = { catId, catName, _, from, to -> onOpenCategory(catId, catName, from, to) },
        onOpenSource = { accId, accName, _, from, to -> onOpenSource(accId, accName, from, to) },
        onAddTransaction = onAddTransaction,
        vm = vm,
    )
}

@Composable
private fun SingleDirectionFlowRow(
    title: String,
    subtitle: String,
    amountPaise: Long,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                amountPaise.inr(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CategoryNetFlowRow(
    item: CategoryNetSpend,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val catColor = categoryColor(item.color) ?: scheme.primary
    val icon = CategoryIcons.iconFor(null, item.categoryName)
    val subtitle = when {
        item.debitPaise > 0 && item.creditPaise > 0 ->
            "Spent ${item.debitPaise.inr()} · Received ${item.creditPaise.inr()}"
        item.creditPaise > 0 ->
            "Received ${item.creditPaise.inr()}"
        else ->
            "Spent ${item.debitPaise.inr()}"
    }
    val netFormatted = when {
        item.netPaise < 0 -> "-${abs(item.netPaise).inr()}"
        item.netPaise > 0 -> "+${item.netPaise.inr()}"
        else -> item.netPaise.inr()
    }
    val amountColor = if (item.netPaise > 0) scheme.primary else scheme.onSurface

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(catColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = catColor, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                netFormatted,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SourceNetFlowRow(
    item: SourceNetSpend,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isCash = item.accountName.equals("Cash", ignoreCase = true)
    val icon = if (isCash) Icons.Default.Payments else Icons.Default.AccountBalance
    val subtitle = when {
        item.debitPaise > 0 && item.creditPaise > 0 ->
            "In ${item.creditPaise.inr()} · Out ${item.debitPaise.inr()}"
        item.creditPaise > 0 ->
            "In ${item.creditPaise.inr()}"
        item.debitPaise > 0 ->
            "Out ${item.debitPaise.inr()}"
        else ->
            "No activity"
    }
    val netFormatted = when {
        item.netPaise < 0 -> "-${abs(item.netPaise).inr()}"
        item.netPaise > 0 -> "+${item.netPaise.inr()}"
        else -> item.netPaise.inr()
    }
    val amountColor = if (item.netPaise > 0) scheme.primary else scheme.onSurface

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.accountName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                netFormatted,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = scheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
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
}
