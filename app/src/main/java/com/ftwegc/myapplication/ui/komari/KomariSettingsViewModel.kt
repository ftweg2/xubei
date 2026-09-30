package com.ftwegc.myapplication.ui.komari

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ftwegc.myapplication.data.prefs.ApiKeyStatus
import com.ftwegc.myapplication.data.prefs.SecretStore
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.data.remote.komari.KomariUrl
import com.ftwegc.myapplication.data.repo.KeyInput
import com.ftwegc.myapplication.data.repo.KomariConnectionInfo
import com.ftwegc.myapplication.data.repo.KomariRepository
import com.ftwegc.myapplication.data.repo.SyncSummary
import com.ftwegc.myapplication.data.repo.TrafficRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the connection test card. */
sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Testing : ConnectionTest
    data class Success(val info: KomariConnectionInfo, val withKey: Boolean) : ConnectionTest
    data class Failure(val error: KomariError) : ConnectionTest
}

/**
 * Komari settings page state. The typed API key lives only in memory ([keyText]) and is never
 * part of `toString()` output.
 */
@Immutable
data class KomariSettingsUiState(
    val isLoading: Boolean = true,
    val url: String = "",
    val savedUrl: String? = null,
    val keyStatus: ApiKeyStatus = ApiKeyStatus.NONE,
    /** The key editor is open (no saved key, replacing, or the saved key is unreadable). */
    val editingKey: Boolean = true,
    val keyText: String = "",
    /** The saved key will be removed on save. */
    val clearKeyPending: Boolean = false,
    val test: ConnectionTest = ConnectionTest.Idle,
    val urlInvalid: Boolean = false,
    val isSaving: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncAt: Long? = null,
) {
    /** A key would be sent with the current inputs. */
    val keyInUse: Boolean
        get() = (editingKey && keyText.isNotBlank()) || (!editingKey && keyStatus == ApiKeyStatus.SAVED && !clearKeyPending)

    /** Plain HTTP with an API key: show the cleartext warning. */
    val showCleartextWarning: Boolean
        get() = keyInUse && (KomariUrl.isCleartext(KomariUrl.normalize(url)) || url.trim().startsWith("http://", ignoreCase = true))

    val isDirty: Boolean
        get() = normalizedOrRaw() != (savedUrl ?: "") || (editingKey && keyText.isNotBlank()) || clearKeyPending

    val isConfigured: Boolean get() = savedUrl != null

    private fun normalizedOrRaw(): String = if (url.isBlank()) "" else KomariUrl.normalize(url) ?: url.trim()

    override fun toString(): String =
        "KomariSettingsUiState(url=$url, savedUrl=$savedUrl, keyStatus=$keyStatus, editingKey=$editingKey, " +
            "keyTyped=${keyText.isNotEmpty()}, clearKeyPending=$clearKeyPending, test=$test)"
}

sealed interface KomariSettingsEvent {
    data class Saved(val removed: Boolean) : KomariSettingsEvent
    data class SaveFailed(val error: KomariError) : KomariSettingsEvent
    data class Synced(val summary: SyncSummary) : KomariSettingsEvent
    data class SyncFailed(val error: KomariError) : KomariSettingsEvent
}

class KomariSettingsViewModel(
    private val repository: KomariRepository,
    private val trafficRepository: TrafficRepository,
    settingsStore: SettingsStore,
    secretStore: SecretStore,
) : ViewModel() {

    private val _state = MutableStateFlow(KomariSettingsUiState())
    val uiState: StateFlow<KomariSettingsUiState> = _state.asStateFlow()

    private val _events = Channel<KomariSettingsEvent>(Channel.BUFFERED)
    val events: Flow<KomariSettingsEvent> = _events.receiveAsFlow()

    private var testJob: Job? = null

    init {
        viewModelScope.launch {
            combine(settingsStore.settings, secretStore.status) { settings, status -> settings to status }
                .collect { (settings, status) ->
                    _state.update { s ->
                        val first = s.isLoading
                        s.copy(
                            isLoading = false,
                            url = if (first) settings.komariUrl.orEmpty() else s.url,
                            savedUrl = settings.komariUrl,
                            keyStatus = status,
                            editingKey = if (first || !s.editingKey) status != ApiKeyStatus.SAVED else s.editingKey,
                            lastSyncAt = settings.komariLastSyncAt,
                        )
                    }
                }
        }
    }

    fun setUrl(value: String) {
        val url = value.take(MAX_URL_LENGTH)
        // The keyboard re-sends the same text when focus changes; that must not reset a running test.
        if (url == _state.value.url) return
        cancelRunningTest()
        _state.update { it.copy(url = url, urlInvalid = false).resetTest() }
    }

    fun setKeyText(value: String) {
        val key = value.filterNot { c -> c == '\n' || c == '\r' }.take(MAX_KEY_LENGTH)
        if (key == _state.value.keyText) return
        cancelRunningTest()
        _state.update { it.copy(keyText = key).resetTest() }
    }

    /** Opens the editor to type a new key; the saved one stays until save. */
    fun startReplaceKey() {
        cancelRunningTest()
        _state.update { it.copy(editingKey = true, keyText = "", clearKeyPending = false).resetTest() }
    }

    /** Closes the editor and keeps the saved key. */
    fun cancelReplaceKey() {
        cancelRunningTest()
        _state.update { it.copy(editingKey = it.keyStatus != ApiKeyStatus.SAVED, keyText = "").resetTest() }
    }

    fun clearKey() {
        cancelRunningTest()
        _state.update { it.copy(clearKeyPending = true, editingKey = false, keyText = "").resetTest() }
    }

    fun undoClearKey() {
        cancelRunningTest()
        _state.update { it.copy(clearKeyPending = false).resetTest() }
    }

    fun testConnection() {
        val s = _state.value
        if (s.test == ConnectionTest.Testing) return
        if (s.url.isNotBlank() && KomariUrl.normalize(s.url) == null) {
            _state.update { it.copy(urlInvalid = true, test = ConnectionTest.Failure(KomariError.InvalidUrl)) }
            return
        }
        val key = keyInput(s)
        testJob = viewModelScope.launch {
            _state.update { it.copy(test = ConnectionTest.Testing) }
            val started = System.currentTimeMillis()
            val result = repository.testConnection(s.url, key)
            // Let the "connecting" animation read as a state even on a fast LAN.
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < MIN_TEST_MILLIS) delay(MIN_TEST_MILLIS - elapsed)
            _state.update {
                it.copy(
                    test = when (result) {
                        is KomariResult.Ok -> ConnectionTest.Success(result.value, withKey = key !is KeyInput.None)
                        is KomariResult.Err -> ConnectionTest.Failure(result.error)
                    },
                    urlInvalid = (result as? KomariResult.Err)?.error == KomariError.InvalidUrl,
                )
            }
        }
    }

    fun save() {
        val s = _state.value
        if (s.isSaving) return
        val input = keyInput(s)
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (val result = repository.saveConfig(s.url, input)) {
                is KomariResult.Ok -> {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            url = result.value.orEmpty(),
                            keyText = "",
                            clearKeyPending = false,
                            // Guest mode (no key stored) keeps the editor open; a stored key shows "已保存".
                            editingKey = input is KeyInput.None,
                            keyStatus = if (input is KeyInput.None) ApiKeyStatus.NONE else it.keyStatus,
                        )
                    }
                    _events.send(KomariSettingsEvent.Saved(removed = result.value == null))
                }
                is KomariResult.Err -> {
                    _state.update { it.copy(isSaving = false, urlInvalid = result.error == KomariError.InvalidUrl) }
                    _events.send(KomariSettingsEvent.SaveFailed(result.error))
                }
            }
        }
    }

    /** Syncs node data, then refreshes traffic. */
    fun sync() {
        if (_state.value.isSyncing) return
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true) }
            when (val result = repository.sync()) {
                is KomariResult.Ok -> {
                    trafficRepository.refreshAll()
                    _events.send(KomariSettingsEvent.Synced(result.value))
                }
                is KomariResult.Err -> _events.send(KomariSettingsEvent.SyncFailed(result.error))
            }
            _state.update { it.copy(isSyncing = false) }
        }
    }

    private fun keyInput(s: KomariSettingsUiState): KeyInput = when {
        s.editingKey && s.keyText.isNotBlank() -> KeyInput.New(s.keyText.trim())
        s.clearKeyPending -> KeyInput.None
        !s.editingKey && s.keyStatus == ApiKeyStatus.SAVED -> KeyInput.Saved
        else -> KeyInput.None
    }

    private fun KomariSettingsUiState.resetTest(): KomariSettingsUiState = copy(test = ConnectionTest.Idle)

    /** Cancels a running test after an input changed (never inside a state update lambda). */
    private fun cancelRunningTest() {
        testJob?.cancel()
        testJob = null
    }

    companion object {
        private const val MAX_URL_LENGTH = 300
        private const val MAX_KEY_LENGTH = 256
        private const val MIN_TEST_MILLIS = 700L
    }
}
