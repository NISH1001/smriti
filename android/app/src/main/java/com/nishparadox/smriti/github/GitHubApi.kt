package com.nishparadox.smriti.github

import android.util.Log
import com.nishparadox.smriti.search.ExternalHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Thin GitHub REST client (token-authed) — same approach as [com.nishparadox.smriti.update.UpdateChecker]:
 * [HttpURLConnection] + org.json, no third-party HTTP lib. Used only for the optional connector.
 */
object GitHubApi {
    private const val TAG = "SMRITI"
    private const val API = "https://api.github.com"

    data class Repo(val fullName: String, val private: Boolean)

    /** Confirm the token; returns the account login, or null if invalid/unreachable. */
    suspend fun verify(token: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("$API/user", token) ?: return@withContext null
            JSONObject(body).optString("login").ifEmpty { null }
        }.getOrElse { Log.w(TAG, "gh verify failed", it); null }
    }

    /** Repos the token can access (most-recently-updated first, first 100). */
    suspend fun listRepos(token: String, page: Int = 1): List<Repo> = withContext(Dispatchers.IO) {
        runCatching {
            // affiliation=owner → the user's OWN repos first (no org/collab noise); page = "load more".
            val body = get("$API/user/repos?affiliation=owner&per_page=100&sort=updated&page=$page", token)
                ?: return@withContext emptyList()
            val arr = JSONArray(body)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Repo(o.optString("full_name"), o.optBoolean("private"))
            }
        }.getOrElse { Log.w(TAG, "gh listRepos failed", it); emptyList() }
    }

    /**
     * Code search across [repos] in ONE request (multiple `repo:` qualifiers OR together), so N
     * repos cost a single call and stay under the 10/min code-search limit. Token-match, not BM25.
     */
    suspend fun searchCode(token: String, query: String, repos: Collection<String>): List<ExternalHit> =
        withContext(Dispatchers.IO) {
            if (repos.isEmpty() || query.isBlank()) return@withContext emptyList()
            runCatching {
                val q = query + repos.joinToString("") { " repo:$it" }
                val url = "$API/search/code?per_page=30&q=" + URLEncoder.encode(q, "UTF-8")
                val body = get(url, token, "application/vnd.github.text-match+json") ?: return@withContext emptyList()
                val items = JSONObject(body).optJSONArray("items") ?: return@withContext emptyList()
                (0 until items.length()).map {
                    val o = items.getJSONObject(it)
                    val repo = o.optJSONObject("repository")?.optString("full_name") ?: ""
                    val path = o.optString("path")
                    ExternalHit(
                        sourceLabel = repo.substringAfterLast('/').ifEmpty { "GitHub" },
                        title = path,
                        snippet = firstFragment(o) ?: path,
                        url = o.optString("html_url"),
                        repoFullName = repo,
                        path = path,
                    )
                }
            }.getOrElse { Log.w(TAG, "gh searchCode failed", it); emptyList() }
        }

    private fun firstFragment(item: JSONObject): String? {
        val tm = item.optJSONArray("text_matches") ?: return null
        for (i in 0 until tm.length()) {
            val frag = tm.getJSONObject(i).optString("fragment").trim()
            if (frag.isNotEmpty()) return frag.replace(Regex("\\s+"), " ").take(200)
        }
        return null
    }

    /** File content (token-authed, so private repos work) — raw bytes as text. Null on failure. */
    suspend fun fetchFile(token: String, repoFullName: String, path: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val encPath = path.split("/").joinToString("/") {
                    URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                }
                get("$API/repos/$repoFullName/contents/$encPath", token, "application/vnd.github.raw")
            }.getOrElse { Log.w(TAG, "gh fetchFile failed", it); null }
        }

    private fun get(url: String, token: String, accept: String = "application/vnd.github+json"): String? {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", accept)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "smriti-android")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connectTimeout = 8000; readTimeout = 10000
        }
        return try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    }
}
