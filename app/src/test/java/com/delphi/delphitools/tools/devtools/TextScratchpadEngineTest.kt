package com.delphi.delphitools.tools.devtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextScratchpadEngineTest {
    private val e = TextScratchpadEngine

    @Test
    fun cases() {
        assertEquals("Hello World-wide", e.titleCase("hELLO world-WIDE"))
        assertEquals("Hi there. How are you? Fine!", e.sentenceCase("HI THERE. how are you? fine!"))
        assertEquals("helloBigWorld", e.camelCase("Hello big_WORLD"))
        assertEquals("hello_big_world", e.snakeCase("Hello big-World"))
        assertEquals("hello-big-world", e.kebabCase("Hello big World"))
        assertEquals("!!", e.camelCase("!!"))
    }

    @Test
    fun lines() {
        assertEquals("a\nb\nc", e.sortAZ("c\na\nb"))
        assertEquals("c\nb\na", e.sortZA("c\r\na\nb"))
        assertEquals("a\nbb\nccc", e.sortByLength("ccc\na\nbb"))
        assertEquals("b\na", e.reverseOrder("a\nb"))
        assertEquals("a\nb", e.removeDuplicates("a\nb\na"))
        assertEquals("1. a\n2. b", e.addLineNumbers("a\nb"))
        assertEquals("a\nb\nc", e.removeLineNumbers("1. a\n2)b\n3: c"))
        assertEquals("b\na\nc".lines().sorted(), e.shuffle("a\nb\nc").lines().sorted())
    }

    @Test
    fun cleanUp() {
        assertEquals("a\nb", e.trimLines("  a \n\tb"))
        assertEquals("a\nb", e.removeEmptyLines("a\n  \n\nb"))
        assertEquals("a b c", e.removeExtraSpaces("a  b     c"))
        assertEquals("a b", e.joinLines(" a \n\n b"))
        assertEquals("aaa bbb\nccc", e.wrapAt("aaa bbb ccc", 7))
        assertEquals("a%20b%C3%A9?x=1&y=~", e.urlEncode("a bé?x=1&y=~"))
        assertEquals("a bé", e.urlDecode("a%20b%C3%A9"))
        assertNull(e.urlDecode("100%"))
        assertNull(e.urlDecode("%zz"))
        assertNull(e.urlDecode("%C3"))
    }

    @Test
    fun find() {
        // dt-ios ScratchpadEditor.selfCheck
        assertEquals(listOf(TextSpan(0, 3), TextSpan(8, 11)), e.matches("a.b", false, "A.B axb a.b"))
        assertEquals(2, e.matches("a.b", true, "A.B axb a.b")?.size)
        assertNull(e.matches("(", true, "x"))
        assertEquals(emptyList<TextSpan>(), e.matches("x*", true, "abc"))
        assertEquals(emptyList<TextSpan>(), e.matches("", false, "abc"))
    }

    @Test
    fun replace() {
        assertEquals("X b A", e.replace("a b A", "A", "X", isRegex = false, all = false))
        assertEquals("X b X", e.replace("a b A", "A", "X", isRegex = false, all = true))
        assertEquals("$1 b", e.replace("a b", "a", "$1", isRegex = false, all = true))
        assertEquals("2024/05", e.replace("05-2024", "(\\d+)-(\\d+)", "$2/$1", isRegex = true, all = true))
        assertEquals("[a]\$", e.replace("a", "(a)", "[$1]\\$", isRegex = true, all = true))
        assertEquals("a b", e.replace("a b", "(", "x", isRegex = true, all = true))
        assertEquals("x-x", e.replace("1-2", "\\d", "x", isRegex = true, all = true))
        assertEquals("x-2", e.replace("1-2", "\\d", "x", isRegex = true, all = false))
    }

    @Test
    fun extractAndCount() {
        assertEquals(listOf("a@b.co", "c.d@e.org"), e.extractEmails("mail a@b.co or c.d@e.org"))
        assertEquals(listOf("https://x.io/a?b"), e.extractUrls("see https://x.io/a?b now"))
        assertEquals(listOf("-1.5", "42"), e.extractNumbers("-1.5 and 42"))
        assertEquals(ScratchpadCounts(0, 0, 0), e.counts(""))
        assertEquals(ScratchpadCounts(3, 11, 2), e.counts("one two\nsix"))
        assertEquals(1, e.counts("👍🏽").characters)
    }
}
