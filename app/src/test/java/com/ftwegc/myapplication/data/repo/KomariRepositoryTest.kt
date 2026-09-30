package com.ftwegc.myapplication.data.repo

import com.ftwegc.myapplication.data.prefs.ApiKeyStatus
import com.ftwegc.myapplication.data.prefs.SecretStore
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.domain.calc.FieldChange
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.LockedField
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.VpsConfig
import kotlinx.coroutines.Dispatchers
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

class KomariRepositoryTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-09-28T04:00:00Z")
    private val clock = Clock.fixed(now, zone)
    private val tables = FakeTables()
    private val subDao = FakeSubscriptionDao(tables)
    private val vpsDao = FakeVpsDao(tables)
    private val tx = DirectTransactionRunner()
    private val settingsData = FakePreferencesDataStore()
    private val secretData = FakePreferencesDataStore()
    private val settings = SettingsStore(settingsData)
    private val cipher = FakeCipher()
    private val secrets = SecretStore(secretData, cipher, Dispatchers.Unconfined)
    private val api = FakeKomariApi()
    private val factory = RecordingFactory(api)
    private val repo = KomariRepository(settings, secrets, factory, subDao, vpsDao, tx, clock)
    private val subs = SubscriptionRepository(subDao, vpsDao, tx, clock)

    private val key = "test-key-for-unit-tests"

    private suspend fun configure(url: String = "https://panel.example.com", apiKey: String? = null) {
        repo.saveConfig(url, apiKey?.let { KeyInput.New(it) } ?: KeyInput.None)
    }

    private suspend fun linkedSubscription(uuid: String, locked: Int = LockedField.NONE, price: Long = 1_000): Long {
        val id = subs.save(
            Subscription(
                name = "Mine", category = Category.VPS, priceMinor = price, currency = "¥",
                cycle = BillingCycle.MONTHLY, nextDue = LocalDate.of(2026, 10, 15), anchorDay = 15,
            ),
            VpsConfig(trafficLimitBytes = 1L shl 40),
            link = KomariLinkChange.Link(uuid, "Node $uuid", null),
        )
        if (locked != LockedField.NONE) {
            val row = tables.vps.value.getValue(id)
            tables.vps.value = tables.vps.value + (id to row.copy(lockedFields = locked))
        }
        return id
    }

    // ------------------------------------------------------------------ configuration

    @Test
    fun testConnection_validatesAddress() = runTest {
        assertEquals(KomariResult.Err(KomariError.NotConfigured), repo.testConnection("  ", KeyInput.None))
        assertEquals(KomariResult.Err(KomariError.InvalidUrl), repo.testConnection("ftp://x", KeyInput.None))
        assertTrue(factory.endpoints.isEmpty())
    }

    @Test
    fun testConnection_reportsVersionAndNodeCount_usingTheGivenKey() = runTest {
        api.setNodes(komariNode("a"), komariNode("b"))
        val info = (repo.testConnection("panel.example.com/api/", KeyInput.New(" $key ")) as KomariResult.Ok).value
        assertEquals(KomariConnectionInfo("1.5.1", 2, 0), info)
        val endpoint = factory.endpoints.single()
        assertEquals("https://panel.example.com", endpoint.baseUrl)
        assertEquals(key, endpoint.apiKey)
        // Nothing is saved by a test.
        assertNull(settings.settings.first().komariUrl)
        assertNull(secrets.getApiKey())
    }

    @Test
    fun testConnection_savedKeyAndGuest() = runTest {
        configure(apiKey = key)
        repo.testConnection("https://panel.example.com", KeyInput.Saved)
        repo.testConnection("https://panel.example.com", KeyInput.None)
        assertEquals(key, factory.endpoints[0].apiKey)
        assertNull(factory.endpoints[1].apiKey)
    }

    @Test
    fun testConnection_versionFailureIsTolerated_nodesFailureIsNot() = runTest {
        api.version = KomariResult.Err(KomariError.NotFound)
        api.setNodes(komariNode("a"))
        assertEquals("", (repo.testConnection("a.com", KeyInput.None) as KomariResult.Ok).value.version)
        api.failNodes(KomariError.Unauthorized(false))
        assertEquals(KomariResult.Err(KomariError.Unauthorized(false)), repo.testConnection("a.com", KeyInput.None))
    }

    @Test
    fun saveConfig_normalizesUrl_encryptsKey_andBlankRemovesAll() = runTest {
        assertEquals(KomariResult.Ok("http://10.0.2.2:8765"), repo.saveConfig(" http://10.0.2.2:8765/api/ ", KeyInput.New(key)))
        assertEquals("http://10.0.2.2:8765", settings.settings.first().komariUrl)
        assertEquals(key, secrets.getApiKey())
        assertEquals(ApiKeyStatus.SAVED, secrets.status.first())
        val stored = secretData.snapshot().asMap().values.joinToString()
        assertFalse("key must not be stored in clear text", stored.contains(key))

        assertEquals(KomariResult.Ok(null), repo.saveConfig("", KeyInput.Saved))
        assertNull(settings.settings.first().komariUrl)
        assertEquals(ApiKeyStatus.NONE, secrets.status.first())
    }

    @Test
    fun saveConfig_invalidUrl_changesNothing() = runTest {
        configure()
        assertEquals(KomariResult.Err(KomariError.InvalidUrl), repo.saveConfig("mailto:x", KeyInput.New(key)))
        assertEquals("https://panel.example.com", settings.settings.first().komariUrl)
        assertNull(secrets.getApiKey())
    }

    @Test
    fun unreadableKey_behavesAsUnset() = runTest {
        configure(apiKey = key)
        cipher.broken = true
        assertEquals(ApiKeyStatus.UNREADABLE, secrets.status.first())
        assertNull(secrets.getApiKey())
        repo.currentApi()
        assertNull(factory.endpoints.last().apiKey)
    }

    @Test
    fun currentApi_requiresConfiguration() = runTest {
        assertEquals(KomariResult.Err(KomariError.NotConfigured), repo.currentApi())
        configure(apiKey = key)
        assertTrue(repo.currentApi() is KomariResult.Ok)
        assertEquals(key, factory.endpoints.last().apiKey)
    }

    // ------------------------------------------------------------------ import

    @Test
    fun preview_statusesAndMissing() = runTest {
        configure()
        val updatedId = linkedSubscription("upd", price = 500)
        repo.importNodes(listOf(komariNode("same")))
        linkedSubscription("gone")
        api.setNodes(komariNode("new"), komariNode("same"), komariNode("upd", price = 10.0))
        val preview = (repo.loadImportPreview() as KomariResult.Ok).value
        val byUuid = preview.items.associateBy { it.node.uuid }
        assertEquals(ImportStatus.NEW, byUuid.getValue("new").status)
        assertEquals(ImportStatus.LINKED, byUuid.getValue("same").status)
        assertEquals(ImportStatus.UPDATE, byUuid.getValue("upd").status)
        assertEquals(updatedId, byUuid.getValue("upd").subscriptionId)
        assertTrue(byUuid.getValue("upd").changes.any { it is FieldChange.Price })
        assertEquals(8, byUuid.getValue("new").changes.size)
        assertEquals(listOf("Mine"), preview.missingNames)
    }

    @Test
    fun preview_notConfigured() = runTest {
        assertEquals(KomariResult.Err(KomariError.NotConfigured), repo.loadImportPreview())
    }

    @Test
    fun importNodes_createsLinkedVpsSubscriptions() = runTest {
        settings.setTrafficAlertPercent(90)
        val summary = repo.importNodes(
            listOf(
                komariNode("a", name = "HK", price = 12.5, cycle = 90, limit = 500L shl 30, limitType = "sum", group = "asia"),
                komariNode("b", price = -1.0, cycle = -1, expiredAt = Instant.parse("2226-09-28T00:00:00Z")),
            ),
        )
        assertEquals(ImportSummary(created = 2, updated = 0, unchanged = 0), summary)
        val all = subs.observeAll().first()
        val hk = all.single { it.vps?.komariUuid == "a" }
        assertEquals("HK", hk.subscription.name)
        assertEquals(Category.VPS, hk.subscription.category)
        assertEquals(1_250L, hk.subscription.priceMinor)
        assertEquals(BillingCycle.QUARTERLY, hk.subscription.cycle)
        assertEquals(LocalDate.of(2026, 10, 15), hk.subscription.nextDue)
        assertEquals(500L shl 30, hk.vps!!.trafficLimitBytes)
        assertEquals(TrafficLimitType.SUM, hk.vps!!.trafficLimitType)
        assertEquals("asia", hk.vps!!.komariGroup)
        assertEquals(90, hk.vps!!.alertPercent)
        assertEquals(LockedField.NONE, hk.vps!!.lockedFields)
        val free = all.single { it.vps?.komariUuid == "b" }.subscription
        assertTrue(free.isFree)
        assertEquals(BillingCycle.ONE_TIME, free.cycle)
        assertTrue(free.isPermanent)
    }

    @Test
    fun importNodes_twice_updatesInsteadOfDuplicating() = runTest {
        repo.importNodes(listOf(komariNode("a", price = 10.0)))
        val again = repo.importNodes(listOf(komariNode("a", price = 20.0), komariNode("a2")))
        assertEquals(ImportSummary(created = 1, updated = 1, unchanged = 0), again)
        val rows = subs.observeAll().first()
        assertEquals(2, rows.size)
        assertEquals(2_000L, rows.single { it.vps?.komariUuid == "a" }.subscription.priceMinor)
    }

    @Test
    fun importNodes_recordsTheSyncTime() = runTest {
        assertNull(settings.settings.first().komariLastSyncAt)
        repo.importNodes(listOf(komariNode("a")))
        // The settings page shows this instead of "还没有同步过".
        assertEquals(now.toEpochMilli(), settings.settings.first().komariLastSyncAt)
    }

    @Test
    fun importNodes_nothingSelected_recordsNoSyncTime() = runTest {
        repo.importNodes(emptyList())
        assertNull(settings.settings.first().komariLastSyncAt)
    }

    @Test
    fun importNodes_newName_isUsedAndLocked_andSyncKeepsIt() = runTest {
        configure()
        val node = komariNode("auto", name = "Auto-C202609280075129")
        val summary = repo.importNodes(listOf(node), names = mapOf("auto" to "  东京 CN2  "))
        assertEquals(1, summary.created)
        val row = subs.observeAll().first().single()
        assertEquals("东京 CN2", row.subscription.name)
        assertEquals(LockedField.NAME.mask, row.vps!!.lockedFields)
        assertEquals("the panel name stays the node's name", "Auto-C202609280075129", row.vps!!.komariName)

        api.setNodes(node.copy(price = 20.0))
        repo.sync()
        val synced = subs.get(row.subscription.id)!!
        assertEquals("locked name kept by sync", "东京 CN2", synced.subscription.name)
        assertEquals("other fields still sync", 2_000L, synced.subscription.priceMinor)
    }

    @Test
    fun importNodes_nameEqualToPanelOrBlank_isNotLocked() = runTest {
        repo.importNodes(
            listOf(komariNode("a", name = "HK"), komariNode("b", name = "JP")),
            names = mapOf("a" to " HK ", "b" to "   "),
        )
        val rows = subs.observeAll().first().associateBy { it.vps!!.komariUuid }
        assertEquals("HK", rows.getValue("a").subscription.name)
        assertEquals(LockedField.NONE, rows.getValue("a").vps!!.lockedFields)
        assertEquals("blank means the default name", "JP", rows.getValue("b").subscription.name)
        assertEquals(LockedField.NONE, rows.getValue("b").vps!!.lockedFields)
    }

    @Test
    fun importNodes_renamesALinkedSubscription_keepingItsLocks() = runTest {
        val id = linkedSubscription("u", locked = LockedField.PRICE.mask)
        val summary = repo.importNodes(listOf(komariNode("u", price = 99.0)), names = mapOf("u" to "新名字"))
        assertEquals(ImportSummary(created = 0, updated = 1, unchanged = 0), summary)
        val row = subs.get(id)!!
        assertEquals("新名字", row.subscription.name)
        assertEquals(LockedField.maskOf(LockedField.PRICE, LockedField.NAME), row.vps!!.lockedFields)
        assertEquals("locked price kept", 1_000L, row.subscription.priceMinor)
    }

    @Test
    fun importNodes_onlyANewName_countsAsUpdated() = runTest {
        repo.importNodes(listOf(komariNode("u")))
        val summary = repo.importNodes(listOf(komariNode("u")), names = mapOf("u" to "Renamed"))
        assertEquals(ImportSummary(created = 0, updated = 1, unchanged = 0), summary)
        assertEquals("Renamed", subs.observeAll().first().single().subscription.name)
    }

    @Test
    fun preview_importName_isTheNameAnImportGives() = runTest {
        configure()
        linkedSubscription("locked", locked = LockedField.NAME.mask)
        linkedSubscription("open")
        api.setNodes(komariNode("new", name = "Auto-r1266577"), komariNode("locked"), komariNode("open"), komariNode("blank", name = ""))
        val items = (repo.loadImportPreview() as KomariResult.Ok).value.items.associateBy { it.node.uuid }
        assertEquals("Auto-r1266577", items.getValue("new").importName)
        assertEquals("locked name stays", "Mine", items.getValue("locked").importName)
        assertEquals("unlocked name follows the panel", "Node open", items.getValue("open").importName)
        assertEquals("no panel name: the uuid", "blank", items.getValue("blank").importName)
    }

    // ------------------------------------------------------------------ sync

    @Test
    fun sync_respectsLockedFields() = runTest {
        configure()
        val id = linkedSubscription("a", locked = LockedField.maskOf(LockedField.PRICE, LockedField.NAME), price = 999)
        api.setNodes(komariNode("a", name = "Panel name", price = 50.0, cycle = 365, limit = 2L shl 40))
        val summary = (repo.sync() as KomariResult.Ok).value
        assertEquals(SyncSummary(updated = 1, unchanged = 0, missing = 0, unlinkedNodes = 0), summary)
        val row = subs.get(id)!!
        assertEquals("locked price kept", 999L, row.subscription.priceMinor)
        assertEquals("locked name kept", "Mine", row.subscription.name)
        assertEquals("unlocked cycle synced", BillingCycle.ANNUAL, row.subscription.cycle)
        assertEquals(2L shl 40, row.vps!!.trafficLimitBytes)
        assertEquals("Panel name", row.vps!!.komariName)
        assertEquals(now.toEpochMilli(), settings.settings.first().komariLastSyncAt)
    }

    @Test
    fun editInApp_thenSync_keepsTheEditedPrice() = runTest {
        configure()
        api.setNodes(komariNode("a", price = 10.0))
        repo.importNodes(listOf(komariNode("a", price = 10.0)))
        val stored = subs.observeAll().first().single()
        subs.save(stored.subscription.copy(priceMinor = 4_200), stored.vps)
        assertTrue(LockedField.PRICE.isLockedIn(subs.get(stored.subscription.id)!!.vps!!.lockedFields))
        api.setNodes(komariNode("a", price = 11.0))
        repo.sync()
        assertEquals(4_200L, subs.get(stored.subscription.id)!!.subscription.priceMinor)
    }

    @Test
    fun unlock_thenSync_takesPanelValue() = runTest {
        configure()
        repo.importNodes(listOf(komariNode("a", price = 10.0)))
        val stored = subs.observeAll().first().single()
        subs.save(stored.subscription.copy(priceMinor = 4_200), stored.vps)
        val edited = subs.get(stored.subscription.id)!!
        subs.save(edited.subscription, edited.vps, unlocked = LockedField.PRICE.mask)
        assertFalse(LockedField.PRICE.isLockedIn(subs.get(stored.subscription.id)!!.vps!!.lockedFields))
        api.setNodes(komariNode("a", price = 11.0))
        repo.sync()
        assertEquals(1_100L, subs.get(stored.subscription.id)!!.subscription.priceMinor)
    }

    @Test
    fun sync_marksMissingNodes_neverDeletes_andClearsOnReturn() = runTest {
        configure()
        val id = linkedSubscription("gone")
        api.setNodes(komariNode("other"))
        val summary = (repo.sync() as KomariResult.Ok).value
        assertEquals(1, summary.missing)
        assertEquals(1, summary.unlinkedNodes)
        val row = subs.get(id)!!
        assertTrue(row.vps!!.komariMissing)
        assertEquals("gone", row.vps!!.komariUuid)
        assertEquals("no subscription is created by sync", 1, subs.observeAll().first().size)

        api.setNodes(komariNode("gone"))
        repo.sync()
        assertFalse(subs.get(id)!!.vps!!.komariMissing)
    }

    @Test
    fun sync_failure_changesNothing() = runTest {
        configure()
        val id = linkedSubscription("a")
        val before = tables.vps.value.getValue(id)
        api.failNodes(KomariError.Network(KomariError.NetworkKind.TIMEOUT))
        assertEquals(KomariResult.Err(KomariError.Network(KomariError.NetworkKind.TIMEOUT)), repo.sync())
        assertEquals(before, tables.vps.value.getValue(id))
        assertNull(settings.settings.first().komariLastSyncAt)
    }

    @Test
    fun pickerNodes_showOwner() = runTest {
        configure()
        val id = linkedSubscription("a")
        api.setNodes(komariNode("a"), komariNode("b"))
        val nodes = (repo.loadPickerNodes() as KomariResult.Ok).value
        assertEquals(id, nodes.single { it.node.uuid == "a" }.linkedSubscriptionId)
        assertEquals("Mine", nodes.single { it.node.uuid == "a" }.linkedSubscriptionName)
        assertNull(nodes.single { it.node.uuid == "b" }.linkedSubscriptionId)
    }
}
