package com.ftwegc.myapplication.data.repo

import com.ftwegc.myapplication.data.remote.komari.KomariApi
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.remote.komari.KomariLatest
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.ui.common.TrafficIssue
import com.ftwegc.myapplication.ui.common.TrafficUi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TrafficRepositoryTest {

    private val now = Instant.parse("2026-09-28T04:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Asia/Shanghai"))
    private val tables = FakeTables()
    private val vpsDao = FakeVpsDao(tables)
    private val subs = SubscriptionRepository(FakeSubscriptionDao(tables), vpsDao, DirectTransactionRunner(), clock)
    private val api = FakeKomariApi()
    private var apiResult: KomariResult<KomariApi> = KomariResult.Ok(api)
    private val repo = TrafficRepository({ apiResult }, vpsDao, clock)

    private val gib = 1L shl 30
    private val reported = Instant.parse("2026-09-28T03:59:30Z")

    private suspend fun vps(uuid: String?, limit: Long = 1L shl 40, type: TrafficLimitType = TrafficLimitType.MAX): Long =
        subs.save(
            Subscription(name = "VPS $uuid", category = Category.VPS, priceMinor = 100, currency = "¥", cycle = BillingCycle.MONTHLY),
            VpsConfig(trafficLimitBytes = limit, trafficLimitType = type),
            link = if (uuid != null) KomariLinkChange.Link(uuid, uuid, null) else KomariLinkChange.Keep,
        )

    private fun reading(up: Long, down: Long) = KomariResult.Ok(KomariLatest(up, down, 3_600, reported))

    @Test
    fun refreshAll_storesCountersAndClearsError() = runTest {
        val id = vps("a")
        tables.vps.value = tables.vps.value + (id to tables.vps.value.getValue(id).copy(lastError = "OFFLINE"))
        api.latest = { reading(10 * gib, 20 * gib) }
        val summary = (repo.refreshAll() as KomariResult.Ok).value
        assertEquals(TrafficRefreshSummary(updated = 1, offline = 0, failed = 0), summary)
        val row = tables.vps.value.getValue(id)
        assertEquals(10 * gib, row.lastTotalUp)
        assertEquals(20 * gib, row.lastTotalDown)
        assertEquals(3_600L, row.lastUptimeSec)
        assertEquals(reported.toEpochMilli(), row.lastReportedAt)
        assertEquals(now.toEpochMilli(), row.lastFetchAt)
        assertNull(row.lastError)
    }

    @Test
    fun offlineNode_keepsLastReading() = runTest {
        val id = vps("a")
        api.latest = { reading(1, 2) }
        repo.refreshAll()
        api.latest = { KomariResult.Ok(null) }
        val summary = (repo.refreshAll() as KomariResult.Ok).value
        assertEquals(1, summary.offline)
        val row = tables.vps.value.getValue(id)
        assertEquals(1L, row.lastTotalUp)
        assertEquals(TrafficRepository.OFFLINE, row.lastError)
        val ui = TrafficUi.of(subs.get(id)!!.vps!!)
        assertEquals(TrafficIssue.Offline, ui.issue)
        assertEquals("last data still shown", 2L, ui.usage!!.usedBytes)
    }

    @Test
    fun failedNode_storesErrorCode_andDoesNotAffectOthers() = runTest {
        val ok = vps("ok")
        val bad = vps("bad")
        val off = vps("off")
        api.latest = { uuid ->
            when (uuid) {
                "ok" -> reading(5, 6)
                "bad" -> KomariResult.Err(KomariError.Http(500))
                else -> KomariResult.Ok(null)
            }
        }
        val summary = (repo.refreshAll() as KomariResult.Ok).value
        assertEquals(TrafficRefreshSummary(updated = 1, offline = 1, failed = 1), summary)
        assertEquals(6L, tables.vps.value.getValue(ok).lastTotalDown)
        assertEquals("HTTP_500", tables.vps.value.getValue(bad).lastError)
        assertNull(tables.vps.value.getValue(bad).lastTotalUp)
        assertEquals("OFFLINE", tables.vps.value.getValue(off).lastError)
        assertEquals(TrafficIssue.Failed(KomariError.Http(500)), TrafficUi.of(subs.get(bad)!!.vps!!).issue)
    }

    @Test
    fun allFailingForTheSameReason_reportsCommonError() = runTest {
        vps("a")
        vps("b")
        api.latest = { KomariResult.Err(KomariError.Unauthorized(false)) }
        val summary = (repo.refreshAll() as KomariResult.Ok).value
        assertEquals(KomariError.Unauthorized(false), summary.commonError)
        api.latest = { uuid -> if (uuid == "a") KomariResult.Err(KomariError.Http(500)) else KomariResult.Err(KomariError.NotFound) }
        assertNull((repo.refreshAll() as KomariResult.Ok).value.commonError)
    }

    @Test
    fun onlyLinkedVpsAreFetched() = runTest {
        vps(null)
        vps("a")
        repo.refreshAll()
        assertEquals(listOf("a"), api.latestCalls)
    }

    @Test
    fun concurrencyIsLimitedToFour() = runTest {
        repeat(10) { vps("n$it") }
        api.latestDelayMillis = 100
        api.latest = { reading(1, 1) }
        val summary = (repo.refreshAll() as KomariResult.Ok).value
        assertEquals(10, summary.updated)
        assertEquals(TrafficRepository.DEFAULT_CONCURRENCY, api.maxConcurrent.get())
    }

    @Test
    fun notConfigured_failsWithoutTouchingRows() = runTest {
        val id = vps("a")
        apiResult = KomariResult.Err(KomariError.NotConfigured)
        assertEquals(KomariResult.Err(KomariError.NotConfigured), repo.refreshAll())
        assertNull(tables.vps.value.getValue(id).lastFetchAt)
    }

    @Test
    fun refreshOne() = runTest {
        val unlinked = vps(null)
        val id = vps("a")
        assertEquals(KomariResult.Ok(false), repo.refreshOne(unlinked))
        assertTrue(api.latestCalls.isEmpty())
        api.latest = { reading(3, 4) }
        assertEquals(KomariResult.Ok(true), repo.refreshOne(id))
        api.latest = { KomariResult.Err(KomariError.Unauthorized(true)) }
        assertEquals(KomariResult.Err(KomariError.Unauthorized(true)), repo.refreshOne(id))
        assertEquals("UNAUTHORIZED_KEY", tables.vps.value.getValue(id).lastError)
        assertEquals(3L, tables.vps.value.getValue(id).lastTotalUp)
    }

    @Test
    fun usedAmountFollowsLimitType() = runTest {
        val types = TrafficLimitType.entries.associateWith { vps("t-${it.name}", limit = 100 * gib, type = it) }
        api.latest = { reading(30 * gib, 50 * gib) }
        repo.refreshAll()
        val expected = mapOf(
            TrafficLimitType.SUM to 80 * gib,
            TrafficLimitType.MAX to 50 * gib,
            TrafficLimitType.MIN to 30 * gib,
            TrafficLimitType.UP to 30 * gib,
            TrafficLimitType.DOWN to 50 * gib,
        )
        types.forEach { (type, id) ->
            val usage = TrafficUi.of(subs.get(id)!!.vps!!).usage!!
            assertEquals(type.name, expected.getValue(type), usage.usedBytes)
            assertEquals(type.name, expected.getValue(type) * 100.0 / (100 * gib), usage.percent!!, 1e-9)
        }
    }

    @Test
    fun unlimited_hasNoPercent() = runTest {
        val id = vps("u", limit = 0)
        api.latest = { reading(1 * gib, 2 * gib) }
        repo.refreshAll()
        val usage = TrafficUi.of(subs.get(id)!!.vps!!).usage!!
        assertNull(usage.percent)
        assertEquals(2 * gib, usage.usedBytes)
    }
}
