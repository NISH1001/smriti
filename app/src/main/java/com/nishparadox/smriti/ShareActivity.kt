package com.nishparadox.smriti

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.nishparadox.smriti.notes.DeviceId
import com.nishparadox.smriti.notes.DriveSync
import com.nishparadox.smriti.notes.Snip
import com.nishparadox.smriti.notes.SnipStatus
import com.nishparadox.smriti.notes.SnipStore
import com.nishparadox.smriti.settings.Settings

/**
 * Receives shared text (ACTION_SEND, text/plain) and saves it as a smaran — the "share to
 * Smriti" capture path that lets any text selection from any app land in the memory list.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (!text.isNullOrEmpty()) {
            SnipStore.ensureLoaded(this)
            DriveSync.init(this, Settings(this).driveRoot)
            val src = runCatching {
                referrer?.host?.let { pkg ->
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
                }
            }.getOrNull() ?: "Shared"
            val id = System.currentTimeMillis()
            SnipStore.add(
                Snip(id = id, createdAt = id, status = SnipStatus.DONE, text = text, source = src, device = DeviceId.of(this))
            )
            DriveSync.syncMine()
            Toast.makeText(this, "Saved to Smriti", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
