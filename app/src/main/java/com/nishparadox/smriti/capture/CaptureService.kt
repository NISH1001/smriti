package com.nishparadox.smriti.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.nishparadox.smriti.MainActivity
import com.nishparadox.smriti.notes.DeviceId
import com.nishparadox.smriti.notes.DriveSync
import com.nishparadox.smriti.notes.Snip
import com.nishparadox.smriti.notes.SnipStatus
import com.nishparadox.smriti.notes.SnipStore
import com.nishparadox.smriti.output.NotificationOutput
import com.nishparadox.smriti.settings.Settings
import com.nishparadox.smriti.transcribe.WhisperTranscriber
import com.nishparadox.smriti.trigger.FloatingBubbleService
import com.nishparadox.smriti.trigger.SnipEntryPoint
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Foreground service: captures playback audio from whitelisted app UIDs into a
 * rolling [RingBuffer]; [requestSnip] toggles a snip — first tap starts a [-N, +(K-N)]
 * clip, a second tap force-finalizes it early. Each snip is tracked in [SnipStore]
 * (Recording -> Transcribing -> Done) and posted as a notification when transcribed.
 */
class CaptureService : Service(), SnipEntryPoint {
    companion object {
        @Volatile var instance: CaptureService? = null
        /** Set by the floating bubble to reflect snip state (true = recording). */
        @Volatile var onSnipState: ((Boolean) -> Unit)? = null
        /** Set by MainActivity to keep its Start/Stop button in sync with the actual session. */
        @Volatile var onRunningChanged: ((Boolean) -> Unit)? = null
        const val MODEL_ASSET = "models/ggml-tiny.en-q5_1.bin"
        const val TAG = "SMRITI"
        const val ACTION_STOP = "com.nishparadox.smriti.STOP"
    }

    private val sr = 16000
    private var preRoll = 5
    private var total = 10
    private var sourceLabel = ""
    private var deviceId = ""
    @Volatile private var snipStartMs = 0L
    private lateinit var ring: RingBuffer
    private var record: AudioRecord? = null
    private var projection: MediaProjection? = null
    private val running = AtomicBoolean(false)
    private val snipRef = AtomicReference<SnipController?>(null)
    private val main = Handler(Looper.getMainLooper())

    private val transcribeExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var transcriber: WhisperTranscriber? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == ACTION_STOP) {
            stopService(Intent(this, FloatingBubbleService::class.java))
            stopSelf()
            return START_NOT_STICKY
        }
        preRoll = intent.getIntExtra("preRoll", 5)
        total = intent.getIntExtra("total", 10)
        val uids = intent.getIntArrayExtra("uids") ?: intArrayOf()
        sourceLabel = intent.getStringExtra("sourceLabel") ?: ""
        deviceId = DeviceId.of(this)
        ring = RingBuffer(sr * preRoll)
        SnipStore.ensureLoaded(this)
        DriveSync.init(this, Settings(this).driveRoot)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("cap", "Capture", NotificationManager.IMPORTANCE_LOW)
        )
        val contentPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        startForeground(
            1,
            NotificationCompat.Builder(this, "cap")
                .setContentTitle("Smriti listening")
                .setContentText("Tap the स्म bubble to snip · tap here to open")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(contentPi)
                .addAction(0, "Stop listening", stopPi)
                .setOngoing(true)
                .build()
        )

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val code = intent.getIntExtra("code", 0)
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        projection = mpm.getMediaProjection(code, data).apply {
            registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { running.set(false); stopSelf() }
            }, main)
        }

        val cb = AudioPlaybackCaptureConfiguration.Builder(projection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
        for (u in uids) cb.addMatchingUid(u)

        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sr)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBuf = AudioRecord.getMinBufferSize(
            sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        record = AudioRecord.Builder()
            .setAudioFormat(fmt)
            .setBufferSizeInBytes(minBuf * 2)
            .setAudioPlaybackCaptureConfig(cb.build())
            .build()

        instance = this
        running.set(true)
        onRunningChanged?.invoke(true)
        record!!.startRecording()
        thread(name = "smriti-capture") {
            val buf = ShortArray(minBuf)
            while (running.get()) {
                val n = record!!.read(buf, 0, buf.size)
                if (n > 0) {
                    ring.write(buf, n)
                    val s = snipRef.get()
                    if (s != null && s.feed(buf, n) && snipRef.compareAndSet(s, null)) {
                        notifySnip(false)
                        finalizeClip(s.result(), s.noteId)
                    }
                }
            }
        }
        return START_STICKY
    }

    /** First tap starts a snip; second tap force-finalizes the current one. */
    override fun requestSnip() {
        val s = snipRef.get()
        if (s == null) {
            val preRollBuf = ring.snapshot()
            if (isSilent(preRollBuf)) {           // nothing playing -> skip the whole pipeline
                toast("No audio playing — nothing to snip")
                return
            }
            val sc = SnipController(sr)
            sc.begin(preRollBuf, total)
            val id = System.currentTimeMillis()
            sc.noteId = id
            snipStartMs = id
            snipRef.set(sc)
            SnipStore.add(Snip(id = id, createdAt = id, status = SnipStatus.RECORDING, source = sourceLabel, durationS = total, device = deviceId))
            notifySnip(true)
            Log.i(TAG, "snip started preRoll=$preRoll total=$total")
        } else if (snipRef.compareAndSet(s, null)) {
            notifySnip(false)
            if (System.currentTimeMillis() - snipStartMs < 2000L) {   // tapped stop right away -> cancel
                SnipStore.delete(s.noteId)
                toast("Snip cancelled")
                Log.i(TAG, "snip cancelled (stopped <2s)")
            } else {
                Log.i(TAG, "snip force-stopped")
                finalizeClip(s.result(), s.noteId)
            }
        }
    }

    private fun notifySnip(active: Boolean) {
        main.post { onSnipState?.invoke(active) }
    }

    private fun finalizeClip(clip: ShortArray, noteId: Long) {
        val durationS = clip.size / sr
        // Backstop for audio that stopped mid-snip: discard premature / near-silent clips.
        if (clip.size < preRoll * sr || isSilent(clip)) {
            SnipStore.delete(noteId)
            Log.i(TAG, "discarded snip (short/silent): ${durationS}s")
            return
        }
        val dir = File(filesDir, "clips").apply { mkdirs() }
        val wav = File(dir, "${System.nanoTime()}.wav")
        WavWriter.write(clip, sr, wav)
        Log.i(TAG, "saved ${wav.name} samples=${clip.size}")
        SnipStore.update(noteId) { it.copy(status = SnipStatus.TRANSCRIBING, durationS = durationS) }
        val floats = FloatArray(clip.size) { clip[it] / 32768f }
        transcribeExecutor.execute {
            val t = ensureTranscriber()
            if (t == null || !t.loaded) {
                SnipStore.update(noteId) { it.copy(status = SnipStatus.FAILED, text = "(model not loaded)") }
                return@execute
            }
            val text = cleanTranscript(t.transcribe(floats))
            Log.i(TAG, "transcript: '$text'")
            if (text.isBlank()) {                 // silence / no speech -> drop, don't clutter the list
                SnipStore.delete(noteId)
                return@execute
            }
            SnipStore.update(noteId) { it.copy(status = SnipStatus.DONE, text = text) }
            NotificationOutput.showTranscript(this, text)
        }
    }

    /** True if the loudest sample is essentially silence (nothing was playing). */
    private fun isSilent(clip: ShortArray): Boolean {
        if (clip.isEmpty()) return true
        var peak = 0
        for (v in clip) { val a = if (v < 0) -v.toInt() else v.toInt(); if (a > peak) peak = a }
        return peak < 150
    }

    /** Strip Whisper non-speech markers like [BLANK_AUDIO], [MUSIC]; blank => nothing to keep. */
    private fun cleanTranscript(text: String): String =
        text.replace(Regex("\\[[^\\]]*\\]"), "").trim()

    private fun toast(msg: String) {
        main.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun ensureTranscriber(): WhisperTranscriber? {
        transcriber?.let { return it }
        return try {
            val modelFile = File(filesDir, "ggml-tiny.en-q5_1.bin")
            if (!modelFile.exists()) {
                assets.open(MODEL_ASSET).use { input ->
                    modelFile.outputStream().use { input.copyTo(it) }
                }
            }
            WhisperTranscriber(modelFile.absolutePath).also { transcriber = it }
        } catch (e: Exception) {
            Log.e(TAG, "transcriber init failed", e); null
        }
    }

    override fun onDestroy() {
        running.set(false)
        onSnipState = null
        runCatching { record?.stop(); record?.release() }
        runCatching { projection?.stop() }
        transcribeExecutor.shutdown()
        instance = null
        onRunningChanged?.invoke(false)
        super.onDestroy()
    }
}
