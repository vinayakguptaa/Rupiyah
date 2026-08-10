package com.krtky.financetracker.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
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

/** Shared outer shell for non-hero widgets — matches Overview radius / padding language. */
@Composable
internal fun WidgetCard(
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = GlanceTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .cornerRadius(28.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
        content = content,
    )
}

@Composable
internal fun WidgetTitle(text: String) {
    val colors = GlanceTheme.colors
    Text(
        text = text,
        style = TextStyle(
            color = colors.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
}

@Composable
internal fun WidgetCaption(text: String) {
    val colors = GlanceTheme.colors
    Text(
        text = text,
        style = TextStyle(
            color = colors.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        ),
        maxLines = 1,
    )
}

@Composable
internal fun WidgetEmpty(text: String) {
    val colors = GlanceTheme.colors
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
            ),
        )
    }
}

/**
 * List row used by Recent / Spending widgets.
 * @param trailingIsError expense / overspent → error color
 * @param trailingIsPositive income → primary color
 */
@Composable
internal fun WidgetListRow(
    title: String,
    subtitle: String?,
    trailing: String,
    trailingIsError: Boolean = false,
    trailingIsPositive: Boolean = false,
) {
    val colors = GlanceTheme.colors
    val trailingColor = when {
        trailingIsError -> colors.error
        trailingIsPositive -> colors.primary
        else -> colors.onSurface
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .cornerRadius(14.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title,
                style = TextStyle(
                    color = colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = TextStyle(
                        color = colors.onSurfaceVariant,
                        fontSize = 10.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = trailing,
            style = TextStyle(
                color = trailingColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

@Composable
internal fun WidgetProgressBar(
    progress: Float,
    isError: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
) {
    val colors = GlanceTheme.colors
    val clamped = progress.coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = clamped,
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
        color = if (isError) colors.error else colors.primary,
        backgroundColor = colors.surfaceVariant,
    )
}

@Composable
internal fun WidgetPrimaryCta(
    label: String,
    modifier: GlanceModifier = GlanceModifier,
) {
    val colors = GlanceTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.primaryContainer)
            .cornerRadius(16.dp)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = colors.onPrimaryContainer,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
