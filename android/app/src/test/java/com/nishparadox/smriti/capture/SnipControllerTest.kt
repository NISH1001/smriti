package com.nishparadox.smriti.capture

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnipControllerTest {
    @Test fun assemblesPreRollPlusForward_andStopsAtTotal() {
        val sc = SnipController(sampleRate = 4)               // 4 samples == 1 second
        sc.begin(preRoll = shortArrayOf(1, 2), totalSeconds = 2) // total = 8 samples
        assertFalse(sc.feed(shortArrayOf(3, 4, 5), 3))        // 5 total, not done
        assertTrue(sc.feed(shortArrayOf(6, 7, 8, 9), 4))      // reaches 8 -> done
        assertArrayEquals(shortArrayOf(1, 2, 3, 4, 5, 6, 7, 8), sc.result())
    }
}
