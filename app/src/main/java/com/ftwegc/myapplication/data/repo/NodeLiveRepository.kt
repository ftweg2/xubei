package com.ftwegc.myapplication.data.repo

import com.ftwegc.myapplication.data.remote.komari.KomariApi
import com.ftwegc.myapplication.data.remote.komari.KomariNode
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.data.remote.komari.KomariSample
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Live node data for the charts: every report of the last minute from `GET /api/recent/{uuid}`.
 *
 * Nothing is stored: traffic counters stay the job of [TrafficRepository]. Requests run
 * concurrently with at most [maxConcurrency] at a time (the same limit as the traffic refresh);
 * one node failing never affects the others.
 */
class NodeLiveRepository(
    private val apiProvider: suspend () -> KomariResult<KomariApi>,
    private val maxConcurrency: Int = TrafficRepository.DEFAULT_CONCURRENCY,
) {

    /**
     * The recent samples of each node in [uuids] (oldest first; empty while a node is offline).
     * Fails as a whole only when the panel is not configured or its address is invalid.
     */
    suspend fun recent(uuids: Collection<String>): KomariResult<Map<String, KomariResult<List<KomariSample>>>> {
        val api = when (val r = apiProvider()) {
            is KomariResult.Err -> return r
            is KomariResult.Ok -> r.value
        }
        val ids = uuids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) return KomariResult.Ok(emptyMap())
        val results = coroutineScope {
            val permits = Semaphore(maxConcurrency.coerceAtLeast(1))
            ids.map { id -> async { id to permits.withPermit { api.getRecent(id) } } }.awaitAll()
        }
        return KomariResult.Ok(results.toMap())
    }

    /**
     * The panel's entry for node [uuid] (name, region, machine facts), `Ok(null)` when the panel no
     * longer lists it. Read only: nothing is stored.
     */
    suspend fun node(uuid: String): KomariResult<KomariNode?> {
        val api = when (val r = apiProvider()) {
            is KomariResult.Err -> return r
            is KomariResult.Ok -> r.value
        }
        return when (val nodes = api.getNodes()) {
            is KomariResult.Err -> nodes
            is KomariResult.Ok -> KomariResult.Ok(nodes.value.nodes.firstOrNull { it.uuid == uuid.trim() })
        }
    }
}

/** The chart view of a Komari report. */
fun KomariSample.toReading(): com.ftwegc.myapplication.domain.calc.LiveReading =
    com.ftwegc.myapplication.domain.calc.LiveReading(
        at = reportedAt,
        upSpeed = netUp,
        downSpeed = netDown,
        totalUp = totalUp,
        totalDown = totalDown,
        cpuPercent = cpuPercent,
        ramFraction = com.ftwegc.myapplication.domain.calc.LiveSeriesCalculator.fraction(ramUsed, ramTotal),
        diskFraction = com.ftwegc.myapplication.domain.calc.LiveSeriesCalculator.fraction(diskUsed, diskTotal),
        load1 = load1,
        load5 = load5,
        load15 = load15,
        process = process,
        tcp = tcp,
        udp = udp,
        uptimeSec = uptimeSec,
    )
