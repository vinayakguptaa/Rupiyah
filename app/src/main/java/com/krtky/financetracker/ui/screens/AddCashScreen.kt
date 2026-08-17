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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionSource
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.AccountChipRow
import com.krtky.financetracker.ui.components.AmountNumpadSheet
import com.krtky.financetracker.ui.components.AmountRupeeField
import com.krtky.financetracker.ui.components.CategoryChipRow
import com.krtky.financetracker.ui.components.DateTimeField
import com.krtky.financetracker.ui.components.DatePickerSheet
import com.krtky.financetracker.ui.components.FormCategoryChip
import com.krtky.financetracker.ui.components.FormExpandableHeader
import com.krtky.financetracker.ui.components.FormToggleRow
import com.krtky.financetracker.ui.components.FormTypeSegment
import com.krtky.financetracker.ui.components.M3LoadingIndicator
import com.krtky.financetracker.ui.components.ReceiptAttachmentField
import com.krtky.financetracker.ui.components.TimePickerSheet
import com.krtky.financetracker.ui.components.TransactionFormState
import com.krtky.financetracker.ui.components.formTextFieldColors
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.viewmodel.AddCashViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCashScreen(
    onDone: () -> Unit,
    initialAmount: String = "",
    initialType: TransactionType = TransactionType.DEBIT,
    initialSharedText: String? = null,
    initialParsed: Transaction? = null,
    vm: AddCashViewModel = hiltViewModel(),
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val defaultPay by vm.defaultPaymentMethod.collectAsStateWithLifecycle()
    val defaultDigital by vm.defaultDigitalAccount.collectAsStateWithLifecycle()
    val accountBalances by vm.accountBalances.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()

    val formState = remember {
        TransactionFormState(initialAmount, initialType)
    }

    var reviewFromAi by remember { mutableStateOf(initialParsed != null) }
    var parsedAccountHint by remember { mutableStateOf(initialParsed) }
    var amountPadIsEntryGate by remember { mutableStateOf(initialParsed == null && initialSharedText.isNullOrBlank()) }
    var saveSource by remember {
        mutableStateOf(if (initialParsed != null) TransactionSource.PASTE else TransactionSource.MANUAL)
    }
    var showTransferSheet by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var recommendedTabId by remember { mutableStateOf<Long?>(null) }
    var appliedLastUsed by remember { mutableStateOf(false) }
    var transferAmount by remember { mutableStateOf(initialAmount) }
    val scope = rememberCoroutineScope()

    val lastCategory by vm.lastUsedCategoryId.collectAsStateWithLifecycle()
    val lastTab by vm.lastUsedTabId.collectAsStateWithLifecycle()
    val lastPayment by vm.lastUsedPaymentMethod.collectAsStateWithLifecycle()

    LaunchedEffect(formState.showAmountPad) {
        if (formState.showAmountPad) {
            amountPadIsEntryGate = false
        }
    }

    fun resolveParsedAccount(parsed: Transaction): Long? {
        if (accounts.isEmpty()) return parsed.accountId
        parsed.accountId?.let { id ->
            if (accounts.any { it.id == id }) return id
        }
        parsed.accountName?.takeIf { it.isNotBlank() }?.let { name ->
            accounts.firstOrNull { it.name.equals(name, true) }?.id?.let { return it }
            accounts.firstOrNull {
                it.name.contains(name, true) || name.contains(it.name, true)
            }?.id?.let { return it }
        }
        if (parsed.isCash) {
            accounts.firstOrNull { it.kind.name == "CASH" }?.id?.let { return it }
        }
        return accounts.firstOrNull { it.name.equals(defaultDigital, true) }?.id
            ?: accounts.firstOrNull { it.name.equals(defaultPay, true) }?.id
    }

    LaunchedEffect(accounts, defaultPay, defaultDigital, lastPayment, reviewFromAi) {
        if (reviewFromAi) {
            parsedAccountHint?.let { parsed ->
                formState.selectedAccountId = resolveParsedAccount(parsed)
            }
            return@LaunchedEffect
        }
        if (formState.selectedAccountId != null) {
            if (accounts.isNotEmpty() && accounts.none { it.id == formState.selectedAccountId }) {
                formState.selectedAccountId = null
            } else {
                return@LaunchedEffect
            }
        }
        if (accounts.isEmpty()) return@LaunchedEffect
        fun match(name: String?) =
            name?.takeIf { it.isNotBlank() }?.let { n ->
                accounts.firstOrNull { it.name.equals(n, true) }?.id
            }
        formState.selectedAccountId = match(lastPayment)
            ?: match(defaultPay)
            ?: match(defaultDigital)
            ?: accounts.firstOrNull { it.kind.name == "CASH" }?.id
            ?: accounts.first().id
    }

    LaunchedEffect(formState.categoryId) {
        val rec = formState.categoryId?.let { vm.recommendTabForCategory(it) }
        recommendedTabId = rec
    }

    LaunchedEffect(lastCategory, lastTab, categories, tabs, reviewFromAi) {
        if (appliedLastUsed || reviewFromAi) return@LaunchedEffect
        if (lastCategory != null && categories.any { it.id == lastCategory }) {
            formState.categoryId = lastCategory
        }
        if (lastTab != null && tabs.any { it.tab.id == lastTab }) {
            formState.tabId = lastTab
            formState.addToTab = true
        }
        appliedLastUsed = true
    }

    val paymentLabel = accounts.firstOrNull { it.id == formState.selectedAccountId }?.name ?: "Select account"
    val ctaLabel = if (formState.type == TransactionType.DEBIT) "Add debit" else "Add credit"

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    fun applyParsed(parsed: Transaction) {
        formState.hydrateFrom(parsed) { name, isCash ->
            parsed.accountId?.takeIf { id -> accounts.any { it.id == id } }
                ?: name?.let { n -> accounts.firstOrNull { it.name.equals(n, true) }?.id }
                ?: if (isCash) accounts.firstOrNull { it.kind.name == "CASH" }?.id else null
        }
        formState.selectedAccountId = resolveParsedAccount(parsed) ?: formState.selectedAccountId
        formState.paymentExpanded = true
        parsedAccountHint = parsed
        reviewFromAi = true
        saveSource = TransactionSource.PASTE
        haptics.select()
    }

    LaunchedEffect(initialParsed) {
        initialParsed?.let { applyParsed(it) }
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Add",
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
                                val whenMs = formState.computeDisplayWhen()
                                val acc = accounts.firstOrNull { it.id == formState.selectedAccountId }
                                val method = acc?.name ?: "Cash"
                                val ok = vm.save(
                                    amountText = formState.amount,
                                    type = formState.type,
                                    categoryId = formState.categoryId,
                                    tabId = formState.tabId,
                                    note = formState.note,
                                    counterparty = formState.counterparty,
                                    paymentMethod = method,
                                    accountId = formState.selectedAccountId,
                                    useLocation = formState.useLocation,
                                    addToTab = formState.addToTab,
                                    occurredAt = whenMs,
                                    receiptLocalUri = formState.receiptUri,
                                    source = saveSource,
                                ) != null
                                saving = false
                                if (ok) {
                                    haptics.click()
                                    onDone()
                                }
                            }
                        },
                        enabled = !saving && formState.amount.isNotBlank(),
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
                if (reviewFromAi) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = scheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        "Review before saving",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSecondaryContainer,
                                    )
                                    Text(
                                        "Check the account — AI may guess the wrong bank.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(scheme.surfaceContainerHighest, RoundedCornerShape(28.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FormTypeSegment(
                                label = "Debit",
                                selected = formState.type == TransactionType.DEBIT,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    formState.type = TransactionType.DEBIT
                                    haptics.select()
                                },
                            )
                            FormTypeSegment(
                                label = "Credit",
                                selected = formState.type == TransactionType.CREDIT,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    formState.type = TransactionType.CREDIT
                                    haptics.select()
                                },
                            )
                        }

                        AnimatedContent(
                            targetState = formState.type,
                            transitionSpec = {
                                (fadeIn(M3EMotion.effectsFast()) + slideInVertically(M3EMotion.spatialFast()) { it / 8 })
                                    .togetherWith(fadeOut(M3EMotion.effectsFast()))
                            },
                            label = "typeFields",
                        ) { currentType ->
                            TextField(
                                value = formState.counterparty,
                                onValueChange = { formState.counterparty = it },
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
                                shape = RoundedCornerShape(18.dp),
                                colors = formTextFieldColors(),
                            )
                        }

                        AmountRupeeField(
                            amount = formState.amount,
                            onClick = {
                                haptics.select()
                                formState.showAmountPad = true
                            },
                            shape = RoundedCornerShape(18.dp),
                            containerColor = scheme.surfaceContainerHigh,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        ) {
                            listOf("100", "500", "1000").forEach { chip ->
                                Surface(
                                    onClick = {
                                        haptics.select()
                                        val base = formState.amount.toDoubleOrNull() ?: 0.0
                                        val add = chip.toDouble()
                                        formState.amount = if (base == 0.0) chip else {
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

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = scheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    if (formState.type == TransactionType.CREDIT) "Received in" else "Paid from",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    paymentLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                                if (accounts.isEmpty()) {
                                    Text(
                                        "No accounts yet. Add banks in Settings → Bank accounts.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                } else {
                                    AccountChipRow(
                                        accounts = accounts,
                                        selectedAccountId = formState.selectedAccountId,
                                        onAccountSelected = { formState.selectedAccountId = it },
                                        accountBalances = accountBalances,
                                        defaultDigital = defaultDigital,
                                        defaultPay = defaultPay,
                                        showArchivedSuffix = false,
                                    )
                                }
                            }
                        }

                        TextField(
                            value = formState.note,
                            onValueChange = { formState.note = it },
                            placeholder = { Text("Description") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = formTextFieldColors(),
                            minLines = 2,
                        )

                        ReceiptAttachmentField(
                            localUri = formState.receiptUri,
                            onUriChange = { formState.receiptUri = it },
                        )

                        DateTimeField(state = formState)

                        FormExpandableHeader(
                            title = "Category",
                            subtitle = categories.firstOrNull { it.id == formState.categoryId }?.name ?: "Select category",
                            icon = Icons.Default.Payments,
                            expanded = formState.categoryExpanded,
                            onToggle = {
                                haptics.select()
                                formState.categoryExpanded = !formState.categoryExpanded
                            },
                        )
                        AnimatedVisibility(
                            visible = formState.categoryExpanded,
                            enter = expandVertically(M3EMotion.spatialDefault()) + fadeIn(M3EMotion.effectsDefault()),
                            exit = shrinkVertically(M3EMotion.spatialDefault()) + fadeOut(M3EMotion.effectsDefault()),
                        ) {
                            CategoryChipRow(
                                categories = categories,
                                selectedCategoryId = formState.categoryId,
                                onCategorySelected = { formState.categoryId = it },
                                noneIcon = Icons.Default.Clear,
                            )
                        }

                        FormExpandableHeader(
                            title = "More",
                            subtitle = buildString {
                                val bits = mutableListOf<String>()
                                if (formState.tabId != null) {
                                    bits += tabs.firstOrNull { it.tab.id == formState.tabId }?.tab?.name ?: "Tab"
                                }
                                if (formState.useLocation) bits += "Location"
                                append(bits.joinToString(" · ").ifBlank { "Tab, location" })
                            },
                            icon = Icons.Default.KeyboardArrowDown,
                            expanded = formState.moreExpanded,
                            onToggle = {
                                haptics.select()
                                formState.moreExpanded = !formState.moreExpanded
                            },
                        )
                        AnimatedVisibility(
                            visible = formState.moreExpanded,
                            enter = expandVertically(M3EMotion.spatialDefault()) + fadeIn(M3EMotion.effectsDefault()),
                            exit = shrinkVertically(M3EMotion.spatialDefault()) + fadeOut(M3EMotion.effectsDefault()),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (tabs.isNotEmpty()) {
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
                                        val recTabName = recommendedTabId?.let { id ->
                                            tabs.firstOrNull { it.tab.id == id }?.tab?.name
                                        }
                                        if (recTabName != null && formState.tabId == null) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = scheme.tertiaryContainer,
                                            ) {
                                                Text(
                                                    "Spend from $recTabName",
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
                                            selected = formState.tabId == null,
                                            onClick = { formState.tabId = null },
                                        )
                                        tabs.forEach { f ->
                                            FormCategoryChip(
                                                label = f.tab.name,
                                                icon = Icons.Default.Payments,
                                                selected = formState.tabId == f.tab.id,
                                                onClick = {
                                                    formState.tabId = f.tab.id
                                                    formState.addToTab = true
                                                },
                                            )
                                        }
                                    }
                                    AnimatedVisibility(visible = formState.type == TransactionType.CREDIT && formState.tabId != null) {
                                        FormToggleRow(
                                            title = "Apply credit to tab balance",
                                            checked = formState.addToTab,
                                            onCheckedChange = { formState.addToTab = it },
                                        )
                                    }
                                }
                                FormToggleRow(
                                    title = "Attach current location",
                                    checked = formState.useLocation,
                                    onCheckedChange = { formState.useLocation = it },
                                )
                            }
                        }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (formState.showDatePicker) {
        DatePickerSheet(
            initialMillis = formState.computeDisplayWhen(),
            onDismiss = { formState.showDatePicker = false },
            onConfirm = { y, m, d ->
                formState.selectedYear = y
                formState.selectedMonth = m
                formState.selectedDay = d
                haptics.select()
            },
        )
    }

    if (formState.showTimePicker) {
        TimePickerSheet(
            initialHour = formState.selectedHour,
            initialMinute = formState.selectedMinute,
            onDismiss = { formState.showTimePicker = false },
            onConfirm = { h, m, s, ms ->
                formState.selectedHour = h
                formState.selectedMinute = m
                formState.selectedSecond = s
                formState.selectedMillis = ms
                haptics.select()
            },
        )
    }

    if (formState.showAmountPad) {
        AmountNumpadSheet(
            initialAmount = formState.amount,
            title = "Enter amount",
            pickTransactionType = amountPadIsEntryGate || formState.amount.isBlank(),
            onDismiss = {
                formState.showAmountPad = false
                if (amountPadIsEntryGate && formState.amount.isBlank()) onDone()
                amountPadIsEntryGate = false
            },
            onConfirmEntry = { entry ->
                formState.amount = entry.amountText
                if (entry.isSelfTransfer) {
                    transferAmount = entry.amountText
                    showTransferSheet = true
                } else {
                    formState.type = entry.type
                }
                formState.showAmountPad = false
                amountPadIsEntryGate = false
                haptics.select()
            },
            onConfirmWithType = { amountText, selectedType ->
                formState.amount = amountText
                formState.type = selectedType
                formState.showAmountPad = false
                amountPadIsEntryGate = false
                haptics.select()
            },
            onConfirmAmount = { amountText ->
                formState.amount = amountText
                formState.showAmountPad = false
                amountPadIsEntryGate = false
                haptics.select()
            },
        )
    }

    if (showTransferSheet) {
        SelfTransferSheet(
            accounts = accounts,
            accountBalances = accountBalances,
            initialAmount = transferAmount.ifBlank { formState.amount },
            onDismiss = { showTransferSheet = false },
            onTransfer = { fromId, toId, amountText, note ->
                val ok = vm.saveSelfTransfer(
                    amountText = amountText,
                    fromAccountId = fromId,
                    toAccountId = toId,
                    note = note,
                    occurredAt = formState.computeDisplayWhen(),
                )
                if (ok) onDone()
                ok
            },
        )
    }
}
