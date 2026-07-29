package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krtky.financetracker.R
import com.krtky.financetracker.domain.model.CategorySpend
import com.krtky.financetracker.ui.components.EmptyState
import com.krtky.financetracker.ui.components.chrome.StackTopBar
import com.krtky.financetracker.ui.theme.Dimens
import com.krtky.financetracker.ui.util.CategoryIcons
import com.krtky.financetracker.ui.util.inr
import com.krtky.financetracker.ui.viewmodel.CategoriesViewModel
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

/**
 * Month category spend list — opened from Home “Top category” tile.
 * Each row opens the category’s expense transactions.
 */
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onOpenCategory: (categoryId: Long?, categoryName: String) -> Unit,
    onAddTransaction: () -> Unit = {},
    vm: CategoriesViewModel = hiltViewModel(),
) {
    // NavHost handles predictive back (no intercepting BackHandler).
    val spends by vm.categorySpend.collectAsStateWithLifecycle()
    val total by vm.totalExpense.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val list = spends.filter { it.totalPaise > 0 }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = Dimens.ScreenHorizontal,
            end = Dimens.ScreenHorizontal,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            StackTopBar(
                title = "Categories",
                subtitle = "This month’s spending by category",
                onBack = onBack,
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = scheme.primaryContainer,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Total spent",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    Text(
                        total.inr(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                    )
                    Text(
                        "${list.size} categor${if (list.size == 1) "y" else "ies"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
        }

        if (list.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Category,
                    title = stringResource(R.string.empty_categories_title),
                    body = stringResource(R.string.empty_categories_body),
                    actionLabel = stringResource(R.string.empty_categories_action),
                    onAction = onAddTransaction,
                )
            }
        } else {
            items(list, key = { "${it.categoryId}-${it.categoryName}" }) { cat ->
                CategorySpendRow(
                    spend = cat,
                    totalExpense = total,
                    onClick = { onOpenCategory(cat.categoryId, cat.categoryName) },
                )
            }
        }
    }
}

@Composable
private fun CategorySpendRow(
    spend: CategorySpend,
    totalExpense: Long,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val pct = if (totalExpense > 0) {
        ((spend.totalPaise * 100.0) / totalExpense).roundToInt()
    } else {
        0
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                CategoryIcons.iconFor(null, spend.categoryName),
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    spend.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$pct% of this month",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Text(
                spend.totalPaise.inr(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
