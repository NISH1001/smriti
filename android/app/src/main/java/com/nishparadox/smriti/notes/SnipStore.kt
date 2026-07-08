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

    /** Undo a delete: re-insert in order and re-sync (fires [onChanged]). */
    @Synchronized fun restore(s: Snip) {
        if (snips.any { it.id == s.id }) return
        val idx = snips.indexOfFirst { it.createdAt < s.createdAt }
        if (idx < 0) snips.add(s) else snips.add(idx, s)
        persist(); onChanged?.invoke()
    }

    /** This device's finished notes — what we write to Drive ("" = legacy own; deviceId = stamped own). */
    @Synchronized fun ownDone(deviceId: String): List<Snip> =
        snips.filter { it.status == SnipStatus.DONE && (it.device == deviceId || it.device.isEmpty()) }

    @Synchronized fun idSet(): Set<Long> = snips.map { it.id }.toSet()

    private fun persist() {
        val f = file ?: return
        runCatching { f.writeText(serialize(snips.toList())) }
    }

    fun toJson(s: Snip): JSONObject {
        val meta = JSONObject()
        for ((k, v) in s.metadata) meta.put(k, v)
        return JSONObject()
            .put("id", s.id).put("createdAt", s.createdAt)
            .put("type", s.type.name.lowercase())
            .put("status", s.status.name.lowercase())
            .put("text", s.text)
            .put("source", s.source).put("durationS", s.durationS)
            .put("device", s.device)
            .put("metadata", meta)
    }

    fun fromJson(o: JSONObject): Snip {
        val durationS = o.optInt("durationS", 0)
        // type: tolerant + case-insensitive; an unrecognised value (from a newer/other client)
        // -> UNKNOWN, never crashes. Legacy records have no "type" — infer it (a positive
        // duration means it was an audio snip; everything else was shared/typed text).
        val type = if (o.has("type"))
            runCatching { SmaranType.valueOf(o.getString("type").uppercase()) }.getOrDefault(SmaranType.UNKNOWN)
        else if (durationS > 0) SmaranType.AUDIO else SmaranType.TEXT
        val metadata = o.optJSONObject("metadata")?.let { m ->
            buildMap { for (k in m.keys()) put(k, m.get(k).toString()) }
        } ?: emptyMap()
        return Snip(
            id = o.getLong("id"),
            createdAt = o.optLong("createdAt", o.getLong("id")),
            status = runCatching { SnipStatus.valueOf(o.optString("status").uppercase()) }.getOrDefault(SnipStatus.DONE),
            type = type,
            text = o.optString("text", ""),
            source = o.optString("source", ""),
            durationS = durationS,
            device = o.optString("device", ""),
            metadata = metadata,
        )
    }

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
