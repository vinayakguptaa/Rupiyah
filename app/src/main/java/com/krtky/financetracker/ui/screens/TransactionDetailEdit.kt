package com.krtky.financetracker.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.domain.model.Account
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.FundBalance
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.AmountRupeeField
import com.krtky.financetracker.ui.components.FormAccountChip
import com.krtky.financetracker.ui.components.FormCategoryChip
import com.krtky.financetracker.ui.components.FormExpandableHeader
import com.krtky.financetracker.ui.components.FormTypeSegment
import com.krtky.financetracker.ui.components.ReceiptAttachmentField
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.mapsUri
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Full editor for a loaded transaction. Hosted by [TransactionDetailScreen] when
 * the user taps Edit; dirty-tracking and save stay on the screen / ViewModel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransactionDetailEdit(
    t: Transaction,
    categories: List<Category>,
    funds: List<FundBalance>,
    pickerAccounts: List<Account>,
    defaultDigital: String,
    defaultPay: String,
    note: String,
    onNote: (String) -> Unit,
    counterparty: String,
    onCounterparty: (String) -> Unit,
    categoryId: Long?,
    onCategoryId: (Long?) -> Unit,
    fundId: Long?,
    onFundId: (Long?) -> Unit,
    addToFund: Boolean,
    onAddToFund: (Boolean) -> Unit,
    amount: String,
    type: TransactionType,
    onType: (TransactionType) -> Unit,
    selectedAccountId: Long?,
    onAccountId: (Long) -> Unit,
    useCurrentLocation: Boolean,
    onUseCurrentLocation: (Boolean) -> Unit,
    displayReceiptUri: Uri?,
    onReceiptChange: (Uri?) -> Unit,
    recommendedFundId: Long?,
    displayWhen: Long,
    paymentExpanded: Boolean,
    onPaymentExpanded: (Boolean) -> Unit,
    categoryExpanded: Boolean,
    onCategoryExpanded: (Boolean) -> Unit,
    dateFmt: SimpleDateFormat,
    timeFmt: SimpleDateFormat,
    context: Context,
    onShowAmountPad: () -> Unit,
    onShowDatePicker: () -> Unit,
    onShowTimePicker: () -> Unit,
    onHapticSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
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

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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
                    onType(TransactionType.DEBIT)
                    onHapticSelect()
                },
            )
            FormTypeSegment(
                label = "Credit",
                selected = type == TransactionType.CREDIT,
                modifier = Modifier.weight(1f),
                onClick = {
                    onType(TransactionType.CREDIT)
                    onHapticSelect()
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
                onValueChange = onCounterparty,
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

        AmountRupeeField(
            amount = amount,
            onClick = {
                onHapticSelect()
                onShowAmountPad()
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
            onValueChange = onNote,
            placeholder = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            shape = fieldShape,
            colors = fieldColors,
            minLines = 2,
        )

        ReceiptAttachmentField(
            localUri = displayReceiptUri,
            onUriChange = onReceiptChange,
            enabled = true,
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                onClick = {
                    onHapticSelect()
                    onShowDatePicker()
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
                        androidx.compose.material3.Icon(
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
                    onHapticSelect()
                    onShowTimePicker()
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
                        androidx.compose.material3.Icon(
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
                onHapticSelect()
                onPaymentExpanded(!paymentExpanded)
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
                                onAccountId(acc.id)
                                onHapticSelect()
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
                onHapticSelect()
                onCategoryExpanded(!categoryExpanded)
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
                        onCategoryId(null)
                        onHapticSelect()
                    },
                )
                categories.forEach { c ->
                    FormCategoryChip(
                        label = c.name,
                        icon = CategoryIcons.iconFor(c.icon, c.name),
                        selected = categoryId == c.id,
                        onClick = {
                            onCategoryId(c.id)
                            onHapticSelect()
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
                    onClick = { onFundId(null) },
                )
                funds.forEach { f ->
                    FormCategoryChip(
                        label = f.fund.name,
                        icon = Icons.Default.Payments,
                        selected = fundId == f.fund.id,
                        onClick = {
                            onFundId(f.fund.id)
                            onAddToFund(true)
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
                            onCheckedChange = onAddToFund,
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
                Switch(checked = useCurrentLocation, onCheckedChange = onUseCurrentLocation)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
