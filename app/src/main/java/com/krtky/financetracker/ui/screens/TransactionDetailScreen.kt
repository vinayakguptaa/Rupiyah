package com.krtky.financetracker.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.AmountNumpadSheet
import com.krtky.financetracker.ui.components.AppFabSize
import com.krtky.financetracker.ui.components.ConfirmActionSheet
import com.krtky.financetracker.ui.components.DatePickerSheet
import com.krtky.financetracker.ui.components.DeleteConfirmSheet
import com.krtky.financetracker.ui.components.M3LoadingIndicator
import com.krtky.financetracker.ui.components.TimePickerSheet
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.util.formDateFormatter
import com.krtky.financetracker.ui.util.formTimeFormatter
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.viewmodel.TransactionDetailViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpenSplit: () -> Unit = {},
    startEditing: Boolean = false,
    vm: TransactionDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { vm.load(id) }
    val txn by vm.transaction.collectAsStateWithLifecycle()
    val splits by vm.splits.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val archivedCurrent by vm.archivedCurrentAccount.collectAsStateWithLifecycle()
    val defaultDigital by vm.defaultDigitalAccount.collectAsStateWithLifecycle()
    val defaultPay by vm.defaultPaymentMethod.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var note by remember { mutableStateOf("") }
    var counterparty by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var tabId by remember { mutableStateOf<Long?>(null) }
    var addToTab by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.DEBIT) }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var useCurrentLocation by remember { mutableStateOf(false) }
    /** New pick while editing; null means keep existing unless [receiptCleared]. */
    var receiptLocalUri by remember { mutableStateOf<Uri?>(null) }
    var receiptCleared by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var paymentExpanded by remember { mutableStateOf(true) }
    var categoryExpanded by remember { mutableStateOf(true) }
    var contentVisible by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var recommendedTabId by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showAmountPad by remember { mutableStateOf(false) }
    /** Info first; Edit FAB opens the full editor. */
    var editing by remember { mutableStateOf(startEditing) }

    val nowCal = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableStateOf(nowCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(nowCal.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableStateOf(nowCal.get(Calendar.DAY_OF_MONTH)) }
    var selectedHour by remember { mutableStateOf(nowCal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(nowCal.get(Calendar.MINUTE)) }
    var selectedSecond by remember { mutableStateOf(nowCal.get(Calendar.SECOND)) }
    var selectedMillis by remember { mutableStateOf(nowCal.get(Calendar.MILLISECOND)) }

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

    val pickerAccounts = remember(accounts, archivedCurrent, selectedAccountId) {
        buildList {
            addAll(accounts)
            val extra = archivedCurrent
            if (extra != null && none { it.id == extra.id }) add(extra)
        }
    }

    LaunchedEffect(Unit) { contentVisible = true }

    LaunchedEffect(categoryId) {
        val rec = categoryId?.let { vm.recommendTabForCategory(it) }
        recommendedTabId = rec
    }

    LaunchedEffect(txn, accounts, archivedCurrent, defaultDigital, defaultPay) {
        txn?.let {
            note = it.note.orEmpty()
            counterparty = it.counterparty.orEmpty()
            categoryId = it.categoryId
            tabId = it.tabId
            addToTab = it.tabId != null || it.type == TransactionType.CREDIT
            amount = "%.2f".format(Locale.US, it.amountPaise / 100.0)
            type = it.type
            selectedAccountId = when {
                it.accountId != null -> it.accountId
                it.isCash || it.accountName.equals("Cash", true) ->
                    accounts.firstOrNull { a -> a.name.equals("Cash", true) }?.id
                        ?: archivedCurrent?.takeIf { a -> a.name.equals("Cash", true) }?.id
                else -> {
                    val pm = it.accountName.orEmpty()
                    accounts.firstOrNull { a -> a.name.equals(pm, true) }?.id
                        ?: archivedCurrent?.id
                        ?: accounts.firstOrNull { a -> a.name.equals(defaultDigital, true) }?.id
                        ?: accounts.firstOrNull { a -> a.name.equals(defaultPay, true) }?.id
                        ?: accounts.firstOrNull()?.id
                }
            }
            val c = Calendar.getInstance().apply { timeInMillis = it.occurredAt }
            selectedYear = c.get(Calendar.YEAR)
            selectedMonth = c.get(Calendar.MONTH)
            selectedDay = c.get(Calendar.DAY_OF_MONTH)
            selectedHour = c.get(Calendar.HOUR_OF_DAY)
            selectedMinute = c.get(Calendar.MINUTE)
            selectedSecond = c.get(Calendar.SECOND)
            selectedMillis = c.get(Calendar.MILLISECOND)
            useCurrentLocation = false
            receiptLocalUri = null
            receiptCleared = false
        }
    }

    val isDirty = remember(
        txn, note, counterparty, categoryId, tabId, addToTab, amount, type, selectedAccountId,
        selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute, useCurrentLocation,
        receiptLocalUri, receiptCleared,
    ) {
        val t = txn ?: return@remember false
        if (useCurrentLocation) return@remember true
        if (receiptLocalUri != null || receiptCleared) return@remember true
        val amountPaise = amount.toDoubleOrNull()?.let { (it * 100.0).roundToLong() }
            ?: return@remember amount.isNotBlank()
        val originalParty = t.counterparty.orEmpty()
        val origCal = Calendar.getInstance().apply { timeInMillis = t.occurredAt }
        val sameTime =
            selectedYear == origCal.get(Calendar.YEAR) &&
                selectedMonth == origCal.get(Calendar.MONTH) &&
                selectedDay == origCal.get(Calendar.DAY_OF_MONTH) &&
                selectedHour == origCal.get(Calendar.HOUR_OF_DAY) &&
                selectedMinute == origCal.get(Calendar.MINUTE)
        val effectiveTabId = when {
            tabId == null -> null
            type == TransactionType.DEBIT -> tabId
            type == TransactionType.CREDIT && addToTab -> tabId
            else -> null
        }
        val origAccountId = t.accountId
        note != t.note.orEmpty() ||
            counterparty != originalParty ||
            categoryId != t.categoryId ||
            effectiveTabId != t.tabId ||
            type != t.type ||
            selectedAccountId != origAccountId ||
            amountPaise != t.amountPaise ||
            !sameTime
    }

    fun exitEditOrScreen() {
        if (editing) {
            if (isDirty) {
                showLeaveConfirm = true
            } else {
                editing = false
            }
        } else {
            onBack()
        }
    }

    suspend fun doSave(): Boolean {
        val ok = vm.save(
            amountText = amount,
            type = type,
            occurredAt = displayWhen,
            accountId = selectedAccountId,
            categoryId = categoryId,
            tabId = tabId,
            note = note,
            counterparty = counterparty,
            useCurrentLocation = useCurrentLocation,
            addToTab = addToTab,
            receiptLocalUri = receiptLocalUri,
            clearReceipt = receiptCleared && receiptLocalUri == null,
        )
        if (ok) {
            receiptLocalUri = null
            receiptCleared = false
        }
        return ok
    }

    val existingReceiptUri = remember(txn?.receiptUri, receiptCleared) {
        if (receiptCleared) null
        else txn?.receiptUri?.let { path ->
            when {
                path.startsWith("content:") || path.startsWith("file:") -> Uri.parse(path)
                else -> {
                    val file = File(context.filesDir, path.removePrefix("/"))
                    if (file.exists()) Uri.fromFile(file) else null
                }
            }
        }
    }
    val displayReceiptUri = receiptLocalUri ?: existingReceiptUri

    val t = txn
    if (t == null) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            M3LoadingIndicator()
        }
        return
    }

    BackHandler(enabled = editing) { exitEditOrScreen() }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (editing) "Edit transaction" else "Transaction",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Normal,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { exitEditOrScreen() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = scheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onBackground,
                    navigationIconContentColor = scheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            if (!editing && contentVisible) {
                FloatingActionButton(
                    onClick = {
                        haptics.select()
                        editing = true
                    },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .size(AppFabSize),
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = contentVisible && editing,
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
                                val ok = doSave()
                                saving = false
                                if (ok) {
                                    haptics.click()
                                    editing = false
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
                                "Save changes",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Normal,
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
            if (!editing) {
                TransactionDetailView(
                    t = t,
                    categories = categories,
                    tabs = tabs,
                    splits = splits,
                    existingReceiptUri = existingReceiptUri,
                    context = context,
                    onOpenSplit = onOpenSplit,
                    onClearSplits = { scope.launch { vm.clearSplits() } },
                    haptics = haptics,
                )
            } else {
                TransactionDetailEdit(
                    t = t,
                    categories = categories,
                    tabs = tabs,
                    pickerAccounts = pickerAccounts,
                    defaultDigital = defaultDigital,
                    defaultPay = defaultPay,
                    note = note,
                    onNote = { note = it },
                    counterparty = counterparty,
                    onCounterparty = { counterparty = it },
                    categoryId = categoryId,
                    onCategoryId = { categoryId = it },
                    tabId = tabId,
                    onTabId = { tabId = it },
                    addToTab = addToTab,
                    onAddToTab = { addToTab = it },
                    amount = amount,
                    type = type,
                    onType = { type = it },
                    selectedAccountId = selectedAccountId,
                    onAccountId = { selectedAccountId = it },
                    useCurrentLocation = useCurrentLocation,
                    onUseCurrentLocation = { useCurrentLocation = it },
                    displayReceiptUri = displayReceiptUri,
                    onReceiptChange = { uri ->
                        if (uri == null) {
                            receiptLocalUri = null
                            receiptCleared = true
                        } else {
                            receiptLocalUri = uri
                            receiptCleared = false
                        }
                    },
                    recommendedTabId = recommendedTabId,
                    displayWhen = displayWhen,
                    paymentExpanded = paymentExpanded,
                    onPaymentExpanded = { paymentExpanded = it },
                    categoryExpanded = categoryExpanded,
                    onCategoryExpanded = { categoryExpanded = it },
                    dateFmt = dateFmt,
                    timeFmt = timeFmt,
                    context = context,
                    onShowAmountPad = { showAmountPad = true },
                    onShowDatePicker = { showDatePicker = true },
                    onShowTimePicker = { showTimePicker = true },
                    onHapticSelect = { haptics.select() },
                )
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
                    if (doSave()) {
                        showLeaveConfirm = false
                        editing = false
                    }
                }
            },
            secondaryLabel = "Discard",
            onSecondary = {
                showLeaveConfirm = false
                txn?.let {
                    note = it.note.orEmpty()
                    counterparty = it.counterparty.orEmpty()
                    categoryId = it.categoryId
                    tabId = it.tabId
                    addToTab = it.tabId != null || it.type == TransactionType.CREDIT
                    amount = "%.2f".format(Locale.US, it.amountPaise / 100.0)
                    type = it.type
                    selectedAccountId = it.accountId
                        ?: accounts.firstOrNull { a ->
                            a.name.equals(it.accountName, true) ||
                                (it.isCash && a.name.equals("Cash", true))
                        }?.id
                        ?: archivedCurrent?.id
                    val c = Calendar.getInstance().apply { timeInMillis = it.occurredAt }
                    selectedYear = c.get(Calendar.YEAR)
                    selectedMonth = c.get(Calendar.MONTH)
                    selectedDay = c.get(Calendar.DAY_OF_MONTH)
                    selectedHour = c.get(Calendar.HOUR_OF_DAY)
                    selectedMinute = c.get(Calendar.MINUTE)
                    selectedSecond = c.get(Calendar.SECOND)
                    selectedMillis = c.get(Calendar.MILLISECOND)
                    useCurrentLocation = false
                    receiptLocalUri = null
                    receiptCleared = false
                }
                editing = false
            },
            tertiaryLabel = "Keep editing",
            onTertiary = { showLeaveConfirm = false },
        )
    }

    if (showAmountPad) {
        AmountNumpadSheet(
            initialAmount = amount,
            title = "Edit amount",
            onDismiss = { showAmountPad = false },
            onConfirmAmount = { amountText ->
                amount = amountText
                showAmountPad = false
                haptics.select()
            },
        )
    }
}
