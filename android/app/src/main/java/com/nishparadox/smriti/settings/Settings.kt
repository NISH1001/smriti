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

    /** RAG (on-device answer synthesis) is opt-in; enabling downloads the selected small LLM. */
    var ragEnabled: Boolean
        get() = p.getBoolean("ragEnabled", false)
        set(v) { p.edit().putBoolean("ragEnabled", v).apply() }

    /** Selected RAG model id (see rag/RagModel). */
    var ragModel: String
        get() = p.getString("ragModel", DEFAULT_RAG_MODEL)!!
        set(v) { p.edit().putString("ragModel", v).apply() }

    /** Editable grounding instruction, sent as the system prompt. */
    var ragPrompt: String
        get() = p.getString("ragPrompt", DEFAULT_RAG_PROMPT)!!
        set(v) { p.edit().putString("ragPrompt", v).apply() }

    /** How many top-ranked smarans to feed the model as context. */
    var ragTopN: Int
        get() = p.getInt("ragTopN", 3)
        set(v) { p.edit().putInt("ragTopN", v).apply() }

    /** Auto = generate on each search; false (default) = only when the ✨ Answer button is tapped. */
    var ragAuto: Boolean
        get() = p.getBoolean("ragAuto", false)
        set(v) { p.edit().putBoolean("ragAuto", v).apply() }

    /** Context-window CAP in tokens (memory knob). Effective window = min(model-trained, this). */
    var ragCtx: Int
        get() = p.getInt("ragCtx", 4096)
        set(v) { p.edit().putInt("ragCtx", v).apply() }

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

        const val DEFAULT_RAG_MODEL = "lfm2.5-350m"
        const val DEFAULT_RAG_PROMPT =
            "You are a helpful assistant answering from the user's personal notes. Using the notes " +
                "provided, answer the question clearly and completely in your own words. If the notes " +
                "don't contain the answer, say it's not in the notes."
    }
}
