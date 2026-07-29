package com.krtky.financetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.FundBalance
import com.krtky.financetracker.ui.components.EmptyState
import com.krtky.financetracker.ui.components.SpendRatioRing
import com.krtky.financetracker.ui.components.chrome.ScreenHeader
import com.krtky.financetracker.ui.theme.Dimens
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.theme.NavContentInsets
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.inrCompact
import com.krtky.financetracker.ui.viewmodel.FundsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundsScreen(
    onOpenFund: (Long) -> Unit = {},
    /** Incremented by the floating nav FAB to open create sheet. */
    createRequestTick: Int = 0,
    vm: FundsViewModel = hiltViewModel(),
) {
    val funds by vm.funds.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newAmount by remember { mutableStateOf("") }
    var adjustAmount by remember { mutableStateOf("") }
    var adjustFundId by remember { mutableStateOf<Long?>(null) }
    var adjustFundName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40)
        ready = true
    }
    LaunchedEffect(createRequestTick) {
        if (createRequestTick > 0) {
            showCreate = true
        }
    }

    // Remaining = cash in pots; spent bar = empty portion of fixed limits
    val totalBudget = funds.sumOf { it.limitPaise() }
    val totalRemaining = funds.sumOf { it.remainingOfLimitPaise() }
    val totalSpent = (totalBudget - totalRemaining).coerceAtLeast(0L)
    val spentRatio = if (totalBudget > 0) {
        (totalSpent.toFloat() / totalBudget.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Dimens.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
            contentPadding = PaddingValues(bottom = NavContentInsets.bottom),
        ) {
            item {
                ScreenHeader(
                    title = "Funds",
                    subtitle = "Envelope budgets you can credit and spend from",
                )
            }

            // Hero: shared ring + Total / Spent / Remaining
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = scheme.surfaceContainerHigh,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SpendRatioRing(
                            ratio = spentRatio,
                            size = 112.dp,
                            stroke = 12.dp,
                            progressColor = if (spentRatio >= 1f) scheme.error else scheme.primary,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "${(spentRatio * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (spentRatio >= 1f) scheme.error else scheme.onSurface,
                                )
                                Text(
                                    stringResource(R.string.funds_spent),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Dimens.CardInnerGap),
                        ) {
                            FundStatChip(
                                label = stringResource(R.string.funds_total),
                                value = totalBudget.inrCompact(),
                                icon = Icons.Default.AccountBalanceWallet,
                            )
                            FundStatChip(
                                label = stringResource(R.string.funds_spent),
                                value = totalSpent.inrCompact(),
                                icon = Icons.Default.CreditCard,
                            )
                            FundStatChip(
                                label = "Remaining",
                                value = totalRemaining.inrCompact(),
                                icon = Icons.Default.AccountBalanceWallet,
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${funds.size} fund${if (funds.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (funds.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = stringResource(R.string.empty_funds_title),
                        body = stringResource(R.string.empty_funds_body),
                        actionLabel = stringResource(R.string.empty_funds_action),
                        onAction = { showCreate = true },
                    )
                }
            }

            itemsIndexed(funds, key = { _, f -> f.fund.id }) { index, f ->
                AnimatedVisibility(
                    visible = ready,
                    enter = fadeIn(M3EMotion.effectsDefault()) +
                        slideInVertically(M3EMotion.spatialDefault()) { it / 8 },
                ) {
                    val colors = listOf(
                        scheme.primary to scheme.onPrimary,
                        scheme.tertiary to scheme.onTertiary,
                        scheme.secondary to scheme.onSecondary,
                        scheme.primaryContainer to scheme.onPrimaryContainer,
                        scheme.tertiaryContainer to scheme.onTertiaryContainer,
                    )[index % 5]
                    BudgetStyleFundCard(
                        fund = f,
                        headerColor = colors.first,
                        onHeaderColor = colors.second,
                        onOpen = { onOpenFund(f.fund.id) },
                        onAdjust = {
                            adjustFundId = f.fund.id
                            adjustFundName = f.fund.name
                            adjustAmount = if (f.limitPaise() > 0L) {
                                val r = f.limitPaise() / 100.0
                                if (r == r.toLong().toDouble()) {
                                    r.toLong().toString()
                                } else {
                                    String.format(java.util.Locale.US, "%.2f", r)
                                }
                            } else {
                                ""
                            }
                            showAdjust = true
                        },
                    )
                }
            }

        }
        // FAB lives beside the floating navbar (MainActivity FloatingBottomNav)
    }

    if (showCreate) {
        ModalBottomSheet(
            onDismissRequest = { showCreate = false },
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
                Text("New fund", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    value = newAmount,
                    onValueChange = { newAmount = it },
                    label = { Text("Fund amount ₹") },
                    placeholder = { Text("e.g. 1500") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                )
                Button(
                    onClick = {
                        scope.launch {
                            vm.create(newName, newAmount)
                            newName = ""
                            newAmount = ""
                            showCreate = false
                        }
                    },
                    enabled = newName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Create") }
                OutlinedButton(
                    onClick = { showCreate = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Cancel") }
            }
        }
    }

    if (showAdjust && adjustFundId != null) {
        ModalBottomSheet(
            onDismissRequest = { showAdjust = false },
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
                Text("Edit $adjustFundName", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Set the fund amount (starting pot). Remaining = this amount + income − expenses on this fund.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = adjustAmount,
                    onValueChange = { adjustAmount = it },
                    label = { Text("Fund amount ₹") },
                    placeholder = { Text("e.g. 1500") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                )
                Button(
                    onClick = {
                        scope.launch {
                            vm.adjust(adjustFundId!!, adjustAmount)
                            showAdjust = false
                            adjustAmount = ""
                        }
                    },
                    enabled = adjustAmount.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Save amount") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            vm.delete(adjustFundId!!)
                            showAdjust = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Delete fund", color = scheme.error) }
                OutlinedButton(
                    onClick = { showAdjust = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun FundStatChip(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainerHighest,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Original envelope-style fund card: colored header + remaining + usage bar. */
@Composable
private fun BudgetStyleFundCard(
    fund: FundBalance,
    headerColor: Color,
    onHeaderColor: Color,
    onOpen: () -> Unit,
    onAdjust: () -> Unit,
) {
    val limit = fund.limitPaise()
    val left = fund.remainingOfLimitPaise() // cash in pot
    val spentRatio = fund.spentRatio() // empty portion of limit
    val overspent = fund.isOverspent()
    val scheme = MaterialTheme.colorScheme
    val cardHeader = if (overspent) scheme.error else headerColor
    val cardOnHeader = if (overspent) scheme.onError else onHeaderColor
    val progressColor = if (overspent) scheme.error else headerColor

    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerHigh,
    ) {
        Column {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = cardHeader,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            fund.fund.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = cardOnHeader,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (overspent) {
                                "Over by ${(-fund.balancePaise).coerceAtLeast(0L).inr()} · of ${limit.inr()}"
                            } else {
                                "${left.inr()} left of ${limit.inr()}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = cardOnHeader.copy(alpha = 0.9f),
                        )
                    }
                    Surface(
                        onClick = onAdjust,
                        shape = CircleShape,
                        // Soft wash of the header content color so the icon stays legible
                        color = cardOnHeader.copy(alpha = 0.18f),
                    ) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Tune, null, tint = cardOnHeader, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Used", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Text(
                        "${(spentRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (overspent) scheme.error else scheme.onSurface,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { spentRatio.coerceAtMost(1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = progressColor,
                    trackColor = scheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "In ${fund.creditedPaise.inr()} · Out ${fund.debitedPaise.inr()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
