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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krtky.financetracker.BuildConfig
import com.krtky.financetracker.R
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
    onImportStatement: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    var versionTaps by remember { mutableIntStateOf(0) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

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

    val showYou = matches("profile", "name", "phone", "you")
    val showMoney = matches(
        "categories", "accounts", "bank", "money", "wallet", "cash", "digital", "upi",
    )
    val showCapture = matches(
        "sms", "bank", "import", "message", "text", "csv", "statement",
        "llm", "ai", "intelligence", "openai", "groq", "model", "smart", "helper", "capture",
    )
    val showLook = matches("appearance", "theme", "color", "dark", "light", "look", "font")
    val showCopies = matches(
        "backup", "restore", "export", "import", "sheet", "spreadsheet", "google", "save", "copy", "json", "csv",
    )
    val showOptional = matches(
        "location", "place", "map", "optional",
    )
    val showDev = state.devUnlocked && matches(
        "developer", "dev", "prompt", "diagnostics", "paste", "test", "parser",
    )

    val bankCount = state.bankAccounts.split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .size // mirrored from active accounts; archived not counted

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
        ScreenHeader(
            title = "Settings",
            actions = {
                IconButton(
                    onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) searchQuery = ""
                    },
                ) {
                    Icon(
                        if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = stringResource(
                            if (searchOpen) R.string.cd_close_search else R.string.cd_search_settings,
                        ),
                    )
                }
            },
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

        // ── Money setup ──────────────────────────────────────────────────
        if (showMoney) {
            SettingsSectionLabel("Money setup")
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
                            bankCount == 0 -> "Opens Accounts — add banks and UPI apps"
                            else -> {
                                val def = state.defaultDigitalAccount.trim()
                                buildString {
                                    append("Opens Accounts · $bankCount")
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

        // ── Capture ──────────────────────────────────────────────────────
        if (showCapture) {
            SettingsSectionLabel("Capture")
            GroupedCard {
                val showAi = matches(
                    "llm", "ai", "intelligence", "openai", "groq", "model", "smart", "helper", "capture", "sms",
                )
                val showSms = matches("sms", "text", "message", "import", "bank", "capture")
                val showCsv = matches("csv", "statement", "import", "bank", "capture")
                if (showAi) {
                    SettingsGroupRow(
                        title = "AI helper",
                        subtitle = when {
                            state.llmReady -> "Ready · used for SMS and messy text"
                            state.llmEnabled -> "Almost ready · add an API key"
                            else -> "Off · better SMS, paste and auto-classify"
                        },
                        icon = Icons.Default.Psychology,
                        onClick = { onOpenSection(SettingsSection.LLM) },
                        iconContainer = scheme.secondaryContainer,
                        iconTint = scheme.onSecondaryContainer,
                        showDivider = showSms || showCsv,
                    )
                }
                if (showSms) {
                    SettingsGroupRow(
                        title = "Bank text messages (SMS)",
                        subtitle = when {
                            state.smsEnabled -> "On · reading bank SMS on this phone"
                            else -> "Turn on to read bank SMS"
                        },
                        icon = Icons.Default.Sms,
                        onClick = { onOpenSection(SettingsSection.SMS) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                        showDivider = showCsv,
                    )
                }
                if (showCsv) {
                    SettingsGroupRow(
                        title = "Import bank statement",
                        subtitle = "CSV from your bank or wallet into one account",
                        icon = Icons.Default.UploadFile,
                        onClick = onImportStatement,
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                        showDivider = false,
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

        // ── Copies ───────────────────────────────────────────────────────
        if (showCopies) {
            SettingsSectionLabel("Copies")
            GroupedCard {
                val showBackup = matches("backup", "restore", "export", "import", "save", "copy", "json", "csv")
                val showSheets = matches("sheet", "spreadsheet", "google", "save", "copy", "export")
                if (showBackup) {
                    SettingsGroupRow(
                        title = "Backup & restore",
                        subtitle = "JSON safety copy, or merge an Activity CSV",
                        icon = Icons.Default.Backup,
                        onClick = { onOpenSection(SettingsSection.BACKUP) },
                        iconContainer = scheme.primaryContainer,
                        iconTint = scheme.onPrimaryContainer,
                        showDivider = showSheets,
                    )
                }
                if (showSheets) {
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
                        showDivider = false,
                    )
                }
            }
        }

        // ── Optional ─────────────────────────────────────────────────────
        if (showOptional) {
            SettingsSectionLabel("Optional")
            GroupedCard {
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
            "Rupiyah · v${BuildConfig.VERSION_NAME}",
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
