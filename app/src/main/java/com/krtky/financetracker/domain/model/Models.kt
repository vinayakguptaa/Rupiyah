package com.krtky.financetracker.domain.model

/** Ledger direction — Debit out / Credit in. Forms use these labels (not Expense/Income). */
enum class TransactionType { DEBIT, CREDIT }

enum class TransactionSource { SMS, MANUAL, IMPORT }

enum class ClassificationStatus { PENDING, CLASSIFIED, SKIPPED }

/**
 * NORMAL — ordinary cashflow.
 * SELF_TRANSFER — linked legs between owned accounts (excluded from lifestyle/credit metrics).
 * TAB_TRANSFER — move open balance between tabs (affects tab balances only; excluded from cashflow).
 */
enum class TransactionKind { NORMAL, SELF_TRANSFER, TAB_TRANSFER }

enum class AccountKind { BANK, CARD, CASH, WALLET }

enum class FundEntryType { CREDIT, DEBIT, ADJUSTMENT }

data class Money(val paise: Long) {
    fun toRupees(): Double = paise / 100.0
    fun formatInr(): String {
        val sign = if (paise < 0) "-" else ""
        val abs = kotlin.math.abs(paise)
        val rupees = abs / 100
        val p = abs % 100
        return "%s₹%,d.%02d".format(sign, rupees, p)
    }

    companion object {
        fun fromRupees(value: Double): Money = Money(Math.round(value * 100.0))
        fun fromRupeesString(raw: String): Money? {
            val cleaned = raw.replace(",", "").replace("₹", "").replace("Rs.", "", ignoreCase = true)
                .replace("INR", "", ignoreCase = true).trim()
            val d = cleaned.toDoubleOrNull() ?: return null
            return fromRupees(d)
        }
    }
}

data class Category(
    val id: Long = 0,
    val name: String,
    val icon: String = "category",
    val color: Long = 0xFF0B6E4F,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
    val isQuickAction: Boolean = false,
)

/** Owned ledger (bank / card / cash / wallet). Balance = opening + credits − debits. */
data class Account(
    val id: Long = 0,
    val name: String,
    val kind: AccountKind = AccountKind.BANK,
    val currency: String = "INR",
    val openingBalancePaise: Long = 0L,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

data class AccountBalance(
    val account: Account,
    val balancePaise: Long,
    val txnCount: Long = 0,
)

data class Fund(
    val id: Long = 0,
    val name: String,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Envelope budget / limit in paise (fixed; not inflated by later credits). */
    val budgetPaise: Long = 0L,
)

/**
 * Open Tab balance (was Fund envelope).
 *
 * Spec: positive → they owe you; negative → you owe them.
 *   balance = opening + debits − credits
 * (money you advanced / spent on their behalf vs settlements).
 *
 * [fund.budgetPaise] is optional opening / starting open balance.
 */
data class FundBalance(
    val fund: Fund,
    /** Open balance: + they owe you, − you owe them. */
    val balancePaise: Long,
    /** Credits (settlements / repayments) on this tab. */
    val creditedPaise: Long,
    /** Debits (advances / spends) on this tab. */
    val debitedPaise: Long,
    /** Optional opening balance you set. */
    val openingPaise: Long = 0L,
) {
    /** Magnitude for display bars / default adjust amount (never zero for ratio math). */
    fun limitPaise(): Long = when {
        fund.budgetPaise > 0L -> fund.budgetPaise
        openingPaise > 0L -> openingPaise
        else -> maxOf(kotlin.math.abs(balancePaise), debitedPaise + creditedPaise, 1L)
    }

    fun theyOweYou(): Boolean = balancePaise > 0L
    fun youOweThem(): Boolean = balancePaise < 0L
    fun isSettled(): Boolean = balancePaise == 0L
}

/** Lifestyle / investment home metrics for a period. */
data class CashflowMetrics(
    val lifestyleSpendPaise: Long,
    val creditPaise: Long,
    val investedPaise: Long,
    val redeemedPaise: Long,
    val lifestyleByCategory: List<CategorySpend> = emptyList(),
    val investmentByName: List<NamedAmount> = emptyList(),
) {
    val netInvestedPaise: Long get() = investedPaise - redeemedPaise
}

data class NamedAmount(
    val name: String,
    val debitPaise: Long = 0L,
    val creditPaise: Long = 0L,
) {
    val netPaise: Long get() = debitPaise - creditPaise
}

/** Validation helpers for split editor (pure; unit-testable). */
object SplitRules {
    /** Null if valid; otherwise a short user-facing reason. */
    fun validateSum(parentAmountPaise: Long, splitAmounts: List<Long>): String? {
        if (splitAmounts.isEmpty()) return null
        if (parentAmountPaise <= 0L) return "Parent amount must be greater than zero"
        if (splitAmounts.any { it <= 0L }) return "Each split must be greater than zero"
        val sum = splitAmounts.sum()
        if (sum != parentAmountPaise) {
            return "Splits must sum to parent amount"
        }
        return null
    }

    fun remainingPaise(parentAmountPaise: Long, splitAmounts: List<Long>): Long =
        parentAmountPaise - splitAmounts.sum()
}

/**
 * One line of a split: amount must be > 0; parts must sum to the parent.
 *
 * Splitting replaces the original transaction with standalone child rows
 * that share a [Transaction.splitGroupId]; the parent is soft-deleted.
 */
data class SplitPart(
    val amountPaise: Long,
    val categoryId: Long? = null,
    val counterparty: String? = null,
    val fundId: Long? = null,
    val note: String? = null,
)

data class Transaction(
    val id: String,
    val type: TransactionType,
    val amountPaise: Long,
    val currency: String = "INR",
    val occurredAt: Long,
    val recordedAt: Long = System.currentTimeMillis(),
    /** UI label: party / merchant / person / venue. */
    val counterparty: String? = null,
    val categoryId: Long? = null,
    val fundId: Long? = null,
    val accountId: Long? = null,
    val source: TransactionSource = TransactionSource.MANUAL,
    val note: String? = null,
    val isCash: Boolean = false,
    val classificationStatus: ClassificationStatus = ClassificationStatus.PENDING,
    val isSkipped: Boolean = false,
    val kind: TransactionKind = TransactionKind.NORMAL,
    val transferGroupId: String? = null,
    val rawDescription: String? = null,
    val classificationNotifiedAt: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeName: String? = null,
    val locationAccuracy: Float? = null,
    val locationMatchedAt: Long? = null,
    val smsMessageId: String? = null,
    val externalRefId: String? = null,
    val contentHash: String? = null,
    val sheetsSynced: Boolean = false,
    val deletedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val categoryName: String? = null,
    /** Stored category icon id (e.g. "restaurant"); null if uncategorized. */
    val categoryIcon: String? = null,
    /** ARGB category color; null if uncategorized. */
    val categoryColor: Long? = null,
    val fundName: String? = null,
    val accountName: String? = null,
    /** Relative path under app files (`receipts/…`) or content URI string. */
    val receiptUri: String? = null,
    /** Shared id for parts created by [com.krtky.financetracker.data.repository.TransactionRepository.splitTransaction]; null if not a split child. */
    val splitGroupId: String? = null,
) {
    /** Display name for party / merchant. */
    fun displayName(): String? =
        counterparty?.takeIf { it.isNotBlank() }

    /**
     * True when the parent still needs a category.
     */
    fun needsClassification(): Boolean =
        categoryId == null &&
            !isSkipped &&
            classificationStatus != ClassificationStatus.SKIPPED &&
            classificationStatus != ClassificationStatus.CLASSIFIED &&
            kind != TransactionKind.SELF_TRANSFER &&
            kind != TransactionKind.TAB_TRANSFER

    fun isSelfTransfer(): Boolean = kind == TransactionKind.SELF_TRANSFER

    fun isTabTransfer(): Boolean = kind == TransactionKind.TAB_TRANSFER

    /** True when this row is one leg of a split group (created by splitting). */
    fun isSplitPart(): Boolean = !splitGroupId.isNullOrBlank()
}

data class MonthlySummary(
    val incomePaise: Long,
    val expensePaise: Long,
) {
    val netPaise: Long get() = incomePaise - expensePaise
    /** Alias: credits (money in). */
    val creditPaise: Long get() = incomePaise
    /** Alias: debits (money out), before lifestyle exclusions. */
    val debitPaise: Long get() = expensePaise
}

data class CategorySpend(
    val categoryId: Long?,
    val categoryName: String,
    val totalPaise: Long,
)

data class MonthlyTrend(
    val monthKey: String,
    val incomePaise: Long,
    val expensePaise: Long,
) {
    val netPaise: Long get() = incomePaise - expensePaise
}
