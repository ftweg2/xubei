package com.ftwegc.myapplication.ui.komari

import com.ftwegc.myapplication.data.prefs.SecretStore
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.data.repo.DirectTransactionRunner
import com.ftwegc.myapplication.data.repo.FakeCipher
import com.ftwegc.myapplication.data.repo.FakeKomariApi
import com.ftwegc.myapplication.data.repo.FakePreferencesDataStore
import com.ftwegc.myapplication.data.repo.FakeSubscriptionDao
import com.ftwegc.myapplication.data.repo.FakeTables
import com.ftwegc.myapplication.data.repo.FakeVpsDao
import com.ftwegc.myapplication.data.repo.KeyInput
import com.ftwegc.myapplication.data.repo.KomariRepository
import com.ftwegc.myapplication.data.repo.RecordingFactory
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.data.repo.TrafficRepository
import com.ftwegc.myapplication.data.repo.komariNode
import com.ftwegc.myapplication.domain.model.LockedField
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionFormValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** "导入后的名称" on the import page: editing, selection and what the import stores. */
@OptIn(ExperimentalCoroutinesApi::class)
class KomariImportNameTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-28T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
    private val tables = FakeTables()
    private val subDao = FakeSubscriptionDao(tables)
    private val vpsDao = FakeVpsDao(tables)
    private val settings = SettingsStore(FakePreferencesDataStore())
    private val secrets = SecretStore(FakePreferencesDataStore(), FakeCipher(), Dispatchers.Unconfined)
    private val api = FakeKomariApi()
    private val repo = KomariRepository(settings, secrets, RecordingFactory(api), subDao, vpsDao, DirectTransactionRunner(), clock)
    private val subs = SubscriptionRepository(subDao, vpsDao, DirectTransactionRunner(), clock)
    private val traffic = TrafficRepository({ KomariResult.Err(KomariError.NotConfigured) }, vpsDao, clock)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun typedName_selectsTheNode_isCut_andIsImportedLocked() = runTest {
        repo.saveConfig("https://panel.example.com", KeyInput.None)
        api.setNodes(komariNode("auto", name = "Auto-C202609280075129"), komariNode("hk", name = "HK"))
        val vm = KomariImportViewModel(repo, traffic, backgroundScope)
        advanceUntilIdle()
        val item = vm.uiState.value.preview!!.items.first { it.node.uuid == "auto" }
        assertEquals("Auto-C202609280075129", vm.uiState.value.nameFieldText(item))
        assertNull(vm.uiState.value.customName(item))

        vm.toggle("auto") // deselect, typing selects again
        assertFalse("auto" in vm.uiState.value.selected)
        vm.setName("auto", "东京 CN2 GIA 很长很长的名字".repeat(3))
        assertTrue("auto" in vm.uiState.value.selected)
        assertEquals(SubscriptionFormValidator.NAME_MAX, vm.uiState.value.nameFieldText(item).length)

        vm.setName("auto", "东京 CN2")
        assertEquals("东京 CN2", vm.uiState.value.customName(item))
        vm.import()
        advanceUntilIdle()

        val rows = subs.observeAll().first().associateBy { it.vps!!.komariUuid }
        assertEquals("东京 CN2", rows.getValue("auto").subscription.name)
        assertEquals(LockedField.NAME.mask, rows.getValue("auto").vps!!.lockedFields)
        assertEquals("untouched node keeps the panel name", "HK", rows.getValue("hk").subscription.name)
        assertEquals(LockedField.NONE, rows.getValue("hk").vps!!.lockedFields)
        // An import is a sync: the settings page no longer says "还没有同步过".
        assertEquals(clock.millis(), settings.settings.first().komariLastSyncAt)
    }

    @Test
    fun clearedOrUnchangedName_isNoCustomName() = runTest {
        repo.saveConfig("https://panel.example.com", KeyInput.None)
        api.setNodes(komariNode("a", name = "HK"))
        val vm = KomariImportViewModel(repo, traffic, backgroundScope)
        advanceUntilIdle()
        val item = vm.uiState.value.preview!!.items.single()
        vm.setName("a", "")
        assertEquals("", vm.uiState.value.nameFieldText(item))
        assertNull(vm.uiState.value.customName(item))
        vm.setName("a", " HK ")
        assertNull(vm.uiState.value.customName(item))
    }
}
