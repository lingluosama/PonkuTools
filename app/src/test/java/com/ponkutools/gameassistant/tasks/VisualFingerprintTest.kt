package com.ponkutools.gameassistant.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualFingerprintTest {
    @Test
    fun identicalScreensHaveNoDifference() {
        val frame = VisualFingerprint(IntArray(12) { 0xff202020.toInt() })
        assertEquals(0f, frame.differenceRatio(frame), 0.0001f)
    }

    @Test
    fun changedScreensExceedActionThreshold() {
        val before = VisualFingerprint(IntArray(12) { 0xff202020.toInt() })
        val after = VisualFingerprint(IntArray(12) { 0xffffffff.toInt() })
        assertTrue(after.differenceRatio(before) >= 0.025f)
    }
}
