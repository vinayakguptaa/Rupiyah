package com.krtky.financetracker.domain.model

/** Ledger direction — Debit out / Credit in. Forms use these labels (not Expense/Income). */
enum class TransactionType { DEBIT, CREDIT }

enum class TransactionSource { SMS, MANUAL, IMPORT, PASTE }

enum class ClassificationStatus { PENDING, CLASSIFIED, SKIPPED }

/**
 * NORMAL — ordinary cashflow.
 * SELF_TRANSFER — linked legs between owned accounts (excluded from Home debit/credit).
 * TAB_TRANSFER — tab-only bookkeeping (move IOU between tabs, openings, future “they paid”).
 *   Affects tab balances only; excluded from cashflow and from Digital / owned-account totals.
 */
enum class TransactionKind { NORMAL, SELF_TRANSFER, TAB_TRANSFER }

/** Tab-only bookkeeping must not move owned-account or Digital (no bank) aggregates. */
fun isTabOnlyBookkeeping(kind: String?): Boolean =
    kind?.uppercase() == TransactionKind.TAB_TRANSFER.name

/** Self-transfer and tab-transfer never enter Home debit/credit totals. */
fun isExcludedFromCashflow(kind: String?): Boolean {
    val k = kind?.uppercase()
    return k == TransactionKind.SELF_TRANSFER.name || k == TransactionKind.TAB_TRANSFER.name
}

enum class AccountKind { BANK, CARD, CASH, WALLET }

enum class TabEntryType { CREDIT, DEBIT, ADJUSTMENT }

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

data class Tab(
    val id: Long = 0,
    val name: String,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Open Tab balance.
 *
 * Spec: positive → they owe you; negative → you owe them.
 *   balance = debits − credits
 * (money you advanced / spent on their behalf vs settlements).
 */
data class TabBalance(
    val tab: Tab,
    /** Open balance: + they owe you, − you owe them. */
    val balancePaise: Long,
    /** Credits (settlements / repayments) on this tab. */
    val creditedPaise: Long,
    /** Debits (advances / spends) on this tab. */
    val debitedPaise: Long,
) {
    fun theyOweYou(): Boolean = balancePaise > 0L
    fun youOweThem(): Boolean = balancePaise < 0L
    fun isSettled(): Boolean = balancePaise == 0L
}

/** Validation helpers for split editor (pure; unit-testable). */
object SplitRules {
    /** Debit = +, Credit = −. Matches account net (credits − debits flipped for “out positive”). */
    fun signedPaise(type: TransactionType, amountPaise: Long): Long =
        if (type == TransactionType.DEBIT) amountPaise else -amountPaise

    fun signedParent(parentType: TransactionType, parentAmountPaise: Long): Long =
        signedPaise(parentType, kotlin.math.abs(parentAmountPaise))

    fun signedPartsSum(parts: List<SplitPart>): Long =
        parts.sumOf { signedPaise(it.type, it.amountPaise) }

    /**
     * Null if valid; otherwise a short user-facing reason.
     * Empty [parts] clears a draft split. Non-empty: each amount > 0 and
     * Debit lines − Credit lines must equal the signed parent.
     */
    fun validateParts(
        parentType: TransactionType,
        parentAmountPaise: Long,
        parts: List<SplitPart>,
    ): String? {
        if (parts.isEmpty()) return null
        val parentAbs = kotlin.math.abs(parentAmountPaise)
        if (parentAbs == 0L) return "Parent amount must be greater than zero"
        if (parts.any { it.amountPaise <= 0L }) return "Each split must be greater than zero"
        val parentSigned = signedParent(parentType, parentAbs)
        val sum = signedPartsSum(parts)
        if (sum != parentSigned) {
            return "Debit − Credit must equal parent"
        }
        return null
    }

    /** Remaining signed amount to allocate (0 when balanced). */
    fun remainingSignedPaise(
        parentType: TransactionType,
        parentAmountPaise: Long,
        parts: List<SplitPart>,
    ): Long = signedParent(parentType, parentAmountPaise) - signedPartsSum(parts)
}

/**
 * One line of a split: amount must be > 0; signed Debit−Credit must equal parent.
 *
 * Splitting replaces the original transaction with standalone child rows
 * that share a [Transaction.splitGroupId]; the parent is soft-deleted.
 */
data class SplitPart(
    val amountPaise: Long,
    val categoryId: Long? = null,
    val counterparty: String? = null,
    val tabId: Long? = null,
    val note: String? = null,
    /** Defaults to parent type when omitted (same-direction splits). */
    val type: TransactionType = TransactionType.DEBIT,
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
    val tabId: Long? = null,
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
    val tabName: String? = null,
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
    /** ARGB from the category row; null if unknown / uncategorized. */
    val color: Long? = null,
)

/** Net cashflow for a category: credits - debits. */
data class CategoryNetSpend(
    val categoryId: Long?,
    val categoryName: String,
    val debitPaise: Long,
    val creditPaise: Long,
    /** Net amount: creditPaise - debitPaise (positive = surplus/inflow, negative = net spend/outflow). */
    val netPaise: Long = creditPaise - debitPaise,
    /** ARGB from the category row; null if unknown / uncategorized. */
    val color: Long? = null,
)

/** This-month flow grouped by account (source). */
data class SourceSpend(
    val accountId: Long?,
    val accountName: String,
    val totalPaise: Long,
)

/** Net cashflow for an account/source: credits - debits. */
data class SourceNetSpend(
    val accountId: Long?,
    val accountName: String,
    val debitPaise: Long,
    val creditPaise: Long,
    /** Net delta for the account: creditPaise - debitPaise. */
    val netPaise: Long = creditPaise - debitPaise,
)

data class MonthlyTrend(
    val monthKey: String,
    val incomePaise: Long,
    val expensePaise: Long,
    val topCategoryId: Long? = null,
    val topCategoryName: String? = null,
    val topCategoryPaise: Long = 0L,
) {
    val netPaise: Long get() = incomePaise - expensePaise
    val topCategoryShare: Float
        get() = if (expensePaise > 0L) {
            (topCategoryPaise.toFloat() / expensePaise.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}
