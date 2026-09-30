package com.ftwegc.myapplication.domain.model

import java.time.Instant

/**
 * Per-VPS settings stored alongside a [Subscription] of category [Category.VPS] (1:1).
 *
 * @property subscriptionId id of the owning subscription.
 * @property komariUuid linked Komari node, `null` when not linked.
 * @property komariMissing the linked node was not found in the last node list.
 * @property lockedFields [LockedField] bit mask of fields the user edited in the app.
 * @property trafficLimitBytes traffic limit in bytes; `0` means no limit.
 * @property resetDay day of month (1–31) the traffic period resets on.
 * @property resetZoneId time zone id used for the reset day, `null` means the device zone.
 * @property alertPercent usage percentage that triggers a traffic alert.
 * @property lastTotalUp last raw Komari upload counter, bytes.
 * @property lastTotalDown last raw Komari download counter, bytes.
 * @property lastUptimeSec last reported uptime in seconds.
 * @property lastReportedAt when the agent reported the last counters.
 * @property lastFetchAt when the app last fetched this node.
 * @property lastError last fetch error message, `null` when the last fetch succeeded.
 * @property appCountingSince counting mode 2: when the app started accumulating; snapshots fetched
 *   earlier are never used as a baseline. `null` outside mode 2.
 * @property agentMonthRotate the user says the node's agent runs with `--month-rotate`: Komari's
 *   counters are then monthly totals from [resetDay], not "since boot".
 * @property komariRegion the linked node's `region` (a flag emoji), `null` when unknown.
 */
data class VpsConfig(
    val subscriptionId: Long = 0,
    val komariUuid: String? = null,
    val komariName: String? = null,
    val komariGroup: String? = null,
    val komariMissing: Boolean = false,
    val lockedFields: Int = LockedField.NONE,
    val trafficLimitBytes: Long = 0,
    val trafficLimitType: TrafficLimitType = TrafficLimitType.DEFAULT,
    val countingMode: CountingMode = CountingMode.KOMARI_CUMULATIVE,
    val resetDay: Int = DEFAULT_RESET_DAY,
    val resetZoneId: String? = null,
    val alertPercent: Int = DEFAULT_ALERT_PERCENT,
    val lastTotalUp: Long? = null,
    val lastTotalDown: Long? = null,
    val lastUptimeSec: Long? = null,
    val lastReportedAt: Instant? = null,
    val lastFetchAt: Instant? = null,
    val lastError: String? = null,
    val appCountingSince: Instant? = null,
    val agentMonthRotate: Boolean = false,
    val komariRegion: String? = null,
) {
    /** True when a Komari node is linked. */
    val isLinked: Boolean get() = komariUuid != null

    /** True when a traffic limit is set. */
    val hasLimit: Boolean get() = trafficLimitBytes > 0

    /** The last raw counter reading, or `null` when none was stored. */
    val lastSample: CounterSample?
        get() {
            val up = lastTotalUp ?: return null
            val down = lastTotalDown ?: return null
            val at = lastReportedAt ?: return null
            return CounterSample(up, down, lastUptimeSec, at)
        }

    companion object {
        const val DEFAULT_RESET_DAY: Int = 1
        const val DEFAULT_ALERT_PERCENT: Int = 80
        const val MIN_ALERT_PERCENT: Int = 50
        const val MAX_ALERT_PERCENT: Int = 100
    }
}

/**
 * A subscription together with its VPS settings (`null` for non-VPS or not yet configured) and,
 * for a VPS, the newest traffic period the app accumulated (counting mode 2).
 */
data class SubscriptionWithVps(
    val subscription: Subscription,
    val vps: VpsConfig?,
    val appPeriod: AppPeriodUsage? = null,
)

/**
 * Traffic the app accumulated in one billing period (counting mode 2).
 *
 * @property upBytes / [downBytes] accumulated deltas.
 * @property initialUsedBytes usage the user entered for the time before the app started counting,
 *   in "used" terms (already per the limit type).
 * @property updatedAt last time a delta was added.
 */
data class AppPeriodUsage(
    val period: TrafficPeriod,
    val upBytes: Long,
    val downBytes: Long,
    val initialUsedBytes: Long,
    val updatedAt: Instant?,
)
