package com.krtky.financetracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Refreshes every home-screen widget by re-running [GlanceAppWidget.provideGlance].
 * Widgets load live data from Room — no fragile SharedPreferences cache.
 */
object WidgetUpdater {

    suspend fun refreshAll(context: Context) {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            runCatching { OverviewWidget().updateAll(app) }
            runCatching { TransactionsWidget().updateAll(app) }
            runCatching { AddButtonWidget().updateAll(app) }
            runCatching { FundsWidget().updateAll(app) }
            runCatching { SpendingWidget().updateAll(app) }
        }
    }

    /** @deprecated Prefer [refreshAll]; kept for call-site compatibility. */
    suspend fun updateOverviewWidget(
        context: Context,
        summary: com.krtky.financetracker.domain.model.MonthlySummary,
        monthlyTrend: List<com.krtky.financetracker.domain.model.MonthlyTrend>,
    ) {
        refreshAll(context)
    }

    /** @deprecated Prefer [refreshAll]; kept for call-site compatibility. */
    suspend fun updateTransactionsWidget(
        context: Context,
        transactions: List<com.krtky.financetracker.domain.model.Transaction>,
    ) {
        refreshAll(context)
    }
}
