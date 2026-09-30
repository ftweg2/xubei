package com.ftwegc.myapplication.notify

import com.ftwegc.myapplication.notify.NotificationGrouping.Plan
import com.ftwegc.myapplication.notify.NotificationGrouping.Row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationGroupingTest {

    @Test
    fun noRowsRemovesAnExistingSummary() {
        assertEquals(Plan.Remove, NotificationGrouping.plan(emptyList(), summaryShowing = true))
        assertEquals(Plan.Keep, NotificationGrouping.plan(emptyList(), summaryShowing = false))
    }

    @Test
    fun oneRowGetsASummaryThatOpensItsSubscription() {
        // Android 16 treats a group without a summary as invalid and bundles it again.
        val plan = NotificationGrouping.plan(listOf(Row(2, 1, "A 还有 1 天到期")), summaryShowing = false)
        assertEquals(Plan.Show(1, listOf("A 还有 1 天到期"), 1L), plan)
    }

    @Test
    fun severalSubscriptionsGetASummaryWithoutASingleTarget() {
        val plan = NotificationGrouping.plan(
            listOf(Row(2, 1, "A"), Row(4, 2, "B")),
            summaryShowing = false,
        ) as Plan.Show
        assertEquals(2, plan.count)
        assertNull(plan.openSubscriptionId)
        // Newest first.
        assertEquals(listOf("B", "A"), plan.lines)
    }

    @Test
    fun dueAndTrafficRowsOfOneSubscriptionOpenIt() {
        val plan = NotificationGrouping.plan(listOf(Row(2, 1, "A 到期"), Row(3, 1, "A 流量")), summaryShowing = true) as Plan.Show
        assertEquals(1L, plan.openSubscriptionId)
    }

    @Test
    fun aRepostedRowCountsOnceAndLinesAreCapped() {
        val rows = (1..7).map { Row(it * 2, it.toLong(), "R$it") } + Row(2, 1, "R1 新")
        val plan = NotificationGrouping.plan(rows, summaryShowing = true) as Plan.Show
        assertEquals(7, plan.count)
        assertEquals(NotificationGrouping.MAX_LINES, plan.lines.size)
        assertEquals("R1 新", plan.lines.first())
    }

    @Test
    fun recentRows_countRowsTheSystemDoesNotListYet() {
        val recent = NotificationGrouping.RecentRows(windowMillis = 5_000)
        recent.add(Row(2, 1, "A", postedAt = 1_000))
        recent.add(Row(4, 2, "B", postedAt = 1_010))
        // The system lists only the first one so far.
        val merged = recent.merge(listOf(Row(2, 1, "A", postedAt = 1_001)), now = 1_020)
        assertEquals(listOf(2, 4), merged.map { it.id })
        val plan = NotificationGrouping.plan(merged, summaryShowing = true) as Plan.Show
        assertEquals(2, plan.count)
        assertNull("two subscriptions: no single target", plan.openSubscriptionId)
    }

    @Test
    fun recentRows_expireAndKeepSystemRowsInPostingOrder() {
        val recent = NotificationGrouping.RecentRows(windowMillis = 5_000)
        recent.add(Row(2, 1, "A", postedAt = 1_000))
        val showing = listOf(Row(6, 3, "C", postedAt = 900), Row(8, 4, "D", postedAt = 500))
        // After the window only what the system lists counts (a dismissed row is gone).
        assertEquals(listOf(8, 6), recent.merge(showing, now = 10_000).map { it.id })
        // A re-posted row counts once, with its new title.
        recent.add(Row(6, 3, "C new", postedAt = 11_000))
        val merged = recent.merge(showing, now = 11_001)
        assertEquals(listOf("D", "C new"), merged.map { it.title })
    }

    @Test
    fun unknownSubscriptionIdIsNoTarget() {
        val plan = NotificationGrouping.plan(listOf(Row(0, 0, "x"), Row(1, 0, "y")), summaryShowing = false) as Plan.Show
        assertNull(plan.openSubscriptionId)
    }
}
