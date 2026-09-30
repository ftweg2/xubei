package com.ftwegc.myapplication.data.backup

import com.ftwegc.myapplication.data.repo.DirectTransactionRunner
import com.ftwegc.myapplication.data.repo.FakeSubscriptionDao
import com.ftwegc.myapplication.data.repo.FakeTables
import com.ftwegc.myapplication.data.repo.FakeVpsDao
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The app was renamed from Vluer to 续杯: new exports get a new file name, and files exported
 * before the rename (named `vluer-subscriptions-<date>.json`) must keep importing.
 */
class BackupCompatTest {

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("backup/$name")) { "missing test resource backup/$name" }.readText()

    @Test
    fun exportFileName_usesTheNewName() {
        assertEquals("xubei-backup-2026-09-29.json", BackupCodec.fileName(LocalDate.of(2026, 9, 29)))
    }

    @Test
    fun exportBeforeRename_readsEveryField() {
        val read = BackupCodec.read(resource("vluer-subscriptions-2026-09-28.json")) as BackupReadResult.Ok
        assertEquals(0, read.invalidCount)
        assertEquals(listOf("ChatGPT Plus", "甲骨文-eu-92", "终身机"), read.items.map { it.subscription.name })
        val ai = read.items[0].subscription
        assertEquals(Category.AI, ai.category)
        assertEquals(2_000L, ai.priceMinor)
        assertEquals("USD", ai.currency)
        assertEquals(LocalDate.of(2026, 10, 1), ai.nextDue)
        assertEquals(listOf(3, 0), ai.reminderDays)
        assertEquals("openai", ai.iconKey)
        val vps = read.items[1].vps!!
        assertEquals("uuid-1", vps.komariUuid)
        assertEquals(TrafficLimitType.SUM, vps.trafficLimitType)
        assertEquals(CountingMode.APP_ACCUMULATED, vps.countingMode)
        assertEquals(15, vps.resetDay)
        assertEquals("🇩🇪", vps.komariRegion)
        val lifetime = read.items[2].subscription
        assertEquals(BillingCycle.ONE_TIME, lifetime.cycle)
        assertTrue(lifetime.isPermanent)
        assertTrue(lifetime.archived)
    }

    @Test
    fun earlyExportWithoutNewerFields_imports() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-09-29T02:00:00Z"), ZoneId.of("Asia/Shanghai"))
        val tables = FakeTables()
        val subDao = FakeSubscriptionDao(tables)
        val vpsDao = FakeVpsDao(tables)
        val backup = BackupRepository(subDao, vpsDao, DirectTransactionRunner(), clock)
        val plan = backup.plan(resource("vluer-subscriptions-early.json")).getOrThrow()
        assertEquals(2, plan.toImport.size)
        assertEquals(2, backup.import(plan).imported)
        val all = SubscriptionRepository(subDao, vpsDao, DirectTransactionRunner(), clock).observeAll().first()
        assertEquals(setOf("Netflix", "东京 CN2"), all.map { it.subscription.name }.toSet())
        val vps = all.single { it.subscription.name == "东京 CN2" }.vps!!
        assertEquals("uuid-9", vps.komariUuid)
        assertEquals(18, vps.resetDay)
    }

    @Test
    fun newExport_isReadBackAndStillCarriesTheFormatOlderVersionsKnow() {
        val old = BackupCodec.read(resource("vluer-subscriptions-2026-09-28.json")) as BackupReadResult.Ok
        val text = BackupCodec.write(old.items, Instant.parse("2026-09-29T02:00:00Z"))
        assertTrue(text.contains("\"format\": \"vluer-subscriptions\""))
        val again = BackupCodec.read(text) as BackupReadResult.Ok
        assertEquals(old.items, again.items)
    }
}
