package com.sand.tracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sand.BlockedApps
import com.sand.network.SandClient
import com.sand.network.TimeSyncRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TrackerService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val CHANNEL_ID = "sand_tracker"
    private val NOTIFICATION_ID = 1
    private val POLL_INTERVAL_MS = 60_000L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Watching..."))
        startTracking()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTracking() {
        scope.launch {
            while (true) {
                syncAllApps()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun syncAllApps() {
        val blockedApps = BlockedApps.getBlocked(this@TrackerService)
        if (blockedApps.isEmpty()) {
            updateNotification("No apps blocked")
            return
        }

        val api = SandClient.buildFromPrefs(this@TrackerService)

        var totalMinutes = 0
        blockedApps.forEach { packageName ->
            val minutes = getAppMinutesToday(packageName)
            totalMinutes += minutes
            if (api != null) {
                try {
                    api.sync(TimeSyncRequest(packageName, minutes))
                } catch (e: Exception) {
                    // Silent fail — AccessibilityService handles blocking
                }
            }
        }

        updateNotification("Watching ${blockedApps.size} apps — ${totalMinutes}min total today")
    }

    private fun getAppMinutesToday(packageName: String): Int {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault()).apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        val events = usm.queryEvents(cal.timeInMillis, now)
        val event = android.app.usage.UsageEvents.Event()

        var totalMs = 0L
        var lastForeground = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastForeground = event.timeStamp
                }
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (lastForeground > 0) {
                        totalMs += event.timeStamp - lastForeground
                        lastForeground = 0
                    }
                }
            }
        }

        if (lastForeground > 0) totalMs += now - lastForeground
        return (totalMs / 1000 / 60).toInt()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sand Tracker",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Tracks blocked app usage" }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sand")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
