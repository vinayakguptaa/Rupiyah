package com.krtky.financetracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.domain.model.MonthlyTrend
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.util.inr
import java.text.DateFormatSymbols
import kotlin.math.sqrt

/**
 * Category spend pie: multi-segment ring with gaps; tap a segment to filter.
 * M3 progress-style track + active indicator (round caps).
 */
@Composable
fun CategoryInteractivePieChart(
    categorySpends: List<CategorySpend>,
    totalExpense: Long,
    goalLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    incomePaise: Long = 0L,
    onCategorySelected: (CategorySpend?) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val trackColor = scheme.surfaceContainerHighest
    val activeColor = scheme.primary
    val onBg = scheme.onSurface
    val muted = scheme.onSurfaceVariant
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { (size * 0.11f).toPx().coerceIn(12.dp.toPx(), 18.dp.toPx()) }
    val gapDeg = run {
        val radius = (with(density) { size.toPx() } - strokeWidthPx) / 2f
        val circumference = (2.0 * Math.PI * radius).toFloat()
        val gapPx = strokeWidthPx + with(density) { 3.dp.toPx() }
        ((gapPx / circumference) * 360f).coerceIn(8f, 22f)
    }

    var selectedIndex by remember { mutableStateOf(-1) }

    val colors = listOf(
        scheme.primary,
        scheme.tertiary,
        scheme.error,
        scheme.secondary,
        scheme.primary.copy(alpha = 0.68f),
        scheme.tertiary.copy(alpha = 0.68f),
        scheme.error.copy(alpha = 0.68f),
        scheme.secondary.copy(alpha = 0.68f),
    )

    val validSpends = categorySpends.filter { it.totalPaise > 0 }
    val calculatedTotal = validSpends.sumOf { it.totalPaise }
    val finalTotal = if (calculatedTotal > 0) calculatedTotal else totalExpense
    val overallProgress = if (incomePaise > 0) {
        (finalTotal.toFloat() / incomePaise.toFloat()).coerceIn(0f, 1f)
    } else if (finalTotal > 0) 1f else 0f
    val animatedOverall by animateFloatAsState(
        targetValue = overallProgress,
        animationSpec = M3EMotion.spatialSlow(),
        label = "m3CircularProgress",
    )

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(validSpends, finalTotal) {
                detectTapGestures { offset ->
                    val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val dist = sqrt(dx * dx + dy * dy)
                    val outerRadius = size.toPx() / 2f
                    val innerRadius = outerRadius - strokeWidthPx

                    if (dist < innerRadius - 8f) {
                        selectedIndex = -1
                        onCategorySelected(null)
                    } else if (dist <= outerRadius + 12f && validSpends.isNotEmpty() && finalTotal > 0) {
                        var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        angle = (angle + 90f + 360f) % 360f
                        var currentAngle = 0f
                        var foundIndex = -1
                        validSpends.forEachIndexed { index, cat ->
                            val sweep = (360f - gapDeg * validSpends.size) *
                                (cat.totalPaise.toFloat() / finalTotal.toFloat())
                            if (angle >= currentAngle && angle <= currentAngle + sweep + gapDeg) {
                                foundIndex = index
                            }
                            currentAngle += sweep + gapDeg
                        }
                        if (foundIndex != -1) {
                            if (selectedIndex == foundIndex) {
                                selectedIndex = -1
                                onCategorySelected(null)
                            } else {
                                selectedIndex = foundIndex
                                onCategorySelected(validSpends[foundIndex])
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val strokeWidth = strokeWidthPx
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )

            if (validSpends.isEmpty() || finalTotal == 0L) {
                if (animatedOverall > 0.01f) {
                    drawArc(
                        color = activeColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedOverall,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke,
                    )
                }
            } else if (selectedIndex == -1) {
                val usable = 360f - gapDeg * validSpends.size.coerceAtLeast(1)
                var startAngle = -90f + gapDeg / 2f
                validSpends.forEachIndexed { index, cat ->
                    val sweep = usable * (cat.totalPaise.toFloat() / finalTotal.toFloat())
                    if (sweep > 0.5f) {
                        drawArc(
                            color = colors[index % colors.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke,
                        )
                    }
                    startAngle += sweep + gapDeg
                }
            } else {
                val cat = validSpends[selectedIndex]
                val pct = cat.totalPaise.toFloat() / finalTotal.toFloat()
                drawArc(
                    color = colors[selectedIndex % colors.size],
                    startAngle = -90f,
                    sweepAngle = 360f * pct,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(size * 0.18f),
        ) {
            val centerLabel = if (selectedIndex == -1) stringResource(com.krtky.financetracker.R.string.home_spent_label) else validSpends[selectedIndex].categoryName
            val centerValue = if (selectedIndex == -1) {
                finalTotal.inr()
            } else {
                validSpends[selectedIndex].totalPaise.inr()
            }
            val centerSub = if (selectedIndex == -1) {
                goalLabel
            } else {
                val pct = (validSpends[selectedIndex].totalPaise * 100f / maxOf(finalTotal, 1L)).toInt()
                "$pct%"
            }

            Text(
                centerLabel,
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                centerValue,
                style = MaterialTheme.typography.titleLarge,
                color = onBg,
                maxLines = 1,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                centerSub,
                style = MaterialTheme.typography.labelMedium,
                color = if (selectedIndex == -1) activeColor else colors[selectedIndex % colors.size],
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun MonthlyExpenseChart(
    data: List<MonthlyTrend>,
    modifier: Modifier = Modifier,
    onMonthSelected: (MonthlyTrend) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val hasData = data.any { it.incomePaise > 0L || it.expensePaise > 0L }
    var selectedIndex by remember(data) { mutableStateOf(data.lastIndex.coerceAtLeast(0)) }
    val maxValue = data.maxOfOrNull { maxOf(it.incomePaise, it.expensePaise) }?.coerceAtLeast(1L) ?: 1L
    val animatedValues = data.map { trend ->
        val expense by animateFloatAsState(
            (trend.expensePaise.toFloat() / maxValue).coerceIn(0f, 1f),
            animationSpec = M3EMotion.spatialSlow(),
            label = "expenseBar",
        )
        val income by animateFloatAsState(
            (trend.incomePaise.toFloat() / maxValue).coerceIn(0f, 1f),
            animationSpec = M3EMotion.spatialSlow(),
            label = "incomeBar",
        )
        expense to income
    }
    val selected = data.getOrNull(selectedIndex)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(com.krtky.financetracker.R.string.home_monthly_flow),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (!hasData) {
                    stringResource(com.krtky.financetracker.R.string.home_monthly_flow_empty)
                } else {
                    selected?.let { t ->
                        val parts = t.monthKey.split("-")
                        val month = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: 1
                        val name = DateFormatSymbols().months[month - 1]
                        "$name \u00b7 Out ${t.expensePaise.inr()} \u00b7 In ${t.incomePaise.inr()}"
                    } ?: stringResource(com.krtky.financetracker.R.string.home_months_count, data.size)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        if (hasData) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LegendDot(scheme.error, "Debit")
                LegendDot(scheme.primary, "Credit")
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .pointerInput(data, hasData) {
                    detectTapGestures { offset ->
                        if (hasData && data.isNotEmpty()) {
                            val slot = size.width / data.size
                            val index = (offset.x / slot).toInt().coerceIn(data.indices)
                            selectedIndex = index
                            onMonthSelected(data[index])
                        }
                    }
                },
        ) {
            val chartHeight = size.height - 8.dp.toPx()
            val count = data.size.coerceAtLeast(1)
            val slot = size.width / count
            val pairGap = 3.dp.toPx()
            val barWidth = ((slot - pairGap) * 0.32f).coerceIn(8.dp.toPx(), 22.dp.toPx())
            val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

            drawLine(
                color = scheme.outlineVariant,
                start = Offset(0f, chartHeight),
                end = Offset(size.width, chartHeight),
                strokeWidth = 1.dp.toPx(),
            )
            for (i in 1..3) {
                val y = chartHeight * (1f - i / 4f)
                drawLine(
                    color = scheme.outlineVariant.copy(alpha = 0.35f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            data.forEachIndexed { index, _ ->
                val center = slot * index + slot / 2f
                val active = hasData && index == selectedIndex
                if (!hasData) {
                    val ghostH = chartHeight * (0.18f + (index % 3) * 0.08f)
                    drawRoundRect(
                        color = scheme.surfaceContainerHighest,
                        topLeft = Offset(center - barWidth - pairGap / 2f, chartHeight - ghostH),
                        size = Size(barWidth, ghostH),
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        color = scheme.surfaceContainerHighest.copy(alpha = 0.7f),
                        topLeft = Offset(center + pairGap / 2f, chartHeight - ghostH * 0.75f),
                        size = Size(barWidth, ghostH * 0.75f),
                        cornerRadius = corner,
                    )
                } else {
                    val expenseH = (chartHeight * animatedValues[index].first).let {
                        if (it > 0f) it.coerceAtLeast(4.dp.toPx()) else 0f
                    }
                    val incomeH = (chartHeight * animatedValues[index].second).let {
                        if (it > 0f) it.coerceAtLeast(4.dp.toPx()) else 0f
                    }
                    if (expenseH > 0f) {
                        drawRoundRect(
                            color = scheme.error.copy(alpha = if (active) 1f else 0.72f),
                            topLeft = Offset(center - barWidth - pairGap / 2f, chartHeight - expenseH),
                            size = Size(barWidth, expenseH),
                            cornerRadius = corner,
                        )
                    }
                    if (incomeH > 0f) {
                        drawRoundRect(
                            color = scheme.primary.copy(alpha = if (active) 1f else 0.72f),
                            topLeft = Offset(center + pairGap / 2f, chartHeight - incomeH),
                            size = Size(barWidth, incomeH),
                            cornerRadius = corner,
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            data.forEachIndexed { index, trend ->
                val parts = trend.monthKey.split("-")
                val month = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: 1
                val label = DateFormatSymbols().months[month - 1].take(3)
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hasData && index == selectedIndex) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
