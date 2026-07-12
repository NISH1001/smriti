package com.nishparadox.smriti.rag

/**
 * JNI bridge to llama.cpp (`libsmriti_llama.so`) — the on-device RAG generator. Incremental so
 * Kotlin can stream tokens: [startGen] prefills the prompt, [nextToken] yields one token at a time
 * (null when done). Not thread-safe — callers serialize (see [RagGenerator]).
 */
object LlamaNative {
    init { System.loadLibrary("smriti_llama") }

    external fun systemInfo(): String

    /** Load a GGUF model; returns an opaque handle (0 = failure). */
    external fun loadModel(path: String, nCtx: Int, nThreads: Int): Long

    /** Apply the model's chat template to (system,user), tokenize, and prefill. */
    external fun startGen(handle: Long, system: String, user: String, maxTokens: Int): Boolean

    /** Next generated token as text, or null when generation is complete. */
    external fun nextToken(handle: Long): String?

    external fun freeModel(handle: Long)
}
