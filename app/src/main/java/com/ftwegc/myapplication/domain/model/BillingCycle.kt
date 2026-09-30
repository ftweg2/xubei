package com.ftwegc.myapplication.domain.model

/** Unit of a [BillingCycle]. */
enum class CycleUnit {
    /** Calendar months; the due day follows the subscription's anchor day. */
    MONTH,

    /** A fixed number of days. */
    DAY,

    /** One-time purchase; never renews. */
    ONCE,
}

/**
 * How often a subscription is billed: [count] × [unit].
 *
 * Invariants: `count >= 1`; a [CycleUnit.ONCE] cycle always has `count == 1`
 * (use [of] to normalize values read from storage).
 */
data class BillingCycle(val unit: CycleUnit, val count: Int) {

    init {
        require(count >= 1) { "cycle count must be >= 1, was $count" }
        require(unit != CycleUnit.ONCE || count == 1) { "ONCE cycle must have count 1, was $count" }
    }

    companion object {
        /** Monthly (MONTH × 1). */
        val MONTHLY = BillingCycle(CycleUnit.MONTH, 1)

        /** Quarterly (MONTH × 3). */
        val QUARTERLY = BillingCycle(CycleUnit.MONTH, 3)

        /** Semi-annual (MONTH × 6). */
        val SEMI_ANNUAL = BillingCycle(CycleUnit.MONTH, 6)

        /** Annual (MONTH × 12). */
        val ANNUAL = BillingCycle(CycleUnit.MONTH, 12)

        /** Every two years (MONTH × 24). */
        val BIENNIAL = BillingCycle(CycleUnit.MONTH, 24)

        /** Every three years (MONTH × 36). */
        val TRIENNIAL = BillingCycle(CycleUnit.MONTH, 36)

        /** One-time purchase. */
        val ONE_TIME = BillingCycle(CycleUnit.ONCE, 1)

        /** Custom cycle of [days] days (`days >= 1`). */
        fun days(days: Int): BillingCycle = BillingCycle(CycleUnit.DAY, days)

        /** Cycle of [months] calendar months (`months >= 1`). */
        fun months(months: Int): BillingCycle = BillingCycle(CycleUnit.MONTH, months)

        /**
         * Builds a cycle from stored values, normalizing the count of a [CycleUnit.ONCE] cycle to 1.
         * Throws [IllegalArgumentException] when a MONTH/DAY count is < 1.
         */
        fun of(unit: CycleUnit, count: Int): BillingCycle =
            if (unit == CycleUnit.ONCE) ONE_TIME else BillingCycle(unit, count)
    }
}
