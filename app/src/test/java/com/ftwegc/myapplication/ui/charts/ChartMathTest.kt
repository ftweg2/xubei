package com.ftwegc.myapplication.ui.charts

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class ChartMathTest {

    // ------------------------------------------------------------ coordinates

    @Test
    fun mapPoints_spreadsXAndInvertsY() {
        val p = ChartMath.mapPoints(listOf(0f, 50f, 100f), width = 200f, height = 100f)
        assertEquals(listOf(Offset(0f, 100f), Offset(100f, 50f), Offset(200f, 0f)), p)
    }

    @Test
    fun mapPoints_slotsLeaveRoomAndPaddingIsKept() {
        // 3 values in 5 slots: the last real point is at 2/4 of the width.
        val p = ChartMath.mapPoints(listOf(0f, 10f, 20f), width = 400f, height = 110f, maxY = 20f, slots = 5, topPad = 10f)
        assertEquals(200f, p.last().x, 1e-4f)
        assertEquals(10f, p.last().y, 1e-4f)
        assertEquals(110f, p.first().y, 1e-4f)
    }

    @Test
    fun mapPoints_edgeCases() {
        assertTrue(ChartMath.mapPoints(emptyList(), 10f, 10f).isEmpty())
        // A single value sits in the middle; values above max are clamped.
        assertEquals(Offset(5f, 0f), ChartMath.mapPoints(listOf(3f), 10f, 10f, maxY = 1f).single())
        // All zero: no division by zero.
        assertEquals(10f, ChartMath.mapPoints(listOf(0f, 0f), 10f, 10f).first().y, 0f)
    }

    // ------------------------------------------------------------ smooth curve

    @Test
    fun smoothSegments_passThroughEveryPoint() {
        val pts = listOf(Offset(0f, 80f), Offset(30f, 60f), Offset(60f, 60f), Offset(90f, 20f))
        val segs = ChartMath.smoothSegments(pts)
        assertEquals(3, segs.size)
        segs.forEachIndexed { i, s ->
            assertEquals(pts[i], s.from)
            assertEquals(pts[i + 1], s.to)
        }
    }

    @Test
    fun smoothSegments_neverOvershootMonotoneData() {
        // A cumulative spend curve (y down on screen): control points stay within each step.
        val pts = listOf(Offset(0f, 100f), Offset(10f, 100f), Offset(20f, 40f), Offset(30f, 38f), Offset(40f, 0f))
        ChartMath.smoothSegments(pts).forEach { s ->
            val lo = minOf(s.from.y, s.to.y)
            val hi = maxOf(s.from.y, s.to.y)
            assertTrue("c1 ${s.c1.y} in [$lo, $hi]", s.c1.y in lo - 1e-3f..hi + 1e-3f)
            assertTrue("c2 ${s.c2.y} in [$lo, $hi]", s.c2.y in lo - 1e-3f..hi + 1e-3f)
        }
        // A flat step stays flat.
        val flat = ChartMath.smoothSegments(pts)[0]
        assertEquals(100f, flat.c1.y, 1e-4f)
        assertEquals(100f, flat.c2.y, 1e-4f)
    }

    @Test
    fun smoothSegments_tooFewPoints() {
        assertTrue(ChartMath.smoothSegments(emptyList()).isEmpty())
        assertTrue(ChartMath.smoothSegments(listOf(Offset.Zero)).isEmpty())
    }

    // ------------------------------------------------------------ rings and donut

    @Test
    fun ringSweep_clamps() {
        assertEquals(0f, ChartMath.ringSweep(-1f), 0f)
        assertEquals(180f, ChartMath.ringSweep(0.5f), 1e-4f)
        assertEquals(360f, ChartMath.ringSweep(1.4f), 0f)
    }

    @Test
    fun donutSlices_sharesAndGaps() {
        val slices = ChartMath.donutSlices(listOf(212f, 139f, 35f), gap = 10f)
        assertEquals(3, slices.size)
        assertEquals(1f, slices.sumOf { it.fraction.toDouble() }.toFloat(), 1e-5f)
        assertEquals(330f, slices.sumOf { it.sweep.toDouble() }.toFloat(), 1e-3f)
        assertEquals(-90f, slices[0].start, 0f)
        assertEquals(slices[0].start + slices[0].sweep + 10f, slices[1].start, 1e-3f)
        // One slice: no gap; nothing: no slices; negatives count as 0.
        assertEquals(360f, ChartMath.donutSlices(listOf(5f, 0f), gap = 10f)[0].sweep, 1e-4f)
        assertTrue(ChartMath.donutSlices(listOf(0f, 0f)).isEmpty())
        assertEquals(0f, ChartMath.donutSlices(listOf(-3f, 4f))[0].fraction, 0f)
    }

    @Test
    fun sliceAt_findsTheSliceUnderAnAngle() {
        val slices = ChartMath.donutSlices(listOf(1f, 1f, 2f), gap = 0f) // -90..0, 0..90, 90..270
        assertEquals(0, ChartMath.sliceAt(slices, -45f))
        assertEquals(1, ChartMath.sliceAt(slices, 45f))
        assertEquals(2, ChartMath.sliceAt(slices, 180f))
        assertEquals(2, ChartMath.sliceAt(slices, -100f)) // = 260°
        val gapped = ChartMath.donutSlices(listOf(1f, 1f), gap = 20f)
        assertEquals(-1, ChartMath.sliceAt(gapped, gapped[0].start + gapped[0].sweep + 10f))
    }

    // ------------------------------------------------------------ bars

    @Test
    fun stack_accumulates() {
        val stacked = ChartMath.stack(listOf(listOf(2f, 3f), listOf(1f, -4f, 5f)))
        assertEquals(listOf(StackPiece(0f, 2f), StackPiece(2f, 5f)), stacked[0])
        assertEquals(listOf(StackPiece(0f, 1f), StackPiece(1f, 1f), StackPiece(1f, 6f)), stacked[1])
        assertEquals(6f, ChartMath.stackMax(listOf(listOf(2f, 3f), listOf(1f, -4f, 5f))), 0f)
        assertEquals(0f, ChartMath.stackMax(emptyList()), 0f)
    }

    @Test
    fun niceCeil_roundsUp() {
        assertEquals(1f, ChartMath.niceCeil(0f), 0f)
        assertEquals(1f, ChartMath.niceCeil(0.8f), 1e-6f)
        assertEquals(500f, ChartMath.niceCeil(386f), 1e-3f)
        assertEquals(5000f, ChartMath.niceCeil(4636f), 1e-2f)
        assertEquals(250f, ChartMath.niceCeil(210f), 1e-3f)
        assertEquals(100f, ChartMath.niceCeil(100f), 1e-3f)
    }

    // ------------------------------------------------------------ calendar

    @Test
    fun calendar_mondayFirst() {
        // 2026-09-01 is a Tuesday: one blank before it, 30 days, 5 rows.
        val sep = ChartMath.calendarLayout(YearMonth.of(2026, 9))
        assertEquals(1, sep.leadingBlanks)
        assertEquals(30, sep.days)
        assertEquals(5, sep.rows)
        assertEquals(0 to 1, sep.cellOf(1))
        assertEquals(4 to 1, sep.cellOf(29))
        // 2026-02-01 is a Sunday: six blanks, 28 days, 5 rows.
        val feb = ChartMath.calendarLayout(YearMonth.of(2026, 2))
        assertEquals(6, feb.leadingBlanks)
        assertEquals(5, feb.rows)
        // 2026-06-01 is a Monday: no blank.
        assertEquals(0, ChartMath.calendarLayout(YearMonth.of(2026, 6)).leadingBlanks)
    }

    @Test
    fun heatLevel_byShareOfTheMonthsMaximum() {
        assertEquals(0, ChartMath.heatLevel(0, 1000))
        assertEquals(0, ChartMath.heatLevel(10, 0))
        assertEquals(1, ChartMath.heatLevel(10, 1000))
        assertEquals(1, ChartMath.heatLevel(333, 1000))
        assertEquals(2, ChartMath.heatLevel(500, 1000))
        assertEquals(3, ChartMath.heatLevel(1000, 1000))
    }

    // ------------------------------------------------------------ forecast

    @Test
    fun forecast_projectsAverageRate() {
        // 470 GB in 27 days of a 30-day period with a 500 GB limit.
        val f = ChartMath.forecast(470.0, elapsedDays = 27.0, totalDays = 30.0, limit = 500.0)
        assertNotNull(f)
        assertEquals(470.0 / 27.0, f!!.ratePerDay, 1e-9)
        assertEquals(470.0 / 27.0 * 30.0, f.projectedAtEnd, 1e-9)
        assertEquals(500.0 / (470.0 / 27.0), f.limitReachedAtDay!!, 1e-9)
    }

    @Test
    fun forecast_edgeCases() {
        assertNull(ChartMath.forecast(10.0, 0.0, 30.0))
        // Not reached within the period.
        assertNull(ChartMath.forecast(100.0, 15.0, 30.0, 500.0)!!.limitReachedAtDay)
        // Already over the limit: reached "now".
        assertEquals(12.0, ChartMath.forecast(600.0, 12.0, 30.0, 500.0)!!.limitReachedAtDay!!, 1e-9)
        // No growth, no limit.
        assertNull(ChartMath.forecast(0.0, 5.0, 30.0, 500.0)!!.limitReachedAtDay)
        assertNull(ChartMath.forecast(10.0, 5.0, 30.0, null)!!.limitReachedAtDay)
    }

    @Test
    fun extrapolate_followsTheRecentSlope() {
        assertEquals(listOf(12f, 14f), ChartMath.extrapolate(listOf(0f, 2f, 4f, 6f, 8f, 10f), 2, window = 3))
        assertTrue(ChartMath.extrapolate(listOf(1f), 3).isEmpty())
        assertTrue(ChartMath.extrapolate(listOf(1f, 2f), 0).isEmpty())
    }

    // ------------------------------------------------------------ timeline

    @Test
    fun timelinePositions_clampToTheWindow() {
        assertEquals(listOf(0f, 0.5f, 1f, 1f), ChartMath.timelinePositions(listOf(-2, 15, 30, 40), 30))
    }

    @Test
    fun lanes_separateCrowdedPins() {
        // 0 and 20 collide (gap 32), 50 is free, 60 collides with 50, 100 free again.
        assertEquals(listOf(0, 1, 0, 1, 0), ChartMath.lanes(listOf(0f, 20f, 50f, 60f, 100f), minGap = 32f))
        // Order of the input does not matter.
        assertEquals(listOf(0, 0, 1), ChartMath.lanes(listOf(100f, 0f, 10f), 32f))
        assertTrue(ChartMath.lanes(emptyList(), 10f).isEmpty())
    }

    @Test
    fun helpers() {
        assertEquals(listOf(5f, 10f), ChartMath.lerpValues(listOf(0f), listOf(10f, 20f), 0.5f))
        assertTrue(ChartMath.isFlat(listOf(3f, 3f, 3f)))
        assertTrue(!ChartMath.isFlat(listOf(3f, 4f)))
        assertEquals(350f, ChartMath.normalize(-10f), 1e-4f)
    }
}
