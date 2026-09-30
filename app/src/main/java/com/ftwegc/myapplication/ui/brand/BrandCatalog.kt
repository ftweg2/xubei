package com.ftwegc.myapplication.ui.brand

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.ftwegc.myapplication.domain.brand.Brand
import com.ftwegc.myapplication.domain.brand.BrandMatcher
import com.ftwegc.myapplication.domain.brand.Monogram
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.ui.glass.GlassContrast
import java.util.concurrent.ConcurrentHashMap

/** Where a built-in icon comes from (see THIRD_PARTY_NOTICES.md). */
enum class IconSource(val title: String, val license: String) {
    SIMPLE_ICONS("Simple Icons 16.33.0", "CC0-1.0"),
    LOBE_ICONS("Lobe Icons (@lobehub/icons-static-svg 1.95.1)", "MIT"),
    DASHBOARD_ICONS("Dashboard Icons (homarr-labs)", "Apache-2.0"),
}

/** One path of a glyph: SVG path data, fill rule, fill opacity, or a stroke width for outline icons. */
@Immutable
data class GlyphPath(val d: String, val evenOdd: Boolean = false, val alpha: Float = 1f, val strokeWidth: Float? = null)

/**
 * A service icon: SVG paths in their own viewport and the brand color the icon library gives.
 *
 * @property color ARGB.
 */
@Immutable
data class BrandGlyph(
    val source: IconSource,
    val sourceName: String,
    val color: Long,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val paths: List<GlyphPath>,
)

/**
 * What a subscription's mark shows.
 *
 * @property brandId built-in icon, or `null` for the letter mark.
 * @property letter first letter / character of the name (used without an icon).
 * @property start / [end] base gradient, ARGB.
 */
@Immutable
data class MarkSpec(val brandId: String?, val letter: String, val start: Long, val end: Long)

/** Everything needed to draw a subscription's mark. */
@Immutable
data class MarkSource(
    val name: String,
    val category: Category = Category.OTHER,
    val iconKey: String? = null,
    val iconColor: Int? = null,
    /** Komari region flag emoji; shown on VPS marks. */
    val region: String? = null,
)

/** Built-in icons, their colors, and the choice of icon for a name. */
object BrandCatalog {

    /** [MarkSource.iconKey] value that forces the letter mark. */
    const val LETTER_KEY: String = "letter"

    /** [MarkSource.iconKey] prefix of a picture from the gallery ("image:<file name>"). */
    const val IMAGE_PREFIX: String = "image:"

    /** The file name of a gallery picture key, `null` for any other key. */
    fun imageName(iconKey: String?): String? =
        iconKey?.takeIf { it.startsWith(IMAGE_PREFIX) }?.removePrefix(IMAGE_PREFIX)?.takeIf { it.isNotBlank() && '/' !in it && '\\' !in it && ".." !in it }

    /**
     * Letter mark gradients (mockup `.m-*`): rose, sky, indigo, red, green, blue-violet, ice,
     * clay, teal, amber.
     */
    val MonogramGradients: List<Pair<Long, Long>> = listOf(
        0xFFFF7A90 to 0xFFC2335A,
        0xFF38C6FF to 0xFF1662C4,
        0xFF7C8CFF to 0xFF3A3FB8,
        0xFFFF4D3D to 0xFFB01208,
        0xFF1FCF8B to 0xFF0B8A59,
        0xFF6EA8FF to 0xFF8F5BF0,
        0xFF9FD3FF to 0xFF3C8EE6,
        0xFFE08A67 to 0xFFB4532F,
        0xFF2DD4BF to 0xFF0F766E,
        0xFFFBBF24 to 0xFFB45309,
    )

    /** Graphite base for black / near-black logos (mockup `.m-cursor`). */
    val NeutralGradient: Pair<Long, Long> = 0xFF5B6472 to 0xFF1D222B

    /** Logos darker than this luminance get the graphite base: a black base would vanish on dark glass. */
    const val NEUTRAL_LUMINANCE: Float = 0.03f

    fun glyph(brandId: String?): BrandGlyph? = brandId?.let { BrandGlyphs.all[it] }

    /** Brands that have an icon, in catalog order. */
    val brands: List<Brand> get() = BrandMatcher.brands.filter { BrandGlyphs.all.containsKey(it.id) }

    /**
     * The mark for a subscription. A user's choice ([iconKey], [iconColor]) wins; otherwise the
     * name is matched against the catalog; otherwise a letter mark with a color derived from the
     * name. Pure.
     */
    fun resolve(name: String, iconKey: String? = null, iconColor: Int? = null): MarkSpec {
        val forcedLetter = iconKey == LETTER_KEY
        val brand = when {
            forcedLetter -> null
            iconKey != null && glyph(iconKey) != null -> BrandMatcher.byId(iconKey)
            else -> BrandMatcher.match(name)?.takeIf { glyph(it.id) != null }
        }
        val letter = Monogram.letterOf(name)
        val (start, end) = when {
            iconColor != null -> gradientOf(iconColor.toLong() and 0xFFFFFFFFL)
            brand != null -> brandGradient(glyph(brand.id)!!.color)
            else -> MonogramGradients[Monogram.colorIndex(name, MonogramGradients.size)]
        }
        return MarkSpec(brand?.id, letter, start, end)
    }

    /** Base gradient for a brand color; black logos sit on graphite. */
    fun brandGradient(color: Long): Pair<Long, Long> =
        if (GlassContrast.luminance(Color(color)) < NEUTRAL_LUMINANCE) NeutralGradient else gradientOf(color)

    /** A 135° gradient around [color]: a lighter start and a deeper end (like the mockup marks). */
    fun gradientOf(color: Long): Pair<Long, Long> {
        val base = Color(color)
        val start = GlassContrast.over(Color.White.copy(alpha = LIGHTEN), base)
        val end = GlassContrast.over(Color.Black.copy(alpha = DARKEN), base)
        return start.toArgbLong() to end.toArgbLong()
    }

    private const val LIGHTEN = 0.16f
    private const val DARKEN = 0.30f

    private fun Color.toArgbLong(): Long {
        fun ch(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
        return (0xFFL shl 24) or (ch(red) shl 16) or (ch(green) shl 8) or ch(blue)
    }

    private val vectors = ConcurrentHashMap<String, ImageVector>()

    /** The icon as a white [ImageVector] (built once per brand with [PathParser]). */
    fun vector(brandId: String): ImageVector? {
        val glyph = glyph(brandId) ?: return null
        return vectors.getOrPut(brandId) { build(brandId, glyph) }
    }

    private fun build(id: String, glyph: BrandGlyph): ImageVector {
        val longest = maxOf(glyph.viewportWidth, glyph.viewportHeight)
        return ImageVector.Builder(
            name = "brand-$id",
            defaultWidth = (24f * glyph.viewportWidth / longest).dp,
            defaultHeight = (24f * glyph.viewportHeight / longest).dp,
            viewportWidth = glyph.viewportWidth,
            viewportHeight = glyph.viewportHeight,
        ).apply {
            glyph.paths.forEach { path ->
                val nodes = PathParser().parsePathString(path.d).toNodes()
                if (path.strokeWidth != null) {
                    addPath(
                        pathData = nodes,
                        fill = null,
                        stroke = SolidColor(Color.White),
                        strokeAlpha = path.alpha,
                        strokeLineWidth = path.strokeWidth,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                } else {
                    addPath(
                        pathData = nodes,
                        pathFillType = if (path.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                        fill = SolidColor(Color.White),
                        fillAlpha = path.alpha,
                    )
                }
            }
        }.build()
    }
}
