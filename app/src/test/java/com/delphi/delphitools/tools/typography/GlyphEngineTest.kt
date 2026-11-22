package com.delphi.delphitools.tools.typography

import com.delphi.delphitools.tools.typography.GlyphEngine.Find
import com.delphi.delphitools.tools.typography.GlyphGeometry.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphEngineTest {
    private val blocks = listOf(UnicodeBlock("Basic Latin", 0x20..0x7F), UnicodeBlock("Arrows", 0x2190..0x21FF))

    @Test
    fun find() {
        assertEquals(Find.CodePoint(0x2192), GlyphEngine.find("U+2192", blocks))
        assertEquals(Find.CodePoint(0x41), GlyphEngine.find("0x41", blocks))
        assertEquals(Find.CodePoint(0x2192), GlyphEngine.find("→", blocks))
        assertEquals(Find.CodePoint(0x1F600), GlyphEngine.find("1f600", blocks))
        assertEquals(Find.Block(1), GlyphEngine.find("arrow", blocks))
        assertEquals(Find.CodePoint(0xCAFE), GlyphEngine.find("cafe", blocks))
        assertNull(GlyphEngine.find("zzzz", blocks))
        assertNull(GlyphEngine.find("  ", blocks))
        // surrogate is no character
        assertNull(GlyphEngine.find("U+D800", blocks))
        assertEquals(1, GlyphEngine.index(0x2195, blocks))
        assertNull(GlyphEngine.index(0x10, blocks))
    }

    @Test
    fun category() {
        assertEquals("Lowercase Letter", GlyphEngine.category(0x67))
        assertEquals("Math Symbol", GlyphEngine.category(0x2192))
    }

    @Test
    fun values() {
        assertEquals("\\u0041", GlyphEngine.js(0x41))
        assertEquals("\\u{1F600}", GlyphEngine.js(0x1F600))
        assertEquals("F0 9F 98 80", GlyphEngine.valueLines(0x1F600)[2].second)
        assertEquals("\\0041", GlyphEngine.valueLines(0x41)[4].second)
        assertEquals("LATIN SMALL LETTER G", GlyphEngine.name(0x67))
        assertEquals("Noto Sans Shavian", GlyphEngine.familyFromFile("NotoSansShavian-Regular.ttf"))
        assertEquals("Roboto", GlyphEngine.familyFromFile("Roboto-Regular.ttf"))
    }

    @Test
    fun sections() {
        val latin = GlyphSection(null, 0x20..0x7F)
        // control characters left out
        assertEquals(6, latin.masks.size)
        assertFalse(latin.shows(0x7F))
        assertTrue(latin.shows(0x41))
        assertFalse(GlyphEngine.isVisible(0x0378))
        assertEquals("◌́", GlyphEngine.shown(0x0301))
        assertEquals("A", GlyphEngine.shown(0x41))
    }

    @Test
    fun geometry() {
        val latin = GlyphSection(null, 0x20..0x7F)
        val narrow = GlyphGeometry(listOf(latin), 376f)
        assertEquals(8, narrow.columns)
        assertEquals(40.5f, narrow.cell)
        assertEquals(13, narrow.items.size)
        val wide = GlyphGeometry(listOf(latin), 700f)
        assertEquals(16, wide.columns)
        assertEquals(7, wide.items.size)
        assertEquals(GlyphGeometry.DIGITS + 6 * wide.cell, wide.height)
        assertEquals(0, wide.item(0f))
        assertEquals(2, wide.item(GlyphGeometry.DIGITS + wide.cell * 1.5f))
        assertNull(wide.item(wide.height))
        assertEquals(3, wide.row(0x41))
        assertEquals(1, wide.row(0x20))
        assertNull(wide.row(0x10))
        assertNull(wide.row(0x90))

        val greek = GlyphSection("Greek", 0x0370..0x037F)
        val unassigned = GlyphSection("None", 0x0380..0x0383)
        val arrows = GlyphSection("Arrows", 0x2190..0x219F)
        assertTrue(unassigned.isEmpty)
        val titled = GlyphGeometry(listOf(greek, unassigned, arrows), 700f)
        assertEquals(listOf(Item.Heading(0), Item.Row(0x370, 0), Item.Heading(2), Item.Row(0x2190, 2)), titled.items)
        assertEquals(GlyphGeometry.HEADING, titled.tops[1])
        assertEquals(3, titled.row(0x2195))
        assertEquals(1, titled.row(0x375))
        assertNull(titled.row(0x1000))
    }

    @Test
    fun parseBlocks() {
        val parsed = GlyphEngine.parseBlocks("""[[0,127,"Basic Latin"],[128,255,"Latin-1 Supplement"]]""")
        assertEquals(listOf(UnicodeBlock("Basic Latin", 0..127), UnicodeBlock("Latin-1 Supplement", 128..255)), parsed)
    }
}
