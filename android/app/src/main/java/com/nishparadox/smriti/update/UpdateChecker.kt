package com.nishparadox.smriti.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sideload update check against **public** GitHub Releases. Lists releases, keeps those tagged
 * `android/vX.Y.Z`, and compares the highest to the running version. No auth (public repo), no
 * third-party HTTP lib — just [HttpURLConnection] + org.json. See ROADMAP.md release convention.
 */
object UpdateChecker {
    private const val TAG = "SMRITI"
    private const val RELEASES = "https://api.github.com/repos/NISH1001/smriti/releases?per_page=30"
    private const val TAG_PREFIX = "android/v"

    sealed interface Result {
        data class Available(val version: String, val url: String) : Result
        data object UpToDate : Result
        data object Failed : Result
    }

    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        runCatching {
            val body = fetch(RELEASES) ?: return@withContext Result.Failed
            val arr = JSONArray(body)
            var best: IntArray? = null
            var bestVer = ""
            var bestUrl = ""
            for (i in 0 until arr.length()) {
                val rel = arr.getJSONObject(i)
                if (rel.optBoolean("draft") || rel.optBoolean("prerelease")) continue
                val tag = rel.optString("tag_name")
                if (!tag.startsWith(TAG_PREFIX)) continue
                val ver = tag.removePrefix(TAG_PREFIX)
                val parts = parse(ver) ?: continue
                if (best == null || compare(parts, best!!) > 0) {
                    best = parts; bestVer = ver; bestUrl = rel.optString("html_url")
                }
            }
            val latest = best ?: return@withContext Result.UpToDate
            val current = parse(currentVersion) ?: intArrayOf(0, 0, 0)
            if (compare(latest, current) > 0) Result.Available(bestVer, bestUrl) else Result.UpToDate
        }.getOrElse { Log.w(TAG, "update check failed", it); Result.Failed }
    }

    private fun fetch(url: String): String? {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "smriti-android")
            connectTimeout = 8000; readTimeout = 8000
        }
        return try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    }

    /** "0.1.0" -> [0,1,0]; tolerant (missing parts = 0, trailing suffixes ignored); null if unparseable. */
    private fun parse(v: String): IntArray? {
        val nums = v.trim().split(".").map { it.takeWhile(Char::isDigit) }
        if (nums.isEmpty() || nums[0].isEmpty()) return null
        return IntArray(3) { nums.getOrNull(it)?.toIntOrNull() ?: 0 }
    }

    private fun compare(a: IntArray, b: IntArray): Int {
        for (i in 0 until 3) if (a[i] != b[i]) return a[i] - b[i]
        return 0
    }
}
