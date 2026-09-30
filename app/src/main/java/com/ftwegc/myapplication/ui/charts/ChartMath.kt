package com.ftwegc.myapplication.ui.charts

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import java.time.DayOfWeek
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** One cubic Bézier piece of a smooth curve. */
@Immutable
data class CubicSegment(val from: Offset, val c1: Offset, val c2: Offset, val to: Offset)

/** One ring / donut slice in degrees (0° = 3 o'clock, clockwise). */
@Immutable
data class ArcSlice(val start: Float, val sweep: Float, val fraction: Float)

/** A stacked bar piece: from [start] to [end] in value units. */
@Immutable
data class StackPiece(val start: Float, val end: Float)

/** Calendar grid of one month: blanks before day 1, number of days, rows needed. */
@Immutable
data class CalendarLayout(val leadingBlanks: Int, val days: Int, val rows: Int, val columns: Int = 7) {
    /** Row and column of [day] (1-based). */
    fun cellOf(day: Int): Pair<Int, Int> {
        val index = leadingBlanks + day - 1
        return index / columns to index % columns
    }
}

/**
 * Straight-line projection of a usage over a period.
 *
 * @property ratePerDay average use per day so far.
 * @property projectedAtEnd expected total at the end of the period.
 * @property limitReachedAtDay day (fractional, from the period start) when [limit] is reached at
 *   that rate; `null` without a limit, without growth, or when it is not reached within the period.
 */
@Immutable
data class Forecast(val ratePerDay: Double, val projectedAtEnd: Double, val limitReachedAtDay: Double?)

/**
 * Chart geometry and data math, shared by every chart. Pure (no drawing), unit tested.
 */
object ChartMath {

    /**
     * Places [values] in a [width] × [height] box: x evenly spaced over [slots] positions (defaults
     * to the value count; more slots leave room for a forecast), y from [minY] (bottom) to [maxY]
     * (top) with [topPad] / [bottomPad] kept free.
     */
    fun mapPoints(
        values: List<Float>,
        width: Float,
        height: Float,
        minY: Float = 0f,
        maxY: Float = values.maxOrNull() ?: 0f,
        slots: Int = values.size,
        topPad: Float = 0f,
        bottomPad: Float = 0f,
    ): List<Offset> {
        if (values.isEmpty()) return emptyList()
        val span = (maxY - minY).takeIf { it > 0f } ?: 1f
        val usable = (height - topPad - bottomPad).coerceAtLeast(0f)
        val step = if (slots > 1) width / (slots - 1) else 0f
        return values.mapIndexed { i, v ->
            val t = ((v - minY) / span).coerceIn(0f, 1f)
            Offset(if (slots > 1) i * step else width / 2f, topPad + usable * (1f - t))
        }
    }

    /**
     * A smooth curve through [points] (x increasing) that never overshoots between two points
     * (monotone cubic, Fritsch–Carlson): a cumulative total never dips, a peak stays a peak.
     */
    fun smoothSegments(points: List<Offset>): List<CubicSegment> {
        val n = points.size
        if (n < 2) return emptyList()
        val dx = FloatArray(n - 1) { points[it + 1].x - points[it].x }
        val slope = FloatArray(n - 1) { if (dx[it] == 0f) 0f else (points[it + 1].y - points[it].y) / dx[it] }
        val tangent = FloatArray(n)
        tangent[0] = slope[0]
        tangent[n - 1] = slope[n - 2]
        for (i in 1 until n - 1) {
            tangent[i] = if (slope[i - 1] * slope[i] <= 0f) 0f else (slope[i - 1] + slope[i]) / 2f
        }
        for (i in 0 until n - 1) {
            if (slope[i] == 0f) {
                tangent[i] = 0f
                tangent[i + 1] = 0f
                continue
            }
            val a = tangent[i] / slope[i]
            val b = tangent[i + 1] / slope[i]
            val h = a * a + b * b
            if (h > 9f) {
                val t = 3f / sqrt(h)
                tangent[i] = t * a * slope[i]
                tangent[i + 1] = t * b * slope[i]
            }
        }
        return (0 until n - 1).map { i ->
            val p0 = points[i]
            val p1 = points[i + 1]
            val third = dx[i] / 3f
            CubicSegment(
                from = p0,
                c1 = Offset(p0.x + third, p0.y + tangent[i] * third),
                c2 = Offset(p1.x - third, p1.y - tangent[i + 1] * third),
                to = p1,
            )
        }
    }

    /** Degrees of a ring filled to [fraction] (clamped 0..1). */
    fun ringSweep(fraction: Float): Float = 360f * fraction.coerceIn(0f, 1f)

    /**
     * Donut slices for [values] (negatives count as 0) starting at [startAngle], with [gap]
     * degrees between slices (no gaps for a single slice). Empty when everything is 0.
     */
    fun donutSlices(values: List<Float>, gap: Float = 0f, startAngle: Float = -90f): List<ArcSlice> {
        val clean = values.map { max(it, 0f) }
        val total = clean.sum()
        if (total <= 0f) return emptyList()
        val visible = clean.count { it > 0f }
        val gapTotal = if (visible > 1) gap * visible else 0f
        val available = 360f - gapTotal
        var angle = startAngle
        return clean.map { v ->
            val fraction = v / total
            val sweep = available * fraction
            val slice = ArcSlice(angle, sweep, fraction)
            if (v > 0f) angle += sweep + if (visible > 1) gap else 0f
            slice
        }
    }

    /**
     * Index of the slice under [angle] (degrees, same convention as [donutSlices]), or -1 in a gap.
     */
    fun sliceAt(slices: List<ArcSlice>, angle: Float): Int {
        slices.forEachIndexed { i, s ->
            if (s.sweep <= 0f) return@forEachIndexed
            val rel = normalize(angle - s.start)
            if (rel <= s.sweep) return i
        }
        return -1
    }

    /** [degrees] into 0 until 360. */
    fun normalize(degrees: Float): Float {
        val r = degrees % 360f
        return if (r < 0f) r + 360f else r
    }

    /** Stacked pieces of each bar: every value sits on the ones before it; negatives count as 0. */
    fun stack(bars: List<List<Float>>): List<List<StackPiece>> = bars.map { values ->
        var acc = 0f
        values.map { v ->
            val start = acc
            acc += max(v, 0f)
            StackPiece(start, acc)
        }
    }

    /** Tallest stacked bar. */
    fun stackMax(bars: List<List<Float>>): Float = bars.maxOfOrNull { b -> b.sumOf { max(it, 0f).toDouble() }.toFloat() } ?: 0f

    /**
     * A round axis maximum at or above [value]: 1, 2, 2.5 or 5 times a power of ten
     * (0 → 1, 386 → 500, 4636 → 5000, 0.8 → 1).
     */
    fun niceCeil(value: Float): Float {
        if (value <= 0f) return 1f
        val exponent = floor(log10(value.toDouble()))
        val base = 10.0.pow(exponent)
        val scaled = value / base
        val nice = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it >= scaled - 1e-9 }
        return (nice * base).toFloat()
    }

    /** Month grid with weeks starting on [firstDayOfWeek] (Monday in China). */
    fun calendarLayout(month: YearMonth, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): CalendarLayout {
        val first = month.atDay(1).dayOfWeek
        val blanks = Math.floorMod(first.value - firstDayOfWeek.value, 7)
        val days = month.lengthOfMonth()
        return CalendarLayout(blanks, days, ceil((blanks + days) / 7.0).toInt())
    }

    /**
     * Heat level 0..[levels] of [amount] against the month's [max]: 0 means nothing, then evenly
     * split by share of the maximum (the largest payment is always the top level).
     */
    fun heatLevel(amount: Long, max: Long, levels: Int = 3): Int {
        if (amount <= 0L || max <= 0L) return 0
        val share = amount.toDouble() / max
        return ceil(share * levels).toInt().coerceIn(1, levels)
    }

    /**
     * Projects [used] after [elapsedDays] of a [totalDays] period at the same average rate.
     * `null` when no time has passed yet.
     */
    fun forecast(used: Double, elapsedDays: Double, totalDays: Double, limit: Double? = null): Forecast? {
        if (elapsedDays <= 0.0 || totalDays <= 0.0) return null
        val rate = max(used, 0.0) / elapsedDays
        val projected = rate * totalDays
        val reachDay = when {
            limit == null || limit <= 0.0 -> null
            used >= limit -> min(elapsedDays, totalDays)
            rate <= 0.0 -> null
            else -> (limit / rate).takeIf { it <= totalDays }
        }
        return Forecast(rate, projected, reachDay)
    }

    /**
     * Continues [values] by [extra] steps along the slope of the last [window] values (a short,
     * honest dashed "where this is heading" tail). Empty with fewer than two values.
     */
    fun extrapolate(values: List<Float>, extra: Int, window: Int = 5): List<Float> {
        if (values.size < 2 || extra <= 0) return emptyList()
        val tail = values.takeLast(window.coerceAtLeast(2))
        val slope = (tail.last() - tail.first()) / (tail.size - 1)
        return (1..extra).map { values.last() + slope * it }
    }

    /**
     * Horizontal positions (0..1) of events on a timeline of [windowDays] days, by day offset.
     */
    fun timelinePositions(dayOffsets: List<Int>, windowDays: Int): List<Float> {
        val span = windowDays.coerceAtLeast(1).toFloat()
        return dayOffsets.map { (it.coerceIn(0, windowDays) / span) }
    }

    /**
     * Lanes for pins at [xs] (any unit) so that pins closer than [minGap] never share a lane:
     * greedy, left to right, lowest free lane first.
     */
    fun lanes(xs: List<Float>, minGap: Float): List<Int> {
        val order = xs.indices.sortedBy { xs[it] }
        val laneEnds = mutableListOf<Float>()
        val result = IntArray(xs.size)
        for (i in order) {
            val x = xs[i]
            var lane = laneEnds.indexOfFirst { x - it >= minGap }
            if (lane < 0) {
                lane = laneEnds.size
                laneEnds += x
            } else {
                laneEnds[lane] = x
            }
            result[i] = lane
        }
        return result.toList()
    }

    /** Linear interpolation of two lists (missing entries count as 0). */
    fun lerpValues(from: List<Float>, to: List<Float>, t: Float): List<Float> =
        to.indices.map { i -> (from.getOrElse(i) { 0f }) + (to[i] - from.getOrElse(i) { 0f }) * t }

    /** True when every value is (about) the same: a flat line carries no shape. */
    fun isFlat(values: List<Float>, epsilon: Float = 1e-6f): Boolean {
        if (values.isEmpty()) return true
        val first = values.first()
        return values.all { abs(it - first) <= epsilon }
    }
}
