package com.delphi.delphitools.tools.turbonerd

import com.delphi.delphitools.tools.turbonerd.IPAEngine.Token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IPAEngineTest {
    @Test
    fun selfCheck() {
        assertEquals("ðə", IPAEngine.wordToIpa(listOf("DH", "AH0")))
        // monosyllables carry no stress
        assertFalse(IPAEngine.wordToIpa(listOf("K", "AE1", "T")).contains('ˈ'))
        assertTrue(IPAEngine.wordToIpa(listOf("AH0", "B", "S", "T", "EY1", "N")).contains('ˈ'))
        val tokens = IPAEngine.transcribe("hi, there") { null }
        assertEquals(3, tokens.size)
        assertEquals(Token.Other(", "), tokens[1])
        // non-ASCII letters pass through instead of being guessed at
        assertTrue(IPAEngine.text(IPAEngine.transcribe("café") { null }).endsWith("é"))
    }

    @Test
    fun lookupAndGuess() {
        val dictionary = mapOf("the" to listOf("DH", "AH0"))
        val tokens = IPAEngine.transcribe("The cat") { dictionary[it] }
        assertEquals(Token.Word("The", "ðə", guess = false), tokens[0])
        assertEquals(Token.Word("cat", "kæt", guess = true), tokens[2])
        // compound: the earlier primary becomes secondary; maximal onset puts "sb" after the mark
        assertEquals("ˌbæˈsbæl", IPAEngine.wordToIpa(listOf("B", "AE1", "S", "B", "AE1", "L")))
        // silent final e, "ough"
        assertEquals("mɛk", ShavianHeuristic.transliterate("meke").joinToString("") { it.ipa })
        assertEquals("θoʊ", ShavianHeuristic.transliterate("though").joinToString("") { it.ipa })
    }
}
