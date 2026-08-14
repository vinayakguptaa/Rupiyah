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

class FundsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val funds = WidgetDataLoader.loadFunds(context)
        provideContent {
            GlanceTheme {
                FundsWidgetContent(funds)
            }
        }
    }
}

@Composable
private fun FundsWidgetContent(funds: List<FundRow>) {
    val colors = GlanceTheme.colors

    WidgetCard(
        modifier = GlanceModifier.clickable(actionRunCallback<OpenAppAction>()),
    ) {
        WidgetTitle("Funds")
        Spacer(modifier = GlanceModifier.height(4.dp))
        WidgetCaption("Who owes whom")
        Spacer(modifier = GlanceModifier.height(10.dp))

        if (funds.isEmpty()) {
            WidgetEmpty("No tabs yet — open the app to create one")
        } else {
            funds.take(4).forEach { fund ->
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
                            text = fund.name,
                            style = TextStyle(
                                color = colors.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Text(
                            text = fund.balance,
                            style = TextStyle(
                                color = if (!fund.owedToMe && !fund.settled) colors.error else colors.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = when {
                            fund.settled -> "settled"
                            fund.owedToMe -> "they owe you"
                            else -> "you owe them"
                        },
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = 10.sp,
                        ),
                        maxLines = 1,
                    )
                }
                Spacer(modifier = GlanceModifier.height(6.dp))
            }
        }
    }
}

class FundsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FundsWidget()
}
