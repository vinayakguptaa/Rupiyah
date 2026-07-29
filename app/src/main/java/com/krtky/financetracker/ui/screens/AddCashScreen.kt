package com.krtky.financetracker.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.AmountNumpadSheet
import com.krtky.financetracker.ui.components.AmountRupeeField
import com.krtky.financetracker.ui.components.M3LoadingIndicator
import com.krtky.financetracker.ui.components.DatePickerSheet
import com.krtky.financetracker.ui.components.FormAccountChip
import com.krtky.financetracker.ui.components.FormCategoryChip
import com.krtky.financetracker.ui.components.FormExpandableHeader
import com.krtky.financetracker.ui.components.FormToggleRow
import com.krtky.financetracker.ui.components.FormTypeSegment
import com.krtky.financetracker.ui.components.ReceiptAttachmentField
import com.krtky.financetracker.ui.components.TimePickerSheet
import com.krtky.financetracker.ui.util.formDateFormatter
import com.krtky.financetracker.ui.util.formTimeFormatter
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.viewmodel.AddCashViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCashScreen(
    onDone: () -> Unit,
    initialAmount: String = "",
    initialType: TransactionType = TransactionType.EXPENSE,
    vm: AddCashViewModel = hiltViewModel(),
) {
    // NavHost handles predictive back (no intercepting BackHandler).
    val categories by vm.categories.collectAsStateWithLifecycle()
    val funds by vm.funds.collectAsStateWithLifecycle()
    val banks by vm.bankAccounts.collectAsStateWithLifecycle()
    val defaultPay by vm.defaultPaymentMethod.collectAsStateWithLifecycle()
    val defaultDigital by vm.defaultDigitalAccount.collectAsStateWithLifecycle()
    val accountBalances by vm.accountBalances.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    var amount by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf("") }
    var counterparty by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(initialType) }
    // Always open the amount numpad when landing on this screen; tapping amount reopens it later
    var showAmountPad by remember { mutableStateOf(true) }
    var amountPadIsEntryGate by remember { mutableStateOf(true) }
    val defaultIsCash = defaultPay.equals("Cash", true)
    val resolvedDefaultBank = remember(defaultPay, defaultDigital, banks) {
        when {
            defaultIsCash -> null
            banks.any { it.equals(defaultPay, true) } ->
                banks.first { it.equals(defaultPay, true) }
            defaultDigital.isNotBlank() && banks.any { it.equals(defaultDigital, true) } ->
                banks.first { it.equals(defaultDigital, true) }
            banks.isNotEmpty() -> banks.first()
            else -> null
        }
    }
    var channel by remember(defaultPay) {
        mutableStateOf(if (defaultIsCash) "Cash" else "Digital")
    }
    var selectedBank by remember(defaultPay, defaultDigital, banks) {
        mutableStateOf(if (defaultIsCash) null else resolvedDefaultBank)
    }
    val lastCategory by vm.lastUsedCategoryId.collectAsStateWithLifecycle()
    val lastFund by vm.lastUsedFundId.collectAsStateWithLifecycle()
    val lastPayment by vm.lastUsedPaymentMethod.collectAsStateWithLifecycle()
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var fundId by remember { mutableStateOf<Long?>(null) }
    var useLocation by remember { mutableStateOf(false) }
    var addToFund by remember { mutableStateOf(true) }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }
    var moreExpanded by remember { mutableStateOf(false) }
    var appliedLastUsed by remember { mutableStateOf(false) }
    // Date + time stored as wall-clock fields so either picker can update independently
    val nowCal = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableStateOf(nowCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(nowCal.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableStateOf(nowCal.get(Calendar.DAY_OF_MONTH)) }
    var selectedHour by remember { mutableStateOf(nowCal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(nowCal.get(Calendar.MINUTE)) }
    var selectedSecond by remember { mutableStateOf(nowCal.get(Calendar.SECOND)) }
    var selectedMillis by remember { mutableStateOf(nowCal.get(Calendar.MILLISECOND)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var paymentExpanded by remember { mutableStateOf(true) }
    var categoryExpanded by remember { mutableStateOf(true) }
    var contentVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var recommendedFundId by remember { mutableStateOf<Long?>(null) }
    val dateFmt = remember { formDateFormatter() }
    val timeFmt = remember { formTimeFormatter() }
    val displayWhen = remember(
        selectedYear, selectedMonth, selectedDay,
        selectedHour, selectedMinute, selectedSecond, selectedMillis,
    ) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonth)
            set(Calendar.DAY_OF_MONTH, selectedDay)
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
            set(Calendar.SECOND, selectedSecond)
            set(Calendar.MILLISECOND, selectedMillis)
        }.timeInMillis
    }
    val fieldShape = RoundedCornerShape(18.dp)
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
    val ctaLabel = if (type == TransactionType.EXPENSE) "Add transaction" else "Add income"
    val paymentLabel = when {
        channel == "Cash" -> "Cash"
        !selectedBank.isNullOrBlank() -> "Digital · $selectedBank"
        else -> "Digital · ${defaultDigital.ifBlank { banks.firstOrNull() ?: "UPI" }}"
    }

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    // When category changes, look up recommended fund from past usage
    LaunchedEffect(categoryId) {
        val rec = categoryId?.let { vm.recommendFundForCategory(it) }
        recommendedFundId = rec
    }

    // Prefer last-used category / fund / payment over static defaults (once)
    LaunchedEffect(lastCategory, lastFund, lastPayment, categories, funds, banks, defaultPay) {
        if (appliedLastUsed) return@LaunchedEffect
        val pay = lastPayment
        if (pay != null) {
            if (pay.equals("Cash", true)) {
                channel = "Cash"
                selectedBank = null
            } else {
                channel = "Digital"
                selectedBank = banks.firstOrNull { it.equals(pay, true) } ?: pay
            }
        }
        if (lastCategory != null && categories.any { it.id == lastCategory }) {
            categoryId = lastCategory
        }
        if (lastFund != null && funds.any { it.fund.id == lastFund }) {
            fundId = lastFund
            addToFund = true
        }
        appliedLastUsed = true
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Transaction",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(M3EMotion.effectsDefault()) +
                    slideInVertically(M3EMotion.spatialDefault()) { it / 2 },
            ) {
                Surface(
                    color = scheme.background,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                saving = true
                                val whenMs = displayWhen
                                // Cash = mode; Digital banks are accounts under digital
                                val method = when {
                                    channel == "Cash" -> "Cash"
                                    !selectedBank.isNullOrBlank() -> selectedBank!!
                                    defaultDigital.isNotBlank() -> defaultDigital
                                    banks.isNotEmpty() -> banks.first()
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
                                    receiptLocalUri = receiptUri,
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
                            containerColor = scheme.primaryContainer,
                            contentColor = scheme.onPrimaryContainer,
                            disabledContainerColor = scheme.surfaceContainerHighest,
                            disabledContentColor = scheme.onSurfaceVariant,
                        ),
                    ) {
                        if (saving) {
                            M3LoadingIndicator(size = 22.dp, strokeWidth = 3.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                ctaLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(M3EMotion.effectsDefault()) +
                slideInVertically(M3EMotion.spatialDefault()) { it / 12 } +
                scaleIn(M3EMotion.spatialDefault(), initialScale = 0.98f),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Expense | Income segmented control
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(scheme.surfaceContainerHighest, RoundedCornerShape(28.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FormTypeSegment(
                        label = "Expense",
                        selected = type == TransactionType.EXPENSE,
                        modifier = Modifier.weight(1f),
                        onClick = { type = TransactionType.EXPENSE; haptics.select() },
                    )
                    FormTypeSegment(
                        label = "Income",
                        selected = type == TransactionType.INCOME,
                        modifier = Modifier.weight(1f),
                        onClick = { type = TransactionType.INCOME; haptics.select() },
                    )
                }

                AnimatedContent(
                    targetState = type,
                    transitionSpec = {
                        (fadeIn(M3EMotion.effectsFast()) + slideInVertically(M3EMotion.spatialFast()) { it / 8 })
                            .togetherWith(fadeOut(M3EMotion.effectsFast()))
                    },
                    label = "typeFields",
                ) { currentType ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        TextField(
                            value = counterparty,
                            onValueChange = { counterparty = it },
                            placeholder = {
                                Text(
                                    if (currentType == TransactionType.EXPENSE) {
                                        "Expense name"
                                    } else {
                                        "Income source"
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = fieldShape,
                            colors = fieldColors,
                        )
                    }
                }

                // Amount — tap opens app numpad (no system keyboard)
                AmountRupeeField(
                    amount = amount,
                    onClick = {
                        haptics.select()
                        showAmountPad = true
                    },
                    shape = fieldShape,
                    containerColor = fieldBg,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    listOf("100", "500", "1000").forEach { chip ->
                        Surface(
                            onClick = {
                                haptics.select()
                                val base = amount.toDoubleOrNull() ?: 0.0
                                val add = chip.toDouble()
                                amount = if (base == 0.0) chip else {
                                    val sum = base + add
                                    if (sum == sum.toLong().toDouble()) sum.toLong().toString()
                                    else String.format(Locale.US, "%.2f", sum)
                                }
                            },
                            shape = MaterialTheme.shapes.extraLarge,
                            color = scheme.surfaceContainerHighest,
                        ) {
                            Text(
                                "+$chip",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                TextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = fieldShape,
                    colors = fieldColors,
                    minLines = 2,
                )

                ReceiptAttachmentField(
                    localUri = receiptUri,
                    onUriChange = { receiptUri = it },
                )

                // Date + Time (both pickers work)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        onClick = {
                            haptics.select()
                            showDatePicker = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = fieldShape,
                        color = fieldBg,
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = scheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    "Date",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                dateFmt.format(Date(displayWhen)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Surface(
                        onClick = {
                            haptics.select()
                            showTimePicker = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = fieldShape,
                        color = fieldBg,
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = scheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    "Time",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                timeFmt.format(Date(displayWhen)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                FormExpandableHeader(
                    title = "Payment",
                    subtitle = paymentLabel,
                    icon = Icons.Default.Payments,
                    expanded = paymentExpanded,
                    onToggle = {
                        haptics.select()
                        paymentExpanded = !paymentExpanded
                    },
                )
                AnimatedVisibility(
                    visible = paymentExpanded,
                    enter = expandVertically(M3EMotion.spatialDefault()) + fadeIn(M3EMotion.effectsDefault()),
                    exit = shrinkVertically(M3EMotion.spatialDefault()) + fadeOut(M3EMotion.effectsDefault()),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Mode",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FormAccountChip(
                                label = "Cash",
                                icon = Icons.Default.Payments,
                                selected = channel == "Cash",
                                balanceLabel = accountBalances["Cash"]?.inr(),
                                onClick = {
                                    channel = "Cash"
                                    selectedBank = null
                                    haptics.select()
                                },
                            )
                            FormAccountChip(
                                label = "Digital",
                                icon = Icons.Default.AccountBalance,
                                selected = channel == "Digital",
                                balanceLabel = null,
                                onClick = {
                                    channel = "Digital"
                                    if (selectedBank == null) {
                                        selectedBank = resolvedDefaultBank
                                    }
                                    haptics.select()
                                },
                            )
                        }

                        AnimatedVisibility(
                            visible = channel == "Digital",
                            enter = expandVertically(M3EMotion.spatialDefault()) + fadeIn(),
                            exit = shrinkVertically(M3EMotion.spatialDefault()) + fadeOut(),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Account (under Digital)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onSurfaceVariant,
                                )
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (banks.isEmpty()) {
                                        FormAccountChip(
                                            label = "UPI / any",
                                            icon = Icons.Default.AccountBalance,
                                            selected = selectedBank == null,
                                            onClick = {
                                                selectedBank = null
                                                haptics.select()
                                            },
                                        )
                                    }
                                    banks.forEach { bank ->
                                        val bal = accountBalances.entries
                                            .firstOrNull { it.key.equals(bank, true) }
                                            ?.value
                                        FormAccountChip(
                                            label = bank,
                                            icon = Icons.Default.AccountBalance,
                                            selected = selectedBank.equals(bank, true),
                                            balanceLabel = bal?.inr(),
                                            isDefault = defaultDigital.equals(bank, true),
                                            onClick = {
                                                selectedBank = bank
                                                haptics.select()
                                            },
                                        )
                                    }
                                }
                                Text(
                                    "AI auto-detects bank/wallet from email/SMS when possible; " +
                                        "otherwise your default digital account is used.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                FormExpandableHeader(
                    title = "Category",
                    subtitle = categories.firstOrNull { it.id == categoryId }?.name ?: "Select category",
                    icon = Icons.Default.Payments,
                    expanded = categoryExpanded,
                    onToggle = {
                        haptics.select()
                        categoryExpanded = !categoryExpanded
                    },
                )
                AnimatedVisibility(
                    visible = categoryExpanded,
                    enter = expandVertically(M3EMotion.spatialDefault()) + fadeIn(M3EMotion.effectsDefault()),
                    exit = shrinkVertically(M3EMotion.spatialDefault()) + fadeOut(M3EMotion.effectsDefault()),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FormCategoryChip(
                            label = "None",
                            icon = Icons.Default.Clear,
                            selected = categoryId == null,
                            onClick = {
                                categoryId = null
                                haptics.select()
                            },
                        )
                        categories.forEach { c ->
                            FormCategoryChip(
                                label = c.name,
                                icon = CategoryIcons.iconFor(c.icon, c.name),
                                selected = categoryId == c.id,
                                onClick = {
                                    categoryId = c.id
                                    haptics.select()
                                },
                            )
                        }
                    }
                }

                FormExpandableHeader(
                    title = "More",
                    subtitle = buildString {
                        val bits = mutableListOf<String>()
                        if (fundId != null) {
                            bits += funds.firstOrNull { it.fund.id == fundId }?.fund?.name ?: "Fund"
                        }
                        if (useLocation) bits += "Location"
                        append(bits.joinToString(" · ").ifBlank { "Fund, location" })
                    },
                    icon = Icons.Default.KeyboardArrowDown,
                    expanded = moreExpanded,
                    onToggle = {
                        haptics.select()
                        moreExpanded = !moreExpanded
                    },
                )
                AnimatedVisibility(
                    visible = moreExpanded,
                    enter = expandVertically(M3EMotion.spatialDefault()) + fadeIn(M3EMotion.effectsDefault()),
                    exit = shrinkVertically(M3EMotion.spatialDefault()) + fadeOut(M3EMotion.effectsDefault()),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (funds.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Fund",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurface,
                                )
                                val recFundName = recommendedFundId?.let { id ->
                                    funds.firstOrNull { it.fund.id == id }?.fund?.name
                                }
                                if (recFundName != null && fundId == null) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = scheme.tertiaryContainer,
                                    ) {
                                        Text(
                                            "Spend from $recFundName",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = scheme.onTertiaryContainer,
                                        )
                                    }
                                }
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FormCategoryChip(
                                    label = "None",
                                    icon = Icons.Default.Clear,
                                    selected = fundId == null,
                                    onClick = { fundId = null },
                                )
                                funds.forEach { f ->
                                    FormCategoryChip(
                                        label = f.fund.name,
                                        icon = Icons.Default.Payments,
                                        selected = fundId == f.fund.id,
                                        onClick = {
                                            fundId = f.fund.id
                                            addToFund = true
                                        },
                                    )
                                }
                            }
                            AnimatedVisibility(visible = type == TransactionType.INCOME && fundId != null) {
                                FormToggleRow(
                                    title = "Add to fund balance",
                                    checked = addToFund,
                                    onCheckedChange = { addToFund = it },
                                )
                            }
                        }
                        FormToggleRow(
                            title = "Attach current location",
                            checked = useLocation,
                            onCheckedChange = { useLocation = it },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showDatePicker) {
        DatePickerSheet(
            initialMillis = displayWhen,
            onDismiss = { showDatePicker = false },
            onConfirm = { y, m, d ->
                selectedYear = y
                selectedMonth = m
                selectedDay = d
                haptics.select()
            },
        )
    }

    if (showTimePicker) {
        TimePickerSheet(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m, s, ms ->
                selectedHour = h
                selectedMinute = m
                selectedSecond = s
                selectedMillis = ms
                haptics.select()
            },
        )
    }

    if (showAmountPad) {
        AmountNumpadSheet(
            initialAmount = amount,
            title = "Enter amount",
            // Entry gate: pick expense/income. Later edits on this screen: amount only.
            pickTransactionType = amountPadIsEntryGate || amount.isBlank(),
            onDismiss = {
                showAmountPad = false
                // Only exit the whole screen when the pad was the entry gate (empty launch)
                if (amountPadIsEntryGate && amount.isBlank()) onDone()
                amountPadIsEntryGate = false
            },
            onConfirmWithType = { amountText, selectedType ->
                amount = amountText
                type = selectedType
                showAmountPad = false
                amountPadIsEntryGate = false
                haptics.select()
            },
            onConfirmAmount = { amountText ->
                amount = amountText
                showAmountPad = false
                amountPadIsEntryGate = false
                haptics.select()
            },
        )
    }
}
