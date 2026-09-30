package com.ftwegc.myapplication.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

private val LocalNumberTypography = staticCompositionLocalOf { VluerNumbers }

/**
 * App theme. Uses the brand palette on every Android version; wallpaper-based dynamic
 * color is available but off by default so the app looks the same on every device.
 *
 * @param reducedMotion overrides the system "remove animations" detection (for previews / tests).
 */
@Composable
fun VluerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    reducedMotion: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val extended = if (darkTheme) DarkVluerColors else LightVluerColors
    val reduced = reducedMotion ?: rememberSystemReducedMotion()

    CompositionLocalProvider(
        LocalVluerColors provides extended,
        LocalNumberTypography provides VluerNumbers,
        LocalReducedMotion provides reduced,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VluerTypography,
            shapes = VluerShapes,
            content = content,
        )
    }
}

/** Accessors for the app-specific theme values. */
object VluerTheme {
    val colors: VluerColors
        @Composable @ReadOnlyComposable get() = LocalVluerColors.current

    val numbers: NumberTypography
        @Composable @ReadOnlyComposable get() = LocalNumberTypography.current

    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}
