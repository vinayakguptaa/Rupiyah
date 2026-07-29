package com.krtky.financetracker.ui.navigation

/**
 * Typed Settings detail sections — prefer these over raw route strings.
 * Route remains `settings/{route}` for NavHost compatibility.
 */
enum class SettingsSection(val route: String, val title: String) {
    PROFILE("profile", "Profile"),
    APPEARANCE("appearance", "Appearance"),
    BACKUP("backup", "Backup & restore"),
    LLM("llm", "LLM Providers"),
    GMAIL("gmail", "Email IMAP"),
    EMAIL("email", "Email settings"),
    SENDERS("senders", "Trusted senders"),
    PASTE("paste", "Paste email"),
    SMS("sms", "SMS transactions"),
    LOCATION("location", "Location"),
    SHEETS("sheets", "Google Sheets"),
    GOOGLE_AUTH("google_auth", "Google Auth"),
    CATEGORIES("categories", "Categories"),
    BANKS("banks", "Accounts"),
    DEV("dev", "Developer"),
    ;

    companion object {
        fun fromRoute(route: String): SettingsSection? =
            entries.firstOrNull { it.route == route }
    }
}
