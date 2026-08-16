package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.ui.components.FundsWaveSummary

@Composable
internal fun HomeOpenTabsSection(
    data: HomeDashboardData,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onOpenFunds: () -> Unit,
) {
    Column(modifier) {
        FundsWaveSummary(
            funds = data.funds,
            hidden = data.isNetHidden,
            onOpenFunds = onOpenFunds,
        )
        if (!compact) Spacer(Modifier.height(16.dp))
    }
}
