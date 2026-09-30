package com.ftwegc.myapplication.domain.calc

import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.Subscription
import java.time.LocalDate

/** How "mark as renewed" computes the next due date. */
enum class RenewMode {
    /** One cycle after the current due date (today when there is none); the anchor day is kept. */
    FROM_DUE,

    /** One cycle after today; the anchor day moves to today's day of month. */
    FROM_TODAY,
}

/** Result of [RenewCalculator.renew]: the new due date and the anchor day to store with it. */
data class RenewPlan(val nextDue: LocalDate, val anchorDay: Int?)

/** Manual renewal on top of [DueDateCalculator.advance]. */
object RenewCalculator {

    /** False for one-time and permanent subscriptions, which have nothing to renew. */
    fun canRenew(sub: Subscription): Boolean = sub.cycle.unit != CycleUnit.ONCE && !sub.isPermanent

    /**
     * The due date after renewing [sub] once with [mode], or `null` when [canRenew] is false.
     *
     * - [RenewMode.FROM_DUE]: base = `nextDue ?: today`; anchor = `anchorDay ?: base.dayOfMonth`
     *   (kept for MONTH cycles so month ends do not drift).
     * - [RenewMode.FROM_TODAY]: base = today; for MONTH cycles the anchor becomes today's day.
     *
     * DAY cycles keep the stored anchor unchanged (it is not used for them).
     */
    fun renew(sub: Subscription, today: LocalDate, mode: RenewMode): RenewPlan? {
        if (!canRenew(sub)) return null
        val isMonth = sub.cycle.unit == CycleUnit.MONTH
        return when (mode) {
            RenewMode.FROM_DUE -> {
                val base = sub.nextDue ?: today
                val anchor = if (isMonth) sub.anchorDay ?: base.dayOfMonth else sub.anchorDay
                val next = DueDateCalculator.advance(base, sub.cycle, anchor) ?: return null
                RenewPlan(next, anchor)
            }
            RenewMode.FROM_TODAY -> {
                val anchor = if (isMonth) today.dayOfMonth else sub.anchorDay
                val next = DueDateCalculator.advance(today, sub.cycle, anchor) ?: return null
                RenewPlan(next, anchor)
            }
        }
    }
}
