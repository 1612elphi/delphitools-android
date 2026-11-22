package com.delphi.delphitools.tools.devtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexTesterEngineTest {
    private fun matched(outcome: RegexOutcome) = outcome as RegexOutcome.Matched

    @Test
    fun matching() {
        val text = "ab ab ab"
        val all = matched(RegexTesterEngine.run("(a)b", "g", text))
        assertFalse(all.truncated)
        assertEquals(1, all.groups)
        assertEquals(3, all.matches.size)
        assertEquals(TextSpan(3, 5), all.matches[1].range)
        assertEquals(listOf(TextSpan(3, 4)), all.matches[1].groups)

        val first = matched(RegexTesterEngine.run("ab", "", text))
        assertEquals(0, first.groups)
        assertEquals(1, first.matches.size)
        assertEquals(3, matched(RegexTesterEngine.run("AB", "gi", text)).matches.size)

        val optional = matched(RegexTesterEngine.run("(x)?(b)", "g", "b"))
        assertEquals(2, optional.groups)
        assertNull(optional.matches.first().groups.first())

        val empty = matched(RegexTesterEngine.run("^", "gm", "a\nb"))
        assertEquals(2, empty.matches.size)
        assertTrue(empty.matches.all { it.range.length == 0 })

        val capped = matched(RegexTesterEngine.run(".", "g", "x".repeat(20), limit = 5))
        assertTrue(capped.truncated)
        assertEquals(5, capped.matches.size)

        assertTrue(RegexTesterEngine.run("(", "g", text) is RegexOutcome.Invalid)
        assertEquals(RegexOutcome.Empty, RegexTesterEngine.run("", "g", text))
        assertEquals(2, matched(RegexTesterEngine.run("a.b", "gs", "a\nb a\rb")).matches.size)
        assertEquals(1, matched(RegexTesterEngine.run("É", "gi", "é")).matches.size)
    }

    @Test
    fun runaway() {
        val started = System.nanoTime()
        // jdk memoises (a+)+$
        val runaway = RegexTesterEngine.run("((a+)+)+b", "g", "a".repeat(40) + "!", timeLimitMs = 200)
        assertEquals(RegexOutcome.TimedOut, runaway)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2000)
    }

    @Test
    fun paragraphs() {
        assertEquals(listOf(0, 3, 4, 7), paragraphStarts("ab\n\ncd\n"))
        assertEquals(listOf(0), paragraphStarts(""))
    }
}
