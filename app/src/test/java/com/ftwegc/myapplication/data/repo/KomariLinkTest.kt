package com.ftwegc.myapplication.data.repo

import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.LockedField
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.VpsConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Linking, unlinking and unlocking through [SubscriptionRepository.save]. */
class KomariLinkTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-28T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
    private val tables = FakeTables()
    private val repo = SubscriptionRepository(FakeSubscriptionDao(tables), FakeVpsDao(tables), DirectTransactionRunner(), clock)

    private val vpsSub = Subscription(
        name = "VPS", category = Category.VPS, priceMinor = 1_000, currency = "¥", cycle = BillingCycle.MONTHLY,
    )

    @Test
    fun link_onNewAndExisting_withoutLocks() = runTest {
        val a = repo.save(vpsSub, VpsConfig(), link = KomariLinkChange.Link("n1", "Node 1", "asia"))
        val va = repo.get(a)!!.vps!!
        assertEquals("n1", va.komariUuid)
        assertEquals("Node 1", va.komariName)
        assertEquals("asia", va.komariGroup)
        assertEquals(LockedField.NONE, va.lockedFields)

        val b = repo.save(vpsSub.copy(name = "B"), VpsConfig())
        repo.save(repo.get(b)!!.subscription.copy(priceMinor = 5), repo.get(b)!!.vps, link = KomariLinkChange.Link("n2", "Node 2", null))
        val vb = repo.get(b)!!.vps!!
        assertEquals("n2", vb.komariUuid)
        assertEquals("a newly linked node starts unlocked", LockedField.NONE, vb.lockedFields)
    }

    @Test
    fun relink_toAnotherNode_resetsLocksAndCounters() = runTest {
        val id = repo.save(vpsSub, VpsConfig(), link = KomariLinkChange.Link("n1", "Node 1", null))
        val row = tables.vps.value.getValue(id)
        tables.vps.value = tables.vps.value + (id to row.copy(lockedFields = LockedField.ALL, lastTotalUp = 5, lastTotalDown = 6, lastReportedAt = 1))
        val stored = repo.get(id)!!
        repo.save(stored.subscription, stored.vps, link = KomariLinkChange.Link("n9", "Node 9", null))
        val v = repo.get(id)!!.vps!!
        assertEquals("n9", v.komariUuid)
        assertEquals(LockedField.NONE, v.lockedFields)
        assertNull(v.lastTotalUp)
        assertNull(v.lastSample)
    }

    @Test
    fun linkSameNode_isNoChange() = runTest {
        val id = repo.save(vpsSub, VpsConfig(), link = KomariLinkChange.Link("n1", "Node 1", null))
        val row = tables.vps.value.getValue(id)
        tables.vps.value = tables.vps.value + (id to row.copy(lockedFields = LockedField.PRICE.mask, lastTotalUp = 5))
        val stored = repo.get(id)!!
        repo.save(stored.subscription, stored.vps, link = KomariLinkChange.Link("n1", "Renamed", null))
        val v = repo.get(id)!!.vps!!
        assertEquals(LockedField.PRICE.mask, v.lockedFields)
        assertEquals(5L, v.lastTotalUp)
    }

    @Test
    fun unlink_clearsLinkLocksAndCounters_keepsSettings() = runTest {
        val id = repo.save(vpsSub, VpsConfig(resetDay = 9), link = KomariLinkChange.Link("n1", "Node 1", null))
        val row = tables.vps.value.getValue(id)
        tables.vps.value = tables.vps.value + (id to row.copy(lockedFields = LockedField.ALL, lastTotalUp = 5, komariMissing = true))
        val stored = repo.get(id)!!
        repo.save(stored.subscription, stored.vps, link = KomariLinkChange.Unlink)
        val v = repo.get(id)!!.vps!!
        assertNull(v.komariUuid)
        assertFalse(v.komariMissing)
        assertEquals(LockedField.NONE, v.lockedFields)
        assertNull(v.lastTotalUp)
        assertEquals(9, v.resetDay)
    }

    @Test
    fun linkTakenByAnotherSubscription_isRejected() = runTest {
        repo.save(vpsSub, VpsConfig(), link = KomariLinkChange.Link("n1", "Node 1", null))
        val other = repo.save(vpsSub.copy(name = "Other"), VpsConfig())
        val before = repo.get(other)!!
        try {
            repo.save(before.subscription.copy(name = "Changed"), before.vps, link = KomariLinkChange.Link("n1", null, null))
            fail("expected KomariNodeTakenException")
        } catch (e: KomariNodeTakenException) {
            assertTrue(e.ownerId > 0)
        }
        try {
            repo.save(vpsSub.copy(name = "New"), VpsConfig(), link = KomariLinkChange.Link("n1", null, null))
            fail("expected KomariNodeTakenException")
        } catch (_: KomariNodeTakenException) {
        }
    }

    @Test
    fun unlocked_removesLocks_butNewEditsRelock() = runTest {
        val id = repo.save(vpsSub, VpsConfig(), link = KomariLinkChange.Link("n1", "Node 1", null))
        val row = tables.vps.value.getValue(id)
        tables.vps.value = tables.vps.value + (id to row.copy(lockedFields = LockedField.maskOf(LockedField.PRICE, LockedField.NAME)))
        val stored = repo.get(id)!!
        repo.save(stored.subscription, stored.vps, unlocked = LockedField.maskOf(LockedField.PRICE, LockedField.NAME))
        assertEquals(LockedField.NONE, repo.get(id)!!.vps!!.lockedFields)

        val again = repo.get(id)!!
        repo.save(again.subscription.copy(name = "Edited"), again.vps, unlocked = LockedField.NAME.mask)
        assertEquals("an edited field locks again even if unlocked", LockedField.NAME.mask, repo.get(id)!!.vps!!.lockedFields)
    }
}
