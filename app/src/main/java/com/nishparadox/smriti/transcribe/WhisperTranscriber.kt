package com.nishparadox.smriti.transcribe

/** Whisper.cpp-backed transcriber. [modelPath] is a ggml .bin on the filesystem. */
class WhisperTranscriber(modelPath: String) : Transcriber {
    private val ctx: Long = WhisperNative.init(modelPath)
    val loaded: Boolean get() = ctx != 0L

    override fun transcribe(pcm: FloatArray): String {
        if (ctx == 0L) return ""
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        return WhisperNative.transcribe(ctx, pcm, threads).trim()
    }

    fun close() { if (ctx != 0L) WhisperNative.free(ctx) }
}
