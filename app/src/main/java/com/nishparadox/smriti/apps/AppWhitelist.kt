package com.nishparadox.smriti.apps

import android.content.Context
import android.content.Intent

/** App selection helpers: list launchable apps and resolve package -> uid. */
object AppWhitelist {
    fun uidsFor(ctx: Context, pkgs: Set<String>): IntArray = pkgs.mapNotNull { pkg ->
        runCatching { ctx.packageManager.getPackageUid(pkg, 0) }.getOrNull()
    }.toIntArray()

    /** All launchable installed apps (needs QUERY_ALL_PACKAGES; sideload only), sorted by label. */
    fun installedLaunchable(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }
}
