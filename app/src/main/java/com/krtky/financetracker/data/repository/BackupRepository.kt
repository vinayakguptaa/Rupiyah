package com.krtky.financetracker.data.repository

import android.content.Context
import android.net.Uri
import com.krtky.financetracker.data.local.db.AccountEntity
import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.CategoryEntity
import com.krtky.financetracker.data.local.db.FundEntity
import com.krtky.financetracker.data.local.db.FundLedgerEntity
import com.krtky.financetracker.data.local.db.TransactionEntity
import com.krtky.financetracker.data.local.db.TransactionSplitEntity
import com.krtky.financetracker.data.local.db.TrustedSenderEntity
import com.krtky.financetracker.data.prefs.SecureStore
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.ui.theme.ColorSchemeStyle
import com.krtky.financetracker.ui.theme.ThemeMode
import com.krtky.financetracker.ui.theme.ThemePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val secureStore: SecureStore,
    private val userPreferences: UserPreferences,
    private val trustedSenderRepository: TrustedSenderRepository,
    private val transactionRepository: TransactionRepository,
    private val db: AppDatabase,
) {
    suspend fun exportData(context: Context, uri: Uri): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val json = buildJsonObject {
                    // v3: accounts + splits first-class; email poll forced off on restore
                    put("version", 3)
                    put("secure_store", buildJsonObject {
                        put("llm_api_key", secureStore.llmApiKey.orEmpty())
                        put("llm_enabled", secureStore.llmEnabled)
                        put("llm_base_url", secureStore.llmBaseUrl)
                        put("llm_model", secureStore.llmModel)
                        // Gmail secrets no longer used — omitted intentionally
                        put("sheets_spreadsheet_id", secureStore.sheetsSpreadsheetId.orEmpty())
                        put("sheets_access_token", secureStore.sheetsAccessToken.orEmpty())
                    })
                    put("user_prefs", buildJsonObject {
                        put("location_enabled", userPreferences.locationEnabled.first())
                        put("email_poll_enabled", false)
                        put("email_source", "IMAP")
                        put("sheets_sync_enabled", userPreferences.sheetsSyncEnabled.first())
                        put("classification_delay_min", userPreferences.classificationDelayMin.first())
                        put("theme_mode", userPreferences.themeMode.first().name)
                        put("theme_preset", userPreferences.themePreset.first().name)
                        put("theme_custom_primary", userPreferences.themeCustomPrimary.first())
                        put("theme_custom_secondary", userPreferences.themeCustomSecondary.first())
                        put("theme_custom_tertiary", userPreferences.themeCustomTertiary.first())
                        put("theme_scheme_style", userPreferences.themeSchemeStyle.first().name)
                        // Profile
                        put("display_name", userPreferences.displayName.first())
                        put("profile_email", userPreferences.profileEmail.first())
                        put("profile_phone", userPreferences.profilePhone.first())
                        // Accounts / payment defaults
                        put("bank_accounts", userPreferences.bankAccounts.first())
                        put("default_payment_method", userPreferences.defaultPaymentMethod.first())
                        put("default_digital_account", userPreferences.defaultDigitalAccount.first())
                        // SMS ingest
                        put("sms_enabled", userPreferences.smsEnabled.first())
                        put("sms_senders", userPreferences.smsSenders.first())
                        put("sms_keywords", userPreferences.smsKeywords.first())
                        // App state
                        put("onboarding_completed", userPreferences.onboardingCompleted.first())
                        put("dev_unlocked", userPreferences.devUnlocked.first())
                        put("last_email_poll_at", userPreferences.getLastEmailPollAt())
                    })
                    val senders = trustedSenderRepository.observeAll().first()
                    put("trusted_senders", buildJsonArray {
                        senders.forEach { s ->
                            add(buildJsonObject {
                                put("emailPattern", s.emailPattern)
                                put("walletLabel", s.walletLabel)
                                put("enabled", s.enabled)
                            })
                        }
                    })
                    val categories = db.categoryDao().getAll()
                    put("categories", buildJsonArray {
                        categories.forEach { c ->
                            add(buildJsonObject {
                                put("id", c.id)
                                put("name", c.name)
                                put("icon", c.icon)
                                put("color", c.color)
                                put("sortOrder", c.sortOrder)
                                put("isSystem", c.isSystem)
                                put("isQuickAction", c.isQuickAction)
                            })
                        }
                    })
                    val accounts = db.accountDao().getAll()
                    put("accounts", buildJsonArray {
                        accounts.forEach { a ->
                            add(buildJsonObject {
                                put("id", a.id)
                                put("name", a.name)
                                put("kind", a.kind)
                                put("currency", a.currency)
                                put("openingBalancePaise", a.openingBalancePaise)
                                put("archived", a.archived)
                                put("sortOrder", a.sortOrder)
                                put("createdAt", a.createdAt)
                            })
                        }
                    })
                    val funds = db.fundDao().getAll()
                    put("funds", buildJsonArray {
                        for (f in funds) {
                            val ledger = db.fundLedgerDao().getForFund(f.id)
                            add(buildJsonObject {
                                put("id", f.id)
                                put("name", f.name)
                                put("archived", f.archived)
                                put("createdAt", f.createdAt)
                                put("budgetPaise", f.budgetPaise)
                                put("ledger", buildJsonArray {
                                    ledger.forEach { l ->
                                        add(buildJsonObject {
                                            put("entryType", l.entryType)
                                            put("amountPaise", l.amountPaise)
                                            put("balanceAfterPaise", l.balanceAfterPaise)
                                            put("note", l.note.orEmpty())
                                            put("createdAt", l.createdAt)
                                        })
                                    }
                                })
                            })
                        }
                    })
                    val transactions = db.transactionDao().observeAll().first()
                    put("transactions", buildJsonArray {
                        transactions.forEach { t ->
                            add(buildJsonObject {
                                put("id", t.id)
                                put("type", t.type)
                                put("amountPaise", t.amountPaise)
                                put("currency", t.currency)
                                put("occurredAt", t.occurredAt)
                                put("recordedAt", t.recordedAt)
                                put("merchant", t.merchant.orEmpty())
                                put("counterparty", t.counterparty.orEmpty())
                                put("categoryId", t.categoryId ?: -1L)
                                put("fundId", t.fundId ?: -1L)
                                put("accountId", t.accountId ?: -1L)
                                put("paymentMethod", t.paymentMethod.orEmpty())
                                put("source", t.source)
                                put("note", t.note.orEmpty())
                                put("isCash", t.isCash)
                                put("classificationStatus", t.classificationStatus)
                                put("isSkipped", t.isSkipped)
                                put("kind", t.kind)
                                put("transferGroupId", t.transferGroupId.orEmpty())
                                put("rawDescription", t.rawDescription.orEmpty())
                                put("classificationNotifiedAt", t.classificationNotifiedAt ?: -1L)
                                put("latitude", t.latitude ?: 0.0)
                                put("longitude", t.longitude ?: 0.0)
                                put("placeName", t.placeName.orEmpty())
                                put("locationAccuracy", (t.locationAccuracy ?: 0f).toDouble())
                                put("locationMatchedAt", t.locationMatchedAt ?: -1L)
                                put("emailMessageId", t.emailMessageId.orEmpty())
                                put("externalRefId", t.externalRefId.orEmpty())
                                put("contentHash", t.contentHash.orEmpty())
                                put("sheetsSynced", t.sheetsSynced)
                                put("deletedAt", t.deletedAt ?: -1L)
                                put("updatedAt", t.updatedAt)
                                put("version", t.version)
                                put("receiptUri", t.receiptUri.orEmpty())
                            })
                        }
                    })
                    val splits = db.transactionSplitDao().getAll()
                    put("transaction_splits", buildJsonArray {
                        splits.forEach { s ->
                            add(buildJsonObject {
                                put("id", s.id)
                                put("transactionId", s.transactionId)
                                put("amountPaise", s.amountPaise)
                                put("categoryId", s.categoryId ?: -1L)
                                put("counterparty", s.counterparty.orEmpty())
                                put("fundId", s.fundId ?: -1L)
                                put("note", s.note.orEmpty())
                                put("sortOrder", s.sortOrder)
                            })
                        }
                    })
                }
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(json.toString().toByteArray())
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun importData(context: Context, uri: Uri): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { isStream ->
                    isStream.readBytes().toString(Charsets.UTF_8)
                } ?: return@withContext Result.failure(IllegalStateException("Failed to read file"))

                val jsonObj = Json.parseToJsonElement(content).jsonObject

                // 1. Secure Store
                val secure = jsonObj["secure_store"]?.jsonObject
                if (secure != null) {
                    secure["llm_api_key"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.llmApiKey = it }
                    secure["llm_enabled"]?.jsonPrimitive?.boolean?.let { secureStore.llmEnabled = it }
                    secure["llm_base_url"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.llmBaseUrl = it }
                    secure["llm_model"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.llmModel = it }
                    // Gmail credentials intentionally not restored (email ingest removed)
                    secure["sheets_spreadsheet_id"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.sheetsSpreadsheetId = it }
                    secure["sheets_access_token"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) secureStore.sheetsAccessToken = it }
                }

                // 2. User Prefs (all settings, including banks / profile / SMS)
                val prefs = jsonObj["user_prefs"]?.jsonObject
                if (prefs != null) {
                    prefs["location_enabled"]?.jsonPrimitive?.boolean?.let { userPreferences.setLocationEnabled(it) }
                    // Always leave email poll off after restore
                    userPreferences.setEmailPollEnabled(false)
                    prefs["sheets_sync_enabled"]?.jsonPrimitive?.boolean?.let { userPreferences.setSheetsSyncEnabled(it) }
                    prefs["classification_delay_min"]?.jsonPrimitive?.long?.let { userPreferences.setClassificationDelayMin(it) }
                    prefs["theme_mode"]?.jsonPrimitive?.content?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }?.let { userPreferences.setThemeMode(it) }
                    prefs["theme_preset"]?.jsonPrimitive?.content?.let { runCatching { ThemePreset.valueOf(it) }.getOrNull() }?.let { userPreferences.setThemePreset(it) }
                    val customPrimary = prefs["theme_custom_primary"]?.jsonPrimitive?.content
                    val customSecondary = prefs["theme_custom_secondary"]?.jsonPrimitive?.content
                    val customTertiary = prefs["theme_custom_tertiary"]?.jsonPrimitive?.content
                    if (customPrimary != null || customSecondary != null || customTertiary != null) {
                        userPreferences.setThemeCustomColors(
                            customPrimary ?: userPreferences.themeCustomPrimary.first(),
                            customSecondary ?: userPreferences.themeCustomSecondary.first(),
                            customTertiary ?: userPreferences.themeCustomTertiary.first(),
                        )
                    }
                    prefs["theme_scheme_style"]?.jsonPrimitive?.content
                        ?.let { runCatching { ColorSchemeStyle.valueOf(it) }.getOrNull() }
                        ?.let { userPreferences.setThemeSchemeStyle(it) }
                    val displayName = prefs["display_name"]?.jsonPrimitive?.content
                    val profileEmail = prefs["profile_email"]?.jsonPrimitive?.content
                    val profilePhone = prefs["profile_phone"]?.jsonPrimitive?.content
                    if (displayName != null || profileEmail != null || profilePhone != null) {
                        userPreferences.setProfile(
                            displayName ?: userPreferences.displayName.first(),
                            profileEmail ?: userPreferences.profileEmail.first(),
                            profilePhone ?: userPreferences.profilePhone.first(),
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
                            smsSenders ?: userPreferences.smsSenders.first(),
                            smsKeywords ?: userPreferences.smsKeywords.first(),
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

                db.runInTransaction {
                    runBlocking {
                        db.openHelper.writableDatabase.execSQL("DELETE FROM transaction_splits")
                        db.openHelper.writableDatabase.execSQL("DELETE FROM transactions")
                        db.openHelper.writableDatabase.execSQL("DELETE FROM categories")
                        db.openHelper.writableDatabase.execSQL("DELETE FROM funds")
                        db.openHelper.writableDatabase.execSQL("DELETE FROM fund_ledger")
                        db.openHelper.writableDatabase.execSQL("DELETE FROM accounts")
                        db.openHelper.writableDatabase.execSQL("DELETE FROM trusted_senders")

                        // 4. Import Senders
                        jsonObj["trusted_senders"]?.jsonArray?.forEach { item ->
                            val obj = item.jsonObject
                            db.trustedSenderDao().upsert(
                                TrustedSenderEntity(
                                    emailPattern = obj["emailPattern"]?.jsonPrimitive?.content.orEmpty(),
                                    walletLabel = obj["walletLabel"]?.jsonPrimitive?.content ?: "Wallet",
                                    enabled = obj["enabled"]?.jsonPrimitive?.boolean ?: true
                                )
                            )
                        }

                        // 5. Import Categories
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
                                    isQuickAction = obj["isQuickAction"]?.jsonPrimitive?.boolean ?: false
                                )
                            )
                        }

                        // 5b. Import Accounts (v2+; optional on older backups)
                        jsonObj["accounts"]?.jsonArray?.forEach { item ->
                            val obj = item.jsonObject
                            db.accountDao().upsert(
                                AccountEntity(
                                    id = obj["id"]?.jsonPrimitive?.long ?: 0L,
                                    name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                                    kind = obj["kind"]?.jsonPrimitive?.content ?: "BANK",
                                    currency = obj["currency"]?.jsonPrimitive?.content ?: "INR",
                                    openingBalancePaise = obj["openingBalancePaise"]?.jsonPrimitive?.long ?: 0L,
                                    archived = obj["archived"]?.jsonPrimitive?.boolean ?: false,
                                    sortOrder = obj["sortOrder"]?.jsonPrimitive?.int ?: 0,
                                    createdAt = obj["createdAt"]?.jsonPrimitive?.long
                                        ?: System.currentTimeMillis(),
                                ),
                            )
                        }

                        // 6. Import Funds & Ledger
                        jsonObj["funds"]?.jsonArray?.forEach { item ->
                            val obj = item.jsonObject
                            val fundId = db.fundDao().upsert(
                                FundEntity(
                                    id = obj["id"]?.jsonPrimitive?.long ?: 0L,
                                    name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                                    archived = obj["archived"]?.jsonPrimitive?.boolean ?: false,
                                    createdAt = obj["createdAt"]?.jsonPrimitive?.long
                                        ?: System.currentTimeMillis(),
                                    budgetPaise = obj["budgetPaise"]?.jsonPrimitive?.long ?: 0L,
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
                                        createdAt = lObj["createdAt"]?.jsonPrimitive?.long
                                            ?: System.currentTimeMillis(),
                                    )
                                )
                            }
                        }

                        // 7. Import Transactions
                        jsonObj["transactions"]?.jsonArray?.forEach { item ->
                            val obj = item.jsonObject
                            val catId = obj["categoryId"]?.jsonPrimitive?.long?.takeIf { it != -1L }
                            val fId = obj["fundId"]?.jsonPrimitive?.long?.takeIf { it != -1L }
                            val accId = obj["accountId"]?.jsonPrimitive?.long?.takeIf { it != -1L }
                            db.transactionDao().insert(
                                TransactionEntity(
                                    id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                                    type = normalizeDirection(obj["type"]?.jsonPrimitive?.content),
                                    amountPaise = obj["amountPaise"]?.jsonPrimitive?.long ?: 0L,
                                    currency = obj["currency"]?.jsonPrimitive?.content ?: "INR",
                                    occurredAt = obj["occurredAt"]?.jsonPrimitive?.long
                                        ?: System.currentTimeMillis(),
                                    recordedAt = obj["recordedAt"]?.jsonPrimitive?.long
                                        ?: System.currentTimeMillis(),
                                    merchant = obj["merchant"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    counterparty = obj["counterparty"]?.jsonPrimitive?.content
                                        ?.takeIf { it.isNotBlank() },
                                    categoryId = catId,
                                    fundId = fId,
                                    accountId = accId,
                                    paymentMethod = obj["paymentMethod"]?.jsonPrimitive?.content
                                        ?.takeIf { it.isNotBlank() },
                                    source = obj["source"]?.jsonPrimitive?.content ?: "MANUAL",
                                    note = obj["note"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    isCash = obj["isCash"]?.jsonPrimitive?.boolean ?: false,
                                    classificationStatus = obj["classificationStatus"]?.jsonPrimitive?.content
                                        ?: "PENDING",
                                    isSkipped = obj["isSkipped"]?.jsonPrimitive?.boolean ?: false,
                                    kind = obj["kind"]?.jsonPrimitive?.content ?: "NORMAL",
                                    transferGroupId = obj["transferGroupId"]?.jsonPrimitive?.content
                                        ?.takeIf { it.isNotBlank() },
                                    rawDescription = obj["rawDescription"]?.jsonPrimitive?.content
                                        ?.takeIf { it.isNotBlank() },
                                    classificationNotifiedAt = obj["classificationNotifiedAt"]
                                        ?.jsonPrimitive?.long?.takeIf { it != -1L },
                                    latitude = obj["latitude"]?.jsonPrimitive?.double?.takeIf { it != 0.0 },
                                    longitude = obj["longitude"]?.jsonPrimitive?.double?.takeIf { it != 0.0 },
                                    placeName = obj["placeName"]?.jsonPrimitive?.content
                                        ?.takeIf { it.isNotBlank() },
                                    locationAccuracy = obj["locationAccuracy"]?.jsonPrimitive?.double
                                        ?.toFloat()?.takeIf { it != 0f },
                                    locationMatchedAt = obj["locationMatchedAt"]?.jsonPrimitive?.long
                                        ?.takeIf { it != -1L },
                                    emailMessageId = obj["emailMessageId"]?.jsonPrimitive?.content
                                        ?.takeIf { it.isNotBlank() },
                                    externalRefId = obj["externalRefId"]?.jsonPrimitive?.content
                                        ?.takeIf { it.isNotBlank() },
                                    contentHash = obj["contentHash"]?.jsonPrimitive?.content
                                        ?.takeIf { it.isNotBlank() },
                                    sheetsSynced = obj["sheetsSynced"]?.jsonPrimitive?.boolean ?: false,
                                    deletedAt = obj["deletedAt"]?.jsonPrimitive?.long?.takeIf { it != -1L },
                                    updatedAt = obj["updatedAt"]?.jsonPrimitive?.long
                                        ?: System.currentTimeMillis(),
                                    version = obj["version"]?.jsonPrimitive?.int ?: 1,
                                    receiptUri = obj["receiptUri"]?.jsonPrimitive?.content
                                        ?.takeIf { it.isNotBlank() },
                                )
                            )
                        }

                        // 8. Import splits (after parents)
                        jsonObj["transaction_splits"]?.jsonArray?.forEach { item ->
                            val obj = item.jsonObject
                            val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                            val txnId = obj["transactionId"]?.jsonPrimitive?.content.orEmpty()
                            if (id.isBlank() || txnId.isBlank()) return@forEach
                            db.transactionSplitDao().upsert(
                                TransactionSplitEntity(
                                    id = id,
                                    transactionId = txnId,
                                    amountPaise = obj["amountPaise"]?.jsonPrimitive?.long ?: 0L,
                                    categoryId = obj["categoryId"]?.jsonPrimitive?.long?.takeIf { it != -1L },
                                    counterparty = obj["counterparty"]?.jsonPrimitive?.content
                                        ?.takeIf { it.isNotBlank() },
                                    fundId = obj["fundId"]?.jsonPrimitive?.long?.takeIf { it != -1L },
                                    note = obj["note"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                                    sortOrder = obj["sortOrder"]?.jsonPrimitive?.int ?: 0,
                                ),
                            )
                        }
                    }
                }
                // Rebuild ledgers so open-tab signs match derived balances after import.
                transactionRepository.repairAllFundLedgers()
                // Align prefs bank list with restored active accounts (and ensure Cash exists).
                val activeBanks = db.accountDao().getAll()
                    .filter { !it.archived && !it.name.equals("Cash", true) }
                    .sortedBy { it.sortOrder }
                    .map { it.name }
                userPreferences.setBankAccounts(activeBanks.joinToString(","))
                // If backup had only bank_accounts string (pre-v2), seed accounts table
                if (jsonObj["accounts"]?.jsonArray.isNullOrEmpty()) {
                    val fromPrefs = userPreferences.parseBankList(userPreferences.bankAccounts.first())
                    // AccountRepository not injected — lightweight Cash ensure via DAO
                    if (db.accountDao().getByName("Cash") == null) {
                        db.accountDao().upsert(
                            AccountEntity(name = "Cash", kind = "CASH", sortOrder = 0),
                        )
                    }
                    fromPrefs.forEachIndexed { i, name ->
                        if (db.accountDao().getByName(name) == null) {
                            db.accountDao().upsert(
                                AccountEntity(name = name, kind = "BANK", sortOrder = i + 1),
                            )
                        }
                    }
                }
                Result.success("Restored accounts, tabs, transactions & splits")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun normalizeDirection(raw: String?): String = when (raw?.uppercase()) {
        "INCOME", "CREDIT" -> "CREDIT"
        else -> "DEBIT"
    }
}
