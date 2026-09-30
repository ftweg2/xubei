package com.ftwegc.myapplication.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * How used traffic is derived from upload/download byte counters (Komari `traffic_limit_type`).
 */
enum class TrafficLimitType {
    /** up + down. */
    SUM,

    /** max(up, down). */
    MAX,

    /** min(up, down). */
    MIN,

    /** Upload only. */
    UP,

    /** Download only. */
    DOWN;

    companion object {
        /** Fallback used for null, blank or unknown values. */
        val DEFAULT: TrafficLimitType = MAX

        /**
         * Lenient parse: trims and ignores case (`" Sum "` → [SUM]).
         * `null`, blank or unknown strings fall back to [MAX].
         */
        fun parse(raw: String?): TrafficLimitType {
            val key = raw?.trim()?.uppercase(Locale.ROOT).orEmpty()
            return entries.firstOrNull { it.name == key } ?: DEFAULT
        }
    }
}

/** Where the "used this period" figure comes from. */
enum class CountingMode {
    /** Use Komari's cumulative counters as-is (accurate with agent `--month-rotate`). */
    KOMARI_CUMULATIVE,

    /** App sums deltas between its own snapshots and resets on the user's reset day. */
    APP_ACCUMULATED,
}

/**
 * One raw counter reading from Komari.
 *
 * @property totalUp cumulative upload in bytes.
 * @property totalDown cumulative download in bytes.
 * @property uptimeSec machine uptime in seconds, `null` if not reported.
 * @property reportedAt when the agent reported this reading.
 */
data class CounterSample(
    val totalUp: Long,
    val totalDown: Long,
    val uptimeSec: Long?,
    val reportedAt: Instant,
)

/**
 * A traffic billing period `[start, endExclusive)` in local dates.
 */
data class TrafficPeriod(val start: LocalDate, val endExclusive: LocalDate) {
    init {
        require(start.isBefore(endExclusive)) { "start $start must be before endExclusive $endExclusive" }
    }

    /** True when [date] is inside `[start, endExclusive)`. */
    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && date.isBefore(endExclusive)

    /** Number of days in the period. */
    val lengthDays: Long get() = endExclusive.toEpochDay() - start.toEpochDay()
}

/**
 * Traffic usage of one VPS for display.
 *
 * @property upBytes upload in bytes for the period.
 * @property downBytes download in bytes for the period.
 * @property usedBytes bytes counted against the limit per [limitType].
 * @property limitBytes limit in bytes; `<= 0` means no limit.
 * @property percent `usedBytes / limitBytes × 100`, not capped at 100; `null` when there is no limit.
 * @property period billing period, `null` when unknown.
 * @property updatedAt time of the underlying data, `null` when unknown.
 */
data class TrafficUsage(
    val upBytes: Long,
    val downBytes: Long,
    val usedBytes: Long,
    val limitBytes: Long,
    val limitType: TrafficLimitType,
    val percent: Double?,
    val period: TrafficPeriod? = null,
    val updatedAt: Instant? = null,
)

/**
 * VPS traffic settings synced from Komari.
 *
 * @property limitBytes traffic limit in bytes; `0` means no limit.
 */
data class VpsTrafficFields(
    val limitBytes: Long,
    val limitType: TrafficLimitType,
)
