package com.ftwegc.myapplication.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.ui.brand.BrandMark
import com.ftwegc.myapplication.ui.brand.MarkSize
import com.ftwegc.myapplication.ui.brand.MarkSource
import com.ftwegc.myapplication.ui.preview.GlassPreview
import com.ftwegc.myapplication.ui.theme.Motion
import kotlin.math.abs
import kotlin.math.roundToInt

/** One event on [TimelineChart]: [dayOffset] days from the start, labelled [dateLabel]. */
@Immutable
data class TimelineEvent(val key: Any, val dayOffset: Int, val dateLabel: String, val overdue: Boolean = false)

/**
 * Which events of a timeline are shown and how many more each pin stands for. Events that would
 * need a lane beyond [maxLanes] are folded into the nearest shown pin (a "+N" badge). Pure.
 */
internal object TimelineLanes {
    fun fold(xs: List<Float>, lanes: List<Int>, maxLanes: Int): IntArray {
        val extra = IntArray(xs.size)
        xs.indices.filter { lanes[it] >= maxLanes }.forEach { hidden ->
            val target = xs.indices.filter { lanes[it] < maxLanes }.minByOrNull { abs(xs[it] - xs[hidden]) * 10 + (maxLanes - 1 - lanes[it]) }
            if (target != null) extra[target]++
        }
        return extra
    }
}

/**
 * A horizontal timeline of [windowDays] days (mockup "未来 30 天"): a line fading to the right,
 * each event's pin (usually a subscription mark) standing on its date with a short stem, and the
 * date below. Pins too close to share a row move up a lane (at most [maxLanes]; the rest fold into
 * a "+N" badge on the nearest pin); dates that would overlap are skipped.
 */
@Composable
fun TimelineChart(
    events: List<TimelineEvent>,
    windowDays: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    pinSize: Dp = 28.dp,
    maxLanes: Int = Int.MAX_VALUE,
    memoryKey: String? = null,
    emptyText: String = stringResource(R.string.chart_empty),
    pin: @Composable (TimelineEvent) -> Unit,
) {
    if (events.isEmpty()) {
        ChartPlaceholder(emptyText, modifier.fillMaxWidth().height(70.dp), PlaceholderKind.Line)
        return
    }
    val chart = chartColors()
    val reveal by rememberReveal(events.map { it.key to it.dayOffset }, memoryKey, Motion.DurationChartDraw, Motion.DelayChartDraw, Motion.ChartDrawEasing)
    val positions = ChartMath.timelinePositions(events.map { it.dayOffset }, windowDays)
    val lineFrom = chart.inkSecondary.copy(alpha = 0.5f)
    val lineTo = chart.inkSecondary.copy(alpha = 0.12f)
    val cap = maxLanes.coerceAtLeast(1)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widthPx = constraints.maxWidth
    val pinPxF = with(density) { pinSize.toPx() }
    // The same lanes as the layout below: pins that do not fit fold into their neighbor's badge.
    val folded = remember(positions, cap, widthPx, pinPxF) {
        val g = timelineGeometry(widthPx, positions, pinPxF, cap, density)
        TimelineLanes.fold(g.xs, g.laneOf, cap)
    }
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .drawBehind {
                val g = timelineGeometry(size.width.roundToInt(), positions, pinSize.toPx(), cap, this)
                val w = size.width * reveal
                // Mockup: a 2 px line, linear-gradient(90deg, rgba(255,255,255,.5), rgba(255,255,255,.12)).
                drawRoundRect(
                    Brush.horizontalGradient(listOf(lineFrom, lineTo), endX = size.width),
                    topLeft = Offset(4.dp.toPx(), g.lineY.toFloat()),
                    size = Size((w - 8.dp.toPx()).coerceAtLeast(0f), 2.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                )
                // Stems from each pin down to the line (mockup: 2 × 8 px, white .4).
                g.xs.forEachIndexed { i, x ->
                    if (x > w || g.laneOf[i] >= cap) return@forEachIndexed
                    val pinBottom = g.pinTop(g.laneOf[i]) + pinSize.toPx()
                    drawLine(lineFrom, Offset(x, pinBottom + 2.dp.toPx()), Offset(x, g.lineY.toFloat()), strokeWidth = 2.dp.toPx())
                }
            },
        content = {
            events.forEachIndexed { i, event ->
                Box(
                    Modifier.graphicsLayer {
                        // Pins pop in along the line as it is drawn.
                        val t = ((reveal - positions[i] * 0.8f) / 0.2f).coerceIn(0f, 1f)
                        alpha = t
                        scaleX = 0.6f + 0.4f * t
                        scaleY = 0.6f + 0.4f * t
                    },
                ) {
                    pin(event)
                    val more = folded[i]
                    if (more > 0) {
                        Text(
                            "+$more",
                            color = Color.White,
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 8.dp, y = (-6).dp)
                                .background(chart.violet, RoundedCornerShape(8.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            events.forEach { event ->
                Text(event.dateLabel, color = if (event.overdue) chart.coral else chart.inkSecondary, fontSize = 10.5.sp, maxLines = 1)
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val pinPx = pinSize.roundToPx()
        val g = timelineGeometry(width, positions, pinPx.toFloat(), cap, this)
        val pins = measurables.take(events.size).map { it.measure(Constraints.fixed(pinPx, pinPx)) }
        val labels = measurables.drop(events.size).map { it.measure(Constraints()) }
        val height = g.labelY + (labels.maxOfOrNull { it.height } ?: 0)
        layout(width, height) {
            pins.forEachIndexed { i, p ->
                if (g.laneOf[i] < cap) p.place((g.xs[i] - pinPx / 2f).roundToInt(), g.pinTop(g.laneOf[i]).roundToInt())
            }
            var lastRight = Int.MIN_VALUE
            labels.forEachIndexed { i, l ->
                if (g.laneOf[i] >= cap) return@forEachIndexed
                val left = (g.xs[i] - l.width / 2f).roundToInt().coerceIn(0, (width - l.width).coerceAtLeast(0))
                if (left >= lastRight + 4.dp.roundToPx()) {
                    l.place(left, g.labelY)
                    lastRight = left + l.width
                }
            }
        }
    }
    }
}

/** Where the pins, the line and the dates of a timeline go for a given width. */
private class TimelineGeometry(
    val xs: List<Float>,
    val laneOf: List<Int>,
    val lanes: Int,
    val top: Float,
    val laneStep: Float,
    val lineY: Int,
    val labelY: Int,
) {
    fun pinTop(lane: Int): Float = top + (lanes - 1 - lane) * laneStep
}

private fun timelineGeometry(width: Int, positions: List<Float>, pinPx: Float, maxLanes: Int, density: androidx.compose.ui.unit.Density): TimelineGeometry =
    with(density) {
        val inset = pinPx / 2 + 4.dp.toPx()
        val usable = (width - inset * 2).coerceAtLeast(1f)
        val xs = positions.map { inset + it * usable }
        val laneOf = ChartMath.lanes(xs, minGap = pinPx + 2.dp.toPx())
        val lanes = ((laneOf.maxOrNull() ?: 0) + 1).coerceAtMost(maxLanes)
        val laneStep = pinPx + 4.dp.toPx()
        val top = 6.dp.toPx()
        val stem = 10.dp.toPx()
        val lineY = (top + pinPx + (lanes - 1) * laneStep + stem).roundToInt()
        TimelineGeometry(xs, laneOf, lanes, top, laneStep, lineY, lineY + 8.dp.roundToPx())
    }

@PreviewLightDark
@Composable
private fun TimelineChartPreview() {
    GlassPreview {
        TimelineChart(
            events = listOf(
                TimelineEvent("c", 3, "10/1"),
                TimelineEvent("g", 9, "10/7"),
                TimelineEvent("o", 17, "10/15"),
                TimelineEvent("o2", 18, "10/16"),
                TimelineEvent("u", 26, "10/24"),
            ),
            windowDays = 30,
            contentDescription = "未来 30 天",
        ) { e ->
            BrandMark(MarkSource(e.key.toString()), size = MarkSize.Small)
        }
    }
}
