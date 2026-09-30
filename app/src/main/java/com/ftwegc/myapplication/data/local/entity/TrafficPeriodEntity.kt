package com.ftwegc.myapplication.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Row of `traffic_periods`: traffic accumulated by the app in one billing period (counting mode 2).
 * One row per (subscription_id, period_start_day).
 */
@Entity(
    tableName = "traffic_periods",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscription_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["subscription_id", "period_start_day"], unique = true)],
)
data class TrafficPeriodEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "subscription_id") val subscriptionId: Long,
    /** Period start (inclusive), epoch day. */
    @ColumnInfo(name = "period_start_day") val periodStartDay: Long,
    /** Period end (exclusive), epoch day. */
    @ColumnInfo(name = "period_end_day") val periodEndDay: Long,
    /** Upload accumulated in this period, bytes. */
    @ColumnInfo(name = "up_bytes") val upBytes: Long,
    /** Download accumulated in this period, bytes. */
    @ColumnInfo(name = "down_bytes") val downBytes: Long,
    /** Usage consumed before the app started counting (entered by the user), bytes. */
    @ColumnInfo(name = "initial_used_bytes") val initialUsedBytes: Long = 0,
    /** Epoch millis. */
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
