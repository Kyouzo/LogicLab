package com.bsit1c.logiclab

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * LogicLab visual language: ink-blue on warm "paper", with amber used only to mark the part of a
 * formula currently being worked on, teal for TRUE and rose for FALSE. Headings use a serif face
 * (textbook feel); every formula uses a monospaced face so symbols line up in tables.
 */

private val Ink = Color(0xFF283593)
private val InkDeep = Color(0xFF0F1A5C)
private val Paper = Color(0xFFF7F5EF)

private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = InkDeep,
    secondary = Color(0xFF7A5900),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2A8),
    onSecondaryContainer = Color(0xFF261A00),
    tertiary = Color(0xFF00695C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB2EFE4),
    onTertiaryContainer = Color(0xFF00201C),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Paper,
    onBackground = Color(0xFF1B1B1F),
    surface = Paper,
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC8C5D0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDFBF6),
    surfaceContainer = Color(0xFFF1EFE8),
    surfaceContainerHigh = Color(0xFFEBE9E2),
    surfaceContainerHighest = Color(0xFFE5E3DC),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF16247A),
    primaryContainer = Color(0xFF2F3D92),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFF5C04E),
    onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFE2A8),
    tertiary = Color(0xFF7DD9C8),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005047),
    onTertiaryContainer = Color(0xFFB2EFE4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF91909A),
    outlineVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34343A),
)

/** Colours with a logical meaning that Material's scheme does not cover. */
@Immutable
data class LabColors(
    val trueFg: Color,
    val trueBg: Color,
    val falseFg: Color,
    val falseBg: Color,
    /** Marks the part of a formula being evaluated or rewritten. */
    val highlight: Color,
    val onHighlight: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

private val LightLab = LabColors(
    trueFg = Color(0xFF00695C), trueBg = Color(0xFFD5F5EE),
    falseFg = Color(0xFFB0304A), falseBg = Color(0xFFFBE1E6),
    highlight = Color(0xFFFFD670), onHighlight = Color(0xFF2B1D00),
    success = Color(0xFF1B7A3E), successContainer = Color(0xFFD3F3DC), onSuccessContainer = Color(0xFF00210D),
    warningContainer = Color(0xFFFFE9C2), onWarningContainer = Color(0xFF3A2700),
)

private val DarkLab = LabColors(
    trueFg = Color(0xFF7DD9C8), trueBg = Color(0xFF113A35),
    falseFg = Color(0xFFFF9FB2), falseBg = Color(0xFF45202A),
    highlight = Color(0xFF8C6A12), onHighlight = Color(0xFFFFF1D1),
    success = Color(0xFF8EDBA4), successContainer = Color(0xFF15402A), onSuccessContainer = Color(0xFFD3F3DC),
    warningContainer = Color(0xFF4A3708), onWarningContainer = Color(0xFFFFE9C2),
)

val LocalLabColors = staticCompositionLocalOf { LightLab }

/** Monospaced family for every formula, so symbols and truth-table columns align. */
val FormulaFont: FontFamily = FontFamily.Monospace

private val Base = Typography()

private val LabTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineLarge = Base.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

object FormulaStyle {
    val large = TextStyle(fontFamily = FormulaFont, fontSize = 22.sp, lineHeight = 30.sp)
    val medium = TextStyle(fontFamily = FormulaFont, fontSize = 16.sp, lineHeight = 22.sp)
    val small = TextStyle(fontFamily = FormulaFont, fontSize = 14.sp, lineHeight = 20.sp)
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun LabTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(LocalLabColors provides if (dark) DarkLab else LightLab) {
        MaterialTheme(colorScheme = scheme, typography = LabTypography, content = content)
    }
}

object Lab {
    val colors: LabColors
        @Composable get() = LocalLabColors.current
}
