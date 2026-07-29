package com.krtky.financetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.TransactionCard
import com.krtky.financetracker.ui.components.DeleteConfirmSheet
import com.krtky.financetracker.ui.components.EmptyState
import com.krtky.financetracker.ui.components.TransactionFilterBar
import com.krtky.financetracker.ui.components.TransactionSortButton
import com.krtky.financetracker.ui.components.chrome.ScreenHeader
import com.krtky.financetracker.ui.navigation.ActivityFilterKeys
import com.krtky.financetracker.ui.navigation.consumeActivityFilters
import com.krtky.financetracker.ui.navigation.consumeClearActivityFilters
import com.krtky.financetracker.ui.theme.Dimens
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.theme.NavContentInsets
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.categoryColor
import com.krtky.financetracker.ui.util.formatDateTime
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.onCategoryColor
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.util.downloadTransactionsCsv
import com.krtky.financetracker.ui.viewmodel.TransactionsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(
    onOpen: (String) -> Unit,
    onAddTransaction: () -> Unit = {},
    savedStateHandle: SavedStateHandle? = null,
    vm: TransactionsViewModel = hiltViewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val type by vm.typeFilter.collectAsStateWithLifecycle()
    val payment by vm.paymentFilter.collectAsStateWithLifecycle()
    val categoryId by vm.categoryFilter.collectAsStateWithLifecycle()
    val fundId by vm.fundFilter.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val funds by vm.funds.collectAsStateWithLifecycle()
    val bankAccounts by vm.bankAccounts.collectAsStateWithLifecycle()
    val sortOrder by vm.sortOrder.collectAsStateWithLifecycle()
    val timeRange by vm.timeRange.collectAsStateWithLifecycle()
    val customFrom by vm.customFrom.collectAsStateWithLifecycle()
    val customTo by vm.customTo.collectAsStateWithLifecycle()
    val items by vm.transactions.collectAsStateWithLifecycle()
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val grouped = remember(items) { groupByMonth(items) }

    // Deep-link filters from Home (SavedStateHandle on transactions route)
    val applyCategoryFlag by remember(savedStateHandle) {
        savedStateHandle?.getStateFlow(ActivityFilterKeys.APPLY_CATEGORY, false)
            ?: kotlinx.coroutines.flow.MutableStateFlow(false)
    }.collectAsStateWithLifecycle()
    val filterTypeName by remember(savedStateHandle) {
        savedStateHandle?.getStateFlow(ActivityFilterKeys.TYPE, null as String?)
            ?: kotlinx.coroutines.flow.MutableStateFlow(null as String?)
    }.collectAsStateWithLifecycle()
    val filterPayment by remember(savedStateHandle) {
        savedStateHandle?.getStateFlow(ActivityFilterKeys.PAYMENT, null as String?)
            ?: kotlinx.coroutines.flow.MutableStateFlow(null as String?)
    }.collectAsStateWithLifecycle()
    val clearFlag by remember(savedStateHandle) {
        savedStateHandle?.getStateFlow(ActivityFilterKeys.CLEAR, false)
            ?: kotlinx.coroutines.flow.MutableStateFlow(false)
    }.collectAsStateWithLifecycle()

    LaunchedEffect(applyCategoryFlag, filterTypeName, filterPayment) {
        val handle = savedStateHandle ?: return@LaunchedEffect
        val shot = handle.consumeActivityFilters() ?: return@LaunchedEffect
        vm.setPayment(shot.payment)
        vm.setType(shot.type)
        if (shot.applyCategory) {
            vm.setCategory(shot.categoryId)
        }
    }

    LaunchedEffect(clearFlag) {
        val handle = savedStateHandle ?: return@LaunchedEffect
        if (!handle.consumeClearActivityFilters()) return@LaunchedEffect
        vm.setType(null)
        vm.setPayment(null)
        vm.setCategory(null)
    }

    LaunchedEffect(searchOpen) {
        if (searchOpen) searchFocus.requestFocus()
    }

    val filterSummary = remember(items) {
        val count = items.size
        val expense = items.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPaise }
        val income = items.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }
        Triple(count, expense, income)
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(Modifier.padding(horizontal = Dimens.ScreenHorizontal)) {
            ScreenHeader(
                title = if (selectedIds.isEmpty()) "Activity" else "${selectedIds.size} selected",
                actions = {
                    if (selectedIds.isEmpty()) {
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
                                searchOpen = !searchOpen
                                if (!searchOpen) vm.setQuery("")
                            },
                        ) {
                            Icon(
                                if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = stringResource(
                                    if (searchOpen) R.string.cd_close_search else R.string.cd_search_transactions,
                                ),
                                tint = scheme.onSurface,
                            )
                        }
                        IconButton(
                            onClick = {
                                haptics.select()
                                downloadTransactionsCsv(context, items, "activity")
                            },
                            enabled = items.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = stringResource(R.string.cd_download_csv),
                                tint = scheme.onSurface,
                            )
                        }
                    } else {
                        TextButton(onClick = { selectedIds = emptySet() }) { Text("Cancel") }
                    }
                },
            )
            Spacer(Modifier.height(Dimens.SectionGap))

            AnimatedVisibility(
                visible = searchOpen && selectedIds.isEmpty(),
                enter = fadeIn(M3EMotion.effectsDefault()) + expandVertically(M3EMotion.spatialDefault()),
                exit = fadeOut(M3EMotion.effectsDefault()) + shrinkVertically(M3EMotion.spatialDefault()),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocus)
                        .padding(bottom = Dimens.CardInnerGap),
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
            }

            TransactionFilterBar(
                type = type,
                paymentMethod = payment,
                categoryId = categoryId,
                categories = categories.map {
                    com.krtky.financetracker.ui.components.CategoryFilterOption(it.id, it.name)
                },
                bankAccounts = bankAccounts,
                fundId = fundId,
                funds = funds.map {
                    com.krtky.financetracker.ui.components.FundFilterOption(it.fund.id, it.fund.name)
                },
                timeRange = timeRange,
                customFromMillis = customFrom,
                customToMillis = customTo,
                onTypeChange = {
                    haptics.select()
                    vm.setType(it)
                },
                onCategoryChange = {
                    haptics.select()
                    vm.setCategory(it)
                },
                onPaymentChange = {
                    haptics.select()
                    vm.setPayment(it)
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
            )

            // Filtered summary strip: count · −expense · +income
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.CardInnerGap))
                val (count, expense, income) = filterSummary
                Text(
                    buildString {
                        append(count)
                        append(if (count == 1) " txn" else " txns")
                        if (expense > 0L) append(" · −${expense.inr()}")
                        if (income > 0L) append(" · +${income.inr()}")
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = Dimens.ScreenHorizontal,
                end = Dimens.ScreenHorizontal,
                top = Dimens.CardInnerGap,
                bottom = if (selectedIds.isNotEmpty()) {
                    NavContentInsets.bottom + 56.dp
                } else {
                    NavContentInsets.bottom
                },
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.ReceiptLong,
                        title = stringResource(R.string.empty_activity_title),
                        body = stringResource(R.string.empty_activity_body),
                        actionLabel = stringResource(R.string.empty_activity_action),
                        onAction = onAddTransaction,
                    )
                }
            } else {
                grouped.forEach { (monthKey, monthItems) ->
                    item(key = "hdr_$monthKey") {
                        val monthTotal = monthItems.sumOf {
                            if (it.type == TransactionType.INCOME) it.amountPaise else -it.amountPaise
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = scheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    }
                    items(monthItems, key = { it.id }) { t ->
                        val party = t.counterparty ?: t.merchant ?: t.note ?: "Transaction"
                        val sign = if (t.type == TransactionType.EXPENSE) "-" else "+"
                        val catColor = categoryColor(t.categoryColor)
                        TransactionCard(
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
                            icon = CategoryIcons.iconFor(t.categoryIcon, t.categoryName),
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
                            iconContainerColor = catColor,
                            iconTint = catColor?.let { onCategoryColor(it) },
                        )
                    }
                }
            }
        }
    }

    // Sticky multi-select bar for discoverability (above floating nav)
    if (selectedIds.isNotEmpty()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = NavContentInsets.bottom)
                .padding(horizontal = Dimens.ScreenHorizontal),
            shape = MaterialTheme.shapes.extraLarge,
            color = scheme.errorContainer,
            tonalElevation = 4.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${selectedIds.size} selected",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onErrorContainer,
                )
                Button(
                    onClick = {
                        haptics.select()
                        showDeleteConfirm = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.error,
                        contentColor = scheme.onError,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("Delete (${selectedIds.size})")
                }
            }
        }
    }
    } // end Box

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
