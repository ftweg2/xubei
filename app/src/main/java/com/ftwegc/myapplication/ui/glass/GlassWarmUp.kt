package com.ftwegc.myapplication.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Draws a few tiny, invisible pieces of refracting glass for the first frames, so the GPU
 * compiles the refraction shaders (with and without chromatic aberration) while the splash
 * screen is still up, instead of on the first press of the navigation drop.
 */
@Composable
fun GlassShaderWarmUp() {
    val backdrop = LocalPageBackdrop.current ?: return
    var done by rememberSaveable { mutableStateOf(false) }
    if (done) return
    LaunchedEffect(Unit) {
        repeat(WARM_UP_FRAMES) { withFrameMillis { } }
        done = true
    }
    val shape = remember { RoundedCornerShape(4.dp) }
    Row(Modifier.clearAndSetSemantics {}.alpha(WARM_UP_ALPHA)) {
        listOf(false, true).forEach { dispersion ->
            Box(
                Modifier
                    .size(8.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(2.dp.toPx())
                            lens(2.dp.toPx(), 3.dp.toPx(), depthEffect = !dispersion, chromaticAberration = dispersion)
                        },
                        highlight = null,
                        shadow = null,
                        onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.01f)) },
                    ),
            )
        }
    }
}

private const val WARM_UP_FRAMES = 3

/** Low but not zero: a fully transparent layer may be skipped by the renderer. */
private const val WARM_UP_ALPHA = 0.01f
