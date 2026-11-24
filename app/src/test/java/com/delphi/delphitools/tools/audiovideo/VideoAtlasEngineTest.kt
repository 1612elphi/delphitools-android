package com.delphi.delphitools.tools.audiovideo

import com.delphi.delphitools.canon.CanonTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VideoAtlasEngineTest {
    @Test
    fun componentsMatchBt709() {
        val red = VideoAtlasEngine.components(1.0, 0.0, 0.0)
        val white = VideoAtlasEngine.components(1.0, 1.0, 1.0)
        assertTrue(abs(red.cr - 0.5) < 1e-9 && red.cb < 0)
        assertTrue(abs(white.cb) < 1e-9 && abs(white.cr) < 1e-9 && abs(white.luma - 1) < 1e-9)
    }

    @Test
    fun greyFrameLandsInTheVectorscopeCentre() {
        val width = VideoAtlasEngine.SAMPLE_WIDTH
        val height = VideoAtlasEngine.SAMPLE_HEIGHT
        val cells = VideoAtlasEngine.CELLS
        val pixels = IntArray(width * height) { 0xFF808080.toInt() }
        val vector = IntArray(cells * cells)
        val wave = IntArray(width * cells)
        VideoAtlasEngine.plot(pixels, width, height, vector, wave)
        assertEquals(255, vector[cells / 2 * cells + cells / 2])
        assertEquals(1, vector.count { it > 0 })
        // every column has one lit level
        assertEquals(width, wave.count { it > 0 })
    }

    @Test
    fun reportAndSummary() {
        val sections = listOf(
            AVEditProbeSection("General", listOf(CanonTile("Size", "1 MB"))),
            AVEditProbeSection("Video #1", listOf(CanonTile("Codec", "AVC / H.264"))),
        )
        assertEquals("General\n  Size: 1 MB\n\nVideo #1\n  Codec: AVC / H.264", VideoAtlasEngine.report(sections))
        assertEquals("1 stream", VideoAtlasEngine.streamSummary(sections))
        assertNull(VideoAtlasEngine.streamSummary(sections.take(1)))
    }

    @Test
    fun colourAndText() {
        assertEquals("BT.709 · SDR · Limited range", VideoAtlasEngine.colour(1, 3, 2))
        assertNull(VideoAtlasEngine.colour(null, 99, null))
        assertTrue(VideoAtlasEngine.isText("application/x-subrip") && VideoAtlasEngine.isText("text/vtt"))
        assertEquals("WebVTT", VideoAtlasEngine.codecName("text/vtt"))
    }
}
