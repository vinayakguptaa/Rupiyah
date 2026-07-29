package com.krtky.financetracker.domain.model

enum class TransactionType { INCOME, EXPENSE }

enum class TransactionSource { EMAIL, SMS, MANUAL, IMPORT }

enum class ClassificationStatus { PENDING, CLASSIFIED, SKIPPED }

enum class FundEntryType { CREDIT, DEBIT, ADJUSTMENT }

enum class EmailProcessStatus { NEW, PARSED, DUPLICATE, FAILED, IGNORED }

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

data class Fund(
    val id: Long = 0,
    val name: String,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Envelope budget / limit in paise (fixed; not inflated by later credits). */
    val budgetPaise: Long = 0L,
)

/**
 * Fund pot math (simple, always):
 *
 *   remaining = fundAmount + income − expenses
 *   display   = remaining out of fundAmount
 *
 * Example: set amount ₹1500, txn −100, txn +50 → ₹1450 left of ₹1500.
 *
 * [fund.budgetPaise] is the amount you set (create / edit). Transactions
 * with this fundId are the only other inputs — no stacked ledger noise.
 */
data class FundBalance(
    val fund: Fund,
    /** Cash left: amount + income − expenses. */
    val balancePaise: Long,
    /** Income transactions assigned to this fund. */
    val creditedPaise: Long,
    /** Expense transactions assigned to this fund. */
    val debitedPaise: Long,
    /** Same as the amount you set (limit / starting pot). */
    val openingPaise: Long = 0L,
) {
    /** Envelope size you configured (never “all credits ever”). */
    fun limitPaise(): Long = when {
        fund.budgetPaise > 0L -> fund.budgetPaise
        openingPaise > 0L -> openingPaise
        else -> maxOf(balancePaise.coerceAtLeast(0L), 1L)
    }

    fun remainingOfLimitPaise(): Long = balancePaise.coerceAtLeast(0L)

    fun remainingRatio(): Float {
        val limit = limitPaise().toFloat()
        if (limit <= 0f) return 0f
        return (remainingOfLimitPaise().toFloat() / limit).coerceIn(0f, 1f)
    }

    fun spentRatio(): Float = (1f - remainingRatio()).coerceIn(0f, 1f)

    /** Over limit or negative cash left in the pot. */
    fun isOverspent(): Boolean = balancePaise < 0L || spentRatio() >= 1f
}

data class Transaction(
    val id: String,
    val type: TransactionType,
    val amountPaise: Long,
    val currency: String = "INR",
    val occurredAt: Long,
    val recordedAt: Long = System.currentTimeMillis(),
    val merchant: String? = null,
    /** Person/merchant paid to (expense) or received from (income). */
    val counterparty: String? = null,
    val categoryId: Long? = null,
    val fundId: Long? = null,
    val paymentMethod: String? = null,
    val source: TransactionSource = TransactionSource.MANUAL,
    val note: String? = null,
    val isCash: Boolean = false,
    val classificationStatus: ClassificationStatus = ClassificationStatus.PENDING,
    val classificationNotifiedAt: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeName: String? = null,
    val locationAccuracy: Float? = null,
    val locationMatchedAt: Long? = null,
    val emailMessageId: String? = null,
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
    /** Relative path under app files (`receipts/…`) or content URI string. */
    val receiptUri: String? = null,
)

data class TrustedSender(
    val id: Long = 0,
    val emailPattern: String,
    val walletLabel: String,
    val enabled: Boolean = true,
)

data class MonthlySummary(
    val incomePaise: Long,
    val expensePaise: Long,
) {
    val netPaise: Long get() = incomePaise - expensePaise
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

/** Named payment account (bank/wallet/cash) with running net balance. */
data class AccountBalance(
    val name: String,
    val balancePaise: Long,
    val txnCount: Long = 0,
)
