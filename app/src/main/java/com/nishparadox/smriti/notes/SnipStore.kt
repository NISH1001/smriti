package com.nishparadox.smriti.notes

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Single source of truth for snips. Backed by a Compose snapshot list (observed by the
 * UI) and persisted to a small JSON file. Safe to mutate from the capture service thread.
 * [onChanged] fires after local edits (not pulled merges) so Drive sync can rewrite our file.
 */
object SnipStore {
    /** Newest first. Read directly in composables; written from any thread. */
    val snips = mutableStateListOf<Snip>()

    /** Set by DriveSync; invoked after a local add/update/delete (not after a pulled merge). */
    @Volatile var onChanged: (() -> Unit)? = null

    private var file: File? = null
    private const val MAX = 500

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
        persist(); onChanged?.invoke()
    }

    @Synchronized fun update(id: Long, transform: (Snip) -> Snip) {
        val i = snips.indexOfFirst { it.id == id }
        if (i >= 0) { snips[i] = transform(snips[i]); persist(); onChanged?.invoke() }
    }

    @Synchronized fun delete(id: Long) {
        val i = snips.indexOfFirst { it.id == id }
        if (i >= 0) { snips.removeAt(i); persist(); onChanged?.invoke() }
    }

    /** Insert a note pulled from another device (keeps newest-first order). No [onChanged]. */
    @Synchronized fun merge(s: Snip) {
        if (snips.any { it.id == s.id }) return
        val idx = snips.indexOfFirst { it.createdAt < s.createdAt }
        if (idx < 0) snips.add(s) else snips.add(idx, s)
        persist()
    }

    /** This device's own notes (empty device tag) that are finished — what we write to Drive. */
    @Synchronized fun ownDone(): List<Snip> =
        snips.filter { it.device.isEmpty() && it.status == SnipStatus.DONE }

    @Synchronized fun idSet(): Set<Long> = snips.map { it.id }.toSet()

    private fun persist() {
        val f = file ?: return
        runCatching { f.writeText(serialize(snips.toList())) }
    }

    fun toJson(s: Snip, includeDevice: Boolean = true): JSONObject = JSONObject()
        .put("id", s.id).put("createdAt", s.createdAt)
        .put("status", s.status.name).put("text", s.text)
        .put("source", s.source).put("durationS", s.durationS)
        .apply { if (includeDevice) put("device", s.device) }

    fun fromJson(o: JSONObject): Snip = Snip(
        id = o.getLong("id"),
        createdAt = o.optLong("createdAt", o.getLong("id")),
        status = runCatching { SnipStatus.valueOf(o.getString("status")) }.getOrDefault(SnipStatus.DONE),
        text = o.optString("text", ""),
        source = o.optString("source", ""),
        durationS = o.optInt("durationS", 0),
        device = o.optString("device", ""),
    )

    private fun serialize(list: List<Snip>): String {
        val arr = JSONArray()
        for (s in list) arr.put(toJson(s))
        return arr.toString()
    }

    private fun parse(json: String): List<Snip> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }
}
