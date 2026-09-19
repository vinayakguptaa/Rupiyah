package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.SourceSpend
import com.krtky.financetracker.ui.components.CategoryInteractivePieChart
import com.krtky.financetracker.ui.util.FlowSlice
import com.krtky.financetracker.ui.util.FlowSliceCollapse
import com.krtky.financetracker.ui.util.FlowSliceColors
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
    onOpenCategoryList: () -> Unit,
    onOpenSourceList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var cut by rememberSaveable(title) { mutableStateOf(FlowCut.Category) }
    val collapsed = remember(cut, byCategory, bySource) {
        val rawSlices = when (cut) {
            FlowCut.Category -> byCategory.map {
                FlowSlice(
                    id = it.categoryId,
                    name = it.categoryName,
                    totalPaise = it.totalPaise,
                    colorArgb = it.color,
                )
            }
            FlowCut.Source -> bySource.map {
                FlowSlice(
                    id = it.accountId,
                    name = it.accountName,
                    totalPaise = it.totalPaise,
                    colorArgb = FlowSliceColors.forSourceKey(it.accountName),
                )
            }
        }
        FlowSliceCollapse.collapse(rawSlices, coverage = 0.80, maxNamed = 4)
    }
    val pieSlices = collapsed.map { slice ->
        CategorySpend(
            categoryId = slice.id,
            categoryName = slice.name,
            totalPaise = slice.totalPaise,
            color = slice.colorArgb,
        )
    }
    val onOpenList = if (cut == FlowCut.Category) onOpenCategoryList else onOpenSourceList
    val restMuted = scheme.onSurfaceVariant.copy(alpha = 0.55f)
    val legendColors = collapsed.mapIndexed { index, slice ->
        when {
            slice.isRest -> restMuted
            slice.colorArgb != null -> Color(slice.colorArgb.toInt())
            else -> listOf(
                scheme.primary,
                scheme.tertiary,
                scheme.secondary,
                scheme.error,
            )[index % 4]
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (hidden) "••••" else "${totalPaise.inr()} · $monthLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            FlowCutTabs(
                cut = cut,
                onCut = { cut = it },
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
                    exactProportions = true,
                )
                FlowSliceList(
                    slices = collapsed,
                    colors = legendColors,
                    totalPaise = totalPaise,
                    hidden = hidden,
                    emptyLabel = emptyLabel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FlowCutTabs(
    cut: FlowCut,
    onCut: (FlowCut) -> Unit,
) {
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

@Composable
private fun FlowSliceList(
    slices: List<FlowSlice>,
    colors: List<Color>,
    totalPaise: Long,
    hidden: Boolean,
    emptyLabel: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (slices.isEmpty()) {
            Text(
                emptyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        } else {
            slices.forEachIndexed { index, slice ->
                val pct = if (totalPaise > 0) {
                    (slice.totalPaise.toFloat() / totalPaise.toFloat() * 100f).roundToInt()
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
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.getOrElse(index) { scheme.primary }),
                    )
                    Text(
                        slice.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (slice.isRest) scheme.onSurfaceVariant else scheme.onSurface,
                    )
                    Text(
                        if (hidden) "••••" else "$pct%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
