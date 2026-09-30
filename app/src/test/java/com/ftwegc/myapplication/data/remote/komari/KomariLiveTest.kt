package com.ftwegc.myapplication.data.remote.komari

import com.ftwegc.myapplication.data.repo.NodeLiveRepository
import com.ftwegc.myapplication.data.repo.toReading
import com.ftwegc.myapplication.domain.calc.LiveReading
import com.ftwegc.myapplication.domain.calc.LiveSeriesCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

/** `/api/recent/{uuid}` as live samples (every field of Komari's report), and the live repository. */
class KomariLiveTest {

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

    private fun wrap(data: String) = """{"status":"success","message":"","data":$data}"""

    @Test
    fun recentSample_parsesEveryField() = runBlocking {
        serve(resource("recent-1.5.1.json"))
        val samples = (api().getRecent("00000000-0000-4000-8000-000000000001") as KomariResult.Ok).value
        assertEquals(3, samples.size)
        val last = samples.last()
        assertEquals(Instant.parse("2026-09-28T12:16:58.554845943Z"), last.reportedAt)
        assertEquals(68.65248224021614, last.cpuPercent, 1e-9)
        assertEquals(519_405_568L, last.ramUsed)
        assertEquals(1_003_114_496L, last.ramTotal)
        assertEquals(464_203_776L, last.swapUsed)
        assertEquals(4_294_963_200L, last.swapTotal)
        assertEquals(26_406_696_448L, last.diskUsed)
        assertEquals(48_384_024_064L, last.diskTotal)
        assertEquals(2.51, last.load1, 1e-9)
        assertEquals(2.58, last.load5, 1e-9)
        assertEquals(2.12, last.load15, 1e-9)
        assertEquals(10_975L, last.netUp)
        assertEquals(5_892L, last.netDown)
        assertEquals(685_567_691_447L, last.totalUp)
        assertEquals(633_439_985_440L, last.totalDown)
        assertEquals(547, last.tcp)
        assertEquals(41, last.udp)
        assertEquals(12_186_921L, last.uptimeSec)
        assertEquals(144, last.process)
        // Oldest first.
        assertTrue(samples.zipWithNext().all { (a, b) -> a.reportedAt!! < b.reportedAt!! })
        assertEquals(listOf(1_885L, 19_740L, 10_975L), samples.map { it.netUp })
    }

    @Test
    fun recent_isSortedByTime_undatedFirst() = runBlocking {
        serve(
            wrap(
                """[
                {"network":{"up":3},"updated_at":"2026-09-28T12:00:06Z"},
                {"network":{"up":1},"updated_at":"2026-09-28T12:00:00Z"},
                {"network":{"up":9}},
                {"network":{"up":2},"updated_at":"2026-09-28T12:00:03Z"}
                ]""",
            ),
        )
        val samples = (api().getRecent("n") as KomariResult.Ok).value
        assertEquals(listOf(9L, 1L, 2L, 3L), samples.map { it.netUp })
        assertNull(samples.first().reportedAt)
    }

    @Test
    fun recent_offlineIsEmpty() = runBlocking {
        serve(wrap("[]"))
        assertEquals(KomariResult.Ok(emptyList<KomariSample>()), api().getRecent("n"))
    }

    @Test
    fun malformedLiveBlocksNeverDropTheReport() = runBlocking {
        // cpu is a number, ram a string, load null, connections an array: the counters still count.
        val body = wrap(
            """[{"cpu":55,"ram":"x","load":null,"connections":[1,2],"swap":{"total":"12","used":-5},
            "network":{"up":1,"down":2,"totalUp":100,"totalDown":200},"uptime":50,"process":"143",
            "updated_at":"2026-09-28T12:00:00Z"}]""",
        )
        serve(body)
        val sample = (api().getRecent("n") as KomariResult.Ok).value.single()
        assertEquals(0.0, sample.cpuPercent, 0.0)
        assertEquals(0L, sample.ramTotal)
        assertEquals(0.0, sample.load1, 0.0)
        assertEquals(0, sample.tcp)
        assertEquals(12L, sample.swapTotal)
        assertEquals(0L, sample.swapUsed)
        assertEquals(143, sample.process)
        assertEquals(100L, sample.totalUp)
        // The traffic reading of the same report is unchanged.
        serve(body)
        val latest = (api().getLatest("n") as KomariResult.Ok).value!!
        assertEquals(100L, latest.totalUp)
        assertEquals(200L, latest.totalDown)
        assertEquals(50L, latest.uptimeSec)
    }

    @Test
    fun recent_errorsLikeTheOtherCalls() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401).body("{}").build())
        assertTrue((api().getRecent("n") as KomariResult.Err).error is KomariError.Unauthorized)
        assertEquals(KomariResult.Err(KomariError.NotFound), api().getRecent("  "))
    }

    // ------------------------------------------------------------ repository

    private class LiveApi(private val answers: Map<String, KomariResult<List<KomariSample>>>, private val delayMillis: Long = 0) : KomariApi {
        val running = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val calls = mutableListOf<String>()
        override suspend fun getVersion(): KomariResult<KomariVersion> = KomariResult.Ok(KomariVersion("1.5.1", ""))
        override suspend fun getNodes(): KomariResult<KomariNodeList> = KomariResult.Ok(KomariNodeList(emptyList()))
        override suspend fun getLatest(uuid: String): KomariResult<KomariLatest?> = KomariResult.Ok(null)
        override suspend fun getRecent(uuid: String): KomariResult<List<KomariSample>> {
            val now = running.incrementAndGet()
            maxConcurrent.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            try {
                synchronized(calls) { calls += uuid }
                if (delayMillis > 0) delay(delayMillis)
                return answers[uuid] ?: KomariResult.Err(KomariError.NotFound)
            } finally {
                running.decrementAndGet()
            }
        }
    }

    private fun sample(up: Long) = KomariSample(
        reportedAt = null, cpuPercent = 10.0, ramUsed = 1, ramTotal = 4, swapUsed = 0, swapTotal = 0, diskUsed = 1, diskTotal = 2,
        load1 = 0.1, load5 = 0.1, load15 = 0.1, netUp = up, netDown = up / 2, totalUp = up, totalDown = up, tcp = 1, udp = 1, uptimeSec = 1, process = 1,
    )

    @Test
    fun repository_fetchesEveryNodeWithLimitedConcurrency() = runTest {
        val answers = (1..10).associate { "n$it" to KomariResult.Ok(listOf(sample(it.toLong()))) as KomariResult<List<KomariSample>> }
        val fake = LiveApi(answers, delayMillis = 50)
        val repo = NodeLiveRepository({ KomariResult.Ok(fake) }, maxConcurrency = 3)
        val result = (repo.recent(answers.keys + listOf("n1", " ", "")) as KomariResult.Ok).value
        assertEquals(10, result.size)
        assertEquals(10, fake.calls.size) // duplicates and blanks skipped
        assertTrue(fake.maxConcurrent.get() <= 3)
        assertEquals(5L, (result.getValue("n5") as KomariResult.Ok).value.single().netUp)
    }

    @Test
    fun repository_notConfiguredAndPartialFailures() = runTest {
        val notConfigured = NodeLiveRepository({ KomariResult.Err(KomariError.NotConfigured) })
        assertEquals(KomariResult.Err(KomariError.NotConfigured), notConfigured.recent(listOf("a")))
        val fake = LiveApi(mapOf("ok" to KomariResult.Ok(emptyList())))
        val result = (NodeLiveRepository({ KomariResult.Ok(fake) }).recent(listOf("ok", "bad")) as KomariResult.Ok).value
        assertEquals(KomariResult.Ok(emptyList<KomariSample>()), result["ok"])
        assertEquals(KomariResult.Err(KomariError.NotFound), result["bad"])
        assertTrue((NodeLiveRepository({ KomariResult.Ok(fake) }).recent(emptyList()) as KomariResult.Ok).value.isEmpty())
    }

    // ------------------------------------------------------------ series

    private fun reading(at: Long?, up: Long, down: Long, totalUp: Long, totalDown: Long) = LiveReading(
        at = at?.let { Instant.ofEpochSecond(it) }, upSpeed = up, downSpeed = down, totalUp = totalUp, totalDown = totalDown,
        cpuPercent = 20.0, ramFraction = 0.5, diskFraction = null,
    )

    @Test
    fun series_usesTheAgentsSpeeds() {
        val s = LiveSeriesCalculator.series(listOf(reading(0, 100, 50, 0, 0), reading(3, 300, 60, 900, 180)))
        assertTrue(s.online)
        assertEquals(listOf(100L, 300L), s.up)
        assertEquals(listOf(50L, 60L), s.down)
        assertEquals(300L, s.latestUp)
        assertEquals(60L, s.latestDown)
        assertEquals(0.5, s.latestRam!!, 0.0)
        assertNull(s.latestDisk)
    }

    @Test
    fun series_derivesSpeedsFromCountersWhenTheAgentReportsNone() {
        val s = LiveSeriesCalculator.series(
            listOf(reading(0, 0, 0, 1_000, 2_000), reading(3, 0, 0, 4_000, 2_600), reading(6, 0, 0, 4_300, 2_600)),
        )
        assertEquals(listOf(1_000L, 1_000L, 100L), s.up)
        assertEquals(listOf(200L, 200L, 0L), s.down)
    }

    @Test
    fun series_offlineAndMapping() {
        val offline = LiveSeriesCalculator.series(emptyList())
        assertTrue(!offline.online && offline.up.isEmpty() && offline.latestCpu == null)
        assertNull(LiveSeriesCalculator.fraction(5, 0))
        assertEquals(0.25, LiveSeriesCalculator.fraction(1, 4)!!, 0.0)
        val r = sample(40).toReading()
        assertEquals(40L, r.upSpeed)
        assertEquals(0.25, r.ramFraction!!, 0.0)
        assertEquals(0.5, r.diskFraction!!, 0.0)
    }
}
