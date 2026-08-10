package com.krtky.financetracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import com.krtky.financetracker.R

/**
 * Home-screen mirror of [com.krtky.financetracker.ui.components.BalanceHeroCard]:
 * primaryContainer card, large net, Income | Expense with MoM % and “Compared to …”.
 *
 * Colors come only from [GlanceTheme] tokens so ink always contrasts with the
 * system Material You primaryContainer (hardcoded muted inks fail on dark fills).
 */
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
    // Full-contrast ink on primaryContainer (matches hero onCard).
    val onCard = colors.onPrimaryContainer
    // Secondary labels: same role family as onCard for guaranteed readability.
    // Glance has no alpha on ColorProviders; slightly smaller type + Medium weight
    // stands in for the in-app muted onCard copy.
    val labelColor = onCard
    // Theme-aware trend colors (primary = favorable, error = unfavorable).
    val goodTrend = colors.primary
    val badTrend = colors.error

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.primaryContainer)
            .cornerRadius(28.dp)
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .clickable(actionRunCallback<OpenAppAction>()),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Net this month",
            style = TextStyle(
                color = labelColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = snap.balance,
                style = TextStyle(
                    color = onCard,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Image(
                provider = ImageProvider(R.drawable.widget_ic_info),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(onCard),
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            Image(
                provider = ImageProvider(R.drawable.widget_ic_visibility),
                contentDescription = null,
                modifier = GlanceModifier.size(22.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(onCard),
            )
        }

        Spacer(modifier = GlanceModifier.height(16.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            HeroMetricColumn(
                label = "Income",
                value = snap.income,
                pctLabel = snap.incomePct,
                isUp = snap.incomeIsUp,
                changeGood = snap.incomeChangeGood,
                comparedLabel = snap.incomeCompared,
                onCard = onCard,
                labelColor = labelColor,
                goodTrend = goodTrend,
                badTrend = badTrend,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.width(12.dp))
            HeroMetricColumn(
                label = "Expense",
                value = snap.expense,
                pctLabel = snap.expensePct,
                isUp = snap.expenseIsUp,
                changeGood = snap.expenseChangeGood,
                comparedLabel = snap.expenseCompared,
                onCard = onCard,
                labelColor = labelColor,
                goodTrend = goodTrend,
                badTrend = badTrend,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
private fun HeroMetricColumn(
    label: String,
    value: String,
    pctLabel: String,
    isUp: Boolean,
    changeGood: Boolean,
    comparedLabel: String,
    onCard: GlanceColorProvider,
    labelColor: GlanceColorProvider,
    goodTrend: GlanceColorProvider,
    badTrend: GlanceColorProvider,
    modifier: GlanceModifier = GlanceModifier,
) {
    val trendColor = if (changeGood) goodTrend else badTrend

    Column(modifier = modifier) {
        Text(
            text = label,
            style = TextStyle(
                color = labelColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = TextStyle(
                    color = onCard,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            if (pctLabel.isNotBlank()) {
                Spacer(modifier = GlanceModifier.width(6.dp))
                Image(
                    provider = ImageProvider(
                        if (isUp) R.drawable.widget_ic_trending_up
                        else R.drawable.widget_ic_trending_down,
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.size(12.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(trendColor),
                )
                Spacer(modifier = GlanceModifier.width(2.dp))
                Text(
                    text = pctLabel,
                    style = TextStyle(
                        color = trendColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
        }
        if (comparedLabel.isNotBlank()) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = comparedLabel,
                style = TextStyle(
                    color = labelColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 2,
            )
        }
    }
}

class OverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverviewWidget()
}
