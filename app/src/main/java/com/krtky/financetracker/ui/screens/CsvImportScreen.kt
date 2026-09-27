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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.krtky.financetracker.ui.util.formatDate
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.viewmodel.CsvImportStep
import com.krtky.financetracker.ui.viewmodel.CsvImportViewModel
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.style.TextOverflow
import com.krtky.financetracker.ui.util.formatDateTime
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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

    var activeFilter by rememberSaveable { mutableStateOf(PreviewFilter.ALL) }
    var inspectingRowId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        StackTopBar(
            title = when (state.step) {
                CsvImportStep.PICK_ACCOUNT -> "Import statement"
                CsvImportStep.PICK_FILE -> "Choose statement"
                CsvImportStep.PREVIEW -> "Preview import"
                CsvImportStep.DONE -> "Import done"
            },
            subtitle = when (state.step) {
                CsvImportStep.PICK_ACCOUNT -> "Pick the account this file belongs to"
                CsvImportStep.PICK_FILE ->
                    accounts.firstOrNull { it.id == state.selectedAccountId }?.name
                        ?: "CSV, PDF, or Excel statement"
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
                        "Import a bank or wallet statement (CSV, PDF, or Excel spreadsheet). Typical columns: Date, Description, Debit, Credit (or Amount + Type), Ref.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Surface(
                        onClick = {
                            haptics.select()
                            picker.launch(
                                arrayOf(
                                    "text/*",
                                    "text/csv",
                                    "application/csv",
                                    "application/pdf",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "*/*",
                                )
                            )
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
                                    "Choose statement file",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onPrimaryContainer,
                                )
                                Text(
                                    "CSV, PDF, or Excel spreadsheet",
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
                val newCount = remember(preview.rows) {
                    preview.rows.count { it.confidence == DedupeConfidence.LOW && !it.isSplitMatch }
                }
                val dupCount = remember(preview.rows) {
                    preview.rows.count { it.confidence != DedupeConfidence.LOW || it.isSplitMatch }
                }

                val filteredRows = remember(preview.rows, activeFilter) {
                    when (activeFilter) {
                        PreviewFilter.ALL -> preview.rows
                        PreviewFilter.NEW -> preview.rows.filter { it.confidence == DedupeConfidence.LOW && !it.isSplitMatch }
                        PreviewFilter.DUPLICATE -> preview.rows.filter { it.confidence != DedupeConfidence.LOW || it.isSplitMatch }
                        PreviewFilter.TO_IMPORT -> preview.rows.filter {
                            it.action == ImportRowAction.IMPORT || it.action == ImportRowAction.IMPORT_ANYWAY
                        }
                        PreviewFilter.SKIPPED -> preview.rows.filter { it.action == ImportRowAction.SKIP_MERGE }
                    }
                }

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
                        Spacer(Modifier.height(6.dp))
                        // Overall filter chips row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = activeFilter == PreviewFilter.ALL,
                                onClick = {
                                    haptics.select()
                                    activeFilter = PreviewFilter.ALL
                                },
                                label = { Text("All (${preview.rows.size})") },
                            )
                            FilterChip(
                                selected = activeFilter == PreviewFilter.NEW,
                                onClick = {
                                    haptics.select()
                                    activeFilter = PreviewFilter.NEW
                                },
                                label = { Text("New ($newCount)") },
                            )
                            FilterChip(
                                selected = activeFilter == PreviewFilter.DUPLICATE,
                                onClick = {
                                    haptics.select()
                                    activeFilter = PreviewFilter.DUPLICATE
                                },
                                label = { Text("Duplicates / Splits ($dupCount)") },
                            )
                            FilterChip(
                                selected = activeFilter == PreviewFilter.TO_IMPORT,
                                onClick = {
                                    haptics.select()
                                    activeFilter = PreviewFilter.TO_IMPORT
                                },
                                label = { Text("To Import ($toImport)") },
                            )
                            FilterChip(
                                selected = activeFilter == PreviewFilter.SKIPPED,
                                onClick = {
                                    haptics.select()
                                    activeFilter = PreviewFilter.SKIPPED
                                },
                                label = { Text("Skipped ($toSkip)") },
                            )
                        }
                    }
                    if (filteredRows.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = scheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                            ) {
                                Text(
                                    "No transactions match the \"${activeFilter.label}\" filter.",
                                    Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(filteredRows, key = { it.id }) { row ->
                            PreviewRowCard(
                                row = row,
                                dateLabel = row.parsed.occurredAt.formatDate(),
                                onAction = { action ->
                                    haptics.select()
                                    vm.setRowAction(row.id, action)
                                },
                                onInspect = {
                                    haptics.select()
                                    inspectingRowId = row.id
                                },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }

                // If user selected a row to inspect
                val inspectingRow = inspectingRowId?.let { id ->
                    preview.rows.firstOrNull { it.id == id }
                }
                if (inspectingRow != null) {
                    ImportRowDetailSheet(
                        row = inspectingRow,
                        accountName = accounts.firstOrNull { it.id == state.selectedAccountId }?.name ?: "Account",
                        onDismiss = { inspectingRowId = null },
                        onSetAction = { action ->
                            haptics.select()
                            vm.setRowAction(inspectingRow.id, action)
                        },
                    )
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

        if (state.requiresPassword) {
            var passwordInput by rememberSaveable { mutableStateOf("") }
            var passwordVisible by rememberSaveable { mutableStateOf(false) }

            androidx.compose.material3.AlertDialog(
                onDismissRequest = { vm.dismissPasswordDialog() },
                title = {
                    Text("Password Protected")
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            state.passwordError ?: "This statement is encrypted with a password.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.passwordError != null) scheme.error else scheme.onSurfaceVariant,
                        )
                        Text(
                            "Common bank passwords: Date of Birth (DDMMYYYY), Customer ID, or PAN number in uppercase.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Statement Password") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.Check else Icons.Default.UploadFile,
                                        contentDescription = "Toggle password visibility",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            haptics.select()
                            vm.submitPassword(passwordInput)
                        },
                        enabled = passwordInput.isNotBlank(),
                    ) {
                        Text("Unlock")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vm.dismissPasswordDialog() }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun PreviewRowCard(
    row: ImportPreviewRow,
    dateLabel: String,
    onAction: (ImportRowAction) -> Unit,
    onInspect: () -> Unit,
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
        onClick = onInspect,
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (row.isSplitMatch) {
                        Surface(shape = RoundedCornerShape(8.dp), color = scheme.tertiaryContainer) {
                            Text(
                                "Split Match ⚡",
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onTertiaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
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
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val partyDisplay = p.counterparty?.takeIf {
                    val lower = it.trim().lowercase(Locale.US)
                    lower !in setOf("dr", "cr", "debit", "credit", "d", "c")
                } ?: p.description ?: "—"
                Text(
                    partyDisplay,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (row.categoryName != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = scheme.surfaceContainerHighest,
                ) {
                    Row(
                        Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = com.krtky.financetracker.ui.util.CategoryIcons.iconFor(row.categoryIcon, row.categoryName),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = row.categoryColor?.let { com.krtky.financetracker.ui.util.categoryColor(it) } ?: scheme.primary,
                        )
                        Text(
                            text = row.categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        modifier = Modifier.height(32.dp),
                    )
                    FilterChip(
                        selected = row.action == ImportRowAction.SKIP_MERGE,
                        onClick = { onAction(ImportRowAction.SKIP_MERGE) },
                        label = {
                            Text(if (row.confidence == DedupeConfidence.LOW && !row.isSplitMatch) "Skip" else "Skip / merge")
                        },
                        modifier = Modifier.height(32.dp),
                    )
                }

                TextButton(
                    onClick = onInspect,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (row.isSplitMatch) "Verify (${row.matchedParts.size})" else "Verify",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportRowDetailSheet(
    row: ImportPreviewRow,
    accountName: String,
    onDismiss: () -> Unit,
    onSetAction: (ImportRowAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val p = row.parsed
    val dir = if (p.type == TransactionType.DEBIT) "Debit" else "Credit"
    var nearbyScope by rememberSaveable { mutableStateOf(0) } // 0 = Same Day, 1 = ±1 Day

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Verify Statement Line",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        accountName,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (row.isSplitMatch) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = scheme.tertiaryContainer,
                    ) {
                        Text(
                            "Split Match ⚡",
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    val (confText, confBg, confFg) = when (row.confidence) {
                        DedupeConfidence.HIGH -> Triple("Duplicate", scheme.tertiaryContainer, scheme.onTertiaryContainer)
                        DedupeConfidence.MEDIUM -> Triple("Maybe duplicate", scheme.secondaryContainer, scheme.onSecondaryContainer)
                        DedupeConfidence.LOW -> Triple("New", scheme.primaryContainer, scheme.onPrimaryContainer)
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = confBg) {
                        Text(
                            confText,
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = confFg,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Section 1: Statement Entry
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "STATEMENT ENTRY (FROM FILE)",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = scheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$dir · ${p.amountPaise.inr()}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (p.type == TransactionType.DEBIT) scheme.error else scheme.primary,
                            )
                            Text(
                                p.occurredAt.formatDate(),
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        val partyDisplay = p.counterparty?.takeIf {
                            val lower = it.trim().lowercase(Locale.US)
                            lower !in setOf("dr", "cr", "debit", "credit", "d", "c")
                        } ?: p.description ?: "—"
                        Text(
                            partyDisplay,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        if (!p.externalRef.isNullOrBlank()) {
                            Text(
                                "Ref: ${p.externalRef}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        if (row.categoryName != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = scheme.surfaceContainerHighest,
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = com.krtky.financetracker.ui.util.CategoryIcons.iconFor(row.categoryIcon, row.categoryName),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = row.categoryColor?.let { com.krtky.financetracker.ui.util.categoryColor(it) } ?: scheme.primary,
                                    )
                                    Text(
                                        "Category: ${row.categoryName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = scheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Matched App Activity
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (row.isSplitMatch) {
                        "MATCHED SPLIT IN RUPIYAH (${row.matchedParts.size} PARTS)"
                    } else {
                        "MATCHED IN RUPIYAH"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                if (row.matchedParts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.matchedParts.forEach { part ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = scheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            part.categoryName ?: "Uncategorized",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        val partsDesc = listOfNotNull(
                                            part.tabName?.let { "Tab: $it" },
                                            part.note?.takeIf { it.isNotBlank() },
                                            part.displayName()?.takeIf { it.isNotBlank() },
                                        ).joinToString(" · ")
                                        if (partsDesc.isNotBlank()) {
                                            Text(
                                                partsDesc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = scheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        part.amountPaise.inr(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                } else if (!row.matchedSummary.isNullOrBlank() || row.matchReason.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.matchedSummary?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            if (row.matchReason.isNotBlank()) {
                                Text(
                                    row.matchReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "No duplicate match detected in Rupiyah.",
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Section 3: Bank Account Activity Around Date
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "ACCOUNT ACTIVITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = nearbyScope == 0,
                            onClick = { nearbyScope = 0 },
                            label = { Text("Same Day (${row.sameDayTransactions.size})") },
                            modifier = Modifier.height(30.dp),
                        )
                        FilterChip(
                            selected = nearbyScope == 1,
                            onClick = { nearbyScope = 1 },
                            label = { Text("±1 Day (${row.nearbyTransactions.size})") },
                            modifier = Modifier.height(30.dp),
                        )
                    }
                }

                val currentList = if (nearbyScope == 0) row.sameDayTransactions else row.nearbyTransactions
                if (currentList.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (nearbyScope == 0) "No transactions logged in this account on the same day."
                            else "No transactions logged in this account within ±1 day.",
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        currentList.forEach { tx ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = scheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.padding(10.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            tx.displayName() ?: tx.categoryName ?: tx.rawDescription ?: "Transaction",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            tx.occurredAt.formatDateTime(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        (if (tx.type == TransactionType.DEBIT) "- " else "+ ") + tx.amountPaise.inr(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (tx.type == TransactionType.DEBIT) scheme.error else scheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: Action Buttons
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onSetAction(ImportRowAction.SKIP_MERGE)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Skip (Logged)")
                }
                Button(
                    onClick = {
                        onSetAction(ImportRowAction.IMPORT)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Import as New")
                }
            }
        }
    }
}

private enum class PreviewFilter(val label: String) {
    ALL("All"),
    NEW("New"),
    DUPLICATE("Duplicates / Splits"),
    TO_IMPORT("To Import"),
    SKIPPED("Skipped"),
}
