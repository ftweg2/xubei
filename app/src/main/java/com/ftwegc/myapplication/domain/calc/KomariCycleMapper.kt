package com.ftwegc.myapplication.domain.calc

import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.CycleUnit

/** Maps Komari's `billing_cycle` (days) to and from [BillingCycle]. */
object KomariCycleMapper {

    /** Komari `billing_cycle` value meaning a one-time purchase. */
    const val ONCE_DAYS: Int = -1

    /** Inclusive day ranges recognized as whole-month cycles, and the month count they map to. */
    private val MONTH_RANGES: List<Pair<IntRange, Int>> = listOf(
        27..32 to 1,
        87..95 to 3,
        175..185 to 6,
        360..370 to 12,
        720..750 to 24,
        1080..1150 to 36,
        1800..1850 to 60,
    )

    /** Canonical day counts shown for whole-month cycles. */
    private val MONTH_TO_DAYS: Map<Int, Int> = mapOf(
        1 to 30,
        3 to 90,
        6 to 180,
        12 to 365,
        24 to 730,
        36 to 1095,
        60 to 1825,
    )

    /**
     * Cycle for a Komari `billing_cycle` in days.
     *
     * 27–32 → MONTH×1, 87–95 → MONTH×3, 175–185 → MONTH×6, 360–370 → MONTH×12, 720–750 → MONTH×24,
     * 1080–1150 → MONTH×36, 1800–1850 → MONTH×60 (all inclusive); -1 → ONCE; any other positive
     * value → DAY×n; 0 or any other negative value → `null` (unknown / not set).
     */
    fun fromDays(billingCycleDays: Int): BillingCycle? {
        if (billingCycleDays == ONCE_DAYS) return BillingCycle.ONE_TIME
        if (billingCycleDays <= 0) return null
        val months = MONTH_RANGES.firstOrNull { (range, _) -> billingCycleDays in range }?.second
        return if (months != null) BillingCycle.months(months) else BillingCycle.days(billingCycleDays)
    }

    /**
     * Day count for display / writing back in Komari's convention.
     *
     * Known month counts use canonical values (1→30, 3→90, 6→180, 12→365, 24→730, 36→1095, 60→1825);
     * other month counts use `round(count × 30.4375)`; DAY×n → n; ONCE → -1.
     * `fromDays(toDays(c)) == c` holds for the known month counts, ONCE, and DAY×n whose n is
     * outside every month range (e.g. DAY×30 maps back to MONTH×1).
     */
    fun toDays(cycle: BillingCycle): Int = when (cycle.unit) {
        CycleUnit.ONCE -> ONCE_DAYS
        CycleUnit.DAY -> cycle.count
        CycleUnit.MONTH -> MONTH_TO_DAYS[cycle.count]
            ?: Math.round(cycle.count * 30.4375).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
