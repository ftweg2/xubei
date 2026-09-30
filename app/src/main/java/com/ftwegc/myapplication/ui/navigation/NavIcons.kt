package com.ftwegc.myapplication.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Line icons of the navigation, drawn from the mockup's SVGs (24 × 24, stroke 1.9, round caps and
 * joins; the add button uses stroke 2.2). Tinted by `Icon`.
 */
object NavIcons {

    private const val STROKE = 1.9f
    private const val ADD_STROKE = 2.2f

    /** SVG `<rect rx>` as a path. */
    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
        "M${x + r},$y H${x + w - r} A$r,$r 0 0 1 ${x + w},${y + r} V${y + h - r} " +
            "A$r,$r 0 0 1 ${x + w - r},${y + h} H${x + r} A$r,$r 0 0 1 $x,${y + h - r} " +
            "V${y + r} A$r,$r 0 0 1 ${x + r},$y Z"

    /** SVG `<circle>` as a path. */
    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx + r},$cy A$r,$r 0 1 1 ${cx - r},$cy A$r,$r 0 1 1 ${cx + r},$cy Z"

    private fun icon(name: String, stroke: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    /** 总览: four blocks of the bento grid. */
    val Overview: ImageVector by lazy {
        icon(
            "overview", STROKE,
            rect(3f, 3f, 8f, 10f, 2.5f), rect(13f, 3f, 8f, 6f, 2.5f),
            rect(13f, 11f, 8f, 10f, 2.5f), rect(3f, 15f, 8f, 6f, 2.5f),
        )
    }

    /** 订阅: stacked layers. */
    val Subscriptions: ImageVector by lazy {
        icon("subscriptions", STROKE, "M12 3 3 8l9 5 9-5-9-5Z", "m3 13 9 5 9-5")
    }

    /** 统计: bars on a base line. */
    val Stats: ImageVector by lazy {
        icon("stats", STROKE, "M4 20V11M10 20V4M16 20v-7M22 20H2")
    }

    /** 设置: a sun-like gear. */
    val Settings: ImageVector by lazy {
        icon(
            "settings", STROKE,
            circle(12f, 12f, 3.2f),
            "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.2 5.2l2.1 2.1M16.7 16.7l2.1 2.1M18.8 5.2l-2.1 2.1M7.3 16.7l-2.1 2.1",
        )
    }

    /** The add button's plus. */
    val Add: ImageVector by lazy { icon("add", ADD_STROKE, "M12 5v14M5 12h14") }
}
