package com.delphi.delphitools.tools.print

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PaperSizesEngineTest {
    @Test
    fun query() {
        assertEquals(PaperQuery.None, PaperQuery.parse("  "))
        assertEquals(PaperQuery.Name("a4"), PaperQuery.parse("A4"))
        assertEquals(PaperQuery.Dimensions(210.0, 297.0, null), PaperQuery.parse("210x297"))
        assertEquals(PaperQuery.Dimensions(8.5 * 25.4, 11 * 25.4, null), PaperQuery.parse("8,5 × 11"))
        val pixels = PaperQuery.parse("2480x3508 @300dpi") as PaperQuery.Dimensions
        assertTrue(abs(pixels.width - 209.97) < 0.01 && pixels.dpi == 300.0)
        assertEquals("A4", PaperQuery.closest(297.0, 210.0).first().pick.size.id)
        assertEquals("Letter", PaperQuery.closest(215.9, 279.4).first().pick.size.name)
        assertEquals("ANSI A", PaperQuery.closest(216.0, 279.0).first().pick.size.name)
    }

    @Test
    fun units() {
        assertEquals("8½" to true, PaperUnit.eighths(8.5))
        assertEquals("8¼" to false, PaperUnit.eighths(8.27))
        assertEquals("⅜" to true, PaperUnit.eighths(0.375))
        assertEquals("11", PaperUnit.decimal(11.0))
        assertEquals("8.27", PaperUnit.decimal(8.268))
        val a4 = PaperPick.named("a4")!!.size
        assertEquals("210 × 297 mm", PaperUnit.Mm.text(a4, 300.0))
        assertEquals("2480 × 3508 px", PaperUnit.Pixels.text(a4, 300.0))
        assertEquals("595.3 × 841.9 pt", PaperUnit.Points.text(a4, 300.0))
    }

    @Test
    fun frames() {
        val area = Rect(0f, 0f, 400f, 500f)
        val a4 = Size(210f, 297f)
        val a3 = Size(297f, 420f)
        val stacked = PaperGeometry.frames(listOf(a4, a3), PaperArrangement.Stacked, area, flipped = false)
        assertTrue(stacked[0].left == stacked[1].left && stacked[0].bottom == stacked[1].bottom)
        val side = PaperGeometry.frames(listOf(a4, a3), PaperArrangement.SideBySide, area, flipped = false)
        assertTrue(abs(side[1].left - side[0].right - PaperGeometry.GAP) < 0.001f && side[0].bottom == side[1].bottom)
        assertTrue(side.all { area.inflate(0.001f).contains(it.topLeft) && area.inflate(0.001f).contains(it.bottomRight) })
        val above = PaperGeometry.frames(listOf(a4, a3), PaperArrangement.AboveBelow, area, flipped = false)
        assertTrue(abs(above[1].top - above[0].bottom - PaperGeometry.GAP) < 0.001f)
        assertTrue(above.all { area.inflate(0.001f).contains(it.topLeft) && area.inflate(0.001f).contains(it.bottomRight) })
        assertEquals("2", PaperGeometry.ratio(62370.0 / 31080))
        assertEquals("4", PaperGeometry.ratio(124740.0 / 31080))
        assertEquals("1.03", PaperGeometry.ratio(64130.0 / 62370))
    }
}
