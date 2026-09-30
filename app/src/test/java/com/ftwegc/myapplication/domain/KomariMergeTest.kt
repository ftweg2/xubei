package com.ftwegc.myapplication.domain

import com.ftwegc.myapplication.domain.calc.FieldChange
import com.ftwegc.myapplication.domain.calc.KomariMerge
import com.ftwegc.myapplication.domain.calc.KomariMergeResult
import com.ftwegc.myapplication.domain.calc.MergeDue
import com.ftwegc.myapplication.domain.calc.MergePrice
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.KomariNodeSnapshot
import com.ftwegc.myapplication.domain.model.LockedField
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.VpsTrafficFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class KomariMergeTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val tib = 1L shl 40
    private val gib500 = 500L shl 30

    private fun d(s: String) = LocalDate.parse(s)

    /** "Now" for every merge: 2026-09-28 12:00 in Shanghai. */
    private val now = Instant.parse("2026-09-28T04:00:00Z")

    /** 2026-10-14T16:30Z is 2026-10-15 00:30 in Shanghai. */
    private val node = KomariNodeSnapshot(
        uuid = "node-1",
        name = " HK-1 ",
        group = "asia",
        price = 12.5,
        currency = "$",
        billingCycleDays = 30,
        autoRenewal = true,
        expiredAt = Instant.parse("2026-10-14T16:30:00Z"),
        trafficLimitBytes = tib,
        trafficLimitType = "sum",
    )

    private val existing = Subscription(
        id = 42,
        name = "Old name",
        category = Category.OTHER,
        priceMinor = 1_000,
        isFree = false,
        currency = "¥",
        cycle = BillingCycle.QUARTERLY,
        nextDue = d("2026-09-01"),
        anchorDay = 1,
        isPermanent = false,
        autoRenew = false,
        note = "keep me",
        archived = false,
    )

    private val existingTraffic = VpsTrafficFields(gib500, TrafficLimitType.MAX)

    /** Expected result of merging [node] into [existing] with nothing locked. */
    private val fullyMerged = existing.copy(
        name = "HK-1",
        priceMinor = 1_250,
        isFree = false,
        currency = "$",
        cycle = BillingCycle.MONTHLY,
        nextDue = d("2026-10-15"),
        anchorDay = 15,
        isPermanent = false,
        autoRenew = true,
    )
    private val fullyMergedTraffic = VpsTrafficFields(tib, TrafficLimitType.SUM)
    private val fullChanges: List<FieldChange> = listOf(
        FieldChange.Name("Old name", "HK-1"),
        FieldChange.Price(MergePrice(1_000, false), MergePrice(1_250, false)),
        FieldChange.Currency("¥", "$"),
        FieldChange.Cycle(BillingCycle.QUARTERLY, BillingCycle.MONTHLY),
        FieldChange.DueDate(MergeDue(d("2026-09-01"), false), MergeDue(d("2026-10-15"), false)),
        FieldChange.AutoRenew(false, true),
        FieldChange.TrafficLimit(gib500, tib),
        FieldChange.LimitType(TrafficLimitType.MAX, TrafficLimitType.SUM),
    )

    private fun merge(
        n: KomariNodeSnapshot = node,
        sub: Subscription? = existing,
        traffic: VpsTrafficFields? = existingTraffic,
        locked: Int = LockedField.NONE,
        z: ZoneId = zone,
    ): KomariMergeResult = KomariMerge.merge(sub, traffic, n, locked, z, now)

    // ---- create ----

    @Test
    fun create_buildsVpsSubscriptionFromNode() {
        val result = merge(sub = null, traffic = null)

        assertTrue(result.isNew)
        assertEquals(
            Subscription(
                id = 0,
                name = "HK-1",
                category = Category.VPS,
                priceMinor = 1_250,
                isFree = false,
                currency = "$",
                cycle = BillingCycle.MONTHLY,
                nextDue = d("2026-10-15"),
                anchorDay = 15,
                isPermanent = false,
                autoRenew = true,
                note = "",
                archived = false,
            ),
            result.subscription,
        )
        assertEquals(VpsTrafficFields(tib, TrafficLimitType.SUM), result.traffic)
        assertEquals(
            listOf(
                FieldChange.Name(null, "HK-1"),
                FieldChange.Price(null, MergePrice(1_250, false)),
                FieldChange.Currency(null, "$"),
                FieldChange.Cycle(null, BillingCycle.MONTHLY),
                FieldChange.DueDate(null, MergeDue(d("2026-10-15"), false)),
                FieldChange.AutoRenew(null, true),
                FieldChange.TrafficLimit(null, tib),
                FieldChange.LimitType(null, TrafficLimitType.SUM),
            ),
            result.changes,
        )
    }

    @Test
    fun create_ignoresLocks() {
        assertEquals(merge(sub = null, traffic = null), merge(sub = null, traffic = null, locked = LockedField.ALL))
    }

    @Test
    fun create_withMissingNodeValues_usesDefaults() {
        val bare = node.copy(name = "  ", price = -5.0, currency = " ", billingCycleDays = 0, expiredAt = null,
            trafficLimitBytes = -1, trafficLimitType = "weird")
        val s = merge(n = bare, sub = null, traffic = null)
        assertEquals("node-1", s.subscription.name)
        assertEquals(0L, s.subscription.priceMinor)
        assertFalse(s.subscription.isFree)
        assertEquals("", s.subscription.currency)
        assertEquals(BillingCycle.MONTHLY, s.subscription.cycle)
        assertNull(s.subscription.nextDue)
        assertNull(s.subscription.anchorDay)
        assertFalse(s.subscription.isPermanent)
        assertEquals(VpsTrafficFields(0, TrafficLimitType.MAX), s.traffic)
    }

    @Test
    fun create_freeAndOnceNode() {
        val s = merge(n = node.copy(price = -1.0, billingCycleDays = -1), sub = null, traffic = null).subscription
        assertTrue(s.isFree)
        assertEquals(0L, s.priceMinor)
        assertEquals(BillingCycle.ONE_TIME, s.cycle)
    }

    // ---- update ----

    @Test
    fun update_allUnlocked_takesNodeValuesAndKeepsLocalOnlyFields() {
        val result = merge()
        assertFalse(result.isNew)
        assertTrue(result.hasChanges)
        assertEquals(fullyMerged, result.subscription)
        assertEquals(42L, result.subscription.id)
        assertEquals(Category.OTHER, result.subscription.category)
        assertEquals("keep me", result.subscription.note)
        assertEquals(fullyMergedTraffic, result.traffic)
        assertEquals(fullChanges, result.changes)
    }

    @Test
    fun update_eachFieldLockedAlone_isNotOverwritten() {
        for (field in LockedField.entries) {
            val result = merge(locked = LockedField.maskOf(field))
            var expectedSub = fullyMerged
            var expectedTraffic = fullyMergedTraffic
            when (field) {
                LockedField.NAME -> expectedSub = expectedSub.copy(name = existing.name)
                LockedField.PRICE -> expectedSub = expectedSub.copy(priceMinor = existing.priceMinor, isFree = existing.isFree)
                LockedField.CURRENCY -> expectedSub = expectedSub.copy(currency = existing.currency)
                LockedField.CYCLE -> expectedSub = expectedSub.copy(cycle = existing.cycle)
                LockedField.DUE_DATE -> expectedSub = expectedSub.copy(
                    nextDue = existing.nextDue, anchorDay = existing.anchorDay, isPermanent = existing.isPermanent,
                )
                LockedField.AUTO_RENEW -> expectedSub = expectedSub.copy(autoRenew = existing.autoRenew)
                LockedField.TRAFFIC_LIMIT -> expectedTraffic = expectedTraffic.copy(limitBytes = existingTraffic.limitBytes)
                LockedField.TRAFFIC_LIMIT_TYPE -> expectedTraffic = expectedTraffic.copy(limitType = existingTraffic.limitType)
            }
            assertEquals("locked $field", expectedSub, result.subscription)
            assertEquals("locked $field", expectedTraffic, result.traffic)
            assertEquals("locked $field", fullChanges.filter { it.field != field }, result.changes)
        }
    }

    @Test
    fun update_severalLocked() {
        val result = merge(locked = LockedField.maskOf(LockedField.PRICE, LockedField.DUE_DATE))
        assertEquals(
            fullyMerged.copy(priceMinor = 1_000, nextDue = d("2026-09-01"), anchorDay = 1),
            result.subscription,
        )
        assertEquals(
            listOf(LockedField.NAME, LockedField.CURRENCY, LockedField.CYCLE, LockedField.AUTO_RENEW,
                LockedField.TRAFFIC_LIMIT, LockedField.TRAFFIC_LIMIT_TYPE),
            result.changes.map { it.field },
        )
    }

    @Test
    fun update_allLocked_changesNothing() {
        val result = merge(locked = LockedField.ALL)
        assertEquals(existing, result.subscription)
        assertEquals(existingTraffic, result.traffic)
        assertTrue(result.changes.isEmpty())
        assertFalse(result.hasChanges)
    }

    @Test
    fun update_noDifferences_emptyChangeList() {
        val created = merge(sub = null, traffic = null)
        val stored = created.subscription.copy(id = 7)
        val again = merge(sub = stored, traffic = created.traffic)
        assertFalse(again.isNew)
        assertEquals(emptyList<FieldChange>(), again.changes)
        assertEquals(stored, again.subscription)
        assertEquals(created.traffic, again.traffic)
    }

    @Test
    fun update_notSetNodeValues_keepExisting() {
        val bare = node.copy(name = "", currency = "  ", billingCycleDays = 0, price = -3.0)
        val result = merge(n = bare)
        assertEquals(existing.name, result.subscription.name)
        assertEquals(existing.currency, result.subscription.currency)
        assertEquals(existing.cycle, result.subscription.cycle)
        assertEquals(existing.priceMinor, result.subscription.priceMinor)
        assertEquals(
            listOf(LockedField.DUE_DATE, LockedField.AUTO_RENEW, LockedField.TRAFFIC_LIMIT, LockedField.TRAFFIC_LIMIT_TYPE),
            result.changes.map { it.field },
        )
    }

    @Test
    fun update_withoutExistingTraffic_appliesNodeTrafficEvenIfLocked() {
        val locked = LockedField.maskOf(LockedField.TRAFFIC_LIMIT, LockedField.TRAFFIC_LIMIT_TYPE)
        val result = merge(traffic = null, locked = locked)
        assertEquals(VpsTrafficFields(tib, TrafficLimitType.SUM), result.traffic)
        assertTrue(FieldChange.TrafficLimit(null, tib) in result.changes)
        assertTrue(FieldChange.LimitType(null, TrafficLimitType.SUM) in result.changes)
    }

    // ---- price ----

    @Test
    fun priceMinusOne_meansFree() {
        val result = merge(n = node.copy(price = -1.0))
        assertTrue(result.subscription.isFree)
        assertEquals(0L, result.subscription.priceMinor)
        assertEquals(
            FieldChange.Price(MergePrice(1_000, false), MergePrice(0, true)),
            result.changes.single { it.field == LockedField.PRICE },
        )
    }

    @Test
    fun otherNegativePrice_isNotSynced() {
        val result = merge(n = node.copy(price = -2.0))
        assertEquals(1_000L, result.subscription.priceMinor)
        assertFalse(result.subscription.isFree)
        assertTrue(result.changes.none { it.field == LockedField.PRICE })
    }

    @Test
    fun zeroPrice_isNotSet_keepsExistingPrice() {
        // Komari admin UI: "0 不显示，-1 表示免费".
        val result = merge(n = node.copy(price = 0.0))
        assertEquals(1_000L, result.subscription.priceMinor)
        assertFalse(result.subscription.isFree)
        assertTrue(result.changes.none { it.field == LockedField.PRICE })
        val created = merge(n = node.copy(price = 0.0), sub = null, traffic = null).subscription
        assertEquals(0L, created.priceMinor)
        assertFalse(created.isFree)
    }

    @Test
    fun currencyWithoutPrice_isNotSynced() {
        // Real Komari 1.5.1 node with nothing filled in: price 0, currency "$" (the column default).
        val empty = node.copy(price = 0.0, currency = "$", billingCycleDays = 0, expiredAt = null, trafficLimitBytes = 0, trafficLimitType = "max")
        val result = merge(n = empty)
        assertEquals("¥", result.subscription.currency)
        assertTrue(result.changes.none { it.field == LockedField.CURRENCY || it.field == LockedField.PRICE || it.field == LockedField.CYCLE })
    }

    @Test
    fun newSubscription_withoutPanelPrice_usesDefaultCurrency() {
        val empty = node.copy(price = 0.0, currency = "$", billingCycleDays = 0, expiredAt = null)
        val created = KomariMerge.merge(null, null, empty, 0, zone, now, defaultCurrency = "CNY").subscription
        assertEquals("CNY", created.currency)
        assertEquals(0L, created.priceMinor)
        assertNull(created.nextDue)
        assertFalse(created.isPermanent)
        // With a panel price the panel's currency wins.
        assertEquals("$", KomariMerge.merge(null, null, node, 0, zone, now, defaultCurrency = "CNY").subscription.currency)
    }

    @Test
    fun lacksBillingInfo_onlyWithoutPriceAndDate() {
        val empty = node.copy(price = 0.0, billingCycleDays = 0, expiredAt = null)
        assertTrue(KomariMerge.lacksBillingInfo(empty, zone, now))
        assertTrue(KomariMerge.lacksBillingInfo(empty.copy(expiredAt = Instant.parse("0001-01-01T00:00:00Z")), zone, now))
        assertFalse(KomariMerge.lacksBillingInfo(empty.copy(price = 5.0), zone, now))
        assertFalse(KomariMerge.lacksBillingInfo(empty.copy(price = -1.0), zone, now))
        assertFalse(KomariMerge.lacksBillingInfo(empty.copy(expiredAt = Instant.parse("2026-12-01T00:00:00Z")), zone, now))
        assertFalse(KomariMerge.lacksBillingInfo(empty.copy(expiredAt = Instant.parse("2300-01-01T00:00:00Z")), zone, now))
    }

    @Test
    fun freeExisting_becomesPaidWhenNodeHasPrice() {
        val free = existing.copy(isFree = true, priceMinor = 0)
        val result = merge(sub = free)
        assertEquals(1_250L, result.subscription.priceMinor)
        assertFalse(result.subscription.isFree)
        assertEquals(
            FieldChange.Price(MergePrice(0, true), MergePrice(1_250, false)),
            result.changes.single { it.field == LockedField.PRICE },
        )
    }

    @Test
    fun priceFromKomari_roundsHalfUpToMinorUnits() {
        assertEquals(MergePrice(10, false), KomariMerge.priceFromKomari(0.1))
        assertEquals(MergePrice(1_999, false), KomariMerge.priceFromKomari(19.99))
        assertEquals(MergePrice(1_235, false), KomariMerge.priceFromKomari(12.345))
        assertEquals(MergePrice(1, false), KomariMerge.priceFromKomari(0.005))
        assertEquals(MergePrice(0, false), KomariMerge.priceFromKomari(0.004))
        assertNull("0 means not set in Komari", KomariMerge.priceFromKomari(0.0))
        assertNull(KomariMerge.priceFromKomari(-0.0))
        assertEquals(MergePrice(29_900, false), KomariMerge.priceFromKomari(299.0))
        assertEquals(MergePrice(0, true), KomariMerge.priceFromKomari(-1.0))
        assertNull(KomariMerge.priceFromKomari(-0.5))
        assertNull(KomariMerge.priceFromKomari(Double.NaN))
        assertNull(KomariMerge.priceFromKomari(Double.POSITIVE_INFINITY))
        assertNull(KomariMerge.priceFromKomari(1e30))
    }

    // ---- expiry ----

    @Test
    fun zeroTime_meansNoDueDate() {
        for (zero in listOf("0001-01-01T00:00:00Z", "1970-01-01T00:00:00Z", "1970-12-31T15:59:59Z")) {
            val result = merge(n = node.copy(expiredAt = Instant.parse(zero)))
            assertNull(zero, result.subscription.nextDue)
            assertFalse(zero, result.subscription.isPermanent)
            assertEquals(zero, 1, result.subscription.anchorDay) // anchor kept when there is no date
            assertEquals(
                FieldChange.DueDate(MergeDue(d("2026-09-01"), false), MergeDue(null, false)),
                result.changes.single { it.field == LockedField.DUE_DATE },
            )
        }
        assertEquals(MergeDue(null, false), KomariMerge.dueFromKomari(null, zone, now))
        assertEquals(MergeDue(null, false), KomariMerge.dueFromKomari(Instant.MIN, zone, now))
        // 1970-12-31T16:00Z is already 1971-01-01 in Shanghai.
        assertEquals(
            MergeDue(d("1971-01-01"), false),
            KomariMerge.dueFromKomari(Instant.parse("1970-12-31T16:00:00Z"), zone, now),
        )
    }

    @Test
    fun moreThanHundredYearsAhead_meansPermanent() {
        // Komari's "set to long term" button writes now + 200 years.
        for (far in listOf("2226-09-28T00:00:00Z", "9999-12-31T23:59:59Z", "2126-09-28T04:00:01Z")) {
            val result = merge(n = node.copy(expiredAt = Instant.parse(far)))
            assertTrue(far, result.subscription.isPermanent)
            assertNull(far, result.subscription.nextDue)
            assertEquals(
                FieldChange.DueDate(MergeDue(d("2026-09-01"), false), MergeDue(null, true)),
                result.changes.single { it.field == LockedField.DUE_DATE },
            )
        }
        assertEquals(MergeDue(null, true), KomariMerge.dueFromKomari(Instant.MAX, zone, now))
    }

    @Test
    fun upToHundredYearsAhead_isARegularDate() {
        // Exactly now + 100 years is not "long term" yet (Komari: strictly after).
        assertEquals(
            MergeDue(d("2126-09-28"), false),
            KomariMerge.dueFromKomari(Instant.parse("2126-09-28T04:00:00Z"), zone, now),
        )
        // A far but fixed year such as 2100 is a real date in 2026.
        assertEquals(
            MergeDue(d("2100-01-01"), false),
            KomariMerge.dueFromKomari(Instant.parse("2100-01-01T00:00:00Z"), zone, now),
        )
        // The long-term threshold moves with "now".
        val later = Instant.parse("2090-01-01T00:00:00Z")
        assertEquals(
            MergeDue(null, true),
            KomariMerge.dueFromKomari(Instant.parse("2189-01-01T00:00:00Z"), zone, Instant.parse("2080-01-01T00:00:00Z")),
        )
        assertEquals(MergeDue(d("2189-01-01"), false), KomariMerge.dueFromKomari(Instant.parse("2189-01-01T00:00:00Z"), zone, later))
    }

    @Test
    fun expiryDateIsJudgedInTheGivenZone() {
        // 1970-12-31T16:00Z is 1971-01-01 in Shanghai but still 1970 in UTC.
        val instant = Instant.parse("1970-12-31T16:00:00Z")
        assertEquals(MergeDue(d("1971-01-01"), false), KomariMerge.dueFromKomari(instant, zone, now))
        assertEquals(MergeDue(null, false), KomariMerge.dueFromKomari(instant, ZoneOffset.UTC, now))
        // Regular date: Shanghai vs UTC differ by a day.
        assertEquals(d("2026-10-15"), merge(z = zone).subscription.nextDue)
        assertEquals(d("2026-10-14"), merge(z = ZoneOffset.UTC).subscription.nextDue)
    }

    @Test
    fun permanentExisting_getsDateWhenNodeHasOne() {
        val permanent = existing.copy(nextDue = null, isPermanent = true, anchorDay = null)
        val result = merge(sub = permanent)
        assertFalse(result.subscription.isPermanent)
        assertEquals(d("2026-10-15"), result.subscription.nextDue)
        assertEquals(15, result.subscription.anchorDay)
        assertEquals(
            FieldChange.DueDate(MergeDue(null, true), MergeDue(d("2026-10-15"), false)),
            result.changes.single { it.field == LockedField.DUE_DATE },
        )
    }

    // ---- anchor day ----

    @Test
    fun anchorDay_keptWhenItExplainsNewDate() {
        val endOfMonth = existing.copy(cycle = BillingCycle.MONTHLY, nextDue = d("2026-01-31"), anchorDay = 31)
        // 2026-02-27T16:00Z is 2026-02-28 in Shanghai.
        val feb = merge(n = node.copy(expiredAt = Instant.parse("2026-02-27T16:00:00Z")), sub = endOfMonth)
        assertEquals(d("2026-02-28"), feb.subscription.nextDue)
        assertEquals(31, feb.subscription.anchorDay)

        val mid = merge(n = node.copy(expiredAt = Instant.parse("2026-03-14T16:00:00Z")), sub = endOfMonth)
        assertEquals(d("2026-03-15"), mid.subscription.nextDue)
        assertEquals(15, mid.subscription.anchorDay)
    }

    // ---- traffic fields ----

    @Test
    fun trafficValuesAreNormalized() {
        val result = merge(n = node.copy(trafficLimitBytes = -5, trafficLimitType = "  DOWN "))
        assertEquals(VpsTrafficFields(0, TrafficLimitType.DOWN), result.traffic)
        val unknown = merge(n = node.copy(trafficLimitType = "weird"))
        assertEquals(TrafficLimitType.MAX, unknown.traffic.limitType)
        assertTrue(unknown.changes.none { it.field == LockedField.TRAFFIC_LIMIT_TYPE })
    }

    // ---- LockedField mask ----

    @Test
    fun lockedField_bitsAreStable() {
        assertEquals(
            listOf(1, 2, 4, 8, 16, 32, 64, 128),
            LockedField.entries.map { it.mask },
        )
        assertEquals(255, LockedField.ALL)
        val mask = LockedField.maskOf(LockedField.NAME, LockedField.TRAFFIC_LIMIT)
        assertEquals(65, mask)
        assertEquals(setOf(LockedField.NAME, LockedField.TRAFFIC_LIMIT), LockedField.fromMask(mask))
        assertTrue(LockedField.NAME.isLockedIn(mask))
        assertFalse(LockedField.PRICE.isLockedIn(mask))
        assertEquals(67, LockedField.lock(mask, LockedField.PRICE))
        assertEquals(64, LockedField.unlock(mask, LockedField.NAME))
        assertEquals(setOf(LockedField.CYCLE), LockedField.fromMask(8 or (1 shl 20)))
    }
}
