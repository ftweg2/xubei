package com.ftwegc.myapplication.ui.background

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.ftwegc.myapplication.ui.glass.GlassContrast
import kotlin.math.PI
import kotlin.math.cos

/** Which page the background belongs to: every page has its own arrangement of the same colors. */
@Immutable
sealed interface PageTone {
    data object Home : PageTone
    data object Subscriptions : PageTone
    data object Stats : PageTone
    data object Settings : PageTone

    /** A page about one subscription: its representative color leads (detail page, round 2). */
    data class Accent(val argb: Long) : PageTone
}

/**
 * One soft color blob of the background.
 *
 * Positions are fractions: [cx] and [radius] of the width, [cy] of the height, [driftX] / [driftY]
 * of the width. The blob drifts by (driftX, driftY) and grows to [scaleTo] and back over
 * 2 × [periodMs] (the mockup's `animation: drift … ease-in-out infinite alternate`).
 */
@Immutable
data class AuroraBlob(
    val color: Color,
    val alpha: Float,
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val periodMs: Int,
    val reverse: Boolean = false,
    val driftX: Float = DRIFT_X,
    val driftY: Float = DRIFT_Y,
    val scaleTo: Float = DRIFT_SCALE,
) {
    companion object {
        /** Mockup: `translate(60px, -40px) scale(1.18)` on a 372 px wide phone. */
        const val DRIFT_X: Float = 60f / 372f
        const val DRIFT_Y: Float = -40f / 372f
        const val DRIFT_SCALE: Float = 1.18f
    }
}

/** Blob position and size at one moment, in pixels. */
@Immutable
data class BlobFrame(val x: Float, val y: Float, val radius: Float)

/**
 * The background of one page in one mood and theme. Pure data; drawn by [AuroraBackground].
 *
 * @property screenBlend dark theme: blobs are "screen"-blended onto the dark base like the
 *   mockup (`mix-blend-mode: screen`); light theme: plain alpha over a light base.
 */
@Immutable
data class AuroraPalette(
    val base: Color,
    val blobs: List<AuroraBlob>,
    val dark: Boolean,
) {
    val screenBlend: Boolean get() = dark

    /** Blob color as painted at its center over [base]. */
    fun peakColor(blob: AuroraBlob): Color {
        val top = blob.color.copy(alpha = blob.alpha)
        return if (screenBlend) GlassContrast.screen(top, base) else GlassContrast.over(top, base)
    }

    /**
     * The colors glass may sit on: the base and every blob at its strongest. Overlapping blobs
     * are covered by also mixing each pair (screen blending adds up).
     */
    fun backdropExtremes(): List<Color> {
        val peaks = blobs.map { peakColor(it) }
        val pairs = if (screenBlend) {
            blobs.indices.flatMap { i ->
                (i + 1 until blobs.size).map { j ->
                    GlassContrast.screen(blobs[j].color.copy(alpha = blobs[j].alpha * PAIR_OVERLAP), peaks[i])
                }
            }
        } else {
            emptyList()
        }
        return listOf(base) + peaks + pairs
    }

    companion object {
        /** Where two neighbouring blobs overlap, each is at about a quarter of its strength. */
        private const val PAIR_OVERLAP = 0.25f
    }
}

/** Builds [AuroraPalette]s from the mockup's background fields. Pure. */
object AuroraPalettes {

    /** Mockup `.phone` background. */
    val DarkBase = Color(0xFF070B11)
    val LightBase = Color(0xFFE9EFF5)

    // Mockup blob colors (dark theme).
    val Teal = Color(0xFF0FB9A6)
    val Blue = Color(0xFF3B6DF0)
    val Violet = Color(0xFF7C4DDE)
    val Amber = Color(0xFFE08A1E)
    val Coral = Color(0xFFE0446A)
    val Sky = Color(0xFF1A8FE0)
    val Orchid = Color(0xFFA24DDF)
    val Indigo = Color(0xFF4F5BD5)

    /**
     * Mockup blob opacity is .85 with `screen`; lowered to .72 so that glass keeps readable text
     * with a lighter scrim (see [GlassContrast]).
     */
    const val DarkAlpha: Float = 0.72f
    const val LightAlpha: Float = 0.50f

    /** Mockup drift durations. */
    const val PeriodA: Int = 26_000
    const val PeriodB: Int = 31_000
    const val PeriodC: Int = 36_000

    /** Light theme: the same hues, lighter, so the page stays bright. */
    fun lightVariant(color: Color): Color = GlassContrast.over(Color.White.copy(alpha = 0.38f), color)

    fun of(tone: PageTone, mood: BackgroundMood, dark: Boolean): AuroraPalette {
        val alpha = if (dark) DarkAlpha else LightAlpha
        val blobs = mutableListOf<AuroraBlob>()
        when (tone) {
            PageTone.Home -> {
                blobs += AuroraBlob(Teal, alpha, cx = 0.148f, cy = 0.130f, radius = 0.444f, periodMs = PeriodA)
                blobs += AuroraBlob(Blue, alpha, cx = 0.906f, cy = 0.403f, radius = 0.417f, periodMs = PeriodB, reverse = true)
                blobs += AuroraBlob(Violet, alpha, cx = 0.228f, cy = 0.726f, radius = 0.390f, periodMs = PeriodC)
            }
            PageTone.Subscriptions -> {
                blobs += AuroraBlob(Violet, alpha, cx = 0.866f, cy = 0.112f, radius = 0.430f, periodMs = PeriodA)
                blobs += AuroraBlob(Sky, alpha, cx = 0.081f, cy = 0.471f, radius = 0.403f, periodMs = PeriodB, reverse = true)
                blobs += AuroraBlob(Teal, alpha, cx = 0.812f, cy = 0.831f, radius = 0.403f, periodMs = PeriodC)
            }
            PageTone.Stats -> {
                blobs += AuroraBlob(Blue, alpha, cx = 0.161f, cy = 0.124f, radius = 0.430f, periodMs = PeriodA)
                blobs += AuroraBlob(Teal, alpha, cx = 0.919f, cy = 0.484f, radius = 0.403f, periodMs = PeriodB, reverse = true)
                blobs += AuroraBlob(Orchid, alpha, cx = 0.215f, cy = 0.844f, radius = 0.430f, periodMs = PeriodC)
            }
            PageTone.Settings -> {
                blobs += AuroraBlob(Teal, alpha, cx = 0.170f, cy = 0.110f, radius = 0.420f, periodMs = PeriodA)
                blobs += AuroraBlob(Indigo, alpha, cx = 0.900f, cy = 0.460f, radius = 0.400f, periodMs = PeriodB, reverse = true)
                blobs += AuroraBlob(Sky, alpha, cx = 0.200f, cy = 0.830f, radius = 0.400f, periodMs = PeriodC)
            }
            is PageTone.Accent -> {
                blobs += AuroraBlob(Color(tone.argb), alpha, cx = 0.349f, cy = 0.062f, radius = 0.511f, periodMs = PeriodA)
                blobs += AuroraBlob(Blue, alpha, cx = 0.946f, cy = 0.509f, radius = 0.403f, periodMs = PeriodB, reverse = true)
                blobs += AuroraBlob(Violet, alpha, cx = 0.134f, cy = 0.831f, radius = 0.403f, periodMs = PeriodC)
            }
        }
        // State colors: amber for "due soon", coral for "expired / traffic used up". Mockup
        // `.blob` #4: 230 px, bottom right, opacity .55.
        if (mood.dueSoon) {
            blobs += AuroraBlob(Amber, alpha * 0.66f, cx = 0.798f, cy = 0.900f, radius = 0.309f, periodMs = PeriodA, reverse = true)
        }
        if (mood.alert) {
            blobs += AuroraBlob(Coral, alpha * 0.78f, cx = 0.720f, cy = 0.300f, radius = 0.330f, periodMs = PeriodB)
        }
        val shaped = if (dark) blobs else blobs.map { it.copy(color = lightVariant(it.color)) }
        return AuroraPalette(base = if (dark) DarkBase else LightBase, blobs = shaped, dark = dark)
    }

    /**
     * Where [blob] is at [timeMs] on a [width] × [height] canvas: an ease-in-out back-and-forth
     * between the rest position and the drifted, scaled one. Deterministic, so a frozen clock
     * (animations off) always shows the same picture.
     */
    fun frame(blob: AuroraBlob, timeMs: Long, width: Float, height: Float): BlobFrame {
        val period = blob.periodMs.coerceAtLeast(1).toLong()
        val cycle = (timeMs % (2 * period)).toFloat() / period // 0..2
        val tri = if (cycle <= 1f) cycle else 2f - cycle // 0..1..0
        val phase = if (blob.reverse) 1f - tri else tri
        val eased = easeInOut(phase)
        val radius = blob.radius * width * (1f + (blob.scaleTo - 1f) * eased)
        return BlobFrame(
            x = blob.cx * width + blob.driftX * width * eased,
            y = blob.cy * height + blob.driftY * width * eased,
            radius = radius,
        )
    }

    /** CSS `ease-in-out` approximated with a cosine: 0 → 0, 1 → 1, symmetric. */
    fun easeInOut(t: Float): Float = ((1 - cos(PI * t.coerceIn(0f, 1f))) / 2).toFloat()
}
