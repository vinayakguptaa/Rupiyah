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
     * SMS auto-import and paste/share parse require [isLlmReady] (this flag + API key).
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

    /** True when AI is turned on and an API key is saved — required for SMS auto-import and paste parse. */
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
            You extract completed bank/wallet money movements from SMS or pasted text in India.
            Return ONLY valid JSON matching this schema (null allowed where noted):
            {
              "type": "DEBIT" | "CREDIT" | "none",
              "amount": number (INR, no currency symbol) | null,
              "currency": "INR",
              "occurredAt": ISO-8601 string | null,
              "counterparty": string | null,
              "merchant": string | null,
              "category": string | null,
              "bank": string | null,
              "toBank": string | null,
              "isSelfTransfer": boolean | null,
              "paymentMethod": "Cash" | "Digital" | "UPI" | account label | null,
              "referenceId": string | null,
              "note": string | null,
              "confidence": number 0-1
            }
            Field meaning (must fit Rupiyah):
            - type: DEBIT = money out of the user's account; CREDIT = money in. Prefer these over sent/received/expense/income.
            - counterparty: Name on the Add form (person or merchant paid / who paid you). Prefer over merchant.
            - merchant: fallback Name if counterparty is empty.
            - category: MUST be an exact string from the user-provided ALLOWED CATEGORIES list, or null. Never invent labels.
            - bank: MUST be an exact string from ALLOWED DIGITAL ACCOUNTS (source account), or null.
            - toBank: destination account label from that same list when isSelfTransfer is true; else null.
            - isSelfTransfer: true only when money moved between two of the user's listed accounts (not a spend).
            - paymentMethod: Cash when clearly cash; else the exact bank/account label or Digital.
            - occurredAt: Prefer full local datetime when the message has a date or time.
              Output ISO-8601 with offset when possible, e.g. "2026-09-03T14:32:10+05:30".
              Acceptable alternatives: "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "dd-MM-yyyy HH:mm",
              "dd/MM/yyyy HH:mm", "dd-MMM-yyyy HH:mm". If only a date is present, use that date at 00:00:00 +05:30.
              If no date/time appears in the message, set occurredAt null (do not invent "now").
            Rules:
            - ONLY completed money movement that already happened (debited/credited/paid/sent/received/withdrawn/transferred successfully).
            - You MUST refuse (type "none") for: bill generated/ready, amount due, outstanding, minimum due, EMI due,
              pay-by reminders, autopay reminders, unpaid/overdue notices, statements without a completed debit/credit,
              payment requests / collect requests, and any future or scheduled charge that has not cleared yet.
            - When refusing: {"type":"none","amount":null,"confidence":0,"note":"brief reason"}. Do not fill amount for refusals.
            - If several completed movements appear in one paste, extract ONLY the single clearest/most recent one.
              Do not invent a combined amount. Mention other movements only in note (e.g. "Other txns in paste ignored").
            - isSelfTransfer: mainly for pasted text that names two of the user's accounts (from X to Y / transferred).
              Ordinary merchant UPI SMS names one account — that is a DEBIT, not a self-transfer.
            - Map aliases (e.g. "HDFC Bank"→"HDFC", "Google Pay"→"GPay") only onto labels present in the allowed lists.
            - If no account matches, set bank null and paymentMethod "Digital".
            - Infer bank from sender, body, UPI handle, or account mask when possible.
            - Do not invent amounts or datetimes.
        """.trimIndent()
    }
}
