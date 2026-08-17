package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.ui.components.M3LoadingIndicator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteAiParseSheet(
    llmReady: Boolean,
    onDismiss: () -> Unit,
    onParse: suspend (String) -> Result<Transaction>,
    onApply: (Transaction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Paste a message",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (llmReady) {
                    "Paste a bank SMS, UPI note, or any spend line. AI fills the form so you can check it before saving."
                } else {
                    "Turn on AI helper in Settings first. Without a key, only very clear bank-style text (amount + debited/credited) can be read."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                placeholder = { Text("Rs 450 spent at Zomato via HDFC…") },
                shape = shapes.medium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        val pasted = clipboard.getText()?.text
                        if (!pasted.isNullOrBlank()) {
                            text = pasted
                            error = null
                        }
                    },
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Paste from clipboard")
                }
            }
            if (error != null) {
                Text(error!!, color = scheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    scope.launch {
                        parsing = true
                        error = null
                        val result = onParse(text)
                        parsing = false
                        result.fold(
                            onSuccess = onApply,
                            onFailure = { error = it.message ?: "Could not read a transaction from that text" },
                        )
                    }
                },
                enabled = !parsing && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) {
                if (parsing) {
                    M3LoadingIndicator(size = 20.dp, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Reading…")
                } else {
                    Text("Read with AI")
                }
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text("Cancel") }
        }
    }
}
