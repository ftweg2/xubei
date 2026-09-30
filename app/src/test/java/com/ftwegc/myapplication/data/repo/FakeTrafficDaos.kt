package com.ftwegc.myapplication.data.repo

import com.ftwegc.myapplication.data.local.dao.ReminderLogDao
import com.ftwegc.myapplication.data.local.dao.TrafficDao
import com.ftwegc.myapplication.data.local.entity.ReminderLogEntity
import com.ftwegc.myapplication.data.local.entity.TrafficPeriodEntity
import com.ftwegc.myapplication.data.local.entity.TrafficSnapshotEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A clock the test moves forward. */
class MutableClock(var now: Instant, private val zoneId: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = MutableClockView(this, zone)
    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    private class MutableClockView(private val base: MutableClock, private val zoneId: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = MutableClockView(base, zone)
        override fun instant(): Instant = base.instant()
    }
}

/** In-memory `traffic_periods` / `traffic_snapshots` with the unique (subscription, start) rule. */
class FakeTrafficDao : TrafficDao {
    val periods = MutableStateFlow<List<TrafficPeriodEntity>>(emptyList())
    val snapshots = MutableStateFlow<List<TrafficSnapshotEntity>>(emptyList())
    private var nextPeriodId = 1L
    private var nextSnapshotId = 1L

    override suspend fun insertPeriod(period: TrafficPeriodEntity): Long {
        check(periods.value.none { it.subscriptionId == period.subscriptionId && it.periodStartDay == period.periodStartDay }) {
            "UNIQUE constraint failed: traffic_periods"
        }
        val id = nextPeriodId++
        periods.update { it + period.copy(id = id) }
        return id
    }

    override suspend fun upsertPeriod(period: TrafficPeriodEntity): Long {
        if (period.id != 0L && periods.value.any { it.id == period.id }) {
            updatePeriod(period)
            return -1
        }
        return insertPeriod(period)
    }

    override suspend fun updatePeriod(period: TrafficPeriodEntity): Int {
        if (periods.value.none { it.id == period.id }) return 0
        check(periods.value.none { it.id != period.id && it.subscriptionId == period.subscriptionId && it.periodStartDay == period.periodStartDay }) {
            "UNIQUE constraint failed: traffic_periods"
        }
        periods.update { list -> list.map { if (it.id == period.id) period else it } }
        return 1
    }

    override suspend fun getPeriod(subscriptionId: Long, startDay: Long): TrafficPeriodEntity? =
        periods.value.firstOrNull { it.subscriptionId == subscriptionId && it.periodStartDay == startDay }

    override suspend fun getPeriodContaining(subscriptionId: Long, day: Long): TrafficPeriodEntity? =
        periods.value.filter { it.subscriptionId == subscriptionId && it.periodStartDay <= day && it.periodEndDay > day }
            .maxByOrNull { it.periodStartDay }

    override suspend fun getLatestPeriod(subscriptionId: Long): TrafficPeriodEntity? =
        periods.value.filter { it.subscriptionId == subscriptionId }.maxByOrNull { it.periodStartDay }

    override fun observeLatestPeriod(subscriptionId: Long): Flow<TrafficPeriodEntity?> =
        periods.map { list -> list.filter { it.subscriptionId == subscriptionId }.maxByOrNull { it.periodStartDay } }

    override fun observePeriods(subscriptionId: Long): Flow<List<TrafficPeriodEntity>> =
        periods.map { list -> list.filter { it.subscriptionId == subscriptionId }.sortedByDescending { it.periodStartDay } }

    override fun observeLatestPeriods(): Flow<List<TrafficPeriodEntity>> =
        periods.map { list -> list.groupBy { it.subscriptionId }.values.map { rows -> rows.maxBy { it.periodStartDay } } }

    override suspend fun insertSnapshot(snapshot: TrafficSnapshotEntity): Long {
        val id = nextSnapshotId++
        snapshots.update { it + snapshot.copy(id = id) }
        return id
    }

    override suspend fun getLatestSnapshot(subscriptionId: Long): TrafficSnapshotEntity? =
        snapshots.value.filter { it.subscriptionId == subscriptionId }
            .maxWithOrNull(compareBy<TrafficSnapshotEntity> { it.fetchedAt }.thenBy { it.id })

    override suspend fun getSnapshotsSince(subscriptionId: Long, sinceMillis: Long): List<TrafficSnapshotEntity> =
        snapshots.value.filter { it.subscriptionId == subscriptionId && it.fetchedAt >= sinceMillis }
            .sortedWith(compareBy({ it.fetchedAt }, { it.id }))

    override fun observeSnapshotsSince(subscriptionId: Long, sinceMillis: Long): Flow<List<TrafficSnapshotEntity>> =
        snapshots.map { list ->
            list.filter { it.subscriptionId == subscriptionId && it.fetchedAt >= sinceMillis }.sortedWith(compareBy({ it.fetchedAt }, { it.id }))
        }

    override fun observeSnapshotNotes(subscriptionId: Long): Flow<List<String>> =
        snapshots.map { list -> list.filter { it.subscriptionId == subscriptionId }.mapNotNull { it.note }.distinct() }

    override suspend fun deleteSnapshotsBefore(cutoffMillis: Long): Int {
        val before = snapshots.value.size
        snapshots.update { list -> list.filter { it.fetchedAt >= cutoffMillis } }
        return before - snapshots.value.size
    }

    override suspend fun deleteSnapshotsFor(subscriptionId: Long): Int {
        val before = snapshots.value.size
        snapshots.update { list -> list.filter { it.subscriptionId != subscriptionId } }
        return before - snapshots.value.size
    }

    fun periodsOf(subscriptionId: Long): List<TrafficPeriodEntity> =
        periods.value.filter { it.subscriptionId == subscriptionId }.sortedBy { it.periodStartDay }

    fun snapshotsOf(subscriptionId: Long): List<TrafficSnapshotEntity> =
        snapshots.value.filter { it.subscriptionId == subscriptionId }.sortedBy { it.id }
}

/** In-memory `reminder_log` with the unique (subscription, key) rule. */
class FakeReminderLogDao : ReminderLogDao {
    val rows = mutableListOf<ReminderLogEntity>()

    override suspend fun insertIfAbsent(entry: ReminderLogEntity): Long {
        if (rows.any { it.subscriptionId == entry.subscriptionId && it.key == entry.key }) return -1
        val id = rows.size + 1L
        rows += entry.copy(id = id)
        return id
    }

    override suspend fun exists(subscriptionId: Long, key: String): Boolean =
        rows.any { it.subscriptionId == subscriptionId && it.key == key }

    override suspend fun getForSubscription(subscriptionId: Long): List<ReminderLogEntity> =
        rows.filter { it.subscriptionId == subscriptionId }.sortedBy { it.createdAt }

    override suspend fun deleteBefore(cutoffMillis: Long): Int {
        val before = rows.size
        rows.removeAll { it.createdAt < cutoffMillis }
        return before - rows.size
    }
}
