package com.krtky.financetracker.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import com.krtky.financetracker.ui.components.FormDirectionChips
import com.krtky.financetracker.ui.components.FormToggleRow
import com.krtky.financetracker.ui.components.M3LoadingIndicator
import com.krtky.financetracker.ui.components.ReceiptAttachmentField
import com.krtky.financetracker.ui.components.TabChipRow
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
    initialParsed: Transaction? = null,
    initialTabId: Long? = null,
    initialCategoryName: String = "",
    initialNote: String = "",
    /** When set, this is a tab settlement — banner + CTA change. */
    settleTabName: String? = null,
    vm: AddCashViewModel = hiltViewModel(),
) {
    val isSettle = !settleTabName.isNullOrBlank()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val defaultPay by vm.defaultPaymentMethod.collectAsStateWithLifecycle()
    val defaultDigital by vm.defaultDigitalAccount.collectAsStateWithLifecycle()
    val accountBalances by vm.accountBalances.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()

    val formState = remember {
        TransactionFormState(initialAmount, initialType).also { state ->
            if (initialNote.isNotBlank()) state.note = initialNote
            if (initialTabId != null) {
                state.tabId = initialTabId
                state.addToTab = true
            }
        }
    }

    var reviewFromAi by remember { mutableStateOf(initialParsed != null) }
    var parsedAccountHint by remember { mutableStateOf(initialParsed) }
    var saveSource by remember {
        mutableStateOf(if (initialParsed != null) TransactionSource.PASTE else TransactionSource.MANUAL)
    }
    var contentVisible by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

    // Only hydrate account from AI/parse hints — never preselect last-used or defaults.
    LaunchedEffect(accounts, defaultPay, defaultDigital, reviewFromAi) {
        if (!reviewFromAi) return@LaunchedEffect
        parsedAccountHint?.let { parsed ->
            formState.selectedAccountId = resolveParsedAccount(parsed)
        }
    }

    LaunchedEffect(categories, initialCategoryName) {
        if (initialCategoryName.isBlank()) return@LaunchedEffect
        categories.firstOrNull { it.name.equals(initialCategoryName, ignoreCase = true) }?.let {
            formState.categoryId = it.id
        }
    }

    LaunchedEffect(tabs, initialTabId) {
        val id = initialTabId ?: return@LaunchedEffect
        if (tabs.any { it.tab.id == id }) {
            formState.tabId = id
            formState.addToTab = true
        }
    }

    val ctaLabel = when {
        isSettle -> "Record settlement"
        formState.type == TransactionType.DEBIT -> "Add debit"
        else -> "Add credit"
    }

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
                        if (isSettle) "Settle" else "Add",
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
                                    addToTab = formState.tabId != null,
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
                if (isSettle) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = scheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "Settle $settleTabName",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onTertiaryContainer,
                            )
                            Text(
                                "Records a real account debit or credit and closes the tab balance. Pick the account that money moved through.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onTertiaryContainer,
                            )
                        }
                    }
                } else if (reviewFromAi) {
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
                                formState.amount = if (base == 0.0) {
                                    chip
                                } else {
                                    val sum = base + add
                                    if (sum == sum.toLong().toDouble()) {
                                        sum.toLong().toString()
                                    } else {
                                        String.format(Locale.US, "%.2f", sum)
                                    }
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

                FormDirectionChips(
                    debitSelected = formState.type == TransactionType.DEBIT,
                    onDebit = {
                        formState.type = TransactionType.DEBIT
                        haptics.select()
                    },
                    onCredit = {
                        formState.type = TransactionType.CREDIT
                        haptics.select()
                    },
                )

                Text(
                    "Account",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
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

                Text(
                    "Category",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                CategoryChipRow(
                    categories = categories,
                    selectedCategoryId = formState.categoryId,
                    onCategorySelected = { formState.categoryId = it },
                    noneIcon = Icons.Default.Clear,
                )

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

                TextField(
                    value = formState.note,
                    onValueChange = { formState.note = it },
                    placeholder = { Text("Note") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = formTextFieldColors(),
                    minLines = 2,
                )

                DateTimeField(state = formState)

                if (tabs.isNotEmpty()) {
                    Text(
                        "Tab",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TabChipRow(
                        tabs = tabs,
                        selectedTabId = formState.tabId,
                        onTabSelected = { id ->
                            formState.tabId = id
                            formState.addToTab = id != null
                        },
                        noneIcon = Icons.Default.Clear,
                    )
                }

                FormToggleRow(
                    title = "Attach current location",
                    checked = formState.useLocation,
                    onCheckedChange = { formState.useLocation = it },
                )
                ReceiptAttachmentField(
                    localUri = formState.receiptUri,
                    onUriChange = { formState.receiptUri = it },
                )

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
            onDismiss = { formState.showAmountPad = false },
            onConfirmAmount = { amountText ->
                formState.amount = amountText
                formState.showAmountPad = false
                haptics.select()
            },
        )
    }
}
