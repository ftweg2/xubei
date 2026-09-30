package com.ftwegc.myapplication.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ftwegc.myapplication.data.local.dao.ReminderLogDao
import com.ftwegc.myapplication.data.local.dao.SubscriptionDao
import com.ftwegc.myapplication.data.local.dao.TrafficDao
import com.ftwegc.myapplication.data.local.dao.VpsDao
import com.ftwegc.myapplication.data.local.entity.ReminderLogEntity
import com.ftwegc.myapplication.data.local.entity.TrafficPeriodEntity
import com.ftwegc.myapplication.data.local.entity.TrafficSnapshotEntity
import com.ftwegc.myapplication.data.repo.RoomTransactionRunner
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.domain.calc.RenewMode
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.VpsConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: AppDatabase
    private lateinit var subs: SubscriptionDao
    private lateinit var vps: VpsDao
    private lateinit var traffic: TrafficDao
    private lateinit var reminders: ReminderLogDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        subs = db.subscriptionDao()
        vps = db.vpsDao()
        traffic = db.trafficDao()
        reminders = db.reminderLogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sub(
        name: String,
        due: LocalDate? = LocalDate.of(2026, 10, 1),
        category: Category = Category.VPS,
        autoRenew: Boolean = false,
        archived: Boolean = false,
        cycle: BillingCycle = BillingCycle.MONTHLY,
        permanent: Boolean = false,
    ) = Subscription(
        name = name, category = category, priceMinor = 1_000, currency = "CNY", cycle = cycle,
        nextDue = due, anchorDay = due?.dayOfMonth, autoRenew = autoRenew, archived = archived, isPermanent = permanent,
    ).toEntity(createdAt = 1, updatedAt = 1)

    private fun config(subscriptionId: Long, uuid: String?) =
        VpsConfig(subscriptionId = subscriptionId, komariUuid = uuid, trafficLimitBytes = 1L shl 40).toEntity()

    // ------------------------------------------------------------ subscriptions

    @Test
    fun upsert_insertsThenUpdates_andObserveAllJoinsVps() = runBlocking {
        val id = subs.upsert(sub("Tokyo"))
        assertTrue(id > 0)
        vps.insert(config(id, "node-1"))
        val otherId = subs.upsert(sub("Claude", category = Category.AI))

        val updateResult = subs.upsert(subs.getEntity(id)!!.copy(name = "Tokyo 2"))
        assertEquals(-1L, updateResult)

        val all = subs.observeAll().first().map { it.toDomain() }
        assertEquals(listOf(id, otherId), all.map { it.subscription.id })
        assertEquals("Tokyo 2", all[0].subscription.name)
        assertEquals("node-1", all[0].vps!!.komariUuid)
        assertNull(all[1].vps)
        assertNotNull("update via upsert must not cascade-delete the child", vps.getBySubscriptionId(id))
    }

    @Test
    fun observeById_emitsNullAfterDelete() = runBlocking {
        val id = subs.upsert(sub("A"))
        assertEquals("A", subs.observeById(id).first()!!.subscription.name)
        assertEquals(1, subs.delete(id))
        assertNull(subs.observeById(id).first())
    }

    @Test
    fun deleteSubscription_cascadesToAllChildTables() = runBlocking {
        val id = subs.upsert(sub("Tokyo"))
        val keep = subs.upsert(sub("Keep"))
        vps.insert(config(id, "node-1"))
        vps.insert(config(keep, "node-2"))
        traffic.insertPeriod(TrafficPeriodEntity(subscriptionId = id, periodStartDay = 100, periodEndDay = 130, upBytes = 1, downBytes = 2, updatedAt = 0))
        traffic.insertSnapshot(TrafficSnapshotEntity(subscriptionId = id, fetchedAt = 5, reportedAt = 4, totalUp = 1, totalDown = 2, uptimeSec = 3, deltaUp = 0, deltaDown = 0, resetDetected = false))
        reminders.insertIfAbsent(ReminderLogEntity(subscriptionId = id, key = "due:d3", createdAt = 0))

        subs.delete(id)

        assertNull(vps.getBySubscriptionId(id))
        assertNull(traffic.getPeriod(id, 100))
        assertNull(traffic.getLatestSnapshot(id))
        assertFalse(reminders.exists(id, "due:d3"))
        assertNotNull("other rows are untouched", vps.getBySubscriptionId(keep))
    }

    @Test
    fun vpsConfig_requiresExistingSubscription() = runBlocking {
        try {
            vps.insert(config(999, null))
            fail("foreign key must be enforced")
        } catch (expected: SQLiteConstraintException) {
            // ok
        }
    }

    @Test
    fun komariUuid_isUnique_butNullsAreAllowed() = runBlocking {
        val a = subs.upsert(sub("A"))
        val b = subs.upsert(sub("B"))
        val c = subs.upsert(sub("C"))
        val d = subs.upsert(sub("D"))
        vps.insert(config(a, "same"))
        try {
            vps.insert(config(b, "same"))
            fail("duplicate komari_uuid must be rejected")
        } catch (expected: SQLiteConstraintException) {
            // ok
        }
        vps.insert(config(c, null))
        vps.insert(config(d, null))
        assertEquals(a, vps.getByKomariUuid("same")!!.subscriptionId)
        assertEquals(listOf(a), vps.getLinked().map { it.subscriptionId })
        assertEquals(3, vps.getAll().size)
    }

    @Test
    fun vpsUpsert_updatesExistingRow() = runBlocking {
        val id = subs.upsert(sub("A"))
        vps.upsert(config(id, null))
        vps.upsert(config(id, "n1").copy(resetDay = 15))
        val row = vps.getBySubscriptionId(id)!!
        assertEquals("n1", row.komariUuid)
        assertEquals(15, row.resetDay)
        assertEquals(1, vps.deleteBySubscriptionId(id))
        assertNull(vps.observeBySubscriptionId(id).first())
    }

    @Test
    fun getDueBetween_excludesArchivedPermanentAndUndated() = runBlocking {
        val inRange = subs.upsert(sub("in", due = LocalDate.of(2026, 10, 3)))
        val edge = subs.upsert(sub("edge", due = LocalDate.of(2026, 10, 10)))
        subs.upsert(sub("out", due = LocalDate.of(2026, 10, 11)))
        subs.upsert(sub("archived", due = LocalDate.of(2026, 10, 4), archived = true))
        subs.upsert(sub("undated", due = null))
        val start = LocalDate.of(2026, 10, 1).toEpochDay()
        val end = LocalDate.of(2026, 10, 10).toEpochDay()
        assertEquals(listOf(inRange, edge), subs.getDueBetween(start, end).map { it.id })
    }

    @Test
    fun getAutoRenewExpired_onlyActiveRenewableOverdue() = runBlocking {
        val today = LocalDate.of(2026, 9, 28)
        val hit = subs.upsert(sub("hit", due = LocalDate.of(2026, 9, 1), autoRenew = true))
        subs.upsert(sub("manual", due = LocalDate.of(2026, 9, 1), autoRenew = false))
        subs.upsert(sub("today", due = today, autoRenew = true))
        subs.upsert(sub("archived", due = LocalDate.of(2026, 9, 1), autoRenew = true, archived = true))
        subs.upsert(sub("once", due = LocalDate.of(2026, 9, 1), autoRenew = true, cycle = BillingCycle.ONE_TIME))
        assertEquals(listOf(hit), subs.getAutoRenewExpired(today.toEpochDay()).map { it.id })
    }

    @Test
    fun setArchived_andUpdateDue() = runBlocking {
        val id = subs.upsert(sub("A"))
        assertEquals(1, subs.setArchived(id, true, 99))
        assertEquals(1, subs.updateDue(id, 20_000, 31, 100))
        val row = subs.getEntity(id)!!
        assertTrue(row.archived)
        assertEquals(20_000L, row.nextDueDay)
        assertEquals(31, row.anchorDay)
        assertEquals(100L, row.updatedAt)
        assertEquals(0, subs.setArchived(12345, true, 0))
    }

    // ------------------------------------------------------------ traffic / reminders

    @Test
    fun trafficPeriod_uniquePerSubscriptionAndStart() = runBlocking {
        val id = subs.upsert(sub("A"))
        val period = TrafficPeriodEntity(subscriptionId = id, periodStartDay = 100, periodEndDay = 130, upBytes = 1, downBytes = 2, updatedAt = 0)
        traffic.insertPeriod(period)
        try {
            traffic.insertPeriod(period.copy(upBytes = 5))
            fail("duplicate (subscription_id, period_start_day) must be rejected")
        } catch (expected: SQLiteConstraintException) {
            // ok
        }
        traffic.insertPeriod(period.copy(periodStartDay = 130, periodEndDay = 160))
        assertEquals(130L, traffic.observeLatestPeriod(id).first()!!.periodStartDay)
    }

    @Test
    fun snapshots_latestAndRetention() = runBlocking {
        val id = subs.upsert(sub("A"))
        fun snap(at: Long) = TrafficSnapshotEntity(subscriptionId = id, fetchedAt = at, reportedAt = at, totalUp = at, totalDown = at, uptimeSec = null, deltaUp = 0, deltaDown = 0, resetDetected = false)
        traffic.insertSnapshot(snap(10))
        traffic.insertSnapshot(snap(30))
        traffic.insertSnapshot(snap(20))
        assertEquals(30L, traffic.getLatestSnapshot(id)!!.fetchedAt)
        assertEquals(listOf(20L, 30L), traffic.getSnapshotsSince(id, 15).map { it.fetchedAt })
        assertEquals(2, traffic.deleteSnapshotsBefore(25))
        assertEquals(listOf(30L), traffic.getSnapshotsSince(id, 0).map { it.fetchedAt })
    }

    @Test
    fun reminderLog_uniqueKeyPerSubscription() = runBlocking {
        val a = subs.upsert(sub("A"))
        val b = subs.upsert(sub("B"))
        assertTrue(reminders.insertIfAbsent(ReminderLogEntity(subscriptionId = a, key = "due:d1", createdAt = 1)) > 0)
        assertEquals(-1L, reminders.insertIfAbsent(ReminderLogEntity(subscriptionId = a, key = "due:d1", createdAt = 2)))
        assertTrue("same key on another subscription is fine", reminders.insertIfAbsent(ReminderLogEntity(subscriptionId = b, key = "due:d1", createdAt = 3)) > 0)
        assertTrue(reminders.exists(a, "due:d1"))
        assertFalse(reminders.exists(a, "due:d0"))
        assertEquals(1, reminders.getForSubscription(a).size)
        assertEquals(1, reminders.deleteBefore(3))
    }

    // ------------------------------------------------------------ repository on real Room

    @Test
    fun repository_markRenewed_andSave_inRealTransactions() = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-09-28T04:00:00Z"), ZoneOffset.UTC)
        val repo = SubscriptionRepository(subs, vps, RoomTransactionRunner(db), clock)
        val domain = sub("Tokyo", due = LocalDate.of(2026, 1, 31)).toDomain().copy(id = 0)
        val id = repo.save(domain, VpsConfig(trafficLimitBytes = 5))
        assertEquals(LocalDate.of(2026, 2, 28), repo.markRenewed(id, RenewMode.FROM_DUE)!!.newDue)
        assertEquals(LocalDate.of(2026, 3, 31), repo.markRenewed(id, RenewMode.FROM_DUE)!!.newDue)
        repo.save(repo.get(id)!!.subscription.copy(category = Category.AI), null)
        assertNull(vps.getBySubscriptionId(id))
        assertTrue(repo.delete(id))
        assertEquals(0, repo.observeAll().first().size)
    }
}
