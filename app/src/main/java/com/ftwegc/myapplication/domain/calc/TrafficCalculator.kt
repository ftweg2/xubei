package com.ftwegc.myapplication.domain.calc

import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.TrafficPeriod
import com.ftwegc.myapplication.domain.model.TrafficUsage
import java.time.Instant

/** Pure traffic arithmetic. All byte values are in bytes. */
object TrafficCalculator {

    /**
     * Bytes counted against the limit for [type].
     * Negative inputs are treated as 0; [TrafficLimitType.SUM] saturates at [Long.MAX_VALUE].
     */
    fun usedBytes(up: Long, down: Long, type: TrafficLimitType): Long {
        val u = up.coerceAtLeast(0)
        val d = down.coerceAtLeast(0)
        return when (type) {
            TrafficLimitType.SUM -> saturatingAdd(u, d)
            TrafficLimitType.MAX -> maxOf(u, d)
            TrafficLimitType.MIN -> minOf(u, d)
            TrafficLimitType.UP -> u
            TrafficLimitType.DOWN -> d
        }
    }

    /**
     * `used / limit × 100`. Returns `null` when `limit <= 0` (no limit).
     * Not capped: may exceed 100. Negative [used] is treated as 0.
     */
    fun usagePercent(used: Long, limit: Long): Double? {
        if (limit <= 0) return null
        return used.coerceAtLeast(0).toDouble() * 100.0 / limit.toDouble()
    }

    /** Builds a [TrafficUsage] from raw up/down bytes. */
    fun usage(
        up: Long,
        down: Long,
        limitBytes: Long,
        type: TrafficLimitType,
        period: TrafficPeriod? = null,
        updatedAt: Instant? = null,
    ): TrafficUsage {
        val used = usedBytes(up, down, type)
        return TrafficUsage(
            upBytes = up.coerceAtLeast(0),
            downBytes = down.coerceAtLeast(0),
            usedBytes = used,
            limitBytes = limitBytes,
            limitType = type,
            percent = usagePercent(used, limitBytes),
            period = period,
            updatedAt = updatedAt,
        )
    }

    /**
     * Usage in counting mode 2: [initialUsed] (entered by the user, already in "used" terms) plus
     * the accumulated [up] / [down] counted per [type]. Up and down stay the accumulated values.
     */
    fun appUsage(
        up: Long,
        down: Long,
        initialUsed: Long,
        limitBytes: Long,
        type: TrafficLimitType,
        period: TrafficPeriod? = null,
        updatedAt: Instant? = null,
    ): TrafficUsage {
        val used = saturatingAdd(initialUsed.coerceAtLeast(0), usedBytes(up, down, type))
        return TrafficUsage(
            upBytes = up.coerceAtLeast(0),
            downBytes = down.coerceAtLeast(0),
            usedBytes = used,
            limitBytes = limitBytes,
            limitType = type,
            percent = usagePercent(used, limitBytes),
            period = period,
            updatedAt = updatedAt,
        )
    }

    /** a + b for non-negative a and b, clamped to [Long.MAX_VALUE]. */
    internal fun saturatingAdd(a: Long, b: Long): Long =
        if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
}
