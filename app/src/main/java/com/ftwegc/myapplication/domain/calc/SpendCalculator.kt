package com.ftwegc.myapplication.domain.calc

import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.Subscription
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.util.Locale

/**
 * Spend summaries. All money values are in minor units ("fen"/cents, price × 100).
 * Amounts are grouped by currency; no exchange-rate conversion is done.
 *
 * Amortization uses exact [BigDecimal] math; each currency total is rounded once (HALF_UP)
 * to whole minor units, so a total can differ by a unit from the sum of rounded per-item values.
 */
object SpendCalculator {

    /** Average days per month (365.25 / 12), used to amortize DAY cycles. */
    val DAYS_PER_MONTH: BigDecimal = BigDecimal("30.4375")

    /** Average days per year, used to annualize DAY cycles. */
    val DAYS_PER_YEAR: BigDecimal = BigDecimal("365.25")

    private val TWELVE = BigDecimal(12)

    /**
     * Whether [sub] counts toward recurring spend: not free, not archived, positive price,
     * and not a ONCE cycle.
     */
    fun isRecurringSpend(sub: Subscription): Boolean =
        !sub.isFree && !sub.archived && sub.priceMinor > 0 && sub.cycle.unit != CycleUnit.ONCE

    /**
     * Exact monthly amortized cost of [sub] in minor units, or `null` when it does not count
     * (see [isRecurringSpend]). MONTH: `price / count`; DAY: `price × 30.4375 / count`.
     */
    fun monthlyExact(sub: Subscription): BigDecimal? {
        if (!isRecurringSpend(sub)) return null
        val price = BigDecimal.valueOf(sub.priceMinor)
        val count = BigDecimal(sub.cycle.count)
        return when (sub.cycle.unit) {
            CycleUnit.MONTH -> price.divide(count, SCALE, RoundingMode.HALF_UP)
            CycleUnit.DAY -> price.multiply(DAYS_PER_MONTH).divide(count, SCALE, RoundingMode.HALF_UP)
            CycleUnit.ONCE -> null
        }
    }

    /** [monthlyExact] rounded HALF_UP to whole minor units, or `null` when not counted. */
    fun monthlyMinor(sub: Subscription): Long? = monthlyExact(sub)?.let(::roundMinor)

    /**
     * Exact annual cost of [sub] in minor units, or `null` when not counted.
     * MONTH: `price × 12 / count`; DAY: `price × 365.25 / count`.
     */
    fun annualExact(sub: Subscription): BigDecimal? {
        if (!isRecurringSpend(sub)) return null
        val price = BigDecimal.valueOf(sub.priceMinor)
        val count = BigDecimal(sub.cycle.count)
        return when (sub.cycle.unit) {
            CycleUnit.MONTH -> price.multiply(TWELVE).divide(count, SCALE, RoundingMode.HALF_UP)
            CycleUnit.DAY -> price.multiply(DAYS_PER_YEAR).divide(count, SCALE, RoundingMode.HALF_UP)
            CycleUnit.ONCE -> null
        }
    }

    /** Monthly amortized spend per currency key (see [currencyKey]), in minor units, sorted by key. */
    fun monthlyByCurrency(subs: Iterable<Subscription>): Map<String, Long> =
        sumByCurrency(subs, ::monthlyExact)

    /** Annual spend per currency key, in minor units, sorted by key. */
    fun annualByCurrency(subs: Iterable<Subscription>): Map<String, Long> =
        sumByCurrency(subs, ::annualExact)

    /**
     * Amount payable in [month] per currency key, in minor units, sorted by key.
     *
     * A subscription contributes when it is not free, not archived, not permanent, not ONCE,
     * and its `nextDue` falls in [month]. It contributes its price once for every due date from
     * `nextDue` onward that stays inside [month] (DAY cycles shorter than a month can be due
     * several times; MONTH cycles are due exactly once).
     */
    fun dueInMonthByCurrency(subs: Iterable<Subscription>, month: YearMonth): Map<String, Long> {
        val totals = sortedMapOf<String, Long>()
        for (sub in subs) {
            if (!isRecurringSpend(sub) || sub.isPermanent) continue
            var due = sub.nextDue ?: continue
            if (YearMonth.from(due) != month) continue
            var occurrences = 0L
            while (YearMonth.from(due) == month) {
                occurrences++
                due = DueDateCalculator.advance(due, sub.cycle, sub.anchorDay) ?: break
            }
            val key = currencyKey(sub.currency)
            totals[key] = Math.addExact(totals[key] ?: 0L, Math.multiplyExact(sub.priceMinor, occurrences))
        }
        return totals
    }

    /** Grouping key for a currency label: trimmed and upper-cased ("usd " and "USD" group together). */
    fun currencyKey(currency: String): String = currency.trim().uppercase(Locale.ROOT)

    /** Rounds an exact minor-unit amount HALF_UP to a whole number of minor units. */
    fun roundMinor(value: BigDecimal): Long = value.setScale(0, RoundingMode.HALF_UP).longValueExact()

    /** Scale for intermediate division; far beyond minor-unit precision. */
    private const val SCALE = 10

    private fun sumByCurrency(
        subs: Iterable<Subscription>,
        amount: (Subscription) -> BigDecimal?,
    ): Map<String, Long> {
        val exact = sortedMapOf<String, BigDecimal>()
        for (sub in subs) {
            val value = amount(sub) ?: continue
            val key = currencyKey(sub.currency)
            exact[key] = (exact[key] ?: BigDecimal.ZERO).add(value)
        }
        return exact.mapValuesTo(sortedMapOf()) { (_, v) -> roundMinor(v) }
    }
}
