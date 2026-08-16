package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.R
import com.krtky.financetracker.ui.components.OverviewTile
import com.krtky.financetracker.ui.util.inr

@Composable
internal fun HomeTilesSection(
    data: HomeDashboardData,
    compact: Boolean,
    halfWidth: Boolean,
    onOpenFunds: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenExpenseActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(if (halfWidth) 8.dp else 12.dp),
    ) {
        if (!compact && !halfWidth) {
            Text(
                stringResource(R.string.home_overview),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground,
            )
        }
        if (halfWidth) {
            // Stacked compact tiles for half-width cell
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OverviewTile(
                    title = "Open Tabs",
                    value = if (data.isNetHidden) "••••" else "${data.funds.size}",
                    subtitle = if (data.isNetHidden) "₹ ••••" else data.fundBalance.inr(),
                    icon = Icons.Default.Savings,
                    onClick = onOpenFunds,
                    accent = scheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                OverviewTile(
                    title = "Invested",
                    value = if (data.isNetHidden) "••••" else data.netInvested.inr(),
                    subtitle = if (data.isNetHidden) {
                        "Net this month"
                    } else {
                        "In ${data.investedPaise.inr()} · out ${data.redeemedPaise.inr()}"
                    },
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    onClick = onOpenExpenseActivity,
                    accent = scheme.tertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
                OverviewTile(
                    title = "Accounts",
                    value = if (data.isNetHidden) "••••" else data.accountsTotal.inr(),
                    subtitle = if (data.isNetHidden) "Cash · banks" else "Cash ${data.cashBal.inr()}",
                    icon = Icons.Default.Payments,
                    onClick = onOpenAccounts,
                    accent = scheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                OverviewTile(
                    title = "Lifestyle",
                    value = if (data.isNetHidden) "••••" else data.spent.inr(),
                    subtitle = data.monthLabel,
                    icon = Icons.Default.ShoppingBag,
                    onClick = onOpenExpenseActivity,
                    accent = scheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OverviewTile(
                    title = "Open Tabs",
                    value = if (data.isNetHidden) "••••" else "${data.funds.size}",
                    subtitle = if (data.isNetHidden) {
                        "₹ ••••"
                    } else {
                        data.fundBalance.inr() + if (data.funds.isEmpty()) " · none open" else " net open"
                    },
                    icon = Icons.Default.Savings,
                    onClick = onOpenFunds,
                    accent = scheme.primary,
                    modifier = Modifier.weight(1f),
                )
                OverviewTile(
                    title = "Accounts",
                    value = if (data.isNetHidden) "••••" else data.accountsTotal.inr(),
                    subtitle = if (data.isNetHidden) {
                        "Cash · Digital"
                    } else {
                        "Cash ${data.cashBal.inr()} · Digital ${data.digitalBal.inr()}"
                    },
                    icon = Icons.Default.Payments,
                    onClick = onOpenAccounts,
                    accent = scheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OverviewTile(
                    title = "Top category",
                    value = if (data.isNetHidden) {
                        "••••"
                    } else {
                        data.topCategory?.totalPaise?.inr() ?: "—"
                    },
                    subtitle = when {
                        data.isNetHidden -> "Hidden"
                        data.topCategory == null -> stringResource(R.string.home_no_expenses_yet)
                        data.topCategoryPct != null ->
                            "${data.topCategory.categoryName} · ${data.topCategoryPct}%"
                        else -> data.topCategory.categoryName
                    },
                    icon = Icons.Default.Category,
                    onClick = onOpenCategories,
                    accent = scheme.primary,
                    modifier = Modifier.weight(1f),
                )
                OverviewTile(
                    title = "Lifestyle",
                    value = if (data.isNetHidden) "••••" else data.spent.inr(),
                    subtitle = "${data.monthLabel} · excl. invest",
                    icon = Icons.Default.ShoppingBag,
                    onClick = onOpenExpenseActivity,
                    accent = scheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
