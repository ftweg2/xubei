package com.ftwegc.myapplication.domain.calc

import com.ftwegc.myapplication.domain.model.TrafficLimitType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Spreads an amount of traffic evenly over a time span. */
object TimeSplit {

    /**
     * The part of [amount] that falls before [boundary] when [amount] is spread evenly over
     * `[from, to]`: all of it when `to <= boundary`, none when `from >= boundary`.
     */
    fun before(amount: Long, from: Instant, to: Instant, boundary: Instant): Long {
        if (amount <= 0) return 0
        if (!to.isAfter(boundary)) return amount
        if (!from.isBefore(boundary)) return 0
        val total = Duration.between(from, to).toMillis().toDouble()
        if (total <= 0) return 0
        val part = Duration.between(from, boundary).toMillis().toDouble()
        return Math.round(amount * (part / total)).coerceIn(0, amount)
    }

    /**
     * [amount] spread evenly over `[from, to]` and split by local calendar day in [zone]. The parts
     * add up to [amount] exactly (rounding goes to the last day). An empty or reversed span puts
     * everything on the day of [to].
     */
    fun byDay(amount: Long, from: Instant, to: Instant, zone: ZoneId): Map<LocalDate, Long> {
        val lastDay = to.atZone(zone).toLocalDate()
        if (amount <= 0) return emptyMap()
        if (!from.isBefore(to)) return mapOf(lastDay to amount)
        val result = linkedMapOf<LocalDate, Long>()
        var day = from.atZone(zone).toLocalDate()
        var assigned = 0L
        var guard = 0
        while (day.isBefore(lastDay) && guard++ < MAX_DAYS) {
            val nextStart = day.plusDays(1).atStartOfDay(zone).toInstant()
            val cumulative = before(amount, from, to, nextStart)
            val part = (cumulative - assigned).coerceAtLeast(0)
            if (part > 0) result[day] = part
            assigned += part
            day = day.plusDays(1)
        }
        val rest = amount - assigned
        if (rest > 0) result[lastDay] = (result[lastDay] ?: 0L) + rest
        return result
    }

    /** Longer spans put the remainder on the last day instead of looping. */
    private const val MAX_DAYS = 400
}

/** One stored reading, as far as the trend needs it. */
data class SnapshotPoint(
    val reportedAt: Instant,
    val deltaUp: Long,
    val deltaDown: Long,
    val uptimeSec: Long?,
    val resetDetected: Boolean,
)

/** Traffic of one local day. */
data class DayUsage(val date: LocalDate, val upBytes: Long, val downBytes: Long) {
    fun usedBytes(type: TrafficLimitType): Long = TrafficCalculator.usedBytes(upBytes, downBytes, type)
}

/**
 * Daily usage for the trend chart.
 *
 * @property days one entry per day from the first day with data (or `today - maxDays + 1`, when
 *   that is later) up to [today], oldest first; days without traffic are 0.
 * @property enoughData false while fewer than [TrafficTrend.MIN_DAYS_WITH_DATA] days carry data:
 *   the UI then explains instead of drawing a nearly empty chart.
 */
data class TrendResult(val days: List<DayUsage>, val enoughData: Boolean)

/** Builds daily usage from consecutive snapshots. */
object TrafficTrend {

    const val DEFAULT_DAYS: Int = 14
    const val MIN_DAYS_WITH_DATA: Int = 2

    /**
     * Sums the deltas of [points] (any order; sorted by report time here) per local day in [zone].
     *
     * A delta covers the time since the previous reading, so it is spread over that span and split
     * at midnight ([TimeSplit.byDay]). After a reset the traffic started at the reboot
     * (`reportedAt - uptime`) when known. The first point has no previous reading and is put on
     * its own day. Days before `today - maxDays + 1` are dropped.
     */
    fun daily(points: List<SnapshotPoint>, zone: ZoneId, today: LocalDate, maxDays: Int = DEFAULT_DAYS): TrendResult {
        val sorted = points.sortedBy { it.reportedAt }
        val totals = sortedMapOf<LocalDate, LongArray>()
        var prevAt: Instant? = null
        for (p in sorted) {
            val from = spanStart(prevAt, p)
            for ((day, up) in TimeSplit.byDay(p.deltaUp.coerceAtLeast(0), from, p.reportedAt, zone)) {
                totals.getOrPut(day) { LongArray(2) }[0] += up
            }
            for ((day, down) in TimeSplit.byDay(p.deltaDown.coerceAtLeast(0), from, p.reportedAt, zone)) {
                totals.getOrPut(day) { LongArray(2) }[1] += down
            }
            // A day with readings but no traffic still counts as "has data".
            totals.getOrPut(p.reportedAt.atZone(zone).toLocalDate()) { LongArray(2) }
            prevAt = p.reportedAt
        }
        val windowStart = today.minusDays((maxDays - 1).coerceAtLeast(0).toLong())
        val dataDays = totals.keys.filter { !it.isBefore(windowStart) && !it.isAfter(today) }
        if (dataDays.isEmpty()) return TrendResult(emptyList(), enoughData = false)
        val first = maxOf(dataDays.first(), windowStart)
        val days = generateSequence(first) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .map { day -> totals[day]?.let { DayUsage(day, it[0], it[1]) } ?: DayUsage(day, 0, 0) }
            .toList()
        return TrendResult(days, enoughData = dataDays.size >= MIN_DAYS_WITH_DATA)
    }

    /** Start of the span a point's delta covers. */
    internal fun spanStart(prevAt: Instant?, p: SnapshotPoint): Instant {
        if (prevAt == null || !prevAt.isBefore(p.reportedAt)) return p.reportedAt
        val uptime = p.uptimeSec?.takeIf { it >= 0 }
        if (p.resetDetected && uptime != null) {
            val boot = p.reportedAt.minusSeconds(uptime)
            if (boot.isAfter(prevAt)) return boot
        }
        return prevAt
    }
}
