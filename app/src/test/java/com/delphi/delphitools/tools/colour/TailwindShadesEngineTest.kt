package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.parseHex
import com.delphi.delphitools.tools.colour.TailwindShadesEngine.Mode
import com.delphi.delphitools.tools.colour.TailwindShadesEngine.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TailwindShadesEngineTest {
    @Test
    fun selfCheck() {
        val blue = parseHex("#3B82F6")!!
        for (mode in Mode.entries) {
            val saturations = TailwindShadesEngine.shades(blue, mode).map { it[1] / maxOf(ColourAtlasEngine.maxChroma(it[0], it[2]), 1e-9) }
            for (index in 1 until saturations.size - 1) {
                assertTrue(mode.name, saturations[index] >= minOf(saturations[index - 1], saturations[index + 1]) - 1e-6)
            }
            assertTrue(mode.name, saturations.all { it <= 1 + 1e-9 })
        }
        assertEquals(0.875, TailwindShadesEngine.rampScale(1, Mode.Classic), 1e-12)
        assertEquals(0.75, TailwindShadesEngine.rampScale(0, Mode.Classic), 0.0)
        assertEquals(0.85, TailwindShadesEngine.rampScale(10, Mode.Classic), 0.0)
        assertTrue(TailwindShadesEngine.shades(Rgb(0.5, 0.5, 0.5), Mode.Vivid).all { it[1] < 1e-6 })
    }

    @Test
    fun rampPlace() {
        val targets = TailwindShadesEngine.targetLightness
        assertEquals(Place(5.0, 500, 5), TailwindShadesEngine.place(0.55, targets))
        assertEquals(Place(0.0, 50, 0), TailwindShadesEngine.place(0.99, targets))
        assertEquals(Place(10.0, 950, 10), TailwindShadesEngine.place(0.05, targets))
        val between = TailwindShadesEngine.place(0.605, targets)
        assertEquals(4.5, between.index, 1e-9)
        assertEquals(450, between.level)
        assertEquals(5, between.nearest)
        assertEquals(80, TailwindShadesEngine.place(0.95, targets).level)
    }

    @Test
    fun exports() {
        val colours = List(11) { Rgb.white }
        assertTrue(TailwindShadesEngine.cssHex("", colours).startsWith(":root {\n  --colour-50: #FFFFFF;\n"))
        assertTrue(TailwindShadesEngine.tailwindConfig("brand", colours).startsWith("'brand': {\n    '50': '#FFFFFF',\n"))
        assertEquals("my-brand2", TailwindShadesEngine.cleanName("My-Brand 2!é"))
    }
}
