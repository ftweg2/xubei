package com.ftwegc.myapplication.data.repo

import com.ftwegc.myapplication.data.local.toDomain
import com.ftwegc.myapplication.data.remote.komari.KomariLatest
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.domain.calc.DeltaNote
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.ui.common.TrafficUi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Counting mode 2 end to end: a scripted Komari node, a clock the test moves, and the refresh
 * writing snapshots and period totals. Every step asserts the accumulated values.
 */
class TrafficAccumulationTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val clock = MutableClock(Instant.parse("2026-09-28T04:00:00Z"), shanghai)
    private val tables = FakeTables()
    private val vpsDao = FakeVpsDao(tables)
    private val trafficDao = FakeTrafficDao()
    private val transactions = DirectTransactionRunner()
    private val subs = SubscriptionRepository(FakeSubscriptionDao(tables), vpsDao, transactions, clock, trafficDao)
    private val api = FakeKomariApi()
    private var resetZone: ZoneId = shanghai
    private val repo = TrafficRepository(
        apiProvider = { KomariResult.Ok(api) },
        vpsDao = vpsDao,
        clock = clock,
        trafficDao = trafficDao,
        transactions = transactions,
        resetZone = { resetZone },
    )

    private val mib = 1L shl 20
    private val gib = 1L shl 30

    /** What the node reports next. */
    private var reading: KomariLatest? = null

    init {
        api.latest = { KomariResult.Ok(reading) }
    }

    private suspend fun vps(mode: CountingMode = CountingMode.APP_ACCUMULATED, resetDay: Int = 1): Long =
        subs.save(
            Subscription(name = "VPS", category = Category.VPS, priceMinor = 100, currency = "CNY", cycle = BillingCycle.MONTHLY),
            VpsConfig(trafficLimitBytes = 1L shl 40, trafficLimitType = TrafficLimitType.MAX, countingMode = mode, resetDay = resetDay),
            link = KomariLinkChange.Link("n1", "n1", null),
        )

    /** The node reports [up]/[down]/[uptime] a few seconds before "now", then the app refreshes. */
    private suspend fun report(up: Long, down: Long, uptime: Long?, reportedAt: Instant = clock.now.minusSeconds(5)) {
        reading = KomariLatest(up, down, uptime, reportedAt)
        repo.refreshAll()
    }

    private fun later(minutes: Long) = clock.advance(Duration.ofMinutes(minutes))

    private fun current(id: Long) = trafficDao.periodsOf(id).last()

    @Test
    fun accumulates_growthDuplicatesAndReboot() = runTest {
        val id = vps()
        // 1. Baseline: a snapshot, an empty period, no traffic counted yet.
        report(100 * gib, 200 * gib, uptime = 864_000)
        assertEquals(1, trafficDao.snapshotsOf(id).size)
        assertEquals(LocalDate.of(2026, 9, 1).toEpochDay(), current(id).periodStartDay)
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), current(id).periodEndDay)
        assertEquals(0L, current(id).upBytes)
        assertEquals(0L, current(id).downBytes)

        // 2. Growth: the difference is added.
        later(30)
        report(100 * gib + 10 * mib, 200 * gib + 30 * mib, uptime = 865_800)
        assertEquals(10 * mib, current(id).upBytes)
        assertEquals(30 * mib, current(id).downBytes)

        // 3. The same report again (agent silent): no snapshot, nothing added.
        val sameTime = reading!!.reportedAt!!
        later(30)
        report(100 * gib + 10 * mib, 200 * gib + 30 * mib, uptime = 865_800, reportedAt = sameTime)
        assertEquals(2, trafficDao.snapshotsOf(id).size)
        assertEquals(10 * mib, current(id).upBytes)

        // 4. Reboot: counters restart near 0, the new reading is the traffic since boot.
        later(30)
        report(5 * mib, 8 * mib, uptime = 600)
        assertEquals(15 * mib, current(id).upBytes)
        assertEquals(38 * mib, current(id).downBytes)
        assertEquals(DeltaNote.REBOOT.name, trafficDao.snapshotsOf(id).last().note)
        assertTrue(trafficDao.snapshotsOf(id).last().resetDetected)

        // 5. Normal growth after the reboot.
        later(30)
        report(7 * mib, 20 * mib, uptime = 2_400)
        assertEquals(17 * mib, current(id).upBytes)
        assertEquals(50 * mib, current(id).downBytes)

        // Displayed usage: max(up, down) with a 1 TiB limit.
        val item = subs.get(id)!!
        val ui = TrafficUi.of(item.vps!!, item.appPeriod, LocalDate.of(2026, 9, 28))
        assertEquals(50 * mib, ui.usage!!.usedBytes)
        assertEquals(1, trafficDao.periodsOf(id).size)
    }

    @Test
    fun switchingToMode2_firstReadingIsOnlyTheBaseline() = runTest {
        val id = vps(mode = CountingMode.KOMARI_CUMULATIVE)
        report(1 * gib, 2 * gib, uptime = 1_000)
        later(30)
        report(1 * gib + 50 * mib, 2 * gib + 50 * mib, uptime = 2_800)
        assertTrue("mode 1 keeps snapshots for the trend", trafficDao.snapshotsOf(id).size == 2)
        assertTrue("mode 1 never writes periods", trafficDao.periodsOf(id).isEmpty())

        later(5)
        val stored = subs.get(id)!!
        subs.save(stored.subscription, stored.vps!!.copy(countingMode = CountingMode.APP_ACCUMULATED))
        assertNotNull(subs.get(id)!!.vps!!.appCountingSince)

        later(25)
        report(1 * gib + 80 * mib, 2 * gib + 90 * mib, uptime = 4_600)
        assertEquals("traffic before the switch is not counted", 0L, current(id).downBytes)

        later(30)
        report(1 * gib + 90 * mib, 2 * gib + 100 * mib, uptime = 6_400)
        assertEquals(10 * mib, current(id).upBytes)
        assertEquals(10 * mib, current(id).downBytes)
    }

    @Test
    fun newPeriod_closesTheOldOne_andSplitsTheDeltaAtMidnight() = runTest {
        // 23:00 on Sep 30 in Shanghai.
        clock.now = Instant.parse("2026-09-30T15:00:05Z")
        val id = vps()
        report(0, 0, uptime = 100_000, reportedAt = Instant.parse("2026-09-30T15:00:00Z"))
        // 01:00 on Oct 1: 120 MiB over two hours, half of it before midnight.
        clock.now = Instant.parse("2026-09-30T17:00:05Z")
        report(120 * mib, 240 * mib, uptime = 107_200, reportedAt = Instant.parse("2026-09-30T17:00:00Z"))

        val rows = trafficDao.periodsOf(id)
        assertEquals(2, rows.size)
        val september = rows[0]
        val october = rows[1]
        assertEquals(LocalDate.of(2026, 9, 1).toEpochDay(), september.periodStartDay)
        assertEquals("old period closed where the new one starts", LocalDate.of(2026, 10, 1).toEpochDay(), september.periodEndDay)
        assertEquals(60 * mib, september.upBytes)
        assertEquals(120 * mib, september.downBytes)
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), october.periodStartDay)
        assertEquals(60 * mib, october.upBytes)
        assertEquals(120 * mib, october.downBytes)

        // The next reading only adds to October.
        clock.now = Instant.parse("2026-09-30T17:30:05Z")
        report(130 * mib, 250 * mib, uptime = 109_000, reportedAt = Instant.parse("2026-09-30T17:30:00Z"))
        assertEquals(60 * mib, trafficDao.periodsOf(id)[0].upBytes)
        assertEquals(70 * mib, trafficDao.periodsOf(id)[1].upBytes)
    }

    @Test
    fun resetZone_decidesTheBoundary() = runTest {
        // Same instants as above, but reset days follow UTC: both readings are on Sep 30.
        resetZone = ZoneOffset.UTC
        clock.now = Instant.parse("2026-09-30T15:00:05Z")
        val id = vps()
        report(0, 0, uptime = 100_000, reportedAt = Instant.parse("2026-09-30T15:00:00Z"))
        clock.now = Instant.parse("2026-09-30T17:00:05Z")
        report(120 * mib, 240 * mib, uptime = 107_200, reportedAt = Instant.parse("2026-09-30T17:00:00Z"))
        val rows = trafficDao.periodsOf(id)
        assertEquals(1, rows.size)
        assertEquals(LocalDate.of(2026, 9, 1).toEpochDay(), rows[0].periodStartDay)
        assertEquals(120 * mib, rows[0].upBytes)

        // A VPS's own zone wins over the app setting.
        val stored = subs.get(id)!!
        subs.save(stored.subscription, stored.vps!!.copy(resetZoneId = "Asia/Shanghai"))
        assertEquals(shanghai, repo.zoneOf(vpsDao.getBySubscriptionId(id)!!))
    }

    @Test
    fun initialUsed_isAddedToTheCountedTraffic() = runTest {
        val id = vps()
        assertTrue(repo.setInitialUsed(id, 300 * gib))
        report(1 * gib, 1 * gib, uptime = 1_000)
        later(30)
        report(1 * gib + 100 * mib, 1 * gib + 400 * mib, uptime = 2_800)

        val item = subs.get(id)!!
        assertEquals(300 * gib, item.appPeriod!!.initialUsedBytes)
        val ui = TrafficUi.of(item.vps!!, item.appPeriod, LocalDate.of(2026, 9, 28))
        assertEquals(300 * gib + 400 * mib, ui.usage!!.usedBytes)
        assertEquals(400 * mib, ui.app!!.accumulatedUsedBytes)
        assertEquals(300 * gib, ui.app!!.initialUsedBytes)
        assertEquals(29.3, ui.usage!!.percent!!, 0.05)

        // Entering it again replaces the value, it does not add up.
        repo.setInitialUsed(id, 200 * gib)
        assertEquals(200 * gib, current(id).initialUsedBytes)
        assertEquals(400 * mib, current(id).downBytes)
    }

    @Test
    fun monthRotateAgent_rebootWithPersistentCounters_isNotCountedTwice() = runTest {
        val id = vps()
        report(300 * gib, 400 * gib, uptime = 864_000)
        later(30)
        // The machine rebooted, but the agent's --month-rotate totals continue from its file.
        report(300 * gib + 100 * mib, 400 * gib + 200 * mib, uptime = 300)
        assertEquals(100 * mib, current(id).upBytes)
        assertEquals(200 * mib, current(id).downBytes)
        assertEquals(DeltaNote.REBOOT_COUNTERS_KEPT.name, trafficDao.snapshotsOf(id).last().note)
        assertFalse(trafficDao.snapshotsOf(id).last().resetDetected)
    }

    @Test
    fun turningOnMonthRotate_largeDropIsAReset_neverNegativeOrDoubled() = runTest {
        // The real panel case: since-boot totals of hundreds of GB drop to a few MB when the agent
        // restarts with --month-rotate; the machine itself keeps running (uptime grows).
        val id = vps()
        report(685 * gib, 633 * gib, uptime = 12_186_921)
        later(30)
        report(686 * gib, 634 * gib, uptime = 12_188_721)
        assertEquals(1 * gib, current(id).upBytes)
        later(30)
        report(3 * mib, 9 * mib, uptime = 12_190_521)
        assertEquals(DeltaNote.COUNTER_RESET.name, trafficDao.snapshotsOf(id).last().note)
        assertEquals(1 * gib + 3 * mib, current(id).upBytes)
        assertEquals(1 * gib + 9 * mib, current(id).downBytes)
        later(30)
        report(10 * mib, 30 * mib, uptime = 12_192_321)
        assertEquals(1 * gib + 10 * mib, current(id).upBytes)
        assertEquals(1 * gib + 30 * mib, current(id).downBytes)
        assertTrue(trafficDao.periodsOf(id).all { it.upBytes >= 0 && it.downBytes >= 0 })
    }

    @Test
    fun smallRollbackOfPersistentCounters_isIgnored() = runTest {
        val id = vps()
        report(300 * gib, 400 * gib, uptime = 864_000)
        later(30)
        // An agent restart lost its last unsaved minutes: a slight drop without a reboot.
        report(300 * gib - 20 * mib, 400 * gib + 10 * mib, uptime = 865_800)
        assertEquals(0L, current(id).upBytes)
        assertEquals(10 * mib, current(id).downBytes)
        assertEquals(DeltaNote.SMALL_ROLLBACK.name, trafficDao.snapshotsOf(id).last().note)
    }

    @Test
    fun snapshotsOlderThan90Days_areRemovedOnRefresh() = runTest {
        val id = vps()
        report(1 * gib, 1 * gib, uptime = 1_000)
        clock.advance(Duration.ofDays(91))
        report(2 * gib, 2 * gib, uptime = 1_000 + 91 * 86_400)
        assertEquals(1, trafficDao.snapshotsOf(id).size)
    }

    @Test
    fun relinkingAnotherNode_dropsOldSnapshotsAndRestartsCounting() = runTest {
        val id = vps()
        report(1 * gib, 1 * gib, uptime = 1_000)
        later(30)
        val stored = subs.get(id)!!
        subs.save(stored.subscription, stored.vps, link = KomariLinkChange.Link("n2", "n2", null))
        assertTrue(trafficDao.snapshotsOf(id).isEmpty())
        // The other node reports much smaller counters: only a new baseline, no reset counted.
        report(5 * mib, 5 * mib, uptime = 50)
        assertEquals(0L, current(id).upBytes)
    }

    @Test
    fun observeAll_attachesTheNewestPeriod() = runTest {
        val id = vps()
        report(1 * gib, 1 * gib, uptime = 1_000)
        later(30)
        report(1 * gib + mib, 1 * gib + 2 * mib, uptime = 2_800)
        val item = subs.observeAll().first().single { it.subscription.id == id }
        assertEquals(mib, item.appPeriod!!.upBytes)
        assertEquals(current(id).toDomain(), item.appPeriod)
        val card = item.vps!!.let { TrafficUi.of(it, item.appPeriod, LocalDate.of(2026, 9, 28)) }
        assertNull(card.issue)
    }
}
