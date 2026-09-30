package com.ftwegc.myapplication.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.ui.preview.GlassPreview
import com.ftwegc.myapplication.ui.theme.Motion
import kotlin.math.atan2

/**
 * A ring filled clockwise from the top to [fraction] with [content] in the middle (due ring,
 * traffic ring). Mockup `.arc`: round caps, drawn in 1.4 s after .3 s.
 *
 * @param colors one color, or several for a sweep gradient along the ring.
 * @param fraction `null`: nothing to show, a dashed placeholder ring.
 */
@Composable
fun ProgressRing(
    fraction: Float?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    strokeWidth: Dp = 9.dp,
    colors: List<Color> = listOf(chartColors().teal),
    memoryKey: String? = null,
    emptyText: String = stringResource(R.string.chart_empty_short),
    content: @Composable BoxScope.() -> Unit = {},
) {
    val chart = chartColors()
    val values = rememberAnimatedValues(
        targets = listOf(fraction?.coerceIn(0f, 1f) ?: 0f),
        memoryKey = memoryKey,
        durationMillis = Motion.DurationChartArc,
        easing = Motion.ChartArcEasing,
        delayOf = { Motion.DelayChartArc },
    )
    Box(
        modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            if (fraction == null) {
                drawCircle(
                    chart.track,
                    radius = arcSize.width / 2,
                    style = Stroke(stroke / 3, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))),
                )
                return@Canvas
            }
            drawArc(chart.track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawRingArc(values[0].value, colors, Offset(inset, inset), arcSize, stroke, -90f)
        }
        if (fraction == null) {
            Text(emptyText, color = chart.inkSecondary, fontSize = 11.5.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        } else {
            content()
        }
    }
}

private fun DrawScope.drawRingArc(fraction: Float, colors: List<Color>, topLeft: Offset, arcSize: Size, stroke: Float, start: Float) {
    if (fraction <= 0f) return
    val sweep = ChartMath.ringSweep(fraction)
    val brush = if (colors.size > 1) {
        Brush.sweepGradient(colors + colors.first(), center = Offset(topLeft.x + arcSize.width / 2, topLeft.y + arcSize.height / 2))
    } else {
        Brush.linearGradient(listOf(colors.first(), colors.first()))
    }
    drawArc(brush, start, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
}

/** One ring of [ConcentricRings]. */
@Immutable
data class RingSeries(val fraction: Float, val color: Color, val label: String)

/**
 * Several rings inside each other, one per series (mockup "流量总览": radii 52 / 41 / 30 / 19 on
 * 118, stroke 8, tracks behind). The outermost ring is the first series.
 */
@Composable
fun ConcentricRings(
    series: List<RingSeries>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    memoryKey: String? = null,
    emptyText: String = stringResource(R.string.chart_empty),
) {
    if (series.isEmpty()) {
        ChartPlaceholder(emptyText, modifier.size(size), PlaceholderKind.Ring)
        return
    }
    val chart = chartColors()
    val values = rememberAnimatedValues(
        targets = series.map { it.fraction.coerceIn(0f, 1f) },
        memoryKey = memoryKey,
        durationMillis = Motion.DurationChartArc,
        easing = Motion.ChartArcEasing,
        delayOf = { Motion.DelayChartArc + it * Motion.StaggerBarMillis },
    )
    Canvas(
        modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val unit = this.size.minDimension / 118f
        val stroke = 8f * unit
        series.forEachIndexed { i, s ->
            val r = (52f - 11f * i) * unit
            if (r <= stroke) return@forEachIndexed
            val topLeft = Offset(center.x - r, center.y - r)
            val arcSize = Size(r * 2, r * 2)
            drawArc(chart.track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawRingArc(values[i].value, listOf(s.color), topLeft, arcSize, stroke, -90f)
        }
    }
}

/** One slice of [DonutChart]. */
@Immutable
data class DonutSlice(val value: Float, val color: Color, val label: String)

/**
 * Share donut (mockup "分类占比": stroke 14 on 118, round caps). Tap a slice to select it: the
 * others fade and [onSelect] reports the index (-1 when the tap misses).
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 104.dp,
    selected: Int = -1,
    onSelect: ((Int) -> Unit)? = null,
    memoryKey: String? = null,
    emptyText: String = stringResource(R.string.chart_empty),
    center: @Composable BoxScope.() -> Unit = {},
) {
    val arcs = remember(slices) { ChartMath.donutSlices(slices.map { it.value }, gap = DONUT_GAP_DEGREES) }
    if (arcs.isEmpty()) {
        ChartPlaceholder(emptyText, modifier.size(size), PlaceholderKind.Ring)
        return
    }
    val reveal by rememberReveal(slices.map { it.value }, memoryKey, Motion.DurationChartArc, Motion.DelayChartArc, Motion.ChartArcEasing)
    Box(
        modifier
            .size(size)
            .semantics {
                this.contentDescription = contentDescription
                if (onSelect != null) role = Role.Button
            }
            .then(
                if (onSelect != null) {
                    Modifier.pointerInput(arcs) {
                        detectTapGestures { tap ->
                            val c = Offset(this.size.width / 2f, this.size.height / 2f)
                            val angle = Math.toDegrees(atan2((tap.y - c.y).toDouble(), (tap.x - c.x).toDouble())).toFloat()
                            onSelect(ChartMath.sliceAt(arcs, angle))
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val unit = this.size.minDimension / 118f
            val stroke = 14f * unit
            val r = 46f * unit
            val topLeft = Offset(this.center.x - r, this.center.y - r)
            val arcSize = Size(r * 2, r * 2)
            // Round caps reach half a stroke past each end: shorten the arcs to keep the gaps.
            val capDegrees = Math.toDegrees((stroke / 2 / r).toDouble()).toFloat()
            arcs.forEachIndexed { i, arc ->
                if (arc.sweep <= 0f) return@forEachIndexed
                val alpha = if (selected >= 0 && selected != i) 0.35f else 1f
                val grow = if (selected == i) 3.dp.toPx() else 0f
                val sweep = (arc.sweep * reveal - capDegrees * 2).coerceAtLeast(0.1f)
                drawArc(
                    slices[i].color.copy(alpha = alpha),
                    arc.start + capDegrees,
                    sweep,
                    false,
                    Offset(topLeft.x, topLeft.y),
                    arcSize,
                    style = Stroke(stroke + grow, cap = StrokeCap.Round),
                )
            }
        }
        center()
    }
}

private const val DONUT_GAP_DEGREES = 10f

@PreviewLightDark
@Composable
private fun ProgressRingPreview() {
    GlassPreview {
        val c = chartColors()
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(0.9f, "已过 90%", colors = listOf(c.amber)) { Text("3", fontSize = 30.sp) }
            ProgressRing(0.62f, "流量 62%", colors = listOf(c.teal, c.blue), size = 86.dp, strokeWidth = 7.dp) { Text("62%") }
            ProgressRing(null, "无数据", size = 86.dp)
        }
    }
}

@PreviewLightDark
@Composable
private fun ConcentricRingsPreview() {
    GlassPreview {
        val c = chartColors()
        ConcentricRings(
            listOf(RingSeries(0.94f, c.coral, "洛杉矶"), RingSeries(0.62f, c.amber, "法兰克福"), RingSeries(0.31f, c.teal, "新西伯利亚"), RingSeries(0.12f, c.blue, "伦敦")),
            "流量",
        )
    }
}

@PreviewLightDark
@Composable
private fun DonutChartPreview() {
    GlassPreview {
        val c = chartColors()
        var sel by remember { mutableIntStateOf(0) }
        DonutChart(
            listOf(DonutSlice(212f, c.violet, "AI"), DonutSlice(139f, c.blue, "VPS"), DonutSlice(35f, c.teal, "其他")),
            "分类占比",
            selected = sel,
            onSelect = { sel = it },
        )
    }
}
