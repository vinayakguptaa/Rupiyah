package com.krtky.financetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.viewmodel.TimeRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CategoryFilterOption(val id: Long, val name: String)

data class FundFilterOption(val id: Long, val name: String)

/**
 * Filters button + removable active-filter pills.
 * Dropdowns for type, bank, fund, category, date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFilterBar(
    type: TransactionType?,
    paymentMethod: String?,
    categoryId: Long?,
    categories: List<CategoryFilterOption>,
    bankAccounts: List<String> = emptyList(),
    fundId: Long? = null,
    funds: List<FundFilterOption> = emptyList(),
    timeRange: TimeRange,
    customFromMillis: Long?,
    customToMillis: Long?,
    onTypeChange: (TransactionType?) -> Unit,
    onPaymentChange: (String?) -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onFundChange: (Long?) -> Unit = {},
    onTimeRangeChange: (TimeRange) -> Unit,
    onCustomRange: (Long, Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    showCategoryFilter: Boolean = true,
    showFundFilter: Boolean = true,
    /** When false, hides bank/cash/digital dropdown (rarely used). */
    showBankFilter: Boolean = true,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var showCustomPicker by remember { mutableStateOf(false) }
    var draftType by remember { mutableStateOf<TransactionType?>(null) }
    var draftPayment by remember { mutableStateOf<String?>(null) }
    var draftCategory by remember { mutableStateOf<Long?>(null) }
    var draftFund by remember { mutableStateOf<Long?>(null) }
    var draftRange by remember { mutableStateOf(TimeRange.MONTH) }
    val dateFmt = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    val scheme = MaterialTheme.colorScheme

    val typePill = when (type) {
        TransactionType.EXPENSE -> "Expense"
        TransactionType.INCOME -> "Income"
        null -> null
    }
    val bankPill = when {
        !showBankFilter -> null
        paymentMethod == null -> null
        paymentMethod == "Digital" -> "Digital"
        else -> paymentMethod
    }
    val fundPill = if (showFundFilter) {
        funds.firstOrNull { it.id == fundId }?.name
    } else {
        null
    }
    val categoryPill = if (showCategoryFilter) {
        categories.firstOrNull { it.id == categoryId }?.name
    } else {
        null
    }
    val datePill = when (timeRange) {
        TimeRange.MONTH -> null
        TimeRange.TODAY -> "Today"
        TimeRange.WEEK -> "This week"
        TimeRange.YEAR -> "This year"
        TimeRange.ALL -> "All time"
        TimeRange.CUSTOM -> if (customFromMillis != null && customToMillis != null) {
            "${dateFmt.format(Date(customFromMillis))} – ${dateFmt.format(Date(customToMillis))}"
        } else {
            "Custom"
        }
    }
    val activeCount = listOf(typePill, bankPill, fundPill, categoryPill, datePill).count { it != null }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = {
                draftType = type
                draftPayment = paymentMethod
                draftCategory = categoryId
                draftFund = fundId
                draftRange = timeRange
                sheetOpen = true
            },
            shape = RoundedCornerShape(20.dp),
            color = if (activeCount > 0) scheme.primaryContainer else scheme.surface,
            border = BorderStroke(
                1.dp,
                if (activeCount > 0) scheme.primaryContainer else scheme.outlineVariant,
            ),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (activeCount > 0) scheme.onPrimaryContainer else scheme.onSurface,
                )
                Text(
                    if (activeCount > 0) "Filters ($activeCount)" else "Filters",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (activeCount > 0) scheme.onPrimaryContainer else scheme.onSurface,
                )
            }
        }

        typePill?.let { ActiveFilterPill(it) { onTypeChange(null) } }
        bankPill?.let { ActiveFilterPill(it) { onPaymentChange(null) } }
        fundPill?.let { ActiveFilterPill(it) { onFundChange(null) } }
        categoryPill?.let { ActiveFilterPill(it) { onCategoryChange(null) } }
        datePill?.let { ActiveFilterPill(it) { onTimeRangeChange(TimeRange.MONTH) } }
    }

    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
            containerColor = scheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Filters",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { sheetOpen = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Spacer(Modifier.height(12.dp))

                FilterDropdownField(
                    label = "Type",
                    value = when (draftType) {
                        TransactionType.EXPENSE -> "Expense"
                        TransactionType.INCOME -> "Income"
                        null -> "All"
                    },
                    options = listOf("All", "Expense", "Income"),
                    onSelect = { pick ->
                        draftType = when (pick) {
                            "Expense" -> TransactionType.EXPENSE
                            "Income" -> TransactionType.INCOME
                            else -> null
                        }
                    },
                )

                if (showBankFilter) {
                    Spacer(Modifier.height(12.dp))
                    val bankOptions = buildList {
                        add("All banks")
                        add("Cash")
                        add("Digital (all)")
                        addAll(bankAccounts)
                    }
                    val bankValue = when {
                        draftPayment == null -> "All banks"
                        draftPayment == "Digital" -> "Digital (all)"
                        else -> draftPayment!!
                    }
                    FilterDropdownField(
                        label = "Bank / account",
                        value = bankValue,
                        options = bankOptions,
                        onSelect = { pick ->
                            draftPayment = when (pick) {
                                "All banks" -> null
                                "Digital (all)" -> "Digital"
                                else -> pick
                            }
                        },
                    )
                }

                if (showFundFilter) {
                    Spacer(Modifier.height(12.dp))
                    val fundOptions = listOf("All funds") + funds.map { it.name }
                    val fundValue = funds.firstOrNull { it.id == draftFund }?.name ?: "All funds"
                    FilterDropdownField(
                        label = "Fund",
                        value = fundValue,
                        options = fundOptions,
                        onSelect = { pick ->
                            draftFund = if (pick == "All funds") {
                                null
                            } else {
                                funds.firstOrNull { it.name == pick }?.id
                            }
                        },
                    )
                }

                if (showCategoryFilter) {
                    Spacer(Modifier.height(12.dp))
                    val catOptions = listOf("All categories") + categories.map { it.name }
                    val catValue = categories.firstOrNull { it.id == draftCategory }?.name
                        ?: "All categories"
                    FilterDropdownField(
                        label = "Category",
                        value = catValue,
                        options = catOptions,
                        onSelect = { pick ->
                            draftCategory = if (pick == "All categories") {
                                null
                            } else {
                                categories.firstOrNull { it.name == pick }?.id
                            }
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))
                val dateOptions = listOf(
                    "Today",
                    "This week",
                    "This month",
                    "This year",
                    "All time",
                    "Custom range",
                )
                val dateValue = when (draftRange) {
                    TimeRange.TODAY -> "Today"
                    TimeRange.WEEK -> "This week"
                    TimeRange.MONTH -> "This month"
                    TimeRange.YEAR -> "This year"
                    TimeRange.ALL -> "All time"
                    TimeRange.CUSTOM -> "Custom range"
                }
                FilterDropdownField(
                    label = "Date",
                    value = dateValue,
                    options = dateOptions,
                    onSelect = { pick ->
                        draftRange = when (pick) {
                            "Today" -> TimeRange.TODAY
                            "This week" -> TimeRange.WEEK
                            "This year" -> TimeRange.YEAR
                            "All time" -> TimeRange.ALL
                            "Custom range" -> TimeRange.CUSTOM
                            else -> TimeRange.MONTH
                        }
                    },
                )

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        onTypeChange(draftType)
                        if (showBankFilter) onPaymentChange(draftPayment)
                        if (showCategoryFilter) onCategoryChange(draftCategory)
                        if (showFundFilter) onFundChange(draftFund)
                        if (draftRange == TimeRange.CUSTOM) {
                            showCustomPicker = true
                        } else {
                            onTimeRangeChange(draftRange)
                            sheetOpen = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                    ),
                ) {
                    Text("Apply", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        draftType = null
                        draftPayment = null
                        draftCategory = null
                        draftFund = null
                        draftRange = TimeRange.MONTH
                        onClearAll()
                        sheetOpen = false
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Text("Clear all")
                }
            }
        }
    }

    if (showCustomPicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = customFromMillis,
            initialSelectedEndDateMillis = customToMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showCustomPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            onTypeChange(draftType)
                            if (showBankFilter) onPaymentChange(draftPayment)
                            if (showCategoryFilter) onCategoryChange(draftCategory)
                            if (showFundFilter) onFundChange(draftFund)
                            onCustomRange(start, end)
                            showCustomPicker = false
                            sheetOpen = false
                        }
                    },
                    enabled = pickerState.selectedStartDateMillis != null &&
                        pickerState.selectedEndDateMillis != null,
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPicker = false }) { Text("Cancel") }
            },
        ) {
            DateRangePicker(
                state = pickerState,
                modifier = Modifier.height(500.dp),
                title = {
                    Text(
                        "Select period",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                headline = null,
                showModeToggle = false,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            fontWeight = if (option == value) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ActiveFilterPill(
    label: String,
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onRemove,
        shape = RoundedCornerShape(20.dp),
        color = scheme.secondaryContainer,
        border = BorderStroke(1.dp, scheme.secondaryContainer),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSecondaryContainer,
            )
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove $label filter",
                modifier = Modifier.size(16.dp),
                tint = scheme.onSecondaryContainer,
            )
        }
    }
}
