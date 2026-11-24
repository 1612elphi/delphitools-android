package com.delphi.delphitools.tools.devtools

import com.delphi.delphitools.colour.Rgb
import com.google.zxing.WriterException
import com.google.zxing.aztec.encoder.Encoder as AztecEncoder
import com.google.zxing.oned.Code128Writer
import com.google.zxing.pdf417.encoder.PDF417
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min

/** dt-ios EAN13: the 95 modules of an EAN-13 code. */
object Ean13 {
    private val lPatterns = listOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
    private val gPatterns = listOf("0100111", "0110011", "0011011", "0100001", "0011101", "0000101", "0010001", "0000101", "0001001", "0010111")
    private val rPatterns = listOf("1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100")
    private val parity = listOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL")

    /** Check digit for the first 12 digits, weights 1 and 3. */
    fun checkDigit(first12: List<Int>): Int {
        val sum = (0 until 12).sumOf { first12[it] * if (it % 2 == 0) 1 else 3 }
        return (10 - sum % 10) % 10
    }

    /** The 95 modules, true for a bar; null unless [digits] is 13 digits with a valid check digit. */
    fun bars(digits: String): BooleanArray? {
        if (digits.length != 13 || !digits.all { it in '0'..'9' }) return null
        val d = digits.map { it - '0' }
        if (d[12] != checkDigit(d)) return null
        val bits = StringBuilder("101")
        for (i in 0 until 6) bits.append(if (parity[d[0]][i] == 'L') lPatterns[d[i + 1]] else gPatterns[d[i + 1]])
        bits.append("01010")
        for (i in 0 until 6) bits.append(rPatterns[d[i + 7]])
        bits.append("101")
        return BooleanArray(bits.length) { bits[it] == '1' }
    }

    /** Modules of the start, centre and end guards, which run longer than the digit bars. */
    val guards = setOf(0, 1, 2, 45, 46, 47, 48, 49, 92, 93, 94)
}

enum class BarcodeKind(val label: String, val description: String, val placeholder: String, val is1D: Boolean, val fileStem: String) {
    Code128("Code 128", "Alphanumeric, variable length. Used in shipping and logistics.", "Hello World", true, "code-128"),
    Pdf417("PDF417", "High-capacity 2D barcode. Used in IDs, boarding passes, and labels.", "Enter text or data", false, "pdf417"),
    Aztec("Aztec", "Compact 2D barcode. Used in transport tickets and boarding passes.", "Enter text or URL", false, "aztec"),
    Ean13("EAN-13", "13-digit numeric. Standard retail product barcode.", "5901234123457", true, "ean-13");

    /** Symbology quiet zones, the default padding. */
    val defaultPadding
        get() = when (this) {
            Code128 -> 10
            Ean13 -> 11
            Pdf417, Aztec -> 2
        }

    val quietZone
        get() = when (this) {
            Code128 -> 10f
            Ean13 -> 7f
            Pdf417 -> 2f
            Aztec -> 0f
        }
}

sealed interface BarcodeModules {
    data object Empty : BarcodeModules

    /** Rows of modules, the height of one row in modules, and the human-readable text. */
    class Code(val rows: List<BooleanArray>, val rowHeight: Int, val text: String) : BarcodeModules
    data class Failure(val message: String) : BarcodeModules
}

/** dt-ios BarcodeGeneratorViewModel.modules, with ZXing where iOS uses Core Image filters. */
object BarcodeEngine {
    /** Bar height in modules, and how far the EAN guards drop below it. */
    const val BAR_HEIGHT = 60f
    const val GUARD_DROP = 5f

    /** In modules. */
    const val DIGIT_BAND = 11f

    // ponytail: Core Image's PDF417 row height is read from its bitmap on iOS; 3 modules is the spec's least
    private const val PDF417_ROW_HEIGHT = 3

    /** Core Image's default error correction for each 2D code. */
    private const val PDF417_LEVEL = 2
    private const val AZTEC_PERCENT = 23

    /** The code for [content] as modules. EAN-13 takes 12 digits and adds the check digit. */
    fun modules(content: String, kind: BarcodeKind): BarcodeModules {
        if (content.isEmpty()) return BarcodeModules.Empty
        if (kind == BarcodeKind.Ean13) {
            var digits = content.trim()
            if (digits.length !in 12..13 || !digits.all { it in '0'..'9' }) {
                return BarcodeModules.Failure("EAN-13 requires exactly 13 digits with a valid check digit.")
            }
            if (digits.length == 12) digits += Ean13.checkDigit(digits.map { it - '0' })
            val bars = Ean13.bars(digits) ?: return BarcodeModules.Failure("Invalid EAN-13 code. Check the check digit.")
            return BarcodeModules.Code(listOf(bars), 1, digits)
        }
        // core image takes latin-1 data
        if (content.any { it.code > 0xFF }) return BarcodeModules.Failure("Invalid characters for this barcode type.")
        val rows = try {
            when (kind) {
                BarcodeKind.Code128 -> listOf(Code128Writer().encode(content))
                BarcodeKind.Pdf417 -> {
                    val encoder = PDF417().apply { setEncoding(StandardCharsets.ISO_8859_1) }
                    encoder.generateBarcodeLogic(content, PDF417_LEVEL)
                    encoder.barcodeMatrix.matrix.map { row -> BooleanArray(row.size) { row[it].toInt() == 1 } }
                }
                BarcodeKind.Aztec -> {
                    val matrix = AztecEncoder.encode(content.toByteArray(StandardCharsets.ISO_8859_1), AZTEC_PERCENT, AztecEncoder.DEFAULT_AZTEC_LAYERS).matrix
                    List(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix[x, y] } }
                }
                BarcodeKind.Ean13 -> error("handled above")
            }
        } catch (_: WriterException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } ?: return BarcodeModules.Failure("Could not generate barcode. Check the input format.")
        return BarcodeModules.Code(rows, if (kind == BarcodeKind.Pdf417) PDF417_ROW_HEIGHT else 1, content)
    }

    /** The code laid out on its sheet: margin, the long EAN guards and the human-readable line under a linear code. */
    fun sheet(rows: List<BooleanArray>, rowHeight: Int, text: String, kind: BarcodeKind, padding: Int, ink: Rgb, paper: Rgb?, numbers: Boolean): CodeSheet {
        val pad = padding.toFloat()
        val showsText = kind.is1D && numbers
        val sweep = if (kind == BarcodeKind.Aztec) CodeSheet.Sweep.Rings else CodeSheet.Sweep.Columns
        val anchor = if (kind == BarcodeKind.Aztec) CodeSheet.Anchor.Centre else CodeSheet.Anchor.Leading
        val height = if (kind.is1D) BAR_HEIGHT else rowHeight.toFloat()
        if (!showsText) {
            return CodeSheet(rows, height, CodeSheet.Margin(pad), ink = ink, paper = paper, quietZone = kind.quietZone, sweep = sweep, anchor = anchor)
        }
        val size = 8.5f
        if (kind == BarcodeKind.Ean13) {
            val marks = mutableListOf<CodeSheet.Mark>(CodeSheet.Mark.Text(text.take(1), SheetRect(-8f, BAR_HEIGHT, 7f, DIGIT_BAND), size, true, ink))
            text.drop(1).forEachIndexed { index, digit ->
                val x = 3 + index * 7f + if (index >= 6) 5 else 0
                marks += CodeSheet.Mark.Text(digit.toString(), SheetRect(x, BAR_HEIGHT, 7f, DIGIT_BAND), size, true, ink)
            }
            // room for leading digit
            val left = max(pad, 9f)
            return CodeSheet(
                rows, height, CodeSheet.Margin(left, pad, pad, pad), DIGIT_BAND, ink, paper, kind.quietZone, sweep, anchor,
                shape = { rect, _, column ->
                    if (column in Ean13.guards) SheetPath().rect(SheetRect(rect.x, rect.y, rect.width, rect.height * (BAR_HEIGHT + GUARD_DROP) / BAR_HEIGHT))
                    else SheetPath().rect(rect)
                },
                marks = marks,
                marksKey = text + CodeSheet.hex(ink) + left,
            )
        }
        val columns = rows.firstOrNull()?.size?.toFloat() ?: 0f
        // text fits bar width
        val fitted = min(size, columns / max(1, text.length) / 0.62f)
        return CodeSheet(
            rows, height, CodeSheet.Margin(pad), DIGIT_BAND, ink, paper, kind.quietZone, sweep, anchor,
            marks = listOf(CodeSheet.Mark.Text(text, SheetRect(0f, BAR_HEIGHT, columns, DIGIT_BAND), fitted, true, ink)),
            marksKey = text + CodeSheet.hex(ink) + pad,
        )
    }
}
