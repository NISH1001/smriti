package com.nishparadox.smriti

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.nishparadox.smriti.notes.DeviceId
import com.nishparadox.smriti.notes.DriveSync
import com.nishparadox.smriti.notes.SmaranType
import com.nishparadox.smriti.notes.Snip
import com.nishparadox.smriti.notes.SnipStatus
import com.nishparadox.smriti.notes.SnipStore
import com.nishparadox.smriti.settings.Settings
import org.json.JSONArray

/**
 * Deep-link capture target — `smriti://save?…`.
 *
 * **Any** app or link can fire this intent (a Firefox addon is the first caller, but it's not
 * web-specific): it hands Smriti one or many text selections *with their source URLs* —
 * provenance the plain Android share sheet can't provide. Saved as WEB smaran(s), no Whisper.
 *
 * One endpoint, two shapes:
 *  - single: `smriti://save?text=…&url=…&title=…[&source=…]`
 *  - bulk:   `smriti://save?items=[{"text":…,"url":…,"title":…,"source":…}, …]`  (one toast)
 *
 * Bulk keeps per-item provenance, so highlights from different pages batch together fine.
 */
class SaveLinkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data ?: run { finish(); return }
        SnipStore.ensureLoaded(this)
        DriveSync.init(this, Settings(this).driveRoot)

        val itemsParam = data.getQueryParameter("items")
        val saved = if (!itemsParam.isNullOrBlank()) {
            saveBulk(itemsParam)
        } else {
            val text = data.getQueryParameter("text")?.trim().orEmpty()
            if (text.isNotEmpty()) {
                saveOne(
                    System.currentTimeMillis(), text,
                    data.getQueryParameter("url"), data.getQueryParameter("title"),
                    data.getQueryParameter("source"),
                )
                1
            } else 0
        }

        if (saved > 0) {
            DriveSync.syncMine()   // one debounced push regardless of count
            Toast.makeText(
                this, if (saved == 1) "Saved to Smriti" else "Saved $saved to Smriti", Toast.LENGTH_SHORT
            ).show()
        }
        finish()
    }

    /** Parse `items` as a JSON array of {text,url,title,source}; save each as a WEB smaran. */
    private fun saveBulk(json: String): Int {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return 0
        val base = System.currentTimeMillis()
        var count = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val text = o.optString("text").trim()
            if (text.isEmpty()) continue
            // base + count keeps ids unique + createdAt monotonic across the batch
            saveOne(base + count, text, o.optString("url"), o.optString("title"), o.optString("source"))
            count++
        }
        return count
    }

    private fun saveOne(id: Long, text: String, url: String?, title: String?, source: String?) {
        val u = url?.trim().orEmpty()
        val t = title?.trim().orEmpty()
        val src = source?.trim()?.takeIf { it.isNotEmpty() } ?: hostOf(u) ?: "Web"
        val meta = buildMap {
            if (u.isNotEmpty()) put("url", u)
            if (t.isNotEmpty()) put("title", t)
        }
        SnipStore.add(
            Snip(
                id = id, createdAt = id, status = SnipStatus.DONE, type = SmaranType.WEB,
                text = text, source = src, device = DeviceId.of(this), metadata = meta,
            )
        )
    }

    private fun hostOf(url: String): String? =
        runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull()?.takeIf { it.isNotEmpty() }
}
