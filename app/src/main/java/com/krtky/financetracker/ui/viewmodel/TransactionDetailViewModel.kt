package com.krtky.financetracker.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.receipt.ReceiptStore
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.CategoryRepository
import com.krtky.financetracker.data.repository.TransactionRepository
import com.krtky.financetracker.domain.model.Account
import com.krtky.financetracker.domain.model.Money
import com.krtky.financetracker.domain.model.SplitPart
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val locationRepository: LocationRepository,
    userPreferences: UserPreferences,
    private val receiptStore: ReceiptStore,
) : ViewModel() {
    private val _txn = MutableStateFlow<Transaction?>(null)
    private val txnIdFlow = MutableStateFlow<String?>(null)
    val transaction: StateFlow<Transaction?> = _txn
    val categories = categoriesState(categoryRepository, transactionRepository)
    val funds = fundsState(transactionRepository)
    /** Active accounts for the account picker (Add-style). */
    val accounts = accountRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val defaultDigitalAccount = userPreferences.defaultDigitalAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val defaultPaymentMethod = userPreferences.defaultPaymentMethod
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Cash")

    /**
     * If the loaded txn sits on an archived account, expose it so the chip still shows
     * (without listing all archived banks).
     */
    private val currentAccountId = MutableStateFlow<Long?>(null)
    val currentAccount: StateFlow<Account?> = combine(currentAccountId, accounts) { id, active ->
        if (id == null) return@combine null
        active.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _archivedCurrent = MutableStateFlow<Account?>(null)
    val archivedCurrentAccount: StateFlow<Account?> = _archivedCurrent

    @OptIn(ExperimentalCoroutinesApi::class)
    val splits: StateFlow<List<SplitPart>> = txnIdFlow
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptyList())
            else transactionRepository.observeSplitGroup(id).map { parts ->
                parts.map { SplitPart(it.amountPaise, it.categoryId, it.counterparty, it.fundId, it.note) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(id: String) {
        txnIdFlow.value = id
        viewModelScope.launch {
            val t = transactionRepository.getById(id)
            _txn.value = t
            val accId = t?.accountId
            currentAccountId.value = accId
            _archivedCurrent.value = accId?.let { accountRepository.getById(it) }?.takeIf { it.archived }
        }
    }

    suspend fun saveSplits(lines: List<SplitPart>): Result<Unit> {
        val id = _txn.value?.id ?: return Result.failure(IllegalStateException("No transaction"))
        val result = transactionRepository.saveSplit(id, lines)
        if (result.isSuccess) {
            _txn.value = transactionRepository.getById(id)
        }
        return result.map { }
    }

    suspend fun clearSplits(): Result<Unit> {
        val id = _txn.value?.id ?: return Result.failure(IllegalStateException("No transaction"))
        val result = transactionRepository.mergeSplitGroup(id)
        if (result.isSuccess) {
            _txn.value = transactionRepository.getById(id)
        }
        return result
    }

    suspend fun save(
        amountText: String,
        type: TransactionType,
        occurredAt: Long,
        accountId: Long?,
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
        val account = accountId?.let { accountRepository.getById(it) }
        val methodLabel = account?.name
            ?: t.accountName
            ?: "Cash"
        val isCash = methodLabel.equals("Cash", true) || account?.kind?.name == "CASH"
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
        val amountPaise = amount.paise
        val updated = t.copy(
            amountPaise = amountPaise,
            type = type,
            occurredAt = occurredAt,
            accountId = account?.id ?: accountId,
            isCash = isCash,
            categoryId = categoryId,
            categoryName = catName,
            fundId = resolvedFundId,
            note = note.ifBlank { null },
            counterparty = counterparty.ifBlank { null },
            latitude = location?.latitude ?: t.latitude,
            longitude = location?.longitude ?: t.longitude,
            placeName = location?.placeName ?: t.placeName,
            locationAccuracy = location?.accuracy ?: t.locationAccuracy,
            locationMatchedAt = if (location != null) System.currentTimeMillis() else t.locationMatchedAt,
            receiptUri = newReceipt,
        )
        transactionRepository.update(updated)
        _txn.value = transactionRepository.getById(t.id) ?: updated
        currentAccountId.value = updated.accountId
        _archivedCurrent.value = updated.accountId?.let { id ->
            accountRepository.getById(id)?.takeIf { it.archived }
        }
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
