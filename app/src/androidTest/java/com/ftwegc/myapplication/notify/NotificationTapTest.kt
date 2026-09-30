package com.ftwegc.myapplication.notify

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ftwegc.myapplication.AppContainer
import com.ftwegc.myapplication.MainActivity
import com.ftwegc.myapplication.VluerApp
import com.ftwegc.myapplication.data.local.AppDatabase
import com.ftwegc.myapplication.domain.calc.Reminder
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.ui.OPEN_HOME_REQUEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Tapping reminder notifications: rows are grouped under the app's own summary (never the system
 * bundle, whose summary only opened the launcher and cleared every row), every row carries its
 * own PendingIntent, and firing a row's intent shows that subscription's detail page, whether the
 * activity is running or has to be started. The "process was killed" case cannot be simulated
 * inside the instrumented process; it was checked on the emulator (see the stage report).
 */
@RunWith(AndroidJUnit4::class)
class NotificationTapTest {

    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val app = context.applicationContext as VluerApp
    private val system = context.getSystemService(NotificationManager::class.java)
    private lateinit var original: AppContainer
    private lateinit var db: AppDatabase
    private lateinit var notifier: Notifier
    private var alphaId = 0L
    private var betaId = 0L

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        original = app.container
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val container = AppContainer(context = context, databaseOverride = db, settingsDataStoreOverride = MemoryDataStore())
        app.replaceContainer(container)
        runBlocking {
            val due = LocalDate.now().plusDays(1)
            alphaId = container.subscriptionRepository.save(
                Subscription(name = "Alpha 云服务", category = Category.AI, priceMinor = 2_000, currency = "CNY", cycle = BillingCycle.MONTHLY, nextDue = due),
                null,
            )
            betaId = container.subscriptionRepository.save(
                Subscription(name = "Beta 东京", category = Category.OTHER, priceMinor = 3_600, currency = "CNY", cycle = BillingCycle.MONTHLY, nextDue = due),
                null,
            )
        }
        notifier = Notifier(context)
        system.cancelAll()
        Notifier.forgetRecentRows()
    }

    private fun mainActivities(vararg stages: Stage): List<Activity> {
        val found = mutableListOf<Activity>()
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            stages.forEach { stage -> found += monitor.getActivitiesInStage(stage).filterIsInstance<MainActivity>() }
        }
        return found
    }

    private fun resumedMainActivity(): Activity = mainActivities(Stage.RESUMED).single()

    @After
    fun tearDown() {
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.CREATED, Stage.STARTED).forEach { stage ->
                monitor.getActivitiesInStage(stage).forEach { it.finish() }
            }
        }
        instrumentation.waitForIdleSync()
        system.cancelAll()
        app.replaceContainer(original)
        db.close()
    }

    private fun due(id: Long, name: String) = Reminder.Due(
        subscriptionId = id,
        name = name,
        dueDate = LocalDate.now().plusDays(1),
        daysLeft = 1,
        threshold = 1,
        priceMinor = 2_000,
        currency = "CNY",
        isFree = false,
        autoRenew = false,
    )

    private fun traffic(id: Long, name: String) = Reminder.Traffic(
        subscriptionId = id,
        name = name,
        period = com.ftwegc.myapplication.domain.model.TrafficPeriod(LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(1).plusMonths(1)),
        threshold = 80,
        percent = 81.0,
        usedBytes = 81L shl 30,
        limitBytes = 100L shl 30,
    )

    /** Waits until the app shows [count] notifications (posting is asynchronous). */
    private fun awaitNotifications(count: Int): List<StatusBarNotification> {
        val deadline = System.currentTimeMillis() + 5_000
        var active = system.activeNotifications.toList()
        while (active.size != count && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            active = system.activeNotifications.toList()
        }
        assertEquals(active.joinToString { "${it.tag}/${it.id}" }, count, active.size)
        return active
    }

    /** Waits until the summary of [group] reports [rows] rows (the summary is updated asynchronously). */
    private fun awaitSummary(group: String, rows: Int): List<StatusBarNotification> {
        val deadline = System.currentTimeMillis() + 5_000
        fun current() = system.activeNotifications.toList()
        var active = current()
        while (System.currentTimeMillis() < deadline &&
            active.none { it.isSummary() && it.notification.group == group && it.notification.number == rows }
        ) {
            Thread.sleep(50)
            active = current()
        }
        return active
    }

    private fun StatusBarNotification.isSummary() = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

    private fun row(active: List<StatusBarNotification>, subscriptionId: Long): StatusBarNotification =
        active.single { !it.isSummary() && it.notification.extras.getLong(Notifier.EXTRA_SUBSCRIPTION_ID) == subscriptionId }

    private fun summary(active: List<StatusBarNotification>): StatusBarNotification = active.single { it.isSummary() }

    private fun awaitDetail(name: String, otherName: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription("编辑")).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasText(name)).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasText(otherName)).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun send(intent: PendingIntent) {
        val options = if (Build.VERSION.SDK_INT >= 34) {
            android.app.ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else {
            null
        }
        intent.send(context, 0, null, null, null, null, options)
    }

    @Test
    fun twoRemindersGetTheAppsOwnGroupAndSummaryWithSeparateIntents() {
        notifier.notify(due(alphaId, "Alpha 云服务"))
        notifier.notify(due(betaId, "Beta 东京"))
        awaitNotifications(3)
        val active = awaitSummary(Notifier.GROUP_DUE, 2)

        // No system bundle ("ranker_group"): the app's own group and summary.
        assertTrue(active.none { it.tag == "ranker_group" })
        assertTrue(active.all { it.notification.group == Notifier.GROUP_DUE })
        val summary = summary(active)
        assertEquals(Notifier.SUMMARY_TAG, summary.tag)
        assertEquals(2, summary.notification.number)

        val alpha = row(active, alphaId)
        val beta = row(active, betaId)
        assertNotNull(alpha.notification.contentIntent)
        assertNotNull(beta.notification.contentIntent)
        // Distinct PendingIntents: the system did not merge the two rows' intents.
        assertNotEquals(alpha.notification.contentIntent, beta.notification.contentIntent)
        assertTrue(alpha.notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        // Several subscriptions: the summary keeps the rows (a tap opens the overview).
        assertTrue(summary.notification.flags and Notification.FLAG_AUTO_CANCEL == 0)
        assertNotNull(summary.notification.contentIntent)
    }

    @Test
    fun dueAndTrafficRowsStayInTheirOwnGroups() {
        notifier.notify(due(alphaId, "Alpha 云服务"))
        notifier.notify(traffic(alphaId, "Alpha 云服务"))
        val active = awaitNotifications(4)
        val summaries = active.filter { it.isSummary() }
        assertEquals(setOf(Notifier.GROUP_DUE, Notifier.GROUP_TRAFFIC), summaries.mapTo(HashSet()) { it.notification.group })
        assertEquals(setOf(Notifier.GROUP_DUE, Notifier.GROUP_TRAFFIC), active.filterNot { it.isSummary() }.mapTo(HashSet()) { it.notification.group })
    }

    @Test
    fun eachRowOpensItsOwnDetailPageWhileTheAppIsRunning() {
        notifier.notify(due(alphaId, "Alpha 云服务"))
        notifier.notify(due(betaId, "Beta 东京"))
        val active = awaitNotifications(3)

        send(row(active, betaId).notification.contentIntent)
        awaitDetail("Beta 东京", "Alpha 云服务")
        val running = resumedMainActivity()

        // The app is in the foreground on another detail page: the same activity gets the new
        // intent (onNewIntent) and shows the other subscription.
        send(row(active, alphaId).notification.contentIntent)
        awaitDetail("Alpha 云服务", "Beta 东京")
        assertTrue("the running activity handled the tap", running === resumedMainActivity())
    }

    @Test
    fun aRowStartsTheActivityOnItsDetailPageWhenTheAppIsClosed() {
        notifier.notify(due(alphaId, "Alpha 云服务"))
        notifier.notify(due(betaId, "Beta 东京"))
        val active = awaitNotifications(3)
        // No activity is running: the row's intent has to create it.
        send(row(active, alphaId).notification.contentIntent)
        awaitDetail("Alpha 云服务", "Beta 东京")
    }

    @Test
    fun summaryOfOneSubscriptionOpensItsDetailPage() {
        notifier.notify(due(betaId, "Beta 东京"))
        notifier.notify(due(betaId, "Beta 东京").copy(threshold = 0, daysLeft = 0))
        // Same subscription and kind: the second replaces the first (one row plus the summary).
        awaitNotifications(2)
        val active = awaitSummary(Notifier.GROUP_DUE, 1)
        val summary = summary(active)
        assertTrue(summary.notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        send(summary.notification.contentIntent)
        awaitDetail("Beta 东京", "Alpha 云服务")
    }

    @Test
    fun openTargetReadsNotificationIntentsOnlyOnce() {
        val intent = Notifier.openActivityIntent(context, 7).putExtra(Notifier.EXTRA_OPEN_TOKEN, 123L)
        assertEquals(7L, MainActivity.openTarget(intent, handledToken = 0L))
        // Already handled (the activity is recreated with its old intent).
        assertNull(MainActivity.openTarget(intent, handledToken = 123L, restored = true))
        // A newer tap on the same subscription.
        assertEquals(7L, MainActivity.openTarget(Intent(intent).putExtra(Notifier.EXTRA_OPEN_TOKEN, 124L), handledToken = 123L))
        // Reopened from the recent apps list: the task's first intent is not a new tap.
        assertNull(MainActivity.openTarget(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY), handledToken = 0L))
        // Without a token only a fresh start counts.
        val noToken = Notifier.openActivityIntent(context, 7)
        assertEquals(7L, MainActivity.openTarget(noToken, handledToken = 0L, restored = false))
        assertNull(MainActivity.openTarget(noToken, handledToken = 0L, restored = true))
        // The launcher intent and the overview intent.
        assertNull(MainActivity.openTarget(Intent(Intent.ACTION_MAIN), handledToken = 0L))
        assertEquals(OPEN_HOME_REQUEST, MainActivity.openTarget(Notifier.openHomeIntent(context).putExtra(Notifier.EXTRA_OPEN_TOKEN, 5L), handledToken = 0L))
        // Different subscriptions never give "equal" intents (extras are ignored by filterEquals).
        assertTrue(!Notifier.openActivityIntent(context, 1).filterEquals(Notifier.openActivityIntent(context, 2)))
    }
}
