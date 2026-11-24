package com.delphi.delphitools.tools.colour

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PixelPickerSamplerTest {
    // 3x2: rgb, white/black/grey
    private val pixels = intArrayOf(
        0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
        0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF808080.toInt(),
    )

    private fun at(x: Double, y: Double) = pixels[PixelPickerSampler.index(y, 2) * 3 + PixelPickerSampler.index(x, 3)]

    private fun loupe(x: Int, y: Int, radius: Int) = PixelPickerSampler.loupe(3, 2, x, y, radius) { px, py -> pixels[py * 3 + px] }

    @Test
    fun selfCheck() {
        assertEquals(0xFFFF0000.toInt(), at(0.1, 0.1))
        assertEquals(0xFF808080.toInt(), at(0.9, 0.9))
        assertEquals(0xFF000000.toInt(), at(0.5, 0.9))
        assertEquals(2, PixelPickerSampler.index(1.0, 3))
        assertEquals(0, PixelPickerSampler.index(-0.2, 3))

        val corner = loupe(0, 0, 2)
        assertEquals(25, corner.size)
        assertEquals(0xFFFF0000.toInt(), corner[2 * 5 + 2])
        assertEquals(0xFF00FF00.toInt(), corner[2 * 5 + 3])
        assertEquals(0xFFFFFFFF.toInt(), corner[3 * 5 + 2])
        assertEquals(PixelPickerSampler.OUTSIDE, corner[2 * 5 + 1])
        assertEquals(PixelPickerSampler.OUTSIDE, corner[1 * 5 + 2])
        val far = loupe(2, 1, 1)
        assertEquals(0xFF808080.toInt(), far[4])
        assertEquals(0xFF00FF00.toInt(), far[0])
        assertEquals(PixelPickerSampler.OUTSIDE, far[8])
        assertArrayEquals(intArrayOf(26, 26, 26), PixelPickerSampler.OUTSIDE.let { intArrayOf(it shr 16 and 0xFF, it shr 8 and 0xFF, it and 0xFF) })
    }

    @Test
    fun displayP3() {
        assertEquals("color(display-p3 1.0000 0.0000 0.5020)", PixelPickerSampler.displayP3(1.2f, -0.1f, 0.5f))
    }
}
