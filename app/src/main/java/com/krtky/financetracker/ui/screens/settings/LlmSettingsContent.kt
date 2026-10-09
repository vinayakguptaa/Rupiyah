package com.krtky.financetracker.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    val llmTest by vm.llmTest.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    var llmKey by remember(state.llmApiKeySet) { mutableStateOf("") }
    var llmBase by remember(state.llmBaseUrl) { mutableStateOf(state.llmBaseUrl) }
    var llmModel by remember(state.llmModel) { mutableStateOf(state.llmModel) }

    SettingsBlock(
        title = "Smarter reading",
        helpTitle = "AI helper",
        helpMessage = "AI reads merchants and categories from bank SMS and pasted text, and powers Auto-Classify. Without it, SMS still imports with basic reading. Keys stay on this phone.",
    ) {
        SettingsToggleRow(
            title = "Use AI helper",
            subtitle = when {
                state.llmReady -> "On"
                state.llmEnabled -> "On — paste an API key below to finish"
                else -> "Off · SMS uses basic reading"
            },
            checked = state.llmEnabled,
            onCheckedChange = { vm.setLlmEnabled(it) },
        )
        SettingsStatusText(
            text = when {
                llmTest.running -> "Testing ${state.llmModel}…"
                llmTest.message != null -> llmTest.message!!
                state.llmReady -> "Saved · ${state.llmModel} (not tested yet)"
                state.llmEnabled -> "Almost done — add your API key"
                else -> "Off"
            },
            positive = state.llmReady && (llmTest.message == null || llmTest.ok),
        )
        if (state.llmReady) {
            OutlinedButton(
                onClick = { vm.testLlmConnection() },
                enabled = !llmTest.running,
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) {
                if (llmTest.running) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Test connection")
                }
            }
        }
    }
    if (state.llmEnabled) {
        SettingsBlock(
            title = "API key",
            helpTitle = "API key",
            helpMessage = "Pick Groq (often free tier) or OpenAI, paste your key, then Save. Save runs a quick test so you know the key and model work.",
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
