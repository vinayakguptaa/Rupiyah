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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.FundBalance
import com.krtky.financetracker.domain.model.SplitPart
import com.krtky.financetracker.ui.util.inr

@Composable
fun AddCashDraftSplitsCard(
    draftSplits: List<SplitPart>,
    categories: List<Category>,
    funds: List<FundBalance>,
    amountBlank: Boolean,
    onOpenEditor: () -> Unit,
    onClear: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Splits", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (draftSplits.isEmpty()) {
                            "Optional · break amount across categories, names, or tabs"
                        } else {
                            "${draftSplits.size} lines · saved as separate transactions"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onOpenEditor,
                    shape = RoundedCornerShape(18.dp),
                    enabled = !amountBlank,
                ) {
                    Text(if (draftSplits.isEmpty()) "Split" else "Edit")
                }
            }
            draftSplits.forEach { line ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        line.counterparty
                            ?: categories.firstOrNull { it.id == line.categoryId }?.name
                            ?: funds.firstOrNull { it.fund.id == line.fundId }?.fund?.name
                            ?: "Line",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        line.amountPaise.inr(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (draftSplits.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("Clear splits")
                }
            }
        }
    }
}
