package com.ftwegc.myapplication.ui.brand

import androidx.compose.ui.graphics.vector.PathParser
import com.ftwegc.myapplication.domain.brand.Monogram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandCatalogTest {

    @Test
    fun resolve_matchesByName() {
        val spec = BrandCatalog.resolve("Claude Max")
        assertEquals("claude", spec.brandId)
        assertEquals("C", spec.letter)
        assertEquals(BrandCatalog.brandGradient(0xFFD97757), spec.start to spec.end)
    }

    @Test
    fun resolve_letterMarkWithStableColor() {
        val spec = BrandCatalog.resolve("洛杉矶 9929")
        assertNull(spec.brandId)
        assertEquals("洛", spec.letter)
        assertEquals(BrandCatalog.MonogramGradients[Monogram.colorIndex("洛杉矶 9929", BrandCatalog.MonogramGradients.size)], spec.start to spec.end)
        assertEquals(spec, BrandCatalog.resolve("洛杉矶 9929"))
    }

    @Test
    fun resolve_userChoiceWins() {
        // A brand picked by hand, whatever the name says.
        assertEquals("spotify", BrandCatalog.resolve("家里的音乐", iconKey = "spotify").brandId)
        // Forced letter mark for a name that would match.
        val letter = BrandCatalog.resolve("ChatGPT Plus", iconKey = BrandCatalog.LETTER_KEY)
        assertNull(letter.brandId)
        assertEquals("C", letter.letter)
        // Unknown key: back to matching by name.
        assertEquals("openai", BrandCatalog.resolve("ChatGPT Plus", iconKey = "gone").brandId)
        // A picked color replaces the brand color.
        val colored = BrandCatalog.resolve("ChatGPT Plus", iconColor = 0xFF123456.toInt())
        assertEquals(BrandCatalog.gradientOf(0xFF123456), colored.start to colored.end)
    }

    @Test
    fun blackLogosSitOnGraphite() {
        assertEquals(BrandCatalog.NeutralGradient, BrandCatalog.brandGradient(0xFF000000))
        assertEquals(BrandCatalog.NeutralGradient, BrandCatalog.brandGradient(0xFF191919))
        assertNotEquals(BrandCatalog.NeutralGradient, BrandCatalog.brandGradient(0xFFD97757))
        assertEquals(BrandCatalog.NeutralGradient, BrandCatalog.resolve("Cursor").let { it.start to it.end })
    }

    @Test
    fun gradient_isLighterThenDarker() {
        val (start, end) = BrandCatalog.gradientOf(0xFF3B6DF0)
        fun lum(c: Long) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
        assertTrue(lum(start) > lum(0xFF3B6DF0))
        assertTrue(lum(end) < lum(0xFF3B6DF0))
        assertEquals(0xFF, (start shr 24).toInt() and 0xFF)
    }

    @Test
    fun everyGlyphParses() {
        BrandGlyphs.all.forEach { (id, glyph) ->
            assertTrue(id, glyph.paths.isNotEmpty())
            assertTrue(id, glyph.viewportWidth > 0 && glyph.viewportHeight > 0)
            glyph.paths.forEach { path -> assertTrue(id, PathParser().parsePathString(path.d).toNodes().isNotEmpty()) }
        }
        // Dashboard Icons' Oracle is an outline: drawn with a stroke.
        assertTrue(BrandGlyphs.all.getValue("oracle").paths.all { it.strokeWidth != null })
    }
}
