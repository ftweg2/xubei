package com.ftwegc.myapplication.ui.glass

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Text contrast on glass. Pure color math (sRGB, WCAG 2 relative luminance), no Android types
 * besides [Color]'s components, so it is unit tested on the JVM.
 *
 * A glass surface is drawn as: backdrop → scrim (darkens or lightens it) → fill (the glass sheen)
 * → text. The backdrop is the flowing background, whose colors are known ([backdrops] are its
 * brightest / darkest points). [minScrimAlpha] finds the weakest scrim that still gives every text
 * color at least the target ratio over every backdrop: when the background gets brighter (a warm
 * palette, a bright subscription color), the scrim automatically gets stronger.
 */
object GlassContrast {

    /** WCAG AA for body text. */
    const val BODY_TEXT_RATIO: Float = 4.5f

    private const val SEARCH_STEPS = 24

    /** sRGB channel (0..1) to linear light. */
    fun linear(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

    /** WCAG relative luminance of an opaque color (its alpha is ignored). */
    fun luminance(color: Color): Float =
        0.2126f * linear(color.red) + 0.7152f * linear(color.green) + 0.0722f * linear(color.blue)

    /** WCAG contrast ratio of two opaque colors, 1..21. */
    fun contrast(a: Color, b: Color): Float {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    /** [top] (with its alpha, times [alpha]) painted over the opaque [bottom]; the result is opaque. */
    fun over(top: Color, bottom: Color, alpha: Float = 1f): Color {
        val a = (top.alpha * alpha).coerceIn(0f, 1f)
        return Color(
            red = top.red * a + bottom.red * (1 - a),
            green = top.green * a + bottom.green * (1 - a),
            blue = top.blue * a + bottom.blue * (1 - a),
            alpha = 1f,
        )
    }

    /** "Screen" blending of [top] (with its alpha) over the opaque [bottom], as the dark background draws its color blobs. */
    fun screen(top: Color, bottom: Color): Color {
        val mixed = Color(
            red = 1 - (1 - top.red) * (1 - bottom.red),
            green = 1 - (1 - top.green) * (1 - bottom.green),
            blue = 1 - (1 - top.blue) * (1 - bottom.blue),
            alpha = 1f,
        )
        return over(mixed, bottom, top.alpha)
    }

    /** The surface color of a glass with [scrim] at [scrimAlpha] and the [fills] (bottom to top) over [backdrop]. */
    fun surface(backdrop: Color, scrim: Color, scrimAlpha: Float, fills: List<Color>): Color {
        var color = over(scrim.copy(alpha = 1f), backdrop, scrimAlpha)
        for (fill in fills) color = over(fill, color)
        return color
    }

    /** True when every [texts] color (with its alpha) reaches [ratio] on [surface]. */
    fun readable(surface: Color, texts: List<Color>, ratio: Float): Boolean =
        texts.all { text -> contrast(over(text, surface), surface) >= ratio }

    /**
     * The smallest scrim alpha in 0..[maxAlpha] that makes all [texts] readable (at least [ratio])
     * on the glass over each of [backdrops]; [maxAlpha] when even that is not enough.
     */
    fun minScrimAlpha(
        backdrops: List<Color>,
        scrim: Color,
        fills: List<Color>,
        texts: List<Color>,
        ratio: Float = BODY_TEXT_RATIO,
        maxAlpha: Float = 1f,
    ): Float {
        fun ok(alpha: Float) = backdrops.all { readable(surface(it, scrim, alpha, fills), texts, ratio) }
        if (backdrops.isEmpty() || ok(0f)) return 0f
        if (!ok(maxAlpha)) return maxAlpha
        var low = 0f
        var high = maxAlpha
        repeat(SEARCH_STEPS) {
            val mid = (low + high) / 2
            if (ok(mid)) high = mid else low = mid
        }
        return high
    }
}
