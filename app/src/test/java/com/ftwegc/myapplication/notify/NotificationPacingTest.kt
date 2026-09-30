package com.ftwegc.myapplication.notify

import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.repo.DirectTransactionRunner
import com.ftwegc.myapplication.data.repo.FakePreferencesDataStore
import com.ftwegc.myapplication.data.repo.FakeReminderLogDao
import com.ftwegc.myapplication.data.repo.FakeSubscriptionDao
import com.ftwegc.myapplication.data.repo.FakeTables
import com.ftwegc.myapplication.data.repo.FakeTrafficDao
import com.ftwegc.myapplication.data.repo.FakeVpsDao
import com.ftwegc.myapplication.data.repo.MutableClock
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.domain.calc.Reminder
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

/**
 * A batch of reminders is posted row by row, then the group summary once; the pace keeps every
 * summary update below the rate at which Android drops updates.
 */
class NotificationPacingTest {

    /**
     * The per-app rate estimate of Android's NotificationManagerService (RateEstimator: the gap
     * between events averaged with weight 0.7 on the newest, at least 0.5 ms). Updates of a
     * showing notification are dropped while the rate is above [LIMIT].
     */
    private class SystemRate {
        private var last: Long? = null
        private var interarrival = 1000.0

        private fun estimate(now: Long): Double = 0.3 * interarrival + 0.7 * max(0.0005, (now - (last ?: now)) / 1000.0)

        fun rateAt(now: Long): Double = if (last == null) 0.0 else 1.0 / estimate(now)

        fun enqueue(now: Long) {
            if (last != null) interarrival = estimate(now)
            last = now
        }

        companion object {
            const val LIMIT = 5.0
        }
    }

    /** Posts [rows] rows and [summaries] summary updates through [pacer] from [start]; returns the highest rate an update saw. */
    private fun simulate(pacer: NotificationPacer?, system: SystemRate, start: Long, rows: Int, summaries: Int): Pair<Double, Long> {
        var now = start
        var worst = 0.0
        repeat(rows) {
            now += pacer?.reserve(now, update = false) ?: 1
            system.enqueue(now)
        }
        repeat(summaries) {
            now += pacer?.reserve(now, update = true) ?: 1
            worst = maxOf(worst, system.rateAt(now))
            system.enqueue(now)
        }
        return worst to now
    }

    @Test
    fun unpacedSummaryUpdatesAreDropped_pacedOnesAreNot() {
        // The old way (a row, then its summary, a few milliseconds apart) goes far above the limit.
        // (Enough rows for the estimate to forget its idle start value.)
        val (unpaced, _) = simulate(null, SystemRate(), 0, rows = 20, summaries = 1)
        assertTrue("unpaced rate $unpaced", unpaced > SystemRate.LIMIT)

        val system = SystemRate()
        val pacer = NotificationPacer()
        // Twenty rows and both summaries, then another batch right away.
        val (first, end) = simulate(pacer, system, 10_000, rows = 20, summaries = 2)
        val (second, _) = simulate(pacer, system, end + 5, rows = 3, summaries = 2)
        assertTrue("first batch $first", first <= SystemRate.LIMIT)
        assertTrue("second batch $second", second <= SystemRate.LIMIT)
    }

    @Test
    fun pacerSpacesRowsAndUpdates() {
        val pacer = NotificationPacer()
        assertEquals(0L, pacer.reserve(1_000, update = false))
        // A second row 10 ms later waits for the row gap.
        assertEquals(NotificationPacer.ROW_GAP_MILLIS - 10, pacer.reserve(1_010, update = false))
        // The summary waits for the update gap after that row.
        val rowAt = 1_000 + NotificationPacer.ROW_GAP_MILLIS
        assertEquals(NotificationPacer.UPDATE_GAP_MILLIS, pacer.reserve(rowAt, update = true))
        // Long after: no wait.
        assertEquals(0L, pacer.reserve(60_000, update = true))
    }

    // ------------------------------------------------------------------ one batch per check

    private class BatchNotifier : ReminderNotifier {
        val batches = mutableListOf<List<Reminder>>()
        override fun canNotify(): Boolean = true
        override fun notify(reminder: Reminder) = notifyAll(listOf(reminder))
        override fun notifyAll(reminders: List<Reminder>) {
            batches += reminders
        }
    }

    @Test
    fun serviceSendsTheRemindersOfOneCheckAsOneBatch() = runTest {
        val clock = MutableClock(Instant.parse("2026-09-28T01:00:00Z"), ZoneId.of("Asia/Shanghai"))
        val tables = FakeTables()
        val repo = SubscriptionRepository(FakeSubscriptionDao(tables), FakeVpsDao(tables), DirectTransactionRunner(), clock, FakeTrafficDao())
        val notifier = BatchNotifier()
        val service = ReminderService(repo, SettingsStore(FakePreferencesDataStore()), FakeReminderLogDao(), notifier, clock)
        fun sub(name: String, due: LocalDate) =
            Subscription(name = name, category = Category.AI, priceMinor = 2_000, currency = "CNY", cycle = BillingCycle.MONTHLY, nextDue = due)
        repo.save(sub("A", LocalDate.of(2026, 10, 1)), null) // 3 days
        repo.save(sub("B", LocalDate.of(2026, 9, 29)), null) // 1 day
        repo.save(sub("C", LocalDate.of(2026, 9, 28)), null) // today

        assertEquals(3, service.checkDue().size)
        assertEquals(1, notifier.batches.size)
        assertEquals(listOf("A", "B", "C"), notifier.batches.single().map { (it as Reminder.Due).name }.sorted())
        // Nothing new: no empty batch either.
        assertEquals(0, service.checkDue().size)
        assertEquals(1, notifier.batches.size)
    }
}
