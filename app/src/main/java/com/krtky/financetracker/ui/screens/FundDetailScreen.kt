package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.TransactionCard
import com.krtky.financetracker.ui.components.CategoryFilterOption
import com.krtky.financetracker.ui.components.DeleteConfirmSheet
import com.krtky.financetracker.ui.components.EmptyState
import com.krtky.financetracker.ui.components.FundTransferSheet
import com.krtky.financetracker.ui.components.M3LoadingIndicator
import com.krtky.financetracker.ui.components.TransactionFilterBar
import com.krtky.financetracker.ui.components.TransactionSortButton
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.categoryColor
import com.krtky.financetracker.ui.util.downloadTransactionsCsv
import com.krtky.financetracker.ui.util.onCategoryColor
import com.krtky.financetracker.ui.util.formatDateTime
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.viewmodel.FundDetailViewModel
import kotlinx.coroutines.launch

@Composable
fun FundDetailScreen(
    fundId: Long,
    onBack: () -> Unit,
    onOpenTxn: (String) -> Unit,
    vm: FundDetailViewModel = hiltViewModel(),
) {
    val fund by vm.fund.collectAsStateWithLifecycle()
    val txns by vm.transactions.collectAsStateWithLifecycle()
    val type by vm.typeFilter.collectAsStateWithLifecycle()
    val payment by vm.paymentFilter.collectAsStateWithLifecycle()
    val categoryId by vm.categoryFilter.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val bankAccounts by vm.bankAccounts.collectAsStateWithLifecycle()
    val sortOrder by vm.sortOrder.collectAsStateWithLifecycle()
    val timeRange by vm.timeRange.collectAsStateWithLifecycle()
    val customFrom by vm.customFrom.collectAsStateWithLifecycle()
    val customTo by vm.customTo.collectAsStateWithLifecycle()
    val allFunds by vm.allFunds.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }

    LaunchedEffect(fundId) { vm.load(fundId) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                fund?.fund?.name ?: "Fund",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TransactionSortButton(
                sort = sortOrder,
                onSortChange = {
                    haptics.select()
                    vm.setSortOrder(it)
                },
            )
            IconButton(
                onClick = {
                    haptics.select()
                    val name = fund?.fund?.name ?: "fund"
                    downloadTransactionsCsv(context, txns, "fund_$name")
                },
                enabled = txns.isNotEmpty(),
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = "Download CSV")
            }
            if (allFunds.size > 1) {
                IconButton(onClick = { showTransfer = true }) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Transfer")
                }
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete fund", tint = scheme.error)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (fund == null) {
            Column(
                Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                M3LoadingIndicator()
            }
        } else {
            val overspent = fund!!.isOverspent()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = if (overspent) scheme.errorContainer else scheme.primaryContainer,
            ) {
                val onC = if (overspent) scheme.onErrorContainer else scheme.onPrimaryContainer
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (overspent) "Overspent" else "Left in fund",
                        style = MaterialTheme.typography.labelLarge,
                        color = onC.copy(alpha = 0.75f),
                    )
                    Text(
                        fund!!.balancePaise.inr(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = onC,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${fund!!.remainingOfLimitPaise().inr()} left of ${fund!!.limitPaise().inr()} · ${(fund!!.spentRatio() * 100).toInt()}% used",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onC.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "In ${fund!!.creditedPaise.inr()} · Out ${fund!!.debitedPaise.inr()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onC.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TransactionFilterBar(
                type = type,
                paymentMethod = payment,
                categoryId = categoryId,
                categories = categories.map { CategoryFilterOption(it.id, it.name) },
                bankAccounts = bankAccounts,
                timeRange = timeRange,
                customFromMillis = customFrom,
                customToMillis = customTo,
                onTypeChange = {
                    haptics.select()
                    vm.setType(it)
                },
                onPaymentChange = {
                    haptics.select()
                    vm.setPayment(it)
                },
                onCategoryChange = {
                    haptics.select()
                    vm.setCategory(it)
                },
                onTimeRangeChange = {
                    haptics.select()
                    vm.setTimeRange(it)
                },
                onCustomRange = vm::setCustomRange,
                onClearAll = vm::clearFilters,
                showFundFilter = false,
            )
            Spacer(Modifier.height(12.dp))
            Text("Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (txns.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = stringResource(R.string.empty_fund_txns_title),
                    body = stringResource(R.string.empty_fund_txns_body),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(txns, key = { it.id }) { t ->
                    val party = t.counterparty ?: t.merchant ?: t.note ?: "Transaction"
                    val sign = if (t.type == TransactionType.DEBIT) "-" else "+"
                    val catColor = categoryColor(t.categoryColor)
                    TransactionCard(
                        title = party,
                        subtitle = listOfNotNull(t.occurredAt.formatDateTime(), t.categoryName, t.paymentMethod)
                            .joinToString(" · "),
                        amount = "$sign${t.amountPaise.inr()}",
                        amountColor = if (t.type == TransactionType.DEBIT) scheme.error else scheme.primary,
                        icon = CategoryIcons.iconFor(t.categoryIcon, t.categoryName),
                        onClick = { onOpenTxn(t.id) },
                        iconContainerColor = catColor,
                        iconTint = catColor?.let { onCategoryColor(it) },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        DeleteConfirmSheet(
            title = "Delete fund?",
            message = "This archives the fund. Existing transactions keep their history.",
            onDismiss = { confirmDelete = false },
            onConfirmDelete = {
                scope.launch {
                    if (vm.deleteFund()) onBack()
                    confirmDelete = false
                }
            },
            deleteLabel = "Delete fund",
        )
    }

    if (showTransfer && fund != null) {
        FundTransferSheet(
            sourceFundId = fund!!.fund.id,
            sourceFundName = fund!!.fund.name,
            allFunds = allFunds,
            onDismiss = { showTransfer = false },
            onTransfer = vm::transferBetweenFunds,
        )
    }
}
