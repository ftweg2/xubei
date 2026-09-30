package com.ftwegc.myapplication.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.ui.preview.GlassPreview
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

/**
 * Liquid traffic ring (mockup `.liquid`): a round glass vessel whose liquid level is the used
 * share, with two waves moving in opposite directions. The level rises on entrance and the liquid
 * sloshes for about three seconds, then comes to rest (a flat surface, nothing redrawn any more:
 * a page left open costs no frames). It sloshes again when the value changes or on a tap. Still
 * with animations off.
 *
 * @param colors liquid gradient, top to bottom (coral by default, like the mockup at 94 %).
 */
@Composable
fun LiquidRing(
    fraction: Float,
    value: String,
    label: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 128.dp,
    colors: List<Color> = listOf(Color(0xFFFB7185).copy(alpha = 0.95f), Color(0xFFBE285A).copy(alpha = 0.85f)),
    waveBack: Color = Color(0xFFFFBEC8).copy(alpha = 0.9f),
    memoryKey: String? = null,
    valueSize: androidx.compose.ui.unit.TextUnit = 32.sp,
) {
    val reduced = LocalReducedMotion.current
    val level = rememberAnimatedValues(
        targets = listOf(fraction.coerceIn(0f, 1f)),
        memoryKey = memoryKey,
        durationMillis = Motion.DurationLiquidRise,
        easing = Motion.Standard,
    )
    var time by remember { mutableLongStateOf(0L) }
    val slosh = remember { Animatable(0f) }
    var taps by remember { mutableIntStateOf(0) }
    val target = fraction.coerceIn(0f, 1f)
    // Entry, a new value or a tap: slosh, then rest. The frame loop ends with the slosh.
    LaunchedEffect(target, taps, reduced) {
        if (reduced) {
            slosh.snapTo(0f)
            return@LaunchedEffect
        }
        slosh.snapTo(1f)
        coroutineScope {
            launch { slosh.animateTo(0f, tween(Motion.DurationLiquidSlosh, easing = Motion.StandardDecelerate)) }
            var start = -1L
            val base = time
            while (isActive && (start < 0 || slosh.isRunning)) {
                withFrameMillis { now ->
                    if (start < 0) start = now
                    time = base + (now - start)
                }
            }
        }
    }
    val chart = chartColors()
    val tapLabel = androidx.compose.ui.res.stringResource(com.ftwegc.myapplication.R.string.liquid_slosh)
    Box(
        modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = !reduced,
                onClickLabel = tapLabel,
            ) { taps++ },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(size)
                // The light vessel is translucent: an elevation shadow would show through it as a
                // polygon of shade, so the light theme goes without.
                .then(
                    if (chart.dark) {
                        Modifier.shadow(10.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                    } else {
                        Modifier
                    },
                )
                .clip(CircleShape),
        ) {
            val w = this.size.width
            val h = this.size.height
            // Vessel: background: rgba(255,255,255,.07).
            drawRect(if (chart.dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.45f))
            val f = level[0].value.coerceIn(0f, 1f)
            val surface = h * (1f - f)
            // At rest the surface is flat (the waves only move while the liquid sloshes).
            val amp = h * Motion.LiquidSloshAmplitude * slosh.value
            val t = time.toFloat()
            // The back wave, lighter and slower, moving the other way (.wv.b: opacity .5, 5 s, reverse).
            drawWave(surface, amp * 0.9f, w / 4f, -t / Motion.DurationWaveBack * w * 2f, waveBack.copy(alpha = waveBack.alpha * 0.5f), phaseShift = PI.toFloat())
            // Liquid body and the front wave (3.2 s).
            drawWave(surface, amp, w / 4f, t / Motion.DurationWaveFront * w * 2f, null, brush = Brush.verticalGradient(colors, startY = surface - amp, endY = h))
            // Glass: inset 0 0 0 1.5px rgba(255,255,255,.4), inset 0 10px 22px rgba(255,255,255,.12),
            // inset 0 -12px 26px rgba(0,0,0,.35).
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 0.25f to Color.Transparent))
            drawRect(Brush.verticalGradient(0.72f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f)))
            drawCircle(Color.White.copy(alpha = 0.4f), radius = w / 2 - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            // ::after: an ellipse of light at 16 % / 9 %, 42 % × 26 %, rotated -24°.
            val hl = Offset(w * (0.16f + 0.21f), h * (0.09f + 0.13f))
            rotate(-24f, pivot = hl) {
                scale(scaleX = 0.42f / 0.26f, scaleY = 1f, pivot = hl) {
                    drawCircle(
                        Brush.radialGradient(0f to Color.White.copy(alpha = 0.5f), 0.7f to Color.Transparent, center = hl, radius = h * 0.13f),
                        radius = h * 0.13f,
                        center = hl,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // In the light theme the empty vessel is pale: while the liquid stays below the
            // number, it is dark ink; over liquid (and in the dark theme) white with a shadow.
            val overLiquid = chart.dark || target >= LIGHT_INK_BELOW
            val ink = if (overLiquid) Color.White else com.ftwegc.myapplication.ui.glass.currentGlass().ink.primary
            val style = if (overLiquid) {
                androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 8f))
            } else {
                androidx.compose.ui.text.TextStyle.Default
            }
            Text(value, color = ink, fontSize = valueSize, lineHeight = valueSize, fontWeight = FontWeight.SemiBold, style = style)
            Text(label, color = ink.copy(alpha = 0.9f), fontSize = 11.sp, style = style)
        }
    }
}

/** Light theme: below this fill the number sits on the pale empty vessel and is drawn in ink. */
private const val LIGHT_INK_BELOW = 0.55f

/** A sine wave surface at [surfaceY] filled down to the bottom. */
private fun DrawScope.drawWave(
    surfaceY: Float,
    amplitude: Float,
    wavelength: Float,
    shift: Float,
    color: Color?,
    brush: Brush? = null,
    phaseShift: Float = 0f,
) {
    val path = Path()
    val step = 4f
    path.moveTo(0f, size.height)
    var x = 0f
    while (x <= size.width + step) {
        val y = surfaceY + amplitude * sin(((x + shift) / wavelength) * 2f * PI.toFloat() + phaseShift)
        path.lineTo(x, y)
        x += step
    }
    path.lineTo(size.width, size.height)
    path.close()
    if (brush != null) drawPath(path, brush) else if (color != null) drawPath(path, color)
}

/** Liquid color for a used share: teal, amber from 70 %, coral from 90 % (like the traffic bars). */
fun liquidColors(fraction: Float, chart: ChartColors): List<Color> {
    val base = when {
        fraction >= 0.9f -> chart.coral
        fraction >= 0.7f -> lerp(chart.amber, chart.coral, ((fraction - 0.7f) / 0.2f).coerceIn(0f, 1f))
        else -> chart.teal
    }
    return listOf(base.copy(alpha = 0.95f), lerp(base, Color.Black, 0.25f).copy(alpha = 0.85f))
}

@PreviewLightDark
@Composable
private fun LiquidRingPreview() {
    GlassPreview {
        val c = chartColors()
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LiquidRing(0.94f, "94%", "已用", "流量已用 94%")
            LiquidRing(0.35f, "35%", "已用", "流量已用 35%", colors = liquidColors(0.35f, c), waveBack = c.mint)
        }
    }
}
