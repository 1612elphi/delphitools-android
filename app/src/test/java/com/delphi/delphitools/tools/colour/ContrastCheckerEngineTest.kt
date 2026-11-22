package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.ContrastLevel
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.contrastFigure
import com.delphi.delphitools.colour.contrastRatio
import com.delphi.delphitools.colour.hsl
import com.delphi.delphitools.colour.parseHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastCheckerEngineTest {
    @Test
    fun figures() {
        assertEquals("4.49", contrastFigure(4.499))
        assertEquals("4.5", contrastFigure(4.5))
        assertEquals("21", contrastFigure(21.0))
        assertEquals("7", contrastFigure(7.001))
        assertEquals(contrastFigure(4.4999) == "4.5", 4.4999 >= ContrastLevel.AA)
        assertEquals("21", contrastFigure(contrastRatio(Rgb.white, Rgb.black)))
    }

    @Test
    fun grades() {
        assertEquals(
            listOf("AAA", "AAA", "AA", "AA", "AA Large", "AA Large", null),
            listOf(21.0, 7.0, 6.99, 4.5, 4.49, 3.0, 2.99).map(ContrastCheckerEngine::grade),
        )
    }

    @Test
    fun adjustsGreyDarkerOnWhite() {
        val grey = parseHex("777777")!!
        val fixed = ContrastCheckerEngine.adjustToMeetContrast(grey, Rgb.white, ContrastLevel.AA)
        assertTrue(contrastRatio(fixed, Rgb.white) >= ContrastLevel.AA)
        assertTrue(fixed.hsl()[2] < grey.hsl()[2])
    }

    @Test
    fun suggestionIsNullWhenMet() {
        assertNull(ContrastCheckerEngine.suggestion(Rgb.black, Rgb.white, ContrastLevel.AAA))
    }

    @Test
    fun scaleEnds() {
        assertEquals(0.0, ContrastCheckerEngine.share(0.5), 1e-12)
        assertEquals(1.0, ContrastCheckerEngine.share(21.0), 1e-12)
    }
}
