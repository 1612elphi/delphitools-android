package com.delphi.delphitools.tools.images

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ArtworkEnhanceEngineTest {
    // dt-ios ArtworkDetail.selfCheck
    @Test
    fun detailRect() {
        assertEquals(PixelRect(68, 18, 64, 64), ArtworkEnhanceEngine.detailRect(200, 100, 64))
        assertEquals(PixelRect(5, 0, 30, 30), ArtworkEnhanceEngine.detailRect(40, 30, 64))
    }

    @Test
    fun noiseBlocks() {
        val noise = { x: Int, y: Int -> ArtworkEnhanceEngine.noise(x, y, 3, 2) }
        for ((x, y) in listOf(0 to 0, 2 to 4, 6 to 6)) {
            val first = noise(x, y)
            assertTrue(listOf(1 to 0, 0 to 1, 1 to 1).all { (dx, dy) -> noise(x + dx, y + dy) == first })
        }
        assertTrue(noise(0, 0) != noise(2, 0) || noise(0, 0) != noise(0, 2))
        assertNotEquals(ArtworkEnhanceEngine.noise(0, 0, 3, 1), ArtworkEnhanceEngine.noise(0, 0, 4, 1))
    }

    @Test
    fun magnified() {
        val pixels = IntArray(16) { it }
        val (big, side) = ArtworkEnhanceEngine.magnified(pixels, 4, 4, 4, 3)
        assertEquals(12, side)
        assertEquals(big[0], big[2 * 12 + 2])
        assertEquals(big[3], big[2 * 12 + 5])
        assertEquals(1, big[3])
        assertEquals(15, big[143])
    }

    @Test
    fun overlay() {
        val grey = 0xFF808080.toInt()
        assertArrayEquals(intArrayOf(grey), ArtworkEnhanceEngine.enhance(intArrayOf(grey), 1, 0f, 1, 0))
        val pixels = IntArray(64 * 64) { grey }
        val out = ArtworkEnhanceEngine.enhance(pixels, 64, 0.2f, 1, 0)
        assertTrue(out.all { it ushr 24 == 0xFF })
        assertTrue(out.all { abs((it and 0xFF) - 0x80) <= 26 })
        assertTrue(out.any { it != grey })
        val clear = ArtworkEnhanceEngine.enhance(intArrayOf(0), 1, 1f, 1, 0)[0]
        assertEquals(ArtworkEnhanceEngine.noise(0, 0, 0, 1) ushr 24, clear ushr 24)
    }
}
