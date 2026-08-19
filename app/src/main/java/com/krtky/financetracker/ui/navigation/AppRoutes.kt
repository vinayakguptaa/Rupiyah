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
@SerialName("tabs")
data object TabsRoute

@Serializable
@SerialName("settings")
data object SettingsRoute

@Serializable
@SerialName("accounts")
data object AccountsRoute

@Serializable
@SerialName("csv_import")
data class CsvImportRoute(val accountId: Long = -1L)

@Serializable
@SerialName("categories")
data object CategoriesRoute

@Serializable
@SerialName("month_flow")
data class MonthFlowRoute(
    /** `DEBIT` (expenses) or `CREDIT` (income). */
    val direction: String,
    /** `category` or `source`. */
    val group: String,
)

@Serializable
@SerialName("add_cash")
data class AddCashRoute(
    val tabId: Long = 0L,
    val amountPaise: Long = 0L,
    /** [com.krtky.financetracker.domain.model.TransactionType] name, or empty for debit. */
    val type: String = "",
    val categoryName: String = "",
    val note: String = "",
)

@Serializable
@SerialName("txn")
data class TxnRoute(val id: String)

@Serializable
@SerialName("txn_split")
data class SplitRoute(val id: String)

@Serializable
@SerialName("tab")
data class TabRoute(val id: Long)

@Serializable
@SerialName("category")
data class CategoryRoute(
    val id: String,
    val name: String,
    val fromMillis: Long = 0L,
    val toMillis: Long = 0L,
    /** `DEBIT`, `CREDIT`, or empty for the screen default (debit). */
    val type: String = "",
)

@Serializable
@SerialName("account")
data class AccountRoute(
    val id: Long,
    val name: String,
    /** `DEBIT`, `CREDIT`, or empty for all types. */
    val type: String = "",
)

const val UNASSIGNED_DIGITAL_ACCOUNT_ID = -1L

@Serializable
@SerialName("settings_section")
data class SettingsSectionRoute(val section: String)

/** Main bottom-nav tab destinations (string ids used by [FloatingBottomNav]). */
object MainTabs {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val TABS = "tabs"
    const val SETTINGS = "settings"

    val all = listOf(HOME, TRANSACTIONS, TABS, SETTINGS)

    fun routeObject(tab: String): Any = when (tab) {
        HOME -> HomeRoute
        TRANSACTIONS -> TransactionsRoute
        TABS -> TabsRoute
        SETTINGS -> SettingsRoute
        else -> HomeRoute
    }
}

/** Map legacy / widget intent strings to typed destinations. */
fun destinationFromNavigateExtra(raw: String): Any? = when (raw) {
    MainTabs.HOME, "home" -> HomeRoute
    MainTabs.TRANSACTIONS, "transactions" -> TransactionsRoute
    MainTabs.TABS, "tabs", "funds" -> TabsRoute
    MainTabs.SETTINGS, "settings" -> SettingsRoute
    "add_cash", "add-cash" -> AddCashRoute()
    "accounts" -> AccountsRoute
    "categories" -> CategoriesRoute
    else -> when {
        raw.startsWith("txn/split/") -> SplitRoute(raw.removePrefix("txn/split/"))
        raw.startsWith("txn/") -> TxnRoute(raw.removePrefix("txn/"))
        raw.startsWith("tab/") -> raw.removePrefix("tab/").toLongOrNull()?.let { TabRoute(it) }
        raw.startsWith("fund/") -> raw.removePrefix("fund/").toLongOrNull()?.let { TabRoute(it) }
        raw.startsWith("settings/") -> SettingsSectionRoute(raw.removePrefix("settings/"))
        else -> null
    }
}
