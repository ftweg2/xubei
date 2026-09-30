package com.ftwegc.myapplication.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.ui.preview.GlassPreview
import com.ftwegc.myapplication.ui.theme.Motion

/**
 * One bar: its stacked [values] (bottom first), whether it is in the future (translucent), and
 * whether it is the current one (bright with a glow, mockup `.bars i.now`).
 */
@Immutable
data class BarDatum(val values: List<Float>, val future: Boolean = false, val current: Boolean = false) {
    constructor(value: Float, future: Boolean = false, current: Boolean = false) : this(listOf(value), future, current)
}

/**
 * Bars (mockup `.bars`): rounded tops (5 px) and feet (2 px), a mint-to-blue gradient, future bars
 * translucent, the current one glowing; they grow one after another with an overshoot. Stacked
 * values use [stackColors] from the bottom. Tapping selects a bar ([onSelect]; the others fade).
 */
@Composable
fun BarChart(
    bars: List<BarDatum>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 70.dp,
    gap: Dp = 5.dp,
    maxValue: Float? = null,
    stackColors: List<Color>? = null,
    selected: Int = -1,
    onSelect: ((Int) -> Unit)? = null,
    memoryKey: String? = null,
    emptyText: String = stringResource(R.string.chart_empty),
) {
    val totals = bars.map { b -> b.values.sumOf { maxOf(it, 0f).toDouble() }.toFloat() }
    if (bars.isEmpty() || totals.all { it <= 0f }) {
        ChartPlaceholder(emptyText, modifier.fillMaxWidth().height(height), PlaceholderKind.Bars)
        return
    }
    val chart = chartColors()
    val top = maxValue ?: ChartMath.stackMax(bars.map { it.values })
    // Each bar's height as a share of the tallest: grows on entrance, moves when values change.
    val grow = rememberAnimatedValues(
        targets = totals.map { if (top > 0f) (it / top).coerceIn(0f, 1f) else 0f },
        memoryKey = memoryKey,
        durationMillis = Motion.DurationBarGrow,
        easing = Motion.BarGrowEasing,
        delayOf = { Motion.DelayBarGrow + it * Motion.StaggerBarMillis },
    )
    val pieces = remember(bars) { ChartMath.stack(bars.map { it.values }) }
    val gradient = listOf(chart.mint.copy(alpha = 0.95f), chart.blue.copy(alpha = 0.35f))
    val future = listOf(chart.future, chart.future.copy(alpha = chart.future.alpha * 0.2f))
    val now = listOf(chart.highlight, chart.mint.copy(alpha = 0.7f))
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
            .then(
                if (onSelect != null) {
                    Modifier.pointerInput(bars.size) {
                        detectTapGestures { tap ->
                            val g = gap.toPx()
                            val w = (size.width - g * (bars.size - 1)) / bars.size
                            val index = (tap.x / (w + g)).toInt().coerceIn(0, bars.lastIndex)
                            onSelect(if (index == selected) -1 else index)
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val g = gap.toPx()
        val w = (size.width - g * (bars.size - 1)) / bars.size
        val topR = CornerRadius(minOf(5.dp.toPx(), w / 2))
        val footR = CornerRadius(minOf(2.dp.toPx(), w / 2))
        bars.forEachIndexed { i, bar ->
            val x = i * (w + g)
            val dim = selected >= 0 && selected != i
            val alpha = if (dim) 0.35f else 1f
            val barPieces = pieces[i]
            val total = barPieces.lastOrNull()?.end ?: 0f
            if (total <= 0f || top <= 0f) return@forEachIndexed
            val fullHeight = size.height * grow[i].value.coerceAtLeast(0f)
            if (fullHeight <= 0.5f) return@forEachIndexed
            if (bar.current && !dim) {
                // box-shadow: 0 0 16px rgba(126,240,221,.7)
                drawSoftGlow(chart.mint.copy(alpha = 0.7f), Offset(x, size.height - fullHeight), Size(w, fullHeight), topR.x, 8.dp.toPx())
            }
            barPieces.forEachIndexed { p, piece ->
                val y0 = size.height - fullHeight * (piece.start / total)
                val y1 = size.height - fullHeight * (piece.end / total)
                if (y0 - y1 <= 0.5f) return@forEachIndexed
                val isTop = p == barPieces.lastIndex
                val isBottom = p == 0
                val colors = when {
                    bar.current -> now
                    bar.future -> future
                    stackColors != null && barPieces.size > 1 -> listOf(stackColors[p % stackColors.size], stackColors[p % stackColors.size].copy(alpha = 0.55f))
                    else -> gradient
                }
                drawBarPiece(
                    Brush.verticalGradient(colors, startY = y1, endY = y0),
                    Offset(x, y1),
                    Size(w, y0 - y1),
                    if (isTop) topR else CornerRadius.Zero,
                    if (isBottom) footR else CornerRadius.Zero,
                    alpha,
                )
            }
        }
    }
}

private fun DrawScope.drawBarPiece(brush: Brush, topLeft: Offset, size: Size, top: CornerRadius, bottom: CornerRadius, alpha: Float) {
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = topLeft.x, top = topLeft.y, right = topLeft.x + size.width, bottom = topLeft.y + size.height,
                topLeftCornerRadius = top, topRightCornerRadius = top,
                bottomRightCornerRadius = bottom, bottomLeftCornerRadius = bottom,
            ),
        )
    }
    drawPath(path, brush, alpha = alpha)
}

/** One row of [HorizontalBars]. */
@Immutable
data class HBarRow(val fraction: Float, val colors: List<Color>, val value: String, val label: String)

/**
 * Horizontal bars (mockup `.hb`): a leading mark, a 9 px rounded track filled with a gradient, and
 * the value on the right; rows grow left to right one after another.
 */
@Composable
fun HorizontalBars(
    rows: List<HBarRow>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    valueWidth: Dp = 44.dp,
    memoryKey: String? = null,
    emptyText: String = stringResource(R.string.chart_empty),
    leading: @Composable (Int) -> Unit = {},
) {
    if (rows.isEmpty()) {
        ChartPlaceholder(emptyText, modifier.fillMaxWidth().height(60.dp), PlaceholderKind.Bars)
        return
    }
    val chart = chartColors()
    val values = rememberAnimatedValues(
        targets = rows.map { it.fraction.coerceIn(0f, 1f) },
        memoryKey = memoryKey,
        durationMillis = Motion.DurationHBarGrow,
        easing = Motion.HBarGrowEasing,
        delayOf = { Motion.DelayHBarGrow + it * Motion.StaggerHBarMillis },
    )
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        rows.forEachIndexed { i, row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                leading(i)
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(9.dp),
                ) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(chart.track, cornerRadius = r)
                    val f = values[i].value
                    if (f > 0f) {
                        val w = (size.width * f).coerceAtLeast(size.height)
                        val colors = if (row.colors.size > 1) row.colors else listOf(row.colors.first(), row.colors.first())
                        drawRoundRect(Brush.horizontalGradient(colors, endX = w), size = Size(w, size.height), cornerRadius = r)
                    }
                }
                Text(
                    row.value,
                    color = chart.ink,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(valueWidth),
                )
            }
        }
    }
}

/**
 * Half-circle gauge (mockup `.gauges`: an 80 × 46 arc of radius 32, stroke 7) with the value
 * under the arc and a label.
 */
@Composable
fun SemiGauge(
    fraction: Float?,
    value: String,
    label: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    color: Color = chartColors().teal,
    width: Dp = 80.dp,
    memoryKey: String? = null,
) {
    val chart = chartColors()
    val values = rememberAnimatedValues(
        targets = listOf(fraction?.coerceIn(0f, 1f) ?: 0f),
        memoryKey = memoryKey,
        durationMillis = Motion.DurationChartArc,
        easing = Motion.ChartArcEasing,
        delayOf = { Motion.DelayChartArc },
    )
    Column(
        modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.size(width, width * (46f / 80f))) {
                val unit = size.width / 80f
                val stroke = 7f * unit
                val r = 32f * unit
                val topLeft = Offset(size.width / 2 - r, 42f * unit - r)
                val arcSize = Size(r * 2, r * 2)
                drawArc(chart.track, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                val f = values[0].value
                if (fraction != null && f > 0f) {
                    drawArc(color, 180f, 180f * f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Text(value, color = chart.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(label, color = chart.inkSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

@PreviewLightDark
@Composable
private fun BarChartPreview() {
    GlassPreview {
        val c = chartColors()
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            var sel by remember { mutableIntStateOf(-1) }
            BarChart(
                bars = listOf(52f, 60f, 48f, 92f, 56f, 64f, 58f, 100f, 54f, 62f, 50f, 88f).mapIndexed { i, v ->
                    BarDatum(v, future = i > 5, current = i == 5)
                },
                contentDescription = "每月应付",
                height = 96.dp,
                selected = sel,
                onSelect = { sel = it },
            )
            BarChart(
                bars = List(10) { BarDatum(listOf(10f + it * 3, 6f + (it % 4) * 4)) },
                contentDescription = "上下行",
                stackColors = listOf(c.mint, c.violet),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun HorizontalBarsPreview() {
    GlassPreview {
        val c = chartColors()
        HorizontalBars(
            rows = listOf(
                HBarRow(1f, listOf(Color(0xFFE08A67), c.amber), "¥720", "Claude"),
                HBarRow(0.4f, listOf(Color(0xFF1FCF8B), c.mint), "¥144", "ChatGPT"),
                HBarRow(0.22f, listOf(Color(0xFFFF7A90), Color(0xFFFDA4AF)), "¥43", "洛杉矶"),
            ),
            contentDescription = "花费排行",
        )
    }
}

@PreviewLightDark
@Composable
private fun SemiGaugePreview() {
    GlassPreview {
        val c = chartColors()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SemiGauge(0.38f, "38%", "CPU", "CPU 38%", Modifier.weight(1f), c.teal)
            SemiGauge(0.71f, "71%", "内存", "内存 71%", Modifier.weight(1f), c.amber)
            SemiGauge(0.54f, "54%", "硬盘", "硬盘 54%", Modifier.weight(1f), c.blue)
        }
    }
}
