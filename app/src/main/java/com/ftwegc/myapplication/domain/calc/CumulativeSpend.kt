package com.ftwegc.myapplication.domain.calc

import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.Subscription
import java.time.LocalDate

/** One payment on the way: on [date] the total paid so far became [totalMinor]. */
data class SpendStep(val date: LocalDate, val amountMinor: Long, val totalMinor: Long)

/**
 * How much one subscription has cost so far (detail page "累计花费"). Pure.
 *
 * Payments are reconstructed the way [SpendSchedule] does: a recurring subscription is paid on
 * each due date, for the cycle that starts there. Counted are the cycles from the one that was
 * running when the subscription was added to the app (paid before it was added) up to the one
 * running now; a cycle renewed in advance ("标记已续费") counts as paid, the next due date does not
 * until it is renewed. A one-time purchase, a lifetime plan with a price and a subscription
 * without a due date count one payment on the day they were added. Free subscriptions cost nothing.
 */
object CumulativeSpend {

    fun steps(sub: Subscription, addedOn: LocalDate, today: LocalDate): List<SpendStep> {
        if (sub.isFree || sub.priceMinor <= 0L) return emptyList()
        val price = sub.priceMinor
        val next = sub.nextDue
        val single = sub.cycle.unit == CycleUnit.ONCE || sub.isPermanent || next == null
        val paidUpTo = next?.let { SpendSchedule.previousDue(sub, it) }
        val dates = if (single || paidUpTo == null) {
            listOf(minOf(addedOn, today))
        } else {
            // The cycle running when it was added: its start is the latest due date on or before that day.
            var first: LocalDate = paidUpTo
            var guard = 0
            while (first.isAfter(addedOn) && guard++ < MAX_CYCLES) {
                first = SpendSchedule.previousDue(sub, first) ?: break
            }
            SpendSchedule.dueDatesIn(sub, first, paidUpTo)
        }
        var total = 0L
        return dates.map { date ->
            total += price
            SpendStep(date, price, total)
        }
    }

    /** Longest history walked back (a daily cycle over five years). */
    private const val MAX_CYCLES = 2_000

    /** Everything paid so far (the last step's total, 0 without payments). */
    fun total(steps: List<SpendStep>): Long = steps.lastOrNull()?.totalMinor ?: 0L
}
