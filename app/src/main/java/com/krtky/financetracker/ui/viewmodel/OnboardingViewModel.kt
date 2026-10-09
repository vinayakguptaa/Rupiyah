package com.krtky.financetracker.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.prefs.SecureStore
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
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
    private val backupRepository: BackupRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state

    fun setLlmBaseUrl(url: String) { _state.value = _state.value.copy(llmBaseUrl = url) }
    fun setLlmModel(model: String) { _state.value = _state.value.copy(llmModel = model) }

    fun saveLlm(base: String, model: String, key: String?) {
        secureStore.llmBaseUrl = base.ifBlank { SecureStore.DEFAULT_LLM_BASE }
        secureStore.llmModel = model.ifBlank { SecureStore.DEFAULT_LLM_MODEL }
        if (key != null) {
            secureStore.llmApiKey = key
            if (key.isNotBlank()) secureStore.llmEnabled = true
        }
        _state.value = _state.value.copy(llmApiKeySet = !secureStore.llmApiKey.isNullOrBlank())
    }

    fun setSmsSenders(senders: String) { _state.value = _state.value.copy(smsSenders = senders) }
    fun setSmsKeywords(keywords: String) { _state.value = _state.value.copy(smsKeywords = keywords) }

    fun saveSmsRules(senders: String, keywords: String) = viewModelScope.launch {
        userPreferences.setSmsRules(senders, keywords)
    }

    fun setLocationGranted(granted: Boolean) { _state.value = _state.value.copy(locationGranted = granted) }
    fun setNotificationGranted(granted: Boolean) { _state.value = _state.value.copy(notificationGranted = granted) }
    fun setSmsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setSmsEnabled(enabled)
            _state.value = _state.value.copy(smsEnabled = enabled, status = null)
        }
    }

    fun setStatus(msg: String?) { _state.value = _state.value.copy(status = msg) }
    fun setBackupImported() { _state.value = _state.value.copy(backupImported = true) }

    /**
     * Full wipe-and-restore via [BackupRepository] (same path as Settings → Backup).
     * Restores accounts, accountId links, tabs, transactions, splits, and prefs.
     */
    suspend fun importData(context: Context, uri: Uri): Result<String> {
        val result = backupRepository.importData(context, uri)
        if (result.isSuccess) {
            refreshStateFromStores()
        }
        return result
    }

    private suspend fun refreshStateFromStores() {
        _state.value = _state.value.copy(
            llmBaseUrl = secureStore.llmBaseUrl ?: SecureStore.DEFAULT_LLM_BASE,
            llmModel = secureStore.llmModel ?: SecureStore.DEFAULT_LLM_MODEL,
            llmApiKeySet = !secureStore.llmApiKey.isNullOrBlank(),
            smsSenders = userPreferences.smsSenders.first(),
            smsKeywords = userPreferences.smsKeywords.first(),
            smsEnabled = userPreferences.smsEnabled.first(),
        )
    }

    fun completeOnboarding() = viewModelScope.launch {
        userPreferences.setOnboardingCompleted(true)
    }
}
