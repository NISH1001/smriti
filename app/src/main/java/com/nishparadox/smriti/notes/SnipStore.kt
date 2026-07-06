package com.nishparadox.smriti.notes

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Single source of truth for snips. Backed by a Compose snapshot list (observed by the
 * UI) and persisted to a small JSON file. Safe to mutate from the capture service thread.
 */
object SnipStore {
    /** Newest first. Read directly in composables; written from any thread. */
    val snips = mutableStateListOf<Snip>()
    private var file: File? = null
    private const val MAX = 200

    @Synchronized fun ensureLoaded(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "snips.json")
        file = f
        if (f.exists()) runCatching {
            val loaded = parse(f.readText())
            snips.clear(); snips.addAll(loaded)
        }
    }

    @Synchronized fun add(s: Snip) {
        snips.add(0, s)
        while (snips.size > MAX) snips.removeAt(snips.size - 1)
        persist()
    }

    @Synchronized fun update(id: Long, transform: (Snip) -> Snip) {
        val i = snips.indexOfFirst { it.id == id }
        if (i >= 0) { snips[i] = transform(snips[i]); persist() }
    }

    private fun persist() {
        val f = file ?: return
        runCatching { f.writeText(serialize(snips.toList())) }
    }

    private fun serialize(list: List<Snip>): String {
        val arr = JSONArray()
        for (s in list) arr.put(
            JSONObject()
                .put("id", s.id).put("createdAt", s.createdAt)
                .put("status", s.status.name).put("text", s.text)
                .put("source", s.source).put("durationS", s.durationS)
        )
        return arr.toString()
    }

    private fun parse(json: String): List<Snip> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Snip(
                id = o.getLong("id"),
                createdAt = o.optLong("createdAt", o.getLong("id")),
                status = runCatching { SnipStatus.valueOf(o.getString("status")) }
                    .getOrDefault(SnipStatus.DONE),
                text = o.optString("text", ""),
                source = o.optString("source", ""),
                durationS = o.optInt("durationS", 0),
            )
        }
    }
}
