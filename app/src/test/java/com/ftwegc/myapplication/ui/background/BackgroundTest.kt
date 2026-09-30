package com.ftwegc.myapplication.ui.background

import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.VpsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BackgroundTest {

    private val today = LocalDate.of(2026, 9, 28)
    private val gib = 1L shl 30

    private fun sub(id: Long, due: LocalDate?, archived: Boolean = false, permanent: Boolean = false, category: Category = Category.AI) =
        Subscription(
            id = id, name = "s$id", category = category, priceMinor = 1_000, currency = "CNY",
            cycle = BillingCycle.MONTHLY, nextDue = due, archived = archived, isPermanent = permanent,
        )

    private fun vps(id: Long, usedBytes: Long, limit: Long, linked: Boolean = true) = SubscriptionWithVps(
        sub(id, today.plusDays(40), category = Category.VPS),
        VpsConfig(
            subscriptionId = id,
            komariUuid = if (linked) "n$id" else null,
            trafficLimitBytes = limit,
            trafficLimitType = TrafficLimitType.MAX,
            lastTotalUp = 0,
            lastTotalDown = usedBytes,
            lastReportedAt = Instant.parse("2026-09-28T00:00:00Z"),
        ),
    )

    // ------------------------------------------------------------ mood

    @Test
    fun mood_calmWhenNothingIsClose() {
        val items = listOf(SubscriptionWithVps(sub(1, today.plusDays(8)), null), SubscriptionWithVps(sub(2, null), null))
        assertEquals(BackgroundMood.Calm, BackgroundMoods.of(items, today))
        assertEquals(BackgroundMood.Calm, BackgroundMoods.of(emptyList(), today))
    }

    @Test
    fun mood_dueWithinSevenDaysAddsAmber() {
        assertEquals(BackgroundMood(dueSoon = true), BackgroundMoods.of(listOf(SubscriptionWithVps(sub(1, today.plusDays(7)), null)), today))
        assertEquals(BackgroundMood(dueSoon = true), BackgroundMoods.of(listOf(SubscriptionWithVps(sub(1, today), null)), today))
    }

    @Test
    fun mood_expiredOrTrafficUsedUpAddsCoral() {
        assertEquals(BackgroundMood(alert = true), BackgroundMoods.of(listOf(SubscriptionWithVps(sub(1, today.minusDays(1)), null)), today))
        assertEquals(BackgroundMood(alert = true), BackgroundMoods.of(listOf(vps(2, 100 * gib, 100 * gib)), today))
        assertEquals(BackgroundMood.Calm, BackgroundMoods.of(listOf(vps(2, 99 * gib, 100 * gib)), today))
        // Both at once.
        val both = BackgroundMoods.of(listOf(vps(2, 120 * gib, 100 * gib), SubscriptionWithVps(sub(3, today.plusDays(2)), null)), today)
        assertEquals(BackgroundMood(dueSoon = true, alert = true), both)
    }

    @Test
    fun mood_ignoresArchivedPermanentUnlimitedAndUnlinked() {
        val items = listOf(
            SubscriptionWithVps(sub(1, today.minusDays(3), archived = true), null),
            SubscriptionWithVps(sub(2, today.minusDays(3), permanent = true), null),
            vps(3, 500 * gib, 0),
            vps(4, 500 * gib, 100 * gib, linked = false),
        )
        assertEquals(BackgroundMood.Calm, BackgroundMoods.of(items, today))
    }

    // ------------------------------------------------------------ palette and drift

    @Test
    fun palette_moodMixesAmberAndCoral() {
        val calm = AuroraPalettes.of(PageTone.Home, BackgroundMood.Calm, dark = true)
        val warn = AuroraPalettes.of(PageTone.Home, BackgroundMood(dueSoon = true), dark = true)
        val alert = AuroraPalettes.of(PageTone.Home, BackgroundMood(alert = true), dark = true)
        assertEquals(listOf(AuroraPalettes.Teal, AuroraPalettes.Blue, AuroraPalettes.Violet), calm.blobs.map { it.color })
        assertTrue(warn.blobs.any { it.color == AuroraPalettes.Amber })
        assertTrue(alert.blobs.any { it.color == AuroraPalettes.Coral })
        assertFalse(calm.blobs.any { it.color == AuroraPalettes.Amber || it.color == AuroraPalettes.Coral })
    }

    @Test
    fun palette_eachPageHasItsOwnArrangement_andAccentLeads() {
        val tones = listOf(PageTone.Home, PageTone.Subscriptions, PageTone.Stats, PageTone.Settings)
        val palettes = tones.map { AuroraPalettes.of(it, BackgroundMood.Calm, dark = true) }
        assertEquals(palettes.size, palettes.map { p -> p.blobs.map { it.cx to it.color } }.toSet().size)
        val accent = AuroraPalettes.of(PageTone.Accent(0xFFE0446A), BackgroundMood.Calm, dark = true)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFE0446A), accent.blobs.first().color)
    }

    @Test
    fun palette_lightThemeUsesLightBaseAndLighterBlobs() {
        val dark = AuroraPalettes.of(PageTone.Home, BackgroundMood.Calm, dark = true)
        val light = AuroraPalettes.of(PageTone.Home, BackgroundMood.Calm, dark = false)
        assertEquals(AuroraPalettes.LightBase, light.base)
        assertTrue(light.blobs.first().color.red >= dark.blobs.first().color.red)
        assertTrue(dark.screenBlend && !light.screenBlend)
    }

    @Test
    fun drift_isDeterministicAndSlow() {
        val blob = AuroraPalettes.of(PageTone.Home, BackgroundMood.Calm, dark = true).blobs.first()
        val w = 1080f
        val h = 2400f
        val start = AuroraPalettes.frame(blob, 0, w, h)
        assertEquals(blob.cx * w, start.x, 0.01f)
        assertEquals(blob.cy * h, start.y, 0.01f)
        assertEquals(blob.radius * w, start.radius, 0.01f)
        // Half a cycle later it has fully drifted and grown to 1.18×.
        val far = AuroraPalettes.frame(blob, blob.periodMs.toLong(), w, h)
        assertEquals(blob.radius * w * AuroraBlob.DRIFT_SCALE, far.radius, 0.5f)
        assertEquals(blob.cx * w + AuroraBlob.DRIFT_X * w, far.x, 0.5f)
        // …and back after a full cycle; same time, same picture.
        assertEquals(start, AuroraPalettes.frame(blob, 2L * blob.periodMs, w, h))
        assertEquals(AuroraPalettes.frame(blob, 1234, w, h), AuroraPalettes.frame(blob, 1234, w, h))
        // One redraw step moves it by well under a pixel.
        val a = AuroraPalettes.frame(blob, 10_000, w, h)
        val b = AuroraPalettes.frame(blob, 10_050, w, h)
        assertTrue(kotlin.math.abs(a.x - b.x) < 1f)
        assertNotEquals(AuroraPalettes.frame(blob, 5_000, w, h), start)
    }

    @Test
    fun easeInOut_endpointsAndSymmetry() {
        assertEquals(0f, AuroraPalettes.easeInOut(0f), 1e-6f)
        assertEquals(1f, AuroraPalettes.easeInOut(1f), 1e-6f)
        assertEquals(0.5f, AuroraPalettes.easeInOut(0.5f), 1e-6f)
        assertEquals(1f - AuroraPalettes.easeInOut(0.2f), AuroraPalettes.easeInOut(0.8f), 1e-5f)
    }
}
