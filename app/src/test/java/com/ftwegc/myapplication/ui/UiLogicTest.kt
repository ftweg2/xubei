package com.ftwegc.myapplication.ui

import com.ftwegc.myapplication.data.prefs.AppSettings
import com.ftwegc.myapplication.data.prefs.SortOrder
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.DueStatus
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.ui.components.CyclePreset
import com.ftwegc.myapplication.ui.components.trafficColor
import com.ftwegc.myapplication.ui.home.HomeViewModel
import com.ftwegc.myapplication.ui.preview.SampleData
import com.ftwegc.myapplication.ui.subscriptions.CategoryFilter
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionDetailViewModel
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionEditViewModel
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionListViewModel
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Pure presentation logic behind the screens. */
class UiLogicTest {

    private val today = SampleData.today

    private fun item(
        id: Long, name: String, due: LocalDate?, price: Long = 100, cycle: BillingCycle = BillingCycle.MONTHLY,
        category: Category = Category.AI, archived: Boolean = false, permanent: Boolean = false,
    ) = SubscriptionWithVps(
        Subscription(
            id = id, name = name, category = category, priceMinor = price, currency = "CNY", cycle = cycle,
            nextDue = due, archived = archived, isPermanent = permanent,
        ),
        null,
    )

    // ------------------------------------------------------------ list sorting / filtering

    @Test
    fun sort_byDueDate_undatedAndArchivedLast() {
        val items = listOf(
            item(1, "C", today.plusDays(9)),
            item(2, "A", null),
            item(3, "B", today.plusDays(1)),
            item(4, "D", today.minusDays(1), archived = true),
            item(5, "E", null, permanent = true),
        )
        val ids = SubscriptionListViewModel.sort(items, SortOrder.DUE_DATE).map { it.subscription.id }
        assertEquals(listOf(3L, 1L, 2L, 5L, 4L), ids)
    }

    @Test
    fun sort_byPrice_usesMonthlyEquivalent() {
        val items = listOf(
            item(1, "yearly", today, price = 12_000, cycle = BillingCycle.ANNUAL), // 1000 / month
            item(2, "monthly", today, price = 1_500), // 1500 / month
            item(3, "once", today, price = 800, cycle = BillingCycle.ONE_TIME), // raw 800
        )
        assertEquals(listOf(2L, 1L, 3L), SubscriptionListViewModel.sort(items, SortOrder.PRICE).map { it.subscription.id })
    }

    @Test
    fun sort_byName_usesChineseCollation() {
        val items = listOf(item(1, "腾讯云", today), item(2, "阿里云", today), item(3, "Claude", today))
        val names = SubscriptionListViewModel.sort(items, SortOrder.NAME).map { it.subscription.name }
        assertEquals("阿里云", names[names.indexOf("阿里云")])
        assertTrue(names.indexOf("阿里云") < names.indexOf("腾讯云"))
    }

    @Test
    fun build_filtersCountsAndHidesArchived() {
        val state = SubscriptionListViewModel.build(SampleData.all, AppSettings(showArchived = false), CategoryFilter.VPS, today)
        assertEquals(2, state.items.size)
        assertTrue(state.items.all { it.category == Category.VPS })
        assertEquals(5, state.counts[CategoryFilter.ALL])
        assertEquals(1, state.counts[CategoryFilter.OTHER])
        // The archived one is "其他": under the VPS chip it is not mentioned.
        assertEquals(0, state.hiddenArchivedCount)
        assertEquals(1, SubscriptionListViewModel.build(SampleData.all, AppSettings(showArchived = false), CategoryFilter.OTHER, today).hiddenArchivedCount)
        assertEquals(6, state.totalCount)
        assertFalse(state.isEmpty)

        val shown = SubscriptionListViewModel.build(SampleData.all, AppSettings(showArchived = true), CategoryFilter.ALL, today)
        assertEquals(6, shown.items.size)
        assertTrue("archived goes last", shown.items.last().archived)
        assertEquals(0, shown.hiddenArchivedCount)
    }

    @Test
    fun build_emptyStates() {
        val empty = SubscriptionListViewModel.build(emptyList(), AppSettings(), CategoryFilter.ALL, today)
        assertTrue(empty.isEmpty)
        val filtered = SubscriptionListViewModel.build(listOf(item(1, "a", today)), AppSettings(), CategoryFilter.VPS, today)
        assertTrue(filtered.isFilteredEmpty)
        assertFalse(filtered.isEmpty)
    }

    // ------------------------------------------------------------ home

    @Test
    fun home_build_spendAndUpcoming() {
        val state = HomeViewModel.build(SampleData.all, AppSettings(), today, isRefreshing = false)
        assertEquals(5, state.activeCount)
        // CNY: 148 + 499/12 + 21 = 210.58; USD: 20 + 12.99/3 = 24.33
        assertEquals(listOf("CNY", "USD"), state.monthly.map { it.currency })
        assertEquals(21_058L, state.monthly[0].minor)
        assertEquals(2_433L, state.monthly[1].minor)
        // Upcoming: overdue LA VPS, iCloud today, ChatGPT +3, Claude +16; Tokyo (+58) is beyond 30 days.
        assertEquals(listOf(4L, 5L, 1L, 2L), state.upcoming.map { it.id })
        assertEquals(DueStatus.EXPIRED, state.upcoming[0].due.status)
        assertEquals(2, state.vpsCount)
        assertTrue(state.hasAny)
    }

    // ------------------------------------------------------------ detail

    @Test
    fun cycleProgress_monthly() {
        val sub = item(1, "a", LocalDate.of(2026, 10, 28)).subscription
        // cycle 2026-09-28 .. 2026-10-28 (30 days), today is the first day
        assertEquals(0f, SubscriptionDetailViewModel.cycleProgress(sub, today)!!, 0.0001f)
        assertEquals(0.5f, SubscriptionDetailViewModel.cycleProgress(sub, LocalDate.of(2026, 10, 13))!!, 0.0001f)
        assertEquals(1f, SubscriptionDetailViewModel.cycleProgress(sub, LocalDate.of(2026, 11, 30))!!, 0.0001f)
    }

    @Test
    fun cycleProgress_noneForOnceOrPermanentOrUndated() {
        assertNull(SubscriptionDetailViewModel.cycleProgress(item(1, "a", today, cycle = BillingCycle.ONE_TIME).subscription, today))
        assertNull(SubscriptionDetailViewModel.cycleProgress(item(1, "a", today, permanent = true).subscription, today))
        assertNull(SubscriptionDetailViewModel.cycleProgress(item(1, "a", null).subscription, today))
    }

    @Test
    fun detail_build_notFound() {
        assertTrue(SubscriptionDetailViewModel.build(null, AppSettings(), today).notFound)
    }

    @Test
    fun detail_build_renewPreviews() {
        val state = SubscriptionDetailViewModel.build(SampleData.all[0], AppSettings(), today)
        assertTrue(state.canRenew)
        assertEquals(LocalDate.of(2026, 11, 1), state.renewFromDue) // due 10-01, anchor 1
        assertEquals(LocalDate.of(2026, 10, 28), state.renewFromToday)
    }

    // ------------------------------------------------------------ edit

    @Test
    fun edit_defaultDue_followsPreset() {
        assertEquals(LocalDate.of(2026, 10, 28), SubscriptionEditViewModel.defaultDue(CyclePreset.MONTHLY, today))
        assertEquals(LocalDate.of(2027, 9, 28), SubscriptionEditViewModel.defaultDue(CyclePreset.ANNUAL, today))
        assertNull(SubscriptionEditViewModel.defaultDue(CyclePreset.ONCE, today))
        assertNull(SubscriptionEditViewModel.defaultDue(CyclePreset.CUSTOM, today))
    }

    // ------------------------------------------------------------ components

    @Test
    fun trafficColor_thresholds() {
        val p = Color.Green
        val w = Color.Yellow
        val d = Color.Red
        assertEquals(p, trafficColor(0.2f, p, w, d))
        assertEquals(p, trafficColor(0.69f, p, w, d))
        assertEquals(w, trafficColor(0.9f, p, w, d))
        assertEquals(d, trafficColor(0.95f, p, w, d))
        assertEquals(d, trafficColor(1.4f, p, w, d))
        val mid = trafficColor(0.8f, p, w, d)
        assertTrue(mid != p && mid != w)
    }
}
