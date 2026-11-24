package com.delphi.delphitools.tools.devtools

import com.delphi.delphitools.colour.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeEngineTest {
    private val black = Rgb(0.0, 0.0, 0.0)
    private val white = Rgb(1.0, 1.0, 1.0)

    @Test
    fun ean13() {
        assertEquals(7, Ean13.checkDigit(listOf(5, 9, 0, 1, 2, 3, 4, 1, 2, 3, 4, 5)))
        assertEquals(95, Ean13.bars("5901234123457")?.size)
        assertNull(Ean13.bars("5901234123458"))
        val added = BarcodeEngine.modules("590123412345", BarcodeKind.Ean13) as BarcodeModules.Code
        assertEquals("5901234123457", added.text)
        assertTrue(BarcodeEngine.modules("12345", BarcodeKind.Ean13) is BarcodeModules.Failure)
        assertEquals(BarcodeModules.Failure("Invalid EAN-13 code. Check the check digit."), BarcodeEngine.modules("5901234123458", BarcodeKind.Ean13))
    }

    @Test
    fun ean13Modules() {
        fun modules(code: String) = Ean13.bars(code)!!.joinToString("") { if (it) "1" else "0" }
        // from the published l, g and r tables; 1253670000002 puts 5, 6 and 7 in g positions
        assertEquals("10100011010100111010111101111010001001011001101010100001010000101000010111010010000101100110101", modules("4006381333931"))
        assertEquals("10100010110100111011001100100110111101001110101010110011011011001000010101110010011101000100101", modules("5901234123457"))
        assertEquals("10100100110110001010000101011110010001010011101010111001011100101110010111001011100101101100101", modules("1253670000002"))
    }

    @Test
    fun ean13Tables() {
        for (digit in 0..9) {
            val r = Ean13.rPatterns[digit]
            assertEquals(r.reversed(), Ean13.gPatterns[digit])
            assertEquals(r.map { if (it == '1') '0' else '1' }.joinToString(""), Ean13.lPatterns[digit])
        }
    }

    @Test
    fun code128() {
        val code = BarcodeEngine.modules("Hello World", BarcodeKind.Code128) as BarcodeModules.Code
        val bars = code.rows.single()
        // start b, 11 characters, check, 11 modules each, then the 13-module stop
        assertEquals(11 * 13 + 13, bars.size)
        assertEquals("11010010000", bars.take(11).joinToString("") { if (it) "1" else "0" })
        assertEquals("1100011101011", bars.takeLast(13).joinToString("") { if (it) "1" else "0" })
        assertTrue(BarcodeEngine.modules("é", BarcodeKind.Code128) is BarcodeModules.Failure)
        assertEquals(BarcodeModules.Empty, BarcodeEngine.modules("", BarcodeKind.Code128))
    }

    @Test
    fun twoDimensional() {
        val aztec = BarcodeEngine.modules("Hello", BarcodeKind.Aztec) as BarcodeModules.Code
        // compact one-layer aztec
        assertEquals(15, aztec.rows.size)
        assertEquals(15, aztec.rows[0].size)
        val pdf = BarcodeEngine.modules("Hello", BarcodeKind.Pdf417) as BarcodeModules.Code
        assertTrue(pdf.rows.size >= 3)
        assertTrue(pdf.rows.all { it.size == pdf.rows[0].size })
        assertTrue(BarcodeEngine.modules("日本", BarcodeKind.Aztec) is BarcodeModules.Failure)
    }

    @Test
    fun sheets() {
        val table = CodeSheet.zigzag(21)
        assertEquals(441, table.toSet().size)
        assertEquals(0, table[20 * 21 + 20])
        assertEquals(1, table[20 * 21 + 19])
        assertEquals(2, table[19 * 21 + 20])
        assertEquals(42, table[0 * 21 + 18])
        val sheet = CodeSheet(
            listOf(booleanArrayOf(true, true, false, true)), 2f, CodeSheet.Margin(1f),
            ink = black, paper = white, sweep = CodeSheet.Sweep.Columns, anchor = CodeSheet.Anchor.Leading,
        )
        assertEquals(6f, sheet.width)
        assertEquals(4f, sheet.height)
        assertEquals(18 to 12, sheet.pixelSize(3f))
        assertTrue(sheet.svg(3f).contains("d=\"M3 3h6v6h-6zM12 3h3v6h-3z\""))
        assertEquals(0.0, sheet.order(0, 0), 0.0)
        assertEquals(1.0, sheet.order(0, 3), 0.0)
        val clear = CodeSheet(sheet.modules, 2f, CodeSheet.Margin(1f), ink = black, paper = null, sweep = CodeSheet.Sweep.Columns, anchor = CodeSheet.Anchor.Leading)
        assertFalse(clear.svg(3f).contains("<rect"))
        assertEquals("1.5", CodeSheet.number(1.5f))
        assertEquals("2", CodeSheet.number(2f))
        assertEquals("0.13", CodeSheet.number(0.126f))
        val placed = placeSheet(sheet, SheetRect(0f, 0f, 300f, 300f))
        assertNotNull(placed)
        assertEquals(placed!!.pitch, kotlin.math.floor(placed.pitch), 0f)
    }

    @Test
    fun linearText() {
        val code = BarcodeEngine.modules("5901234123457", BarcodeKind.Ean13) as BarcodeModules.Code
        val sheet = BarcodeEngine.sheet(code.rows, 1, code.text, BarcodeKind.Ean13, 11, black, white, numbers = true)
        assertEquals(13, sheet.marks.size)
        assertEquals(BarcodeEngine.DIGIT_BAND, sheet.footer)
        assertTrue(sheet.svg(2f).contains(">5</text>"))
    }
}
