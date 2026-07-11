package com.nishparadox.smriti.transcribe

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * On-demand Whisper model. NOT bundled in the APK — downloaded once from Hugging Face into app
 * storage when the user enables Audio transcription, then cached forever (offline thereafter).
 * Keeps the APK ~7 MB and lets web/text-only users skip the 31 MB entirely.
 */
object ModelManager {
    const val FILE = "ggml-tiny.en-q5_1.bin"
    private const val MODEL_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q5_1.bin"
    private const val EXPECTED_BYTES = 32_166_155L
    private const val TAG = "SMRITI"

    fun modelFile(ctx: Context): File = File(ctx.filesDir, FILE)

    /** True once the fully-downloaded, size-verified model is on disk. */
    fun isPresent(ctx: Context): Boolean = modelFile(ctx).length() == EXPECTED_BYTES

    fun delete(ctx: Context): Boolean = modelFile(ctx).delete()

    /** Stream to a temp file (reporting 0..100), verify exact size, then atomically rename in. */
    suspend fun download(ctx: Context, onProgress: (Int) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val dest = modelFile(ctx)
            val tmp = File(ctx.filesDir, "$FILE.part")
            runCatching {
                val c = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000; readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "smriti-android")
                }
                if (c.responseCode != 200) { c.disconnect(); return@runCatching false }
                val total = c.contentLengthLong.takeIf { it > 0 } ?: EXPECTED_BYTES
                c.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var lastPct = -1
                        var n = input.read(buf)
                        while (n >= 0) {
                            out.write(buf, 0, n); read += n
                            val pct = ((read * 100) / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                            n = input.read(buf)
                        }
                    }
                }
                c.disconnect()
                if (tmp.length() == EXPECTED_BYTES) {
                    dest.delete(); tmp.renameTo(dest)
                } else {
                    tmp.delete(); false
                }
            }.getOrElse { Log.w(TAG, "model download failed", it); tmp.delete(); false }
        }
}
