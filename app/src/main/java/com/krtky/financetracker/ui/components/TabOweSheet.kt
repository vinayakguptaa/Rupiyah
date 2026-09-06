package com.krtky.financetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.Money
import kotlinx.coroutines.launch

/**
 * Record a tab-only IOU (they covered / you covered) without touching accounts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabOweSheet(
    onDismiss: () -> Unit,
    onSave: suspend (amountText: String, note: String, youOweThem: Boolean) -> Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var youOweThem by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val canSave = !saving && (Money.fromRupeesString(amountText)?.paise ?: 0L) > 0L

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
                stringResource(R.string.tab_owe_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.tab_owe_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormTypeSegment(
                    label = stringResource(R.string.tab_owe_they_covered),
                    selected = youOweThem,
                    onClick = { youOweThem = true },
                    modifier = Modifier.weight(1f),
                )
                FormTypeSegment(
                    label = stringResource(R.string.tab_owe_you_covered),
                    selected = !youOweThem,
                    onClick = { youOweThem = false },
                    modifier = Modifier.weight(1f),
                )
            }
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
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        val ok = onSave(amountText, note, youOweThem)
                        saving = false
                        if (ok) onDismiss()
                    }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                ),
            ) {
                Text(stringResource(R.string.tab_owe_save))
            }
        }
    }
}
