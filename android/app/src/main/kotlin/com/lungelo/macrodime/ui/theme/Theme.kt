/*
 * Theme.kt
 * MacroDime
 *
 * The brand palette, ported from MacroDime/Views/Components/Theme.swift with
 * the same hex values in both appearances.
 *
 * Direction: warm provisions. Gold carries the budget and takes the accent role
 * that would otherwise be Material purple. The macros sit in food colours
 * (berry, clay, olive). Green and red are reserved for one job only: whether
 * you are inside your budget. Dynamic colour is deliberately off, so a
 * wallpaper cannot turn the budget meter's red into a decoration.
 */
package com.lungelo.macrodime.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colours outside Material's roles: the macro palette and the budget signal. */
@Immutable
data class BrandColors(
    val gold: Color,
    val goldInk: Color,
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
    gold = Color(0xFFA87B1E),
    goldInk = Color(0xFF6B4C0B),
    protein = Color(0xFFB03060),
    carbs = Color(0xFFC05A2B),
    fat = Color(0xFF77803C),
    underBudget = Color(0xFF2E7D52),
    overBudget = Color(0xFFC0392B),
    measurement = Color(0xFF3C7A85),
    pantry = Color(0xFF5B4B8A),
    caution = Color(0xFFB45309),
)

private val DarkBrand = BrandColors(
    gold = Color(0xFFE3B45C),
    goldInk = Color(0xFFF3DDAE),
    protein = Color(0xFFE8628F),
    carbs = Color(0xFFF0854A),
    fat = Color(0xFFB7C06A),
    underBudget = Color(0xFF4FBF85),
    overBudget = Color(0xFFFF6B5A),
    measurement = Color(0xFF64BECB),
    pantry = Color(0xFF9B8BD0),
    caution = Color(0xFFF5A524),
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFA87B1E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6E6C4),
    onPrimaryContainer = Color(0xFF3D2C05),
    secondary = Color(0xFF6E6352),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE4D3),
    onSecondaryContainer = Color(0xFF261E10),
    tertiary = Color(0xFF3C7A85),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F1EB),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFF4F1EB),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFE9E3D8),
    onSurfaceVariant = Color(0xFF5E584E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF9F7F3),
    surfaceContainerHigh = Color(0xFFEFEBE4),
    surfaceContainerHighest = Color(0xFFE7E2D9),
    outline = Color(0xFF8C857A),
    outlineVariant = Color(0xFFDCD5CA),
    error = Color(0xFFC0392B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFADAD5),
    onErrorContainer = Color(0xFF5C120A),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFE3B45C),
    onPrimary = Color(0xFF3A2A00),
    primaryContainer = Color(0xFF5A4310),
    onPrimaryContainer = Color(0xFFF3DDAE),
    secondary = Color(0xFFD3C5AC),
    onSecondary = Color(0xFF382F1F),
    secondaryContainer = Color(0xFF4F4534),
    onSecondaryContainer = Color(0xFFF0E1C7),
    tertiary = Color(0xFF64BECB),
    onTertiary = Color(0xFF00363D),
    background = Color(0xFF121110),
    onBackground = Color(0xFFECE7DF),
    surface = Color(0xFF121110),
    onSurface = Color(0xFFECE7DF),
    surfaceVariant = Color(0xFF2E2B27),
    onSurfaceVariant = Color(0xFFBDB5A9),
    surfaceContainerLowest = Color(0xFF0D0C0B),
    surfaceContainerLow = Color(0xFF1D1B19),
    surfaceContainer = Color(0xFF221F1C),
    surfaceContainerHigh = Color(0xFF2B2825),
    surfaceContainerHighest = Color(0xFF36332F),
    outline = Color(0xFF8A8277),
    outlineVariant = Color(0xFF3E3A35),
    error = Color(0xFFFF6B5A),
    onError = Color(0xFF4A0B03),
    errorContainer = Color(0xFF6B1A10),
    onErrorContainer = Color(0xFFFFDAD4),
)

val LocalBrandColors = staticCompositionLocalOf { LightBrand }

/** Shorthand for the brand palette inside a composable: `Brand.colors.gold`. */
object Brand {
    val colors: BrandColors
        @Composable get() = LocalBrandColors.current
}

@Composable
fun MacroDimeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBrandColors provides if (darkTheme) DarkBrand else LightBrand) {
        MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, content = content)
    }
}
