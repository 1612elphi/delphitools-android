package com.delphi.delphitools.tools.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class PdfCompressEngineTest {
    private fun fixture(name: String) = javaClass.getResourceAsStream("/pdf/$name")!!.readBytes()

    private fun imagePdf(): ByteArray {
        val out = ByteArrayOutputStream()
        val document = PdfImageDocument(out)
        val rgb = PdfFilters.deflate(ByteArray(40 * 20 * 3) { (it % 251).toByte() })
        val mask = PdfFilters.deflate(ByteArray(40 * 20) { 0x7f })
        document.add(PdfSize(100.0, 50.0), PdfRect(0.0, 0.0, 100.0, 50.0), PdfPicture(40, 20, rgb, dct = false, mask = mask))
        document.finish()
        return out.toByteArray()
    }

    @Test
    fun picksPlainRgbImages() {
        val file = PdfFile(imagePdf())
        val streams = (1 until file.size).mapNotNull { file.obj(it) as? PdfStream }
        val found = streams.mapNotNull { PdfCompressEngine.samples(file, it) }
        assertEquals(1, found.size)
        assertEquals(40 * 20 * 3, found[0].rgb!!.size)
        assertNull(found[0].jpeg)
    }

    @Test
    fun skipsImagesTooLargeOrShorterThanTheirSize() {
        val file = PdfFile(imagePdf())
        val image = (1 until file.size).mapNotNull { file.obj(it) as? PdfStream }.first { PdfCompressEngine.samples(file, it) != null }
        // int overflow to 0
        val huge = PdfStream(LinkedHashMap(image.dict).apply { put("Width", 65536); put("Height", 65536) }, image.data)
        assertNull(PdfCompressEngine.samples(file, huge))
        val tall = PdfStream(LinkedHashMap(image.dict).apply { put("Height", 4000) }, PdfFilters.deflate(ByteArray(40 * 8000 * 3)))
        assertNull(PdfCompressEngine.samples(file, tall))
    }

    @Test
    fun recompressesSmallerImagesOnly() {
        val file = PdfFile(imagePdf())
        val fakeJpeg = PdfPicture(20, 10, ByteArray(10), dct = true)
        val out = ByteArrayOutputStream()
        PdfCompressEngine.rewrite(file, out, encode = { fakeJpeg })
        val rewritten = PdfFile(out.toByteArray())
        val images = (1 until rewritten.size).mapNotNull { rewritten.obj(it) as? PdfStream }.filter { (it.dict["Subtype"] as? PdfName)?.name == "Image" }
        val jpeg = images.single { PdfFilters.filters(it.dict) == listOf("DCTDecode") }
        assertEquals(20, jpeg.dict["Width"])
        assertTrue(rewritten.dict(jpeg.dict["SMask"]) != null)

        val big = PdfPicture(20, 10, ByteArray(100_000), dct = true)
        val kept = ByteArrayOutputStream()
        PdfCompressEngine.rewrite(file, kept, encode = { big })
        val unchanged = PdfFile(kept.toByteArray())
        assertTrue((1 until unchanged.size).none { (unchanged.obj(it) as? PdfStream)?.let { s -> PdfFilters.filters(s.dict) } == listOf("DCTDecode") })
    }

    @Test
    fun keepsEveryPage() {
        for (name in listOf("quartz.pdf", "xref-stream.pdf")) {
            val file = PdfFile(fixture(name))
            val out = ByteArrayOutputStream()
            PdfCompressEngine.rewrite(file, out)
            File("build/pdf-out").apply { mkdirs() }.resolve("keep-$name").writeBytes(out.toByteArray())
            val kept = PdfFile(out.toByteArray())
            assertEquals(file.pages.size, kept.pages.size)
            assertEquals(file.pages.map(file::cropBox), kept.pages.map(kept::cropBox))
            assertTrue(kept.dict(kept.trailer["Info"]) != null)
        }
    }

    @Test
    fun deflatesPlainStreams() {
        val plain = PdfStream(linkedMapOf(), ByteArray(1000) { 'a'.code.toByte() })
        val packed = PdfCompressEngine.deflated(plain)
        assertEquals(PdfName("FlateDecode"), packed.dict["Filter"])
        assertTrue(PdfFilters.decode(packed)!!.contentEquals(plain.data))
    }
}
