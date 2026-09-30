package com.ftwegc.myapplication.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Row of `reminder_log`: a sent notification, de-duplicated by (subscription_id, key). */
@Entity(
    tableName = "reminder_log",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscription_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["subscription_id", "key"], unique = true)],
)
data class ReminderLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "subscription_id") val subscriptionId: Long,
    /** Reminder identity, for example "due:2026-10-01:d3" or "traffic:2026-10-01:80". */
    @ColumnInfo(name = "key") val key: String,
    /** Epoch millis. */
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
