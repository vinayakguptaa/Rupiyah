package com.krtky.financetracker.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.data.prefs.SecureStore
import com.krtky.financetracker.ui.components.AppSecondaryButton
import com.krtky.financetracker.ui.components.SettingsBlock
import com.krtky.financetracker.ui.components.SettingsButtonStack
import com.krtky.financetracker.ui.viewmodel.SettingsViewModel

@Composable
fun DevSettingsContent(vm: SettingsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    var systemPrompt by remember(state.llmSystemPrompt) {
        mutableStateOf(state.llmSystemPrompt.ifBlank { SecureStore.DEFAULT_LLM_SYSTEM })
    }
    var classDelay by remember(state.classificationDelayMin) {
        mutableStateOf(state.classificationDelayMin.toString())
    }

    SettingsBlock(
        title = "System prompt",
        helpTitle = "LLM system prompt",
        helpMessage = "Instructions sent when the model extracts transactions from SMS or pasted text. Reset restores the built-in default.",
    ) {
        OutlinedTextField(
            systemPrompt,
            { systemPrompt = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 8,
            shape = shapes.medium,
        )
        SettingsButtonStack {
            Button(
                onClick = { vm.saveSystemPrompt(systemPrompt) },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text("Save system prompt") }
            OutlinedButton(
                onClick = {
                    vm.resetSystemPrompt()
                    systemPrompt = SecureStore.DEFAULT_LLM_SYSTEM
                },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text("Reset to default") }
        }
    }
    SettingsBlock(
        title = "Classification",
        helpTitle = "Classification delay",
        helpMessage = "Minutes to wait before classification prompts fire for new drafts.",
    ) {
        OutlinedTextField(
            classDelay,
            { classDelay = it.filter { ch -> ch.isDigit() } },
            label = { Text("Delay (minutes)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = shapes.medium,
        )
        AppSecondaryButton(
            onClick = { vm.setClassificationDelay(classDelay.toLongOrNull() ?: 15L) },
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.large,
        ) { Text("Save delay") }
    }
    SettingsBlock(
        title = "Diagnostics",
        helpTitle = "Diagnostics",
        helpMessage = "Read-only status for debugging ingestion and LLM configuration.",
    ) {
        Text("Package: com.krtky.financetracker", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Text("LLM configured: ${state.llmApiKeySet}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Text("SMS on: ${state.smsEnabled}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Button(
            onClick = { vm.lockDev() },
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
        ) { Text("Lock developer settings") }
    }
}
