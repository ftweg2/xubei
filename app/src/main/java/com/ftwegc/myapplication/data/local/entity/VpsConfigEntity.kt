package com.ftwegc.myapplication.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Row of `vps_configs`, 1:1 with a subscription and deleted with it.
 * `komari_uuid` is unique; SQLite allows any number of NULLs in a unique index.
 */
@Entity(
    tableName = "vps_configs",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscription_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["komari_uuid"], unique = true)],
)
data class VpsConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "subscription_id") val subscriptionId: Long,
    @ColumnInfo(name = "komari_uuid") val komariUuid: String?,
    @ColumnInfo(name = "komari_name") val komariName: String?,
    @ColumnInfo(name = "komari_group") val komariGroup: String?,
    @ColumnInfo(name = "komari_missing") val komariMissing: Boolean,
    /** LockedField bit mask. */
    @ColumnInfo(name = "locked_fields") val lockedFields: Int,
    /** Bytes; 0 means no limit. */
    @ColumnInfo(name = "traffic_limit_bytes") val trafficLimitBytes: Long,
    /** TrafficLimitType enum name. */
    @ColumnInfo(name = "traffic_limit_type") val trafficLimitType: String,
    /** CountingMode enum name. */
    @ColumnInfo(name = "counting_mode") val countingMode: String,
    @ColumnInfo(name = "reset_day") val resetDay: Int,
    @ColumnInfo(name = "reset_zone_id") val resetZoneId: String?,
    @ColumnInfo(name = "alert_percent") val alertPercent: Int,
    @ColumnInfo(name = "last_total_up") val lastTotalUp: Long?,
    @ColumnInfo(name = "last_total_down") val lastTotalDown: Long?,
    @ColumnInfo(name = "last_uptime_sec") val lastUptimeSec: Long?,
    /** Epoch millis. */
    @ColumnInfo(name = "last_reported_at") val lastReportedAt: Long?,
    /** Epoch millis. */
    @ColumnInfo(name = "last_fetch_at") val lastFetchAt: Long?,
    @ColumnInfo(name = "last_error") val lastError: String?,
    /**
     * Counting mode 2: when the app started accumulating (epoch millis). Snapshots fetched before
     * this instant are never used as the baseline of a delta. Null outside mode 2.
     */
    @ColumnInfo(name = "app_counting_since") val appCountingSince: Long? = null,
    /** The user says this node's agent runs with `--month-rotate` (monthly counters). */
    @ColumnInfo(name = "agent_month_rotate") val agentMonthRotate: Boolean = false,
    /** Komari node `region`: a flag emoji such as "🇩🇪"; updated by import and sync. */
    @ColumnInfo(name = "komari_region") val komariRegion: String? = null,
)
