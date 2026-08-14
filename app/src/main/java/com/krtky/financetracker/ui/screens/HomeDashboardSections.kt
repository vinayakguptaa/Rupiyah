package com.krtky.financetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.FundBalance
import com.krtky.financetracker.domain.model.MonthlyTrend
import com.krtky.financetracker.domain.model.NamedAmount
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.BalanceHeroCard
import com.krtky.financetracker.ui.components.CategoryInteractivePieChart
import com.krtky.financetracker.ui.components.EmptyState
import com.krtky.financetracker.ui.components.FundsWaveSummary
import com.krtky.financetracker.ui.components.MonthlyExpenseChart
import com.krtky.financetracker.ui.components.OutlinePillButton
import com.krtky.financetracker.ui.components.OverviewTile
import com.krtky.financetracker.ui.components.TransactionCard
import com.krtky.financetracker.ui.navigation.HomeSection
import com.krtky.financetracker.ui.navigation.HomeSectionConfig
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.categoryColor
import com.krtky.financetracker.ui.util.formatDateTime
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.onCategoryColor
import kotlin.math.roundToInt

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
    val investmentByName: List<NamedAmount> = emptyList(),
) {
    val netInvested: Long get() = investedPaise - redeemedPaise
}

internal fun LazyListScope.homeDashboardSections(
    layout: List<HomeSectionConfig>,
    editMode: Boolean,
    data: HomeDashboardData,
    onMoveSection: (from: Int, to: Int) -> Unit,
    onToggleSpan: (HomeSection) -> Unit,
    onToggleHidden: () -> Unit,
    onOpenFunds: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenExpenseActivity: () -> Unit,
    onCategorySelected: (CategorySpend?) -> Unit,
    onOpenTxn: (String) -> Unit,
    onAddCash: () -> Unit,
    onOpenHistory: () -> Unit,
    onSelectHaptic: () -> Unit,
) {
    if (editMode) {
        layout.forEachIndexed { index, config ->
            item(key = "section_${config.section.id}") {
                ReorderableSectionChrome(
                    title = config.section.title,
                    index = index,
                    total = layout.size,
                    span = config.effectiveSpan,
                    canChangeSpan = config.section.allowsHalfWidth,
                    onMoveUp = {
                        if (index > 0) onMoveSection(index, index - 1)
                    },
                    onMoveDown = {
                        if (index < layout.lastIndex) onMoveSection(index, index + 1)
                    },
                    onDragSwap = { deltaIndex ->
                        val target = (index + deltaIndex).coerceIn(0, layout.lastIndex)
                        if (target != index) onMoveSection(index, target)
                    },
                    onToggleSpan = { onToggleSpan(config.section) },
                ) {
                    HomeSectionBody(
                        section = config.section,
                        data = data,
                        compact = true,
                        halfWidth = config.effectiveSpan == 1,
                        onToggleHidden = onToggleHidden,
                        onOpenFunds = onOpenFunds,
                        onOpenAccounts = onOpenAccounts,
                        onOpenCategories = onOpenCategories,
                        onOpenExpenseActivity = onOpenExpenseActivity,
                        onCategorySelected = onCategorySelected,
                        onOpenTxn = onOpenTxn,
                        onAddCash = onAddCash,
                        onOpenHistory = onOpenHistory,
                        onSelectHaptic = onSelectHaptic,
                    )
                }
            }
        }
        return
    }

    var i = 0
    while (i < layout.size) {
        val config = layout[i]
        val span = config.effectiveSpan
        val next = layout.getOrNull(i + 1)
        val canPair = span == 1 &&
            next != null &&
            next.effectiveSpan == 1

        if (canPair && next != null) {
            val left = config
            val right = next
            item(key = "row_${left.section.id}_${right.section.id}") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        HomeSectionBody(
                            section = left.section,
                            data = data,
                            compact = false,
                            halfWidth = true,
                            onToggleHidden = onToggleHidden,
                            onOpenFunds = onOpenFunds,
                            onOpenAccounts = onOpenAccounts,
                            onOpenCategories = onOpenCategories,
                            onOpenExpenseActivity = onOpenExpenseActivity,
                            onCategorySelected = onCategorySelected,
                            onOpenTxn = onOpenTxn,
                            onAddCash = onAddCash,
                            onOpenHistory = onOpenHistory,
                            onSelectHaptic = onSelectHaptic,
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        HomeSectionBody(
                            section = right.section,
                            data = data,
                            compact = false,
                            halfWidth = true,
                            onToggleHidden = onToggleHidden,
                            onOpenFunds = onOpenFunds,
                            onOpenAccounts = onOpenAccounts,
                            onOpenCategories = onOpenCategories,
                            onOpenExpenseActivity = onOpenExpenseActivity,
                            onCategorySelected = onCategorySelected,
                            onOpenTxn = onOpenTxn,
                            onAddCash = onAddCash,
                            onOpenHistory = onOpenHistory,
                            onSelectHaptic = onSelectHaptic,
                        )
                    }
                }
            }
            i += 2
        } else if (span == 1) {
            item(key = "section_${config.section.id}") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        HomeSectionBody(
                            section = config.section,
                            data = data,
                            compact = false,
                            halfWidth = true,
                            onToggleHidden = onToggleHidden,
                            onOpenFunds = onOpenFunds,
                            onOpenAccounts = onOpenAccounts,
                            onOpenCategories = onOpenCategories,
                            onOpenExpenseActivity = onOpenExpenseActivity,
                            onCategorySelected = onCategorySelected,
                            onOpenTxn = onOpenTxn,
                            onAddCash = onAddCash,
                            onOpenHistory = onOpenHistory,
                            onSelectHaptic = onSelectHaptic,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            if (config.section == HomeSection.RECENT) {
                recentActivityItems(
                    data = data,
                    onCategorySelected = onCategorySelected,
                    onOpenTxn = onOpenTxn,
                    onAddCash = onAddCash,
                    onOpenHistory = onOpenHistory,
                )
            }
            i++
        } else {
            item(key = "section_${config.section.id}") {
                HomeSectionBody(
                    section = config.section,
                    data = data,
                    compact = false,
                    halfWidth = false,
                    onToggleHidden = onToggleHidden,
                    onOpenFunds = onOpenFunds,
                    onOpenAccounts = onOpenAccounts,
                    onOpenCategories = onOpenCategories,
                    onOpenExpenseActivity = onOpenExpenseActivity,
                    onCategorySelected = onCategorySelected,
                    onOpenTxn = onOpenTxn,
                    onAddCash = onAddCash,
                    onOpenHistory = onOpenHistory,
                    onSelectHaptic = onSelectHaptic,
                )
            }
            if (config.section == HomeSection.RECENT) {
                recentActivityItems(
                    data = data,
                    onCategorySelected = onCategorySelected,
                    onOpenTxn = onOpenTxn,
                    onAddCash = onAddCash,
                    onOpenHistory = onOpenHistory,
                )
            }
            i++
        }
    }
}

private fun LazyListScope.recentActivityItems(
    data: HomeDashboardData,
    onCategorySelected: (CategorySpend?) -> Unit,
    onOpenTxn: (String) -> Unit,
    onAddCash: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    if (data.filtered.isEmpty()) {
        item(key = "recent_empty") {
            if (data.selectedCategoryFilter != null) {
                EmptyState(
                    icon = Icons.Default.FilterAltOff,
                    title = stringResource(R.string.empty_category_filter_title),
                    body = stringResource(R.string.empty_category_filter_body),
                    actionLabel = stringResource(R.string.action_clear_filter),
                    onAction = { onCategorySelected(null) },
                )
            } else {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = stringResource(R.string.empty_txns_title),
                    body = stringResource(R.string.empty_txns_body),
                    actionLabel = stringResource(R.string.empty_txns_action),
                    onAction = onAddCash,
                )
            }
        }
    } else {
        itemsIndexed(
            data.filtered,
            key = { _, t -> t.id },
        ) { _, t ->
            val scheme = MaterialTheme.colorScheme
            val party = t.counterparty ?: t.note ?: t.accountName ?: "Transaction"
            val sign = if (t.type == TransactionType.DEBIT) "-" else "+"
            val catColor = categoryColor(t.categoryColor)
            TransactionCard(
                title = party,
                subtitle = listOfNotNull(
                    t.occurredAt.formatDateTime(),
                    t.categoryName,
                    t.note?.take(28),
                    t.accountName,
                ).joinToString(" · "),
                amount = "$sign${t.amountPaise.inr()}",
                amountColor = if (t.type == TransactionType.DEBIT) scheme.error else scheme.primary,
                icon = CategoryIcons.iconFor(t.categoryIcon, t.categoryName),
                onClick = { onOpenTxn(t.id) },
                visible = true,
                iconContainerColor = catColor,
                iconTint = catColor?.let { onCategoryColor(it) },
            )
        }
        item(key = "see_all") {
            OutlinePillButton(
                text = stringResource(R.string.home_see_all_activity),
                icon = Icons.Default.History,
                onClick = onOpenHistory,
            )
        }
    }
}

@Composable
private fun ReorderableSectionChrome(
    title: String,
    index: Int,
    total: Int,
    span: Int,
    canChangeSpan: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragSwap: (deltaIndex: Int) -> Unit,
    onToggleSpan: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var dragAccum by remember { mutableFloatStateOf(0f) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.cd_drag_section),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .pointerInput(index, total) {
                            detectDragGesturesAfterLongPress(
                                onDragEnd = { dragAccum = 0f },
                                onDragCancel = { dragAccum = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragAccum += dragAmount.y
                                    val threshold = 64f
                                    when {
                                        dragAccum > threshold -> {
                                            onDragSwap(1)
                                            dragAccum = 0f
                                        }
                                        dragAccum < -threshold -> {
                                            onDragSwap(-1)
                                            dragAccum = 0f
                                        }
                                    }
                                },
                            )
                        }
                        .padding(4.dp),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (canChangeSpan) {
                    IconButton(onClick = onToggleSpan) {
                        Icon(
                            if (span == 1) Icons.Default.ViewColumn else Icons.Default.ViewAgenda,
                            contentDescription = stringResource(R.string.cd_toggle_section_width),
                            tint = if (span == 1) scheme.primary else scheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onMoveUp, enabled = index > 0) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.cd_move_section_up),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = index < total - 1) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_move_section_down),
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun HomeSectionBody(
    section: HomeSection,
    data: HomeDashboardData,
    compact: Boolean,
    halfWidth: Boolean,
    onToggleHidden: () -> Unit,
    onOpenFunds: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenExpenseActivity: () -> Unit,
    onCategorySelected: (CategorySpend?) -> Unit,
    onOpenTxn: (String) -> Unit,
    onAddCash: () -> Unit,
    onOpenHistory: () -> Unit,
    onSelectHaptic: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    when (section) {
        HomeSection.HERO -> {
            AnimatedVisibility(
                visible = data.heroVisible,
                enter = fadeIn(M3EMotion.effectsDefault()) +
                    slideInVertically(M3EMotion.spatialDefault()) { it / 10 },
                exit = fadeOut(),
            ) {
                BalanceHeroCard(
                    title = stringResource(R.string.home_net_this_month),
                    balance = data.net.inr(),
                    monthLabel = "Credits − lifestyle · ${data.monthLabel}",
                    incomeLabel = "Credits",
                    incomeValue = data.income.inr(),
                    expenseLabel = "Lifestyle",
                    expenseValue = data.spent.inr(),
                    hidden = data.isNetHidden,
                    incomeChangePct = data.mom.incomePct,
                    expenseChangePct = data.mom.expensePct,
                    lastMonthIncomeLabel = data.mom.lastIncomeLabel,
                    lastMonthExpenseLabel = data.mom.lastExpenseLabel,
                    onToggleHidden = {
                        onSelectHaptic()
                        onToggleHidden()
                    },
                )
            }
        }
        HomeSection.OVERVIEW -> {
            Column(verticalArrangement = Arrangement.spacedBy(if (halfWidth) 8.dp else 12.dp)) {
                if (!compact && !halfWidth) {
                    Text(
                        stringResource(R.string.home_overview),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onBackground,
                    )
                }
                if (halfWidth) {
                    // Stacked compact tiles for half-width cell
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OverviewTile(
                            title = "Open Tabs",
                            value = if (data.isNetHidden) "••••" else "${data.funds.size}",
                            subtitle = if (data.isNetHidden) "₹ ••••" else data.fundBalance.inr(),
                            icon = Icons.Default.Savings,
                            onClick = onOpenFunds,
                            accent = scheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OverviewTile(
                            title = "Invested",
                            value = if (data.isNetHidden) "••••" else data.netInvested.inr(),
                            subtitle = if (data.isNetHidden) {
                                "Net this month"
                            } else {
                                "In ${data.investedPaise.inr()} · out ${data.redeemedPaise.inr()}"
                            },
                            icon = Icons.Default.Savings,
                            onClick = onOpenExpenseActivity,
                            accent = scheme.tertiary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OverviewTile(
                            title = "Accounts",
                            value = if (data.isNetHidden) "••••" else data.accountsTotal.inr(),
                            subtitle = if (data.isNetHidden) "Cash · banks" else "Cash ${data.cashBal.inr()}",
                            icon = Icons.Default.Payments,
                            onClick = onOpenAccounts,
                            accent = scheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OverviewTile(
                            title = "Lifestyle",
                            value = if (data.isNetHidden) "••••" else data.spent.inr(),
                            subtitle = data.monthLabel,
                            icon = Icons.Default.ShoppingBag,
                            onClick = onOpenExpenseActivity,
                            accent = scheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OverviewTile(
                            title = "Open Tabs",
                            value = if (data.isNetHidden) "••••" else "${data.funds.size}",
                            subtitle = if (data.isNetHidden) {
                                "₹ ••••"
                            } else {
                                data.fundBalance.inr() + if (data.funds.isEmpty()) " · none open" else " net open"
                            },
                            icon = Icons.Default.Savings,
                            onClick = onOpenFunds,
                            accent = scheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        OverviewTile(
                            title = "Accounts",
                            value = if (data.isNetHidden) "••••" else data.accountsTotal.inr(),
                            subtitle = if (data.isNetHidden) {
                                "Cash · Digital"
                            } else {
                                "Cash ${data.cashBal.inr()} · Digital ${data.digitalBal.inr()}"
                            },
                            icon = Icons.Default.Payments,
                            onClick = onOpenAccounts,
                            accent = scheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OverviewTile(
                            title = "Top category",
                            value = if (data.isNetHidden) {
                                "••••"
                            } else {
                                data.topCategory?.totalPaise?.inr() ?: "—"
                            },
                            subtitle = when {
                                data.isNetHidden -> "Hidden"
                                data.topCategory == null -> stringResource(R.string.home_no_expenses_yet)
                                data.topCategoryPct != null ->
                                    "${data.topCategory.categoryName} · ${data.topCategoryPct}%"
                                else -> data.topCategory.categoryName
                            },
                            icon = Icons.Default.Category,
                            onClick = onOpenCategories,
                            accent = scheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        OverviewTile(
                            title = "Lifestyle",
                            value = if (data.isNetHidden) "••••" else data.spent.inr(),
                            subtitle = "${data.monthLabel} · excl. invest",
                            icon = Icons.Default.ShoppingBag,
                            onClick = onOpenExpenseActivity,
                            accent = scheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        HomeSection.CATEGORY_RING -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = if (compact) scheme.surfaceContainerLowest else scheme.surfaceContainerHigh,
            ) {
                Column(
                    Modifier.padding(horizontal = if (halfWidth) 10.dp else 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = if (halfWidth) Alignment.CenterHorizontally else Alignment.Start,
                ) {
                    Text(
                        stringResource(R.string.home_spending_by_category),
                        style = if (halfWidth) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!halfWidth) {
                        Text(
                            stringResource(R.string.home_ring_filter_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (halfWidth) {
                        CategoryInteractivePieChart(
                            categorySpends = data.categorySpend,
                            totalExpense = data.spent,
                            incomePaise = data.income,
                            goalLabel = if (data.income > 0) "of ${data.income.inr()}" else data.monthLabel,
                            size = 108.dp,
                            onCategorySelected = onCategorySelected,
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            CategoryInteractivePieChart(
                                categorySpends = data.categorySpend,
                                totalExpense = data.spent,
                                incomePaise = data.income,
                                goalLabel = if (data.income > 0) "of ${data.income.inr()}" else data.monthLabel,
                                size = 136.dp,
                                onCategorySelected = onCategorySelected,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val topCats = data.categorySpend.filter { it.totalPaise > 0 }.take(5)
                                if (topCats.isEmpty()) {
                                    Text(
                                        stringResource(R.string.home_no_expenses_yet),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                } else {
                                    val pieColors = listOf(
                                        scheme.primary,
                                        scheme.tertiary,
                                        scheme.secondary,
                                        scheme.error,
                                        scheme.primary.copy(alpha = 0.55f),
                                    )
                                    topCats.forEachIndexed { index, cat ->
                                        val pct = if (data.spent > 0) {
                                            (cat.totalPaise.toFloat() / data.spent.toFloat() * 100f).roundToInt()
                                        } else {
                                            0
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(pieColors.getOrElse(index) { scheme.primary }),
                                            )
                                            Text(
                                                cat.categoryName,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                            )
                                            Text(
                                                "$pct%",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = scheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (data.categorySpend.size > 5) {
                                        Text(
                                            "+${data.categorySpend.size - 5} more",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = scheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        HomeSection.MONTHLY_TREND -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = if (compact) scheme.surfaceContainerLowest else scheme.surfaceContainerHigh,
            ) {
                MonthlyExpenseChart(
                    data = data.monthlyTrend,
                    modifier = Modifier.padding(
                        horizontal = if (halfWidth) 10.dp else 16.dp,
                        vertical = if (halfWidth) 12.dp else 16.dp,
                    ),
                )
            }
        }
        HomeSection.RECENT -> {
            if (!compact) {
                Text(
                    data.selectedCategoryFilter?.categoryName ?: stringResource(R.string.home_recent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    data.selectedCategoryFilter?.categoryName ?: stringResource(R.string.home_recent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        HomeSection.FUNDS_SUMMARY -> {
            Column {
                FundsWaveSummary(
                    funds = data.funds,
                    hidden = data.isNetHidden,
                    onOpenFunds = onOpenFunds,
                )
                if (!compact) Spacer(Modifier.height(16.dp))
            }
        }
    }
}
