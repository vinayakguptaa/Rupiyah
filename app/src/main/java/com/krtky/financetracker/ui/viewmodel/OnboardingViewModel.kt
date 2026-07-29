package com.krtky.financetracker.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.prefs.SecureStore
import com.krtky.financetracker.data.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.long
import kotlinx.serialization.json.int
import kotlinx.serialization.json.double
import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.CategoryEntity
import com.krtky.financetracker.data.local.db.FundEntity
import com.krtky.financetracker.data.local.db.FundLedgerEntity
import com.krtky.financetracker.data.local.db.TransactionEntity
import com.krtky.financetracker.data.local.db.TrustedSenderEntity
import com.krtky.financetracker.ui.theme.ThemeMode
import com.krtky.financetracker.ui.theme.ThemePreset
import javax.inject.Inject

data class OnboardingUiState(
    val gmail: String = "",
    val gmailPassSet: Boolean = false,
    val llmBaseUrl: String = "https://api.groq.com/openai/v1",
    val llmModel: String = "llama-3.3-70b-versatile",
    val llmApiKeySet: Boolean = false,
    val smsSenders: String = "",
    val smsKeywords: String = "debited,credited,spent,paid,sent,received,transaction,INR,Rs,UPI",
    val locationGranted: Boolean = false,
    val notificationGranted: Boolean = false,
    val smsEnabled: Boolean = false,
    val status: String? = null,
    val backupImported: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val secureStore: SecureStore,
    private val userPreferences: UserPreferences,
    private val db: AppDatabase,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state

    fun setGmail(gmail: String) { _state.value = _state.value.copy(gmail = gmail) }

    fun saveGmail(gmail: String, password: String?) {
        secureStore.gmailAddress = gmail.trim().lowercase()
        if (password != null) secureStore.gmailAppPassword = password.replace(" ", "").trim()
        _state.value = _state.value.copy(gmailPassSet = !secureStore.gmailAppPassword.isNullOrBlank())
    }

    fun setLlmBaseUrl(url: String) { _state.value = _state.value.copy(llmBaseUrl = url) }
    fun setLlmModel(model: String) { _state.value = _state.value.copy(llmModel = model) }

    fun saveLlm(base: String, model: String, key: String?) {
        secureStore.llmBaseUrl = base.ifBlank { SecureStore.DEFAULT_LLM_BASE }
        secureStore.llmModel = model.ifBlank { SecureStore.DEFAULT_LLM_MODEL }
        if (key != null) secureStore.llmApiKey = key
        _state.value = _state.value.copy(llmApiKeySet = !secureStore.llmApiKey.isNullOrBlank())
    }

    fun setSmsSenders(senders: String) { _state.value = _state.value.copy(smsSenders = senders) }
    fun setSmsKeywords(keywords: String) { _state.value = _state.value.copy(smsKeywords = keywords) }

    fun saveSmsRules(senders: String, keywords: String) = viewModelScope.launch {
        userPreferences.setSmsRules(senders, keywords)
        _state.value = _state.value.copy(smsEnabled = true)
    }

    fun setLocationGranted(granted: Boolean) { _state.value = _state.value.copy(locationGranted = granted) }
    fun setNotificationGranted(granted: Boolean) { _state.value = _state.value.copy(notificationGranted = granted) }
    fun setSmsEnabled(enabled: Boolean) { _state.value = _state.value.copy(smsEnabled = enabled) }

    fun setStatus(msg: String?) { _state.value = _state.value.copy(status = msg) }
    fun setBackupImported() { _state.value = _state.value.copy(backupImported = true) }

    suspend fun importData(context: Context, uri: Uri): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: return@withContext Result.failure(IllegalStateException("Failed to read file"))
                val jsonObj = Json.parseToJsonElement(content).jsonObject

                // Secure Store
                val secure = jsonObj["secure_store"]?.jsonObject
                if (secure != null) {
                    secure["llm_api_key"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.llmApiKey = it }
                    secure["llm_base_url"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.llmBaseUrl = it }
                    secure["llm_model"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.llmModel = it }
                    secure["gmail_address"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.gmailAddress = it }
                    secure["gmail_app_password"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.gmailAppPassword = it }
                    secure["sheets_spreadsheet_id"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.sheetsSpreadsheetId = it }
                    secure["sheets_access_token"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.sheetsAccessToken = it }
                }

                // User Prefs (banks, profile, SMS, theme, etc.)
                val prefs = jsonObj["user_prefs"]?.jsonObject
                if (prefs != null) {
                    prefs["location_enabled"]?.jsonPrimitive?.boolean?.let { userPreferences.setLocationEnabled(it) }
                    prefs["email_poll_enabled"]?.jsonPrimitive?.boolean?.let { userPreferences.setEmailPollEnabled(it) }
                    prefs["sheets_sync_enabled"]?.jsonPrimitive?.boolean?.let { userPreferences.setSheetsSyncEnabled(it) }
                    prefs["classification_delay_min"]?.jsonPrimitive?.long?.let { userPreferences.setClassificationDelayMin(it) }
                    prefs["theme_mode"]?.jsonPrimitive?.content?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }?.let { userPreferences.setThemeMode(it) }
                    prefs["theme_preset"]?.jsonPrimitive?.content?.let { runCatching { ThemePreset.valueOf(it) }.getOrNull() }?.let { userPreferences.setThemePreset(it) }
                    val customPrimary = prefs["theme_custom_primary"]?.jsonPrimitive?.content
                    val customSecondary = prefs["theme_custom_secondary"]?.jsonPrimitive?.content
                    val customTertiary = prefs["theme_custom_tertiary"]?.jsonPrimitive?.content
                    if (customPrimary != null || customSecondary != null || customTertiary != null) {
                        userPreferences.setThemeCustomColors(
                            customPrimary ?: "#3157C9",
                            customSecondary ?: "#167C83",
                            customTertiary ?: "#C47A24",
                        )
                    }
                    val displayName = prefs["display_name"]?.jsonPrimitive?.content
                    val profileEmail = prefs["profile_email"]?.jsonPrimitive?.content
                    val profilePhone = prefs["profile_phone"]?.jsonPrimitive?.content
                    if (displayName != null || profileEmail != null || profilePhone != null) {
                        userPreferences.setProfile(
                            displayName.orEmpty(),
                            profileEmail.orEmpty(),
                            profilePhone.orEmpty(),
                        )
                    }
                    prefs["bank_accounts"]?.jsonPrimitive?.content?.let { userPreferences.setBankAccounts(it) }
                    prefs["default_payment_method"]?.jsonPrimitive?.content?.let {
                        userPreferences.setDefaultPaymentMethod(it)
                    }
                    prefs["default_digital_account"]?.jsonPrimitive?.content?.let {
                        userPreferences.setDefaultDigitalAccount(it)
                    }
                    prefs["sms_enabled"]?.jsonPrimitive?.boolean?.let { userPreferences.setSmsEnabled(it) }
                    val smsSenders = prefs["sms_senders"]?.jsonPrimitive?.content
                    val smsKeywords = prefs["sms_keywords"]?.jsonPrimitive?.content
                    if (smsSenders != null || smsKeywords != null) {
                        userPreferences.setSmsRules(
                            smsSenders.orEmpty(),
                            smsKeywords ?: "debited,credited,spent,paid,sent,received,transaction,INR,Rs,UPI",
                        )
                    }
                    prefs["onboarding_completed"]?.jsonPrimitive?.boolean?.let {
                        userPreferences.setOnboardingCompleted(it)
                    }
                    prefs["dev_unlocked"]?.jsonPrimitive?.boolean?.let {
                        userPreferences.setDevUnlocked(it)
                    }
                    prefs["last_email_poll_at"]?.jsonPrimitive?.long?.let {
                        userPreferences.setLastEmailPollAt(it)
                    }
                }

                // Database tables
                db.runInTransaction {
                    kotlinx.coroutines.runBlocking {
                        db.openHelper.writableDatabase.execSQL("DELETE FROM transactions")
                        db.openHelper.writableDatabase.execSQL("DELETE FROM categories")
                        db.openHelper.writableDatabase.execSQL("DELETE FROM funds")
                        db.openHelper.writableDatabase.execSQL("DELETE FROM fund_ledger")
                        db.openHelper.writableDatabase.execSQL("DELETE FROM trusted_senders")

                        jsonObj["trusted_senders"]?.jsonArray?.forEach { item ->
                            val obj = item.jsonObject
                            db.trustedSenderDao().upsert(
                                TrustedSenderEntity(
                                    emailPattern = obj["emailPattern"]?.jsonPrimitive?.content.orEmpty(),
                                    walletLabel = obj["walletLabel"]?.jsonPrimitive?.content ?: "Wallet",
                                    enabled = obj["enabled"]?.jsonPrimitive?.boolean ?: true,
                                )
                            )
                        }
                        jsonObj["categories"]?.jsonArray?.forEach { item ->
                            val obj = item.jsonObject
                            db.categoryDao().upsert(
                                CategoryEntity(
                                    id = obj["id"]?.jsonPrimitive?.long ?: 0L,
                                    name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                                    icon = obj["icon"]?.jsonPrimitive?.content ?: "category",
                                    color = obj["color"]?.jsonPrimitive?.long ?: 0xFF0B6E4F,
                                    sortOrder = obj["sortOrder"]?.jsonPrimitive?.int ?: 0,
                                    isSystem = obj["isSystem"]?.jsonPrimitive?.boolean ?: false,
                                    isQuickAction = obj["isQuickAction"]?.jsonPrimitive?.boolean ?: false,
                                )
                            )
                        }
                        jsonObj["funds"]?.jsonArray?.forEach { item ->
                            val obj = item.jsonObject
                            val fundId = db.fundDao().upsert(
                                FundEntity(
                                    id = obj["id"]?.jsonPrimitive?.long ?: 0L,
                                    name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                                    archived = obj["archived"]?.jsonPrimitive?.boolean ?: false,
                                    createdAt = obj["createdAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis(),
                                )
                            )
                            obj["ledger"]?.jsonArray?.forEach { led ->
                                val lObj = led.jsonObject
                                db.fundLedgerDao().insert(
                                    FundLedgerEntity(
                                        fundId = fundId,
                                        entryType = lObj["entryType"]?.jsonPrimitive?.content ?: "ADJUSTMENT",
                                        amountPaise = lObj["amountPaise"]?.jsonPrimitive?.long ?: 0L,
                                        balanceAfterPaise = lObj["balanceAfterPaise"]?.jsonPrimitive?.long ?: 0L,
                                        note = lObj["note"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                        createdAt = lObj["createdAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis(),
                                    )
                                )
                            }
                        }
                        jsonObj["transactions"]?.jsonArray?.forEach { item ->
                            val obj = item.jsonObject
                            val catId = obj["categoryId"]?.jsonPrimitive?.long?.takeIf { it != -1L }
                            val fId = obj["fundId"]?.jsonPrimitive?.long?.takeIf { it != -1L }
                            db.transactionDao().insert(
                                TransactionEntity(
                                    id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                                    type = obj["type"]?.jsonPrimitive?.content ?: "EXPENSE",
                                    amountPaise = obj["amountPaise"]?.jsonPrimitive?.long ?: 0L,
                                    currency = obj["currency"]?.jsonPrimitive?.content ?: "INR",
                                    occurredAt = obj["occurredAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis(),
                                    recordedAt = obj["recordedAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis(),
                                    merchant = obj["merchant"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    counterparty = obj["counterparty"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    categoryId = catId,
                                    fundId = fId,
                                    paymentMethod = obj["paymentMethod"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    source = obj["source"]?.jsonPrimitive?.content ?: "MANUAL",
                                    note = obj["note"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    isCash = obj["isCash"]?.jsonPrimitive?.boolean ?: false,
                                    classificationStatus = obj["classificationStatus"]?.jsonPrimitive?.content ?: "PENDING",
                                    classificationNotifiedAt = obj["classificationNotifiedAt"]?.jsonPrimitive?.long?.takeIf { it != -1L },
                                    latitude = obj["latitude"]?.jsonPrimitive?.double?.takeIf { it != 0.0 },
                                    longitude = obj["longitude"]?.jsonPrimitive?.double?.takeIf { it != 0.0 },
                                    placeName = obj["placeName"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    locationAccuracy = obj["locationAccuracy"]?.jsonPrimitive?.double?.toFloat()?.takeIf { it != 0f },
                                    locationMatchedAt = obj["locationMatchedAt"]?.jsonPrimitive?.long?.takeIf { it != -1L },
                                    emailMessageId = obj["emailMessageId"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    externalRefId = obj["externalRefId"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    contentHash = obj["contentHash"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    sheetsSynced = obj["sheetsSynced"]?.jsonPrimitive?.boolean ?: false,
                                    deletedAt = obj["deletedAt"]?.jsonPrimitive?.long?.takeIf { it != -1L },
                                    updatedAt = obj["updatedAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis(),
                                    version = obj["version"]?.jsonPrimitive?.int ?: 1,
                                )
                            )
                        }
                    }
                }
                Result.success("Imported configuration successfully!")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun completeOnboarding() = viewModelScope.launch {
        userPreferences.setOnboardingCompleted(true)
    }
}
