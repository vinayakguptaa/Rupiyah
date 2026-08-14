package com.krtky.financetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.TransactionCard
import com.krtky.financetracker.ui.components.BalanceHeroCard
import com.krtky.financetracker.ui.components.CategoryInteractivePieChart
import com.krtky.financetracker.ui.components.EmptyState
import com.krtky.financetracker.ui.components.FundsWaveSummary
import com.krtky.financetracker.ui.components.HomeShimmerSkeleton
import com.krtky.financetracker.ui.components.MonthlyExpenseChart
import com.krtky.financetracker.ui.components.OutlinePillButton
import com.krtky.financetracker.ui.components.OverviewTile
import com.krtky.financetracker.ui.navigation.HomeSection
import com.krtky.financetracker.ui.theme.Dimens
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.theme.NavContentInsets
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.categoryColor
import com.krtky.financetracker.ui.util.onCategoryColor
import com.krtky.financetracker.ui.util.formatDateTime
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.viewmodel.HomeViewModel
import java.util.Calendar
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenTxn: (String) -> Unit,
    onAddCash: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenFunds: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    /** Open Activity with Expense type filter. */
    onOpenExpenseActivity: () -> Unit = onOpenHistory,
    /** Open categories list (spend by category). */
    onOpenCategories: () -> Unit = {},
    /** Open classify sheet for a pending transaction. */
    onClassifyPending: (String) -> Unit = {},
    /** Open Settings detail (e.g. email). */
    onOpenSettingsSection: (String) -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val cashflow by vm.cashflow.collectAsStateWithLifecycle()
    val funds by vm.funds.collectAsStateWithLifecycle()
    val openTabs by vm.openTabs.collectAsStateWithLifecycle()
    val paymentBalances by vm.paymentBalances.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val categorySpend by vm.categorySpend.collectAsStateWithLifecycle()
    val monthlyTrend by vm.monthlyTrend.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val initialLoaded by vm.initialLoaded.collectAsStateWithLifecycle()
    val isNetHidden by vm.hideBalances.collectAsStateWithLifecycle()
    val pendingCount by vm.pendingCount.collectAsStateWithLifecycle()
    val firstPendingId by vm.firstPendingId.collectAsStateWithLifecycle()
    val setupChecklist by vm.setupChecklist.collectAsStateWithLifecycle()
    val sectionLayout by vm.homeSectionLayout.collectAsStateWithLifecycle()
    var selectedCategoryFilter by remember { mutableStateOf<CategorySpend?>(null) }
    var layoutEditMode by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()

    // Home uses lifestyle spend (excl. Investment + self-transfer) and credits
    val income = cashflow.creditPaise
    val spent = cashflow.lifestyleSpendPaise
    val net = income - spent
    val fundBalance = openTabs.sumOf { it.balancePaise }
    val lifestyleCategories = cashflow.lifestyleByCategory.ifEmpty { categorySpend }
    // Cash mode vs everything else (named banks/wallets + unlabelled Digital)
    val cashBal = paymentBalances.entries
        .firstOrNull { it.key.equals("Cash", ignoreCase = true) }
        ?.value ?: 0L
    val digitalBal = paymentBalances.entries
        .filter { !it.key.equals("Cash", ignoreCase = true) }
        .sumOf { it.value }
    val accountsTotal = cashBal + digitalBal
    val topCategory = lifestyleCategories.maxByOrNull { it.totalPaise }
    val topCategoryPct = if (spent > 0 && topCategory != null) {
        ((topCategory.totalPaise * 100.0) / spent).roundToInt()
    } else {
        null
    }
    val displayName by vm.displayName.collectAsStateWithLifecycle()
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greetingBase = when {
        hour < 5 -> "Good late night"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 21 -> "Good evening"
        else -> "Good late night"
    }
    val greeting = if (displayName.isNotBlank()) {
        "$greetingBase ${displayName.trim().lowercase()}"
    } else {
        greetingBase
    }
    val monthLabel = Calendar.getInstance()
        .getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale.getDefault()) ?: "This month"

    // MoM % from monthly trend (current vs previous month)
    val mom = remember(monthlyTrend, income, spent) {
        val prev = monthlyTrend.getOrNull(monthlyTrend.lastIndex - 1)
        val incomePct = prev?.let { pctChange(income, it.incomePaise) }
        val expensePct = prev?.let { pctChange(spent, it.expensePaise) }
        val lastInc = prev?.incomePaise?.inr()
        val lastExp = prev?.expensePaise?.inr()
        MomMetrics(incomePct, expensePct, lastInc, lastExp)
    }

    var heroVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        heroVisible = true
    }

    val filtered = remember(recent, selectedCategoryFilter) {
        val base = recent.take(6)
        val sel = selectedCategoryFilter ?: return@remember base
        base.filter { t ->
            when {
                sel.categoryId != null -> t.categoryId == sel.categoryId
                else -> t.categoryId == null || t.categoryName.equals(sel.categoryName, true)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { vm.refreshNow() },
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = NavContentInsets.listPadding(),
            verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            greeting,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onBackground,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            monthLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (initialLoaded) {
                        IconButton(
                            onClick = {
                                haptics.select()
                                layoutEditMode = !layoutEditMode
                            },
                        ) {
                            Icon(
                                if (layoutEditMode) Icons.Default.Check else Icons.Default.DashboardCustomize,
                                contentDescription = stringResource(
                                    if (layoutEditMode) R.string.cd_done_home_layout
                                    else R.string.cd_edit_home_layout,
                                ),
                                tint = if (layoutEditMode) {
                                    scheme.primary
                                } else {
                                    scheme.onSurfaceVariant.copy(alpha = 0.55f)
                                },
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }

            if (layoutEditMode) {
                item {
                    Text(
                        stringResource(R.string.home_reorder_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            if (pendingCount > 0 && firstPendingId != null) {
                item {
                    FilterChip(
                        selected = true,
                        onClick = {
                            haptics.select()
                            firstPendingId?.let(onClassifyPending)
                        },
                        label = {
                            Text(stringResource(R.string.home_pending_classify, pendingCount))
                        },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            if (setupChecklist.visible) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = scheme.secondaryContainer,
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.home_setup_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = {
                                    haptics.select()
                                    vm.dismissSetupChecklist()
                                }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.home_setup_dismiss),
                                        tint = scheme.onSecondaryContainer,
                                    )
                                }
                            }
                            SetupCheckRow(
                                done = setupChecklist.aiReady,
                                label = stringResource(R.string.home_setup_ai),
                                onClick = { onOpenSettingsSection("llm") },
                            )
                            SetupCheckRow(
                                done = setupChecklist.banksDone,
                                label = stringResource(R.string.home_setup_banks),
                                onClick = { onOpenSettingsSection("banks") },
                            )
                            SetupCheckRow(
                                done = setupChecklist.firstTxnDone,
                                label = stringResource(R.string.home_setup_first_txn),
                                onClick = onAddCash,
                            )
                        }
                    }
                }
            }

            if (!initialLoaded) {
                item(key = "shimmer") { HomeShimmerSkeleton() }
            } else {
                homeDashboardSections(
                    layout = sectionLayout,
                    editMode = layoutEditMode,
                    data = HomeDashboardData(
                        heroVisible = heroVisible,
                        net = net,
                        income = income,
                        spent = spent,
                        monthLabel = monthLabel,
                        isNetHidden = isNetHidden,
                        mom = mom,
                        funds = openTabs.ifEmpty { funds },
                        fundBalance = fundBalance,
                        accountsTotal = accountsTotal,
                        cashBal = cashBal,
                        digitalBal = digitalBal,
                        topCategory = topCategory,
                        topCategoryPct = topCategoryPct,
                        categorySpend = lifestyleCategories,
                        monthlyTrend = monthlyTrend,
                        filtered = filtered,
                        selectedCategoryFilter = selectedCategoryFilter,
                        investedPaise = cashflow.investedPaise,
                        redeemedPaise = cashflow.redeemedPaise,
                        investmentByName = cashflow.investmentByName,
                    ),
                    onMoveSection = { from, to ->
                        haptics.select()
                        vm.moveHomeSection(from, to)
                    },
                    onToggleSpan = { section ->
                        haptics.select()
                        vm.toggleHomeSectionSpan(section)
                    },
                    onToggleHidden = { vm.setHideBalances(!isNetHidden) },
                    onOpenFunds = onOpenFunds,
                    onOpenAccounts = onOpenAccounts,
                    onOpenCategories = onOpenCategories,
                    onOpenExpenseActivity = onOpenExpenseActivity,
                    onCategorySelected = { selectedCategoryFilter = it },
                    onOpenTxn = onOpenTxn,
                    onAddCash = onAddCash,
                    onOpenHistory = onOpenHistory,
                    onSelectHaptic = { haptics.select() },
                )
            }
        }
    }
}

@Composable
private fun SetupCheckRow(
    done: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (done) scheme.primary else scheme.onSecondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            if (!done) {
                TextButton(onClick = onClick) { Text("Open") }
            }
        }
    }
}

internal data class MomMetrics(
    val incomePct: Float?,
    val expensePct: Float?,
    val lastIncomeLabel: String?,
    val lastExpenseLabel: String?,
)

/** Percent change current vs previous; null if previous is zero and current is zero. */
private fun pctChange(current: Long, previous: Long): Float? {
    if (previous == 0L && current == 0L) return 0f
    if (previous == 0L) return 100f
    return ((current - previous).toFloat() / previous.toFloat()) * 100f
}
