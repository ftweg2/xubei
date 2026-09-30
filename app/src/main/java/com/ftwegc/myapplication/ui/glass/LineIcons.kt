package com.ftwegc.myapplication.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Line icons of the glass buttons, drawn from the mockup's SVGs (24 × 24, stroke 1.9, round caps
 * and joins) like the navigation icons. Tinted by `Icon`.
 */
object LineIcons {

    private const val STROKE = 1.9f

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
        "M${x + r},$y H${x + w - r} A$r,$r 0 0 1 ${x + w},${y + r} V${y + h - r} " +
            "A$r,$r 0 0 1 ${x + w - r},${y + h} H${x + r} A$r,$r 0 0 1 $x,${y + h - r} " +
            "V${y + r} A$r,$r 0 0 1 ${x + r},$y Z"

    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx + r},$cy A$r,$r 0 1 1 ${cx - r},$cy A$r,$r 0 1 1 ${cx + r},$cy Z"

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    /** Mockup: a lens and its handle. */
    val Search: ImageVector by lazy { icon("search", circle(11f, 11f, 6.5f), "M20 20l-4.2-4.2") }

    /** Mockup: four rounded squares (the tile view). */
    val Grid: ImageVector by lazy {
        icon("grid", rect(4f, 4f, 7f, 7f, 2f), rect(13f, 4f, 7f, 7f, 2f), rect(4f, 13f, 7f, 7f, 2f), rect(13f, 13f, 7f, 7f, 2f))
    }

    /** Two wide bars (the row view). */
    val Rows: ImageVector by lazy { icon("rows", rect(4f, 4.5f, 16f, 6.5f, 2f), rect(4f, 13f, 16f, 6.5f, 2f)) }

    /** Arrows up and down (sorting). */
    val Sort: ImageVector by lazy { icon("sort", "M8 5v14M8 19l-3-3M8 19l3-3M16 19V5M16 5l-3 3M16 5l3 3") }

    /** Mockup: the back chevron. */
    val Back: ImageVector by lazy { icon("back", "M15 5l-7 7 7 7") }

    /** The forward chevron (next month). */
    val Forward: ImageVector by lazy { icon("forward", "M9 5l7 7-7 7") }

    /** Mockup: the pencil. */
    val Edit: ImageVector by lazy { icon("edit", "M4 20h4L19 9l-4-4L4 16v4Z") }

    val Close: ImageVector by lazy { icon("close", "M6 6l12 12M18 6L6 18") }

    /** Three dots (more actions). */
    val More: ImageVector by lazy { icon("more", circle(5.5f, 12f, 1.2f), circle(12f, 12f, 1.2f), circle(18.5f, 12f, 1.2f)) }

    /** A plus in a rounded square (add from the gallery). */
    val Photo: ImageVector by lazy { icon("photo", rect(3.5f, 5f, 17f, 14f, 3f), circle(9f, 10f, 1.6f), "M20.5 16l-5-5-8.5 8") }
}
