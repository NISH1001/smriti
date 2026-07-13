package com.nishparadox.smriti.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * Stub listener — receives no callbacks and reads no notifications. It exists only because
 * [MediaSessionManager.getActiveSessions] is gated on the user granting this component
 * Notification access (the same gate "now playing history" apps use).
 */
class NowPlayingListener : NotificationListenerService()

/**
 * What's playing right now in the watched apps, from their active MediaSessions — so an audio
 * smaran carries *what* was heard (book/track title, album, author), not just which app it
 * came from. Returns empty (never throws) when access isn't granted or nothing is playing.
 */
object NowPlaying {
    fun hasAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /**
     * Now-playing tags for [pkgs] as metadata keys (`title` / `album` / `artist` / `app`).
     * Prefers the session actually in STATE_PLAYING; players disagree on where the book name
     * lives (Audiobookshelf: title=chapter, album=book; music apps: title=track), so all three
     * are kept raw rather than normalised.
     */
    fun tags(ctx: Context, pkgs: Set<String>): Map<String, String> = runCatching {
        val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return emptyMap()
        val sessions = msm.getActiveSessions(ComponentName(ctx, NowPlayingListener::class.java))
            .filter { it.packageName in pkgs }
        val ctl = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
            ?: return emptyMap()
        val md = ctl.metadata ?: return emptyMap()
        fun first(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { md.getString(it)?.trim()?.ifBlank { null } }
        buildMap {
            first(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?.let { put("title", it) }
            first(MediaMetadata.METADATA_KEY_ALBUM)?.let { put("album", it) }
            first(MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?.let { put("artist", it) }
            put("app", ctl.packageName)
        }
    }.getOrDefault(emptyMap())   // SecurityException if access was revoked mid-session
}
