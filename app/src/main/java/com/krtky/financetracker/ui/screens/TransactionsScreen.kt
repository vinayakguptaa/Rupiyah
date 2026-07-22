package com.krtky.financetracker.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.ActivityTxnCard
import com.krtky.financetracker.ui.components.DeleteConfirmSheet
import com.krtky.financetracker.ui.components.EmptyState
import com.krtky.financetracker.ui.components.TransactionFilterBar
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.formatDateTime
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.viewmodel.TransactionsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(
    onOpen: (String) -> Unit,
    vm: TransactionsViewModel = hiltViewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val type by vm.typeFilter.collectAsStateWithLifecycle()
    val payment by vm.paymentFilter.collectAsStateWithLifecycle()
    val timeRange by vm.timeRange.collectAsStateWithLifecycle()
    val customFrom by vm.customFrom.collectAsStateWithLifecycle()
    val customTo by vm.customTo.collectAsStateWithLifecycle()
    val items by vm.transactions.collectAsStateWithLifecycle()
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    val grouped = remember(items) { groupByMonth(items) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (selectedIds.isEmpty()) "Activity" else "${selectedIds.size} selected",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            if (selectedIds.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { selectedIds = emptySet() }) { Text("Cancel") }
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = scheme.error,
                        ),
                    ) { Text("Delete") }
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search transactions") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = scheme.surfaceContainerHigh,
                    unfocusedContainerColor = scheme.surfaceContainerHigh,
                    focusedBorderColor = scheme.outlineVariant,
                    unfocusedBorderColor = scheme.outlineVariant,
                ),
            )
            Spacer(Modifier.height(12.dp))
            TransactionFilterBar(
                type = type,
                paymentMethod = payment,
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
                onTimeRangeChange = {
                    haptics.select()
                    vm.setTimeRange(it)
                },
                onCustomRange = vm::setCustomRange,
                onClearAll = vm::clearFilters,
            )
            Spacer(Modifier.height(4.dp))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.ReceiptLong,
                        title = stringResource(R.string.empty_activity_title),
                        body = stringResource(R.string.empty_activity_body),
                    )
                }
            } else {
                grouped.forEach { (monthKey, monthItems) ->
                    item(key = "hdr_$monthKey") {
                        val monthTotal = monthItems.sumOf {
                            if (it.type == TransactionType.INCOME) it.amountPaise else -it.amountPaise
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Column {
                                Text(
                                    monthKey.substringBefore('\n'),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onSurfaceVariant,
                                )
                                Text(
                                    monthKey.substringAfter('\n', monthKey),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                monthTotal.inr(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                            )
                        }
                    }
                    items(monthItems, key = { it.id }) { t ->
                        val party = t.counterparty ?: t.merchant ?: t.note ?: "Transaction"
                        val sign = if (t.type == TransactionType.EXPENSE) "-" else "+"
                        ActivityTxnCard(
                            title = party,
                            subtitle = listOfNotNull(
                                t.occurredAt.formatDateTime(),
                                t.categoryName,
                                t.note?.take(32),
                                t.fundName,
                                t.paymentMethod,
                            ).joinToString(" · "),
                            amount = "$sign${t.amountPaise.inr()}",
                            amountColor = if (t.type == TransactionType.EXPENSE)
                                scheme.error
                            else scheme.primary,
                            icon = CategoryIcons.iconFor(null, t.categoryName),
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    selectedIds =
                                        if (t.id in selectedIds) selectedIds - t.id
                                        else selectedIds + t.id
                                } else {
                                    onOpen(t.id)
                                }
                            },
                            selected = t.id in selectedIds,
                            onLongClick = {
                                selectedIds =
                                    if (t.id in selectedIds) selectedIds - t.id
                                    else selectedIds + t.id
                            },
                            visible = true,
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmSheet(
            title = "Delete selected transactions?",
            message = "This will remove ${selectedIds.size} transaction(s) from your lists.",
            onDismiss = { showDeleteConfirm = false },
            onConfirmDelete = {
                haptics.longPress()
                vm.delete(selectedIds)
                selectedIds = emptySet()
                showDeleteConfirm = false
            },
        )
    }
}

private fun groupByMonth(items: List<Transaction>): List<Pair<String, List<Transaction>>> {
    val fmtYear = SimpleDateFormat("yyyy", Locale.getDefault())
    val fmtMonth = SimpleDateFormat("MMMM", Locale.getDefault())
    return items
        .groupBy { t ->
            "${fmtYear.format(Date(t.occurredAt))}\n${fmtMonth.format(Date(t.occurredAt))}"
        }
        .toList()
}
