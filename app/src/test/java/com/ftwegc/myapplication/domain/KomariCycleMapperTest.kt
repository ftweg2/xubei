package com.ftwegc.myapplication.domain

import com.ftwegc.myapplication.domain.calc.KomariCycleMapper
import com.ftwegc.myapplication.domain.model.BillingCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KomariCycleMapperTest {

    /** (lower, upper, months) of every month range. */
    private val ranges = listOf(
        Triple(27, 32, 1),
        Triple(87, 95, 3),
        Triple(175, 185, 6),
        Triple(360, 370, 12),
        Triple(720, 750, 24),
        Triple(1080, 1150, 36),
        Triple(1800, 1850, 60),
    )

    @Test
    fun monthRanges_boundariesAndOneOutside() {
        for ((lower, upper, months) in ranges) {
            assertEquals("lower $lower", BillingCycle.months(months), KomariCycleMapper.fromDays(lower))
            assertEquals("upper $upper", BillingCycle.months(months), KomariCycleMapper.fromDays(upper))
            assertEquals("below $lower", BillingCycle.days(lower - 1), KomariCycleMapper.fromDays(lower - 1))
            assertEquals("above $upper", BillingCycle.days(upper + 1), KomariCycleMapper.fromDays(upper + 1))
        }
    }

    @Test
    fun monthRanges_explicitValues() {
        assertEquals(BillingCycle.days(26), KomariCycleMapper.fromDays(26))
        assertEquals(BillingCycle.MONTHLY, KomariCycleMapper.fromDays(27))
        assertEquals(BillingCycle.MONTHLY, KomariCycleMapper.fromDays(30))
        assertEquals(BillingCycle.MONTHLY, KomariCycleMapper.fromDays(32))
        assertEquals(BillingCycle.days(33), KomariCycleMapper.fromDays(33))
        assertEquals(BillingCycle.QUARTERLY, KomariCycleMapper.fromDays(90))
        assertEquals(BillingCycle.QUARTERLY, KomariCycleMapper.fromDays(92))
        assertEquals(BillingCycle.SEMI_ANNUAL, KomariCycleMapper.fromDays(180))
        assertEquals(BillingCycle.ANNUAL, KomariCycleMapper.fromDays(365))
        assertEquals(BillingCycle.ANNUAL, KomariCycleMapper.fromDays(366))
        assertEquals(BillingCycle.BIENNIAL, KomariCycleMapper.fromDays(730))
        assertEquals(BillingCycle.TRIENNIAL, KomariCycleMapper.fromDays(1095))
        assertEquals(BillingCycle.months(60), KomariCycleMapper.fromDays(1825))
    }

    @Test
    fun otherPositiveValues_areCustomDays() {
        assertEquals(BillingCycle.days(1), KomariCycleMapper.fromDays(1))
        assertEquals(BillingCycle.days(7), KomariCycleMapper.fromDays(7))
        assertEquals(BillingCycle.days(45), KomariCycleMapper.fromDays(45))
        assertEquals(BillingCycle.days(500), KomariCycleMapper.fromDays(500))
        assertEquals(BillingCycle.days(Int.MAX_VALUE), KomariCycleMapper.fromDays(Int.MAX_VALUE))
    }

    @Test
    fun minusOne_isOnce() {
        assertEquals(BillingCycle.ONE_TIME, KomariCycleMapper.fromDays(-1))
    }

    @Test
    fun zero_isNull() {
        assertNull(KomariCycleMapper.fromDays(0))
    }

    @Test
    fun otherNegatives_areNull() {
        assertNull(KomariCycleMapper.fromDays(-2))
        assertNull(KomariCycleMapper.fromDays(-30))
        assertNull(KomariCycleMapper.fromDays(Int.MIN_VALUE))
    }

    @Test
    fun toDays_values() {
        assertEquals(30, KomariCycleMapper.toDays(BillingCycle.MONTHLY))
        assertEquals(90, KomariCycleMapper.toDays(BillingCycle.QUARTERLY))
        assertEquals(180, KomariCycleMapper.toDays(BillingCycle.SEMI_ANNUAL))
        assertEquals(365, KomariCycleMapper.toDays(BillingCycle.ANNUAL))
        assertEquals(730, KomariCycleMapper.toDays(BillingCycle.BIENNIAL))
        assertEquals(1095, KomariCycleMapper.toDays(BillingCycle.TRIENNIAL))
        assertEquals(1825, KomariCycleMapper.toDays(BillingCycle.months(60)))
        assertEquals(61, KomariCycleMapper.toDays(BillingCycle.months(2))) // 60.875 -> 61
        assertEquals(152, KomariCycleMapper.toDays(BillingCycle.months(5))) // 152.1875 -> 152
        assertEquals(45, KomariCycleMapper.toDays(BillingCycle.days(45)))
        assertEquals(-1, KomariCycleMapper.toDays(BillingCycle.ONE_TIME))
    }

    @Test
    fun roundTrip_cycleToDaysToCycle() {
        val cycles = listOf(
            BillingCycle.MONTHLY, BillingCycle.QUARTERLY, BillingCycle.SEMI_ANNUAL, BillingCycle.ANNUAL,
            BillingCycle.BIENNIAL, BillingCycle.TRIENNIAL, BillingCycle.months(60), BillingCycle.ONE_TIME,
            BillingCycle.days(1), BillingCycle.days(7), BillingCycle.days(45), BillingCycle.days(100),
        )
        for (cycle in cycles) {
            assertEquals(cycle.toString(), cycle, KomariCycleMapper.fromDays(KomariCycleMapper.toDays(cycle)))
        }
        // A 30-day custom cycle is displayed as 30 days but reads back as monthly (documented).
        assertEquals(BillingCycle.MONTHLY, KomariCycleMapper.fromDays(KomariCycleMapper.toDays(BillingCycle.days(30))))
    }

    @Test
    fun roundTrip_daysToCycleIsStable() {
        for (days in -5..2_000) {
            val cycle = KomariCycleMapper.fromDays(days) ?: continue
            assertEquals("days $days", cycle, KomariCycleMapper.fromDays(KomariCycleMapper.toDays(cycle)))
        }
    }
}
