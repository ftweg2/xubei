package com.ftwegc.myapplication.ui.components

import com.ftwegc.myapplication.domain.model.BillingCycle

/** Cycle choices of the editor's cycle switch. [CUSTOM] reveals a count + unit input. */
enum class CyclePreset(val cycle: BillingCycle?) {
    MONTHLY(BillingCycle.MONTHLY),
    QUARTERLY(BillingCycle.QUARTERLY),
    SEMI_ANNUAL(BillingCycle.SEMI_ANNUAL),
    ANNUAL(BillingCycle.ANNUAL),
    ONCE(BillingCycle.ONE_TIME),
    CUSTOM(null);

    companion object {
        /** Preset matching [cycle], or [CUSTOM]. */
        fun of(cycle: BillingCycle): CyclePreset = entries.firstOrNull { it.cycle == cycle } ?: CUSTOM
    }
}
