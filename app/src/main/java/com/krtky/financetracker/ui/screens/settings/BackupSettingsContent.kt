package com.krtky.financetracker.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.ui.components.AppSecondaryButton
import com.krtky.financetracker.ui.components.SettingsBlock
import com.krtky.financetracker.ui.components.SettingsButtonStack
import com.krtky.financetracker.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSettingsContent(vm: SettingsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportPendingUri by remember { mutableStateOf<Uri?>(null) }

    val doExport: (Uri, Boolean) -> Unit = { uri, includeSecrets ->
        scope.launch {
            vm.setStatus("Exporting…")
            val r = vm.exportData(context, uri, includeSecrets)
            vm.setStatus(r.fold({ "Exported settings & data" }, { it.message ?: "Export failed" }))
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (state.llmApiKeySet || state.sheetTokenSet) {
            exportPendingUri = uri
        } else {
            doExport(uri, false)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            vm.setStatus("Importing…")
            val r = vm.importData(context, uri)
            vm.setStatus(r.fold({ it }, { it.message ?: "Import failed" }))
        }
    }

    SettingsBlock(
        title = "Save or restore your data",
        helpTitle = "Backup & restore",
        helpMessage = "Export creates a file with your transactions, categories, tabs, and settings. Keep it somewhere safe (like Google Drive). Import puts that data back. The file may include API keys if you saved any.",
    ) {
        Text(
            "Use Export to make a safety copy. Use Import only when you want to restore an old copy.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        SettingsButtonStack {
            AppSecondaryButton(
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    exportLauncher.launch("rupiyah-backup-$stamp.json")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text("Save backup file") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text("Restore from backup file") }
        }
    }

    exportPendingUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { exportPendingUri = null },
            title = { Text("Include credentials?") },
            text = {
                Text(
                    "This device has an AI API key and/or Google Sheets token saved. " +
                        "Including them in the backup file stores them in plaintext — anyone who " +
                        "gets the file can use them. Exclude them to keep the file safe to share.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        doExport(uri, true)
                        exportPendingUri = null
                    },
                ) { Text("Include credentials") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        doExport(uri, false)
                        exportPendingUri = null
                    },
                ) { Text("Exclude") }
            },
        )
    }
}
