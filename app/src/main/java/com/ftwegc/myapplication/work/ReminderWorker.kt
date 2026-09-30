package com.ftwegc.myapplication.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ftwegc.myapplication.VluerApp
import kotlinx.coroutines.CancellationException

/**
 * Daily due-date check (no network): first rolls overdue auto-renewing subscriptions forward to
 * their current cycle, then sends the due reminders and the traffic reminders of the stored
 * readings.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as VluerApp).container
        return try {
            container.subscriptionRepository.rollForwardAutoRenewals()
            container.reminderService.checkDue()
            container.reminderService.checkTraffic()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reminder check failed: ${e.javaClass.simpleName}")
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "ReminderWorker"
    }
}
