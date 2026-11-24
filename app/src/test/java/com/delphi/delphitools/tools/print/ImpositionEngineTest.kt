package com.delphi.delphitools.tools.print

import com.delphi.delphitools.tools.pdf.PdfFile
import com.delphi.delphitools.tools.pdf.PdfRotateCropWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class ImpositionEngineTest {
    private val base = ImpositionConfig(
        ImpositionLayoutId.SaddleStitch, 210.0, 297.0, ImpositionOrientation.Landscape, 5.0, 2.0, 0.0,
        ImpositionScaling.Fit, true, 4, 2, 2, DuplexFlip.LongEdge,
    )

    @Test
    fun selfCheck() {
        var config = base
        val saddle = ImpositionLayoutId.SaddleStitch.calculate(8, config)
        val front = ImpositionEngine.sheet(saddle.sheets[0].front, config, false)
        assertEquals(297.0 to 210.0, front.width to front.height)
        assertEquals(listOf(8, 1), front.cells.map { it.page })
        assertEquals(1, front.creases.size)
        // two marks per edge
        assertEquals(12, front.marks.size)
        assertTrue(front.marks.all { m -> !(m.x1 > 5.01 && m.x1 < 297 - 5.01 && m.y1 > 5.01 && m.y1 < 210 - 5.01) })

        config = config.copy(layoutId = ImpositionLayoutId.FourUpBooklet, cropMarks = false)
        val booklet = ImpositionLayoutId.FourUpBooklet.calculate(8, config)
        val four = ImpositionEngine.sheet(booklet.sheets[0].front, config, false)
        assertEquals(listOf(180, 180, 0, 0), four.cells.map { it.turn })
        assertEquals(2, four.creases.size)
        assertTrue(four.marks.isEmpty())
        val fitted = four.paperRect(SheetRect(0.0, 0.0, 400.0, 400.0))
        assertEquals(400.0, fitted.width, 0.0)
        assertTrue(abs(fitted.midY - 200) <= 1)

        config = config.copy(layoutId = ImpositionLayoutId.PerfectBind, signaturePages = 16)
        val bound = ImpositionLayoutId.PerfectBind.calculate(38, config)
        assertEquals(List(4) { 0 } + List(4) { 1 } + List(4) { 2 }, bound.sheets.map { it.signature })
        assertEquals(48, bound.totalPages)
        assertEquals(10, bound.blanksAdded)
        assertEquals(listOf(17, 32), bound.sheets[4].front.map { it.pageNumber }.sorted())
        assertEquals(listOf(0, 33), bound.sheets[8].front.map { it.pageNumber }.sorted())
        val placed = bound.sheets.flatMap { sheet -> (sheet.front + sheet.back).map { sheet to it.pageNumber } }
        assertEquals(10, placed.count { it.second == 0 })
        assertTrue(placed.filter { it.second == 0 }.all { it.first.signature == 2 })
        assertEquals((1..38).toSet(), placed.map { it.second }.filter { it > 0 }.toSet())
        assertEquals(
            listOf(ImpositionEngine.Signature(4, 1..16), ImpositionEngine.Signature(4, 17..32), ImpositionEngine.Signature(4, 33..48)),
            ImpositionEngine.signatures(bound.sheets),
        )

        // backs register behind fronts
        for (layout in listOf(ImpositionLayoutId.SaddleStitch, ImpositionLayoutId.PerfectBind, ImpositionLayoutId.FourUpBooklet)) {
            for (orientation in ImpositionOrientation.entries) {
                for (flip in DuplexFlip.entries) {
                    config = config.copy(layoutId = layout, orientation = orientation, duplexFlip = flip, signaturePages = 8)
                    val (width, height) = config.sheetW to config.sheetH
                    for (sheet in layout.calculate(24, config).sheets) {
                        for (page in sheet.front) {
                            val (x, y) = (page.x + page.width / 2) to (page.y + page.height / 2)
                            val seen = if (config.turnsTopOverBottom) x to height - y else width - x to y
                            val reverse = sheet.back.firstOrNull {
                                seen.first > it.x && seen.first < it.x + it.width && seen.second > it.y && seen.second < it.y + it.height
                            }
                            val upright = reverse?.let { ((if (config.turnsTopOverBottom) it.rotation + 180 else it.rotation) % 360) == page.rotation }
                            val partner = if (page.pageNumber % 2 == 1) page.pageNumber + 1 else page.pageNumber - 1
                            assertEquals("$layout $orientation $flip", partner, reverse?.pageNumber)
                            assertEquals(true, upright)
                        }
                    }
                }
            }
        }
        config = config.copy(orientation = ImpositionOrientation.Landscape, duplexFlip = DuplexFlip.LongEdge)
        assertTrue(config.turnsTopOverBottom)
        assertFalse(config.copy(duplexFlip = DuplexFlip.ShortEdge).turnsTopOverBottom)
    }

    @Test
    fun writesFormXObjects() {
        val source = PdfFile(javaClass.getResourceAsStream("/pdf/xref-stream.pdf")!!.readBytes())
        // one page turned, so the placement follows /Rotate
        val file = PdfFile(PdfRotateCropWriter.apply(source, listOf(PdfRotateCropWriter.Edit(), PdfRotateCropWriter.Edit(rotation = 90))))
        val config = base.copy(layoutId = ImpositionLayoutId.FourUpBooklet)
        val result = config.layoutId.calculate(file.pages.size, config)
        val out = File("build/pdf-out").apply { mkdirs() }.resolve("imposed.pdf")
        out.outputStream().use { ImposedPdfWriter.write(file, result, config, it) }
        val written = PdfFile(out.readBytes())
        assertEquals(result.sheets.size * 2, written.pages.size)
        val xobjects = written.dict(written.dict(written.pages[1].attribute("Resources"))!!["XObject"])!!
        assertTrue(xobjects.keys.toString(), xobjects.keys.containsAll(listOf("P2_0", "P1_0")))
        assertEquals("Form", (written.dict(xobjects.getValue("P1_0"))!!["Subtype"] as com.delphi.delphitools.tools.pdf.PdfName).name)
    }
}
