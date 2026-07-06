package com.nishparadox.smriti.capture

/**
 * Assembles a clip from a pre-roll snapshot plus forward PCM chunks, using sample
 * counts (no wall clock). The clip is complete once it reaches [totalSeconds] of audio.
 */
class SnipController(private val sampleRate: Int) {
    private var target = 0
    private val acc = ArrayList<Short>()

    fun begin(preRoll: ShortArray, totalSeconds: Int) {
        acc.clear()
        for (s in preRoll) acc.add(s)
        target = totalSeconds * sampleRate
    }

    /** Returns true once the clip has reached [target] samples. */
    fun feed(chunk: ShortArray, len: Int): Boolean {
        var i = 0
        while (i < len && acc.size < target) { acc.add(chunk[i]); i++ }
        return acc.size >= target
    }

    fun result(): ShortArray = acc.toShortArray()
}
