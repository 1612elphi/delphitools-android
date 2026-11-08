package com.delphi.delphitools.tools.turbonerd

import com.delphi.delphitools.tools.turbonerd.BrailleEngine.Reading.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrailleEngineTest {
    private fun roundTrip(text: String) = BrailleEngine.fromBraille(BrailleEngine.toBraille(text))

    @Test
    fun selfCheck() {
        assertEquals("⠁", BrailleEngine.toBraille("a"))
        for (text in listOf("hello world", "Hello", "BBC", "123", "1a", "end.")) assertEquals(text, roundTrip(text))
        assertEquals("1", BrailleEngine.dots("⠁"))
        assertTrue(BrailleEngine.looksLikeBraille("⠁"))
        assertFalse(BrailleEngine.looksLikeBraille("abc"))
        val readings = BrailleEngine.read(BrailleEngine.toBraille("Hi 1"))
        assertEquals(listOf("", "H", "i", " ", "", "1"), readings.map { it.text })
        assertEquals(Kind.Indicator, readings.first().kind)
        assertEquals(Kind.Other, readings[3].kind)
        assertEquals(2, BrailleEngine.read(BrailleEngine.toBraille("(a)")).first().cells.length)
    }

    @Test
    fun quotesAndPassThrough() {
        assertEquals("\"a\"", roundTrip("\"a\""))
        assertEquals("a😀", roundTrip("a😀"))
    }
}
