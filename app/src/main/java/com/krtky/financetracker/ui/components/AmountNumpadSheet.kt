package com.krtky.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * App-owned number keyboard for ₹ amounts (bottom sheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountNumpadSheet(
    onDismiss: () -> Unit,
    initialAmount: String = "",
    title: String = "Enter amount",
    onConfirmAmount: (amountText: String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amount by remember(initialAmount) { mutableStateOf(sanitizeAmountInput(initialAmount)) }

    val canConfirm = amount.isNotBlank() &&
        amount != "." &&
        (amount.toDoubleOrNull() ?: 0.0) > 0.0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = scheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    "₹",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 36.sp,
                    ),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(
                    text = formatAmountDisplay(amount),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 48.sp,
                        letterSpacing = (-1).sp,
                    ),
                    color = if (amount.isBlank()) {
                        scheme.onSurfaceVariant.copy(alpha = 0.35f)
                    } else {
                        scheme.onSurface
                    },
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(12.dp))

            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf(".", "0", "⌫"),
            )
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                keys.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { key ->
                            NumpadKey(
                                label = key,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    amount = applyNumpadKey(amount, key)
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            NumpadActionButton(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Check,
                label = "Done",
                enabled = canConfirm,
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirmAmount(normalizeAmount(amount))
                },
            )
        }
    }
}

/**
 * Tappable ₹ amount field that opens the app numpad (no system keyboard).
 */
@Composable
fun AmountRupeeField(
    amount: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    placeholder: String = "0.00",
    amountStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
    ),
    symbolStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
    ),
    contentPaddingHorizontal: Dp = 16.dp,
    contentPaddingVertical: Dp = 18.dp,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = contentPaddingHorizontal, vertical = contentPaddingVertical),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "₹",
                style = symbolStyle,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = amount.ifBlank { placeholder },
                style = amountStyle,
                color = if (amount.isBlank()) {
                    scheme.onSurfaceVariant.copy(alpha = 0.55f)
                } else {
                    scheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun NumpadActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = if (enabled) containerColor else scheme.surfaceContainerHighest,
        contentColor = if (enabled) contentColor else scheme.onSurfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NumpadKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .aspectRatio(1.65f)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerHighest)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (label == "⌫") {
            Icon(
                Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Backspace",
                tint = scheme.onSurface,
                modifier = Modifier.size(26.dp),
            )
        } else {
            Text(
                label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
        }
    }
}

/** Digits / decimal rules for rupee amounts (max 2 fraction digits). */
internal fun applyNumpadKey(current: String, key: String): String {
    return when (key) {
        "⌫" -> current.dropLast(1)
        "." -> when {
            current.contains('.') -> current
            current.isEmpty() -> "0."
            else -> "$current."
        }
        else -> {
            if (current == "0" && key != ".") return key
            val dot = current.indexOf('.')
            if (dot >= 0 && current.length - dot > 2) return current
            val intPart = if (dot >= 0) current.substring(0, dot) else current
            if (dot < 0 && intPart.length >= 9) return current
            current + key
        }
    }
}

internal fun sanitizeAmountInput(raw: String): String {
    if (raw.isBlank()) return ""
    val filtered = buildString {
        var seenDot = false
        for (c in raw) {
            when {
                c.isDigit() -> append(c)
                c == '.' && !seenDot -> {
                    append('.')
                    seenDot = true
                }
            }
        }
    }
    return filtered
}

internal fun formatAmountDisplay(amount: String): String {
    if (amount.isBlank()) return "0"
    return amount
}

internal fun normalizeAmount(amount: String): String {
    val t = amount.trimEnd('.')
    if (t.isBlank() || t == ".") return ""
    val d = t.toDoubleOrNull() ?: return t
    return if (d == d.toLong().toDouble()) {
        d.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')
    }
}
