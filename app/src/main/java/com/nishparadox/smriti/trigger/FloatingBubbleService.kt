package com.nishparadox.smriti.trigger

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import com.nishparadox.smriti.capture.CaptureService
import kotlin.math.hypot

/**
 * Circular draggable overlay bubble. Tap = toggle snip (start / force-stop);
 * drag = move. Turns red with ■ while a snip is recording.
 */
class FloatingBubbleService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private val main = Handler(Looper.getMainLooper())

    private val idleColor = Color.parseColor("#8AB4F8")
    private val recColor = Color.parseColor("#E5484D")
    private val idleText = Color.parseColor("#0B1220")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        val sizePx = (58 * resources.displayMetrics.density).toInt()

        val tv = TextView(this).apply {
            text = "स्म"
            gravity = Gravity.CENTER
            setTextColor(idleText)
            textSize = 20f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(idleColor)
            }
            elevation = 16f
        }
        bubble = tv

        val lp = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 320 }

        var downX = 0f; var downY = 0f; var px = 0; var py = 0; var dragged = false
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; px = lp.x; py = lp.y; dragged = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = px + (e.rawX - downX).toInt()
                    lp.y = py + (e.rawY - downY).toInt()
                    if (hypot(e.rawX - downX, e.rawY - downY) > 20) dragged = true
                    bubble?.let { wm.updateViewLayout(it, lp) }; true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) CaptureService.instance?.requestSnip(); true
                }
                else -> false
            }
        }

        wm.addView(tv, lp)

        CaptureService.onSnipState = { active -> main.post { render(active) } }
    }

    private fun render(active: Boolean) {
        val tv = bubble ?: return
        tv.text = if (active) "■" else "स्म"
        tv.setTextColor(if (active) Color.WHITE else idleText)
        (tv.background as? GradientDrawable)?.setColor(if (active) recColor else idleColor)
    }

    override fun onDestroy() {
        CaptureService.onSnipState = null
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        super.onDestroy()
    }
}
