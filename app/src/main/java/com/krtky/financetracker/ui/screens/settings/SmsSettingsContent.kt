package com.krtky.financetracker.ui.screens.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.ui.components.SettingsBlock
import com.krtky.financetracker.ui.components.SettingsToggleRow
import com.krtky.financetracker.ui.viewmodel.SettingsViewModel

@Composable
fun SmsSettingsContent(vm: SettingsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    var smsSenders by remember(state.smsSenders) { mutableStateOf(state.smsSenders) }
    var smsKeywords by remember(state.smsKeywords) { mutableStateOf(state.smsKeywords) }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> vm.setSmsEnabled(granted) }

    SettingsBlock(
        title = "Read bank SMS",
        helpTitle = "Bank text messages",
        helpMessage = "When on, bank SMS on this phone become spends. Only messages from the senders and keywords you list below are used. With the AI helper set up, merchants and categories are read more accurately.",
    ) {
        if (!state.llmReady) {
            Text(
                "Without the AI helper, only amount, debit/credit and reference are read. Set it up in AI helper for merchants and categories.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        SettingsToggleRow(
            title = "Read bank text messages",
            subtitle = when {
                state.smsEnabled && state.llmReady -> "On · AI reading"
                state.smsEnabled -> "On · basic reading"
                else -> "Off"
            },
            checked = state.smsEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                } else {
                    vm.setSmsEnabled(false)
                }
            },
        )
    }
    SettingsBlock(
        title = "Which SMS to allow",
        helpTitle = "SMS filters",
        helpMessage = "Allowed senders are short IDs from SMS (e.g. HDFCBK, AX-ICICIB). Keywords are words that mean “this is a payment” (e.g. debited, spent, UPI).",
    ) {
        OutlinedTextField(
            smsSenders,
            { smsSenders = it },
            label = { Text("Allowed senders (comma-separated)") },
            placeholder = { Text("HDFCBK, AX-ICICIB, VK-PhonePe") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = shapes.medium,
        )
        OutlinedTextField(
            smsKeywords,
            { smsKeywords = it },
            label = { Text("Keywords (comma-separated)") },
            placeholder = { Text("debited, spent, UPI, credited") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = shapes.medium,
        )
        Button(
            onClick = { vm.saveSmsRules(smsSenders, smsKeywords) },
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.large,
        ) { Text("Save") }
    }
}
