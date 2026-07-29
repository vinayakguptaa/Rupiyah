package com.krtky.financetracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.theme.RupiyahTheme

@Composable
fun TransactionCard(
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
    iconContainerColor: Color? = null,
    iconTint: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
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
    val container = iconContainerColor ?: scheme.surfaceContainerHighest
    val tint = iconTint ?: if (iconContainerColor != null) {
        if (container.luminance() > 0.55f) Color.Black else Color.White
    } else {
        scheme.onSurfaceVariant
    }
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
        color = if (selected) scheme.primaryContainer
        else scheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.dp, scheme.primary) else null,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
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

@Preview(showBackground = true, name = "EmptyState")
@Composable
private fun EmptyStatePreview() {
    RupiyahTheme {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            title = "No transactions yet",
            body = "Add your first expense or income to see it here.",
            actionLabel = "Add transaction",
            onAction = {},
        )
    }
}

@Preview(showBackground = true, name = "TransactionCard")
@Composable
private fun TransactionCardPreview() {
    RupiyahTheme {
        TransactionCard(
            title = "Swiggy",
            subtitle = "Today · Food · UPI",
            amount = "−₹420.00",
            amountColor = MaterialTheme.colorScheme.error,
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            onClick = {},
        )
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = M3EMotion.spatialFast(),
        label = "settingsRowScale",
    )
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
                .scale(scale)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                )
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

/** Section heading above a [GroupedCard] in Settings / onboarding. */
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

/**
 * Rounded surface used to group related rows (settings lists, form sections).
 */
@Composable
fun GroupedCard(
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
