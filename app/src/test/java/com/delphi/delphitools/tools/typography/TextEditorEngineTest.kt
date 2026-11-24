package com.delphi.delphitools.tools.typography

import com.delphi.delphitools.tools.typography.TextDoc.Block
import com.delphi.delphitools.tools.typography.TextDoc.mark
import com.delphi.delphitools.tools.typography.TextDoc.words
import com.delphi.delphitools.tools.typography.TextEditorEngine.Look
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditorEngineTest {
    @Test
    fun docConvert() {
        val blocks = TextDoc.parseMarkdown("# Title\n\nSome **bold** and `code`.\n\n- one\n- two\n\n> quoted\n")
        assertEquals(4, blocks.size)
        val heading = blocks[0] as Block.Heading
        assertEquals(1, heading.level)
        assertEquals("Title", heading.runs.first().text)
        val list = blocks[2] as Block.Bullets
        assertFalse(list.ordered)
        assertEquals(2, list.items.size)

        val inline = TextDoc.parseInline("a **b** [c](https://x) `d`")
        assertTrue(inline.any { it.bold && it.text == "b" })
        assertTrue(inline.any { it.link == "https://x" && it.text == "c" })
        assertTrue(inline.any { it.code && it.text == "d" })
        assertEquals(1, TextDoc.parseInline("[".repeat(4000)).size)

        val look = TextDoc.Style(TextDoc.Style.Family.Serif, 1.6, TextDoc.Rgb(0.0, 0.0, 0.0), TextDoc.Rgb(1.0, 1.0, 0.5))
        assertTrue(TextDoc.html(blocks, true, look).contains("line-height: 1.6"))
        assertFalse(TextDoc.html(blocks, true).contains("<style>"))
        assertEquals("#ffff7f", look.background.css)

        val spans = TextDoc.markdownBlocks("# T\n\npara one\npara two\n\n- a\n- b\n\n```\nx\n```\nSet\n===")
        assertEquals(listOf(0 until 1, 2 until 4, 5 until 7, 8 until 11, 11 until 13), spans.map { it.second })
        assertEquals(listOf("H1", "¶", "•", "{ }", "H1"), spans.map { it.first.mark })
        assertEquals(4, spans[1].first.words)
    }

    @Test
    fun rtf() {
        val rtf = TextDoc.rtf(TextDoc.parseMarkdown("# Ti{t}le\n\ncafé **b** [l](https://x)"))
        assertTrue(rtf.startsWith("{\\rtf1"))
        assertTrue(rtf.endsWith("}"))
        assertTrue(rtf.contains("Ti\\{t\\}le"))
        assertTrue(rtf.contains("caf\\u233?"))
        assertTrue(rtf.contains("HYPERLINK \"https://x\""))
        assertTrue(rtf.contains("\\fs48"))
    }

    @Test
    fun manuscript() {
        val text = "# Title\n\nOne two. Three four!\n\n- a\n- b"
        val found = TextEditorEngine.spans(text)
        assertEquals(listOf(0 to 7, 9 to 20, 31 to 7), found.map { it.start to it.length })
        assertEquals(0, TextEditorEngine.block(0, found))
        assertEquals(0, TextEditorEngine.block(8, found))
        assertEquals(1, TextEditorEngine.block(12, found))
        assertEquals(2, TextEditorEngine.block(40, found))
        assertEquals(18 until 29, TextEditorEngine.sentence(20, text, found[1].start, found[1].end))
        assertEquals(9, TextEditorEngine.sentence(9, text, found[1].start, found[1].end)?.first)
        assertEquals(1, TextEditorEngine.spans("a\r\nb").size)
        assertTrue(TextEditorEngine.spans("").isEmpty())
    }

    @Test
    fun counts() {
        assertEquals(3, TextEditorEngine.words(" one two\nthree "))
        assertEquals(0, TextEditorEngine.lines(""))
        assertEquals(3, TextEditorEngine.lines("a\nb\nc"))
        assertEquals(0, TextEditorEngine.readingMinutes(""))
        assertEquals(2, TextEditorEngine.readingMinutes("w ".repeat(201)))
        assertEquals(3, TextEditorEngine.characters("aéb"))
        assertEquals(5, TextEditorEngine.changeEnd("hello", "hello"))
        assertEquals(4, TextEditorEngine.changeEnd("abXef", "abYZef"))
        assertEquals(0, TextEditorEngine.changeEnd("abc", ""))
    }

    @Test
    fun highlight() {
        val marks = TextEditorEngine.highlight("## Head\n**b** and `c` [l](u)")
        assertTrue(marks.any { it.level == 2 && it.start == 0 && it.end == 7 })
        assertTrue(marks.any { it.look == Look.Bold && it.start == 10 && it.end == 11 })
        assertTrue(marks.any { it.look == Look.Code && it.start == 18 })
        assertTrue(marks.any { it.look == Look.Primary && it.start == 23 && it.end == 24 })
        assertTrue(TextEditorEngine.highlight("x".repeat(TextEditorEngine.STYLE_LIMIT + 1)).isEmpty())
    }
}
