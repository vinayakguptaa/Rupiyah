package com.krtky.financetracker.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.data.importcsv.DedupeConfidence
import com.krtky.financetracker.data.repository.ImportPreviewRow
import com.krtky.financetracker.data.repository.ImportRowAction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.M3LoadingIndicator
import com.krtky.financetracker.ui.components.chrome.StackTopBar
import com.krtky.financetracker.ui.theme.Dimens
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.viewmodel.CsvImportStep
import com.krtky.financetracker.ui.viewmodel.CsvImportViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CsvImportScreen(
    onBack: () -> Unit,
    onDone: () -> Unit = onBack,
    initialAccountId: Long? = null,
    vm: CsvImportViewModel = hiltViewModel(),
) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    // Prefill account when opened from Accounts row
    androidx.compose.runtime.LaunchedEffect(initialAccountId, accounts) {
        if (initialAccountId != null &&
            state.selectedAccountId == null &&
            accounts.any { it.id == initialAccountId }
        ) {
            vm.selectAccount(initialAccountId)
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            }
        }.getOrNull()
        // Persist read permission for the session
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        vm.loadFile(uri, name)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        StackTopBar(
            title = when (state.step) {
                CsvImportStep.PICK_ACCOUNT -> "Import statement"
                CsvImportStep.PICK_FILE -> "Choose CSV"
                CsvImportStep.PREVIEW -> "Preview import"
                CsvImportStep.DONE -> "Import done"
            },
            subtitle = when (state.step) {
                CsvImportStep.PICK_ACCOUNT -> "Pick the account this file belongs to"
                CsvImportStep.PICK_FILE ->
                    accounts.firstOrNull { it.id == state.selectedAccountId }?.name
                        ?: "CSV from your bank or wallet"
                CsvImportStep.PREVIEW -> state.fileName ?: "Review rows"
                CsvImportStep.DONE -> "Added to Activity · classify if needed"
            },
            onBack = {
                when (state.step) {
                    CsvImportStep.PICK_ACCOUNT -> onBack()
                    CsvImportStep.PICK_FILE -> vm.backToAccount()
                    CsvImportStep.PREVIEW -> vm.backToFile()
                    CsvImportStep.DONE -> onDone()
                }
            },
            modifier = Modifier.padding(horizontal = Dimens.ScreenHorizontal),
        )

        if (state.loading) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                M3LoadingIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Working…", color = scheme.onSurfaceVariant)
            }
            return
        }

        state.error?.let { err ->
            Surface(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.ScreenHorizontal)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = scheme.errorContainer,
            ) {
                Text(
                    err,
                    Modifier.padding(12.dp),
                    color = scheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        when (state.step) {
            CsvImportStep.PICK_ACCOUNT -> {
                LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = Dimens.ScreenHorizontal,
                        vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "Statements are imported into one account. Active accounts only — archive others in Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    items(accounts, key = { it.id }) { acc ->
                        Surface(
                            onClick = {
                                haptics.select()
                                vm.selectAccount(acc.id)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = scheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.AccountBalance, null, tint = scheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        acc.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        acc.kind.name.lowercase()
                                            .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (accounts.isEmpty()) {
                        item {
                            Text(
                                "No accounts yet. Add a bank in Settings → Bank accounts first.",
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            CsvImportStep.PICK_FILE -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimens.ScreenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "Export a CSV from your bank app or net banking. Typical columns: Date, Description, Debit, Credit (or Amount + Type), Ref.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Surface(
                        onClick = {
                            haptics.select()
                            picker.launch(arrayOf("text/*", "text/csv", "application/csv", "*/*"))
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = scheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    "Choose CSV file",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onPrimaryContainer,
                                )
                                Text(
                                    "Opens your file picker",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                    TextButton(onClick = { vm.backToAccount() }) {
                        Text("Change account")
                    }
                }
            }

            CsvImportStep.PREVIEW -> {
                val preview = state.preview
                if (preview == null) return
                val toImport = preview.rows.count {
                    it.action == ImportRowAction.IMPORT || it.action == ImportRowAction.IMPORT_ANYWAY
                }
                val toSkip = preview.rows.size - toImport

                LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = Dimens.ScreenHorizontal,
                        vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    item {
                        Text(
                            "${preview.rows.size} rows · ${preview.presetName}" +
                                if (preview.skippedLines > 0) " · ${preview.skippedLines} lines skipped" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        if (preview.parseErrors.isNotEmpty()) {
                            Text(
                                preview.parseErrors.take(3).joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.error,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Will import $toImport · skip/merge $toSkip",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { vm.importAllNew() }) {
                                Text("Import uncertain rows too")
                            }
                        }
                    }
                    items(preview.rows, key = { it.id }) { row ->
                        PreviewRowCard(
                            row = row,
                            dateLabel = dateFmt.format(Date(row.parsed.occurredAt)),
                            onAction = { action ->
                                haptics.select()
                                vm.setRowAction(row.id, action)
                            },
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
                Button(
                    onClick = {
                        haptics.click()
                        vm.commit()
                    },
                    enabled = toImport > 0 || toSkip > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.ScreenHorizontal)
                        .padding(bottom = 12.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                ) {
                    Text("Import $toImport transactions", fontWeight = FontWeight.Bold)
                }
            }

            CsvImportStep.DONE -> {
                val r = state.result
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimens.ScreenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(24.dp))
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        "Import complete",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (r != null) {
                        Text(
                            "Imported ${r.imported} · enriched ${r.merged} · skipped ${r.skipped}" +
                                if (r.failed > 0) " · failed ${r.failed}" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "New rows without a category sit in the classify queue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                    ) { Text("Done") }
                    OutlinedButton(
                        onClick = {
                            vm.reset()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                    ) { Text("Import another file") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewRowCard(
    row: ImportPreviewRow,
    dateLabel: String,
    onAction: (ImportRowAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val p = row.parsed
    val dir = if (p.type == TransactionType.DEBIT) "Debit" else "Credit"
    val confLabel = when (row.confidence) {
        DedupeConfidence.HIGH -> "Duplicate"
        DedupeConfidence.MEDIUM -> "Maybe duplicate"
        DedupeConfidence.LOW -> "New"
    }
    val confColor = when (row.confidence) {
        DedupeConfidence.HIGH -> scheme.tertiaryContainer
        DedupeConfidence.MEDIUM -> scheme.secondaryContainer
        DedupeConfidence.LOW -> scheme.primaryContainer
    }
    val confOn = when (row.confidence) {
        DedupeConfidence.HIGH -> scheme.onTertiaryContainer
        DedupeConfidence.MEDIUM -> scheme.onSecondaryContainer
        DedupeConfidence.LOW -> scheme.onPrimaryContainer
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$dir · ${p.amountPaise.inr()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(shape = RoundedCornerShape(8.dp), color = confColor) {
                    Text(
                        confLabel,
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = confOn,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(dateLabel, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(
                p.counterparty ?: p.description ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            if (row.matchReason.isNotBlank() && row.confidence != DedupeConfidence.LOW) {
                Text(
                    row.matchReason + (row.matchedSummary?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = row.action == ImportRowAction.IMPORT ||
                        row.action == ImportRowAction.IMPORT_ANYWAY,
                    onClick = {
                        onAction(
                            if (row.confidence == DedupeConfidence.MEDIUM) {
                                ImportRowAction.IMPORT_ANYWAY
                            } else {
                                ImportRowAction.IMPORT
                            },
                        )
                    },
                    label = { Text("Import") },
                )
                FilterChip(
                    selected = row.action == ImportRowAction.SKIP_MERGE,
                    onClick = { onAction(ImportRowAction.SKIP_MERGE) },
                    label = {
                        Text(if (row.confidence == DedupeConfidence.LOW) "Skip" else "Skip / merge")
                    },
                )
            }
        }
    }
}
