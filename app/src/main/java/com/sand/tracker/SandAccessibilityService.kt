package com.sand.tracker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.sand.BlockedApps
import com.sand.network.SandClient
import com.sand.network.TimeSyncRequest
import com.sand.overlay.OverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SandAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val FREE_MINUTES = 30

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        // Only care about blocked apps
        if (!BlockedApps.isBlocked(this, pkg)) return
        if (pkg == packageName) return

        scope.launch {
            checkAndGate(pkg)
        }
    }

    private suspend fun checkAndGate(packageName: String) {
        val minutes = getAppMinutesToday(packageName)
        android.util.Log.d("SAND", "$packageName minutes today: $minutes, free zone: $FREE_MINUTES")

        if (minutes < FREE_MINUTES) return

        val api = SandClient.buildFromPrefs(this)

        if (api == null) {
            fireOverlay(packageName, minutes, level = -1)
            return
        }

        try {
            val response = api.sync(TimeSyncRequest(packageName, minutes))
            if (response.isSuccessful) {
                val body = response.body()!!
                if (body.gated) fireOverlay(packageName, minutes, level = body.current_level)
            } else {
                fireOverlay(packageName, minutes, level = -1)
            }
        } catch (e: Exception) {
            fireOverlay(packageName, minutes, level = -1)
        }
    }

    private fun getAppMinutesToday(packageName: String): Int {
        val usm = getSystemService(USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
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

    private fun fireOverlay(packageName: String, minutes: Int, level: Int) {
        val intent = Intent(this, OverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(OverlayActivity.EXTRA_MINUTES, minutes)
            putExtra(OverlayActivity.EXTRA_LEVEL, level)
            putExtra(OverlayActivity.EXTRA_NO_API, level == -1)
            putExtra(OverlayActivity.EXTRA_APP_ID, packageName)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {}
}
