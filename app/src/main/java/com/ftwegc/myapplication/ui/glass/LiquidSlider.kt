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
 * Source: https://github.com/Kyant0/AndroidLiquidGlass/blob/1.0.6/catalog/src/main/java/com/kyant/backdrop/catalog/components/LiquidSlider.kt
 * Modified for 续杯 (Xubei, formerly Vluer):
 * - named GlassSlider; values snap to [step] and are reported only when they change;
 * - the app's colors (teal to amber accent, glass track) in both themes;
 * - samples the surrounding glass through LocalGlassSource (the sheet's exported backdrop) and
 *   falls back to a plain knob on tiers without blur;
 * - accessibility: exposes the range and accepts "set progress";
 * - jumps without animation when system animations are off;
 * - RoundedCornerShape capsules and the app's DampedDragAnimation.
 */

package com.ftwegc.myapplication.ui.glass

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
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
import kotlin.math.roundToInt

private val Capsule = RoundedCornerShape(percent = 50)

/**
 * A liquid glass slider over [valueRange] in steps of [step]: a 6 dp glass track filled up to the
 * value, and a glass knob that grows and refracts while dragged.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val env = currentGlass()
    val reduced = LocalReducedMotion.current
    val (accent, track) = switchColors(env.dark)
    val warm = Color(if (env.dark) 0xFFFBBF24 else 0xFFD97706)
    val source = LocalGlassSource.current
    val sampling = env.tier.blurs && source.backdrop != null && source.exported
    val trackLayer = if (sampling) rememberLayerBackdrop() else null
    val latestChange by rememberUpdatedState(onValueChange)
    fun snap(v: Float): Float {
        val s = if (step > 0f) ((v - valueRange.start) / step).roundToInt() * step + valueRange.start else v
        return s.coerceIn(valueRange)
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps = ((valueRange.endInclusive - valueRange.start) / step).roundToInt() - 1)
                setProgress { target ->
                    latestChange(snap(target))
                    true
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val scope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        var raw by remember { mutableFloatStateOf(value) }
        val drag = remember(scope) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = value,
                valueRange = valueRange,
                visibilityThreshold = step / 100f,
                initialScale = 1f,
                pressedScaleX = 1.5f,
                pressedScaleY = 1.5f,
                moveSpec = tween(Motion.DurationMedium, easing = Motion.Emphasized),
                onDragStarted = { raw = targetValue },
                onDragStopped = {
                    if (didDrag) {
                        val snapped = snap(raw)
                        updateValue(snapped)
                        latestChange(snapped)
                    }
                    didDrag = false
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) didDrag = dragAmount.x != 0f
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                    raw = (if (isLtr) raw + delta else raw - delta).coerceIn(valueRange)
                    updateValue(raw)
                    val snapped = snap(raw)
                    if (snapped != value) latestChange(snapped)
                },
            )
        }
        LaunchedEffect(value, reduced) {
            if (drag.targetValue != value && !didDrag) {
                if (reduced) drag.snapTo(value) else drag.updateValue(value)
            }
        }

        Box(Modifier.then(if (trackLayer != null) Modifier.layerBackdrop(trackLayer) else Modifier)) {
            Box(
                Modifier
                    .clip(Capsule)
                    .drawBehind {
                        drawRect(track)
                        val w = size.width * drag.progress.fastCoerceIn(0f, 1f)
                        if (w > 0f) {
                            drawRect(
                                Brush.horizontalGradient(listOf(accent, lerp(accent, warm, drag.progress)), startX = 0f, endX = w.coerceAtLeast(1f)),
                                size = size.copy(width = w),
                                topLeft = if (isLtr) Offset.Zero else Offset(size.width - w, 0f),
                            )
                        }
                    }
                    .pointerInput(scope, trackWidth) {
                        detectTapGestures { position ->
                            val delta = (valueRange.endInclusive - valueRange.start) * (position.x / trackWidth)
                            val target = snap(if (isLtr) valueRange.start + delta else valueRange.endInclusive - delta)
                            if (reduced) drag.snapTo(target) else drag.animateToValue(target)
                            latestChange(target)
                        }
                    }
                    .height(6.dp)
                    .fillMaxWidth(),
            )
        }

        val parent = source.backdrop
        val knobBackdrop = if (trackLayer != null && parent != null) {
            rememberCombinedBackdrop(
                parent,
                rememberBackdrop(trackLayer) { drawBackdrop ->
                    val progress = drag.pressProgress
                    scale(lerp(2f / 3f, 1f, progress), lerp(0f, 1f, progress)) { drawBackdrop() }
                },
            )
        } else {
            emptyBackdrop()
        }
        Box(
            Modifier
                .graphicsLayer {
                    translationX = (-size.width / 2f + trackWidth * drag.progress)
                        .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }
                .then(drag.modifier)
                .drawBackdrop(
                    backdrop = knobBackdrop,
                    shape = { Capsule },
                    effects = {
                        if (sampling) {
                            val progress = drag.pressProgress
                            blur(8.dp.toPx() * (1f - progress))
                            if (env.tier.refracts) lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, chromaticAberration = true)
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
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = if (env.dark) 0.2f else 0.1f)) },
                    innerShadow = {
                        val progress = drag.pressProgress
                        if (progress > 0.01f) InnerShadow(radius = 4.dp * progress, alpha = progress) else null
                    },
                    layerBlock = {
                        var sx = drag.scaleX
                        var sy = drag.scaleY
                        val velocity = drag.velocity / 10f
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
