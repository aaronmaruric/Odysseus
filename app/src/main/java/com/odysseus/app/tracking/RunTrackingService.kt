package com.odysseus.app.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.odysseus.app.MainActivity
import com.odysseus.app.OdysseusApp
import com.odysseus.app.R
import com.odysseus.app.ui.calendar.formatDuration
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the GPS session while a run is in progress.
 *
 * The tracker itself lives in [com.odysseus.app.AppContainer] so the UI can observe it directly;
 * this service exists to keep the process alive with a location-type foreground notification and
 * a partial wake lock, and to mirror progress into that notification.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class RunTrackingService : Service() {

    private val tracker: LocationRunTracker get() = (application as OdysseusApp).container.runTracker
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mirrorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                goForeground(buildNotification(tracker.state.value))
                acquireWakeLock()
                tracker.start()
                mirrorToNotification()
            }
            ACTION_PAUSE -> tracker.pause()
            ACTION_RESUME -> tracker.resume()
            ACTION_STOP -> {
                // The UI is responsible for calling tracker.stop() and saving; we only tear down.
                mirrorJob?.cancel()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- helpers -----------------------------------------------------------------------

    private fun goForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun mirrorToNotification() {
        mirrorJob?.cancel()
        mirrorJob = scope.launch {
            val manager = getSystemService(NotificationManager::class.java)
            tracker.state.sample(3_000).collect { state ->
                if (state.isActive) manager.notify(NOTIFICATION_ID, buildNotification(state))
            }
        }
    }

    private fun buildNotification(state: RunState): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when (state.status) {
            TrackerStatus.WAITING_FOR_FIX -> "Waiting for GPS"
            TrackerStatus.PAUSED -> "Paused · " + summary(state)
            else -> summary(state)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun summary(state: RunState): String =
        String.format(Locale.getDefault(), "%.2f km · %s", state.distanceMeters / 1000, formatDuration(state.elapsedMillis))

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "odysseus:run").apply {
            acquire(MAX_WAKE_LOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Run tracking", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "run_tracking"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.odysseus.app.tracking.START"
        const val ACTION_PAUSE = "com.odysseus.app.tracking.PAUSE"
        const val ACTION_RESUME = "com.odysseus.app.tracking.RESUME"
        const val ACTION_STOP = "com.odysseus.app.tracking.STOP"

        /** Safety cap: a run longer than this releases the lock rather than draining the battery forever. */
        private const val MAX_WAKE_LOCK_MS = 6L * 60 * 60 * 1000

        fun send(context: Context, action: String) {
            val intent = Intent(context, RunTrackingService::class.java).setAction(action)
            if (action == ACTION_START) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        }
    }
}
