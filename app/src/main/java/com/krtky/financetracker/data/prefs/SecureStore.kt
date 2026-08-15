package com.krtky.financetracker.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secret_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { error ->
        // Credentials must never be silently written to unencrypted preferences.
        throw IllegalStateException("Unable to initialize encrypted credential storage", error)
    }

    fun getString(key: String): String? = prefs.getString(key, null)?.takeIf { it.isNotBlank() }

    fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(key) else putString(key, value)
        }.apply()
    }

    var llmApiKey: String?
        get() = getString(KEY_LLM_API)
        set(v) = putString(KEY_LLM_API, v)

    /**
     * Master switch for AI parsing.
     * Bank email / SMS auto-import requires [isLlmReady] (this flag + API key).
     * Default: on only if an API key was already saved (existing installs); otherwise off.
     */
    var llmEnabled: Boolean
        get() = if (prefs.contains(KEY_LLM_ENABLED)) {
            prefs.getBoolean(KEY_LLM_ENABLED, false)
        } else {
            !llmApiKey.isNullOrBlank()
        }
        set(v) {
            prefs.edit().putBoolean(KEY_LLM_ENABLED, v).apply()
        }

    /** True when AI is turned on and an API key is saved — required for email/SMS auto-import. */
    fun isLlmReady(): Boolean = llmEnabled && !llmApiKey.isNullOrBlank()

    var llmBaseUrl: String
        get() = getString(KEY_LLM_BASE) ?: DEFAULT_LLM_BASE
        set(v) = putString(KEY_LLM_BASE, v)

    var llmModel: String
        get() = getString(KEY_LLM_MODEL) ?: DEFAULT_LLM_MODEL
        set(v) = putString(KEY_LLM_MODEL, v)

    var sheetsSpreadsheetId: String?
        get() = getString(KEY_SHEETS_ID)
        set(v) = putString(KEY_SHEETS_ID, v)

    var sheetsAccessToken: String?
        get() = getString(KEY_SHEETS_TOKEN)
        set(v) = putString(KEY_SHEETS_TOKEN, v)

    /** Web client ID from Google Cloud Console (used for Google Sign-In without google-services.json). */
    var googleWebClientId: String?
        get() = getString(KEY_GOOGLE_WEB_CLIENT_ID)
        set(v) = putString(KEY_GOOGLE_WEB_CLIENT_ID, v)

    var llmSystemPrompt: String
        get() = getString(KEY_LLM_SYSTEM) ?: DEFAULT_LLM_SYSTEM
        set(v) = putString(KEY_LLM_SYSTEM, v?.takeIf { it.isNotBlank() })

    companion object {
        const val KEY_LLM_API = "llm_api_key"
        const val KEY_LLM_ENABLED = "llm_enabled"
        const val KEY_LLM_BASE = "llm_base_url"
        const val KEY_LLM_MODEL = "llm_model"
        const val KEY_LLM_SYSTEM = "llm_system_prompt"
        const val KEY_SHEETS_ID = "sheets_spreadsheet_id"
        const val KEY_SHEETS_TOKEN = "sheets_access_token"
        const val KEY_GOOGLE_WEB_CLIENT_ID = "google_web_client_id"
        const val DEFAULT_LLM_BASE = "https://api.groq.com/openai/v1"
        const val DEFAULT_LLM_MODEL = "llama-3.3-70b-versatile"
        val DEFAULT_LLM_SYSTEM = """
            You extract completed bank/wallet money movements from SMS in India.
            Return ONLY valid JSON with keys:
            type (sent|received|INCOME|EXPENSE|none),
            amount (number in INR, no currency symbol),
            currency (default INR),
            occurredAt (ISO-8601 if present),
            merchant (string or null),
            counterparty (who was paid / who paid you — person or merchant name),
            category (MUST be an exact string from the user-provided category list when possible; otherwise null),
            bank (MUST be an exact string from the user-provided digital accounts list when possible; otherwise null),
            paymentMethod (Cash|Digital|UPI|or the exact bank/account label from the provided list),
            referenceId (UPI/UTR/txn id or null),
            note (short useful note or null),
            confidence (0-1).
            Rules:
            - Only completed money movement (debited/credited/paid/sent/received/withdrawn).
            - Ignore bill reminders, dues, outstanding, EMI due, statements without actual debit/credit.
            - Prefer counterparty over generic words like "merchant" or "beneficiary".
            - Category matching: choose the closest label EXACTLY as written in the provided categories list (same spelling/casing). Do not invent new category names.
            - Bank/account matching: choose the closest label EXACTLY as written in the provided digital accounts list. Map aliases (e.g. "HDFC Bank"→"HDFC", "Google Pay"→"GPay") only to labels present in that list. If no account matches, set bank null and paymentMethod "Digital".
            - Infer bank from sender domain, body, UPI handle, or account mask when possible.
            - Cash only when the message clearly says cash.
            - If not a completed money transaction: {"type":"none","amount":null,"confidence":0}.
            - Do not invent amounts.
        """.trimIndent()
    }
}
