package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.AccountKind
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.util.TransactionSortOrder
import com.krtky.financetracker.ui.util.sortedWithOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Sentinel for digital rows with no owning account. */
const val PAYMENT_DIGITAL_UNASSIGNED = "Digital-unassigned"

/** Cash / Digital bucket, unassigned digital, or exact bank/wallet / account name. */
fun matchesPaymentFilter(txn: Transaction, pay: String): Boolean {
    val isCash = txn.isCash || txn.accountName.equals("Cash", true)
    val unassignedDigital = !isCash && txn.accountId == null
    return when {
        pay.equals("Cash", true) -> isCash
        pay.equals(PAYMENT_DIGITAL_UNASSIGNED, true) ||
            pay.equals("Digital (no bank)", true) -> unassignedDigital
        pay.equals("Digital", true) -> !isCash
        else -> txn.accountName.equals(pay, true)
    }
}

/** Credits only hit the pot when [addToTab] is on; debits always do when tab is set. */
fun effectiveTabId(type: TransactionType, tabId: Long?, addToTab: Boolean): Long? = when {
    tabId == null -> null
    type == TransactionType.DEBIT -> tabId
    type == TransactionType.CREDIT && addToTab -> tabId
    else -> null
}

/** Normalize Digital/UPI/blank to default digital account or first bank. */
fun normalizePaymentMethod(
    paymentMethod: String,
    defaultDigitalAccount: String,
    bankAccounts: List<String>,
): String = when {
    paymentMethod.equals("Cash", true) -> "Cash"
    paymentMethod.equals("Digital", true) ||
        paymentMethod.equals("UPI", true) ||
        paymentMethod.isBlank() -> {
        val def = defaultDigitalAccount.trim()
        when {
            def.isNotBlank() -> def
            bankAccounts.isNotEmpty() -> bankAccounts.first()
            else -> "Digital"
        }
    }
    else -> paymentMethod
}

fun applyPaymentAndSort(
    list: List<Transaction>,
    payment: String?,
    sort: TransactionSortOrder,
): List<Transaction> {
    val filtered = if (payment == null) list else list.filter { matchesPaymentFilter(it, payment) }
    return filtered.sortedWithOrder(sort)
}

fun observeCategoriesSortedByUsage(
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
): Flow<List<Category>> = combine(
    categoryRepository.observeAll(),
    transactionRepository.observeCategoryUsage(),
) { cats, usage ->
    cats.sortedWith(
        compareByDescending<Category> { usage[it.id] ?: 0L }
            .thenBy { it.sortOrder }
            .thenBy { it.name },
    )
}

/**
 * Account names for Activity filters: active + archived (non-Cash).
 * Archived stay filterable so history on old banks is still findable.
 * Sorted active first, then archived, by usage when available.
 */
fun observeFilterAccountNames(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
): Flow<List<String>> = combine(
    accountRepository.observeAll(),
    transactionRepository.observeAccountUsage(),
) { accounts, usage ->
    accounts
        .filter { it.kind != AccountKind.CASH && !it.name.equals("Cash", true) }
        .sortedWith(
            compareBy<com.krtky.financetracker.domain.model.Account> { it.archived }
                .thenByDescending { usage[it.id] ?: 0L }
                .thenBy { it.sortOrder }
                .thenBy { it.name },
        )
        .map { it.name }
        .distinct()
}

fun ViewModel.categoriesState(
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
): StateFlow<List<Category>> =
    observeCategoriesSortedByUsage(categoryRepository, transactionRepository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

/** Filter chips: active + archived account names (not inventing usage-only labels). */
fun ViewModel.filterAccountNamesState(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
): StateFlow<List<String>> =
    observeFilterAccountNames(accountRepository, transactionRepository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

/** Active non-Cash names only (Add / defaults). */
fun ViewModel.activeBankNamesState(accountRepository: AccountRepository): StateFlow<List<String>> =
    accountRepository.observeActive()
        .map { list ->
            list.filter { it.kind != AccountKind.CASH && !it.name.equals("Cash", true) }
                .map { it.name }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

fun ViewModel.tabsState(transactionRepository: TransactionRepository) =
    transactionRepository.observeTabs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

suspend fun ViewModel.recommendTabForCategory(
    transactionRepository: TransactionRepository,
    categoryId: Long?,
): Long? {
    if (categoryId == null) return null
    return transactionRepository.getRecommendedTabForCategory(categoryId)
}

