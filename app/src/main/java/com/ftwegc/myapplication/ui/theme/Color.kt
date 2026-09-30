package com.ftwegc.myapplication.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * 续杯 brand palette.
 *
 * Primary: teal-cyan (#2DD4BF family). Secondary: warm amber (#FBBF24 family), used for "due soon".
 * Error: soft coral instead of pure red. Dark mode uses three blue-black surface layers, never pure
 * black. Light mode uses the deeper tones of the same hues so that text stays at >= 4.5:1.
 */

// ---- Brand tones ----
private val Teal300 = Color(0xFF2DD4BF)
private val Teal700 = Color(0xFF0F766E)
private val Amber300 = Color(0xFFFBBF24)
private val Amber800 = Color(0xFFA65200)
private val Coral300 = Color(0xFFFF8A7A)
private val Coral700 = Color(0xFFB93A31)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Teal300,
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0E4A43),
    onPrimaryContainer = Color(0xFFA7F3E6),
    inversePrimary = Teal700,
    // Material "secondary" drives selection surfaces (chips, segments, sliders): a muted teal, so
    // amber stays reserved for "due soon" (VluerColors.warning).
    secondary = Color(0xFF9FCEC6),
    onSecondary = Color(0xFF05302B),
    secondaryContainer = Color(0xFF1B3C38),
    onSecondaryContainer = Color(0xFFBDEBE3),
    tertiary = Color(0xFF7DB6FF),
    onTertiary = Color(0xFF00315E),
    tertiaryContainer = Color(0xFF123B66),
    onTertiaryContainer = Color(0xFFD2E4FF),
    error = Coral300,
    onError = Color(0xFF5C130B),
    errorContainer = Color(0xFF6B2820),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF0B1215),
    onBackground = Color(0xFFE2EBEE),
    surface = Color(0xFF0B1215),
    onSurface = Color(0xFFE2EBEE),
    surfaceVariant = Color(0xFF1F2B31),
    onSurfaceVariant = Color(0xFFA1B1B8),
    surfaceTint = Teal300,
    inverseSurface = Color(0xFFE2EBEE),
    inverseOnSurface = Color(0xFF1B2529),
    outline = Color(0xFF62747C),
    outlineVariant = Color(0xFF2A383F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A363C),
    surfaceDim = Color(0xFF0B1215),
    surfaceContainerLowest = Color(0xFF070C0E),
    surfaceContainerLow = Color(0xFF10191D),
    surfaceContainer = Color(0xFF141F24),
    surfaceContainerHigh = Color(0xFF1A272D),
    surfaceContainerHighest = Color(0xFF223137),
)

internal val LightColors: ColorScheme = lightColorScheme(
    primary = Teal700,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBDF3EA),
    onPrimaryContainer = Color(0xFF00201C),
    inversePrimary = Teal300,
    secondary = Color(0xFF4A6460),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFF2F6FB5),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5E5FF),
    onTertiaryContainer = Color(0xFF001C3A),
    error = Coral700,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410001),
    background = Color(0xFFF4F8F9),
    onBackground = Color(0xFF0E1A1E),
    surface = Color(0xFFF4F8F9),
    onSurface = Color(0xFF0E1A1E),
    surfaceVariant = Color(0xFFDCE5E8),
    onSurfaceVariant = Color(0xFF465459),
    surfaceTint = Teal700,
    inverseSurface = Color(0xFF243135),
    inverseOnSurface = Color(0xFFEAF2F4),
    outline = Color(0xFF74858B),
    outlineVariant = Color(0xFFC9D4D8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF4F8F9),
    surfaceDim = Color(0xFFD5DDE0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF3F5),
    surfaceContainer = Color(0xFFE8EEF0),
    surfaceContainerHigh = Color(0xFFE2E9EB),
    surfaceContainerHighest = Color(0xFFDCE4E6),
)

/**
 * Colors outside the Material scheme: status, category tints, card surface and the home
 * header gradient. Read through [VluerTheme.colors].
 */
@Immutable
data class VluerColors(
    /** Card background (sits one layer above the page background). */
    val card: Color,
    /** Very light hairline around cards. */
    val cardBorder: Color,
    /** Amber: due soon, 70–90 % traffic. */
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    /** Coral: expired, > 90 % traffic (same hue as the scheme's error). */
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val categoryAi: Color,
    val categoryVps: Color,
    val categoryOther: Color,
    /** Home header gradient, primary to deep blue (the only large gradient in the app). */
    val heroGradientStart: Color,
    val heroGradientEnd: Color,
    val onHero: Color,
    val onHeroVariant: Color,
    /** Track behind progress bars and rings. */
    val progressTrack: Color,
    /** Shimmer highlight for skeletons. */
    val skeletonBase: Color,
    val skeletonHighlight: Color,
    val isDark: Boolean,
)

internal val DarkVluerColors = VluerColors(
    card = Color(0xFF141F24),
    cardBorder = Color(0x14FFFFFF),
    warning = Amber300,
    warningContainer = Color(0xFF3A2C08),
    onWarningContainer = Color(0xFFFFE3A3),
    danger = Coral300,
    dangerContainer = Color(0xFF4A1E19),
    onDangerContainer = Color(0xFFFFDAD4),
    success = Color(0xFF5EE3A1),
    successContainer = Color(0xFF123D2B),
    onSuccessContainer = Color(0xFFBFF5DA),
    categoryAi = Color(0xFFB69CFF),
    categoryVps = Color(0xFF6CB6FF),
    categoryOther = Color(0xFFA7B6C8),
    heroGradientStart = Color(0xFF0E5A53),
    heroGradientEnd = Color(0xFF14264A),
    onHero = Color(0xFFF2FBFA),
    onHeroVariant = Color(0xB8E6F4F2),
    progressTrack = Color(0xFF223137),
    skeletonBase = Color(0xFF1A272D),
    skeletonHighlight = Color(0xFF26363D),
    isDark = true,
)

internal val LightVluerColors = VluerColors(
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0x140E1A1E),
    warning = Amber800,
    warningContainer = Color(0xFFFFF0CC),
    onWarningContainer = Color(0xFF3A2400),
    danger = Coral700,
    dangerContainer = Color(0xFFFFE3DF),
    onDangerContainer = Color(0xFF410001),
    success = Color(0xFF0F6E37),
    successContainer = Color(0xFFD3F5E0),
    onSuccessContainer = Color(0xFF00210E),
    categoryAi = Color(0xFF6B47DC),
    categoryVps = Color(0xFF1F66C2),
    categoryOther = Color(0xFF52667A),
    heroGradientStart = Color(0xFF0F766E),
    heroGradientEnd = Color(0xFF1C3A6B),
    onHero = Color(0xFFFFFFFF),
    onHeroVariant = Color(0xC7FFFFFF),
    progressTrack = Color(0xFFE2E9EB),
    skeletonBase = Color(0xFFE6ECEE),
    skeletonHighlight = Color(0xFFF4F8F9),
    isDark = false,
)

internal val LocalVluerColors = staticCompositionLocalOf { DarkVluerColors }
