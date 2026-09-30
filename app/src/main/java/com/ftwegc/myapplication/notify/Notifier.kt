package com.ftwegc.myapplication.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import com.ftwegc.myapplication.MainActivity
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.domain.calc.Reminder
import com.ftwegc.myapplication.ui.format.Formatters
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Android notifications for [Reminder]s: two channels (due dates, traffic), tapping opens the
 * subscription's detail page ([MainActivity] reads [EXTRA_SUBSCRIPTION_ID]).
 *
 * Every reminder is posted in its channel's own group ([GROUP_DUE] / [GROUP_TRAFFIC]) with an
 * app-provided summary ([NotificationGrouping]). Without that, Android bundles two or more
 * notifications of the app into a system group whose summary opens the launcher activity and is
 * auto-cancel: tapping a line of the collapsed bundle (a tap on the summary) only brought the app
 * to the front and cleared every row. Each row's [PendingIntent] has its own request code and data URI, so the system can
 * never merge the intents of two rows.
 */
class Notifier(context: Context) : ReminderNotifier {

    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    /** Creates the channels (idempotent; names follow the current language). */
    fun ensureChannels() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_DUE, context.getString(R.string.notify_channel_due), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notify_channel_due_desc)
            },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_TRAFFIC, context.getString(R.string.notify_channel_traffic), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notify_channel_traffic_desc)
            },
        )
    }

    override fun canNotify(): Boolean = notificationsAllowed(context)

    override fun notify(reminder: Reminder) = notifyAll(listOf(reminder))

    /**
     * Posts [reminders] as one batch: every row first (paced, see [NotificationPacer]), then each
     * touched group's summary once, after the last row. Updating the summary after every row made
     * the system drop the later updates ("Shedding … group_summary") and the count stayed wrong.
     * Blocks while pacing; called from the background workers.
     */
    override fun notifyAll(reminders: List<Reminder>) {
        if (reminders.isEmpty() || !canNotify()) return
        ensureChannels()
        val touched = LinkedHashSet<String>()
        try {
            for (reminder in reminders) {
                pace(update = false)
                touched += postRow(reminder)
            }
            for (channel in touched) {
                pace(update = true)
                updateSummary(channel)
            }
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post: nothing to do.
        }
    }

    /** Waits for the next free slot of the process-wide [pacer]. */
    private fun pace(update: Boolean) {
        val wait = pacer.reserve(SystemClock.elapsedRealtime(), update)
        if (wait > 0) Thread.sleep(wait)
    }

    /** Posts one reminder row; returns its channel. */
    private fun postRow(reminder: Reminder): String {
        val (channel, title, text) = when (reminder) {
            is Reminder.Due -> Triple(CHANNEL_DUE, dueTitle(reminder), dueText(reminder))
            is Reminder.Traffic -> Triple(CHANNEL_TRAFFIC, trafficTitle(reminder), trafficText(reminder))
        }
        val id = notificationId(reminder)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_cup)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(groupOf(channel))
            // Only the new row alerts, never the summary.
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .addExtras(bundleOf(EXTRA_SUBSCRIPTION_ID to reminder.subscriptionId))
            .setContentIntent(openIntent(reminder.subscriptionId, id))
            .build()
        try {
            manager.notify(id, notification)
            recentRows(channel).add(NotificationGrouping.Row(id, reminder.subscriptionId, title, System.currentTimeMillis()))
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post: nothing to do.
        }
        return channel
    }

    /**
     * Brings both summaries in line with the rows that are showing (an empty summary is removed).
     * Called (off the main thread: it paces) when the app opens from a notification.
     */
    fun refreshSummaries() {
        try {
            pace(update = true)
            updateSummary(CHANNEL_DUE)
            pace(update = true)
            updateSummary(CHANNEL_TRAFFIC)
        } catch (_: SecurityException) {
            // Permission revoked meanwhile: nothing to tidy.
        }
    }

    /** Posts, updates or removes the summary of [channel]'s group from the rows that are showing. */
    private fun updateSummary(channel: String) {
        val group = groupOf(channel)
        val active = activeNotifications()
        val showing = active.filter { it.notification.group == group && it.tag == null && !it.isSummary() }.map { it.toRow() }
        val rows = recentRows(channel).merge(showing, System.currentTimeMillis())
        val summaryShowing = active.any { it.tag == SUMMARY_TAG && it.id == summaryId(channel) }
        when (val plan = NotificationGrouping.plan(rows, summaryShowing)) {
            NotificationGrouping.Plan.Keep -> Unit
            NotificationGrouping.Plan.Remove -> manager.cancel(SUMMARY_TAG, summaryId(channel))
            is NotificationGrouping.Plan.Show -> postSummary(channel, plan)
        }
    }

    private fun postSummary(channel: String, plan: NotificationGrouping.Plan.Show) {
        val title = context.getString(
            if (channel == CHANNEL_TRAFFIC) R.string.notify_summary_traffic_title else R.string.notify_summary_due_title,
            plan.count,
        )
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        plan.lines.forEach { style.addLine(it) }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_cup)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(plan.lines.firstOrNull().orEmpty())
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setNumber(plan.count)
            .setGroup(groupOf(channel))
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
        val requestCode = SUMMARY_REQUEST_BASE - summaryId(channel)
        val single = plan.openSubscriptionId
        if (single != null) {
            // Every row is about this subscription: open it and clear them all.
            builder.setContentIntent(openIntent(single, requestCode)).setAutoCancel(true)
        } else {
            // Several subscriptions: a tap on the collapsed group cannot say which line was meant.
            // Open the overview (it lists them) and keep the rows, each still opens its own page.
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    requestCode,
                    openHomeIntent(context).putExtra(EXTRA_OPEN_TOKEN, System.currentTimeMillis()),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        try {
            manager.notify(SUMMARY_TAG, summaryId(channel), builder.build())
        } catch (_: SecurityException) {
            // Permission revoked meanwhile: the rows are not showing either.
        }
    }

    private fun activeNotifications(): List<StatusBarNotification> =
        context.getSystemService(NotificationManager::class.java)?.activeNotifications?.toList().orEmpty()

    private fun StatusBarNotification.isSummary(): Boolean = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

    private fun StatusBarNotification.toRow(): NotificationGrouping.Row {
        val extras = notification.extras
        return NotificationGrouping.Row(
            id = id,
            subscriptionId = extras.getLong(EXTRA_SUBSCRIPTION_ID, 0L),
            title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString().orEmpty(),
            postedAt = postTime,
        )
    }

    private fun openIntent(subscriptionId: Long, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            openActivityIntent(context, subscriptionId).putExtra(EXTRA_OPEN_TOKEN, System.currentTimeMillis()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun dueTitle(r: Reminder.Due): String = when (r.daysLeft) {
        0L -> context.getString(R.string.notify_due_today_title, Formatters.shortName(r.name))
        else -> context.getString(R.string.notify_due_title, Formatters.shortName(r.name), r.daysLeft.toInt())
    }

    private fun dueText(r: Reminder.Due): String {
        val date = formatDate(r.dueDate)
        val amount = when {
            r.isFree -> context.getString(R.string.free_tag)
            r.priceMinor > 0 -> Formatters.formatAmount(r.priceMinor, r.currency)
            else -> null
        }
        val base = if (amount != null) context.getString(R.string.notify_due_text, date, amount) else context.getString(R.string.notify_due_text_no_price, date)
        return if (r.autoRenew) base + context.getString(R.string.notify_due_auto_renew_suffix) else base
    }

    private fun trafficTitle(r: Reminder.Traffic): String =
        context.getString(R.string.notify_traffic_title, Formatters.shortName(r.name), Formatters.formatPercent(r.percent))

    private fun trafficText(r: Reminder.Traffic): String = context.getString(
        R.string.notify_traffic_text,
        Formatters.formatBytes(r.usedBytes),
        Formatters.formatBytes(r.limitBytes),
        formatDate(r.period.start),
        formatDate(r.period.endExclusive.minusDays(1)),
    )

    private fun formatDate(date: LocalDate): String {
        val today = LocalDate.now()
        val pattern = context.getString(if (date.year == today.year) R.string.date_pattern_same_year else R.string.date_pattern_full)
        return DateTimeFormatter.ofPattern(pattern, Locale.CHINA).format(date)
    }

    companion object {
        const val CHANNEL_DUE: String = "due_reminders"
        const val CHANNEL_TRAFFIC: String = "traffic_alerts"
        const val GROUP_DUE: String = "com.ftwegc.myapplication.group.DUE"
        const val GROUP_TRAFFIC: String = "com.ftwegc.myapplication.group.TRAFFIC"

        /** Tag of the two group summaries; reminder rows are posted without a tag. */
        const val SUMMARY_TAG: String = "group_summary"
        const val ACTION_OPEN_SUBSCRIPTION: String = "com.ftwegc.myapplication.OPEN_SUBSCRIPTION"
        const val ACTION_OPEN_HOME: String = "com.ftwegc.myapplication.OPEN_HOME"
        const val EXTRA_SUBSCRIPTION_ID: String = "subscription_id"

        /** When the notification was posted; tells a new tap from a restored launch intent. */
        const val EXTRA_OPEN_TOKEN: String = "open_token"

        /** Summary request codes are negative; rows use their (non-negative) notification id. */
        private const val SUMMARY_REQUEST_BASE: Int = -10

        fun groupOf(channel: String): String = if (channel == CHANNEL_TRAFFIC) GROUP_TRAFFIC else GROUP_DUE

        /** One pace for every [Notifier] of the process (the system counts per app). */
        private val pacer = NotificationPacer()

        /** Process-wide, so every [Notifier] instance sees the rows the others just posted. */
        private val recentDue = NotificationGrouping.RecentRows()
        private val recentTraffic = NotificationGrouping.RecentRows()

        private fun recentRows(channel: String) = if (channel == CHANNEL_TRAFFIC) recentTraffic else recentDue

        /** Forgets the recently posted rows (tests cancel everything between cases). */
        @VisibleForTesting
        internal fun forgetRecentRows() {
            recentDue.clear()
            recentTraffic.clear()
        }

        /** Summary ids, posted under [SUMMARY_TAG] so they never collide with a row. */
        fun summaryId(channel: String): Int = if (channel == CHANNEL_TRAFFIC) 2 else 1

        /** One notification per subscription and kind; a newer one replaces the older. */
        fun notificationId(reminder: Reminder): Int {
            val base = (reminder.subscriptionId % (Int.MAX_VALUE / 2)).toInt() * 2
            return if (reminder is Reminder.Traffic) base + 1 else base
        }

        /**
         * The activity intent a notification opens. The data URI names the subscription: extras
         * do not count when the system compares intents, so without it two subscriptions' intents
         * would be "equal" and could be merged.
         */
        fun openActivityIntent(context: Context, subscriptionId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_SUBSCRIPTION)
                .setData("vluer://subscription/$subscriptionId".toUri())
                .putExtra(EXTRA_SUBSCRIPTION_ID, subscriptionId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** The activity intent of a summary that covers several subscriptions: the overview tab. */
        fun openHomeIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_HOME)
                .setData("vluer://home".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** True when the app may post (permission on Android 13+, and not blocked by the user). */
        fun notificationsAllowed(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}
