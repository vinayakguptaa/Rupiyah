package com.krtky.financetracker.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.domain.model.Account
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.TabBalance
import com.krtky.financetracker.domain.model.Transaction
import com.krtky.financetracker.domain.model.TransactionType
import com.krtky.financetracker.ui.components.AccountChipRow
import com.krtky.financetracker.ui.components.AmountRupeeField
import com.krtky.financetracker.ui.components.CategoryChipRow
import com.krtky.financetracker.ui.components.FormDirectionChips
import com.krtky.financetracker.ui.components.FormToggleRow
import com.krtky.financetracker.ui.components.ReceiptAttachmentField
import com.krtky.financetracker.ui.components.TabChipRow
import com.krtky.financetracker.ui.components.formFieldContainerColor
import com.krtky.financetracker.ui.components.formTextFieldColors
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.util.mapsUri
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Full editor for a loaded transaction — same field order and chip chrome as [AddCashScreen].
 * Edit-only extras: existing place / maps / external ref.
 */
@Composable
internal fun TransactionDetailEdit(
    t: Transaction,
    categories: List<Category>,
    tabs: List<TabBalance>,
    pickerAccounts: List<Account>,
    defaultDigital: String,
    defaultPay: String,
    note: String,
    onNote: (String) -> Unit,
    counterparty: String,
    onCounterparty: (String) -> Unit,
    categoryId: Long?,
    onCategoryId: (Long?) -> Unit,
    tabId: Long?,
    onTabId: (Long?) -> Unit,
    amount: String,
    type: TransactionType,
    onType: (TransactionType) -> Unit,
    selectedAccountId: Long?,
    onAccountId: (Long) -> Unit,
    useCurrentLocation: Boolean,
    onUseCurrentLocation: (Boolean) -> Unit,
    displayReceiptUri: Uri?,
    onReceiptChange: (Uri?) -> Unit,
    displayWhen: Long,
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
    val fieldBg = formFieldContainerColor()
    val fieldColors = formTextFieldColors()

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AmountRupeeField(
            amount = amount,
            onClick = {
                onHapticSelect()
                onShowAmountPad()
            },
            shape = fieldShape,
            containerColor = fieldBg,
        )

        FormDirectionChips(
            debitSelected = type == TransactionType.DEBIT,
            onDebit = {
                onType(TransactionType.DEBIT)
                onHapticSelect()
            },
            onCredit = {
                onType(TransactionType.CREDIT)
                onHapticSelect()
            },
        )

        Text(
            "Account",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (pickerAccounts.isEmpty()) {
            Text(
                "No accounts yet. Add banks in Settings → Bank accounts.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        } else {
            AccountChipRow(
                accounts = pickerAccounts,
                selectedAccountId = selectedAccountId,
                onAccountSelected = onAccountId,
                defaultDigital = defaultDigital,
                defaultPay = defaultPay,
                showArchivedSuffix = true,
            )
        }

        Text(
            "Category",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        CategoryChipRow(
            categories = categories,
            selectedCategoryId = categoryId,
            onCategorySelected = onCategoryId,
            noneIcon = Icons.Default.Clear,
        )

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

        TextField(
            value = note,
            onValueChange = onNote,
            placeholder = { Text("Note") },
            modifier = Modifier.fillMaxWidth(),
            shape = fieldShape,
            colors = fieldColors,
            minLines = 2,
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            androidx.compose.material3.Surface(
                onClick = {
                    onHapticSelect()
                    onShowDatePicker()
                },
                modifier = Modifier.weight(1f),
                shape = fieldShape,
                color = fieldBg,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        "Date",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        dateFmt.format(Date(displayWhen)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            androidx.compose.material3.Surface(
                onClick = {
                    onHapticSelect()
                    onShowTimePicker()
                },
                modifier = Modifier.weight(1f),
                shape = fieldShape,
                color = fieldBg,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        "Time",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        timeFmt.format(Date(displayWhen)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (tabs.isNotEmpty()) {
            Text(
                "Tab",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            TabChipRow(
                tabs = tabs,
                selectedTabId = tabId,
                onTabSelected = onTabId,
                noneIcon = Icons.Default.Clear,
            )
        }

        FormToggleRow(
            title = "Update with current location",
            checked = useCurrentLocation,
            onCheckedChange = onUseCurrentLocation,
        )

        ReceiptAttachmentField(
            localUri = displayReceiptUri,
            onUriChange = onReceiptChange,
            enabled = true,
        )

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

        Spacer(Modifier.height(8.dp))
    }
}
