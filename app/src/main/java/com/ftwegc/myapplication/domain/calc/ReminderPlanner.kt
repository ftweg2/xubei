package com.ftwegc.myapplication.domain.calc

import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.TrafficPeriod
import com.ftwegc.myapplication.domain.model.TrafficUsage
import java.time.LocalDate

/** A notification that is due. [key] identifies it in `reminder_log` (one per subscription). */
sealed interface Reminder {
    val subscriptionId: Long
    val key: String

    /**
     * Keys to log together with [key] so that thresholds that were passed at the same time never
     * fire later (for example 80 % when usage jumped straight to 96 %).
     */
    val coveredKeys: List<String>

    /** "Due in [daysLeft] days" for the threshold [threshold] (days before the due date). */
    data class Due(
        override val subscriptionId: Long,
        val name: String,
        val dueDate: LocalDate,
        val daysLeft: Long,
        val threshold: Int,
        val priceMinor: Long,
        val currency: String,
        val isFree: Boolean,
        val autoRenew: Boolean,
        override val coveredKeys: List<String> = emptyList(),
    ) : Reminder {
        override val key: String get() = dueKey(dueDate, threshold)
    }

    /** Usage reached [threshold] percent in the period starting [period].start. */
    data class Traffic(
        override val subscriptionId: Long,
        val name: String,
        val period: TrafficPeriod,
        val threshold: Int,
        val percent: Double,
        val usedBytes: Long,
        val limitBytes: Long,
        override val coveredKeys: List<String> = emptyList(),
    ) : Reminder {
        override val key: String get() = trafficKey(period.start, threshold)
    }

    companion object {
        fun dueKey(due: LocalDate, threshold: Int): String = "due:$due:d$threshold"
        fun trafficKey(periodStart: LocalDate, threshold: Int): String = "traffic:$periodStart:$threshold"
    }
}

/**
 * Decides which reminders are due. Pure; de-duplication against `reminder_log` is the caller's job
 * (a reminder whose [Reminder.key] was logged is never sent again).
 */
object ReminderPlanner {

    /** Extra traffic thresholds on top of the per-VPS one. */
    val EXTRA_TRAFFIC_THRESHOLDS: List<Int> = listOf(95, 100)

    /**
     * Due-date reminders for [today].
     *
     * Skipped: archived, lifetime, no due date ("待补充" subscriptions have none), and due dates
     * already past. The subscription's own days win over [defaultDays]; an empty list turns
     * reminders off. With N days left, the smallest threshold `t >= N` fires, so a day the worker
     * missed (phone off) is caught up once instead of being lost; its key contains the due date,
     * so a renewal starts over.
     */
    fun dueReminders(items: List<SubscriptionWithVps>, today: LocalDate, defaultDays: List<Int>): List<Reminder.Due> =
        items.mapNotNull { dueReminder(it.subscription, today, defaultDays) }

    fun dueReminder(sub: Subscription, today: LocalDate, defaultDays: List<Int>): Reminder.Due? {
        if (sub.archived || sub.isPermanent) return null
        val due = sub.nextDue ?: return null
        val daysLeft = DueDateCalculator.daysUntil(due, today)
        if (daysLeft < 0) return null
        val days = sub.reminderDays ?: defaultDays
        val eligible = days.filter { it >= 0 && daysLeft <= it }.sorted()
        val threshold = eligible.firstOrNull() ?: return null
        return Reminder.Due(
            subscriptionId = sub.id,
            name = sub.name,
            dueDate = due,
            daysLeft = daysLeft,
            threshold = threshold,
            priceMinor = sub.priceMinor,
            currency = sub.currency,
            isFree = sub.isFree,
            autoRenew = sub.autoRenew,
            coveredKeys = eligible.drop(1).map { Reminder.dueKey(due, it) },
        )
    }

    /** Thresholds for a VPS alerting at [alertPercent]: it, 95 and 100, without those below it. */
    fun trafficThresholds(alertPercent: Int): List<Int> =
        (listOf(alertPercent) + EXTRA_TRAFFIC_THRESHOLDS).filter { it >= alertPercent }.distinct().sorted()

    /**
     * The traffic reminder for [item] with [usage] in [period], or `null`.
     *
     * Only for an active VPS with a limit and a known percentage. The highest threshold reached
     * fires; lower ones reached at the same time are covered. The key contains the period start,
     * so every period starts over.
     */
    fun trafficReminder(item: SubscriptionWithVps, usage: TrafficUsage?, period: TrafficPeriod?): Reminder.Traffic? {
        val sub = item.subscription
        val vps = item.vps ?: return null
        if (sub.archived || sub.category != Category.VPS) return null
        if (usage == null || period == null || usage.limitBytes <= 0) return null
        val percent = usage.percent ?: return null
        val reached = trafficThresholds(vps.alertPercent).filter { percent >= it }
        val threshold = reached.maxOrNull() ?: return null
        return Reminder.Traffic(
            subscriptionId = sub.id,
            name = sub.name,
            period = period,
            threshold = threshold,
            percent = percent,
            usedBytes = usage.usedBytes,
            limitBytes = usage.limitBytes,
            coveredKeys = reached.filter { it != threshold }.map { Reminder.trafficKey(period.start, it) },
        )
    }
}
