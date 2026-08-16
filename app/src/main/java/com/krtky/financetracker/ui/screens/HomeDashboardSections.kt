package com.krtky.financetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.ui.components.BalanceHeroCard
import com.krtky.financetracker.ui.components.FundsWaveSummary
import com.krtky.financetracker.ui.navigation.HomeSection
import com.krtky.financetracker.ui.navigation.HomeSectionConfig
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.util.inr

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

        if (canPair) {
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
            HomeTilesSection(
                data = data,
                compact = compact,
                halfWidth = halfWidth,
                onOpenFunds = onOpenFunds,
                onOpenAccounts = onOpenAccounts,
                onOpenCategories = onOpenCategories,
                onOpenExpenseActivity = onOpenExpenseActivity,
            )
        }
        HomeSection.CATEGORY_RING -> {
            HomeCategoryRingSection(
                data = data,
                compact = compact,
                halfWidth = halfWidth,
                onCategorySelected = onCategorySelected,
            )
        }
        HomeSection.MONTHLY_TREND -> {
            HomeTrendSection(
                monthlyTrend = data.monthlyTrend,
                compact = compact,
                halfWidth = halfWidth,
            )
        }
        HomeSection.RECENT -> {
            HomeRecentSection(
                data = data,
                compact = compact,
            )
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
