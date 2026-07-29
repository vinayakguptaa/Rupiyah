package com.krtky.financetracker.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.krtky.financetracker.ui.MainActivity
import androidx.glance.action.actionStartActivity as actionStartActivityClass

class TransactionsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rows = WidgetDataLoader.loadTransactions(context)
        val addIntent = Intent().apply {
            component = ComponentName(context.packageName, MainActivity::class.java.name)
            putExtra("navigate_to", "add_cash")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        provideContent {
            GlanceTheme {
                TransactionsWidgetContent(rows = rows, addIntent = addIntent)
            }
        }
    }
}

@Composable
private fun TransactionsWidgetContent(rows: List<TxnRow>, addIntent: Intent) {
    val colors = GlanceTheme.colors

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.surface)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivityClass<MainActivity>()),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        WidgetTitle("Recent")
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (rows.isEmpty()) {
            WidgetEmpty("No transactions yet")
            Spacer(modifier = GlanceModifier.height(8.dp))
        } else {
            rows.take(3).forEach { txn ->
                WidgetListRow(
                    title = txn.name,
                    subtitle = txn.date,
                    trailing = txn.amount,
                    trailingIsError = txn.isExpense,
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(colors.primaryContainer)
                .cornerRadius(14.dp)
                .padding(vertical = 8.dp)
                .clickable(actionStartActivity(addIntent)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "+ Add",
                style = TextStyle(
                    color = colors.onPrimaryContainer,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

class TransactionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TransactionsWidget()
}
