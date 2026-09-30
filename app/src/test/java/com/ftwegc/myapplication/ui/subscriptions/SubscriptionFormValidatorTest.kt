package com.ftwegc.myapplication.ui.subscriptions

import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.ui.components.CyclePreset
import com.ftwegc.myapplication.ui.format.SizeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SubscriptionFormValidatorTest {

    private val due = LocalDate.of(2026, 10, 31)
    private val valid = SubscriptionForm(name = " ChatGPT ", price = "20", currency = "USD", nextDue = due)

    private fun ok(form: SubscriptionForm, base: Subscription? = null, baseVps: VpsConfig? = null) =
        SubscriptionFormValidator.validate(form, base, baseVps) as FormResult.Valid

    private fun errors(form: SubscriptionForm) =
        (SubscriptionFormValidator.validate(form, null, null) as FormResult.Invalid).errors

    @Test
    fun valid_monthly_buildsSubscription() {
        val r = ok(valid)
        assertEquals("ChatGPT", r.subscription.name)
        assertEquals(2_000L, r.subscription.priceMinor)
        assertEquals(BillingCycle.MONTHLY, r.subscription.cycle)
        assertEquals(due, r.subscription.nextDue)
        assertEquals(31, r.subscription.anchorDay)
        assertEquals(0L, r.subscription.id)
        assertNull(r.vps)
    }

    @Test
    fun requiredFields_reportErrors() {
        val e = errors(SubscriptionForm(name = "  ", price = "", currency = " ", nextDue = null))
        assertEquals(FieldError.Required, e[FormField.NAME])
        assertEquals(FieldError.InvalidAmount, e[FormField.PRICE])
        assertEquals(FieldError.Required, e[FormField.CURRENCY])
        assertEquals(FieldError.DueRequired, e[FormField.DUE_DATE])
    }

    @Test
    fun nameTooLong() {
        val e = errors(valid.copy(name = "x".repeat(SubscriptionFormValidator.NAME_MAX + 1)))
        assertEquals(FieldError.TooLong(SubscriptionFormValidator.NAME_MAX), e[FormField.NAME])
    }

    @Test
    fun priceWithThreeDecimals_isInvalid() {
        assertEquals(FieldError.InvalidAmount, errors(valid.copy(price = "1.999"))[FormField.PRICE])
    }

    @Test
    fun oneTime_andPermanent_doNotNeedDueDate() {
        val once = ok(valid.copy(cyclePreset = CyclePreset.ONCE, nextDue = null))
        assertEquals(BillingCycle.ONE_TIME, once.subscription.cycle)
        assertNull(once.subscription.anchorDay)
        val permanent = ok(valid.copy(isPermanent = true, nextDue = due))
        assertTrue(permanent.subscription.isPermanent)
        assertNull("permanent drops the date", permanent.subscription.nextDue)
    }

    @Test
    fun customCycle_validatesCount() {
        assertEquals(BillingCycle.days(45), ok(valid.copy(cyclePreset = CyclePreset.CUSTOM, customCount = "45")).subscription.cycle)
        assertEquals(
            BillingCycle.months(2),
            ok(valid.copy(cyclePreset = CyclePreset.CUSTOM, customCount = "2", customUnit = CycleUnit.MONTH)).subscription.cycle,
        )
        assertEquals(
            FieldError.CycleCount(SubscriptionFormValidator.MAX_CYCLE_DAYS),
            errors(valid.copy(cyclePreset = CyclePreset.CUSTOM, customCount = "0"))[FormField.CYCLE_COUNT],
        )
        assertEquals(
            FieldError.CycleCount(SubscriptionFormValidator.MAX_CYCLE_MONTHS),
            errors(valid.copy(cyclePreset = CyclePreset.CUSTOM, customCount = "121", customUnit = CycleUnit.MONTH))[FormField.CYCLE_COUNT],
        )
    }

    @Test
    fun dayCycle_hasNoAnchor() {
        assertNull(ok(valid.copy(cyclePreset = CyclePreset.CUSTOM, customCount = "30")).subscription.anchorDay)
    }

    @Test
    fun edit_keepsIdArchivedAndAnchor_whenDueUnchanged() {
        val base = Subscription(
            id = 9, name = "Old", category = Category.AI, priceMinor = 100, currency = "USD",
            cycle = BillingCycle.MONTHLY, nextDue = LocalDate.of(2027, 2, 28), anchorDay = 31, archived = true,
        )
        val r = ok(SubscriptionForm.from(base, null).copy(name = "New"), base)
        assertEquals(9L, r.subscription.id)
        assertTrue(r.subscription.archived)
        assertEquals(31, r.subscription.anchorDay)
        assertEquals("New", r.subscription.name)

        val moved = ok(SubscriptionForm.from(base, null).copy(nextDue = LocalDate.of(2027, 3, 15)), base)
        assertEquals(15, moved.subscription.anchorDay)
    }

    @Test
    fun vps_buildsConfig_andValidatesTrafficFields() {
        val form = valid.copy(
            category = Category.VPS, limitValue = "1.5", limitUnit = SizeUnit.TB, limitType = TrafficLimitType.SUM,
            countingMode = CountingMode.APP_ACCUMULATED, resetDay = "31", alertPercent = 85,
        )
        val base = VpsConfig(subscriptionId = 4, komariUuid = "n1", lastTotalUp = 1)
        val vps = ok(form, baseVps = base).vps!!
        assertEquals((1L shl 40) + (1L shl 39), vps.trafficLimitBytes)
        assertEquals(TrafficLimitType.SUM, vps.trafficLimitType)
        assertEquals(CountingMode.APP_ACCUMULATED, vps.countingMode)
        assertEquals(31, vps.resetDay)
        assertEquals(85, vps.alertPercent)
        assertEquals("n1", vps.komariUuid)
        assertEquals(1L, vps.lastTotalUp)

        assertEquals(0L, ok(form.copy(limitValue = " ")).vps!!.trafficLimitBytes)
        val bad = errors(form.copy(limitValue = "0", resetDay = "32"))
        assertEquals(FieldError.InvalidLimit, bad[FormField.TRAFFIC_LIMIT])
        assertEquals(FieldError.InvalidResetDay, bad[FormField.RESET_DAY])
    }

    @Test
    fun vpsFieldsIgnored_forOtherCategories() {
        val r = ok(valid.copy(category = Category.OTHER, limitValue = "abc", resetDay = "99"))
        assertNull(r.vps)
    }

    @Test
    fun formFrom_roundTripsThroughValidate() {
        val sub = Subscription(
            id = 3, name = "Tokyo", category = Category.VPS, priceMinor = 49_950, currency = "CNY",
            cycle = BillingCycle.days(90), nextDue = LocalDate.of(2026, 12, 1), anchorDay = null, autoRenew = true,
            note = "note",
        )
        val vps = VpsConfig(subscriptionId = 3, trafficLimitBytes = 500L shl 30, resetDay = 12, alertPercent = 70)
        val r = ok(SubscriptionForm.from(sub, vps), sub, vps)
        assertEquals(sub, r.subscription)
        assertEquals(vps, r.vps)
    }
}
