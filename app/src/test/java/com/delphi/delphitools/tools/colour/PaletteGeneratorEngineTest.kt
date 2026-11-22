package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteGeneratorEngineTest {
    @Test
    fun resizeKeepsColoursAndLocks() {
        val model = GennyState.initial()
        val before = model.palette
        val grown = model.copy(locked = setOf(1)).resized(8)
        assertEquals(8, grown.palette.size)
        assertEquals(8, grown.count)
        assertEquals(before, grown.palette.take(before.size))
        assertEquals(setOf(1), grown.locked)
        val shrunk = grown.resized(4)
        assertEquals(4, shrunk.palette.size)
        assertEquals(before.take(4), shrunk.palette)
        assertEquals(setOf(1), shrunk.locked)
        assertEquals(3, shrunk.resized(1).palette.size)
    }

    @Test
    fun removeShiftsLocks() {
        val model = GennyState.initial().copy(locked = setOf(0, 2, 4)).removing(2)
        assertEquals(setOf(0, 3), model.locked)
        assertEquals(4, model.count)
    }

    @Test
    fun generateKeepsLocked() {
        val model = GennyState.initial().copy(locked = setOf(3))
        repeat(5) { assertEquals(model.palette[3], model.generated().palette[3]) }
    }

    @Test
    fun everyStrategyMakesItsCount() {
        PaletteStrategies.all.forEach { strategy ->
            val colours = strategy.generator(7, null)
            assertEquals(strategy.id, 7, colours.size)
            assertTrue(strategy.id, colours.all { it.isFinite })
        }
    }

    @Test
    fun categoriesStartOnTheirFirstStrategy() {
        val model = GennyState.initial().choosing(PaletteCategory.Mood)
        assertEquals("thermos", model.strategy.id)
        assertEquals(PaletteCategory.Mood, model.category)
    }

    @Test
    fun css() {
        assertEquals("--palette-1: #000000;\n--palette-2: #FFFFFF;", GennyState(palette = listOf(Rgb.black, Rgb.white)).css)
    }
}
