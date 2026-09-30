package com.ftwegc.myapplication.domain.calc

import com.ftwegc.myapplication.domain.model.CounterSample
import java.time.Duration

/**
 * What happened between two readings, beyond a plain difference. Stored with each snapshot, so
 * the UI can tell the user that a machine rebooted or that its agent seems to use
 * `--month-rotate`.
 */
enum class DeltaNote {
    /** The machine rebooted (uptime) and its counters restarted from 0. */
    REBOOT,

    /** The counters went down without a reboot (for example an agent `--month-rotate` reset). */
    COUNTER_RESET,

    /**
     * The machine rebooted but its counters kept growing: the agent keeps a persistent total
     * (`--month-rotate` stores its statistics in a file). Counted as a plain difference.
     */
    REBOOT_COUNTERS_KEPT,

    /**
     * The counters went down by less than [CounterDeltaCalculator.ROLLBACK_TOLERANCE] of a large
     * value: a persistent total that lost its last unsaved minutes after an agent restart, not a
     * reset. Counted as no traffic in the direction that went down.
     */
    SMALL_ROLLBACK,
}

/**
 * Traffic added between two counter readings (bytes).
 *
 * @property resetDetected the counters were found to have restarted (reboot or counter reset);
 *   the delta is then the current reading itself.
 * @property skipped the reading is a duplicate or older than the previous one and must be ignored
 *   (do not replace the stored baseline with it).
 * [note] tells what was detected (`null` for a plain difference); it is extra detail and not part
 * of equality.
 */
data class DeltaResult(
    val deltaUp: Long,
    val deltaDown: Long,
    val resetDetected: Boolean,
    val skipped: Boolean,
) {
    var note: DeltaNote? = null
        private set

    constructor(deltaUp: Long, deltaDown: Long, resetDetected: Boolean, skipped: Boolean, note: DeltaNote?) :
        this(deltaUp, deltaDown, resetDetected, skipped) {
        this.note = note
    }
}

/** How a reboot or a drop of the counters is interpreted. */
enum class ResetPolicy {
    /**
     * Counters restart on every reboot (Komari's default "since boot" totals): a reboot or any
     * decrease counts the current reading as new traffic.
     */
    SINCE_BOOT,

    /**
     * Used for app accumulation (counting mode 2), where counting a whole month twice is far worse
     * than missing a little: a reboot whose counters did not go down is a persistent total (agent
     * `--month-rotate`) and a small drop of a large value is a lost unsaved tail, both counted as a
     * plain difference. Since-boot counters are still handled correctly: after a real reboot they
     * restart near 0.
     */
    TOLERANT,
}

/** Computes byte deltas between consecutive Komari counter samples. */
object CounterDeltaCalculator {

    /** Tolerance in seconds when comparing uptime to the wall-clock gap between reports. */
    const val UPTIME_TOLERANCE_SEC: Long = 60

    /** [ResetPolicy.TOLERANT]: a drop to at least this share of the previous value is a rollback. */
    const val ROLLBACK_TOLERANCE: Double = 0.9

    /** [ResetPolicy.TOLERANT]: rollbacks are only assumed for values of at least this many bytes. */
    const val ROLLBACK_MIN_BYTES: Long = 64L shl 20

    /**
     * Delta from [prev] to [cur], checked in this order:
     * 1. `prev == null`: first sample, delta 0 (baseline only).
     * 2. `cur.reportedAt <= prev.reportedAt`: duplicate/stale, delta 0, `skipped = true`.
     * 3. Both uptimes known and (`cur.uptime < prev.uptime` or
     *    `cur.uptime + 60 < seconds between reports`): reboot, delta = cur, `resetDetected = true`.
     *    With [ResetPolicy.TOLERANT] a reboot whose counters did not go down is a plain difference
     *    ([DeltaNote.REBOOT_COUNTERS_KEPT]).
     * 4. Either direction decreased: counter reset, delta = cur for both directions,
     *    `resetDetected = true`. With [ResetPolicy.TOLERANT] and no reboot, a drop to at least
     *    90 % of a value of 64 MiB or more is a rollback ([DeltaNote.SMALL_ROLLBACK]).
     * 5. Otherwise delta = cur − prev.
     *
     * Negative counters are treated as 0; a negative uptime is treated as unknown.
     */
    fun computeDelta(prev: CounterSample?, cur: CounterSample, policy: ResetPolicy = ResetPolicy.SINCE_BOOT): DeltaResult {
        if (prev == null) return DeltaResult(0, 0, resetDetected = false, skipped = false)
        if (!cur.reportedAt.isAfter(prev.reportedAt)) {
            return DeltaResult(0, 0, resetDetected = false, skipped = true)
        }

        val prevUp = prev.totalUp.coerceAtLeast(0)
        val prevDown = prev.totalDown.coerceAtLeast(0)
        val curUp = cur.totalUp.coerceAtLeast(0)
        val curDown = cur.totalDown.coerceAtLeast(0)
        val decreased = curUp < prevUp || curDown < prevDown

        val prevUptime = prev.uptimeSec?.takeIf { it >= 0 }
        val curUptime = cur.uptimeSec?.takeIf { it >= 0 }
        var rebooted = false
        if (prevUptime != null && curUptime != null) {
            val gapSec = Duration.between(prev.reportedAt, cur.reportedAt).seconds
            // Written as `cur < gap - tolerance` (equivalent to `cur + tolerance < gap`) to avoid overflow.
            rebooted = curUptime < prevUptime || curUptime < gapSec - UPTIME_TOLERANCE_SEC
        }

        if (rebooted) {
            if (policy == ResetPolicy.TOLERANT && !decreased) {
                return DeltaResult(curUp - prevUp, curDown - prevDown, resetDetected = false, skipped = false, note = DeltaNote.REBOOT_COUNTERS_KEPT)
            }
            return DeltaResult(curUp, curDown, resetDetected = true, skipped = false, note = DeltaNote.REBOOT)
        }

        if (decreased) {
            if (policy == ResetPolicy.TOLERANT && isRollback(prevUp, curUp) && isRollback(prevDown, curDown)) {
                return DeltaResult(
                    (curUp - prevUp).coerceAtLeast(0),
                    (curDown - prevDown).coerceAtLeast(0),
                    resetDetected = false,
                    skipped = false,
                    note = DeltaNote.SMALL_ROLLBACK,
                )
            }
            return DeltaResult(curUp, curDown, resetDetected = true, skipped = false, note = DeltaNote.COUNTER_RESET)
        }
        return DeltaResult(curUp - prevUp, curDown - prevDown, resetDetected = false, skipped = false)
    }

    /** True when [cur] did not go down, or went down only a little from a large [prev]. */
    private fun isRollback(prev: Long, cur: Long): Boolean =
        cur >= prev || (prev >= ROLLBACK_MIN_BYTES && cur >= prev * ROLLBACK_TOLERANCE)
}
