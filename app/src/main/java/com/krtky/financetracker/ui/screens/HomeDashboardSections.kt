package com.krtky.financetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.krtky.financetracker.R
import com.krtky.financetracker.ui.components.BalanceHeroCard
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.util.inr

/** Fixed Home order: Balance Hero → Unified Net Flow (Category / Account switcher) → Open Tabs. */
internal fun LazyListScope.homeDashboardSections(
    data: HomeDashboardData,
    onToggleHidden: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenMonthFlow: (direction: String, group: MonthFlowGroup) -> Unit,
    onSelectHaptic: () -> Unit,
    onOpenTabDetail: (tabId: Long) -> Unit = {},
) {
    item(key = "section_hero") {
        HomeHeroSection(
            data = data,
            onToggleHidden = onToggleHidden,
            onOpenAccounts = onOpenAccounts,
            onSelectHaptic = onSelectHaptic,
        )
    }

    item(key = "section_unified_net") {
        HomeUnifiedNetSection(
            categoryNetSpend = data.categoryNetSpend,
            sourceNetSpend = data.sourceNetSpend,
            activeAccountIds = data.activeAccountIds,
            monthLabel = data.monthLabel,
            hidden = data.isNetHidden,
            onOpenMonthFlow = onOpenMonthFlow,
        )
    }

    item(key = "section_tabs") {
        HomeOpenTabsSection(
            data = data,
            onOpenTabs = onOpenTabs,
            onOpenTabDetail = onOpenTabDetail,
        )
    }
}

@Composable
private fun HomeHeroSection(
    data: HomeDashboardData,
    onToggleHidden: () -> Unit,
    onOpenAccounts: () -> Unit,
    onSelectHaptic: () -> Unit,
) {
    AnimatedVisibility(
        visible = data.heroVisible,
        enter = fadeIn(M3EMotion.effectsDefault()) +
            slideInVertically(M3EMotion.spatialDefault()) { it / 10 },
        exit = fadeOut(),
    ) {
        BalanceHeroCard(
            title = stringResource(R.string.home_available_balance),
            balance = data.availableBalance.inr(),
            subtitle = if (data.isNetHidden) {
                "Cash · Digital"
            } else {
                "Cash ${data.cashBal.inr()} · Digital ${data.digitalBal.inr()}"
            },
            hidden = data.isNetHidden,
            onClick = onOpenAccounts,
            onToggleHidden = {
                onSelectHaptic()
                onToggleHidden()
            },
        )
    }
}
