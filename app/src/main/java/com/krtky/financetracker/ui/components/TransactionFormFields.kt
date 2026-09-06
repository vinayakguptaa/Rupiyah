package com.krtky.financetracker.ui.components

import android.net.Uri
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.domain.model.Account
import com.krtky.financetracker.domain.model.Category
import com.krtky.financetracker.domain.model.TabBalance
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.formDateFormatter
import com.krtky.financetracker.ui.util.formTimeFormatter
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.util.rememberAppHaptics
import java.util.Date

/** Shape used by all form surface / text-field containers. */
val FormFieldShape: RoundedCornerShape = RoundedCornerShape(18.dp)

/** Background color for form surfaces. */
@Composable
fun formFieldContainerColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

/**
 * [TextFieldDefaults.colors] that match the form's surface background,
 * used identically by AddCashScreen and TransactionDetailScreen.
 */
@Composable
fun formTextFieldColors(): TextFieldColors {
    val bg = formFieldContainerColor()
    val scheme = MaterialTheme.colorScheme
    return TextFieldDefaults.colors(
        focusedContainerColor = bg,
        unfocusedContainerColor = bg,
        disabledContainerColor = bg,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        cursorColor = scheme.primary,
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        focusedPlaceholderColor = scheme.onSurfaceVariant.copy(alpha = 0.55f),
        unfocusedPlaceholderColor = scheme.onSurfaceVariant.copy(alpha = 0.55f),
    )
}

/**
 * Date + time picker row.  Tapping the date half opens [DatePickerSheet];
 * the time half opens [TimePickerSheet].  Both read from [TransactionFormState].
 */
@Composable
fun DateTimeField(
    state: TransactionFormState,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = FormFieldShape,
    containerColor: Color = formFieldContainerColor(),
) {
    val dateFmt = remember { formDateFormatter() }
    val timeFmt = remember { formTimeFormatter() }
    val displayWhen = state.computeDisplayWhen()
    Row(
        modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            onClick = { state.showDatePicker = true },
            modifier = Modifier.weight(1f),
            shape = shape,
            color = containerColor,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "Date",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            onClick = { state.showTimePicker = true },
            modifier = Modifier.weight(1f),
            shape = shape,
            color = containerColor,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "Time",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

/** How many chips show before a More / Less toggle on form pickers. */
const val FormChipCollapsedCount: Int = 6

/**
 * Horizontal FlowRow of [FormAccountChip] items for the payment/account picker.
 *
 * Collapses to [collapsedCount] most-used entries (list should already be usage-sorted);
 * expands in place. Selected account always stays visible when collapsed.
 */
@Composable
fun AccountChipRow(
    accounts: List<Account>,
    selectedAccountId: Long?,
    onAccountSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    accountBalances: Map<String, Long> = emptyMap(),
    defaultDigital: String = "",
    defaultPay: String = "",
    showArchivedSuffix: Boolean = false,
    collapsedCount: Int = FormChipCollapsedCount,
) {
    val haptics = rememberAppHaptics()
    var expanded by remember { mutableStateOf(false) }
    val visible = remember(accounts, selectedAccountId, expanded, collapsedCount) {
        visibleFormChips(
            items = accounts,
            expanded = expanded,
            collapsedCount = collapsedCount,
            selected = { it.id == selectedAccountId },
        )
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visible.forEach { acc ->
            FormAccountChip(
                label = if (showArchivedSuffix && acc.archived) "${acc.name} (archived)" else acc.name,
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
                    onAccountSelected(acc.id)
                    haptics.select()
                },
            )
        }
        FormChipExpandToggle(
            total = accounts.size,
            collapsedCount = collapsedCount,
            expanded = expanded,
            onToggle = {
                expanded = !expanded
                haptics.select()
            },
        )
    }
}

/**
 * Horizontal FlowRow of [FormCategoryChip] items for the category picker,
 * prefixed with a "None" chip that resets the selection.
 */
@Composable
fun CategoryChipRow(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    noneIcon: ImageVector = Icons.Default.Clear,
    collapsedCount: Int = FormChipCollapsedCount,
) {
    val haptics = rememberAppHaptics()
    var expanded by remember { mutableStateOf(false) }
    val visible = remember(categories, selectedCategoryId, expanded, collapsedCount) {
        visibleFormChips(
            items = categories,
            expanded = expanded,
            collapsedCount = collapsedCount,
            selected = { it.id == selectedCategoryId },
        )
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FormCategoryChip(
            label = "None",
            icon = noneIcon,
            selected = selectedCategoryId == null,
            onClick = {
                onCategorySelected(null)
                haptics.select()
            },
        )
        visible.forEach { c ->
            FormCategoryChip(
                label = c.name,
                icon = CategoryIcons.iconFor(c.icon, c.name),
                selected = selectedCategoryId == c.id,
                onClick = {
                    onCategorySelected(c.id)
                    haptics.select()
                },
            )
        }
        FormChipExpandToggle(
            total = categories.size,
            collapsedCount = collapsedCount,
            expanded = expanded,
            onToggle = {
                expanded = !expanded
                haptics.select()
            },
        )
    }
}

/**
 * Tab picker with the same chip chrome as categories (None + expandable list).
 */
@Composable
fun TabChipRow(
    tabs: List<TabBalance>,
    selectedTabId: Long?,
    onTabSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    noneIcon: ImageVector = Icons.Default.Clear,
    collapsedCount: Int = FormChipCollapsedCount,
) {
    val haptics = rememberAppHaptics()
    var expanded by remember { mutableStateOf(false) }
    val visible = remember(tabs, selectedTabId, expanded, collapsedCount) {
        visibleFormChips(
            items = tabs,
            expanded = expanded,
            collapsedCount = collapsedCount,
            selected = { it.tab.id == selectedTabId },
        )
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FormCategoryChip(
            label = "None",
            icon = noneIcon,
            selected = selectedTabId == null,
            onClick = {
                onTabSelected(null)
                haptics.select()
            },
        )
        visible.forEach { tb ->
            FormCategoryChip(
                label = tb.tab.name,
                icon = Icons.Default.Payments,
                selected = selectedTabId == tb.tab.id,
                onClick = {
                    onTabSelected(tb.tab.id)
                    haptics.select()
                },
            )
        }
        FormChipExpandToggle(
            total = tabs.size,
            collapsedCount = collapsedCount,
            expanded = expanded,
            onToggle = {
                expanded = !expanded
                haptics.select()
            },
        )
    }
}

@Composable
private fun FormChipExpandToggle(
    total: Int,
    collapsedCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    if (total <= collapsedCount) return
    FormCategoryChip(
        label = if (expanded) "Less" else "More",
        icon = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
        selected = false,
        onClick = onToggle,
    )
}

/** Keeps selection visible when the row is collapsed. */
internal fun <T> visibleFormChips(
    items: List<T>,
    expanded: Boolean,
    collapsedCount: Int,
    selected: (T) -> Boolean,
): List<T> {
    if (expanded || items.size <= collapsedCount) return items
    val head = items.take(collapsedCount)
    val selectedItem = items.firstOrNull(selected) ?: return head
    if (head.any(selected)) return head
    return head.dropLast(1) + selectedItem
}
