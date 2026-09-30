package com.ftwegc.myapplication.domain

import com.ftwegc.myapplication.domain.calc.CounterDeltaCalculator
import com.ftwegc.myapplication.domain.calc.DeltaNote
import com.ftwegc.myapplication.domain.calc.ResetPolicy
import com.ftwegc.myapplication.domain.calc.SnapshotPoint
import com.ftwegc.myapplication.domain.calc.TimeSplit
import com.ftwegc.myapplication.domain.calc.TrafficTrend
import com.ftwegc.myapplication.domain.model.CounterSample
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TolerantDeltaAndTrendTest {

    private val t0: Instant = Instant.parse("2026-09-01T00:00:00Z")
    private val gib = 1L shl 30
    private val mib = 1L shl 20

    private fun sample(up: Long, down: Long, uptime: Long?, secondsAfterT0: Long) =
        CounterSample(up, down, uptime, t0.plusSeconds(secondsAfterT0))

    // ------------------------------------------------------------------ Delta policies

    @Test
    fun sinceBoot_isStillTheDefault_andNotesWhatHappened() {
        val prev = sample(1_000, 2_000, 86_400, 0)
        val reboot = CounterDeltaCalculator.computeDelta(prev, sample(5_000, 6_000, 100, 600))
        assertTrue(reboot.resetDetected)
        assertEquals(DeltaNote.REBOOT, reboot.note)
        val dropped = CounterDeltaCalculator.computeDelta(prev, sample(10, 20, 87_000, 600))
        assertEquals(DeltaNote.COUNTER_RESET, dropped.note)
        assertNull(CounterDeltaCalculator.computeDelta(prev, sample(1_100, 2_100, 87_000, 600)).note)
    }

    @Test
    fun tolerant_rebootWithGrowingCounters_isPlainDifference() {
        val prev = sample(300 * gib, 400 * gib, 864_000, 0)
        val r = CounterDeltaCalculator.computeDelta(prev, sample(300 * gib + 5, 400 * gib + 7, 60, 1_800), ResetPolicy.TOLERANT)
        assertEquals(5L, r.deltaUp)
        assertEquals(7L, r.deltaDown)
        assertFalse(r.resetDetected)
        assertEquals(DeltaNote.REBOOT_COUNTERS_KEPT, r.note)
    }

    @Test
    fun tolerant_realRebootOfSinceBootCounters_isStillAReset() {
        val prev = sample(300 * gib, 400 * gib, 864_000, 0)
        val r = CounterDeltaCalculator.computeDelta(prev, sample(2 * mib, 3 * mib, 60, 1_800), ResetPolicy.TOLERANT)
        assertTrue(r.resetDetected)
        assertEquals(2 * mib, r.deltaUp)
        assertEquals(DeltaNote.REBOOT, r.note)
    }

    @Test
    fun tolerant_smallDropOfLargeValue_isRollback_largeDropIsReset() {
        val prev = sample(100 * gib, 100 * gib, 1_000, 0)
        val rollback = CounterDeltaCalculator.computeDelta(prev, sample(95 * gib, 100 * gib + 3, 2_800, 1_800), ResetPolicy.TOLERANT)
        assertEquals(0L, rollback.deltaUp)
        assertEquals(3L, rollback.deltaDown)
        assertEquals(DeltaNote.SMALL_ROLLBACK, rollback.note)
        assertFalse(rollback.resetDetected)

        val reset = CounterDeltaCalculator.computeDelta(prev, sample(80 * gib, 100 * gib, 2_800, 1_800), ResetPolicy.TOLERANT)
        assertTrue(reset.resetDetected)
        assertEquals(80 * gib, reset.deltaUp)

        // Small values never count as a rollback: a drop is a reset.
        val small = sample(10 * mib, 10 * mib, 1_000, 0)
        val r = CounterDeltaCalculator.computeDelta(small, sample(9 * mib + 900 * 1024, 10 * mib, 2_800, 1_800), ResetPolicy.TOLERANT)
        assertTrue(r.resetDetected)
    }

    @Test
    fun tolerant_duplicateAndFirstSample_unchanged() {
        val prev = sample(1, 2, 3, 0)
        assertTrue(CounterDeltaCalculator.computeDelta(prev, prev, ResetPolicy.TOLERANT).skipped)
        assertEquals(0L, CounterDeltaCalculator.computeDelta(null, prev, ResetPolicy.TOLERANT).deltaUp)
    }

    // ------------------------------------------------------------------ TimeSplit

    @Test
    fun timeSplit_before_isProportional() {
        val from = Instant.parse("2026-09-30T15:00:00Z")
        val to = Instant.parse("2026-09-30T17:00:00Z")
        assertEquals(60L, TimeSplit.before(120, from, to, Instant.parse("2026-09-30T16:00:00Z")))
        assertEquals(120L, TimeSplit.before(120, from, to, to))
        assertEquals(0L, TimeSplit.before(120, from, to, from))
        assertEquals(0L, TimeSplit.before(0, from, to, Instant.parse("2026-09-30T16:00:00Z")))
    }

    @Test
    fun timeSplit_byDay_sumsExactly() {
        val zone = ZoneOffset.UTC
        val parts = TimeSplit.byDay(1_000, Instant.parse("2026-09-01T18:00:00Z"), Instant.parse("2026-09-03T06:00:00Z"), zone)
        assertEquals(1_000L, parts.values.sum())
        assertEquals(167L, parts[LocalDate.of(2026, 9, 1)])
        assertEquals(666L, parts[LocalDate.of(2026, 9, 2)])
        assertEquals(167L, parts[LocalDate.of(2026, 9, 3)])
        assertEquals(mapOf(LocalDate.of(2026, 9, 3) to 5L), TimeSplit.byDay(5, Instant.parse("2026-09-03T06:00:00Z"), Instant.parse("2026-09-03T06:00:00Z"), zone))
    }

    // ------------------------------------------------------------------ Trend

    private fun point(at: String, up: Long, down: Long, uptime: Long? = null, reset: Boolean = false) =
        SnapshotPoint(Instant.parse(at), up, down, uptime, reset)

    @Test
    fun trend_sumsPerLocalDay_andFillsGaps() {
        val zone = ZoneId.of("Asia/Shanghai")
        val points = listOf(
            point("2026-09-25T01:00:00Z", 0, 0),
            point("2026-09-25T02:00:00Z", 100, 200),
            point("2026-09-25T03:00:00Z", 100, 200),
            // 16:00Z is midnight in Shanghai: the next delta covers 15:00Z..17:00Z, half on each day.
            point("2026-09-25T15:00:00Z", 0, 0),
            point("2026-09-25T17:00:00Z", 40, 80),
            point("2026-09-27T04:00:00Z", 10, 10),
        )
        val trend = TrafficTrend.daily(points, zone, LocalDate.of(2026, 9, 28), maxDays = 14)
        assertTrue(trend.enoughData)
        assertEquals(listOf(25, 26, 27, 28).map { LocalDate.of(2026, 9, it) }, trend.days.map { it.date })
        assertEquals(220L, trend.days[0].upBytes)
        assertEquals(440L, trend.days[0].downBytes)
        // 2026-09-27T04:00Z covers 26th 01:00 local .. 27th 12:00 local (35 h): 23 h on the 26th.
        assertEquals(20L + 7L, trend.days[1].upBytes)
        assertEquals(3L, trend.days[2].upBytes)
        assertEquals(0L, trend.days[3].upBytes)
        assertEquals(20L + 7L, trend.days[1].usedBytes(TrafficLimitType.UP))
    }

    @Test
    fun trend_afterReset_startsAtTheReboot() {
        val zone = ZoneOffset.UTC
        val points = listOf(
            point("2026-09-20T12:00:00Z", 0, 0),
            // Rebooted one hour before this reading: all traffic falls on the 22nd, not spread back to the 20th.
            point("2026-09-22T12:00:00Z", 500, 500, uptime = 3_600, reset = true),
        )
        val trend = TrafficTrend.daily(points, zone, LocalDate.of(2026, 9, 22))
        assertEquals(500L, trend.days.last().upBytes)
        assertEquals(0L, trend.days.first().upBytes)
    }

    @Test
    fun trend_notEnoughData_andWindow() {
        val zone = ZoneOffset.UTC
        assertFalse(TrafficTrend.daily(emptyList(), zone, LocalDate.of(2026, 9, 28)).enoughData)
        val oneDay = listOf(point("2026-09-28T01:00:00Z", 0, 0), point("2026-09-28T02:00:00Z", 5, 5))
        val trend = TrafficTrend.daily(oneDay, zone, LocalDate.of(2026, 9, 28))
        assertFalse(trend.enoughData)
        assertEquals(1, trend.days.size)

        val long = (0..40).map { i -> point(Instant.parse("2026-08-20T12:00:00Z").plusSeconds(i * 86_400L).toString(), 1, 1) }
        val window = TrafficTrend.daily(long, zone, LocalDate.of(2026, 9, 28), maxDays = 7)
        assertEquals(7, window.days.size)
        assertEquals(LocalDate.of(2026, 9, 22), window.days.first().date)
        assertTrue(window.enoughData)
    }
}
