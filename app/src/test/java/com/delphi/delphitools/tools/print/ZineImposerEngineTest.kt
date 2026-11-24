package com.delphi.delphitools.tools.print

import com.delphi.delphitools.tools.pdf.PdfFile
import com.delphi.delphitools.tools.pdf.PdfFilters
import com.delphi.delphitools.tools.pdf.PdfPicture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ZineImposerEngineTest {
    @Test
    fun selfCheck() {
        val e = ZineImposerEngine
        assertEquals(listOf(4, 1, 2), e.fillOrder(listOf(true, false, false, true, false), 3))
        assertTrue(e.fillOrder(listOf(true, true), 0).isEmpty() && e.fillOrder(emptyList(), 2).isEmpty())
        val mini = e.miniEight()
        val front = e.sheet(mini.sides[0], mini, 297.0, 210.0)
        assertEquals(8, front.cells.size)
        assertEquals(listOf(5, 4, 3, 2), front.cells.filter { it.turn == 180 }.map { it.page })
        assertEquals(
            listOf(PrintSheet.Crease.Valley, PrintSheet.Crease.Mountain, PrintSheet.Crease.Valley, PrintSheet.Crease.Mountain),
            front.creases.map { it.second },
        )
        assertEquals(listOf(SheetLine(74.25, 105.0, 222.75, 105.0)), front.cuts)
        val accordion = e.accordion(4, doubleSided = true, split = false)
        val back = e.sheet(accordion.sides[1], accordion, 297.0, 210.0)
        assertTrue(back.isBack && back.cuts.isEmpty())
        assertEquals(listOf(5, 6, 7, 8), back.cells.map { it.page })
    }

    @Test
    fun accordionVariants() {
        val split = ZineImposerEngine.accordion(6, doubleSided = false, split = true)
        assertEquals(6, split.pageCount)
        assertEquals(12, split.sides.single().placements.size)
        assertEquals(listOf(true, false, true, false, true), split.foldLines.map { it.valley })
        assertEquals(listOf(CutLine(0.0, 0.5, 1.0, 0.5)), split.cutLines)
        assertTrue(split.instructions[1].startsWith("Cut the sheet"))
    }

    @Test
    fun writesOnePagePerSide() {
        val grey = PdfPicture(2, 2, PdfFilters.deflate(byteArrayOf(0, 64, -128, -1)), dct = false, grey = true)
        val layout = ZineImposerEngine.accordion(4, doubleSided = true, split = false)
        val slots = List(8) { if (it % 3 == 0) ZineImposerEngine.Slot(grey, it == 3) else null }
        val out = File("build/pdf-out").apply { mkdirs() }.resolve("zine.pdf")
        out.outputStream().use { ZineImposerEngine.write(layout, slots, 297.0, 210.0, it) }
        val file = PdfFile(out.readBytes())
        assertEquals(2, file.pages.size)
        val xobjects = file.dict(file.dict(file.pages[0].attribute("Resources"))!!["XObject"])!!
        assertEquals(setOf("Im0", "Im3"), xobjects.keys)
    }
}
