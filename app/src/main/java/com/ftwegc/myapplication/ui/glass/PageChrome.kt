package com.ftwegc.myapplication.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Color of the top and bottom bars of pages that are not redesigned yet: the background's base
 * color, mostly opaque, so the flowing background shows through a little and text scrolling
 * under the bars stays hidden.
 */
val LocalPageBarColor = staticCompositionLocalOf { Color.Unspecified }

/** See [LocalPageBarColor]; the theme background outside the app shell (previews). */
@Composable
@ReadOnlyComposable
fun pageBarColor(): Color = LocalPageBarColor.current.takeOrElse { MaterialTheme.colorScheme.background }

/**
 * A soft fade under the status bar for pages that scroll edge to edge (the overview): content
 * passing under the clock and icons fades out instead of colliding with them.
 */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    val env = currentGlass()
    if (env.tier.blurs && LocalGlassSource.current.backdrop != null) {
        // The flowing background itself is painted over the content (see BackgroundFade).
        BackgroundFade(
            // Solid behind the clock and icons (bright text there must not collide with them), then a
            // short fade below the status bar.
            stops = listOf(0f to 1f, 0.62f to 0.98f, 0.8f to 0.6f, 1f to 0f),
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars.add(WindowInsets(top = StatusScrimExtra))),
        )
    } else {
        // Android 11 and lower, "reduce transparency": only the background's flat base color can be
        // painted, and it never matches the colored background under it. A solid band of it showed
        // a hard edge below the status bar; instead it thins out gradually over a longer reach, so
        // there is no line where it ends.
        BackgroundFade(
            stops = FlatScrimStops,
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars.add(WindowInsets(top = FlatScrimExtra))),
        )
    }
}

private val StatusScrimExtra = 22.dp

/** How far below the status bar the flat fade reaches. */
private val FlatScrimExtra = 56.dp

/** Opacity of the flat fade from the top of the screen down: opaque at the very top, then easing out (no plateau). */
private val FlatScrimStops: List<Pair<Float, Float>> = listOf(
    0f to 1f,
    0.3f to 0.95f,
    0.45f to 0.8f,
    0.6f to 0.55f,
    0.75f to 0.3f,
    0.88f to 0.12f,
    1f to 0f,
)
