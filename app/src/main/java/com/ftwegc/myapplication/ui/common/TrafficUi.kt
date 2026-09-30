package com.ftwegc.myapplication.ui.common

import androidx.compose.runtime.Immutable
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.repo.TrafficRepository
import com.ftwegc.myapplication.domain.calc.TrafficCalculator
import com.ftwegc.myapplication.domain.calc.TrafficResolver
import com.ftwegc.myapplication.domain.model.AppPeriodUsage
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.TrafficPeriod
import com.ftwegc.myapplication.domain.model.TrafficUsage
import com.ftwegc.myapplication.domain.model.VpsConfig
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Why the traffic shown may be stale. */
sealed interface TrafficIssue {
    /** The panel had no recent report of the node. */
    data object Offline : TrafficIssue

    /** The node is no longer listed by the panel. */
    data object Missing : TrafficIssue

    /** The last fetch failed with [error]. */
    data class Failed(val error: KomariError) : TrafficIssue
}

/**
 * Counting mode 2 details.
 *
 * @property period the current billing period.
 * @property accumulatedUsedBytes what the app counted itself this period (per the limit type).
 * @property initialUsedBytes usage entered for the time before counting started.
 * @property countingSince when the app started accumulating.
 * @property waitingForBaseline nothing accumulated yet: the next refresh sets the starting point.
 * @property waitingForNewPeriod a new period began and has not been refreshed yet.
 * @property updatedAt last time a delta was added.
 */
@Immutable
data class AppTrafficInfo(
    val period: TrafficPeriod?,
    val accumulatedUsedBytes: Long,
    val initialUsedBytes: Long,
    val countingSince: Instant?,
    val waitingForBaseline: Boolean,
    val waitingForNewPeriod: Boolean,
    val updatedAt: Instant?,
)

/**
 * Traffic of one VPS as shown by the detail card, the home rows and the list cards.
 *
 * - Mode 1: Komari's cumulative counters, used amount per the limit type.
 * - Mode 2: the period the app accumulated ([app]), plus the usage entered for the time before.
 *
 * @property usage `null` until there is something to show.
 * @property longOffline linked, offline and without a report for [LONG_OFFLINE] (or never reported):
 *   the node is probably gone for good; the detail page offers to unlink it.
 */
@Immutable
data class TrafficUi(
    val linked: Boolean,
    val nodeName: String?,
    val usage: TrafficUsage?,
    val reportedAt: Instant?,
    val fetchedAt: Instant?,
    val issue: TrafficIssue?,
    val mode: CountingMode = CountingMode.KOMARI_CUMULATIVE,
    /** Machine uptime at the reading; Komari's counters run since boot unless `--month-rotate` is set. */
    val uptimeSec: Long? = null,
    val app: AppTrafficInfo? = null,
    val longOffline: Boolean = false,
) {
    val isAppMode: Boolean get() = mode == CountingMode.APP_ACCUMULATED

    companion object {
        /** Offline this long (or never reported) counts as gone for good. */
        val LONG_OFFLINE: Duration = Duration.ofDays(3)

        /**
         * @param appPeriod newest accumulated period (mode 2).
         * @param today local date used to decide the current period; `null` takes the stored one.
         * @param now reference time for [longOffline]; `null` skips that check.
         */
        fun of(
            config: VpsConfig,
            appPeriod: AppPeriodUsage? = null,
            today: LocalDate? = null,
            now: Instant? = null,
        ): TrafficUi {
            val resolved = TrafficResolver.resolve(config, appPeriod, today)
            val appMode = config.countingMode == CountingMode.APP_ACCUMULATED
            val usage = resolved.usage
            val issue = when {
                !config.isLinked -> null
                config.komariMissing -> TrafficIssue.Missing
                config.lastError == TrafficRepository.OFFLINE -> TrafficIssue.Offline
                config.lastError != null -> TrafficIssue.Failed(KomariError.fromCode(config.lastError) ?: KomariError.Parse)
                else -> null
            }
            val lastReport = config.lastReportedAt
            val longOffline = config.isLinked && issue == TrafficIssue.Offline && now != null &&
                (lastReport == null || Duration.between(lastReport, now) >= LONG_OFFLINE)
            val app = if (appMode) {
                val stored = appPeriod?.takeIf { !resolved.waitingForNewPeriod }
                AppTrafficInfo(
                    period = resolved.period,
                    accumulatedUsedBytes = stored?.let { TrafficCalculator.usedBytes(it.upBytes, it.downBytes, config.trafficLimitType) } ?: 0,
                    initialUsedBytes = stored?.initialUsedBytes ?: 0,
                    countingSince = config.appCountingSince,
                    waitingForBaseline = appPeriod == null || (appPeriod.updatedAt == null && !resolved.waitingForNewPeriod),
                    waitingForNewPeriod = resolved.waitingForNewPeriod,
                    updatedAt = appPeriod?.updatedAt,
                )
            } else {
                null
            }
            return TrafficUi(
                linked = config.isLinked,
                nodeName = config.komariName,
                usage = usage,
                reportedAt = if (appMode) appPeriod?.updatedAt ?: config.lastReportedAt else usage?.updatedAt,
                fetchedAt = config.lastFetchAt,
                issue = issue,
                mode = config.countingMode,
                uptimeSec = if (usage != null && !appMode) config.lastUptimeSec else null,
                app = app,
                longOffline = longOffline,
            )
        }
    }
}

/** Sort key for "highest usage first": limited VPSs by percent, then unlimited by bytes. */
fun TrafficUsage?.usageSortKey(): Pair<Double, Long> = when {
    this == null -> -2.0 to 0L
    percent == null -> -1.0 to usedBytes
    else -> percent to usedBytes
}
