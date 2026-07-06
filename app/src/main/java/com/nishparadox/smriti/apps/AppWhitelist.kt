package com.nishparadox.smriti.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** App selection helpers: list *media* apps and resolve package -> uid. */
object AppWhitelist {
    // Known players always shown if installed (safety net in case heuristics miss them).
    private val KNOWN = setOf(
        "com.audiobookshelf.app",
        "com.google.android.apps.youtube.music",
        "app.revanced.android.apps.youtube.music",
        "com.google.android.youtube",
        "com.spotify.music",
    )

    fun uidsFor(ctx: Context, pkgs: Set<String>): IntArray = pkgs.mapNotNull { pkg ->
        runCatching { ctx.packageManager.getPackageUid(pkg, 0) }.getOrNull()
    }.toIntArray()

    /**
     * Media-capable apps only — those exposing a MediaBrowserService (music/audiobook/podcast
     * players), or requesting the media-playback foreground-service permission, plus known players.
     * Excludes banking/etc. Needs QUERY_ALL_PACKAGES (sideload only).
     */
    fun installedMediaApps(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val pkgs = LinkedHashSet<String>()

        runCatching {
            pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), 0)
                .forEach { it.serviceInfo?.packageName?.let(pkgs::add) }
        }
        runCatching {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).forEach { pi ->
                if (pi.requestedPermissions?.contains(
                        "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"
                    ) == true
                ) pkgs.add(pi.packageName)
            }
        }
        KNOWN.forEach { if (isInstalled(pm, it)) pkgs.add(it) }

        return pkgs.mapNotNull { pkg ->
            runCatching {
                pkg to pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrNull()
        }.sortedBy { it.second.lowercase() }
    }

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean =
        runCatching { pm.getApplicationInfo(pkg, 0); true }.getOrDefault(false)
}
