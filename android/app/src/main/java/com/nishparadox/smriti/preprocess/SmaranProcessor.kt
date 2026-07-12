package com.nishparadox.smriti.preprocess

/**
 * Turns a raw external note into clean prose for RAG context. Each [com.nishparadox.smriti.search.ExternalSource]
 * owns the processor for its format, so format-specific noise-stripping never leaks into the UI or
 * the generator — add a new note tool by adding a processor, not by editing the answer pipeline.
 *
 * These clean content for the *model*; the in-app viewer still shows the raw note verbatim.
 */
interface SmaranProcessor {
    fun process(raw: String): String
}

/** Identity — plain text or an unknown format. */
object PlainProcessor : SmaranProcessor {
    override fun process(raw: String) = raw.trim()
}

/** Strips a leading YAML/`---` frontmatter block (common across markdown note tools). */
object MarkdownFrontmatterProcessor : SmaranProcessor {
    private val FRONTMATTER = Regex("(?s)^\\uFEFF?\\s*---\\s*\\n.*?\\n---\\s*\\n")
    override fun process(raw: String) = raw.replace(FRONTMATTER, "").trim()
}

/**
 * Logseq markdown. On top of [MarkdownFrontmatterProcessor] it drops `key:: value` page/block
 * properties, `{{embed …}}` blocks, `id::` lines and bullet markers — leaving the actual prose a
 * small model can answer from (raw Logseq is mostly metadata in its first few hundred chars).
 */
object LogseqProcessor : SmaranProcessor {
    private val PROPERTY = Regex("^[\\p{L}][\\p{L}\\p{N} /_-]*::\\s")
    override fun process(raw: String): String =
        MarkdownFrontmatterProcessor.process(raw)
            .lineSequence()
            .map { it.trim().removePrefix("- ").removePrefix("* ").trim() }
            .filter { it.isNotEmpty() }
            .filterNot { PROPERTY.containsMatchIn(it) }         // `SOURCE:: …`, `Total Time:: …`
            .filterNot { it.startsWith("{{") || it.startsWith("id::") }
            .joinToString("\n")
            .trim()
}

/** Runs [steps] left-to-right — compose format layers (e.g. frontmatter then tool-specific). */
class ChainProcessor(private vararg val steps: SmaranProcessor) : SmaranProcessor {
    override fun process(raw: String) = steps.fold(raw) { acc, p -> p.process(acc) }
}
