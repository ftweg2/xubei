package com.ftwegc.myapplication.domain

import com.ftwegc.myapplication.domain.calc.RenewCalculator
import com.ftwegc.myapplication.domain.calc.RenewMode
import com.ftwegc.myapplication.domain.calc.RenewPlan
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RenewCalculatorTest {

    private val today = LocalDate.of(2026, 9, 28)
    private fun d(s: String) = LocalDate.parse(s)

    private fun sub(cycle: BillingCycle = BillingCycle.MONTHLY, due: String? = "2026-01-31", anchor: Int? = 31, permanent: Boolean = false) =
        Subscription(
            name = "x", category = Category.AI, priceMinor = 100, currency = "USD", cycle = cycle,
            nextDue = due?.let(::d), anchorDay = anchor, isPermanent = permanent,
        )

    @Test
    fun fromDue_twelveMonthlyRenewals_fromJan31_stayOnMonthEnds() {
        var s = sub()
        val dues = mutableListOf<LocalDate>()
        repeat(12) {
            val plan = RenewCalculator.renew(s, today, RenewMode.FROM_DUE)!!
            dues += plan.nextDue
            s = s.copy(nextDue = plan.nextDue, anchorDay = plan.anchorDay)
        }
        assertEquals(
            listOf(
                "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31", "2026-06-30", "2026-07-31",
                "2026-08-31", "2026-09-30", "2026-10-31", "2026-11-30", "2026-12-31", "2027-01-31",
            ).map(::d),
            dues,
        )
    }

    @Test
    fun fromToday_resetsAnchor() {
        assertEquals(RenewPlan(d("2026-10-28"), 28), RenewCalculator.renew(sub(), today, RenewMode.FROM_TODAY))
    }

    @Test
    fun fromToday_on31st_clampsNextMonth() {
        val on31 = LocalDate.of(2026, 1, 31)
        assertEquals(RenewPlan(d("2026-02-28"), 31), RenewCalculator.renew(sub(), on31, RenewMode.FROM_TODAY))
    }

    @Test
    fun dayCycle_keepsStoredAnchor() {
        val s = sub(cycle = BillingCycle.days(30), due = "2026-10-01", anchor = null)
        assertEquals(RenewPlan(d("2026-10-31"), null), RenewCalculator.renew(s, today, RenewMode.FROM_DUE))
        assertEquals(RenewPlan(d("2026-10-28"), null), RenewCalculator.renew(s, today, RenewMode.FROM_TODAY))
    }

    @Test
    fun missingDue_fromDue_usesToday() {
        assertEquals(RenewPlan(d("2026-10-28"), 28), RenewCalculator.renew(sub(due = null, anchor = null), today, RenewMode.FROM_DUE))
    }

    @Test
    fun onceAndPermanent_cannotRenew() {
        assertFalse(RenewCalculator.canRenew(sub(cycle = BillingCycle.ONE_TIME)))
        assertFalse(RenewCalculator.canRenew(sub(permanent = true)))
        assertTrue(RenewCalculator.canRenew(sub()))
        assertNull(RenewCalculator.renew(sub(cycle = BillingCycle.ONE_TIME), today, RenewMode.FROM_DUE))
        assertNull(RenewCalculator.renew(sub(permanent = true), today, RenewMode.FROM_TODAY))
    }
}
