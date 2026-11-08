package com.delphi.delphitools.canon

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// ported from the UIKit selfCheck()s of CanonGlyphRows and CanonSplit
class LayoutCheckTest {
    private val g = CanonGlyphKind.Glyph
    private val s = CanonGlyphKind.Space

    @Test
    fun glyphRows() {
        val wrapped = CanonGlyphRows.layout(listOf(20f, 20f, 10f, 20f, 20f), listOf(g, g, s, g, g), 60f, 0f, 10f, 12f)
        assertEquals(2, wrapped.rows)
        assertEquals(Rect(0f, 12f, 20f, 22f), wrapped.frames[3])
        val spaced = CanonGlyphRows.layout(listOf(50f, 10f, 50f), listOf(g, s, g), 60f, 0f, 10f, 12f)
        assertEquals(2, spaced.rows)
        assertEquals(0f, spaced.frames[2].left)
        assertEquals(2, CanonGlyphRows.layout(listOf(40f, 40f, 40f), listOf(g, g, g), 90f, 5f, 10f, 12f).rows)
        assertEquals(2, CanonGlyphRows.layout(listOf(10f, 0f, 10f), listOf(g, CanonGlyphKind.Newline, g), 90f, 0f, 10f, 12f).rows)
        assertEquals(0, CanonGlyphRows.layout(emptyList(), emptyList(), 90f, 0f, 10f, 12f).rows)
    }

    @Test
    fun splitLayouts() {
        val flatHinge = Rect(0f, 475.5f, 669f, 475.5f)
        val (beside, across) = creases(flatHinge, 669f, 951f)
        assertNull(beside)
        assertEquals(flatHinge, across)
        assertNull(creases(Rect(0f, 900f, 669f, 900f), 669f, 951f).second)
        assertEquals(SplitLayout.Stacked, splitLayout(669.dp, 951.dp, beside = false, across = false, halves = true))
        assertEquals(SplitLayout.Laptop, splitLayout(669.dp, 951.dp, beside = false, across = true, halves = true))
        val vertical = creases(Rect(475.5f, 0f, 475.5f, 669f), 951f, 669f).first
        assertEquals(SplitLayout.Equal, splitLayout(951.dp, 669.dp, beside = vertical != null, across = false, halves = false))
        assertEquals(SplitLayout.Sidebar, splitLayout(1366.dp, 1024.dp, beside = false, across = false, halves = false))
        assertEquals(SplitLayout.Equal, splitLayout(1366.dp, 1024.dp, beside = false, across = false, halves = true))
        assertEquals(SplitLayout.Stacked, splitLayout(402.dp, 874.dp, beside = false, across = false, halves = true))
    }
}
