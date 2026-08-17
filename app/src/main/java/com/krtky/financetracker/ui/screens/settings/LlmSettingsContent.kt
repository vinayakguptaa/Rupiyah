package com.krtky.financetracker.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.ui.components.SettingsBlock
import com.krtky.financetracker.ui.components.SettingsButtonStack
import com.krtky.financetracker.ui.components.SettingsSegment
import com.krtky.financetracker.ui.components.SettingsSegmentedRow
import com.krtky.financetracker.ui.components.SettingsStatusText
import com.krtky.financetracker.ui.components.SettingsToggleRow
import com.krtky.financetracker.ui.viewmodel.SettingsViewModel

@Composable
fun LlmSettingsContent(vm: SettingsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    var llmKey by remember(state.llmApiKeySet) { mutableStateOf("") }
    var llmBase by remember(state.llmBaseUrl) { mutableStateOf(state.llmBaseUrl) }
    var llmModel by remember(state.llmModel) { mutableStateOf(state.llmModel) }

    SettingsBlock(
        title = "Required for SMS import",
        helpTitle = "AI helper",
        helpMessage = "Bank SMS and pasted text need AI to read amounts and merchants. You can still add spends by hand without AI. Keys stay on this phone.",
    ) {
        SettingsToggleRow(
            title = "Use AI helper",
            subtitle = when {
                state.llmReady -> "On · SMS and paste import unlocked"
                state.llmEnabled -> "On — paste an API key below to finish"
                else -> "Off · turn on to import bank SMS or pasted text"
            },
            checked = state.llmEnabled,
            onCheckedChange = { vm.setLlmEnabled(it) },
        )
        SettingsStatusText(
            text = when {
                state.llmReady -> "Ready · ${state.llmModel}"
                state.llmEnabled -> "Almost done — add your API key"
                else -> "Not ready — SMS auto-import and paste parse are locked"
            },
            positive = state.llmReady,
        )
    }
    if (state.llmEnabled) {
        SettingsBlock(
            title = "API key",
            helpTitle = "API key",
            helpMessage = "Pick Groq (often free tier) or OpenAI, paste your key, then Save. Without a key, SMS import and paste parse stay off.",
        ) {
            Text(
                "Pick a service, paste your key, then Save.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            SettingsSegmentedRow {
                SettingsSegment(
                    label = "Groq (easy)",
                    selected = llmBase.contains("groq", ignoreCase = true),
                    onClick = {
                        llmBase = "https://api.groq.com/openai/v1"
                        llmModel = "llama-3.3-70b-versatile"
                    },
                )
                SettingsSegment(
                    label = "OpenAI",
                    selected = llmBase.contains("openai.com", ignoreCase = true),
                    onClick = {
                        llmBase = "https://api.openai.com/v1"
                        llmModel = "gpt-4o-mini"
                    },
                )
            }
            OutlinedTextField(
                llmKey,
                { llmKey = it },
                label = { Text(if (state.llmApiKeySet) "API key (saved — type to replace)" else "Paste your API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.medium,
            )
            OutlinedTextField(
                llmModel,
                { llmModel = it },
                label = { Text("Model name") },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.medium,
            )
            OutlinedTextField(
                llmBase,
                { llmBase = it },
                label = { Text("Service address (advanced)") },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.medium,
            )
            SettingsButtonStack {
                Button(
                    onClick = { vm.saveLlm(llmBase, llmModel, llmKey.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.large,
                ) { Text("Save") }
                if (state.llmApiKeySet) {
                    OutlinedButton(
                        onClick = { vm.clearLlmKey() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large,
                    ) { Text("Turn off and remove key") }
                }
            }
        }
    }
}
