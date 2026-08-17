package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.TabBalance
import com.krtky.financetracker.domain.model.SplitPart
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.ui.util.AppHaptics
import com.krtky.financetracker.ui.util.inr

@Composable
internal fun TransactionDetailSplits(
    t: Transaction,
    splits: List<SplitPart>,
    categories: List<Category>,
    tabs: List<TabBalance>,
    onOpenSplit: () -> Unit,
    onClearSplits: () -> Unit,
    haptics: AppHaptics,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val canSplit = !t.isSelfTransfer() && !t.isTabTransfer()
    if (canSplit) {
        val groupSize = if (t.isSplitPart()) splits.size + 1 else splits.size
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = scheme.surfaceContainerHigh,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.split_section_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (splits.isEmpty()) {
                                "Break into categories, names, or tabs"
                            } else {
                                stringResource(
                                    R.string.split_lines_summary,
                                    groupSize,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.select()
                            onOpenSplit()
                        },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(
                            if (splits.isEmpty()) {
                                stringResource(R.string.split_action)
                            } else {
                                stringResource(R.string.split_edit)
                            },
                        )
                    }
                }
                splits.forEach { line ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                line.counterparty
                                    ?: categories.firstOrNull { it.id == line.categoryId }?.name
                                    ?: tabs.firstOrNull { it.tab.id == line.tabId }?.tab?.name
                                    ?: "Line",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val bits = buildList {
                                line.counterparty?.takeIf { it.isNotBlank() }?.let { add(it) }
                                tabs.firstOrNull { it.tab.id == line.tabId }?.tab?.name?.let { add(it) }
                                line.note?.takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                            if (bits.isNotEmpty()) {
                                Text(
                                    bits.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            line.amountPaise.inr(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                if (t.isSplitPart()) {
                    OutlinedButton(
                        onClick = {
                            haptics.select()
                            onClearSplits()
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.split_merge_action))
                    }
                }
            }
        }
    } else if (t.isSelfTransfer()) {
        Text(
            stringResource(R.string.split_not_for_transfer),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}
