package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.SourceSpend
import com.krtky.financetracker.ui.components.CategoryInteractivePieChart
import com.krtky.financetracker.ui.util.inr
import kotlin.math.roundToInt

private enum class FlowCut { Category, Source }

@Composable
internal fun HomeFlowBreakdownSection(
    title: String,
    totalPaise: Long,
    monthLabel: String,
    hidden: Boolean,
    byCategory: List<CategorySpend>,
    bySource: List<SourceSpend>,
    emptyLabel: String,
    compact: Boolean,
    halfWidth: Boolean,
    onOpenCategoryList: () -> Unit,
    onOpenSourceList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var cut by rememberSaveable(title) { mutableStateOf(FlowCut.Category) }
    val slices = when (cut) {
        FlowCut.Category -> byCategory.map { it.categoryName to it.totalPaise }
        FlowCut.Source -> bySource.map { it.accountName to it.totalPaise }
    }
    val pieSlices = slices.map { (name, paise) ->
        CategorySpend(categoryId = null, categoryName = name, totalPaise = paise)
    }
    val onOpenList = if (cut == FlowCut.Category) onOpenCategoryList else onOpenSourceList
    val top = slices.filter { it.second > 0 }.take(5)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (compact) scheme.surfaceContainerLowest else scheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .padding(
                    horizontal = if (halfWidth) 10.dp else 14.dp,
                    vertical = if (halfWidth) 10.dp else 14.dp,
                )
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (halfWidth) 6.dp else 10.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenList),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = if (halfWidth) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!halfWidth) {
                        Text(
                            if (hidden) "••••" else "${totalPaise.inr()} · $monthLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(if (halfWidth) 18.dp else 24.dp),
                )
            }
            if (halfWidth) {
                FlowCutTabs(
                    cut = cut,
                    onCut = { cut = it },
                    compact = true,
                )
                CategoryInteractivePieChart(
                    categorySpends = pieSlices,
                    totalExpense = totalPaise,
                    incomePaise = 0L,
                    goalLabel = monthLabel,
                    compact = true,
                    centerTitle = title,
                    hidden = hidden,
                    interactive = true,
                    onCenterClick = onOpenList,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
            } else {
                FlowCutTabs(
                    cut = cut,
                    onCut = { cut = it },
                    compact = false,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CategoryInteractivePieChart(
                        categorySpends = pieSlices,
                        totalExpense = totalPaise,
                        incomePaise = 0L,
                        goalLabel = monthLabel,
                        size = 136.dp,
                        centerTitle = title,
                        hidden = hidden,
                        interactive = false,
                    )
                    FlowSliceList(
                        top = top,
                        totalPaise = totalPaise,
                        hidden = hidden,
                        emptyLabel = emptyLabel,
                        moreCount = (slices.size - top.size).coerceAtLeast(0),
                        compact = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowCutTabs(
    cut: FlowCut,
    onCut: (FlowCut) -> Unit,
    compact: Boolean,
) {
    if (compact) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowCut.entries.forEach { option ->
                val selected = cut == option
                val label = if (option == FlowCut.Category) {
                    stringResource(R.string.home_cut_category)
                } else {
                    stringResource(R.string.home_cut_source)
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onCut(option) },
                )
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = cut == FlowCut.Category,
                onClick = { onCut(FlowCut.Category) },
                label = { Text(stringResource(R.string.home_cut_category)) },
            )
            FilterChip(
                selected = cut == FlowCut.Source,
                onClick = { onCut(FlowCut.Source) },
                label = { Text(stringResource(R.string.home_cut_source)) },
            )
        }
    }
}

@Composable
private fun FlowSliceList(
    top: List<Pair<String, Long>>,
    totalPaise: Long,
    hidden: Boolean,
    emptyLabel: String,
    moreCount: Int,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val pieColors = listOf(
        scheme.primary,
        scheme.tertiary,
        scheme.secondary,
        scheme.error,
        scheme.primary.copy(alpha = 0.55f),
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        if (top.isEmpty()) {
            Text(
                emptyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        } else {
            top.forEachIndexed { index, (name, paise) ->
                val pct = if (totalPaise > 0) {
                    (paise.toFloat() / totalPaise.toFloat() * 100f).roundToInt()
                } else {
                    0
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(if (compact) 7.dp else 8.dp)
                            .clip(CircleShape)
                            .background(pieColors.getOrElse(index) { scheme.primary }),
                    )
                    Text(
                        name,
                        style = if (compact) {
                            MaterialTheme.typography.labelMedium
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (hidden) {
                            "••••"
                        } else if (compact) {
                            paise.inr()
                        } else {
                            "$pct%"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (moreCount > 0) {
                Text(
                    "+$moreCount more",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                )
            }
        }
    }
}
