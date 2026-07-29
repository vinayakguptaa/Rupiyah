package com.krtky.financetracker.ui.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import com.krtky.financetracker.domain.model.TransactionType

/**
 * One-shot Activity (transactions) filters when opening from Home tiles or similar.
 * Stored on the `transactions` destination [SavedStateHandle] so rotation / tab
 * restore does not rely on a pile of boolean ticks in MainActivity.
 *
 * Keys are cleared after the Activity screen consumes them.
 */
object ActivityFilterKeys {
    /** Must match [MainTabs.TRANSACTIONS] / [TransactionsRoute] serial name. */
    const val ROUTE = "transactions"
    const val PAYMENT = "activity_filter_payment"
    const val TYPE = "activity_filter_type"
    const val CATEGORY_ID = "activity_filter_category_id"
    /** When true, apply [CATEGORY_ID] even if null (clear category filter). */
    const val APPLY_CATEGORY = "activity_filter_apply_category"
    /** When true, wipe type / payment / category (user left Activity after a deep-link). */
    const val CLEAR = "activity_filter_clear"
    /** True while a Home deep-link filter is still in effect for this tab session. */
    const val DEEP_LINK_ACTIVE = "activity_filter_deep_link_active"
}

data class ActivityFilterArgs(
    val payment: String? = null,
    val type: TransactionType? = null,
    val categoryId: Long? = null,
    val applyCategory: Boolean = false,
)

fun SavedStateHandle.setActivityFilters(args: ActivityFilterArgs) {
    set(ActivityFilterKeys.CLEAR, false)
    set(ActivityFilterKeys.DEEP_LINK_ACTIVE, true)
    set(ActivityFilterKeys.PAYMENT, args.payment)
    set(ActivityFilterKeys.TYPE, args.type?.name)
    set(ActivityFilterKeys.CATEGORY_ID, args.categoryId)
    set(ActivityFilterKeys.APPLY_CATEGORY, args.applyCategory)
}

fun SavedStateHandle.requestClearActivityFiltersIfDeepLinked() {
    if (get<Boolean>(ActivityFilterKeys.DEEP_LINK_ACTIVE) == true) {
        set(ActivityFilterKeys.CLEAR, true)
    }
}

/**
 * Consume a pending one-shot filter shot, if any.
 * Returns null when nothing to apply (or when a clear is pending — handle clear first).
 */
fun SavedStateHandle.consumeActivityFilters(): ActivityFilterArgs? {
    if (get<Boolean>(ActivityFilterKeys.CLEAR) == true) return null

    val applyCategory = get<Boolean>(ActivityFilterKeys.APPLY_CATEGORY) == true
    val payment = get<String>(ActivityFilterKeys.PAYMENT)
    val typeName = get<String>(ActivityFilterKeys.TYPE)
    val categoryId = get<Long>(ActivityFilterKeys.CATEGORY_ID)
    val type = typeName?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }

    val hasShot = payment != null || type != null || applyCategory
    if (!hasShot) return null

    remove<String>(ActivityFilterKeys.PAYMENT)
    remove<String>(ActivityFilterKeys.TYPE)
    remove<Long>(ActivityFilterKeys.CATEGORY_ID)
    remove<Boolean>(ActivityFilterKeys.APPLY_CATEGORY)

    return ActivityFilterArgs(
        payment = payment,
        type = type,
        categoryId = categoryId,
        applyCategory = applyCategory,
    )
}

/** Returns true if filters should be wiped (and clears the clear + deep-link flags). */
fun SavedStateHandle.consumeClearActivityFilters(): Boolean {
    if (get<Boolean>(ActivityFilterKeys.CLEAR) != true) return false
    remove<Boolean>(ActivityFilterKeys.CLEAR)
    remove<Boolean>(ActivityFilterKeys.DEEP_LINK_ACTIVE)
    remove<String>(ActivityFilterKeys.PAYMENT)
    remove<String>(ActivityFilterKeys.TYPE)
    remove<Long>(ActivityFilterKeys.CATEGORY_ID)
    remove<Boolean>(ActivityFilterKeys.APPLY_CATEGORY)
    return true
}

/** Open Activity tab and push one-shot filters onto its [SavedStateHandle]. */
fun NavHostController.openActivityWithFilters(
    args: ActivityFilterArgs,
    tabNavigate: NavHostController.(String) -> Unit,
) {
    tabNavigate(ActivityFilterKeys.ROUTE)
    runCatching {
        getBackStackEntry(TransactionsRoute).savedStateHandle.setActivityFilters(args)
    }
}

fun NavHostController.clearActivityDeepLinkFiltersIfNeeded() {
    runCatching {
        getBackStackEntry(TransactionsRoute)
            .savedStateHandle
            .requestClearActivityFiltersIfDeepLinked()
    }
}
