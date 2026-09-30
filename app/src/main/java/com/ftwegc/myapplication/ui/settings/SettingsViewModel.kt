package com.ftwegc.myapplication.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ftwegc.myapplication.data.backup.BackupCodec
import com.ftwegc.myapplication.data.backup.BackupRepository
import com.ftwegc.myapplication.data.backup.ImportOutcome
import com.ftwegc.myapplication.data.backup.ImportPlan
import com.ftwegc.myapplication.data.prefs.AppSettings
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.prefs.ThemeMode
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.data.repo.KomariRepository
import com.ftwegc.myapplication.data.repo.SyncSummary
import com.ftwegc.myapplication.data.repo.TrafficRepository
import com.ftwegc.myapplication.work.WorkScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class SettingsUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val versionName: String = "",
    val isSyncing: Boolean = false,
    /** Export or import running. */
    val isTransferring: Boolean = false,
    /** A read export waiting for the user's confirmation. */
    val pendingImport: ImportPlan? = null,
)

sealed interface SettingsEvent {
    data class Synced(val summary: SyncSummary) : SettingsEvent
    data class SyncFailed(val error: KomariError) : SettingsEvent
    data class Exported(val count: Int) : SettingsEvent
    data object ExportFailed : SettingsEvent
    data class Imported(val outcome: ImportOutcome) : SettingsEvent

    /** The chosen file could not be imported; [reason] picks the message. */
    data class ImportFailed(val reason: ImportFailure) : SettingsEvent
    data object DebugTriggered : SettingsEvent
}

enum class ImportFailure { UNREADABLE, NOT_BACKUP, NEWER_VERSION, TOO_LARGE }

/** File too large for an export. */
class FileTooLargeException : IllegalStateException("file too large")

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val versionName: String,
    private val komariRepository: KomariRepository? = null,
    private val trafficRepository: TrafficRepository? = null,
    private val backupRepository: BackupRepository? = null,
    private val workScheduler: WorkScheduler? = null,
) : ViewModel() {

    private val syncing = MutableStateFlow(false)
    private val transferring = MutableStateFlow(false)
    private val pendingImport = MutableStateFlow<ImportPlan?>(null)
    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsStore.settings,
        syncing,
        transferring,
        pendingImport,
    ) { settings, isSyncing, isTransferring, pending ->
        SettingsUiState(
            isLoading = false,
            settings = settings,
            versionName = versionName,
            isSyncing = isSyncing,
            isTransferring = isTransferring,
            pendingImport = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(versionName = versionName))

    fun setShowArchived(show: Boolean) {
        viewModelScope.launch { settingsStore.setShowArchived(show) }
    }

    fun setDefaultCurrency(currency: String) {
        viewModelScope.launch { settingsStore.setDefaultCurrency(currency) }
    }

    // ------------------------------------------------------------------ Appearance

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setReduceTransparency(reduce: Boolean) {
        viewModelScope.launch { settingsStore.setReduceTransparency(reduce) }
    }

    fun setBackgroundAnimation(animate: Boolean) {
        viewModelScope.launch { settingsStore.setBackgroundAnimation(animate) }
    }

    /** Stores the interval and updates the periodic work right away (0 turns it off). */
    fun setRefreshInterval(minutes: Int) {
        viewModelScope.launch {
            settingsStore.setRefreshIntervalMinutes(minutes)
            workScheduler?.scheduleRefresh(minutes, update = true)
        }
    }

    fun setReminderDays(days: List<Int>) {
        viewModelScope.launch { settingsStore.setReminderDays(days) }
    }

    fun setTrafficAlertPercent(percent: Int) {
        viewModelScope.launch { settingsStore.setTrafficAlertPercent(percent) }
    }

    fun setResetZone(zoneId: String?) {
        viewModelScope.launch { settingsStore.setResetZoneId(zoneId) }
    }

    /** The system notification dialog was shown (from the settings page). */
    fun setNotificationPermissionRequested() {
        viewModelScope.launch {
            settingsStore.setNotificationPermissionRequested(true)
            settingsStore.setNotificationPromptShown(true)
        }
    }

    /** Manual sync: node data (prices, due dates, limits) first, then traffic. */
    fun sync() {
        val komari = komariRepository ?: return
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            when (val result = komari.sync()) {
                is KomariResult.Ok -> {
                    trafficRepository?.refreshAll()
                    _events.send(SettingsEvent.Synced(result.value))
                }
                is KomariResult.Err -> _events.send(SettingsEvent.SyncFailed(result.error))
            }
            syncing.value = false
        }
    }

    // ------------------------------------------------------------------ Export / import

    /** Writes every subscription with [write] (a document the user picked). */
    fun export(write: suspend (ByteArray) -> Unit) {
        val backup = backupRepository ?: return
        if (transferring.value) return
        viewModelScope.launch {
            transferring.value = true
            try {
                val (text, count) = backup.export()
                withContext(Dispatchers.IO) { write(text.toByteArray(Charsets.UTF_8)) }
                _events.send(SettingsEvent.Exported(count))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(SettingsEvent.ExportFailed)
            } finally {
                transferring.value = false
            }
        }
    }

    /** Reads an export with [read] and waits for [confirmImport]. */
    fun prepareImport(read: suspend () -> String) {
        val backup = backupRepository ?: return
        if (transferring.value) return
        viewModelScope.launch {
            transferring.value = true
            try {
                val text = withContext(Dispatchers.IO) { read() }
                backup.plan(text)
                    .onSuccess { pendingImport.value = it }
                    .onFailure { e ->
                        _events.send(
                            SettingsEvent.ImportFailed(
                                when (e) {
                                    is BackupRepository.NewerVersionException -> ImportFailure.NEWER_VERSION
                                    else -> ImportFailure.NOT_BACKUP
                                },
                            ),
                        )
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FileTooLargeException) {
                _events.send(SettingsEvent.ImportFailed(ImportFailure.TOO_LARGE))
            } catch (_: Exception) {
                _events.send(SettingsEvent.ImportFailed(ImportFailure.UNREADABLE))
            } finally {
                transferring.value = false
            }
        }
    }

    fun confirmImport() {
        val backup = backupRepository ?: return
        val plan = pendingImport.value ?: return
        pendingImport.value = null
        viewModelScope.launch {
            transferring.value = true
            runCatching { backup.import(plan) }
                .onSuccess { _events.send(SettingsEvent.Imported(it)) }
                .onFailure { _events.send(SettingsEvent.ImportFailed(ImportFailure.UNREADABLE)) }
            transferring.value = false
        }
    }

    fun cancelImport() {
        pendingImport.value = null
    }

    // ------------------------------------------------------------------ Debug builds only

    fun debugRunReminders() {
        workScheduler?.runRemindersNow()
        viewModelScope.launch { _events.send(SettingsEvent.DebugTriggered) }
    }

    fun debugRunRefresh() {
        workScheduler?.runRefreshNow()
        viewModelScope.launch { _events.send(SettingsEvent.DebugTriggered) }
    }

    companion object {
        /** Largest import file read. */
        const val MAX_IMPORT_BYTES: Int = BackupCodec.MAX_BYTES
    }
}
