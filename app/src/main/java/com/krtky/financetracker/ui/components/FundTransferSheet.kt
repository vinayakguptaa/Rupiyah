package com.krtky.financetracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.domain.model.FundBalance
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundTransferSheet(
    sourceFundId: Long,
    sourceFundName: String,
    allFunds: List<FundBalance>,
    onDismiss: () -> Unit,
    onTransfer: suspend (fromId: Long, toId: Long, amountPaise: Long, note: String) -> Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var targetFundId by remember { mutableStateOf<Long?>(null) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val targetFunds = allFunds.filter { it.fund.id != sourceFundId && !it.fund.archived }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            Text("Move money", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Text(
                "Transfer from \"$sourceFundName\" to another fund.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )

            Text("From: $sourceFundName", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)

            Text("To", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                targetFunds.forEach { f ->
                    FormCategoryChip(
                        label = f.fund.name,
                        icon = Icons.Default.Payments,
                        selected = targetFundId == f.fund.id,
                        onClick = { targetFundId = f.fund.id },
                    )
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount ₹") },
                placeholder = { Text("e.g. 500") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                placeholder = { Text("Transfer note") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )

            val amountPaise = (amountText.toDoubleOrNull()?.let { (it * 100).toLong() } ?: 0L)
            val valid = targetFundId != null && amountPaise > 0L

            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        val ok = onTransfer(sourceFundId, targetFundId!!, amountPaise, note)
                        saving = false
                        if (ok) onDismiss()
                    }
                },
                enabled = valid && !saving,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(if (saving) "Moving\u2026" else "Move money")
            }
        }
    }
}
