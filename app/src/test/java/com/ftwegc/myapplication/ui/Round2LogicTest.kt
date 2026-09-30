package com.ftwegc.myapplication.ui

import com.ftwegc.myapplication.data.backup.BackupCodec
import com.ftwegc.myapplication.data.backup.BackupReadResult
import com.ftwegc.myapplication.data.prefs.AppSettings
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.remote.komari.KomariEndpoint
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.data.remote.komari.OkHttpKomariApi
import com.ftwegc.myapplication.data.repo.FakePreferencesDataStore
import com.ftwegc.myapplication.data.repo.KomariLinkChange
import com.ftwegc.myapplication.domain.calc.CumulativeSpend
import com.ftwegc.myapplication.domain.calc.DayUsage
import com.ftwegc.myapplication.domain.calc.UsageForecast
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.TrafficPeriod
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.ui.brand.BrandCatalog
import com.ftwegc.myapplication.ui.preview.SampleData
import com.ftwegc.myapplication.ui.subscriptions.CategoryFilter
import com.ftwegc.myapplication.ui.subscriptions.FormResult
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionEditViewModel
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionForm
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionFormValidator
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionListViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Logic behind the round 2 screens: forecast, spend so far, list search / pinning, icons in forms and backups. */
class Round2LogicTest {

    private val gib = 1L shl 30
    private val today = LocalDate.of(2026, 9, 28)
    private val september = TrafficPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1))

    // ------------------------------------------------------------------ Usage forecast

    @Test
    fun forecast_runsOutBeforeThePeriodEnds() {
        // 28 days in, 470 GB used: 16.79 GB / day; 500 GB is reached on day 30 (29.8 days).
        val f = UsageForecast.compute(september, today, 470 * gib, 500 * gib, emptyList(), TrafficLimitType.SUM)!!
        assertEquals(30, f.totalDays)
        assertEquals(28, f.cumulative.size)
        assertEquals(470 * gib, f.cumulative.last())
        assertEquals(LocalDate.of(2026, 9, 30), f.exhaustDate)
        assertTrue(f.runsOut)
        assertFalse(f.exceeded)
        assertTrue(f.projectedEnd > 500 * gib)
    }

    @Test
    fun forecast_enoughForThisPeriod() {
        val f = UsageForecast.compute(september, today, 100 * gib, 1024 * gib, emptyList(), TrafficLimitType.SUM)!!
        assertNull(f.exhaustDate)
        assertFalse(f.runsOut)
        assertEquals(100.0 * gib / 28, f.ratePerDay, 1.0)
    }

    @Test
    fun forecast_exceededAndUnlimited() {
        val over = UsageForecast.compute(september, today, 600 * gib, 500 * gib, emptyList(), TrafficLimitType.SUM)!!
        assertTrue(over.exceeded)
        assertNull(over.exhaustDate)
        assertTrue(over.runsOut)
        val unlimited = UsageForecast.compute(september, today, 600 * gib, 0, emptyList(), TrafficLimitType.SUM)!!
        assertNull(unlimited.limitBytes)
        assertFalse(unlimited.runsOut)
        assertEquals((600.0 * gib / 28 * 30).toLong(), unlimited.projectedEnd)
    }

    @Test
    fun forecast_curveFollowsDailyUsageAndNeverDips() {
        // 10 GB on each of the last three days, 100 GB used in total.
        val daily = (25..28).map { DayUsage(LocalDate.of(2026, 9, it), 5 * gib, 5 * gib) } +
            DayUsage(LocalDate.of(2026, 8, 31), 500 * gib, 0) // before the period: ignored
        val f = UsageForecast.compute(september, today, 100 * gib, 0, daily, TrafficLimitType.SUM)!!
        val c = f.cumulative
        assertEquals(100 * gib, c[27])
        assertEquals(90 * gib, c[26])
        assertEquals(80 * gib, c[25])
        assertEquals(70 * gib, c[24])
        assertEquals(60 * gib, c[23])
        // Before the first reading: a straight line from 0 at the period start.
        assertEquals(60 * gib / 24, c[0])
        assertEquals(30 * gib, c[11])
        // Without any readings the whole curve is the average line.
        val flat = UsageForecast.compute(september, today, 280 * gib, 0, emptyList(), TrafficLimitType.SUM)!!.cumulative
        assertEquals(10 * gib, flat[0])
        assertEquals(280 * gib, flat.last())
        assertTrue(c.zipWithNext().all { (a, b) -> a <= b })
        // More traffic in the readings than used: clamped at 0, still increasing.
        val clamped = UsageForecast.compute(september, today, 15 * gib, 0, daily, TrafficLimitType.SUM)!!.cumulative
        assertTrue(clamped.all { it >= 0 })
        assertTrue(clamped.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test
    fun forecast_limitTypeAndPeriodBounds() {
        val daily = listOf(DayUsage(today, 8 * gib, 2 * gib))
        val max = UsageForecast.compute(september, today, 50 * gib, 0, daily, TrafficLimitType.MAX)!!
        assertEquals(42 * gib, max.cumulative[26])
        assertNull("today outside the period", UsageForecast.compute(september, LocalDate.of(2026, 10, 1), 1, 0, emptyList(), TrafficLimitType.SUM))
        val firstDay = UsageForecast.compute(september, LocalDate.of(2026, 9, 1), 20 * gib, 500 * gib, emptyList(), TrafficLimitType.SUM)!!
        assertEquals(1, firstDay.cumulative.size)
        assertEquals(LocalDate.of(2026, 9, 25), firstDay.exhaustDate)
    }

    // ------------------------------------------------------------------ Spend so far

    private fun sub(
        price: Long = 2_000,
        cycle: BillingCycle = BillingCycle.MONTHLY,
        due: LocalDate? = LocalDate.of(2026, 10, 15),
        free: Boolean = false,
        permanent: Boolean = false,
    ) = Subscription(
        id = 1, name = "x", category = Category.AI, priceMinor = price, isFree = free, currency = "USD", cycle = cycle,
        nextDue = due, anchorDay = due?.dayOfMonth, isPermanent = permanent,
    )

    @Test
    fun spend_monthlyFromTheDayItWasAdded() {
        // Added on 6-20, due on the 15th: the cycle running then was paid on 6-15, then 7-15,
        // 8-15 and 9-15 (10-15 is still ahead).
        val steps = CumulativeSpend.steps(sub(), LocalDate.of(2026, 6, 20), today)
        assertEquals(
            listOf(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 15)),
            steps.map { it.date },
        )
        assertEquals(listOf(2_000L, 4_000L, 6_000L, 8_000L), steps.map { it.totalMinor })
        assertEquals(8_000L, CumulativeSpend.total(steps))
    }

    @Test
    fun spend_renewedInAdvanceCountsTheNewCycle() {
        // Added 9-28 while due 10-1; renewed early, so now due 11-1: 9-1 and 10-1 are paid.
        val renewed = sub(due = LocalDate.of(2026, 11, 1))
        val steps = CumulativeSpend.steps(renewed, today, today)
        assertEquals(listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)), steps.map { it.date })
        assertEquals(4_000L, CumulativeSpend.total(steps))
    }

    @Test
    fun spend_countsTheRunningCycleEvenWhenAddedLater() {
        // Added today; the cycle running now started on 9-15 and was paid then.
        val steps = CumulativeSpend.steps(sub(), today, today)
        assertEquals(listOf(LocalDate.of(2026, 9, 15)), steps.map { it.date })
        // Overdue since 9-20: 9-20 itself is not paid yet, 8-20 was.
        val overdue = CumulativeSpend.steps(sub(due = LocalDate.of(2026, 9, 20)), today, today)
        assertEquals(listOf(LocalDate.of(2026, 8, 20)), overdue.map { it.date })
    }

    @Test
    fun spend_oneOffAndFree() {
        assertEquals(listOf(9_900L), CumulativeSpend.steps(sub(price = 9_900, cycle = BillingCycle.ONE_TIME, due = null), today, today).map { it.totalMinor })
        assertEquals(1, CumulativeSpend.steps(sub(price = 30_000, permanent = true, due = null), LocalDate.of(2026, 1, 1), today).size)
        assertEquals("undated recurring: one payment", 1, CumulativeSpend.steps(sub(due = null), today, today).size)
        assertTrue(CumulativeSpend.steps(sub(free = true), LocalDate.of(2025, 1, 1), today).isEmpty())
        assertTrue(CumulativeSpend.steps(sub(price = 0), LocalDate.of(2025, 1, 1), today).isEmpty())
    }

    // ------------------------------------------------------------------ Subscription page

    @Test
    fun list_searchIgnoresCaseSpacesAndWidth() {
        assertTrue(SubscriptionListViewModel.matchesQuery("ChatGPT Plus", "gpt"))
        assertTrue(SubscriptionListViewModel.matchesQuery("ChatGPT Plus", "ＣＨＡＴ gpt"))
        assertTrue(SubscriptionListViewModel.matchesQuery("东京 CN2 GIA", "东京cn2"))
        assertTrue(SubscriptionListViewModel.matchesQuery("anything", "  "))
        assertFalse(SubscriptionListViewModel.matchesQuery("Claude Pro", "gpt"))
    }

    @Test
    fun list_pinsTheSoonestDue_andSearchHidesIt() {
        val state = SubscriptionListViewModel.build(SampleData.all, AppSettings(), CategoryFilter.ALL, SampleData.today)
        // LA is overdue (due 9-26): it leads; it stays in items, the tiles leave it out.
        assertEquals(4L, state.pinned!!.id)
        assertTrue(state.items.any { it.id == 4L })
        assertFalse(state.collection.any { it.id == 4L })
        val searched = SubscriptionListViewModel.build(SampleData.all, AppSettings(), CategoryFilter.ALL, SampleData.today, query = "claude")
        assertNull(searched.pinned)
        assertEquals(listOf(2L), searched.items.map { it.id })
        assertTrue(SubscriptionListViewModel.build(SampleData.all, AppSettings(), CategoryFilter.ALL, SampleData.today, query = "zzz").isFilteredEmpty)
        // "另有 N 个已归档订阅未显示" only counts archived ones the search would have found.
        assertEquals(1, state.hiddenArchivedCount)
        assertEquals(0, searched.hiddenArchivedCount)
        assertEquals(1, SubscriptionListViewModel.build(SampleData.all, AppSettings(), CategoryFilter.ALL, SampleData.today, query = "vluer").hiddenArchivedCount)
    }

    @Test
    fun list_needsInfoFilter_andViewMode() = runTest {
        val all = SampleData.all + SubscriptionWithVps(SampleData.incompleteVps, SampleData.incompleteVpsConfig)
        val state = SubscriptionListViewModel.build(all, AppSettings(listGrid = false), CategoryFilter.NEEDS_INFO, SampleData.today)
        assertEquals(listOf(7L), state.items.map { it.id })
        assertEquals(1, state.counts[CategoryFilter.NEEDS_INFO])
        assertNull("待补充 is never pinned", state.pinned)
        assertFalse(state.grid)
        // The choice is remembered.
        val store = SettingsStore(FakePreferencesDataStore())
        assertTrue(store.settings.first().listGrid)
        store.setListGrid(false)
        assertFalse(store.settings.first().listGrid)
    }

    // ------------------------------------------------------------------ Icons in the form and in backups

    @Test
    fun form_carriesIconColorAndFree() {
        val base = SampleData.chatGpt.copy(iconKey = "openai")
        val form = SubscriptionForm.from(base, null).copy(iconKey = "image:abc.png", iconColor = 0xFF123456.toInt())
        val saved = (SubscriptionFormValidator.validate(form, base, null) as FormResult.Valid).subscription
        assertEquals("image:abc.png", saved.iconKey)
        assertEquals(0xFF123456.toInt(), saved.iconColor)
        val auto = (SubscriptionFormValidator.validate(form.copy(iconKey = null, iconColor = null), base, null) as FormResult.Valid).subscription
        assertNull(auto.iconKey)
        // Free: the price is not asked for.
        val free = SubscriptionForm(name = "免费服务", price = "", nextDue = today, isFree = true)
        val freeSaved = (SubscriptionFormValidator.validate(free, null, null) as FormResult.Valid).subscription
        assertTrue(freeSaved.isFree)
        assertEquals(0L, freeSaved.priceMinor)
    }

    @Test
    fun imageKeys_areRecognizedSafely() {
        assertEquals("a.png", BrandCatalog.imageName("image:a.png"))
        assertNull(BrandCatalog.imageName("image:../x.png"))
        assertNull(BrandCatalog.imageName("openai"))
        assertNull(BrandCatalog.imageName(null))
        // A picture that is gone falls back to the name's icon.
        assertEquals("openai", BrandCatalog.resolve("ChatGPT Plus", iconKey = "image:gone.png").brandId)
    }

    @Test
    fun backup_keepsIconAndRegion_andReadsOlderFiles() {
        val sub = Subscription(
            name = "甲骨文 法兰克福", category = Category.VPS, priceMinor = 3_600, currency = "CNY", cycle = BillingCycle.MONTHLY,
            nextDue = today, iconKey = "oracle", iconColor = 0xFF334455.toInt(),
        )
        val vps = VpsConfig(komariUuid = "u-1", komariName = "fra", komariRegion = "🇩🇪")
        val text = BackupCodec.write(listOf(SubscriptionWithVps(sub, vps)), Instant.parse("2026-09-28T00:00:00Z"))
        assertTrue(text.contains("\"icon_key\": \"oracle\""))
        val read = (BackupCodec.read(text) as BackupReadResult.Ok).items.single()
        assertEquals("oracle", read.subscription.iconKey)
        assertEquals(0xFF334455.toInt(), read.subscription.iconColor)
        assertEquals("🇩🇪", read.vps!!.komariRegion)

        // A file written before the icon fields existed.
        val old = """
            {"format":"vluer-subscriptions","version":1,"subscriptions":[
              {"name":"ChatGPT","category":"AI","price_minor":2000,"currency":"USD","cycle_unit":"MONTH","cycle_count":1,"next_due":"2026-10-01"},
              {"name":"blank icon","category":"AI","price_minor":100,"currency":"USD","next_due":"2026-10-01","icon_key":"  ","icon_color":null}
            ]}
        """.trimIndent()
        val items = (BackupCodec.read(old) as BackupReadResult.Ok).items
        assertEquals(2, items.size)
        assertTrue(items.all { it.subscription.iconKey == null && it.subscription.iconColor == null })
    }

    // ------------------------------------------------------------------ Edit page saved state

    @Test
    fun linkChange_keepsTheRegion_andReadsTheOlderForm() {
        val link = KomariLinkChange.Link("uuid-1", "东京", "亚洲", "🇯🇵")
        assertEquals(link, SubscriptionEditViewModel.decodeLink(SubscriptionEditViewModel.encodeLink(link)))
        val noRegion = KomariLinkChange.Link("uuid-2", null, null, null)
        assertEquals(noRegion, SubscriptionEditViewModel.decodeLink(SubscriptionEditViewModel.encodeLink(noRegion)))
        // Written by the previous version (four parts, no region).
        val old = listOf("link", "uuid-3", "name", "group").joinToString("\u001F")
        assertEquals(KomariLinkChange.Link("uuid-3", "name", "group", null), SubscriptionEditViewModel.decodeLink(old))
    }

    // ------------------------------------------------------------------ Node facts for "节点信息"

    @Test
    fun nodes_carryMachineFacts() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val body = requireNotNull(javaClass.classLoader!!.getResourceAsStream("komari/nodes-1.5.1.json")).bufferedReader().use { it.readText() }
            server.enqueue(MockResponse.Builder().code(200).setHeader("Content-Type", "application/json").body(body).build())
            val api = OkHttpKomariApi(KomariEndpoint("http://localhost:${server.port}", null), OkHttpKomariApi.defaultClient(), ZoneOffset.UTC)
            val node = (api.getNodes() as KomariResult.Ok).value.nodes.first()
            assertEquals("AMD EPYC 7551 32-Core Processor", node.machine.cpuName)
            assertEquals(2, node.machine.cpuCores)
            assertEquals("kvm", node.machine.virtualization)
            assertEquals("Ubuntu 22.04.5 LTS", node.machine.os)
            assertEquals("6.8.0-1049-oracle", node.machine.kernelVersion)
            assertEquals(48_384_024_064L, node.machine.diskTotal)
            assertNotNull(node.region)
        } finally {
            server.close()
        }
    }
}
