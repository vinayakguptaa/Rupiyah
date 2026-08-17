package com.krtky.financetracker.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.TabBalance
import com.krtky.financetracker.domain.model.SplitPart
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.InfoRow
import com.krtky.financetracker.ui.components.OsmMiniMap
import com.krtky.financetracker.ui.components.ReceiptPreview
import com.krtky.financetracker.ui.util.AppHaptics
import com.krtky.financetracker.ui.util.formatDateTime
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.mapsUri

/**
 * Read-only detail column shown when not editing (Info-first mode).
 * Rendered inside the Scaffold's AnimatedVisibility in [TransactionDetailScreen].
 */
@Composable
internal fun TransactionDetailView(
    t: Transaction,
    categories: List<Category>,
    tabs: List<TabBalance>,
    splits: List<SplitPart>,
    existingReceiptUri: Uri?,
    context: Context,
    onOpenSplit: () -> Unit,
    onClearSplits: () -> Unit,
    haptics: AppHaptics,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val partyTitle = t.counterparty ?: t.accountName ?: "Transaction"
    val amountSign = if (t.type == TransactionType.DEBIT) "-" else "+"
    val infoPayment = when {
        t.isCash || t.accountName.equals("Cash", true) -> "Cash"
        !t.accountName.isNullOrBlank() -> t.accountName!!
        else -> "Digital"
    }
    val categoryName = t.categoryName ?: categories.firstOrNull { it.id == t.categoryId }?.name
    val tabName = tabs.firstOrNull { it.tab.id == t.tabId }?.tab?.name

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = scheme.primaryContainer,
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (t.type == TransactionType.DEBIT) "Debit" else "Credit",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
                Text(
                    partyTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Normal,
                    color = scheme.onPrimaryContainer,
                )
                Text(
                    "$amountSign${t.amountPaise.inr()}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Normal,
                    color = scheme.onPrimaryContainer,
                )
                Text(
                    t.occurredAt.formatDateTime(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.72f),
                )
            }
        }

        InfoRow(
            icon = Icons.Default.Payments,
            label = "Payment",
            value = infoPayment,
        )
        InfoRow(
            icon = Icons.Default.Category,
            label = "Category",
            value = categoryName ?: "Uncategorized",
        )
        ReceiptPreview(
            receiptUri = existingReceiptUri,
            context = context,
        )
        if (tabName != null) {
            InfoRow(
                icon = Icons.Default.AccountBalance,
                label = "Tab",
                value = tabName,
            )
        }
        TransactionDetailSplits(
            t = t,
            splits = splits,
            categories = categories,
            tabs = tabs,
            onOpenSplit = onOpenSplit,
            onClearSplits = onClearSplits,
            haptics = haptics,
        )
        if (!t.note.isNullOrBlank()) {
            InfoRow(
                icon = Icons.Default.Edit,
                label = "Note",
                value = t.note!!,
            )
        }
        if (t.placeName != null || t.latitude != null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = scheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.Place, null,
                            tint = scheme.primary, modifier = Modifier.size(20.dp),
                        )
                        Column {
                            Text(
                                "Location",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                            )
                            Text(
                                t.placeName ?: "${t.latitude}, ${t.longitude}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    if (t.latitude != null && t.longitude != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        ) {
                            OsmMiniMap(
                                latitude = t.latitude,
                                longitude = t.longitude,
                                placeName = t.placeName,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, mapsUri(t.latitude, t.longitude, t.placeName)),
                                )
                            },
                            shape = RoundedCornerShape(18.dp),
                        ) { Text("Open in Maps") }
                    }
                }
            }
        }
        if (!t.externalRefId.isNullOrBlank()) {
            Text(
                "Ref: ${t.externalRefId}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(72.dp))
    }
}
