package com.krtky.financetracker.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.krtky.financetracker.R
import java.io.File

/**
 * Camera / gallery picker for an optional receipt photo on transaction forms.
 *
 * [localUri] is a temporary content/file URI while editing; parent persists via [ReceiptStore].
 */
@Composable
fun ReceiptAttachmentField(
    localUri: Uri?,
    onUriChange: (Uri?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCamera by remember { mutableStateOf(false) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        pendingCamera = false
        if (success) {
            cameraFile?.let { onUriChange(Uri.fromFile(it)) }
        }
    }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onUriChange(uri)
    }

    val requestCameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera(context, onFile = { cameraFile = it }, takePicture = takePicture)
        pendingCamera = false
    }

    fun openCamera() {
        val needPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) != PackageManager.PERMISSION_GRANTED
        if (needPermission) {
            pendingCamera = true
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        } else {
            launchCamera(context, onFile = { cameraFile = it }, takePicture = takePicture)
        }
    }

    LaunchedEffect(pendingCamera) {
        // no-op; keeps state for recomposition after permission
    }

    val scheme = MaterialTheme.colorScheme
    val previewBitmap = remember(localUri) {
        localUri?.let { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: when (uri.scheme) {
                    "file" -> uri.path?.let { BitmapFactory.decodeFile(it) }
                    else -> null
                }
            }.getOrNull()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.receipt_label),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        if (previewBitmap != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = scheme.surfaceContainerHighest,
            ) {
                Row(
                    Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_receipt_preview),
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        stringResource(R.string.receipt_attached),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (enabled) {
                        IconButton(onClick = { onUriChange(null) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_remove_receipt),
                            )
                        }
                    }
                }
            }
        } else if (enabled) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { openCamera() },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.receipt_camera),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                TextButton(
                    onClick = {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.receipt_gallery),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.AddAPhoto,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.receipt_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun launchCamera(
    context: android.content.Context,
    onFile: (File) -> Unit,
    takePicture: androidx.activity.result.ActivityResultLauncher<Uri>,
) {
    val dir = File(context.cacheDir, "camera").also { it.mkdirs() }
    val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
    onFile(file)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    takePicture.launch(uri)
}
