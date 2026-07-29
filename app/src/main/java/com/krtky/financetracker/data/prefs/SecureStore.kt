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

    var llmBaseUrl: String
        get() = getString(KEY_LLM_BASE) ?: DEFAULT_LLM_BASE
        set(v) = putString(KEY_LLM_BASE, v)

    var llmModel: String
        get() = getString(KEY_LLM_MODEL) ?: DEFAULT_LLM_MODEL
        set(v) = putString(KEY_LLM_MODEL, v)

    var gmailAddress: String?
        get() = getString(KEY_GMAIL)
        set(v) = putString(KEY_GMAIL, v)

    var gmailAppPassword: String?
        get() = getString(KEY_GMAIL_PASS)
        set(v) = putString(KEY_GMAIL_PASS, v)

    /** Google account email used for Gmail OAuth (gmail.readonly). */
    var gmailOAuthEmail: String?
        get() = getString(KEY_GMAIL_OAUTH_EMAIL)
        set(v) = putString(KEY_GMAIL_OAUTH_EMAIL, v)

    /** Cached OAuth access token for Gmail API; refreshed via GoogleAuthUtil on 401. */
    var gmailAccessToken: String?
        get() = getString(KEY_GMAIL_TOKEN)
        set(v) = putString(KEY_GMAIL_TOKEN, v)

    /** Gmail mailbox history cursor for watch / history.list change detection. */
    var gmailHistoryId: String?
        get() = getString(KEY_GMAIL_HISTORY_ID)
        set(v) = putString(KEY_GMAIL_HISTORY_ID, v)

    /** Epoch millis when [users.watch] registration expires (null if never registered). */
    var gmailWatchExpirationMs: Long?
        get() = prefs.getLong(KEY_GMAIL_WATCH_EXP, -1L).takeIf { it > 0L }
        set(v) {
            prefs.edit().apply {
                if (v == null || v <= 0L) remove(KEY_GMAIL_WATCH_EXP)
                else putLong(KEY_GMAIL_WATCH_EXP, v)
            }.apply()
        }

    /**
     * Optional Pub/Sub topic for Gmail [users.watch], e.g.
     * `projects/my-project/topics/rupiyah-gmail`.
     * Required for server push; history.list still works without it.
     */
    var gmailPubSubTopic: String?
        get() = getString(KEY_GMAIL_PUBSUB_TOPIC)
        set(v) = putString(KEY_GMAIL_PUBSUB_TOPIC, v)

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
        const val KEY_LLM_BASE = "llm_base_url"
        const val KEY_LLM_MODEL = "llm_model"
        const val KEY_LLM_SYSTEM = "llm_system_prompt"
        const val KEY_GMAIL = "gmail_address"
        const val KEY_GMAIL_PASS = "gmail_app_password"
        const val KEY_GMAIL_OAUTH_EMAIL = "gmail_oauth_email"
        const val KEY_GMAIL_TOKEN = "gmail_access_token"
        const val KEY_GMAIL_HISTORY_ID = "gmail_history_id"
        const val KEY_GMAIL_WATCH_EXP = "gmail_watch_expiration_ms"
        const val KEY_GMAIL_PUBSUB_TOPIC = "gmail_pubsub_topic"
        const val KEY_SHEETS_ID = "sheets_spreadsheet_id"
        const val KEY_SHEETS_TOKEN = "sheets_access_token"
        const val KEY_GOOGLE_WEB_CLIENT_ID = "google_web_client_id"
        const val DEFAULT_LLM_BASE = "https://api.groq.com/openai/v1"
        const val DEFAULT_LLM_MODEL = "llama-3.3-70b-versatile"
        val DEFAULT_LLM_SYSTEM = """
            You extract completed bank/wallet money movements from emails and SMS in India.
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
