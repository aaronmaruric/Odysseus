package com.painani.app.data.health

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.painani.app.PainaniApp
import java.util.concurrent.TimeUnit

/**
 * Periodic Health Connect pull that runs while the app is closed. WorkManager batches it with
 * other apps' deferred work, so the battery cost is a few seconds of CPU per run rather than a
 * process kept alive. Reads only succeed when Health Connect has granted background access
 * ([HealthConnectManager.PERMISSION_BACKGROUND]); on devices without it the worker simply
 * finds nothing to do and the app catches up next time it is opened.
 */
class HealthSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PainaniApp).container
        val health = container.healthConnect
        if (!health.hasAllPermissions()) return Result.success()
        if (health.supportsBackgroundRead && !health.hasBackgroundRead()) return Result.success()
        return runCatching { container.healthSync.syncNow() }
            .fold(
                onSuccess = { Log.i(TAG, it); Result.success() },
                onFailure = { Log.w(TAG, "background sync failed", it); Result.retry() },
            )
    }

    companion object {
        private const val TAG = "HealthSyncWorker"
        private const val WORK_NAME = "health_sync"

        fun schedule(context: Context, settings: AutoSyncSettings) {
            val wm = WorkManager.getInstance(context)
            if (!settings.enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(settings.intervalHours.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            // UPDATE keeps the existing period timer when nothing changed and swaps the spec when it did.
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
