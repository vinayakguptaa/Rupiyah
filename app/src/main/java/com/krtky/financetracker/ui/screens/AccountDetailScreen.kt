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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.CategoryFilterOption
import com.krtky.financetracker.ui.components.EmptyState
import com.krtky.financetracker.ui.components.FundFilterOption
import com.krtky.financetracker.ui.components.TransactionCard
import com.krtky.financetracker.ui.components.TransactionFilterBar
import com.krtky.financetracker.ui.components.TransactionSortButton
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.categoryColor
import com.krtky.financetracker.ui.util.downloadTransactionsCsv
import com.krtky.financetracker.ui.util.formatDateTime
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.onCategoryColor
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.util.timeRangeSubtitle
import com.krtky.financetracker.ui.viewmodel.AccountDetailViewModel

@Composable
fun AccountDetailScreen(
    accountId: Long,
    accountName: String,
    onBack: () -> Unit,
    onOpenTxn: (String) -> Unit,
    initialType: TransactionType? = null,
    vm: AccountDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(accountId, accountName, initialType) {
        vm.load(accountId, accountName)
        vm.setType(initialType)
    }
    val txns by vm.transactions.collectAsStateWithLifecycle()
    val net by vm.netPaise.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val type by vm.typeFilter.collectAsStateWithLifecycle()
    val categoryId by vm.categoryFilter.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val fundId by vm.fundFilter.collectAsStateWithLifecycle()
    val funds by vm.funds.collectAsStateWithLifecycle()
    val sortOrder by vm.sortOrder.collectAsStateWithLifecycle()
    val timeRange by vm.timeRange.collectAsStateWithLifecycle()
    val customFrom by vm.customFrom.collectAsStateWithLifecycle()
    val customTo by vm.customTo.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val dateFmt = timeRangeSubtitle(timeRange, customFrom, customTo)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Surface(
                onClick = onBack,
                shape = MaterialTheme.shapes.extraLarge,
                color = scheme.surfaceContainerHigh,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title.ifBlank { accountName },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    dateFmt,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
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
                    val name = title.ifBlank { accountName }.ifBlank { "account" }
                    downloadTransactionsCsv(context, txns, "account_$name")
                },
                enabled = txns.isNotEmpty(),
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = "Download CSV")
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = scheme.primaryContainer,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    when (type) {
                        TransactionType.DEBIT -> "Spent this period"
                        TransactionType.CREDIT -> "Received this period"
                        else -> "Net this period"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
                Text(
                    when (type) {
                        TransactionType.DEBIT ->
                            txns.filter { it.type == TransactionType.DEBIT }.sumOf { it.amountPaise }.inr()
                        TransactionType.CREDIT ->
                            txns.filter { it.type == TransactionType.CREDIT }.sumOf { it.amountPaise }.inr()
                        else -> net.inr()
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer,
                )
                Text(
                    "${txns.size} transaction${if (txns.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TransactionFilterBar(
            type = type,
            paymentMethod = null,
            categoryId = categoryId,
            categories = categories.map { CategoryFilterOption(it.id, it.name) },
            bankAccounts = emptyList(),
            fundId = fundId,
            funds = funds.map { FundFilterOption(it.fund.id, it.fund.name) },
            timeRange = timeRange,
            customFromMillis = customFrom,
            customToMillis = customTo,
            onTypeChange = {
                haptics.select()
                vm.setType(it)
            },
            onPaymentChange = {},
            onCategoryChange = {
                haptics.select()
                vm.setCategory(it)
            },
            onFundChange = {
                haptics.select()
                vm.setFund(it)
            },
            onTimeRangeChange = {
                haptics.select()
                vm.setTimeRange(it)
            },
            onCustomRange = vm::setCustomRange,
            onClearAll = vm::clearFilters,
            showCategoryFilter = true,
            showBankFilter = false,
            showFundFilter = true,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Transactions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        if (txns.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "No transactions",
                body = "Nothing matches the current filters for this account.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(txns, key = { it.id }) { t ->
                    val party = t.counterparty ?: t.note ?: "Transaction"
                    val sign = if (t.type == TransactionType.DEBIT) "-" else "+"
                    val catColor = categoryColor(t.categoryColor)
                    TransactionCard(
                        title = party,
                        subtitle = listOfNotNull(
                            t.occurredAt.formatDateTime(),
                            t.categoryName,
                        ).joinToString(" · "),
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
}
