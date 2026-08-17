package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.domain.model.Account
import com.krtky.financetracker.ui.components.formTextFieldColors

@Composable
fun AddTransferContent(
    accounts: List<Account>,
    accountBalances: Map<String, Long>,
    amount: String,
    onAmount: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    fromAccountId: Long?,
    toAccountId: Long?,
    onFromAccount: (Long) -> Unit,
    onToAccount: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Transfer",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Move money between your accounts. This is not a spend.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        TextField(
            value = amount,
            onValueChange = { onAmount(it.filter { ch -> ch.isDigit() || ch == '.' }) },
            placeholder = { Text("Amount") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = formTextFieldColors(),
            prefix = { Text("₹ ") },
        )
        AddCashSelfTransferFields(
            accounts = accounts,
            accountBalances = accountBalances,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            onFromAccount = onFromAccount,
            onToAccount = onToAccount,
        )
        TextField(
            value = note,
            onValueChange = onNote,
            placeholder = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = formTextFieldColors(),
        )
        if (accounts.size < 2) {
            Text(
                "Add at least two accounts in Settings → Bank accounts to transfer.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.error,
            )
        }
    }
}
