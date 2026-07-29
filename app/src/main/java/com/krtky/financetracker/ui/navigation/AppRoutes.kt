package com.krtky.financetracker.ui.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation Compose destinations.
 * [SerialName] keeps route strings stable for widgets / intents / SavedStateHandle.
 */
@Serializable
@SerialName("onboarding")
data object OnboardingRoute

@Serializable
@SerialName("home")
data object HomeRoute

@Serializable
@SerialName("transactions")
data object TransactionsRoute

@Serializable
@SerialName("funds")
data object FundsRoute

@Serializable
@SerialName("settings")
data object SettingsRoute

@Serializable
@SerialName("accounts")
data object AccountsRoute

@Serializable
@SerialName("categories")
data object CategoriesRoute

@Serializable
@SerialName("add_cash")
data object AddCashRoute

@Serializable
@SerialName("txn")
data class TxnRoute(val id: String)

@Serializable
@SerialName("fund")
data class FundRoute(val id: Long)

@Serializable
@SerialName("category")
data class CategoryRoute(val id: String, val name: String)

@Serializable
@SerialName("settings_section")
data class SettingsSectionRoute(val section: String)

/** Main bottom-nav tab destinations (string ids used by [FloatingBottomNav]). */
object MainTabs {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val FUNDS = "funds"
    const val SETTINGS = "settings"

    val all = listOf(HOME, TRANSACTIONS, FUNDS, SETTINGS)

    fun routeObject(tab: String): Any = when (tab) {
        HOME -> HomeRoute
        TRANSACTIONS -> TransactionsRoute
        FUNDS -> FundsRoute
        SETTINGS -> SettingsRoute
        else -> HomeRoute
    }
}

/** Map legacy / widget intent strings to typed destinations. */
fun destinationFromNavigateExtra(raw: String): Any? = when (raw) {
    MainTabs.HOME, "home" -> HomeRoute
    MainTabs.TRANSACTIONS, "transactions" -> TransactionsRoute
    MainTabs.FUNDS, "funds" -> FundsRoute
    MainTabs.SETTINGS, "settings" -> SettingsRoute
    "add_cash", "add-cash" -> AddCashRoute
    "accounts" -> AccountsRoute
    "categories" -> CategoriesRoute
    else -> when {
        raw.startsWith("txn/") -> TxnRoute(raw.removePrefix("txn/"))
        raw.startsWith("fund/") -> raw.removePrefix("fund/").toLongOrNull()?.let { FundRoute(it) }
        raw.startsWith("settings/") -> SettingsSectionRoute(raw.removePrefix("settings/"))
        else -> null
    }
}
