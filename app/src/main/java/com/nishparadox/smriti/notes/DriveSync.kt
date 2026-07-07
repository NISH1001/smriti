package com.nishparadox.smriti.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings as AndroidSettings
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.nishparadox.smriti.settings.Settings
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Ecosystem-protocol Drive sync (specs/ecosystem-protocol.md §3–4): NO Google API — the user
 * grants a folder once via SAF; this device writes ONLY its own `snips/<device>.jsonl` and
 * read-time-unions every other device's `*.jsonl`.
 *
 * Race-free design (Google Drive's SAF provider is eventually consistent, so `findFile` +
 * `createFile` on rapid writes makes duplicates): resolve the snips/ folder and the device
 * file EXACTLY ONCE via DocumentsContract, PERSIST their runtime document URIs, and thereafter
 * write straight to the saved URI — never create again. Writes are debounced so a burst of
 * status changes collapses into one whole-file write. Self-heals earlier duplicates on resolve.
 */
object DriveSync {
    private const val TAG = "SMRITI_SYNC"
    private lateinit var app: Context
    private val io = Executors.newSingleThreadExecutor()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var pendingWrite: ScheduledFuture<*>? = null

    @Volatile var root: String = ""
    val enabled: Boolean get() = root.isNotEmpty()

    // Resolved-once document URIs (mirrored to Settings so they survive process death).
    @Volatile private var snipsUri: Uri? = null
    @Volatile private var fileUri: Uri? = null

    /** Stable per-device id: `pixel-9-<6 hex of ANDROID_ID>` (protocol §3). */
    private val deviceId: String by lazy {
        val androidId = runCatching {
            AndroidSettings.Secure.getString(app.contentResolver, AndroidSettings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val hex = androidId.take(6).ifEmpty { "000000" }
        val model = Build.MODEL.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "android" }
        "$model-$hex"
    }
    private val fileName: String get() = "$deviceId.jsonl"

    fun init(ctx: Context, savedRoot: String) {
        app = ctx.applicationContext
        root = savedRoot
        val s = Settings(app)
        snipsUri = s.driveSnipsUri.ifEmpty { null }?.let(Uri::parse)
        fileUri = s.driveFileUri.ifEmpty { null }?.let(Uri::parse)
        SnipStore.onChanged = { syncMine() }
    }

    /** Called from the UI after the SAF folder picker returns a tree uri. */
    fun setup(uri: Uri): Boolean = runCatching {
        app.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        root = uri.toString()
        clearResolved()   // a new grant → forget old URIs, re-resolve against the new folder
        true
    }.getOrElse { Log.e(TAG, "setup failed", it); false }

    fun disable() { root = ""; clearResolved() }

    private fun clearResolved() {
        snipsUri = null; fileUri = null
        Settings(app).apply { driveSnipsUri = ""; driveFileUri = "" }
    }

    /** Debounced: a burst of note changes collapses into one whole-file write. */
    fun syncMine() {
        if (!enabled) return
        pendingWrite?.cancel(false)
        pendingWrite = scheduler.schedule(
            { runCatching { writeMine() }.onFailure { Log.e(TAG, "writeMine", it) } },
            800, TimeUnit.MILLISECONDS
        )
    }

    fun pull() {
        if (enabled) io.execute { runCatching { pullSiblings() }.onFailure { Log.e(TAG, "pull", it) } }
    }

    // ---------- write ----------

    private fun writeMine() {
        val mine = SnipStore.ownDone()
        val text = mine.joinToString(separator = "\n", postfix = "\n") { SnipStore.toJson(it).toString() }
        var uri = deviceFileUri() ?: return
        if (!writeTo(uri, text)) {          // stale URI (file deleted/moved) → re-resolve once
            clearFileUri()
            uri = deviceFileUri() ?: return
            writeTo(uri, text)
        }
        Log.i(TAG, "wrote ${mine.size} notes -> snips/$fileName")
    }

    private fun writeTo(uri: Uri, text: String): Boolean = runCatching {
        app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
    }.getOrDefault(false)

    private fun clearFileUri() { fileUri = null; Settings(app).driveFileUri = "" }

    // ---------- read-time union ----------

    private fun pullSiblings() {
        val folderUri = snipsFolderUri() ?: return
        val existing = SnipStore.idSet()
        var added = 0
        for ((uri, name) in allChildren(folderUri)) {
            if (!name.endsWith(".jsonl") || name == fileName) continue
            val device = name.removeSuffix(".jsonl")
            val text = runCatching {
                app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull() ?: continue
            for (line in text.lineSequence()) {
                val t = line.trim(); if (t.isEmpty()) continue
                val o = runCatching { JSONObject(t) }.getOrNull() ?: continue
                val id = o.optLong("id", 0L)
                if (id == 0L || id in existing) continue
                SnipStore.merge(SnipStore.fromJson(o).copy(device = device))
                added++
            }
        }
        if (added > 0) Log.i(TAG, "pulled $added notes")
    }

    // ---------- resolve-once (create at most once, then reuse the persisted URI) ----------

    /** snips/ folder URI. Reuses one holding our file, self-heals empty duplicates, persists it. */
    @Synchronized private fun snipsFolderUri(): Uri? {
        snipsUri?.let { if (docExists(it)) return it }
        if (root.isEmpty()) return null
        val granted = DocumentFile.fromTreeUri(app, Uri.parse(root)) ?: return null
        val smriti = if (granted.name == "smriti") granted else findOrCreateDir(granted, "smriti") ?: return null
        val candidates = smriti.listFiles().filter { it.isDirectory && it.name == "snips" }
        val chosen = candidates.firstOrNull { hasChild(it.uri, fileName) }
            ?: candidates.firstOrNull()
            ?: smriti.createDirectory("snips")
            ?: return null
        candidates.filter { it.uri != chosen.uri && childCount(it.uri) == 0 }
            .forEach { runCatching { it.delete() } }
        snipsUri = chosen.uri
        Settings(app).driveSnipsUri = chosen.uri.toString()
        return chosen.uri
    }

    /** This device's jsonl URI. Reuses/dedups (keep largest), else creates once, persists it. */
    @Synchronized private fun deviceFileUri(): Uri? {
        fileUri?.let { if (docExists(it)) return it }
        val folder = snipsFolderUri() ?: return null
        val matches = childrenNamed(folder, fileName)
        val chosen = matches.maxByOrNull { it.second }?.first ?: createChild(folder, fileName)
        matches.filter { it.first != chosen }
            .forEach { runCatching { DocumentsContract.deleteDocument(app.contentResolver, it.first) } }
        chosen?.let { fileUri = it; Settings(app).driveFileUri = it.toString() }
        return chosen
    }

    // ---------- DocumentsContract helpers (single reliable query; DocumentFile.findFile is slow) ----------

    private fun findOrCreateDir(parent: DocumentFile, name: String): DocumentFile? =
        parent.listFiles().firstOrNull { it.isDirectory && it.name == name } ?: parent.createDirectory(name)

    private fun createChild(folderUri: Uri, name: String): Uri? = runCatching {
        DocumentsContract.createDocument(app.contentResolver, folderUri, "application/octet-stream", name)
    }.getOrNull()

    private fun docExists(uri: Uri): Boolean = runCatching {
        app.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
            ?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    private fun hasChild(folderUri: Uri, name: String): Boolean {
        var found = false
        forEachChild(folderUri) { _, n, _ -> if (n == name) found = true }
        return found
    }

    private fun childCount(folderUri: Uri): Int {
        var n = 0
        forEachChild(folderUri) { _, _, _ -> n++ }
        return n
    }

    private fun childrenNamed(folderUri: Uri, name: String): List<Pair<Uri, Long>> {
        val out = ArrayList<Pair<Uri, Long>>()
        forEachChild(folderUri) { u, n, size -> if (n == name) out.add(u to size) }
        return out
    }

    private fun allChildren(folderUri: Uri): List<Pair<Uri, String>> {
        val out = ArrayList<Pair<Uri, String>>()
        forEachChild(folderUri) { u, n, _ -> out.add(u to n) }
        return out
    }

    private inline fun forEachChild(folderUri: Uri, block: (Uri, String, Long) -> Unit) {
        val tree = Uri.parse(root)
        val parentId = DocumentsContract.getDocumentId(folderUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        app.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                val size = if (c.isNull(2)) 0L else c.getLong(2)
                block(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, size)
            }
        }
    }
}
