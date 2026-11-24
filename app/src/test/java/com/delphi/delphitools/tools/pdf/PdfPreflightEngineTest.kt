package com.delphi.delphitools.tools.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class PdfPreflightEngineTest {
    private fun fixture(name: String) = javaClass.getResourceAsStream("/pdf/$name")!!.readBytes()

    private fun stream(text: String) = PdfStream(linkedMapOf(), text.toByteArray(Charsets.ISO_8859_1))

    private fun sample(): PdfFile {
        val out = ByteArrayOutputStream()
        val writer = PdfWriter(out)
        val tree = writer.reserve()
        val font = writer.add(linkedMapOf("Type" to PdfName("Font"), "Subtype" to PdfName("Type1"), "BaseFont" to PdfName("Helvetica")))
        val image = writer.add(
            PdfStream(
                linkedMapOf(
                    "Type" to PdfName("XObject"), "Subtype" to PdfName("Image"), "Width" to 100, "Height" to 50,
                    "ColorSpace" to PdfName("DeviceGray"), "BitsPerComponent" to 8,
                ),
                ByteArray(5000),
            ),
        )
        val content = """
            q 100 0 0 50 50 50 cm /Im0 Do Q
            BI /W 2 /H 1 /CS /G /BPC 8 ID ab EI
            0.1 w 0 0 m 10 10 l S
            /GS0 gs 0.5 0.4 0.4 1 k 0 0 10 10 re f
            /CS0 cs 1 0 0 sc BT /F1 12 Tf (Hi) Tj ET
        """.trimIndent()
        val first = writer.add(
            linkedMapOf(
                "Type" to PdfName("Page"), "Parent" to PdfRef(tree), "MediaBox" to listOf(0, 0, 612, 792),
                "TrimBox" to listOf(9, 9, 603, 783), "BleedBox" to listOf(3, 3, 609, 789),
                "Resources" to linkedMapOf(
                    "Font" to linkedMapOf("F1" to font),
                    "XObject" to linkedMapOf("Im0" to image),
                    "ExtGState" to linkedMapOf("GS0" to linkedMapOf("ca" to 0.5, "OP" to true)),
                    "ColorSpace" to linkedMapOf("CS0" to PdfName("DeviceRGB")),
                ),
                "Contents" to writer.add(stream(content)),
            ),
        )
        val second = writer.add(
            linkedMapOf("Type" to PdfName("Page"), "Parent" to PdfRef(tree), "MediaBox" to listOf(0, 0, 595.276, 841.89), "Contents" to writer.add(stream(""))),
        )
        writer.write(tree, linkedMapOf("Type" to PdfName("Pages"), "Kids" to listOf(first, second), "Count" to 2))
        val catalog = writer.add(linkedMapOf("Type" to PdfName("Catalog"), "Pages" to PdfRef(tree)))
        writer.finish(catalog)
        return PdfFile(out.toByteArray())
    }

    @Test
    fun readsFacts() {
        val d = PdfPreflightEngine.read(sample())
        assertEquals("1.7", d.pdfVersion)
        assertEquals(listOf(PdfPreflightEngine.Font("Helvetica", "Type1", false, false, listOf(0))), d.fonts)
        val image = d.images.single()
        assertEquals(72.0, image.ppiX, 1e-9)
        assertEquals(72.0, image.ppiY, 1e-9)
        assertTrue(d.transparency)
        assertEquals(mapOf("DeviceRGB" to listOf(0)), d.colourSpaces)
        assertEquals(listOf(0.1), d.strokes.map { it.width })
        assertEquals(PdfBox(9.0, 9.0, 603.0, 783.0), d.pages[0].trimBox)
        assertEquals(listOf(6.0, 6.0, 6.0, 6.0), d.pages[0].bleedMargins)
        assertNull(d.pages[1].trimBox)
        val names = d.colourUsages.map { it.name }
        assertTrue(names.toString(), "C=50 M=40 Y=40 K=100" in names && "R=255 G=0 B=0" in names)
        assertEquals(listOf(PdfPreflightEngine.Overprint(0, "fill", false), PdfPreflightEngine.Overprint(0, "text", false)), d.overprints)
    }

    @Test
    fun runsProfiles() {
        val d = PdfPreflightEngine.read(sample())
        val x1a = PdfPreflightEngine.run(PdfPreflightEngine.profiles[0], d)
        assertFalse(x1a.passed)
        val byId = x1a.results.associateBy { it.typeId }
        assertEquals("1 font(s) not embedded", byId.getValue("fonts.not_embedded").message)
        assertEquals("DeviceRGB colour space detected", byId.getValue("colour.space_used").message)
        assertEquals(setOf(1), byId.getValue("marks.trim_box_set").pages)
        assertEquals("1 page(s) do not match target size (595.3 x 841.9 pt)", byId.getValue("pages.size").message)
        assertEquals("img_0_0: 72 PPI", byId.getValue("images.resolution_below").detail)
        val print = PdfPreflightEngine.run(PdfPreflightEngine.profiles.first { it.name == "Digital Print" }, d)
        val rich = print.results.first { it.typeId == "colour.rich_black" }
        assertEquals("1 rich black colour(s) detected", rich.message)
        assertEquals("Fill overprint detected on 1 usage(s)", print.results.first { it.typeId == "colour.overprint" && it.status == PdfPreflightEngine.Status.Fail }.message)
        val news = PdfPreflightEngine.run(PdfPreflightEngine.profiles.first { it.name == "Newspaper" }, d)
        assertEquals("Thinnest: 0.100 pt on page 1", news.results.first { it.typeId == "lines.stroke_below" }.detail)
        assertEquals(mapOf(0 to (2 to 4), 1 to (2 to 2)), PdfPreflightEngine.pageIssues(x1a.results))
    }

    @Test
    fun readsQuartzFixture() {
        val d = PdfPreflightEngine.read(PdfFile(fixture("quartz.pdf")))
        assertEquals("1.3", d.pdfVersion)
        assertTrue(d.producer!!.contains("Quartz"))
        assertEquals(listOf("AAAAAB+Helvetica"), d.fonts.map { it.name })
        assertTrue(d.fonts.single().embedded && d.fonts.single().subset)
        val loose = PdfPreflightEngine.run(PdfPreflightEngine.profiles.first { it.name == "Loose" }, d)
        assertTrue(loose.passed)
    }

    @Test
    fun scansOperators() {
        val ops = ArrayList<String>()
        PdfPreflightEngine.scan("[(a) 1 (b)] TJ BI /W 1 ID xEIy EI 2 w (x) ' <0a> Tj".toByteArray()) { op, operands -> ops.add("$op${operands.size}") }
        assertEquals(listOf("TJ1", "w1", "'1", "Tj1"), ops)
    }
}
