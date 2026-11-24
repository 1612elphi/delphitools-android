package com.delphi.delphitools.tools.typography

import com.delphi.delphitools.tools.typography.LargeTypeEngine.CharClass
import com.delphi.delphitools.tools.typography.LargeTypeEngine.charClass
import com.delphi.delphitools.tools.typography.LargeTypeEngine.fit
import com.delphi.delphitools.tools.typography.LargeTypeEngine.frames
import com.delphi.delphitools.tools.typography.LargeTypeEngine.visible
import com.delphi.delphitools.tools.typography.LargeTypeEngine.wrap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeTypeEngineTest {
    private fun chars(text: String) = text.map { it.toString() }

    @Test
    fun engine() {
        assertEquals(CharClass.Digit, charClass("7"))
        assertEquals(CharClass.Space, charClass(" "))
        assertEquals(CharClass.Letter, charClass("é"))
        assertEquals(CharClass.Special, charClass("#"))
        assertEquals(LargeTypeEngine.MAX_GLYPHS, LargeTypeEngine.glyphs("x".repeat(LargeTypeEngine.MAX_GLYPHS * 3)).size)
        assertEquals(listOf("a", "é", "b"), LargeTypeEngine.characters("aéb"))
    }

    @Test
    fun marquee() {
        assertNull(visible(0.0, 100.0, 10.0))
        assertEquals(0..9, visible(100.0, 100.0, 10.0))
        assertEquals(0..10, visible(105.0, 100.0, 10.0))
        assertEquals(15..24, visible(250.0, 100.0, 10.0))
    }

    @Test
    fun layout() {
        assertEquals(LargeTypeEngine.Cell(72.0, 132.0), LargeTypeEngine.cell(100.0, 0.6, false))
        assertEquals(LargeTypeEngine.DEFAULT_SIZE, fit(emptyList(), 0.6, listOf(300.0), 300.0, false), 0.0)
        assertEquals(LargeTypeEngine.MAX_SIZE, fit(listOf("x"), 0.6, listOf(4000.0), 4000.0, false), 0.0)
        assertEquals(LargeTypeEngine.MIN_SIZE, fit(List(400) { "x" }, 0.6, listOf(100.0), 100.0, true), 0.0)
        val one = frames(chars("abc"), 100.0, 0.6, listOf(0.0..720.0), false)
        assertEquals(3, one.size)
        assertEquals(252.0, one[0].x, 0.0)
        assertTrue(one.all { it.y == 0.0 })
        val two = frames(chars("abcd"), 100.0, 0.6, listOf(0.0..144.0, 200.0..344.0), false)
        assertEquals(listOf(0.0, 72.0, 200.0, 272.0), two.map { it.x })
        assertTrue(two.all { it.y == 0.0 })
        assertEquals(listOf(3, 3), wrap(chars("ab cde"), listOf(4), Int.MAX_VALUE))
        assertEquals(listOf(4, 3), wrap(chars("abcdefg"), listOf(4), Int.MAX_VALUE))
        assertEquals(listOf(4, 4), wrap(chars("a bcdefg"), listOf(4), Int.MAX_VALUE))
        assertEquals(listOf(4, 3), wrap(chars("abcd ef"), listOf(4), Int.MAX_VALUE))
    }
}
