package com.krtky.financetracker.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.XYTileSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import com.krtky.financetracker.R
import com.krtky.financetracker.ui.components.ReceiptAttachmentField
import java.io.File
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.AmountNumpadSheet
import com.krtky.financetracker.ui.components.AmountRupeeField
import com.krtky.financetracker.ui.components.AppFabSize
import com.krtky.financetracker.ui.components.ConfirmActionSheet
import com.krtky.financetracker.ui.components.DeleteConfirmSheet
import com.krtky.financetracker.ui.components.M3LoadingIndicator
import com.krtky.financetracker.ui.components.DatePickerSheet
import com.krtky.financetracker.ui.components.FormAccountChip
import com.krtky.financetracker.ui.components.FormCategoryChip
import com.krtky.financetracker.ui.components.FormExpandableHeader
import com.krtky.financetracker.ui.components.FormTypeSegment
import com.krtky.financetracker.ui.components.TimePickerSheet
import com.krtky.financetracker.ui.util.formDateFormatter
import com.krtky.financetracker.ui.util.formTimeFormatter
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.formatDateTime
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.mapsUri
import com.krtky.financetracker.ui.util.rememberAppHaptics
import com.krtky.financetracker.ui.viewmodel.TransactionDetailViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpenSplit: () -> Unit = {},
    vm: TransactionDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { vm.load(id) }
    val txn by vm.transaction.collectAsStateWithLifecycle()
    val splits by vm.splits.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val funds by vm.funds.collectAsStateWithLifecycle()
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
    var fundId by remember { mutableStateOf<Long?>(null) }
    var addToFund by remember { mutableStateOf(true) }
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
    var recommendedFundId by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showAmountPad by remember { mutableStateOf(false) }
    /** Info first; Edit FAB opens the full editor. */
    var editing by remember { mutableStateOf(false) }

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

    /** Active accounts + current archived (if any) so old bank still shows on this txn. */
    val pickerAccounts = remember(accounts, archivedCurrent, selectedAccountId) {
        buildList {
            addAll(accounts)
            val extra = archivedCurrent
            if (extra != null && none { it.id == extra.id }) add(extra)
        }
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

    val paymentLabel = pickerAccounts.firstOrNull { it.id == selectedAccountId }?.let { acc ->
        if (acc.archived) "${acc.name} (archived)" else acc.name
    } ?: "Select account"

    LaunchedEffect(Unit) { contentVisible = true }

    LaunchedEffect(categoryId) {
        val rec = categoryId?.let { vm.recommendFundForCategory(it) }
        recommendedFundId = rec
    }

    LaunchedEffect(txn, accounts, archivedCurrent, defaultDigital, defaultPay) {
        txn?.let {
            note = it.note.orEmpty()
            counterparty = it.counterparty ?: it.merchant.orEmpty()
            categoryId = it.categoryId
            fundId = it.fundId
            addToFund = it.fundId != null || it.type == TransactionType.CREDIT
            amount = "%.2f".format(Locale.US, it.amountPaise / 100.0)
            type = it.type
            selectedAccountId = when {
                it.accountId != null -> it.accountId
                it.isCash || it.paymentMethod.equals("Cash", true) ->
                    accounts.firstOrNull { a -> a.name.equals("Cash", true) }?.id
                        ?: archivedCurrent?.takeIf { a -> a.name.equals("Cash", true) }?.id
                else -> {
                    val pm = it.paymentMethod.orEmpty()
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
        txn, note, counterparty, categoryId, fundId, addToFund, amount, type, selectedAccountId,
        selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute, useCurrentLocation,
        receiptLocalUri, receiptCleared,
    ) {
        val t = txn ?: return@remember false
        if (useCurrentLocation) return@remember true
        if (receiptLocalUri != null || receiptCleared) return@remember true
        val amountPaise = amount.toDoubleOrNull()?.let { (it * 100.0).roundToLong() }
            ?: return@remember amount.isNotBlank()
        val originalParty = t.counterparty ?: t.merchant.orEmpty()
        val origCal = Calendar.getInstance().apply { timeInMillis = t.occurredAt }
        val sameTime =
            selectedYear == origCal.get(Calendar.YEAR) &&
                selectedMonth == origCal.get(Calendar.MONTH) &&
                selectedDay == origCal.get(Calendar.DAY_OF_MONTH) &&
                selectedHour == origCal.get(Calendar.HOUR_OF_DAY) &&
                selectedMinute == origCal.get(Calendar.MINUTE)
        val effectiveFundId = when {
            fundId == null -> null
            type == TransactionType.DEBIT -> fundId
            type == TransactionType.CREDIT && addToFund -> fundId
            else -> null
        }
        val origAccountId = t.accountId
        note != t.note.orEmpty() ||
            counterparty != originalParty ||
            categoryId != t.categoryId ||
            effectiveFundId != t.fundId ||
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
            fundId = fundId,
            note = note,
            counterparty = counterparty,
            useCurrentLocation = useCurrentLocation,
            addToFund = addToFund,
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

    // Only intercept when editing so info mode keeps system predictive-back animation.
    BackHandler(enabled = editing) { exitEditOrScreen() }

    val partyTitle = t.counterparty ?: t.merchant ?: t.paymentMethod ?: "Transaction"
    val amountSign = if (t.type == TransactionType.DEBIT) "-" else "+"
    val infoPayment = when {
        t.isCash || t.paymentMethod.equals("Cash", true) -> "Cash"
        !t.paymentMethod.isNullOrBlank() -> t.paymentMethod!!
        else -> "Digital"
    }
    val categoryName = t.categoryName ?: categories.firstOrNull { it.id == t.categoryId }?.name
    val fundName = funds.firstOrNull { it.fund.id == t.fundId }?.fund?.name
    val canSplit = !t.isSelfTransfer() && !t.isTabTransfer()

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
                // ——— Info / detail view ———
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = scheme.primaryContainer,
                    ) {
                        Column(
                            Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                if (t.type == TransactionType.DEBIT) "Debit" else "Credit",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                            )
                            Text(
                                partyTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Normal,
                                color = scheme.onPrimaryContainer,
                            )
                            Text(
                                "$amountSign${t.amountPaise.inr()}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Normal,
                                color = scheme.onPrimaryContainer,
                            )
                            Text(
                                t.occurredAt.formatDateTime(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onPrimaryContainer.copy(alpha = 0.72f),
                            )
                        }
                    }

                    InfoRow(
                        icon = Icons.Default.Payments,
                        label = "Payment",
                        value = infoPayment,
                    )
                    InfoRow(
                        icon = Icons.Default.Category,
                        label = "Category",
                        value = categoryName ?: "Uncategorized",
                    )
                    existingReceiptUri?.let { receiptUri ->
                        val preview = remember(receiptUri) {
                            runCatching {
                                context.contentResolver.openInputStream(receiptUri)?.use {
                                    BitmapFactory.decodeStream(it)
                                } ?: receiptUri.path?.let { BitmapFactory.decodeFile(it) }
                            }.getOrNull()
                        }
                        if (preview != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = scheme.surfaceContainerHigh,
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.receipt_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = scheme.onSurfaceVariant,
                                    )
                                    Image(
                                        bitmap = preview.asImageBitmap(),
                                        contentDescription = stringResource(R.string.cd_receipt_preview),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(MaterialTheme.shapes.medium),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        }
                    }
                    if (fundName != null) {
                        InfoRow(
                            icon = Icons.Default.AccountBalance,
                            label = "Tab",
                            value = fundName,
                        )
                    }
                    if (canSplit) {
                        val groupSize = if (t.isSplitPart()) splits.size + 1 else splits.size
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = scheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.split_section_title),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text(
                                            if (splits.isEmpty()) {
                                                "Break into categories, names, or tabs"
                                            } else {
                                                stringResource(
                                                    R.string.split_lines_summary,
                                                    groupSize,
                                                )
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            haptics.select()
                                            onOpenSplit()
                                        },
                                        shape = RoundedCornerShape(18.dp),
                                    ) {
                                        Text(
                                            if (splits.isEmpty()) {
                                                stringResource(R.string.split_action)
                                            } else {
                                                stringResource(R.string.split_edit)
                                            },
                                        )
                                    }
                                }
                                splits.forEach { line ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                line.counterparty
                                                    ?: categories.firstOrNull { it.id == line.categoryId }?.name
                                                    ?: funds.firstOrNull { it.fund.id == line.fundId }?.fund?.name
                                                    ?: "Line",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            val bits = buildList {
                                                line.counterparty?.takeIf { it.isNotBlank() }?.let { add(it) }
                                                funds.firstOrNull { it.fund.id == line.fundId }?.fund?.name?.let { add(it) }
                                                line.note?.takeIf { it.isNotBlank() }?.let { add(it) }
                                            }
                                            if (bits.isNotEmpty()) {
                                                Text(
                                                    bits.joinToString(" · "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = scheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        Text(
                                            line.amountPaise.inr(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                                if (t.isSplitPart()) {
                                    OutlinedButton(
                                        onClick = {
                                            haptics.select()
                                            scope.launch {
                                                vm.clearSplits()
                                            }
                                        },
                                        shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.split_merge_action))
                                    }
                                }
                            }
                        }
                    } else if (t.isSelfTransfer()) {
                        Text(
                            stringResource(R.string.split_not_for_transfer),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (!t.note.isNullOrBlank()) {
                        InfoRow(
                            icon = Icons.Default.Edit,
                            label = "Note",
                            value = t.note!!,
                        )
                    }
                    if (t.placeName != null || t.latitude != null) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = scheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(Icons.Default.Place, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text("Location", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                                        Text(
                                            t.placeName ?: "${t.latitude}, ${t.longitude}",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                }
                                if (t.latitude != null && t.longitude != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                    ) {
                                        OsmMiniMap(
                                            latitude = t.latitude,
                                            longitude = t.longitude,
                                            placeName = t.placeName,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedButton(
                                        onClick = {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, mapsUri(t.latitude, t.longitude, t.placeName)),
                                            )
                                        },
                                        shape = RoundedCornerShape(18.dp),
                                    ) { Text("Open in Maps") }
                                }
                            }
                        }
                    }
                    if (!t.externalRefId.isNullOrBlank()) {
                        Text(
                            "Ref: ${t.externalRefId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(72.dp))
                }
            } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Debit | Credit
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(scheme.surfaceContainerHighest, RoundedCornerShape(28.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FormTypeSegment(
                        label = "Debit",
                        selected = type == TransactionType.DEBIT,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            type = TransactionType.DEBIT
                            haptics.select()
                        },
                    )
                    FormTypeSegment(
                        label = "Credit",
                        selected = type == TransactionType.CREDIT,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            type = TransactionType.CREDIT
                            haptics.select()
                        },
                    )
                }

                AnimatedContent(
                    targetState = type,
                    transitionSpec = {
                        (fadeIn(M3EMotion.effectsFast()) + slideInVertically(M3EMotion.spatialFast()) { it / 8 })
                            .togetherWith(fadeOut(M3EMotion.effectsFast()))
                    },
                    label = "editTypeFields",
                ) { currentType ->
                    TextField(
                        value = counterparty,
                        onValueChange = { counterparty = it },
                        placeholder = {
                            Text(
                                if (currentType == TransactionType.DEBIT) "Name (merchant or person)"
                                else "Name (source)",
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = fieldShape,
                        colors = fieldColors,
                    )
                }

                // Amount — app numpad only
                AmountRupeeField(
                    amount = amount,
                    onClick = {
                        haptics.select()
                        showAmountPad = true
                    },
                    shape = fieldShape,
                    containerColor = fieldBg,
                    amountStyle = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    symbolStyle = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )

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
                    localUri = displayReceiptUri,
                    onUriChange = { uri ->
                        if (uri == null) {
                            receiptLocalUri = null
                            receiptCleared = true
                        } else {
                            receiptLocalUri = uri
                            receiptCleared = false
                        }
                    },
                    enabled = true,
                )

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
                                    null,
                                    tint = scheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text("Date", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
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
                                    null,
                                    tint = scheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text("Time", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
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
                            "Same list as Settings → Bank accounts (+ Cash). Archived only if this txn already uses one.",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        if (pickerAccounts.isEmpty()) {
                            Text(
                                "No accounts yet. Add banks in Settings → Bank accounts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            pickerAccounts.forEach { acc ->
                                FormAccountChip(
                                    label = if (acc.archived) "${acc.name} (archived)" else acc.name,
                                    icon = if (acc.kind.name == "CASH") {
                                        Icons.Default.Payments
                                    } else {
                                        Icons.Default.AccountBalance
                                    },
                                    selected = selectedAccountId == acc.id,
                                    isDefault = defaultDigital.equals(acc.name, true) ||
                                        defaultPay.equals(acc.name, true),
                                    onClick = {
                                        selectedAccountId = acc.id
                                        haptics.select()
                                    },
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
                            icon = Icons.Default.Delete,
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

                if (funds.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Tab",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
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
                            icon = Icons.Default.Delete,
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
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = fieldBg,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Add to fund balance",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Switch(
                                    checked = addToFund,
                                    onCheckedChange = { addToFund = it },
                                )
                            }
                        }
                    }
                }

                if (!t.externalRefId.isNullOrBlank()) {
                    Text(
                        "Ref: ${t.externalRefId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (t.placeName != null || t.latitude != null) {
                    Text(
                        "Location: ${t.placeName ?: "${t.latitude}, ${t.longitude}"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    if (t.latitude != null && t.longitude != null) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, mapsUri(t.latitude, t.longitude, t.placeName)),
                                )
                            },
                            shape = RoundedCornerShape(18.dp),
                        ) { Text("Open in Maps") }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = fieldBg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Update with current location", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = useCurrentLocation, onCheckedChange = { useCurrentLocation = it })
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
            } // end editing column
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
                // Reset fields from loaded txn and return to info view
                txn?.let {
                    note = it.note.orEmpty()
                    counterparty = it.counterparty ?: it.merchant.orEmpty()
                    categoryId = it.categoryId
                    fundId = it.fundId
                    addToFund = it.fundId != null || it.type == TransactionType.CREDIT
                    amount = "%.2f".format(Locale.US, it.amountPaise / 100.0)
                    type = it.type
                    selectedAccountId = it.accountId
                        ?: accounts.firstOrNull { a ->
                            a.name.equals(it.paymentMethod, true) ||
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
            pickTransactionType = false,
            onDismiss = { showAmountPad = false },
            onConfirmAmount = { amountText ->
                amount = amountText
                showAmountPad = false
                haptics.select()
            },
        )
    }

}

private val CARTO_LIGHT = XYTileSource(
    "CartoDB_Light", 0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/light_all/",
        "https://b.basemaps.cartocdn.com/light_all/",
        "https://c.basemaps.cartocdn.com/light_all/",
        "https://d.basemaps.cartocdn.com/light_all/",
    ),
)
private val CARTO_DARK = XYTileSource(
    "CartoDB_Dark", 0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/",
    ),
)

@Composable
private fun OsmMiniMap(
    latitude: Double,
    longitude: Double,
    placeName: String?,
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    key(isDark) {
        val mapView = remember(latitude, longitude, isDark) {
            org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
            org.osmdroid.views.MapView(ctx).apply {
                setTileSource(if (isDark) CARTO_DARK else CARTO_LIGHT)
                setMultiTouchControls(false)
                setOnTouchListener { _, _ -> true }
                controller.setZoom(16.0)
                controller.setCenter(org.osmdroid.util.GeoPoint(latitude, longitude))
                val marker = org.osmdroid.views.overlay.Marker(this)
                marker.position = org.osmdroid.util.GeoPoint(latitude, longitude)
                marker.setAnchor(
                    org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                    org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM,
                )
                marker.title = placeName ?: ""
                overlays.add(marker)
                minZoomLevel = 12.0
                isVerticalMapRepetitionEnabled = false
            }
        }
        DisposableEffect(lifecycleOwner) {
            mapView.onResume()
            onDispose { mapView.onPause() }
        }
        AndroidView(factory = { mapView })
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal)
            }
        }
    }
}
