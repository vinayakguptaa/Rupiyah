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
    val jsonRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            vm.setStatus("Restoring JSON backup…")
            val r = vm.importJsonBackup(context, uri)
            vm.setStatus(r.fold({ it }, { it.message ?: "Restore failed" }))
        }
    }
    val activityCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            vm.setStatus("Merging Activity CSV…")
            val r = vm.importActivityCsvFile(context, uri)
            vm.setStatus(r.fold({ it }, { it.message ?: "Merge failed" }))
        }
    }

    SettingsBlock(
        title = "JSON backup",
        helpTitle = "JSON backup",
        helpMessage = "Full safety copy of transactions, categories, tabs, accounts, and settings. " +
            "Restore replaces everything on this phone. Bank statement CSVs are a different file — " +
            "use Settings → Import bank statement.",
    ) {
        Text(
            "Save a JSON file, then restore that same file later. Restore wipes local data first.",
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
            ) { Text("Save JSON backup") }
            OutlinedButton(
                onClick = {
                    jsonRestoreLauncher.launch(arrayOf("application/json", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text("Restore JSON backup") }
        }
    }

    SettingsBlock(
        title = "Merge Activity CSV",
        helpTitle = "Activity CSV",
        helpMessage = "This is the spreadsheet exported from Activity → ⋮ → Export activity CSV. " +
            "It merges by Transaction ID and does not wipe settings. " +
            "It is not a bank statement.",
    ) {
        Text(
            "Use the CSV from Activity export (Downloads → activity_….csv), not a bank download.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        SettingsButtonStack {
            OutlinedButton(
                onClick = {
                    activityCsvLauncher.launch(
                        arrayOf(
                            "text/csv",
                            "text/comma-separated-values",
                            "text/*",
                            "*/*",
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text("Merge Activity CSV") }
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
