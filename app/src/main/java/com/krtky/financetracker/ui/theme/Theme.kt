@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.krtky.financetracker.ui.theme

// Layout spacing tokens: see Dimens.kt / NavContentInsets (tab dock bottom inset, section gaps).

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krtky.financetracker.R

/**
 * Official Material 3 Expressive motion tokens
 * (from androidx.compose.material3.tokens.ExpressiveMotionTokens).
 */
object ExpressiveMotionTokens {
    const val SpringDefaultSpatialDamping = 0.8f
    const val SpringDefaultSpatialStiffness = 380.0f
    const val SpringDefaultEffectsDamping = 1.0f
    const val SpringDefaultEffectsStiffness = 1600.0f
    const val SpringFastSpatialDamping = 0.6f
    const val SpringFastSpatialStiffness = 800.0f
    const val SpringFastEffectsDamping = 1.0f
    const val SpringFastEffectsStiffness = 3800.0f
    const val SpringSlowSpatialDamping = 0.8f
    const val SpringSlowSpatialStiffness = 200.0f
    const val SpringSlowEffectsDamping = 1.0f
    const val SpringSlowEffectsStiffness = 800.0f
}

/**
 * Material 3 Expressive motion scheme (public-compatible implementation of
 * MotionScheme.expressive() springs).
 */
object M3EMotion {
    fun <T> spatialDefault(): FiniteAnimationSpec<T> = spring(
        dampingRatio = ExpressiveMotionTokens.SpringDefaultSpatialDamping,
        stiffness = ExpressiveMotionTokens.SpringDefaultSpatialStiffness,
    )

    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(
        dampingRatio = ExpressiveMotionTokens.SpringFastSpatialDamping,
        stiffness = ExpressiveMotionTokens.SpringFastSpatialStiffness,
    )

    fun <T> spatialSlow(): FiniteAnimationSpec<T> = spring(
        dampingRatio = ExpressiveMotionTokens.SpringSlowSpatialDamping,
        stiffness = ExpressiveMotionTokens.SpringSlowSpatialStiffness,
    )

    fun <T> effectsDefault(): FiniteAnimationSpec<T> = spring(
        dampingRatio = ExpressiveMotionTokens.SpringDefaultEffectsDamping,
        stiffness = ExpressiveMotionTokens.SpringDefaultEffectsStiffness,
    )

    fun <T> effectsFast(): FiniteAnimationSpec<T> = spring(
        dampingRatio = ExpressiveMotionTokens.SpringFastEffectsDamping,
        stiffness = ExpressiveMotionTokens.SpringFastEffectsStiffness,
    )

    fun <T> effectsSlow(): FiniteAnimationSpec<T> = spring(
        dampingRatio = ExpressiveMotionTokens.SpringSlowEffectsDamping,
        stiffness = ExpressiveMotionTokens.SpringSlowEffectsStiffness,
    )
}

enum class ThemeMode {
    MATERIAL_YOU,
    PRESET,
    CUSTOM,
}

enum class DarkModePref {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class ContrastLevel {
    LOW,
    MEDIUM,
    HIGH,
}

enum class TypographyMode {
    EXPRESSIVE,
    CONDENSED,
    SYSTEM,
}

enum class ThemePreset {
    EMERALD_MINT,
    MIDNIGHT_COBALT,
    SUNSET_CORAL,
    NORDIC_FROST,
    VIBRANT_PLUM,
    MONO_OLED,
    INDIGO,
    FOREST,
    OCEAN,
    SAFFRON_GOLD,
    ROSE_QUARTZ,
    AMBER_GLOW,
    LAVENDER_MIST,
    CRIMSON_WINE,
    MATCHA_TEA,
    SLATE_GRAPHITE,
    AURORA_BOREALIS,
    DESERT_SAND,
    ELECTRIC_VIOLET,
    RUBY_NIGHT,
    TEAL_WAVE,
}

data class ThemeColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

/**
 * Material 3 palette styles (inspired by Dynamic Scheme variants).
 * Used with a single seed color to auto-build primary/secondary/tertiary.
 */
enum class ColorSchemeStyle {
    TONAL_SPOT,
    VIBRANT,
    EXPRESSIVE,
    SPRITZ,
    RAINBOW,
    FRUIT_SALAD,
    CONTENT,
    MONOCHROME,
    FIDELITY,
    NEUTRAL,
    TONAL,
    FRUITY,
}

fun ColorSchemeStyle.displayName(): String = when (this) {
    ColorSchemeStyle.TONAL_SPOT -> "Tonal Spot"
    ColorSchemeStyle.VIBRANT -> "Vibrant"
    ColorSchemeStyle.EXPRESSIVE -> "Expressive"
    ColorSchemeStyle.SPRITZ -> "Spritz"
    ColorSchemeStyle.RAINBOW -> "Rainbow"
    ColorSchemeStyle.FRUIT_SALAD -> "Fruit Salad"
    ColorSchemeStyle.CONTENT -> "Content"
    ColorSchemeStyle.MONOCHROME -> "Monochrome"
    ColorSchemeStyle.FIDELITY -> "Fidelity"
    ColorSchemeStyle.NEUTRAL -> "Neutral"
    ColorSchemeStyle.TONAL -> "Tonal"
    ColorSchemeStyle.FRUITY -> "Fruity"
}

fun ColorSchemeStyle.description(): String = when (this) {
    ColorSchemeStyle.TONAL_SPOT -> "Balanced tonal palette from the seed"
    ColorSchemeStyle.VIBRANT -> "High chroma accents"
    ColorSchemeStyle.EXPRESSIVE -> "Playful hue shifts"
    ColorSchemeStyle.SPRITZ -> "Light, airy tints"
    ColorSchemeStyle.RAINBOW -> "Wide hue separation"
    ColorSchemeStyle.FRUIT_SALAD -> "Juicy complementary accents"
    ColorSchemeStyle.CONTENT -> "Seed-faithful content colors"
    ColorSchemeStyle.MONOCHROME -> "Grayscale from seed lightness"
    ColorSchemeStyle.FIDELITY -> "Close to the source hue"
    ColorSchemeStyle.NEUTRAL -> "Low-chroma secondary roles"
    ColorSchemeStyle.TONAL -> "Harmonic tonal steps"
    ColorSchemeStyle.FRUITY -> "Sweet warm/cool contrast"
}

/** Build a full 3-role theme from one seed color + M3-style scheme. */
fun generateThemeFromSeed(seed: Color, style: ColorSchemeStyle): ThemeColors {
    val out = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(
        android.graphics.Color.argb(
            (seed.alpha * 255).toInt().coerceIn(0, 255),
            (seed.red * 255).toInt().coerceIn(0, 255),
            (seed.green * 255).toInt().coerceIn(0, 255),
            (seed.blue * 255).toInt().coerceIn(0, 255),
        ),
        out,
    )
    val h = out[0]
    val s = out[1]
    val l = out[2]
    fun c(hh: Float, ss: Float, ll: Float): Color =
        Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(
            ((hh % 360f) + 360f) % 360f,
            ss.coerceIn(0f, 1f),
            ll.coerceIn(0f, 1f),
        )))

    return when (style) {
        ColorSchemeStyle.TONAL_SPOT -> ThemeColors(
            primary = seed,
            secondary = c(h + 12f, (s * 0.45f).coerceIn(0.15f, 0.55f), 0.42f),
            tertiary = c(h + 28f, (s * 0.55f).coerceIn(0.18f, 0.65f), 0.50f),
        )
        ColorSchemeStyle.VIBRANT -> ThemeColors(
            primary = c(h, s.coerceAtLeast(0.65f), 0.48f),
            secondary = c(h + 35f, 0.75f, 0.45f),
            tertiary = c(h - 40f, 0.70f, 0.52f),
        )
        ColorSchemeStyle.EXPRESSIVE -> ThemeColors(
            primary = seed,
            secondary = c(h + 55f, (s * 0.9f).coerceIn(0.4f, 0.9f), 0.48f),
            tertiary = c(h - 55f, (s * 0.85f).coerceIn(0.35f, 0.85f), 0.55f),
        )
        ColorSchemeStyle.SPRITZ -> ThemeColors(
            primary = c(h, (s * 0.55f).coerceIn(0.25f, 0.65f), 0.58f),
            secondary = c(h + 18f, 0.35f, 0.62f),
            tertiary = c(h - 22f, 0.40f, 0.60f),
        )
        ColorSchemeStyle.RAINBOW -> ThemeColors(
            primary = seed,
            secondary = c(h + 120f, 0.65f, 0.48f),
            tertiary = c(h + 240f, 0.60f, 0.50f),
        )
        ColorSchemeStyle.FRUIT_SALAD -> ThemeColors(
            primary = seed,
            secondary = c(h + 80f, 0.70f, 0.50f),
            tertiary = c(h + 160f, 0.65f, 0.48f),
        )
        ColorSchemeStyle.CONTENT -> ThemeColors(
            primary = seed,
            secondary = c(h, (s * 0.35f).coerceIn(0.12f, 0.45f), l.coerceIn(0.35f, 0.55f)),
            tertiary = c(h + 16f, (s * 0.4f).coerceIn(0.15f, 0.5f), 0.48f),
        )
        ColorSchemeStyle.MONOCHROME -> ThemeColors(
            primary = c(h, 0.05f, l.coerceIn(0.25f, 0.55f)),
            secondary = c(h, 0.03f, 0.45f),
            tertiary = c(h, 0.04f, 0.55f),
        )
        ColorSchemeStyle.FIDELITY -> ThemeColors(
            primary = seed,
            secondary = c(h + 8f, s.coerceIn(0.2f, 0.7f), (l * 0.9f).coerceIn(0.3f, 0.55f)),
            tertiary = c(h - 8f, (s * 0.8f).coerceIn(0.2f, 0.7f), 0.52f),
        )
        ColorSchemeStyle.NEUTRAL -> ThemeColors(
            primary = c(h, (s * 0.4f).coerceIn(0.15f, 0.5f), 0.45f),
            secondary = c(h, 0.12f, 0.42f),
            tertiary = c(h + 10f, 0.18f, 0.50f),
        )
        ColorSchemeStyle.TONAL -> ThemeColors(
            primary = c(h, s.coerceIn(0.3f, 0.7f), 0.42f),
            secondary = c(h, (s * 0.5f).coerceIn(0.15f, 0.55f), 0.55f),
            tertiary = c(h, (s * 0.35f).coerceIn(0.12f, 0.45f), 0.35f),
        )
        ColorSchemeStyle.FRUITY -> ThemeColors(
            primary = c(h, s.coerceAtLeast(0.55f), 0.50f),
            secondary = c(h + 45f, 0.68f, 0.52f),
            tertiary = c(h - 30f, 0.62f, 0.48f),
        )
    }
}

private data class PaletteSeed(
    val light: ThemeColors,
    val dark: ThemeColors,
)

private val Presets = mapOf(
    ThemePreset.EMERALD_MINT to PaletteSeed(
        light = ThemeColors(Color(0xFF006B5A), Color(0xFF2D6A5A), Color(0xFF00696E)),
        dark = ThemeColors(Color(0xFF6EDBC6), Color(0xFFA8E0D2), Color(0xFF80DFE0)),
    ),
    ThemePreset.MIDNIGHT_COBALT to PaletteSeed(
        light = ThemeColors(Color(0xFF1B3A8C), Color(0xFF3D5A8C), Color(0xFF4A5B8C)),
        dark = ThemeColors(Color(0xFFA3B8FF), Color(0xFFB8C4FF), Color(0xFFB0B8FF)),
    ),
    ThemePreset.SUNSET_CORAL to PaletteSeed(
        // Warm coral accents for the Sunset Coral preset
        light = ThemeColors(Color(0xFFC46A52), Color(0xFFB07A62), Color(0xFFC47868)),
        dark = ThemeColors(Color(0xFFF5B8A0), Color(0xFFE8C4B0), Color(0xFFF0A898)),
    ),
    ThemePreset.NORDIC_FROST to PaletteSeed(
        light = ThemeColors(Color(0xFF3A6E8C), Color(0xFF4A6680), Color(0xFF546E8C)),
        dark = ThemeColors(Color(0xFFA0C8E8), Color(0xFFB0C8E0), Color(0xFFB8C8E8)),
    ),
    ThemePreset.VIBRANT_PLUM to PaletteSeed(
        light = ThemeColors(Color(0xFF7C3A8C), Color(0xFF8C4A7C), Color(0xFF8C3A90)),
        dark = ThemeColors(Color(0xFFD8A8E8), Color(0xFFE0B8E8), Color(0xFFD8A0E8)),
    ),
    ThemePreset.MONO_OLED to PaletteSeed(
        light = ThemeColors(Color(0xFF2C2C2C), Color(0xFF484848), Color(0xFF585858)),
        dark = ThemeColors(Color(0xFFE0E0E0), Color(0xFFC0C0C0), Color(0xFFA0A0A0)),
    ),
    ThemePreset.INDIGO to PaletteSeed(
        // One confident action color, with restrained teal and amber companions.
        light = ThemeColors(Color(0xFF3157C9), Color(0xFF167C83), Color(0xFFC47A24)),
        dark = ThemeColors(Color(0xFF9AAEFF), Color(0xFF72D1D0), Color(0xFFFFBB70)),
    ),
    ThemePreset.FOREST to PaletteSeed(
        light = ThemeColors(Color(0xFF2E7D5B), Color(0xFF4D6858), Color(0xFF3E7D89)),
        dark = ThemeColors(Color(0xFF8BD8B5), Color(0xFFB8CBBF), Color(0xFF97D8E0)),
    ),
    ThemePreset.OCEAN to PaletteSeed(
        light = ThemeColors(Color(0xFF006A6A), Color(0xFF4A6363), Color(0xFF4A607C)),
        dark = ThemeColors(Color(0xFF4EDAD9), Color(0xFFB0CCCC), Color(0xFFB2C8E8)),
    ),
    ThemePreset.SAFFRON_GOLD to PaletteSeed(
        light = ThemeColors(Color(0xFFB36B00), Color(0xFF8B5E1A), Color(0xFF9A4A28)),
        dark = ThemeColors(Color(0xFFFFB95C), Color(0xFFE8C48A), Color(0xFFFFB090)),
    ),
    ThemePreset.ROSE_QUARTZ to PaletteSeed(
        light = ThemeColors(Color(0xFF9B4D6B), Color(0xFF8A5A6E), Color(0xFFA05A48)),
        dark = ThemeColors(Color(0xFFFFB0C8), Color(0xFFE8B8C8), Color(0xFFFFB8A0)),
    ),
    ThemePreset.AMBER_GLOW to PaletteSeed(
        light = ThemeColors(Color(0xFF9A6700), Color(0xFF7A5C18), Color(0xFF8B4D1A)),
        dark = ThemeColors(Color(0xFFFFC14D), Color(0xFFE0C080), Color(0xFFFFB070)),
    ),
    ThemePreset.LAVENDER_MIST to PaletteSeed(
        light = ThemeColors(Color(0xFF6B5B95), Color(0xFF6E6288), Color(0xFF7A5A8C)),
        dark = ThemeColors(Color(0xFFD0C0FF), Color(0xFFC8C0E0), Color(0xFFE0B8F0)),
    ),
    ThemePreset.CRIMSON_WINE to PaletteSeed(
        light = ThemeColors(Color(0xFF8B1E3F), Color(0xFF7A3A48), Color(0xFF8C3A2E)),
        dark = ThemeColors(Color(0xFFFFB0C0), Color(0xFFE8B0B8), Color(0xFFFFB0A0)),
    ),
    ThemePreset.MATCHA_TEA to PaletteSeed(
        light = ThemeColors(Color(0xFF4A6B2F), Color(0xFF5A6848), Color(0xFF6B5A2E)),
        dark = ThemeColors(Color(0xFFB0D890), Color(0xFFC0D0B0), Color(0xFFD0C890)),
    ),
    ThemePreset.SLATE_GRAPHITE to PaletteSeed(
        light = ThemeColors(Color(0xFF3D4F5F), Color(0xFF4A5560), Color(0xFF556070)),
        dark = ThemeColors(Color(0xFFB0C4D8), Color(0xFFB8C0C8), Color(0xFFC0C8D8)),
    ),
    ThemePreset.AURORA_BOREALIS to PaletteSeed(
        light = ThemeColors(Color(0xFF007A6E), Color(0xFF5B4A8C), Color(0xFF2A6B8C)),
        dark = ThemeColors(Color(0xFF5EE8D8), Color(0xFFC8B0FF), Color(0xFF80D0F0)),
    ),
    ThemePreset.DESERT_SAND to PaletteSeed(
        light = ThemeColors(Color(0xFF8B6B45), Color(0xFF7A6550), Color(0xFF9A5A40)),
        dark = ThemeColors(Color(0xFFE8C8A0), Color(0xFFD8C8B0), Color(0xFFF0B898)),
    ),
    ThemePreset.ELECTRIC_VIOLET to PaletteSeed(
        light = ThemeColors(Color(0xFF6B2FBF), Color(0xFF5A4A9A), Color(0xFF8C2A8C)),
        dark = ThemeColors(Color(0xFFD0A0FF), Color(0xFFB8B0FF), Color(0xFFF0A0E8)),
    ),
    ThemePreset.RUBY_NIGHT to PaletteSeed(
        light = ThemeColors(Color(0xFFA01830), Color(0xFF7A3040), Color(0xFF8C2A50)),
        dark = ThemeColors(Color(0xFFFF8A9A), Color(0xFFE8A0A8), Color(0xFFFFA0C0)),
    ),
    ThemePreset.TEAL_WAVE to PaletteSeed(
        light = ThemeColors(Color(0xFF007A8C), Color(0xFF3A6A72), Color(0xFF006B8A)),
        dark = ThemeColors(Color(0xFF5ED8F0), Color(0xFFA0C8D0), Color(0xFF70C8F0)),
    ),
)

fun ThemePreset.displayName(): String = when (this) {
    ThemePreset.EMERALD_MINT -> "Emerald Mint"
    ThemePreset.MIDNIGHT_COBALT -> "Midnight Cobalt"
    ThemePreset.SUNSET_CORAL -> "Sunset Coral"
    ThemePreset.NORDIC_FROST -> "Nordic Frost"
    ThemePreset.VIBRANT_PLUM -> "Vibrant Plum"
    ThemePreset.MONO_OLED -> "OLED Mono"
    ThemePreset.INDIGO -> "Classic Indigo"
    ThemePreset.FOREST -> "Forest Green"
    ThemePreset.OCEAN -> "Deep Ocean"
    ThemePreset.SAFFRON_GOLD -> "Saffron Gold"
    ThemePreset.ROSE_QUARTZ -> "Rose Quartz"
    ThemePreset.AMBER_GLOW -> "Amber Glow"
    ThemePreset.LAVENDER_MIST -> "Lavender Mist"
    ThemePreset.CRIMSON_WINE -> "Crimson Wine"
    ThemePreset.MATCHA_TEA -> "Matcha Tea"
    ThemePreset.SLATE_GRAPHITE -> "Slate Graphite"
    ThemePreset.AURORA_BOREALIS -> "Aurora"
    ThemePreset.DESERT_SAND -> "Desert Sand"
    ThemePreset.ELECTRIC_VIOLET -> "Electric Violet"
    ThemePreset.RUBY_NIGHT -> "Ruby Night"
    ThemePreset.TEAL_WAVE -> "Teal Wave"
}

fun ThemePreset.description(): String = when (this) {
    ThemePreset.EMERALD_MINT -> "Calming green tones inspired by nature and growth"
    ThemePreset.MIDNIGHT_COBALT -> "Deep professional blue for focused clarity"
    ThemePreset.SUNSET_CORAL -> "Soft peach and warm coral — dark UI with a cozy balance hero"
    ThemePreset.NORDIC_FROST -> "Clean icy blues for a serene, minimal experience"
    ThemePreset.VIBRANT_PLUM -> "Rich purple tones for a luxurious aesthetic"
    ThemePreset.MONO_OLED -> "True black optimized for AMOLED screens"
    ThemePreset.INDIGO -> "Material 3 baseline indigo with soft accents"
    ThemePreset.FOREST -> "Natural green tones inspired by nature"
    ThemePreset.OCEAN -> "Refreshing teal and blue for a calming vibe"
    ThemePreset.SAFFRON_GOLD -> "Warm turmeric gold — festive and finance-friendly"
    ThemePreset.ROSE_QUARTZ -> "Soft blush pink with warm copper accents"
    ThemePreset.AMBER_GLOW -> "Honey amber for a golden-hour feel"
    ThemePreset.LAVENDER_MIST -> "Gentle lilac for a calm, creative mood"
    ThemePreset.CRIMSON_WINE -> "Deep wine red with refined contrast"
    ThemePreset.MATCHA_TEA -> "Muted green-tea calm for everyday tracking"
    ThemePreset.SLATE_GRAPHITE -> "Cool charcoal-blue for a pro dashboard look"
    ThemePreset.AURORA_BOREALIS -> "Teal, violet, and sky — northern-lights energy"
    ThemePreset.DESERT_SAND -> "Sun-baked sand and clay neutrals"
    ThemePreset.ELECTRIC_VIOLET -> "Bold neon-violet for high-energy vibes"
    ThemePreset.RUBY_NIGHT -> "Jewel-tone ruby with night-out drama"
    ThemePreset.TEAL_WAVE -> "Crisp teal currents and coastal blues"
}

fun ThemePreset.isFeatured(): Boolean = when (this) {
    ThemePreset.EMERALD_MINT, ThemePreset.MIDNIGHT_COBALT, ThemePreset.SUNSET_CORAL,
    ThemePreset.NORDIC_FROST, ThemePreset.VIBRANT_PLUM, ThemePreset.MONO_OLED,
    ThemePreset.SAFFRON_GOLD, ThemePreset.ROSE_QUARTZ, ThemePreset.AURORA_BOREALIS,
    ThemePreset.MATCHA_TEA, ThemePreset.ELECTRIC_VIOLET, ThemePreset.TEAL_WAVE,
    -> true
    else -> false
}

fun ThemePreset.previewColors(darkTheme: Boolean = false): ThemeColors {
    val seed = Presets[this] ?: Presets.getValue(ThemePreset.INDIGO)
    return if (darkTheme) seed.dark else seed.light
}

fun Color.toHexRgb(): String {
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return if (a >= 255) "#%02X%02X%02X".format(r, g, b)
    else "#%02X%02X%02X%02X".format(a, r, g, b)
}

private fun String.parseColorOrNull(): Color? {
    val cleaned = trim().removePrefix("#")
    val value = cleaned.toLongOrNull(16) ?: return null
    return when (cleaned.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> null
    }
}

fun colorOrDefault(raw: String?, fallback: Color): Color = raw?.parseColorOrNull() ?: fallback

private fun baseScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()

/** Slightly deeper dark surfaces so content cards lift off the page. */
private fun ColorScheme.withDeeperDarkSurfaces(darkTheme: Boolean): ColorScheme {
    if (!darkTheme) return this
    return copy(
        background = Color(0xFF0B0F16),
        surface = Color(0xFF10151E),
        surfaceVariant = Color(0xFF1A212C),
        surfaceContainerLowest = Color(0xFF080B11),
        surfaceContainerLow = Color(0xFF0D121A),
        surfaceContainer = Color(0xFF131923),
        surfaceContainerHigh = Color(0xFF19212C),
        surfaceContainerHighest = Color(0xFF222C39),
        onBackground = Color(0xFFF0F3F8),
        onSurface = Color(0xFFF0F3F8),
        onSurfaceVariant = Color(0xFFAAB6C7),
    )
}

private fun Color.toHsl(): FloatArray {
    val out = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(
        android.graphics.Color.argb(
            (alpha * 255).toInt().coerceIn(0, 255),
            (red * 255).toInt().coerceIn(0, 255),
            (green * 255).toInt().coerceIn(0, 255),
            (blue * 255).toInt().coerceIn(0, 255),
        ),
        out,
    )
    return out
}

private fun hslColor(h: Float, s: Float, l: Float): Color =
    Color(
        androidx.core.graphics.ColorUtils.HSLToColor(
            floatArrayOf(
                ((h % 360f) + 360f) % 360f,
                s.coerceIn(0f, 1f),
                l.coerceIn(0f, 1f),
            ),
        ),
    )

/**
 * Dark, readable ink that keeps the accent hue — for text/icons on light accent fills.
 * Tuned for ~WCAG AA on soft pastels (lightness ~0.20–0.28).
 */
private fun darkAccentInk(accent: Color): Color {
    val hsl = accent.toHsl()
    val sat = when {
        hsl[1] < 0.08f -> 0.04f // near-neutral accents stay neutral
        else -> hsl[1].coerceIn(0.40f, 0.78f)
    }
    return hslColor(hsl[0], sat, 0.24f)
}

/** Light ink for text/icons on dark accent fills (slight hue tint, not pure white). */
private fun lightAccentInk(accent: Color): Color {
    val hsl = accent.toHsl()
    val sat = (hsl[1] * 0.22f).coerceIn(0.02f, 0.28f)
    return hslColor(hsl[0], sat, 0.97f)
}

/**
 * Content color for an accent container: dark accent on light fills, light ink on dark fills.
 * Uses relative luminance of the actual background, not theme mode.
 */
private fun contentOnContainer(container: Color, accent: Color): Color =
    if (container.luminance() >= 0.42f) darkAccentInk(accent) else lightAccentInk(accent)

/** Solid primary / secondary / tertiary buttons use the accent itself as fill. */
private fun contentOnSolidAccent(accent: Color): Color =
    if (accent.luminance() >= 0.42f) darkAccentInk(accent) else lightAccentInk(accent)

/** Lift primaryContainer so hero / nav pill / FAB chrome share one accent fill. */
private fun ColorScheme.withBrightAccentChrome(darkTheme: Boolean): ColorScheme {
    val primaryContainer = if (darkTheme) {
        primary.copy(alpha = 0.88f).compositeOver(surfaceContainerHigh)
    } else {
        // Soft light accent wash in light mode (dark ink sits on top)
        primary.copy(alpha = 0.18f).compositeOverWhite()
    }
    return copy(
        primaryContainer = primaryContainer,
        onPrimaryContainer = contentOnContainer(primaryContainer, primary),
    )
}

/**
 * Secondary / tonal buttons (FilledTonalButton) use [ColorScheme.secondaryContainer].
 * Keep that fill a very light, low-opacity accent wash — never a near-solid accent slab.
 */
private fun softSecondaryContainer(darkTheme: Boolean, accent: Color, under: Color): Color =
    if (darkTheme) {
        accent.copy(alpha = 0.14f).compositeOver(under)
    } else {
        accent.copy(alpha = 0.08f).compositeOverWhite()
    }

/** Apply soft secondary containers for Material You / any base scheme. */
private fun ColorScheme.withSoftSecondaryChrome(darkTheme: Boolean): ColorScheme {
    val secondaryContainer = softSecondaryContainer(darkTheme, secondary, surfaceContainer)
    return copy(
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = contentOnContainer(secondaryContainer, secondary),
    )
}

private fun themedScheme(darkTheme: Boolean, colors: ThemeColors): ColorScheme {
    val base = baseScheme(darkTheme).withDeeperDarkSurfaces(darkTheme)
    // Hero / selected chrome use primaryContainer; the rest of the UI stays neutral.
    // Light mode: pastel accent wash + dark accent text.
    // Dark mode: vivid accent wash + dark accent text when the fill is light.
    val primaryContainer = if (darkTheme) {
        colors.primary.copy(alpha = 0.88f).compositeOver(base.surfaceContainer)
    } else {
        colors.primary.copy(alpha = 0.18f).compositeOverWhite()
    }
    // Secondary / tonal buttons: light wash only (see withSoftSecondaryChrome).
    val secondaryContainer = softSecondaryContainer(darkTheme, colors.secondary, base.surfaceContainer)
    val tertiaryContainer = if (darkTheme) {
        colors.tertiary.copy(alpha = 0.16f).compositeOver(base.surfaceContainer)
    } else {
        colors.tertiary.copy(alpha = 0.10f).compositeOverWhite()
    }
    return base.copy(
        primary = colors.primary,
        onPrimary = contentOnSolidAccent(colors.primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = contentOnContainer(primaryContainer, colors.primary),
        secondary = colors.secondary,
        onSecondary = contentOnSolidAccent(colors.secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = contentOnContainer(secondaryContainer, colors.secondary),
        tertiary = colors.tertiary,
        onTertiary = contentOnSolidAccent(colors.tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = contentOnContainer(tertiaryContainer, colors.tertiary),
    )
}

/** Blend [this] (with alpha) over an opaque [under] color. */
private fun Color.compositeOver(under: Color): Color {
    val a = alpha.coerceIn(0f, 1f)
    return Color(
        red = red * a + under.red * (1f - a),
        green = green * a + under.green * (1f - a),
        blue = blue * a + under.blue * (1f - a),
        alpha = 1f,
    )
}

/** Approximate light-surface blend for solid light-mode containers. */
private fun Color.compositeOverWhite(): Color {
    val a = alpha.coerceIn(0f, 1f)
    return Color(
        red = red * a + (1f - a),
        green = green * a + (1f - a),
        blue = blue * a + (1f - a),
        alpha = 1f,
    )
}

/** Soft, pill-forward radii inspired by modern finance apps. */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Google Sans Flex — variable font with weight / width / rounded terminals.
 *
 * Instances are tuned so large display numbers sit a bit condensed, body text
 * is neutral, and labels open up slightly for balanced density.
 * ROND=100 keeps rounded terminals everywhere.
 */
private fun gsf(
    weight: Int,
    width: Float = 100f,
    opsz: Float = 16f,
    round: Float = 100f,
): Font = Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(width),
        FontVariation.Setting("opsz", opsz),
        FontVariation.Setting("ROND", round),
    ),
)

val GoogleSansFlex = FontFamily(
    gsf(weight = 300, width = 110f, opsz = 14f),
    gsf(weight = 350, width = 110f, opsz = 14f),
    gsf(weight = 400, width = 110f, opsz = 16f),
    gsf(weight = 450, width = 112f, opsz = 16f),
    gsf(weight = 500, width = 110f, opsz = 17f),
    gsf(weight = 550, width = 108f, opsz = 20f),
    gsf(weight = 600, width = 106f, opsz = 24f),
    gsf(weight = 650, width = 104f, opsz = 28f),
    gsf(weight = 700, width = 102f, opsz = 36f),
    gsf(weight = 800, width = 100f, opsz = 48f),
)

/** @deprecated Prefer [GoogleSansFlex]. */
val RobotoFlex = GoogleSansFlex

/** @deprecated Prefer [GoogleSansFlex]. */
val Michroma = GoogleSansFlex

private fun gsfCondensed(
    weight: Int,
    width: Float = 85f,
    opsz: Float = 16f,
    round: Float = 100f,
): Font = Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(width),
        FontVariation.Setting("opsz", opsz),
        FontVariation.Setting("ROND", round),
    ),
)

private val CondensedGoogleSansFlex = FontFamily(
    gsfCondensed(weight = 300, width = 85f, opsz = 14f),
    gsfCondensed(weight = 350, width = 85f, opsz = 14f),
    gsfCondensed(weight = 400, width = 85f, opsz = 16f),
    gsfCondensed(weight = 450, width = 85f, opsz = 16f),
    gsfCondensed(weight = 500, width = 85f, opsz = 17f),
    gsfCondensed(weight = 550, width = 85f, opsz = 20f),
    gsfCondensed(weight = 600, width = 85f, opsz = 24f),
    gsfCondensed(weight = 650, width = 85f, opsz = 28f),
    gsfCondensed(weight = 700, width = 85f, opsz = 36f),
    gsfCondensed(weight = 800, width = 85f, opsz = 48f),
)

private fun buildTypography(fontFamily: FontFamily) = Typography(
    displayLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(650),
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(650),
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(600),
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(600),
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(600),
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(550),
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(550),
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(500),
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(500),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(400),
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(400),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(400),
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(500),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(500),
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(500),
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    ),
)

private val ExpressiveTypography = buildTypography(GoogleSansFlex)
private val CondensedTypography = buildTypography(CondensedGoogleSansFlex)

/**
 * Contrast levels adjust *relative* separation between surfaces, text, and outlines
 * (M3-style accessibility), not by dimming RGB channels of the whole scheme.
 *
 * - LOW: baseline scheme (softer hierarchy)
 * - MEDIUM: clearer body text + secondary text, stronger outlines
 * - HIGH: near-max text contrast, bold outlines, wider surface steps
 */
private fun ColorScheme.withContrast(level: ContrastLevel, darkTheme: Boolean): ColorScheme {
    if (level == ContrastLevel.LOW) return this

    val textBoost = when (level) {
        ContrastLevel.MEDIUM -> if (darkTheme) 0.06f else -0.08f
        ContrastLevel.HIGH -> if (darkTheme) 0.12f else -0.14f
        ContrastLevel.LOW -> 0f
    }
    val secondaryBoost = when (level) {
        ContrastLevel.MEDIUM -> if (darkTheme) 0.10f else -0.10f
        ContrastLevel.HIGH -> if (darkTheme) 0.18f else -0.18f
        ContrastLevel.LOW -> 0f
    }
    val outlineBoost = when (level) {
        ContrastLevel.MEDIUM -> if (darkTheme) 0.12f else -0.12f
        ContrastLevel.HIGH -> if (darkTheme) 0.22f else -0.22f
        ContrastLevel.LOW -> 0f
    }

    fun Color.nudgeLightness(delta: Float): Color {
        val hsl = toHsl()
        return hslColor(hsl[0], hsl[1], (hsl[2] + delta).coerceIn(0f, 1f))
    }

    // Widen elevation steps so cards separate more clearly from the page.
    val step = when (level) {
        ContrastLevel.MEDIUM -> if (darkTheme) 0.02f else -0.015f
        ContrastLevel.HIGH -> if (darkTheme) 0.04f else -0.03f
        ContrastLevel.LOW -> 0f
    }

    val next = copy(
        onBackground = onBackground.nudgeLightness(textBoost),
        onSurface = onSurface.nudgeLightness(textBoost),
        onSurfaceVariant = onSurfaceVariant.nudgeLightness(secondaryBoost),
        outline = outline.nudgeLightness(outlineBoost),
        outlineVariant = outlineVariant.nudgeLightness(outlineBoost * 0.65f),
        surfaceContainerLowest = surfaceContainerLowest.nudgeLightness(if (darkTheme) -step else step * 0.5f),
        surfaceContainerLow = surfaceContainerLow.nudgeLightness(step * 0.4f),
        surfaceContainer = surfaceContainer.nudgeLightness(step * 0.7f),
        surfaceContainerHigh = surfaceContainerHigh.nudgeLightness(step),
        surfaceContainerHighest = surfaceContainerHighest.nudgeLightness(step * 1.25f),
        surfaceVariant = surfaceVariant.nudgeLightness(step * 0.8f),
    )

    // Keep accent-on-container pairs legible after any surface shifts; high contrast
    // pushes container ink further toward the extremes while preserving hue.
    fun contrastInk(container: Color, accent: Color, onCurrent: Color): Color {
        val base = contentOnContainer(container, accent)
        return when (level) {
            ContrastLevel.HIGH -> {
                val hsl = base.toHsl()
                if (container.luminance() >= 0.42f) {
                    hslColor(hsl[0], hsl[1].coerceAtLeast(0.35f), 0.14f)
                } else {
                    hslColor(hsl[0], (hsl[1] * 0.15f).coerceIn(0f, 0.2f), 0.99f)
                }
            }
            ContrastLevel.MEDIUM -> base
            ContrastLevel.LOW -> onCurrent
        }
    }

    return next.copy(
        onPrimaryContainer = contrastInk(next.primaryContainer, next.primary, next.onPrimaryContainer),
        onSecondaryContainer = contrastInk(next.secondaryContainer, next.secondary, next.onSecondaryContainer),
        onTertiaryContainer = contrastInk(next.tertiaryContainer, next.tertiary, next.onTertiaryContainer),
        onPrimary = contentOnSolidAccent(next.primary),
        onSecondary = contentOnSolidAccent(next.secondary),
        onTertiary = contentOnSolidAccent(next.tertiary),
    )
}

private fun ColorScheme.withOled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF101010),
    surfaceVariant = Color(0xFF111111),
)

/**
 * Material 3 Expressive-oriented theme:
 * - MaterialExpressiveTheme + MotionScheme.expressive()
 * - Expressive corner radii + motion tokens (M3EMotion)
 * - Dynamic color (Material You) layered on M3 roles when available
 * - Accent containers use dark accent ink on light fills (readable chrome)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RupiyahTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode = ThemeMode.MATERIAL_YOU,
    themePreset: ThemePreset = ThemePreset.INDIGO,
    customColors: ThemeColors = ThemeColors(Color(0xFF3157C9), Color(0xFF167C83), Color(0xFFC47A24)),
    typographyMode: TypographyMode = TypographyMode.EXPRESSIVE,
    contrastLevel: ContrastLevel = ContrastLevel.LOW,
    oledMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        themeMode == ThemeMode.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dyn = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dyn.withDeeperDarkSurfaces(darkTheme)
                .withBrightAccentChrome(darkTheme)
                .withSoftSecondaryChrome(darkTheme)
        }
        themeMode == ThemeMode.CUSTOM -> themedScheme(darkTheme, customColors)
        else -> {
            val seed = Presets[themePreset] ?: Presets.getValue(ThemePreset.INDIGO)
            themedScheme(darkTheme, if (darkTheme) seed.dark else seed.light)
        }
    }.withContrast(contrastLevel, darkTheme).let { if (oledMode && darkTheme) it.withOled() else it }

    val typography = when (typographyMode) {
        TypographyMode.CONDENSED -> CondensedTypography
        TypographyMode.SYSTEM -> Typography()
        TypographyMode.EXPRESSIVE -> ExpressiveTypography
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = typography,
        content = content,
    )
}
