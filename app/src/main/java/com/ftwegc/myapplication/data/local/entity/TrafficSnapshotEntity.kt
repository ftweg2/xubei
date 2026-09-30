package com.ftwegc.myapplication.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Row of `traffic_snapshots`: one raw reading per refresh together with the computed delta. */
@Entity(
    tableName = "traffic_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscription_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["subscription_id", "fetched_at"])],
)
data class TrafficSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "subscription_id") val subscriptionId: Long,
    /** When the app fetched this reading, epoch millis. */
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    /** When the agent reported it, epoch millis. */
    @ColumnInfo(name = "reported_at") val reportedAt: Long?,
    @ColumnInfo(name = "total_up") val totalUp: Long,
    @ColumnInfo(name = "total_down") val totalDown: Long,
    @ColumnInfo(name = "uptime_sec") val uptimeSec: Long?,
    @ColumnInfo(name = "delta_up") val deltaUp: Long,
    @ColumnInfo(name = "delta_down") val deltaDown: Long,
    @ColumnInfo(name = "reset_detected") val resetDetected: Boolean,
    /** [com.ftwegc.myapplication.domain.calc.DeltaNote] name, null for a plain difference. */
    @ColumnInfo(name = "note") val note: String? = null,
)
