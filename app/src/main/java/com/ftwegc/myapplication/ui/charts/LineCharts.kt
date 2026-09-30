package com.ftwegc.myapplication.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.ui.preview.GlassPreview
import com.ftwegc.myapplication.ui.theme.Motion

/**
 * Smooth area curve (mockup "支出节奏"): a gradient line over a fading fill, an optional dashed
 * forecast continuing it, and a glowing dot at the last real value ("now").
 *
 * @param values the real points, left to right.
 * @param slots x positions of the whole chart (≥ values + forecast); extra slots are space for
 *   the forecast (for a month: its days).
 * @param forecast values continuing after the last real one (dashed).
 */
@Composable
fun SmoothAreaChart(
    values: List<Float>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 86.dp,
    forecast: List<Float> = emptyList(),
    slots: Int = values.size + forecast.size,
    lineColors: List<Color> = listOf(chartColors().mint, chartColors().violet),
    fillColor: Color = chartColors().mint,
    showNow: Boolean = true,
    memoryKey: String? = null,
    emptyText: String = stringResource(R.string.chart_empty),
) {
    if (values.size < 2) {
        ChartPlaceholder(emptyText, modifier.fillMaxWidth().height(height), PlaceholderKind.Line)
        return
    }
    val chart = chartColors()
    val draw by rememberReveal(values + forecast, memoryKey, Motion.DurationChartDraw, Motion.DelayChartDraw, Motion.ChartDrawEasing)
    val fade by rememberReveal(values + forecast, memoryKey?.let { "$it:fade" }, Motion.DurationChartFade, Motion.DelayChartFade, Motion.ChartFadeEasing)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
            .drawWithCache {
                val glow = 10.dp.toPx()
                val all = values + forecast
                val maxY = (all.maxOrNull() ?: 0f).let { if (it <= 0f) 1f else it }
                val count = slots.coerceAtLeast(all.size)
                val points = ChartMath.mapPoints(all, size.width, size.height, 0f, maxY, count, topPad = glow, bottomPad = 2.dp.toPx())
                val real = points.take(values.size)
                val line = segmentsPath(ChartMath.smoothSegments(real))
                val area = Path().apply {
                    addPath(line)
                    lineTo(real.last().x, size.height)
                    lineTo(real.first().x, size.height)
                    close()
                }
                val tail = points.drop(values.size - 1)
                val tailPath = if (tail.size >= 2) segmentsPath(ChartMath.smoothSegments(tail)) else null
                val measure = PathMeasure().apply { setPath(line, false) }
                val length = measure.length
                val partial = Path()
                val lineBrush = Brush.horizontalGradient(lineColors, startX = 0f, endX = real.last().x.coerceAtLeast(1f))
                val fillBrush = Brush.verticalGradient(0f to fillColor.copy(alpha = 0.45f), 1f to fillColor.copy(alpha = 0f))
                val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val dash = Stroke(
                    2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 6.dp.toPx())),
                )
                onDrawBehind {
                    drawPath(area, fillBrush, alpha = fade)
                    drawPath(partialPath(measure, length, draw, partial), lineBrush, style = stroke)
                    if (tailPath != null) drawPath(tailPath, chart.future.copy(alpha = 0.5f), alpha = fade, style = dash)
                    if (showNow) {
                        val now = real.last()
                        drawCircle(chart.highlight.copy(alpha = 0.22f), radius = glow, center = now, alpha = fade)
                        drawCircle(chart.highlight, radius = 5.dp.toPx(), center = now, alpha = fade)
                    }
                }
            },
    ) {}
}

/**
 * Mini line (mockup node cells: straight segments, stroke 2, round joins), drawn in on entrance.
 * With [animateUpdates] false (live data), later values replace the line without replaying.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    color: Color = chartColors().mint,
    height: Dp = 26.dp,
    strokeWidth: Dp = 2.dp,
    fill: Boolean = false,
    memoryKey: String? = null,
    animateUpdates: Boolean = false,
    emptyText: String = stringResource(R.string.chart_empty_short),
) {
    if (values.size < 2) {
        ChartPlaceholder(emptyText, modifier.fillMaxWidth().height(height), PlaceholderKind.Line)
        return
    }
    val draw by rememberReveal(if (animateUpdates) values else Unit, memoryKey, Motion.DurationChartDraw, Motion.DelayChartDraw, Motion.ChartDrawEasing)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
            .drawWithCache {
                val pad = strokeWidth.toPx()
                val min = values.minOrNull() ?: 0f
                val max = values.maxOrNull() ?: 0f
                // A flat series sits in the middle rather than on the floor.
                val lo = if (max - min < 1e-6f) min - 1f else min
                val hi = if (max - min < 1e-6f) max + 1f else max
                val points = ChartMath.mapPoints(values, size.width, size.height, lo, hi, topPad = pad, bottomPad = pad)
                val line = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                val area = if (fill) {
                    Path().apply {
                        addPath(line)
                        lineTo(points.last().x, size.height)
                        lineTo(points.first().x, size.height)
                        close()
                    }
                } else {
                    null
                }
                val measure = PathMeasure().apply { setPath(line, false) }
                val length = measure.length
                val partial = Path()
                val stroke = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val fillBrush = Brush.verticalGradient(0f to color.copy(alpha = 0.30f), 1f to color.copy(alpha = 0f))
                onDrawBehind {
                    if (area != null) drawPath(area, fillBrush, alpha = draw)
                    drawPath(partialPath(measure, length, draw, partial), color, style = stroke)
                }
            },
    ) {}
}

/**
 * Usage forecast (mockup "用量预测"): the used amount so far as a filled curve, a dashed straight
 * projection to the end of the period at the current average rate, a dashed limit line with its
 * label, and a glowing "today" dot.
 *
 * @param actual cumulative use per day so far (day 0 first).
 * @param totalDays days in the period.
 */
@Composable
fun ForecastLineChart(
    actual: List<Float>,
    totalDays: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    limit: Float? = null,
    limitLabel: String? = null,
    startLabel: String? = null,
    todayLabel: String? = null,
    color: Color = chartColors().coral,
    height: Dp = 110.dp,
    memoryKey: String? = null,
    emptyText: String = stringResource(R.string.chart_empty),
) {
    if (actual.size < 2 || totalDays < 2) {
        ChartPlaceholder(emptyText, modifier.fillMaxWidth().height(height), PlaceholderKind.Line)
        return
    }
    val chart = chartColors()
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = chart.inkSecondary)
    val draw by rememberReveal(actual, memoryKey, Motion.DurationChartDraw, Motion.DelayChartDraw, Motion.ChartDrawEasing)
    val fade by rememberReveal(actual, memoryKey?.let { "$it:fade" }, Motion.DurationChartFade, Motion.DelayChartFade, Motion.ChartFadeEasing)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
            .drawWithCache {
                val elapsed = (actual.size - 1).toDouble()
                val forecast = ChartMath.forecast(actual.last().toDouble(), elapsed, (totalDays - 1).toDouble(), limit?.toDouble())
                val projected = forecast?.projectedAtEnd?.toFloat() ?: actual.last()
                val top = maxOf(projected, limit ?: 0f, actual.maxOrNull() ?: 0f).let { if (it <= 0f) 1f else it * 1.06f }
                val bottomPad = 14.dp.toPx()
                val topPad = 10.dp.toPx()
                val points = ChartMath.mapPoints(actual, size.width, size.height, 0f, top, totalDays, topPad, bottomPad)
                val end = ChartMath.mapPoints(listOf(projected), size.width, size.height, 0f, top, 1, topPad, bottomPad).first()
                    .copy(x = size.width)
                val line = segmentsPath(ChartMath.smoothSegments(points))
                val baseline = size.height - bottomPad
                val area = Path().apply {
                    addPath(line)
                    lineTo(points.last().x, baseline)
                    lineTo(points.first().x, baseline)
                    close()
                }
                val limitY = limit?.let { topPad + (size.height - topPad - bottomPad) * (1f - (it / top).coerceIn(0f, 1f)) }
                val measure = PathMeasure().apply { setPath(line, false) }
                val length = measure.length
                val partial = Path()
                val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val dashed = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 6.dp.toPx())))
                val limitStroke = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())))
                val fillBrush = Brush.verticalGradient(0f to color.copy(alpha = 0.40f), 1f to color.copy(alpha = 0f))
                val limitText = limitLabel?.let { measurer.measure(it, labelStyle) }
                val startText = startLabel?.let { measurer.measure(it, labelStyle) }
                val todayText = todayLabel?.let { measurer.measure(it, labelStyle.copy(color = chart.ink)) }
                onDrawBehind {
                    if (limitY != null) {
                        drawLine(chart.future, Offset(0f, limitY), Offset(size.width, limitY), strokeWidth = limitStroke.width, pathEffect = limitStroke.pathEffect)
                        limitText?.let {
                            // The label sits at the right end unless "today" is there (period
                            // almost over): then at the left end, clear of the dot.
                            val now = points.last()
                            val right = size.width - it.size.width
                            val x = if (now.x > right - 12.dp.toPx()) 0f else right
                            drawText(it, topLeft = Offset(x, (limitY - it.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
                        }
                    }
                    drawPath(area, fillBrush, alpha = fade)
                    drawPath(partialPath(measure, length, draw, partial), color, style = stroke)
                    val now = points.last()
                    val tail = Path().apply {
                        moveTo(now.x, now.y)
                        lineTo(end.x, end.y)
                    }
                    drawPath(tail, color.copy(alpha = 0.7f), alpha = fade, style = dashed)
                    drawCircle(color.copy(alpha = 0.30f), radius = 9.dp.toPx(), center = now, alpha = fade)
                    drawCircle(chart.highlight, radius = 4.5.dp.toPx(), center = now, alpha = fade)
                    startText?.let { drawText(it, topLeft = Offset(0f, size.height - it.size.height)) }
                    todayText?.let {
                        val x = (now.x - it.size.width / 2f).coerceIn(0f, size.width - it.size.width)
                        drawText(it, topLeft = Offset(x, size.height - it.size.height))
                    }
                }
            },
    ) {}
}

/**
 * Two live lines on one scale (mockup "实时": upload mint, download violet, straight segments,
 * stroke 2.4). Drawn in once; later data replaces the lines without replaying.
 */
@Composable
fun DualLineChart(
    first: List<Float>,
    second: List<Float>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    firstColor: Color = chartColors().mint,
    secondColor: Color = chartColors().violet,
    height: Dp = 64.dp,
    memoryKey: String? = null,
    emptyText: String = stringResource(R.string.chart_empty_short),
) {
    if (first.size < 2 && second.size < 2) {
        ChartPlaceholder(emptyText, modifier.fillMaxWidth().height(height), PlaceholderKind.Line)
        return
    }
    val draw by rememberReveal(Unit, memoryKey, Motion.DurationChartDraw, Motion.DelayChartDraw, Motion.ChartDrawEasing)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
            .drawWithCache {
                val pad = 3.dp.toPx()
                val max = maxOf(first.maxOrNull() ?: 0f, second.maxOrNull() ?: 0f).let { if (it <= 0f) 1f else it * 1.1f }
                fun lineOf(values: List<Float>): Pair<Path, PathMeasure>? {
                    if (values.size < 2) return null
                    val points = ChartMath.mapPoints(values, size.width, size.height, 0f, max, topPad = pad, bottomPad = pad)
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    return path to PathMeasure().apply { setPath(path, false) }
                }
                val a = lineOf(first)
                val b = lineOf(second)
                val partial = Path()
                val stroke = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind {
                    b?.let { (_, m) -> drawPath(partialPath(m, m.length, draw, partial), secondColor, style = stroke) }
                    a?.let { (_, m) -> drawPath(partialPath(m, m.length, draw, partial), firstColor, style = stroke) }
                }
            },
    ) {}
}

/** One payment of [StepAreaChart]: at [x] (0..1 of the time span) the total became [value]. */
@androidx.compose.runtime.Immutable
data class StepPoint(val x: Float, val value: Float)

/**
 * Cumulative total as a staircase (detail "累计花费"): flat between payments, a step up at each
 * one, a fading fill below and a dot on every step; the last level runs on to "today" at the
 * right edge. Drawn left to right on entrance.
 */
@Composable
fun StepAreaChart(
    steps: List<StepPoint>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    color: Color = chartColors().violet,
    height: Dp = 96.dp,
    startLabel: String? = null,
    endLabel: String? = null,
    memoryKey: String? = null,
    emptyText: String = stringResource(R.string.chart_empty),
) {
    if (steps.isEmpty()) {
        ChartPlaceholder(emptyText, modifier.fillMaxWidth().height(height), PlaceholderKind.Line)
        return
    }
    val chart = chartColors()
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = chart.inkSecondary)
    val draw by rememberReveal(steps, memoryKey, Motion.DurationChartDraw, Motion.DelayChartDraw, Motion.ChartDrawEasing)
    val fade by rememberReveal(steps, memoryKey?.let { "$it:fade" }, Motion.DurationChartFade, Motion.DelayChartFade, Motion.ChartFadeEasing)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
            .drawWithCache {
                val top = 10.dp.toPx()
                val bottom = 14.dp.toPx()
                val usable = size.height - top - bottom
                val max = steps.maxOf { it.value }.let { if (it <= 0f) 1f else it }
                fun y(v: Float) = top + usable * (1f - (v / max).coerceIn(0f, 1f))
                val base = size.height - bottom
                val path = Path()
                var lastY = base
                path.moveTo(0f, base)
                steps.forEach { s ->
                    val x = (s.x.coerceIn(0f, 1f)) * size.width
                    path.lineTo(x, lastY)
                    lastY = y(s.value)
                    path.lineTo(x, lastY)
                }
                path.lineTo(size.width, lastY)
                val area = Path().apply {
                    addPath(path)
                    lineTo(size.width, base)
                    lineTo(0f, base)
                    close()
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val length = measure.length
                val partial = Path()
                val stroke = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val fillBrush = Brush.verticalGradient(0f to color.copy(alpha = 0.38f), 1f to color.copy(alpha = 0f), startY = top, endY = base)
                val startText = startLabel?.let { measurer.measure(it, labelStyle) }
                val endText = endLabel?.let { measurer.measure(it, labelStyle.copy(color = chart.ink)) }
                onDrawBehind {
                    drawPath(area, fillBrush, alpha = fade)
                    drawPath(partialPath(measure, length, draw, partial), color, style = stroke)
                    steps.forEach { s ->
                        val c = Offset(s.x.coerceIn(0f, 1f) * size.width, y(s.value))
                        drawCircle(color.copy(alpha = 0.30f), radius = 6.dp.toPx(), center = c, alpha = fade)
                        drawCircle(chart.highlight, radius = 3.dp.toPx(), center = c, alpha = fade)
                    }
                    startText?.let { drawText(it, topLeft = Offset(0f, size.height - it.size.height)) }
                    endText?.let { drawText(it, topLeft = Offset(size.width - it.size.width, size.height - it.size.height)) }
                }
            },
    ) {}
}

@PreviewLightDark
@Composable
private fun SmoothAreaChartPreview() {
    GlassPreview {
        SmoothAreaChart(
            values = listOf(0f, 20f, 20f, 35f, 35f, 60f, 80f, 120f, 150f, 150f),
            forecast = listOf(170f, 190f),
            contentDescription = "本月支出节奏",
        )
    }
}

@PreviewLightDark
@Composable
private fun SparklinePreview() {
    GlassPreview {
        Sparkline(listOf(18f, 14f, 17f, 8f, 12f, 6f, 14f, 10f, 15f, 9f), "网速", color = chartColors().coral)
    }
}

@PreviewLightDark
@Composable
private fun ForecastLineChartPreview() {
    GlassPreview {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ForecastLineChart(
                actual = listOf(0f, 20f, 50f, 80f, 120f, 170f, 220f, 280f, 330f, 380f, 420f, 470f),
                totalDays = 30,
                limit = 500f,
                limitLabel = "限额 500 GB",
                startLabel = "9/1",
                todayLabel = "今天",
                contentDescription = "用量预测",
            )
            ForecastLineChart(actual = listOf(1f), totalDays = 30, contentDescription = "无数据")
        }
    }
}
