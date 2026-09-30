package com.ftwegc.myapplication.domain

import com.ftwegc.myapplication.domain.calc.TrafficCalculator
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.TrafficPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TrafficCalculatorTest {

    private val delta = 1e-9

    // ---- limit types ----

    @Test
    fun sum_addsBothDirections() {
        assertEquals(300L, TrafficCalculator.usedBytes(100, 200, TrafficLimitType.SUM))
    }

    @Test
    fun max_takesLargerDirection() {
        assertEquals(200L, TrafficCalculator.usedBytes(100, 200, TrafficLimitType.MAX))
        assertEquals(700L, TrafficCalculator.usedBytes(700, 200, TrafficLimitType.MAX))
    }

    @Test
    fun min_takesSmallerDirection() {
        assertEquals(100L, TrafficCalculator.usedBytes(100, 200, TrafficLimitType.MIN))
        assertEquals(200L, TrafficCalculator.usedBytes(700, 200, TrafficLimitType.MIN))
    }

    @Test
    fun up_takesUploadOnly() {
        assertEquals(100L, TrafficCalculator.usedBytes(100, 200, TrafficLimitType.UP))
    }

    @Test
    fun down_takesDownloadOnly() {
        assertEquals(200L, TrafficCalculator.usedBytes(100, 200, TrafficLimitType.DOWN))
    }

    @Test
    fun upEqualsDown_allTypes() {
        assertEquals(1000L, TrafficCalculator.usedBytes(500, 500, TrafficLimitType.SUM))
        assertEquals(500L, TrafficCalculator.usedBytes(500, 500, TrafficLimitType.MAX))
        assertEquals(500L, TrafficCalculator.usedBytes(500, 500, TrafficLimitType.MIN))
        assertEquals(500L, TrafficCalculator.usedBytes(500, 500, TrafficLimitType.UP))
        assertEquals(500L, TrafficCalculator.usedBytes(500, 500, TrafficLimitType.DOWN))
    }

    @Test
    fun parse_knownValuesAreCaseAndWhitespaceInsensitive() {
        assertEquals(TrafficLimitType.SUM, TrafficLimitType.parse("sum"))
        assertEquals(TrafficLimitType.MAX, TrafficLimitType.parse(" MAX "))
        assertEquals(TrafficLimitType.MIN, TrafficLimitType.parse("Min"))
        assertEquals(TrafficLimitType.UP, TrafficLimitType.parse("up"))
        assertEquals(TrafficLimitType.DOWN, TrafficLimitType.parse("DOWN"))
    }

    @Test
    fun parse_unknownNullOrBlankFallsBackToMax() {
        assertEquals(TrafficLimitType.MAX, TrafficLimitType.parse("total"))
        assertEquals(TrafficLimitType.MAX, TrafficLimitType.parse("upload"))
        assertEquals(TrafficLimitType.MAX, TrafficLimitType.parse(""))
        assertEquals(TrafficLimitType.MAX, TrafficLimitType.parse("   "))
        assertEquals(TrafficLimitType.MAX, TrafficLimitType.parse(null))
    }

    @Test
    fun sum_saturatesOnOverflow() {
        assertEquals(Long.MAX_VALUE, TrafficCalculator.usedBytes(Long.MAX_VALUE, 1, TrafficLimitType.SUM))
        assertEquals(Long.MAX_VALUE, TrafficCalculator.usedBytes(Long.MAX_VALUE, Long.MAX_VALUE, TrafficLimitType.SUM))
        // Exactly at the limit and one below it are exact, not saturated.
        assertEquals(Long.MAX_VALUE, TrafficCalculator.usedBytes(Long.MAX_VALUE - 10, 10, TrafficLimitType.SUM))
        assertEquals(Long.MAX_VALUE - 1, TrafficCalculator.usedBytes(Long.MAX_VALUE - 10, 9, TrafficLimitType.SUM))
    }

    @Test
    fun negativeInputs_areTreatedAsZero() {
        assertEquals(100L, TrafficCalculator.usedBytes(-5, 100, TrafficLimitType.SUM))
        assertEquals(0L, TrafficCalculator.usedBytes(-5, -10, TrafficLimitType.MAX))
        assertEquals(0L, TrafficCalculator.usedBytes(-5, 100, TrafficLimitType.MIN))
        assertEquals(0L, TrafficCalculator.usedBytes(-1, 50, TrafficLimitType.UP))
        assertEquals(0L, TrafficCalculator.usedBytes(50, -1, TrafficLimitType.DOWN))
        assertEquals(0L, TrafficCalculator.usedBytes(Long.MIN_VALUE, Long.MIN_VALUE, TrafficLimitType.SUM))
    }

    // ---- percentage ----

    @Test
    fun percent_zeroLimitIsNull() {
        assertNull(TrafficCalculator.usagePercent(100, 0))
    }

    @Test
    fun percent_negativeLimitIsNull() {
        assertNull(TrafficCalculator.usagePercent(100, -1))
        assertNull(TrafficCalculator.usagePercent(0, Long.MIN_VALUE))
    }

    @Test
    fun percent_overLimitExceeds100() {
        assertEquals(150.0, TrafficCalculator.usagePercent(150, 100)!!, delta)
        assertEquals(1000.0, TrafficCalculator.usagePercent(10_000, 1_000)!!, delta)
    }

    @Test
    fun percent_zeroUsedIsZero() {
        assertEquals(0.0, TrafficCalculator.usagePercent(0, 100)!!, delta)
    }

    @Test
    fun percent_regularValues() {
        assertEquals(25.0, TrafficCalculator.usagePercent(50, 200)!!, delta)
        val tib = 1L shl 40
        assertEquals(100.0, TrafficCalculator.usagePercent(tib, tib)!!, delta)
        assertEquals(50.0, TrafficCalculator.usagePercent(tib / 2, tib)!!, delta)
    }

    @Test
    fun percent_negativeUsedIsZero() {
        assertEquals(0.0, TrafficCalculator.usagePercent(-50, 100)!!, delta)
    }

    @Test
    fun usage_buildsAllFields() {
        val period = TrafficPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1))
        val at = Instant.parse("2026-09-28T10:00:00Z")
        val usage = TrafficCalculator.usage(300, 500, 1000, TrafficLimitType.SUM, period, at)
        assertEquals(300L, usage.upBytes)
        assertEquals(500L, usage.downBytes)
        assertEquals(800L, usage.usedBytes)
        assertEquals(1000L, usage.limitBytes)
        assertEquals(80.0, usage.percent!!, delta)
        assertEquals(period, usage.period)
        assertEquals(at, usage.updatedAt)

        val unlimited = TrafficCalculator.usage(-1, 500, 0, TrafficLimitType.UP)
        assertEquals(0L, unlimited.upBytes)
        assertEquals(0L, unlimited.usedBytes)
        assertNull(unlimited.percent)
    }
}
