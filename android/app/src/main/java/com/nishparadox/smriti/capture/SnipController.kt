package com.nishparadox.smriti.capture

/**
 * Assembles a clip from a pre-roll snapshot plus forward PCM chunks, using sample
 * counts (no wall clock). Complete once it reaches [totalSeconds] of audio, or when
 * the user force-stops (service reads [result] early). Thread-safe: the capture thread
 * calls [feed] while the trigger thread may call [result].
 */
class SnipController(private val sampleRate: Int) {
    /** Id of the queue note this snip corresponds to (set by the service). */
    @Volatile var noteId: Long = 0L
    private var target = 0
    private val acc = ArrayList<Short>()

    @Synchronized fun begin(preRoll: ShortArray, totalSeconds: Int) {
        acc.clear()
        for (s in preRoll) acc.add(s)
        target = totalSeconds * sampleRate
    }

    /** Returns true once the clip has reached [target] samples. */
    @Synchronized fun feed(chunk: ShortArray, len: Int): Boolean {
        var i = 0
        while (i < len && acc.size < target) { acc.add(chunk[i]); i++ }
        return acc.size >= target
    }

    @Synchronized fun result(): ShortArray = acc.toShortArray()
}
