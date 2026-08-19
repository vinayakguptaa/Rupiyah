package com.krtky.financetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
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
import kotlinx.coroutines.launch

/** One selectable container (bank/cash account or tab) in a [TransferSheet]. */
data class TransferContainer(
    val id: Long,
    val name: String,
    val balanceLabel: String? = null,
)

/**
 * Shared "move money" sheet for both account-to-account and tab-to-tab transfers.
 *
 * The source and target are both pickable (never the same container). Defaults
 * come from [initialFromId] / [initialToId].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransferSheet(
    containers: List<TransferContainer>,
    title: String = "Transfer",
    subtitle: String = "Move money between your accounts. This is not a spend.",
    fromLabel: String = "From account",
    toLabel: String = "To account",
    initialFromId: Long? = null,
    initialToId: Long? = null,
    initialAmount: String = "",
    initialNote: String = "",
    onDismiss: () -> Unit,
    onTransfer: suspend (fromId: Long, toId: Long, amountText: String, note: String) -> Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var fromId by remember {
        mutableStateOf(initialFromId ?: containers.firstOrNull()?.id)
    }
    var toId by remember {
        mutableStateOf(
            initialToId
                ?: containers.firstOrNull { it.id != (initialFromId ?: containers.firstOrNull()?.id) }?.id,
        )
    }
    var amountText by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf(initialNote) }
    var saving by remember { mutableStateOf(false) }

    val canSave = !saving &&
        amountText.isNotBlank() &&
        fromId != null &&
        toId != null &&
        fromId != toId

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
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )

            TransferContainerPicker(
                label = fromLabel,
                containers = containers,
                selectedId = fromId,
                onSelect = { id ->
                    fromId = id
                    if (toId == id) {
                        toId = containers.firstOrNull { it.id != id }?.id
                    }
                },
            )
            TransferContainerPicker(
                label = toLabel,
                containers = containers,
                selectedId = toId,
                onSelect = { id ->
                    toId = id
                    if (fromId == id) {
                        fromId = containers.firstOrNull { it.id != id }?.id
                    }
                },
            )

            TextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                prefix = { Text("₹ ") },
                placeholder = { Text("Amount") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = formTextFieldColors(),
            )
            TextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = formTextFieldColors(),
            )

            if (containers.size < 2) {
                Text(
                    "Add at least two accounts in Settings → Bank accounts to transfer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                )
            }

            Button(
                onClick = {
                    val from = fromId
                    val to = toId
                    if (from == null || to == null) return@Button
                    scope.launch {
                        saving = true
                        val ok = onTransfer(from, to, amountText, note)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransferContainerPicker(
    label: String,
    containers: List<TransferContainer>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        containers.forEach { c ->
            FormAccountChip(
                label = c.name,
                icon = Icons.Default.AccountBalance,
                selected = selectedId == c.id,
                balanceLabel = c.balanceLabel,
                onClick = { onSelect(c.id) },
            )
        }
    }
}