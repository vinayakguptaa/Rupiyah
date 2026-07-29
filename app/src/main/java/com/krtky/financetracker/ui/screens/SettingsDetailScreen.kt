package com.krtky.financetracker.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.data.email.EmailSource
import com.krtky.financetracker.data.prefs.SecureStore
import androidx.compose.ui.res.stringResource
import com.krtky.financetracker.R
import com.krtky.financetracker.ui.components.DeleteConfirmSheet
import com.krtky.financetracker.ui.components.SettingsBlock
import com.krtky.financetracker.ui.components.SettingsPanelLabel
import com.krtky.financetracker.ui.components.SettingsSegment
import com.krtky.financetracker.ui.components.SettingsSegmentedRow
import com.krtky.financetracker.ui.components.SettingsStatusText
import com.krtky.financetracker.ui.components.SettingsToggleRow
import com.krtky.financetracker.ui.components.GroupedCard
import com.krtky.financetracker.ui.components.chrome.StackTopBar
import com.krtky.financetracker.ui.navigation.SettingsSection
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.categoryColor
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.onCategoryColor
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
    val sectionEnum = SettingsSection.fromRoute(section)
    // No BackHandler — NavHost owns system/predictive back so Android 14+ animation works.
    val state by vm.state.collectAsStateWithLifecycle()
    val senders by vm.senders.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accountBalances by vm.accountBalances.collectAsStateWithLifecycle()
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
    var googleClientId by remember(state.googleWebClientId) { mutableStateOf(state.googleWebClientId) }
    // Init once from prefs; do not re-key on every DataStore emit (breaks sliders mid-drag).
    var themePrimary by remember { mutableStateOf(state.themeCustomPrimary) }
    var themeSecondary by remember { mutableStateOf(state.themeCustomSecondary) }
    var themeTertiary by remember { mutableStateOf(state.themeCustomTertiary) }
    var senderEmail by remember { mutableStateOf("") }
    var senderLabel by remember { mutableStateOf("FamPay") }
    var pasteSender by remember { mutableStateOf("noreply@fampay.in") }
    var pasteSubject by remember { mutableStateOf("") }
    var pasteBody by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }
    var newCategoryIcon by remember { mutableStateOf("category") }
    var newCategoryColor by remember { mutableStateOf(0xFF0B6E4FL) }
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
    var defaultDigital by remember(state.defaultDigitalAccount) {
        mutableStateOf(state.defaultDigitalAccount)
    }
    val bankList = remember(banksRaw) {
        banksRaw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
    fun persistBanks(list: List<String>) {
        val joined = list.joinToString(",")
        banksRaw = joined
        vm.saveBankAccounts(joined)
        // Drop default digital if the account was removed
        if (defaultDigital.isNotBlank() && list.none { it.equals(defaultDigital, true) }) {
            defaultDigital = ""
            vm.saveDefaultDigitalAccount("")
        }
        if (defaultPay.isNotBlank() &&
            !defaultPay.equals("Cash", true) &&
            !defaultPay.equals("Digital", true) &&
            list.none { it.equals(defaultPay, true) }
        ) {
            defaultPay = "Cash"
            vm.saveDefaultPaymentMethod("Cash")
        }
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
    val gmailSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.data != null) {
            scope.launch { vm.completeGmailSignIn(context, result.data) }
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

    val title = sectionEnum?.title ?: "Settings"

    val listSections = section == "categories" || section == "banks" || section == "senders" || section == "email"
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        StackTopBar(title = title, onBack = onBack)
        // Progress-only status (saves go to snackbar via UiMessenger)
        if (status != null) {
            Text(status!!, color = scheme.primary, style = MaterialTheme.typography.bodyMedium)
        }

        when (section) {
            "profile" -> SettingsBlock(
                title = "About you",
                helpTitle = "Profile",
                helpMessage = "Optional display details for you. They stay on this device and are included in JSON backups.",
            ) {
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

            "appearance" -> AppearanceSettingsContent(
                state = state,
                onDarkModeChange = vm::setDarkModePref,
                onThemeModeChange = vm::setThemeMode,
                onPresetChange = vm::setThemePreset,
                onCustomColorsChange = { p, s, t ->
                    themePrimary = p
                    themeSecondary = s
                    themeTertiary = t
                    vm.setThemeCustomColors(p, s, t)
                },
                onSchemeStyleChange = vm::setThemeSchemeStyle,
                onContrastChange = vm::setContrastLevel,
                onTypographyModeChange = vm::setTypographyMode,
                onOledModeChange = vm::setOledMode,
                themePrimary = themePrimary,
                themeSecondary = themeSecondary,
                themeTertiary = themeTertiary,
            )

            "backup" -> SettingsBlock(
                title = "Data",
                helpTitle = "Backup & restore",
                helpMessage = "Export credentials, preferences, categories, funds, and transactions as a JSON file. Import restores from a previous export. Keep backups somewhere safe — they can include API keys.",
            ) {
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

            "llm" -> {
                SettingsBlock(
                    title = "Provider",
                    helpTitle = "LLM provider",
                    helpMessage = "Any OpenAI-compatible endpoint works (OpenAI, Groq, local proxies). Save base URL, model, and API key. See docs/OPENAI_API_KEY.md in the project repo for setup notes.",
                ) {
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
                    Button(
                        onClick = { vm.saveLlm(llmBase, llmModel, llmKey.ifBlank { null }) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large,
                    ) { Text("Save provider") }
                }
                SettingsBlock(title = "Presets") {
                    SettingsSegmentedRow {
                        SettingsSegment(
                            label = "Groq",
                            selected = llmBase.contains("groq", ignoreCase = true),
                            onClick = {
                                llmBase = "https://api.groq.com/openai/v1"
                                llmModel = "llama-3.3-70b-versatile"
                            },
                        )
                        SettingsSegment(
                            label = "OpenAI",
                            selected = llmBase.contains("openai.com", ignoreCase = true),
                            onClick = {
                                llmBase = "https://api.openai.com/v1"
                                llmModel = "gpt-4o-mini"
                            },
                        )
                    }
                    SettingsStatusText(
                        text = if (state.llmApiKeySet) "Key saved · ${state.llmModel}" else "No API key yet",
                        positive = state.llmApiKeySet,
                    )
                }
            }

            "email", "gmail" -> {
                SettingsBlock(
                    title = "Connection",
                    helpTitle = "Email connection",
                    helpMessage = "Google Sign-In uses Gmail.readonly and does not store your password. IMAP uses a Gmail App Password (2-Step Verification required). Live monitor only processes mail from Trusted senders.",
                ) {
                    SettingsSegmentedRow {
                        SettingsSegment(
                            label = "Google",
                            selected = state.emailSource == EmailSource.GMAIL_OAUTH,
                            onClick = { vm.setEmailSource(EmailSource.GMAIL_OAUTH) },
                        )
                        SettingsSegment(
                            label = "IMAP",
                            selected = state.emailSource == EmailSource.IMAP,
                            onClick = { vm.setEmailSource(EmailSource.IMAP) },
                        )
                    }
                    SettingsToggleRow(
                        title = "Live email monitor",
                        subtitle = if (state.emailSource == EmailSource.GMAIL_OAUTH) {
                            "Gmail history · trusted senders only"
                        } else {
                            "IMAP IDLE + poll"
                        },
                        checked = state.emailPoll,
                        onCheckedChange = { vm.setEmailPoll(context, it) },
                    )
                }
                if (state.emailSource == EmailSource.GMAIL_OAUTH) {
                    SettingsBlock(
                        title = "Gmail",
                        helpTitle = "Gmail via Google Sign-In",
                        helpMessage = "Uses Gmail API gmail.readonly. Live monitor watches mailbox history, then only opens messages from Trusted senders — it does not re-download the whole inbox. Set up the Web Client ID under Google Auth if sign-in fails.",
                    ) {
                        SettingsStatusText(
                            text = if (state.gmailOAuthConnected) {
                                "Connected as ${state.gmailOAuthEmail.ifBlank { "Google account" }}"
                            } else {
                                "Not connected"
                            },
                            positive = state.gmailOAuthConnected,
                        )
                        if (state.gmailOAuthConnected) {
                            OutlinedButton(
                                onClick = { vm.disconnectGmailOAuth() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = shapes.large,
                            ) { Text("Disconnect Google") }
                        }
                        Button(
                            onClick = { gmailSignInLauncher.launch(vm.gmailSignInIntent(context)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = shapes.large,
                        ) {
                            Text(if (state.gmailOAuthConnected) "Reconnect Google" else "Connect with Google")
                        }
                        FilledTonalButton(
                            onClick = { scope.launch { vm.testGmail() } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = shapes.large,
                        ) { Text("Test connection") }
                        OutlinedButton(
                            onClick = { scope.launch { vm.pollNow() } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = shapes.large,
                        ) { Text("Poll now") }
                    }
                } else {
                    SettingsBlock(
                        title = "Gmail IMAP",
                        helpTitle = "Gmail App Password",
                        helpMessage = "Turn on 2-Step Verification in Google Account, create an App Password for Mail, then enter it here. Your normal Gmail password will not work.",
                    ) {
                        OutlinedTextField(
                            gmail,
                            { gmail = it },
                            label = { Text("Gmail address") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = shapes.medium,
                        )
                        OutlinedTextField(
                            gmailPass,
                            { gmailPass = it },
                            label = { Text(if (state.gmailPassSet) "App password (saved)" else "App password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = shapes.medium,
                        )
                        Button(
                            onClick = { vm.saveGmail(gmail, gmailPass.ifBlank { null }) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = shapes.large,
                        ) { Text("Save Gmail") }
                        FilledTonalButton(
                            onClick = { scope.launch { vm.testGmail() } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = shapes.large,
                        ) { Text("Test connection") }
                        OutlinedButton(
                            onClick = { scope.launch { vm.pollNow() } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = shapes.large,
                        ) { Text("Poll now") }
                    }
                }
                if (section == "email") {
                    SettingsBlock(
                        title = "Trusted senders",
                        helpTitle = "Trusted senders",
                        helpMessage = "Only messages from these addresses or patterns are processed. Tap + to add a sender pattern and wallet label.",
                    ) {
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
            }

            "senders" -> {
                SettingsBlock(
                    title = "Senders",
                    helpTitle = "Trusted senders",
                    helpMessage = "Only mail matching these patterns is parsed into transactions. Use + to add an address or pattern and a wallet label (e.g. FamPay).",
                ) {
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
                SettingsBlock(
                    title = "SMS rules",
                    helpTitle = "SMS filtering",
                    helpMessage = "Allowed senders are comma-separated IDs (e.g. HDFCBK, AX-ICICIB). Keywords let a message through even if the sender is unknown.",
                ) {
                    SettingsToggleRow(
                        title = "Enable SMS monitoring",
                        checked = state.smsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                            else vm.setSmsEnabled(false)
                        },
                    )
                    OutlinedTextField(
                        smsSenders,
                        { smsSenders = it },
                        label = { Text("SMS allowed senders") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = shapes.medium,
                    )
                    OutlinedTextField(
                        smsKeywords,
                        { smsKeywords = it },
                        label = { Text("SMS transaction keywords") },
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
            }

            "paste" -> SettingsBlock(
                title = "Test parser",
                helpTitle = "Paste email",
                helpMessage = "Paste a sample bank or wallet email to test LLM extraction without waiting for live poll.",
            ) {
                OutlinedTextField(pasteSender, { pasteSender = it }, label = { Text("From") }, modifier = Modifier.fillMaxWidth(), shape = shapes.medium)
                OutlinedTextField(pasteSubject, { pasteSubject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), shape = shapes.medium)
                OutlinedTextField(pasteBody, { pasteBody = it }, label = { Text("Body") }, modifier = Modifier.fillMaxWidth(), minLines = 4, shape = shapes.medium)
                Button(
                    onClick = { scope.launch { vm.processPaste(pasteSender, pasteSubject, pasteBody) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.large,
                ) { Text("Process email") }
            }

            "sms" -> SettingsBlock(
                title = "SMS",
                helpTitle = "SMS transactions",
                helpMessage = "When enabled, bank SMS can create draft transactions. Configure allowed senders and keywords under Trusted senders.",
            ) {
                SettingsToggleRow(
                    title = "Enable SMS monitoring",
                    subtitle = if (state.smsEnabled) "Listening for bank SMS" else "Off",
                    checked = state.smsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                        else vm.setSmsEnabled(false)
                    },
                )
            }

            "location" -> SettingsBlock(
                title = "Tracking",
                helpTitle = "Location",
                helpMessage = "Optional background samples are matched to transaction times so you can see where a spend likely happened. Data stays on device.",
            ) {
                SettingsToggleRow(
                    title = "Background location",
                    subtitle = if (state.location) "Sampling on" else "Off",
                    checked = state.location,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        } else {
                            vm.setLocation(context, false)
                        }
                    },
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

            "sheets" -> SettingsBlock(
                title = "Sync",
                helpTitle = "Google Sheets",
                helpMessage = "One-way export to a spreadsheet on your Google account. Connect via Google Sign-In (or paste an OAuth token), optionally create a workbook, then enable sync. Tabs include Transactions, Dashboard, Monthly, Categories, Accounts, Funds, and Merchants.",
            ) {
                SettingsStatusText(
                    text = if (state.sheetTokenSet) "Google connected" else "Not connected",
                    positive = state.sheetTokenSet,
                )
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
                SettingsToggleRow(
                    title = "Enable sync",
                    checked = state.sheetsSync,
                    onCheckedChange = { vm.setSheets(it) },
                )
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

            "google_auth" -> {
                SettingsBlock(
                    title = "Web Client ID",
                    helpTitle = "How to get a Client ID",
                    helpMessage = "1. Go to console.cloud.google.com\n" +
                        "2. Create or select a project\n" +
                        "3. Enable Gmail API and Sheets API\n" +
                        "4. Credentials → Create Credentials → OAuth client ID\n" +
                        "5. Application type: Web application (not Android)\n" +
                        "6. Copy the Client ID (ends with .apps.googleusercontent.com)\n\n" +
                        "Required for Google Sign-In. If empty, the app falls back to google-services.json when present. Tokens are stored in EncryptedSharedPreferences.\n\n" +
                        "Scopes used:\n• Gmail: gmail.readonly (read only)\n• Sheets: spreadsheets (create & update)",
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
                    ) { Text("Save Client ID") }
                }

                SettingsBlock(
                    title = "Gmail",
                    helpTitle = "Gmail OAuth",
                    helpMessage = "Scope: gmail.readonly. Reads mail from trusted senders only to auto-detect transactions. Watches mailbox history changes — not full-inbox sync.",
                ) {
                    SettingsStatusText(
                        text = if (state.gmailOAuthConnected) {
                            "Connected as ${state.gmailOAuthEmail.ifBlank { "Google account" }}"
                        } else {
                            "Not connected"
                        },
                        positive = state.gmailOAuthConnected,
                    )
                    if (state.gmailOAuthConnected) {
                        OutlinedButton(
                            onClick = { vm.disconnectGmailOAuth() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = shapes.large,
                        ) { Text("Disconnect") }
                    }
                    Button(
                        onClick = { gmailSignInLauncher.launch(vm.gmailSignInIntent(context)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large,
                    ) {
                        Text(if (state.gmailOAuthConnected) "Reconnect" else "Connect Gmail")
                    }
                }

                SettingsBlock(
                    title = "Sheets",
                    helpTitle = "Google Sheets OAuth",
                    helpMessage = "Scope: spreadsheets. One-way sync from the app to a Google Spreadsheet with Transactions, Dashboard, Monthly, Categories, Accounts, Funds, and Merchants tabs.",
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
                        Text(if (state.sheetTokenSet) "Reconnect" else "Connect Sheets")
                    }
                }
            }

            "categories" -> SettingsBlock(
                title = "Your categories",
                helpTitle = "Categories",
                helpMessage = "Tap a row to edit name, icon, or color. Use + to add. Seeded system categories can be customized; delete only removes categories you no longer need.",
            ) {
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
                                newCategoryColor = c.color
                                showCategorySheet = true
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val catColor = categoryColor(c.color) ?: Color(c.color)
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(catColor, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                CategoryIcons.iconFor(c.icon, c.name),
                                contentDescription = null,
                                tint = onCategoryColor(catColor),
                            )
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
                SettingsBlock(
                    title = "Payment modes",
                    helpTitle = "Cash & Digital",
                    helpMessage = "Cash and Digital are payment modes. Named banks and UPI wallets live under Digital and are summed into the Digital total.",
                ) {
                    AccountBalanceRow(
                        name = "Cash",
                        balancePaise = accountBalances["Cash"] ?: 0L,
                        isDefaultDigital = false,
                        subtitle = "Payment mode",
                        onDelete = null,
                    )
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    val digitalTotal = bankList.sumOf { bank ->
                        accountBalances.entries.firstOrNull { it.key.equals(bank, true) }?.value ?: 0L
                    } + (accountBalances["Digital"] ?: 0L)
                    AccountBalanceRow(
                        name = "Digital (all banks)",
                        balancePaise = digitalTotal,
                        isDefaultDigital = false,
                        subtitle = "Payment mode · sum of accounts below",
                        onDelete = null,
                    )
                }
                SettingsBlock(
                    title = "Digital accounts",
                    helpTitle = "Banks & wallets",
                    helpMessage = "Add HDFC, ICICI, PhonePe, etc. AI matches these from email/SMS when possible. Tap + to add.",
                ) {
                    if (bankList.isEmpty()) {
                        Text(
                            "No digital accounts yet — tap + to add",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    bankList.forEachIndexed { index, bank ->
                        if (index > 0 || bankList.isNotEmpty()) {
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                        }
                        val bal = accountBalances.entries
                            .firstOrNull { it.key.equals(bank, true) }
                            ?.value
                            ?: 0L
                        AccountBalanceRow(
                            name = bank,
                            balancePaise = bal,
                            isDefaultDigital = defaultDigital.equals(bank, true),
                            onDelete = { bankPendingDelete = bank },
                        )
                    }
                    val known = (bankList + listOf("Cash", "Digital")).map { it.lowercase() }.toSet()
                    accountBalances
                        .filterKeys { it.lowercase() !in known }
                        .forEach { (name, bal) ->
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            AccountBalanceRow(
                                name = name,
                                balancePaise = bal,
                                isDefaultDigital = false,
                                subtitle = "Detected from transactions",
                                onDelete = null,
                            )
                        }
                }
                SettingsBlock(
                    title = "Defaults",
                    helpTitle = "Default payment",
                    helpMessage = "Default payment mode is pre-selected when you add a transaction manually. Default digital account is used when payment is Digital and AI cannot detect which bank or wallet was used.",
                ) {
                    SettingsPanelLabel("Payment mode")
                    SettingsSegmentedRow {
                        listOf("Cash", "Digital").forEach { method ->
                            SettingsSegment(
                                label = method,
                                selected = defaultPay.equals(method, true) ||
                                    (method == "Digital" && bankList.any { it.equals(defaultPay, true) }),
                                onClick = {
                                    defaultPay = method
                                    vm.saveDefaultPaymentMethod(method)
                                },
                            )
                        }
                    }
                    SettingsPanelLabel("Digital account")
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = defaultDigital.isBlank(),
                            onClick = {
                                defaultDigital = ""
                                vm.saveDefaultDigitalAccount("")
                            },
                            label = { Text("Auto") },
                            shape = shapes.medium,
                        )
                        bankList.forEach { bank ->
                            FilterChip(
                                selected = defaultDigital.equals(bank, true),
                                onClick = {
                                    defaultDigital = bank
                                    vm.saveDefaultDigitalAccount(bank)
                                },
                                label = { Text(bank) },
                                shape = shapes.medium,
                            )
                        }
                    }
                    if (bankList.isEmpty()) {
                        Text(
                            "Add a digital account to set a default bank.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(56.dp))
            }

            "dev" -> {
                SettingsBlock(
                    title = "System prompt",
                    helpTitle = "LLM system prompt",
                    helpMessage = "Instructions sent when the model extracts transactions from email or SMS. Reset restores the built-in default.",
                ) {
                    OutlinedTextField(
                        systemPrompt,
                        { systemPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 8,
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
                }
                SettingsBlock(
                    title = "Classification",
                    helpTitle = "Classification delay",
                    helpMessage = "Minutes to wait before classification prompts fire for new drafts.",
                ) {
                    OutlinedTextField(
                        classDelay,
                        { classDelay = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Delay (minutes)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = shapes.medium,
                    )
                    FilledTonalButton(
                        onClick = { vm.setClassificationDelay(classDelay.toLongOrNull() ?: 15L) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large,
                    ) { Text("Save delay") }
                }
                SettingsBlock(title = "Test parser") {
                    OutlinedTextField(pasteSender, { pasteSender = it }, label = { Text("From") }, modifier = Modifier.fillMaxWidth(), shape = shapes.medium)
                    OutlinedTextField(pasteSubject, { pasteSubject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), shape = shapes.medium)
                    OutlinedTextField(pasteBody, { pasteBody = it }, label = { Text("Body") }, modifier = Modifier.fillMaxWidth(), minLines = 4, shape = shapes.medium)
                    Button(
                        onClick = { scope.launch { vm.processPaste(pasteSender, pasteSubject, pasteBody) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large,
                    ) { Text("Process email") }
                }
                SettingsBlock(
                    title = "Diagnostics",
                    helpTitle = "Diagnostics",
                    helpMessage = "Read-only status for debugging ingestion and LLM configuration.",
                ) {
                    Text("Package: com.krtky.financetracker", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    Text("LLM configured: ${state.llmApiKeySet}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    Text(
                        "Gmail: source=${state.emailSource} imap=${state.gmailPassSet} oauth=${state.gmailOAuthConnected}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text("Email monitor: ${state.emailPoll}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    Button(
                        onClick = { vm.lockDev() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large,
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                    ) { Text("Lock developer settings") }
                }
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
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 20.dp),
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
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 20.dp),
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
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 20.dp),
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
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
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
                // Live preview of icon + color as they will appear in lists
                val previewColor = categoryColor(newCategoryColor) ?: Color(newCategoryColor)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(previewColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            CategoryIcons.iconFor(newCategoryIcon, newCategory),
                            contentDescription = null,
                            tint = onCategoryColor(previewColor),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            newCategory.ifBlank { "Category preview" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "How this looks on transaction lists",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
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
                            color = if (selected) previewColor else scheme.surfaceContainerHighest,
                            border = if (selected) BorderStroke(2.dp, scheme.outline) else null,
                        ) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    entry.icon,
                                    contentDescription = entry.label,
                                    tint = if (selected) onCategoryColor(previewColor) else scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val categoryColors = listOf(
                        0xFFE74C3CL, 0xFFE67E22L, 0xFFF1C40FL, 0xFF2ECC71L,
                        0xFF1ABC9CL, 0xFF3498DBL, 0xFF9B59B6L, 0xFFE91E63L,
                        0xFF795548L, 0xFF607D8BL, 0xFF34495EL, 0xFF7F8C8DL,
                        0xFF0B6E4FL,
                    )
                    categoryColors.forEach { colorLong ->
                        val color = categoryColor(colorLong) ?: Color(colorLong)
                        val selected = newCategoryColor == colorLong
                        Surface(
                            onClick = { newCategoryColor = colorLong },
                            shape = CircleShape,
                            color = color,
                            border = if (selected) {
                                BorderStroke(3.dp, scheme.onSurface)
                            } else {
                                BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f))
                            },
                        ) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = onCategoryColor(color),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        val id = editCategoryId
                        if (id == null) vm.addCategory(newCategory, newCategoryIcon, newCategoryColor, true)
                        else vm.updateCategory(id, newCategory, newCategoryIcon, newCategoryColor, true)
                        newCategory = ""
                        newCategoryIcon = "category"
                        newCategoryColor = 0xFF0B6E4FL
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
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Add account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Bank, UPI app, or wallet name. AI matches these labels in emails and SMS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    newBankName,
                    { newBankName = it },
                    label = { Text("Account name") },
                    placeholder = { Text("e.g. HDFC, PhonePe, Axis") },
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
                ) { Text("Add account") }
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
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
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
            title = "Remove account?",
            message = "“$bank” will be removed from selectable accounts. Existing transactions keep their labels.",
            onDismiss = { bankPendingDelete = null },
            onConfirmDelete = {
                persistBanks(bankList.filterNot { it.equals(bank, true) })
                if (defaultPay.equals(bank, true)) {
                    defaultPay = "Cash"
                    vm.saveDefaultPaymentMethod("Cash")
                }
                if (defaultDigital.equals(bank, true)) {
                    defaultDigital = ""
                    vm.saveDefaultDigitalAccount("")
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

@Composable
private fun AccountBalanceRow(
    name: String,
    balancePaise: Long,
    isDefaultDigital: Boolean,
    onDelete: (() -> Unit)?,
    subtitle: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
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
            Icon(Icons.Default.AccountBalance, null, tint = scheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isDefaultDigital) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = scheme.primaryContainer,
                    ) {
                        Text(
                            "Default",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                subtitle ?: balancePaise.inr(),
                style = MaterialTheme.typography.bodySmall,
                color = if (balancePaise < 0) scheme.error else scheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Text(
                    balancePaise.inr(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (balancePaise < 0) scheme.error else scheme.onSurface,
                )
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = scheme.error)
            }
        }
    }
}
