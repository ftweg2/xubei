package com.ftwegc.myapplication.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.ftwegc.myapplication.ui.background.AuroraPalette
import com.kyant.backdrop.Backdrop

/**
 * Everything a glass needs to know about its surroundings: the tier it may draw with, the theme,
 * the content colors and the scrim strengths that keep text readable on the current background.
 *
 * Built once per background palette at the app root ([of]); glass surfaces read it through
 * [LocalGlass].
 */
@Immutable
class GlassEnvironment private constructor(
    val tier: GlassTier,
    val dark: Boolean,
    val ink: GlassInk,
    /** The background colors glass can sit on (see [AuroraPalette.backdropExtremes]). */
    val backdrops: List<Color>,
    private val panelScrims: Map<GlassThickness, Float>,
    private val floatingScrims: Map<GlassThickness, Float>,
) {
    fun material(thickness: GlassThickness): GlassMaterial = GlassDefaults.material(thickness, dark)

    /**
     * Scrim alpha for [thickness]. [floating] glass sits over page content as well (tiles, charts),
     * so it assumes brighter things behind it. Without blur the background shows through sharp,
     * so the tinted tier never goes below the material's floor.
     */
    fun scrimAlpha(thickness: GlassThickness, floating: Boolean = false): Float {
        val computed = (if (floating) floatingScrims else panelScrims)[thickness] ?: 0f
        return when {
            // Floating glass over sharp, unblurred page content: text under it must not show through.
            tier == GlassTier.Tinted && floating -> maxOf(computed, FLOATING_TINTED_FLOOR)
            tier == GlassTier.Tinted -> maxOf(computed, material(thickness).tintedFloor)
            else -> computed
        }
    }

    /** Scrim alpha when a [tint] (drawn at [tintAlpha] as part of the fill) brightens the glass. */
    fun scrimAlpha(thickness: GlassThickness, floating: Boolean, tint: Color, tintAlpha: Float): Float {
        val base = scrimAlpha(thickness, floating)
        val material = material(thickness)
        val tinted = GlassContrast.minScrimAlpha(
            backdrops = backdrops,
            scrim = material.scrim,
            fills = listOf(tint.copy(alpha = tintAlpha)) + material.contrastFills,
            texts = listOf(ink.primary, ink.secondary),
        )
        return maxOf(base, tinted)
    }

    fun withTier(tier: GlassTier): GlassEnvironment =
        GlassEnvironment(tier, dark, ink, backdrops, panelScrims, floatingScrims)

    companion object {
        /** Floating glass without blur (Android 11 and lower) hides what scrolls under it. */
        const val FLOATING_TINTED_FLOOR: Float = 0.84f

        /** A bright chart line under floating glass, blurred to about half strength. */
        private val BrightContent = Color(0xFF7EF0DD).copy(alpha = 0.5f)

        fun of(palette: AuroraPalette, tier: GlassTier): GlassEnvironment {
            val dark = palette.dark
            val ink = if (dark) GlassDefaults.DarkInk else GlassDefaults.LightInk
            val extremes = palette.backdropExtremes()
            val texts = listOf(ink.primary, ink.secondary)
            val panel = GlassDefaults.material(GlassThickness.Standard, dark)
            // Floating glass also passes over panels and chart strokes.
            val floatingBackdrops = extremes +
                extremes.map { GlassContrast.surface(it, panel.scrim, 0f, panel.contrastFills) } +
                extremes.map { GlassContrast.over(BrightContent, it) }
            fun scrims(backdrops: List<Color>) = GlassThickness.entries.associateWith { thickness ->
                val material = GlassDefaults.material(thickness, dark)
                GlassContrast.minScrimAlpha(backdrops, material.scrim, material.contrastFills, texts)
            }
            return GlassEnvironment(tier, dark, ink, extremes, scrims(extremes), scrims(floatingBackdrops))
        }

        /** Previews and anything outside the app shell: no backdrop, tinted glass. */
        fun fallback(dark: Boolean): GlassEnvironment {
            val ink = if (dark) GlassDefaults.DarkInk else GlassDefaults.LightInk
            val none = GlassThickness.entries.associateWith { 0f }
            return GlassEnvironment(GlassTier.Tinted, dark, ink, emptyList(), none, none)
        }
    }
}

/** The glass environment of the app shell. */
val LocalGlass = staticCompositionLocalOf<GlassEnvironment?> { null }

/**
 * What a glass samples. [backdrop] is the background layer for panels, or a parent glass's
 * exported layer for glass inside glass ([depth] > 0). `null` backdrop: draw without sampling.
 */
@Immutable
class GlassSource(val backdrop: Backdrop?, val depth: Int, val exported: Boolean = false)

val LocalGlassSource = staticCompositionLocalOf { GlassSource(null, 0) }

/**
 * The page layer (background and page content) for floating glass: the dock and the add button
 * refract whatever scrolls under them. Never used inside the page itself.
 */
val LocalPageBackdrop = staticCompositionLocalOf<Backdrop?> { null }
