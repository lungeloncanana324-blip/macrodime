/*
 * Theme.kt
 * MacroDime
 *
 * Direction: ink and paper, with the food doing the colour. The interface is
 * nearly monochrome (ink buttons and selections on warm paper, white cards),
 * so the photography and the macro colours are what the eye lands on. One
 * accent, basil, marks what is good: a saving, a free trial, money left.
 * Tomato is reserved for one job, going over budget, so it always means it.
 * The macros sit in food colours (raspberry, wheat, avocado), each a distinct
 * hue so the rings never need their labels to be told apart, though every
 * ring still has one.
 *
 * Dynamic colour stays off, so a wallpaper cannot turn the budget meter's red
 * into a decoration. The type is Plus Jakarta Sans (SIL Open Font License,
 * assets/licenses), bundled because the app has no network to fetch fonts with.
 */
package com.lungelo.macrodime.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lungelo.macrodime.R

/** Colours outside Material's roles: the accent, the macro palette and the budget signal. */
@Immutable
data class BrandColors(
    /** Basil. Savings, free trials, money left, the selected chip: what is good. */
    val accent: Color,
    /** The accent as a fill behind small text. */
    val accentSoft: Color,
    /** Saffron. The flexible price tier, and warmth where green would claim "good". */
    val warm: Color,
    val calories: Color,
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val underBudget: Color,
    val overBudget: Color,
    val measurement: Color,
    val pantry: Color,
    val caution: Color,
)

private val LightBrand = BrandColors(
    accent = Color(0xFF2E7A4E),
    accentSoft = Color(0xFFDFF0E5),
    warm = Color(0xFFB86E0B),
    calories = Color(0xFF1A1A18),
    protein = Color(0xFFD23F6A),
    carbs = Color(0xFFDD8F22),
    fat = Color(0xFF74963A),
    underBudget = Color(0xFF2E7A4E),
    overBudget = Color(0xFFD33A2C),
    measurement = Color(0xFF3C7A85),
    pantry = Color(0xFF5B4B8A),
    caution = Color(0xFFB45F06),
)

private val DarkBrand = BrandColors(
    accent = Color(0xFF6CC791),
    accentSoft = Color(0xFF1C3A28),
    warm = Color(0xFFEDB04B),
    calories = Color(0xFFF3F1EC),
    protein = Color(0xFFF0709A),
    carbs = Color(0xFFF4B556),
    fat = Color(0xFFA9C861),
    underBudget = Color(0xFF6CC791),
    overBudget = Color(0xFFFF6B57),
    measurement = Color(0xFF64BECB),
    pantry = Color(0xFF9B8BD0),
    caution = Color(0xFFF5A524),
)

private val Ink = Color(0xFF161614)
private val Paper = Color(0xFFF6F4EF)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAE7E0),
    onPrimaryContainer = Ink,
    secondary = Color(0xFF2E7A4E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFF0E5),
    onSecondaryContainer = Color(0xFF0E3B22),
    tertiary = Color(0xFF3C7A85),
    onTertiary = Color(0xFFFFFFFF),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAE6DE),
    onSurfaceVariant = Color(0xFF67635B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFBFAF7),
    surfaceContainerHigh = Color(0xFFF0EDE6),
    surfaceContainerHighest = Color(0xFFE7E3DB),
    outline = Color(0xFF8E897F),
    outlineVariant = Color(0xFFE3DED5),
    error = Color(0xFFD33A2C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFADAD5),
    onErrorContainer = Color(0xFF5C120A),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFF3F1EC),
    onPrimary = Color(0xFF121211),
    primaryContainer = Color(0xFF2C2B28),
    onPrimaryContainer = Color(0xFFF3F1EC),
    secondary = Color(0xFF6CC791),
    onSecondary = Color(0xFF08301A),
    secondaryContainer = Color(0xFF1C3A28),
    onSecondaryContainer = Color(0xFFC9EDD6),
    tertiary = Color(0xFF64BECB),
    onTertiary = Color(0xFF00363D),
    background = Color(0xFF0E0E0D),
    onBackground = Color(0xFFF3F1EC),
    surface = Color(0xFF0E0E0D),
    onSurface = Color(0xFFF3F1EC),
    surfaceVariant = Color(0xFF2B2A27),
    onSurfaceVariant = Color(0xFFA9A49A),
    surfaceContainerLowest = Color(0xFF0A0A09),
    surfaceContainerLow = Color(0xFF1A1918),
    surfaceContainer = Color(0xFF1F1E1C),
    surfaceContainerHigh = Color(0xFF262523),
    surfaceContainerHighest = Color(0xFF302F2C),
    outline = Color(0xFF77736B),
    outlineVariant = Color(0xFF34322E),
    error = Color(0xFFFF6B57),
    onError = Color(0xFF4A0B03),
    errorContainer = Color(0xFF6B1A10),
    onErrorContainer = Color(0xFFFFDAD4),
)

/** Plus Jakarta Sans, five weights. A weight asked for between two is drawn with the nearest. */
val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Jakarta,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

/** Tight, heavy headlines over calm body text: the headline says it, the body backs it up. */
private val AppTypography = Typography(
    displayLarge = style(52, 58, FontWeight.ExtraBold, -0.03),
    displayMedium = style(44, 50, FontWeight.ExtraBold, -0.03),
    displaySmall = style(36, 42, FontWeight.ExtraBold, -0.025),
    headlineLarge = style(32, 38, FontWeight.ExtraBold, -0.02),
    headlineMedium = style(28, 34, FontWeight.Bold, -0.015),
    headlineSmall = style(24, 30, FontWeight.Bold, -0.01),
    titleLarge = style(21, 27, FontWeight.Bold, -0.005),
    titleMedium = style(16, 22, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 21, FontWeight.Normal),
    bodySmall = style(12, 17, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.SemiBold),
    labelSmall = style(11, 15, FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val LocalBrandColors = staticCompositionLocalOf { LightBrand }

/** Shorthand for the brand palette inside a composable: `Brand.colors.accent`. */
object Brand {
    val colors: BrandColors
        @Composable get() = LocalBrandColors.current
}

@Composable
fun MacroDimeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBrandColors provides if (darkTheme) DarkBrand else LightBrand) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
