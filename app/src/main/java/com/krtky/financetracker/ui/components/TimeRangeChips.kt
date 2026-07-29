package com.krtky.financetracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.viewmodel.TimeRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangeChips(
    selected: TimeRange,
    onSelect: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
    customFromMillis: Long? = null,
    customToMillis: Long? = null,
    onCustomRange: ((Long, Long) -> Unit)? = null,
) {
    val shapes = MaterialTheme.shapes
    var showCustomPicker by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    val customLabel = if (selected == TimeRange.CUSTOM && customFromMillis != null && customToMillis != null) {
        "${dateFmt.format(Date(customFromMillis))} – ${dateFmt.format(Date(customToMillis))}"
    } else {
        "Custom"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                TimeRange.TODAY to "Today",
                TimeRange.WEEK to "Week",
                TimeRange.MONTH to "Month",
                TimeRange.YEAR to "Year",
                TimeRange.ALL to "All",
            ).forEach { (range, label) ->
                FilterChip(
                    selected = selected == range,
                    onClick = { onSelect(range) },
                    label = { Text(label) },
                    shape = shapes.medium,
                )
            }
            if (onCustomRange != null) {
                FilterChip(
                    selected = selected == TimeRange.CUSTOM,
                    onClick = { showCustomPicker = true },
                    label = { Text(customLabel) },
                    shape = shapes.medium,
                )
            }
        }
    }

    if (showCustomPicker && onCustomRange != null) {
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
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                headline = null,
                showModeToggle = false,
            )
        }
    }
}
