package com.nishparadox.smriti.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Streams a grounded answer from a loaded GGUF model. The model is loaded once and reused across
 * queries (reloaded only if the selected model changes). [generate] returns a cold [Flow] of token
 * strings, so the UI can render tokens live; it runs on a background dispatcher and a [Mutex]
 * serialises access (llama.cpp is not thread-safe).
 */
object RagGenerator {
    private val mutex = Mutex()
    private var handle = 0L
    private var loadedPath: String? = null
    private var loadedCtx = 0

    fun generate(
        modelPath: String, system: String, user: String,
        maxTokens: Int = 512, nCtx: Int = 4096,
    ): Flow<String> =
        flow {
            val cap = nCtx.coerceIn(1024, 8192)   // memory guard: never allocate a huge KV on a phone
            mutex.withLock {
                if (handle == 0L || loadedPath != modelPath || loadedCtx != cap) {
                    if (handle != 0L) { LlamaNative.freeModel(handle); handle = 0L }
                    // cap = context-window ceiling; native uses min(model-trained, cap).
                    handle = LlamaNative.loadModel(modelPath, cap, threads())
                    loadedPath = if (handle != 0L) modelPath else null
                    loadedCtx = if (handle != 0L) cap else 0
                }
                val h = handle
                if (h == 0L) throw IllegalStateException("RAG model failed to load")
                if (!LlamaNative.startGen(h, system, user, maxTokens)) return@withLock
                while (currentCoroutineContext().isActive) {
                    val tok = LlamaNative.nextToken(h) ?: break
                    emit(tok)
                }
            }
        }.flowOn(Dispatchers.Default)

    /** Free the loaded model + its KV cache, reclaiming RAM. Safe anytime; the next [generate]
     *  reloads from disk (~1–2 s). Called on memory pressure (onTrimMemory) and when RAG is off.
     *  Non-blocking: if a generation holds the lock, it's skipped — freeing mid-decode would crash. */
    fun unload() {
        if (mutex.tryLock()) {
            try {
                if (handle != 0L) { LlamaNative.freeModel(handle); handle = 0L }
                loadedPath = null; loadedCtx = 0
            } finally { mutex.unlock() }
        }
    }

    private fun threads(): Int =
        (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)
}
