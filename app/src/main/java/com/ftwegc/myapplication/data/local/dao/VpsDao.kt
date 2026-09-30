package com.ftwegc.myapplication.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.ftwegc.myapplication.data.local.entity.VpsConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VpsDao {

    /** Inserts; throws when the subscription already has a config or the Komari uuid is taken. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: VpsConfigEntity)

    @Update
    suspend fun update(entity: VpsConfigEntity): Int

    /** Inserts or updates by subscription id (insert-then-update, never REPLACE). */
    @Upsert
    suspend fun upsert(entity: VpsConfigEntity)

    @Query("SELECT * FROM vps_configs WHERE subscription_id = :subscriptionId")
    suspend fun getBySubscriptionId(subscriptionId: Long): VpsConfigEntity?

    @Query("SELECT * FROM vps_configs WHERE subscription_id = :subscriptionId")
    fun observeBySubscriptionId(subscriptionId: Long): Flow<VpsConfigEntity?>

    @Query("SELECT * FROM vps_configs WHERE komari_uuid = :uuid")
    suspend fun getByKomariUuid(uuid: String): VpsConfigEntity?

    @Query("SELECT * FROM vps_configs ORDER BY subscription_id")
    suspend fun getAll(): List<VpsConfigEntity>

    /** Configs linked to a Komari node. */
    @Query("SELECT * FROM vps_configs WHERE komari_uuid IS NOT NULL ORDER BY subscription_id")
    suspend fun getLinked(): List<VpsConfigEntity>

    @Query("DELETE FROM vps_configs WHERE subscription_id = :subscriptionId")
    suspend fun deleteBySubscriptionId(subscriptionId: Long): Int

    /**
     * Stores a successful Komari reading (counting mode 1). Only touches the counter columns, so a
     * refresh running next to an edit never overwrites the user's settings.
     */
    @Query(
        "UPDATE vps_configs SET last_total_up = :totalUp, last_total_down = :totalDown, " +
            "last_uptime_sec = :uptimeSec, last_reported_at = :reportedAt, last_fetch_at = :fetchAt, " +
            "last_error = NULL WHERE subscription_id = :subscriptionId AND komari_uuid = :uuid",
    )
    suspend fun updateTrafficSample(
        subscriptionId: Long,
        uuid: String,
        totalUp: Long,
        totalDown: Long,
        uptimeSec: Long?,
        reportedAt: Long?,
        fetchAt: Long,
    ): Int

    /** Records a failed or empty fetch; the last counters stay as they were. */
    @Query(
        "UPDATE vps_configs SET last_fetch_at = :fetchAt, last_error = :error " +
            "WHERE subscription_id = :subscriptionId AND komari_uuid = :uuid",
    )
    suspend fun updateFetchError(subscriptionId: Long, uuid: String, fetchAt: Long, error: String): Int

    /** Marks whether the linked node is missing from the panel's node list. */
    @Query("UPDATE vps_configs SET komari_missing = :missing WHERE subscription_id = :subscriptionId")
    suspend fun setKomariMissing(subscriptionId: Long, missing: Boolean): Int
}
