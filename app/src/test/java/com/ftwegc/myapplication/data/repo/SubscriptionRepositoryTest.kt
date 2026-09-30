package com.ftwegc.myapplication.data.repo

import com.ftwegc.myapplication.data.local.toEntity
import com.ftwegc.myapplication.domain.calc.RenewMode
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.LockedField
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.VpsConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SubscriptionRepositoryTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    /** 2026-09-28 12:00 in Shanghai. */
    private val now = Instant.parse("2026-09-28T04:00:00Z")
    private val tables = FakeTables()
    private val tx = DirectTransactionRunner()
    private val repo = SubscriptionRepository(FakeSubscriptionDao(tables), FakeVpsDao(tables), tx, Clock.fixed(now, zone))

    private fun d(s: String) = LocalDate.parse(s)

    private fun monthly(name: String = "ChatGPT", due: String? = "2026-10-05", anchor: Int? = null, autoRenew: Boolean = false) =
        Subscription(
            name = name, category = Category.AI, priceMinor = 2_000, currency = "USD",
            cycle = BillingCycle.MONTHLY, nextDue = due?.let(::d), anchorDay = anchor, autoRenew = autoRenew,
        )

    private suspend fun dueOf(id: Long) = repo.get(id)!!.subscription.nextDue

    // ------------------------------------------------------------ today / save

    @Test
    fun today_comesFromInjectedClock() {
        assertEquals(d("2026-09-28"), repo.today())
    }

    @Test
    fun save_new_assignsIdAndTimestamps_withoutVpsRow() = runTest {
        val id = repo.save(monthly(), vps = VpsConfig(trafficLimitBytes = 1))
        assertTrue(id > 0)
        val row = tables.subscriptions.value.getValue(id)
        assertEquals(now.toEpochMilli(), row.createdAt)
        assertEquals(now.toEpochMilli(), row.updatedAt)
        assertNull("non-VPS subscriptions never get a VPS row", tables.vps.value[id])
        assertEquals(1, tx.calls)
    }

    @Test
    fun save_newVps_storesConfig_withoutLocks() = runTest {
        val sub = monthly().copy(category = Category.VPS)
        val id = repo.save(sub, VpsConfig(trafficLimitBytes = 500, lockedFields = LockedField.ALL, resetDay = 15))
        val vps = repo.get(id)!!.vps!!
        assertEquals(id, vps.subscriptionId)
        assertEquals(500L, vps.trafficLimitBytes)
        assertEquals(15, vps.resetDay)
        assertEquals(LockedField.NONE, vps.lockedFields)
    }

    @Test
    fun save_newVpsWithoutConfig_storesDefaults() = runTest {
        val id = repo.save(monthly().copy(category = Category.VPS), vps = null)
        assertEquals(VpsConfig(subscriptionId = id), repo.get(id)!!.vps)
    }

    @Test
    fun save_update_keepsCreatedAt_andChangesFields() = runTest {
        val id = repo.save(monthly(), null)
        val created = tables.subscriptions.value.getValue(id).createdAt
        val later = SubscriptionRepository(
            FakeSubscriptionDao(tables), FakeVpsDao(tables), tx, Clock.fixed(now.plusSeconds(60), zone),
        )
        later.save(monthly(name = "ChatGPT Pro").copy(id = id, priceMinor = 20_000), null)
        val row = tables.subscriptions.value.getValue(id)
        assertEquals(created, row.createdAt)
        assertEquals(now.plusSeconds(60).toEpochMilli(), row.updatedAt)
        assertEquals("ChatGPT Pro", row.name)
        assertEquals(20_000L, row.priceMinor)
        assertEquals(1, tables.subscriptions.value.size)
    }

    @Test(expected = NoSuchElementException::class)
    fun save_updateMissing_throws() = runTest {
        repo.save(monthly().copy(id = 99), null)
    }

    @Test
    fun save_switchingAwayFromVps_deletesConfig() = runTest {
        val id = repo.save(monthly().copy(category = Category.VPS), VpsConfig(trafficLimitBytes = 10))
        repo.save(monthly().copy(id = id, category = Category.OTHER), null)
        assertNull(tables.vps.value[id])
    }

    @Test
    fun save_updateLinkedVps_locksChangedFields_andKeepsKomariData() = runTest {
        val sub = monthly().copy(category = Category.VPS)
        val id = repo.save(sub, VpsConfig(trafficLimitBytes = 100, trafficLimitType = TrafficLimitType.MAX))
        // Simulate a later Komari link + refresh.
        val linked = repo.get(id)!!.vps!!.copy(
            komariUuid = "node-1", komariName = "tokyo", lastTotalUp = 5, lastTotalDown = 6,
            lockedFields = LockedField.maskOf(LockedField.CURRENCY),
        )
        FakeVpsDao(tables).upsert(linked.toEntity())

        // The form hands back a stale copy (no Komari data) with a new limit type and a new name.
        val edited = VpsConfig(trafficLimitBytes = 100, trafficLimitType = TrafficLimitType.SUM, resetDay = 3)
        repo.save(sub.copy(id = id, name = "Tokyo VPS"), edited)

        val vps = repo.get(id)!!.vps!!
        assertEquals("node-1", vps.komariUuid)
        assertEquals("tokyo", vps.komariName)
        assertEquals(5L, vps.lastTotalUp)
        assertEquals(TrafficLimitType.SUM, vps.trafficLimitType)
        assertEquals(3, vps.resetDay)
        assertEquals(
            setOf(LockedField.CURRENCY, LockedField.NAME, LockedField.TRAFFIC_LIMIT_TYPE),
            LockedField.fromMask(vps.lockedFields),
        )
    }

    @Test
    fun save_updateUnlinkedVps_locksNothing() = runTest {
        val sub = monthly().copy(category = Category.VPS)
        val id = repo.save(sub, VpsConfig(trafficLimitBytes = 100))
        repo.save(sub.copy(id = id, name = "Renamed", priceMinor = 1), VpsConfig(trafficLimitBytes = 200))
        assertEquals(LockedField.NONE, repo.get(id)!!.vps!!.lockedFields)
    }

    // ------------------------------------------------------------ mark renewed

    @Test
    fun markRenewed_fromDue_monthlyAnchor31_followsMonthEnds() = runTest {
        val id = repo.save(monthly(due = "2026-01-31", anchor = 31), null)
        val first = repo.markRenewed(id, RenewMode.FROM_DUE)!!
        assertEquals(d("2026-02-28"), first.newDue)
        assertEquals(31, first.newAnchorDay)
        repo.markRenewed(id, RenewMode.FROM_DUE)
        assertEquals(d("2026-03-31"), dueOf(id))
        assertEquals(31, repo.get(id)!!.subscription.anchorDay)
    }

    @Test
    fun markRenewed_fromDue_overdue_advancesOneCycleFromOldDue() = runTest {
        val id = repo.save(monthly(due = "2026-09-01", anchor = 1), null)
        val outcome = repo.markRenewed(id, RenewMode.FROM_DUE)!!
        assertEquals(d("2026-09-01"), outcome.previousDue)
        assertEquals(d("2026-10-01"), outcome.newDue)
        assertEquals(d("2026-10-01"), dueOf(id))
    }

    @Test
    fun markRenewed_fromDue_withoutAnchor_storesDueDayAsAnchor() = runTest {
        val id = repo.save(monthly(due = "2026-10-31", anchor = null), null)
        repo.markRenewed(id, RenewMode.FROM_DUE)
        val sub = repo.get(id)!!.subscription
        assertEquals(d("2026-11-30"), sub.nextDue)
        assertEquals(31, sub.anchorDay)
    }

    @Test
    fun markRenewed_fromDue_withoutDueDate_startsFromToday() = runTest {
        val id = repo.save(monthly(due = null), null)
        repo.markRenewed(id, RenewMode.FROM_DUE)
        assertEquals(d("2026-10-28"), dueOf(id))
    }

    @Test
    fun markRenewed_fromToday_monthly_movesAnchorToToday() = runTest {
        val id = repo.save(monthly(due = "2026-09-01", anchor = 1), null)
        val outcome = repo.markRenewed(id, RenewMode.FROM_TODAY)!!
        assertEquals(d("2026-10-28"), outcome.newDue)
        assertEquals(28, repo.get(id)!!.subscription.anchorDay)
    }

    @Test
    fun markRenewed_fromToday_annual() = runTest {
        val id = repo.save(monthly(due = "2026-12-01").copy(cycle = BillingCycle.ANNUAL), null)
        repo.markRenewed(id, RenewMode.FROM_TODAY)
        assertEquals(d("2027-09-28"), dueOf(id))
    }

    @Test
    fun markRenewed_dayCycle_addsDays_andKeepsAnchor() = runTest {
        val id = repo.save(monthly(due = "2026-10-05", anchor = null).copy(cycle = BillingCycle.days(45)), null)
        repo.markRenewed(id, RenewMode.FROM_DUE)
        val sub = repo.get(id)!!.subscription
        assertEquals(d("2026-11-19"), sub.nextDue)
        assertNull(sub.anchorDay)
        repo.markRenewed(id, RenewMode.FROM_TODAY)
        assertEquals(d("2026-11-12"), dueOf(id))
    }

    @Test
    fun markRenewed_oneTimeOrPermanent_returnsNull_andChangesNothing() = runTest {
        val once = repo.save(monthly(due = "2026-10-05").copy(cycle = BillingCycle.ONE_TIME), null)
        val permanent = repo.save(monthly(due = null).copy(isPermanent = true), null)
        assertNull(repo.markRenewed(once, RenewMode.FROM_DUE))
        assertNull(repo.markRenewed(permanent, RenewMode.FROM_TODAY))
        assertEquals(d("2026-10-05"), dueOf(once))
        assertNull(dueOf(permanent))
    }

    @Test
    fun markRenewed_missing_returnsNull() = runTest {
        assertNull(repo.markRenewed(42, RenewMode.FROM_DUE))
    }

    @Test
    fun markRenewed_linkedVps_locksDueDate_andUndoRestoresEverything() = runTest {
        val sub = monthly(due = "2026-10-05", anchor = 5).copy(category = Category.VPS)
        val id = repo.save(sub, VpsConfig())
        val linked = repo.get(id)!!.vps!!.copy(komariUuid = "n1", lockedFields = LockedField.maskOf(LockedField.NAME))
        FakeVpsDao(tables).upsert(linked.toEntity())

        val outcome = repo.markRenewed(id, RenewMode.FROM_TODAY)!!
        val renewed = repo.get(id)!!
        assertEquals(d("2026-10-28"), renewed.subscription.nextDue)
        assertTrue(LockedField.DUE_DATE.isLockedIn(renewed.vps!!.lockedFields))

        repo.undoRenew(outcome)
        val undone = repo.get(id)!!
        assertEquals(d("2026-10-05"), undone.subscription.nextDue)
        assertEquals(5, undone.subscription.anchorDay)
        assertEquals(LockedField.maskOf(LockedField.NAME), undone.vps!!.lockedFields)
    }

    @Test
    fun previewRenew_doesNotSave() = runTest {
        val id = repo.save(monthly(due = "2026-10-05", anchor = 5), null)
        val sub = repo.get(id)!!.subscription
        assertEquals(d("2026-11-05"), repo.previewRenew(sub, RenewMode.FROM_DUE)!!.nextDue)
        assertEquals(d("2026-10-05"), dueOf(id))
    }

    // ------------------------------------------------------------ archive / delete / roll forward

    @Test
    fun setArchived_andDelete_cascade() = runTest {
        val id = repo.save(monthly().copy(category = Category.VPS), VpsConfig())
        assertTrue(repo.setArchived(id, true))
        assertTrue(repo.get(id)!!.subscription.archived)
        assertTrue(repo.delete(id))
        assertNull(repo.get(id))
        assertNull(tables.vps.value[id])
        assertFalse(repo.delete(id))
        assertFalse(repo.setArchived(id, false))
    }

    @Test
    fun rollForwardAutoRenewals_onlyTouchesActiveAutoRenewing() = runTest {
        val auto = repo.save(monthly(due = "2026-07-31", anchor = 31, autoRenew = true), null)
        val manual = repo.save(monthly(due = "2026-07-31", anchor = 31, autoRenew = false), null)
        val archived = repo.save(monthly(due = "2026-07-31", anchor = 31, autoRenew = true).copy(archived = true), null)
        val future = repo.save(monthly(due = "2026-10-31", anchor = 31, autoRenew = true), null)

        assertEquals(1, repo.rollForwardAutoRenewals())
        assertEquals(d("2026-09-30"), dueOf(auto))
        assertEquals(d("2026-07-31"), dueOf(manual))
        assertEquals(d("2026-07-31"), dueOf(archived))
        assertEquals(d("2026-10-31"), dueOf(future))
        assertEquals(0, repo.rollForwardAutoRenewals())
    }

    @Test
    fun observeAll_emitsDomainModels() = runTest {
        repo.save(monthly(name = "A"), null)
        repo.save(monthly(name = "B").copy(category = Category.VPS), VpsConfig(trafficLimitBytes = 9))
        val all = repo.observeAll().first()
        assertEquals(listOf("A", "B"), all.map { it.subscription.name })
        assertNull(all[0].vps)
        assertNotNull(all[1].vps)
        assertEquals(9L, all[1].vps!!.trafficLimitBytes)
    }
}
