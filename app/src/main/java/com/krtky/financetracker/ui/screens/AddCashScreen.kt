package com.krtky.financetracker.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.ChipCarousel
import com.krtky.financetracker.ui.components.M3LoadingIndicator
import com.krtky.financetracker.ui.theme.RobotoFlex
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.viewmodel.AddCashViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCashScreen(
    onDone: () -> Unit,
    vm: AddCashViewModel = hiltViewModel(),
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val funds by vm.funds.collectAsStateWithLifecycle()
    val banks by vm.bankAccounts.collectAsStateWithLifecycle()
    val defaultPay by vm.defaultPaymentMethod.collectAsStateWithLifecycle()
    val shapes = MaterialTheme.shapes
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var counterparty by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    val defaultIsCash = defaultPay.equals("Cash", true)
    var channel by remember(defaultPay) { mutableStateOf(if (defaultIsCash) "Cash" else "Digital") }
    var selectedBank by remember(defaultPay, banks) {
        mutableStateOf(
            when {
                defaultIsCash -> null
                defaultPay.equals("Digital", true) || defaultPay.equals("UPI", true) -> null
                banks.any { it.equals(defaultPay, true) } -> defaultPay
                else -> banks.firstOrNull()
            },
        )
    }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var fundId by remember { mutableStateOf<Long?>(null) }
    var useLocation by remember { mutableStateOf(true) }
    var addToFund by remember { mutableStateOf(false) }
    var customDateMs by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "New transaction",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, color = scheme.surface) {
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            val whenMs = customDateMs?.let { dayStart ->
                                val now = Calendar.getInstance()
                                Calendar.getInstance().apply {
                                    timeInMillis = dayStart
                                    set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY))
                                    set(Calendar.MINUTE, now.get(Calendar.MINUTE))
                                    set(Calendar.SECOND, now.get(Calendar.SECOND))
                                }.timeInMillis
                            } ?: System.currentTimeMillis()
                            val method = when {
                                channel == "Cash" -> "Cash"
                                !selectedBank.isNullOrBlank() -> selectedBank!!
                                else -> "Digital"
                            }
                            val ok = vm.save(
                                amountText = amount,
                                type = type,
                                categoryId = categoryId,
                                fundId = fundId,
                                note = note,
                                counterparty = counterparty,
                                paymentMethod = method,
                                useLocation = useLocation,
                                addToFund = addToFund,
                                occurredAt = whenMs,
                            )
                            saving = false
                            if (ok) {
                                haptics.click()
                                onDone()
                            }
                        }
                    },
                    enabled = !saving && amount.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (type == TransactionType.EXPENSE) {
                            scheme.error
                        } else {
                            scheme.primary
                        },
                    ),
                ) {
                    if (saving) {
                        M3LoadingIndicator(size = 22.dp, strokeWidth = 3.dp)
                    } else {
                        Text(
                            if (type == TransactionType.EXPENSE) "Save expense" else "Save income",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(scheme.surfaceContainerHighest, RoundedCornerShape(28.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TypePill(
                    label = "Expense",
                    selected = type == TransactionType.EXPENSE,
                    selectedColor = scheme.errorContainer,
                    selectedContent = scheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                    onClick = { type = TransactionType.EXPENSE; haptics.select() },
                )
                TypePill(
                    label = "Income",
                    selected = type == TransactionType.INCOME,
                    selectedColor = scheme.primaryContainer,
                    selectedContent = scheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                    onClick = { type = TransactionType.INCOME; haptics.select() },
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.extraLarge,
                color = scheme.surfaceContainerHigh,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (type == TransactionType.EXPENSE) "You spent" else "You received",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "₹",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = RobotoFlex,
                                fontWeight = FontWeight.Black,
                            ),
                            color = if (type == TransactionType.EXPENSE) scheme.error else scheme.primary,
                        )
                        BasicTextField(
                            value = amount,
                            onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                            textStyle = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = RobotoFlex,
                                fontWeight = FontWeight.Black,
                                color = if (type == TransactionType.EXPENSE) scheme.error else scheme.primary,
                                textAlign = TextAlign.Start,
                                fontSize = 40.sp,
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            cursorBrush = SolidColor(scheme.primary),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(start = 4.dp),
                            decorationBox = { inner ->
                                if (amount.isEmpty()) {
                                    Text(
                                        "0",
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontFamily = RobotoFlex,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 40.sp,
                                        ),
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }
            }

            SectionLabel("Payment")
            ChipCarousel {
                FilterChip(
                    selected = channel == "Cash",
                    onClick = { channel = "Cash"; selectedBank = null },
                    label = { Text("Cash", fontWeight = FontWeight.SemiBold) },
                    shape = shapes.large,
                )
                FilterChip(
                    selected = channel == "Digital",
                    onClick = {
                        channel = "Digital"
                        if (selectedBank == null) selectedBank = banks.firstOrNull()
                    },
                    label = { Text("Digital", fontWeight = FontWeight.SemiBold) },
                    shape = shapes.large,
                )
            }
            if (channel == "Digital" && banks.isNotEmpty()) {
                SectionLabel("Account")
                ChipCarousel {
                    FilterChip(
                        selected = selectedBank == null,
                        onClick = { selectedBank = null },
                        label = { Text("Any / UPI") },
                        shape = shapes.large,
                    )
                    banks.forEach { bank ->
                        FilterChip(
                            selected = selectedBank.equals(bank, true),
                            onClick = { selectedBank = bank },
                            label = { Text(bank) },
                            shape = shapes.large,
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
                shape = shapes.large,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                    shape = shapes.large,
                    color = scheme.surfaceContainerHigh,
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, tint = scheme.primary)
                        Text(
                            customDateMs?.let { dateFmt.format(Date(it)) } ?: "Today",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                if (customDateMs != null) {
                    IconButton(onClick = { customDateMs = null }) {
                        Icon(Icons.Default.Clear, contentDescription = "Use today")
                    }
                }
            }

            SectionLabel("Category")
            ChipCarousel {
                FilterChip(
                    selected = categoryId == null,
                    onClick = { categoryId = null },
                    label = { Text("None") },
                    shape = shapes.large,
                )
                categories.forEach { c ->
                    FilterChip(
                        selected = categoryId == c.id,
                        onClick = { categoryId = c.id },
                        label = { Text(c.name) },
                        leadingIcon = {
                            Icon(CategoryIcons.iconFor(c.icon, c.name), null, Modifier.size(18.dp))
                        },
                        shape = shapes.large,
                    )
                }
            }

            SectionLabel("Fund")
            ChipCarousel {
                FilterChip(
                    selected = fundId == null,
                    onClick = { fundId = null },
                    label = { Text("None") },
                    shape = shapes.large,
                )
                funds.forEach { f ->
                    FilterChip(
                        selected = fundId == f.fund.id,
                        onClick = { fundId = f.fund.id },
                        label = { Text(f.fund.name) },
                        shape = shapes.large,
                    )
                }
            }
            if (type == TransactionType.INCOME && fundId != null) {
                SettingsToggleRow(
                    title = "Add to fund balance",
                    checked = addToFund,
                    onCheckedChange = { addToFund = it },
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.large,
                minLines = 2,
            )
            SettingsToggleRow(
                title = "Attach current location",
                checked = useLocation,
                onCheckedChange = { useLocation = it },
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = customDateMs ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        customDateMs = pickerState.selectedDateMillis
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun TypePill(
    label: String,
    selected: Boolean,
    selectedColor: androidx.compose.ui.graphics.Color,
    selectedContent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) selectedColor else androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Text(
            label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) selectedContent else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
