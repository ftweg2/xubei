package com.ftwegc.myapplication.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Base64

/** Whether an API key is stored. */
enum class ApiKeyStatus {
    /** Nothing stored (guest mode). */
    NONE,

    /** A key is stored and decrypts. */
    SAVED,

    /** Ciphertext is stored but cannot be decrypted (key lost, restored backup): ask again. */
    UNREADABLE,
}

/**
 * The Komari API key, encrypted with [cipher] and kept in its own DataStore file
 * ([FILE_NAME], excluded from backups). The plain key only exists in memory while a request is
 * built; it is never logged and never exposed as a Flow.
 */
class SecretStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    val status: Flow<ApiKeyStatus> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[KEY]?.let { if (decryptOrNull(it) != null) ApiKeyStatus.SAVED else ApiKeyStatus.UNREADABLE } ?: ApiKeyStatus.NONE }
        .distinctUntilChanged()
        .flowOn(dispatcher)

    /** The stored key, or `null` when none is stored or it cannot be decrypted. */
    suspend fun getApiKey(): String? = withContext(dispatcher) {
        val stored = try {
            dataStore.data.first()[KEY]
        } catch (_: IOException) {
            null
        } ?: return@withContext null
        decryptOrNull(stored)
    }

    /** Encrypts and stores [key]; blank clears it. Returns false when encryption failed. */
    suspend fun setApiKey(key: String): Boolean = withContext(dispatcher) {
        val value = key.trim()
        if (value.isEmpty()) {
            clear()
            return@withContext true
        }
        val sealed = try {
            Base64.getEncoder().encodeToString(cipher.encrypt(value.toByteArray(Charsets.UTF_8)))
        } catch (_: Exception) {
            // Keystore failures (GeneralSecurityException, ProviderException…): never log, report.
            return@withContext false
        }
        dataStore.edit { it[KEY] = sealed }
        true
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private fun decryptOrNull(stored: String): String? = try {
        String(cipher.decrypt(Base64.getDecoder().decode(stored)), Charsets.UTF_8).takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    companion object {
        /** DataStore name; the file is `datastore/komari_secret.preferences_pb`. */
        const val FILE_NAME: String = "komari_secret"
        private val KEY = stringPreferencesKey("api_key_v1")
    }
}
