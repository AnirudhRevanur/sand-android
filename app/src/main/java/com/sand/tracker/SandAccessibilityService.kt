package com.sand.tracker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.sand.network.SandClient
import com.sand.network.TimeSyncRequest
import com.sand.overlay.OverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SandAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val INSTAGRAM_PKG = "com.instagram.android"
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

        val pkg = event.packageName?.toString()

        if (pkg != INSTAGRAM_PKG) return
        scope.launch {
            checkAndGate()
        }
    }

    private suspend fun checkAndGate() {
        val minutes = getInstagramMinutesToday()


        if (minutes < FREE_MINUTES){
          return
        }

        val api = SandClient.buildFromPrefs(this)

        if (api == null) {
            fireOverlay(minutes, level = -1)
            return
        }

        try {
            val response = api.sync(TimeSyncRequest(minutes))
            if (response.isSuccessful) {
                val body = response.body()!!
                if (body.gated) fireOverlay(minutes, level = body.current_level)
            } else {
                fireOverlay(minutes, level = -1)
            }
        } catch (e: Exception) {
            // Laptop unreachable = blocked
            fireOverlay(minutes, level = -1)
        }
    }

    private fun getInstagramMinutesToday(): Int {
        val usm = getSystemService(USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val stats = usm.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            cal.timeInMillis,
            now
        )
        val ms = stats?.find { it.packageName == INSTAGRAM_PKG }?.totalTimeInForeground ?: 0L
        return (ms / 1000 / 60).toInt()
    }

    private fun fireOverlay(minutes: Int, level: Int) {
        val intent = Intent(this, OverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(OverlayActivity.EXTRA_MINUTES, minutes)
            putExtra(OverlayActivity.EXTRA_LEVEL, level)
            putExtra(OverlayActivity.EXTRA_NO_API, level == -1)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {}
}
