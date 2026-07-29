package com.krtky.financetracker.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.appwidget.cornerRadius
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

@Composable
internal fun WidgetTitle(text: String) {
    val colors = GlanceTheme.colors
    Text(
        text = text,
        style = TextStyle(
            color = colors.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
internal fun WidgetCaption(text: String) {
    val colors = GlanceTheme.colors
    Text(
        text = text,
        style = TextStyle(
            color = colors.onSurfaceVariant,
            fontSize = 10.sp,
        ),
    )
}

@Composable
internal fun WidgetEmpty(text: String) {
    val colors = GlanceTheme.colors
    Text(
        text = text,
        style = TextStyle(
            color = colors.onSurfaceVariant,
            fontSize = 11.sp,
        ),
    )
}

@Composable
internal fun WidgetListRow(
    title: String,
    subtitle: String?,
    trailing: String,
    trailingIsError: Boolean = false,
) {
    val colors = GlanceTheme.colors
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .cornerRadius(12.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title,
                style = TextStyle(
                    color = colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = TextStyle(
                        color = colors.onSurfaceVariant,
                        fontSize = 9.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = trailing,
            style = TextStyle(
                color = if (trailingIsError) colors.error else colors.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}
