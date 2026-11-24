package com.delphi.delphitools.tools.images

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageClipEngineTest {
    @Test
    fun margins() {
        assertEquals(ClipMargins(250, 300, 350, 500, 83), ClipMargins.of(IntRect(500, 250, 1100, 650), 1400, 1000))
        assertTrue(ClipMargins.of(IntRect(0, 0, 10, 10), 10, 10).isEmpty)
    }

    @Test
    fun boundingRect() {
        val width = 12
        val pixels = IntArray(width * 8)
        for (y in 5 until 7) for (x in 4 until 7) pixels[y * width + x] = 0xFF000000.toInt()
        val read = { y: Int, into: IntArray -> pixels.copyInto(into, 0, y * width, y * width + width); Unit }
        assertEquals("top-left origin, pixel exact", IntRect(4, 5, 7, 7), ImageClipEngine.boundingRect(width, 8, readRow = read))
        pixels.fill(0)
        assertNull("fully transparent", ImageClipEngine.boundingRect(width, 8, readRow = read))
    }
}
