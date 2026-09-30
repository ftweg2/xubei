package com.ftwegc.myapplication.data.remote.komari

import com.ftwegc.myapplication.data.prefs.SecretStore
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.repo.DirectTransactionRunner
import com.ftwegc.myapplication.data.repo.FakeCipher
import com.ftwegc.myapplication.data.repo.FakeKomariApi
import com.ftwegc.myapplication.data.repo.FakePreferencesDataStore
import com.ftwegc.myapplication.data.repo.FakeSubscriptionDao
import com.ftwegc.myapplication.data.repo.FakeTables
import com.ftwegc.myapplication.data.repo.FakeVpsDao
import com.ftwegc.myapplication.data.repo.ImportStatus
import com.ftwegc.myapplication.data.repo.KomariRepository
import com.ftwegc.myapplication.data.repo.RecordingFactory
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.data.repo.TrafficRepository
import com.ftwegc.myapplication.domain.model.DueStatus
import com.ftwegc.myapplication.ui.common.TrafficUi
import com.ftwegc.myapplication.ui.common.needsBillingInfo
import com.ftwegc.myapplication.ui.common.toUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Real `GET /api/nodes` and `GET /api/recent/{uuid}` responses of Komari 1.5.1 (guest, uuids
 * replaced), from `docs/komari-samples/`, copied to `src/test/resources/komari/`.
 */
class KomariSamplesTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("komari/$name")) { "missing $name" }
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun serve(body: String) {
        server.enqueue(MockResponse.Builder().code(200).setHeader("Content-Type", "application/json; charset=utf-8").body(body).build())
    }

    private fun api() = OkHttpKomariApi(KomariEndpoint("http://localhost:${server.port}", null), OkHttpKomariApi.defaultClient(), ZoneOffset.UTC)

    private fun nodes(): KomariNodeList = runBlocking {
        serve(resource("nodes-1.5.1.json"))
        (api().getNodes() as KomariResult.Ok).value
    }

    @Test
    fun nodesSample_parses() {
        val list = nodes()
        assertEquals(0, list.droppedCount)
        assertEquals(
            listOf("甲骨文-eu-92", "甲骨文-eu-130", "甲骨文-us-129", "甲骨文-us-80", "AWS-SG"),
            list.nodes.map { it.name },
        )
        assertEquals("00000000-0000-4000-8000-000000000001", list.nodes.first().uuid)
        list.nodes.forEach { n ->
            // Nothing billing-related was filled in on this panel.
            assertEquals(n.name, 0.0, n.price, 0.0)
            assertEquals(n.name, 0, n.billingCycleDays)
            assertNull(n.name, n.expiredAt)
            assertEquals(n.name, 0L, n.trafficLimitBytes)
            assertEquals(n.name, "max", n.trafficLimitType)
            assertEquals(n.name, "$", n.currency)
            assertFalse(n.name, n.hidden)
            assertTrue(n.name, n.tags.isEmpty())
        }
    }

    @Test
    fun recentSample_newestIsLastAndPicked() = runBlocking {
        serve(resource("recent-1.5.1.json"))
        val latest = (api().getLatest("00000000-0000-4000-8000-000000000001") as KomariResult.Ok).value!!
        assertEquals(685_567_691_447L, latest.totalUp)
        assertEquals(633_439_985_440L, latest.totalDown)
        assertEquals(12_186_921L, latest.uptimeSec)
        assertEquals(Instant.parse("2026-09-28T12:16:58.554845943Z"), latest.reportedAt)
        assertEquals("about 141 days of uptime", 141L, latest.uptimeSec!! / 86_400)
    }

    @Test
    fun recentSample_orderDoesNotMatter() = runBlocking {
        val root = Json.parseToJsonElement(resource("recent-1.5.1.json")).jsonObject
        val reversed = JsonObject(root + ("data" to JsonArray(root.getValue("data").jsonArray.reversed())))
        serve(reversed.toString())
        val latest = (api().getLatest("x") as KomariResult.Ok).value!!
        assertEquals(685_567_691_447L, latest.totalUp)
    }

    @Test
    fun offlineNode_emptyRecent() = runBlocking {
        serve("""{"data":[],"message":"","status":"success"}""")
        assertNull((api().getLatest("x") as KomariResult.Ok).value)
    }

    @Test
    fun importingSampleNodes_givesToBeCompletedSubscriptions() = runTest {
        val zone = ZoneId.of("Asia/Shanghai")
        val clock = Clock.fixed(Instant.parse("2026-09-28T12:20:00Z"), zone)
        val tables = FakeTables()
        val subDao = FakeSubscriptionDao(tables)
        val vpsDao = FakeVpsDao(tables)
        val tx = DirectTransactionRunner()
        val settings = SettingsStore(FakePreferencesDataStore())
        val secrets = SecretStore(FakePreferencesDataStore(), FakeCipher(), Dispatchers.Unconfined)
        val fake = FakeKomariApi(nodes = KomariResult.Ok(nodes()))
        val repo = KomariRepository(settings, secrets, RecordingFactory(fake), subDao, vpsDao, tx, clock)
        val subs = SubscriptionRepository(subDao, vpsDao, tx, clock)
        repo.saveConfig("http://10.0.2.2:8765", com.ftwegc.myapplication.data.repo.KeyInput.None)

        val preview = (repo.loadImportPreview() as KomariResult.Ok).value
        assertTrue(preview.items.all { it.status == ImportStatus.NEW && it.billingMissing })

        val summary = repo.importNodes(preview.items.map { it.node })
        assertEquals(5, summary.created)
        assertEquals(5, summary.incompleteIds.size)

        val today = clock.instant().atZone(zone).toLocalDate()
        subs.observeAll().first().forEach { item ->
            val sub = item.subscription
            assertEquals(sub.name, "CNY", sub.currency) // app default, not Komari's "$" placeholder
            assertEquals(0L, sub.priceMinor)
            assertNull(sub.nextDue)
            assertFalse(sub.isPermanent)
            assertTrue(sub.needsBillingInfo())
            val card = item.toUi(today, 7)
            assertTrue(card.needsBillingInfo)
            assertEquals("never shown as expired", DueStatus.NONE, card.due.status)
            assertNull(card.monthlyMinor)
        }

        // Traffic from the recent sample: cumulative since boot, unlimited.
        fake.latest = { KomariResult.Ok(KomariLatest(685_567_691_447L, 633_439_985_440L, 12_186_921L, Instant.parse("2026-09-28T12:16:58Z"))) }
        TrafficRepository({ KomariResult.Ok(fake) }, vpsDao, clock).refreshAll()
        val first = subs.observeAll().first().first()
        val traffic = TrafficUi.of(first.vps!!)
        assertNull(traffic.usage!!.percent)
        assertEquals(685_567_691_447L, traffic.usage!!.usedBytes) // MAX of up / down
        assertEquals(12_186_921L, traffic.uptimeSec)

        // A sync against the same unfilled panel changes nothing.
        val sync = (repo.sync() as KomariResult.Ok).value
        assertEquals(0, sync.updated)
        assertEquals(5, sync.unchanged)
    }
}
