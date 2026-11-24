package com.delphi.delphitools.tools.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfRotateCropWriterTest {
    private fun fixture(name: String) = javaClass.getResourceAsStream("/pdf/$name")!!.readBytes()

    // pdf-out holds the written files for checking with pdfinfo or pdftoppm
    private fun keep(name: String, bytes: ByteArray) {
        File("build/pdf-out").apply { mkdirs() }.resolve(name).writeBytes(bytes)
    }

    private fun sizes(file: PdfFile) = file.pages.map { PdfCropEngine.displaySize(PdfRect.of(file.cropBox(it)), file.rotation(it)) }

    @Test
    fun rotatesAndCropsInAnUpdate() {
        for (name in listOf("quartz.pdf", "xref-stream.pdf")) {
            val source = fixture(name)
            val file = PdfFile(source)
            val edits = listOf(
                PdfRotateCropWriter.Edit(rotation = 90),
                PdfRotateCropWriter.Edit(crop = PdfRect(100.0, 50.0, 300.0, 400.0)),
                PdfRotateCropWriter.Edit(rotation = -90, crop = PdfRect(-50.0, -50.0, 150.0, 150.0)),
            )
            val bytes = PdfRotateCropWriter.apply(file, edits)
            keep("rotate-crop-$name", bytes)
            assertTrue(bytes.copyOf(source.size).contentEquals(source))
            val updated = PdfFile(bytes)
            assertFalse(name, updated.rebuilt)
            assertEquals(file.usesXrefStream, updated.usesXrefStream)
            assertEquals(listOf(90, 0, 270), updated.pages.map(updated::rotation))
            assertEquals(PdfBox(100.0, 50.0, 400.0, 450.0), updated.cropBox(updated.pages[1]))
            assertEquals(PdfBox(0.0, 0.0, 100.0, 100.0), updated.cropBox(updated.pages[2]))
            assertEquals(PdfSize(842.0, 595.0), sizes(updated)[0])
            assertEquals(updated.pages[0].dict["Contents"], file.pages[0].dict["Contents"])
        }
    }
}
