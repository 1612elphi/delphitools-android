package com.delphi.delphitools.tools.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ImageToPdfEngineTest {
    @Test
    fun sortsLikeFinder() {
        val names = listOf("page10.png", "Page2.png", "page1.png", "cover.jpg")
        assertEquals(listOf("cover.jpg", "page1.png", "Page2.png", "page10.png"), ImageToPdfEngine.sortedByName(names, true) { it })
        assertEquals(listOf("page10.png", "Page2.png", "page1.png", "cover.jpg"), ImageToPdfEngine.sortedByName(names, false) { it })
    }

    @Test
    fun laysOutOnPaper() {
        val options = ImageToPdfEngine.Options(paperMm = PdfSize(210.0, 297.0), marginMm = 10.0)
        val layout = ImageToPdfEngine.layout(PdfSize(4000.0, 3000.0), options)!!
        assertTrue(layout.page.width > layout.page.height && layout.area.x > 28 && layout.area.contains(layout.placement))
        val matched = ImageToPdfEngine.layout(PdfSize(400.0, 300.0), ImageToPdfEngine.Options())!!
        assertEquals(PdfSize(300.0, 225.0), matched.page)
    }

    @Test
    fun writesReadablePages() {
        val out = ByteArrayOutputStream()
        val document = PdfImageDocument(out)
        val grey = PdfFilters.deflate(ByteArray(4 * 2) { 0x80.toByte() })
        document.add(PdfSize(300.0, 200.0), PdfRect(10.0, 10.0, 280.0, 180.0), PdfPicture(4, 2, grey, dct = false, grey = true, mask = grey))
        document.add(PdfSize(100.0, 100.0), PdfRect(-10.0, 0.0, 120.0, 100.0), PdfPicture(4, 2, grey, dct = false, grey = true), clip = PdfRect(0.0, 0.0, 100.0, 100.0))
        document.finish()
        val file = PdfFile(out.toByteArray())
        assertEquals(2, file.pages.size)
        assertEquals(PdfBox(0.0, 0.0, 300.0, 200.0), file.mediaBox(file.pages[0]))
        val content = String(PdfFilters.decode(file.resolve(file.pages[1].dict["Contents"]) as PdfStream)!!)
        assertTrue(content.contains("0 0 100 100 re W n"))
    }
}
