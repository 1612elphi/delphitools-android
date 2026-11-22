package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.SolidSpace
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.lab
import com.delphi.delphitools.colour.oklab
import com.delphi.delphitools.colour.parseHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

class ColourConverterEngineTest {
    private val notation = ColourConverterNotation(ColourManagement())
    private val blue = parseHex("3B82F6")!!

    private fun hex(text: String, space: SolidSpace) = notation.parse(text, space)?.let { notation.colour(it, space).hex }

    @Test
    fun management() {
        val srgb = ColourManagement()
        srgb.lab(blue).zip(blue.lab()).forEach { (a, b) -> assertEquals(b, a, 1e-4) }
        srgb.oklab(blue).zip(blue.oklab()).forEach { (a, b) -> assertEquals(b, a, 1e-4) }
        assertEquals(blue.r, srgb.colourFromLab(srgb.lab(blue)).r, 1e-6)
        assertEquals(blue.b, srgb.colourFromOklab(srgb.oklab(blue)).b, 1e-6)
        // p3 red exceeds srgb
        val p3 = ColourManagement(ColourManagement.Working.DISPLAY_P3)
        assertFalse(ColourManagement.inGamut(p3.sRgb(Rgb(1.0, 0.0, 0.0))))
        assertEquals(100.0, p3.lab(Rgb.white)[0], 1e-3)
        val adobe = ColourManagement(ColourManagement.Working.ADOBE_RGB)
        assertEquals(blue.g, adobe.colour(adobe.xyz(blue)).g, 1e-9)
    }

    @Test
    fun formats() {
        assertEquals("rgb(59, 130, 246)", ColourConverterNotation.format(notation.values(blue, SolidSpace.RGB), SolidSpace.RGB))
        assertTrue(ColourConverterNotation.format(notation.values(blue, SolidSpace.HSL), SolidSpace.HSL).startsWith("hsl(217.2, 91."))
        val lab = blue.lab()
        assertEquals(
            String.format(Locale.ROOT, "lab(%.2f %.2f %.2f)", lab[0], lab[1], lab[2]),
            ColourConverterNotation.format(notation.values(blue, SolidSpace.LAB), SolidSpace.LAB),
        )
        assertEquals("0.00", ColourConverterNotation.fixed(-0.00001, 2))
        assertEquals("rgb(1.0000, 0.0000, 0.0000)", ColourConverterNotation.decimalRgb(Rgb(1.0, 0.0, 0.0)))
    }

    @Test
    fun parses() {
        assertEquals("#3B82F6", hex("59 130 246", SolidSpace.RGB))
        assertEquals("#3B82F6", hex("59, 130, 246", SolidSpace.RGB))
        assertEquals("#3B82F6", hex("rgb(59 130 246 / 50%)", SolidSpace.OKLCH))
        assertEquals("#3B82F6", hex("0.2314, 0.5098, 0.9647", SolidSpace.RGB))
        assertEquals("#3B82F6", hex("#3b82f6", SolidSpace.LAB))
        assertEquals("#3B82F6", hex("3b82f6", SolidSpace.HSL))
        assertEquals(hex("0,6231 0,188 259,8", SolidSpace.OKLCH), hex("oklch(62.31% 0.188 259.8)", SolidSpace.RGB))
        assertEquals(hex("76 47 0 4", SolidSpace.CMYK), hex("cmyk(76%, 47%, 0%, 4%)", SolidSpace.RGB))
        assertEquals(hex("hsl(30 100% 50%)", SolidSpace.RGB), hex("hsl(390 100% 50%)", SolidSpace.RGB))
        assertEquals(hex("30 100 50", SolidSpace.HSL), hex("-330 100 50", SolidSpace.HSL))
        assertEquals(hex("30 80 90", SolidSpace.HSB), hex("390 80 90", SolidSpace.HSB))
        assertEquals(notation.colour(listOf(30.0, 100.0, 50.0), SolidSpace.HSL).hex, notation.colour(listOf(390.0, 100.0, 50.0), SolidSpace.HSL).hex)
        assertEquals(30.0, notation.parse("hsl(390 100% 50%)", SolidSpace.HSL)!!.first(), 0.0)
        assertNull(notation.parse("12 34", SolidSpace.RGB))
        assertNull(notation.parse("nan 1 1", SolidSpace.RGB))
        assertNull(notation.parse("banana", SolidSpace.RGB))
    }

    @Test
    fun roundTrips() {
        // cmyk rounds to percents
        SolidSpace.entries.filter { it != SolidSpace.CMYK }.forEach { space ->
            val values = notation.values(blue, space)
            assertEquals(space.title, "#3B82F6", hex(ColourConverterNotation.format(values, space), space))
            val point = ColourConverterNotation.point(values, space)
            ColourConverterNotation.values(point, space).zip(values).forEach { (a, b) -> assertTrue(space.title, abs(a - b) < 1e-6) }
        }
    }

    @Test
    fun p3KeepsLab() {
        val p3 = ColourConverterNotation(ColourManagement(ColourManagement.Working.DISPLAY_P3))
        val lab = notation.values(blue, SolidSpace.LAB)
        val inP3 = p3.colour(lab, SolidSpace.LAB)
        assertNotEquals("#3B82F6", inP3.hex)
        p3.values(inP3, SolidSpace.LAB).zip(lab).forEach { (a, b) -> assertEquals(b, a, 1e-6) }
    }
}
