package com.ftwegc.myapplication.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Row of `subscriptions`.
 *
 * Dates are stored as `LocalDate.toEpochDay()`, timestamps as epoch milliseconds, money in minor
 * units (fen / cents). Enums are stored by name.
 */
@Entity(
    tableName = "subscriptions",
    indices = [
        Index(value = ["next_due_day"]),
        Index(value = ["archived"]),
    ],
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    /** Category enum name. */
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "price_minor") val priceMinor: Long,
    @ColumnInfo(name = "is_free") val isFree: Boolean,
    @ColumnInfo(name = "currency") val currency: String,
    /** CycleUnit enum name. */
    @ColumnInfo(name = "cycle_unit") val cycleUnit: String,
    @ColumnInfo(name = "cycle_count") val cycleCount: Int,
    /** False while the billing cycle was never filled in (a Komari import without one). */
    @ColumnInfo(name = "cycle_known") val cycleKnown: Boolean = true,
    /** Next due date as epoch day, null when unknown. */
    @ColumnInfo(name = "next_due_day") val nextDueDay: Long?,
    @ColumnInfo(name = "anchor_day") val anchorDay: Int?,
    @ColumnInfo(name = "is_permanent") val isPermanent: Boolean,
    @ColumnInfo(name = "auto_renew") val autoRenew: Boolean,
    @ColumnInfo(name = "note") val note: String,
    @ColumnInfo(name = "archived") val archived: Boolean,
    /** Own reminder days ("7,3,1,0"; "" = never), null = use the app default. */
    @ColumnInfo(name = "reminder_days") val reminderDays: String? = null,
    /** Creation time, epoch millis. */
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Last modification time, epoch millis. */
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Icon chosen by the user (a brand id, or "letter" for the letter mark); null = match by name. */
    @ColumnInfo(name = "icon_key") val iconKey: String? = null,
    /** Base color chosen by the user (ARGB); null = the brand's color or one derived from the name. */
    @ColumnInfo(name = "icon_color") val iconColor: Int? = null,
)
