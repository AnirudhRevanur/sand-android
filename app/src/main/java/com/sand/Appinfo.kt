package com.sand

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

fun getInstalledUserApps(context: Context): List<AppInfo> {
    val pm = context.packageManager

    return pm.getInstalledPackages(PackageManager.GET_META_DATA)
     .also { pkgs ->
        pkgs.forEach { android.util.Log.d("SAND_PKGS", it.packageName) }
    }
        .filter { pkg ->
            val hasLauncher = pm.getLaunchIntentForPackage(pkg.packageName) != null
            val isSand = pkg.packageName == context.packageName
            hasLauncher && !isSand
        }
        .map { pkg ->
            AppInfo(
                packageName = pkg.packageName,
                label = pm.getApplicationLabel(pkg.applicationInfo!!).toString(),
                icon = pm.getApplicationIcon(pkg.packageName)
            )
        }
        .sortedBy { it.label.lowercase() }
}
