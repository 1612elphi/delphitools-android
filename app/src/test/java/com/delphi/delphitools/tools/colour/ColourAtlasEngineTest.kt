package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.ColourBlindness
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.hsb
import com.delphi.delphitools.colour.luminance
import com.delphi.delphitools.colour.parseHex
import com.delphi.delphitools.colour.simulate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColourAtlasEngineTest {
    private val blue = parseHex("3B82F6")!!

    @Test
    fun selfCheck() {
        assertArrayEquals(intArrayOf(235, 128, 128), ColourAtlasEngine.ycbcr(Rgb.white))
        assertEquals(1.0, Rgb.white.luminance, 1e-9)

        val rows = ColourAtlasEngine.notations(blue)
        assertEquals(10, rows.size)
        assertEquals("#3B82F6", rows[0].value)
        assertEquals("rgb(59, 130, 246)", rows[1].value)
        assertEquals(96.47, blue.hsb()[2] * 100, 0.05)

        assertTrue(ColourAtlasEngine.maxChroma(1.0, 264.0) < 0.001)
        assertTrue(ColourAtlasEngine.maxChroma(0.64, 264.0) > 0.1)

        assertEquals(9, ColourAtlasEngine.scale(blue, 1.0).size)
        assertEquals(2, ColourAtlasEngine.harmony(blue, listOf(0.0, 180.0)).size)
    }

    @Test
    fun harmonyAndScale() {
        val (base, complement) = ColourAtlasEngine.harmony(Rgb(1.0, 0.0, 0.0), listOf(0.0, 180.0))
        assertEquals("#FF0000", base.hex)
        assertEquals("#00FFFF", complement.hex)
        assertEquals("#555555", ColourAtlasEngine.scale(Rgb.black, 1.0, steps = 2)[1].hex)
        assertEquals("oklch(0.6231 0.1880 259.8)", ColourAtlasEngine.notations(blue)[7].value)
    }

    @Test
    fun visionMatrices() {
        val grey = Rgb(0.4, 0.4, 0.4)
        for (type in ColourBlindness.entries) assertEquals(type.name, grey.hex, grey.simulate(type).hex)
        assertEquals("#4C4C4C", Rgb(1.0, 0.0, 0.0).simulate(ColourBlindness.Achromatopsia).hex)
    }
}
