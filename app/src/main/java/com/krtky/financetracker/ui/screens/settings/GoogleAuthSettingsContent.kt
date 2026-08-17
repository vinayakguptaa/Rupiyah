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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.ui.components.SettingsBlock
import com.krtky.financetracker.ui.components.SettingsStatusText
import com.krtky.financetracker.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun GoogleAuthSettingsContent(vm: SettingsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val shapes = MaterialTheme.shapes
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var googleClientId by remember(state.googleWebClientId) { mutableStateOf(state.googleWebClientId) }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.data != null) {
            scope.launch { vm.completeGoogleSignIn(context, result.data) }
        }
    }

    SettingsBlock(
        title = "Only if Google sign-in fails",
        helpTitle = "Google sign-in setup",
        helpMessage = "Most people never need this. If “Connect with Google” fails, a developer may need to paste a Web Client ID from Google Cloud Console.\n\n" +
            "1. Open console.cloud.google.com\n" +
            "2. Create or pick a project\n" +
            "3. Enable Sheets API\n" +
            "4. Credentials → OAuth client ID → Web application\n" +
            "5. Paste the Client ID below (ends with .apps.googleusercontent.com)",
    ) {
        OutlinedTextField(
            googleClientId,
            { googleClientId = it },
            label = { Text("Web Client ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = shapes.medium,
        )
        Button(
            onClick = { vm.saveGoogleClientId(googleClientId) },
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.large,
        ) { Text("Save") }
    }

    SettingsBlock(
        title = "Sheets connection status",
        helpTitle = "Google Sheets",
        helpMessage = "Used only for optional spreadsheet export.",
    ) {
        SettingsStatusText(
            text = if (state.sheetTokenSet) "Connected" else "Not connected",
            positive = state.sheetTokenSet,
        )
        Button(
            onClick = { googleSignInLauncher.launch(vm.googleSignInIntent(context)) },
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.large,
        ) {
            Text(if (state.sheetTokenSet) "Reconnect Sheets" else "Connect Sheets")
        }
    }
}
