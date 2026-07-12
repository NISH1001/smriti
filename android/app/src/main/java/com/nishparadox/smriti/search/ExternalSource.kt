package com.nishparadox.smriti.search

/**
 * A read-only external place to *find* smarans (never own them): a GitHub-synced repo now, a
 * Logseq local API on macOS later. Distinct from the local [SearchIndex], which returns owned
 * smaran ids — external sources return [ExternalHit]s that link out and are never persisted.
 */
interface ExternalSource {
    /** Shown on the footer, e.g. "Logseq". */
    val label: String
    /** Whether this source is configured/usable right now (e.g. a token + repos are set). */
    val isEnabled: Boolean
    /** Ranked-by-the-source hits for the query; empty on no match or failure. Runs on demand. */
    suspend fun search(query: String): List<ExternalHit>
}

/**
 * One external search result — a read-only card that opens its [url]. [sourceLabel] is shown as the
 * card's source badge (e.g. the repo name); not stored in the jsonl.
 */
data class ExternalHit(
    val sourceLabel: String,
    val title: String,        // e.g. the file path / page name
    val snippet: String,      // matching text fragment
    val url: String,          // browser fallback (the source page)
    val repoFullName: String, // owner/repo — to fetch the file via the Contents API (token-authed)
    val path: String,         // file path within the repo
)
