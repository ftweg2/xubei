package com.ftwegc.myapplication.ui.glass

import androidx.compose.ui.graphics.Color
import com.ftwegc.myapplication.ui.background.AuroraPalettes
import com.ftwegc.myapplication.ui.background.BackgroundMood
import com.ftwegc.myapplication.ui.background.PageTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassSystemTest {

    // ------------------------------------------------------------ contrast math

    @Test
    fun luminanceAndContrast_matchWcag() {
        assertEquals(0f, GlassContrast.luminance(Color.Black), 1e-6f)
        assertEquals(1f, GlassContrast.luminance(Color.White), 1e-4f)
        assertEquals(21f, GlassContrast.contrast(Color.Black, Color.White), 0.01f)
        assertEquals(1f, GlassContrast.contrast(Color.Red, Color.Red), 1e-6f)
        // #767676 on white is the classic 4.54:1.
        assertEquals(4.54f, GlassContrast.contrast(Color(0xFF767676), Color.White), 0.02f)
    }

    @Test
    fun overAndScreen_blend() {
        val half = GlassContrast.over(Color.White.copy(alpha = 0.5f), Color.Black)
        assertEquals(0.5f, half.red, 0.01f) // alpha is stored in 8 bits
        // Screen never darkens.
        val base = Color(0xFF070B11)
        val teal = Color(0xFF0FB9A6).copy(alpha = 0.72f)
        val mixed = GlassContrast.screen(teal, base)
        assertTrue(GlassContrast.luminance(mixed) > GlassContrast.luminance(base))
    }

    @Test
    fun minScrimAlpha_isZeroWhenAlreadyReadable() {
        val alpha = GlassContrast.minScrimAlpha(
            backdrops = listOf(Color(0xFF070B11)),
            scrim = Color.Black,
            fills = emptyList(),
            texts = listOf(Color.White),
        )
        assertEquals(0f, alpha, 0f)
    }

    @Test
    fun minScrimAlpha_makesBrightBackdropsReadable_andIsMinimal() {
        val ink = GlassDefaults.DarkInk
        val material = GlassDefaults.material(GlassThickness.Standard, dark = true)
        val bright = listOf(Color(0xFF7EF0DD), Color(0xFFFBBF24))
        val alpha = GlassContrast.minScrimAlpha(bright, material.scrim, material.contrastFills, listOf(ink.primary, ink.secondary))
        assertTrue(alpha in 0.01f..1f)
        bright.forEach { backdrop ->
            val surface = GlassContrast.surface(backdrop, material.scrim, alpha, material.contrastFills)
            assertTrue(GlassContrast.readable(surface, listOf(ink.primary, ink.secondary), GlassContrast.BODY_TEXT_RATIO))
        }
        // A clearly weaker scrim is not enough for at least one backdrop.
        val weaker = (alpha - 0.05f).coerceAtLeast(0f)
        assertTrue(bright.any { !GlassContrast.readable(GlassContrast.surface(it, material.scrim, weaker, material.contrastFills), listOf(ink.primary, ink.secondary), 4.5f) })
    }

    @Test
    fun minScrimAlpha_returnsMaxWhenImpossible() {
        // White text on a white scrim can never be readable.
        val alpha = GlassContrast.minScrimAlpha(listOf(Color.White), Color.White, emptyList(), listOf(Color.White), maxAlpha = 0.8f)
        assertEquals(0.8f, alpha, 0f)
    }

    @Test
    fun environment_keepsBodyTextReadableOnEveryPaletteExtreme() {
        for (dark in listOf(true, false)) {
            for (mood in listOf(BackgroundMood.Calm, BackgroundMood(dueSoon = true), BackgroundMood(dueSoon = true, alert = true))) {
                for (tone in listOf(PageTone.Home, PageTone.Subscriptions, PageTone.Stats, PageTone.Settings, PageTone.Accent(0xFFFFD60A))) {
                    val palette = AuroraPalettes.of(tone, mood, dark)
                    val env = GlassEnvironment.of(palette, GlassTier.Refractive)
                    for (thickness in GlassThickness.entries) {
                        val material = env.material(thickness)
                        val scrim = env.scrimAlpha(thickness)
                        palette.backdropExtremes().forEach { backdrop ->
                            val surface = GlassContrast.surface(backdrop, material.scrim, scrim, material.contrastFills)
                            assertTrue(
                                "dark=$dark mood=$mood tone=$tone $thickness",
                                GlassContrast.readable(surface, listOf(env.ink.primary, env.ink.secondary), GlassContrast.BODY_TEXT_RATIO),
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun environment_darkensGlassForBrighterBackgrounds() {
        val calm = GlassEnvironment.of(AuroraPalettes.of(PageTone.Home, BackgroundMood.Calm, dark = true), GlassTier.Refractive)
        val yellow = GlassEnvironment.of(AuroraPalettes.of(PageTone.Accent(0xFFFFE600), BackgroundMood.Calm, dark = true), GlassTier.Refractive)
        assertTrue(yellow.scrimAlpha(GlassThickness.Standard) > calm.scrimAlpha(GlassThickness.Standard))
        // A tint washes the glass brighter: the scrim follows.
        val tinted = calm.scrimAlpha(GlassThickness.Standard, floating = false, tint = Color(0xFFFBBF24), tintAlpha = GLASS_TINT_ALPHA)
        assertTrue(tinted >= calm.scrimAlpha(GlassThickness.Standard))
    }

    @Test
    fun tintedTier_neverBelowTheFloor() {
        val palette = AuroraPalettes.of(PageTone.Home, BackgroundMood.Calm, dark = true)
        val env = GlassEnvironment.of(palette, GlassTier.Tinted)
        GlassThickness.entries.forEach { t ->
            assertTrue(env.scrimAlpha(t) >= env.material(t).tintedFloor)
        }
    }

    // ------------------------------------------------------------ tiers

    @Test
    fun tier_followsPlatform() {
        assertEquals(GlassTier.Refractive, GlassTier.resolve(35, null, false))
        assertEquals(GlassTier.Refractive, GlassTier.resolve(33, null, false))
        assertEquals(GlassTier.Blurred, GlassTier.resolve(32, null, false))
        assertEquals(GlassTier.Blurred, GlassTier.resolve(31, null, false))
        assertEquals(GlassTier.Tinted, GlassTier.resolve(30, null, false))
        assertEquals(GlassTier.Tinted, GlassTier.resolve(26, null, false))
    }

    @Test
    fun tier_overrideOnlyLowers_andReduceTransparencyWins() {
        assertEquals(GlassTier.Blurred, GlassTier.resolve(35, GlassTier.Blurred, false))
        assertEquals(GlassTier.Tinted, GlassTier.resolve(35, GlassTier.Tinted, false))
        // Android 11 cannot be forced up to blur.
        assertEquals(GlassTier.Tinted, GlassTier.resolve(30, GlassTier.Refractive, false))
        assertEquals(GlassTier.Solid, GlassTier.resolve(35, null, true))
        assertEquals(GlassTier.Solid, GlassTier.resolve(26, GlassTier.Blurred, true))
    }

    @Test
    fun tier_keys() {
        GlassTier.entries.forEach { assertEquals(it, GlassTier.fromKey(it.key)) }
        assertEquals(GlassTier.Blurred, GlassTier.fromKey(" Blurred "))
        assertNull(GlassTier.fromKey(null))
        assertNull(GlassTier.fromKey("glassy"))
        assertTrue(GlassTier.Refractive.blurs && GlassTier.Refractive.refracts)
        assertTrue(GlassTier.Blurred.blurs && !GlassTier.Blurred.refracts)
        assertTrue(!GlassTier.Tinted.blurs && !GlassTier.Solid.blurs)
    }
}
