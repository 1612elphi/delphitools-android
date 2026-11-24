package com.delphi.delphitools.tools.audiovideo

import com.delphi.delphitools.tools.audiovideo.AVEditEngine.keptVideoSamples
import com.delphi.delphitools.tools.audiovideo.AVEditEngine.shifted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AVEditEngineTest {
    private fun s(pts: Long, sync: Boolean = false) = AVEditSample(pts, sync)

    @Test
    fun names() {
        assertEquals("clip", AVEditEngine.baseName("clip.mov", "video"))
        assertEquals("my.clip", AVEditEngine.baseName("my.clip.mp4", "video"))
        assertEquals("video", AVEditEngine.baseName("", "video"))
        assertEquals("video", AVEditEngine.baseName(".mp4", "video"))
        assertEquals("01:02:03.457", AVEditEngine.timestamp(3723.4567))
        assertEquals("00:00:00.000", AVEditEngine.timestamp(-2.0))
        assertEquals("00-00-02-500", AVEditEngine.fileStamp(2.5))
        assertEquals("29.97 fps", AVEditEngine.fpsLabel(29.97))
        assertEquals("30 fps", AVEditEngine.fpsLabel(30.0))
        assertEquals("1920 × 1080 · 25 fps · 00:00:10.000", AVEditEngine.meta(1920, 1080, 25.0, 10.0))
        assertEquals("", AVEditEngine.meta(1920, 1080, 25.0, 0.0))
    }

    @Test
    fun formatting() {
        assertEquals("2.50 Mb/s", AVEditEngine.bitrate(2_500_000.0))
        assertEquals("128 kb/s", AVEditEngine.bitrate(128_000.0))
        assertEquals("16:9", AVEditEngine.ratio(1920.0 / 1080))
        assertEquals("1.52:1", AVEditEngine.ratio(1.52))
        assertEquals("AVC / H.264", AVEditEngine.codecName("video/avc"))
        assertEquals("X-MATROSKA", AVEditEngine.codecName("video/x-matroska"))
    }

    @Test
    fun tracks() {
        val mimes = listOf("audio/mp4a-latm", "video/avc", null, "video/hevc", "audio/opus")
        assertEquals(listOf(1, 3), AVEditEngine.videoTracks(mimes))
        assertEquals(listOf(0, 1, 3, 4), AVEditEngine.mediaTracks(mimes))
        assertEquals(0, AVEditEngine.firstAudioTrack(mimes))
        assertNull(AVEditEngine.firstAudioTrack(listOf("video/avc")))
    }

    @Test
    fun keptSamplesStopAtTheEnd() {
        // closed GOP, no reordering, sync every 1 s at 10 fps
        val plain = (0 until 30).map { s(it * 100_000L, it % 10 == 0) }
        assertEquals(15, keptVideoSamples(plain, 1_500_000))
        assertEquals(10, keptVideoSamples(plain, 1_000_000))
        assertEquals(30, keptVideoSamples(plain, 9_000_000))
    }

    @Test
    fun keptSamplesKeepReferencesOfReorderedFrames() {
        // decode order I P B B P B B, display 0 3 1 2 6 4 5
        val reordered = listOf(s(0, true), s(3), s(1), s(2), s(6), s(4), s(5), s(7, true))
        // end 3: B at 1 and 2 come after the P at 3, so the P stays in
        assertEquals(4, keptVideoSamples(reordered, 3))
        // end 5: the B at 4 needs the P at 6
        assertEquals(6, keptVideoSamples(reordered, 5))
        assertEquals(1, keptVideoSamples(reordered, 1))
        // the next sync at or after the end closes the scan
        assertEquals(7, keptVideoSamples(reordered, 7))
    }

    @Test
    fun shiftedSamples() {
        assertEquals(0L, shifted(2_000_000, 2_000_000, null))
        assertEquals(500_000L, shifted(2_500_000, 2_000_000, 3_000_000))
        assertNull(shifted(1_900_000, 2_000_000, null))
        assertNull(shifted(3_000_000, 2_000_000, 3_000_000))
        assertEquals(0.5, AVEditEngine.fraction(1_500_000, 1_000_000, 2_000_000), 1e-9)
        assertEquals(0.0, AVEditEngine.fraction(5, 1, 1), 1e-9)
    }

    @Test
    fun selection() {
        assertEquals(2.0 to 7.5, AVEditEngine.select(2.0, 7.5, 10.0))
        assertEquals(0.0 to 10.0, AVEditEngine.select(-1.0, 12.0, 10.0))
        val (start, end) = AVEditEngine.select(5.0, 5.0, 10.0)
        assertEquals(4.99, start, 1e-9)
        assertEquals(5.0, end, 1e-9)
    }

    @Test
    fun wav() {
        // AVEditAudio.selfCheck
        assertTrue(AVEditEngine.fitsWav(48000L * 3600 * 6, 2))
        assertFalse(AVEditEngine.fitsWav(48000L * 3600 * 7, 2))
        assertTrue(AVEditEngine.fitsWav(1, 0))
        val header = AVEditEngine.wavHeader(48000, 2, 1000)
        assertEquals(44, header.size)
        assertEquals("RIFF", String(header, 0, 4))
        assertEquals("WAVE", String(header, 8, 4))
        assertEquals("data", String(header, 36, 4))
        assertEquals(1036, header[4].toInt() and 0xFF or (header[5].toInt() and 0xFF shl 8))
        assertEquals(1000, header[40].toInt() and 0xFF or (header[41].toInt() and 0xFF shl 8))
    }

    @Test
    fun frames() {
        // AVEditFrames.selfCheck
        assertEquals(listOf(1.5, 4.5, 7.5, 10.5), AVEditEngine.sheetTimes(12.0, 4))
        assertTrue(AVEditEngine.sheetTimes(12.0, 0).isEmpty())
        assertEquals(4 to 3, AVEditEngine.sheetGrid(12))
        assertEquals(2 to 1, AVEditEngine.sheetGrid(2))
        assertEquals(4 to 2, AVEditEngine.sheetGrid(5))
        // AVEditFilmstrip.selfCheck
        assertEquals(listOf(4, 12, 20), AVEditEngine.spread(3, 24))
        assertEquals((0 until 24).toList(), AVEditEngine.spread(24, 24))
        assertTrue(AVEditEngine.spread(5, 0).isEmpty())
        assertEquals(30, AVEditEngine.spread(30, 24).size)
        assertEquals(23, AVEditEngine.spread(30, 24).last())
        assertEquals(0, AVEditEngine.bucket(0, 1_000_000, 800))
        assertEquals(799, AVEditEngine.bucket(1_000_000, 1_000_000, 800))
        assertEquals(400, AVEditEngine.bucket(500_000, 1_000_000, 800))
    }
}
