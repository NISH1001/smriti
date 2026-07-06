package com.nishparadox.smriti.apps

import android.content.Context
import android.content.pm.PackageManager

/** Known media apps a user may want to snip from, and package -> uid resolution. */
object AppWhitelist {
    /** package name -> display label. Only installed ones are shown in the UI. */
    val CANDIDATES = linkedMapOf(
        "com.audiobookshelf.app" to "Audiobookshelf",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "app.revanced.android.apps.youtube.music" to "YT Music (ReVanced)",
        "com.google.android.youtube" to "YouTube",
    )

    fun installed(ctx: Context): List<Pair<String, String>> =
        CANDIDATES.entries.filter { isInstalled(ctx, it.key) }.map { it.key to it.value }

    fun uidsFor(ctx: Context, pkgs: Set<String>): IntArray = pkgs.mapNotNull { pkg ->
        runCatching { ctx.packageManager.getPackageUid(pkg, 0) }.getOrNull()
    }.toIntArray()

    private fun isInstalled(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
}
