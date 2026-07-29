package com.krtky.financetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quickreply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.ui.components.SettingsGroupRow
import com.krtky.financetracker.ui.components.SettingsSectionLabel
import com.krtky.financetracker.ui.components.GroupedCard
import com.krtky.financetracker.ui.components.chrome.ScreenHeader
import com.krtky.financetracker.ui.navigation.SettingsSection
import com.krtky.financetracker.ui.theme.DarkModePref
import com.krtky.financetracker.ui.theme.Dimens
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.theme.NavContentInsets
import com.krtky.financetracker.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    onOpenSection: (SettingsSection) -> Unit,
    /** Incremented by floating nav search FAB. */
    searchRequestTick: Int = 0,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val senders by vm.senders.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    var versionTaps by remember { mutableIntStateOf(0) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(searchRequestTick) {
        if (searchRequestTick > 0) {
            searchOpen = true
        }
    }
    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            focusRequester.requestFocus()
        }
    }

    fun matches(vararg tokens: String): Boolean {
        val q = searchQuery.trim()
        if (q.isEmpty()) return true
        return tokens.any { it.contains(q, ignoreCase = true) }
    }

    val showMoney = matches("categories", "accounts", "bank", "money")
    val showAccount = matches("profile", "account", "name", "email", "phone")
    val showIngestion = matches("email", "sms", "paste", "ingestion", "gmail", "imap")
    val showApp = matches("appearance", "theme", "backup", "location", "sheets", "google", "app")
    val showIntel = matches("llm", "ai", "intelligence", "provider", "model")
    val showDev = state.devUnlocked && matches("developer", "dev", "prompt", "diagnostics")

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Dimens.ScreenHorizontal)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScreenHeader(title = "Settings")
        Spacer(Modifier.height(Dimens.SectionGap))

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(M3EMotion.effectsDefault()) + expandVertically(M3EMotion.spatialDefault()),
            exit = fadeOut(M3EMotion.effectsDefault()) + shrinkVertically(M3EMotion.spatialDefault()),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(bottom = 8.dp),
                placeholder = { Text("Search settings") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = scheme.surfaceContainerHigh,
                    unfocusedContainerColor = scheme.surfaceContainerHigh,
                    focusedBorderColor = scheme.outlineVariant,
                    unfocusedBorderColor = scheme.outlineVariant,
                ),
            )
        }

        if (showMoney) {
            SettingsSectionLabel("Money")
            GroupedCard {
                if (matches("categories", "money")) {
                    SettingsGroupRow(
                        title = "Categories",
                        subtitle = "${categories.size} categories · edit icons & delete",
                        icon = Icons.Default.Category,
                        onClick = { onOpenSection(SettingsSection.CATEGORIES) },
                        iconContainer = scheme.secondaryContainer,
                        iconTint = scheme.onSecondaryContainer,
                    )
                }
                if (matches("accounts", "bank", "money")) {
                    SettingsGroupRow(
                        title = "Accounts",
                        subtitle = buildString {
                            val banks = state.bankAccounts.split(',', '\n')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            if (banks.isEmpty()) {
                                append("Digital banks under Cash / Digital modes")
                            } else {
                                append("${banks.size} digital account${if (banks.size == 1) "" else "s"}")
                                val def = state.defaultDigitalAccount.trim()
                                if (def.isNotBlank()) append(" · default $def")
                                else append(" · AI auto-detect")
                            }
                        },
                        icon = Icons.Default.AccountBalance,
                        onClick = { onOpenSection(SettingsSection.BANKS) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                        showDivider = matches("categories", "money"),
                    )
                }
            }
        }

        if (showAccount) {
            SettingsSectionLabel("Account")
            GroupedCard {
                SettingsGroupRow(
                    title = "Profile",
                    subtitle = state.displayName.ifBlank { "Name, email, phone" },
                    icon = Icons.Default.Person,
                    onClick = { onOpenSection(SettingsSection.PROFILE) },
                    iconContainer = scheme.primaryContainer,
                    iconTint = scheme.onPrimaryContainer,
                )
            }
        }

        if (showIngestion) {
            SettingsSectionLabel("Ingestion")
            GroupedCard {
                if (matches("email", "gmail", "imap", "ingestion", "sender", "poll")) {
                    SettingsGroupRow(
                        title = "Email",
                        subtitle = if (state.gmailPassSet) "IMAP configured" else "Connect with App Password",
                        icon = Icons.Default.Email,
                        onClick = { onOpenSection(SettingsSection.GMAIL) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                    )
                    SettingsGroupRow(
                        title = "Trusted senders",
                        subtitle = "${senders.size} sender${if (senders.size == 1) "" else "s"}",
                        icon = Icons.Default.ContactMail,
                        onClick = { onOpenSection(SettingsSection.SENDERS) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                        showDivider = true,
                    )
                    SettingsGroupRow(
                        title = "Live poll",
                        subtitle = if (state.emailPoll) "Monitoring inbox" else "Pull when you open the app",
                        icon = Icons.Default.Cloud,
                        onClick = { onOpenSection(SettingsSection.EMAIL) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                        showDivider = true,
                    )
                }
                if (matches("sms", "ingestion")) {
                    SettingsGroupRow(
                        title = "SMS transactions",
                        subtitle = if (state.smsEnabled) "Monitoring enabled" else "Optional bank SMS",
                        icon = Icons.Default.Sms,
                        onClick = { onOpenSection(SettingsSection.SMS) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                        showDivider = true,
                    )
                }
            }
        }

        if (showApp) {
            SettingsSectionLabel("App")
            GroupedCard {
                if (matches("google", "auth", "oauth", "app")) {
                    val anyGoogleConnected = state.gmailOAuthConnected || state.sheetTokenSet
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSection(SettingsSection.GOOGLE_AUTH) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(scheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "G",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Google Auth",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = scheme.onSurface,
                            )
                            Text(
                                if (anyGoogleConnected) "Connected" else "Client ID & OAuth setup",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                }
                if (matches("appearance", "theme", "app")) {
                    SettingsGroupRow(
                        title = "Appearance",
                        subtitle = when (state.themeMode) {
                            com.krtky.financetracker.ui.theme.ThemeMode.MATERIAL_YOU -> "Material You"
                            com.krtky.financetracker.ui.theme.ThemeMode.PRESET ->
                                "Preset · ${state.themePreset.name.lowercase().replaceFirstChar { it.uppercase() }}"
                            com.krtky.financetracker.ui.theme.ThemeMode.CUSTOM -> "Custom colors"
                        },
                        icon = Icons.Default.Palette,
                        onClick = { onOpenSection(SettingsSection.APPEARANCE) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                    )
                }
                if (matches("dark", "theme", "light", "app")) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val darkPref = state.darkModePref
                        listOf(
                            DarkModePref.SYSTEM to "System",
                            DarkModePref.LIGHT to "Light",
                            DarkModePref.DARK to "Dark",
                        ).forEach { (pref, label) ->
                            val selected = darkPref == pref
                            Surface(
                                onClick = { vm.setDarkModePref(pref) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) scheme.primary else scheme.surfaceContainerHighest,
                                contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
                            ) {
                                Text(
                                    label,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
                if (matches("backup", "export", "import", "app")) {
                    SettingsGroupRow(
                        title = "Backup & restore",
                        subtitle = "Export or import JSON",
                        icon = Icons.Default.Backup,
                        onClick = { onOpenSection(SettingsSection.BACKUP) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                        showDivider = true,
                    )
                }
                if (matches("location", "app")) {
                    SettingsGroupRow(
                        title = "Location",
                        subtitle = if (state.location) "Background sampling on" else "Off",
                        icon = Icons.Default.LocationOn,
                        onClick = { onOpenSection(SettingsSection.LOCATION) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                        showDivider = true,
                    )
                }
                if (matches("sheets", "google", "app")) {
                    SettingsGroupRow(
                        title = "Google Sheets",
                        subtitle = if (state.sheetsSync) "Sync enabled" else "One-way export",
                        icon = Icons.Default.TableChart,
                        onClick = { onOpenSection(SettingsSection.SHEETS) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                        showDivider = true,
                    )
                }
            }
        }

        if (showIntel) {
            SettingsSectionLabel("Intelligence")
            GroupedCard {
                SettingsGroupRow(
                    title = "LLM Providers",
                    subtitle = if (state.llmApiKeySet) "Key saved · ${state.llmModel}" else "OpenAI / Groq / compatible",
                    icon = Icons.Default.Psychology,
                    onClick = { onOpenSection(SettingsSection.LLM) },
                    iconContainer = scheme.primaryContainer,
                    iconTint = scheme.onPrimaryContainer,
                )
            }
        }

        if (showDev) {
            SettingsSectionLabel("Developer")
            GroupedCard {
                SettingsGroupRow(
                    title = "Developer options",
                    subtitle = "System prompt, delays, diagnostics",
                    icon = Icons.Default.Code,
                    onClick = { onOpenSection(SettingsSection.DEV) },
                    iconContainer = scheme.primaryContainer,
                    iconTint = scheme.onPrimaryContainer,
                )
                SettingsGroupRow(
                    title = "Test email parser",
                    subtitle = "Paste an email to test parsing",
                    icon = Icons.Default.Quickreply,
                    onClick = { onOpenSection(SettingsSection.PASTE) },
                    iconContainer = scheme.primaryContainer,
                    iconTint = scheme.onPrimaryContainer,
                    showDivider = true,
                )
                SettingsGroupRow(
                    title = "Lock developer settings",
                    subtitle = "Hide this section until unlocked again",
                    icon = Icons.Default.Lock,
                    onClick = { vm.lockDev() },
                    iconContainer = scheme.primaryContainer,
                    iconTint = scheme.onPrimaryContainer,
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
        Spacer(Modifier.height(NavContentInsets.bottom))
    }
}
