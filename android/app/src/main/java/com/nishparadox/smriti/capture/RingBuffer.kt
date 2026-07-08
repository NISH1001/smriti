package com.nishparadox.smriti.capture

/** Fixed-capacity circular PCM buffer holding the most recent [capacitySamples] samples. */
class RingBuffer(private val capacitySamples: Int) {
    private val data = ShortArray(capacitySamples)
    private var writePos = 0
    private var filled = 0

    @Synchronized fun write(src: ShortArray, len: Int) {
        for (i in 0 until len) {
            data[writePos] = src[i]
            writePos = (writePos + 1) % capacitySamples
            if (filled < capacitySamples) filled++
        }
    }

    /** Returns up to [capacitySamples] most-recent samples, oldest-first. */
    @Synchronized fun snapshot(): ShortArray {
        val out = ShortArray(filled)
        val start = if (filled < capacitySamples) 0 else writePos
        for (i in 0 until filled) out[i] = data[(start + i) % capacitySamples]
        return out
    }
}
