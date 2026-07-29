package com.krtky.financetracker.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.krtky.financetracker.data.email.EmailIngestService
import com.krtky.financetracker.data.email.EmailSource
import com.krtky.financetracker.data.email.GmailApiClient
import com.krtky.financetracker.data.email.ImapEmailClient
import com.krtky.financetracker.data.prefs.SecureStore
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.BackupRepository
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.data.repository.TrustedSenderRepository
import com.krtky.financetracker.data.sheets.SheetsSyncService
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.TrustedSender
import com.krtky.financetracker.email.EmailMonitorService
import com.krtky.financetracker.location.LocationTrackingService
import com.krtky.financetracker.ui.UiMessenger
import com.krtky.financetracker.ui.theme.ColorSchemeStyle
import com.krtky.financetracker.ui.theme.ContrastLevel
import com.krtky.financetracker.ui.theme.DarkModePref
import com.krtky.financetracker.ui.theme.ThemeMode
import com.krtky.financetracker.ui.theme.ThemePreset
import com.krtky.financetracker.ui.theme.TypographyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStore: SecureStore,
    private val userPreferences: UserPreferences,
    private val trustedSenderRepository: TrustedSenderRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val emailIngestService: EmailIngestService,
    private val sheetsSyncService: SheetsSyncService,
    private val imapEmailClient: ImapEmailClient,
    private val gmailApiClient: GmailApiClient,
    private val backupRepository: BackupRepository,
    private val uiMessenger: UiMessenger,
) : ViewModel() {
    private val _state = MutableStateFlow(secureSnapshot())
    val state: StateFlow<SettingsUiState> = _state
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    val senders = trustedSenderRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Net balances keyed by account/payment method label (Cash, HDFC, …). */
    val accountBalances = transactionRepository.observeAccountBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch {
            combine(
                combine(
                    userPreferences.emailPollEnabled,
                    userPreferences.locationEnabled,
                    userPreferences.sheetsSyncEnabled,
                    userPreferences.emailSource,
                ) { e, l, s, source -> arrayOf(e, l, s, source) },
                combine(
                    userPreferences.smsEnabled,
                    userPreferences.smsSenders,
                    userPreferences.smsKeywords,
                ) { sms, senders, keywords -> Triple(sms, senders, keywords) },
                combine(
                    userPreferences.bankAccounts,
                    userPreferences.defaultPaymentMethod,
                    userPreferences.defaultDigitalAccount,
                    userPreferences.devUnlocked,
                    userPreferences.classificationDelayMin,
                ) { banks, defPay, defDigital, dev, delay ->
                    listOf(banks, defPay, defDigital, dev, delay)
                },
            ) { main, sms, extra -> Triple(main, sms, extra) }.collect { (main, sms, extra) ->
                val e = main[0] as Boolean
                val l = main[1] as Boolean
                val s = main[2] as Boolean
                val source = main[3] as EmailSource
                val cur = _state.value
                _state.value = cur.copy(
                    emailPoll = e,
                    location = l,
                    sheetsSync = s,
                    emailSource = source,
                    smsEnabled = sms.first,
                    smsSenders = sms.second,
                    smsKeywords = sms.third,
                    bankAccounts = extra[0] as String,
                    defaultPaymentMethod = extra[1] as String,
                    defaultDigitalAccount = extra[2] as String,
                    devUnlocked = extra[3] as Boolean,
                    classificationDelayMin = extra[4] as Long,
                    displayName = cur.displayName,
                    profileEmail = cur.profileEmail,
                    profilePhone = cur.profilePhone,
                )
            }
        }
        viewModelScope.launch {
            combine(
                userPreferences.themeMode,
                userPreferences.themePreset,
                userPreferences.themeCustomPrimary,
                userPreferences.themeCustomSecondary,
                userPreferences.themeCustomTertiary,
            ) { mode, preset, primary, secondary, tertiary ->
                arrayOf(mode, preset, primary, secondary, tertiary)
            }.collect { values ->
                _state.value = _state.value.copy(
                    themeMode = values[0] as ThemeMode,
                    themePreset = values[1] as ThemePreset,
                    themeCustomPrimary = values[2] as String,
                    themeCustomSecondary = values[3] as String,
                    themeCustomTertiary = values[4] as String,
                )
            }
        }
        viewModelScope.launch {
            userPreferences.themeSchemeStyle.collect { style ->
                _state.value = _state.value.copy(themeSchemeStyle = style)
            }
        }
        viewModelScope.launch {
            userPreferences.darkModePref.collect { pref ->
                _state.value = _state.value.copy(darkModePref = pref)
            }
        }
        viewModelScope.launch {
            userPreferences.contrastLevel.collect { level ->
                _state.value = _state.value.copy(contrastLevel = level)
            }
        }
        viewModelScope.launch {
            userPreferences.typographyMode.collect { mode ->
                _state.value = _state.value.copy(typographyMode = mode)
            }
        }
        viewModelScope.launch {
            userPreferences.oledMode.collect { enabled ->
                _state.value = _state.value.copy(oledMode = enabled)
            }
        }
        viewModelScope.launch {
            combine(
                userPreferences.displayName,
                userPreferences.profileEmail,
                userPreferences.profilePhone,
            ) { n, e, p -> Triple(n, e, p) }.collect { (n, e, p) ->
                _state.value = _state.value.copy(displayName = n, profileEmail = e, profilePhone = p)
            }
        }
    }

    /** Secure-store fields only (synchronous); DataStore prefs arrive via collectors. */
    private fun secureSnapshot(): SettingsUiState = SettingsUiState(
        llmApiKeySet = !secureStore.llmApiKey.isNullOrBlank(),
        llmBaseUrl = secureStore.llmBaseUrl,
        llmModel = secureStore.llmModel,
        gmail = secureStore.gmailAddress.orEmpty(),
        gmailPassSet = !secureStore.gmailAppPassword.isNullOrBlank(),
        gmailOAuthConnected = gmailApiClient.isConfigured(),
        gmailOAuthEmail = gmailApiClient.connectedEmail().orEmpty(),
        sheetId = secureStore.sheetsSpreadsheetId.orEmpty(),
        sheetTokenSet = !secureStore.sheetsAccessToken.isNullOrBlank(),
        googleWebClientId = secureStore.googleWebClientId.orEmpty(),
        llmSystemPrompt = secureStore.llmSystemPrompt,
    )

    private fun refreshSecureFields() {
        val s = secureSnapshot()
        _state.value = _state.value.copy(
            llmApiKeySet = s.llmApiKeySet,
            llmBaseUrl = s.llmBaseUrl,
            llmModel = s.llmModel,
            gmail = s.gmail,
            gmailPassSet = s.gmailPassSet,
            gmailOAuthConnected = s.gmailOAuthConnected,
            gmailOAuthEmail = s.gmailOAuthEmail,
            sheetId = s.sheetId,
            sheetTokenSet = s.sheetTokenSet,
            googleWebClientId = s.googleWebClientId,
            llmSystemPrompt = s.llmSystemPrompt,
        )
    }

    private fun notifySaved(msg: String) {
        uiMessenger.show(msg)
    }

    fun saveProfile(name: String, email: String, phone: String) = viewModelScope.launch {
        userPreferences.setProfile(name, email, phone)
        notifySaved("Profile saved")
    }

    fun setSmsEnabled(enabled: Boolean) = viewModelScope.launch {
        userPreferences.setSmsEnabled(enabled)
    }

    fun saveSmsRules(senders: String, keywords: String) = viewModelScope.launch {
        userPreferences.setSmsRules(senders, keywords)
        notifySaved("SMS rules saved")
    }

    fun setDarkModePref(pref: DarkModePref) = viewModelScope.launch {
        userPreferences.setDarkModePref(pref)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        userPreferences.setThemeMode(mode)
    }

    fun setThemePreset(preset: ThemePreset) = viewModelScope.launch {
        userPreferences.setThemePreset(preset)
    }

    fun setThemeCustomColors(primary: String, secondary: String, tertiary: String) = viewModelScope.launch {
        userPreferences.setThemeCustomColors(primary, secondary, tertiary)
    }

    fun setThemeSchemeStyle(style: ColorSchemeStyle) = viewModelScope.launch {
        userPreferences.setThemeSchemeStyle(style)
    }

    fun setContrastLevel(level: ContrastLevel) = viewModelScope.launch {
        userPreferences.setContrastLevel(level)
    }

    fun setTypographyMode(mode: TypographyMode) = viewModelScope.launch {
        userPreferences.setTypographyMode(mode)
    }

    fun setOledMode(enabled: Boolean) = viewModelScope.launch {
        userPreferences.setOledMode(enabled)
    }

    fun saveLlm(base: String, model: String, key: String?) {
        secureStore.llmBaseUrl = base.ifBlank { SecureStore.DEFAULT_LLM_BASE }
        secureStore.llmModel = model.ifBlank { SecureStore.DEFAULT_LLM_MODEL }
        if (key != null) secureStore.llmApiKey = key
        refreshSecureFields()
        notifySaved("LLM settings saved")
    }

    fun setEmailSource(source: EmailSource) = viewModelScope.launch {
        userPreferences.setEmailSource(source)
        notifySaved(
            when (source) {
                EmailSource.IMAP -> "Using IMAP + App Password"
                EmailSource.GMAIL_OAUTH -> "Using Google Sign-In (Gmail.readonly)"
            },
        )
    }

    fun saveGmail(address: String, password: String?) {
        secureStore.gmailAddress = address.trim().lowercase()
        if (password != null) secureStore.gmailAppPassword = password.replace(" ", "").trim()
        refreshSecureFields()
        notifySaved("Gmail saved — tap Test connection")
    }

    suspend fun testGmail() {
        val source = userPreferences.emailSource.first()
        _status.value = when (source) {
            EmailSource.GMAIL_OAUTH -> "Testing Gmail API…"
            EmailSource.IMAP -> "Testing IMAP…"
        }
        val r = when (source) {
            EmailSource.GMAIL_OAUTH -> gmailApiClient.testConnection()
            EmailSource.IMAP -> imapEmailClient.testConnection()
        }
        _status.value = r.fold(
            onSuccess = { it },
            onFailure = { it.message ?: "Connection failed" },
        )
    }

    fun gmailSignInIntent(context: Context): Intent {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        val webClientId = secureStore.googleWebClientId
        if (!webClientId.isNullOrBlank()) {
            builder.requestIdToken(webClientId)
        }
        builder.requestEmail()
            .requestScopes(Scope(GmailApiClient.GMAIL_READONLY_SCOPE))
        return GoogleSignIn.getClient(context, builder.build()).signInIntent
    }

    suspend fun completeGmailSignIn(context: Context, data: Intent?): Boolean {
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            val email = account.email?.trim().orEmpty()
            if (email.isBlank() || account.account == null) {
                notifySaved("Google did not return an email")
                return false
            }
            val token = withContext(Dispatchers.IO) {
                GoogleAuthUtil.getToken(
                    context,
                    account.account!!,
                    "oauth2:${GmailApiClient.GMAIL_READONLY_SCOPE}",
                )
            }
            gmailApiClient.saveSession(email, token)
            userPreferences.setEmailSource(EmailSource.GMAIL_OAUTH)
            refreshSecureFields()
            notifySaved("Gmail connected as $email")
            true
        } catch (e: Exception) {
            notifySaved(e.message ?: "Gmail sign-in failed")
            false
        }
    }

    fun disconnectGmailOAuth() {
        gmailApiClient.clearSession()
        refreshSecureFields()
        notifySaved("Gmail OAuth disconnected")
    }

    fun saveSheets(id: String, token: String?) {
        secureStore.sheetsSpreadsheetId = id.trim()
        if (token != null) secureStore.sheetsAccessToken = token.trim()
        refreshSecureFields()
        notifySaved("Sheets settings saved")
    }

    fun saveGoogleClientId(clientId: String) {
        secureStore.googleWebClientId = clientId.trim().ifBlank { null }
        refreshSecureFields()
        notifySaved("Google Client ID saved")
    }

    fun googleSignInIntent(context: Context): Intent {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        val webClientId = secureStore.googleWebClientId
        if (!webClientId.isNullOrBlank()) {
            builder.requestIdToken(webClientId)
        }
        builder.requestEmail()
            .requestScopes(Scope(SHEETS_SCOPE))
        return GoogleSignIn.getClient(context, builder.build()).signInIntent
    }

    suspend fun completeGoogleSignIn(context: Context, data: Intent?): Boolean {
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            val token = withContext(Dispatchers.IO) {
                GoogleAuthUtil.getToken(context, account.account!!, "oauth2:$SHEETS_SCOPE")
            }
            secureStore.sheetsAccessToken = token
            refreshSecureFields()
            notifySaved("Google connected as ${account.email.orEmpty()}")
            true
        } catch (e: Exception) {
            notifySaved(e.message ?: "Google sign-in failed")
            false
        }
    }

    suspend fun createSheetsSpreadsheet(title: String): Result<String> {
        _status.value = "Creating spreadsheet…"
        return sheetsSyncService.createSpreadsheet(title).also { result ->
            _status.value = result.fold(
                onSuccess = { id ->
                    secureStore.sheetsSpreadsheetId = id
                    "Spreadsheet created"
                },
                onFailure = { it.message ?: "Create spreadsheet failed" },
            )
            if (result.isSuccess) refreshSecureFields()
        }
    }

    fun setEmailPoll(context: Context, v: Boolean) = viewModelScope.launch {
        userPreferences.setEmailPollEnabled(v)
        if (v) {
            EmailMonitorService.start(context)
            try {
                val pm = context.getSystemService(android.os.PowerManager::class.java)
                val pkg = context.packageName
                if (!pm.isIgnoringBatteryOptimizations(pkg)) {
                    val i = Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$pkg"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(i)
                }
            } catch (_: Exception) {
            }
            notifySaved("Live email monitor started — allow unrestricted battery if asked")
        } else {
            EmailMonitorService.stop(context)
            notifySaved("Email monitor stopped")
        }
    }

    fun setSheets(v: Boolean) = viewModelScope.launch { userPreferences.setSheetsSyncEnabled(v) }

    fun setLocation(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setLocationEnabled(enabled)
            val intent = Intent(context, LocationTrackingService::class.java)
            if (enabled) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }

    fun addSender(email: String, label: String) {
        if (email.isBlank()) return
        viewModelScope.launch {
            trustedSenderRepository.upsert(
                TrustedSender(emailPattern = email.trim(), walletLabel = label.ifBlank { "Wallet" }),
            )
        }
    }

    fun deleteSender(id: Long) = viewModelScope.launch { trustedSenderRepository.delete(id) }

    fun addCategory(name: String, icon: String = "category", color: Long = 0xFF0B6E4FL, quick: Boolean = true) {
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.upsert(
                Category(name = name.trim(), icon = icon, color = color, isQuickAction = quick),
            )
            notifySaved("Category added")
        }
    }

    fun updateCategory(id: Long, name: String, icon: String, color: Long, quick: Boolean) {
        if (id <= 0 || name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.upsert(
                Category(id = id, name = name.trim(), icon = icon, color = color, isQuickAction = quick),
            )
            notifySaved("Category updated")
        }
    }

    fun deleteCategory(id: Long) = viewModelScope.launch {
        categoryRepository.delete(id)
        notifySaved("Category deleted")
    }

    fun saveBankAccounts(raw: String) = viewModelScope.launch {
        userPreferences.setBankAccounts(raw)
        notifySaved("Accounts saved")
    }

    fun saveDefaultPaymentMethod(method: String) = viewModelScope.launch {
        userPreferences.setDefaultPaymentMethod(method)
        notifySaved("Default method saved")
    }

    fun saveDefaultDigitalAccount(account: String) = viewModelScope.launch {
        userPreferences.setDefaultDigitalAccount(account)
        notifySaved("Default digital account saved")
    }

    fun unlockDev() = viewModelScope.launch {
        userPreferences.setDevUnlocked(true)
        notifySaved("Developer options unlocked")
    }

    fun lockDev() = viewModelScope.launch {
        userPreferences.setDevUnlocked(false)
        notifySaved("Developer options hidden")
    }

    fun saveSystemPrompt(prompt: String) {
        secureStore.llmSystemPrompt = prompt.ifBlank { SecureStore.DEFAULT_LLM_SYSTEM }
        _state.value = _state.value.copy(llmSystemPrompt = secureStore.llmSystemPrompt)
        notifySaved("System prompt saved")
    }

    fun resetSystemPrompt() {
        secureStore.llmSystemPrompt = SecureStore.DEFAULT_LLM_SYSTEM
        _state.value = _state.value.copy(llmSystemPrompt = SecureStore.DEFAULT_LLM_SYSTEM)
        notifySaved("System prompt reset")
    }

    fun setClassificationDelay(min: Long) = viewModelScope.launch {
        userPreferences.setClassificationDelayMin(min.coerceIn(0L, 240L))
        notifySaved("Classification delay updated")
    }

    suspend fun pollNow() {
        _status.value = "Polling…"
        val r = emailIngestService.ingest(force = true)
        val msg = r.error ?: "Created ${r.created}, skipped ${r.skipped}"
        _status.value = msg
        if (r.error != null) uiMessenger.show(r.error.take(80))
    }

    suspend fun processPaste(sender: String, subject: String, body: String) {
        _status.value = "Processing…"
        val id = emailIngestService.processPastedEmail(sender, subject, body)
        _status.value = if (id != null) "Transaction created" else "Could not parse / duplicate / not trusted"
    }

    suspend fun syncSheetsNow() {
        _status.value = "Syncing…"
        val r = sheetsSyncService.sync()
        _status.value = r.fold(
            onSuccess = { "Synced $it row(s)" },
            onFailure = {
                val m = it.message ?: "Sync failed"
                uiMessenger.show(m.take(80))
                m
            },
        )
    }

    fun setStatus(msg: String?) {
        _status.value = msg
    }

    suspend fun exportData(context: Context, uri: Uri): Result<Unit> =
        backupRepository.exportData(context, uri)

    suspend fun importData(context: Context, uri: Uri): Result<String> =
        backupRepository.importData(context, uri)

    companion object {
        private const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"
    }
}

