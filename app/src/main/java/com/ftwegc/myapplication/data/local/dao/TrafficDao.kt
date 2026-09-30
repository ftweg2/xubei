package com.ftwegc.myapplication.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.ftwegc.myapplication.data.local.entity.TrafficPeriodEntity
import com.ftwegc.myapplication.data.local.entity.TrafficSnapshotEntity
import kotlinx.coroutines.flow.Flow

/** Traffic periods (counting mode 2) and the raw snapshots of every refresh. */
@Dao
interface TrafficDao {

    // ------------------------------------------------------------------ Periods

    /** Inserts; throws when the (subscription, period start) pair already exists. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPeriod(period: TrafficPeriodEntity): Long

    @Upsert
    suspend fun upsertPeriod(period: TrafficPeriodEntity): Long

    @Update
    suspend fun updatePeriod(period: TrafficPeriodEntity): Int

    @Query("SELECT * FROM traffic_periods WHERE subscription_id = :subscriptionId AND period_start_day = :startDay")
    suspend fun getPeriod(subscriptionId: Long, startDay: Long): TrafficPeriodEntity?

    /** The period whose `[start, end)` contains [day] (epoch day). */
    @Query(
        "SELECT * FROM traffic_periods WHERE subscription_id = :subscriptionId " +
            "AND period_start_day <= :day AND period_end_day > :day ORDER BY period_start_day DESC LIMIT 1",
    )
    suspend fun getPeriodContaining(subscriptionId: Long, day: Long): TrafficPeriodEntity?

    @Query(
        "SELECT * FROM traffic_periods WHERE subscription_id = :subscriptionId " +
            "ORDER BY period_start_day DESC LIMIT 1",
    )
    suspend fun getLatestPeriod(subscriptionId: Long): TrafficPeriodEntity?

    @Query(
        "SELECT * FROM traffic_periods WHERE subscription_id = :subscriptionId " +
            "ORDER BY period_start_day DESC LIMIT 1",
    )
    fun observeLatestPeriod(subscriptionId: Long): Flow<TrafficPeriodEntity?>

    /** Every period of one subscription, newest first. */
    @Query("SELECT * FROM traffic_periods WHERE subscription_id = :subscriptionId ORDER BY period_start_day DESC")
    fun observePeriods(subscriptionId: Long): Flow<List<TrafficPeriodEntity>>

    /** The newest period of every subscription. */
    @Query(
        "SELECT * FROM traffic_periods AS p WHERE p.period_start_day = " +
            "(SELECT MAX(q.period_start_day) FROM traffic_periods AS q WHERE q.subscription_id = p.subscription_id)",
    )
    fun observeLatestPeriods(): Flow<List<TrafficPeriodEntity>>

    // ------------------------------------------------------------------ Snapshots

    @Insert
    suspend fun insertSnapshot(snapshot: TrafficSnapshotEntity): Long

    @Query(
        "SELECT * FROM traffic_snapshots WHERE subscription_id = :subscriptionId " +
            "ORDER BY fetched_at DESC, id DESC LIMIT 1",
    )
    suspend fun getLatestSnapshot(subscriptionId: Long): TrafficSnapshotEntity?

    @Query(
        "SELECT * FROM traffic_snapshots WHERE subscription_id = :subscriptionId " +
            "AND fetched_at >= :sinceMillis ORDER BY fetched_at, id",
    )
    suspend fun getSnapshotsSince(subscriptionId: Long, sinceMillis: Long): List<TrafficSnapshotEntity>

    @Query(
        "SELECT * FROM traffic_snapshots WHERE subscription_id = :subscriptionId " +
            "AND fetched_at >= :sinceMillis ORDER BY fetched_at, id",
    )
    fun observeSnapshotsSince(subscriptionId: Long, sinceMillis: Long): Flow<List<TrafficSnapshotEntity>>

    /** Distinct [TrafficSnapshotEntity.note] values of one subscription's kept snapshots. */
    @Query("SELECT DISTINCT note FROM traffic_snapshots WHERE subscription_id = :subscriptionId AND note IS NOT NULL")
    fun observeSnapshotNotes(subscriptionId: Long): Flow<List<String>>

    /** Retention cleanup; returns the number of rows removed. */
    @Query("DELETE FROM traffic_snapshots WHERE fetched_at < :cutoffMillis")
    suspend fun deleteSnapshotsBefore(cutoffMillis: Long): Int

    /** Drops one subscription's snapshots (its Komari node changed, old readings are meaningless). */
    @Query("DELETE FROM traffic_snapshots WHERE subscription_id = :subscriptionId")
    suspend fun deleteSnapshotsFor(subscriptionId: Long): Int
}
