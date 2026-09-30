package com.ftwegc.myapplication.domain

import com.ftwegc.myapplication.domain.calc.CounterDeltaCalculator
import com.ftwegc.myapplication.domain.calc.DeltaResult
import com.ftwegc.myapplication.domain.model.CounterSample
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CounterDeltaCalculatorTest {

    private val t0: Instant = Instant.parse("2026-09-01T00:00:00Z")

    private fun sample(up: Long, down: Long, uptime: Long?, secondsAfterT0: Long) =
        CounterSample(up, down, uptime, t0.plusSeconds(secondsAfterT0))

    private fun normal(up: Long, down: Long) = DeltaResult(up, down, resetDetected = false, skipped = false)
    private fun reset(up: Long, down: Long) = DeltaResult(up, down, resetDetected = true, skipped = false)
    private val skipped = DeltaResult(0, 0, resetDetected = false, skipped = true)

    @Test
    fun firstSample_isBaselineOnly() {
        val cur = sample(5_000, 9_000, 100, 0)
        assertEquals(normal(0, 0), CounterDeltaCalculator.computeDelta(null, cur))
    }

    @Test
    fun normalGrowth_isDifference() {
        val prev = sample(1_000, 2_000, 3_600, 0)
        val cur = sample(1_500, 2_600, 3_900, 300)
        assertEquals(normal(500, 600), CounterDeltaCalculator.computeDelta(prev, cur))
    }

    @Test
    fun noGrowth_isZeroWithoutReset() {
        val prev = sample(1_000, 2_000, 3_600, 0)
        val cur = sample(1_000, 2_000, 3_900, 300)
        assertEquals(normal(0, 0), CounterDeltaCalculator.computeDelta(prev, cur))
    }

    @Test
    fun bothDirectionsDecrease_isReset() {
        val prev = sample(1_000, 2_000, null, 0)
        val cur = sample(100, 200, null, 300)
        assertEquals(reset(100, 200), CounterDeltaCalculator.computeDelta(prev, cur))
    }

    @Test
    fun onlyOneDirectionDecreases_isResetForBoth() {
        val prev = sample(1_000, 2_000, null, 0)
        val cur = sample(1_100, 50, null, 300)
        assertEquals(reset(1_100, 50), CounterDeltaCalculator.computeDelta(prev, cur))

        val cur2 = sample(10, 2_500, null, 300)
        assertEquals(reset(10, 2_500), CounterDeltaCalculator.computeDelta(prev, cur2))
    }

    @Test
    fun reboot_valuesLargerButUptimeSmaller_isReset() {
        val prev = sample(1_000, 2_000, 86_400, 0)
        val cur = sample(5_000, 6_000, 100, 600)
        assertEquals(reset(5_000, 6_000), CounterDeltaCalculator.computeDelta(prev, cur))
    }

    @Test
    fun reboot_uptimeShorterThanGapBetweenReports_isReset() {
        // Uptime grew (100 -> 1200) but the reports are 3600 s apart: the machine rebooted in between.
        val prev = sample(1_000, 2_000, 100, 0)
        val cur = sample(3_000, 4_000, 1_200, 3_600)
        assertEquals(reset(3_000, 4_000), CounterDeltaCalculator.computeDelta(prev, cur))
    }

    @Test
    fun uptimeGap_boundaryWithin60sTolerance() {
        val prev = sample(1_000, 2_000, 100, 0)
        // cur.uptime + 60 == gap (1000): not a reboot.
        val atTolerance = sample(1_300, 2_400, 940, 1_000)
        assertEquals(normal(300, 400), CounterDeltaCalculator.computeDelta(prev, atTolerance))
        // cur.uptime + 60 < gap: reboot.
        val beyondTolerance = sample(1_300, 2_400, 939, 1_000)
        assertEquals(reset(1_300, 2_400), CounterDeltaCalculator.computeDelta(prev, beyondTolerance))
    }

    @Test
    fun uptimeUnavailable_usesCountersOnly() {
        // Would be a reboot if uptimes were compared, but prev has no uptime.
        val prevNoUptime = sample(1_000, 2_000, null, 0)
        val cur = sample(1_500, 2_500, 5, 3_600)
        assertEquals(normal(500, 500), CounterDeltaCalculator.computeDelta(prevNoUptime, cur))

        // Current has no uptime.
        val prev = sample(1_000, 2_000, 999_999, 0)
        val curNoUptime = sample(1_500, 2_500, null, 3_600)
        assertEquals(normal(500, 500), CounterDeltaCalculator.computeDelta(prev, curNoUptime))

        // Counters still detect a reset.
        val curDropped = sample(10, 20, null, 3_600)
        assertEquals(reset(10, 20), CounterDeltaCalculator.computeDelta(prevNoUptime, curDropped))
    }

    @Test
    fun negativeUptime_isTreatedAsUnavailable() {
        val prev = sample(1_000, 2_000, 5_000, 0)
        val cur = sample(1_200, 2_300, -1, 300)
        assertEquals(normal(200, 300), CounterDeltaCalculator.computeDelta(prev, cur))
    }

    @Test
    fun duplicateReport_sameTimestamp_isSkipped() {
        val prev = sample(1_000, 2_000, 3_600, 0)
        assertEquals(skipped, CounterDeltaCalculator.computeDelta(prev, prev))
        // Even with different counters, the same timestamp is treated as a duplicate.
        val sameTimeBigger = sample(9_000, 9_000, 3_600, 0)
        assertEquals(skipped, CounterDeltaCalculator.computeDelta(prev, sameTimeBigger))
    }

    @Test
    fun reportedAtGoesBackwards_isSkipped() {
        val prev = sample(1_000, 2_000, 3_600, 600)
        val older = sample(100, 200, 10, 300)
        assertEquals(skipped, CounterDeltaCalculator.computeDelta(prev, older))
    }

    @Test
    fun negativeCounters_areTreatedAsZero() {
        // Negative previous counter counts as 0.
        val prevNegative = sample(-100, 500, null, 0)
        val cur = sample(200, 700, null, 300)
        assertEquals(normal(200, 200), CounterDeltaCalculator.computeDelta(prevNegative, cur))

        // Negative current counter counts as 0, which is a decrease -> reset.
        val prev = sample(1_000, 2_000, null, 0)
        val curNegative = sample(-1, 2_500, null, 300)
        assertEquals(reset(0, 2_500), CounterDeltaCalculator.computeDelta(prev, curNegative))

        // Both negative: 0 -> 0, no traffic.
        val a = sample(-5, -5, null, 0)
        val b = sample(-7, -7, null, 300)
        assertEquals(normal(0, 0), CounterDeltaCalculator.computeDelta(a, b))
    }

    @Test
    fun hugeUptime_doesNotOverflow() {
        val prev = sample(1_000, 2_000, Long.MAX_VALUE - 10, 0)
        val cur = sample(1_100, 2_100, Long.MAX_VALUE - 5, 300)
        assertEquals(normal(100, 100), CounterDeltaCalculator.computeDelta(prev, cur))
    }
}
