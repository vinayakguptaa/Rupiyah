package com.krtky.financetracker.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.data.prefs.SecureStore
import androidx.compose.ui.res.stringResource
import com.krtky.financetracker.R
import com.krtky.financetracker.ui.components.DeleteConfirmSheet
import com.krtky.financetracker.ui.components.HelpIcon
import com.krtky.financetracker.ui.components.SettingsSectionLabel
import com.krtky.financetracker.ui.components.SoftPanel
import com.krtky.financetracker.ui.components.HealthStyleComponents
import com.krtky.financetracker.ui.theme.ThemeColors
import com.krtky.financetracker.ui.theme.ThemeMode
import com.krtky.financetracker.ui.theme.colorOrDefault
import com.krtky.financetracker.ui.theme.previewColors
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsDetailScreen(
    section: String,
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val senders by vm.senders.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val shapes = MaterialTheme.shapes
    val scheme = MaterialTheme.colorScheme

    var llmKey by remember(state.llmApiKeySet) { mutableStateOf("") }
    var llmBase by remember(state.llmBaseUrl) { mutableStateOf(state.llmBaseUrl) }
    var llmModel by remember(state.llmModel) { mutableStateOf(state.llmModel) }
    var gmail by remember(state.gmail) { mutableStateOf(state.gmail) }
    var gmailPass by remember { mutableStateOf("") }
    var sheetId by remember(state.sheetId) { mutableStateOf(state.sheetId) }
    var sheetToken by remember { mutableStateOf("") }
    var themePrimary by remember(state.themeCustomPrimary) { mutableStateOf(state.themeCustomPrimary) }
    var themeSecondary by remember(state.themeCustomSecondary) { mutableStateOf(state.themeCustomSecondary) }
    var themeTertiary by remember(state.themeCustomTertiary) { mutableStateOf(state.themeCustomTertiary) }
    var senderEmail by remember { mutableStateOf("") }
    var senderLabel by remember { mutableStateOf("FamPay") }
    var pasteSender by remember { mutableStateOf("noreply@fampay.in") }
    var pasteSubject by remember { mutableStateOf("") }
    var pasteBody by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }
    var newCategoryIcon by remember { mutableStateOf("category") }
    var editCategoryId by remember { mutableStateOf<Long?>(null) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var categoryPendingDelete by remember { mutableStateOf<Long?>(null) }
    var showBankSheet by remember { mutableStateOf(false) }
    var newBankName by remember { mutableStateOf("") }
    var bankPendingDelete by remember { mutableStateOf<String?>(null) }
    var showSenderSheet by remember { mutableStateOf(false) }
    var senderPendingDelete by remember { mutableStateOf<Long?>(null) }
    var smsSenders by remember(state.smsSenders) { mutableStateOf(state.smsSenders) }
    var smsKeywords by remember(state.smsKeywords) { mutableStateOf(state.smsKeywords) }
    var banksRaw by remember(state.bankAccounts) { mutableStateOf(state.bankAccounts) }
    var defaultPay by remember(state.defaultPaymentMethod) { mutableStateOf(state.defaultPaymentMethod) }
    val bankList = remember(banksRaw) {
        banksRaw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
    fun persistBanks(list: List<String>) {
        val joined = list.joinToString(",")
        banksRaw = joined
        vm.saveBankAccounts(joined)
    }
    var systemPrompt by remember(state.llmSystemPrompt) { mutableStateOf(state.llmSystemPrompt.ifBlank { SecureStore.DEFAULT_LLM_SYSTEM }) }
    var classDelay by remember(state.classificationDelayMin) { mutableStateOf(state.classificationDelayMin.toString()) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            vm.setStatus("Exporting…")
            val r = vm.exportData(context, uri)
            vm.setStatus(r.fold({ "Exported settings & data" }, { it.message ?: "Export failed" }))
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
    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.data != null) {
            scope.launch { vm.completeGoogleSignIn(context, result.data) }
        }
    }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> vm.setSmsEnabled(granted) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        vm.setLocation(context, result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
    }

    var profileName by remember(state.displayName) { mutableStateOf(state.displayName) }
    var profileEmail by remember(state.profileEmail) { mutableStateOf(state.profileEmail) }
    var profilePhone by remember(state.profilePhone) { mutableStateOf(state.profilePhone) }

    val title = when (section) {
        "profile" -> "Profile"
        "appearance" -> "Appearance"
        "backup" -> "Backup & restore"
        "llm" -> "LLM Providers"
        "gmail" -> "Gmail IMAP"
        "email" -> "Email settings"
        "senders" -> "Trusted senders"
        "paste" -> "Paste email"
        "sms" -> "SMS transactions"
        "location" -> "Location"
        "sheets" -> "Google Sheets"
        "categories" -> "Categories"
        "banks" -> "Bank accounts"
        "dev" -> "Developer"
        else -> "Settings"
    }

    val listSections = section == "categories" || section == "banks" || section == "senders" || section == "email"
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                shape = MaterialTheme.shapes.extraLarge,
                color = scheme.surfaceContainerHigh,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.onBackground,
        )
        if (status != null) {
            Text(status!!, color = scheme.primary, style = MaterialTheme.typography.bodyMedium)
        }

        when (section) {
            "profile" -> SoftPanel(padded = true) {
                SettingsSectionLabel("About you")
                OutlinedTextField(
                    profileName,
                    { profileName = it },
                    label = { Text("Display name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = shapes.medium,
                )
                OutlinedTextField(
                    profileEmail,
                    { profileEmail = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = shapes.medium,
                )
                OutlinedTextField(
                    profilePhone,
                    { profilePhone = it },
                    label = { Text("Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = shapes.medium,
                )
                Button(
                    onClick = { vm.saveProfile(profileName, profileEmail, profilePhone) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.large,
                ) { Text("Save profile") }
            }

            "appearance" -> SoftPanel(padded = true) {
                SettingsSectionLabel(stringResource(R.string.theme_mode))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        HealthStyleComponents.ThemeModeOption(
                            mode = ThemeMode.MATERIAL_YOU,
                            label = "Dynamic",
                            selected = state.themeMode == ThemeMode.MATERIAL_YOU,
                            onClick = { vm.setThemeMode(ThemeMode.MATERIAL_YOU) },
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        HealthStyleComponents.ThemeModeOption(
                            mode = ThemeMode.PRESET,
                            label = "Presets",
                            selected = state.themeMode == ThemeMode.PRESET,
                            onClick = { vm.setThemeMode(ThemeMode.PRESET) },
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        HealthStyleComponents.ThemeModeOption(
                            mode = ThemeMode.CUSTOM,
                            label = "Custom",
                            selected = state.themeMode == ThemeMode.CUSTOM,
                            onClick = { vm.setThemeMode(ThemeMode.CUSTOM) },
                        )
                    }
                }
                if (state.themeMode == ThemeMode.MATERIAL_YOU && Build.VERSION.SDK_INT < 31) {
                    Text(
                        stringResource(R.string.material_you_api_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }

                ThemePreviewCard(
                    colors = when (state.themeMode) {
                        ThemeMode.PRESET -> state.themePreset.previewColors(isSystemInDarkTheme())
                        ThemeMode.CUSTOM -> ThemeColors(
                            colorOrDefault(themePrimary, androidx.compose.ui.graphics.Color(0xFF4253D4)),
                            colorOrDefault(themeSecondary, androidx.compose.ui.graphics.Color(0xFF5B647A)),
                            colorOrDefault(themeTertiary, androidx.compose.ui.graphics.Color(0xFF7153A8)),
                        )
                        ThemeMode.MATERIAL_YOU -> null
                    },
                    useScheme = state.themeMode == ThemeMode.MATERIAL_YOU,
                )

                if (state.themeMode == ThemeMode.PRESET) {
                    HealthStyleComponents.ThemePresetList(
                        selected = state.themePreset,
                        onSelect = { vm.setThemePreset(it) },
                        darkPreview = isSystemInDarkTheme(),
                    )
                }

                if (state.themeMode == ThemeMode.CUSTOM) {
                    HealthStyleComponents.ThreeNodeColorPicker(
                        primaryHex = themePrimary,
                        secondaryHex = themeSecondary,
                        tertiaryHex = themeTertiary,
                        onColorsChange = { p, s, t ->
                            themePrimary = p
                            themeSecondary = s
                            themeTertiary = t
                            vm.setThemeCustomColors(p, s, t)
                        },
                    )
                }
            }

            "backup" -> SoftPanel(padded = true) {
                SettingsSectionLabel("Data")
                Text(
                    "Export credentials, preferences, categories, funds and transactions as JSON.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                        exportLauncher.launch("finance-tracker-backup-$stamp.json")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.large,
                ) { Text("Export") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.large,
                ) { Text("Import") }
            }

            "llm" -> SoftPanel(padded = true) {
                SettingsSectionLabel("Provider")
                Text(
                    "OpenAI-compatible endpoint. See docs/OPENAI_API_KEY.md in the project repo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                OutlinedTextField(llmBase, { llmBase = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth(), shape = shapes.medium)
                OutlinedTextField(llmModel, { llmModel = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth(), shape = shapes.medium)
                OutlinedTextField(
                    llmKey,
                    { llmKey = it },
                    label = { Text(if (state.llmApiKeySet) "API key (saved — enter to replace)" else "API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.medium,
                )
                Button(onClick = { vm.saveLlm(llmBase, llmModel, llmKey.ifBlank { null }) }, modifier = Modifier.fillMaxWidth(), shape = shapes.large) {
                    Text("Save provider")
                }
                Text("Presets", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            llmBase = "https://api.groq.com/openai/v1"
                            llmModel = "llama-3.3-70b-versatile"
                        },
                        label = { Text("Groq") },
                        shape = shapes.medium,
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            llmBase = "https://api.openai.com/v1"
                            llmModel = "gpt-4o-mini"
                        },
                        label = { Text("OpenAI") },
                        shape = shapes.medium,
                    )
                }
            }

            "email" -> {
                SoftPanel(padded = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsSectionLabel("Gmail IMAP", modifier = Modifier.weight(1f))
                        HelpIcon(
                            title = "Gmail app password",
                            message = "Turn on 2-Step Verification, create an App Password for Mail, then enter it here. Your normal Gmail password will not work.",
                        )
                    }
                    OutlinedTextField(gmail, { gmail = it }, label = { Text("Gmail address") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = shapes.medium)
                    OutlinedTextField(
                        gmailPass,
                        { gmailPass = it },
                        label = { Text(if (state.gmailPassSet) "App password (saved)" else "App password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = shapes.medium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Live email monitor", modifier = Modifier.weight(1f))
                        Switch(checked = state.emailPoll, onCheckedChange = { vm.setEmailPoll(context, it) })
                    }
                    Button(onClick = { vm.saveGmail(gmail, gmailPass.ifBlank { null }) }, modifier = Modifier.fillMaxWidth(), shape = shapes.large) { Text("Save Gmail") }
                    FilledTonalButton(onClick = { scope.launch { vm.testGmail() } }, modifier = Modifier.fillMaxWidth(), shape = shapes.large) { Text("Test connection") }
                    OutlinedButton(onClick = { scope.launch { vm.pollNow() } }, modifier = Modifier.fillMaxWidth(), shape = shapes.large) { Text("Poll now") }
                }
                SoftPanel(padded = true) {
                    SettingsSectionLabel("Trusted senders")
                    Text(
                        "Only messages from these addresses or patterns are processed. Use + to add.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    if (senders.isEmpty()) {
                        Text("No trusted senders yet.", color = scheme.onSurfaceVariant)
                    }
                    senders.forEachIndexed { index, s ->
                        if (index > 0) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .background(scheme.secondaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Mail, null, tint = scheme.onSecondaryContainer)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.emailPattern, fontWeight = FontWeight.SemiBold)
                                Text(s.walletLabel, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { senderPendingDelete = s.id }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = scheme.error)
                            }
                        }
                    }
                }
            }

            "gmail" -> SoftPanel(padded = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsSectionLabel("Connection", modifier = Modifier.weight(1f))
                    HelpIcon(
                        title = "Gmail app password",
                        message = "Turn on 2-Step Verification in your Google account, create an App Password for Mail, then enter the 16-character password here. Your normal Gmail password will not work."
                    )
                }
                Text(
                    "Use a Google App Password. Live monitor uses IMAP IDLE for near-instant alerts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                OutlinedTextField(gmail, { gmail = it }, label = { Text("Gmail address") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = shapes.medium)
                OutlinedTextField(
                    gmailPass,
                    { gmailPass = it },
                    label = { Text(if (state.gmailPassSet) "App password (saved)" else "App password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = shapes.medium,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Live email monitor", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = state.emailPoll, onCheckedChange = { vm.setEmailPoll(context, it) })
                }
                Button(onClick = { vm.saveGmail(gmail, gmailPass.ifBlank { null }) }, modifier = Modifier.fillMaxWidth(), shape = shapes.large) {
                    Text("Save Gmail")
                }
                FilledTonalButton(onClick = { scope.launch { vm.testGmail() } }, modifier = Modifier.fillMaxWidth(), shape = shapes.large) {
                    Text("Test connection")
                }
                OutlinedButton(onClick = { scope.launch { vm.pollNow() } }, modifier = Modifier.fillMaxWidth(), shape = shapes.large) {
                    Text("Poll now")
                }
            }

            "senders" -> SoftPanel(padded = true) {
                SettingsSectionLabel("Senders")
                Text("Use + to add a trusted sender pattern.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                if (senders.isEmpty()) {
                    Text("No trusted senders yet.", color = scheme.onSurfaceVariant)
                }
                senders.forEachIndexed { index, s ->
                    if (index > 0) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(40.dp).background(scheme.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Mail, null, tint = scheme.onSecondaryContainer)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.emailPattern, fontWeight = FontWeight.SemiBold)
                            Text(s.walletLabel, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { senderPendingDelete = s.id }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = scheme.error)
                        }
                    }
                }
            }

            "paste" -> SoftPanel(padded = true) {
                SettingsSectionLabel("Test")
                OutlinedTextField(pasteSender, { pasteSender = it }, label = { Text("From") }, modifier = Modifier.fillMaxWidth(), shape = shapes.medium)
                OutlinedTextField(pasteSubject, { pasteSubject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), shape = shapes.medium)
                OutlinedTextField(pasteBody, { pasteBody = it }, label = { Text("Body") }, modifier = Modifier.fillMaxWidth(), minLines = 4, shape = shapes.medium)
                Button(
                    onClick = { scope.launch { vm.processPaste(pasteSender, pasteSubject, pasteBody) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.large,
                ) { Text("Process email") }
            }

            "sms" -> SoftPanel(padded = true) {
                SettingsSectionLabel("SMS monitoring")
                Text(
                    "Only messages from listed senders or messages containing one of the keywords are inspected. The parser can ignore non-transaction messages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Enable SMS monitoring", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = state.smsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                            else vm.setSmsEnabled(false)
                        },
                    )
                }
                OutlinedTextField(
                    smsSenders,
                    { smsSenders = it },
                    label = { Text("Allowed senders") },
                    supportingText = { Text("Comma-separated sender IDs, e.g. HDFCBK, AX-ICICIB") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = shapes.medium,
                )
                OutlinedTextField(
                    smsKeywords,
                    { smsKeywords = it },
                    label = { Text("Transaction keywords") },
                    supportingText = { Text("Comma-separated. A matching keyword allows inspection regardless of sender.") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = shapes.medium,
                )
                Button(
                    onClick = { vm.saveSmsRules(smsSenders, smsKeywords) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.large,
                ) { Text("Save SMS rules") }
            }

            "location" -> SoftPanel(padded = true) {
                SettingsSectionLabel("Tracking")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Background location", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = state.location,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            } else {
                                vm.setLocation(context, false)
                            }
                        },
                    )
                }
                Text(
                    "Matches transaction time to closest sample.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                if (Build.VERSION.SDK_INT >= 29) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large,
                    ) { Text("App permissions") }
                }
            }

            "sheets" -> SoftPanel(padded = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsSectionLabel("Sync", modifier = Modifier.weight(1f))
                    HelpIcon(
                        title = "Google Sheets setup",
                        message = "Paste a Google OAuth access token with the Sheets scope. The app can create a spreadsheet on your account and will use a Transactions tab for sync."
                    )
                }
                OutlinedTextField(sheetId, { sheetId = it }, label = { Text("Spreadsheet ID") }, modifier = Modifier.fillMaxWidth(), shape = shapes.medium)
                OutlinedTextField(
                    sheetToken,
                    { sheetToken = it },
                    label = { Text(if (state.sheetTokenSet) "OAuth access token (saved)" else "OAuth access token") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.medium,
                )
                FilledTonalButton(
                    onClick = { googleSignInLauncher.launch(vm.googleSignInIntent(context)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.large,
                ) { Text(if (state.sheetTokenSet) "Reconnect Google" else "Login with Google") }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Enable sync", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = state.sheetsSync, onCheckedChange = { vm.setSheets(it) })
                }
                Button(onClick = { vm.saveSheets(sheetId, sheetToken.ifBlank { null }) }, modifier = Modifier.fillMaxWidth(), shape = shapes.large) {
                    Text("Save")
                }
                FilledTonalButton(onClick = { scope.launch { vm.createSheetsSpreadsheet("Rupiyah") } }, modifier = Modifier.fillMaxWidth(), shape = shapes.large) {
                    Text("Create spreadsheet")
                }
                FilledTonalButton(onClick = { scope.launch { vm.syncSheetsNow() } }, modifier = Modifier.fillMaxWidth(), shape = shapes.large) {
                    Text("Sync now")
                }
            }

            "categories" -> SoftPanel(padded = true) {
                SettingsSectionLabel("Your categories")
                Text("Tap a row to edit. Use + to add a new category.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                if (categories.isEmpty()) {
                    Text("No categories yet.", color = scheme.onSurfaceVariant)
                }
                categories.forEachIndexed { index, c ->
                    if (index > 0) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                editCategoryId = c.id
                                newCategory = c.name
                                newCategoryIcon = c.icon
                                showCategorySheet = true
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(scheme.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(CategoryIcons.iconFor(c.icon, c.name), null, tint = scheme.onSecondaryContainer)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                buildString {
                                    append(c.icon)
                                    if (c.isQuickAction) append(" · quick action")
                                    if (c.isSystem) append(" · seeded")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { categoryPendingDelete = c.id }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = scheme.error)
                        }
                    }
                }
                Spacer(Modifier.height(56.dp))
            }

            "banks" -> {
                SoftPanel(padded = true) {
                    SettingsSectionLabel("Accounts")
                    Text(
                        "Selectable labels when logging money (in addition to Cash and UPI).",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    if (bankList.isEmpty()) {
                        Text("No bank accounts yet.", color = scheme.onSurfaceVariant)
                    }
                    bankList.forEachIndexed { index, bank ->
                        if (index > 0) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .background(scheme.secondaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.AccountBalance, null, tint = scheme.onSecondaryContainer)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(bank, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { bankPendingDelete = bank }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = scheme.error)
                            }
                        }
                    }
                }
                SoftPanel(padded = true) {
                    SettingsSectionLabel("Default payment method")
                    Text("Cash, Digital/UPI, or a bank name", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("Cash", "Digital").forEach { method ->
                            FilterChip(
                                selected = defaultPay.equals(method, true),
                                onClick = {
                                    defaultPay = method
                                    vm.saveDefaultPaymentMethod(method)
                                },
                                label = { Text(method) },
                                shape = shapes.medium,
                            )
                        }
                        bankList.forEach { bank ->
                            FilterChip(
                                selected = defaultPay.equals(bank, true),
                                onClick = {
                                    defaultPay = bank
                                    vm.saveDefaultPaymentMethod(bank)
                                },
                                label = { Text(bank) },
                                shape = shapes.medium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(56.dp))
            }

            "dev" -> SoftPanel(padded = true) {
                SettingsSectionLabel("LLM system prompt")
                Text(
                    "Used when the model extracts transactions from email/SMS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    systemPrompt,
                    { systemPrompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 10,
                    shape = shapes.medium,
                )
                Button(
                    onClick = { vm.saveSystemPrompt(systemPrompt) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.large,
                ) { Text("Save system prompt") }
                OutlinedButton(
                    onClick = {
                        vm.resetSystemPrompt()
                        systemPrompt = SecureStore.DEFAULT_LLM_SYSTEM
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.large,
                ) { Text("Reset to default") }

                SettingsSectionLabel("Classification")
                OutlinedTextField(
                    classDelay,
                    { classDelay = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Delay (minutes)") },
                    supportingText = { Text("How long before classification prompts fire") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = shapes.medium,
                )
                FilledTonalButton(
                    onClick = { vm.setClassificationDelay(classDelay.toLongOrNull() ?: 15L) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.large,
                ) { Text("Save delay") }

                SettingsSectionLabel("Diagnostics")
                Text("Package: com.krtky.financetracker", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text("LLM configured: ${state.llmApiKeySet}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text("Gmail configured: ${state.gmailPassSet}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text("IMAP live: ${state.emailPoll}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }

            else -> Text("Unknown section", color = scheme.error)
        }
        Spacer(Modifier.height(if (listSections) 88.dp else 32.dp))
    }

    if (section == "categories") {
        FloatingActionButton(
            onClick = {
                editCategoryId = null
                newCategory = ""
                newCategoryIcon = "category"
                showCategorySheet = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
            shape = shapes.large,
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        ) { Icon(Icons.Default.Add, contentDescription = "Add category") }
    }
    if (section == "banks") {
        FloatingActionButton(
            onClick = {
                newBankName = ""
                showBankSheet = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
            shape = shapes.large,
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        ) { Icon(Icons.Default.Add, contentDescription = "Add bank") }
    }
    if (section == "senders" || section == "email") {
        FloatingActionButton(
            onClick = {
                senderEmail = ""
                senderLabel = "FamPay"
                showSenderSheet = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
            shape = shapes.large,
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        ) { Icon(Icons.Default.Add, contentDescription = "Add sender") }
    }
    }

    if (showCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (editCategoryId == null) "Add category" else "Edit category",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    newCategory,
                    { newCategory = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.medium,
                )
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryIcons.all.forEach { entry ->
                        val selected = newCategoryIcon == entry.id
                        Surface(
                            onClick = { newCategoryIcon = entry.id },
                            shape = CircleShape,
                            color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest,
                        ) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    entry.icon,
                                    contentDescription = entry.label,
                                    tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        val id = editCategoryId
                        if (id == null) vm.addCategory(newCategory, newCategoryIcon, quick = true)
                        else vm.updateCategory(id, newCategory, newCategoryIcon, quick = true)
                        newCategory = ""
                        newCategoryIcon = "category"
                        editCategoryId = null
                        showCategorySheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.extraLarge,
                    enabled = newCategory.isNotBlank(),
                ) { Text(if (editCategoryId == null) "Add category" else "Save changes") }
                OutlinedButton(
                    onClick = { showCategorySheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.extraLarge,
                ) { Text("Cancel") }
            }
        }
    }

    if (showBankSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBankSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Add bank account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    newBankName,
                    { newBankName = it },
                    label = { Text("Bank name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = shapes.medium,
                )
                Button(
                    onClick = {
                        val name = newBankName.trim()
                        if (name.isNotEmpty() && bankList.none { it.equals(name, true) }) {
                            persistBanks(bankList + name)
                        }
                        newBankName = ""
                        showBankSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.extraLarge,
                    enabled = newBankName.isNotBlank(),
                ) { Text("Add bank") }
                OutlinedButton(
                    onClick = { showBankSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.extraLarge,
                ) { Text("Cancel") }
            }
        }
    }

    if (showSenderSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSenderSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Add trusted sender", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    senderEmail,
                    { senderEmail = it },
                    label = { Text("Sender email or pattern") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.medium,
                )
                OutlinedTextField(
                    senderLabel,
                    { senderLabel = it },
                    label = { Text("Bank / wallet label") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.medium,
                )
                Button(
                    onClick = {
                        vm.addSender(senderEmail, senderLabel)
                        senderEmail = ""
                        showSenderSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.extraLarge,
                    enabled = senderEmail.isNotBlank(),
                ) { Text("Add sender") }
                OutlinedButton(
                    onClick = { showSenderSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.extraLarge,
                ) { Text("Cancel") }
            }
        }
    }

    categoryPendingDelete?.let { id ->
        DeleteConfirmSheet(
            title = "Delete category?",
            message = "This category will be removed from the list.",
            onDismiss = { categoryPendingDelete = null },
            onConfirmDelete = {
                vm.deleteCategory(id)
                categoryPendingDelete = null
            },
        )
    }
    bankPendingDelete?.let { bank ->
        DeleteConfirmSheet(
            title = "Remove bank?",
            message = "“$bank” will be removed from selectable payment methods.",
            onDismiss = { bankPendingDelete = null },
            onConfirmDelete = {
                persistBanks(bankList.filterNot { it.equals(bank, true) })
                if (defaultPay.equals(bank, true)) {
                    defaultPay = "Cash"
                    vm.saveDefaultPaymentMethod("Cash")
                }
                bankPendingDelete = null
            },
        )
    }
    senderPendingDelete?.let { id ->
        DeleteConfirmSheet(
            title = "Remove sender?",
            message = "Messages from this pattern will no longer be processed.",
            onDismiss = { senderPendingDelete = null },
            onConfirmDelete = {
                vm.deleteSender(id)
                senderPendingDelete = null
            },
        )
    }
}
