package com.krtky.financetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ConfirmActionSheet(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiaryLabel: String = "Cancel",
    onTertiary: (() -> Unit)? = null,
    destructivePrimary: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (destructivePrimary) {
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text(primaryLabel) }
                if (secondaryLabel != null && onSecondary != null) {
                    FilledTonalButton(
                        onClick = onSecondary,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) { Text(secondaryLabel) }
                }
                OutlinedButton(
                    onClick = onTertiary ?: onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) { Text(tertiaryLabel) }
            } else {
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) { Text(primaryLabel) }
                if (secondaryLabel != null && onSecondary != null) {
                    FilledTonalButton(
                        onClick = onSecondary,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) { Text(secondaryLabel) }
                }
                TextButton(
                    onClick = onTertiary ?: onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(tertiaryLabel) }
            }
        }
    }
}

@Composable
fun DeleteConfirmSheet(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
    deleteLabel: String = "Delete",
) {
    ConfirmActionSheet(
        title = title,
        message = message,
        onDismiss = onDismiss,
        primaryLabel = deleteLabel,
        onPrimary = onConfirmDelete,
        destructivePrimary = true,
        tertiaryLabel = "Cancel",
    )
}
