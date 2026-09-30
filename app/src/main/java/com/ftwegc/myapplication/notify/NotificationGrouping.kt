package com.ftwegc.myapplication.notify

/**
 * Decides the summary of one notification group (pure, for unit tests).
 *
 * Android bundles an app's notifications as soon as two are showing. Rows of our own group with
 * our own summary are left alone; rows without a summary would be regrouped under a system summary
 * that only opens the launcher (and on newer Android versions a group without a summary counts as
 * invalid). So every group that has a row also has a summary:
 *
 * - No row left: an existing summary is removed ([Plan.Remove]); nothing to do otherwise.
 * - One or more rows: the summary lists the titles, newest first ([Plan.Show]). When every row
 *   belongs to the same subscription a tap on the collapsed group opens that detail page; with
 *   several subscriptions Android cannot tell which line was tapped, so it opens the overview,
 *   which lists them, and the rows stay (expanded, each row opens its own detail page).
 */
object NotificationGrouping {

    /** InboxStyle shows at most this many lines. */
    const val MAX_LINES: Int = 5

    /** A reminder row that is showing; [postedAt] orders the summary lines. */
    data class Row(val id: Int, val subscriptionId: Long, val title: String, val postedAt: Long = 0)

    /** How long a row this process posted counts even when the system does not list it yet. */
    const val RECENT_WINDOW_MILLIS: Long = 5_000

    /**
     * Rows this process posted in the last [windowMillis]. Posting is asynchronous: right after
     * `notify()` the system may not list the row yet, and reminders are posted in a quick loop,
     * so the summary of the second one would otherwise count only one row.
     */
    class RecentRows(private val windowMillis: Long = RECENT_WINDOW_MILLIS) {
        private val rows = LinkedHashMap<Int, Row>()

        @Synchronized
        fun add(row: Row) {
            rows.remove(row.id)
            rows[row.id] = row
        }

        @Synchronized
        fun clear() = rows.clear()

        /** [showing] (what the system lists) plus the recent rows it does not list yet, oldest first. */
        @Synchronized
        fun merge(showing: List<Row>, now: Long): List<Row> {
            rows.values.removeAll { now - it.postedAt > windowMillis }
            val recentIds = rows.keys
            return (showing.filterNot { it.id in recentIds } + rows.values).sortedBy { it.postedAt }
        }
    }

    sealed interface Plan {
        data object Keep : Plan

        data object Remove : Plan

        /** @property openSubscriptionId the detail page a tap opens; `null` opens the overview. */
        data class Show(val count: Int, val lines: List<String>, val openSubscriptionId: Long?) : Plan
    }

    /** [rows] in posting order (the last one is the newest); duplicates by id count once. */
    fun plan(rows: List<Row>, summaryShowing: Boolean): Plan {
        val distinct = rows.asReversed().distinctBy { it.id }
        if (distinct.isEmpty()) return if (summaryShowing) Plan.Remove else Plan.Keep
        val open = distinct.map { it.subscriptionId }.distinct().singleOrNull()?.takeIf { it > 0 }
        val lines = distinct.map { it.title }.filter { it.isNotBlank() }.take(MAX_LINES)
        return Plan.Show(distinct.size, lines, open)
    }
}
