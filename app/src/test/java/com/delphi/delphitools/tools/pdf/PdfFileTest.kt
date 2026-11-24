package com.delphi.delphitools.tools.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class PdfFileTest {
    private fun fixture(name: String) = javaClass.getResourceAsStream("/pdf/$name")!!.readBytes()

    // pdf-out holds the written files for checking with pdfinfo or pdftoppm
    private fun keep(name: String, bytes: ByteArray) {
        File("build/pdf-out").apply { mkdirs() }.resolve(name).writeBytes(bytes)
    }

    private fun sizes(file: PdfFile) = file.pages.map {
        val box = file.cropBox(it)
        PdfCropEngine.displaySize(PdfRect.of(box), file.rotation(it))
    }

    private val expected = listOf(PdfSize(595.0, 842.0), PdfSize(792.0, 612.0), PdfSize(200.0, 300.0))

    @Test
    fun readsClassicTable() {
        val file = PdfFile(fixture("quartz.pdf"))
        assertFalse(file.rebuilt)
        assertFalse(file.usesXrefStream)
        assertEquals(expected, sizes(file))
    }

    @Test
    fun readsXrefStreamAndObjectStreams() {
        val file = PdfFile(fixture("xref-stream.pdf"))
        assertFalse(file.rebuilt)
        assertTrue(file.usesXrefStream)
        assertEquals(expected, sizes(file))
        assertNotNull(file.dict(file.trailer["Info"]))
    }

    @Test
    fun rebuildsADamagedChain() {
        val bytes = fixture("quartz.pdf")
        val at = lastIndexOf(bytes, "startxref".toByteArray()) + 10
        bytes[at] = '9'.code.toByte()
        bytes[at + 1] = '9'.code.toByte()
        val file = PdfFile(bytes)
        assertTrue(file.rebuilt)
        assertEquals(expected, sizes(file))
        val page = file.pages[0]
        val updated = PdfFile(file.update(mapOf(page.ref to page.dict + ("Rotate" to 90))))
        assertFalse(updated.rebuilt)
        assertEquals(90, updated.rotation(updated.pages[0]))
    }

    @Test
    fun mergesPagesFromTwoFiles() {
        val a = PdfFile(fixture("quartz.pdf"))
        val b = PdfFile(fixture("xref-stream.pdf"))
        val refs = listOf(
            PdfOrganiserEngine.PageRef(b, 2),
            PdfOrganiserEngine.PageRef(a, 0, rotation = 90),
            PdfOrganiserEngine.PageRef(a, 0),
            PdfOrganiserEngine.PageRef(b, 1, rotation = 180),
        )
        val out = ByteArrayOutputStream()
        PdfOrganiserEngine.build(refs, out, info = a)
        keep("merged.pdf", out.toByteArray())
        val merged = PdfFile(out.toByteArray())
        assertFalse(merged.rebuilt)
        assertEquals(listOf(PdfSize(200.0, 300.0), PdfSize(842.0, 595.0), PdfSize(595.0, 842.0), PdfSize(792.0, 612.0)), sizes(merged))
        assertEquals(listOf(0, 90, 0, 180), merged.pages.map(merged::rotation))
        val content = merged.resolve(merged.pages[0].attribute("Contents")) as PdfStream
        assertTrue(PdfFilters.decode(content)!!.isNotEmpty())
        assertNotNull(merged.dict(merged.trailer["Info"]))
    }

    @Test
    fun inheritsFromThePageTree() {
        val out = ByteArrayOutputStream()
        val writer = PdfWriter(out)
        val root = writer.reserve()
        val top = writer.reserve()
        val middle = writer.reserve()
        val page = writer.reserve()
        writer.write(page, linkedMapOf("Type" to PdfName("Page"), "Parent" to PdfRef(middle)))
        writer.write(middle, linkedMapOf("Type" to PdfName("Pages"), "Parent" to PdfRef(top), "Kids" to listOf(PdfRef(page)), "Count" to 1, "Rotate" to 270))
        writer.write(top, linkedMapOf("Type" to PdfName("Pages"), "Kids" to listOf(PdfRef(middle)), "Count" to 1, "MediaBox" to listOf(0, 0, 300, 100)))
        writer.write(root, linkedMapOf("Type" to PdfName("Catalog"), "Pages" to PdfRef(top)))
        writer.finish(PdfRef(root))
        val file = PdfFile(out.toByteArray())
        assertEquals(listOf(PdfSize(100.0, 300.0)), sizes(file))
        val copy = ByteArrayOutputStream()
        PdfOrganiserEngine.build(listOf(PdfOrganiserEngine.PageRef(file, 0, rotation = 90)), copy)
        val built = PdfFile(copy.toByteArray())
        assertEquals(0, built.rotation(built.pages[0]))
        assertEquals(PdfBox(0.0, 0.0, 300.0, 100.0), built.mediaBox(built.pages[0]))
    }
}
