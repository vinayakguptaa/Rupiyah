package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.receipt.ReceiptStore
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.ClassificationStatus
import com.krtky.financetracker.domain.model.Money
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionSource
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddCashViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    private val locationRepository: LocationRepository,
    private val userPreferences: UserPreferences,
    private val receiptStore: ReceiptStore,
) : ViewModel() {
    val categories = categoriesState(categoryRepository, transactionRepository)
    val funds = fundsState(transactionRepository)
    val bankAccounts = bankAccountsState(userPreferences, transactionRepository, includeUsageExtras = false)
    val defaultPaymentMethod = userPreferences.defaultPaymentMethod
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Cash")
    val defaultDigitalAccount = userPreferences.defaultDigitalAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val accountBalances = transactionRepository.observeAccountBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val lastUsedCategoryId = userPreferences.lastUsedCategoryId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lastUsedFundId = userPreferences.lastUsedFundId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lastUsedPaymentMethod = userPreferences.lastUsedPaymentMethod
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun recommendFundForCategory(categoryId: Long?): Long? {
        if (categoryId == null) return null
        return transactionRepository.getRecommendedFundForCategory(categoryId)
    }

    suspend fun save(
        amountText: String,
        type: TransactionType,
        categoryId: Long?,
        fundId: Long?,
        note: String,
        counterparty: String = "",
        paymentMethod: String,
        useLocation: Boolean,
        addToFund: Boolean,
        occurredAt: Long = System.currentTimeMillis(),
        receiptLocalUri: Uri? = null,
    ): Boolean {
        val money = Money.fromRupeesString(amountText) ?: return false
        val loc = if (useLocation) locationRepository.captureCurrent() else null
        val party = counterparty.ifBlank { null }
        val id = UUID.randomUUID().toString()
        val receiptPath = receiptLocalUri?.let { receiptStore.persistFromUri(it, id) }
        val txn = Transaction(
            id = id,
            type = type,
            amountPaise = money.paise,
            occurredAt = occurredAt,
            merchant = party,
            counterparty = party,
            categoryId = categoryId,
            fundId = fundId,
            paymentMethod = paymentMethod,
            source = TransactionSource.MANUAL,
            note = note.ifBlank { null },
            isCash = paymentMethod == "Cash",
            classificationStatus = if (categoryId != null) ClassificationStatus.CLASSIFIED else ClassificationStatus.PENDING,
            latitude = loc?.latitude,
            longitude = loc?.longitude,
            placeName = loc?.placeName,
            locationAccuracy = loc?.accuracy,
            locationMatchedAt = if (loc != null) System.currentTimeMillis() else null,
            receiptUri = receiptPath,
        )
        val resolvedFundId = effectiveFundId(type, fundId, addToFund)
        transactionRepository.insertManual(
            txn.copy(fundId = resolvedFundId),
            addToFund = resolvedFundId != null,
        )
        userPreferences.setLastUsedDefaults(
            categoryId = categoryId,
            fundId = resolvedFundId,
            paymentMethod = paymentMethod,
        )
        return true
    }
}
