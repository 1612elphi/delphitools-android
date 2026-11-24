package com.delphi.delphitools.tools.pdf

import com.delphi.delphitools.tools.pdf.PdfPagesEngine.RangeError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class PdfPagesEngineTest {
    private fun ranges(spec: String) = runCatching { PdfPagesEngine.parsePageRanges(spec, 10) }.getOrNull()

    private fun failure(spec: String) = try {
        PdfPagesEngine.parsePageRanges(spec, 10)
        null
    } catch (e: PdfPagesEngine.RangeException) {
        e.error
    }

    @Test
    fun pageRanges() {
        assertEquals(listOf(listOf(0, 1, 2), listOf(4)), ranges("1-3, 5"))
        assertEquals(listOf(listOf(3, 4, 5), listOf(7)), ranges(" 4 - 6 ,8 "))
        assertEquals(listOf(listOf(8, 9)), ranges("9-10"))
        assertNull(ranges("0"))
        assertNull(ranges("11"))
        assertEquals(listOf(listOf(4, 3, 2), listOf(6)), ranges("5-3, 7"))
        assertEquals(RangeError.OpenEnded(4), failure("4-"))
        assertEquals(RangeError.OpenEnded(4), failure("4 -"))
        assertEquals(RangeError.OutOfRange(12, 10), failure("2-12"))
        assertEquals(RangeError.OutOfRange(12, 10), failure("12-2"))
        assertEquals(RangeError.OutOfRange(0, 10), failure("0-2"))
        assertEquals(RangeError.Malformed("-4"), failure("-4"))
        assertNull(ranges(""))
        assertNull(ranges("1,,2"))
        assertNull(ranges("a-b"))
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), PdfPagesEngine.everyPageAlone(3))
        assertTrue(PdfPagesEngine.everyPageAlone(0).isEmpty())
    }

    @Test
    fun geometry() {
        assertEquals(90, PdfPagesEngine.normaliseRotation(450))
        assertEquals(270, PdfPagesEngine.normaliseRotation(-90))
        assertEquals(0, PdfPagesEngine.normaliseRotation(44))
        val wide = PdfSize(200.0, 100.0)
        val square = PdfSize(100.0, 100.0)
        assertEquals(PdfRect(0.0, 25.0, 100.0, 50.0), PdfPagesEngine.fitPlacement(wide, square, PdfPagesEngine.FitMode.Contain))
        assertEquals(PdfRect(-50.0, 0.0, 200.0, 100.0), PdfPagesEngine.fitPlacement(wide, square, PdfPagesEngine.FitMode.Cover))
        val a4 = PdfPagesEngine.pageSetup(PdfSize(210.0, 297.0), PdfPagesEngine.PageOrientation.Auto, wide, 0.0)
        assertTrue(a4.width > a4.height)
        assertEquals(2.0, PdfPagesEngine.cappedScale(2.0, square, 1_000_000.0), 0.0)
        assertEquals(sqrt(1.6), PdfPagesEngine.cappedScale(300.0 / 72, PdfSize(5000.0, 5000.0), 40_000_000.0), 1e-12)
    }

    @Test
    fun crop() {
        val bounds = PdfSize(200.0, 300.0)
        assertEquals(PdfRect(50.0, 50.0, 50.0, 50.0), PdfCropEngine.intersectBox(PdfRect(0.0, 0.0, 100.0, 100.0), PdfRect(50.0, 50.0, 100.0, 100.0)))
        assertNull(PdfCropEngine.intersectBox(PdfRect(0.0, 0.0, 50.0, 50.0), PdfRect(50.0, 0.0, 50.0, 50.0)))
        assertEquals(PdfRect(10.0, 20.0, 160.0, 240.0), PdfCropEngine.cropFromInsets(bounds, PdfCropEngine.Insets(10.0, 20.0, 30.0, 40.0)))
        assertNull(PdfCropEngine.cropFromInsets(bounds, PdfCropEngine.Insets(100.0, 0.0, 100.0, 0.0)))
        assertNull(PdfCropEngine.cropFromInsets(bounds, PdfCropEngine.Insets(-1.0, 0.0, 0.0, 0.0)))
        assertEquals(PdfCropEngine.Insets(10.0, 20.0, 30.0, 40.0), PdfCropEngine.insets(PdfRect(10.0, 20.0, 160.0, 240.0), bounds))
        assertEquals(PdfRect(50.0, 50.0, 100.0, 200.0), PdfCropEngine.cropToPaper(bounds, PdfSize(100.0, 200.0)))
        val box = PdfRect(10.0, 20.0, 200.0, 300.0)
        for (rotation in listOf(0, 90, 180, 270)) {
            val size = PdfCropEngine.displaySize(box, rotation)
            val rect = PdfRect(5.0, 15.0, size.width / 2, size.height / 4)
            val page = PdfCropEngine.toPage(rect, box, rotation)
            assertTrue(box.contains(page))
            assertEquals(rect, PdfCropEngine.toDisplay(page, box, rotation))
        }
    }
}
