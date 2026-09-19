package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.domain.model.CategoryNetSpend
import com.krtky.financetracker.domain.model.SourceNetSpend
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.categoryColor
import com.krtky.financetracker.ui.util.inr
import kotlin.math.abs

enum class HomeNetTab { Category, Account }

/**
 * Single unified card for Net Flow:
 * Tab switcher between Category (Top 3 most negative) and Account (non-zero, active, most negative first).
 * In all cases, tapping rows or "View all" opens MonthFlowScreen in "NET" mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeUnifiedNetSection(
    categoryNetSpend: List<CategoryNetSpend>,
    sourceNetSpend: List<SourceNetSpend>,
    activeAccountIds: Set<Long>,
    monthLabel: String,
    hidden: Boolean,
    onOpenMonthFlow: (direction: String, group: MonthFlowGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeNetTab.Category) }
    val scheme = MaterialTheme.colorScheme

    val topCategoryNet = remember(categoryNetSpend) {
        categoryNetSpend
            .filter { it.netPaise < 0 }
            .sortedBy { it.netPaise }
            .take(3)
    }

    val topSourceNet = remember(sourceNetSpend, activeAccountIds) {
        sourceNetSpend
            .filter { src ->
                src.netPaise != 0L && (src.accountId == null || activeAccountIds.isEmpty() || src.accountId in activeAccountIds)
            }
            .sortedBy { it.netPaise }
            .take(3)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val currentGroup = if (selectedTab == HomeNetTab.Category) MonthFlowGroup.Category else MonthFlowGroup.Source
            val headerTitle = "Monthly Exchange"

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenMonthFlow("NET", currentGroup) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        headerTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        monthLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }

            // Tab Switcher between Category and Account
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val tabs = listOf(
                    HomeNetTab.Category to "Category",
                    HomeNetTab.Account to "Account",
                )
                tabs.forEachIndexed { index, (tab, label) ->
                    SegmentedButton(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }

            if (selectedTab == HomeNetTab.Category) {
                if (topCategoryNet.isEmpty()) {
                    Text(
                        "No net spending this month",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        topCategoryNet.forEach { item ->
                            CategoryNetItemRow(
                                item = item,
                                hidden = hidden,
                                onClick = { onOpenMonthFlow("NET", MonthFlowGroup.Category) },
                            )
                        }
                    }
                }

                TextButton(
                    onClick = { onOpenMonthFlow("NET", MonthFlowGroup.Category) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("View all categories")
                }
            } else {
                if (topSourceNet.isEmpty()) {
                    Text(
                        "No account activity this month",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        topSourceNet.forEach { item ->
                            SourceNetItemRow(
                                item = item,
                                hidden = hidden,
                                onClick = { onOpenMonthFlow("NET", MonthFlowGroup.Source) },
                            )
                        }
                    }
                }

                TextButton(
                    onClick = { onOpenMonthFlow("NET", MonthFlowGroup.Source) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("View all accounts")
                }
            }
        }
    }
}

@Composable
private fun CategoryNetItemRow(
    item: CategoryNetSpend,
    hidden: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val catColor = categoryColor(item.color) ?: scheme.primary
    val icon = CategoryIcons.iconFor(null, item.categoryName)
    val subtitle = when {
        item.debitPaise > 0 && item.creditPaise > 0 ->
            "Spent ${item.debitPaise.inr()} · Received ${item.creditPaise.inr()}"
        item.creditPaise > 0 ->
            "Received ${item.creditPaise.inr()}"
        else ->
            "Spent ${item.debitPaise.inr()}"
    }
    val netFormatted = when {
        hidden -> "••••"
        item.netPaise < 0 -> "-${abs(item.netPaise).inr()}"
        item.netPaise > 0 -> "+${item.netPaise.inr()}"
        else -> item.netPaise.inr()
    }
    val amountColor = when {
        hidden -> scheme.onSurface
        item.netPaise > 0 -> scheme.primary
        else -> scheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(catColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = catColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.categoryName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            netFormatted,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = amountColor,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SourceNetItemRow(
    item: SourceNetSpend,
    hidden: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isCash = item.accountName.equals("Cash", ignoreCase = true)
    val icon = if (isCash) Icons.Default.Payments else Icons.Default.AccountBalance
    val subtitle = when {
        item.debitPaise > 0 && item.creditPaise > 0 ->
            "In ${item.creditPaise.inr()} · Out ${item.debitPaise.inr()}"
        item.creditPaise > 0 ->
            "In ${item.creditPaise.inr()}"
        item.debitPaise > 0 ->
            "Out ${item.debitPaise.inr()}"
        else ->
            "No activity"
    }
    val netFormatted = when {
        hidden -> "••••"
        item.netPaise < 0 -> "-${abs(item.netPaise).inr()}"
        item.netPaise > 0 -> "+${item.netPaise.inr()}"
        else -> item.netPaise.inr()
    }
    val amountColor = when {
        hidden -> scheme.onSurface
        item.netPaise > 0 -> scheme.primary
        else -> scheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(scheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.accountName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            netFormatted,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = amountColor,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}
