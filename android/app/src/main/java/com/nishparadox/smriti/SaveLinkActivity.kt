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

/**
 * Deep-link capture target — `smriti://save?text=…&url=…&title=…[&source=…]`.
 *
 * Lets the Firefox addon (or any tool) hand Smriti a web selection *with its URL* — the
 * provenance the plain Android share sheet can't provide. Saved as a WEB smaran with the
 * url + title in [Snip.metadata], no Whisper. Twin of [ShareActivity].
 */
class SaveLinkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data ?: run { finish(); return }
        val text = data.getQueryParameter("text")?.trim().orEmpty()
        if (text.isNotEmpty()) {
            val url = data.getQueryParameter("url")?.trim().orEmpty()
            val title = data.getQueryParameter("title")?.trim().orEmpty()
            // source = short display label: explicit param -> url's host -> "Web"
            val source = data.getQueryParameter("source")?.trim()?.takeIf { it.isNotEmpty() }
                ?: hostOf(url) ?: "Web"
            val metadata = buildMap {
                if (url.isNotEmpty()) put("url", url)
                if (title.isNotEmpty()) put("title", title)
            }
            SnipStore.ensureLoaded(this)
            DriveSync.init(this, Settings(this).driveRoot)
            val id = System.currentTimeMillis()
            SnipStore.add(
                Snip(
                    id = id, createdAt = id, status = SnipStatus.DONE, type = SmaranType.WEB,
                    text = text, source = source, device = DeviceId.of(this), metadata = metadata,
                )
            )
            DriveSync.syncMine()
            Toast.makeText(this, "Saved to Smriti", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun hostOf(url: String): String? =
        runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull()?.takeIf { it.isNotEmpty() }
}
