package com.ftwegc.myapplication.domain.model

import java.time.Instant

/**
 * Network-independent view of one Komari node (`GET /api/nodes`), input to the import merge.
 *
 * @property price price per cycle in major units; `-1` means free, `0` or any other negative value means
 *   "not set" (Komari hides the price tag for 0).
 * @property billingCycleDays Komari `billing_cycle` in days; `-1` one-time, `0` not set.
 * @property expiredAt expiry instant; `null` or year `<= 1970` (Go zero time 0001-01-01) means none,
 *   more than 100 years after "now" means long-term valid.
 * @property trafficLimitBytes traffic limit in bytes; `<= 0` means no limit.
 * @property trafficLimitType raw Komari `traffic_limit_type` (parsed leniently).
 */
data class KomariNodeSnapshot(
    val uuid: String,
    val name: String,
    val group: String = "",
    val price: Double,
    val currency: String,
    val billingCycleDays: Int,
    val autoRenewal: Boolean,
    val expiredAt: Instant?,
    val trafficLimitBytes: Long,
    val trafficLimitType: String,
)
