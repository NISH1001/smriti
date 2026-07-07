package com.nishparadox.smriti.settings

import android.content.Context

/** Persisted v0 settings: pre-roll N, total K, and the watched-app whitelist. */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("smriti", Context.MODE_PRIVATE)

    var preRoll: Int
        get() = p.getInt("preRoll", 5)
        set(v) { p.edit().putInt("preRoll", v).apply() }

    var total: Int
        get() = p.getInt("total", 10)
        set(v) { p.edit().putInt("total", v).apply() }

    var whitelist: Set<String>
        get() = p.getStringSet("wl", setOf("com.audiobookshelf.app"))!!
        set(v) { p.edit().putStringSet("wl", v).apply() }

    var themeMode: String
        get() = p.getString("theme", "dark")!!
        set(v) { p.edit().putString("theme", v).apply() }

    /** SAF tree URI of the granted Drive folder (nishparadox/smriti). Empty = sync off. */
    var driveRoot: String
        get() = p.getString("driveRoot", "")!!
        set(v) { p.edit().putString("driveRoot", v).apply() }
}
