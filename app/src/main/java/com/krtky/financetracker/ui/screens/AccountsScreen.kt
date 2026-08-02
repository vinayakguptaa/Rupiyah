package com.krtky.financetracker.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.domain.model.AccountBalance
import com.krtky.financetracker.domain.model.AccountKind
import com.krtky.financetracker.ui.components.chrome.StackTopBar
import com.krtky.financetracker.ui.theme.Dimens
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.viewmodel.AccountsViewModel

/**
 * Ledger summary: active accounts + optional archived section.
 * Manage list in Settings → Bank accounts (archive keeps history).
 */
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onImportStatement: (accountId: Long?) -> Unit = {},
    vm: AccountsViewModel = hiltViewModel(),
) {
    val balances by vm.allBalancesDetail.collectAsStateWithLifecycle()
    val defaultDigital by vm.defaultDigitalAccount.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    val active = remember(balances) { balances.filter { !it.account.archived } }
    val archived = remember(balances) { balances.filter { it.account.archived } }
    val cashBal = active.firstOrNull { it.account.kind == AccountKind.CASH }?.balancePaise ?: 0L
    val digitalActive = active.filter { it.account.kind != AccountKind.CASH }
    val digitalTotal = digitalActive.sumOf { it.balancePaise }
    val grandTotal = active.sumOf { it.balancePaise }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = Dimens.ScreenHorizontal,
            end = Dimens.ScreenHorizontal,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
    ) {
        item {
            StackTopBar(
                title = "Accounts",
                subtitle = "Where money sits by account",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Account settings")
                    }
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
                    "No accounts yet. Add banks in Settings → Bank accounts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        items(active, key = { it.account.id }) { row ->
            AccountLedgerRow(
                row = row,
                isDefault = defaultDigital.equals(row.account.name, true),
            )
        }

        if (archived.isNotEmpty()) {
            item {
                Text(
                    "Archived",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                Text(
                    "Hidden from Add Transaction · history kept",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            items(archived, key = { "arch-${it.account.id}" }) { row ->
                AccountLedgerRow(
                    row = row,
                    isDefault = false,
                    archived = true,
                )
            }
        }

        item {
            OutlinedButton(
                onClick = { onImportStatement(null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import bank statement (CSV)")
            }
        }

        item {
            Surface(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = scheme.surfaceContainerHighest,
            ) {
                Text(
                    "Manage accounts (add / archive / restore)",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                )
            }
        }
    }
}

@Composable
private fun AccountLedgerRow(
    row: AccountBalance,
    isDefault: Boolean,
    archived: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val acc = row.account
    val icon: ImageVector =
        if (acc.kind == AccountKind.CASH) Icons.Default.Payments else Icons.Default.AccountBalance
    Surface(
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
        }
    }
}
