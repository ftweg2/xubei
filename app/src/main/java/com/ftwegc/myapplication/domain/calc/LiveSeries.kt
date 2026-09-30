package com.ftwegc.myapplication.domain.calc

import java.time.Duration
import java.time.Instant

/**
 * One live report of a node, reduced to what the charts use.
 *
 * @property upSpeed / [downSpeed] bytes per second as the agent measured them.
 * @property totalUp / [totalDown] cumulative counters in bytes.
 * @property cpuPercent CPU usage in percent; [ramFraction] / [diskFraction] 0..1, `null` when the
 *   total is unknown.
 */
data class LiveReading(
    val at: Instant?,
    val upSpeed: Long,
    val downSpeed: Long,
    val totalUp: Long,
    val totalDown: Long,
    val cpuPercent: Double,
    val ramFraction: Double?,
    val diskFraction: Double?,
    val load1: Double = 0.0,
    val load5: Double = 0.0,
    val load15: Double = 0.0,
    val process: Int = 0,
    val tcp: Int = 0,
    val udp: Int = 0,
    val uptimeSec: Long? = null,
)

/**
 * The last minute of a node for the live charts.
 *
 * @property online the panel returned recent reports.
 * @property up / [down] speeds in bytes per second, oldest first.
 */
data class LiveSeries(
    val online: Boolean,
    val up: List<Long>,
    val down: List<Long>,
    val cpu: List<Double>,
    val latestUp: Long,
    val latestDown: Long,
    val latestCpu: Double?,
    val latestRam: Double?,
    val latestDisk: Double?,
    /** The newest report (load, processes, connections, uptime), `null` while offline. */
    val latest: LiveReading? = null,
)

/** Turns live reports into chart series. Pure. */
object LiveSeriesCalculator {

    /**
     * Speeds of [readings] (oldest first). The agent's own speed fields are used; when they are
     * all zero while the counters moved (an agent that does not report speeds), speeds are
     * derived from counter differences over the report times.
     */
    fun speeds(readings: List<LiveReading>): Pair<List<Long>, List<Long>> {
        val up = readings.map { it.upSpeed.coerceAtLeast(0) }
        val down = readings.map { it.downSpeed.coerceAtLeast(0) }
        val reported = up.any { it > 0 } || down.any { it > 0 }
        if (reported || readings.size < 2) return up to down
        val derivedUp = LongArray(readings.size)
        val derivedDown = LongArray(readings.size)
        for (i in 1 until readings.size) {
            val a = readings[i - 1]
            val b = readings[i]
            val seconds = if (a.at != null && b.at != null) Duration.between(a.at, b.at).toMillis() / 1000.0 else 0.0
            if (seconds <= 0.0) continue
            derivedUp[i] = ((b.totalUp - a.totalUp).coerceAtLeast(0) / seconds).toLong()
            derivedDown[i] = ((b.totalDown - a.totalDown).coerceAtLeast(0) / seconds).toLong()
        }
        derivedUp[0] = derivedUp.getOrElse(1) { 0 }
        derivedDown[0] = derivedDown.getOrElse(1) { 0 }
        return derivedUp.toList() to derivedDown.toList()
    }

    fun series(readings: List<LiveReading>): LiveSeries {
        val (up, down) = speeds(readings)
        val last = readings.lastOrNull()
        return LiveSeries(
            online = readings.isNotEmpty(),
            up = up,
            down = down,
            cpu = readings.map { it.cpuPercent },
            latestUp = up.lastOrNull() ?: 0,
            latestDown = down.lastOrNull() ?: 0,
            latestCpu = last?.cpuPercent,
            latestRam = last?.ramFraction,
            latestDisk = last?.diskFraction,
            latest = last,
        )
    }

    /** used / total, `null` when the total is unknown. */
    fun fraction(used: Long, total: Long): Double? = if (total > 0) (used.toDouble() / total).coerceIn(0.0, 1.0) else null
}
