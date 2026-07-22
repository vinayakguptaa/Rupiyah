package com.krtky.financetracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.sqrt
import com.krtky.financetracker.domain.model.CategorySpend
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.util.inr
import kotlin.math.PI
import kotlin.math.sin

@Composable
    fun HeroSpendRing(
        spentLabel: String,
        spentValue: String,
        goalLabel: String,
        progress: Float,
        modifier: Modifier = Modifier,
        size: Dp = 168.dp,
    ) {
        val track = MaterialTheme.colorScheme.surfaceContainerHighest
        val progressColor = MaterialTheme.colorScheme.primary
        val onBg = MaterialTheme.colorScheme.onBackground
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val animatedProgress by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = M3EMotion.spatialSlow(),
            label = "ringProgress",
        )

        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
                Canvas(Modifier.size(size)) {
                    val stroke = size.toPx() * 0.11f
                    val inset = stroke / 2f
                    val arc = Size(this.size.width - stroke, this.size.height - stroke)
                    val topLeft = Offset(inset, inset)
                    drawArc(
                        color = track,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arc,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (animatedProgress > 0.005f) {
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arc,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(size * 0.68f)
                        .background(progressColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("This month", style = MaterialTheme.typography.labelSmall, color = progressColor)
                        Text("$spentValue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = onBg)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(spentLabel, style = MaterialTheme.typography.labelMedium, color = muted)
            Text(goalLabel, style = MaterialTheme.typography.labelSmall, color = progressColor)
        }
    }

/**
 * M3 circular progress style (track + active indicator, round caps).
 * Multi-category segments with gaps; tap to filter.
 * @see https://m3.material.io/components/progress-indicators/overview
 */
@Composable
fun CategoryInteractivePieChart(
    categorySpends: List<CategorySpend>,
    totalExpense: Long,
    goalLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    /** Optional income for overall spend progress when no category selected. */
    incomePaise: Long = 0L,
    onCategorySelected: (CategorySpend?) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    // High-contrast track vs active (M3 loading/progress style)
    val trackColor = scheme.surfaceContainerHighest
    val activeColor = scheme.primary
    val onBg = scheme.onSurface
    val muted = scheme.onSurfaceVariant
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { (size * 0.11f).toPx().coerceIn(12.dp.toPx(), 18.dp.toPx()) }
    // Visible gap ≈ 2–3px after round caps (gap must clear stroke ends)
    val gapDeg = run {
        val radius = (with(density) { size.toPx() } - strokeWidthPx) / 2f
        val circumference = (2.0 * Math.PI * radius).toFloat()
        val gapPx = strokeWidthPx + with(density) { 3.dp.toPx() }
        ((gapPx / circumference) * 360f).coerceIn(8f, 22f)
    }

    var selectedIndex by remember { mutableStateOf(-1) }

    // Distinct high-chroma segment colors (not low-contrast containers)
    val colors = listOf(
        scheme.primary,
        scheme.tertiary,
        scheme.error,
        scheme.secondary,
        Color(0xFF1E88E5),
        Color(0xFF43A047),
        Color(0xFFFB8C00),
        Color(0xFF8E24AA),
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

            // M3 track (inactive track)
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
                // Single determinate indicator like M3 CircularProgressIndicator
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
                // Category ring with M3 gaps between segments
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
                // Focused category as single M3 determinate arc
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
            val centerLabel = if (selectedIndex == -1) "Spent" else validSpends[selectedIndex].categoryName
            val centerValue = if (selectedIndex == -1) {
                com.krtky.financetracker.domain.model.Money(finalTotal).formatInr()
            } else {
                com.krtky.financetracker.domain.model.Money(validSpends[selectedIndex].totalPaise).formatInr()
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
                overflow = TextOverflow.Ellipsis,
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

/** One row inside a grouped settings card (no outer chrome). */
@Composable
fun SettingsGroupRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    showDivider: Boolean = false,
) {
    Column(modifier.fillMaxWidth()) {
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 72.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background(iconContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MetricPill(
    title: String,
    value: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = M3EMotion.spatialFast(),
        label = "pillScale",
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        onClick = onClick,
                    )
                } else Modifier
            ),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(contentColor.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = contentColor.copy(alpha = 0.85f))
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun PillActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = M3EMotion.spatialFast(),
        label = "actionScale",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp).scale(scale),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        interactionSource = interaction,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = contentColor, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun WaveSectionHeader(title: String, modifier: Modifier = Modifier) {
    val waveColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WaveLine(Modifier.weight(1f), waveColor)
        Text(
            title,
            modifier = Modifier.padding(horizontal = 14.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WaveLine(Modifier.weight(1f), waveColor)
    }
}

@Composable
private fun WaveLine(modifier: Modifier, color: Color) {
    Canvas(modifier.height(12.dp).fillMaxWidth()) {
        val path = Path()
        val amp = size.height * 0.35f
        val mid = size.height / 2f
        path.moveTo(0f, mid)
        val step = size.width / 24f
        var x = 0f
        var i = 0
        while (x <= size.width) {
            path.lineTo(x, mid + (sin(i * PI / 2.0) * amp).toFloat())
            x += step
            i++
        }
        drawPath(path, color.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun ActivityTxnCard(
    title: String,
    subtitle: String,
    amount: String,
    amountColor: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = M3EMotion.effectsDefault(),
        label = "cardAlpha",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = M3EMotion.spatialDefault(),
        label = "cardOffset",
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .graphicsLayer {
            this.alpha = alpha
            translationY = offsetY
        },
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = amountColor,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
fun OutlinePillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = M3EMotion.spatialFast(),
        label = "outlineScale",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp).scale(scale),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        interactionSource = interaction,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Material 3 NavigationBar (M3E pill indicator on selected item). */
@Composable
fun HealthBottomBar(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        Triple("home", "Home", Icons.Default.Home),
        Triple("transactions", "Activity", Icons.AutoMirrored.Filled.List),
        Triple("funds", "Funds", Icons.Default.AccountBalanceWallet),
        Triple("settings", "Settings", Icons.Default.Settings),
    )
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        items.forEach { (route, label, icon) ->
            val isSelected = selected == route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(route) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/** M3 card hero: headline value + LinearWavyProgressIndicator + supporting metrics. */
@Composable
fun BalanceHeroCard(
    title: String,
    balance: String,
    monthLabel: String,
    incomeLabel: String,
    incomeValue: String,
    expenseLabel: String,
    expenseValue: String,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
    remainingProgress: Float = 0.5f,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = M3EMotion.spatialFast(),
        label = "heroScale",
    )
    val scheme = MaterialTheme.colorScheme
    val progress by animateFloatAsState(
        targetValue = remainingProgress.coerceIn(0f, 1f),
        animationSpec = M3EMotion.spatialSlow(),
        label = "heroWave",
    )
    Surface(
        modifier = modifier.fillMaxWidth().scale(scale),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.primaryContainer,
        onClick = onToggleHidden,
        interactionSource = interaction,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onPrimaryContainer,
                )
                Text(
                    if (hidden) "Show" else "Hide",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.semantics {
                        contentDescription = if (hidden) "Show balance" else "Hide balance"
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (hidden) "••••••" else balance,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Normal,
                color = scheme.onPrimaryContainer,
                maxLines = 1,
            )
            Spacer(Modifier.height(16.dp))
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WavyProgressIndicatorDefaults.LinearContainerHeight),
                color = scheme.onPrimaryContainer,
                trackColor = scheme.onPrimaryContainer.copy(alpha = 0.24f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                monthLabel,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        incomeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Text(
                        if (hidden) "••••" else incomeValue,
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onPrimaryContainer,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        expenseLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Text(
                        if (hidden) "••••" else expenseValue,
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

/** Funds summary using M3 Expressive LinearWavyProgressIndicator. */
@Composable
fun FundsWaveSummary(
    funds: List<com.krtky.financetracker.domain.model.FundBalance>,
    hidden: Boolean,
    onOpenFunds: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val totalBal = funds.sumOf { it.balancePaise.coerceAtLeast(0L) }
    val totalCred = funds.sumOf { maxOf(it.creditedPaise, it.balancePaise, 1L) }
    val overall = if (totalCred > 0) (totalBal.toFloat() / totalCred.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        onClick = onOpenFunds,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Funds remaining",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (hidden) "••••" else totalBal.inr(),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.primary,
                )
            }
            LinearWavyProgressIndicator(
                progress = { overall },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WavyProgressIndicatorDefaults.LinearContainerHeight),
                color = scheme.primary,
                trackColor = scheme.secondaryContainer,
            )
            if (funds.isEmpty()) {
                Text(
                    "No funds yet — tap to add envelopes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                funds.take(5).forEach { fb ->
                    val denom = maxOf(fb.creditedPaise, fb.balancePaise, 1L).toFloat()
                    val prog = (fb.balancePaise.coerceAtLeast(0L).toFloat() / denom).coerceIn(0f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                fb.fund.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (hidden) "••••" else fb.balancePaise.inr(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        LinearWavyProgressIndicator(
                            progress = { prog },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(WavyProgressIndicatorDefaults.LinearContainerHeight),
                            color = scheme.tertiary,
                            trackColor = scheme.surfaceContainerHighest,
                        )
                    }
                }
            }
        }
    }
}

/** Google Health / Paisa overview tile. */
@Composable
fun OverviewTile(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = M3EMotion.spatialFast(),
        label = "tileScale",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().scale(scale),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = interaction,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(accent.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Google Health settings section label. */
@Composable
fun SettingsSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Music/Health settings row with circular icon badge. */
@Composable
fun SettingsNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = M3EMotion.spatialFast(),
        label = "settingsRow",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().scale(scale),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = interaction,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).background(iconContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun SoftPanel(
    modifier: Modifier = Modifier,
    padded: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.padding(
                horizontal = if (padded) 16.dp else 0.dp,
                vertical = if (padded) 14.dp else 2.dp,
            ),
            verticalArrangement = if (padded) Arrangement.spacedBy(12.dp) else Arrangement.Top,
            content = { content() },
        )
    }
}

@Composable
fun MonthlyExpenseChart(
    data: List<com.krtky.financetracker.domain.model.MonthlyTrend>,
    modifier: Modifier = Modifier,
    onMonthSelected: (com.krtky.financetracker.domain.model.MonthlyTrend) -> Unit = {},
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
                "Monthly flow",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (!hasData) {
                    "Add income or expenses to build your trend"
                } else {
                    selected?.let { t ->
                        val parts = t.monthKey.split("-")
                        val month = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: 1
                        val name = java.text.DateFormatSymbols().months[month - 1]
                        "$name · Out ${com.krtky.financetracker.domain.model.Money(t.expensePaise).formatInr()} · In ${com.krtky.financetracker.domain.model.Money(t.incomePaise).formatInr()}"
                    } ?: "Last ${data.size} months"
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
                LegendDot(scheme.error, "Expense")
                LegendDot(scheme.primary, "Income")
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
            val corner = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)

            // M3-style baseline + light tracks
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
                    // Ghost placeholder bars (empty state) — low contrast, fixed height
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
                val label = java.text.DateFormatSymbols().months[month - 1].take(3)
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

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(scheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = scheme.onSecondaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = scheme.onSurface,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.FilledTonalButton(
                onClick = onAction,
                shape = MaterialTheme.shapes.large,
            ) { Text(actionLabel) }
        }
    }
}

@Composable
fun HomeShimmerSkeleton(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val brush = shimmerBrush(scheme.surfaceContainerHighest, scheme.surfaceContainerLow)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(brush, MaterialTheme.shapes.extraLarge),
        )
        repeat(3) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(brush, MaterialTheme.shapes.large),
            )
        }
    }
}

@Composable
private fun shimmerBrush(base: Color, highlight: Color): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x - 200f, 0f),
        end = Offset(x + 200f, 200f),
    )
}
