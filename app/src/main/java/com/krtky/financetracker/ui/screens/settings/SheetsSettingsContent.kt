package com.krtky.financetracker.ui.screens.settings

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.ui.components.AppSecondaryButton
import com.krtky.financetracker.ui.components.SettingsBlock
import com.krtky.financetracker.ui.components.SettingsButtonStack
import com.krtky.financetracker.ui.components.SettingsStatusText
import com.krtky.financetracker.ui.components.SettingsToggleRow
import com.krtky.financetracker.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SheetsSettingsContent(vm: SettingsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val shapes = MaterialTheme.shapes
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheetId by remember(state.sheetId) { mutableStateOf(state.sheetId) }
    var sheetToken by remember { mutableStateOf("") }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.data != null) {
            scope.launch { vm.completeGoogleSignIn(context, result.data) }
        }
    }

    SettingsBlock(
        title = "Copy to a spreadsheet",
        helpTitle = "Google Spreadsheet",
        helpMessage = "Optional. Connect Google, create or pick a sheet, then turn sync on. The app copies transactions one way into Google Sheets — it does not delete them from your phone.",
    ) {
        SettingsStatusText(
            text = if (state.sheetTokenSet) "Google connected" else "Not connected yet",
            positive = state.sheetTokenSet,
        )
        SettingsToggleRow(
            title = "Keep spreadsheet up to date",
            subtitle = if (state.sheetsSync) "Sync is on" else "Sync is off",
            checked = state.sheetsSync,
            onCheckedChange = { vm.setSheets(it) },
        )
        OutlinedTextField(
            sheetId,
            { sheetId = it },
            label = { Text("Spreadsheet ID (from the sheet link)") },
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.medium,
        )
        OutlinedTextField(
            sheetToken,
            { sheetToken = it },
            label = { Text(if (state.sheetTokenSet) "Access token (saved — advanced)" else "Access token (advanced, optional)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.medium,
        )
        SettingsButtonStack {
            AppSecondaryButton(
                onClick = { googleSignInLauncher.launch(vm.googleSignInIntent(context)) },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text(if (state.sheetTokenSet) "Connect a different Google account" else "Connect with Google") }
            Button(
                onClick = { vm.saveSheets(sheetId, sheetToken.ifBlank { null }) },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text("Save") }
            AppSecondaryButton(
                onClick = { scope.launch { vm.createSheetsSpreadsheet("Rupiyah") } },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text("Create a new spreadsheet for me") }
            AppSecondaryButton(
                onClick = { scope.launch { vm.syncSheetsNow() } },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
            ) { Text("Sync now") }
        }
    }
}
