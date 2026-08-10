package com.krtky.financetracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Spacer
import androidx.glance.layout.height

class TransactionsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rows = WidgetDataLoader.loadTransactions(context)
        provideContent {
            GlanceTheme {
                TransactionsWidgetContent(rows = rows)
            }
        }
    }
}

@Composable
private fun TransactionsWidgetContent(rows: List<TxnRow>) {
    WidgetCard(
        modifier = GlanceModifier.clickable(actionRunCallback<OpenAppAction>()),
    ) {
        WidgetTitle("Recent")
        Spacer(modifier = GlanceModifier.height(10.dp))

        if (rows.isEmpty()) {
            WidgetEmpty("No transactions yet")
            Spacer(modifier = GlanceModifier.height(10.dp))
        } else {
            rows.take(3).forEach { txn ->
                WidgetListRow(
                    title = txn.name,
                    subtitle = txn.date,
                    trailing = txn.amount,
                    trailingIsError = txn.isExpense,
                    trailingIsPositive = txn.amount.startsWith("+"),
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
            }
        }

        WidgetPrimaryCta(
            label = "+ Add",
            modifier = GlanceModifier.clickable(
                actionRunCallback<OpenAppAction>(
                    actionParametersOf(OpenAppAction.NavigateToKey to "add_cash"),
                ),
            ),
        )
    }
}

class TransactionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TransactionsWidget()
}
