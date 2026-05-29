package com.sand.tracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
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
    private val POLL_INTERVAL_MS = 60_000L  // Just syncs time every minute, overlay handled by AccessibilityService
    private val INSTAGRAM_PKG = "com.instagram.android"

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
                syncTime()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun syncTime() {
        val minutes = getInstagramMinutesToday()
        updateNotification("Instagram today: ${minutes}min")

        // Just sync — don't fire overlay here, AccessibilityService handles that instantly
        val api = SandClient.buildFromPrefs(this) ?: return
        try {
            api.sync(TimeSyncRequest(minutes))
        } catch (e: Exception) {
            // Silent fail — AccessibilityService will handle blocking
        }
    }

    private fun getInstagramMinutesToday(): Int {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            cal.timeInMillis,
            now
        )
        val ms = stats?.find { it.packageName == INSTAGRAM_PKG }?.totalTimeInForeground ?: 0L
        return (ms / 1000 / 60).toInt()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sand Tracker",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Tracks Instagram usage" }
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
