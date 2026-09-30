package com.ftwegc.myapplication.domain.calc

import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.Subscription
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** One payment: [subscriptionId] is due on [date] for [amountMinor] in currency [currency] (a [SpendCalculator.currencyKey]). */
data class Payment(val subscriptionId: Long, val date: LocalDate, val amountMinor: Long, val currency: String)

/** A category's share of the monthly spend in one currency. */
data class CategoryShare(val category: Category, val monthlyMinor: Long, val fraction: Double)

/**
 * The nearest due date: [daysLeft] until it (negative when overdue) and [elapsedFraction], the
 * share of the current billing cycle that has passed (for the due ring; 1 when overdue).
 */
data class NextDue(val subscription: Subscription, val daysLeft: Long, val elapsedFraction: Float)

/**
 * Payments by date: what is payable when, reconstructed from each subscription's next due date
 * (earlier cycles backward, later cycles forward). Everything the overview's spending charts show
 * comes from here. Pure; all money in minor units, grouped by currency, no exchange rates.
 *
 * Counted: recurring subscriptions that are not free, not archived, not permanent, have a price
 * and a next due date ([SpendCalculator.isRecurringSpend]). Past cycles are assumed to have been
 * paid on their due dates.
 */
object SpendSchedule {

    /** Bound on generated due dates per subscription and range (a daily cycle over a year). */
    private const val MAX_STEPS = 800

    private fun counts(sub: Subscription): Boolean =
        SpendCalculator.isRecurringSpend(sub) && !sub.isPermanent && sub.nextDue != null

    /** The due date one cycle before [due] for [sub] (`null` for one-time purchases). */
    fun previousDue(sub: Subscription, due: LocalDate? = sub.nextDue): LocalDate? =
        due?.let { retreat(it, sub, 1) }

    /** [steps] cycles before [due]; the month anchor is kept like [DueDateCalculator.advance] does. */
    private fun retreat(due: LocalDate, sub: Subscription, steps: Long): LocalDate? = when (sub.cycle.unit) {
        CycleUnit.MONTH -> {
            val anchor = (sub.anchorDay ?: due.dayOfMonth).coerceIn(1, 31)
            val month = YearMonth.from(due).minusMonths(steps * sub.cycle.count)
            month.atDay(minOf(anchor, month.lengthOfMonth()))
        }
        CycleUnit.DAY -> due.minusDays(steps * sub.cycle.count)
        CycleUnit.ONCE -> null
    }

    /** Due dates of [sub] within [from]..[to] (inclusive), in order. */
    fun dueDatesIn(sub: Subscription, from: LocalDate, to: LocalDate): List<LocalDate> {
        val next = sub.nextDue ?: return emptyList()
        if (to.isBefore(from)) return emptyList()
        val dates = mutableListOf<LocalDate>()
        // Backward from the next due date.
        var k = 1L
        while (k <= MAX_STEPS) {
            val d = retreat(next, sub, k) ?: break
            if (d.isBefore(from)) break
            if (!d.isAfter(to)) dates += d
            k++
        }
        dates.reverse()
        // Forward from the next due date.
        var d: LocalDate? = next
        var guard = 0
        while (d != null && !d.isAfter(to) && guard++ < MAX_STEPS) {
            if (!d.isBefore(from)) dates += d
            d = DueDateCalculator.advance(d, sub.cycle, sub.anchorDay ?: next.dayOfMonth.takeIf { sub.cycle.unit == CycleUnit.MONTH })
        }
        return dates
    }

    /** Every payment within [from]..[to], ordered by date. */
    fun paymentsIn(subs: Iterable<Subscription>, from: LocalDate, to: LocalDate): List<Payment> =
        subs.filter(::counts)
            .flatMap { sub ->
                dueDatesIn(sub, from, to).map { Payment(sub.id, it, sub.priceMinor, SpendCalculator.currencyKey(sub.currency)) }
            }
            .sortedWith(compareBy({ it.date }, { it.subscriptionId }))

    /** Payable in [month] per currency. */
    fun monthTotals(subs: Iterable<Subscription>, month: YearMonth): Map<String, Long> =
        totals(paymentsIn(subs, month.atDay(1), month.atEndOfMonth()))

    /** Payable in [year] per currency. */
    fun yearTotals(subs: Iterable<Subscription>, year: Int): Map<String, Long> =
        totals(paymentsIn(subs, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)))

    /** Payable in each month of [year] (12 entries) in [currency]. */
    fun yearByMonth(subs: Iterable<Subscription>, year: Int, currency: String): List<Long> {
        val key = SpendCalculator.currencyKey(currency)
        val result = LongArray(12)
        paymentsIn(subs, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
            .filter { it.currency == key }
            .forEach { result[it.date.monthValue - 1] += it.amountMinor }
        return result.toList()
    }

    /**
     * The spending pace of [month] in [currency]: for each day, the total payable from the 1st up
     * to and including that day (one entry per day of the month).
     */
    fun dailyCumulative(subs: Iterable<Subscription>, month: YearMonth, currency: String): List<Long> {
        val key = SpendCalculator.currencyKey(currency)
        val perDay = LongArray(month.lengthOfMonth())
        paymentsIn(subs, month.atDay(1), month.atEndOfMonth())
            .filter { it.currency == key }
            .forEach { perDay[it.date.dayOfMonth - 1] += it.amountMinor }
        var acc = 0L
        return perDay.map { acc += it; acc }
    }

    /** Payable per day of [month] in [currency] (only days with a payment). */
    fun dailyAmounts(subs: Iterable<Subscription>, month: YearMonth, currency: String): Map<Int, Long> {
        val key = SpendCalculator.currencyKey(currency)
        return paymentsIn(subs, month.atDay(1), month.atEndOfMonth())
            .filter { it.currency == key }
            .groupBy { it.date.dayOfMonth }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }
    }

    /**
     * The currency the overview leads with: [preferred] (the default currency) when any counted
     * subscription uses it, otherwise the currency with the largest monthly spend, otherwise
     * [preferred].
     */
    fun primaryCurrency(subs: Iterable<Subscription>, preferred: String): String {
        val key = SpendCalculator.currencyKey(preferred)
        val counted = subs.filter(::counts)
        if (counted.any { SpendCalculator.currencyKey(it.currency) == key }) return key
        val monthly = SpendCalculator.monthlyByCurrency(counted)
        return monthly.maxByOrNull { it.value }?.key ?: key
    }

    /** Monthly (amortized) spend per category in [currency], largest first; empty without spend. */
    fun categoryShares(subs: Iterable<Subscription>, currency: String): List<CategoryShare> {
        val key = SpendCalculator.currencyKey(currency)
        val byCategory = subs
            .filter { SpendCalculator.currencyKey(it.currency) == key }
            .groupBy { it.category }
            .mapValues { (_, list) -> SpendCalculator.monthlyByCurrency(list)[key] ?: 0L }
            .filterValues { it > 0L }
        val total = byCategory.values.sum()
        if (total <= 0L) return emptyList()
        return byCategory.entries
            .sortedByDescending { it.value }
            .map { CategoryShare(it.key, it.value, it.value.toDouble() / total) }
    }

    /**
     * The subscription due soonest (overdue first): not archived, not permanent, with a due date.
     */
    fun nextDue(subs: Iterable<Subscription>, today: LocalDate): NextDue? {
        val sub = subs
            .filter { !it.archived && !it.isPermanent && it.nextDue != null }
            .minWithOrNull(compareBy({ it.nextDue }, { it.name })) ?: return null
        val due = sub.nextDue!!
        val days = DueDateCalculator.daysUntil(due, today)
        return NextDue(sub, days, elapsedFraction(sub, today))
    }

    /** Share of the current cycle of [sub] that has passed on [today], 0..1 (1 when overdue). */
    fun elapsedFraction(sub: Subscription, today: LocalDate): Float {
        val due = sub.nextDue ?: return 0f
        if (!today.isBefore(due)) return 1f
        val previous = previousDue(sub, due) ?: return 0f
        val total = ChronoUnit.DAYS.between(previous, due).toFloat()
        if (total <= 0f) return 1f
        return (ChronoUnit.DAYS.between(previous, today) / total).coerceIn(0f, 1f)
    }

    /**
     * Due dates in the next [days] days (today included) for the timeline: the first due date of
     * every active, dated, non-permanent subscription in that window; overdue ones sit at today.
     */
    fun upcoming(subs: Iterable<Subscription>, today: LocalDate, days: Int): List<Pair<Subscription, LocalDate>> {
        val end = today.plusDays(days.toLong())
        return subs
            .filter { !it.archived && !it.isPermanent && it.nextDue != null && !it.nextDue.isAfter(end) }
            .map { it to it.nextDue!! }
            .sortedWith(compareBy({ it.second }, { it.first.name }))
    }

    private fun totals(payments: List<Payment>): Map<String, Long> =
        payments.groupBy { it.currency }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }.toSortedMap()
}
