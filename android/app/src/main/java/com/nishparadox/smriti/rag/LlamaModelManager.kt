package com.nishparadox.smriti.rag

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * On-demand GGUF download for the selected [RagModel] — same shape as the Whisper
 * `transcribe/ModelManager`: stream to `<name>.part`, verify against Content-Length, atomically
 * rename in. Each model lives at its own filename so switching between already-downloaded models
 * is instant. Nothing is bundled in the APK.
 */
object LlamaModelManager {
    private const val TAG = "SMRITI"

    fun modelFile(ctx: Context, model: RagModel): File = File(ctx.filesDir, model.fileName)

    /** A fully-downloaded model exists (the temp file is only renamed in on a complete download). */
    fun isPresent(ctx: Context, model: RagModel): Boolean = modelFile(ctx, model).exists()

    fun delete(ctx: Context, model: RagModel): Boolean = modelFile(ctx, model).delete()

    suspend fun download(ctx: Context, model: RagModel, onProgress: (Int) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val dest = modelFile(ctx, model)
            val tmp = File(ctx.filesDir, "${model.fileName}.part")
            runCatching {
                val c = (URL(model.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000; readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "smriti-android")
                }
                if (c.responseCode != 200) { c.disconnect(); return@runCatching false }
                val total = c.contentLengthLong
                c.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L; var lastPct = -1
                        var n = input.read(buf)
                        while (n >= 0) {
                            out.write(buf, 0, n); read += n
                            if (total > 0) {
                                val pct = ((read * 100) / total).toInt().coerceIn(0, 100)
                                if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                            }
                            n = input.read(buf)
                        }
                    }
                }
                c.disconnect()
                if (total <= 0 || tmp.length() == total) {
                    dest.delete(); tmp.renameTo(dest)
                } else {
                    tmp.delete(); false
                }
            }.getOrElse { Log.w(TAG, "llama model download failed", it); tmp.delete(); false }
        }
}
