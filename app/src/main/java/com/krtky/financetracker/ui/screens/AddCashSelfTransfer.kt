package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.domain.model.Account
import com.krtky.financetracker.ui.components.FormAccountChip
import com.krtky.financetracker.ui.util.inr

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddCashSelfTransferFields(
    accounts: List<Account>,
    accountBalances: Map<String, Long>,
    fromAccountId: Long?,
    toAccountId: Long?,
    onFromAccount: (Long) -> Unit,
    onToAccount: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        "From account",
        style = MaterialTheme.typography.labelMedium,
        color = scheme.onSurfaceVariant,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        accounts.forEach { acc ->
            FormAccountChip(
                label = acc.name,
                icon = Icons.Default.AccountBalance,
                selected = fromAccountId == acc.id,
                balanceLabel = accountBalances[acc.name]?.inr(),
                onClick = { onFromAccount(acc.id) },
            )
        }
    }
    Text(
        "To account",
        style = MaterialTheme.typography.labelMedium,
        color = scheme.onSurfaceVariant,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        accounts.forEach { acc ->
            FormAccountChip(
                label = acc.name,
                icon = Icons.Default.AccountBalance,
                selected = toAccountId == acc.id,
                balanceLabel = accountBalances[acc.name]?.inr(),
                onClick = { onToAccount(acc.id) },
            )
        }
    }
}
