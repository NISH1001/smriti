package com.nishparadox.smriti.notes

import android.content.Context
import android.os.Build
import android.provider.Settings

/** Stable per-device id: `pixel-9-<6 hex of ANDROID_ID>` (ecosystem-protocol §3). */
object DeviceId {
    fun of(ctx: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(ctx.applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val hex = androidId.take(6).ifEmpty { "000000" }
        val model = Build.MODEL.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "android" }
        return "$model-$hex"
    }
}
