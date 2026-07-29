package com.krtky.financetracker.ui.viewmodel

import com.krtky.financetracker.data.email.EmailSource
import com.krtky.financetracker.data.prefs.SecureStore
import com.krtky.financetracker.ui.theme.ColorSchemeStyle
import com.krtky.financetracker.ui.theme.ContrastLevel
import com.krtky.financetracker.ui.theme.DarkModePref
import com.krtky.financetracker.ui.theme.ThemeMode
import com.krtky.financetracker.ui.theme.ThemePreset
import com.krtky.financetracker.ui.theme.TypographyMode

data class SettingsUiState(
    val llmApiKeySet: Boolean = false,
    /** Master switch. Email/SMS auto-import also needs [llmApiKeySet]. */
    val llmEnabled: Boolean = false,
    val llmBaseUrl: String = SecureStore.DEFAULT_LLM_BASE,
    val llmModel: String = SecureStore.DEFAULT_LLM_MODEL,
    val gmail: String = "",
    val gmailPassSet: Boolean = false,
    val gmailOAuthConnected: Boolean = false,
    val gmailOAuthEmail: String = "",
    val emailSource: EmailSource = EmailSource.IMAP,
    val emailPoll: Boolean = false,
    val location: Boolean = false,
    val sheetsSync: Boolean = false,
    val sheetId: String = "",
    val sheetTokenSet: Boolean = false,
    val googleWebClientId: String = "",
    val displayName: String = "",
    val profileEmail: String = "",
    val profilePhone: String = "",
    val themeMode: ThemeMode = ThemeMode.MATERIAL_YOU,
    val themePreset: ThemePreset = ThemePreset.INDIGO,
    val themeCustomPrimary: String = "#3157C9",
    val themeCustomSecondary: String = "#167C83",
    val themeCustomTertiary: String = "#C47A24",
    val themeSchemeStyle: ColorSchemeStyle = ColorSchemeStyle.TONAL_SPOT,
    val smsEnabled: Boolean = false,
    val smsSenders: String = "",
    val smsKeywords: String = "debited,credited,spent,paid,sent,received,transaction,INR,Rs,UPI",
    val bankAccounts: String = "HDFC,ICICI,SBI,Axis",
    val defaultPaymentMethod: String = "Cash",
    val defaultDigitalAccount: String = "",
    val devUnlocked: Boolean = false,
    val llmSystemPrompt: String = "",
    val classificationDelayMin: Long = 15L,
    val darkModePref: DarkModePref = DarkModePref.SYSTEM,
    val contrastLevel: ContrastLevel = ContrastLevel.LOW,
    val typographyMode: TypographyMode = TypographyMode.EXPRESSIVE,
    val oledMode: Boolean = false,
) {
    /** AI on + key saved — required to turn on bank email watch or SMS import. */
    val llmReady: Boolean get() = llmEnabled && llmApiKeySet
}
