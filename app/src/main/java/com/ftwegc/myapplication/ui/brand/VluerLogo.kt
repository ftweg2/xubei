package com.ftwegc.myapplication.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.preview.GlassPreview
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/*
 * The 续杯 mark (docs/mockups/logo-xubei.html, 108 × 108 canvas): a glass cup with liquid inside an
 * open ring. The ring is the app's recurring symbol (due rings, traffic rings, the spending ring).
 * Every coordinate below is the mockup's SVG value; res/drawable holds the same geometry for the
 * launcher, splash, notification and themed icons.
 */

/** Geometry and colors of the mark (SVG units of the 108 canvas). */
object CupMark {
    const val Canvas: Float = 108f

    /** Ring: center (54, 55), radius 36, stroke 5, round caps. */
    val RingCenter: Offset = Offset(54f, 55f)
    const val RingRadius: Float = 36f
    const val RingStroke: Float = 5f

    /** The ring covers 74 % of the circle (`stroke-dasharray="74 100"`). */
    const val RingShare: Float = 0.74f

    /** `rotate(-205)`: the ring starts at 155° (lower left) and runs clockwise. */
    const val RingStart: Float = 155f

    /** Cup: rim at y 33 from x 37 to 71, bottom at y 78; outline stroke 2.2. */
    const val CupTop: Float = 33f
    const val CupBottom: Float = 78f
    const val CupStroke: Float = 2.2f

    /** The liquid's resting surface is at y 52: this share of the cup's height is filled. */
    const val NormalLevel: Float = (CupBottom - 52f) / (CupBottom - CupTop)

    /** Filled up (refreshing, a successful test): the surface a little below the rim. */
    const val FullLevel: Float = (CupBottom - 37f) / (CupBottom - CupTop)

    /** The wave: 14-unit half waves whose control points are 4.5 above / below the surface. */
    const val WaveHalf: Float = 14f
    const val WaveControl: Float = 4.5f

    /** Highlight: (42.5, 38.5) → (45.3, 66), stroke 2.4, round caps. */
    const val HighlightStroke: Float = 2.4f

    // Colors (mockup gradients).
    val Mint = Color(0xFF7EF0DD)
    val Blue = Color(0xFF60A5FA)
    val Violet = Color(0xFFA78BFA)
    val LiquidTop = Color(0xFF8AF5E2)
    val LiquidBottom = Color(0xFF3F8CF0)

    /** Cup outline and highlight on light backgrounds (the brief: close to #12234a). */
    val DarkInk = Color(0xFF12234A)

    /**
     * `linearGradient x1=0 y1=0 x2=1 y2=1` on the ring's box, turned with the ring's `rotate(-205)`:
     * mint at the right (20°), violet at the upper left (200°).
     */
    val RingGradientStart: Offset = Offset(101.84f, 72.41f)
    val RingGradientEnd: Offset = Offset(6.16f, 37.59f)

    /** Cup outline: `M37 33 H71 L66.4 73.5 Q65.8 78 61.4 78 H46.6 Q42.2 78 41.6 73.5 Z`. */
    fun cupPath(path: Path = Path()): Path = path.apply {
        reset()
        moveTo(37f, 33f)
        lineTo(71f, 33f)
        lineTo(66.4f, 73.5f)
        quadraticTo(65.8f, 78f, 61.4f, 78f)
        lineTo(46.6f, 78f)
        quadraticTo(42.2f, 78f, 41.6f, 73.5f)
        close()
    }

    /**
     * The liquid below a wavy surface at [level] (share of the cup's height): the mockup's wave
     * `M28 52 Q35 47.5 42 52 T56 52 …`, moved sideways by [phase] units, its bumps scaled by
     * [amplitude] and the whole surface tilted by [tilt] (units of height per unit of width, around
     * the cup's middle). Closed far below the cup; clip it with [cupPath].
     */
    fun liquidPath(level: Float, phase: Float = 0f, amplitude: Float = 1f, tilt: Float = 0f, path: Path = Path()): Path {
        path.reset()
        val base = CupBottom - level * (CupBottom - CupTop)
        val period = WaveHalf * 2
        val x0 = phase - floor(phase / period) * period - period // in (-28, 0]
        fun y(x: Float, dy: Float) = base + dy + tilt * (x - 54f)
        path.moveTo(x0, y(x0, 0f))
        var x = x0
        var up = true
        while (x < 84f) {
            val c = x + WaveHalf / 2
            val e = x + WaveHalf
            path.quadraticTo(c, y(c, if (up) -WaveControl * amplitude else WaveControl * amplitude), e, y(e, 0f))
            x = e
            up = !up
        }
        path.lineTo(x, 90f)
        path.lineTo(x0, 90f)
        path.close()
        return path
    }

    /** Where the liquid's color gradient starts: the top of the waves (the SVG's bounding box). */
    fun liquidTop(level: Float, amplitude: Float): Float = CupBottom - level * (CupBottom - CupTop) - WaveControl / 2 * amplitude

    val RingColors: List<Color> = listOf(Mint, Blue, Violet)
}

/** The colors of the cup that change with the background. */
@Immutable
data class CupInk(val outline: Color, val highlight: Color, val track: Color) {
    companion object {
        /** Mockup `#mark`: white outline (.85), highlight (.75) and base ring (.12). */
        val OnDark = CupInk(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.12f))

        /** Light backgrounds: the outline and the highlight turn dark; the ring and the liquid keep their colors. */
        val OnLight = CupInk(CupMark.DarkInk.copy(alpha = 0.85f), CupMark.DarkInk.copy(alpha = 0.55f), Color.White.copy(alpha = 0.12f))

        fun of(dark: Boolean): CupInk = if (dark) OnDark else OnLight
    }
}

/** Mockup `#ring`: #7ef0dd → #60a5fa (55 %) → #a78bfa. */
val LogoRing: List<Color> = CupMark.RingColors

/**
 * Draws the mark (without a background) filling this draw scope's smaller side.
 *
 * @param level liquid height as a share of the cup ([CupMark.NormalLevel] at rest).
 * @param ringShare how much of the circle the ring covers ([CupMark.RingShare]; 1 = closed).
 * @param ringRotation turns the ring (and its gradient) clockwise, in degrees.
 * @param phase / [amplitude] / [tilt] move the liquid's surface, see [CupMark.liquidPath].
 */
fun DrawScope.drawCupMark(
    ink: CupInk,
    level: Float = CupMark.NormalLevel,
    ringShare: Float = CupMark.RingShare,
    ringRotation: Float = 0f,
    phase: Float = 0f,
    amplitude: Float = 1f,
    tilt: Float = 0f,
    ringColors: List<Color> = CupMark.RingColors,
    paths: CupPaths = CupPaths(),
) {
    val side = size.minDimension
    val s = side / CupMark.Canvas
    withTransform({
        translate((size.width - side) / 2, (size.height - side) / 2)
        scale(s, s, pivot = Offset.Zero)
    }) {
        val c = CupMark.RingCenter
        val r = CupMark.RingRadius
        val box = Size(r * 2, r * 2)
        val topLeft = Offset(c.x - r, c.y - r)
        // Base ring, then the open gradient ring.
        drawCircle(ink.track, radius = r, center = c, style = Stroke(CupMark.RingStroke))
        if (ringShare > 0f) {
            rotate(ringRotation, pivot = c) {
                drawArc(
                    brush = Brush.linearGradient(
                        0f to ringColors[0], 0.55f to ringColors[1], 1f to ringColors[2],
                        start = CupMark.RingGradientStart, end = CupMark.RingGradientEnd,
                    ),
                    startAngle = CupMark.RingStart,
                    sweepAngle = 360f * ringShare.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = box,
                    style = Stroke(CupMark.RingStroke, cap = StrokeCap.Round),
                )
            }
        }
        val cup = paths.cup
        // The glass body.
        drawPath(
            cup,
            Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0.34f), 0.5f to Color.White.copy(alpha = 0.08f), 1f to Color.White.copy(alpha = 0.22f),
                startX = 37f, endX = 71f,
            ),
        )
        // The liquid, cut by the cup.
        if (level > 0.005f) {
            // A thin layer has no room for waves.
            val amp = amplitude * (level * 6f).coerceAtMost(1f)
            val liquid = CupMark.liquidPath(level, phase, amp, tilt, paths.liquid)
            clipPath(cup) {
                drawPath(
                    liquid,
                    Brush.verticalGradient(0f to CupMark.LiquidTop, 1f to CupMark.LiquidBottom, startY = CupMark.liquidTop(level, amp), endY = 82f),
                )
            }
        }
        drawPath(cup, ink.outline, style = Stroke(CupMark.CupStroke, join = StrokeJoin.Round))
        drawLine(ink.highlight, Offset(42.5f, 38.5f), Offset(45.3f, 66f), strokeWidth = CupMark.HighlightStroke, cap = StrokeCap.Round)
    }
}

/** Reusable paths of one drawn mark (the cup outline never changes). */
class CupPaths {
    val cup: Path = CupMark.cupPath()
    val liquid: Path = Path()
}

/**
 * The 续杯 mark without a background (splash, overview header, about, empty states). The cup's
 * outline and highlight follow the background: white on dark, dark ink on light.
 */
@Composable
fun VluerLogo(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    contentDescription: String? = null,
    dark: Boolean = currentGlass().dark,
    level: Float = CupMark.NormalLevel,
) {
    val ink = CupInk.of(dark)
    val paths = remember { CupPaths() }
    Canvas(
        modifier
            .size(size)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    ) {
        drawCupMark(ink, level = level, paths = paths)
    }
}

/**
 * A damped slosh: the surface tilts back and forth [cycles] times over the animation and comes to
 * rest. [t] 0..1 of the slosh; the tilt in units of height per unit of width.
 */
fun sloshTilt(t: Float, amplitude: Float, cycles: Float): Float {
    if (t <= 0f || t >= 1f) return 0f
    return amplitude * exp(-3.2f * t) * sin(2f * PI.toFloat() * cycles * t)
}

/**
 * The overview header's mark: the liquid sloshes once when the page appears, rises while
 * [refreshing] and falls back when the refresh ends (with another small slosh). Still, at the end
 * state, when animations are off.
 */
@Composable
fun LiveCupLogo(refreshing: Boolean, modifier: Modifier = Modifier, size: Dp = 44.dp, dark: Boolean = currentGlass().dark) {
    val reduced = LocalReducedMotion.current
    val slosh = remember { Animatable(if (reduced) 1f else 0f) }
    val level by animateFloatAsState(
        targetValue = if (refreshing) CupMark.FullLevel else CupMark.NormalLevel,
        animationSpec = if (reduced) tween(0) else tween(Motion.DurationCupLevel, easing = Motion.Emphasized),
        label = "cupLevel",
    )
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(refreshing, reduced) {
        if (reduced) return@LaunchedEffect
        // Entry, and each time the level turns around.
        if (!first[0] && refreshing) return@LaunchedEffect
        first[0] = false
        slosh.snapTo(0f)
        slosh.animateTo(1f, tween(Motion.DurationCupSlosh, easing = LinearEasing))
    }
    val ink = CupInk.of(dark)
    val paths = remember { CupPaths() }
    Canvas(modifier.size(size)) {
        val t = slosh.value
        drawCupMark(
            ink,
            level = level,
            tilt = sloshTilt(t, Motion.CupSloshTilt, Motion.CupSloshCycles),
            phase = if (t in 0f..<1f) t * Motion.CupSloshDrift else 0f,
            paths = paths,
        )
    }
}

@PreviewLightDark
@Composable
private fun VluerLogoPreview() {
    GlassPreview {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            VluerLogo(size = 28.dp)
            VluerLogo()
            VluerLogo(size = 96.dp)
        }
    }
}
