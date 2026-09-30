package com.ftwegc.myapplication

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ftwegc.myapplication.data.backup.BackupRepository
import com.ftwegc.myapplication.data.local.AppDatabase
import com.ftwegc.myapplication.data.local.DatabaseOpener
import com.ftwegc.myapplication.data.local.LegacyDatabaseNotice
import com.ftwegc.myapplication.data.prefs.KeystoreCipher
import com.ftwegc.myapplication.data.prefs.SecretStore
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.remote.komari.KomariApiFactory
import com.ftwegc.myapplication.data.remote.komari.OkHttpKomariApi
import com.ftwegc.myapplication.data.repo.KomariRepository
import com.ftwegc.myapplication.data.repo.NodeLiveRepository
import com.ftwegc.myapplication.data.repo.RoomTransactionRunner
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.data.repo.TrafficRepository
import com.ftwegc.myapplication.notify.Notifier
import com.ftwegc.myapplication.notify.ReminderNotifier
import com.ftwegc.myapplication.notify.ReminderService
import com.ftwegc.myapplication.work.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.time.Clock
import java.time.DateTimeException
import java.time.ZoneId

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Separate file for the encrypted API key; excluded from backups (see res/xml/backup_rules.xml). */
private val Context.secretDataStore: DataStore<Preferences> by preferencesDataStore(name = SecretStore.FILE_NAME)

/**
 * Hand-written dependency container, created once by [VluerApp].
 *
 * @param clock source of "now"/"today" for everything that depends on the date; replace it in
 *   tests to pin the date.
 * @param databaseOverride / [settingsDataStoreOverride] / [komariApiFactoryOverride] /
 *   [notifierOverride] replace the real parts in instrumentation tests.
 */
class AppContainer(
    context: Context,
    val clock: Clock = Clock.systemDefaultZone(),
    private val databaseOverride: AppDatabase? = null,
    private val settingsDataStoreOverride: DataStore<Preferences>? = null,
    private val komariApiFactoryOverride: KomariApiFactory? = null,
    private val notifierOverride: ReminderNotifier? = null,
) {
    private val appContext = context.applicationContext

    /** "An incompatible database was kept aside" not yet shown to the user (only for the real database). */
    private val legacyNotice = LegacyDatabaseNotice(appContext)
    private val pendingLegacyNotice = MutableStateFlow(if (databaseOverride == null) legacyNotice.pending() else null)

    /**
     * Name of the database file of an earlier, incompatible build that was kept aside (see
     * [DatabaseOpener]) while the user has not seen the notice yet; `null` otherwise.
     */
    val legacyDatabaseNotice: StateFlow<String?> = pendingLegacyNotice.asStateFlow()

    /** The user has seen the notice: it is not shown again. */
    fun dismissLegacyDatabaseNotice() {
        legacyNotice.dismiss()
        pendingLegacyNotice.value = null
    }

    val database: AppDatabase by lazy {
        databaseOverride ?: DatabaseOpener(appContext, clock = clock).open().let { opened ->
            opened.keptAs?.let { keptAs ->
                legacyNotice.record(keptAs)
                pendingLegacyNotice.value = keptAs
            }
            opened.database
        }
    }

    private val transactions by lazy { RoomTransactionRunner(database) }

    val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepository(
            subscriptionDao = database.subscriptionDao(),
            vpsDao = database.vpsDao(),
            transactions = transactions,
            clock = clock,
            trafficDao = database.trafficDao(),
        )
    }

    val settingsStore: SettingsStore by lazy { SettingsStore(settingsDataStoreOverride ?: appContext.settingsDataStore) }

    val secretStore: SecretStore by lazy { SecretStore(appContext.secretDataStore, KeystoreCipher()) }

    /** One HTTP client for the whole app (connection pool, dispatcher). No logging interceptor. */
    private val httpClient: OkHttpClient by lazy { OkHttpKomariApi.defaultClient() }

    private val komariApiFactory: KomariApiFactory by lazy {
        komariApiFactoryOverride ?: OkHttpKomariApi.factory(httpClient) { clock.zone }
    }

    val komariRepository: KomariRepository by lazy {
        KomariRepository(
            settingsStore = settingsStore,
            secretStore = secretStore,
            apiFactory = komariApiFactory,
            subscriptionDao = database.subscriptionDao(),
            vpsDao = database.vpsDao(),
            transactions = transactions,
            clock = clock,
        )
    }

    /** The reset-day zone from the settings, else the clock's zone. */
    private val resetZone: suspend () -> ZoneId = {
        settingsStore.settings.first().resetZoneId?.let { id ->
            try {
                ZoneId.of(id)
            } catch (_: DateTimeException) {
                null
            }
        } ?: clock.zone
    }

    val trafficRepository: TrafficRepository by lazy {
        TrafficRepository(
            apiProvider = { komariRepository.currentApi() },
            vpsDao = database.vpsDao(),
            clock = clock,
            trafficDao = database.trafficDao(),
            transactions = transactions,
            resetZone = resetZone,
        )
    }

    /** Live node samples for the charts (nothing is stored). */
    val nodeLiveRepository: NodeLiveRepository by lazy {
        NodeLiveRepository(apiProvider = { komariRepository.currentApi() })
    }

    val notifier: Notifier by lazy { Notifier(appContext) }

    val reminderService: ReminderService by lazy {
        ReminderService(
            subscriptions = subscriptionRepository,
            settingsStore = settingsStore,
            log = database.reminderLogDao(),
            notifier = notifierOverride ?: notifier,
            clock = clock,
            resetZone = resetZone,
        )
    }

    val workScheduler: WorkScheduler by lazy { WorkScheduler(appContext) }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(
            subscriptionDao = database.subscriptionDao(),
            vpsDao = database.vpsDao(),
            transactions = transactions,
            clock = clock,
        )
    }
}
