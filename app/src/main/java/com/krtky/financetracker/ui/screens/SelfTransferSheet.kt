package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.domain.model.Account
import com.krtky.financetracker.ui.components.M3LoadingIndicator
import com.krtky.financetracker.ui.components.formTextFieldColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfTransferSheet(
    accounts: List<Account>,
    accountBalances: Map<String, Long>,
    initialAmount: String,
    initialFromAccountId: Long? = null,
    initialToAccountId: Long? = null,
    initialNote: String = "",
    onDismiss: () -> Unit,
    onTransfer: suspend (fromId: Long, toId: Long, amountText: String, note: String) -> Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf(initialNote) }
    var fromAccountId by remember {
        mutableStateOf(initialFromAccountId ?: accounts.firstOrNull()?.id)
    }
    var toAccountId by remember {
        mutableStateOf(
            initialToAccountId
                ?: accounts.firstOrNull { it.id != (initialFromAccountId ?: accounts.firstOrNull()?.id) }?.id,
        )
    }
    var saving by remember { mutableStateOf(false) }
    val canSave = !saving &&
        amount.isNotBlank() &&
        fromAccountId != null &&
        toAccountId != null &&
        fromAccountId != toAccountId

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Transfer between accounts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Moves money from one of your accounts to another. Not a spend.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            TextField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                placeholder = { Text("Amount") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = formTextFieldColors(),
            )
            AddCashSelfTransferFields(
                accounts = accounts,
                accountBalances = accountBalances,
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                onFromAccount = { id ->
                    fromAccountId = id
                    if (toAccountId == id) {
                        toAccountId = accounts.firstOrNull { it.id != id }?.id
                    }
                },
                onToAccount = { id ->
                    toAccountId = id
                    if (fromAccountId == id) {
                        fromAccountId = accounts.firstOrNull { it.id != id }?.id
                    }
                },
            )
            TextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = formTextFieldColors(),
            )
            Button(
                onClick = {
                    val fromId = fromAccountId
                    val toId = toAccountId
                    if (fromId == null || toId == null) return@Button
                    scope.launch {
                        saving = true
                        val ok = onTransfer(fromId, toId, amount, note)
                        saving = false
                        if (ok) onDismiss()
                    }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                if (saving) {
                    M3LoadingIndicator(size = 22.dp, strokeWidth = 3.dp)
                } else {
                    Text("Transfer")
                }
            }
        }
    }
}
