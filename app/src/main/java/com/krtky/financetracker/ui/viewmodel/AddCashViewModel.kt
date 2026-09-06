package com.krtky.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.krtky.financetracker.data.llm.LlmClient
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.data.receipt.ReceiptStore
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.sms.TransactionParser
import com.krtky.financetracker.data.repository.CashflowRepository
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
    private val cashflowRepository: CashflowRepository,
    categoryRepository: CategoryRepository,
    private val locationRepository: LocationRepository,
    private val userPreferences: UserPreferences,
    private val receiptStore: ReceiptStore,
    private val transactionParser: TransactionParser,
    private val llmClient: LlmClient,
) : ViewModel() {
    val categories = categoriesState(categoryRepository, transactionRepository)
    val tabs = tabsState(transactionRepository)
    /** Active accounts, most-used first — archived banks hidden from Add. */
    val accounts = accountsSortedByUsageState(accountRepository, transactionRepository)
    val defaultPaymentMethod = userPreferences.defaultPaymentMethod
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Cash")
    val defaultDigitalAccount = userPreferences.defaultDigitalAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val accountBalances = cashflowRepository.observeAccountBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun isLlmReady(): Boolean = llmClient.isConfigured()

    suspend fun parsePastedText(text: String): Result<PasteParseResult> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Paste some text first"))
        val movement = transactionParser.parsePastedMovement(trimmed)
            ?: return Result.failure(
                IllegalArgumentException(
                    if (llmClient.isConfigured()) {
                        "No completed debit/credit found (bills, dues, and reminders are skipped). Paste one clear bank/UPI confirmation."
                    } else {
                        "Could not read that text. Set up AI helper in Settings for notes that are not bank-style SMS."
                    },
                ),
            )
        return Result.success(
            PasteParseResult(
                transaction = movement.transaction,
                transferFromAccountId = movement.transferFromAccountId,
                transferToAccountId = movement.transferToAccountId,
            ),
        )
    }

    suspend fun recommendTabForCategory(categoryId: Long?): Long? {
        if (categoryId == null) return null
        return transactionRepository.getRecommendedTabForCategory(categoryId)
    }

    /**
     * @param splits optional lines saved with the parent (sum must match amount).
     * @return new transaction id, or null on failure.
     */
    suspend fun save(
        amountText: String,
        type: TransactionType,
        categoryId: Long?,
        tabId: Long?,
        note: String,
        counterparty: String = "",
        paymentMethod: String,
        accountId: Long? = null,
        useLocation: Boolean,
        addToTab: Boolean,
        occurredAt: Long = System.currentTimeMillis(),
        receiptLocalUri: Uri? = null,
        splits: List<SplitPart> = emptyList(),
        source: TransactionSource = TransactionSource.MANUAL,
    ): String? {
        val money = Money.fromRupeesString(amountText) ?: return null
        if (splits.isNotEmpty()) {
            val err = com.krtky.financetracker.domain.model.SplitRules.validateParts(
                type,
                money.paise,
                splits,
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
            counterparty = party,
            categoryId = primaryCat,
            tabId = if (splits.isNotEmpty()) null else tabId,
            accountId = resolvedAccountId,
            source = source,
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
        val resolvedTabId = if (splits.isNotEmpty()) {
            null
        } else {
            effectiveTabId(type, tabId, addToTab)
        }
        transactionRepository.insertManualWithSplits(
            txn = txn.copy(tabId = resolvedTabId),
            parts = splits,
            addToTab = resolvedTabId != null,
        )
        userPreferences.setLastUsedDefaults(
            categoryId = primaryCat,
            tabId = resolvedTabId,
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

data class PasteParseResult(
    val transaction: Transaction,
    val transferFromAccountId: Long? = null,
    val transferToAccountId: Long? = null,
) {
    val isSelfTransfer: Boolean
        get() = transferFromAccountId != null &&
            transferToAccountId != null &&
            transferFromAccountId != transferToAccountId
}
