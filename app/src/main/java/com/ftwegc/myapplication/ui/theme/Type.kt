package com.ftwegc.myapplication.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** OpenType feature for tabular (fixed-width) digits, so numbers do not jitter while animating. */
const val TabularNumbers: String = "tnum"

private val Base = FontFamily.Default

/** Material type scale, slightly tightened and weighted for a calm finance look. */
val VluerTypography: Typography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontFamily = Base, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontFamily = Base, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        headlineMedium = headlineMedium.copy(fontFamily = Base, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        headlineSmall = headlineSmall.copy(fontFamily = Base, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = Base, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = Base, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(fontFamily = Base, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = Base, letterSpacing = 0.15.sp),
        bodyMedium = bodyMedium.copy(fontFamily = Base, letterSpacing = 0.1.sp),
        bodySmall = bodySmall.copy(fontFamily = Base),
        labelLarge = labelLarge.copy(fontFamily = Base, fontWeight = FontWeight.Medium),
        labelMedium = labelMedium.copy(fontFamily = Base, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontFamily = Base, fontWeight = FontWeight.Medium),
    )
}

/**
 * Number styles: large, semi-bold, tabular digits. Units and currency symbols use [unit] /
 * [unitSmall] with a lower-emphasis color.
 */
@Immutable
data class NumberTypography(
    /** Hero amount on the home header. */
    val display: TextStyle,
    /** Detail header amount, ring center value. */
    val large: TextStyle,
    /** Card amount. */
    val medium: TextStyle,
    /** Inline numbers in rows and badges. */
    val small: TextStyle,
    /** Unit / symbol next to a large number. */
    val unit: TextStyle,
    /** Unit / symbol next to a small number. */
    val unitSmall: TextStyle,
)

internal val VluerNumbers = NumberTypography(
    display = TextStyle(
        fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp,
        letterSpacing = (-0.02).em, fontFeatureSettings = TabularNumbers,
    ),
    large = TextStyle(
        fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp,
        letterSpacing = (-0.015).em, fontFeatureSettings = TabularNumbers,
    ),
    medium = TextStyle(
        fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp,
        fontFeatureSettings = TabularNumbers,
    ),
    small = TextStyle(
        fontFamily = Base, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp,
        fontFeatureSettings = TabularNumbers,
    ),
    unit = TextStyle(
        fontFamily = Base, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp,
        fontFeatureSettings = TabularNumbers,
    ),
    unitSmall = TextStyle(
        fontFamily = Base, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp,
        fontFeatureSettings = TabularNumbers,
    ),
)
