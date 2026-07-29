package com.krtky.financetracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.krtky.financetracker.ui.MainActivity

class OverviewWidget : GlanceAppWidget() {

    /** Recompose when the launcher changes cell size — avoids clipped/stuck layouts. */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetDataLoader.loadOverview(context)
        provideContent {
            GlanceTheme {
                OverviewWidgetContent(snap)
            }
        }
    }
}

@Composable
private fun OverviewWidgetContent(snap: OverviewSnapshot) {
    val colors = GlanceTheme.colors

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.primaryContainer)
            .cornerRadius(20.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Net this month",
            style = TextStyle(
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
        )

        Spacer(modifier = GlanceModifier.height(6.dp))

        Text(
            text = snap.balance,
            style = TextStyle(
                color = colors.onPrimaryContainer,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )

        if (snap.monthSubtitle.isNotBlank()) {
            Text(
                text = snap.monthSubtitle,
                style = TextStyle(
                    color = colors.onSurfaceVariant,
                    fontSize = 10.sp,
                ),
                maxLines = 1,
            )
        }

        Spacer(modifier = GlanceModifier.height(12.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "Income",
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp),
                )
                Text(
                    text = snap.income,
                    style = TextStyle(
                        color = colors.onPrimaryContainer,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                if (snap.incomePct.isNotBlank()) {
                    Text(
                        text = snap.incomePct,
                        style = TextStyle(
                            color = colors.onPrimaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                if (snap.lastIncome.isNotBlank()) {
                    Text(
                        text = "vs ${snap.lastIncome}",
                        style = TextStyle(color = colors.onSurfaceVariant, fontSize = 9.sp),
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "Expense",
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp),
                )
                Text(
                    text = snap.expense,
                    style = TextStyle(
                        color = colors.onPrimaryContainer,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                if (snap.expensePct.isNotBlank()) {
                    Text(
                        text = snap.expensePct,
                        style = TextStyle(
                            color = colors.onPrimaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                if (snap.lastExpense.isNotBlank()) {
                    Text(
                        text = "vs ${snap.lastExpense}",
                        style = TextStyle(color = colors.onSurfaceVariant, fontSize = 9.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

class OverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverviewWidget()
}
