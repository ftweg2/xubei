package com.ftwegc.myapplication.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ftwegc.myapplication.data.local.entity.TrafficPeriodEntity
import com.ftwegc.myapplication.data.local.entity.TrafficSnapshotEntity
import com.ftwegc.myapplication.data.remote.komari.KomariApi
import com.ftwegc.myapplication.data.remote.komari.KomariLatest
import com.ftwegc.myapplication.data.remote.komari.KomariNodeList
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.data.remote.komari.KomariVersion
import com.ftwegc.myapplication.data.repo.KomariLinkChange
import com.ftwegc.myapplication.data.repo.RoomTransactionRunner
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.data.repo.TrafficRepository
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.VpsConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Counting mode 2 against a real (in-memory) Room database: queries, constraints, transactions. */
@RunWith(AndroidJUnit4::class)
class TrafficPeriodDaoTest {

    private lateinit var db: AppDatabase
    private val mib = 1L shl 20

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun subscription(name: String): Long = db.subscriptionDao().upsert(
        Subscription(name = name, category = Category.VPS, priceMinor = 1, currency = "¥", cycle = BillingCycle.MONTHLY).toEntity(1, 1),
    )

    private fun period(id: Long, start: LocalDate, end: LocalDate, up: Long = 0) = TrafficPeriodEntity(
        subscriptionId = id, periodStartDay = start.toEpochDay(), periodEndDay = end.toEpochDay(),
        upBytes = up, downBytes = 0, updatedAt = 1,
    )

    @Test
    fun latestPeriods_perSubscription_andContainingQuery() = runBlocking {
        val a = subscription("a")
        val b = subscription("b")
        val dao = db.trafficDao()
        dao.insertPeriod(period(a, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), up = 1))
        dao.insertPeriod(period(a, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), up = 2))
        dao.insertPeriod(period(b, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15), up = 3))
        val latest = dao.observeLatestPeriods().first().associateBy { it.subscriptionId }
        assertEquals(2L, latest.getValue(a).upBytes)
        assertEquals(3L, latest.getValue(b).upBytes)
        assertEquals(1L, dao.getPeriodContaining(a, LocalDate.of(2026, 8, 31).toEpochDay())!!.upBytes)
        assertNull(dao.getPeriodContaining(a, LocalDate.of(2026, 10, 1).toEpochDay()))
        assertEquals(listOf(2L, 1L), dao.observePeriods(a).first().map { it.upBytes })

        try {
            dao.insertPeriod(period(a, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)))
            fail("same (subscription, start) must be rejected")
        } catch (_: SQLiteConstraintException) {
        }
    }

    @Test
    fun failedTransaction_leavesNothingBehind() = runBlocking {
        val a = subscription("a")
        val dao = db.trafficDao()
        try {
            db.withTransaction {
                dao.insertSnapshot(TrafficSnapshotEntity(subscriptionId = a, fetchedAt = 1, reportedAt = 1, totalUp = 1, totalDown = 1, uptimeSec = 1, deltaUp = 1, deltaDown = 1, resetDetected = false))
                dao.insertPeriod(period(a, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)))
                error("simulated crash in the middle of one node's writes")
            }
        } catch (_: IllegalStateException) {
        }
        assertNull(dao.getLatestSnapshot(a))
        assertNull(dao.getLatestPeriod(a))
    }

    @Test
    fun snapshotNotes_andPerSubscriptionDelete() = runBlocking {
        val a = subscription("a")
        val b = subscription("b")
        val dao = db.trafficDao()
        fun snap(id: Long, at: Long, note: String?) = TrafficSnapshotEntity(
            subscriptionId = id, fetchedAt = at, reportedAt = at, totalUp = 0, totalDown = 0, uptimeSec = null,
            deltaUp = 0, deltaDown = 0, resetDetected = false, note = note,
        )
        dao.insertSnapshot(snap(a, 1, null))
        dao.insertSnapshot(snap(a, 2, "REBOOT"))
        dao.insertSnapshot(snap(a, 3, "REBOOT"))
        dao.insertSnapshot(snap(b, 4, "COUNTER_RESET"))
        assertEquals(listOf("REBOOT"), dao.observeSnapshotNotes(a).first())
        assertEquals(3, dao.deleteSnapshotsFor(a))
        assertNull(dao.getLatestSnapshot(a))
        assertEquals(4L, dao.getLatestSnapshot(b)!!.fetchedAt)
    }

    @Test
    fun refresh_writesCountersSnapshotAndPeriodTogether() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        var now = Instant.parse("2026-09-30T15:00:05Z")
        val clock = object : Clock() {
            override fun getZone(): ZoneId = zone
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = now
        }
        val transactions = RoomTransactionRunner(db)
        val subs = SubscriptionRepository(db.subscriptionDao(), db.vpsDao(), transactions, clock, db.trafficDao())
        var reading = KomariLatest(0, 0, 100_000, Instant.parse("2026-09-30T15:00:00Z"))
        val api = object : KomariApi {
            override suspend fun getVersion() = KomariResult.Ok(KomariVersion("1.5.1", ""))
            override suspend fun getNodes() = KomariResult.Ok(KomariNodeList(emptyList()))
            override suspend fun getLatest(uuid: String): KomariResult<KomariLatest?> = KomariResult.Ok(reading)
        }
        val repo = TrafficRepository({ KomariResult.Ok(api) }, db.vpsDao(), clock, trafficDao = db.trafficDao(), transactions = transactions, resetZone = { zone })
        val id = subs.save(
            Subscription(name = "VPS", category = Category.VPS, priceMinor = 1, currency = "¥", cycle = BillingCycle.MONTHLY),
            VpsConfig(countingMode = CountingMode.APP_ACCUMULATED, trafficLimitBytes = 1L shl 40),
            link = KomariLinkChange.Link("n1", "n1", null),
        )
        repo.refreshAll()
        now = Instant.parse("2026-09-30T17:00:05Z")
        reading = KomariLatest(120 * mib, 60 * mib, 107_200, Instant.parse("2026-09-30T17:00:00Z"))
        repo.refreshAll()

        val periods = db.trafficDao().observePeriods(id).first()
        assertEquals(2, periods.size)
        assertEquals(60 * mib, periods[0].upBytes) // October
        assertEquals(60 * mib, periods[1].upBytes) // September, closed at Oct 1
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), periods[1].periodEndDay)
        assertEquals(120 * mib, db.vpsDao().getBySubscriptionId(id)!!.lastTotalUp)
        assertEquals(2, db.trafficDao().getSnapshotsSince(id, 0).size)
        val item = subs.get(id)!!
        assertEquals(LocalDate.of(2026, 10, 1), item.appPeriod!!.period.start)

        // Deleting the subscription cascades to periods and snapshots.
        subs.delete(id)
        assertTrue(db.trafficDao().observePeriods(id).first().isEmpty())
        assertNull(db.trafficDao().getLatestSnapshot(id))
        assertEquals(ZoneOffset.ofHours(8), zone.rules.getOffset(now))
    }
}
