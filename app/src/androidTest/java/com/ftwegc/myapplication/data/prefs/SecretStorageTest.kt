package com.ftwegc.myapplication.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.GeneralSecurityException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SecretStorageTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alias = "vluer_test_${UUID.randomUUID()}"
    private val cipher = KeystoreCipher(alias)
    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    /** A made-up key used only by this test. */
    private val key = "instrumented-test-key-7c41e0"

    @Before
    fun setUp() {
        file = File(context.cacheDir, "secret_test_${UUID.randomUUID()}.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
        runCatching { cipher.deleteKey() }
    }

    @Test
    fun cipher_roundTrip_withFreshIvEachTime() {
        val plain = key.toByteArray()
        val a = cipher.encrypt(plain)
        val b = cipher.encrypt(plain)
        assertFalse("ciphertext must differ (random IV)", a.contentEquals(b))
        assertArrayEquals(plain, cipher.decrypt(a))
        assertArrayEquals(plain, cipher.decrypt(b))
        assertFalse(String(a, Charsets.ISO_8859_1).contains(key))
    }

    @Test
    fun cipher_rejectsTamperedOrForeignData() {
        val sealed = cipher.encrypt(key.toByteArray())
        val tampered = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        expectFailure { cipher.decrypt(tampered) }
        expectFailure { cipher.decrypt(byteArrayOf(9, 9, 9)) }
        expectFailure { KeystoreCipher("vluer_other_${UUID.randomUUID()}").decrypt(sealed) }
    }

    @Test
    fun cipher_deletedKey_cannotDecrypt() {
        val sealed = cipher.encrypt(key.toByteArray())
        cipher.deleteKey()
        expectFailure { cipher.decrypt(sealed) }
    }

    @Test
    fun store_setGetClear_andFileHasNoPlainKey() = runBlocking {
        val store = SecretStore(dataStore, cipher)
        assertEquals(ApiKeyStatus.NONE, store.status.first())
        assertNull(store.getApiKey())

        assertTrue(store.setApiKey("  $key  "))
        assertEquals(key, store.getApiKey())
        assertEquals(ApiKeyStatus.SAVED, store.status.first())
        val bytes = file.readBytes()
        assertFalse("plain key on disk", String(bytes, Charsets.ISO_8859_1).contains(key))

        store.clear()
        assertNull(store.getApiKey())
        assertEquals(ApiKeyStatus.NONE, store.status.first())
    }

    @Test
    fun store_blankKeyClears() = runBlocking {
        val store = SecretStore(dataStore, cipher)
        store.setApiKey(key)
        assertTrue(store.setApiKey("   "))
        assertEquals(ApiKeyStatus.NONE, store.status.first())
    }

    @Test
    fun store_undecryptable_isUnreadable_thenRecoversOnNewKey() = runBlocking {
        val store = SecretStore(dataStore, cipher)
        store.setApiKey(key)
        cipher.deleteKey() // as after restoring a backup on another device
        assertEquals(ApiKeyStatus.UNREADABLE, store.status.first())
        assertNull(store.getApiKey())

        assertTrue(store.setApiKey("$key-new"))
        assertEquals("$key-new", store.getApiKey())
        assertEquals(ApiKeyStatus.SAVED, store.status.first())
    }

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
            fail("expected a GeneralSecurityException")
        } catch (_: GeneralSecurityException) {
        }
    }
}
