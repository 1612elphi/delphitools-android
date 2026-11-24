package com.delphi.delphitools.tools.audiovideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleStudioEngineTest {
    private val cues = listOf(SubtitleCue(1000, 2000, "a"), SubtitleCue(3000, 4000, "b"))

    @Test
    fun shownCueOrTheNextAsAGhost() {
        assertEquals(0 to false, SubtitleStudioEngine.shown(cues, 1500.0))
        assertEquals(1 to true, SubtitleStudioEngine.shown(cues, 2500.0))
        assertEquals(0 to true, SubtitleStudioEngine.shown(cues, 500.0))
        assertEquals(1 to true, SubtitleStudioEngine.shown(cues, 9000.0))
        assertNull(SubtitleStudioEngine.shown(emptyList(), 0.0))
        assertNull(SubtitleStudioEngine.activeIndex(cues, 2500.0))
        assertEquals(1, SubtitleStudioEngine.activeIndex(cues, 3000.0))
    }

    @Test
    fun placements() {
        assertEquals(0.0, SubtitleStudioEngine.placement(2, 0.05), 0.0)
        assertTrue(SubtitleStudioEngine.placement(0, 0.05) < SubtitleStudioEngine.placement(1, 0.05))
        assertTrue(SubtitleStudioEngine.placement(1, 0.05) < 0)
        assertTrue(SubtitleStudioEngine.growsDown(SubtitleStudioEngine.placement(0, 0.14)))
        assertFalse(SubtitleStudioEngine.growsDown(SubtitleStudioEngine.placement(1, 0.02)))
    }

    @Test
    fun wrapsAndLaysOutUpwardFromTheBaseline() {
        val measure = { text: String -> text.length * 10f }
        assertEquals(listOf("one two", "three"), SubtitleStudioEngine.wrap("one two three", 75f, measure))
        assertEquals(listOf("a", "b"), SubtitleStudioEngine.wrap("a\n\nb", 100f, measure))
        val lines = SubtitleStudioEngine.layout("<i>one two</i> three", 100f, 1000f, SubtitleStyle(width = 0.75), measure)
        assertEquals(listOf("one two", "three"), lines.map { it.text })
        assertEquals(940f, lines.last().baseline, 0.01f)
        assertTrue(lines.first().baseline < lines.last().baseline)
    }

    @Test
    fun jumpsAndSeeks() {
        assertEquals(1, SubtitleStudioEngine.jump(cues, 1500.0, 1))
        assertEquals(0, SubtitleStudioEngine.jump(cues, 3100.0, -1))
        assertNull(SubtitleStudioEngine.jump(cues, 1100.0, -1))
        assertEquals(1.04, SubtitleStudioEngine.cueSeek(cues[0], 25.0), 1e-9)
        assertTrue(SubtitleStyle(x = 1.0, y = -2.0).clamped().let { it.x == 0.45 && it.y == -0.9 })
    }
}
