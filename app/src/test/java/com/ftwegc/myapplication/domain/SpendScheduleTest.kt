package com.ftwegc.myapplication.domain

import com.ftwegc.myapplication.domain.calc.SpendSchedule
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SpendScheduleTest {

    private val today = LocalDate.of(2026, 9, 28)
    private val sep = YearMonth.of(2026, 9)

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
        name: String = "s$id",
    ) = Subscription(
        id = id, name = name, category = category, priceMinor = price, isFree = free, currency = currency, cycle = cycle,
        nextDue = due, anchorDay = anchor, archived = archived, isPermanent = permanent,
    )

    // ------------------------------------------------------------ due dates

    @Test
    fun dueDates_backwardAndForwardFromNextDue() {
        // Monthly on the 5th, next due Oct 5: this month's payment was Sep 5.
        val s = sub(1, 1000, LocalDate.of(2026, 10, 5))
        assertEquals(listOf(LocalDate.of(2026, 9, 5)), SpendSchedule.dueDatesIn(s, sep.atDay(1), sep.atEndOfMonth()))
        assertEquals(
            listOf(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 5)),
            SpendSchedule.dueDatesIn(s, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 30)),
        )
    }

    @Test
    fun dueDates_keepTheMonthEndAnchor() {
        // Due on the 31st: February gets the 28th, March the 31st again.
        val s = sub(1, 1000, LocalDate.of(2026, 3, 31), anchor = 31)
        assertEquals(
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)),
            SpendSchedule.dueDatesIn(s, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30)),
        )
        assertEquals(LocalDate.of(2026, 2, 28), SpendSchedule.previousDue(s))
    }

    @Test
    fun dueDates_dayCyclesAndOneTime() {
        val weekly = sub(1, 100, LocalDate.of(2026, 9, 30), cycle = BillingCycle.days(7))
        assertEquals(
            listOf(2, 9, 16, 23, 30).map { LocalDate.of(2026, 9, it) },
            SpendSchedule.dueDatesIn(weekly, sep.atDay(1), sep.atEndOfMonth()),
        )
        val once = sub(2, 100, LocalDate.of(2026, 9, 10), cycle = BillingCycle.ONE_TIME)
        assertEquals(listOf(LocalDate.of(2026, 9, 10)), SpendSchedule.dueDatesIn(once, sep.atDay(1), sep.atEndOfMonth()))
        assertNull(SpendSchedule.previousDue(once))
        assertTrue(SpendSchedule.dueDatesIn(sub(3, 100, null), sep.atDay(1), sep.atEndOfMonth()).isEmpty())
    }

    // ------------------------------------------------------------ totals

    private val subs = listOf(
        sub(1, 2_000, LocalDate.of(2026, 10, 1), currency = "USD"), // paid Sep 1
        sub(2, 14_800, LocalDate.of(2026, 10, 14)), // paid Sep 14
        sub(3, 49_900, LocalDate.of(2026, 11, 25), cycle = BillingCycle.ANNUAL, category = Category.VPS), // paid 2025-11-25
        sub(4, 12_900, LocalDate.of(2026, 9, 30), cycle = BillingCycle.QUARTERLY, category = Category.VPS), // Sep 30, Jun 30
        sub(5, 2_100, today, category = Category.OTHER), // Sep 28
        sub(6, 9_900, LocalDate.of(2026, 9, 3), archived = true),
        sub(7, 5_000, LocalDate.of(2026, 9, 3), free = true),
        sub(8, 0, null, category = Category.VPS), // imported, nothing filled in
        sub(9, 9_900, LocalDate.of(2026, 9, 3), cycle = BillingCycle.ONE_TIME, permanent = true), // lifetime
    )

    @Test
    fun monthTotals_countEveryPaymentOfTheMonth() {
        assertEquals(mapOf("CNY" to 14_800L + 12_900L + 2_100L, "USD" to 2_000L), SpendSchedule.monthTotals(subs, sep))
        // August: CNY 148 (Aug 14) + 21 (Aug 28); the quarterly one was due in June.
        assertEquals(mapOf("CNY" to 14_800L + 2_100L, "USD" to 2_000L), SpendSchedule.monthTotals(subs, YearMonth.of(2026, 8)))
    }

    @Test
    fun paymentsIn_areOrdered() {
        val p = SpendSchedule.paymentsIn(subs, sep.atDay(1), sep.atEndOfMonth())
        assertEquals(listOf(1L, 2L, 5L, 4L), p.map { it.subscriptionId })
        assertEquals(LocalDate.of(2026, 9, 30), p.last().date)
        assertEquals("USD", p.first().currency)
    }

    @Test
    fun dailyCumulative_isTheSpendingPace() {
        val pace = SpendSchedule.dailyCumulative(subs, sep, "cny")
        assertEquals(30, pace.size)
        assertEquals(0L, pace[0])
        assertEquals(14_800L, pace[13]) // Sep 14
        assertEquals(14_800L + 2_100L, pace[27]) // Sep 28
        assertEquals(14_800L + 2_100L + 12_900L, pace[29])
        // Never decreases.
        assertTrue(pace.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(mapOf(14 to 14_800L, 28 to 2_100L, 30 to 12_900L), SpendSchedule.dailyAmounts(subs, sep, "CNY"))
    }

    @Test
    fun yearByMonth_andYearTotals() {
        val months = SpendSchedule.yearByMonth(subs, 2026, "CNY")
        assertEquals(12, months.size)
        // Quarterly: Mar, Jun, Sep, Dec 30; annual: Nov 25; monthly 148 + 21 every month.
        assertEquals(14_800L + 2_100L + 12_900L, months[2])
        assertEquals(14_800L + 2_100L + 49_900L, months[10])
        assertEquals(14_800L + 2_100L, months[0])
        assertEquals(months.sum(), SpendSchedule.yearTotals(subs, 2026)["CNY"])
        assertEquals(12 * 2_000L, SpendSchedule.yearTotals(subs, 2026)["USD"])
    }

    @Test
    fun primaryCurrency_prefersTheDefault() {
        assertEquals("CNY", SpendSchedule.primaryCurrency(subs, "cny"))
        assertEquals("USD", SpendSchedule.primaryCurrency(subs, "USD"))
        // The default is not used at all: the biggest spender leads.
        assertEquals("CNY", SpendSchedule.primaryCurrency(subs, "EUR"))
        assertEquals("EUR", SpendSchedule.primaryCurrency(emptyList(), "EUR"))
    }

    @Test
    fun categoryShares_ofMonthlySpend() {
        val shares = SpendSchedule.categoryShares(subs.filter { !it.archived }, "CNY")
        assertEquals(listOf(Category.AI, Category.VPS, Category.OTHER), shares.map { it.category })
        // VPS: 499/12 + 129/3 = 41.58 + 43 = 84.58
        assertEquals(14_800L, shares[0].monthlyMinor)
        assertEquals(8_458L, shares[1].monthlyMinor)
        assertEquals(1.0, shares.sumOf { it.fraction }, 1e-9)
        assertTrue(SpendSchedule.categoryShares(subs, "JPY").isEmpty())
    }

    // ------------------------------------------------------------ due ring and timeline

    @Test
    fun nextDue_skipsPermanentAndArchived() {
        val next = SpendSchedule.nextDue(subs, today)!!
        // Archived (6) and permanent (9) are skipped; the free one (7) is due Sep 3 (overdue) and counts.
        assertEquals(7L, next.subscription.id)
        assertEquals(-25L, next.daysLeft)
        assertEquals(1f, next.elapsedFraction, 0f)
        assertNull(SpendSchedule.nextDue(emptyList(), today))
    }

    @Test
    fun elapsedFraction_ofTheCurrentCycle() {
        // Monthly due Oct 1: the cycle began Sep 1 (30 days); on Sep 28, 27 days have passed.
        assertEquals(27f / 30f, SpendSchedule.elapsedFraction(subs[0], today), 1e-5f)
        assertEquals(1f, SpendSchedule.elapsedFraction(subs[4], today), 0f) // due today
        assertEquals(0f, SpendSchedule.elapsedFraction(sub(1, 1, null), today), 0f)
    }

    @Test
    fun upcoming_withinThirtyDays_overdueIncluded() {
        val list = SpendSchedule.upcoming(subs, today, 30).map { it.first.id }
        assertEquals(listOf(7L, 5L, 4L, 1L, 2L), list)
    }
}
