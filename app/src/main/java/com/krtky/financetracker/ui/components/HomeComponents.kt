package com.krtky.financetracker.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.domain.model.FundBalance
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.theme.RupiyahTheme
import com.krtky.financetracker.ui.util.inr
import kotlin.math.abs

/**
 * Home hero card: large balance, hide-toggle, Income | Expense with month-over-month %.
 */
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
    incomeChangePct: Float? = null,
    expenseChangePct: Float? = null,
    lastMonthIncomeLabel: String? = null,
    lastMonthExpenseLabel: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val cardBg = scheme.primaryContainer
    val onCard = scheme.onPrimaryContainer
    val muted = onCard.copy(alpha = 0.78f)
    val incomeUp = (incomeChangePct ?: 0f) >= 0f
    val expenseUp = (expenseChangePct ?: 0f) >= 0f
    var showPeriodInfo by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = cardBg,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                color = muted,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (hidden) "\u20b9  \u2022\u2022\u2022\u2022" else balance,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onCard,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showPeriodInfo = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "About this period",
                            tint = muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = onToggleHidden,
                        modifier = Modifier
                            .size(40.dp)
                            .semantics {
                                contentDescription = if (hidden) "Show balance" else "Hide balance"
                            },
                    ) {
                        Icon(
                            if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = onCard.copy(alpha = 0.85f),
                        )
                    }
                }
            }
            if (showPeriodInfo) {
                AlertDialog(
                    onDismissRequest = { showPeriodInfo = false },
                    title = { Text("This period") },
                    text = {
                        Text(
                            buildString {
                                append(monthLabel.ifBlank { "Current month" })
                                append(". Income and expense compare to the previous month.")
                                if (lastMonthIncomeLabel != null || lastMonthExpenseLabel != null) {
                                    append("\n\n")
                                    lastMonthIncomeLabel?.let { append("Last month income: $it\n") }
                                    lastMonthExpenseLabel?.let { append("Last month expense: $it") }
                                }
                            }.trim(),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showPeriodInfo = false }) {
                            Text("Got it")
                        }
                    },
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeroMetricColumn(
                    label = incomeLabel,
                    value = if (hidden) "\u20b9 \u2022\u2022\u2022\u2022" else incomeValue,
                    changePct = incomeChangePct,
                    changeUpIsGood = true,
                    isUp = incomeUp,
                    comparedLabel = lastMonthIncomeLabel?.let {
                        if (hidden) "Compared to \u20b9\u2022\u2022\u2022\u2022 last month"
                        else "Compared to $it last month"
                    },
                    onCard = onCard,
                    muted = muted,
                    modifier = Modifier.weight(1f),
                )
                HeroMetricColumn(
                    label = expenseLabel,
                    value = if (hidden) "\u20b9 \u2022\u2022\u2022\u2022" else expenseValue,
                    changePct = expenseChangePct,
                    changeUpIsGood = false,
                    isUp = expenseUp,
                    comparedLabel = lastMonthExpenseLabel?.let {
                        if (hidden) "Compared to \u20b9\u2022\u2022\u2022\u2022 last month"
                        else "Compared to $it last month"
                    },
                    onCard = onCard,
                    muted = muted,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroMetricColumn(
    label: String,
    value: String,
    changePct: Float?,
    changeUpIsGood: Boolean,
    isUp: Boolean,
    comparedLabel: String?,
    onCard: Color,
    muted: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = onCard,
                maxLines = 1,
            )
            if (changePct != null) {
                val good = if (changeUpIsGood) isUp else !isUp
                val cardIsLight = onCard.luminance() < 0.5f
                val tint = when {
                    good && cardIsLight -> Color(0xFF0B6B45)
                    good && !cardIsLight -> Color(0xFF8EE8C0)
                    !good && cardIsLight -> Color(0xFFB3261E)
                    else -> Color(0xFFFFB4AB)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        if (isUp) Icons.AutoMirrored.Filled.TrendingUp
                        else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "${if (isUp) "\u2191" else "\u2193"}${abs(changePct).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tint,
                    )
                }
            }
        }
        if (comparedLabel != null) {
            Text(
                comparedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = muted.copy(alpha = 0.85f),
                maxLines = 2,
            )
        }
    }
}

/** Funds summary using M3 Expressive LinearWavyProgressIndicator. */
@Composable
fun FundsWaveSummary(
    funds: List<FundBalance>,
    hidden: Boolean,
    onOpenFunds: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val netOpen = funds.sumOf { it.balancePaise }
    val openCount = funds.count { it.balancePaise != 0L }

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
                    stringResource(com.krtky.financetracker.R.string.home_funds_remaining),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (hidden) "\u2022\u2022\u2022\u2022" else netOpen.inr(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (netOpen != 0L) scheme.primary else scheme.onSurfaceVariant,
                )
            }
            if (funds.isEmpty()) {
                Text(
                    stringResource(com.krtky.financetracker.R.string.home_no_funds_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                funds.take(5).forEach { fb ->
                    val youOweThem = fb.youOweThem()
                    val settled = fb.isSettled()
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
                                if (hidden) {
                                    "\u2022\u2022\u2022\u2022"
                                } else {
                                    when {
                                        youOweThem -> "you owe ${(-fb.balancePaise).inr()}"
                                        settled -> "settled"
                                        else -> "they owe ${fb.balancePaise.inr()}"
                                    }
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (youOweThem) scheme.error else scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (openCount == 0 && funds.isNotEmpty()) {
                    Text(
                        "All tabs settled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Home overview grid tile — neutral surface; [accent] only tints the icon.
 * Values stay onSurface so the grid stays simple (Tonal Spot–friendly).
 */
@Composable
fun OverviewTile(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    valueColor: Color? = null,
    minHeight: Dp = 132.dp,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = M3EMotion.spatialFast(),
        label = "tileScale",
    )
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(minHeight)
            .scale(scale),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerHigh,
        interactionSource = interaction,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = valueColor ?: scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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

@Preview(showBackground = true, name = "BalanceHeroCard")
@Composable
private fun BalanceHeroCardPreview() {
    RupiyahTheme {
        BalanceHeroCard(
            title = "Net this month",
            balance = "₹12,400.00",
            monthLabel = "Income − expenses · July",
            incomeLabel = "Income",
            incomeValue = "₹40,000.00",
            expenseLabel = "Expense",
            expenseValue = "₹27,600.00",
            hidden = false,
            incomeChangePct = 4.2f,
            expenseChangePct = -1.5f,
            onToggleHidden = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, name = "OverviewTile")
@Composable
private fun OverviewTilePreview() {
    RupiyahTheme {
        OverviewTile(
            title = "Funds",
            value = "3",
            subtitle = "₹8,200 total",
            icon = Icons.Default.Savings,
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun shimmerBrush(base: Color, highlight: Color): Brush {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "shimmerX",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x - 200f, 0f),
        end = Offset(x + 200f, 200f),
    )
}
