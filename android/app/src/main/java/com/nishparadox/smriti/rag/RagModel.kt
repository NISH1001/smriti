package com.nishparadox.smriti.rag

/**
 * Curated set of small GGUF models (≈230M–1B) for on-device RAG — vetted for llama.cpp + the right
 * chat template, so the model picker can't land on something that won't run. All hosted on ungated
 * repos (the official `google/…` QAT GGUFs are license-gated, so we can't fetch them unauthenticated;
 * `ggml-org` is the llama.cpp team's own org and re-hosts them ungated). Download-on-demand.
 */
enum class RagModel(
    val id: String,
    val label: String,
    val sizeMb: Int,
    val fileName: String,
    val url: String,
) {
    LFM2_230M(
        "lfm2.5-230m", "LFM2.5 230M · fastest", 153,
        "LFM2.5-230M-Q4_K_M.gguf",
        "https://huggingface.co/LiquidAI/LFM2.5-230M-GGUF/resolve/main/LFM2.5-230M-Q4_K_M.gguf",
    ),
    LFM2_350M(
        "lfm2.5-350m", "LFM2.5 350M · recommended", 229,
        "LFM2.5-350M-Q4_K_M.gguf",
        "https://huggingface.co/LiquidAI/LFM2.5-350M-GGUF/resolve/main/LFM2.5-350M-Q4_K_M.gguf",
    ),
    GEMMA3_270M(
        "gemma3-270m", "Gemma 3 270M · open license", 253,
        "google_gemma-3-270m-it-Q4_K_M.gguf",
        "https://huggingface.co/bartowski/google_gemma-3-270m-it-GGUF/resolve/main/google_gemma-3-270m-it-Q4_K_M.gguf",
    ),
    GEMMA3_1B(
        "gemma3-1b", "Gemma 3 1B · best answers", 806,
        "gemma-3-1b-it-Q4_K_M.gguf",
        "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
    );

    companion object {
        val DEFAULT = LFM2_350M
        fun byId(id: String): RagModel = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
