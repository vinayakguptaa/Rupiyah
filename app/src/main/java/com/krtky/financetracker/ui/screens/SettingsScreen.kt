package com.krtky.financetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quickreply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.ui.components.SettingsGroupRow
import com.krtky.financetracker.ui.components.SettingsSectionLabel
import com.krtky.financetracker.ui.components.GroupedCard
import com.krtky.financetracker.ui.components.chrome.ScreenHeader
import com.krtky.financetracker.ui.navigation.SettingsSection
import com.krtky.financetracker.ui.theme.Dimens
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.theme.NavContentInsets
import com.krtky.financetracker.ui.theme.ThemeMode
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

    val showYou = matches("profile", "name", "phone", "you", "account")
    val showMoney = matches(
        "categories", "accounts", "bank", "money", "wallet", "cash", "digital", "upi",
    )
    val showImport = matches(
        "email", "sms", "gmail", "bank", "import", "message", "text", "inbox", "imap",
        "sender", "trusted", "poll", "monitor",
    )
    val showLook = matches("appearance", "theme", "color", "dark", "light", "look", "font")
    val showSave = matches(
        "backup", "restore", "export", "import", "sheet", "spreadsheet", "google", "save", "copy",
    )
    val showSmart = matches("llm", "ai", "intelligence", "openai", "groq", "model", "smart", "helper")
    val showMore = matches(
        "location", "place", "map", "google", "client", "oauth", "sign", "setup", "more",
    )
    val showDev = state.devUnlocked && matches(
        "developer", "dev", "prompt", "diagnostics", "paste", "test", "parser",
    )

    val bankCount = state.bankAccounts.split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .size

    val emailSubtitle = when {
        !state.llmReady -> "Set up AI helper first, then connect Gmail"
        state.gmailOAuthConnected -> {
            val who = state.gmailOAuthEmail.ifBlank { "Google" }
            if (state.emailPoll) "Connected ($who) · checking for new mail"
            else "Connected ($who) · checks when you open the app"
        }
        state.gmailPassSet -> {
            if (state.emailPoll) "Gmail password saved · checking for new mail"
            else "Gmail password saved · checks when you open the app"
        }
        else -> "Connect Gmail to import spends automatically"
    }

    val themeSubtitle = when (state.themeMode) {
        ThemeMode.MATERIAL_YOU -> "Wallpaper colors"
        ThemeMode.PRESET -> "Preset colors"
        ThemeMode.CUSTOM -> "Your custom colors"
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Dimens.ScreenHorizontal)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScreenHeader(title = "Settings")
        Text(
            "Tap any row to open it. You can change things later anytime.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Spacer(Modifier.height(Dimens.SectionGap / 2))

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

        // ── You ──────────────────────────────────────────────────────────
        if (showYou) {
            SettingsSectionLabel("You")
            GroupedCard {
                SettingsGroupRow(
                    title = "Your profile",
                    subtitle = state.displayName.ifBlank { "Name, email, and phone (optional)" },
                    icon = Icons.Default.Person,
                    onClick = { onOpenSection(SettingsSection.PROFILE) },
                    iconContainer = scheme.primaryContainer,
                    iconTint = scheme.onPrimaryContainer,
                )
            }
        }

        // ── Your money ───────────────────────────────────────────────────
        if (showMoney) {
            SettingsSectionLabel("Your money")
            GroupedCard {
                if (matches("categories", "money", "food", "bills")) {
                    SettingsGroupRow(
                        title = "Categories",
                        subtitle = if (categories.isEmpty()) {
                            "Food, travel, rent, shopping…"
                        } else {
                            "${categories.size} categories · tap to edit"
                        },
                        icon = Icons.Default.Category,
                        onClick = { onOpenSection(SettingsSection.CATEGORIES) },
                        iconContainer = scheme.secondaryContainer,
                        iconTint = scheme.onSecondaryContainer,
                    )
                }
                if (matches("accounts", "bank", "money", "wallet", "cash", "digital", "upi")) {
                    SettingsGroupRow(
                        title = "Bank accounts",
                        subtitle = when {
                            bankCount == 0 -> "Add your banks and UPI apps (PhonePe, GPay…)"
                            else -> {
                                val def = state.defaultDigitalAccount.trim()
                                buildString {
                                    append("$bankCount account${if (bankCount == 1) "" else "s"}")
                                    if (def.isNotBlank()) append(" · default $def")
                                }
                            }
                        },
                        icon = Icons.Default.AccountBalance,
                        onClick = { onOpenSection(SettingsSection.BANKS) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                        showDivider = matches("categories", "money", "food", "bills"),
                    )
                }
            }
        }

        // ── Automatic import ─────────────────────────────────────────────
        if (showImport) {
            SettingsSectionLabel("Import spends automatically")
            GroupedCard {
                if (matches(
                        "email", "gmail", "bank", "import", "inbox", "imap",
                        "sender", "trusted", "poll", "monitor", "message",
                    )
                ) {
                    SettingsGroupRow(
                        title = "Bank emails",
                        subtitle = emailSubtitle,
                        icon = Icons.Default.Email,
                        onClick = { onOpenSection(SettingsSection.EMAIL) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                    )
                }
                if (matches("sms", "text", "message", "import", "bank")) {
                    SettingsGroupRow(
                        title = "Bank text messages (SMS)",
                        subtitle = when {
                            !state.llmReady -> "Set up AI helper first"
                            state.smsEnabled -> "On · reading bank SMS on this phone"
                            else -> "Turn on to read bank SMS"
                        },
                        icon = Icons.Default.Sms,
                        onClick = { onOpenSection(SettingsSection.SMS) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                        showDivider = true,
                    )
                }
            }
        }

        // ── Look ─────────────────────────────────────────────────────────
        if (showLook) {
            SettingsSectionLabel("Look of the app")
            GroupedCard {
                SettingsGroupRow(
                    title = "Colors & theme",
                    subtitle = "$themeSubtitle · light or dark",
                    icon = Icons.Default.Palette,
                    onClick = { onOpenSection(SettingsSection.APPEARANCE) },
                    iconContainer = scheme.tertiaryContainer,
                    iconTint = scheme.onTertiaryContainer,
                )
            }
        }

        // ── Save a copy ──────────────────────────────────────────────────
        if (showSave) {
            SettingsSectionLabel("Save a copy")
            GroupedCard {
                if (matches("backup", "restore", "export", "import", "save", "copy")) {
                    SettingsGroupRow(
                        title = "Backup & restore",
                        subtitle = "Save everything to a file, or restore later",
                        icon = Icons.Default.Backup,
                        onClick = { onOpenSection(SettingsSection.BACKUP) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                    )
                }
                if (matches("sheet", "spreadsheet", "google", "save", "copy", "export")) {
                    SettingsGroupRow(
                        title = "Google Spreadsheet",
                        subtitle = if (state.sheetsSync) {
                            "Sync is on"
                        } else if (state.sheetTokenSet) {
                            "Connected · sync is off"
                        } else {
                            "Optional · copy transactions to Sheets"
                        },
                        icon = Icons.Default.TableChart,
                        onClick = { onOpenSection(SettingsSection.SHEETS) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                        showDivider = matches("backup", "restore", "export", "import", "save", "copy"),
                    )
                }
            }
        }

        // ── Smart helper ─────────────────────────────────────────────────
        if (showSmart) {
            SettingsSectionLabel("Smarter reading")
            GroupedCard {
                SettingsGroupRow(
                    title = "AI helper",
                    subtitle = when {
                        state.llmReady ->
                            "Ready · required for bank email & SMS import"
                        state.llmEnabled ->
                            "Almost ready · add an API key"
                        else ->
                            "Required to turn on bank email & SMS import"
                    },
                    icon = Icons.Default.Psychology,
                    onClick = { onOpenSection(SettingsSection.LLM) },
                    iconContainer = scheme.secondaryContainer,
                    iconTint = scheme.onSecondaryContainer,
                )
            }
        }

        // ── More ─────────────────────────────────────────────────────────
        if (showMore) {
            SettingsSectionLabel("More options")
            GroupedCard {
                if (matches("location", "place", "map", "more")) {
                    SettingsGroupRow(
                        title = "Place tags",
                        subtitle = if (state.location) {
                            "On · remembers where you spent"
                        } else {
                            "Off · optional location on spends"
                        },
                        icon = Icons.Default.LocationOn,
                        onClick = { onOpenSection(SettingsSection.LOCATION) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                    )
                }
                if (matches("google", "client", "oauth", "sign", "setup", "more")) {
                    SettingsGroupRow(
                        title = "Google sign-in setup",
                        subtitle = if (state.gmailOAuthConnected || state.sheetTokenSet) {
                            "Connected · only change if sign-in fails"
                        } else {
                            "Only needed if “Connect with Google” fails"
                        },
                        icon = Icons.Default.VpnKey,
                        onClick = { onOpenSection(SettingsSection.GOOGLE_AUTH) },
                        iconContainer = scheme.surfaceContainerHighest,
                        iconTint = scheme.onSurfaceVariant,
                        showDivider = matches("location", "place", "map", "more"),
                    )
                }
            }
        }

        // ── Developer (hidden until version tapped 7×) ───────────────────
        if (showDev) {
            SettingsSectionLabel("Developer")
            GroupedCard {
                SettingsGroupRow(
                    title = "Developer options",
                    subtitle = "Prompts, delays, diagnostics",
                    icon = Icons.Default.Code,
                    onClick = { onOpenSection(SettingsSection.DEV) },
                    iconContainer = scheme.primaryContainer,
                    iconTint = scheme.onPrimaryContainer,
                )
                SettingsGroupRow(
                    title = "Test email parser",
                    subtitle = "Paste a sample email to try parsing",
                    icon = Icons.Default.Quickreply,
                    onClick = { onOpenSection(SettingsSection.PASTE) },
                    iconContainer = scheme.primaryContainer,
                    iconTint = scheme.onPrimaryContainer,
                    showDivider = true,
                )
                SettingsGroupRow(
                    title = "Hide developer settings",
                    subtitle = "Lock this section again",
                    icon = Icons.Default.Lock,
                    onClick = { vm.lockDev() },
                    iconContainer = scheme.primaryContainer,
                    iconTint = scheme.onPrimaryContainer,
                    showDivider = true,
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
