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
import com.krtky.financetracker.ui.components.AppSecondaryButton
import com.krtky.financetracker.ui.components.DeleteConfirmSheet
import com.krtky.financetracker.ui.components.SettingsBlock
import com.krtky.financetracker.ui.components.SettingsButtonStack
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
    val managedAccountBalances by vm.managedAccountBalances.collectAsStateWithLifecycle()
    val activeAccounts by vm.activeAccounts.collectAsStateWithLifecycle()
    val archivedAccounts by vm.archivedAccounts.collectAsStateWithLifecycle()
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
    var senderLabel by remember { mutableStateOf("") }
    var pasteSender by remember { mutableStateOf("") }
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
    var bankPendingArchiveId by remember { mutableStateOf<Long?>(null) }
    var showSenderSheet by remember { mutableStateOf(false) }
    var senderPendingDelete by remember { mutableStateOf<Long?>(null) }
    var smsSenders by remember(state.smsSenders) { mutableStateOf(state.smsSenders) }
    var smsKeywords by remember(state.smsKeywords) { mutableStateOf(state.smsKeywords) }
    var defaultPay by remember(state.defaultPaymentMethod) { mutableStateOf(state.defaultPaymentMethod) }
    var defaultDigital by remember(state.defaultDigitalAccount) {
        mutableStateOf(state.defaultDigitalAccount)
    }
    val activeBanks = remember(activeAccounts) {
        activeAccounts.filter { !it.name.equals("Cash", true) }
    }
    val archivedBanks = remember(archivedAccounts) {
        archivedAccounts.filter { !it.name.equals("Cash", true) }
    }
    fun balanceFor(name: String): Long =
        managedAccountBalances.firstOrNull { it.account.name.equals(name, true) }?.balancePaise
            ?: accountBalances.entries.firstOrNull { it.key.equals(name, true) }?.value
            ?: 0L

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

    val listSections = section == "categories" || section == "banks"
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                title = "Save or restore your data",
                helpTitle = "Backup & restore",
                helpMessage = "Export creates a file with your transactions, categories, funds, and settings. Keep it somewhere safe (like Google Drive). Import puts that data back. The file may include API keys if you saved any.",
            ) {
                Text(
                    "Use Export to make a safety copy. Use Import only when you want to restore an old copy.",
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
                    ) { Text("Save backup file") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large,
                    ) { Text("Restore from backup file") }
                }
            }

            "llm" -> {
                SettingsBlock(
                    title = "Required for bank email & SMS",
                    helpTitle = "AI helper",
                    helpMessage = "Bank emails and SMS need AI to read amounts and merchants. You can still add spends by hand without AI. Keys stay on this phone.",
                ) {
                    SettingsToggleRow(
                        title = "Use AI helper",
                        subtitle = when {
                            state.llmReady -> "On · bank email & SMS import unlocked"
                            state.llmEnabled -> "On — paste an API key below to finish"
                            else -> "Off · turn on to import bank emails & SMS"
                        },
                        checked = state.llmEnabled,
                        onCheckedChange = { vm.setLlmEnabled(it) },
                    )
                    SettingsStatusText(
                        text = when {
                            state.llmReady -> "Ready · ${state.llmModel}"
                            state.llmEnabled -> "Almost done — add your API key"
                            else -> "Not ready — bank auto-import is locked"
                        },
                        positive = state.llmReady,
                    )
                }
                if (state.llmEnabled) {
                    SettingsBlock(
                        title = "API key",
                        helpTitle = "API key",
                        helpMessage = "Pick Groq (often free tier) or OpenAI, paste your key, then Save. Without a key, bank email and SMS import stay off.",
                    ) {
                        Text(
                            "Pick a service, paste your key, then Save.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        SettingsSegmentedRow {
                            SettingsSegment(
                                label = "Groq (easy)",
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
                        OutlinedTextField(
                            llmKey,
                            { llmKey = it },
                            label = { Text(if (state.llmApiKeySet) "API key (saved — type to replace)" else "Paste your API key") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = shapes.medium,
                        )
                        OutlinedTextField(
                            llmModel,
                            { llmModel = it },
                            label = { Text("Model name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = shapes.medium,
                        )
                        OutlinedTextField(
                            llmBase,
                            { llmBase = it },
                            label = { Text("Service address (advanced)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = shapes.medium,
                        )
                        SettingsButtonStack {
                            Button(
                                onClick = { vm.saveLlm(llmBase, llmModel, llmKey.ifBlank { null }) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = shapes.large,
                            ) { Text("Save") }
                            if (state.llmApiKeySet) {
                                OutlinedButton(
                                    onClick = { vm.clearLlmKey() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = shapes.large,
                                ) { Text("Turn off and remove key") }
                            }
                        }
                    }
                }
            }

            "email", "gmail", "senders", "paste" -> {
                SettingsBlock(
                    title = "Email import removed",
                    helpTitle = "Capture methods",
                    helpMessage = "Rupiyah no longer reads bank email. Use SMS, CSV statement import, or manual entry.",
                ) {
                    Text(
                        "Capture is SMS + CSV + manual only.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "• Settings → Bank text messages (SMS) for live bank alerts\n• Accounts → Import bank statement (CSV)\n• + button for manual Debit / Credit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            "sms" -> {
                SettingsBlock(
                    title = "Read bank SMS",
                    helpTitle = "Bank text messages",
                    helpMessage = "Needs AI helper first. When on, bank SMS on this phone can become draft spends. Only messages from the senders and keywords you list below are used.",
                ) {
                    if (!state.llmReady) {
                        Text(
                            "Set up AI helper first (Settings → Smarter reading → AI helper). Bank SMS cannot be read without it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    SettingsToggleRow(
                        title = "Read bank text messages",
                        subtitle = when {
                            !state.llmReady -> "Needs AI helper first"
                            state.smsEnabled -> "On"
                            else -> "Off"
                        },
                        checked = state.smsEnabled && state.llmReady,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (!state.llmReady) {
                                    vm.setSmsEnabled(true) // shows “set up AI” message
                                } else {
                                    smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                                }
                            } else {
                                vm.setSmsEnabled(false)
                            }
                        },
                    )
                }
                SettingsBlock(
                    title = "Which SMS to allow",
                    helpTitle = "SMS filters",
                    helpMessage = "Allowed senders are short IDs from SMS (e.g. HDFCBK, AX-ICICIB). Keywords are words that mean “this is a payment” (e.g. debited, spent, UPI).",
                ) {
                    OutlinedTextField(
                        smsSenders,
                        { smsSenders = it },
                        label = { Text("Allowed senders (comma-separated)") },
                        placeholder = { Text("HDFCBK, AX-ICICIB, VK-PhonePe") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = shapes.medium,
                    )
                    OutlinedTextField(
                        smsKeywords,
                        { smsKeywords = it },
                        label = { Text("Keywords (comma-separated)") },
                        placeholder = { Text("debited, spent, UPI, credited") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = shapes.medium,
                    )
                    Button(
                        onClick = { vm.saveSmsRules(smsSenders, smsKeywords) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large,
                    ) { Text("Save") }
                }
            }

            "location" -> SettingsBlock(
                title = "Remember where you spent",
                helpTitle = "Place tags",
                helpMessage = "Optional. The app may note a rough place when a spend happens so you can recall it later. Location stays on this phone.",
            ) {
                SettingsToggleRow(
                    title = "Use location for place tags",
                    subtitle = if (state.location) "On" else "Off",
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
                    ) { Text("Open phone permission settings") }
                }
            }

            "sheets" -> SettingsBlock(
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

            "google_auth" -> {
                SettingsBlock(
                    title = "Only if Google sign-in fails",
                    helpTitle = "Google sign-in setup",
                    helpMessage = "Most people never need this. If “Connect with Google” fails, a developer may need to paste a Web Client ID from Google Cloud Console.\n\n" +
                        "1. Open console.cloud.google.com\n" +
                        "2. Create or pick a project\n" +
                        "3. Enable Gmail API and Sheets API\n" +
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
                    title = "Gmail connection status",
                    helpTitle = "Gmail",
                    helpMessage = "Read-only access. Only mail from trusted banks is used.",
                ) {
                    SettingsStatusText(
                        text = if (state.gmailOAuthConnected) {
                            "Connected as ${state.gmailOAuthEmail.ifBlank { "Google account" }}"
                        } else {
                            "Not connected"
                        },
                        positive = state.gmailOAuthConnected,
                    )
                    SettingsButtonStack {
                        if (state.gmailOAuthConnected) {
                            OutlinedButton(
                                onClick = { vm.disconnectGmailOAuth() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = shapes.large,
                            ) { Text("Disconnect Gmail") }
                        }
                        Button(
                            onClick = { gmailSignInLauncher.launch(vm.gmailSignInIntent(context)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = shapes.large,
                        ) {
                            Text(if (state.gmailOAuthConnected) "Reconnect Gmail" else "Connect Gmail")
                        }
                    }
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

            "categories" -> SettingsBlock(
                title = "Spending categories",
                helpTitle = "Categories",
                helpMessage = "Tap a row to change name, icon, or color. Tap + to add. Delete only if you no longer use that category.",
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
                    title = "Cash",
                    helpTitle = "Cash",
                    helpMessage = "Physical cash is always available when you add a transaction. It cannot be archived.",
                ) {
                    AccountBalanceRow(
                        name = "Cash",
                        balancePaise = balanceFor("Cash"),
                        isDefaultDigital = false,
                        subtitle = "Always available",
                        onDelete = null,
                    )
                }
                SettingsBlock(
                    title = "Your banks and UPI apps",
                    helpTitle = "Accounts",
                    helpMessage = "Active accounts appear when you add a transaction. Archiving hides them from Add but keeps all past transactions on that account.",
                ) {
                    if (activeBanks.isEmpty()) {
                        Text(
                            "None yet — tap + to add a bank or UPI app",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    activeBanks.forEachIndexed { index, acc ->
                        if (index > 0) {
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                        }
                        AccountBalanceRow(
                            name = acc.name,
                            balancePaise = balanceFor(acc.name),
                            isDefaultDigital = defaultDigital.equals(acc.name, true),
                            onDelete = { bankPendingArchiveId = acc.id },
                        )
                    }
                }
                if (archivedBanks.isNotEmpty()) {
                    SettingsBlock(
                        title = "Archived",
                        helpTitle = "Archived accounts",
                        helpMessage = "These stay linked to old transactions and can be filtered in Activity. They do not appear when adding a new transaction. Restore to use them again.",
                    ) {
                        archivedBanks.forEachIndexed { index, acc ->
                            if (index > 0) {
                                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            }
                            AccountBalanceRow(
                                name = acc.name,
                                balancePaise = balanceFor(acc.name),
                                isDefaultDigital = false,
                                subtitle = "Archived · ${acc.kind.name.lowercase()}",
                                onDelete = null,
                                onRestore = { vm.restoreBankAccount(acc.id) },
                            )
                        }
                    }
                }
                SettingsBlock(
                    title = "Defaults when you add a spend",
                    helpTitle = "Defaults",
                    helpMessage = "Pre-select Cash or a default bank/UPI when the app cannot tell which account.",
                ) {
                    SettingsPanelLabel("Usually pay with")
                    SettingsSegmentedRow {
                        listOf("Cash", "Digital").forEach { method ->
                            SettingsSegment(
                                label = method,
                                selected = defaultPay.equals(method, true) ||
                                    (method == "Digital" && activeBanks.any { it.name.equals(defaultPay, true) }),
                                onClick = {
                                    defaultPay = method
                                    vm.saveDefaultPaymentMethod(method)
                                },
                            )
                        }
                    }
                    SettingsPanelLabel("Default bank / UPI app")
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
                            label = { Text("Let app choose") },
                            shape = shapes.medium,
                        )
                        activeBanks.forEach { acc ->
                            FilterChip(
                                selected = defaultDigital.equals(acc.name, true),
                                onClick = {
                                    defaultDigital = acc.name
                                    vm.saveDefaultDigitalAccount(acc.name)
                                },
                                label = { Text(acc.name) },
                                shape = shapes.medium,
                            )
                        }
                    }
                    if (activeBanks.isEmpty()) {
                        Text(
                            "Add a bank or UPI app above first.",
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
                    SettingsButtonStack {
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
                    AppSecondaryButton(
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
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
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
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        ) { Icon(Icons.Default.Add, contentDescription = "Add bank") }
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
                SettingsButtonStack {
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
                Text("Add bank or UPI app", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Type a short name you recognize — for example HDFC, Axis, PhonePe, or GPay.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    newBankName,
                    { newBankName = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. HDFC, PhonePe, Axis") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = shapes.medium,
                )
                SettingsButtonStack {
                    Button(
                        onClick = {
                            val name = newBankName.trim()
                            if (name.isNotEmpty()) {
                                vm.addBankAccount(name)
                            }
                            newBankName = ""
                            showBankSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.extraLarge,
                        enabled = newBankName.isNotBlank(),
                    ) { Text("Add") }
                    OutlinedButton(
                        onClick = { showBankSheet = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.extraLarge,
                    ) { Text("Cancel") }
                }
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
                Text("Add a trusted bank email", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Only emails from this address (or containing this text) will be turned into spends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    senderEmail,
                    { senderEmail = it },
                    label = { Text("Email address or part of it") },
                    placeholder = { Text("alerts@hdfcbank.net or hdfcbank") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.medium,
                )
                OutlinedTextField(
                    senderLabel,
                    { senderLabel = it },
                    label = { Text("Short name for this bank") },
                    placeholder = { Text("e.g. HDFC, PhonePe") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.medium,
                )
                SettingsButtonStack {
                    Button(
                        onClick = {
                            vm.addSender(senderEmail, senderLabel)
                            senderEmail = ""
                            senderLabel = ""
                            showSenderSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.extraLarge,
                        enabled = senderEmail.isNotBlank(),
                    ) { Text("Add") }
                    OutlinedButton(
                        onClick = { showSenderSheet = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.extraLarge,
                    ) { Text("Cancel") }
                }
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
    bankPendingArchiveId?.let { id ->
        val name = activeBanks.firstOrNull { it.id == id }?.name ?: "This account"
        DeleteConfirmSheet(
            title = "Archive account?",
            message = "“$name” will leave Add Transaction pickers. Past transactions stay on this account. You can restore it anytime under Archived.",
            onDismiss = { bankPendingArchiveId = null },
            onConfirmDelete = {
                vm.archiveBankAccount(id)
                if (defaultPay.equals(name, true)) {
                    defaultPay = "Cash"
                }
                if (defaultDigital.equals(name, true)) {
                    defaultDigital = ""
                }
                bankPendingArchiveId = null
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
    onRestore: (() -> Unit)? = null,
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
                .background(
                    if (onRestore != null) {
                        scheme.surfaceContainerHighest
                    } else {
                        scheme.secondaryContainer
                    },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.AccountBalance,
                null,
                tint = if (onRestore != null) {
                    scheme.onSurfaceVariant
                } else {
                    scheme.onSecondaryContainer
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (onRestore != null) {
                        scheme.onSurfaceVariant
                    } else {
                        scheme.onSurface
                    },
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
        if (onRestore != null) {
            TextButton(onClick = onRestore) {
                Text("Restore")
            }
        } else if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Archive", tint = scheme.error)
            }
        }
    }
}
