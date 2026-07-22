package com.krtky.financetracker.ui.theme

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
}

data class ThemeColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

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
        light = ThemeColors(Color(0xFFE85D4A), Color(0xFFC46A4A), Color(0xFFD4626A)),
        dark = ThemeColors(Color(0xFFFFB0A0), Color(0xFFFFC4A8), Color(0xFFFFB0B8)),
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
        light = ThemeColors(Color(0xFF4253D4), Color(0xFF5B647A), Color(0xFF7153A8)),
        dark = ThemeColors(Color(0xFFB8C4FF), Color(0xFFC7CDE1), Color(0xFFD7B9FF)),
    ),
    ThemePreset.FOREST to PaletteSeed(
        light = ThemeColors(Color(0xFF2E7D5B), Color(0xFF4D6858), Color(0xFF3E7D89)),
        dark = ThemeColors(Color(0xFF8BD8B5), Color(0xFFB8CBBF), Color(0xFF97D8E0)),
    ),
    ThemePreset.OCEAN to PaletteSeed(
        light = ThemeColors(Color(0xFF006A6A), Color(0xFF4A6363), Color(0xFF4A607C)),
        dark = ThemeColors(Color(0xFF4EDAD9), Color(0xFFB0CCCC), Color(0xFFB2C8E8)),
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
}

fun ThemePreset.description(): String = when (this) {
    ThemePreset.EMERALD_MINT -> "Calming green tones inspired by nature and growth"
    ThemePreset.MIDNIGHT_COBALT -> "Deep professional blue for focused clarity"
    ThemePreset.SUNSET_CORAL -> "Warm coral and rose tones for an inviting feel"
    ThemePreset.NORDIC_FROST -> "Clean icy blues for a serene, minimal experience"
    ThemePreset.VIBRANT_PLUM -> "Rich purple tones for a luxurious aesthetic"
    ThemePreset.MONO_OLED -> "True black optimized for AMOLED screens"
    ThemePreset.INDIGO -> "Material 3 baseline indigo with soft accents"
    ThemePreset.FOREST -> "Natural green tones inspired by nature"
    ThemePreset.OCEAN -> "Refreshing teal and blue for a calming vibe"
}

fun ThemePreset.isFeatured(): Boolean = when (this) {
    ThemePreset.EMERALD_MINT, ThemePreset.MIDNIGHT_COBALT, ThemePreset.SUNSET_CORAL,
    ThemePreset.NORDIC_FROST, ThemePreset.VIBRANT_PLUM, ThemePreset.MONO_OLED,
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

private fun themedScheme(darkTheme: Boolean, colors: ThemeColors): ColorScheme {
    val base = baseScheme(darkTheme)
    val primaryContainer = if (darkTheme) colors.primary.copy(alpha = 0.25f) else colors.primary.copy(alpha = 0.16f)
    val secondaryContainer = if (darkTheme) colors.secondary.copy(alpha = 0.25f) else colors.secondary.copy(alpha = 0.16f)
    val tertiaryContainer = if (darkTheme) colors.tertiary.copy(alpha = 0.25f) else colors.tertiary.copy(alpha = 0.16f)
    return base.copy(
        primary = colors.primary,
        onPrimary = if (darkTheme) Color(0xFF071019) else Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = if (darkTheme) colors.primary else colors.primary,
        secondary = colors.secondary,
        onSecondary = if (darkTheme) Color(0xFF071019) else Color.White,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = if (darkTheme) colors.secondary else colors.secondary,
        tertiary = colors.tertiary,
        onTertiary = if (darkTheme) Color(0xFF071019) else Color.White,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = if (darkTheme) colors.tertiary else colors.tertiary,
    )
}

/** M3 shapes — less bubble, more structured. */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/**
 * Roboto weight ramp (Thin→Black) for M3 Expressive “funky” hierarchy.
 * Bundled as static masters (Roboto Flex variable TTF not packageable as res/font).
 */
val RobotoFlex = FontFamily(
    Font(R.font.roboto_thin, weight = FontWeight.Thin),
    Font(R.font.roboto_light, weight = FontWeight.Light),
    Font(R.font.roboto_regular, weight = FontWeight.Normal),
    Font(R.font.roboto_medium, weight = FontWeight.Medium),
    Font(R.font.roboto_bold, weight = FontWeight.Bold),
    Font(R.font.roboto_bold, weight = FontWeight.SemiBold),
    Font(R.font.roboto_black, weight = FontWeight.ExtraBold),
    Font(R.font.roboto_black, weight = FontWeight.Black),
)

private val ExpressiveTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Black,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Light,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

/**
 * Material 3 Expressive-oriented theme:
 * - Expressive corner radii + motion tokens (M3EMotion)
 * - Dynamic color (Material You) layered on M3 roles when available
 * - Baseline M3 schemes otherwise (not custom brand hex)
 */
@Composable
fun RupiyahTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode = ThemeMode.MATERIAL_YOU,
    themePreset: ThemePreset = ThemePreset.INDIGO,
    customColors: ThemeColors = ThemeColors(Color(0xFF4253D4), Color(0xFF5B647A), Color(0xFF7153A8)),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        themeMode == ThemeMode.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Material You tones mapped into M3 color roles (Expressive-compatible)
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeMode == ThemeMode.CUSTOM -> themedScheme(darkTheme, customColors)
        else -> {
            val seed = Presets[themePreset] ?: Presets.getValue(ThemePreset.INDIGO)
            themedScheme(darkTheme, if (darkTheme) seed.dark else seed.light)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
        content = content,
    )
}
