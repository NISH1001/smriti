package com.nishparadox.smriti.settings

import android.content.Context

/** Persisted v0 settings: pre-roll N, total K, and the watched-app whitelist. */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("smriti", Context.MODE_PRIVATE)

    var preRoll: Int
        get() = p.getInt("preRoll", 5)
        set(v) { p.edit().putInt("preRoll", v).apply() }

    var total: Int
        get() = p.getInt("total", 20)
        set(v) { p.edit().putInt("total", v).apply() }

    var whitelist: Set<String>
        get() = p.getStringSet("wl", setOf("com.audiobookshelf.app"))!!
        set(v) { p.edit().putStringSet("wl", v).apply() }

    var themeMode: String
        get() = p.getString("theme", "dark")!!
        set(v) { p.edit().putString("theme", v).apply() }
}
