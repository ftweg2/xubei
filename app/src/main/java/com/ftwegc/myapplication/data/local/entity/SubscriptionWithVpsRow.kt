package com.ftwegc.myapplication.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/** A subscription row with its optional VPS config row. */
data class SubscriptionWithVpsRow(
    @Embedded val subscription: SubscriptionEntity,
    @Relation(parentColumn = "id", entityColumn = "subscription_id")
    val vps: VpsConfigEntity?,
)
