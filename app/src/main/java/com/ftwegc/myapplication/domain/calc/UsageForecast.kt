package com.ftwegc.myapplication.domain.calc

import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.TrafficPeriod
import java.time.LocalDate
import kotlin.math.ceil

/**
 * Where the traffic of the current period is heading (detail page "用量预测").
 *
 * @property cumulative used bytes at the end of each day from the period start up to today (one
 *   entry per day, the last one is today's [usedBytes]); between readings days without traffic
 *   stay flat, before the first reading the curve rises in a straight line from 0.
 * @property totalDays days in the period.
 * @property ratePerDay average use per day so far (used ÷ elapsed days, today counted as one day).
 * @property projectedEnd expected total at the end of the period at that rate.
 * @property exhaustDate the day the limit is reached at that rate; `null` without a limit, when
 *   it is already exceeded, or when the period ends first.
 * @property exceeded the limit is already used up.
 */
data class UsageForecastResult(
    val period: TrafficPeriod,
    val cumulative: List<Long>,
    val totalDays: Int,
    val usedBytes: Long,
    val limitBytes: Long?,
    val ratePerDay: Double,
    val projectedEnd: Long,
    val exhaustDate: LocalDate?,
    val exceeded: Boolean,
) {
    /** The limit will be reached within this period (or already is). */
    val runsOut: Boolean get() = exceeded || exhaustDate != null
}

/** Straight-line projection of a VPS's traffic over its billing period. Pure. */
object UsageForecast {

    /**
     * @param period the current billing period; [today] must lie in it (otherwise `null`).
     * @param usedBytes what counts against the limit so far this period.
     * @param limitBytes `<= 0` for no limit.
     * @param daily usage per day (any days; only the period's days up to today are used) to shape
     *   the curve; the curve always ends at [usedBytes] and never dips below 0.
     */
    fun compute(
        period: TrafficPeriod,
        today: LocalDate,
        usedBytes: Long,
        limitBytes: Long,
        daily: List<DayUsage>,
        limitType: TrafficLimitType,
    ): UsageForecastResult? {
        if (today !in period) return null
        val used = usedBytes.coerceAtLeast(0)
        val elapsed = (today.toEpochDay() - period.start.toEpochDay() + 1).toInt()
        val total = period.lengthDays.toInt()
        val perDay = daily
            .filter { it.date in period && !it.date.isAfter(today) }
            .associate { it.date to it.usedBytes(limitType).coerceAtLeast(0) }
        // Walk back from today: the day before holds today's total minus what today added.
        val values = LongArray(elapsed)
        values[elapsed - 1] = used
        for (i in elapsed - 2 downTo 0) {
            val nextDay = period.start.plusDays((i + 1).toLong())
            values[i] = (values[i + 1] - (perDay[nextDay] ?: 0L)).coerceAtLeast(0)
        }
        // Before the first day with readings nothing is known about the shape: a straight line
        // from 0 at the period start (the average rate), instead of a jump on day one.
        val firstKnown = (0 until elapsed).firstOrNull { period.start.plusDays(it.toLong()) in perDay } ?: elapsed
        val anchorIndex = firstKnown - 1
        if (anchorIndex > 0) {
            val anchor = values[anchorIndex]
            for (i in 0 until anchorIndex) values[i] = anchor * (i + 1) / (anchorIndex + 1)
        }
        val rate = used.toDouble() / elapsed
        val projected = (rate * total).toLong().coerceAtLeast(used)
        val limit = limitBytes.takeIf { it > 0 }
        val exceeded = limit != null && used >= limit
        val exhaust = when {
            limit == null || exceeded || rate <= 0.0 -> null
            else -> {
                // Day k (0-based) ends with rate × (k + 1) used.
                val index = ceil(limit / rate).toLong() - 1
                period.start.plusDays(index.coerceAtLeast(0)).takeIf { it.isBefore(period.endExclusive) }
            }
        }
        return UsageForecastResult(
            period = period,
            cumulative = values.toList(),
            totalDays = total,
            usedBytes = used,
            limitBytes = limit,
            ratePerDay = rate,
            projectedEnd = projected,
            exhaustDate = exhaust,
            exceeded = exceeded,
        )
    }
}
