package com.ftwegc.myapplication.ui.charts

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineLanesTest {

    @Test
    fun pinsBeyondTheLastLaneFoldIntoTheNearestShownPin() {
        val xs = listOf(10f, 12f, 14f, 16f, 200f, 202f)
        val lanes = ChartMath.lanes(xs, minGap = 20f)
        assertEquals(listOf(0, 1, 2, 3, 0, 1), lanes)
        val extra = TimelineLanes.fold(xs, lanes, maxLanes = 3)
        // The fourth pin near x = 10 folds into its closest neighbor, the one at x = 14.
        assertEquals(listOf(0, 0, 1, 0, 0, 0), extra.toList())
        assertEquals(1, extra.sum())
        // No cap: nothing folds.
        assertEquals(0, TimelineLanes.fold(xs, lanes, maxLanes = 10).sum())
    }
}
