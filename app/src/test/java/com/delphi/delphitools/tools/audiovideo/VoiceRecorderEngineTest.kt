package com.delphi.delphitools.tools.audiovideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VoiceRecorderEngineTest {
    @Test
    fun heldFoldsPeaksAndPunchesIn() {
        val held = VoiceHeld(1000)
        assertEquals(20, held.window)
        // fold crosses buffer edges
        held.append(FloatArray(30) { 0.5f })
        held.append(FloatArray(20) { -0.25f })
        assertTrue(held.peakCount == 2 && held.count == 50 && held.head == 50)
        assertTrue(abs(held.peaks[0] - 0.5f) < 0.001 && abs(held.peaks[1] - 0.5f) < 0.001)
        held.append(FloatArray(10))
        assertTrue(held.peakCount == 3 && abs(held.peaks[2] - 0.25f) < 0.001)
        // punch-in overwrites tail
        held.scrub(0.045)
        assertTrue(held.head == 45 && abs(held.position - 0.045) < 0.0001)
        held.append(FloatArray(10) { 1f })
        assertTrue(held.count == 60 && held.peakCount == 3 && held.peaks[2] > 0.999 && held.head == 55)
        held.append(FloatArray(5))
        assertTrue(held.count == 60 && held.head == 60 && held.peaks[2] > 0.999)
        // clamps both ends
        held.scrub(-1.0)
        assertEquals(0, held.head)
        held.scrub(9.0)
        assertEquals(60, held.head)
        // clipping
        held.append(floatArrayOf(2f, -2f))
        assertEquals(32767.toShort(), held.samples[60])
        assertEquals((-32767).toShort(), held.samples[61])
    }

    @Test
    fun heldGrowsPastItsFirstBuffer() {
        val held = VoiceHeld(100)
        repeat(30) { held.append(FloatArray(100) { 0.1f }) }
        assertEquals(3000, held.count)
        assertEquals(1500, held.peakCount)
    }

    @Test
    fun stripShowsWhatLiesAheadAfterAScrubBack() {
        val peaks = FloatArray(100) { it / 100f }
        val live = VoiceRecorderEngine.strip(peaks, 100, 2.0, 2.0, FloatArray(0), 0, 40, 2f)
        assertEquals(30, live.behind.size)
        assertEquals(0, live.ahead.size)
        val back = VoiceRecorderEngine.strip(peaks, 100, 1.0, 1.0, FloatArray(0), 0, 40, 2f)
        assertEquals(40, back.ahead.size)
        assertEquals(0.5f, back.ahead[0], 0f)
        val tail = VoiceRecorderEngine.strip(peaks, 60, 1.0, 1.2, FloatArray(40) { 1f }, 60, 40, 2f)
        assertEquals(10, tail.gap)
        assertEquals(40, tail.ahead.size)
    }

    @Test
    fun namesAndLengths() {
        val taken = setOf("take.m4a", "take-2.m4a")
        assertEquals("take-3.m4a", VoiceRecorderEngine.freeName("take", "m4a") { it in taken })
        assertEquals("a-b-c", VoiceRecorderEngine.safeName(" a/b:c ", "x"))
        assertNull(VoiceRecorderEngine.safeName("  ", "x"))
        assertNull(VoiceRecorderEngine.safeName("x", "x"))
        assertEquals("1:05", VoiceRecorderEngine.length(65.4))
    }
}
