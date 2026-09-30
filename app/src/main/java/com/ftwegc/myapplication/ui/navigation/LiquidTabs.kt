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
 * Adapted from LiquidBottomTabs.kt and LiquidBottomTab.kt of
 * https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6/catalog/src/main/java/com/kyant/backdrop/catalog/components
 *
 * Modified for 续杯 (Xubei, formerly Vluer):
 * - the bar and the drop use the app's glass (GlassTier fallbacks for Android 12 and 11, "reduce
 *   transparency", the mockup's colors and highlight) instead of fixed colors;
 * - the drop samples the bar through the bar's exported backdrop (glass inside glass) and is
 *   drawn under the tab labels, which stay readable; it is dragged from the selected tab;
 * - the drop stretches sideways while it travels (mockup: 22 px wider for 220 ms) and moves on
 *   the mockup's curve when a tab is tapped;
 * - a collapse fraction shrinks the bar and hides the labels while the page scrolls down;
 * - accessibility: tabs are a selectable group, the drop is decoration.
 */

package com.ftwegc.myapplication.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.ftwegc.myapplication.ui.glass.DampedDragAnimation
import com.ftwegc.myapplication.ui.glass.GlassDefaults
import com.ftwegc.myapplication.ui.glass.GlassThickness
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.glass.glass
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** One tab of [LiquidTabs]. */
@Immutable
data class LiquidTab(val label: String, val icon: ImageVector)

/** Sizes of the bar (mockup `.nav`, `.drop`, `.nav button`), expanded and collapsed. */
object LiquidTabsDefaults {
    val Height: Dp = 62.dp
    val CollapsedHeight: Dp = 48.dp
    val Padding: Dp = 6.dp
    val CollapsedPadding: Dp = 5.dp
    val IconSize: Dp = 20.dp
    val LabelSize = 10.5.sp

    /** Sideways stretch of the drop while it travels, relative to its width. */
    const val DropStretchX: Float = 1.26f
    const val DropSquashY: Float = 0.94f
}

/**
 * The glass tab bar with a liquid drop that slides between tabs and can be dragged.
 *
 * @param collapse 0 = full bar, 1 = compact (icons only, lower); animate it outside.
 */
@Composable
fun LiquidTabs(
    tabs: List<LiquidTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    collapse: Float = 0f,
) {
    val env = currentGlass()
    val dark = env.dark
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val count = tabs.size.coerceAtLeast(1)
    val barHeight = lerp(LiquidTabsDefaults.Height, LiquidTabsDefaults.CollapsedHeight, collapse)
    val pad = lerp(LiquidTabsDefaults.Padding, LiquidTabsDefaults.CollapsedPadding, collapse)
    val barShape = GlassDefaults.CapsuleShape
    val barLayer = if (env.tier.blurs) rememberLayerBackdrop() else null

    BoxWithConstraints(modifier.height(barHeight), contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val padPx = with(density) { pad.toPx() }
        val tabWidthPx = ((constraints.maxWidth.toFloat() - padPx * 2f) / count).coerceAtLeast(1f)
        val tabWidth = with(density) { tabWidthPx.toDp() }
        val dropHeight = barHeight - pad * 2

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }
        val scope = rememberCoroutineScope()
        val latestSelect = androidx.compose.runtime.rememberUpdatedState(onSelect)
        val latestSelected = androidx.compose.runtime.rememberUpdatedState(selectedIndex)
        val drag = remember(scope, count) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(count - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScaleX = LiquidTabsDefaults.DropStretchX,
                pressedScaleY = LiquidTabsDefaults.DropSquashY,
                moveSpec = tween(Motion.DurationDropMove, easing = Motion.DropMoveEasing),
                onDragStarted = {},
                onDragStopped = {
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, count - 1)
                    animateToValue(target.toFloat())
                    scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                    if (target != latestSelected.value) latestSelect.value(target)
                },
                onDrag = { _, dragAmount ->
                    updateValue((targetValue + dragAmount.x / tabWidthPx).fastCoerceIn(0f, (count - 1).toFloat()))
                    scope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                },
            )
        }
        LaunchedEffect(selectedIndex, reduced) {
            if (drag.targetValue.fastRoundToInt() == selectedIndex && drag.value == selectedIndex.toFloat()) return@LaunchedEffect
            if (reduced) drag.snapTo(selectedIndex.toFloat()) else drag.animateToValue(selectedIndex.toFloat())
        }

        // The bar: floating thick glass that exports itself for the drop.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = panelOffset }
                .glass(barShape, GlassThickness.Thick, floating = true, exportTo = barLayer),
        ) {
            // The drop, under the labels.
            val dropShape = RoundedCornerShape(percent = 50)
            val sampling = barLayer != null
            val refracts = env.tier.refracts && sampling
            val dropFill = if (dark) {
                listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.10f))
            } else {
                listOf(Color.White.copy(alpha = 0.92f), Color.White.copy(alpha = 0.62f))
            }
            val glow = Color(0xFF2DD4BF).copy(alpha = if (dark) 0.22f else 0.20f)
            val edge = Color.White.copy(alpha = if (dark) 0.60f else 0.95f)
            Box(
                Modifier
                    .offset(x = pad, y = pad)
                    .size(tabWidth, dropHeight)
                    .graphicsLayer { translationX = drag.value * tabWidthPx }
                    .clearAndSetSemantics {}
                    .drawBackdrop(
                        backdrop = barLayer ?: emptyBackdrop(),
                        shape = { dropShape },
                        effects = {
                            val p = drag.pressProgress
                            if (refracts && p > 0.01f) {
                                lens(10f.dp.toPx() * p, 14f.dp.toPx() * p, chromaticAberration = true)
                            }
                        },
                        highlight = null,
                        shadow = { Shadow(radius = 16.dp, offset = androidx.compose.ui.unit.DpOffset(0.dp, 6.dp), color = Color.Black.copy(alpha = if (dark) 0.25f else 0.12f)) },
                        innerShadow = {
                            val p = drag.pressProgress
                            if (p > 0.01f) InnerShadow(radius = 8.dp * p, alpha = p) else null
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
                            drawRect(Brush.linearGradient(dropFill, start = Offset.Zero, end = Offset(size.width, size.height)))
                            // mockup: inset 0 -6px 12px rgba(45,212,191,.22)
                            drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to glow))
                            val stroke = 1.dp.toPx()
                            drawRoundRect(
                                Brush.verticalGradient(0f to edge, 0.4f to edge.copy(alpha = 0f)),
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                                style = Stroke(stroke),
                            )
                        },
                    ),
            )
            // Tabs on top.
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    val color by animateColorAsState(
                        targetValue = when {
                            !selected -> env.ink.secondary
                            dark -> Color.White
                            else -> env.ink.primary
                        },
                        animationSpec = if (reduced) tween(0) else tween(Motion.DurationShort),
                        label = "tabColor",
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(if (selected) drag.modifier else Modifier)
                            .selectable(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        onSelect(index)
                                    }
                                },
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ),
                        verticalArrangement = Arrangement.spacedBy(3.dp * (1f - collapse), Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(LiquidTabsDefaults.IconSize))
                        if (collapse < 1f) {
                            Text(
                                text = tab.label,
                                color = color,
                                fontSize = LiquidTabsDefaults.LabelSize,
                                lineHeight = LiquidTabsDefaults.LabelSize * 1.2f,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier
                                    .graphicsLayer {
                                        alpha = 1f - collapse
                                        scaleX = 1f - 0.2f * collapse
                                        scaleY = 1f - 0.2f * collapse
                                    }
                                    .height(14.dp * (1f - collapse)),
                            )
                        }
                    }
                }
            }
        }
    }
}
