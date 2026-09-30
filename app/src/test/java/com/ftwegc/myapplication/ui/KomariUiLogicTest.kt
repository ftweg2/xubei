package com.ftwegc.myapplication.ui

import com.ftwegc.myapplication.data.prefs.AppSettings
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.ui.common.TrafficIssue
import com.ftwegc.myapplication.ui.common.TrafficUi
import com.ftwegc.myapplication.ui.common.toUi
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.format.RelativeTime
import com.ftwegc.myapplication.ui.home.HomeViewModel
import com.ftwegc.myapplication.ui.komari.KomariSettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class KomariUiLogicTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-09-28T04:00:00Z") // 12:00 local
    private val gib = 1L shl 30

    @Test
    fun relativeTime_buckets() {
        fun rel(s: String) = Formatters.relativeTime(Instant.parse(s), now, zone)
        assertEquals(RelativeTime.JustNow, rel("2026-09-28T03:59:30Z"))
        assertEquals(RelativeTime.JustNow, rel("2026-09-28T04:02:00Z")) // small clock skew
        assertEquals(RelativeTime.MinutesAgo(5), rel("2026-09-28T03:55:00Z"))
        assertEquals(RelativeTime.HoursAgo(3), rel("2026-09-28T01:00:00Z"))
        assertEquals(RelativeTime.Yesterday(LocalDateTime.of(2026, 9, 27, 21, 5)), rel("2026-09-27T13:05:00Z"))
        assertEquals(RelativeTime.At(LocalDateTime.of(2026, 9, 3, 8, 0), true), rel("2026-09-03T00:00:00Z"))
        assertEquals(RelativeTime.At(LocalDateTime.of(2025, 12, 31, 8, 0), false), rel("2025-12-31T00:00:00Z"))
    }

    private fun vps(id: Long, name: String, limit: Long, up: Long?, down: Long?, linked: Boolean = true, error: String? = null) =
        SubscriptionWithVps(
            Subscription(id = id, name = name, category = Category.VPS, priceMinor = 100, currency = "¥", cycle = BillingCycle.MONTHLY),
            VpsConfig(
                subscriptionId = id,
                komariUuid = if (linked) "u$id" else null,
                trafficLimitBytes = limit,
                lastTotalUp = up,
                lastTotalDown = down,
                lastReportedAt = if (up != null) now else null,
                lastError = error,
            ),
        )

    @Test
    fun homeTraffic_sortedByUsageShare_unlimitedAndNoDataLast() {
        val all = listOf(
            vps(1, "low", 100 * gib, 1 * gib, 10 * gib),
            vps(2, "high", 100 * gib, 5 * gib, 90 * gib),
            vps(3, "unlimited", 0, 500 * gib, 500 * gib),
            vps(4, "nodata", 100 * gib, null, null),
            vps(5, "over", 10 * gib, 1 * gib, 12 * gib),
            vps(6, "unlinked", 100 * gib, null, null, linked = false),
        )
        val state = HomeViewModel.build(all, AppSettings(komariUrl = "https://a.com"), LocalDate.of(2026, 9, 28), isRefreshing = false)
        assertEquals(listOf("over", "high", "low", "unlimited", "nodata"), state.traffic.map { it.name })
        assertTrue(state.komariConfigured)
        assertEquals(6, state.vpsCount)
    }

    @Test
    fun trafficUi_issues() {
        assertEquals(TrafficIssue.Offline, TrafficUi.of(vps(1, "a", 0, 1, 1, error = "OFFLINE").vps!!).issue)
        assertTrue(TrafficUi.of(vps(1, "a", 0, 1, 1, error = "NETWORK_TIMEOUT").vps!!).issue is TrafficIssue.Failed)
        assertEquals(TrafficIssue.Missing, TrafficUi.of(vps(1, "a", 0, 1, 1).vps!!.copy(komariMissing = true)).issue)
        assertNull(TrafficUi.of(vps(1, "a", 0, 1, 1).vps!!).issue)
        assertNull("unlinked has no data and no issue", TrafficUi.of(vps(1, "a", 0, 1, 1, linked = false).vps!!).usage)
    }

    // Stage 7 replaced the stage 6 placeholder ("mode 2 shows Komari's counters"): mode 2 now shows
    // what the app accumulated, and nothing until the first period exists.
    @Test
    fun appAccumulatedMode_showsAccumulatedPeriod() {
        val item = vps(1, "a", 100 * gib, 2 * gib, 3 * gib)
        val appMode = item.copy(vps = item.vps!!.copy(countingMode = CountingMode.APP_ACCUMULATED))
        val waiting = TrafficUi.of(appMode.vps!!)
        assertTrue(waiting.isAppMode)
        assertNull("no period yet: nothing to show", waiting.usage)
        assertTrue(waiting.app!!.waitingForBaseline)

        val today = LocalDate.of(2026, 9, 28)
        val period = com.ftwegc.myapplication.domain.model.AppPeriodUsage(
            com.ftwegc.myapplication.domain.model.TrafficPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)),
            upBytes = 1 * gib, downBytes = 4 * gib, initialUsedBytes = 10 * gib, updatedAt = java.time.Instant.parse("2026-09-28T00:00:00Z"),
        )
        val ui = TrafficUi.of(appMode.vps!!, period, today)
        assertEquals("initial + max(up, down)", 14 * gib, ui.usage!!.usedBytes)
        assertEquals(4 * gib, ui.app!!.accumulatedUsedBytes)
        assertNotNull(appMode.copy(appPeriod = period).toUi(today, 7).traffic)
    }

    @Test
    fun komariSettings_cleartextWarning() {
        val base = KomariSettingsUiState(isLoading = false, url = "http://10.0.2.2:8765")
        assertFalse("no key, no warning", base.showCleartextWarning)
        assertTrue(base.copy(keyText = "k").showCleartextWarning)
        assertFalse(base.copy(url = "https://a.com", keyText = "k").showCleartextWarning)
        val saved = base.copy(keyStatus = com.ftwegc.myapplication.data.prefs.ApiKeyStatus.SAVED, editingKey = false)
        assertTrue(saved.showCleartextWarning)
        assertFalse(saved.copy(clearKeyPending = true).showCleartextWarning)
    }

    @Test
    fun komariSettings_stateNeverPrintsKey() {
        val state = KomariSettingsUiState(isLoading = false, url = "https://a.com", keyText = "secret-typed-key")
        assertFalse(state.toString().contains("secret-typed-key"))
    }

    @Test
    fun komariSettings_dirty() {
        val saved = KomariSettingsUiState(isLoading = false, url = "https://a.com", savedUrl = "https://a.com", editingKey = false)
        assertFalse(saved.isDirty)
        assertFalse("same address, different spelling", saved.copy(url = "a.com/api/").isDirty)
        assertTrue(saved.copy(url = "https://b.com").isDirty)
        assertTrue(saved.copy(clearKeyPending = true).isDirty)
    }
}
