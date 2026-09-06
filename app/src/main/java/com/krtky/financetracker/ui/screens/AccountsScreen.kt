package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.AccountBalance
import com.krtky.financetracker.domain.model.AccountKind
import com.krtky.financetracker.ui.components.DeleteConfirmSheet
import com.krtky.financetracker.ui.components.chrome.OverflowMenuButton
import com.krtky.financetracker.ui.components.chrome.OverflowMenuItem
import com.krtky.financetracker.ui.components.chrome.StackTopBar
import com.krtky.financetracker.ui.navigation.UNASSIGNED_DIGITAL_ACCOUNT_ID
import com.krtky.financetracker.ui.theme.Dimens
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.viewmodel.AccountsViewModel

/**
 * Single accounts hub: balances, add/archive/restore, and Add defaults.
 * Settings → Bank accounts deep-links here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onImportStatement: (accountId: Long?) -> Unit = {},
    onOpenAccount: (accountId: Long, accountName: String) -> Unit = { _, _ -> },
    vm: AccountsViewModel = hiltViewModel(),
) {
    val balances by vm.allBalancesDetail.collectAsStateWithLifecycle()
    val defaultDigital by vm.defaultDigitalAccount.collectAsStateWithLifecycle()
    val defaultPay by vm.defaultPaymentMethod.collectAsStateWithLifecycle()
    val unassigned by vm.unassignedDigital.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes

    var showAddSheet by remember { mutableStateOf(false) }
    var newBankName by remember { mutableStateOf("") }
    var pendingArchiveId by remember { mutableStateOf<Long?>(null) }

    val active = remember(balances) { balances.filter { !it.account.archived } }
    val archived = remember(balances) { balances.filter { it.account.archived } }
    val activeBanks = remember(active) {
        active.filter { it.account.kind != AccountKind.CASH && !it.account.name.equals("Cash", true) }
    }
    val cashBal = active.firstOrNull { it.account.kind == AccountKind.CASH }?.balancePaise ?: 0L
    val digitalTotal = activeBanks.sumOf { it.balancePaise }
    val grandTotal = active.sumOf { it.balancePaise }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newBankName = ""
                    showAddSheet = true
                },
                shape = shapes.large,
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add bank")
            }
        },
    ) { padding ->
        // Scaffold already applies system-bar insets via [padding]; do not add
        // statusBarsPadding again (that doubled the top gap vs other stack screens).
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Dimens.ScreenHorizontal,
                end = Dimens.ScreenHorizontal,
                top = Dimens.ScreenTop,
                bottom = 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
        ) {
            item {
                StackTopBar(
                    title = "Accounts",
                    subtitle = "Balances and Add defaults",
                    onBack = onBack,
                    actions = {
                        OverflowMenuButton(
                            items = listOf(
                                OverflowMenuItem("Add bank") {
                                    newBankName = ""
                                    showAddSheet = true
                                },
                                OverflowMenuItem(
                                    stringResource(R.string.overflow_import_statement),
                                    onClick = { onImportStatement(null) },
                                ),
                            ),
                        )
                    },
                )
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = scheme.primaryContainer,
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Total (active accounts)",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                        Text(
                            grandTotal.inr(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Cash",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onPrimaryContainer.copy(alpha = 0.7f),
                                )
                                Text(
                                    cashBal.inr(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onPrimaryContainer,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Banks & wallets",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onPrimaryContainer.copy(alpha = 0.7f),
                                )
                                Text(
                                    digitalTotal.inr(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Active",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (active.isEmpty()) {
                item {
                    Text(
                        "No accounts yet — tap + to add a bank or UPI app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            items(active, key = { it.account.id }) { row ->
                AccountLedgerRow(
                    row = row,
                    isDefault = defaultDigital.equals(row.account.name, true),
                    onClick = { onOpenAccount(row.account.id, row.account.name) },
                    onArchive = if (row.account.kind != AccountKind.CASH) {
                        { pendingArchiveId = row.account.id }
                    } else {
                        null
                    },
                )
            }
            if (unassigned.count > 0) {
                item(key = "unassigned-digital") {
                    UnassignedDigitalRow(
                        count = unassigned.count,
                        netPaise = unassigned.netPaise,
                        onClick = {
                            onOpenAccount(UNASSIGNED_DIGITAL_ACCOUNT_ID, "Digital (no bank)")
                        },
                    )
                }
            }

            item {
                Text(
                    "Defaults for Add",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                Text(
                    "Used when the app cannot tell which account to pick.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    "Usually pay with",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Cash", "Digital").forEach { method ->
                        FilterChip(
                            selected = defaultPay.equals(method, true) ||
                                (method == "Digital" && activeBanks.any {
                                    it.account.name.equals(defaultPay, true)
                                }),
                            onClick = { vm.setDefaultPaymentMethod(method) },
                            label = { Text(method) },
                        )
                    }
                }
            }
            item {
                Text(
                    "Default bank / UPI",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = defaultDigital.isBlank(),
                        onClick = { vm.setDefaultDigitalAccount("") },
                        label = { Text("Let app choose") },
                    )
                    activeBanks.forEach { row ->
                        FilterChip(
                            selected = defaultDigital.equals(row.account.name, true),
                            onClick = { vm.setDefaultDigitalAccount(row.account.name) },
                            label = { Text(row.account.name) },
                        )
                    }
                }
            }

            // Archived stays on this screen but sits well below primary manage work.
            if (archived.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(48.dp))
                }
                item {
                    Text(
                        "Archived",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                item {
                    Text(
                        "Hidden from Add · history kept · tap Restore to use again",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                items(archived, key = { "arch-${it.account.id}" }) { row ->
                    AccountLedgerRow(
                        row = row,
                        isDefault = false,
                        archived = true,
                        onClick = { onOpenAccount(row.account.id, row.account.name) },
                        onRestore = { vm.restoreAccount(row.account.id) },
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Add bank or UPI app",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Short name you recognize — HDFC, Axis, PhonePe, GPay…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = newBankName,
                    onValueChange = { newBankName = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = shapes.medium,
                )
                Button(
                    onClick = {
                        val name = newBankName.trim()
                        if (name.isNotEmpty()) vm.addAccount(name)
                        showAddSheet = false
                    },
                    enabled = newBankName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.extraLarge,
                ) { Text("Add") }
                OutlinedButton(
                    onClick = { showAddSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.extraLarge,
                ) { Text("Cancel") }
            }
        }
    }

    pendingArchiveId?.let { id ->
        val name = active.firstOrNull { it.account.id == id }?.account?.name ?: "This account"
        DeleteConfirmSheet(
            title = "Archive account?",
            message = "“$name” leaves Add pickers. Past transactions stay. You can restore anytime under Archived.",
            onDismiss = { pendingArchiveId = null },
            onConfirmDelete = {
                vm.archiveAccount(id)
                if (defaultPay.equals(name, true)) vm.setDefaultPaymentMethod("Cash")
                if (defaultDigital.equals(name, true)) vm.setDefaultDigitalAccount("")
                pendingArchiveId = null
            },
            deleteLabel = "Archive",
        )
    }
}

@Composable
private fun AccountLedgerRow(
    row: AccountBalance,
    isDefault: Boolean,
    archived: Boolean = false,
    onClick: () -> Unit,
    onArchive: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val acc = row.account
    val icon: ImageVector =
        if (acc.kind == AccountKind.CASH) Icons.Default.Payments else Icons.Default.AccountBalance
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (archived) scheme.surfaceContainerLow else scheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(
                        if (archived) {
                            scheme.surfaceContainerHighest
                        } else {
                            scheme.secondaryContainer
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    null,
                    tint = if (archived) scheme.onSurfaceVariant else scheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    acc.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (archived) scheme.onSurfaceVariant else scheme.onSurface,
                )
                Text(
                    buildString {
                        append(acc.kind.name.lowercase().replaceFirstChar { it.titlecase() })
                        if (isDefault) append(" · default")
                        if (archived) append(" · archived")
                        if (row.txnCount > 0) append(" · ${row.txnCount} txns")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Text(
                row.balancePaise.inr(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    row.balancePaise < 0 -> scheme.error
                    archived -> scheme.onSurfaceVariant
                    else -> scheme.onSurface
                },
            )
            if (onArchive != null) {
                IconButton(onClick = onArchive) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Archive",
                        tint = scheme.error,
                    )
                }
            }
            if (onRestore != null) {
                TextButton(onClick = onRestore) {
                    Text("Restore")
                }
            }
        }
    }
}

@Composable
private fun UnassignedDigitalRow(
    count: Int,
    netPaise: Long,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = scheme.tertiaryContainer,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(scheme.tertiary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Payments, null, tint = scheme.onTertiary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Digital (no bank)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Unassigned · $count txns",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
            Text(
                netPaise.inr(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (netPaise < 0) scheme.error else scheme.onTertiaryContainer,
            )
        }
    }
}
