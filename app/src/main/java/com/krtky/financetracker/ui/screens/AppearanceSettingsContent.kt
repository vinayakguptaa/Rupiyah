package com.krtky.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krtky.financetracker.ui.theme.ColorSchemeStyle
import com.krtky.financetracker.ui.theme.ContrastLevel
import com.krtky.financetracker.ui.theme.DarkModePref
import com.krtky.financetracker.ui.theme.ThemeColors
import com.krtky.financetracker.ui.theme.ThemeMode
import com.krtky.financetracker.ui.theme.ThemePreset
import com.krtky.financetracker.ui.theme.TypographyMode
import com.krtky.financetracker.ui.theme.colorOrDefault
import com.krtky.financetracker.ui.theme.previewColors
import com.krtky.financetracker.ui.components.SeedColorSchemePicker
import com.krtky.financetracker.ui.viewmodel.SettingsUiState

@Composable
fun AppearanceSettingsContent(
    state: SettingsUiState,
    onDarkModeChange: (DarkModePref) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPresetChange: (ThemePreset) -> Unit,
    onCustomColorsChange: (String, String, String) -> Unit,
    onSchemeStyleChange: (ColorSchemeStyle) -> Unit,
    onContrastChange: (ContrastLevel) -> Unit,
    onTypographyModeChange: (TypographyMode) -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    themePrimary: String,
    themeSecondary: String,
    themeTertiary: String,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = state.darkModePref == DarkModePref.DARK
    val dynamic = state.themeMode == ThemeMode.MATERIAL_YOU
    val selectedColors = if (dynamic) {
        ThemeColors(scheme.primary, scheme.secondary, scheme.tertiary)
    } else if (state.themeMode == ThemeMode.CUSTOM) {
        ThemeColors(
            colorOrDefault(themePrimary, scheme.primary),
            colorOrDefault(themeSecondary, scheme.secondary),
            colorOrDefault(themeTertiary, scheme.tertiary),
        )
    } else {
        state.themePreset.previewColors(dark)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppearancePreviewCard(colors = selectedColors)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .padding(horizontal = 5.dp)
                        .size(if (index == 0) 14.dp else 12.dp)
                        .clip(CircleShape)
                        .background(if (index == 0) scheme.primary else scheme.outlineVariant),
                )
            }
        }

        Text("Light or dark", style = MaterialTheme.typography.titleLarge, color = scheme.primary)
        AppearancePanel {
            SegmentedRow {
                AppearanceSegment("Phone", Icons.Filled.BrightnessAuto, state.darkModePref == DarkModePref.SYSTEM) { onDarkModeChange(DarkModePref.SYSTEM) }
                AppearanceSegment("Light", Icons.Filled.LightMode, state.darkModePref == DarkModePref.LIGHT) { onDarkModeChange(DarkModePref.LIGHT) }
                AppearanceSegment("Dark", Icons.Filled.DarkMode, state.darkModePref == DarkModePref.DARK) { onDarkModeChange(DarkModePref.DARK) }
            }
            Text("Easier to read", style = MaterialTheme.typography.titleMedium)
            SegmentedRow {
                AppearanceSegment("Soft", selected = state.contrastLevel == ContrastLevel.LOW) { onContrastChange(ContrastLevel.LOW) }
                AppearanceSegment("Normal", selected = state.contrastLevel == ContrastLevel.MEDIUM) { onContrastChange(ContrastLevel.MEDIUM) }
                AppearanceSegment("Strong", selected = state.contrastLevel == ContrastLevel.HIGH) { onContrastChange(ContrastLevel.HIGH) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Palette, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
                Text(
                    "Match phone wallpaper colors",
                    Modifier.weight(1f).padding(horizontal = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Switch(
                    checked = dynamic,
                    onCheckedChange = { onThemeModeChange(if (it) ThemeMode.MATERIAL_YOU else ThemeMode.PRESET) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.BrightnessAuto, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
                Text(
                    "True black (saves battery on some screens)",
                    Modifier.weight(1f).padding(horizontal = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Switch(
                    checked = state.oledMode,
                    onCheckedChange = onOledModeChange,
                )
            }
            Text("App colors", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
            ColorSchemeRow(
                state = state,
                dynamic = dynamic,
                dark = dark,
                onDynamicClick = { onThemeModeChange(ThemeMode.MATERIAL_YOU) },
                onPresetClick = { preset ->
                    onThemeModeChange(ThemeMode.PRESET)
                    onPresetChange(preset)
                },
                onCustomClick = { onThemeModeChange(ThemeMode.CUSTOM) },
                themePrimary = themePrimary,
                themeSecondary = themeSecondary,
                themeTertiary = themeTertiary,
            )
            if (state.themeMode == ThemeMode.CUSTOM) {
                SeedColorSchemePicker(
                    primaryHex = themePrimary,
                    secondaryHex = themeSecondary,
                    tertiaryHex = themeTertiary,
                    style = state.themeSchemeStyle,
                    onStyleChange = onSchemeStyleChange,
                    onColorsChange = onCustomColorsChange,
                )
            }
        }

        Text("Text style", style = MaterialTheme.typography.titleLarge, color = scheme.primary)
        AppearancePanel {
            SegmentedRow {
                AppearanceSegment("Compact", selected = state.typographyMode == TypographyMode.CONDENSED) { onTypographyModeChange(TypographyMode.CONDENSED) }
                AppearanceSegment("Friendly", selected = state.typographyMode == TypographyMode.EXPRESSIVE) { onTypographyModeChange(TypographyMode.EXPRESSIVE) }
            }
            AppearanceOption("Phone default", selected = state.typographyMode == TypographyMode.SYSTEM) { onTypographyModeChange(TypographyMode.SYSTEM) }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ColorSchemeRow(
    state: SettingsUiState,
    dynamic: Boolean,
    dark: Boolean,
    onDynamicClick: () -> Unit,
    onPresetClick: (ThemePreset) -> Unit,
    onCustomClick: () -> Unit,
    themePrimary: String,
    themeSecondary: String,
    themeTertiary: String,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val dynamicColors = ThemeColors(scheme.primary, scheme.secondary, scheme.tertiary)
        ColorSwatch(
            colors = dynamicColors,
            label = null,
            selected = dynamic,
            onClick = onDynamicClick,
        )
        ThemePreset.entries.forEach { preset ->
            val colors = preset.previewColors(dark)
            ColorSwatch(
                colors = colors,
                label = null,
                selected = state.themeMode == ThemeMode.PRESET && state.themePreset == preset,
                onClick = { onPresetClick(preset) },
            )
        }
        val customColors = ThemeColors(
            colorOrDefault(themePrimary, scheme.primary),
            colorOrDefault(themeSecondary, scheme.secondary),
            colorOrDefault(themeTertiary, scheme.tertiary),
        )
        ColorSwatch(
            colors = customColors,
            label = null,
            selected = state.themeMode == ThemeMode.CUSTOM,
            onClick = onCustomClick,
        )
    }
}

@Composable
private fun ColorSwatch(
    colors: ThemeColors,
    label: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(18.dp),
                ),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(colors.primary))
                Row(Modifier.weight(1f)) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(colors.secondary))
                    Box(Modifier.weight(1f).fillMaxWidth().background(colors.tertiary))
                }
            }
        }
        if (label != null) {
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppearancePreviewCard(colors: ThemeColors) {
    val scheme = MaterialTheme.colorScheme
    val onAccent = if (colors.primary.luminance() > .55f) Color(0xFF25291D) else Color.White
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(260.dp)
                .height(350.dp)
                .border(3.dp, scheme.outlineVariant.copy(alpha = .5f), RoundedCornerShape(52.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainerHighest).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.width(72.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(scheme.outlineVariant))
                    Text("$ 1,234.56", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(22.dp)).background(colors.secondary))
                Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(22.dp)).background(colors.tertiary))
            }
            repeat(2) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(14.dp)).background(colors.primary))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.width(72.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(scheme.onSurface))
                        Box(Modifier.width(96.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(scheme.outlineVariant))
                    }
                    Box(Modifier.width(38.dp).height(9.dp).clip(RoundedCornerShape(5.dp)).background(scheme.error))
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(22.dp)).background(colors.primary), contentAlignment = Alignment.Center) {
                Text("\u2713", color = onAccent, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun AppearancePanel(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun SegmentedRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun RowScope.AppearanceSegment(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Icon(icon, null, modifier = Modifier.size(22.dp))
        Text(
            label,
            Modifier.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppearanceOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
