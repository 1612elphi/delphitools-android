package com.delphi.delphitools.tools.typography

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineHeightEngineTest {
    @Test
    fun wrap() {
        val measure = { word: String -> word.length + 1f }
        assertEquals(listOf("ab cd", "ef"), LineHeightEngine.wrap(listOf("ab", "cd", "ef"), 6f, measure))
        assertEquals(listOf("abcdefgh", "x"), LineHeightEngine.wrap(listOf("abcdefgh", "x"), 4f, measure))
        assertTrue(LineHeightEngine.wrap(emptyList(), 10f, measure).isEmpty())
    }

    @Test
    fun ratios() {
        assertEquals(LineHeightRatio.Relaxed, LineHeightRatio.matching(1.7502))
        assertEquals(LineHeightRatio.Custom, LineHeightRatio.matching(1.6))
    }
}
