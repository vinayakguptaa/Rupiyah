package com.krtky.financetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.krtky.financetracker.R
import com.krtky.financetracker.ui.theme.ColorSchemeStyle
import com.krtky.financetracker.ui.theme.ThemeColors
import com.krtky.financetracker.ui.theme.ThemeMode
import com.krtky.financetracker.ui.theme.ThemePreset
import com.krtky.financetracker.ui.theme.colorOrDefault
import com.krtky.financetracker.ui.theme.description
import com.krtky.financetracker.ui.theme.displayName
import com.krtky.financetracker.ui.theme.generateThemeFromSeed
import com.krtky.financetracker.ui.theme.isFeatured
import com.krtky.financetracker.ui.theme.previewColors
import com.krtky.financetracker.ui.theme.toHexRgb
import com.krtky.financetracker.ui.util.rememberAppHaptics
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

private enum class ColorNode { PRIMARY, SECONDARY, TERTIARY }

/**
 * Custom theme: seed color + M3 palette style.
 *
 * HSL state is local and stable during drag. App-wide theme + prefs are only written
 * when the finger lifts (or when a style chip is tapped) so sliders stay smooth.
 */
@Composable
fun SeedColorSchemePicker(
    primaryHex: String,
    secondaryHex: String,
    tertiaryHex: String,
    style: ColorSchemeStyle,
    onStyleChange: (ColorSchemeStyle) -> Unit,
    onColorsChange: (primary: String, secondary: String, tertiary: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fallback = Color(0xFF3157C9)
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    val styleState = rememberUpdatedState(style)
    val onColorsChangeState = rememberUpdatedState(onColorsChange)

    // One-shot init from saved primary — never re-key on parent hex while dragging.
    val initialHsl = remember {
        val out = FloatArray(3)
        ColorUtils.colorToHSL(colorOrDefault(primaryHex, fallback).toArgb(), out)
        out
    }
    var hue by remember { mutableFloatStateOf(initialHsl[0]) }
    var sat by remember { mutableFloatStateOf(initialHsl[1].coerceIn(0f, 1f)) }
    var light by remember { mutableFloatStateOf(initialHsl[2].coerceIn(0.08f, 0.92f)) }

    fun seedColor(h: Float = hue, s: Float = sat, l: Float = light): Color =
        Color(
            ColorUtils.HSLToColor(
                floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0.08f, 0.92f)),
            ),
        )

    fun commit(st: ColorSchemeStyle = styleState.value) {
        val generated = generateThemeFromSeed(seedColor(), st)
        onColorsChangeState.value(
            generated.primary.toHexRgb(),
            generated.secondary.toHexRgb(),
            generated.tertiary.toHexRgb(),
        )
    }

    // Cheap local preview only (no DataStore / app theme while dragging)
    val seed = remember(hue, sat, light) { seedColor(hue, sat, light) }
    val generated = remember(seed, style) { generateThemeFromSeed(seed, style) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = scheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Seed color", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Drag hue, saturation, or lightness — the app theme applies when you release.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                HslSliderRow(
                    label = "Hue",
                    valueLabel = "${hue.roundToInt()}°",
                    value = (hue / 360f).coerceIn(0f, 1f),
                    brush = remember {
                        Brush.horizontalGradient(
                            listOf(
                                Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                                Color.Blue, Color.Magenta, Color.Red,
                            ),
                        )
                    },
                    onChange = { f -> hue = (f * 360f).coerceIn(0f, 360f) },
                    onDragEnd = {
                        commit()
                        haptics.tick()
                    },
                )
                HslSliderRow(
                    label = "Saturation",
                    valueLabel = "${(sat * 100).roundToInt()}%",
                    value = sat.coerceIn(0f, 1f),
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(ColorUtils.HSLToColor(floatArrayOf(hue, 0f, light))),
                            Color(ColorUtils.HSLToColor(floatArrayOf(hue, 1f, light))),
                        ),
                    ),
                    onChange = { f -> sat = f.coerceIn(0f, 1f) },
                    onDragEnd = {
                        commit()
                        haptics.tick()
                    },
                )
                HslSliderRow(
                    label = "Lightness",
                    valueLabel = "${(light * 100).roundToInt()}%",
                    value = light.coerceIn(0.08f, 0.92f),
                    valueRange = 0.08f..0.92f,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Black,
                            Color(ColorUtils.HSLToColor(floatArrayOf(hue, sat, 0.5f))),
                            Color.White,
                        ),
                    ),
                    onChange = { f -> light = f.coerceIn(0.08f, 0.92f) },
                    onDragEnd = {
                        commit()
                        haptics.tick()
                    },
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    RoleSwatch(label = "Primary", color = generated.primary, selected = true, onClick = {})
                    RoleSwatch(label = "Secondary", color = generated.secondary, selected = false, onClick = {})
                    RoleSwatch(label = "Tertiary", color = generated.tertiary, selected = false, onClick = {})
                }
            }
        }

        Text("Color scheme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Material 3 palette styles",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorSchemeStyle.entries.forEach { st ->
                val selected = style == st
                // Cheap HSL math; seed already memoized for this frame
                val preview = generateThemeFromSeed(seed, st)
                Surface(
                    onClick = {
                        haptics.select()
                        onStyleChange(st)
                        commit(st)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
                    border = BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) preview.primary else scheme.outlineVariant.copy(alpha = 0.45f),
                    ),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                            listOf(preview.primary, preview.secondary, preview.tertiary).forEach { c ->
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(1.5.dp, scheme.surface, CircleShape),
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                st.displayName(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                                maxLines = 1,
                            )
                            Text(
                                st.description(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) {
                                    scheme.onPrimaryContainer.copy(alpha = 0.8f)
                                } else {
                                    scheme.onSurfaceVariant
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    val options = listOf(
        Triple(ThemeMode.MATERIAL_YOU, "Dynamic", "Wallpaper colors (Material You)"),
        Triple(ThemeMode.PRESET, "Presets", "Curated full-app color schemes"),
        Triple(ThemeMode.CUSTOM, "Custom", "Seed color + M3 palette style"),
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceContainerHighest)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (mode, label, _) ->
                val isSelected = selected == mode
                val bg = if (isSelected) scheme.primary else Color.Transparent
                val fg = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .clickable {
                            haptics.select()
                            onSelect(mode)
                        }
                        .semantics { this.selected = isSelected },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = fg,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Text(
            options.first { it.first == selected }.third,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ThemePreviewCard(
    colors: ThemeColors? = null,
    useScheme: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val primary = if (useScheme || colors == null) scheme.primary else colors.primary
    val secondary = if (useScheme || colors == null) scheme.secondary else colors.secondary
    val tertiary = if (useScheme || colors == null) scheme.tertiary else colors.tertiary
    val surface = if (useScheme || colors == null) scheme.surface else scheme.surface
    val surfaceVariant = if (useScheme || colors == null) scheme.surfaceContainerHigh else primary.copy(alpha = 0.10f)
    val onPrimary = if (primary.luminance() > 0.55f) Color.Black else Color.White
    val shapes = MaterialTheme.shapes

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shapes.extraLarge,
        color = surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Live preview",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Surface(shape = shapes.large, color = primary) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Available balance", style = MaterialTheme.typography.labelMedium, color = onPrimary.copy(alpha = 0.8f))
                    Text("₹42,680", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = onPrimary)
                    Text("This month  ·  +₹18,200 income", style = MaterialTheme.typography.bodySmall, color = onPrimary.copy(alpha = 0.8f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewMetric("Spent", "₹7,420", tertiary, surfaceVariant, scheme.onSurface, Modifier.weight(1f))
                PreviewMetric("Saved", "₹10,780", secondary, surfaceVariant, scheme.onSurface, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = onPrimary), shape = shapes.medium) { Text("Add") }
                FilterChip(selected = true, onClick = {}, label = { Text("This month") }, shape = shapes.medium)
            }
        }
    }
}

@Composable
private fun PreviewMetric(label: String, value: String, accent: Color, container: Color, content: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = container) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.7f))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
fun ThemePresetGrid(
    selected: ThemePreset,
    onSelect: (ThemePreset) -> Unit,
    darkPreview: Boolean = false,
    modifier: Modifier = Modifier,
) {
    ThemePresetList(
        selected = selected,
        onSelect = onSelect,
        darkPreview = darkPreview,
        modifier = modifier,
    )
}

@Composable
fun ThemePresetList(
    selected: ThemePreset,
    onSelect: (ThemePreset) -> Unit,
    darkPreview: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val featured = ThemePreset.entries.filter { it.isFeatured() }
    val more = ThemePreset.entries.filter { !it.isFeatured() }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "FEATURED SCHEMES",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
        featured.forEach { preset ->
            SchemeRowCard(
                preset = preset,
                colors = preset.previewColors(darkPreview),
                isSelected = selected == preset,
                onClick = {
                    haptics.select()
                    onSelect(preset)
                },
            )
        }
        if (more.isNotEmpty()) {
            Text(
                "MORE SCHEMES",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            more.forEach { preset ->
                SchemeRowCard(
                    preset = preset,
                    colors = preset.previewColors(darkPreview),
                    isSelected = selected == preset,
                    onClick = {
                        haptics.select()
                        onSelect(preset)
                    },
                )
            }
        }
    }
}

@Composable
private fun SchemeRowCard(
    preset: ThemePreset,
    colors: ThemeColors,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val name = preset.displayName()
    val desc = preset.description()
    val a11y = if (isSelected) {
        stringResource(R.string.theme_preset_desc_selected, name)
    } else {
        stringResource(R.string.theme_preset_desc, name)
    }
    val shape = RoundedCornerShape(20.dp)
    val container = scheme.surfaceContainerLow
    val titleColor = scheme.onSurface
    val bodyColor = scheme.onSurfaceVariant
    val border = if (isSelected) {
        BorderStroke(2.dp, colors.primary)
    } else {
        BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f))
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                selected = isSelected
                contentDescription = a11y
            },
        shape = shape,
        color = container,
        border = border,
         tonalElevation = if (isSelected) 3.dp else 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
             Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                 Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                     listOf(colors.primary, colors.secondary, colors.tertiary).forEach { c ->
                     Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(2.dp, container, CircleShape),
                     )
                     }
                 }
                 Text("Aa", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.primary)
             }
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = bodyColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isSelected) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                         .background(colors.primary.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                         tint = colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
fun ThreeNodeColorPicker(
    primaryHex: String,
    secondaryHex: String,
    tertiaryHex: String,
    onColorsChange: (primary: String, secondary: String, tertiary: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fallback = ThemeColors(Color(0xFF3157C9), Color(0xFF167C83), Color(0xFFC47A24))
    var primary by remember { mutableStateOf(colorOrDefault(primaryHex, fallback.primary)) }
    var secondary by remember { mutableStateOf(colorOrDefault(secondaryHex, fallback.secondary)) }
    var tertiary by remember { mutableStateOf(colorOrDefault(tertiaryHex, fallback.tertiary)) }
    var selectedNode by remember { mutableStateOf(ColorNode.PRIMARY) }
    var hexDraft by remember { mutableStateOf(primary.toHexRgb()) }
    var hexError by remember { mutableStateOf(false) }
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    val panelShape = MaterialTheme.shapes.extraLarge

    LaunchedEffect(primaryHex, secondaryHex, tertiaryHex) {
        primary = colorOrDefault(primaryHex, fallback.primary)
        secondary = colorOrDefault(secondaryHex, fallback.secondary)
        tertiary = colorOrDefault(tertiaryHex, fallback.tertiary)
    }

    val active = when (selectedNode) {
        ColorNode.PRIMARY -> primary
        ColorNode.SECONDARY -> secondary
        ColorNode.TERTIARY -> tertiary
    }
    val hsl = remember(active) {
        val out = FloatArray(3)
        ColorUtils.colorToHSL(active.toArgb(), out)
        out
    }
    var hue by remember { mutableFloatStateOf(hsl[0]) }
    var sat by remember { mutableFloatStateOf(hsl[1]) }
    var light by remember { mutableFloatStateOf(hsl[2]) }

    LaunchedEffect(selectedNode, active) {
        val out = FloatArray(3)
        ColorUtils.colorToHSL(active.toArgb(), out)
        hue = out[0]
        sat = out[1]
        light = out[2]
        hexDraft = active.toHexRgb()
        hexError = false
    }

    fun applyHsl(h: Float, s: Float, l: Float) {
        val argb = ColorUtils.HSLToColor(floatArrayOf(h, s, l))
        val color = Color(argb)
        when (selectedNode) {
            ColorNode.PRIMARY -> primary = color
            ColorNode.SECONDARY -> secondary = color
            ColorNode.TERTIARY -> tertiary = color
        }
        hexDraft = color.toHexRgb()
        hexError = false
    }

    LaunchedEffect(primary, secondary, tertiary) {
        snapshotFlow { Triple(primary.toHexRgb(), secondary.toHexRgb(), tertiary.toHexRgb()) }
            .distinctUntilChanged()
            .debounce(80)
            .collect { (p, s, t) -> onColorsChange(p, s, t) }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            shape = panelShape,
            color = scheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Color Preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    RoleSwatch(
                        label = "Primary",
                        color = primary,
                        selected = selectedNode == ColorNode.PRIMARY,
                        onClick = {
                            haptics.select()
                            selectedNode = ColorNode.PRIMARY
                        },
                    )
                    RoleSwatch(
                        label = "Secondary",
                        color = secondary,
                        selected = selectedNode == ColorNode.SECONDARY,
                        onClick = {
                            haptics.select()
                            selectedNode = ColorNode.SECONDARY
                        },
                    )
                    RoleSwatch(
                        label = "Tertiary",
                        color = tertiary,
                        selected = selectedNode == ColorNode.TERTIARY,
                        onClick = {
                            haptics.select()
                            selectedNode = ColorNode.TERTIARY
                        },
                    )
                }

                Text("Color Picker", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                HexPill(
                    color = active,
                    hex = hexDraft,
                    isError = hexError,
                    onHexChange = { raw ->
                        hexDraft = raw.uppercase()
                        val parsed = parseStrictHex(raw)
                        if (parsed != null) {
                            hexError = false
                            when (selectedNode) {
                                ColorNode.PRIMARY -> primary = parsed
                                ColorNode.SECONDARY -> secondary = parsed
                                ColorNode.TERTIARY -> tertiary = parsed
                            }
                            val out = FloatArray(3)
                            ColorUtils.colorToHSL(parsed.toArgb(), out)
                            hue = out[0]
                            sat = out[1]
                            light = out[2]
                            haptics.tick()
                        } else if (raw.isNotBlank()) {
                            hexError = true
                        }
                    },
                )

                HslSliderRow(
                    label = "Hue",
                    valueLabel = "${hue.roundToInt()}°",
                    value = hue / 360f,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
                        ),
                    ),
                    onChange = { f ->
                        hue = f * 360f
                        applyHsl(hue, sat, light)
                    },
                    onDragEnd = { haptics.tick() },
                )
                HslSliderRow(
                    label = "Saturation",
                    valueLabel = "${(sat * 100).roundToInt()}%",
                    value = sat,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(ColorUtils.HSLToColor(floatArrayOf(hue, 0f, light))),
                            Color(ColorUtils.HSLToColor(floatArrayOf(hue, 1f, light))),
                        ),
                    ),
                    onChange = { f ->
                        sat = f
                        applyHsl(hue, sat, light)
                    },
                    onDragEnd = { haptics.tick() },
                )
                HslSliderRow(
                    label = "Lightness",
                    valueLabel = "${(light * 100).roundToInt()}%",
                    value = light,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Black,
                            Color(ColorUtils.HSLToColor(floatArrayOf(hue, sat, 0.5f))),
                            Color.White,
                        ),
                    ),
                    onChange = { f ->
                        light = f
                        applyHsl(hue, sat, light)
                    },
                    onDragEnd = { haptics.tick() },
                )
            }
        }

        Text("Color Inspirations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        InspirationGroup(
            title = "Warm & Cozy",
            colors = listOf(
                Color(0xFFFF6B35), Color(0xFFFF8C42), Color(0xFFFF5722),
                Color(0xFFE91E63), Color(0xFFC2185B), Color(0xFF9C27B0),
            ),
            onPick = { c ->
                haptics.click()
                applyInspiration(c, selectedNode) { p, s, t ->
                    primary = p; secondary = s; tertiary = t
                }
            },
        )
        InspirationGroup(
            title = "Cool & Fresh",
            colors = listOf(
                Color(0xFF2196F3), Color(0xFF00BCD4), Color(0xFF009688),
                Color(0xFF26C6DA), Color(0xFF4FC3F7), Color(0xFF00897B),
            ),
            onPick = { c ->
                haptics.click()
                applyInspiration(c, selectedNode) { p, s, t ->
                    primary = p; secondary = s; tertiary = t
                }
            },
        )
        InspirationGroup(
            title = "Nature & Earth",
            colors = listOf(
                Color(0xFF2E7D32), Color(0xFF43A047), Color(0xFF66BB6A),
                Color(0xFF81C784), Color(0xFFA5D6A7), Color(0xFFC8E6C9),
            ),
            onPick = { c ->
                haptics.click()
                applyInspiration(c, selectedNode) { p, s, t ->
                    primary = p; secondary = s; tertiary = t
                }
            },
        )
    }
}

private fun applyInspiration(
    seed: Color,
    node: ColorNode,
    set: (Color, Color, Color) -> Unit,
) {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    val companionA = Color(
        ColorUtils.HSLToColor(
            floatArrayOf((hsl[0] + 24f) % 360f, (hsl[1] * 0.5f).coerceIn(0.12f, 0.55f), 0.42f),
        ),
    )
    val companionB = Color(
        ColorUtils.HSLToColor(
            floatArrayOf((hsl[0] + 48f) % 360f, (hsl[1] * 0.65f).coerceIn(0.18f, 0.7f), 0.55f),
        ),
    )
    when (node) {
        ColorNode.PRIMARY -> set(seed, companionA, companionB)
        ColorNode.SECONDARY -> set(companionA, seed, companionB)
        ColorNode.TERTIARY -> set(companionA, companionB, seed)
    }
}

@Composable
private fun InspirationGroup(
    title: String,
    colors: List<Color>,
    onPick: (Color) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            colors.forEach { c ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c)
                        .clickable { onPick(c) },
                )
            }
        }
    }
}

@Composable
private fun RoleSwatch(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) scheme.primary else scheme.outline.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onClick)
                .semantics { this.selected = selected },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun HexPill(
    color: Color,
    hex: String,
    isError: Boolean,
    onHexChange: (String) -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.22f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Palette, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = hex,
                onValueChange = onHexChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {}),
                modifier = Modifier.width(96.dp),
            )
        }
        if (isError) {
            Text(
                stringResource(R.string.hex_invalid_hint),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun HslSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    brush: Brush,
    onChange: (Float) -> Unit,
    onDragEnd: () -> Unit = {},
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Surface(
                shape = RoundedCornerShape(50),
                color = scheme.surfaceContainerHighest,
            ) {
                Text(
                    valueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        GradientSlider(
            value = value,
            valueRange = valueRange,
            brush = brush,
            onChange = onChange,
            onDragEnd = onDragEnd,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Material3 Slider over a gradient track — reliable drag/tap without custom pointer fights.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradientSlider(
    value: Float,
    brush: Brush,
    onChange: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val trackShape = RoundedCornerShape(50)
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onChange,
        onValueChangeFinished = onDragEnd,
        valueRange = valueRange,
        modifier = modifier.height(36.dp),
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(trackShape)
                    .background(brush),
            )
        },
    )
}

private fun parseStrictHex(raw: String): Color? {
    val cleaned = raw.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    val value = cleaned.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or value)
}
