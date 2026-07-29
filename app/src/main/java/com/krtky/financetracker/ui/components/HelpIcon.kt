package com.krtky.financetracker.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Compact info control that opens a dialog with the given help text.
 * Prefer this over embedding long “How to” copy in settings panels.
 */
@Composable
fun HelpIcon(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentDescription: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(
        onClick = { open = true },
        modifier = modifier.size(36.dp),
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = contentDescription ?: "Info: $title",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Text(
                    message,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("Got it") }
            },
        )
    }
}

/** Section title row with optional trailing [HelpIcon]. */
@Composable
fun SettingsTitleWithHelp(
    title: String,
    helpTitle: String? = null,
    helpMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleLarge,
            color = scheme.primary,
        )
        if (helpTitle != null && helpMessage != null) {
            HelpIcon(
                title = helpTitle,
                message = helpMessage,
                tint = scheme.primary.copy(alpha = 0.85f),
            )
        }
    }
}
