package com.nishparadox.smriti.output

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

object NotificationOutput {
    private const val CHANNEL = "snip"

    fun showTranscript(ctx: Context, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Snips", NotificationManager.IMPORTANCE_HIGH)
        )
        val body = text.ifBlank { "(no speech detected)" }
        nm.notify(
            (System.nanoTime() and 0xFFFF).toInt(),
            NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Smriti snip")
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentText(body.take(60))
                .setAutoCancel(true)
                .build()
        )
    }
}
