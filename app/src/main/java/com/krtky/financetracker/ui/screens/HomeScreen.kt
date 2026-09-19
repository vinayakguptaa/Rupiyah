package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.R
import com.krtky.financetracker.ui.components.HomeShimmerSkeleton
import com.krtky.financetracker.ui.components.chrome.ScreenHeader
import com.krtky.financetracker.ui.theme.Dimens
import com.krtky.financetracker.ui.theme.NavContentInsets
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.viewmodel.HomeViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddCash: () -> Unit = {},
    onOpenTabs: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    onOpenMonthFlow: (direction: String, group: MonthFlowGroup) -> Unit = { _, _ -> },
    /** Open Activity filtered to rows that still need a category. */
    onOpenClassifyInbox: () -> Unit = {},
    /** Open Settings detail (e.g. LLM / banks). */
    onOpenSettingsSection: (String) -> Unit = {},
    onOpenTabDetail: (tabId: Long) -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val homeCashflow by vm.homeCashflow.collectAsStateWithLifecycle()
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    val openTabs by vm.openTabs.collectAsStateWithLifecycle()
    val paymentBalances by vm.paymentBalances.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val initialLoaded by vm.initialLoaded.collectAsStateWithLifecycle()
    val isNetHidden by vm.hideBalances.collectAsStateWithLifecycle()
    val pendingCount by vm.pendingCount.collectAsStateWithLifecycle()
    val setupChecklist by vm.setupChecklist.collectAsStateWithLifecycle()
    val activeAccounts by vm.activeAccounts.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()

    // This month: every credit/debit except self-transfer and tab-transfer.
    val income = homeCashflow.summary.incomePaise
    val spent = homeCashflow.summary.expensePaise
    val fundBalance = openTabs.sumOf { it.balancePaise }
    val cashBal = paymentBalances.entries
        .firstOrNull { it.key.equals("Cash", ignoreCase = true) }
        ?.value ?: 0L
    val digitalBal = paymentBalances.entries
        .filter { !it.key.equals("Cash", ignoreCase = true) }
        .sumOf { it.value }
    val accountsTotal = cashBal + digitalBal
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
        "$greetingBase, ${displayName.trim()}"
    } else {
        greetingBase
    }
    val monthLabel = Calendar.getInstance()
        .getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale.getDefault()) ?: "This month"

    var heroVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        heroVisible = true
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
                ScreenHeader(
                    title = greeting,
                    subtitle = monthLabel,
                )
            }

            if (pendingCount > 0) {
                item {
                    Surface(
                        onClick = {
                            haptics.select()
                            onOpenClassifyInbox()
                        },
                        shape = MaterialTheme.shapes.large,
                        color = scheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(scheme.primary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                stringResource(R.string.home_pending_classify, pendingCount),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = scheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            if (setupChecklist.visible) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = scheme.surfaceContainerHigh,
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
                                    color = scheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = {
                                    haptics.select()
                                    vm.dismissSetupChecklist()
                                }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.home_setup_dismiss),
                                        tint = scheme.onSurfaceVariant,
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
                    data = HomeDashboardData(
                        heroVisible = heroVisible,
                        availableBalance = accountsTotal,
                        income = income,
                        spent = spent,
                        monthLabel = monthLabel,
                        isNetHidden = isNetHidden,
                        tabs = openTabs.ifEmpty { tabs },
                        fundBalance = fundBalance,
                        cashBal = cashBal,
                        digitalBal = digitalBal,
                        categoryNetSpend = homeCashflow.categoryNetSpend,
                        sourceNetSpend = homeCashflow.sourceNetSpend,
                        expenseByCategory = homeCashflow.categorySpend,
                        expenseBySource = homeCashflow.expenseBySource,
                        incomeByCategory = homeCashflow.incomeByCategory,
                        incomeBySource = homeCashflow.incomeBySource,
                        activeAccountIds = activeAccounts.map { it.id }.toSet(),
                    ),
                    onToggleHidden = { vm.setHideBalances(!isNetHidden) },
                    onOpenTabs = onOpenTabs,
                    onOpenAccounts = onOpenAccounts,
                    onOpenMonthFlow = onOpenMonthFlow,
                    onSelectHaptic = { haptics.select() },
                    onOpenTabDetail = onOpenTabDetail,
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
                tint = if (done) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (!done) {
                TextButton(onClick = onClick) { Text("Open") }
            }
        }
    }
}
