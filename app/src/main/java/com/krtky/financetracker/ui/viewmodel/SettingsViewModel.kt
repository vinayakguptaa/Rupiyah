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
import com.krtky.financetracker.data.prefs.SecureStore
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.BackupRepository
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.data.sheets.SheetsSyncService
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.location.LocationTrackingService
import com.krtky.financetracker.ui.UiMessenger
import com.krtky.financetracker.ui.theme.ColorSchemeStyle
import com.krtky.financetracker.ui.theme.ContrastLevel
import com.krtky.financetracker.ui.theme.DarkModePref
import com.krtky.financetracker.ui.theme.ThemeMode
import com.krtky.financetracker.ui.theme.ThemePreset
import com.krtky.financetracker.ui.theme.TypographyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val appContext: Context,
    private val secureStore: SecureStore,
    private val userPreferences: UserPreferences,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val sheetsSyncService: SheetsSyncService,
    private val backupRepository: BackupRepository,
    private val uiMessenger: UiMessenger,
) : ViewModel() {
    private val _state = MutableStateFlow(secureSnapshot())
    val state: StateFlow<SettingsUiState> = _state
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    val categories = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Net balances keyed by account/payment method label (Cash, HDFC, …). */
    val accountBalances = transactionRepository.observeAccountBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    /** Ledger balances including archived (Settings bank list). */
    val managedAccountBalances = accountRepository.observeAllBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeAccounts = accountRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val archivedAccounts = accountRepository.observeArchived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            combine(
                combine(
                    userPreferences.locationEnabled,
                    userPreferences.sheetsSyncEnabled,
                ) { l, s -> Pair(l, s) },
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
                val l = main.first
                val s = main.second
                val cur = _state.value
                _state.value = cur.copy(
                    location = l,
                    sheetsSync = s,
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
        llmEnabled = secureStore.llmEnabled,
        llmBaseUrl = secureStore.llmBaseUrl,
        llmModel = secureStore.llmModel,
        sheetId = secureStore.sheetsSpreadsheetId.orEmpty(),
        sheetTokenSet = !secureStore.sheetsAccessToken.isNullOrBlank(),
        googleWebClientId = secureStore.googleWebClientId.orEmpty(),
        llmSystemPrompt = secureStore.llmSystemPrompt,
    )

    private fun refreshSecureFields() {
        val s = secureSnapshot()
        _state.value = _state.value.copy(
            llmApiKeySet = s.llmApiKeySet,
            llmEnabled = s.llmEnabled,
            llmBaseUrl = s.llmBaseUrl,
            llmModel = s.llmModel,
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
        if (enabled && !secureStore.isLlmReady()) {
            notifySaved("Set up AI helper first — required to read bank SMS")
            return@launch
        }
        userPreferences.setSmsEnabled(enabled)
        if (enabled) notifySaved("Bank SMS reading on")
        else notifySaved("Bank SMS reading off")
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

    fun setLlmEnabled(enabled: Boolean) {
        secureStore.llmEnabled = enabled
        if (!enabled || secureStore.llmApiKey.isNullOrBlank()) {
            // Auto-import cannot run without a ready AI helper.
            viewModelScope.launch { disableAutoImportForMissingAi() }
        }
        refreshSecureFields()
        notifySaved(
            when {
                enabled && !secureStore.llmApiKey.isNullOrBlank() ->
                    "AI helper on — you can use SMS import"
                enabled ->
                    "AI helper on — paste your API key to finish"
                else ->
                    "AI helper off — SMS auto-import turned off"
            },
        )
    }

    fun saveLlm(base: String, model: String, key: String?) {
        secureStore.llmBaseUrl = base.ifBlank { SecureStore.DEFAULT_LLM_BASE }
        secureStore.llmModel = model.ifBlank { SecureStore.DEFAULT_LLM_MODEL }
        if (key != null) {
            secureStore.llmApiKey = key
            // Saving a key implies the user wants AI on.
            if (key.isNotBlank()) secureStore.llmEnabled = true
        }
        if (!secureStore.isLlmReady()) {
            viewModelScope.launch { disableAutoImportForMissingAi() }
        }
        refreshSecureFields()
        notifySaved(
            if (secureStore.isLlmReady()) {
                "AI helper ready — SMS import unlocked"
            } else {
                "AI helper saved — add a key to unlock SMS import"
            },
        )
    }

    fun clearLlmKey() {
        secureStore.llmApiKey = null
        secureStore.llmEnabled = false
        viewModelScope.launch { disableAutoImportForMissingAi() }
        refreshSecureFields()
        notifySaved("AI key removed — SMS auto-import turned off")
    }

    /** Stops SMS import when AI is no longer ready. */
    private suspend fun disableAutoImportForMissingAi() {
        userPreferences.setSmsEnabled(false)
        _state.value = _state.value.copy(smsEnabled = false)
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
        accountRepository.syncFromBankList(userPreferences.parseBankList(raw))
        notifySaved("Accounts saved")
    }

    /** Add bank/UPI name (or restore if archived). Prefs stay mirrored for backup/SMS. */
    fun addBankAccount(name: String) = viewModelScope.launch {
        val id = accountRepository.addOrRestore(name) ?: return@launch
        syncBankPrefsFromAccounts()
        // Clear defaults that pointed at nothing
        notifySaved("“${accountRepository.getById(id)?.name ?: name.trim()}” added")
    }

    /** Archive account — transactions kept; hidden from Add Transaction pickers. */
    fun archiveBankAccount(id: Long) = viewModelScope.launch {
        val acc = accountRepository.getById(id) ?: return@launch
        if (acc.name.equals("Cash", true)) return@launch
        accountRepository.archive(id)
        syncBankPrefsFromAccounts()
        val defDigital = userPreferences.defaultDigitalAccount.first()
        if (defDigital.equals(acc.name, true)) {
            userPreferences.setDefaultDigitalAccount("")
            _state.value = _state.value.copy(defaultDigitalAccount = "")
        }
        val defPay = userPreferences.defaultPaymentMethod.first()
        if (defPay.equals(acc.name, true)) {
            userPreferences.setDefaultPaymentMethod("Cash")
            _state.value = _state.value.copy(defaultPaymentMethod = "Cash")
        }
        notifySaved("“${acc.name}” archived — past transactions kept")
    }

    fun restoreBankAccount(id: Long) = viewModelScope.launch {
        val acc = accountRepository.getById(id) ?: return@launch
        accountRepository.unarchive(id)
        syncBankPrefsFromAccounts()
        notifySaved("“${acc.name}” restored")
    }

    private suspend fun syncBankPrefsFromAccounts() {
        val names = accountRepository.activeBankNames()
        val joined = names.joinToString(",")
        userPreferences.setBankAccounts(joined)
        _state.value = _state.value.copy(bankAccounts = joined)
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

