package com.ftwegc.myapplication.notify

import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.repo.DirectTransactionRunner
import com.ftwegc.myapplication.data.repo.FakePreferencesDataStore
import com.ftwegc.myapplication.data.repo.FakeReminderLogDao
import com.ftwegc.myapplication.data.repo.FakeSubscriptionDao
import com.ftwegc.myapplication.data.repo.FakeTables
import com.ftwegc.myapplication.data.repo.FakeTrafficDao
import com.ftwegc.myapplication.data.repo.FakeVpsDao
import com.ftwegc.myapplication.data.repo.KomariLinkChange
import com.ftwegc.myapplication.data.repo.MutableClock
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.domain.calc.Reminder
import com.ftwegc.myapplication.domain.calc.ReminderPlanner
import com.ftwegc.myapplication.domain.calc.TrafficCalculator
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.TrafficPeriod
import com.ftwegc.myapplication.domain.model.VpsConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReminderTest {

    private val today = LocalDate.of(2026, 9, 28)
    private val defaults = listOf(7, 3, 1, 0)

    private fun sub(
        due: LocalDate?,
        id: Long = 1,
        price: Long = 2_000,
        archived: Boolean = false,
        permanent: Boolean = false,
        days: List<Int>? = null,
    ) = Subscription(
        id = id, name = "S$id", category = Category.AI, priceMinor = price, currency = "USD",
        cycle = BillingCycle.MONTHLY, nextDue = due, archived = archived, isPermanent = permanent, reminderDays = days,
    )

    // ------------------------------------------------------------------ Planner: due dates

    @Test
    fun due_firesOnConfiguredDays() {
        assertEquals(7, ReminderPlanner.dueReminder(sub(today.plusDays(7)), today, defaults)!!.threshold)
        assertEquals(3, ReminderPlanner.dueReminder(sub(today.plusDays(3)), today, defaults)!!.threshold)
        assertEquals(1, ReminderPlanner.dueReminder(sub(today.plusDays(1)), today, defaults)!!.threshold)
        val onDay = ReminderPlanner.dueReminder(sub(today), today, defaults)!!
        assertEquals(0, onDay.threshold)
        assertEquals("due:2026-09-28:d0", onDay.key)
        assertNull("8 days: not yet", ReminderPlanner.dueReminder(sub(today.plusDays(8)), today, defaults))
    }

    @Test
    fun due_missedDayIsCaughtUpOnce_withTheSmallestFittingThreshold() {
        // Phone was off on the "3 days" day; with 2 days left the 3-day reminder goes out.
        val r = ReminderPlanner.dueReminder(sub(today.plusDays(2)), today, defaults)!!
        assertEquals(3, r.threshold)
        assertEquals(2L, r.daysLeft)
        assertEquals(listOf("due:2026-09-30:d7"), r.coveredKeys)
    }

    @Test
    fun due_skipped_forIncompleteArchivedPermanentPastAndOptOut() {
        assertNull("no due date (待补充)", ReminderPlanner.dueReminder(sub(null, price = 0), today, defaults))
        assertNull(ReminderPlanner.dueReminder(sub(today.plusDays(1), archived = true), today, defaults))
        assertNull(ReminderPlanner.dueReminder(sub(today.plusDays(1), permanent = true), today, defaults))
        assertNull("already expired", ReminderPlanner.dueReminder(sub(today.minusDays(1)), today, defaults))
        assertNull("own days: none", ReminderPlanner.dueReminder(sub(today.plusDays(1), days = emptyList()), today, defaults))
    }

    @Test
    fun due_ownDaysOverrideTheDefault() {
        val own = sub(today.plusDays(14), days = listOf(14, 2))
        assertEquals(14, ReminderPlanner.dueReminder(own, today, defaults)!!.threshold)
        // 7 days left: the default's 7-day threshold does not apply; the own 14-day one (caught up) does.
        assertEquals(14, ReminderPlanner.dueReminder(own.copy(nextDue = today.plusDays(7)), today, defaults)!!.threshold)
        assertEquals(2, ReminderPlanner.dueReminder(own.copy(nextDue = today.plusDays(1)), today, defaults)!!.threshold)
        assertNull("default off", ReminderPlanner.dueReminder(sub(today.plusDays(3)), today, emptyList()))
    }

    // ------------------------------------------------------------------ Planner: traffic

    private val gib = 1L shl 30
    private val period = TrafficPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1))

    private fun vpsItem(alert: Int = 80, archived: Boolean = false) = SubscriptionWithVps(
        sub(today.plusDays(20), archived = archived).copy(category = Category.VPS),
        VpsConfig(subscriptionId = 1, komariUuid = "n", trafficLimitBytes = 100 * gib, alertPercent = alert),
    )

    private fun usage(usedGib: Long, limit: Long = 100 * gib) = TrafficCalculator.usage(0, usedGib * gib, limit, TrafficLimitType.MAX)

    @Test
    fun traffic_thresholds() {
        assertEquals(listOf(80, 95, 100), ReminderPlanner.trafficThresholds(80))
        assertEquals(listOf(95, 100), ReminderPlanner.trafficThresholds(95))
        assertEquals(listOf(100), ReminderPlanner.trafficThresholds(100))
        assertEquals(listOf(50, 95, 100), ReminderPlanner.trafficThresholds(50))
    }

    @Test
    fun traffic_highestReachedFires_lowerOnesAreCovered() {
        assertNull(ReminderPlanner.trafficReminder(vpsItem(), usage(79), period))
        val at80 = ReminderPlanner.trafficReminder(vpsItem(), usage(80), period)!!
        assertEquals(80, at80.threshold)
        assertEquals("traffic:2026-09-01:80", at80.key)
        val jump = ReminderPlanner.trafficReminder(vpsItem(), usage(96), period)!!
        assertEquals(95, jump.threshold)
        assertEquals(listOf("traffic:2026-09-01:80"), jump.coveredKeys)
        assertEquals(100, ReminderPlanner.trafficReminder(vpsItem(), usage(130), period)!!.threshold)
    }

    @Test
    fun traffic_skippedWithoutLimitOrData() {
        assertNull("unlimited", ReminderPlanner.trafficReminder(vpsItem(), usage(500, limit = 0), period))
        assertNull(ReminderPlanner.trafficReminder(vpsItem(), null, period))
        assertNull(ReminderPlanner.trafficReminder(vpsItem(archived = true), usage(99), period))
    }

    // ------------------------------------------------------------------ Service: de-duplication

    private class RecordingNotifier : ReminderNotifier {
        var allowed = true
        val sent = mutableListOf<Reminder>()
        override fun canNotify(): Boolean = allowed
        override fun notify(reminder: Reminder) {
            sent += reminder
        }
    }

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val clock = MutableClock(Instant.parse("2026-09-28T01:00:00Z"), shanghai)
    private val tables = FakeTables()
    private val trafficDao = FakeTrafficDao()
    private val repo = SubscriptionRepository(FakeSubscriptionDao(tables), FakeVpsDao(tables), DirectTransactionRunner(), clock, trafficDao)
    private val settings = SettingsStore(FakePreferencesDataStore())
    private val log = FakeReminderLogDao()
    private val notifier = RecordingNotifier()
    private val service = ReminderService(repo, settings, log, notifier, clock)

    @Test
    fun service_sendsEachDueReminderOnce_andAgainAfterRenewal() = runTest {
        val id = repo.save(sub(LocalDate.of(2026, 10, 1), id = 0), null) // 3 days left
        assertEquals(1, service.checkDue().size)
        assertEquals(0, service.checkDue().size)
        assertEquals(1, notifier.sent.size)
        assertTrue(log.exists(id, "due:2026-10-01:d3"))

        // Two days later: the 1-day reminder.
        clock.advance(Duration.ofDays(2))
        assertEquals(1, (service.checkDue().single() as Reminder.Due).threshold)
        assertEquals(0, service.checkDue().size)

        // Renewed to November: the new due date starts over.
        val stored = repo.get(id)!!
        repo.save(stored.subscription.copy(nextDue = LocalDate.of(2026, 11, 1)), null)
        clock.advance(Duration.ofDays(29)) // 2026-10-29: 3 days left
        assertEquals(1, service.checkDue().size)
        assertEquals(3, notifier.sent.size)
    }

    @Test
    fun service_withNotificationsOff_sendsAndLogsNothing() = runTest {
        repo.save(sub(LocalDate.of(2026, 9, 29), id = 0), null)
        notifier.allowed = false
        assertEquals(0, service.checkDue().size)
        assertTrue(log.rows.isEmpty())
        notifier.allowed = true
        assertEquals("sent once allowed", 1, service.checkDue().size)
    }

    @Test
    fun service_trafficReminderPerPeriod_restartsInTheNextPeriod() = runTest {
        val id = repo.save(
            Subscription(name = "VPS", category = Category.VPS, priceMinor = 100, currency = "CNY", cycle = BillingCycle.MONTHLY),
            VpsConfig(trafficLimitBytes = 100 * gib, alertPercent = 80, resetDay = 1),
            link = KomariLinkChange.Link("n1", "n1", null),
        )
        // Mode 1 reading at 85 %.
        tables.vps.value = tables.vps.value + (id to tables.vps.value.getValue(id).copy(
            lastTotalUp = 0, lastTotalDown = 85 * gib, lastReportedAt = clock.millis(),
        ))
        assertEquals(80, (service.checkTraffic().single() as Reminder.Traffic).threshold)
        assertEquals(0, service.checkTraffic().size)

        // 97 %: the 95 % reminder, once.
        tables.vps.value = tables.vps.value + (id to tables.vps.value.getValue(id).copy(lastTotalDown = 97 * gib))
        assertEquals(95, (service.checkTraffic().single() as Reminder.Traffic).threshold)
        assertEquals(0, service.checkTraffic().size)

        // Next period (reset day 1): 85 % again notifies again.
        clock.now = Instant.parse("2026-10-02T01:00:00Z")
        tables.vps.value = tables.vps.value + (id to tables.vps.value.getValue(id).copy(lastTotalDown = 85 * gib))
        val next = service.checkTraffic().single() as Reminder.Traffic
        assertEquals(80, next.threshold)
        assertEquals(LocalDate.of(2026, 10, 1), next.period.start)
    }

    @Test
    fun service_unlimitedVps_neverNotifies() = runTest {
        val id = repo.save(
            Subscription(name = "VPS", category = Category.VPS, priceMinor = 100, currency = "CNY", cycle = BillingCycle.MONTHLY),
            VpsConfig(trafficLimitBytes = 0),
            link = KomariLinkChange.Link("n1", "n1", null),
        )
        tables.vps.value = tables.vps.value + (id to tables.vps.value.getValue(id).copy(
            lastTotalUp = 0, lastTotalDown = 5_000 * gib, lastReportedAt = clock.millis(),
        ))
        assertEquals(0, service.checkTraffic().size)
    }
}
