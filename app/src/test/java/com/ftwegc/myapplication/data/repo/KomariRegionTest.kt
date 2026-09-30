package com.ftwegc.myapplication.data.repo

import com.ftwegc.myapplication.data.prefs.SecretStore
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.VpsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** The node's flag (`region`) is kept with the VPS: import, sync, manual link and unlink. */
class KomariRegionTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-28T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
    private val tables = FakeTables()
    private val subDao = FakeSubscriptionDao(tables)
    private val vpsDao = FakeVpsDao(tables)
    private val tx = DirectTransactionRunner()
    private val settings = SettingsStore(FakePreferencesDataStore())
    private val secrets = SecretStore(FakePreferencesDataStore(), FakeCipher(), Dispatchers.Unconfined)
    private val api = FakeKomariApi()
    private val repo = KomariRepository(settings, secrets, RecordingFactory(api), subDao, vpsDao, tx, clock)
    private val subs = SubscriptionRepository(subDao, vpsDao, tx, clock)

    private fun node(uuid: String, region: String) = komariNode(uuid).copy(region = region)

    private suspend fun regionOf(uuid: String): String? = vpsDao.getByKomariUuid(uuid)?.komariRegion

    @Test
    fun importAndSync_keepTheRegion() = runTest {
        repo.importNodes(listOf(node("a", "🇩🇪"), node("b", "")))
        assertEquals("🇩🇪", regionOf("a"))
        assertNull(regionOf("b"))
        // The panel moved the node: the next sync updates the flag.
        repo.syncWith(listOf(node("a", "🇺🇸"), node("b", "🇯🇵")))
        assertEquals("🇺🇸", regionOf("a"))
        assertEquals("🇯🇵", regionOf("b"))
        // A region cleared on the panel keeps the last known flag.
        repo.syncWith(listOf(node("a", " "), node("b", "🇯🇵")))
        assertEquals("🇺🇸", regionOf("a"))
    }

    @Test
    fun manualLinkAndUnlink() = runTest {
        val sub = Subscription(name = "VPS", category = Category.VPS, priceMinor = 100, currency = "CNY", cycle = BillingCycle.MONTHLY)
        val id = subs.save(sub, VpsConfig(), link = KomariLinkChange.Link("n1", "Node", null, "🇸🇬"))
        assertEquals("🇸🇬", subs.get(id)!!.vps!!.komariRegion)
        // Editing without touching the link keeps it.
        subs.save(subs.get(id)!!.subscription.copy(priceMinor = 200), subs.get(id)!!.vps)
        assertEquals("🇸🇬", subs.get(id)!!.vps!!.komariRegion)
        subs.unlinkKomari(id)
        assertNull(subs.get(id)!!.vps!!.komariRegion)
    }
}
