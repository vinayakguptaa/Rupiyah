package com.krtky.financetracker.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.krtky.financetracker.data.email.EmailSource
import com.krtky.financetracker.ui.theme.ColorSchemeStyle
import com.krtky.financetracker.ui.theme.ContrastLevel
import com.krtky.financetracker.ui.theme.DarkModePref
import com.krtky.financetracker.ui.theme.ThemeMode
import com.krtky.financetracker.ui.theme.ThemePreset
import com.krtky.financetracker.ui.theme.TypographyMode
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("user_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val locationEnabled = booleanPreferencesKey("location_enabled")
        val emailPollEnabled = booleanPreferencesKey("email_poll_enabled")
        /** [EmailSource] name: IMAP (default) or GMAIL_OAUTH. */
        val emailSource = stringPreferencesKey("email_source")
        val sheetsSyncEnabled = booleanPreferencesKey("sheets_sync_enabled")
        val classificationDelayMin = longPreferencesKey("classification_delay_min")
        val lastEmailPollAt = longPreferencesKey("last_email_poll_at")
        val displayName = stringPreferencesKey("display_name")
        val profileEmail = stringPreferencesKey("profile_email")
        val profilePhone = stringPreferencesKey("profile_phone")
        val themeMode = stringPreferencesKey("theme_mode")
        val themePreset = stringPreferencesKey("theme_preset")
        val themeCustomPrimary = stringPreferencesKey("theme_custom_primary")
        val themeCustomSecondary = stringPreferencesKey("theme_custom_secondary")
        val themeCustomTertiary = stringPreferencesKey("theme_custom_tertiary")
        val themeSchemeStyle = stringPreferencesKey("theme_scheme_style")
        val smsEnabled = booleanPreferencesKey("sms_enabled")
        val smsSenders = stringPreferencesKey("sms_senders")
        val smsKeywords = stringPreferencesKey("sms_keywords")
        val bankAccounts = stringPreferencesKey("bank_accounts")
        val defaultPaymentMethod = stringPreferencesKey("default_payment_method")
        /** Bank/wallet used for digital/UPI when AI cannot detect a specific account. */
        val defaultDigitalAccount = stringPreferencesKey("default_digital_account")
        val devUnlocked = booleanPreferencesKey("dev_unlocked")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val hideBalances = booleanPreferencesKey("hide_balances")
        val setupChecklistDismissed = booleanPreferencesKey("setup_checklist_dismissed")
        val lastUsedCategoryId = longPreferencesKey("last_used_category_id")
        val lastUsedFundId = longPreferencesKey("last_used_fund_id")
        val lastUsedPaymentMethod = stringPreferencesKey("last_used_payment_method")
        val darkModePref = stringPreferencesKey("dark_mode_pref")
        val contrastLevel = stringPreferencesKey("contrast_level")
        val typographyMode = stringPreferencesKey("typography_mode")
        val oledMode = booleanPreferencesKey("oled_mode")
        /** Comma-separated [com.krtky.financetracker.ui.navigation.HomeSection] ids. */
        val homeSectionOrder = stringPreferencesKey("home_section_order")
    }

    val locationEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.locationEnabled] ?: false }
    val emailPollEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.emailPollEnabled] ?: false }
    val emailSource: Flow<EmailSource> = context.dataStore.data.map {
        EmailSource.fromStored(it[Keys.emailSource])
    }
    val sheetsSyncEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.sheetsSyncEnabled] ?: false }
    val classificationDelayMin: Flow<Long> = context.dataStore.data.map { it[Keys.classificationDelayMin] ?: 15L }
    val displayName: Flow<String> = context.dataStore.data.map { it[Keys.displayName].orEmpty() }
    val profileEmail: Flow<String> = context.dataStore.data.map { it[Keys.profileEmail].orEmpty() }
    val profilePhone: Flow<String> = context.dataStore.data.map { it[Keys.profilePhone].orEmpty() }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[Keys.themeMode] ?: ThemeMode.MATERIAL_YOU.name) }
            .getOrDefault(ThemeMode.MATERIAL_YOU)
    }
    val themePreset: Flow<ThemePreset> = context.dataStore.data.map {
        runCatching { ThemePreset.valueOf(it[Keys.themePreset] ?: ThemePreset.INDIGO.name) }
            .getOrDefault(ThemePreset.INDIGO)
    }
    val themeCustomPrimary: Flow<String> = context.dataStore.data.map { it[Keys.themeCustomPrimary] ?: "#3157C9" }
    val themeCustomSecondary: Flow<String> = context.dataStore.data.map { it[Keys.themeCustomSecondary] ?: "#167C83" }
    val themeCustomTertiary: Flow<String> = context.dataStore.data.map { it[Keys.themeCustomTertiary] ?: "#C47A24" }
    val themeSchemeStyle: Flow<ColorSchemeStyle> = context.dataStore.data.map {
        runCatching { ColorSchemeStyle.valueOf(it[Keys.themeSchemeStyle] ?: ColorSchemeStyle.TONAL_SPOT.name) }
            .getOrDefault(ColorSchemeStyle.TONAL_SPOT)
    }
    val smsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.smsEnabled] ?: false }
    val smsSenders: Flow<String> = context.dataStore.data.map { it[Keys.smsSenders].orEmpty() }
    val smsKeywords: Flow<String> = context.dataStore.data.map {
        it[Keys.smsKeywords] ?: "debited,credited,spent,paid,sent,received,transaction,INR,Rs,UPI"
    }
    /** Comma-separated active bank names (mirror of accounts table). Empty until user adds. */
    val bankAccounts: Flow<String> = context.dataStore.data.map {
        it[Keys.bankAccounts].orEmpty()
    }
    val defaultPaymentMethod: Flow<String> = context.dataStore.data.map {
        it[Keys.defaultPaymentMethod] ?: "Cash"
    }
    val defaultDigitalAccount: Flow<String> = context.dataStore.data.map {
        it[Keys.defaultDigitalAccount].orEmpty()
    }
    val devUnlocked: Flow<Boolean> = context.dataStore.data.map { it[Keys.devUnlocked] ?: false }
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[Keys.onboardingCompleted] ?: false }
    val hideBalances: Flow<Boolean> = context.dataStore.data.map { it[Keys.hideBalances] ?: false }
    val setupChecklistDismissed: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.setupChecklistDismissed] ?: false }
    val lastUsedCategoryId: Flow<Long?> =
        context.dataStore.data.map { it[Keys.lastUsedCategoryId] }
    val lastUsedFundId: Flow<Long?> =
        context.dataStore.data.map { it[Keys.lastUsedFundId] }
    val lastUsedPaymentMethod: Flow<String?> =
        context.dataStore.data.map { it[Keys.lastUsedPaymentMethod] }
    val darkModePref: Flow<DarkModePref> = context.dataStore.data.map {
        runCatching { DarkModePref.valueOf(it[Keys.darkModePref] ?: DarkModePref.SYSTEM.name) }
            .getOrDefault(DarkModePref.SYSTEM)
    }
    val contrastLevel: Flow<ContrastLevel> = context.dataStore.data.map {
        runCatching { ContrastLevel.valueOf(it[Keys.contrastLevel] ?: ContrastLevel.LOW.name) }
            .getOrDefault(ContrastLevel.LOW)
    }
    val typographyMode: Flow<TypographyMode> = context.dataStore.data.map {
        runCatching { TypographyMode.valueOf(it[Keys.typographyMode] ?: TypographyMode.EXPRESSIVE.name) }
            .getOrDefault(TypographyMode.EXPRESSIVE)
    }
    val oledMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.oledMode] ?: false }
    val homeSectionOrder: Flow<String> = context.dataStore.data.map {
        it[Keys.homeSectionOrder].orEmpty()
    }

    suspend fun setLocationEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.locationEnabled] = v }
    }

    suspend fun setEmailPollEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.emailPollEnabled] = v }
    }

    suspend fun setEmailSource(source: EmailSource) {
        context.dataStore.edit { it[Keys.emailSource] = source.name }
    }

    suspend fun setSheetsSyncEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.sheetsSyncEnabled] = v }
    }

    suspend fun setClassificationDelayMin(v: Long) {
        context.dataStore.edit { it[Keys.classificationDelayMin] = v }
    }

    suspend fun setLastEmailPollAt(v: Long) {
        context.dataStore.edit { it[Keys.lastEmailPollAt] = v }
    }

    suspend fun setProfile(name: String, email: String, phone: String) {
        context.dataStore.edit {
            it[Keys.displayName] = name.trim()
            it[Keys.profileEmail] = email.trim()
            it[Keys.profilePhone] = phone.trim()
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setThemePreset(preset: ThemePreset) {
        context.dataStore.edit { it[Keys.themePreset] = preset.name }
    }

    suspend fun setThemeCustomColors(primary: String, secondary: String, tertiary: String) {
        context.dataStore.edit {
            it[Keys.themeCustomPrimary] = primary.trim()
            it[Keys.themeCustomSecondary] = secondary.trim()
            it[Keys.themeCustomTertiary] = tertiary.trim()
        }
    }

    suspend fun setThemeSchemeStyle(style: ColorSchemeStyle) {
        context.dataStore.edit { it[Keys.themeSchemeStyle] = style.name }
    }

    suspend fun setSmsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.smsEnabled] = enabled }
    }

    suspend fun setSmsRules(senders: String, keywords: String) {
        context.dataStore.edit {
            it[Keys.smsSenders] = senders.trim()
            it[Keys.smsKeywords] = keywords.trim()
        }
    }

    suspend fun setBankAccounts(raw: String) {
        context.dataStore.edit { it[Keys.bankAccounts] = raw.trim() }
    }

    suspend fun setDefaultPaymentMethod(method: String) {
        context.dataStore.edit { it[Keys.defaultPaymentMethod] = method.trim().ifBlank { "Cash" } }
    }

    suspend fun setDefaultDigitalAccount(account: String) {
        context.dataStore.edit { it[Keys.defaultDigitalAccount] = account.trim() }
    }

    suspend fun setDevUnlocked(v: Boolean) {
        context.dataStore.edit { it[Keys.devUnlocked] = v }
    }

    suspend fun setOnboardingCompleted(v: Boolean) {
        context.dataStore.edit { it[Keys.onboardingCompleted] = v }
    }

    suspend fun setHideBalances(v: Boolean) {
        context.dataStore.edit { it[Keys.hideBalances] = v }
    }

    suspend fun setSetupChecklistDismissed(v: Boolean) {
        context.dataStore.edit { it[Keys.setupChecklistDismissed] = v }
    }

    suspend fun setDarkModePref(pref: DarkModePref) {
        context.dataStore.edit { it[Keys.darkModePref] = pref.name }
    }

    suspend fun setContrastLevel(level: ContrastLevel) {
        context.dataStore.edit { it[Keys.contrastLevel] = level.name }
    }

    suspend fun setTypographyMode(mode: TypographyMode) {
        context.dataStore.edit { it[Keys.typographyMode] = mode.name }
    }

    suspend fun setOledMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.oledMode] = enabled }
    }

    suspend fun setHomeSectionOrder(serialized: String) {
        context.dataStore.edit { it[Keys.homeSectionOrder] = serialized }
    }

    suspend fun setLastUsedDefaults(
        categoryId: Long?,
        fundId: Long?,
        paymentMethod: String,
    ) {
        context.dataStore.edit {
            if (categoryId != null) it[Keys.lastUsedCategoryId] = categoryId
            else it.remove(Keys.lastUsedCategoryId)
            if (fundId != null) it[Keys.lastUsedFundId] = fundId
            else it.remove(Keys.lastUsedFundId)
            it[Keys.lastUsedPaymentMethod] = paymentMethod.trim()
        }
    }

    fun parseBankList(raw: String): List<String> =
        raw.split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    /**
     * Resolve the payment method for a digital (non-cash) transaction.
     * Prefer an AI/user-detected bank name; otherwise default digital account;
     * otherwise first configured bank; finally the generic "Digital" label.
     */
    suspend fun resolveDigitalPaymentMethod(detected: String?): String {
        val banks = parseBankList(bankAccounts.first())
        val cleaned = detected?.trim()?.takeIf { it.isNotBlank() }
        if (cleaned != null) {
            if (cleaned.equals("Cash", true)) return "Cash"
            if (cleaned.equals("Digital", true) || cleaned.equals("UPI", true)) {
                // fall through to default digital
            } else {
                banks.firstOrNull { it.equals(cleaned, true) }?.let { return it }
                banks.firstOrNull {
                    cleaned.contains(it, true) || it.contains(cleaned, true)
                }?.let { return it }
                // Unknown wallet/bank string from AI — keep as-is so it still tracks separately
                return cleaned
            }
        }
        val defDigital = defaultDigitalAccount.first().trim()
        if (defDigital.isNotBlank()) {
            if (defDigital.equals("Cash", true)) return "Cash"
            banks.firstOrNull { it.equals(defDigital, true) }?.let { return it }
            return defDigital
        }
        val defPay = defaultPaymentMethod.first().trim()
        if (defPay.isNotBlank() &&
            !defPay.equals("Cash", true) &&
            !defPay.equals("Digital", true) &&
            !defPay.equals("UPI", true)
        ) {
            banks.firstOrNull { it.equals(defPay, true) }?.let { return it }
            return defPay
        }
        return banks.firstOrNull() ?: "Digital"
    }

    suspend fun getLastEmailPollAt(): Long =
        context.dataStore.data.map { it[Keys.lastEmailPollAt] ?: 0L }.first()
}
