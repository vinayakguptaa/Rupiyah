package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.util.TransactionSortOrder
import com.krtky.financetracker.ui.util.sortedWithOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Cash / Digital bucket or exact bank/wallet name. */
fun matchesPaymentFilter(txn: Transaction, pay: String): Boolean {
    val isCash = txn.isCash || txn.paymentMethod.equals("Cash", true)
    return when {
        pay.equals("Cash", true) -> isCash
        pay.equals("Digital", true) -> !isCash
        else -> txn.paymentMethod.equals(pay, true)
    }
}

/** Income only hits the pot when [addToFund] is on; expenses always do when fund is set. */
fun effectiveFundId(type: TransactionType, fundId: Long?, addToFund: Boolean): Long? = when {
    fundId == null -> null
    type == TransactionType.EXPENSE -> fundId
    type == TransactionType.INCOME && addToFund -> fundId
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
 * Configured banks sorted by usage.
 * When [includeUsageExtras] is true, also append payment methods seen on transactions
 * that are not Cash/Digital and not already configured.
 */
fun observeBankAccounts(
    userPreferences: UserPreferences,
    transactionRepository: TransactionRepository,
    includeUsageExtras: Boolean,
): Flow<List<String>> = combine(
    userPreferences.bankAccounts,
    transactionRepository.observePaymentMethodUsage(),
) { raw, usage ->
    val configured = userPreferences.parseBankList(raw)
    val sortedConfigured = configured.sortedByDescending { usage[it] ?: 0L }
    if (!includeUsageExtras) return@combine sortedConfigured
    val extras = usage.keys
        .filter { name ->
            !name.equals("Cash", true) &&
                !name.equals("Digital", true) &&
                configured.none { it.equals(name, true) }
        }
        .sortedByDescending { usage[it] ?: 0L }
    (sortedConfigured + extras).distinct()
}

fun ViewModel.categoriesState(
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
): StateFlow<List<Category>> =
    observeCategoriesSortedByUsage(categoryRepository, transactionRepository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

fun ViewModel.bankAccountsState(
    userPreferences: UserPreferences,
    transactionRepository: TransactionRepository,
    includeUsageExtras: Boolean,
): StateFlow<List<String>> =
    observeBankAccounts(userPreferences, transactionRepository, includeUsageExtras)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

fun ViewModel.fundsState(transactionRepository: TransactionRepository) =
    transactionRepository.observeFunds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

suspend fun ViewModel.recommendFundForCategory(
    transactionRepository: TransactionRepository,
    categoryId: Long?,
): Long? {
    if (categoryId == null) return null
    return transactionRepository.getRecommendedFundForCategory(categoryId)
}

