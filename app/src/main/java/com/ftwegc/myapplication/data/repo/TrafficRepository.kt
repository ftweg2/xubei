package com.ftwegc.myapplication.data.repo

import com.ftwegc.myapplication.data.local.dao.TrafficDao
import com.ftwegc.myapplication.data.local.dao.VpsDao
import com.ftwegc.myapplication.data.local.entity.TrafficPeriodEntity
import com.ftwegc.myapplication.data.local.entity.TrafficSnapshotEntity
import com.ftwegc.myapplication.data.local.entity.VpsConfigEntity
import com.ftwegc.myapplication.data.remote.komari.KomariApi
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.remote.komari.KomariLatest
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.data.local.toDomain
import com.ftwegc.myapplication.domain.calc.CounterDeltaCalculator
import com.ftwegc.myapplication.domain.calc.DeltaNote
import com.ftwegc.myapplication.domain.calc.ResetPolicy
import com.ftwegc.myapplication.domain.calc.SnapshotPoint
import com.ftwegc.myapplication.domain.calc.TimeSplit
import com.ftwegc.myapplication.domain.calc.TrafficPeriodCalculator
import com.ftwegc.myapplication.domain.calc.TrafficTrend
import com.ftwegc.myapplication.domain.model.AppPeriodUsage
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.CounterSample
import com.ftwegc.myapplication.domain.model.TrafficPeriod
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Clock
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Outcome of one refresh run. */
data class TrafficRefreshSummary(
    /** Nodes that returned a fresh reading. */
    val updated: Int,
    /** Nodes without a recent report (agent offline); the last reading is kept. */
    val offline: Int,
    /** Nodes whose request failed; the last reading is kept and the error code stored. */
    val failed: Int,
    /** The error when every request failed for the same reason (e.g. 401 for all), else `null`. */
    val commonError: KomariError? = null,
) {
    val total: Int get() = updated + offline + failed
}

/**
 * Traffic counters of every linked VPS.
 *
 * Every successful reading is written to `vps_configs` (the newest counters, used by counting
 * mode 1, "use Komari's numbers") and, when [trafficDao] is set, kept as a snapshot in
 * `traffic_snapshots` together with its delta to the previous snapshot (for the trend chart).
 * A reading whose report time equals the previous snapshot's is not stored twice.
 *
 * Counting mode 2 ("the app accumulates"): the delta is added to the traffic period that contains
 * the report time ([TrafficPeriodCalculator], reset day and time zone of the VPS). A delta that
 * spans the start of a new period is split in proportion to time. A new period closes the
 * previous one. All writes for one node run in one database transaction. Snapshots older than
 * [SNAPSHOT_RETENTION] are removed on the way.
 *
 * Requests run concurrently, at most [maxConcurrency] at a time; one node failing never affects
 * the others.
 *
 * @param resetZone the app-wide reset-day zone (settings); a VPS's own zone wins over it.
 */
class TrafficRepository(
    private val apiProvider: suspend () -> KomariResult<KomariApi>,
    private val vpsDao: VpsDao,
    private val clock: Clock,
    private val maxConcurrency: Int = DEFAULT_CONCURRENCY,
    private val trafficDao: TrafficDao? = null,
    private val transactions: TransactionRunner = DirectTransactions,
    private val resetZone: suspend () -> ZoneId = { clock.zone },
) {

    /** Refreshes every linked VPS. Fails as a whole only when the panel is not configured / invalid. */
    suspend fun refreshAll(): KomariResult<TrafficRefreshSummary> {
        val api = when (val r = apiProvider()) {
            is KomariResult.Err -> return r
            is KomariResult.Ok -> r.value
        }
        return KomariResult.Ok(refresh(api, vpsDao.getLinked()))
    }

    /**
     * Refreshes one VPS. Returns the error of that node (the reading is still stored as failed),
     * `Ok(false)` when it is offline and `Ok(true)` for a fresh reading. A VPS that is not linked
     * gives `Ok(false)` without a request.
     */
    suspend fun refreshOne(subscriptionId: Long): KomariResult<Boolean> {
        val config = vpsDao.getBySubscriptionId(subscriptionId)?.takeIf { it.komariUuid != null }
            ?: return KomariResult.Ok(false)
        val api = when (val r = apiProvider()) {
            is KomariResult.Err -> return r
            is KomariResult.Ok -> r.value
        }
        val outcome = fetchOne(api, config)
        trafficDao?.deleteSnapshotsBefore(clock.millis() - SNAPSHOT_RETENTION.toMillis())
        return when (outcome) {
            Outcome.Updated -> KomariResult.Ok(true)
            Outcome.Offline -> KomariResult.Ok(false)
            is Outcome.Failed -> KomariResult.Err(outcome.error)
        }
    }

    private suspend fun refresh(api: KomariApi, configs: List<VpsConfigEntity>): TrafficRefreshSummary = coroutineScope {
        val permits = Semaphore(maxConcurrency.coerceAtLeast(1))
        val outcomes = configs.map { config ->
            async { permits.withPermit { fetchOne(api, config) } }
        }.awaitAll()
        trafficDao?.deleteSnapshotsBefore(clock.millis() - SNAPSHOT_RETENTION.toMillis())
        val errors = outcomes.mapNotNull { (it as? Outcome.Failed)?.error }.distinct()
        TrafficRefreshSummary(
            updated = outcomes.count { it == Outcome.Updated },
            offline = outcomes.count { it == Outcome.Offline },
            failed = outcomes.count { it is Outcome.Failed },
            commonError = errors.singleOrNull()?.takeIf { outcomes.isNotEmpty() && outcomes.all { it is Outcome.Failed } },
        )
    }

    private sealed interface Outcome {
        data object Updated : Outcome
        data object Offline : Outcome
        data class Failed(val error: KomariError) : Outcome
    }

    private suspend fun fetchOne(api: KomariApi, config: VpsConfigEntity): Outcome {
        val uuid = config.komariUuid ?: return Outcome.Offline
        val result = api.getLatest(uuid)
        val now = clock.millis()
        return when (result) {
            is KomariResult.Err -> {
                vpsDao.updateFetchError(config.subscriptionId, uuid, now, result.error.code)
                Outcome.Failed(result.error)
            }
            is KomariResult.Ok -> {
                val latest = result.value
                if (latest == null) {
                    vpsDao.updateFetchError(config.subscriptionId, uuid, now, OFFLINE)
                    Outcome.Offline
                } else {
                    val zone = zoneOf(config)
                    transactions.inTransaction { store(config.subscriptionId, uuid, latest, now, zone) }
                    Outcome.Updated
                }
            }
        }
    }

    /** Writes one reading: counters, snapshot and (mode 2) period totals. Runs in a transaction. */
    private suspend fun store(subscriptionId: Long, uuid: String, latest: KomariLatest, now: Long, zone: ZoneId) {
        val reportedAt = latest.reportedAt ?: Instant.ofEpochMilli(now)
        // Re-read inside the transaction: an edit may have changed the mode or the link meanwhile.
        val written = vpsDao.updateTrafficSample(
            subscriptionId = subscriptionId,
            uuid = uuid,
            totalUp = latest.totalUp,
            totalDown = latest.totalDown,
            uptimeSec = latest.uptimeSec,
            reportedAt = reportedAt.toEpochMilli(),
            fetchAt = now,
        )
        val dao = trafficDao ?: return
        if (written == 0) return
        val config = vpsDao.getBySubscriptionId(subscriptionId) ?: return

        val prevSnapshot = dao.getLatestSnapshot(subscriptionId)
        val prevAt = prevSnapshot?.let { Instant.ofEpochMilli(it.reportedAt ?: it.fetchedAt) }
        if (prevAt != null && !reportedAt.isAfter(prevAt)) return // same report as last time: nothing new

        val cur = CounterSample(latest.totalUp, latest.totalDown, latest.uptimeSec, reportedAt)
        val prev = prevSnapshot?.let { CounterSample(it.totalUp, it.totalDown, it.uptimeSec, prevAt!!) }
        val delta = CounterDeltaCalculator.computeDelta(prev, cur, ResetPolicy.TOLERANT)
        dao.insertSnapshot(
            TrafficSnapshotEntity(
                subscriptionId = subscriptionId,
                fetchedAt = now,
                reportedAt = reportedAt.toEpochMilli(),
                totalUp = cur.totalUp,
                totalDown = cur.totalDown,
                uptimeSec = cur.uptimeSec,
                deltaUp = delta.deltaUp,
                deltaDown = delta.deltaDown,
                resetDetected = delta.resetDetected,
                note = delta.note?.name,
            ),
        )

        if (config.countingMode != CountingMode.APP_ACCUMULATED.name) return
        val period = TrafficPeriodCalculator.periodFor(reportedAt, zone, config.resetDay)
        val current = ensurePeriod(dao, subscriptionId, period, now)
        val since = config.appCountingSince
        // The first reading after counting started is only the baseline.
        val baselineOk = prevSnapshot != null && (since == null || prevSnapshot.fetchedAt >= since)
        if (!baselineOk || prevAt == null) return

        val point = SnapshotPoint(reportedAt, delta.deltaUp, delta.deltaDown, cur.uptimeSec, delta.resetDetected)
        val from = TrafficTrend.spanStart(prevAt, point)
        val boundary = period.start.atStartOfDay(zone).toInstant()
        val beforeUp = TimeSplit.before(delta.deltaUp, from, reportedAt, boundary)
        val beforeDown = TimeSplit.before(delta.deltaDown, from, reportedAt, boundary)
        if (beforeUp > 0 || beforeDown > 0) {
            val previous = dao.getPeriodContaining(subscriptionId, period.start.toEpochDay() - 1)
            if (previous != null) {
                dao.updatePeriod(
                    previous.copy(
                        upBytes = add(previous.upBytes, beforeUp),
                        downBytes = add(previous.downBytes, beforeDown),
                        updatedAt = now,
                    ),
                )
            }
        }
        dao.updatePeriod(
            current.copy(
                upBytes = add(current.upBytes, delta.deltaUp - beforeUp),
                downBytes = add(current.downBytes, delta.deltaDown - beforeDown),
                updatedAt = now,
            ),
        )
    }

    /**
     * The row for [period], created when missing. A newer period closes the previous one at its
     * start. When the reset day changed, a row that overlaps [period] is moved onto it (its totals
     * are kept) instead of creating a second row for the same days.
     */
    private suspend fun ensurePeriod(dao: TrafficDao, subscriptionId: Long, period: TrafficPeriod, now: Long): TrafficPeriodEntity {
        val start = period.start.toEpochDay()
        val end = period.endExclusive.toEpochDay()
        dao.getPeriod(subscriptionId, start)?.let { row ->
            if (row.periodEndDay != end) {
                val fixed = row.copy(periodEndDay = end)
                dao.updatePeriod(fixed)
                return fixed
            }
            return row
        }
        val latest = dao.getLatestPeriod(subscriptionId)
        if (latest != null) {
            if (latest.periodStartDay > start && latest.periodStartDay < end) {
                // The reset day moved earlier: the latest row lies inside the new period.
                val moved = latest.copy(periodStartDay = start, periodEndDay = end)
                dao.updatePeriod(moved)
                return moved
            }
            if (latest.periodStartDay < start && latest.periodEndDay != start) {
                // Close the previous period where the new one begins.
                dao.updatePeriod(latest.copy(periodEndDay = start))
            }
        }
        val row = TrafficPeriodEntity(
            subscriptionId = subscriptionId,
            periodStartDay = start,
            periodEndDay = end,
            upBytes = 0,
            downBytes = 0,
            initialUsedBytes = 0,
            updatedAt = 0,
        )
        val id = dao.insertPeriod(row)
        return row.copy(id = id)
    }

    /**
     * Sets the usage of the current period from before the app started counting ("期初已用量").
     * Creates the current period when needed. Returns false for a VPS that does not exist.
     */
    suspend fun setInitialUsed(subscriptionId: Long, bytes: Long): Boolean {
        val dao = trafficDao ?: return false
        val config = vpsDao.getBySubscriptionId(subscriptionId) ?: return false
        val zone = zoneOf(config)
        val now = clock.millis()
        return transactions.inTransaction {
            val period = TrafficPeriodCalculator.periodFor(clock.instant(), zone, config.resetDay)
            val row = ensurePeriod(dao, subscriptionId, period, now)
            dao.updatePeriod(row.copy(initialUsedBytes = bytes.coerceAtLeast(0)))
            true
        }
    }

    /** The current period's row after a settings change (reset day / zone), creating it when needed. */
    suspend fun alignCurrentPeriod(subscriptionId: Long) {
        val dao = trafficDao ?: return
        val config = vpsDao.getBySubscriptionId(subscriptionId) ?: return
        if (config.countingMode != CountingMode.APP_ACCUMULATED.name) return
        val zone = zoneOf(config)
        transactions.inTransaction {
            ensurePeriod(dao, subscriptionId, TrafficPeriodCalculator.periodFor(clock.instant(), zone, config.resetDay), clock.millis())
        }
    }

    // ------------------------------------------------------------------ Reading back

    /** Accumulated periods of one VPS (counting mode 2), newest first. */
    fun observePeriods(subscriptionId: Long): Flow<List<AppPeriodUsage>> =
        trafficDao?.observePeriods(subscriptionId)?.map { rows -> rows.mapNotNull { it.toDomain() } } ?: flowOf(emptyList())

    /** Snapshots since [since] as trend input, oldest first. */
    fun observeTrendPoints(subscriptionId: Long, since: Instant): Flow<List<SnapshotPoint>> =
        trafficDao?.observeSnapshotsSince(subscriptionId, since.toEpochMilli())?.map { rows ->
            rows.map { row ->
                SnapshotPoint(
                    reportedAt = Instant.ofEpochMilli(row.reportedAt ?: row.fetchedAt),
                    deltaUp = row.deltaUp,
                    deltaDown = row.deltaDown,
                    uptimeSec = row.uptimeSec,
                    resetDetected = row.resetDetected,
                )
            }
        } ?: flowOf(emptyList())

    /** What the kept snapshots of one VPS noticed (reboots, resets, persistent counters). */
    fun observeNotes(subscriptionId: Long): Flow<Set<DeltaNote>> =
        trafficDao?.observeSnapshotNotes(subscriptionId)?.map { names ->
            names.mapNotNullTo(mutableSetOf()) { name -> DeltaNote.entries.firstOrNull { it.name == name } }
        } ?: flowOf(emptySet())

    /** The app-wide reset zone (settings, else the clock's zone). */
    suspend fun defaultZone(): ZoneId = resetZone()

    /** Reset-day zone of [config]: its own zone, else the app setting, else the clock's zone. */
    suspend fun zoneOf(config: VpsConfigEntity): ZoneId =
        config.resetZoneId?.let(::zoneOrNull) ?: resetZone()

    private fun zoneOrNull(id: String): ZoneId? = try {
        ZoneId.of(id)
    } catch (_: DateTimeException) {
        null
    }

    private fun add(a: Long, b: Long): Long {
        val x = a.coerceAtLeast(0)
        val y = b.coerceAtLeast(0)
        return if (x > Long.MAX_VALUE - y) Long.MAX_VALUE else x + y
    }

    /** Pass-through runner for callers without a database (unit tests of mode 1). */
    private object DirectTransactions : TransactionRunner {
        override suspend fun <R> inTransaction(block: suspend () -> R): R = block()
    }

    companion object {
        const val DEFAULT_CONCURRENCY: Int = 4

        /** `last_error` value for "the panel has no recent report of this node". */
        const val OFFLINE: String = "OFFLINE"

        /** How long snapshots are kept. */
        val SNAPSHOT_RETENTION: Duration = Duration.ofDays(90)
    }
}
