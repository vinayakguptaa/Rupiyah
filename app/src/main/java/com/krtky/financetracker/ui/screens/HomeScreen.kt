package com.krtky.financetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.ActivityTxnCard
import com.krtky.financetracker.ui.components.BalanceHeroCard
import com.krtky.financetracker.ui.components.CategoryInteractivePieChart
import com.krtky.financetracker.ui.components.EmptyState
import com.krtky.financetracker.ui.components.FundsWaveSummary
import com.krtky.financetracker.ui.components.HomeShimmerSkeleton
import com.krtky.financetracker.ui.components.OutlinePillButton
import com.krtky.financetracker.ui.components.MonthlyExpenseChart
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.util.formatDateTime
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.viewmodel.HomeViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenTxn: (String) -> Unit,
    onAddCash: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenFunds: () -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val funds by vm.funds.collectAsStateWithLifecycle()
    val paymentBalances by vm.paymentBalances.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val categorySpend by vm.categorySpend.collectAsStateWithLifecycle()
    val monthlyTrend by vm.monthlyTrend.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val initialLoaded by vm.initialLoaded.collectAsStateWithLifecycle()
    var selectedCategoryFilter by remember { mutableStateOf<CategorySpend?>(null) }
    var isNetHidden by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()

    val income = summary.incomePaise
    val spent = summary.expensePaise
    val net = summary.netPaise
    val fundBalance = funds.sumOf { it.balancePaise }
    val fundsSubtitle = when {
        funds.isEmpty() -> "No envelopes yet"
        funds.size == 1 -> funds.first().fund.name
        else -> funds.take(3).joinToString(" · ") { it.fund.name } +
            if (funds.size > 3) " +${funds.size - 3}" else ""
    }
    val displayName by vm.displayName.collectAsStateWithLifecycle()
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greetingBase = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val greeting = if (displayName.isNotBlank()) "$greetingBase, $displayName" else greetingBase
    val monthLabel = Calendar.getInstance()
        .getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale.getDefault()) ?: "This month"

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
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // M3 large top-app-bar style: left-aligned headline + supporting text
                Text(
                    greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    color = scheme.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    monthLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
            }

            if (!initialLoaded) {
                item { HomeShimmerSkeleton() }
            } else {
                item {
                    AnimatedVisibility(
                        visible = heroVisible,
                        enter = fadeIn(M3EMotion.effectsDefault()) +
                            slideInVertically(M3EMotion.spatialDefault()) { it / 10 },
                        exit = fadeOut(),
                    ) {
                        val remainProg = if (income > 0) {
                            (net.toFloat() / income.toFloat()).coerceIn(0f, 1f)
                        } else if (spent > 0) 0f else 1f
                        BalanceHeroCard(
                            title = "Remaining this month",
                            balance = net.inr(),
                            monthLabel = monthLabel,
                            incomeLabel = "Income",
                            incomeValue = income.inr(),
                            expenseLabel = "Expense",
                            expenseValue = spent.inr(),
                            hidden = isNetHidden,
                            remainingProgress = remainProg,
                            onToggleHidden = {
                                haptics.select()
                                isNetHidden = !isNetHidden
                            },
                        )
                    }
                }

                item {
                    Surface(
                        onClick = onOpenFunds,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = scheme.surfaceContainerHigh,
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Available to spend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("Cash", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                                    Text((paymentBalances["Cash"] ?: 0L).inr(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = scheme.primary)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("UPI", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                                    Text((paymentBalances["Digital"] ?: 0L).inr(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = scheme.tertiary)
                                }
                            }
                            androidx.compose.material3.HorizontalDivider(color = scheme.outlineVariant)
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Icon(
                                        Icons.Default.AccountBalanceWallet,
                                        contentDescription = stringResource(R.string.cd_nav_funds),
                                        tint = scheme.tertiary,
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                    Column {
                                        Text("Remaining funds", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                        Text(fundsSubtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                                    }
                                }
                                Text(if (isNetHidden) "••••" else fundBalance.inr(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = scheme.tertiary)
                            }
                        }
                    }
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = scheme.surfaceContainerHigh,
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Spending", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                            Text("Tap the ring to filter by category", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                            CategoryInteractivePieChart(
                                categorySpends = categorySpend,
                                totalExpense = spent,
                                incomePaise = income,
                                goalLabel = if (income > 0) "of ${income.inr()}" else monthLabel,
                                size = 168.dp,
                                onCategorySelected = { selectedCategoryFilter = it },
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
                        MonthlyExpenseChart(
                            data = monthlyTrend,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                    }
                }

                item {
                    Text(
                        selectedCategoryFilter?.categoryName ?: "Recent",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (filtered.isEmpty()) {
                    item {
                        if (selectedCategoryFilter != null) {
                            EmptyState(
                                icon = Icons.Default.FilterAltOff,
                                title = stringResource(R.string.empty_category_filter_title),
                                body = stringResource(R.string.empty_category_filter_body),
                                actionLabel = stringResource(R.string.action_clear_filter),
                                onAction = { selectedCategoryFilter = null },
                            )
                        } else {
                            EmptyState(
                                icon = Icons.Default.ReceiptLong,
                                title = stringResource(R.string.empty_txns_title),
                                body = stringResource(R.string.empty_txns_body),
                                actionLabel = stringResource(R.string.empty_txns_action),
                                onAction = onAddCash,
                            )
                        }
                    }
                }

                itemsIndexed(filtered, key = { _, t -> t.id }) { _, t ->
                    val party = t.counterparty ?: t.merchant ?: t.paymentMethod ?: "Transaction"
                    val sign = if (t.type == TransactionType.EXPENSE) "-" else "+"
                    ActivityTxnCard(
                        title = party,
                        subtitle = listOfNotNull(
                            t.occurredAt.formatDateTime(),
                            t.categoryName,
                            t.note?.take(28),
                            t.paymentMethod,
                        ).joinToString(" · "),
                        amount = "$sign${t.amountPaise.inr()}",
                        amountColor = if (t.type == TransactionType.EXPENSE) scheme.error else scheme.primary,
                        icon = CategoryIcons.iconFor(null, t.categoryName),
                        onClick = { onOpenTxn(t.id) },
                        visible = true,
                    )
                }

                item {
                    OutlinePillButton(text = "Full history", icon = Icons.Default.History, onClick = onOpenHistory)
                }

                item {
                    FundsWaveSummary(
                        funds = funds,
                        hidden = isNetHidden,
                        onOpenFunds = onOpenFunds,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
