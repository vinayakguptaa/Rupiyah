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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.krtky.financetracker.ui.components.TransactionFilterBar
import com.krtky.financetracker.ui.components.TransferContainer
import com.krtky.financetracker.ui.components.TransferSheet
import com.krtky.financetracker.ui.components.M3LoadingIndicator
import com.krtky.financetracker.ui.components.TransactionSortButton
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.categoryColor
import com.krtky.financetracker.ui.util.downloadTransactionsCsv
import com.krtky.financetracker.ui.util.onCategoryColor
import com.krtky.financetracker.ui.util.formatDateTime
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.viewmodel.TabDetailViewModel
import kotlinx.coroutines.launch

@Composable
fun TabDetailScreen(
    tabId: Long,
    onBack: () -> Unit,
    onOpenTxn: (String) -> Unit,
    vm: TabDetailViewModel = hiltViewModel(),
) {
    val tab by vm.tab.collectAsStateWithLifecycle()
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
    val allTabs by vm.allTabs.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var showSettle by remember { mutableStateOf(false) }

    LaunchedEffect(tabId) { vm.load(tabId) }

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
                tab?.tab?.name ?: "Tab",
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
                    val name = tab?.tab?.name ?: "tab"
                    downloadTransactionsCsv(context, txns, "fund_$name")
                },
                enabled = txns.isNotEmpty(),
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = "Download CSV")
            }
            if (allTabs.size > 1) {
                IconButton(onClick = { showTransfer = true }) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Transfer")
                }
            }
            if (!(tab?.isSettled() ?: true)) {
                IconButton(onClick = { showSettle = true }) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Mark settled",
                        tint = scheme.primary,
                    )
                }
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete tab", tint = scheme.error)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (tab == null) {
            Column(
                Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                M3LoadingIndicator()
            }
        } else {
            val youOweThem = tab!!.youOweThem()
            val settled = tab!!.isSettled()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = if (youOweThem) scheme.errorContainer else scheme.primaryContainer,
            ) {
                val onC = if (youOweThem) scheme.onErrorContainer else scheme.onPrimaryContainer
                Column(Modifier.padding(16.dp)) {
                    Text(
                        when {
                            youOweThem -> "You owe them"
                            settled -> "Settled"
                            else -> "They owe you"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = onC.copy(alpha = 0.75f),
                    )
                    Text(
                        if (settled) "₹0" else tab!!.balancePaise.let { if (it < 0) -it else it }.inr(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = onC,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "In ${tab!!.creditedPaise.inr()} · Out ${tab!!.debitedPaise.inr()}",
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
                showTabFilter = false,
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
                    val party = t.counterparty ?: t.note ?: "Transaction"
                    val sign = if (t.type == TransactionType.DEBIT) "-" else "+"
                    val catColor = categoryColor(t.categoryColor)
                    TransactionCard(
                        title = party,
                        subtitle = listOfNotNull(t.occurredAt.formatDateTime(), t.categoryName, t.accountName)
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
            title = "Archive tab?",
            message = "This archives the tab. Existing transactions keep their history.",
            onDismiss = { confirmDelete = false },
            onConfirmDelete = {
                scope.launch {
                    if (vm.deleteTab()) onBack()
                    confirmDelete = false
                }
            },
            deleteLabel = "Archive tab",
        )
    }

    if (showTransfer && tab != null) {
        val sourceTab = tab!!.tab
        val containers = allTabs
            .filter { it.tab.id == sourceTab.id || !it.tab.archived }
            .map { TransferContainer(it.tab.id, it.tab.name, it.balancePaise.inr()) }
        TransferSheet(
            containers = containers,
            title = "Move money",
            subtitle = "Moves money between tabs. Not a spend.",
            fromLabel = "From tab",
            toLabel = "To tab",
            initialFromId = sourceTab.id,
            onDismiss = { showTransfer = false },
            onTransfer = vm::transferBetweenTabs,
        )
    }

    if (showSettle && tab != null) {
        val open = tab!!.balancePaise.let { if (it < 0) -it else it }
        ModalBottomSheet(
            onDismissRequest = { showSettle = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Mark settled?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Records a ${open.inr()} settlement on ${tab!!.tab.name} and brings the balance to zero.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        scope.launch {
                            vm.settleTab()
                            showSettle = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Mark settled") }
                OutlinedButton(
                    onClick = { showSettle = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Cancel") }
            }
        }
    }
}
