package com.nishparadox.smriti.trigger

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import com.nishparadox.smriti.capture.CaptureService
import kotlin.math.hypot

/** Draggable overlay button that fires a snip. Tap = snip, drag = move (no snip). */
class FloatingBubbleService : Service() {
    private lateinit var wm: WindowManager
    private var view: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        val btn = Button(this).apply {
            text = "स्म"
            setBackgroundColor(Color.parseColor("#CC1E293B"))
            setTextColor(Color.WHITE)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 320 }

        var downX = 0f; var downY = 0f; var px = 0; var py = 0; var dragged = false
        btn.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; px = lp.x; py = lp.y; dragged = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = px + (e.rawX - downX).toInt()
                    lp.y = py + (e.rawY - downY).toInt()
                    if (hypot(e.rawX - downX, e.rawY - downY) > 20) dragged = true
                    view?.let { wm.updateViewLayout(it, lp) }; true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) CaptureService.instance?.requestSnip(); true
                }
                else -> false
            }
        }
        view = btn
        wm.addView(btn, lp)
    }

    override fun onDestroy() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        super.onDestroy()
    }
}
