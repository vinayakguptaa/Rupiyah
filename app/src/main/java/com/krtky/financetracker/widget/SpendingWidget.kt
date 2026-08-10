package com.krtky.financetracker.widget

import android.content.Context
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
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class SpendingWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rows = WidgetDataLoader.loadSpending(context)
        provideContent {
            GlanceTheme {
                SpendingWidgetContent(rows)
            }
        }
    }
}

@Composable
private fun SpendingWidgetContent(rows: List<SpendRow>) {
    val colors = GlanceTheme.colors

    WidgetCard(
        modifier = GlanceModifier.clickable(actionRunCallback<OpenAppAction>()),
    ) {
        WidgetTitle("Top spending")
        Spacer(modifier = GlanceModifier.height(4.dp))
        WidgetCaption("This month")
        Spacer(modifier = GlanceModifier.height(10.dp))

        if (rows.isEmpty()) {
            WidgetEmpty("No expenses this month")
        } else {
            rows.take(4).forEach { row ->
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant)
                        .cornerRadius(14.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.name,
                            style = TextStyle(
                                color = colors.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Text(
                            text = row.amount,
                            style = TextStyle(
                                color = colors.error,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    WidgetProgressBar(
                        progress = row.ratio,
                        isError = true,
                    )
                }
                Spacer(modifier = GlanceModifier.height(6.dp))
            }
        }
    }
}

class SpendingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendingWidget()
}
