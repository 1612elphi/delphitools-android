package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.ColourBlindness
import com.delphi.delphitools.colour.rgbOf
import com.delphi.delphitools.colour.simulate
import org.junit.Assert.assertEquals
import org.junit.Test

class ColourBlindnessImageTest {
    @Test
    fun pixelsFollowTheMatrixAndKeepAlpha() {
        val red = 0x80FF0000.toInt()
        val (out) = ColourBlindnessImage.simulate(intArrayOf(red), ColourBlindness.Achromatopsia).toList()
        assertEquals(0x80, out ushr 24)
        // 0.299 * 255 = 76.2, truncated
        assertEquals(76, out shr 16 and 0xFF)
        assertEquals(76, out shr 8 and 0xFF)
        assertEquals(76, out and 0xFF)
    }

    @Test
    fun matchesTheColourSimulationWithinTruncation() {
        val pixel = 0xFF3B82F6.toInt()
        for (type in ColourBlindness.entries) {
            val image = ColourBlindnessImage.simulate(intArrayOf(pixel), type)[0]
            val colour = rgbOf(pixel).simulate(type)
            assertEquals(type.name, colour.r * 255, (image shr 16 and 0xFF).toDouble(), 1.0)
            assertEquals(type.name, colour.g * 255, (image shr 8 and 0xFF).toDouble(), 1.0)
            assertEquals(type.name, colour.b * 255, (image and 0xFF).toDouble(), 1.0)
        }
    }
}
