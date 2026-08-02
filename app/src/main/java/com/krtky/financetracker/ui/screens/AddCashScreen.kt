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
import com.krtky.financetracker.domain.model.Money
import com.krtky.financetracker.domain.model.TransactionSplit
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
    initialType: TransactionType = TransactionType.DEBIT,
    vm: AddCashViewModel = hiltViewModel(),
) {
    // NavHost handles predictive back (no intercepting BackHandler).
    val categories by vm.categories.collectAsStateWithLifecycle()
    val funds by vm.funds.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val defaultPay by vm.defaultPaymentMethod.collectAsStateWithLifecycle()
    val defaultDigital by vm.defaultDigitalAccount.collectAsStateWithLifecycle()
    val accountBalances by vm.accountBalances.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    var amount by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf("") }
    var counterparty by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(initialType) }
    var isSelfTransfer by remember { mutableStateOf(false) }
    var fromAccountId by remember { mutableStateOf<Long?>(null) }
    var toAccountId by remember { mutableStateOf<Long?>(null) }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    // Always open the amount numpad when landing on this screen; tapping amount reopens it later
    var showAmountPad by remember { mutableStateOf(true) }
    var amountPadIsEntryGate by remember { mutableStateOf(true) }
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
    /** Draft splits composed on Add; saved with the parent on submit. */
    var draftSplits by remember { mutableStateOf<List<TransactionSplit>>(emptyList()) }
    /** Full-screen split editor (not a sheet). */
    var editingSplits by remember { mutableStateOf(false) }
    // Default account: last used → default pay/digital → Cash → first account
    LaunchedEffect(accounts, defaultPay, defaultDigital, lastPayment) {
        if (selectedAccountId != null) return@LaunchedEffect
        if (accounts.isEmpty()) return@LaunchedEffect
        fun match(name: String?) =
            name?.takeIf { it.isNotBlank() }?.let { n ->
                accounts.firstOrNull { it.name.equals(n, true) }?.id
            }
        selectedAccountId = match(lastPayment)
            ?: match(defaultPay)
            ?: match(defaultDigital)
            ?: accounts.firstOrNull { it.kind.name == "CASH" }?.id
            ?: accounts.first().id
    }
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
    val ctaLabel = when {
        isSelfTransfer -> "Transfer between accounts"
        draftSplits.isNotEmpty() && type == TransactionType.DEBIT -> "Add debit (split)"
        draftSplits.isNotEmpty() && type == TransactionType.CREDIT -> "Add credit (split)"
        type == TransactionType.DEBIT -> "Add debit"
        else -> "Add credit"
    }

    // Self-transfer cannot use splits
    LaunchedEffect(isSelfTransfer) {
        if (isSelfTransfer) {
            draftSplits = emptyList()
            editingSplits = false
        }
    }

    // Seed self-transfer account picks once accounts load
    LaunchedEffect(accounts) {
        if (fromAccountId == null && accounts.isNotEmpty()) {
            fromAccountId = accounts.first().id
        }
        if (toAccountId == null && accounts.size >= 2) {
            toAccountId = accounts.getOrNull(1)?.id ?: accounts.first().id
        }
        // Drop selection if account was archived (no longer in list)
        if (selectedAccountId != null && accounts.none { it.id == selectedAccountId }) {
            selectedAccountId = null
        }
        if (fromAccountId != null && accounts.none { it.id == fromAccountId }) {
            fromAccountId = accounts.firstOrNull()?.id
        }
        if (toAccountId != null && accounts.none { it.id == toAccountId }) {
            toAccountId = accounts.firstOrNull { it.id != fromAccountId }?.id
        }
    }
    val paymentLabel = accounts.firstOrNull { it.id == selectedAccountId }?.name ?: "Select account"
    val parentAmountPaise = Money.fromRupeesString(amount)?.paise ?: 0L

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    // Full-screen split editor (same route, full body)
    if (editingSplits && !isSelfTransfer) {
        if (parentAmountPaise <= 0L) {
            LaunchedEffect(Unit) { editingSplits = false }
        } else {
            SplitEditorScreen(
                parentAmountPaise = parentAmountPaise,
                initialSplits = draftSplits,
                categories = categories,
                funds = funds,
                onBack = { editingSplits = false },
                allowClear = draftSplits.isNotEmpty(),
                saveLabel = "Use these splits",
                onSave = { lines ->
                    draftSplits = lines
                    Result.success(Unit)
                },
                onClear = {
                    draftSplits = emptyList()
                    Result.success(Unit)
                },
            )
            return
        }
    }

    // When category changes, look up recommended fund from past usage
    LaunchedEffect(categoryId) {
        val rec = categoryId?.let { vm.recommendFundForCategory(it) }
        recommendedFundId = rec
    }

    // Prefer last-used category / fund once
    LaunchedEffect(lastCategory, lastFund, categories, funds) {
        if (appliedLastUsed) return@LaunchedEffect
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
                                val ok = if (isSelfTransfer) {
                                    val fromId = fromAccountId
                                    val toId = toAccountId
                                    if (fromId == null || toId == null || fromId == toId) {
                                        false
                                    } else {
                                        vm.saveSelfTransfer(
                                            amountText = amount,
                                            fromAccountId = fromId,
                                            toAccountId = toId,
                                            note = note,
                                            occurredAt = whenMs,
                                        )
                                    }
                                } else {
                                    val acc = accounts.firstOrNull { it.id == selectedAccountId }
                                    val method = acc?.name ?: "Cash"
                                    val id = vm.save(
                                        amountText = amount,
                                        type = type,
                                        categoryId = categoryId,
                                        fundId = fundId,
                                        note = note,
                                        counterparty = counterparty,
                                        paymentMethod = method,
                                        accountId = selectedAccountId,
                                        useLocation = useLocation,
                                        addToFund = addToFund,
                                        occurredAt = whenMs,
                                        receiptLocalUri = receiptUri,
                                        splits = draftSplits,
                                    )
                                    id != null
                                }
                                saving = false
                                if (ok) {
                                    haptics.click()
                                    onDone()
                                }
                            }
                        },
                        enabled = !saving && amount.isNotBlank() &&
                            (!isSelfTransfer || (fromAccountId != null && toAccountId != null && fromAccountId != toAccountId)),
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
                // Debit | Credit | Transfer segmented control
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(scheme.surfaceContainerHighest, RoundedCornerShape(28.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FormTypeSegment(
                        label = "Debit",
                        selected = !isSelfTransfer && type == TransactionType.DEBIT,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            isSelfTransfer = false
                            type = TransactionType.DEBIT
                            haptics.select()
                        },
                    )
                    FormTypeSegment(
                        label = "Credit",
                        selected = !isSelfTransfer && type == TransactionType.CREDIT,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            isSelfTransfer = false
                            type = TransactionType.CREDIT
                            haptics.select()
                        },
                    )
                    FormTypeSegment(
                        label = "Transfer",
                        selected = isSelfTransfer,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            isSelfTransfer = true
                            haptics.select()
                        },
                    )
                }

                if (isSelfTransfer) {
                    Text(
                        "From account",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        accounts.forEach { acc ->
                            FormAccountChip(
                                label = acc.name,
                                icon = Icons.Default.AccountBalance,
                                selected = fromAccountId == acc.id,
                                balanceLabel = accountBalances[acc.name]?.let { paise -> paise.inr() },
                                onClick = {
                                    haptics.select()
                                    fromAccountId = acc.id
                                    if (toAccountId == acc.id) {
                                        toAccountId = accounts.firstOrNull { it.id != acc.id }?.id
                                    }
                                },
                            )
                        }
                    }
                    Text(
                        "To account",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        accounts.forEach { acc ->
                            FormAccountChip(
                                label = acc.name,
                                icon = Icons.Default.AccountBalance,
                                selected = toAccountId == acc.id,
                                balanceLabel = accountBalances[acc.name]?.let { paise -> paise.inr() },
                                onClick = {
                                    haptics.select()
                                    toAccountId = acc.id
                                    if (fromAccountId == acc.id) {
                                        fromAccountId = accounts.firstOrNull { it.id != acc.id }?.id
                                    }
                                },
                            )
                        }
                    }
                } else {
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
                                        if (currentType == TransactionType.DEBIT) {
                                            "Name (merchant or person)"
                                        } else {
                                            "Name (source)"
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

                if (!isSelfTransfer) FormExpandableHeader(
                    title = "Account",
                    subtitle = paymentLabel,
                    icon = Icons.Default.Payments,
                    expanded = paymentExpanded,
                    onToggle = {
                        haptics.select()
                        paymentExpanded = !paymentExpanded
                    },
                )
                AnimatedVisibility(
                    visible = !isSelfTransfer && paymentExpanded,
                    enter = expandVertically(M3EMotion.spatialDefault()) + fadeIn(M3EMotion.effectsDefault()),
                    exit = shrinkVertically(M3EMotion.spatialDefault()) + fadeOut(M3EMotion.effectsDefault()),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Same list as Settings → Bank accounts (+ Cash)",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            accounts.forEach { acc ->
                                FormAccountChip(
                                    label = acc.name,
                                    icon = if (acc.kind.name == "CASH") {
                                        Icons.Default.Payments
                                    } else {
                                        Icons.Default.AccountBalance
                                    },
                                    selected = selectedAccountId == acc.id,
                                    balanceLabel = accountBalances[acc.name]?.inr(),
                                    isDefault = defaultDigital.equals(acc.name, true) ||
                                        defaultPay.equals(acc.name, true),
                                    onClick = {
                                        selectedAccountId = acc.id
                                        haptics.select()
                                    },
                                )
                            }
                        }
                        if (accounts.isEmpty()) {
                            Text(
                                "No accounts yet. Add banks in Settings → Bank accounts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Splits — full-screen editor (available while adding, not only from detail)
                if (!isSelfTransfer) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = scheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Splits",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        if (draftSplits.isEmpty()) {
                                            "Optional · break amount across categories, names, or tabs"
                                        } else {
                                            "${draftSplits.size} lines · parent amount locked on save"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        haptics.select()
                                        if (parentAmountPaise <= 0L) {
                                            showAmountPad = true
                                        } else {
                                            editingSplits = true
                                        }
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    enabled = amount.isNotBlank(),
                                ) {
                                    Text(if (draftSplits.isEmpty()) "Split" else "Edit")
                                }
                            }
                            draftSplits.forEach { line ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        line.categoryName
                                            ?: line.counterparty
                                            ?: categories.firstOrNull { it.id == line.categoryId }?.name
                                            ?: funds.firstOrNull { it.fund.id == line.fundId }?.fund?.name
                                            ?: "Line",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        line.amountPaise.inr(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            if (draftSplits.isNotEmpty()) {
                                TextButton(onClick = { draftSplits = emptyList() }) {
                                    Text("Clear splits")
                                }
                            }
                        }
                    }
                }

                if (!isSelfTransfer && draftSplits.isEmpty()) FormExpandableHeader(
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
                    visible = !isSelfTransfer && draftSplits.isEmpty() && categoryExpanded,
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

                if (draftSplits.isEmpty()) FormExpandableHeader(
                    title = "More",
                    subtitle = buildString {
                        val bits = mutableListOf<String>()
                        if (fundId != null) {
                            bits += funds.firstOrNull { it.fund.id == fundId }?.fund?.name ?: "Tab"
                        }
                        if (useLocation) bits += "Location"
                        append(bits.joinToString(" · ").ifBlank { "Tab, location" })
                    },
                    icon = Icons.Default.KeyboardArrowDown,
                    expanded = moreExpanded,
                    onToggle = {
                        haptics.select()
                        moreExpanded = !moreExpanded
                    },
                )
                AnimatedVisibility(
                    visible = draftSplits.isEmpty() && moreExpanded,
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
                                    "Tab",
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
                            AnimatedVisibility(visible = type == TransactionType.CREDIT && fundId != null) {
                                FormToggleRow(
                                    title = "Apply credit to tab balance",
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
            // Entry gate: Debit / Credit / Transfer. Later edits: amount only.
            pickTransactionType = amountPadIsEntryGate || amount.isBlank(),
            onDismiss = {
                showAmountPad = false
                // Only exit the whole screen when the pad was the entry gate (empty launch)
                if (amountPadIsEntryGate && amount.isBlank()) onDone()
                amountPadIsEntryGate = false
            },
            onConfirmEntry = { entry ->
                amount = entry.amountText
                isSelfTransfer = entry.isSelfTransfer
                if (!entry.isSelfTransfer) type = entry.type
                showAmountPad = false
                amountPadIsEntryGate = false
                haptics.select()
            },
            onConfirmWithType = { amountText, selectedType ->
                amount = amountText
                type = selectedType
                isSelfTransfer = false
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
