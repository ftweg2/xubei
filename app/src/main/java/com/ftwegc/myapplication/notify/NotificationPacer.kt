package com.ftwegc.myapplication.notify

/**
 * Keeps the app's notification posts below the rate Android accepts.
 *
 * `NotificationManagerService` estimates each app's enqueue rate from the gaps between its posts
 * (a moving average that weighs the newest gap 0.7) and drops *updates* of a notification that is
 * already showing while that rate is above 5 per second ("Package enqueue rate is … Shedding
 * events"). New notifications are never dropped, but an update of the group summary right after a
 * row is: the summary then kept an old count.
 *
 * So rows are posted at least [rowGapMillis] apart (4 per second at most) and an update waits
 * [updateGapMillis] after the previous post, which brings the estimate below 5 per second even
 * right after a quick burst. Pure (time is passed in) and thread safe; one instance per process.
 */
class NotificationPacer(
    private val rowGapMillis: Long = ROW_GAP_MILLIS,
    private val updateGapMillis: Long = UPDATE_GAP_MILLIS,
) {
    /** When the last post was (or will be) sent, `null` before the first one. */
    private var last: Long? = null

    /**
     * Reserves the next slot for a post at [now] (monotonic millis) and returns how long to wait
     * first. [update]: the post replaces a notification that is showing (the summary).
     */
    @Synchronized
    fun reserve(now: Long, update: Boolean): Long {
        val previous = last
        val gap = if (update) updateGapMillis else rowGapMillis
        val wait = if (previous == null) 0L else (previous + gap - now).coerceAtLeast(0L)
        last = now + wait
        return wait
    }

    companion object {
        /** Rows: at most 4 per second. */
        const val ROW_GAP_MILLIS: Long = 250

        /** An update waits this long after the previous post. */
        const val UPDATE_GAP_MILLIS: Long = 350
    }
}
