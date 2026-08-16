package com.krtky.financetracker.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.R

/**
 * View-only receipt image preview shown on the Detail view.
 *
 * Decodes a bitmap from [receiptUri] via [Context.getContentResolver] or
 * [File] fallback.  Renders nothing when the bitmap cannot be decoded.
 *
 * Extracted from `TransactionDetailView.kt` so the same preview can be
 * reused from other destinations if needed.
 */
@Composable
fun ReceiptPreview(
    receiptUri: Uri?,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val preview = remember(receiptUri) {
        receiptUri?.let { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: uri.path?.let { BitmapFactory.decodeFile(it) }
            }.getOrNull()
        }
    }
    if (preview != null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = scheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.receipt_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_receipt_preview),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}
