package com.ftwegc.myapplication.ui

import androidx.datastore.preferences.core.emptyPreferences
import com.ftwegc.myapplication.data.prefs.AppSettings
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.repo.FakePreferencesDataStore
import com.ftwegc.myapplication.data.repo.KomariLinkChange
import com.ftwegc.myapplication.domain.calc.TrafficResolver
import com.ftwegc.myapplication.domain.model.AppPeriodUsage
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.TrafficPeriod
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.ui.common.TrafficUi
import com.ftwegc.myapplication.ui.common.toUi
import com.ftwegc.myapplication.ui.components.CyclePreset
import com.ftwegc.myapplication.ui.format.SizeUnit
import com.ftwegc.myapplication.ui.subscriptions.FieldError
import com.ftwegc.myapplication.ui.subscriptions.FormField
import com.ftwegc.myapplication.ui.subscriptions.FormResult
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionDetailViewModel
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionEditViewModel
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionForm
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionFormValidator
import com.ftwegc.myapplication.work.WorkScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class Stage79UiLogicTest {

    private val gib = 1L shl 30
    private val today = LocalDate.of(2026, 9, 28)

    /** A Komari import without billing data and without a cycle. */
    private val incomplete = Subscription(
        id = 7, name = "甲骨文-eu-92", category = Category.VPS, priceMinor = 0, currency = "CNY",
        cycle = BillingCycle.MONTHLY, cycleKnown = false,
    )

    // ------------------------------------------------------------------ Issue 1: incomplete can be saved

    @Test
    fun incomplete_canBeSavedWithOnlyTheNameChanged() {
        val form = SubscriptionForm.from(incomplete, VpsConfig(subscriptionId = 7)).copy(name = "法兰克福")
        assertNull("no cycle chip preselected", form.cyclePreset)
        assertEquals("", form.price)
        val result = SubscriptionFormValidator.validate(form, incomplete, VpsConfig(subscriptionId = 7)) as FormResult.Valid
        assertEquals("法兰克福", result.subscription.name)
        assertEquals(0L, result.subscription.priceMinor)
        assertNull(result.subscription.nextDue)
        assertFalse("cycle stays unknown", result.subscription.cycleKnown)
    }

    @Test
    fun incomplete_needsACycleOncePriceOrDueIsFilled() {
        val form = SubscriptionForm.from(incomplete, null).copy(price = "5")
        val errors = (SubscriptionFormValidator.validate(form, incomplete, null) as FormResult.Invalid).errors
        assertEquals(FieldError.CycleRequired, errors[FormField.CYCLE])
        val ok = SubscriptionFormValidator.validate(form.copy(cyclePreset = CyclePreset.ANNUAL), incomplete, null) as FormResult.Valid
        assertTrue(ok.subscription.cycleKnown)
        assertEquals(BillingCycle.ANNUAL, ok.subscription.cycle)
        assertEquals(500L, ok.subscription.priceMinor)
    }

    @Test
    fun newSubscription_stillNeedsPriceAndDueDate() {
        val errors = (SubscriptionFormValidator.validate(SubscriptionForm(name = "x", price = ""), null, null) as FormResult.Invalid).errors
        assertEquals(FieldError.InvalidAmount, errors[FormField.PRICE])
        assertEquals(FieldError.DueRequired, errors[FormField.DUE_DATE])
    }

    // ------------------------------------------------------------------ Issue 2: no cycle shown when unknown

    @Test
    fun unknownCycle_isNotShown() {
        val card = SubscriptionWithVps(incomplete.copy(priceMinor = 900), null).toUi(today, 7)
        assertFalse(card.cycleKnown)
        assertFalse("price filled, so no longer 待补充", card.needsBillingInfo)
        val state = SubscriptionDetailViewModel.build(SubscriptionWithVps(incomplete, null), AppSettings(), today)
        assertNull("no cycle progress without a known cycle", state.cycleProgress)
    }

    // ------------------------------------------------------------------ Mode 2 form fields

    @Test
    fun initialUsed_parsedOnlyInMode2() {
        val base = SubscriptionForm(name = "v", category = Category.VPS, price = "1", nextDue = today)
        val app = base.copy(countingMode = CountingMode.APP_ACCUMULATED, initialUsedValue = "1.5", initialUsedUnit = SizeUnit.TB)
        assertEquals(3L * (1L shl 39), (SubscriptionFormValidator.validate(app, null, null) as FormResult.Valid).initialUsedBytes)
        assertEquals(0L, (SubscriptionFormValidator.validate(app.copy(initialUsedValue = ""), null, null) as FormResult.Valid).initialUsedBytes)
        assertEquals(0L, (SubscriptionFormValidator.validate(app.copy(initialUsedValue = "0"), null, null) as FormResult.Valid).initialUsedBytes)
        assertEquals(
            FieldError.InvalidInitialUsed,
            (SubscriptionFormValidator.validate(app.copy(initialUsedValue = "1.2.3"), null, null) as FormResult.Invalid).errors[FormField.INITIAL_USED],
        )
        assertNull((SubscriptionFormValidator.validate(base, null, null) as FormResult.Valid).initialUsedBytes)
        val rotated = SubscriptionFormValidator.validate(base.copy(agentMonthRotate = true), null, null) as FormResult.Valid
        assertTrue(rotated.vps!!.agentMonthRotate)
    }

    @Test
    fun ownReminderDays_storedOnlyWhenCustom() {
        val base = SubscriptionForm(name = "a", price = "1", nextDue = today, reminderDays = listOf(0, 14))
        assertNull((SubscriptionFormValidator.validate(base, null, null) as FormResult.Valid).subscription.reminderDays)
        val custom = SubscriptionFormValidator.validate(base.copy(customReminders = true), null, null) as FormResult.Valid
        assertEquals(listOf(14, 0), custom.subscription.reminderDays)
        val none = SubscriptionFormValidator.validate(base.copy(customReminders = true, reminderDays = emptyList()), null, null) as FormResult.Valid
        assertEquals(emptyList<Int>(), none.subscription.reminderDays)
    }

    // ------------------------------------------------------------------ Issue 4: process death

    @Test
    fun form_survivesSerialization() {
        val form = SubscriptionForm(
            name = "东京", category = Category.VPS, price = "12.5", cyclePreset = null, nextDue = today,
            countingMode = CountingMode.APP_ACCUMULATED, initialUsedValue = "3", reminderDays = listOf(7, 0), customReminders = true,
        )
        val json = Json.encodeToString(SubscriptionForm.serializer(), form)
        assertEquals(form, Json.decodeFromString(SubscriptionForm.serializer(), json))
    }

    @Test
    fun linkChange_codec() {
        val link = KomariLinkChange.Link("uuid-1", "东京", null)
        assertEquals(link, SubscriptionEditViewModel.decodeLink(SubscriptionEditViewModel.encodeLink(link)))
        assertEquals(KomariLinkChange.Unlink, SubscriptionEditViewModel.decodeLink(SubscriptionEditViewModel.encodeLink(KomariLinkChange.Unlink)))
        assertEquals(KomariLinkChange.Keep, SubscriptionEditViewModel.decodeLink(null))
        assertEquals(KomariLinkChange.Keep, SubscriptionEditViewModel.decodeLink("garbage"))
    }

    // ------------------------------------------------------------------ Resolver and long offline

    @Test
    fun resolver_mode2_stalePeriodShowsEmptyNewPeriod() {
        val vps = VpsConfig(komariUuid = "n", countingMode = CountingMode.APP_ACCUMULATED, trafficLimitBytes = 100 * gib, resetDay = 1)
        val september = AppPeriodUsage(TrafficPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)), 5 * gib, 9 * gib, 1 * gib, Instant.EPOCH.plusSeconds(1))
        val inSeptember = TrafficResolver.resolve(vps, september, today)
        assertEquals(10 * gib, inSeptember.usage!!.usedBytes)
        val inOctober = TrafficResolver.resolve(vps, september, LocalDate.of(2026, 10, 2))
        assertTrue(inOctober.waitingForNewPeriod)
        assertEquals(0L, inOctober.usage!!.usedBytes)
        assertEquals(LocalDate.of(2026, 10, 1), inOctober.period!!.start)
    }

    @Test
    fun longOffline_afterThreeDaysWithoutReport() {
        val now = Instant.parse("2026-09-28T00:00:00Z")
        val vps = VpsConfig(komariUuid = "aws", lastError = "OFFLINE", lastReportedAt = now.minus(Duration.ofDays(2)))
        assertFalse(TrafficUi.of(vps, now = now).longOffline)
        assertTrue(TrafficUi.of(vps.copy(lastReportedAt = now.minus(Duration.ofDays(4))), now = now).longOffline)
        assertTrue("never reported", TrafficUi.of(vps.copy(lastReportedAt = null), now = now).longOffline)
        assertFalse("online", TrafficUi.of(vps.copy(lastError = null, lastReportedAt = null), now = now).longOffline)
    }

    // ------------------------------------------------------------------ Settings and scheduling

    @Test
    fun settings_newFields_defaultsAndValidation() = runTest {
        val defaults = SettingsStore.read(emptyPreferences())
        assertEquals(30, defaults.refreshIntervalMinutes)
        assertNull(defaults.resetZoneId)
        assertFalse(defaults.notificationPromptShown)

        val store = SettingsStore(FakePreferencesDataStore())
        store.setRefreshIntervalMinutes(0)
        assertEquals(0, store.settings.first().refreshIntervalMinutes)
        store.setRefreshIntervalMinutes(7) // not offered: ignored
        assertEquals(0, store.settings.first().refreshIntervalMinutes)
        store.setResetZoneId("Asia/Shanghai")
        assertEquals("Asia/Shanghai", store.settings.first().resetZoneId)
        store.setResetZoneId("Mars/Olympus")
        assertNull(store.settings.first().resetZoneId)
        store.setNotificationPromptShown(true)
        assertTrue(store.settings.first().notificationPromptShown)
    }

    @Test
    fun dailyReminder_runsAtNineNextTime() {
        val nine = LocalTime.of(9, 0)
        assertEquals(Duration.ofHours(1), WorkScheduler.delayUntilNext(LocalDateTime.of(2026, 9, 28, 8, 0), nine))
        assertEquals(Duration.ofHours(23), WorkScheduler.delayUntilNext(LocalDateTime.of(2026, 9, 28, 10, 0), nine))
        assertEquals(Duration.ofDays(1), WorkScheduler.delayUntilNext(LocalDateTime.of(2026, 9, 28, 9, 0), nine))
    }
}
