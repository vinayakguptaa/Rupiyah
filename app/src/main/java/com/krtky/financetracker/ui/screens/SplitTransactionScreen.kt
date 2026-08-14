package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.FundBalance
import com.krtky.financetracker.domain.model.Money
import com.krtky.financetracker.domain.model.SplitPart
import com.krtky.financetracker.domain.model.SplitRules
import com.krtky.financetracker.ui.components.M3LoadingIndicator
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.viewmodel.SplitTransactionViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private data class SplitDraft(
    val localId: String,
    val amountText: String = "",
    val categoryId: Long? = null,
    val counterparty: String = "",
    val fundId: Long? = null,
    val note: String = "",
)

/**
 * Full-screen split editor for an existing transaction (nav destination).
 */
@Composable
fun SplitTransactionScreen(
    id: String,
    onBack: () -> Unit,
    vm: SplitTransactionViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { vm.load(id) }
    val txn by vm.transaction.collectAsStateWithLifecycle()
    val splits by vm.splits.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val funds by vm.funds.collectAsStateWithLifecycle()
    val parent = txn
    val parentAmount by vm.parentAmountPaise.collectAsStateWithLifecycle()

    if (parent == null) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            M3LoadingIndicator()
        }
        return
    }

    if (parent.isSelfTransfer() || parent.isTabTransfer()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.split_section_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Text(
                stringResource(R.string.split_not_for_transfer),
                modifier = Modifier.padding(padding).padding(16.dp),
            )
        }
        return
    }

    SplitEditorScreen(
        parentAmountPaise = parentAmount,
        initialSplits = splits,
        categories = categories,
        funds = funds,
        onBack = onBack,
        allowClear = splits.isNotEmpty(),
        onSave = { lines -> vm.saveSplit(lines) },
        onClear = { vm.mergeSplitGroup() },
    )
}

/**
 * Full-screen split editor (also used inline from Add Transaction).
 * [onSave] may only update local draft (Add) or persist (Detail).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitEditorScreen(
    parentAmountPaise: Long,
    initialSplits: List<SplitPart>,
    categories: List<Category>,
    funds: List<FundBalance>,
    onBack: () -> Unit,
    allowClear: Boolean = false,
    saveLabel: String = "Save splits",
    onSave: suspend (List<SplitPart>) -> Result<Unit>,
    onClear: (suspend () -> Result<Unit>)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val drafts = remember(initialSplits, parentAmountPaise) {
        mutableStateListOf<SplitDraft>().apply {
            if (initialSplits.isEmpty()) {
                add(SplitDraft(localId = UUID.randomUUID().toString()))
                add(SplitDraft(localId = UUID.randomUUID().toString()))
            } else {
                initialSplits.forEach { s ->
                    add(
                        SplitDraft(
                            localId = UUID.randomUUID().toString(),
                            amountText = if (s.amountPaise > 0) {
                                "%.2f".format(Locale.US, s.amountPaise / 100.0)
                            } else {
                                ""
                            },
                            categoryId = s.categoryId,
                            counterparty = s.counterparty.orEmpty(),
                            fundId = s.fundId,
                            note = s.note.orEmpty(),
                        ),
                    )
                }
            }
        }
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val amounts = drafts.map { Money.fromRupeesString(it.amountText)?.paise ?: 0L }
    val remaining = SplitRules.remainingPaise(parentAmountPaise, amounts)
    val validation = SplitRules.validateSum(parentAmountPaise, amounts)

    val fieldBg = scheme.surfaceContainerHigh
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = fieldBg,
        unfocusedContainerColor = fieldBg,
        disabledContainerColor = fieldBg,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        cursorColor = scheme.primary,
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        focusedPlaceholderColor = scheme.onSurfaceVariant.copy(alpha = 0.55f),
        unfocusedPlaceholderColor = scheme.onSurfaceVariant.copy(alpha = 0.55f),
    )

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.split_section_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Normal,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onBackground,
                    navigationIconContentColor = scheme.onBackground,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = scheme.background,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error)
                    }
                    if (error == null && validation != null) {
                        Text(
                            validation,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val err = SplitRules.validateSum(parentAmountPaise, amounts)
                                if (err != null) {
                                    error = err
                                    return@launch
                                }
                                saving = true
                                error = null
                                val lines = drafts.mapIndexed { index, d ->
                                    SplitPart(
                                        amountPaise = amounts[index],
                                        categoryId = d.categoryId,
                                        counterparty = d.counterparty.ifBlank { null },
                                        fundId = d.fundId,
                                        note = d.note.ifBlank { null },
                                    )
                                }
                                val result = onSave(lines)
                                saving = false
                                if (result.isSuccess) {
                                    haptics.click()
                                    onBack()
                                } else {
                                    error = result.exceptionOrNull()?.message ?: "Could not save splits"
                                }
                            }
                        },
                        enabled = !saving && validation == null && amounts.all { it > 0L },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.primaryContainer,
                            contentColor = scheme.onPrimaryContainer,
                            disabledContainerColor = scheme.surfaceContainerHighest,
                            disabledContentColor = scheme.onSurfaceVariant,
                        ),
                    ) {
                        if (saving) {
                            M3LoadingIndicator(size = 22.dp, strokeWidth = 3.dp)
                        } else {
                            Icon(Icons.Default.Save, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(saveLabel, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (allowClear && onClear != null) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    saving = true
                                    val result = onClear()
                                    saving = false
                                    if (result.isSuccess) {
                                        haptics.click()
                                        onBack()
                                    } else {
                                        error = result.exceptionOrNull()?.message ?: "Could not clear"
                                    }
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            Text("Remove all splits")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Parent ${parentAmountPaise.inr()} · lines must sum exactly",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (remaining == 0L && amounts.all { it > 0L }) {
                    scheme.tertiaryContainer
                } else {
                    scheme.surfaceContainerHigh
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Remaining",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        remaining.inr(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            remaining == 0L -> scheme.onTertiaryContainer
                            remaining < 0L -> scheme.error
                            else -> scheme.onSurface
                        },
                    )
                }
            }

            drafts.forEachIndexed { index, draft ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = scheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Line ${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurfaceVariant,
                            )
                            if (drafts.size > 1) {
                                IconButton(
                                    onClick = { drafts.removeAt(index) },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove line",
                                        tint = scheme.error,
                                    )
                                }
                            }
                        }
                        TextField(
                            value = draft.amountText,
                            onValueChange = { text ->
                                drafts[index] = draft.copy(
                                    amountText = text.filter { c ->
                                        c.isDigit() || c == '.' || c == ','
                                    },
                                )
                            },
                            placeholder = { Text("Amount") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            prefix = { Text("₹ ") },
                        )
                        TextField(
                            value = draft.counterparty,
                            onValueChange = { drafts[index] = draft.copy(counterparty = it) },
                            placeholder = { Text("Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                        )
                        Text(
                            "Category",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = draft.categoryId == null,
                                onClick = { drafts[index] = draft.copy(categoryId = null) },
                                label = { Text("None") },
                            )
                            categories.forEach { cat ->
                                FilterChip(
                                    selected = draft.categoryId == cat.id,
                                    onClick = { drafts[index] = draft.copy(categoryId = cat.id) },
                                    label = { Text(cat.name) },
                                )
                            }
                        }
                        if (funds.isNotEmpty()) {
                            Text(
                                "Tab",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FilterChip(
                                    selected = draft.fundId == null,
                                    onClick = { drafts[index] = draft.copy(fundId = null) },
                                    label = { Text("None") },
                                )
                                funds.forEach { fb ->
                                    FilterChip(
                                        selected = draft.fundId == fb.fund.id,
                                        onClick = {
                                            drafts[index] = draft.copy(fundId = fb.fund.id)
                                        },
                                        label = { Text(fb.fund.name) },
                                    )
                                }
                            }
                        }
                        TextField(
                            value = draft.note,
                            onValueChange = { drafts[index] = draft.copy(note = it) },
                            placeholder = { Text("Note (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                        )
                    }
                }
            }

            TextButton(
                onClick = {
                    drafts.add(SplitDraft(localId = UUID.randomUUID().toString()))
                },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add line")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
