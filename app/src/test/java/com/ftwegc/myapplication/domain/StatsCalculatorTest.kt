package com.ftwegc.myapplication.domain

import com.ftwegc.myapplication.domain.calc.SpendBasis
import com.ftwegc.myapplication.domain.calc.StatsCalculator
import com.ftwegc.myapplication.domain.calc.StatsPeriod
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.VpsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class StatsCalculatorTest {

    private val today = LocalDate.of(2026, 9, 28)
    private val sep = YearMonth.of(2026, 9)
    private val noDates = emptyMap<Long, LocalDate>()

    private fun sub(
        id: Long,
        price: Long,
        due: LocalDate?,
        cycle: BillingCycle = BillingCycle.MONTHLY,
        currency: String = "CNY",
        anchor: Int? = null,
        category: Category = Category.AI,
        free: Boolean = false,
        archived: Boolean = false,
        permanent: Boolean = false,
    ) = Subscription(
        id = id, name = "s$id", category = category, priceMinor = price, isFree = free, currency = currency, cycle = cycle,
        nextDue = due, anchorDay = anchor, archived = archived, isPermanent = permanent,
    )

    private fun cash(subs: List<Subscription>, month: YearMonth, currency: String = "CNY", added: Map<Long, LocalDate> = noDates) =
        StatsCalculator.month(subs, added, month, currency, SpendBasis.CASH_FLOW)

    private fun amortized(subs: List<Subscription>, month: YearMonth, currency: String = "CNY") =
        StatsCalculator.month(subs, noDates, month, currency, SpendBasis.AMORTIZED)

    // ------------------------------------------------------------ the two bases

    @Test
    fun cashFlowCountsTheMonthOfTheCharge_amortizedSpreadsIt() {
        // Annual ¥1200, charged Nov 25 every year; quarterly ¥300 charged Sep 30, Dec 30, …
        val annual = sub(1, 120_000, LocalDate.of(2026, 11, 25), cycle = BillingCycle.ANNUAL)
        val quarterly = sub(2, 30_000, LocalDate.of(2026, 9, 30), cycle = BillingCycle.QUARTERLY)
        val subs = listOf(annual, quarterly)

        assertEquals(30_000L, cash(subs, sep).totalMinor)
        assertEquals(0L, cash(subs, YearMonth.of(2026, 10)).totalMinor)
        assertEquals(120_000L, cash(subs, YearMonth.of(2026, 11)).totalMinor)

        // Every month carries a twelfth of the annual plan and a third of the quarterly one.
        for (m in 7..12) assertEquals("month $m", 10_000L + 10_000L, amortized(subs, YearMonth.of(2026, m)).totalMinor)
        val oct = amortized(subs, YearMonth.of(2026, 10))
        // The October share of the annual plan comes from the payment of 2025-11-25.
        assertEquals(LocalDate.of(2025, 11, 25), oct.entries.single { it.subscriptionId == 1L }.date)
        assertEquals(120_000L, oct.entries.single { it.subscriptionId == 1L }.paymentMinor)
        assertEquals(0.5, oct.share(oct.entries.first()), 1e-9)
    }

    @Test
    fun overTwelveMonthsBothBasesAddUpToTheSame() {
        val subs = listOf(
            sub(1, 120_000, LocalDate.of(2026, 11, 25), cycle = BillingCycle.ANNUAL),
            sub(2, 30_000, LocalDate.of(2026, 9, 30), cycle = BillingCycle.QUARTERLY),
            sub(3, 2_000, LocalDate.of(2026, 10, 3)),
        )
        val c = StatsCalculator.year(subs, noDates, 2026, "CNY", SpendBasis.CASH_FLOW).sumOf { it.totalMinor }
        val a = StatsCalculator.year(subs, noDates, 2026, "CNY", SpendBasis.AMORTIZED).sumOf { it.totalMinor }
        assertEquals(120_000L + 4 * 30_000L + 12 * 2_000L, c)
        assertEquals(c, a)
    }

    @Test
    fun monthsAroundTheCurrentOne_crossTheYear() {
        // Quarterly ¥300 charged Dec 15 2026, then Mar 15 2027.
        val q = sub(1, 30_000, LocalDate.of(2026, 12, 15), cycle = BillingCycle.QUARTERLY)
        val series = StatsCalculator.months(listOf(q), noDates, YearMonth.of(2026, 12), 6, 6, "CNY", SpendBasis.CASH_FLOW)
        assertEquals(13, series.size)
        assertEquals(YearMonth.of(2026, 6), series.first().month)
        assertEquals(YearMonth.of(2027, 6), series.last().month)
        assertEquals(listOf(6, 9, 12, 3, 6), series.filter { it.totalMinor > 0 }.map { it.month.monthValue })
        // January 2027 carries a third of the December payment.
        val jan = amortized(listOf(q), YearMonth.of(2027, 1))
        assertEquals(10_000L, jan.totalMinor)
        assertEquals(LocalDate.of(2026, 12, 15), jan.entries.single().date)
    }

    @Test
    fun leapYear_dayCyclesAndFebruary29() {
        // Weekly ¥70 due Mar 1 2028: February 2028 (29 days) has payments on the 2nd, 9th, 16th, 23rd.
        val weekly = sub(1, 7_000, LocalDate.of(2028, 3, 1), cycle = BillingCycle.days(7))
        val feb = YearMonth.of(2028, 2)
        assertEquals(4 * 7_000L, cash(listOf(weekly), feb).totalMinor)
        // Amortized: ¥10 a day for 29 days.
        assertEquals(29 * 1_000L, amortized(listOf(weekly), feb).totalMinor)
        // An annual plan charged on Feb 29 moves to Feb 28 in 2029 and back to the 29th in 2032.
        val annual = sub(2, 36_600, LocalDate.of(2028, 2, 29), cycle = BillingCycle.ANNUAL, anchor = 29)
        assertEquals(LocalDate.of(2029, 2, 28), cash(listOf(annual), YearMonth.of(2029, 2)).entries.single().date)
        assertEquals(LocalDate.of(2032, 2, 29), cash(listOf(annual), YearMonth.of(2032, 2)).entries.single().date)
        // The daily average divides by 366 days in a leap year.
        val metrics = StatsCalculator.metrics(listOf(annual), noDates, LocalDate.of(2028, 6, 1), "CNY", StatsPeriod.MONTH)
        assertEquals(100L, metrics.dailyAverageMinor)
    }

    @Test
    fun monthEndDueDates_stayOnTheLastDay() {
        val s = sub(1, 3_100, LocalDate.of(2026, 1, 31), anchor = 31)
        assertEquals(LocalDate.of(2026, 2, 28), cash(listOf(s), YearMonth.of(2026, 2)).entries.single().date)
        assertEquals(LocalDate.of(2026, 4, 30), cash(listOf(s), YearMonth.of(2026, 4)).entries.single().date)
        // Monthly plans are exactly their price in both bases, even in February.
        assertEquals(3_100L, amortized(listOf(s), YearMonth.of(2026, 2)).totalMinor)
        assertEquals(3_100L, cash(listOf(s), YearMonth.of(2026, 2)).totalMinor)
    }

    @Test
    fun customDayCycle_isChargedSeveralTimesInAMonth() {
        // Every 10 days, ¥100, next on Sep 5: charged Sep 5, 15 and 25.
        val s = sub(1, 10_000, LocalDate.of(2026, 9, 5), cycle = BillingCycle.days(10))
        val c = cash(listOf(s), sep)
        assertEquals(listOf(5, 15, 25), c.entries.map { it.date.dayOfMonth })
        assertEquals(30_000L, c.totalMinor)
        // Amortized: ¥10 a day. Sep 1–4 come from the Aug 26 payment, Sep 25–30 are 6 of the last 10 days.
        val a = amortized(listOf(s), sep)
        assertEquals(30_000L, a.totalMinor)
        assertEquals(1, a.entries.size)
        val days = StatsCalculator.days(listOf(s), noDates, sep, "CNY")
        assertEquals(setOf(5, 15, 25), days.keys)
    }

    @Test
    fun oneTimePurchasesAndLifetimePlans_countOnceInTheCashFlowOnly() {
        val added = mapOf(1L to LocalDate.of(2026, 9, 10), 2L to LocalDate.of(2026, 8, 2))
        val once = sub(1, 50_000, LocalDate.of(2027, 1, 1), cycle = BillingCycle.ONE_TIME)
        val lifetime = sub(2, 80_000, null, permanent = true)
        val subs = listOf(once, lifetime)
        val september = cash(subs, sep, added = added)
        assertEquals(50_000L, september.totalMinor)
        assertTrue(september.entries.single().oneTime)
        assertEquals(80_000L, cash(subs, YearMonth.of(2026, 8), added = added).totalMinor)
        assertEquals(0L, cash(subs, YearMonth.of(2027, 1), added = added).totalMinor)
        assertEquals(0L, StatsCalculator.month(subs, added, sep, "CNY", SpendBasis.AMORTIZED).totalMinor)
        // Without the day it was added, a one-time purchase uses its date; a lifetime plan has none.
        assertEquals(50_000L, cash(subs, YearMonth.of(2027, 1)).totalMinor)
        assertEquals(0L, cash(subs, YearMonth.of(2026, 8)).totalMinor)
        // It still counts as something that costs money, and in the currency list.
        val metrics = StatsCalculator.metrics(subs, added, today, "CNY", StatsPeriod.MONTH)
        assertEquals(2, metrics.countedCount)
        assertEquals(listOf("CNY"), StatsCalculator.currencies(subs, added))
    }

    @Test
    fun freeNeedsInfoArchivedAndLifetime_areLeftOutOfTheMonths() {
        val subs = listOf(
            sub(1, 2_000, LocalDate.of(2026, 9, 3)),
            sub(2, 5_000, LocalDate.of(2026, 9, 3), free = true),
            sub(3, 0, null, category = Category.VPS), // imported, nothing filled in
            sub(4, 9_900, LocalDate.of(2026, 9, 3), archived = true),
            sub(5, 9_900, LocalDate.of(2026, 9, 3), permanent = true),
        )
        assertEquals(listOf(1L), cash(subs, sep).entries.map { it.subscriptionId })
        assertEquals(listOf(1L), amortized(subs, sep).entries.map { it.subscriptionId })
        assertEquals(listOf(1L), StatsCalculator.ranking(subs, "CNY").map { it.subscriptionId })
        assertTrue(StatsCalculator.needsInfo(subs[2]))
        assertFalse(StatsCalculator.needsInfo(subs[1]))
        val metrics = StatsCalculator.metrics(subs, noDates, today, "CNY", StatsPeriod.MONTH)
        assertEquals(1, metrics.needsInfoCount)
        assertEquals(4, metrics.activeCount)
        assertEquals(1, metrics.countedCount)
    }

    @Test
    fun onlySubscriptionsWaitingForInformation_leaveNothingToShow() {
        val subs = listOf(sub(1, 0, null, category = Category.VPS), sub(2, 0, null, category = Category.VPS))
        assertTrue(StatsCalculator.currencies(subs, noDates).isEmpty())
        assertEquals("CNY", StatsCalculator.primaryCurrency(subs, noDates, "cny"))
        assertEquals(0L, cash(subs, sep).totalMinor)
        assertTrue(StatsCalculator.ranking(subs, "CNY").isEmpty())
        assertTrue(StatsCalculator.categories(subs, "CNY").isEmpty())
        val metrics = StatsCalculator.metrics(subs, noDates, today, "CNY", StatsPeriod.YEAR)
        assertEquals(2, metrics.needsInfoCount)
        assertNull(metrics.topSubscriptionId)
    }

    @Test
    fun currenciesAreCountedSeparately() {
        val subs = listOf(
            sub(1, 2_000, LocalDate.of(2026, 9, 14), currency = "usd"),
            sub(2, 14_800, LocalDate.of(2026, 9, 20)),
            sub(3, 39_000, LocalDate.of(2026, 9, 1), currency = "RUB"),
        )
        assertEquals(14_800L, cash(subs, sep).totalMinor)
        assertEquals(2_000L, cash(subs, sep, "USD").totalMinor)
        assertEquals(39_000L, cash(subs, sep, " rub ").totalMinor)
        // Largest monthly spend first (no exchange rates: raw minor units).
        assertEquals(listOf("RUB", "CNY", "USD"), StatsCalculator.currencies(subs, noDates))
        assertEquals("USD", StatsCalculator.primaryCurrency(subs, noDates, "usd"))
        assertEquals("RUB", StatsCalculator.primaryCurrency(subs, noDates, "EUR"))
        assertEquals(listOf(1L), StatsCalculator.ranking(subs, "USD").map { it.subscriptionId })
    }

    // ------------------------------------------------------------ key figures and ranking

    @Test
    fun metrics_monthAndYear() {
        val added = mapOf(1L to LocalDate.of(2026, 7, 10), 2L to LocalDate.of(2026, 1, 5), 3L to LocalDate.of(2026, 9, 1))
        val subs = listOf(
            sub(1, 2_000, LocalDate.of(2026, 10, 10)), // monthly ¥20: paid Jul 10, Aug 10, Sep 10
            sub(2, 120_000, LocalDate.of(2027, 1, 5), cycle = BillingCycle.ANNUAL), // paid 2026-01-05
            sub(3, 9_900, LocalDate.of(2026, 9, 3), archived = true),
        )
        val month = StatsCalculator.metrics(subs, added, today, "CNY", StatsPeriod.MONTH)
        assertEquals(2_000L, month.periodMinor)
        assertEquals(2_000L, month.previousMinor)
        assertEquals(0L, month.deltaMinor)
        assertEquals(2L, month.topSubscriptionId)
        assertEquals(10_000L, month.topMonthlyMinor)
        // (¥20 × 12 + ¥1200) / 365 days.
        assertEquals(Math.round((24_000.0 + 120_000.0) / 365), month.dailyAverageMinor)
        // Lifetime: three monthly payments, one annual one, and the archived plan's September charge.
        assertEquals(3 * 2_000L + 120_000L + 9_900L, month.lifetimeMinor)
        assertEquals(2, month.activeCount)

        val year = StatsCalculator.metrics(subs, added, today, "CNY", StatsPeriod.YEAR)
        assertEquals(12 * 2_000L + 120_000L, year.periodMinor)
        assertEquals(12 * 2_000L + 120_000L, year.previousMinor)
    }

    @Test
    fun ranking_byMonthlyEquivalent() {
        val subs = listOf(
            sub(1, 2_000, LocalDate.of(2026, 10, 1)),
            sub(2, 120_000, LocalDate.of(2026, 11, 1), cycle = BillingCycle.ANNUAL),
            sub(3, 12_900, LocalDate.of(2026, 10, 1), cycle = BillingCycle.QUARTERLY),
        )
        val rows = StatsCalculator.ranking(subs, "CNY")
        assertEquals(listOf(2L, 3L, 1L), rows.map { it.subscriptionId })
        assertEquals(listOf(10_000L, 4_300L, 2_000L), rows.map { it.monthlyMinor })
        assertEquals(1f, rows[0].fraction, 0f)
        assertEquals(0.2f, rows[2].fraction, 1e-6f)
        val categories = StatsCalculator.categories(subs, "CNY")
        assertEquals(1, categories.size)
        assertEquals(16_300L, categories.single().monthlyMinor)
    }

    // ------------------------------------------------------------ traffic

    private fun vps(id: Long, limitGb: Long, usedGb: Long, archived: Boolean = false, linked: Boolean = true): SubscriptionWithVps {
        val gb = 1L shl 30
        return SubscriptionWithVps(
            sub(id, 1_000, LocalDate.of(2026, 10, 1), category = Category.VPS, archived = archived),
            VpsConfig(
                subscriptionId = id,
                komariUuid = if (linked) "node-$id" else null,
                trafficLimitBytes = limitGb * gb,
                lastTotalUp = usedGb * gb / 2,
                lastTotalDown = usedGb * gb,
                lastReportedAt = Instant.parse("2026-09-28T00:00:00Z"),
            ),
        )
    }

    @Test
    fun traffic_limitedByShareAndUnlimitedByAmount() {
        val items = listOf(
            vps(1, 1000, 310),
            vps(2, 500, 470),
            vps(3, 0, 800),
            vps(4, 0, 20),
            vps(5, 100, 99, archived = true),
            vps(6, 100, 50, linked = false),
            SubscriptionWithVps(sub(7, 1_000, null), null),
        )
        val t = StatsCalculator.traffic(items, today)
        assertEquals(listOf(2L, 1L), t.limited.map { it.subscriptionId })
        assertEquals(94.0, t.limited.first().percent!!, 1e-9)
        assertEquals(listOf(3L, 4L), t.unlimited.map { it.subscriptionId })
        assertFalse(t.isEmpty)
        assertTrue(StatsCalculator.traffic(emptyList(), today).isEmpty)
    }

    @Test
    fun yearRange_fromTheFirstAddedYearToNextYear() {
        assertEquals(2026..2027, StatsCalculator.yearRange(today, null))
        assertEquals(2024..2027, StatsCalculator.yearRange(today, LocalDate.of(2024, 5, 1)))
        // At most four years back; a date in the future does not move the start.
        assertEquals(2022..2027, StatsCalculator.yearRange(today, LocalDate.of(2015, 1, 1)))
        assertEquals(2026..2027, StatsCalculator.yearRange(today, LocalDate.of(2027, 1, 1)))
    }
}
