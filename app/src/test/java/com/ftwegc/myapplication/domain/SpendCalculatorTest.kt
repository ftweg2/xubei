package com.ftwegc.myapplication.domain

import com.ftwegc.myapplication.domain.calc.SpendCalculator
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class SpendCalculatorTest {

    private fun sub(
        name: String,
        priceMinor: Long,
        cycle: BillingCycle,
        currency: String = "USD",
        isFree: Boolean = false,
        archived: Boolean = false,
        nextDue: LocalDate? = null,
        anchorDay: Int? = null,
        isPermanent: Boolean = false,
    ) = Subscription(
        name = name,
        category = Category.OTHER,
        priceMinor = priceMinor,
        isFree = isFree,
        currency = currency,
        cycle = cycle,
        nextDue = nextDue,
        anchorDay = anchorDay,
        isPermanent = isPermanent,
        archived = archived,
    )

    private val monthly = sub("A monthly", 2_000, BillingCycle.MONTHLY, "USD")
    private val quarterly = sub("B quarterly", 3_000, BillingCycle.QUARTERLY, "USD")
    private val annual = sub("C annual", 12_000, BillingCycle.ANNUAL, "CNY")
    private val custom45 = sub("D 45 days", 4_500, BillingCycle.days(45), "CNY")
    private val once = sub("E once", 99_900, BillingCycle.ONE_TIME, "USD")
    private val free = sub("F free", 5_000, BillingCycle.MONTHLY, "USD", isFree = true)
    private val archivedSub = sub("G archived", 7_000, BillingCycle.MONTHLY, "CNY", archived = true)
    private val mixed = listOf(monthly, quarterly, annual, custom45, once, free, archivedSub)

    @Test
    fun monthlyPerSubscription() {
        assertEquals(2_000L, SpendCalculator.monthlyMinor(monthly))
        assertEquals(1_000L, SpendCalculator.monthlyMinor(quarterly))
        assertEquals(1_000L, SpendCalculator.monthlyMinor(annual))
        // 4500 × 30.4375 / 45 = 3043.75 -> 3044
        assertEquals(0, BigDecimal("3043.75").compareTo(SpendCalculator.monthlyExact(custom45)))
        assertEquals(3_044L, SpendCalculator.monthlyMinor(custom45))
        assertNull(SpendCalculator.monthlyMinor(once))
        assertNull(SpendCalculator.monthlyMinor(free))
        assertNull(SpendCalculator.monthlyMinor(archivedSub))
    }

    @Test
    fun monthlyByCurrency_mixedCycles() {
        // USD: 2000 + 1000 ; CNY: 1000 + 3043.75 = 4043.75 -> 4044
        assertEquals(mapOf("CNY" to 4_044L, "USD" to 3_000L), SpendCalculator.monthlyByCurrency(mixed))
        assertEquals(listOf("CNY", "USD"), SpendCalculator.monthlyByCurrency(mixed).keys.toList())
    }

    @Test
    fun annualByCurrency_mixedCycles() {
        // USD: 2000×12 + 3000×4 = 36000 ; CNY: 12000 + 4500 × 365.25 / 45 = 12000 + 36525
        assertEquals(mapOf("CNY" to 48_525L, "USD" to 36_000L), SpendCalculator.annualByCurrency(mixed))
        assertNull(SpendCalculator.annualExact(once))
    }

    @Test
    fun otherMonthCycles() {
        assertEquals(1_000L, SpendCalculator.monthlyMinor(sub("semi", 6_000, BillingCycle.SEMI_ANNUAL)))
        assertEquals(500L, SpendCalculator.monthlyMinor(sub("2y", 12_000, BillingCycle.BIENNIAL)))
        assertEquals(1_000L, SpendCalculator.monthlyMinor(sub("3y", 36_000, BillingCycle.TRIENNIAL)))
        assertEquals(6_000L, SpendCalculator.annualExact(sub("2y", 12_000, BillingCycle.BIENNIAL))!!.let(SpendCalculator::roundMinor))
    }

    @Test
    fun rounding_halfUpToMinorUnits() {
        // 1000 / 3 = 333.33 -> 333 ; 1001 / 3 = 333.67 -> 334 ; 1 / 2 = 0.5 -> 1
        assertEquals(333L, SpendCalculator.monthlyMinor(sub("q", 1_000, BillingCycle.QUARTERLY)))
        assertEquals(334L, SpendCalculator.monthlyMinor(sub("q", 1_001, BillingCycle.QUARTERLY)))
        assertEquals(1L, SpendCalculator.monthlyMinor(sub("m2", 1, BillingCycle.months(2))))
        // 100 × 30.4375 / 7 = 434.82 -> 435
        assertEquals(435L, SpendCalculator.monthlyMinor(sub("weekly", 100, BillingCycle.days(7))))
        // 999 × 30.4375 / 30 = 1013.54 -> 1014 (not 999: 30 days is not a calendar month)
        assertEquals(1_014L, SpendCalculator.monthlyMinor(sub("30d", 999, BillingCycle.days(30))))
    }

    @Test
    fun rounding_totalIsRoundedOnceFromExactSum() {
        val a = sub("q1", 1_000, BillingCycle.QUARTERLY)
        val b = sub("q2", 1_000, BillingCycle.QUARTERLY)
        // 333.33 + 333.33 = 666.67 -> 667 (rounding each first would give 666)
        assertEquals(mapOf("USD" to 667L), SpendCalculator.monthlyByCurrency(listOf(a, b)))
        val three = listOf(a, b, sub("q3", 1_000, BillingCycle.QUARTERLY))
        assertEquals(mapOf("USD" to 1_000L), SpendCalculator.monthlyByCurrency(three))
    }

    @Test
    fun currencyGrouping_trimsAndIgnoresCase_butKeepsSymbolsSeparate() {
        val subs = listOf(
            sub("a", 100, BillingCycle.MONTHLY, "usd"),
            sub("b", 200, BillingCycle.MONTHLY, " USD "),
            sub("c", 300, BillingCycle.MONTHLY, "$"),
            sub("d", 400, BillingCycle.MONTHLY, "¥"),
            sub("e", 500, BillingCycle.MONTHLY, "CNY"),
            sub("f", 600, BillingCycle.MONTHLY, "€"),
        )
        assertEquals(
            mapOf("$" to 300L, "CNY" to 500L, "USD" to 300L, "¥" to 400L, "€" to 600L),
            SpendCalculator.monthlyByCurrency(subs),
        )
    }

    @Test
    fun zeroPrice_freeArchivedAndOnceProduceNoGroups() {
        val subs = listOf(
            sub("zero", 0, BillingCycle.MONTHLY, "EUR"),
            sub("free", 1_000, BillingCycle.MONTHLY, "GBP", isFree = true),
            sub("archived", 1_000, BillingCycle.MONTHLY, "JPY", archived = true),
            sub("once", 1_000, BillingCycle.ONE_TIME, "HKD"),
        )
        assertTrue(SpendCalculator.monthlyByCurrency(subs).isEmpty())
        assertTrue(SpendCalculator.annualByCurrency(subs).isEmpty())
        assertTrue(SpendCalculator.monthlyByCurrency(emptyList()).isEmpty())
    }

    @Test
    fun dueInMonth_sumsPricesDueInThatMonth() {
        val oct = YearMonth.of(2026, 10)
        val subs = listOf(
            sub("monthly", 2_000, BillingCycle.MONTHLY, "USD", nextDue = LocalDate.of(2026, 10, 5)),
            sub("quarterly next month", 3_000, BillingCycle.QUARTERLY, "USD", nextDue = LocalDate.of(2026, 11, 1)),
            sub("annual last day", 12_000, BillingCycle.ANNUAL, "CNY", nextDue = LocalDate.of(2026, 10, 31)),
            // Weekly: due Oct 3, 10, 17, 24, 31 -> 5 × 100
            sub("weekly", 100, BillingCycle.days(7), "USD", nextDue = LocalDate.of(2026, 10, 3)),
            // 45 days: Oct 20, next Dec 4 -> once
            sub("45d", 4_500, BillingCycle.days(45), "CNY", nextDue = LocalDate.of(2026, 10, 20)),
            sub("overdue", 800, BillingCycle.MONTHLY, "USD", nextDue = LocalDate.of(2026, 9, 30)),
            sub("no due", 800, BillingCycle.MONTHLY, "USD"),
            sub("free", 800, BillingCycle.MONTHLY, "USD", isFree = true, nextDue = LocalDate.of(2026, 10, 1)),
            sub("archived", 800, BillingCycle.MONTHLY, "USD", archived = true, nextDue = LocalDate.of(2026, 10, 1)),
            sub("once", 800, BillingCycle.ONE_TIME, "USD", nextDue = LocalDate.of(2026, 10, 1)),
            sub("permanent", 800, BillingCycle.MONTHLY, "USD", isPermanent = true, nextDue = LocalDate.of(2026, 10, 1)),
        )
        assertEquals(
            mapOf("CNY" to 16_500L, "USD" to 2_500L),
            SpendCalculator.dueInMonthByCurrency(subs, oct),
        )
        assertEquals(
            mapOf("USD" to 3_000L),
            SpendCalculator.dueInMonthByCurrency(subs, YearMonth.of(2026, 11)),
        )
        assertTrue(SpendCalculator.dueInMonthByCurrency(subs, YearMonth.of(2027, 1)).isEmpty())
    }
}
