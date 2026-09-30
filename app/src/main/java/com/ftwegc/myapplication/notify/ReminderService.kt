package com.ftwegc.myapplication.notify

import com.ftwegc.myapplication.data.local.dao.ReminderLogDao
import com.ftwegc.myapplication.data.local.entity.ReminderLogEntity
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.domain.calc.Reminder
import com.ftwegc.myapplication.domain.calc.ReminderPlanner
import com.ftwegc.myapplication.domain.calc.TrafficResolver
import com.ftwegc.myapplication.domain.model.Category
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** Posts reminders; implemented with Android notifications, faked in tests. */
interface ReminderNotifier {
    /** False when the user turned notifications off (or has not granted the permission). */
    fun canNotify(): Boolean

    fun notify(reminder: Reminder)

    /**
     * Posts a batch of reminders found by one check. The Android notifier posts every row first
     * and updates the group summaries once at the end (see `Notifier.notifyAll`).
     */
    fun notifyAll(reminders: List<Reminder>) {
        reminders.forEach(::notify)
    }
}

/**
 * Finds due reminders ([ReminderPlanner]) and sends each one at most once: a reminder is logged
 * in `reminder_log` under its key before it is posted, so two workers running at the same time
 * cannot both send it. While notifications are off nothing is logged, so a reminder that is
 * still relevant goes out once they are turned on.
 */
class ReminderService(
    private val subscriptions: SubscriptionRepository,
    private val settingsStore: SettingsStore,
    private val log: ReminderLogDao,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
    private val resetZone: suspend () -> ZoneId = { clock.zone },
) {

    /** Sends due-date reminders; returns the ones sent. */
    suspend fun checkDue(): List<Reminder> {
        val settings = settingsStore.settings.first()
        val today = LocalDate.now(clock)
        val items = subscriptions.observeAll().first()
        val sent = deliver(ReminderPlanner.dueReminders(items, today, settings.reminderDays))
        log.deleteBefore(clock.millis() - LOG_RETENTION.toMillis())
        return sent
    }

    /** Sends traffic threshold reminders for the latest readings; returns the ones sent. */
    suspend fun checkTraffic(): List<Reminder> {
        val today = LocalDate.now(clock.withZone(resetZone()))
        val items = subscriptions.observeAll().first()
        val reminders = items.mapNotNull { item ->
            val vps = item.vps ?: return@mapNotNull null
            if (item.subscription.category != Category.VPS) return@mapNotNull null
            val resolved = TrafficResolver.resolve(vps, item.appPeriod, today)
            ReminderPlanner.trafficReminder(item, resolved.usage, resolved.period)
        }
        return deliver(reminders)
    }

    private suspend fun deliver(reminders: List<Reminder>): List<Reminder> {
        if (reminders.isEmpty() || !notifier.canNotify()) return emptyList()
        val now = clock.millis()
        val sent = mutableListOf<Reminder>()
        for (reminder in reminders) {
            val inserted = log.insertIfAbsent(ReminderLogEntity(subscriptionId = reminder.subscriptionId, key = reminder.key, createdAt = now))
            if (inserted == -1L) continue
            reminder.coveredKeys.forEach { key ->
                log.insertIfAbsent(ReminderLogEntity(subscriptionId = reminder.subscriptionId, key = key, createdAt = now))
            }
            sent += reminder
        }
        // One batch: the rows go out one after another and the group summary is updated once.
        if (sent.isNotEmpty()) notifier.notifyAll(sent)
        return sent
    }

    companion object {
        /** Log entries older than this are removed; every key contains a date, so this is safe. */
        val LOG_RETENTION: Duration = Duration.ofDays(400)
    }
}
