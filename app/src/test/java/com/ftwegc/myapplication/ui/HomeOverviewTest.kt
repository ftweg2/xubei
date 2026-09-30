package com.ftwegc.myapplication.ui

import com.ftwegc.myapplication.data.local.toDomain
import com.ftwegc.myapplication.data.local.toEntity
import com.ftwegc.myapplication.data.prefs.AppSettings
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.repo.FakePreferencesDataStore
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.ui.home.HomeViewModel
import com.ftwegc.myapplication.ui.preview.SampleData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The v2 overview blocks built by [HomeViewModel.build]. */
class HomeOverviewTest {

    private val today = SampleData.today // 2026-09-28
    private val gib = 1L shl 30

    @Test
    fun overview_leadsWithTheDefaultCurrency_othersAsChips() {
        val state = HomeViewModel.build(SampleData.all, AppSettings(), today, isRefreshing = false)
        val o = state.overview!!
        assertEquals("CNY", o.currency)
        // September, CNY: Claude Pro (Sep 14) 148 + iCloud (today) 21; the annual Tokyo VPS was paid in 2025-11.
        assertEquals(14_800L + 2_100L, o.thisMonthMinor)
        assertEquals(listOf("USD"), o.otherCurrencies.map { it.currency })
        // USD: ChatGPT (Sep 1) 20; the quarterly LA VPS (due Sep 26) 12.99.
        assertEquals(2_000L + 1_299L, o.otherCurrencies.single().minor)
        assertEquals(30, o.pace.size)
        assertEquals(27, o.todayIndex)
        assertEquals(o.thisMonthMinor, o.pace.last())
        assertEquals(12, o.yearByMonth.size)
        assertEquals(8, o.monthIndex)
        assertEquals(o.yearByMonth.sum(), o.yearTotalMinor)
        assertEquals(listOf(Category.AI, Category.VPS, Category.OTHER), o.categories.map { it.category })
        assertEquals(o.thisMonthMinor - o.lastMonthMinor, o.deltaMinor)
    }

    @Test
    fun overview_nullWithoutAnythingToPay() {
        val free = Subscription(name = "免费", category = Category.OTHER, priceMinor = 0, isFree = true, currency = "CNY", cycle = BillingCycle.MONTHLY)
        assertNull(HomeViewModel.build(listOf(SubscriptionWithVps(free, null)), AppSettings(), today, false).overview)
        assertNull(HomeViewModel.build(emptyList(), AppSettings(), today, false).overview)
    }

    @Test
    fun nextDue_timelineAndMarks() {
        val state = HomeViewModel.build(SampleData.all, AppSettings(), today, isRefreshing = false)
        // The LA VPS expired two days ago: it is the nearest, with its cycle fully elapsed.
        val due = state.nextDue!!
        assertEquals(4L, due.id)
        assertEquals(-2L, due.daysLeft)
        assertEquals(1f, due.elapsed, 0f)
        assertEquals("洛杉矶 9929", due.mark.name)
        assertEquals(Category.VPS, due.mark.category)
        // Timeline: overdue first (at day 0), then by date; Tokyo (+58) is beyond 30 days; archived never.
        assertEquals(listOf(4L, 5L, 1L, 2L), state.timeline.map { it.id })
        assertEquals(listOf(0, 0, 3, 16), state.timeline.map { it.dayOffset })
        assertTrue(state.timeline.first().overdue)
    }

    @Test
    fun ringsAndNodes_ofLinkedVps() {
        fun vps(id: Long, name: String, used: Long, limit: Long, region: String?, error: String? = null) = SubscriptionWithVps(
            Subscription(id = id, name = name, category = Category.VPS, priceMinor = 1_000, currency = "CNY", cycle = BillingCycle.MONTHLY, nextDue = today.plusDays(40)),
            VpsConfig(
                subscriptionId = id, komariUuid = "u$id", komariRegion = region,
                trafficLimitBytes = limit, trafficLimitType = TrafficLimitType.MAX,
                lastTotalUp = 0, lastTotalDown = used, lastReportedAt = Instant.parse("2026-09-28T00:00:00Z"), lastError = error,
            ),
        )
        val all = listOf(
            vps(1, "东京", 30 * gib, 100 * gib, "🇯🇵"),
            vps(2, "洛杉矶", 94 * gib, 100 * gib, "🇺🇸"),
            vps(3, "法兰克福", 62 * gib, 100 * gib, "🇩🇪", error = "OFFLINE"),
            vps(4, "不限", 500 * gib, 0, null),
            vps(5, "伦敦", 12 * gib, 100 * gib, "🇬🇧"),
            vps(6, "新加坡", 5 * gib, 100 * gib, null),
        )
        val state = HomeViewModel.build(all, AppSettings(komariUrl = "http://10.0.2.2:8765"), today, false)
        assertEquals(listOf("洛杉矶", "法兰克福", "东京", "伦敦"), state.rings.map { it.name })
        assertEquals(94.0, state.rings.first().percent, 1e-9)
        assertEquals(HomeViewModel.MAX_RINGS, state.rings.size)
        assertEquals(6, state.nodes.size)
        val frankfurt = state.nodes.single { it.id == 3L }
        assertFalse(frankfurt.storedOnline)
        assertEquals("🇩🇪", frankfurt.region)
        assertTrue(state.nodes.single { it.id == 1L }.storedOnline)
        assertEquals("u2", state.nodes.single { it.id == 2L }.uuid)
    }

    // ------------------------------------------------------------ storage of the new fields

    @Test
    fun iconChoiceAndRegion_roundTripThroughTheDatabaseRows() {
        val sub = Subscription(
            id = 3, name = "x", category = Category.VPS, priceMinor = 1, currency = "CNY", cycle = BillingCycle.MONTHLY,
            iconKey = "oracle", iconColor = 0xFF112233.toInt(),
        )
        assertEquals(sub, sub.toEntity(1, 2).toDomain())
        assertNull(sub.copy(iconKey = null, iconColor = null).toEntity(1, 2).iconKey)
        // A blank stored key reads as "match by name".
        assertNull(sub.toEntity(1, 2).copy(iconKey = " ").toDomain().iconKey)
        val vps = VpsConfig(subscriptionId = 3, komariUuid = "n", komariRegion = "🇩🇪")
        assertEquals(vps, vps.toEntity().toDomain())
        assertNull(vps.toEntity().copy(komariRegion = "").toDomain().komariRegion)
    }

    @Test
    fun glassSettings_areStored() = runTest {
        val store = SettingsStore(FakePreferencesDataStore())
        assertFalse(store.settings.first().reduceTransparency)
        assertNull(store.settings.first().glassTierOverride)
        store.setReduceTransparency(true)
        store.setGlassTierOverride("tinted")
        assertTrue(store.settings.first().reduceTransparency)
        assertEquals("tinted", store.settings.first().glassTierOverride)
        store.setGlassTierOverride(" ")
        assertNull(store.settings.first().glassTierOverride)
    }

    @Test
    fun formKeepsTheStoredIcon() {
        // The edit form has no icon picker yet: saving must not drop a stored choice.
        val base = SampleData.chatGpt.copy(iconKey = "letter", iconColor = 0xFF00FF00.toInt())
        val form = com.ftwegc.myapplication.ui.subscriptions.SubscriptionForm.from(base, null)
        val result = com.ftwegc.myapplication.ui.subscriptions.SubscriptionFormValidator.validate(form, base, null)
        val saved = (result as com.ftwegc.myapplication.ui.subscriptions.FormResult.Valid).subscription
        assertEquals("letter", saved.iconKey)
        assertEquals(0xFF00FF00.toInt(), saved.iconColor)
    }
}
