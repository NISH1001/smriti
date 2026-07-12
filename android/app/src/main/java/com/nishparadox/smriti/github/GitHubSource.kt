package com.nishparadox.smriti.github

import com.nishparadox.smriti.preprocess.LogseqProcessor
import com.nishparadox.smriti.preprocess.SmaranProcessor
import com.nishparadox.smriti.search.ExternalHit
import com.nishparadox.smriti.search.ExternalSource

/**
 * External search over the user's selected GitHub repos (their Logseq graph etc.) via
 * [GitHubApi] code search. Enabled only when a token + at least one repo are configured.
 */
class GitHubSource(private val conn: GitHubConnection) : ExternalSource {
    override val label: String get() = "Logseq"   // TODO(v0.6.x): per-connection custom label

    override val isEnabled: Boolean get() = conn.isConnected

    // The GitHub connector points at a Logseq graph, so its notes are Logseq markdown.
    private val processor: SmaranProcessor = LogseqProcessor

    override suspend fun search(query: String): List<ExternalHit> {
        val token = conn.token ?: return emptyList()
        return GitHubApi.searchCode(token, query, conn.repos)
    }

    override suspend fun content(hit: ExternalHit): String? {
        val token = conn.token ?: return null
        val raw = GitHubApi.fetchFile(token, hit.repoFullName, hit.path) ?: return null
        return processor.process(raw).ifBlank { null }
    }
}
