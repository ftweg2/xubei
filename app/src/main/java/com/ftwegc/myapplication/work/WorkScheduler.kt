package com.ftwegc.myapplication.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Schedules the background workers with unique names, so re-scheduling never duplicates them. */
class WorkScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    /**
     * Periodic traffic refresh every [intervalMinutes] (network required); 0 cancels it.
     *
     * @param update true when the user changed the interval: the existing work is updated
     *   ([ExistingPeriodicWorkPolicy.UPDATE]). At app start (false) an existing schedule is kept, so
     *   opening the app never postpones the next run.
     */
    fun scheduleRefresh(intervalMinutes: Int, update: Boolean) {
        if (intervalMinutes <= 0) {
            workManager.cancelUniqueWork(REFRESH_WORK)
            return
        }
        val minutes = intervalMinutes.coerceAtLeast(MIN_PERIOD_MINUTES).toLong()
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(REFRESH_WORK)
            .build()
        val policy = if (update) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
        workManager.enqueueUniquePeriodicWork(REFRESH_WORK, policy, request)
    }

    /** Daily reminder check, first run at the next [REMINDER_TIME]; kept when already scheduled. */
    fun scheduleDailyReminders(now: LocalDateTime = LocalDateTime.now()) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNext(now, REMINDER_TIME).toMinutes(), TimeUnit.MINUTES)
            .addTag(REMINDER_WORK)
            .build()
        workManager.enqueueUniquePeriodicWork(REMINDER_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Runs the reminder check once now (app start, debug trigger). */
    fun runRemindersNow() {
        workManager.enqueueUniqueWork(
            REMINDER_NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().build(),
        )
    }

    /** Runs a traffic refresh once now (debug trigger). */
    fun runRefreshNow() {
        workManager.enqueueUniqueWork(
            REFRESH_NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    companion object {
        const val REFRESH_WORK: String = "vluer-traffic-refresh"
        const val REMINDER_WORK: String = "vluer-daily-reminders"
        const val REMINDER_NOW_WORK: String = "vluer-reminders-now"
        const val REFRESH_NOW_WORK: String = "vluer-refresh-now"

        /** WorkManager's minimum periodic interval. */
        const val MIN_PERIOD_MINUTES: Int = 15

        /** Local time of the daily reminder check. */
        val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

        /** Time from [now] to the next [time] of day (tomorrow when it has passed). */
        fun delayUntilNext(now: LocalDateTime, time: LocalTime): Duration {
            var next = now.toLocalDate().atTime(time)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
