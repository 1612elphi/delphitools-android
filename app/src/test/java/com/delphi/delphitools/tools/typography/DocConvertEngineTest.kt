package com.delphi.delphitools.tools.typography

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocConvertEngineTest {
    private val e = DocConvertEngine

    @Test
    fun markdownReader() {
        val blocks = e.parseMarkdown("# Title\n\nSome **bold** and `code`.\n\n- one\n- two\n\n> quoted\n")
        assertEquals(4, blocks.size)
        val heading = blocks[0] as DocBlock.Heading
        assertEquals(1, heading.level)
        assertEquals("Title", heading.runs.first().text)
        val list = blocks[2] as DocBlock.Bullets
        assertFalse(list.ordered)
        assertEquals(2, list.items.size)

        val inline = e.parseInline("a **b** [c](https://x) `d`")
        assertTrue(inline.any { it.bold && it.text == "b" })
        assertTrue(inline.any { it.link == "https://x" && it.text == "c" })
        assertTrue(inline.any { it.code && it.text == "d" })
        assertEquals(1, e.parseInline("[".repeat(4000)).size)
        assertEquals(listOf(DocInline("snake_case_word")), e.parseInline("snake_case_word"))

        val spans = e.markdownBlocks("# T\n\npara one\npara two\n\n- a\n- b\n\n```\nx\n```\nSet\n===")
        assertEquals(listOf(0..0, 2..3, 5..6, 8..10, 11..12), spans.map { it.second })
        assertEquals(listOf("H1", "¶", "•", "{ }", "H1"), spans.map { it.first.mark })
        assertEquals(4, spans[1].first.words)
        assertEquals(DocBlock.Heading(2, listOf(DocInline("Closed"))), e.parseMarkdown("## Closed ##").single())
        assertEquals(DocBlock.Bullets(true, listOf(listOf(DocInline("a")), listOf(DocInline("b")))), e.parseMarkdown("1. a\n2) b").single())
        assertEquals(DocBlock.Rule, e.parseMarkdown("* * *").single())
    }

    @Test
    fun htmlReader() {
        val blocks = e.parseHtml("<h2>Hi</h2><p>a <b>b</b></p><ul><li>x</li><li>y</li></ul>")
        assertEquals(3, blocks.size)
        assertEquals(2, (blocks[0] as DocBlock.Heading).level)
        assertEquals(2, (blocks[2] as DocBlock.Bullets).items.size)
        assertEquals(2, (e.parseHtml("<LI>a</LI><li>b</li>").first() as DocBlock.Bullets).items.size)
        assertEquals("AB&#xZZ;", e.decodeEntities("&#x41;&#66;&#xZZ;"))
        assertEquals(
            listOf(DocBlock.Paragraph(listOf(DocInline("see "), DocInline("here", link = "/x"), DocInline(" & there")))),
            e.parseHtml("<head><title>t</title></head><!-- c --><p>see <a href='/x'>here</a> &amp; there</p><script>x</script>"),
        )
        assertEquals(DocBlock.Code("a\n  b"), e.parseHtml("<pre>a\n  b</pre>").single())
    }

    @Test
    fun writersAndDetection() {
        val blocks = e.parseMarkdown("# Title\n\nSome **bold** and `code`.\n\n- one\n- two\n\n> quoted\n")
        assertTrue("# Title" in e.markdown(e.parseHtml(e.html(blocks, standalone = false))))
        assertEquals(DocFormat.Html, e.formatOf("<p>hi</p>"))
        assertEquals(DocFormat.Markdown, e.formatOf("# hi"))
        assertEquals(DocFormat.Plain, e.formatOf("just words"))
        assertEquals(DocFormat.Markdown, e.format("notes.MD"))
        assertFalse("**" in e.plain(blocks))
        assertEquals("<p><a href=\"u\"><strong>b</strong></a></p>", e.html(listOf(DocBlock.Paragraph(listOf(DocInline("b", bold = true, link = "u")))), false))
        assertTrue(e.html(blocks, true).startsWith("<!DOCTYPE html>\n<html>\n<head><meta charset=\"utf-8\"><title>Document</title></head>\n<body>\n<h1>"))
        assertEquals("one\n\ntwo\n", e.plain(e.parsePlain("one\r\n\r\ntwo")))
        assertEquals("", e.markdown(emptyList()))
    }

    @Test
    fun richText() {
        val paragraphs = e.attributed(e.parseMarkdown("# A\n\n- x\n- y"))
        assertEquals(3, paragraphs.size)
        assertEquals(24f, paragraphs[0].runs[0].size)
        assertTrue(paragraphs[0].runs[0].bold)
        assertEquals("•\t", paragraphs[1].runs[0].text)
        val rtf = e.rtf(e.attributed(e.parseMarkdown("é {x} [l](https://u)")))
        assertTrue(rtf.startsWith("{\\rtf1"))
        assertTrue(rtf.endsWith("}"))
        assertTrue("\\u233?" in rtf)
        assertTrue("\\{x\\}" in rtf)
        assertTrue("HYPERLINK \"https://u\"" in rtf)
        val groups = rtf.replace("\\{", "").replace("\\}", "")
        assertEquals(groups.count { it == '{' }, groups.count { it == '}' })
    }

    @Test
    fun ledger() {
        // DocConvertLedger.selfCheck
        val found = e.ledger(e.parseMarkdown("# A\n\nSome **b** and [c](https://x).\n\n- one\n- two\n\n# D"))
        assertEquals(listOf(DocElement.Heading, DocElement.Paragraph, DocElement.Bullets, DocElement.Bold, DocElement.Link), found.map { it.element })
        assertEquals(2, found[0].count)
        assertEquals(1, found[2].count)
        assertEquals("<h1>A</h1>", e.sample(found[0].example, DocFormat.Html))
        assertEquals("- one", e.sample(found[2].example, DocFormat.Markdown))
        assertEquals("b", e.sample(found[3].example, DocFormat.Plain))
        assertTrue(e.isLost(DocElement.Bold, DocFormat.Plain))
        assertFalse(e.isLost(DocElement.Bold, DocFormat.Rtf))
        assertEquals("A", e.richSample(found[0].example).joinToString("") { it.text })
        assertEquals("• one", e.richSample(found[2].example).joinToString("") { it.text })
    }
}
