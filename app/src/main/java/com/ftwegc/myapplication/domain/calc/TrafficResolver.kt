package com.ftwegc.myapplication.domain.calc

import com.ftwegc.myapplication.domain.model.AppPeriodUsage
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.TrafficPeriod
import com.ftwegc.myapplication.domain.model.TrafficUsage
import com.ftwegc.myapplication.domain.model.VpsConfig
import java.time.LocalDate

/**
 * The traffic of one VPS as currently known.
 *
 * @property usage `null` while there is nothing to show (no reading yet; in mode 2 no baseline yet).
 * @property period the billing period [today] falls in (per the reset day), `null` without a date.
 * @property waitingForNewPeriod mode 2: the newest accumulated period is older than [period]; the
 *   next refresh starts the new one (usage shows 0 until then).
 */
data class ResolvedTraffic(
    val usage: TrafficUsage?,
    val period: TrafficPeriod?,
    val waitingForNewPeriod: Boolean = false,
)

/** Picks the numbers per counting mode. Shared by the UI and the reminders. */
object TrafficResolver {

    /**
     * - Mode 1 ([CountingMode.KOMARI_CUMULATIVE]): the stored Komari counters, used amount per
     *   the limit type ([TrafficCalculator.usage]).
     * - Mode 2 ([CountingMode.APP_ACCUMULATED]): initial usage plus the accumulated deltas of the
     *   current period ([TrafficCalculator.appUsage]); a stale period counts as an empty new one.
     *
     * [today] is the local date in the reset zone; without it the stored period is taken as current.
     */
    fun resolve(config: VpsConfig, appPeriod: AppPeriodUsage?, today: LocalDate?): ResolvedTraffic {
        val period = today?.let { TrafficPeriodCalculator.periodFor(it, config.resetDay) }
        if (!config.isLinked && config.countingMode == CountingMode.KOMARI_CUMULATIVE) return ResolvedTraffic(null, period)
        return when (config.countingMode) {
            CountingMode.KOMARI_CUMULATIVE -> {
                val sample = config.lastSample
                val usage = sample?.let {
                    TrafficCalculator.usage(
                        up = it.totalUp,
                        down = it.totalDown,
                        limitBytes = config.trafficLimitBytes,
                        type = config.trafficLimitType,
                        updatedAt = it.reportedAt,
                    )
                }
                ResolvedTraffic(usage, period)
            }
            CountingMode.APP_ACCUMULATED -> {
                val current = period ?: appPeriod?.period
                when {
                    appPeriod == null || current == null -> ResolvedTraffic(null, current)
                    appPeriod.period.start == current.start || (today != null && today in appPeriod.period) -> ResolvedTraffic(
                        TrafficCalculator.appUsage(
                            up = appPeriod.upBytes,
                            down = appPeriod.downBytes,
                            initialUsed = appPeriod.initialUsedBytes,
                            limitBytes = config.trafficLimitBytes,
                            type = config.trafficLimitType,
                            period = appPeriod.period,
                            updatedAt = appPeriod.updatedAt,
                        ),
                        appPeriod.period,
                    )
                    appPeriod.period.start.isBefore(current.start) -> ResolvedTraffic(
                        TrafficCalculator.appUsage(0, 0, 0, config.trafficLimitBytes, config.trafficLimitType, current, null),
                        current,
                        waitingForNewPeriod = true,
                    )
                    // Stored period lies in the future (device clock moved back): show it as is.
                    else -> ResolvedTraffic(
                        TrafficCalculator.appUsage(
                            appPeriod.upBytes, appPeriod.downBytes, appPeriod.initialUsedBytes,
                            config.trafficLimitBytes, config.trafficLimitType, appPeriod.period, appPeriod.updatedAt,
                        ),
                        appPeriod.period,
                    )
                }
            }
        }
    }
}
