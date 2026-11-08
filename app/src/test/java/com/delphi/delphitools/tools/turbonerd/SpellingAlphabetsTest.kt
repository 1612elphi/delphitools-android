package com.delphi.delphitools.tools.turbonerd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpellingAlphabetsTest {
    private val nato = SpellingAlphabets.all[0]
    private val din = SpellingAlphabets.all[1]

    @Test
    fun selfCheck() {
        assertEquals("Alfa Bravo / Charlie", SpellingAlphabets.spellText("ab c", nato))
        assertEquals("AB C", SpellingAlphabets.unspell("Alfa Bravo / Charlie", nato))
        assertTrue(SpellingAlphabets.looksSpelled("Alfa Bravo", nato))
        assertFalse(SpellingAlphabets.looksSpelled("Alfa", nato))
        assertFalse(SpellingAlphabets.looksSpelled("hello there", nato))
        assertEquals("XY", SpellingAlphabets.unspell("X-ray Yankee", nato))
        assertEquals("ÄB", SpellingAlphabets.unspell("Umlaut Aachen Berlin", din))
    }

    @Test
    fun eszett() {
        assertEquals("Eszett", SpellingAlphabets.spell("ß", din).single().word)
        assertEquals("Ärger", SpellingAlphabets.spell("ä", SpellingAlphabets.all[2]).single().word)
    }
}
