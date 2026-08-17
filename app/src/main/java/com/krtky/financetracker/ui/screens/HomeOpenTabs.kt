package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.ui.components.TabsWaveSummary

@Composable
internal fun HomeOpenTabsSection(
    data: HomeDashboardData,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onOpenTabs: () -> Unit,
) {
    Column(modifier) {
        TabsWaveSummary(
            tabs = data.tabs,
            hidden = data.isNetHidden,
            onOpenTabs = onOpenTabs,
        )
        if (!compact) Spacer(Modifier.height(16.dp))
    }
}
