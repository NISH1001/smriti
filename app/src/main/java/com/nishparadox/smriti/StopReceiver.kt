package com.nishparadox.smriti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nishparadox.smriti.capture.CaptureService
import com.nishparadox.smriti.trigger.FloatingBubbleService

/**
 * Notification "Stop listening" action. A broadcast (not startService) — it's delivered even
 * when the app is in the background, so stopping the session from the shade actually works.
 */
class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i("SMRITI", "StopReceiver -> stopping session + bubble")
        context.stopService(Intent(context, CaptureService::class.java))
        context.stopService(Intent(context, FloatingBubbleService::class.java))
    }
}
