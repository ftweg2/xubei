package com.ftwegc.myapplication.work

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.ftwegc.myapplication.AppContainer
import com.ftwegc.myapplication.VluerApp
import com.ftwegc.myapplication.data.local.AppDatabase
import com.ftwegc.myapplication.data.remote.komari.KomariApi
import com.ftwegc.myapplication.data.remote.komari.KomariApiFactory
import com.ftwegc.myapplication.data.remote.komari.KomariLatest
import com.ftwegc.myapplication.data.remote.komari.KomariNodeList
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.data.remote.komari.KomariVersion
import com.ftwegc.myapplication.data.repo.KomariLinkChange
import com.ftwegc.myapplication.domain.calc.Reminder
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.notify.Notifier
import com.ftwegc.myapplication.notify.ReminderNotifier
import com.ftwegc.myapplication.domain.model.TrafficPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Runs the real workers (TestListenableWorkerBuilder) against a test [AppContainer]. */
@RunWith(AndroidJUnit4::class)
class WorkerTest {

    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private class RecordingNotifier : ReminderNotifier {
        val sent = mutableListOf<Reminder>()
        override fun canNotify(): Boolean = true
        override fun notify(reminder: Reminder) {
            sent += reminder
        }
    }

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as VluerApp
    private lateinit var original: AppContainer
    private lateinit var db: AppDatabase
    private lateinit var container: AppContainer
    private val notifier = RecordingNotifier()
    private val gib = 1L shl 30
    private var reading: KomariLatest? = null

    @Before
    fun setUp() {
        original = app.container
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val api = object : KomariApi {
            override suspend fun getVersion() = KomariResult.Ok(KomariVersion("1.5.1", ""))
            override suspend fun getNodes() = KomariResult.Ok(KomariNodeList(emptyList()))
            override suspend fun getLatest(uuid: String): KomariResult<KomariLatest?> = KomariResult.Ok(reading)
        }
        container = AppContainer(
            context = context,
            clock = Clock.fixed(Instant.parse("2026-09-28T01:00:00Z"), ZoneId.of("Asia/Shanghai")),
            databaseOverride = db,
            settingsDataStoreOverride = MemoryDataStore(),
            komariApiFactoryOverride = KomariApiFactory { api },
            notifierOverride = notifier,
        )
        app.replaceContainer(container)
    }

    @After
    fun tearDown() {
        app.replaceContainer(original)
        db.close()
    }

    @Test
    fun reminderWorker_rollsAutoRenewalsForward_andNotifiesOnce() = runBlocking {
        val subs = container.subscriptionRepository
        val due = subs.save(
            Subscription(name = "ChatGPT", category = Category.AI, priceMinor = 2_000, currency = "USD", cycle = BillingCycle.MONTHLY, nextDue = LocalDate.of(2026, 9, 29)),
            null,
        )
        val renewing = subs.save(
            Subscription(
                name = "iCloud", category = Category.OTHER, priceMinor = 600, currency = "CNY", cycle = BillingCycle.MONTHLY,
                nextDue = LocalDate.of(2026, 8, 10), anchorDay = 10, autoRenew = true,
            ),
            null,
        )
        subs.save(Subscription(name = "待补充", category = Category.VPS, priceMinor = 0, currency = "CNY", cycle = BillingCycle.MONTHLY), null)

        val worker = TestListenableWorkerBuilder<ReminderWorker>(app).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(LocalDate.of(2026, 10, 10), subs.get(renewing)!!.subscription.nextDue)
        assertEquals(listOf(due), notifier.sent.map { it.subscriptionId })
        assertEquals(1, (notifier.sent.single() as Reminder.Due).threshold)

        assertEquals(ListenableWorker.Result.success(), TestListenableWorkerBuilder<ReminderWorker>(app).build().doWork())
        assertEquals("the same reminder is never sent twice", 1, notifier.sent.size)
        assertTrue(db.reminderLogDao().exists(due, "due:2026-09-29:d1"))
    }

    @Test
    fun refreshWorker_readsTrafficAndSendsThresholdReminder() = runBlocking {
        val worker = { TestListenableWorkerBuilder<RefreshWorker>(app).build() }
        // Not configured: nothing to do, still a success.
        assertEquals(ListenableWorker.Result.success(), worker().doWork())

        container.settingsStore.setKomariUrl("https://panel.example.test")
        val id = container.subscriptionRepository.save(
            Subscription(name = "VPS", category = Category.VPS, priceMinor = 100, currency = "CNY", cycle = BillingCycle.MONTHLY),
            VpsConfig(trafficLimitBytes = 100 * gib, alertPercent = 80),
            link = KomariLinkChange.Link("n1", "n1", null),
        )
        reading = KomariLatest(10 * gib, 85 * gib, 3_600, Instant.parse("2026-09-28T00:59:50Z"))
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(85 * gib, db.vpsDao().getBySubscriptionId(id)!!.lastTotalDown)
        val sent = notifier.sent.single() as Reminder.Traffic
        assertEquals(80, sent.threshold)
        assertEquals(id, sent.subscriptionId)

        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(1, notifier.sent.size)
    }

    @Test
    fun notifier_postsToTheRightChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val notifier = Notifier(context)
        notifier.ensureChannels()
        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue(manager.getNotificationChannel(Notifier.CHANNEL_DUE) != null)
        assertTrue(manager.getNotificationChannel(Notifier.CHANNEL_TRAFFIC) != null)
        val reminder = Reminder.Traffic(
            subscriptionId = 987_654, name = "测试 VPS",
            period = TrafficPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)),
            threshold = 95, percent = 96.2, usedBytes = 962 * gib, limitBytes = 1000 * gib,
        )
        notifier.notify(reminder)
        val posted = manager.activeNotifications.firstOrNull { it.id == Notifier.notificationId(reminder) }
        assertTrue(posted != null)
        assertEquals(Notifier.CHANNEL_TRAFFIC, posted!!.notification.channelId)
        manager.cancel(Notifier.notificationId(reminder))
    }
}
