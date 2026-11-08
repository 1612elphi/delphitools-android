package com.delphi.delphitools.tools.typography

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordCounterEngineTest {
    @Test
    fun counts() {
        val stats = WordStats("Hello there, world. The world is big!\n\nA new paragraph here.")
        assertEquals(11, stats.words)
        assertEquals(3, stats.sentences)
        assertEquals(2, stats.paragraphs)
        assertEquals(3, stats.lines)
        assertEquals(60, stats.characters)
        assertEquals(49, stats.charactersNoSpaces)
        assertEquals("world" to 2, stats.topWords(5).first())
        assertEquals(listOf("world", "big", "hello"), stats.topWords(3).map { it.first })
        val empty = WordStats("")
        assertEquals(0, empty.words + empty.sentences + empty.paragraphs + empty.lines + empty.characters)
        assertEquals(1, WordStats("👍🏽").characters)
    }

    @Test
    fun bars() {
        assertEquals(1, WordBars.rows(listOf(listOf(3, 3, 3)), 20))
        assertEquals(2, WordBars.rows(listOf(listOf(5, 5)), 8))
        assertEquals(2, WordBars.rows(listOf(listOf(), listOf(2)), 10))
        val short = WordBars.fit(listOf(listOf(4)), 300f, 100f)
        assertTrue(short.bar == WordBars.LARGEST && short.columnCharacters == 4)
        assertEquals(WordBars.SMALLEST, WordBars.fit(List(5000) { listOf(4, 4) }, 300f, 100f).bar)
        assertTrue(WordBars.fit(List(40) { listOf(9) }, 600f, 100f).columns > 1)
    }
}
