package com.ftwegc.myapplication.domain.calc

import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.DueStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Result of [DueDateCalculator.rollForward].
 *
 * @property due the rolled due date (unchanged when nothing was rolled).
 * @property periods number of billing cycles advanced (0 when not rolled).
 */
data class RollResult(val due: LocalDate, val periods: Long)

/** Due-date arithmetic. All dates are local dates; "today" is always passed in. */
object DueDateCalculator {

    /**
     * Next due date after [due] for one [cycle].
     *
     * - MONTH: `due + count months`, then the day is set to `min(anchorDay, lengthOfMonth)`;
     *   when [anchorDay] is `null` the day of [due] is used. [anchorDay] is clamped to 1..31.
     * - DAY: `due + count days`.
     * - ONCE: `null` (never renews).
     */
    fun advance(due: LocalDate, cycle: BillingCycle, anchorDay: Int?): LocalDate? = when (cycle.unit) {
        CycleUnit.MONTH -> monthsAfter(due, cycle.count.toLong(), anchorDay ?: due.dayOfMonth)
        CycleUnit.DAY -> due.plusDays(cycle.count.toLong())
        CycleUnit.ONCE -> null
    }

    /**
     * Rolls an overdue [due] forward to the first due date that is not before [today].
     *
     * Nothing is rolled when `due >= today` (a due date equal to today stays) or for ONCE cycles.
     * DAY cycles are computed with integer division. MONTH cycles keep a fixed anchor for the whole
     * roll (`anchorDay ?: due.dayOfMonth`), so a 31st subscription does not drift to the 28th while
     * rolling through February; the month loop is bounded by a jump-ahead plus [MAX_MONTH_STEPS].
     */
    fun rollForward(due: LocalDate, cycle: BillingCycle, anchorDay: Int?, today: LocalDate): RollResult {
        if (!today.isAfter(due)) return RollResult(due, 0)
        return when (cycle.unit) {
            CycleUnit.ONCE -> RollResult(due, 0)
            CycleUnit.DAY -> {
                val step = cycle.count.toLong()
                val overdueDays = ChronoUnit.DAYS.between(due, today)
                val periods = (overdueDays + step - 1) / step
                RollResult(due.plusDays(periods * step), periods)
            }
            CycleUnit.MONTH -> rollMonths(due, cycle.count.toLong(), anchorDay ?: due.dayOfMonth, today)
        }
    }

    /**
     * Status of [due] relative to [today].
     *
     * Order: [permanent] → PERMANENT; `due == null` → NONE; before today → EXPIRED;
     * today → DUE_TODAY (not expired); within `1..soonDays` days (inclusive) → DUE_SOON; else ACTIVE.
     * A negative [soonDays] is treated as 0.
     */
    fun statusOf(due: LocalDate?, today: LocalDate, soonDays: Int, permanent: Boolean): DueStatus {
        if (permanent) return DueStatus.PERMANENT
        if (due == null) return DueStatus.NONE
        val days = daysUntil(due, today)
        return when {
            days < 0 -> DueStatus.EXPIRED
            days == 0L -> DueStatus.DUE_TODAY
            days <= soonDays.coerceAtLeast(0) -> DueStatus.DUE_SOON
            else -> DueStatus.ACTIVE
        }
    }

    /** Days from [today] to [due]; negative when [due] is in the past, 0 when equal. */
    fun daysUntil(due: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, due)

    /** Safety bound on month-loop iterations after the jump-ahead (normally at most 2 are needed). */
    private const val MAX_MONTH_STEPS = 1_000

    private fun rollMonths(due: LocalDate, step: Long, anchor: Int, today: LocalDate): RollResult {
        // Jump close to today in one go, staying at or before the target, then step forward.
        val monthGap = ChronoUnit.MONTHS.between(YearMonth.from(due), YearMonth.from(today))
        var periods = ((monthGap / step) - 1).coerceAtLeast(0)
        // periods == 0 keeps the original date: re-anchoring it here would skip a real cycle.
        var current = if (periods == 0L) due else monthsAfter(due, periods * step, anchor)
        var guard = 0
        while (current.isBefore(today)) {
            check(guard++ < MAX_MONTH_STEPS) { "rollForward did not converge for $due / $today" }
            periods++
            current = monthsAfter(due, periods * step, anchor)
        }
        return RollResult(current, periods)
    }

    /** `base + months`, day set to `min(anchor clamped to 1..31, lengthOfMonth)`. */
    private fun monthsAfter(base: LocalDate, months: Long, anchor: Int): LocalDate {
        val target = YearMonth.from(base).plusMonths(months)
        return target.atDay(minOf(anchor.coerceIn(1, 31), target.lengthOfMonth()))
    }
}
