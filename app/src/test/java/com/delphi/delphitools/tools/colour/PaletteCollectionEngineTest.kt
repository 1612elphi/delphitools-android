package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.ColourFormat
import com.delphi.delphitools.colour.CuratedPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class PaletteCollectionEngineTest {
    @Test
    fun fansFitTheirRoom() {
        for ((w, h) in listOf(370.0 to 290.0, 650.0 to 980.0, 370.0 to 150.0, 900.0 to 420.0)) {
            for (count in listOf(1, 3, 6, 8, 13)) {
                val fan = PaletteCollectionEngine.fan(16.0, 40.0, w, h, count)
                assertEquals(count, fan.angles.size)
                assertTrue(fan.length > 0)
                for (angle in fan.angles) {
                    for ((cx, cy) in listOf(-fan.width / 2 to -fan.length, fan.width / 2 to -fan.length, -fan.width / 2 to fan.tail, fan.width / 2 to fan.tail)) {
                        val x = fan.pivotX + cx * cos(angle) - cy * sin(angle)
                        val y = fan.pivotY + cx * sin(angle) + cy * cos(angle)
                        assertTrue(x >= 15.5 && x <= 16.5 + w && y >= 39.5 && y <= 40.5 + h)
                    }
                }
                val chips = fan.angles.dropLast(1)
                assertTrue(abs(fan.angles.last() - (Math.PI / 2 + PaletteCollectionEngine.coverTilt)) < 0.001)
                assertTrue(chips.zipWithNext().all { (a, b) -> a < b } && chips.all { it > fan.angles.last() && it < Math.PI })
            }
        }
        val wide = PaletteCollectionEngine.fan(0.0, 0.0, 900.0, 300.0, 6)
        val tall = PaletteCollectionEngine.fan(0.0, 0.0, 300.0, 900.0, 6)
        assertTrue(wide.angles[4] <= tall.angles[4])
        val held = PaletteCollectionEngine.fan(0.0, 0.0, 400.0, 320.0, 6)
        assertTrue(held.pivotX < 100 && held.pivotY < 80)
    }

    @Test
    fun matches() {
        val palette = CuratedPalette("mayfair", "Mayfair", "Classic", listOf("#1a2634", "#f5f0e6"))
        assertTrue(PaletteCollectionEngine.matches(palette, "may", ColourFormat.HEX))
        assertTrue(PaletteCollectionEngine.matches(palette, "classic", ColourFormat.HEX))
        assertTrue(PaletteCollectionEngine.matches(palette, "1a26", ColourFormat.HEX))
        assertTrue(PaletteCollectionEngine.matches(palette, "rgb(26, 38", ColourFormat.RGB))
        assertFalse(PaletteCollectionEngine.matches(palette, "nature", ColourFormat.HEX))
    }

    @Test
    fun curatedData() {
        assertEquals(284, PaletteCollectionData.all.size)
        assertEquals(PaletteCollectionData.all.size, PaletteCollectionData.all.map { it.id }.toSet().size)
        assertTrue(PaletteCollectionData.all.all { it.category in PaletteCollectionData.categories })
    }

    @Test
    fun easeOutBackEnds() {
        assertEquals(0.0, PaletteCollectionEngine.easeOutBack(0.0), 1e-12)
        assertEquals(1.0, PaletteCollectionEngine.easeOutBack(1.0), 1e-12)
    }
}
