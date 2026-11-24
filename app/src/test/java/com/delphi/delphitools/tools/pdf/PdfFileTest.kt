package com.delphi.delphitools.tools.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    private val catalog = "<< /Type /Catalog /Pages 2 0 R >>"
    private val tree = "<< /Type /Pages /Kids [3 0 R] /Count 1 >>"
    private val page = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 200] >>"

    /** [objects] numbered from 1, then a classic table whose entry for object n points at the offset of [xref]`[n - 1]`, 0 for free. */
    private fun raw(objects: List<String>, xref: List<Int> = objects.indices.map { it + 1 }, root: Int = 1): ByteArray {
        val text = StringBuilder("%PDF-1.4\n")
        val at = objects.mapIndexed { index, body ->
            text.length.also { text.append("${index + 1} 0 obj\n$body\nendobj\n") }
        }
        val start = text.length
        text.append("xref\n0 ${xref.size + 1}\n0000000000 65535 f\r\n")
        for (target in xref) {
            text.append(if (target == 0) "0000000000 65535 f\r\n" else String.format("%010d 00000 n\r\n", at[target - 1]))
        }
        text.append("trailer\n<< /Size ${xref.size + 1} /Root $root 0 R >>\nstartxref\n$start\n%%EOF\n")
        return text.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun refuses(bytes: ByteArray) {
        try {
            PdfFile(bytes)
            fail("parsed")
        } catch (_: PdfSyntaxException) {
        }
    }

    @Test
    fun rejectsAnObjectAtAnotherNumbersOffset() {
        // every entry shifted by one object: 1 points at 2, 2 at 3, 3 at 1
        val file = PdfFile(raw(listOf(catalog, tree, page), xref = listOf(2, 3, 1)))
        assertTrue(file.rebuilt)
        assertEquals(listOf(PdfSize(100.0, 200.0)), sizes(file))
    }

    @Test
    fun rebuildsWhenTheChainHasNoPages() {
        val file = PdfFile(raw(listOf(catalog, tree, page), xref = listOf(1, 2, 0)))
        assertTrue(file.rebuilt)
        assertEquals(1, file.pages.size)
    }

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

    private fun chain(depth: Int) = raw(
        listOf("<< /Type /Catalog /Pages 2 0 R >>") +
            (1..depth).map { "<< /Type /Pages /Kids [${it + 2} 0 R] /Count 1 >>" } +
            "<< /Type /Page >>",
    )

    @Test
    fun walksADeepPageTreeOnASmallStack() {
        val bytes = chain(3000)
        var pages = -1
        var error: Throwable? = null
        // 256 KB, a worker thread's stack on a small device
        val thread = Thread(null, { runCatching { pages = PdfFile(bytes).pages.size }.onFailure { error = it } }, "walk", 256L * 1024)
        thread.start()
        thread.join()
        assertEquals(null, error)
        assertEquals(1, pages)
    }

    @Test
    fun refusesAPageTreeDeeperThanTheLimit() = refuses(chain(20000))

    @Test(timeout = 5000)
    fun rejectsAnXrefStreamWithZeroWidthEntries() {
        val text = StringBuilder("%PDF-1.5\n")
        val objects = listOf(catalog, tree, page)
        objects.forEachIndexed { index, body -> text.append("${index + 1} 0 obj\n$body\nendobj\n") }
        val start = text.length
        text.append("4 0 obj\n<< /Type /XRef /W [0 0 0] /Size 2147483647 /Root 1 0 R /Length 0 >>\nstream\n\nendstream\nendobj\n")
        text.append("startxref\n$start\n%%EOF\n")
        val file = PdfFile(text.toString().toByteArray(Charsets.ISO_8859_1))
        assertTrue(file.rebuilt)
        assertEquals(listOf(PdfSize(100.0, 200.0)), sizes(file))
    }

    @Test
    fun rebuildSkipsHeadersInsideStreams() {
        val fake = "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 5 5] >>\nendobj"
        val bytes = raw(listOf(catalog, tree, page, "<< /Length ${fake.length} >>\nstream\n$fake\nendstream"))
        val at = lastIndexOf(bytes, "startxref".toByteArray()) + 10
        bytes[at] = '9'.code.toByte()
        val file = PdfFile(bytes)
        assertTrue(file.rebuilt)
        assertEquals(listOf(PdfSize(100.0, 200.0)), sizes(file))
    }
}
