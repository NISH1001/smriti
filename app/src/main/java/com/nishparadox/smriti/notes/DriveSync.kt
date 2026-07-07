package com.nishparadox.smriti.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Ecosystem-protocol Drive sync (specs/ecosystem-protocol.md §3–4): NO Google API.
 * The user grants the `nishparadox/smriti` folder once via SAF; this device writes ONLY its
 * own `snips/<device>.jsonl` (single-writer, whole-file) and read-time-unions every other
 * device's `*.jsonl`. Fails soft: sync never blocks capture.
 */
object DriveSync {
    private const val TAG = "SMRITI_SYNC"
    private lateinit var app: Context
    private val io = Executors.newSingleThreadExecutor()

    @Volatile var root: String = ""
    val enabled: Boolean get() = root.isNotEmpty()

    /** Resolved snips/ folder, cached for the process so we never race-create a duplicate. */
    @Volatile private var snipsDir: DocumentFile? = null

    /** Stable per-device id: `pixel-9-<6 hex of ANDROID_ID>` (protocol §3). */
    private val deviceId: String by lazy {
        val androidId = runCatching {
            Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val hex = androidId.take(6).ifEmpty { "000000" }
        val model = Build.MODEL.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "android" }
        "$model-$hex"
    }
    private val fileName: String get() = "$deviceId.jsonl"

    fun init(ctx: Context, savedRoot: String) {
        app = ctx.applicationContext
        root = savedRoot
        SnipStore.onChanged = { syncMine() }
    }

    /** Called from the UI after the SAF folder picker returns a tree uri. */
    fun setup(uri: Uri): Boolean = runCatching {
        app.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        root = uri.toString()
        snipsDir = null
        true
    }.getOrElse { Log.e(TAG, "setup failed", it); false }

    fun disable() { root = ""; snipsDir = null }

    fun syncMine() {
        if (enabled) io.execute { runCatching { writeMine() }.onFailure { Log.e(TAG, "writeMine", it) } }
    }

    fun pull() {
        if (enabled) io.execute { runCatching { pullSiblings() }.onFailure { Log.e(TAG, "pull", it) } }
    }

    private fun snipsFolder(): DocumentFile? {
        snipsDir?.let { if (it.exists()) return it }   // reuse within this process — no re-create
        if (root.isEmpty()) return null
        val granted = DocumentFile.fromTreeUri(app, Uri.parse(root)) ?: return null
        // Grant may be the ecosystem root (create/reuse smriti/) or the smriti folder itself.
        val smriti = if (granted.name == "smriti") granted else findOrCreateDir(granted, "smriti")
        smriti ?: return null
        // Reuse an existing snips/ (prefer the one already holding our device file); never make a 2nd.
        val candidates = smriti.listFiles().filter { it.isDirectory && it.name == "snips" }
        val dir = candidates.firstOrNull { it.findFile(fileName) != null }
            ?: candidates.firstOrNull()
            ?: smriti.createDirectory("snips")
        snipsDir = dir
        // Self-heal: delete any *empty* duplicate snips/ folders left by the old race bug.
        candidates.filter { it.uri != dir?.uri && it.listFiles().isEmpty() }
            .forEach { runCatching { it.delete() } }
        return dir
    }

    /** Reuse the first existing child dir with this name, else create it — dedup-safe. */
    private fun findOrCreateDir(parent: DocumentFile, name: String): DocumentFile? =
        parent.listFiles().firstOrNull { it.isDirectory && it.name == name } ?: parent.createDirectory(name)

    /** Rewrite this device's whole file with its own finished notes (single-writer, "wt" truncates). */
    private fun writeMine() {
        val folder = snipsFolder() ?: return
        val mine = SnipStore.ownDone()
        val text = mine.joinToString(separator = "\n", postfix = "\n") { SnipStore.toJson(it).toString() }
        val f = folder.findFile(fileName)?.takeIf { it.isFile }
            ?: folder.createFile("application/octet-stream", fileName) ?: return
        app.contentResolver.openOutputStream(f.uri, "wt")?.use { it.write(text.toByteArray()) }
        Log.i(TAG, "wrote ${mine.size} notes -> snips/$fileName")
    }

    /** Merge every other device's jsonl into the store, deduped by id. Corrupt lines skipped. */
    private fun pullSiblings() {
        val folder = snipsFolder() ?: return
        val existing = SnipStore.idSet()
        var added = 0
        for (file in folder.listFiles()) {
            val name = file.name ?: continue
            if (!name.endsWith(".jsonl") || name == fileName) continue
            val device = name.removeSuffix(".jsonl")
            val text = runCatching {
                app.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull() ?: continue
            for (line in text.lineSequence()) {
                val t = line.trim()
                if (t.isEmpty()) continue
                val o = runCatching { JSONObject(t) }.getOrNull() ?: continue
                val id = o.optLong("id", 0L)
                if (id == 0L || id in existing) continue
                SnipStore.merge(SnipStore.fromJson(o).copy(device = device))
                added++
            }
        }
        if (added > 0) Log.i(TAG, "pulled $added notes")
    }
}
