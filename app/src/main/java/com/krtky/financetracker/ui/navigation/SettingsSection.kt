package com.krtky.financetracker.ui.navigation

/**
 * Typed Settings detail sections — prefer these over raw route strings.
 * Route remains `settings/{route}` for NavHost compatibility.
 *
 * [title] is shown in the detail top bar — keep plain language.
 */
enum class SettingsSection(val route: String, val title: String) {
    PROFILE("profile", "Your profile"),
    APPEARANCE("appearance", "Colors & theme"),
    BACKUP("backup", "Backup & restore"),
    LLM("llm", "AI helper"),
    SMS("sms", "Bank text messages"),
    LOCATION("location", "Place tags"),
    SHEETS("sheets", "Google Spreadsheet"),
    GOOGLE_AUTH("google_auth", "Google sign-in setup"),
    CATEGORIES("categories", "Categories"),
    BANKS("banks", "Bank accounts"),
    DEV("dev", "Developer"),
    ;

    companion object {
        fun fromRoute(route: String): SettingsSection? =
            entries.firstOrNull { it.route == route }
    }
}
