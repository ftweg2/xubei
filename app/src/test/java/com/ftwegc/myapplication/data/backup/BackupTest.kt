package com.ftwegc.myapplication.data.backup

import com.ftwegc.myapplication.data.repo.DirectTransactionRunner
import com.ftwegc.myapplication.data.repo.FakeSubscriptionDao
import com.ftwegc.myapplication.data.repo.FakeTables
import com.ftwegc.myapplication.data.repo.FakeVpsDao
import com.ftwegc.myapplication.data.repo.KomariLinkChange
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.LockedField
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.VpsConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BackupTest {

    private val exportedAt = Instant.parse("2026-09-28T08:00:00Z")

    private val ai = Subscription(
        id = 5, name = "ChatGPT Plus", category = Category.AI, priceMinor = 2_000, currency = "USD",
        cycle = BillingCycle.MONTHLY, nextDue = LocalDate.of(2026, 10, 1), anchorDay = 31, autoRenew = true,
        note = "账号 a@b.c", reminderDays = listOf(3, 0),
    )
    private val vps = Subscription(
        id = 6, name = "甲骨文-eu-92", category = Category.VPS, priceMinor = 0, currency = "CNY",
        cycle = BillingCycle.MONTHLY, cycleKnown = false,
    )
    private val vpsConfig = VpsConfig(
        subscriptionId = 6, komariUuid = "uuid-1", komariName = "eu-92", lockedFields = LockedField.NAME.mask,
        trafficLimitBytes = 1L shl 40, trafficLimitType = TrafficLimitType.SUM, countingMode = CountingMode.APP_ACCUMULATED,
        resetDay = 15, alertPercent = 90, agentMonthRotate = true,
        // Readings and error state are not exported.
        lastTotalUp = 123, lastError = "OFFLINE", appCountingSince = exportedAt,
    )
    private val lifetime = Subscription(
        id = 7, name = "终身机", category = Category.OTHER, priceMinor = 0, isFree = true, currency = "CNY",
        cycle = BillingCycle.ONE_TIME, isPermanent = true, archived = true,
    )

    @Test
    fun roundTrip_keepsEverySubscriptionField() {
        val items = listOf(SubscriptionWithVps(ai, null), SubscriptionWithVps(vps, vpsConfig), SubscriptionWithVps(lifetime, null))
        val text = BackupCodec.write(items, exportedAt)
        val read = BackupCodec.read(text) as BackupReadResult.Ok
        assertEquals(0, read.invalidCount)
        assertEquals(listOf(ai.copy(id = 0), vps.copy(id = 0), lifetime.copy(id = 0)), read.items.map { it.subscription })
        val v = read.items[1].vps!!
        assertEquals("uuid-1", v.komariUuid)
        assertEquals(LockedField.NAME.mask, v.lockedFields)
        assertEquals(TrafficLimitType.SUM, v.trafficLimitType)
        assertEquals(CountingMode.APP_ACCUMULATED, v.countingMode)
        assertEquals(15, v.resetDay)
        assertEquals(90, v.alertPercent)
        assertTrue(v.agentMonthRotate)
        assertNull("readings are not exported", v.lastTotalUp)
        assertNull(v.lastError)
        assertNull(v.appCountingSince)
        assertNull(read.items[0].vps)
    }

    @Test
    fun export_neverContainsSecrets() {
        val text = BackupCodec.write(listOf(SubscriptionWithVps(vps, vpsConfig)), exportedAt)
        assertTrue(text.contains("\"format\": \"vluer-subscriptions\""))
        assertFalse(text.contains("api_key", ignoreCase = true))
        assertFalse(text.contains("komari_url"))
        assertFalse(text.contains("OFFLINE"))
    }

    @Test
    fun read_rejectsOtherFiles_andSkipsBrokenEntries() {
        assertEquals(BackupReadResult.NotBackup, BackupCodec.read("<html>"))
        assertEquals(BackupReadResult.NotBackup, BackupCodec.read("""{"format":"other","version":1,"subscriptions":[]}"""))
        assertEquals(BackupReadResult.NewerVersion(9), BackupCodec.read("""{"format":"vluer-subscriptions","version":9,"subscriptions":[]}"""))
        val mixed = """
            {"format":"vluer-subscriptions","version":1,"subscriptions":[
              {"name":"ok","category":"AI","price_minor":100,"currency":"USD","cycle_unit":"MONTH","cycle_count":1,"next_due":"2026-10-01"},
              {"name":"","category":"AI"},
              {"name":"bad date","category":"AI","next_due":"2026-13-45"},
              {"name":"bad cycle","category":"AI","cycle_unit":"MONTH","cycle_count":0},
              "not an object"
            ]}
        """.trimIndent()
        val read = BackupCodec.read(mixed) as BackupReadResult.Ok
        assertEquals(listOf("ok"), read.items.map { it.subscription.name })
        assertEquals(4, read.invalidCount)
    }

    @Test
    fun importPlan_skipsDuplicates_andDropsTakenLinks() = runTest {
        val clock = Clock.fixed(exportedAt, ZoneId.of("Asia/Shanghai"))
        val tables = FakeTables()
        val subDao = FakeSubscriptionDao(tables)
        val vpsDao = FakeVpsDao(tables)
        val subs = SubscriptionRepository(subDao, vpsDao, DirectTransactionRunner(), clock)
        val backup = BackupRepository(subDao, vpsDao, DirectTransactionRunner(), clock)
        subs.save(ai.copy(id = 0), null)
        subs.save(vps.copy(id = 0, name = "another"), VpsConfig(), link = KomariLinkChange.Link("uuid-1", "x", null))

        val text = BackupCodec.write(listOf(SubscriptionWithVps(ai, null), SubscriptionWithVps(vps, vpsConfig), SubscriptionWithVps(lifetime, null)), exportedAt)
        val plan = backup.plan(text).getOrThrow()
        assertEquals("ChatGPT already exists", 1, plan.duplicates)
        assertEquals(2, plan.toImport.size)
        val outcome = backup.import(plan)
        assertEquals(2, outcome.imported)
        assertEquals("uuid-1 is linked to 'another'", 1, outcome.linksDropped)

        val all = subs.observeAll().first()
        assertEquals(4, all.size)
        val imported = all.single { it.subscription.name == "甲骨文-eu-92" }
        assertNull(imported.vps!!.komariUuid)
        assertEquals(CountingMode.APP_ACCUMULATED, imported.vps!!.countingMode)
        assertEquals("counting restarts at import", exportedAt, imported.vps!!.appCountingSince)
        assertFalse(imported.subscription.cycleKnown)

        // Round trip through the database: exporting again gives the same subscriptions.
        val (again, count) = backup.export()
        assertEquals(4, count)
        val reread = BackupCodec.read(again) as BackupReadResult.Ok
        assertEquals(all.map { it.subscription.copy(id = 0) }, reread.items.map { it.subscription })

        assertTrue(backup.plan("nope").isFailure)
    }
}
