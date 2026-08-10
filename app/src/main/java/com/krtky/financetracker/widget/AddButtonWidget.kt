package com.krtky.financetracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class AddButtonWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                AddButtonContent()
            }
        }
    }
}

@Composable
private fun AddButtonContent() {
    val colors = GlanceTheme.colors

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.primaryContainer)
            .cornerRadius(28.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(
                actionRunCallback<OpenAppAction>(
                    actionParametersOf(OpenAppAction.NavigateToKey to "add_cash"),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+ Add",
            style = TextStyle(
                color = colors.onPrimaryContainer,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

class AddButtonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AddButtonWidget()
}
