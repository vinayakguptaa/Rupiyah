package com.krtky.financetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.viewmodel.TimeRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FilterSheet { TYPE, PAYMENT, DATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFilterBar(
    type: TransactionType?,
    paymentMethod: String?,
    timeRange: TimeRange,
    customFromMillis: Long?,
    customToMillis: Long?,
    onTypeChange: (TransactionType?) -> Unit,
    onPaymentChange: (String?) -> Unit,
    onTimeRangeChange: (TimeRange) -> Unit,
    onCustomRange: (Long, Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var openSheet by remember { mutableStateOf<FilterSheet?>(null) }
    var draftType by remember { mutableStateOf<TransactionType?>(null) }
    var draftPayment by remember { mutableStateOf<String?>(null) }
    var draftRange by remember { mutableStateOf(TimeRange.MONTH) }
    var showCustomPicker by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

    val typeLabel = when (type) {
        TransactionType.EXPENSE -> "Expense"
        TransactionType.INCOME -> "Income"
        null -> "Type"
    }
    val paymentLabel = paymentMethod ?: "Payment"
    val dateLabel = when (timeRange) {
        TimeRange.TODAY -> "Today"
        TimeRange.WEEK -> "Week"
        TimeRange.MONTH -> "Month"
        TimeRange.YEAR -> "Year"
        TimeRange.ALL -> "All time"
        TimeRange.CUSTOM -> if (customFromMillis != null && customToMillis != null) {
            "${dateFmt.format(Date(customFromMillis))} – ${dateFmt.format(Date(customToMillis))}"
        } else "Custom"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterDropdownChip(
            label = typeLabel,
            selected = type != null,
            onClick = {
                draftType = type
                openSheet = FilterSheet.TYPE
            },
        )
        FilterDropdownChip(
            label = paymentLabel,
            selected = paymentMethod != null,
            onClick = {
                draftPayment = paymentMethod
                openSheet = FilterSheet.PAYMENT
            },
        )
        FilterDropdownChip(
            label = dateLabel,
            selected = timeRange != TimeRange.MONTH,
            onClick = {
                draftRange = timeRange
                openSheet = FilterSheet.DATE
            },
        )
    }

    val sheet = openSheet
    if (sheet != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { openSheet = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            when (sheet) {
                FilterSheet.TYPE -> FilterSheetContent(
                    title = "Type",
                    onClose = { openSheet = null },
                    onApply = {
                        onTypeChange(draftType)
                        openSheet = null
                    },
                    onClear = {
                        draftType = null
                        onTypeChange(null)
                        openSheet = null
                    },
                ) {
                    FilterRadioRow("All", draftType == null) { draftType = null }
                    FilterRadioRow("Expense", draftType == TransactionType.EXPENSE) {
                        draftType = TransactionType.EXPENSE
                    }
                    FilterRadioRow("Income", draftType == TransactionType.INCOME) {
                        draftType = TransactionType.INCOME
                    }
                }
                FilterSheet.PAYMENT -> FilterSheetContent(
                    title = "Payment method",
                    onClose = { openSheet = null },
                    onApply = {
                        onPaymentChange(draftPayment)
                        openSheet = null
                    },
                    onClear = {
                        draftPayment = null
                        onPaymentChange(null)
                        openSheet = null
                    },
                ) {
                    FilterRadioRow("All", draftPayment == null) { draftPayment = null }
                    FilterRadioRow("Cash", draftPayment == "Cash") { draftPayment = "Cash" }
                    FilterRadioRow("Digital", draftPayment == "Digital") { draftPayment = "Digital" }
                }
                FilterSheet.DATE -> FilterSheetContent(
                    title = "Date",
                    onClose = { openSheet = null },
                    onApply = {
                        if (draftRange == TimeRange.CUSTOM) {
                            showCustomPicker = true
                        } else {
                            onTimeRangeChange(draftRange)
                            openSheet = null
                        }
                    },
                    onClear = {
                        draftRange = TimeRange.MONTH
                        onTimeRangeChange(TimeRange.MONTH)
                        openSheet = null
                    },
                ) {
                    listOf(
                        TimeRange.TODAY to "Today",
                        TimeRange.WEEK to "This week",
                        TimeRange.MONTH to "This month",
                        TimeRange.YEAR to "This year",
                        TimeRange.ALL to "All time",
                        TimeRange.CUSTOM to "Custom range",
                    ).forEach { (range, label) ->
                        FilterRadioRow(label, draftRange == range) { draftRange = range }
                    }
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
                            onCustomRange(start, end)
                            showCustomPicker = false
                            openSheet = null
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

@Composable
private fun FilterDropdownChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) scheme.secondaryContainer else scheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) scheme.secondaryContainer else scheme.outlineVariant,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterSheetContent(
    title: String,
    onClose: () -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
        Spacer(Modifier.height(8.dp))
        content()
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text("Apply", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onClear,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Text("Clear all")
        }
    }
}

@Composable
private fun FilterRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        RadioButton(selected = selected, onClick = onClick)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}
