package com.krtky.financetracker.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.ChipCarousel
import com.krtky.financetracker.ui.components.ConfirmActionSheet
import com.krtky.financetracker.ui.components.DeleteConfirmSheet
import com.krtky.financetracker.ui.components.M3LoadingIndicator
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.mapsUri
import com.krtky.financetracker.ui.viewmodel.TransactionDetailViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    id: String,
    onBack: () -> Unit,
    vm: TransactionDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { vm.load(id) }
    val txn by vm.transaction.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val funds by vm.funds.collectAsStateWithLifecycle()
    val banks by vm.bankAccounts.collectAsStateWithLifecycle()
    val shapes = MaterialTheme.shapes
    var note by remember { mutableStateOf("") }
    var counterparty by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var fundId by remember { mutableStateOf<Long?>(null) }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var channel by remember { mutableStateOf("Digital") }
    var selectedBank by remember { mutableStateOf<String?>(null) }
    var occurredAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var useCurrentLocation by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var fundExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    LaunchedEffect(txn, banks) {
        txn?.let {
            note = it.note.orEmpty()
            counterparty = it.counterparty ?: it.merchant.orEmpty()
            categoryId = it.categoryId
            fundId = it.fundId
            amount = "%.2f".format(Locale.US, it.amountPaise / 100.0)
            type = it.type
            when {
                it.isCash || it.paymentMethod.equals("Cash", true) -> {
                    channel = "Cash"
                    selectedBank = null
                }
                else -> {
                    channel = "Digital"
                    val pm = it.paymentMethod.orEmpty()
                    selectedBank = when {
                        pm.isBlank() || pm.equals("Digital", true) || pm.equals("UPI", true) -> null
                        banks.any { b -> b.equals(pm, true) } -> pm
                        else -> pm.takeIf { p -> p.isNotBlank() }
                    }
                }
            }
            occurredAt = it.occurredAt
            useCurrentLocation = false
        }
    }

    fun paymentMethod(): String = when {
        channel == "Cash" -> "Cash"
        !selectedBank.isNullOrBlank() -> selectedBank!!
        else -> "Digital"
    }

    val isDirty = remember(
        txn, note, counterparty, categoryId, fundId, amount, type, channel, selectedBank, occurredAt, useCurrentLocation,
    ) {
        val t = txn ?: return@remember false
        if (useCurrentLocation) return@remember true
        val amountPaise = amount.toDoubleOrNull()?.let { (it * 100.0).roundToLong() }
        val originalParty = t.counterparty ?: t.merchant.orEmpty()
        val originalMethod = when {
            t.isCash || t.paymentMethod.equals("Cash", true) -> "Cash"
            t.paymentMethod.isNullOrBlank() ||
                t.paymentMethod.equals("Digital", true) ||
                t.paymentMethod.equals("UPI", true) -> "Digital"
            else -> t.paymentMethod.orEmpty()
        }
        note != t.note.orEmpty() ||
            counterparty != originalParty ||
            categoryId != t.categoryId ||
            fundId != t.fundId ||
            type != t.type ||
            paymentMethod() != originalMethod ||
            amountPaise == null || amountPaise != t.amountPaise ||
            !sameCalendarDay(occurredAt, t.occurredAt)
    }

    fun requestLeave() {
        if (isDirty) showLeaveConfirm = true else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit transaction") },
                navigationIcon = {
                    IconButton(onClick = { requestLeave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        val t = txn
        if (t == null) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                M3LoadingIndicator()
            }
            return@Scaffold
        }
        BackHandler { requestLeave() }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryTabRow(selectedTabIndex = if (type == TransactionType.EXPENSE) 0 else 1) {
                Tab(
                    selected = type == TransactionType.EXPENSE,
                    onClick = { type = TransactionType.EXPENSE },
                    text = { Text("Expense") },
                )
                Tab(
                    selected = type == TransactionType.INCOME,
                    onClick = { type = TransactionType.INCOME },
                    text = { Text("Income") },
                )
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = shapes.medium,
            )

            Text("Payment method", style = MaterialTheme.typography.labelLarge)
            ChipCarousel {
                FilterChip(
                    selected = channel == "Cash",
                    onClick = { channel = "Cash"; selectedBank = null },
                    label = { Text("Cash") },
                    shape = shapes.medium,
                )
                FilterChip(
                    selected = channel == "Digital",
                    onClick = {
                        channel = "Digital"
                        if (selectedBank == null) selectedBank = banks.firstOrNull()
                    },
                    label = { Text("Digital") },
                    shape = shapes.medium,
                )
            }
            if (channel == "Digital" && banks.isNotEmpty()) {
                Text("Bank account", style = MaterialTheme.typography.labelLarge)
                ChipCarousel {
                    FilterChip(
                        selected = selectedBank == null,
                        onClick = { selectedBank = null },
                        label = { Text("Any / UPI") },
                        shape = shapes.medium,
                    )
                    banks.forEach { bank ->
                        FilterChip(
                            selected = selectedBank.equals(bank, true),
                            onClick = { selectedBank = bank },
                            label = { Text(bank) },
                            shape = shapes.medium,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = counterparty,
                onValueChange = { counterparty = it },
                label = { Text(if (type == TransactionType.INCOME) "Received from" else "Paid to") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = shapes.medium,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                    shape = shapes.medium,
                ) {
                    Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.padding(end = 8.dp))
                    Text(dateFmt.format(Date(occurredAt)))
                }
            }

            Text("Category", style = MaterialTheme.typography.labelLarge)
            ChipCarousel {
                FilterChip(
                    selected = categoryId == null,
                    onClick = { categoryId = null },
                    label = { Text("None") },
                    shape = shapes.medium,
                )
                categories.forEach { c ->
                    FilterChip(
                        selected = categoryId == c.id,
                        onClick = { categoryId = c.id },
                        label = { Text(c.name) },
                        leadingIcon = {
                            Icon(CategoryIcons.iconFor(c.icon, c.name), null, Modifier.size(18.dp))
                        },
                        shape = shapes.medium,
                    )
                }
            }

            ExposedDropdownMenuBox(expanded = fundExpanded, onExpandedChange = { fundExpanded = it }) {
                OutlinedTextField(
                    value = funds.firstOrNull { it.fund.id == fundId }?.fund?.name ?: "Fund (optional)",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(fundExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    label = { Text("Fund") },
                    shape = shapes.medium,
                )
                ExposedDropdownMenu(expanded = fundExpanded, onDismissRequest = { fundExpanded = false }) {
                    DropdownMenuItem(text = { Text("None") }, onClick = { fundId = null; fundExpanded = false })
                    funds.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.fund.name) },
                            onClick = { fundId = f.fund.id; fundExpanded = false },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = shapes.medium,
            )

            if (!t.externalRefId.isNullOrBlank()) {
                Text("Ref: ${t.externalRefId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (t.placeName != null || t.latitude != null) {
                Text(
                    "Location: ${t.placeName ?: "${t.latitude}, ${t.longitude}"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (t.latitude != null && t.longitude != null) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, mapsUri(t.latitude, t.longitude, t.placeName)),
                            )
                        },
                        shape = shapes.large,
                    ) { Text("Open in Maps") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useCurrentLocation, onCheckedChange = { useCurrentLocation = it })
                Text("Update with current location")
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    scope.launch {
                        if (vm.save(amount, type, occurredAt, paymentMethod(), categoryId, fundId, note, counterparty, useCurrentLocation)) {
                            onBack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
                enabled = amount.isNotBlank(),
            ) { Text("Save") }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = occurredAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { selected ->
                        val old = Calendar.getInstance().apply { timeInMillis = occurredAt }
                        val date = Calendar.getInstance().apply {
                            timeInMillis = selected
                            set(Calendar.HOUR_OF_DAY, old.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, old.get(Calendar.MINUTE))
                            set(Calendar.SECOND, old.get(Calendar.SECOND))
                        }
                        occurredAt = date.timeInMillis
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }

    if (showDeleteConfirm) {
        DeleteConfirmSheet(
            title = "Delete transaction?",
            message = "This transaction will be removed from your lists.",
            onDismiss = { showDeleteConfirm = false },
            onConfirmDelete = {
                scope.launch {
                    vm.delete()
                    showDeleteConfirm = false
                    onBack()
                }
            },
        )
    }
    if (showLeaveConfirm) {
        ConfirmActionSheet(
            title = "Save changes?",
            message = "You have unsaved edits on this transaction.",
            onDismiss = { showLeaveConfirm = false },
            primaryLabel = "Save",
            onPrimary = {
                scope.launch {
                    if (vm.save(amount, type, occurredAt, paymentMethod(), categoryId, fundId, note, counterparty, useCurrentLocation)) {
                        showLeaveConfirm = false
                        onBack()
                    }
                }
            },
            secondaryLabel = "Discard",
            onSecondary = {
                showLeaveConfirm = false
                onBack()
            },
            tertiaryLabel = "Keep editing",
            onTertiary = { showLeaveConfirm = false },
        )
    }
}

private fun sameCalendarDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}
