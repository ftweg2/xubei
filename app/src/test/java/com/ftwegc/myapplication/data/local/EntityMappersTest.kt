package com.ftwegc.myapplication.data.local

import com.ftwegc.myapplication.data.local.entity.SubscriptionEntity
import com.ftwegc.myapplication.data.local.entity.SubscriptionWithVpsRow
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.LockedField
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.VpsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EntityMappersTest {

    private val full = Subscription(
        id = 7,
        name = "Claude Pro",
        category = Category.AI,
        priceMinor = 14_800,
        isFree = false,
        currency = "CNY",
        cycle = BillingCycle.months(3),
        nextDue = LocalDate.of(2027, 2, 28),
        anchorDay = 31,
        isPermanent = false,
        autoRenew = true,
        note = "团队账号",
        archived = true,
    )

    private fun entity(
        category: String = "VPS",
        cycleUnit: String = "MONTH",
        cycleCount: Int = 1,
        priceMinor: Long = 100,
        anchorDay: Int? = 5,
    ) = SubscriptionEntity(
        id = 1, name = "x", category = category, priceMinor = priceMinor, isFree = false, currency = "USD",
        cycleUnit = cycleUnit, cycleCount = cycleCount, nextDueDay = null, anchorDay = anchorDay,
        isPermanent = false, autoRenew = false, note = "", archived = false, createdAt = 1, updatedAt = 2,
    )

    // ------------------------------------------------------------ subscription

    @Test
    fun subscription_roundTrip_isExact() {
        val e = full.toEntity(createdAt = 10, updatedAt = 20)
        assertEquals(full, e.toDomain())
        assertEquals(10L, e.createdAt)
        assertEquals(20L, e.updatedAt)
    }

    @Test
    fun subscription_toEntity_storesPrimitives() {
        val e = full.toEntity(createdAt = 10, updatedAt = 20)
        assertEquals("AI", e.category)
        assertEquals("MONTH", e.cycleUnit)
        assertEquals(3, e.cycleCount)
        assertEquals(LocalDate.of(2027, 2, 28).toEpochDay(), e.nextDueDay)
        assertEquals(14_800L, e.priceMinor)
    }

    @Test
    fun subscription_roundTrip_allCyclesAndNullDates() {
        val cycles = listOf(BillingCycle.MONTHLY, BillingCycle.ANNUAL, BillingCycle.days(45), BillingCycle.ONE_TIME)
        for (cycle in cycles) {
            val sub = full.copy(cycle = cycle, nextDue = null, anchorDay = null, isPermanent = true, isFree = true, priceMinor = 0)
            assertEquals(sub, sub.toEntity(0, 0).toDomain())
        }
    }

    @Test
    fun subscription_read_onceWithBadCount_isNormalizedByBillingCycleOf() {
        assertEquals(BillingCycle.ONE_TIME, entity(cycleUnit = "ONCE", cycleCount = 7).toDomain().cycle)
    }

    @Test
    fun subscription_read_lenientForCorruptRows() {
        val s = entity(category = "GAME", cycleUnit = "WEEK", cycleCount = 0, priceMinor = -5, anchorDay = 40).toDomain()
        assertEquals(Category.OTHER, s.category)
        assertEquals(BillingCycle(CycleUnit.MONTH, 1), s.cycle)
        assertEquals(0L, s.priceMinor)
        assertNull(s.anchorDay)
    }

    // ------------------------------------------------------------ vps config

    private val vps = VpsConfig(
        subscriptionId = 7,
        komariUuid = "uuid-1",
        komariName = "tokyo-01",
        komariGroup = "JP",
        komariMissing = true,
        lockedFields = LockedField.maskOf(LockedField.NAME, LockedField.TRAFFIC_LIMIT),
        trafficLimitBytes = 1L shl 40,
        trafficLimitType = TrafficLimitType.SUM,
        countingMode = CountingMode.APP_ACCUMULATED,
        resetDay = 31,
        resetZoneId = "Asia/Tokyo",
        alertPercent = 90,
        lastTotalUp = 123,
        lastTotalDown = 456,
        lastUptimeSec = 789,
        lastReportedAt = Instant.ofEpochMilli(1_700_000_000_123),
        lastFetchAt = Instant.ofEpochMilli(1_700_000_001_000),
        lastError = "timeout",
    )

    @Test
    fun vps_roundTrip_isExact() {
        assertEquals(vps, vps.toEntity().toDomain())
    }

    @Test
    fun vps_toEntity_canReassignOwner() {
        assertEquals(42L, vps.toEntity(subscriptionId = 42).subscriptionId)
    }

    @Test
    fun vps_defaults_roundTrip() {
        val d = VpsConfig(subscriptionId = 3)
        assertEquals(d, d.toEntity().toDomain())
    }

    @Test
    fun vps_lockedFieldBits_arePersistedUnchanged() {
        val mask = LockedField.ALL
        assertEquals(mask, vps.copy(lockedFields = mask).toEntity().lockedFields)
        assertEquals(0xFF, LockedField.ALL)
    }

    @Test
    fun vps_read_lenientForCorruptRows() {
        val e = vps.toEntity().copy(
            trafficLimitType = " sum ",
            countingMode = "SOMETHING",
            resetDay = 0,
            alertPercent = 400,
            trafficLimitBytes = -1,
        )
        val d = e.toDomain()
        assertEquals(TrafficLimitType.SUM, d.trafficLimitType)
        assertEquals(CountingMode.KOMARI_CUMULATIVE, d.countingMode)
        assertEquals(1, d.resetDay)
        assertEquals(100, d.alertPercent)
        assertEquals(0L, d.trafficLimitBytes)
        assertEquals(TrafficLimitType.MAX, e.copy(trafficLimitType = "weird").toDomain().trafficLimitType)
    }

    @Test
    fun vps_lastSample_requiresAllCounterFields() {
        assertEquals(123L, vps.lastSample!!.totalUp)
        assertNull(vps.copy(lastReportedAt = null).lastSample)
        assertNull(vps.copy(lastTotalDown = null).lastSample)
    }

    @Test
    fun row_mapsBothParts() {
        val row = SubscriptionWithVpsRow(full.toEntity(0, 0), vps.toEntity())
        val d = row.toDomain()
        assertEquals(full, d.subscription)
        assertEquals(vps, d.vps)
        assertNull(SubscriptionWithVpsRow(full.toEntity(0, 0), null).toDomain().vps)
    }

    @Test
    fun enumOrDefault_exactNamesOnly() {
        assertEquals(Category.AI, enumOrDefault("AI", Category.OTHER))
        assertEquals(Category.OTHER, enumOrDefault("ai", Category.OTHER))
        assertEquals(Category.OTHER, enumOrDefault(null, Category.OTHER))
    }
}
