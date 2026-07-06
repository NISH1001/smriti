package com.nishparadox.smriti.capture

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RingBufferTest {
    @Test fun snapshot_returnsMostRecent_whenOverfilled() {
        val rb = RingBuffer(capacitySamples = 4)
        rb.write(shortArrayOf(1, 2, 3), 3)
        rb.write(shortArrayOf(4, 5, 6), 3)   // total 6 written, capacity 4
        assertArrayEquals(shortArrayOf(3, 4, 5, 6), rb.snapshot())
    }

    @Test fun snapshot_returnsAll_whenUnderfilled() {
        val rb = RingBuffer(capacitySamples = 4)
        rb.write(shortArrayOf(7, 8), 2)
        assertArrayEquals(shortArrayOf(7, 8), rb.snapshot())
        assertEquals(2, rb.snapshot().size)
    }
}
