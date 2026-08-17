package com.krtky.financetracker.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.ui.components.DeleteConfirmSheet
import com.krtky.financetracker.ui.components.SettingsBlock
import com.krtky.financetracker.ui.components.SettingsButtonStack
import com.krtky.financetracker.ui.components.SettingsPanelLabel
import com.krtky.financetracker.ui.components.SettingsSegment
import com.krtky.financetracker.ui.components.SettingsSegmentedRow
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.viewmodel.SettingsViewModel

@Composable
fun BanksSettingsContent(vm: SettingsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val accountBalances by vm.accountBalances.collectAsStateWithLifecycle()
    val managedAccountBalances by vm.managedAccountBalances.collectAsStateWithLifecycle()
    val activeAccounts by vm.activeAccounts.collectAsStateWithLifecycle()
    val archivedAccounts by vm.archivedAccounts.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    var showBankSheet by remember { mutableStateOf(false) }
    var newBankName by remember { mutableStateOf("") }
    var bankPendingArchiveId by remember { mutableStateOf<Long?>(null) }
    var defaultPay by remember(state.defaultPaymentMethod) { mutableStateOf(state.defaultPaymentMethod) }
    var defaultDigital by remember(state.defaultDigitalAccount) {
        mutableStateOf(state.defaultDigitalAccount)
    }
    val activeBanks = remember(activeAccounts) {
        activeAccounts.filter { !it.name.equals("Cash", true) }
    }
    val archivedBanks = remember(archivedAccounts) {
        archivedAccounts.filter { !it.name.equals("Cash", true) }
    }
    fun balanceFor(name: String): Long =
        managedAccountBalances.firstOrNull { it.account.name.equals(name, true) }?.balancePaise
            ?: accountBalances.entries.firstOrNull { it.key.equals(name, true) }?.value
            ?: 0L

    SettingsBlock(
        title = "Cash",
        helpTitle = "Cash",
        helpMessage = "Physical cash is always available when you add a transaction. It cannot be archived.",
    ) {
        AccountBalanceRow(
            name = "Cash",
            balancePaise = balanceFor("Cash"),
            isDefaultDigital = false,
            subtitle = "Always available",
            onDelete = null,
        )
    }
    SettingsBlock(
        title = "Your banks and UPI apps",
        helpTitle = "Accounts",
        helpMessage = "Active accounts appear when you add a transaction. Archiving hides them from Add but keeps all past transactions on that account.",
    ) {
        if (activeBanks.isEmpty()) {
            Text(
                "None yet — tap + to add a bank or UPI app",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        activeBanks.forEachIndexed { index, acc ->
            if (index > 0) {
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            }
            AccountBalanceRow(
                name = acc.name,
                balancePaise = balanceFor(acc.name),
                isDefaultDigital = defaultDigital.equals(acc.name, true),
                onDelete = { bankPendingArchiveId = acc.id },
            )
        }
    }
    if (archivedBanks.isNotEmpty()) {
        SettingsBlock(
            title = "Archived",
            helpTitle = "Archived accounts",
            helpMessage = "These stay linked to old transactions and can be filtered in Activity. They do not appear when adding a new transaction. Restore to use them again.",
        ) {
            archivedBanks.forEachIndexed { index, acc ->
                if (index > 0) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                }
                AccountBalanceRow(
                    name = acc.name,
                    balancePaise = balanceFor(acc.name),
                    isDefaultDigital = false,
                    subtitle = "Archived · ${acc.kind.name.lowercase()}",
                    onDelete = null,
                    onRestore = { vm.restoreBankAccount(acc.id) },
                )
            }
        }
    }
    SettingsBlock(
        title = "Defaults when you add a spend",
        helpTitle = "Defaults",
        helpMessage = "Pre-select Cash or a default bank/UPI when the app cannot tell which account.",
    ) {
        SettingsPanelLabel("Usually pay with")
        SettingsSegmentedRow {
            listOf("Cash", "Digital").forEach { method ->
                SettingsSegment(
                    label = method,
                    selected = defaultPay.equals(method, true) ||
                        (method == "Digital" && activeBanks.any { it.name.equals(defaultPay, true) }),
                    onClick = {
                        defaultPay = method
                        vm.saveDefaultPaymentMethod(method)
                    },
                )
            }
        }
        SettingsPanelLabel("Default bank / UPI app")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = defaultDigital.isBlank(),
                onClick = {
                    defaultDigital = ""
                    vm.saveDefaultDigitalAccount("")
                },
                label = { Text("Let app choose") },
                shape = shapes.medium,
            )
            activeBanks.forEach { acc ->
                FilterChip(
                    selected = defaultDigital.equals(acc.name, true),
                    onClick = {
                        defaultDigital = acc.name
                        vm.saveDefaultDigitalAccount(acc.name)
                    },
                    label = { Text(acc.name) },
                    shape = shapes.medium,
                )
            }
        }
        if (activeBanks.isEmpty()) {
            Text(
                "Add a bank or UPI app above first.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(56.dp))

    Box(Modifier.fillMaxWidth()) {
        FloatingActionButton(
            onClick = {
                newBankName = ""
                showBankSheet = true
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(bottom = 8.dp),
            shape = shapes.large,
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        ) { Icon(Icons.Default.Add, contentDescription = "Add bank") }
    }

    if (showBankSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBankSheet = false },
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
                Text("Add bank or UPI app", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Type a short name you recognize — for example HDFC, Axis, PhonePe, or GPay.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    newBankName,
                    { newBankName = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. HDFC, PhonePe, Axis") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = shapes.medium,
                )
                SettingsButtonStack {
                    Button(
                        onClick = {
                            val name = newBankName.trim()
                            if (name.isNotEmpty()) {
                                vm.addBankAccount(name)
                            }
                            newBankName = ""
                            showBankSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.extraLarge,
                        enabled = newBankName.isNotBlank(),
                    ) { Text("Add") }
                    OutlinedButton(
                        onClick = { showBankSheet = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.extraLarge,
                    ) { Text("Cancel") }
                }
            }
        }
    }

    bankPendingArchiveId?.let { id ->
        val name = activeBanks.firstOrNull { it.id == id }?.name ?: "This account"
        DeleteConfirmSheet(
            title = "Archive account?",
            message = "“$name” will leave Add Transaction pickers. Past transactions stay on this account. You can restore it anytime under Archived.",
            onDismiss = { bankPendingArchiveId = null },
            onConfirmDelete = {
                vm.archiveBankAccount(id)
                if (defaultPay.equals(name, true)) {
                    defaultPay = "Cash"
                }
                if (defaultDigital.equals(name, true)) {
                    defaultDigital = ""
                }
                bankPendingArchiveId = null
            },
        )
    }
}

@Composable
private fun AccountBalanceRow(
    name: String,
    balancePaise: Long,
    isDefaultDigital: Boolean,
    onDelete: (() -> Unit)?,
    subtitle: String? = null,
    onRestore: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(
                    if (onRestore != null) {
                        scheme.surfaceContainerHighest
                    } else {
                        scheme.secondaryContainer
                    },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.AccountBalance,
                null,
                tint = if (onRestore != null) {
                    scheme.onSurfaceVariant
                } else {
                    scheme.onSecondaryContainer
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (onRestore != null) {
                        scheme.onSurfaceVariant
                    } else {
                        scheme.onSurface
                    },
                )
                if (isDefaultDigital) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = scheme.primaryContainer,
                    ) {
                        Text(
                            "Default",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                subtitle ?: balancePaise.inr(),
                style = MaterialTheme.typography.bodySmall,
                color = if (balancePaise < 0) scheme.error else scheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Text(
                    balancePaise.inr(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (balancePaise < 0) scheme.error else scheme.onSurface,
                )
            }
        }
        if (onRestore != null) {
            TextButton(onClick = onRestore) {
                Text("Restore")
            }
        } else if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Archive", tint = scheme.error)
            }
        }
    }
}
