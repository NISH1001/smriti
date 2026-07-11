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

    /** SAF tree URI of the granted Drive folder. Empty = sync off. */
    var driveRoot: String
        get() = p.getString("driveRoot", "")!!
        set(v) { p.edit().putString("driveRoot", v).apply() }

    /** Resolved-once document URIs, persisted so we never re-create (SAF is eventually consistent). */
    var driveSmaransUri: String
        get() = p.getString("driveSmaransUri", "")!!
        set(v) { p.edit().putString("driveSmaransUri", v).apply() }

    var driveFileUri: String
        get() = p.getString("driveFileUri", "")!!
        set(v) { p.edit().putString("driveFileUri", v).apply() }

    /** Audio transcription is opt-in (downloads the Whisper model). Off = no sliders, no Listen FAB. */
    var audioTranscription: Boolean
        get() = p.getBoolean("audioTranscription", false)
        set(v) { p.edit().putBoolean("audioTranscription", v).apply() }

    /** Which fields the Recent list shows per row (the note text is always shown). */
    var listFields: Set<String>
        get() = p.getStringSet("listFields", DEFAULT_LIST_FIELDS)!!
        set(v) { p.edit().putStringSet("listFields", v).apply() }

    companion object {
        /** Row-field keys, in display order. */
        val LIST_FIELDS = listOf(
            "type" to "Type icon",
            "source" to "Source",
            "time" to "Time",
            "title" to "Title",
            "url" to "URL",
        )
        val DEFAULT_LIST_FIELDS = LIST_FIELDS.map { it.first }.toSet()
    }
}
