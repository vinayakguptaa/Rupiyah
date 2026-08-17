package com.krtky.financetracker.ui.screens.settings

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
import com.krtky.financetracker.ui.viewmodel.SettingsViewModel

@Composable
fun ProfileSettingsContent(vm: SettingsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val shapes = MaterialTheme.shapes
    var profileName by remember(state.displayName) { mutableStateOf(state.displayName) }
    var profileEmail by remember(state.profileEmail) { mutableStateOf(state.profileEmail) }
    var profilePhone by remember(state.profilePhone) { mutableStateOf(state.profilePhone) }

    SettingsBlock(
        title = "About you",
        helpTitle = "Your profile",
        helpMessage = "Optional. Used for greetings and backups. Stays on this phone only.",
    ) {
        OutlinedTextField(
            profileName,
            { profileName = it },
            label = { Text("Your name") },
            placeholder = { Text("How should we greet you?") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = shapes.medium,
        )
        OutlinedTextField(
            profileEmail,
            { profileEmail = it },
            label = { Text("Your email (optional)") },
            placeholder = { Text("Not used for bank login") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = shapes.medium,
        )
        OutlinedTextField(
            profilePhone,
            { profilePhone = it },
            label = { Text("Phone (optional)") },
            placeholder = { Text("Optional") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = shapes.medium,
        )
        Button(
            onClick = { vm.saveProfile(profileName, profileEmail, profilePhone) },
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.large,
        ) { Text("Save") }
    }
}
