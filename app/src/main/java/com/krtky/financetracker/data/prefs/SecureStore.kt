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
        get() = getString(KEY_LLM_MODEL)?.takeUnless { it in RETIRED_MODELS } ?: DEFAULT_LLM_MODEL
        set(v) = putString(KEY_LLM_MODEL, v)

    /**
     * Models tried in order when the main one is rate-limited or down. Each Groq model has its own
     * per-minute quota, so a backup roughly multiplies free-tier throughput. Not set → Groq defaults.
     */
    var llmFallbackModels: List<String>
        get() = getString(KEY_LLM_FALLBACKS)
            ?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }
            ?: if (llmBaseUrl.contains("groq.com", ignoreCase = true)) DEFAULT_GROQ_FALLBACKS else emptyList()
        set(v) = putString(KEY_LLM_FALLBACKS, v.joinToString(",") { it.trim() }.ifBlank { null })

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
        get() = getString(KEY_LLM_SYSTEM)
            ?.takeUnless { it.startsWith(LEGACY_LLM_SYSTEM_PREFIX) }
            ?: DEFAULT_LLM_SYSTEM
        set(v) = putString(KEY_LLM_SYSTEM, v?.takeIf { it.isNotBlank() })

    companion object {
        const val KEY_LLM_API = "llm_api_key"
        const val KEY_LLM_ENABLED = "llm_enabled"
        const val KEY_LLM_BASE = "llm_base_url"
        const val KEY_LLM_MODEL = "llm_model"
        const val KEY_LLM_FALLBACKS = "llm_fallback_models"
        const val KEY_LLM_SYSTEM = "llm_system_prompt"
        const val KEY_SHEETS_ID = "sheets_spreadsheet_id"
        const val KEY_SHEETS_TOKEN = "sheets_access_token"
        const val KEY_GOOGLE_WEB_CLIENT_ID = "google_web_client_id"
        const val DEFAULT_LLM_BASE = "https://api.groq.com/openai/v1"
        /**
         * Picked by an eval on real SMS + the user's own categories (Oct 2026): best category accuracy,
         * every extracted field correct, ~0.35 s per call. Backups have separate Groq rate limits.
         */
        const val DEFAULT_LLM_MODEL = "qwen/qwen3.8-27b"
        val DEFAULT_GROQ_FALLBACKS = listOf("openai/gpt-oss-120b", "openai/gpt-oss-20b")

        /** Models Groq has retired; a saved one falls back to [DEFAULT_LLM_MODEL]. */
        val RETIRED_MODELS = setOf("llama-3.3-70b-versatile", "llama-3.1-70b-versatile", "mixtral-8x7b-32768")
        /** v3: short schema, explicit "none" cases, Indian patterns, worked examples; data comes in the user message. */
        val DEFAULT_LLM_SYSTEM = """
            You read ONE Indian bank, card or UPI SMS (or a pasted note) and decide whether it reports money that has ALREADY moved into or out of the user's own account or card. Reply with one JSON object and nothing else.

            {"type":"DEBIT"|"CREDIT"|"none","amount":number|null,"counterparty":string|null,"bank":string|null,"category":string|null,"occurredAt":string|null,"isSelfTransfer":boolean,"toBank":string|null,"note":string|null,"confidence":number}

            type
            - DEBIT: money left the user's account or card (debited, spent, paid, sent, withdrawn, "trf to").
            - CREDIT: money arrived (credited, received, refund, deposited, "credit by").
            - none: everything else. In particular: OTPs; bills, dues and reminders; anything that WILL happen ("will be debited", autopay/NACH/mandate notices); failed or declined payments; statements; offers; login/KYC/service messages; and confirmations that only repeat money the bank already reported (mutual-fund "units allotted" / "subscription received", a PPF or deposit account "credited" from the user's own savings).
            amount: the moved amount in rupees as a plain number ("1,25,000.00" -> 125000). Never the available balance, reward points or units.
            counterparty: who was paid, or who paid the user, as a short readable name: brand form for merchants ("Swiggy", "Zerodha", "Uber"), full name for people. Drop "Pvt Ltd", "Limited", honorifics and account numbers. If only a UPI id is given, use the UPI id. null when the message names nobody.
            bank: the user's account or card it happened on, copied exactly from ACCOUNTS. Use the sender id and card wording (e.g. "SBIUPI"/"CBSSBI" -> SBI, "KOTAKB" -> Kotak, "BOBCARD One" -> OneCard). null if unsure.
            category: exactly one name from CATEGORIES. When a similar payee appears under HOW THIS USER CATEGORISES, follow that. Use null when unsure, and for person-to-person transfers with no hint.
            occurredAt: ISO-8601 with +05:30 when the message states a date (and time); else null.
            isSelfTransfer / toBank: true only when money moved between two accounts in ACCOUNTS; toBank is the destination.
            note: at most 6 words, only when it adds something ("card bill payment", "salary", "ATM cash").
            confidence: 0 to 1.
            Common Indian patterns (map onto the closest name in CATEGORIES; skip any that has no match there):
            - Credit-card bill payments: CRED / "cred.club", BBPS, "payment received against your card", "bill payment" -> Transfer (moving money to your own card, not a spend or subscription).
            - ACH / NACH / "CEMTEX DEP" credits from a listed company, "FnlDiv", "IntDiv" -> Dividend.
            - Interest credits, FD interest, SGB / RBI bond coupons -> Interest.
            - Broker or fund payments (Zerodha, Groww, "Indian Clearing Corp", BSE / NSE clearing, mutual funds, PPF) -> Investment, in either direction.
            - Food delivery (Swiggy, Zomato, Eternal), restaurants, cafes -> Food; quick-commerce groceries (Blinkit, Zepto, Instamart) -> Groceries unless the user's examples say otherwise.
            - Depository (CDSL / NSDL) messages about units are not money movements.
            Masked digits such as ****ACCT**** are redacted on purpose; ignore them.

            Examples (category names here are illustrative; always pick from CATEGORIES):
            SMS from JD-SBIUPI-S: "Dear UPI user A/C X1234 debited by 250.00 on date 03Oct26 trf to SWIGGY LIMITED Refno 612345678901"
            {"type":"DEBIT","amount":250,"counterparty":"Swiggy","bank":"SBI","category":"Food","occurredAt":"2026-10-03T00:00:00+05:30","isSelfTransfer":false,"toBank":null,"note":null,"confidence":0.95}
            SMS from TX-BOBONE-S: "Rs.4,999.00 will be debited from a/c ending 1234 on 06-10-2026 for your BOBCARD One Credit Card bill"
            {"type":"none","amount":null,"counterparty":null,"bank":null,"category":null,"occurredAt":null,"isSelfTransfer":false,"toBank":null,"note":"upcoming debit, not paid yet","confidence":0.95}
            SMS from AX-SBIPSG-S: "INR 52,000.00 credited to your A/c No XX1234 on 01/10/2026 through NEFT with UTR N0000 by ACME TECHNOLOGIES PVT LTD"
            {"type":"CREDIT","amount":52000,"counterparty":"Acme Technologies","bank":"SBI","category":"Salary","occurredAt":"2026-10-01T00:00:00+05:30","isSelfTransfer":false,"toBank":null,"note":"NEFT","confidence":0.9}
        """.trimIndent()

        /** First line of the pre-v2 default; a stored copy of it is treated as "not customised". */
        const val LEGACY_LLM_SYSTEM_PREFIX = "You extract completed bank/wallet money movements"
    }
}
