package com.krtky.financetracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.util.PieSliceLayout
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.inrCompact
import kotlin.math.sqrt

/**
 * Category spend pie: multi-segment ring with gaps; optional tap-to-select.
 */
@Composable
fun CategoryInteractivePieChart(
    categorySpends: List<CategorySpend>,
    totalExpense: Long,
    goalLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    incomePaise: Long = 0L,
    interactive: Boolean = true,
    compact: Boolean = false,
    centerTitle: String? = null,
    hidden: Boolean = false,
    onCenterClick: (() -> Unit)? = null,
    onCategorySelected: (CategorySpend?) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val trackColor = scheme.surfaceContainerHighest
    val activeColor = scheme.primary
    val onBg = scheme.onSurface
    val muted = scheme.onSurfaceVariant
    val density = LocalDensity.current
    val defaultLabel = centerTitle ?: stringResource(com.krtky.financetracker.R.string.home_spent_label)

    BoxWithConstraints(
        modifier = if (modifier == Modifier) Modifier.size(size) else modifier,
        contentAlignment = Alignment.Center,
    ) {
        val chartSize = minOf(
            if (maxWidth.isFinite) maxWidth else size,
            if (maxHeight.isFinite) maxHeight else size,
        ).let { measured ->
            if (measured.value <= 0f || !measured.value.isFinite()) size else measured
        }
        val chartSizePx = with(density) { chartSize.toPx() }
        val tight = compact || chartSize < 148.dp
        val strokeWidthPx = with(density) {
            val minStroke = if (tight) 7.dp else 12.dp
            val maxStroke = if (tight) 13.dp else 18.dp
            (chartSize * if (tight) 0.12f else 0.11f).toPx()
                .coerceIn(minStroke.toPx(), maxStroke.toPx())
        }
        val gapDeg = run {
            val n = categorySpends.count { it.totalPaise > 0 }.coerceAtLeast(1)
            val radius = (chartSizePx - strokeWidthPx) / 2f
            val circumference = (2.0 * Math.PI * radius).toFloat()
            val gapPx = with(density) { if (tight) 3.dp.toPx() else 4.dp.toPx() }
            val minGap = if (tight) 4f else 5f
            val maxGap = if (tight) 10f else 12f
            val fromStroke = ((gapPx / circumference) * 360f).coerceIn(minGap, maxGap)
            // Keep total gaps under a quarter of the ring so small slices still fit.
            val cap = 80f / n
            minOf(fromStroke, cap)
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
        val sliceArcs = remember(validSpends, finalTotal, gapDeg, strokeWidthPx, chartSizePx) {
            if (validSpends.isEmpty() || finalTotal <= 0L) {
                emptyList()
            } else {
                val n = validSpends.size
                val usable = (360f - gapDeg * n).coerceAtLeast(1f)
                // Angular width of the round cap ≈ stroke / radius, so the floor
                // is at least a visible "bead" on the ring (~3% of a full pie).
                val capDeg = ((strokeWidthPx / (chartSizePx / 2f).coerceAtLeast(1f))
                    * (180f / Math.PI.toFloat())).coerceIn(8f, 16f)
                val minSweep = minOf(12f, capDeg, usable / n)
                PieSliceLayout.arcs(
                    weights = validSpends.map { it.totalPaise.toFloat() },
                    gapDeg = gapDeg,
                    minSweepDeg = minSweep,
                )
            }
        }
        val overallProgress = if (incomePaise > 0) {
            (finalTotal.toFloat() / incomePaise.toFloat()).coerceIn(0f, 1f)
        } else if (finalTotal > 0) 1f else 0f
        val animatedOverall by animateFloatAsState(
            targetValue = overallProgress,
            animationSpec = M3EMotion.spatialSlow(),
            label = "m3CircularProgress",
        )

        val tapModifier = if (interactive || onCenterClick != null) {
            Modifier.pointerInput(validSpends, sliceArcs, chartSizePx, interactive) {
                detectTapGestures { offset ->
                    val center = Offset(chartSizePx / 2f, chartSizePx / 2f)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val dist = sqrt(dx * dx + dy * dy)
                    val outerRadius = chartSizePx / 2f
                    val innerRadius = outerRadius - strokeWidthPx
                    val holeSlop = if (tight) 4f else 8f

                    if (dist < innerRadius - holeSlop) {
                        if (onCenterClick != null && selectedIndex == -1) {
                            onCenterClick()
                        } else {
                            selectedIndex = -1
                            onCategorySelected(null)
                        }
                    } else if (
                        interactive &&
                        dist <= outerRadius + 12f &&
                        sliceArcs.isNotEmpty()
                    ) {
                        var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        angle = (angle + 90f + 360f) % 360f
                        var foundIndex = -1
                        sliceArcs.forEachIndexed { index, arc ->
                            val start = (arc.startDeg + 90f + 360f) % 360f
                            val end = start + arc.sweepDeg + gapDeg
                            val hit = if (end <= 360f) {
                                angle >= start && angle <= end
                            } else {
                                angle >= start || angle <= (end - 360f)
                            }
                            if (hit) foundIndex = index
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
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .size(chartSize)
                .then(tapModifier),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
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
                    sliceArcs.forEachIndexed { index, arc ->
                        if (arc.sweepDeg > 0.2f) {
                            drawArc(
                                color = colors[index % colors.size],
                                startAngle = arc.startDeg,
                                sweepAngle = arc.sweepDeg,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = stroke,
                            )
                        }
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

            val holePad = chartSize * if (tight) 0.24f else 0.18f
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(holePad),
            ) {
                val centerLabel = if (selectedIndex == -1) {
                    defaultLabel
                } else {
                    validSpends[selectedIndex].categoryName
                }
                val amountPaise = if (selectedIndex == -1) {
                    finalTotal
                } else {
                    validSpends[selectedIndex].totalPaise
                }
                val centerValue = when {
                    hidden -> "••••"
                    tight -> amountPaise.inrCompact()
                    else -> amountPaise.inr()
                }
                val centerSub = if (selectedIndex == -1) {
                    goalLabel
                } else {
                    val pct = (validSpends[selectedIndex].totalPaise * 100f / maxOf(finalTotal, 1L)).toInt()
                    "$pct%"
                }
                val labelStyle = if (tight) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelMedium
                }
                val valueStyle = when {
                    chartSize < 120.dp -> MaterialTheme.typography.titleSmall
                    tight -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.titleLarge
                }

                Text(
                    centerLabel,
                    style = labelStyle,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    centerValue,
                    style = valueStyle,
                    color = onBg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    centerSub,
                    style = labelStyle,
                    color = if (selectedIndex == -1) activeColor else colors[selectedIndex % colors.size],
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
