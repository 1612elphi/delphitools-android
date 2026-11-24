package com.delphi.delphitools.tools.pdf

import com.delphi.delphitools.tools.pdf.PdfStampEngine.Anchor
import com.delphi.delphitools.tools.pdf.PdfStampEngine.NumberSection
import com.delphi.delphitools.tools.pdf.PdfStampEngine.NumeralStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfStampEngineTest {
    private fun fixture(name: String) = javaClass.getResourceAsStream("/pdf/$name")!!.readBytes()

    @Test
    fun selfCheck() {
        val e = PdfStampEngine
        val page = PdfSize(600.0, 800.0)
        assertEquals(20.0 to 768.0, e.placeText(page, 100.0, 12.0, Anchor.TopLeft, 20.0))
        assertEquals(480.0 to 20.0, e.placeText(page, 100.0, 12.0, Anchor.BottomRight, 20.0))
        assertEquals(250.0 to 394.0, e.placeText(page, 100.0, 12.0, Anchor.MiddleCenter, 20.0))
        assertEquals("Page 3 of 9, 3", e.expandNumber("Page {n} of {N}, {n}", "3", "9"))
        assertEquals("iv", e.formatNumeral(4, NumeralStyle.RomanLower))
        assertEquals("XIV", e.formatNumeral(14, NumeralStyle.RomanUpper))
        assertEquals("a", e.formatNumeral(1, NumeralStyle.AlphaLower))
        assertEquals("aa", e.formatNumeral(27, NumeralStyle.AlphaLower))
        assertEquals("AB", e.formatNumeral(28, NumeralStyle.AlphaUpper))
        assertEquals("0", e.formatNumeral(0, NumeralStyle.RomanUpper))
        assertEquals("4000", e.formatNumeral(4000, NumeralStyle.RomanLower))
        assertEquals("0", e.formatNumeral(0, NumeralStyle.AlphaLower))
        val sections = listOf(NumberSection(3, NumeralStyle.Arabic, 1), NumberSection(2, NumeralStyle.RomanLower, 1))
        assertEquals(listOf(null, "i", "1", "2"), e.resolvePageNumbers(sections, 4))
        assertEquals(listOf("5", "6"), e.resolvePageNumbers(listOf(NumberSection(1, NumeralStyle.Arabic, 5)), 2))
        assertTrue(e.resolvePageNumbers(sections, 0).isEmpty())
        assertNull(e.resolveSection(sections, 1))
        assertEquals(NumeralStyle.RomanLower, e.resolveSection(sections, 2)?.style)
        assertEquals(NumeralStyle.Arabic, e.resolveSection(sections, 9)?.style)
        assertEquals(listOf(null, null, "1"), e.resolvePageNumbers(listOf(NumberSection(99)), 3))
        assertEquals(listOf("1", "2"), e.resolvePageNumbers(listOf(NumberSection(Int.MIN_VALUE)), 2))
        assertEquals(2, e.resolvePageNumbers(listOf(NumberSection(1, NumeralStyle.Arabic, Int.MAX_VALUE)), 2).size)
    }

    @Test
    fun widths() {
        for (font in PdfStampEngine.FontFamily.entries) assertEquals(224, font.widths.size)
        assertEquals(556 * 3 * 10 / 1000.0, PdfStampEngine.width("123", PdfStampEngine.FontFamily.Sans, 10.0), 1e-9)
        assertEquals("?".toByteArray().toList(), PdfStampEngine.encode("中").toList())
    }

    @Test
    fun displayMatrixMapsTheViewedCorners() {
        val box = PdfRect(10.0, 20.0, 200.0, 300.0)
        for (rotation in listOf(0, 90, 180, 270)) {
            val m = PdfStampEngine.displayMatrix(box, rotation)
            val size = PdfCropEngine.displaySize(box, rotation)
            // display top-left, through PdfCropEngine's own mapping, must match the matrix
            val expected = PdfCropEngine.toPage(PdfRect(0.0, 0.0, 1.0, 1.0), box, rotation)
            val x = m[2] * size.height + m[4]
            val y = m[3] * size.height + m[5]
            assertTrue("rotation $rotation", expected.x - 1 <= x && x <= expected.maxX + 1 && expected.y - 1 <= y && y <= expected.maxY + 1)
        }
    }

    @Test
    fun stampsEveryPage() {
        val file = PdfFile(fixture("xref-stream.pdf"))
        val options = PdfStampEngine.Options(stampOn = true, stampText = "DRAFT", stampMode = PdfStampEngine.StampMode.Watermark)
        val bytes = PdfStampEngine.stamp(file, options)
        File("build/pdf-out").apply { mkdirs() }.resolve("numbered.pdf").writeBytes(bytes)
        val turned = PdfFile(PdfRotateCropWriter.apply(file, listOf(PdfRotateCropWriter.Edit(rotation = 90), PdfRotateCropWriter.Edit(rotation = 270))))
        File("build/pdf-out/numbered-turned.pdf").writeBytes(PdfStampEngine.stamp(turned, options.copy(numberAnchor = Anchor.BottomRight)))
        val out = PdfFile(bytes)
        assertEquals(3, out.pages.size)
        val last = out.pages[2]
        val contents = out.resolve(last.dict["Contents"]) as List<*>
        val stamp = PdfFilters.decode(out.resolve(contents.last()) as PdfStream)!!.toString(Charsets.ISO_8859_1)
        assertTrue(stamp, stamp.contains("(3 / 3) Tj") && stamp.contains("(DRAFT) Tj"))
        assertTrue(out.dict(out.dict(last.attribute("Resources"))!!["Font"])!!.containsKey("DTStampF"))
        // first page number
        assertTrue(PdfFilters.decode(out.resolve((out.resolve(out.pages[0].dict["Contents"]) as List<*>).last()) as PdfStream)!!
            .toString(Charsets.ISO_8859_1).contains("(1 / 3) Tj"))
    }

    @Test
    fun closesSavesTheContentLeftOpen() {
        val content = "q 2 0 0 2 0 0 cm q (Q) Tj Q BI /W 1 /H 1 ID Q\u0000 EI q <51> Tj"
        val out = java.io.ByteArrayOutputStream()
        val writer = PdfWriter(out)
        val root = writer.reserve()
        val tree = writer.reserve()
        val stream = writer.add(PdfStream(linkedMapOf(), content.toByteArray(Charsets.ISO_8859_1)))
        val page = writer.add(linkedMapOf("Type" to PdfName("Page"), "Parent" to PdfRef(tree), "MediaBox" to listOf(0, 0, 100, 100), "Contents" to stream))
        writer.write(tree, linkedMapOf("Type" to PdfName("Pages"), "Kids" to listOf(page), "Count" to 1))
        writer.write(root, linkedMapOf("Type" to PdfName("Catalog"), "Pages" to PdfRef(tree)))
        writer.finish(PdfRef(root))
        val file = PdfFile(out.toByteArray())
        val stamped = PdfFile(PdfStampEngine.stamp(file, PdfStampEngine.Options(stampOn = true, stampText = "DRAFT")))
        val contents = stamped.resolve(stamped.pages[0].dict["Contents"]) as List<*>
        val stamp = PdfFilters.decode(stamped.resolve(contents.last()) as PdfStream)!!.toString(Charsets.ISO_8859_1)
        // the save before the content and the two the content left open
        assertTrue(stamp, stamp.startsWith("\nQ\nQ\nQ\nq "))
    }
}
