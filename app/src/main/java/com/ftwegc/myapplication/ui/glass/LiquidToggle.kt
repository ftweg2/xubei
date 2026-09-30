/*
 * Copyright 2025 Kyant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Source: https://github.com/Kyant0/AndroidLiquidGlass/blob/1.0.6/catalog/src/main/java/com/kyant/backdrop/catalog/components/LiquidToggle.kt
 * Modified for 续杯 (Xubei, formerly Vluer):
 * - named GlassSwitch; controlled by `checked` like a Material switch, so a whole settings row
 *   can toggle it; a tap on the row moves the knob, a drag of the knob still works;
 * - the app's colors (teal accent, glass track) in both themes instead of iOS green;
 * - samples the surrounding glass through LocalGlassSource (the sheet's exported backdrop) and
 *   falls back to a plain knob on tiers without blur (Android 11 and lower, "reduce transparency");
 * - jumps without animation when system animations are off;
 * - uses a RoundedCornerShape capsule (the project's rule for lens shapes) and the app's
 *   DampedDragAnimation (separate x / y press scales).
 */

package com.ftwegc.myapplication.ui.glass

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest

/** Switch accent (mockup teal) and the glass track, per theme. */
internal fun switchColors(dark: Boolean): Pair<Color, Color> =
    if (dark) Color(0xFF2DD4BF) to Color.White.copy(alpha = 0.16f)
    else Color(0xFF0D9488) to Color(0xFF0B1A20).copy(alpha = 0.14f)

private val Capsule = RoundedCornerShape(percent = 50)

/**
 * A liquid glass switch (64 × 28 track, 40 × 24 knob). The caller makes the surrounding row
 * toggleable; this draws the state and lets the knob be dragged across. Decoration only for
 * accessibility (the row carries the switch semantics).
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val env = currentGlass()
    val reduced = LocalReducedMotion.current
    val (accent, track) = switchColors(env.dark)
    val source = LocalGlassSource.current
    val sampling = env.tier.blurs && source.backdrop != null && source.exported
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { Motion.SwitchTravel.toPx() }
    val scope = rememberCoroutineScope()
    val latestChange by rememberUpdatedState(onCheckedChange)
    val latestChecked by rememberUpdatedState(checked)
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val drag = remember(scope) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScaleX = 1.5f,
            pressedScaleY = 1.5f,
            moveSpec = tween(Motion.DurationDropMove, easing = Motion.DropMoveEasing),
            onDragStarted = {},
            onDragStopped = {
                // Taps are handled by the row; only a real drag decides here.
                if (didDrag) {
                    val on = targetValue >= 0.5f
                    fraction = if (on) 1f else 0f
                    if (on != latestChecked) latestChange(on)
                    didDrag = false
                }
            },
            onDrag = { _, dragAmount ->
                if (!enabled) return@DampedDragAnimation
                if (!didDrag) didDrag = dragAmount.x != 0f
                val delta = dragAmount.x / dragWidth
                fraction = if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f) else (fraction - delta).fastCoerceIn(0f, 1f)
            },
        )
    }
    LaunchedEffect(drag) {
        snapshotFlow { fraction }.collectLatest { drag.updateValue(it) }
    }
    LaunchedEffect(checked, reduced) {
        val target = if (checked) 1f else 0f
        if (reduced) {
            fraction = target
            drag.snapTo(target)
        } else if (target != fraction || drag.targetValue != target) {
            fraction = target
            drag.animateToValue(target)
        }
    }

    val trackLayer = if (sampling) rememberLayerBackdrop() else null
    Box(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .then(if (trackLayer != null) Modifier.layerBackdrop(trackLayer) else Modifier)
                .clip(Capsule)
                .drawBehind {
                    // Keep the track's own alpha (the off track is a faint tint, not a solid pill).
                    val color = lerp(track, accent, drag.value)
                    drawRect(color.copy(alpha = color.alpha * if (enabled) 1f else 0.5f))
                }
                .size(64.dp, 28.dp),
        )
        val parent = source.backdrop
        val knobBackdrop = if (trackLayer != null && parent != null) {
            rememberCombinedBackdrop(
                parent,
                rememberBackdrop(trackLayer) { drawBackdrop ->
                    val progress = drag.pressProgress
                    scale(lerp(2f / 3f, 0.75f, progress), lerp(0f, 0.75f, progress)) { drawBackdrop() }
                },
            )
        } else {
            emptyBackdrop()
        }
        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2.dp.toPx()
                    translationX = if (isLtr) lerp(padding, padding + dragWidth, drag.value) else lerp(-padding, -(padding + dragWidth), drag.value)
                }
                .then(if (enabled) drag.modifier else Modifier)
                .drawBackdrop(
                    backdrop = knobBackdrop,
                    shape = { Capsule },
                    effects = {
                        if (sampling) {
                            val progress = drag.pressProgress
                            blur(8.dp.toPx() * (1f - progress))
                            if (env.tier.refracts) {
                                lens(5.dp.toPx() * progress, 10.dp.toPx() * progress, chromaticAberration = true)
                            }
                        }
                    },
                    highlight = if (sampling) {
                        {
                            val progress = drag.pressProgress
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.5f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                alpha = progress,
                            )
                        }
                    } else {
                        null
                    },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = if (env.dark) 0.18f else 0.10f)) },
                    innerShadow = {
                        val progress = drag.pressProgress
                        if (progress > 0.01f) InnerShadow(radius = 4.dp * progress, alpha = progress) else null
                    },
                    layerBlock = {
                        var sx = drag.scaleX
                        var sy = drag.scaleY
                        val velocity = drag.velocity / 50f
                        sx /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        sy *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        scaleX = sx
                        scaleY = sy
                    },
                    onDrawSurface = {
                        val progress = if (sampling) drag.pressProgress else 0f
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    },
                )
                .size(40.dp, 24.dp),
        )
    }
}
