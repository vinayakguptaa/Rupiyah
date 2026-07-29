package com.krtky.financetracker.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.receipt.ReceiptStore
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.Money
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    private val locationRepository: LocationRepository,
    userPreferences: UserPreferences,
    private val receiptStore: ReceiptStore,
) : ViewModel() {
    private val _txn = MutableStateFlow<Transaction?>(null)
    val transaction: StateFlow<Transaction?> = _txn
    val categories = categoriesState(categoryRepository, transactionRepository)
    val funds = fundsState(transactionRepository)
    val bankAccounts = bankAccountsState(userPreferences, transactionRepository, includeUsageExtras = false)
    val defaultDigitalAccount = userPreferences.defaultDigitalAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun load(id: String) {
        viewModelScope.launch { _txn.value = transactionRepository.getById(id) }
    }

    suspend fun save(
        amountText: String,
        type: TransactionType,
        occurredAt: Long,
        paymentMethod: String,
        categoryId: Long?,
        fundId: Long?,
        note: String,
        counterparty: String = "",
        useCurrentLocation: Boolean,
        addToFund: Boolean = true,
        receiptLocalUri: Uri? = null,
        clearReceipt: Boolean = false,
    ): Boolean {
        val t = _txn.value ?: return false
        val amount = Money.fromRupeesString(amountText) ?: return false
        val location = if (useCurrentLocation) locationRepository.captureCurrent() else null
        val method = normalizePaymentMethod(
            paymentMethod,
            defaultDigitalAccount.value,
            bankAccounts.value,
        )
        val catName = categories.value.firstOrNull { it.id == categoryId }?.name
        val resolvedFundId = effectiveFundId(type, fundId, addToFund)
        val newReceipt = when {
            clearReceipt -> {
                receiptStore.delete(t.receiptUri)
                null
            }
            receiptLocalUri != null -> {
                receiptStore.delete(t.receiptUri)
                receiptStore.persistFromUri(receiptLocalUri, t.id)
            }
            else -> t.receiptUri
        }
        val updated = t.copy(
            amountPaise = amount.paise,
            type = type,
            occurredAt = occurredAt,
            paymentMethod = method,
            isCash = method.equals("Cash", true),
            categoryId = categoryId,
            categoryName = catName,
            fundId = resolvedFundId,
            note = note.ifBlank { null },
            counterparty = counterparty.ifBlank { null },
            merchant = counterparty.ifBlank { t.merchant },
            latitude = location?.latitude ?: t.latitude,
            longitude = location?.longitude ?: t.longitude,
            placeName = location?.placeName ?: t.placeName,
            locationAccuracy = location?.accuracy ?: t.locationAccuracy,
            locationMatchedAt = if (location != null) System.currentTimeMillis() else t.locationMatchedAt,
            receiptUri = newReceipt,
        )
        transactionRepository.update(updated)
        _txn.value = updated
        return true
    }

    suspend fun delete() {
        _txn.value?.let { t ->
            receiptStore.delete(t.receiptUri)
            transactionRepository.delete(t.id)
        }
    }

    suspend fun recommendFundForCategory(categoryId: Long?): Long? {
        if (categoryId == null) return null
        return transactionRepository.getRecommendedFundForCategory(categoryId)
    }
}
