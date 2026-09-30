package com.ftwegc.myapplication.domain

import com.ftwegc.myapplication.domain.calc.TrafficPeriodCalculator
import com.ftwegc.myapplication.domain.model.TrafficPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TrafficPeriodCalculatorTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun period(start: String, end: String) = TrafficPeriod(d(start), d(end))
    private fun periodFor(today: String, resetDay: Int) = TrafficPeriodCalculator.periodFor(d(today), resetDay)

    // ---- reset day 31 ----

    @Test
    fun reset31_onJan31() {
        assertEquals(period("2026-01-31", "2026-02-28"), periodFor("2026-01-31", 31))
    }

    @Test
    fun reset31_onFeb28CommonYear_startsNewPeriod() {
        assertEquals(period("2026-02-28", "2026-03-31"), periodFor("2026-02-28", 31))
    }

    @Test
    fun reset31_onFeb27_startsOnJan31() {
        assertEquals(period("2026-01-31", "2026-02-28"), periodFor("2026-02-27", 31))
    }

    @Test
    fun reset31_onFeb29LeapYear_startsNewPeriod() {
        assertEquals(period("2028-02-29", "2028-03-31"), periodFor("2028-02-29", 31))
        // Feb 28 of a leap year is still in the January period.
        assertEquals(period("2028-01-31", "2028-02-29"), periodFor("2028-02-28", 31))
    }

    @Test
    fun reset31_onApr30_startsNewPeriod() {
        assertEquals(period("2026-04-30", "2026-05-31"), periodFor("2026-04-30", 31))
        assertEquals(period("2026-03-31", "2026-04-30"), periodFor("2026-04-29", 31))
    }

    @Test
    fun reset31_onMar30_isInFebruaryPeriod() {
        assertEquals(period("2026-02-28", "2026-03-31"), periodFor("2026-03-30", 31))
    }

    @Test
    fun reset30_inFebruary() {
        assertEquals(period("2026-01-30", "2026-02-28"), periodFor("2026-02-27", 30))
        assertEquals(period("2026-02-28", "2026-03-30"), periodFor("2026-03-01", 30))
    }

    // ---- reset day 1 ----

    @Test
    fun reset1_firstOfMonthBelongsToNewPeriod() {
        assertEquals(period("2026-03-01", "2026-04-01"), periodFor("2026-03-01", 1))
        assertEquals(period("2026-02-01", "2026-03-01"), periodFor("2026-02-28", 1))
    }

    @Test
    fun reset1_acrossYearEnd() {
        assertEquals(period("2025-12-01", "2026-01-01"), periodFor("2025-12-31", 1))
        assertEquals(period("2026-01-01", "2026-02-01"), periodFor("2026-01-01", 1))
    }

    @Test
    fun reset15_acrossYearEnd() {
        assertEquals(period("2025-12-15", "2026-01-15"), periodFor("2026-01-10", 15))
        assertEquals(period("2025-12-15", "2026-01-15"), periodFor("2025-12-15", 15))
        assertEquals(period("2025-11-15", "2025-12-15"), periodFor("2025-12-14", 15))
    }

    // ---- out-of-range reset days ----

    @Test
    fun resetDay0_isClampedTo1() {
        assertEquals(periodFor("2026-03-10", 1), periodFor("2026-03-10", 0))
        assertEquals(period("2026-03-01", "2026-04-01"), periodFor("2026-03-10", 0))
    }

    @Test
    fun resetDay32_isClampedTo31() {
        assertEquals(period("2026-01-31", "2026-02-28"), periodFor("2026-02-27", 32))
        assertEquals(period("2026-02-28", "2026-03-31"), periodFor("2026-02-28", 32))
    }

    @Test
    fun negativeResetDay_isClampedTo1() {
        assertEquals(period("2026-03-01", "2026-04-01"), periodFor("2026-03-10", -5))
        assertEquals(period("2026-03-01", "2026-04-01"), periodFor("2026-03-10", Int.MIN_VALUE))
    }

    // ---- time zones ----

    @Test
    fun instantAtUtcEndOfMarch_isAprilInShanghai() {
        val instant = Instant.parse("2026-03-31T16:30:00Z")
        val shanghai = ZoneId.of("Asia/Shanghai")

        assertEquals(d("2026-04-01"), TrafficPeriodCalculator.todayIn(instant, shanghai))
        assertEquals(period("2026-04-01", "2026-05-01"), TrafficPeriodCalculator.periodFor(instant, shanghai, 1))

        assertEquals(d("2026-03-31"), TrafficPeriodCalculator.todayIn(instant, ZoneOffset.UTC))
        assertEquals(period("2026-03-01", "2026-04-01"), TrafficPeriodCalculator.periodFor(instant, ZoneOffset.UTC, 1))
    }

    @Test
    fun dstSpringForward_newYork_noDateSkippedOrRepeated() {
        val ny = ZoneId.of("America/New_York")
        // 2026-03-07 00:00 EST; DST starts 2026-03-08 02:00 local.
        val start = Instant.parse("2026-03-07T05:00:00Z")
        val counts = hourlyDateCounts(start, 71, ny)
        assertEquals(listOf(d("2026-03-07"), d("2026-03-08"), d("2026-03-09")), counts.keys.toList())
        assertEquals(listOf(24, 23, 24), counts.values.toList())

        // Just before / after the gap: both still 2026-03-08.
        assertEquals(d("2026-03-08"), TrafficPeriodCalculator.todayIn(Instant.parse("2026-03-08T06:59:00Z"), ny))
        assertEquals(d("2026-03-08"), TrafficPeriodCalculator.todayIn(Instant.parse("2026-03-08T07:00:00Z"), ny))
        // Local midnight after the switch is 04:00Z (EDT), not 05:00Z.
        assertEquals(d("2026-03-08"), TrafficPeriodCalculator.todayIn(Instant.parse("2026-03-09T03:59:59Z"), ny))
        assertEquals(d("2026-03-09"), TrafficPeriodCalculator.todayIn(Instant.parse("2026-03-09T04:00:00Z"), ny))

        // Reset day on the switch date.
        assertEquals(
            period("2026-02-08", "2026-03-08"),
            TrafficPeriodCalculator.periodFor(Instant.parse("2026-03-08T04:59:59Z"), ny, 8),
        )
        assertEquals(
            period("2026-03-08", "2026-04-08"),
            TrafficPeriodCalculator.periodFor(Instant.parse("2026-03-08T05:00:00Z"), ny, 8),
        )
    }

    @Test
    fun dstFallBack_newYork_noDateSkippedOrRepeated() {
        val ny = ZoneId.of("America/New_York")
        // 2026-10-31 00:00 EDT; DST ends 2026-11-01 02:00 local (01:00-02:00 happens twice).
        val start = Instant.parse("2026-10-31T04:00:00Z")
        val counts = hourlyDateCounts(start, 73, ny)
        assertEquals(listOf(d("2026-10-31"), d("2026-11-01"), d("2026-11-02")), counts.keys.toList())
        assertEquals(listOf(24, 25, 24), counts.values.toList())

        // Both 01:30 occurrences are Nov 1.
        assertEquals(d("2026-11-01"), TrafficPeriodCalculator.todayIn(Instant.parse("2026-11-01T05:30:00Z"), ny))
        assertEquals(d("2026-11-01"), TrafficPeriodCalculator.todayIn(Instant.parse("2026-11-01T06:30:00Z"), ny))
        // Local midnight after the switch is 05:00Z (EST).
        assertEquals(d("2026-11-01"), TrafficPeriodCalculator.todayIn(Instant.parse("2026-11-02T04:59:59Z"), ny))
        assertEquals(d("2026-11-02"), TrafficPeriodCalculator.todayIn(Instant.parse("2026-11-02T05:00:00Z"), ny))

        assertEquals(
            period("2026-11-01", "2026-12-01"),
            TrafficPeriodCalculator.periodFor(Instant.parse("2026-11-01T04:00:00Z"), ny, 1),
        )
        assertEquals(
            period("2026-10-01", "2026-11-01"),
            TrafficPeriodCalculator.periodFor(Instant.parse("2026-11-01T03:59:59Z"), ny, 1),
        )
    }

    @Test
    fun periodsAreContiguousForEveryDayOfSeveralYears() {
        for (resetDay in listOf(1, 15, 28, 29, 30, 31)) {
            var date = d("2027-01-01")
            var previous = TrafficPeriodCalculator.periodFor(date, resetDay)
            while (date.isBefore(d("2029-01-01"))) {
                val p = TrafficPeriodCalculator.periodFor(date, resetDay)
                assertTrue("$date in $p (reset $resetDay)", date in p)
                assertTrue(p.start.isBefore(p.endExclusive))
                if (p != previous) {
                    assertEquals("contiguous at $date (reset $resetDay)", previous.endExclusive, p.start)
                    assertEquals(date, p.start)
                }
                previous = p
                date = date.plusDays(1)
            }
        }
    }

    @Test
    fun period_containsAndLength() {
        val p = period("2026-01-31", "2026-02-28")
        assertTrue(d("2026-01-31") in p)
        assertTrue(d("2026-02-27") in p)
        assertFalse(d("2026-02-28") in p)
        assertFalse(d("2026-01-30") in p)
        assertEquals(28L, p.lengthDays)
    }

    /** Local dates of [hours] hourly instants from [start], with how many hours fell on each date. */
    private fun hourlyDateCounts(start: Instant, hours: Int, zone: ZoneId): LinkedHashMap<LocalDate, Int> {
        val counts = LinkedHashMap<LocalDate, Int>()
        for (i in 0 until hours) {
            val date = TrafficPeriodCalculator.todayIn(start.plus(Duration.ofHours(i.toLong())), zone)
            counts[date] = (counts[date] ?: 0) + 1
        }
        // Dates must be consecutive: none skipped.
        counts.keys.zipWithNext().forEach { (a, b) -> assertEquals(a.plusDays(1), b) }
        return counts
    }
}
