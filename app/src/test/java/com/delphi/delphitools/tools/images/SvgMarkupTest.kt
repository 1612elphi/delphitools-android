package com.delphi.delphitools.tools.images

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgMarkupTest {
    @Test
    fun recognises() {
        assertTrue(SvgMarkup.looksLikeSvg("  \n<svg/>"))
        assertTrue(SvgMarkup.looksLikeSvg("<?xml version=\"1.0\"?>"))
        assertTrue(SvgMarkup.looksLikeSvg("<!DOCTYPE svg>"))
        assertFalse(SvgMarkup.looksLikeSvg("hello <svg>"))
    }

    @Test
    fun size() {
        assertEquals(40.0 to 20.0, SvgMarkup.size("<svg viewBox=\"0 0 40 20\" width=\"4\" height=\"2\">"))
        assertEquals(30.0 to 10.0, SvgMarkup.size("<svg stroke-width='9' width='30px' height='10'>"))
        assertNull(SvgMarkup.size("<svg width=\"100%\">"))
    }

    @Test
    fun scaling() {
        assertEquals("<svg viewBox=\"0 0 30.0 10.0\" width=\"30\" height=\"10\"/>", SvgMarkup.sizedForScaling("<svg width=\"30\" height=\"10\"/>"))
        val boxed = "<svg viewBox=\"0 0 1 1\"/>"
        assertEquals(boxed, SvgMarkup.sizedForScaling(boxed))
    }

    @Test
    fun outline() {
        val svg = "<svg><!-- note --><g id=\"${"a".repeat(60)}\"><path d='M0 0'/>\n text </g></svg>"
        val expected = listOf(
            "<svg>",
            "  <!-- note -->",
            "  <g id=\"${"a".repeat(40)}…\">",
            "    <path d='M0 0'/>",
            "    text",
            "  </g>",
            "</svg>",
        ).joinToString("\n")
        assertEquals(expected, SvgMarkup.outline(svg))
        val long = SvgMarkup.outline("<svg>" + "<a/>".repeat(300) + "</svg>").lines()
        assertEquals(SvgMarkup.LINE_LIMIT + 1, long.size)
        assertEquals("<!-- 62 more -->", long.last())
    }
}
