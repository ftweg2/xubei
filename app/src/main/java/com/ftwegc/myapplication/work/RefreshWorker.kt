package com.ftwegc.myapplication.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ftwegc.myapplication.VluerApp
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Periodic traffic refresh (needs network): re-reads every linked VPS (which also accumulates
 * counting mode 2), then checks the traffic thresholds. A panel that is not configured is not an
 * error. Network trouble is left to the next period instead of retrying, to spare the battery.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as VluerApp).container
        return try {
            if (!container.settingsStore.settings.first().komariConfigured) return Result.success()
            when (val result = container.trafficRepository.refreshAll()) {
                is KomariResult.Err -> if (result.error == KomariError.NotConfigured) return Result.success()
                is KomariResult.Ok -> Unit
            }
            container.reminderService.checkTraffic()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Background refresh failed: ${e.javaClass.simpleName}")
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "RefreshWorker"
    }
}
