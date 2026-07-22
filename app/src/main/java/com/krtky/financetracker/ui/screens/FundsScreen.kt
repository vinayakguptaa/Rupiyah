package com.krtky.financetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.viewmodel.FundsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundsScreen(
    onOpenFund: (Long) -> Unit = {},
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

    val headerColors = listOf(
        scheme.primary to scheme.onPrimary,
        scheme.tertiary to scheme.onTertiary,
        scheme.secondary to scheme.onSecondary,
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Funds",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Envelope balances you can credit and debit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
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
                    val colors = headerColors[index % headerColors.size]
                    BudgetStyleFundCard(
                        fund = f,
                        headerColor = colors.first,
                        onHeaderColor = colors.second,
                        onOpen = { onOpenFund(f.fund.id) },
                        onAdjust = {
                            adjustFundId = f.fund.id
                            adjustFundName = f.fund.name
                            adjustAmount = ""
                            showAdjust = true
                        },
                    )
                }
            }

            item {
                Surface(
                    onClick = { showCreate = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = Color.Transparent,
                    border = BorderStroke(1.5.dp, scheme.outlineVariant),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.cd_add_fund),
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                Spacer(Modifier.height(88.dp))
            }
        }

        FloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            shape = MaterialTheme.shapes.large,
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_fund))
        }
    }

    if (showCreate) {
        ModalBottomSheet(
            onDismissRequest = { showCreate = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
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
                    label = { Text("Opening amount ₹") },
                    placeholder = { Text("0") },
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
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Adjust $adjustFundName", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Use + for credit, − for debit (e.g. 500 or -200)",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = adjustAmount,
                    onValueChange = { adjustAmount = it },
                    label = { Text("Amount ₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                ) { Text("Apply") }
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
private fun BudgetStyleFundCard(
    fund: FundBalance,
    headerColor: Color,
    onHeaderColor: Color,
    onOpen: () -> Unit,
    onAdjust: () -> Unit,
) {
    val credited = fund.creditedPaise.coerceAtLeast(0L)
    val debited = fund.debitedPaise.coerceAtLeast(0L)
    val spentRatio = if (credited > 0) {
        (debited.toFloat() / credited.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val left = credited - debited
    val scheme = MaterialTheme.colorScheme
    val remaining = (credited - debited).coerceAtLeast(0L)

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
                color = headerColor,
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
                            color = onHeaderColor,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${left.inr()} remaining",
                            style = MaterialTheme.typography.titleMedium,
                            color = onHeaderColor.copy(alpha = 0.9f),
                        )
                    }
                    Surface(
                        onClick = onAdjust,
                        shape = CircleShape,
                        color = onHeaderColor.copy(alpha = 0.18f),
                    ) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Tune, null, tint = onHeaderColor, modifier = Modifier.size(20.dp))
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
                        color = scheme.onSurface,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { spentRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = headerColor,
                    trackColor = scheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Credit ${credited.inr()} · Debit ${debited.inr()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
