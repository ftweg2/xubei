package com.ftwegc.myapplication.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.LocalVluerColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Chart colors (mockup `:root`): mint, teal, blue, violet, amber, coral; deeper tones in light
 * mode so thin strokes stay visible on light glass.
 */
@Immutable
data class ChartColors(
    val mint: Color,
    val teal: Color,
    val blue: Color,
    val violet: Color,
    val amber: Color,
    val coral: Color,
    /** Tracks behind rings and bars. */
    val track: Color,
    /** "Future" bars and the forecast dash. */
    val future: Color,
    /** Dots and highlights drawn on top. */
    val highlight: Color,
    /** Text on charts. */
    val ink: Color,
    val inkSecondary: Color,
    val dark: Boolean,
) {
    /** Series colors in order. */
    val series: List<Color> get() = listOf(coral, amber, teal, blue, violet, mint)
}

@Composable
@ReadOnlyComposable
fun chartColors(): ChartColors {
    val ink = currentGlass().ink
    return if (LocalVluerColors.current.isDark) {
        ChartColors(
            mint = Color(0xFF7EF0DD), teal = Color(0xFF2DD4BF), blue = Color(0xFF60A5FA), violet = Color(0xFFA78BFA),
            amber = Color(0xFFFBBF24), coral = Color(0xFFFB7185),
            track = Color.White.copy(alpha = 0.13f), future = Color.White.copy(alpha = 0.40f), highlight = Color.White,
            ink = ink.primary, inkSecondary = ink.secondary, dark = true,
        )
    } else {
        ChartColors(
            mint = Color(0xFF14B8A6), teal = Color(0xFF0D9488), blue = Color(0xFF2563EB), violet = Color(0xFF7C3AED),
            amber = Color(0xFFD97706), coral = Color(0xFFE11D48),
            track = Color(0xFF0B1A20).copy(alpha = 0.10f), future = Color(0xFF0B1A20).copy(alpha = 0.22f), highlight = Color.White,
            ink = ink.primary, inkSecondary = ink.secondary, dark = false,
        )
    }
}

/**
 * What each chart showed last, per memory key, for the whole process: a chart that comes back on
 * screen with the same data starts at its end state; only changed data animates.
 */
object ChartMemory {
    private val values = ConcurrentHashMap<String, Any>()

    fun get(key: String): Any? = values[key]

    fun put(key: String, value: Any) {
        values[key] = value
    }

    fun clear() = values.clear()
}

/**
 * 0 → 1 reveal of a chart (line drawing, fade-in). Plays when [data] is shown for the first time
 * under [memoryKey] or when it changed; otherwise starts at 1. Jumps to 1 with animations off.
 */
@Composable
fun rememberReveal(
    data: Any,
    memoryKey: String?,
    durationMillis: Int,
    delayMillis: Int,
    easing: Easing,
): State<Float> {
    val reduced = LocalReducedMotion.current
    val anim = remember {
        val seen = memoryKey != null && ChartMemory.get(memoryKey) == data
        Animatable(if (reduced || seen) 1f else 0f)
    }
    LaunchedEffect(data, reduced) {
        val seen = memoryKey != null && ChartMemory.get(memoryKey) == data
        memoryKey?.let { ChartMemory.put(it, data) }
        if (reduced) {
            anim.snapTo(1f)
            return@LaunchedEffect
        }
        if (seen && anim.value >= 1f) return@LaunchedEffect
        if (anim.value >= 1f) anim.snapTo(0f)
        anim.animateTo(1f, tween(durationMillis, delayMillis, easing))
    }
    return anim.asState()
}

/**
 * Animated values for rings, bars and gauges: the first time they grow from 0 (each after
 * [delayOf] its index), later they move from what was shown to the new [targets]. Coming back to
 * a chart with unchanged values shows them without animation ([memoryKey]).
 */
@Composable
fun rememberAnimatedValues(
    targets: List<Float>,
    memoryKey: String?,
    durationMillis: Int,
    easing: Easing,
    delayOf: (Int) -> Int = { 0 },
    updateMillis: Int = com.ftwegc.myapplication.ui.theme.Motion.DurationChartUpdate,
): List<State<Float>> {
    val reduced = LocalReducedMotion.current
    val animatables = remember(targets.size) {
        @Suppress("UNCHECKED_CAST")
        val remembered = memoryKey?.let { ChartMemory.get(it) as? List<Float> }
        List(targets.size) { i ->
            val start = when {
                reduced -> targets[i]
                remembered != null && remembered.size == targets.size -> remembered[i]
                else -> 0f
            }
            Animatable(start)
        }
    }
    val first = remember(targets.size) { mutableStateOf(true) }
    LaunchedEffect(targets, reduced) {
        memoryKey?.let { ChartMemory.put(it, targets) }
        val entrance = first.value && animatables.all { it.value == 0f }
        first.value = false
        coroutineScope {
            animatables.forEachIndexed { i, a: Animatable<Float, AnimationVector1D> ->
                val target = targets.getOrElse(i) { 0f }
                if (reduced) {
                    launch { a.snapTo(target) }
                } else if (a.value != target) {
                    val spec = if (entrance) tween<Float>(durationMillis, delayOf(i), easing) else tween(updateMillis, easing = easing)
                    launch { a.animateTo(target, spec) }
                }
            }
        }
    }
    return animatables.map { it.asState() }
}

/** The part of [path] from its start to [fraction] of its length (the SVG `stroke-dashoffset` draw). */
fun partialPath(measure: PathMeasure, length: Float, fraction: Float, out: Path): Path {
    out.reset()
    if (fraction >= 1f) {
        measure.getSegment(0f, length, out, true)
    } else if (fraction > 0f) {
        measure.getSegment(0f, length * fraction, out, true)
    }
    return out
}

/** Builds a path from smooth segments. */
fun segmentsPath(segments: List<CubicSegment>, path: Path = Path()): Path {
    path.reset()
    if (segments.isEmpty()) return path
    path.moveTo(segments.first().from.x, segments.first().from.y)
    segments.forEach { path.cubicTo(it.c1.x, it.c1.y, it.c2.x, it.c2.y, it.to.x, it.to.y) }
    return path
}

/**
 * Placeholder when a chart has too little data: a faint dashed shape of [kind] and one sentence.
 */
@Composable
fun ChartPlaceholder(text: String, modifier: Modifier = Modifier, kind: PlaceholderKind = PlaceholderKind.Line) {
    val colors = chartColors()
    Box(modifier.semantics { contentDescription = text }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 6.dp.toPx()))
            val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash)
            when (kind) {
                PlaceholderKind.Line -> {
                    val h = size.height
                    val w = size.width
                    val path = Path().apply {
                        moveTo(0f, h * 0.78f)
                        cubicTo(w * 0.25f, h * 0.78f, w * 0.35f, h * 0.55f, w * 0.55f, h * 0.55f)
                        cubicTo(w * 0.75f, h * 0.55f, w * 0.8f, h * 0.3f, w, h * 0.28f)
                    }
                    drawPath(path, colors.track.copy(alpha = colors.track.alpha * 2f), style = stroke)
                }
                PlaceholderKind.Ring -> {
                    val r = size.minDimension / 2f - 6.dp.toPx()
                    drawCircle(colors.track.copy(alpha = colors.track.alpha * 2f), radius = r, style = stroke)
                }
                PlaceholderKind.Bars -> {
                    val n = 7
                    val gap = 5.dp.toPx()
                    val bw = (size.width - gap * (n - 1)) / n
                    repeat(n) { i ->
                        val bh = size.height * (0.3f + 0.08f * ((i * 3) % 5))
                        drawRoundRect(
                            colors.track,
                            topLeft = Offset(i * (bw + gap), size.height - bh),
                            size = androidx.compose.ui.geometry.Size(bw, bh),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                        )
                    }
                }
            }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(contentAlignment = Alignment.Center) {
            // Inside a ring the sentence wraps to stay within the circle.
            val limit = if (kind == PlaceholderKind.Ring) minOf(maxWidth, maxHeight) * 0.78f else maxWidth
            Text(
                text,
                color = colors.inkSecondary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = limit)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

enum class PlaceholderKind { Line, Ring, Bars }

/**
 * A soft glow around a rounded rectangle (CSS `box-shadow: 0 0 16px color`): a few widening,
 * fading rings, cheap enough for every frame of an animation.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSoftGlow(
    color: Color,
    topLeft: Offset,
    size: androidx.compose.ui.geometry.Size,
    corner: Float,
    spread: Float,
    steps: Int = 5,
) {
    for (i in steps downTo 1) {
        val grow = spread * i / steps
        drawRoundRect(
            color.copy(alpha = color.alpha / steps * (1f - (i - 1f) / steps)),
            topLeft = Offset(topLeft.x - grow, topLeft.y - grow),
            size = androidx.compose.ui.geometry.Size(size.width + grow * 2, size.height + grow * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner + grow),
        )
    }
}
