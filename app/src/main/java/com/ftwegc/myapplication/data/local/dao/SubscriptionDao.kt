package com.ftwegc.myapplication.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ftwegc.myapplication.data.local.entity.SubscriptionEntity
import com.ftwegc.myapplication.data.local.entity.SubscriptionWithVpsRow
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    /** Every subscription (archived included) with its VPS config, oldest first. */
    @Transaction
    @Query("SELECT * FROM subscriptions ORDER BY id")
    fun observeAll(): Flow<List<SubscriptionWithVpsRow>>

    /** One subscription with its VPS config; emits `null` once it is deleted. */
    @Transaction
    @Query("SELECT * FROM subscriptions WHERE id = :id")
    fun observeById(id: Long): Flow<SubscriptionWithVpsRow?>

    @Transaction
    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: Long): SubscriptionWithVpsRow?

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getEntity(id: Long): SubscriptionEntity?

    /**
     * Inserts ([SubscriptionEntity.id] == 0) or updates by id. Returns the new row id on insert
     * and -1 on update. Implemented as insert-then-update (not REPLACE), so child rows survive.
     */
    @Upsert
    suspend fun upsert(entity: SubscriptionEntity): Long

    /** Deletes a subscription; VPS config, traffic data and reminder log cascade. */
    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("UPDATE subscriptions SET archived = :archived, updated_at = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long): Int

    @Query(
        "UPDATE subscriptions SET next_due_day = :nextDueDay, anchor_day = :anchorDay, " +
            "updated_at = :updatedAt WHERE id = :id",
    )
    suspend fun updateDue(id: Long, nextDueDay: Long?, anchorDay: Int?, updatedAt: Long): Int

    /**
     * Active (not archived, not permanent) subscriptions due in `[startDay, endDay]` (epoch days,
     * inclusive), earliest first.
     */
    @Query(
        "SELECT * FROM subscriptions WHERE archived = 0 AND is_permanent = 0 " +
            "AND next_due_day IS NOT NULL AND next_due_day BETWEEN :startDay AND :endDay " +
            "ORDER BY next_due_day, id",
    )
    suspend fun getDueBetween(startDay: Long, endDay: Long): List<SubscriptionEntity>

    /** Active auto-renewing, renewable subscriptions whose due date is before [todayDay]. */
    @Query(
        "SELECT * FROM subscriptions WHERE archived = 0 AND auto_renew = 1 AND is_permanent = 0 " +
            "AND cycle_unit != 'ONCE' AND next_due_day IS NOT NULL AND next_due_day < :todayDay " +
            "ORDER BY next_due_day, id",
    )
    suspend fun getAutoRenewExpired(todayDay: Long): List<SubscriptionEntity>
}
