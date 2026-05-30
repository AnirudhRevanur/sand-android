package com.sand

import android.content.Context

object BlockedApps {

    private const val PREFS_NAME = "sand_blocked"
    private const val KEY_BLOCKED = "blocked_packages"
    private const val KEY_BLOCKED_AT = "blocked_at_"
    private const val HOURS_48 = 48 * 60 * 60 * 1000L

    fun getBlocked(context: Context): Set<String> {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()
    }

    fun isBlocked(context: Context, packageName: String): Boolean {
        return getBlocked(context).contains(packageName)
    }

    fun block(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_BLOCKED, emptySet())!!.toMutableSet()
        current.add(packageName)
        prefs.edit()
            .putStringSet(KEY_BLOCKED, current)
            .putLong(KEY_BLOCKED_AT + packageName, System.currentTimeMillis())
            .apply()
    }

    fun canUnblock(context: Context, packageName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val blockedAt = prefs.getLong(KEY_BLOCKED_AT + packageName, 0L)
        return System.currentTimeMillis() - blockedAt >= HOURS_48
    }

    fun unblock(context: Context, packageName: String): Boolean {
        if (!canUnblock(context, packageName)) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_BLOCKED, emptySet())!!.toMutableSet()
        current.remove(packageName)
        prefs.edit()
            .putStringSet(KEY_BLOCKED, current)
            .remove(KEY_BLOCKED_AT + packageName)
            .apply()
        return true
    }

    fun timeUntilUnblock(context: Context, packageName: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val blockedAt = prefs.getLong(KEY_BLOCKED_AT + packageName, 0L)
        val remaining = (blockedAt + HOURS_48) - System.currentTimeMillis()
        if (remaining <= 0) return "Ready to unblock"
        val hours = remaining / (60 * 60 * 1000)
        val minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000)
        return "${hours}h ${minutes}m remaining"
    }
}
