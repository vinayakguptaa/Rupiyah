package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.receipt.ReceiptStore
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.Account
import com.krtky.financetracker.domain.model.ClassificationStatus
import com.krtky.financetracker.domain.model.Money
import com.krtky.financetracker.domain.model.SplitPart
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
    private val accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val locationRepository: LocationRepository,
    private val userPreferences: UserPreferences,
    private val receiptStore: ReceiptStore,
) : ViewModel() {
    val categories = categoriesState(categoryRepository, transactionRepository)
    val funds = fundsState(transactionRepository)
    /** Active accounts only — archived banks hidden from Add. */
    val accounts = accountRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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

    /**
     * @param splits optional lines saved with the parent (sum must match amount).
     * @return new transaction id, or null on failure.
     */
    suspend fun save(
        amountText: String,
        type: TransactionType,
        categoryId: Long?,
        fundId: Long?,
        note: String,
        counterparty: String = "",
        paymentMethod: String,
        accountId: Long? = null,
        useLocation: Boolean,
        addToFund: Boolean,
        occurredAt: Long = System.currentTimeMillis(),
        receiptLocalUri: Uri? = null,
        splits: List<SplitPart> = emptyList(),
    ): String? {
        val money = Money.fromRupeesString(amountText) ?: return null
        if (splits.isNotEmpty()) {
            val err = com.krtky.financetracker.domain.model.SplitRules.validateSum(
                money.paise,
                splits.map { it.amountPaise },
            )
            if (err != null) return null
        }
        val loc = if (useLocation) locationRepository.captureCurrent() else null
        val party = counterparty.ifBlank { null }
        val id = UUID.randomUUID().toString()
        val receiptPath = receiptLocalUri?.let { receiptStore.persistFromUri(it, id) }
        val resolvedAccountId = accountId
            ?: accountRepository.resolveId(paymentMethod, paymentMethod.equals("Cash", true))
        val account = resolvedAccountId?.let { accountRepository.getById(it) }
        val methodLabel = account?.name ?: paymentMethod
        // When splits exist, parent category/tab are optional summary; lines own the allocation.
        val primaryCat = if (splits.isNotEmpty()) {
            splits.firstOrNull { it.categoryId != null }?.categoryId ?: categoryId
        } else {
            categoryId
        }
        val txn = Transaction(
            id = id,
            type = type,
            amountPaise = money.paise,
            occurredAt = occurredAt,
            merchant = party,
            counterparty = party,
            categoryId = primaryCat,
            fundId = if (splits.isNotEmpty()) null else fundId,
            accountId = resolvedAccountId,
            paymentMethod = methodLabel,
            source = TransactionSource.MANUAL,
            note = note.ifBlank { null },
            isCash = methodLabel.equals("Cash", true) || account?.kind?.name == "CASH",
            classificationStatus = if (primaryCat != null || splits.any { it.categoryId != null }) {
                ClassificationStatus.CLASSIFIED
            } else {
                ClassificationStatus.PENDING
            },
            latitude = loc?.latitude,
            longitude = loc?.longitude,
            placeName = loc?.placeName,
            locationAccuracy = loc?.accuracy,
            locationMatchedAt = if (loc != null) System.currentTimeMillis() else null,
            receiptUri = receiptPath,
        )
        val resolvedFundId = if (splits.isNotEmpty()) {
            null
        } else {
            effectiveFundId(type, fundId, addToFund)
        }
        transactionRepository.insertManualWithSplits(
            txn = txn.copy(fundId = resolvedFundId),
            parts = splits,
            addToFund = resolvedFundId != null,
        )
        userPreferences.setLastUsedDefaults(
            categoryId = primaryCat,
            fundId = resolvedFundId,
            paymentMethod = methodLabel,
        )
        return id
    }

    suspend fun saveSelfTransfer(
        amountText: String,
        fromAccountId: Long,
        toAccountId: Long,
        note: String,
        occurredAt: Long = System.currentTimeMillis(),
    ): Boolean {
        val money = Money.fromRupeesString(amountText) ?: return false
        return transactionRepository.createSelfTransfer(
            amountPaise = money.paise,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            note = note.ifBlank { null },
            occurredAt = occurredAt,
        ) != null
    }
}
