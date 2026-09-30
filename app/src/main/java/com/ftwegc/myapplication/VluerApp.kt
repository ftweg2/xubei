package com.ftwegc.myapplication

import android.app.Application
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.ftwegc.myapplication.dev.DevHooks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Application entry point; owns the [AppContainer] and schedules the background work. */
class VluerApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Scope for app-wide fire-and-forget work. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, clock = DevHooks.clock(this))
        applicationScope.launch {
            // Auto-renewing subscriptions that expired while the app was closed move to their
            // current due date, so they are not shown as overdue.
            runCatching { container.subscriptionRepository.rollForwardAutoRenewals() }
                .onFailure { Log.w(TAG, "Auto-renew roll forward failed", it) }
        }
        applicationScope.launch {
            runCatching {
                container.notifier.ensureChannels()
                val settings = container.settingsStore.settings.first()
                val scheduler = container.workScheduler
                scheduler.scheduleRefresh(settings.refreshIntervalMinutes, update = false)
                scheduler.scheduleDailyReminders()
                // Catch up on reminders the daily job may have missed while the phone was off.
                scheduler.runRemindersNow()
            }.onFailure { Log.w(TAG, "Scheduling background work failed", it) }
        }
    }

    /** Swaps the container in instrumentation tests (workers read it from the Application). */
    @VisibleForTesting
    fun replaceContainer(replacement: AppContainer) {
        container = replacement
    }

    private companion object {
        const val TAG = "VluerApp"
    }
}
