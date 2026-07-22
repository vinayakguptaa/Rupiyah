package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.ui.components.SettingsGroupRow
import com.krtky.financetracker.ui.components.SettingsSectionLabel
import com.krtky.financetracker.ui.components.SoftPanel
import com.krtky.financetracker.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    onOpenSection: (String) -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val senders by vm.senders.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    var versionTaps by remember { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))

        SettingsSectionLabel("Money")
        SoftPanel {
            SettingsGroupRow(
                title = "Categories",
                subtitle = "${categories.size} categories · edit icons & delete",
                icon = Icons.Default.Category,
                onClick = { onOpenSection("categories") },
                iconContainer = scheme.secondaryContainer,
                iconTint = scheme.onSecondaryContainer,
            )
            SettingsGroupRow(
                title = "Bank accounts",
                subtitle = state.bankAccounts.ifBlank { "Add HDFC, ICICI, …" },
                icon = Icons.Default.AccountBalance,
                onClick = { onOpenSection("banks") },
                iconContainer = scheme.primaryContainer,
                iconTint = scheme.onPrimaryContainer,
                showDivider = true,
            )
        }

        SettingsSectionLabel("Account")
        SoftPanel {
            SettingsGroupRow(
                title = "Profile",
                subtitle = state.displayName.ifBlank { "Name, email, phone" },
                icon = Icons.Default.Person,
                onClick = { onOpenSection("profile") },
                iconContainer = scheme.primaryContainer,
                iconTint = scheme.onPrimaryContainer,
            )
        }

        SettingsSectionLabel("Ingestion")
        SoftPanel {
            SettingsGroupRow(
                title = "Email settings",
                subtitle = buildString {
                    append(if (state.gmailPassSet) "Gmail configured" else "Gmail not configured")
                    append(" · ${senders.size} trusted sender${if (senders.size == 1) "" else "s"}")
                    if (state.emailPoll) append(" · live")
                },
                icon = Icons.Default.Email,
                onClick = { onOpenSection("email") },
                iconContainer = scheme.secondaryContainer,
                iconTint = scheme.onSecondaryContainer,
            )
            SettingsGroupRow(
                title = "SMS transactions",
                subtitle = if (state.smsEnabled) "Monitoring enabled" else "Optional bank SMS",
                icon = Icons.Default.Sms,
                onClick = { onOpenSection("sms") },
                iconContainer = scheme.primaryContainer,
                iconTint = scheme.onPrimaryContainer,
                showDivider = true,
            )
            SettingsGroupRow(
                title = "Paste email",
                subtitle = "Test parser without IMAP",
                icon = Icons.Default.Mail,
                onClick = { onOpenSection("paste") },
                iconContainer = scheme.surfaceContainerHighest,
                iconTint = scheme.onSurface,
                showDivider = true,
            )
        }

        SettingsSectionLabel("App")
        SoftPanel {
            SettingsGroupRow(
                title = "Appearance",
                subtitle = when (state.themeMode) {
                    com.krtky.financetracker.ui.theme.ThemeMode.MATERIAL_YOU -> "Material You"
                    com.krtky.financetracker.ui.theme.ThemeMode.PRESET ->
                        "Preset · ${state.themePreset.name.lowercase().replaceFirstChar { it.uppercase() }}"
                    com.krtky.financetracker.ui.theme.ThemeMode.CUSTOM -> "Custom colors"
                },
                icon = Icons.Default.Palette,
                onClick = { onOpenSection("appearance") },
                iconContainer = scheme.tertiaryContainer,
                iconTint = scheme.onTertiaryContainer,
            )
            SettingsGroupRow(
                title = "Backup & restore",
                subtitle = "Export or import JSON",
                icon = Icons.Default.Backup,
                onClick = { onOpenSection("backup") },
                iconContainer = scheme.primaryContainer,
                iconTint = scheme.onPrimaryContainer,
                showDivider = true,
            )
            SettingsGroupRow(
                title = "Location",
                subtitle = if (state.location) "Background sampling on" else "Off",
                icon = Icons.Default.LocationOn,
                onClick = { onOpenSection("location") },
                iconContainer = scheme.errorContainer,
                iconTint = scheme.onErrorContainer,
                showDivider = true,
            )
            SettingsGroupRow(
                title = "Google Sheets",
                subtitle = if (state.sheetsSync) "Sync enabled" else "One-way export",
                icon = Icons.Default.Cloud,
                onClick = { onOpenSection("sheets") },
                iconContainer = scheme.primaryContainer,
                iconTint = scheme.onPrimaryContainer,
                showDivider = true,
            )
        }

        SettingsSectionLabel("Intelligence")
        SoftPanel {
            SettingsGroupRow(
                title = "LLM Providers",
                subtitle = if (state.llmApiKeySet) "Key saved · ${state.llmModel}" else "OpenAI / Groq / compatible",
                icon = Icons.Default.Psychology,
                onClick = { onOpenSection("llm") },
                iconContainer = scheme.tertiaryContainer,
                iconTint = scheme.onTertiaryContainer,
            )
        }

        if (state.devUnlocked) {
            SettingsSectionLabel("Developer")
            SoftPanel {
                SettingsGroupRow(
                    title = "Developer options",
                    subtitle = "System prompt, delays, diagnostics",
                    icon = Icons.Default.Code,
                    onClick = { onOpenSection("dev") },
                    iconContainer = scheme.errorContainer,
                    iconTint = scheme.onErrorContainer,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Rupiyah · v1.4.0",
            modifier = Modifier
                .padding(bottom = 8.dp)
                .combinedClickable(
                    onClick = {
                        versionTaps += 1
                        if (versionTaps >= 7) {
                            versionTaps = 0
                            vm.unlockDev()
                        }
                    },
                    onLongClick = { vm.unlockDev() },
                ),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
    }
}
