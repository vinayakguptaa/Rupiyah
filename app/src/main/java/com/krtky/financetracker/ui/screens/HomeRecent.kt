package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.EmptyState
import com.krtky.financetracker.ui.components.OutlinePillButton
import com.krtky.financetracker.ui.components.TransactionCard
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.categoryColor
import com.krtky.financetracker.ui.util.formatDateTime
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.onCategoryColor

@Composable
internal fun HomeRecentSection(
    data: HomeDashboardData,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    if (!compact) {
        Text(
            stringResource(R.string.home_recent),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    } else {
        Text(
            stringResource(R.string.home_recent),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

internal fun LazyListScope.recentActivityItems(
    data: HomeDashboardData,
    onOpenTxn: (String) -> Unit,
    onAddCash: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    if (data.filtered.isEmpty()) {
        item(key = "recent_empty") {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = stringResource(R.string.empty_txns_title),
                body = stringResource(R.string.empty_txns_body),
                actionLabel = stringResource(R.string.empty_txns_action),
                onAction = onAddCash,
            )
        }
    } else {
        itemsIndexed(
            data.filtered,
            key = { _, t -> t.id },
        ) { _, t ->
            val scheme = MaterialTheme.colorScheme
            val party = t.counterparty ?: t.note ?: t.accountName ?: "Transaction"
            val sign = if (t.type == TransactionType.DEBIT) "-" else "+"
            val catColor = categoryColor(t.categoryColor)
            TransactionCard(
                title = party,
                subtitle = listOfNotNull(
                    t.occurredAt.formatDateTime(),
                    t.categoryName,
                    t.note?.take(28),
                    t.accountName,
                ).joinToString(" · "),
                amount = "$sign${t.amountPaise.inr()}",
                amountColor = if (t.type == TransactionType.DEBIT) scheme.error else scheme.primary,
                icon = CategoryIcons.iconFor(t.categoryIcon, t.categoryName),
                onClick = { onOpenTxn(t.id) },
                visible = true,
                iconContainerColor = catColor,
                iconTint = catColor?.let { onCategoryColor(it) },
            )
        }
        item(key = "see_all") {
            OutlinePillButton(
                text = stringResource(R.string.home_see_all_activity),
                icon = Icons.Default.History,
                onClick = onOpenHistory,
            )
        }
    }
}
