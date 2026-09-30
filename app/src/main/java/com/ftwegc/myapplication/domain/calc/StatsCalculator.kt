package com.ftwegc.myapplication.domain.calc

import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** How the statistics page counts the spend of a month. */
enum class SpendBasis {
    /** Cash flow: a payment counts in full in the month it is charged. */
    CASH_FLOW,

    /**
     * Amortized: a payment is spread over what it pays for. A payment of an n-month cycle counts
     * one n-th in each of its n months; a payment of a day cycle counts in each month by the share
     * of its days that fall there. One-time purchases and lifetime plans are not spread.
     */
    AMORTIZED,
}

/** The period the key figures of the statistics page cover. */
enum class StatsPeriod { MONTH, YEAR }

/**
 * One subscription's part of a month.
 *
 * @property date cash flow: the payment date; amortized: the date of the payment the share comes from.
 * @property amountMinor what counts in the month (cash flow: the whole payment).
 * @property paymentMinor the whole payment.
 * @property oneTime a one-time purchase or a paid lifetime plan, counted on the day it was added.
 */
data class MonthEntry(
    val subscriptionId: Long,
    val date: LocalDate,
    val amountMinor: Long,
    val paymentMinor: Long,
    val oneTime: Boolean = false,
)

/** What a month costs in one currency, and the parts it is made of (largest first). */
data class MonthSpend(val month: YearMonth, val totalMinor: Long, val entries: List<MonthEntry>) {
    /** Share of [entry] in this month, 0..1. */
    fun share(entry: MonthEntry): Double = if (totalMinor > 0) entry.amountMinor.toDouble() / totalMinor else 0.0
}

/**
 * The key figures of the statistics page in one currency.
 *
 * @property periodMinor spent this month / this year (cash flow, like the overview).
 * @property previousMinor the month / year before.
 * @property dailyAverageMinor the year's amortized spend divided by the days of this year.
 * @property topSubscriptionId the subscription with the highest monthly equivalent, `null` without one.
 * @property lifetimeMinor everything paid since each subscription was added (archived ones included).
 * @property activeCount subscriptions that are not archived.
 * @property countedCount active subscriptions that cost money (recurring with a due date, or bought once).
 * @property needsInfoCount active subscriptions still without a price and a due date.
 */
data class StatsMetrics(
    val periodMinor: Long,
    val previousMinor: Long,
    val dailyAverageMinor: Long,
    val topSubscriptionId: Long?,
    val topMonthlyMinor: Long,
    val lifetimeMinor: Long,
    val activeCount: Int,
    val countedCount: Int,
    val needsInfoCount: Int,
) {
    val deltaMinor: Long get() = periodMinor - previousMinor
}

/** One row of the ranking: [monthlyMinor] and its share of the largest one ([fraction], 0..1). */
data class RankRow(val subscriptionId: Long, val monthlyMinor: Long, val fraction: Float)

/** One VPS of the traffic comparison; [percent] is `null` without a limit. */
data class TrafficRow(val subscriptionId: Long, val usedBytes: Long, val limitBytes: Long, val percent: Double?)

/** VPSs with a limit (highest share first) and without one (most used first). */
data class TrafficComparison(val limited: List<TrafficRow>, val unlimited: List<TrafficRow>) {
    val isEmpty: Boolean get() = limited.isEmpty() && unlimited.isEmpty()
}

/**
 * Everything the statistics page computes. Pure; money in minor units, one currency at a time
 * (no exchange rates), dates as local dates, "today" passed in.
 *
 * Counted like the overview ([SpendSchedule]): recurring subscriptions that are not free, not
 * archived, not lifetime, have a price and a due date; earlier cycles are assumed paid on their
 * due dates. In addition a one-time purchase (or a paid lifetime plan) counts once, on the day it
 * was added to the app ([addedOn]; a one-time purchase without that date uses its due date).
 * Subscriptions still waiting for a price and a due date ("待补充") cost nothing yet.
 */
object StatsCalculator {

    /** Scale for exact intermediate amounts (far beyond minor units). */
    private const val SCALE = 10

    /** A recurring subscription that has payments on the calendar. */
    fun countsRecurring(sub: Subscription): Boolean =
        SpendCalculator.isRecurringSpend(sub) && !sub.isPermanent && sub.nextDue != null

    /** The day a one-time purchase or paid lifetime plan was paid, `null` for anything else. */
    fun oneTimeDate(sub: Subscription, addedOn: LocalDate?): LocalDate? {
        if (sub.archived || sub.isFree || sub.priceMinor <= 0L) return null
        val once = sub.cycle.unit == CycleUnit.ONCE
        if (!once && !sub.isPermanent) return null
        return addedOn ?: sub.nextDue?.takeIf { once }
    }

    private fun key(currency: String) = SpendCalculator.currencyKey(currency)

    /**
     * Currencies with anything to show, the one with the largest monthly spend first (then those
     * with only one-time payments, alphabetically).
     */
    fun currencies(subs: List<Subscription>, addedOn: Map<Long, LocalDate>): List<String> {
        val monthly = SpendCalculator.monthlyByCurrency(subs.filter(::countsRecurring))
        val once = subs.filter { oneTimeDate(it, addedOn[it.id]) != null }.map { key(it.currency) }
        val recurring = subs.filter(::countsRecurring).map { key(it.currency) }
        return (recurring + once).distinct()
            .sortedWith(compareByDescending<String> { monthly[it] ?: -1L }.thenBy { it })
    }

    /** The currency the page opens with: [preferred] when it has anything, else the first of [currencies]. */
    fun primaryCurrency(subs: List<Subscription>, addedOn: Map<Long, LocalDate>, preferred: String): String {
        val list = currencies(subs, addedOn)
        val wanted = key(preferred)
        return if (wanted in list || list.isEmpty()) wanted else list.first()
    }

    // ------------------------------------------------------------------ Months

    /** What [month] costs in [currency] on [basis]. */
    fun month(
        subs: List<Subscription>,
        addedOn: Map<Long, LocalDate>,
        month: YearMonth,
        currency: String,
        basis: SpendBasis,
    ): MonthSpend = when (basis) {
        SpendBasis.CASH_FLOW -> cashFlow(subs, addedOn, month, currency)
        SpendBasis.AMORTIZED -> amortized(subs, month, currency)
    }

    /** [before] months before [center], [center] itself and [after] months after it. */
    fun months(
        subs: List<Subscription>,
        addedOn: Map<Long, LocalDate>,
        center: YearMonth,
        before: Int,
        after: Int,
        currency: String,
        basis: SpendBasis,
    ): List<MonthSpend> = (-before..after).map { month(subs, addedOn, center.plusMonths(it.toLong()), currency, basis) }

    /** The twelve months of [year]. */
    fun year(subs: List<Subscription>, addedOn: Map<Long, LocalDate>, year: Int, currency: String, basis: SpendBasis): List<MonthSpend> =
        (1..12).map { month(subs, addedOn, YearMonth.of(year, it), currency, basis) }

    private fun cashFlow(subs: List<Subscription>, addedOn: Map<Long, LocalDate>, month: YearMonth, currency: String): MonthSpend {
        val wanted = key(currency)
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        val recurring = SpendSchedule.paymentsIn(subs, first, last)
            .filter { it.currency == wanted }
            .map { MonthEntry(it.subscriptionId, it.date, it.amountMinor, it.amountMinor) }
        val once = subs.mapNotNull { sub ->
            if (key(sub.currency) != wanted) return@mapNotNull null
            val date = oneTimeDate(sub, addedOn[sub.id]) ?: return@mapNotNull null
            if (YearMonth.from(date) != month) return@mapNotNull null
            MonthEntry(sub.id, date, sub.priceMinor, sub.priceMinor, oneTime = true)
        }
        val entries = (recurring + once).sortedWith(compareBy({ it.date }, { it.subscriptionId }))
        return MonthSpend(month, entries.sumOf { it.amountMinor }, entries)
    }

    private fun amortized(subs: List<Subscription>, month: YearMonth, currency: String): MonthSpend {
        val wanted = key(currency)
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        var exactTotal = BigDecimal.ZERO
        val entries = mutableListOf<MonthEntry>()
        for (sub in subs) {
            if (!countsRecurring(sub) || key(sub.currency) != wanted) continue
            val price = BigDecimal.valueOf(sub.priceMinor)
            val count = sub.cycle.count.toLong()
            when (sub.cycle.unit) {
                CycleUnit.MONTH -> {
                    // A payment in month m covers months m .. m + count - 1.
                    val from = month.minusMonths(count - 1).atDay(1)
                    SpendSchedule.dueDatesIn(sub, from, last).forEach { date ->
                        val exact = price.divide(BigDecimal.valueOf(count), SCALE, RoundingMode.HALF_UP)
                        exactTotal = exactTotal.add(exact)
                        entries += MonthEntry(sub.id, date, SpendCalculator.roundMinor(exact), sub.priceMinor)
                    }
                }
                CycleUnit.DAY -> {
                    // A payment on day d covers d .. d + count - 1.
                    SpendSchedule.dueDatesIn(sub, first.minusDays(count - 1), last).forEach { date ->
                        val end = date.plusDays(count - 1)
                        val overlapStart = maxOf(date, first)
                        val overlapEnd = minOf(end, last)
                        val days = ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1
                        if (days <= 0) return@forEach
                        val exact = price.multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(count), SCALE, RoundingMode.HALF_UP)
                        exactTotal = exactTotal.add(exact)
                        entries += MonthEntry(sub.id, date, SpendCalculator.roundMinor(exact), sub.priceMinor)
                    }
                }
                CycleUnit.ONCE -> Unit
            }
        }
        // One subscription can have two partial shares (a day cycle): list it once.
        val merged = entries.groupBy { it.subscriptionId }.map { (id, list) ->
            MonthEntry(id, list.first().date, list.sumOf { it.amountMinor }, list.first().paymentMinor)
        }.sortedWith(compareByDescending<MonthEntry> { it.amountMinor }.thenBy { it.subscriptionId })
        return MonthSpend(month, SpendCalculator.roundMinor(exactTotal), merged)
    }

    /** Payments per day of [month] (cash flow, [currency]), for the calendar. */
    fun days(subs: List<Subscription>, addedOn: Map<Long, LocalDate>, month: YearMonth, currency: String): Map<Int, List<MonthEntry>> =
        cashFlow(subs, addedOn, month, currency).entries.groupBy { it.date.dayOfMonth }

    // ------------------------------------------------------------------ Key figures

    fun metrics(
        subs: List<Subscription>,
        addedOn: Map<Long, LocalDate>,
        today: LocalDate,
        currency: String,
        period: StatsPeriod,
    ): StatsMetrics {
        val wanted = key(currency)
        val thisMonth = YearMonth.from(today)
        val (current, previous) = when (period) {
            StatsPeriod.MONTH ->
                month(subs, addedOn, thisMonth, currency, SpendBasis.CASH_FLOW).totalMinor to
                    month(subs, addedOn, thisMonth.minusMonths(1), currency, SpendBasis.CASH_FLOW).totalMinor
            StatsPeriod.YEAR ->
                year(subs, addedOn, today.year, currency, SpendBasis.CASH_FLOW).sumOf { it.totalMinor } to
                    year(subs, addedOn, today.year - 1, currency, SpendBasis.CASH_FLOW).sumOf { it.totalMinor }
        }
        val active = subs.filter { !it.archived }
        val annual = SpendCalculator.annualByCurrency(active.filter(::countsRecurring))[wanted] ?: 0L
        val daily = BigDecimal.valueOf(annual).divide(BigDecimal.valueOf(today.lengthOfYear().toLong()), SCALE, RoundingMode.HALF_UP)
        val top = ranking(subs, currency).firstOrNull()
        val lifetime = subs.filter { key(it.currency) == wanted }.sumOf { sub ->
            CumulativeSpend.total(CumulativeSpend.steps(sub, addedOn[sub.id] ?: today, today))
        }
        return StatsMetrics(
            periodMinor = current,
            previousMinor = previous,
            dailyAverageMinor = SpendCalculator.roundMinor(daily),
            topSubscriptionId = top?.subscriptionId,
            topMonthlyMinor = top?.monthlyMinor ?: 0L,
            lifetimeMinor = lifetime,
            activeCount = active.size,
            countedCount = active.count { countsRecurring(it) || oneTimeDate(it, addedOn[it.id]) != null },
            needsInfoCount = active.count(::needsInfo),
        )
    }

    /** No price, due date or lifetime flag yet (an imported node): costs nothing until completed. */
    fun needsInfo(sub: Subscription): Boolean =
        sub.priceMinor == 0L && !sub.isFree && sub.nextDue == null && !sub.isPermanent && sub.cycle.unit != CycleUnit.ONCE

    /** Recurring subscriptions in [currency] by monthly equivalent, largest first. */
    fun ranking(subs: List<Subscription>, currency: String): List<RankRow> {
        val wanted = key(currency)
        val rows = subs
            .filter { countsRecurring(it) && key(it.currency) == wanted }
            .mapNotNull { sub -> SpendCalculator.monthlyMinor(sub)?.takeIf { it > 0 }?.let { sub to it } }
            .sortedWith(compareByDescending<Pair<Subscription, Long>> { it.second }.thenBy { it.first.name })
        val max = rows.firstOrNull()?.second ?: return emptyList()
        return rows.map { (sub, monthly) -> RankRow(sub.id, monthly, (monthly.toDouble() / max).toFloat()) }
    }

    /** Monthly spend per category in [currency] (active subscriptions), largest first. */
    fun categories(subs: List<Subscription>, currency: String): List<CategoryShare> =
        SpendSchedule.categoryShares(subs.filter { countsRecurring(it) }, currency)

    /**
     * Traffic of every active VPS with data: limited ones by used share (highest first), unlimited
     * ones by used bytes. [today] is the local date in the reset zone.
     */
    fun traffic(items: List<SubscriptionWithVps>, today: LocalDate): TrafficComparison {
        val rows = items
            .filter { it.subscription.category == Category.VPS && !it.subscription.archived && it.vps != null }
            .mapNotNull { item ->
                val usage = TrafficResolver.resolve(item.vps!!, item.appPeriod, today).usage ?: return@mapNotNull null
                TrafficRow(item.subscription.id, usage.usedBytes, usage.limitBytes, usage.percent)
            }
        return TrafficComparison(
            limited = rows.filter { it.percent != null }.sortedWith(compareByDescending<TrafficRow> { it.percent }.thenByDescending { it.usedBytes }),
            unlimited = rows.filter { it.percent == null }.sortedByDescending { it.usedBytes },
        )
    }

    /**
     * The years of the year view: from the year the first subscription was added ([firstAdded];
     * at most four years back) to the year after [today]'s. Years before anything was tracked are
     * left out (they would only repeat today's plans).
     */
    fun yearRange(today: LocalDate, firstAdded: LocalDate?): IntRange {
        val start = (firstAdded?.year ?: today.year).coerceIn(today.year - MAX_YEARS_BACK, today.year)
        return start..(today.year + 1)
    }

    private const val MAX_YEARS_BACK = 4
}
