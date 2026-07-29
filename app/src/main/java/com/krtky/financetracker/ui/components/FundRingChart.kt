package com.krtky.financetracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.util.inr

/**
 * Compact ratio ring (e.g. Funds tab hero “% spent”).
 * Prefer this over one-off Canvas in screens.
 */
@Composable
fun SpendRatioRing(
    ratio: Float,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    stroke: Dp = 12.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    centerContent: @Composable () -> Unit = {},
) {
    val progress by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = M3EMotion.spatialSlow(),
        label = "spendRatioRing",
    )
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            val arc = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            if (progress > 0.005f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arc,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        centerContent()
    }
}

@Composable
fun FundRingChart(
    name: String,
    balancePaise: Long,
    creditedPaise: Long,
    debitedPaise: Long,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    /** Fixed envelope limit; when > 0, progress is spent/limit (not lifetime credits). */
    budgetPaise: Long = 0L,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val spentColor = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    // Limit stays fixed (budget/opening); remaining = cash balance (refunds refill)
    val limit = when {
        budgetPaise > 0L -> budgetPaise
        else -> maxOf(balancePaise.coerceAtLeast(0L), 1L)
    }
    val remaining = balancePaise.coerceAtLeast(0L)
    val target = if (limit > 0) {
        (1f - (remaining.toFloat() / limit.toFloat())).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = M3EMotion.spatialSlow(),
        label = "fundRing",
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            Canvas(Modifier.size(size)) {
                val stroke = size.toPx() * 0.12f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (progress > 0f) {
                    drawArc(
                        color = spentColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(remaining.inr(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface, textAlign = TextAlign.Center)
                Text("left", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(
            "${remaining.inr()} left of ${limit.inr()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
