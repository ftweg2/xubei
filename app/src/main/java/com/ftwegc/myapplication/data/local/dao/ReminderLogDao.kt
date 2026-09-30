package com.ftwegc.myapplication.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ftwegc.myapplication.data.local.entity.ReminderLogEntity

/** Notification de-duplication log. Business logic arrives in a later stage. */
@Dao
interface ReminderLogDao {

    /** Inserts the entry; returns -1 when the same (subscription, key) was already logged. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entry: ReminderLogEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM reminder_log WHERE subscription_id = :subscriptionId AND `key` = :key)")
    suspend fun exists(subscriptionId: Long, key: String): Boolean

    @Query("SELECT * FROM reminder_log WHERE subscription_id = :subscriptionId ORDER BY created_at")
    suspend fun getForSubscription(subscriptionId: Long): List<ReminderLogEntity>

    @Query("DELETE FROM reminder_log WHERE created_at < :cutoffMillis")
    suspend fun deleteBefore(cutoffMillis: Long): Int
}
