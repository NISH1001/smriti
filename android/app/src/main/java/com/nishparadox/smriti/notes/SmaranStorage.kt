package com.nishparadox.smriti.notes

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * How smarans are persisted locally. The on-disk file is the **source of truth**; the in-memory
 * [SnipStore] list is a cache of it, and any search index (e.g. in-memory FTS5) is a derived,
 * rebuildable cache on top. Kept behind this interface so the backing store can later move to
 * SQLite/FTS without [SnipStore] having to change.
 */
interface SmaranStorage {
    fun load(): List<Snip>
    fun save(smarans: List<Snip>)
}

/**
 * JSONL file store: one JSON smaran per line — the same per-record codec as the Drive `.jsonl`
 * ([SnipStore.toJson] / [SnipStore.fromJson]), so local and synced formats never drift.
 *
 * Rigid write contract:
 *  - **Atomic** — serialize to `<name>.tmp`, fsync it, then rename over `<name>`. A crash mid-write
 *    leaves either the old whole file or the new whole file, never a torn one.
 *  - **Backed up** — the previous good file is copied to `<name>.bak` before each replace.
 *  - **Tolerant read** — a corrupt line is skipped, not fatal (line-oriented, unlike one big JSON
 *    array). If the primary file is wholly unreadable, recovery falls back to `.bak`.
 *  - **Migrates** a legacy single-array `snips.json` (v0.4 and earlier) on first load.
 */
class JsonlStorage(private val dir: File, private val name: String) : SmaranStorage {
    private val file = File(dir, name)
    private val tmp = File(dir, "$name.tmp")
    private val bak = File(dir, "$name.bak")
    private val legacy = File(dir, "snips.json")

    override fun load(): List<Snip> {
        migrateLegacyIfPresent()
        parseOrNull(file)?.let { return it }
        parseOrNull(bak)?.let { Log.w(TAG, "$name unreadable — recovered ${it.size} from .bak"); return it }
        return emptyList()
    }

    override fun save(smarans: List<Snip>) {
        val text = buildString {
            for (s in smarans) { append(SnipStore.toJson(s)); append('\n') }
        }
        writeAtomic(text)
    }

    /** Write to a temp file, force it to disk, back up the last good file, then atomically rename. */
    private fun writeAtomic(text: String) {
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()                                      // durability across power loss / kill
        }
        if (file.exists()) runCatching { file.copyTo(bak, overwrite = true) }   // keep last-good copy
        if (!tmp.renameTo(file)) {                             // same-fs rename is atomic on POSIX
            runCatching { tmp.copyTo(file, overwrite = true) } // fallback if rename is refused
            tmp.delete()
        }
    }

    /** null = missing or wholly unparseable (→ try the backup); bad lines within are skipped. */
    private fun parseOrNull(f: File): List<Snip>? {
        if (!f.exists()) return null
        val text = runCatching { f.readText() }.getOrNull() ?: return null
        if (text.isBlank()) return emptyList()
        val out = ArrayList<Snip>()
        var sawContent = false
        for (raw in text.lineSequence()) {
            val line = raw.trim(); if (line.isEmpty()) continue
            sawContent = true
            val o = runCatching { JSONObject(line) }.getOrNull() ?: continue
            runCatching { SnipStore.fromJson(o) }.getOrNull()?.let { out.add(it) }
        }
        return if (sawContent && out.isEmpty()) null else out  // had content but nothing parsed → corrupt
    }

    /** One-time: convert an old single-array `snips.json` into `<name>`, then retire the legacy file. */
    private fun migrateLegacyIfPresent() {
        if (file.exists() || !legacy.exists()) return
        val migrated = runCatching {
            val arr = JSONArray(legacy.readText())
            (0 until arr.length()).mapNotNull {
                runCatching { SnipStore.fromJson(arr.getJSONObject(it)) }.getOrNull()
            }
        }.getOrNull() ?: return
        save(migrated)
        runCatching { legacy.renameTo(File(dir, "snips.json.migrated")) }
        Log.i(TAG, "migrated ${migrated.size} smarans from legacy snips.json")
    }

    companion object { private const val TAG = "SMRITI" }
}
