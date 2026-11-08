package com.delphi.delphitools.tools.turbonerd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MorseEngineTest {
    @Test
    fun selfCheck() {
        assertEquals("... --- ...", MorseEngine.encode("SOS"))
        assertEquals(".... . .-.. .-.. --- / .-- --- .-. .-.. -..", MorseEngine.encode("hello world"))
        assertEquals("HELLO WORLD", MorseEngine.decode(".... . .-.. .-.. --- / .-- --- .-. .-.. -.."))
        assertEquals("S O S", MorseEngine.decode("...   ---   ..."))
        assertTrue(MorseEngine.looksLikeMorse(".- -..."))
        assertFalse(MorseEngine.looksLikeMorse("abc"))
        assertFalse(MorseEngine.looksLikeMorse("   "))
        assertEquals("?", MorseEngine.decode("..--.."))
        assertEquals(1, MorseEngine.timings(".", 20).size)
        assertEquals(listOf(true, false, true), MorseEngine.timings("..", 20).map { it.on })
        assertEquals(1, MorseEngine.words("...  ---").size)
        assertEquals(2, MorseEngine.words("...  ---", minimumGap = 2).size)
    }

    @Test
    fun render() {
        assertNull(MorseEngine.render("", 20))
        // dot 60 ms plus word gap 420 ms at 20 wpm
        val samples = MorseEngine.render(".", 20)!!
        assertEquals((0.48 * MorseEngine.SAMPLE_RATE).toInt() + 1, samples.size)
        assertTrue(samples.take(1323).any { it > 10_000 })
        assertTrue(samples.drop(1323).all { it.toInt() == 0 })
    }
}
