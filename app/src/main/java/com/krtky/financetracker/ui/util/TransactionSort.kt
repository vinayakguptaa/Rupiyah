package com.krtky.financetracker.ui.util

import com.krtky.financetracker.domain.model.Transaction

/**
 * Shared sort options for every transactions list.
 * Date sorts use full millisecond timestamps (HH:mm:ss precision), then
 * [Transaction.recordedAt] and [Transaction.id] as stable tie-breakers.
 */
enum class TransactionSortOrder(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    AMOUNT_HIGH("Amount: high → low"),
    AMOUNT_LOW("Amount: low → high"),
    NAME_AZ("Name: A → Z"),
    NAME_ZA("Name: Z → A"),
}

private val displayName: (Transaction) -> String = { t ->
    (t.counterparty ?: t.merchant ?: t.note ?: "").trim().lowercase()
}

fun List<Transaction>.sortedWithOrder(order: TransactionSortOrder): List<Transaction> = when (order) {
    TransactionSortOrder.NEWEST -> sortedWith(
        compareByDescending<Transaction> { it.occurredAt }
            .thenByDescending { it.recordedAt }
            .thenByDescending { it.id },
    )
    TransactionSortOrder.OLDEST -> sortedWith(
        compareBy<Transaction> { it.occurredAt }
            .thenBy { it.recordedAt }
            .thenBy { it.id },
    )
    TransactionSortOrder.AMOUNT_HIGH -> sortedWith(
        compareByDescending<Transaction> { it.amountPaise }
            .thenByDescending { it.occurredAt }
            .thenByDescending { it.id },
    )
    TransactionSortOrder.AMOUNT_LOW -> sortedWith(
        compareBy<Transaction> { it.amountPaise }
            .thenByDescending { it.occurredAt }
            .thenByDescending { it.id },
    )
    TransactionSortOrder.NAME_AZ -> sortedWith(
        compareBy<Transaction> { displayName(it) }
            .thenByDescending { it.occurredAt }
            .thenBy { it.id },
    )
    TransactionSortOrder.NAME_ZA -> sortedWith(
        compareByDescending<Transaction> { displayName(it) }
            .thenByDescending { it.occurredAt }
            .thenBy { it.id },
    )
}
