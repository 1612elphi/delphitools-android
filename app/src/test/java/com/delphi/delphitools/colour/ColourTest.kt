package com.delphi.delphitools.colour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColourTest {
    private val green = Rgb(30 / 255.0, 102 / 255.0, 38 / 255.0)

    @Test
    fun notationsRoundTrip() {
        // hsl text is whole degrees and percent, too coarse to return the same byte
        for (format in ColourFormat.entries - ColourFormat.HSL) {
            val text = green.formatted(format)
            assertEquals(text, green.hex, parseColour(text)?.hex)
        }
        assertEquals("#1E6626", parseColour("1e6626")?.hex)
        assertEquals("#AABBCC", parseColour("#abc")?.hex)
        assertEquals("#1E6626", parseColour("rgb(30 102 38)")?.hex)
        assertNull(parseColour("rgb(30, 102)"))
        assertNull(parseColour("hsl(nan, 1, 1)"))
        assertNull(parseColour("teal"))
    }

    @Test
    fun knownConversions() {
        assertEquals("rgb(30, 102, 38)", green.formatted(ColourFormat.RGB))
        assertEquals("hsl(127, 55%, 26%)", green.formatted(ColourFormat.HSL))
        // CSS Color 4 sample values for sRGB red
        assertEquals("oklch(0.6280 0.2577 29.2)", Rgb(1.0, 0.0, 0.0).formatted(ColourFormat.OKLCH))
        assertEquals("lab(53.2 80.1 67.2)", Rgb(1.0, 0.0, 0.0).formatted(ColourFormat.LAB))
        assertEquals("21", contrastFigure(contrastRatio(Rgb.black, Rgb.white)))
        assertEquals("4.49", contrastFigure(4.4999))
    }

    @Test
    fun solidPointsRoundTrip() {
        for (space in SolidSpace.entries) {
            assertEquals(space.title, green.hex, space.colourAt(space.pointFor(green)).hex)
        }
    }
}
