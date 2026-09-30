package com.ftwegc.myapplication.domain

import com.ftwegc.myapplication.domain.calc.DueDateCalculator
import com.ftwegc.myapplication.domain.calc.RollResult
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.DueStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DueDateCalculatorTest {

    private fun d(s: String) = LocalDate.parse(s)

    /** Applies [DueDateCalculator.advance] [times] times and returns every intermediate date. */
    private fun advanceSeries(start: LocalDate, cycle: BillingCycle, anchor: Int?, times: Int): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var due = start
        repeat(times) {
            due = DueDateCalculator.advance(due, cycle, anchor)!!
            out += due
        }
        return out
    }

    // ---- advance ----

    @Test
    fun monthly_fromJan31_twelvePeriodsFollowAnchor() {
        val expected = listOf(
            "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31", "2026-06-30", "2026-07-31",
            "2026-08-31", "2026-09-30", "2026-10-31", "2026-11-30", "2026-12-31", "2027-01-31",
        ).map(::d)
        assertEquals(expected, advanceSeries(d("2026-01-31"), BillingCycle.MONTHLY, 31, 12))
    }

    @Test
    fun monthly_withoutAnchor_usesCurrentDueDay() {
        // Documented behaviour of advance(): with no anchor, each step uses the current day (drifts).
        assertEquals(
            listOf(d("2026-02-28"), d("2026-03-28")),
            advanceSeries(d("2026-01-31"), BillingCycle.MONTHLY, null, 2),
        )
    }

    @Test
    fun monthly_anchorDifferentFromDueDay_isApplied() {
        assertEquals(d("2026-02-15"), DueDateCalculator.advance(d("2026-01-31"), BillingCycle.MONTHLY, 15))
        assertEquals(d("2026-02-28"), DueDateCalculator.advance(d("2026-01-15"), BillingCycle.MONTHLY, 31))
    }

    @Test
    fun annual_fromLeapDay() {
        assertEquals(
            listOf(d("2029-02-28"), d("2030-02-28"), d("2031-02-28"), d("2032-02-29")),
            advanceSeries(d("2028-02-29"), BillingCycle.ANNUAL, 29, 4),
        )
        assertEquals(d("2025-02-28"), DueDateCalculator.advance(d("2024-02-29"), BillingCycle.ANNUAL, null))
    }

    @Test
    fun quarterly_acrossYearEnd() {
        assertEquals(
            listOf(d("2026-02-28"), d("2026-05-30"), d("2026-08-30"), d("2026-11-30"), d("2027-02-28")),
            advanceSeries(d("2025-11-30"), BillingCycle.QUARTERLY, 30, 5),
        )
        assertEquals(d("2026-03-15"), DueDateCalculator.advance(d("2025-12-15"), BillingCycle.QUARTERLY, null))
    }

    @Test
    fun otherMonthPresets() {
        assertEquals(d("2027-03-31"), DueDateCalculator.advance(d("2026-09-30"), BillingCycle.SEMI_ANNUAL, 31))
        assertEquals(d("2028-02-29"), DueDateCalculator.advance(d("2026-02-28"), BillingCycle.BIENNIAL, 29))
        assertEquals(d("2029-09-28"), DueDateCalculator.advance(d("2026-09-28"), BillingCycle.TRIENNIAL, null))
    }

    @Test
    fun custom45Days() {
        assertEquals(
            listOf(d("2026-02-15"), d("2026-04-01"), d("2026-05-16")),
            advanceSeries(d("2026-01-01"), BillingCycle.days(45), null, 3),
        )
        // Anchor is ignored for DAY cycles.
        assertEquals(d("2026-02-15"), DueDateCalculator.advance(d("2026-01-01"), BillingCycle.days(45), 31))
    }

    @Test
    fun once_hasNoNextDue() {
        assertNull(DueDateCalculator.advance(d("2026-01-01"), BillingCycle.ONE_TIME, 1))
    }

    // ---- rollForward ----

    @Test
    fun monthly_overdue400Days_rollsInOneCall() {
        val today = d("2026-09-28")
        val due = today.minusDays(400)
        assertEquals(d("2025-08-24"), due)

        val result = DueDateCalculator.rollForward(due, BillingCycle.MONTHLY, 24, today)
        assertEquals(RollResult(d("2026-10-24"), 14), result)
        // Same as advancing 14 times one by one.
        assertEquals(result.due, advanceSeries(due, BillingCycle.MONTHLY, 24, 14).last())
    }

    @Test
    fun monthly_rollKeepsAnchor31ThroughFebruary() {
        val result = DueDateCalculator.rollForward(d("2025-08-31"), BillingCycle.MONTHLY, 31, d("2026-09-28"))
        assertEquals(RollResult(d("2026-09-30"), 13), result)

        // With no anchor the due date's own day (31) is kept for the whole roll.
        val noAnchor = DueDateCalculator.rollForward(d("2026-01-31"), BillingCycle.MONTHLY, null, d("2026-03-05"))
        assertEquals(RollResult(d("2026-03-31"), 2), noAnchor)
    }

    @Test
    fun monthly_rollLandsExactlyOnToday() {
        val result = DueDateCalculator.rollForward(d("2026-01-15"), BillingCycle.MONTHLY, 15, d("2026-03-15"))
        assertEquals(RollResult(d("2026-03-15"), 2), result)
    }

    @Test
    fun monthly_oneDayOverdue_rollsOnePeriod() {
        val result = DueDateCalculator.rollForward(d("2026-09-27"), BillingCycle.MONTHLY, 27, d("2026-09-28"))
        assertEquals(RollResult(d("2026-10-27"), 1), result)
    }

    @Test
    fun monthly_inconsistentAnchor_rollsOneRealPeriod() {
        // due Jan 15 with anchor 31: the next due is Feb 28 (not Jan 31).
        val result = DueDateCalculator.rollForward(d("2026-01-15"), BillingCycle.MONTHLY, 31, d("2026-01-20"))
        assertEquals(RollResult(d("2026-02-28"), 1), result)
    }

    @Test
    fun monthly_veryOldDue_rollsWithoutHittingLoopGuard() {
        val result = DueDateCalculator.rollForward(d("1900-01-01"), BillingCycle.MONTHLY, 1, d("2026-09-28"))
        assertEquals(RollResult(d("2026-10-01"), 1521), result)
    }

    @Test
    fun quarterly_rollAcrossYears() {
        val result = DueDateCalculator.rollForward(d("2024-11-30"), BillingCycle.QUARTERLY, 30, d("2026-01-10"))
        // 2025-02-28, 05-30, 08-30, 11-30, 2026-02-28
        assertEquals(RollResult(d("2026-02-28"), 5), result)
    }

    @Test
    fun annual_leapDayRoll() {
        val result = DueDateCalculator.rollForward(d("2020-02-29"), BillingCycle.ANNUAL, 29, d("2026-09-28"))
        assertEquals(RollResult(d("2027-02-28"), 7), result)
        val toLeap = DueDateCalculator.rollForward(d("2020-02-29"), BillingCycle.ANNUAL, 29, d("2027-03-01"))
        assertEquals(RollResult(d("2028-02-29"), 8), toLeap)
    }

    @Test
    fun days_rollUsesIntegerDivision() {
        val cycle = BillingCycle.days(45)
        // Exactly two periods overdue: lands on today.
        assertEquals(RollResult(d("2026-04-01"), 2), DueDateCalculator.rollForward(d("2026-01-01"), cycle, null, d("2026-04-01")))
        // One day more needs a third period.
        assertEquals(RollResult(d("2026-05-16"), 3), DueDateCalculator.rollForward(d("2026-01-01"), cycle, null, d("2026-04-02")))
        // One day overdue.
        assertEquals(RollResult(d("2026-02-15"), 1), DueDateCalculator.rollForward(d("2026-01-01"), cycle, null, d("2026-01-02")))
        // 400 days overdue on a 1-day cycle.
        assertEquals(RollResult(d("2026-09-28"), 400), DueDateCalculator.rollForward(d("2025-08-24"), BillingCycle.days(1), null, d("2026-09-28")))
    }

    @Test
    fun once_isNeverRolled() {
        val result = DueDateCalculator.rollForward(d("2020-01-01"), BillingCycle.ONE_TIME, null, d("2026-09-28"))
        assertEquals(RollResult(d("2020-01-01"), 0), result)
    }

    @Test
    fun dueToday_isNotRolled() {
        val today = d("2026-09-28")
        assertEquals(RollResult(today, 0), DueDateCalculator.rollForward(today, BillingCycle.MONTHLY, 28, today))
        assertEquals(RollResult(today, 0), DueDateCalculator.rollForward(today, BillingCycle.days(7), null, today))
    }

    @Test
    fun futureDue_isNotRolled() {
        val due = d("2026-10-05")
        assertEquals(RollResult(due, 0), DueDateCalculator.rollForward(due, BillingCycle.MONTHLY, 5, d("2026-09-28")))
    }

    // ---- statusOf / daysUntil ----

    @Test
    fun status_allStatesAndBoundaries() {
        val today = d("2026-09-28")
        fun status(due: LocalDate?, soon: Int = 7, permanent: Boolean = false) =
            DueDateCalculator.statusOf(due, today, soon, permanent)

        assertEquals(DueStatus.PERMANENT, status(d("2020-01-01"), permanent = true))
        assertEquals(DueStatus.PERMANENT, status(null, permanent = true))
        assertEquals(DueStatus.NONE, status(null))
        assertEquals(DueStatus.EXPIRED, status(d("2026-09-27")))
        assertEquals(DueStatus.EXPIRED, status(d("2025-01-01")))
        assertEquals(DueStatus.DUE_TODAY, status(today))
        assertEquals(DueStatus.DUE_SOON, status(d("2026-09-29")))
        assertEquals(DueStatus.DUE_SOON, status(d("2026-10-05"))) // exactly 7 days: inclusive
        assertEquals(DueStatus.ACTIVE, status(d("2026-10-06"))) // 8 days
        assertEquals(DueStatus.ACTIVE, status(d("2026-09-29"), soon = 0))
        assertEquals(DueStatus.DUE_TODAY, status(today, soon = 0))
        assertEquals(DueStatus.ACTIVE, status(d("2026-09-29"), soon = -3))
        assertEquals(DueStatus.DUE_TODAY, status(today, soon = -3))
        assertEquals(DueStatus.EXPIRED, status(d("2026-09-27"), soon = 30))
    }

    @Test
    fun daysUntil_signedDayCount() {
        assertEquals(3L, DueDateCalculator.daysUntil(d("2026-10-01"), d("2026-09-28")))
        assertEquals(-2L, DueDateCalculator.daysUntil(d("2026-09-26"), d("2026-09-28")))
        assertEquals(0L, DueDateCalculator.daysUntil(d("2026-09-28"), d("2026-09-28")))
        assertEquals(1L, DueDateCalculator.daysUntil(d("2027-01-01"), d("2026-12-31")))
        assertEquals(366L, DueDateCalculator.daysUntil(d("2029-01-01"), d("2028-01-01")))
    }
}
