package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.ui.components.CategoryInteractivePieChart
import com.krtky.financetracker.ui.util.inr
import kotlin.math.roundToInt

@Composable
internal fun HomeCategoryRingSection(
    data: HomeDashboardData,
    compact: Boolean,
    halfWidth: Boolean,
    onCategorySelected: (CategorySpend?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (compact) scheme.surfaceContainerLowest else scheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .padding(horizontal = if (halfWidth) 10.dp else 14.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = if (halfWidth) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(
                stringResource(R.string.home_spending_by_category),
                style = if (halfWidth) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            if (!halfWidth) {
                Text(
                    stringResource(R.string.home_ring_filter_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (halfWidth) {
                CategoryInteractivePieChart(
                    categorySpends = data.categorySpend,
                    totalExpense = data.spent,
                    incomePaise = data.income,
                    goalLabel = if (data.income > 0) "of ${data.income.inr()}" else data.monthLabel,
                    size = 108.dp,
                    onCategorySelected = onCategorySelected,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CategoryInteractivePieChart(
                        categorySpends = data.categorySpend,
                        totalExpense = data.spent,
                        incomePaise = data.income,
                        goalLabel = if (data.income > 0) "of ${data.income.inr()}" else data.monthLabel,
                        size = 136.dp,
                        onCategorySelected = onCategorySelected,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val topCats = data.categorySpend.filter { it.totalPaise > 0 }.take(5)
                        if (topCats.isEmpty()) {
                            Text(
                                stringResource(R.string.home_no_expenses_yet),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        } else {
                            val pieColors = listOf(
                                scheme.primary,
                                scheme.tertiary,
                                scheme.secondary,
                                scheme.error,
                                scheme.primary.copy(alpha = 0.55f),
                            )
                            topCats.forEachIndexed { index, cat ->
                                val pct = if (data.spent > 0) {
                                    (cat.totalPaise.toFloat() / data.spent.toFloat() * 100f).roundToInt()
                                } else {
                                    0
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(pieColors.getOrElse(index) { scheme.primary }),
                                    )
                                    Text(
                                        cat.categoryName,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                    )
                                    Text(
                                        "$pct%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (data.categorySpend.size > 5) {
                                Text(
                                    "+${data.categorySpend.size - 5} more",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
