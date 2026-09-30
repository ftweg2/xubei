package com.ftwegc.myapplication.domain.calc

import com.ftwegc.myapplication.domain.model.TrafficPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Computes monthly traffic billing periods from a reset day. */
object TrafficPeriodCalculator {

    /**
     * The period containing [today].
     *
     * [resetDay] is clamped to 1..31. The effective reset day of a month is
     * `min(resetDay, lengthOfMonth)`, so a reset day of 31 falls on Feb 28/29 and Apr 30.
     * The period starts at the latest effective reset day that is not after [today] and ends
     * (exclusive) at the next effective reset day.
     */
    fun periodFor(today: LocalDate, resetDay: Int): TrafficPeriod {
        val day = resetDay.coerceIn(1, 31)
        val month = YearMonth.from(today)
        val resetThisMonth = effectiveResetDate(month, day)
        return if (today.isBefore(resetThisMonth)) {
            TrafficPeriod(effectiveResetDate(month.minusMonths(1), day), resetThisMonth)
        } else {
            TrafficPeriod(resetThisMonth, effectiveResetDate(month.plusMonths(1), day))
        }
    }

    /** Period containing [instant] as seen in [zone]. */
    fun periodFor(instant: Instant, zone: ZoneId, resetDay: Int): TrafficPeriod =
        periodFor(todayIn(instant, zone), resetDay)

    /** Local calendar date of [instant] in [zone]. */
    fun todayIn(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()

    private fun effectiveResetDate(month: YearMonth, day: Int): LocalDate =
        month.atDay(minOf(day, month.lengthOfMonth()))
}
