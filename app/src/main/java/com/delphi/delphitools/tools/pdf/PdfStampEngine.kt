package com.delphi.delphitools.tools.pdf

import java.nio.charset.Charset
import java.util.UUID
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** dt-ios PDFStampEngine: page numbers and a text stamp, placed in display space (bottom-left origin, points). */
object PdfStampEngine {
    enum class Anchor(val label: String) {
        TopLeft("Top left"), TopCenter("Top centre"), TopRight("Top right"),
        MiddleLeft("Middle left"), MiddleCenter("Centre"), MiddleRight("Middle right"),
        BottomLeft("Bottom left"), BottomCenter("Bottom centre"), BottomRight("Bottom right");

        val row get() = ordinal / 3
        val column get() = ordinal % 3
    }

    enum class NumeralStyle(val label: String) {
        Arabic("1, 2, 3"), RomanLower("i, ii, iii"), RomanUpper("I, II, III"), AlphaLower("a, b, c"), AlphaUpper("A, B, C")
    }

    /** Standard 14 fonts stand in for iOS's Helvetica, TimesNewRomanPSMT and Courier, so nothing is embedded. */
    enum class FontFamily(val baseFont: String, internal val widths: IntArray) {
        Sans("Helvetica", HELVETICA), Serif("Times-Roman", TIMES), Mono("Courier", IntArray(224) { 600 })
    }

    enum class StampMode { Label, Watermark }

    data class NumberSection(val fromPage: Int = 1, val style: NumeralStyle = NumeralStyle.Arabic, val startAt: Int = 1, val id: UUID = UUID.randomUUID())

    data class Options(
        val numbersOn: Boolean = true,
        val template: String = "{n} / {N}",
        val numberAnchor: Anchor = Anchor.BottomCenter,
        val font: FontFamily = FontFamily.Sans,
        val fontSize: Double = 11.0,
        val margin: Double = 24.0,
        val stampOn: Boolean = false,
        val stampText: String = "",
        val stampAnchor: Anchor = Anchor.TopRight,
        val stampMode: StampMode = StampMode.Label,
        val watermarkOpacity: Double = 0.15,
        val sections: List<NumberSection> = listOf(NumberSection()),
    )

    /** `y` is the baseline. */
    fun placeText(page: PdfSize, textWidth: Double, fontSize: Double, anchor: Anchor, margin: Double): Pair<Double, Double> {
        val x = when (anchor.column) {
            0 -> margin
            2 -> page.width - textWidth - margin
            else -> (page.width - textWidth) / 2
        }
        val y = when (anchor.row) {
            0 -> page.height - margin - fontSize
            2 -> margin
            else -> (page.height - fontSize) / 2
        }
        return x to y
    }

    fun expandNumber(template: String, n: String, total: String) = template.replace("{n}", n).replace("{N}", total)

    // helvetica caps ~0.55em
    fun watermarkSize(page: PdfSize, textLength: Int): Double {
        val raw = 0.8 * hypot(page.width, page.height) / (0.55 * maxOf(1, textLength))
        return minOf(raw, minOf(page.width, page.height) * 0.9)
    }

    fun formatNumeral(value: Int, style: NumeralStyle): String = when (style) {
        NumeralStyle.Arabic -> value.toString()
        NumeralStyle.RomanLower -> roman(value).lowercase()
        NumeralStyle.RomanUpper -> roman(value)
        NumeralStyle.AlphaLower -> alpha(value)
        NumeralStyle.AlphaUpper -> alpha(value).uppercase()
    }

    /** `fromPage` is clamped into the document, so a section past the end numbers the last page. */
    fun resolvePageNumbers(sections: List<NumberSection>, pageCount: Int): List<String?> {
        if (pageCount <= 0) return emptyList()
        val ordered = sections.map { it.fromPage.coerceIn(1, pageCount) to it }.sortedBy { it.first }
        return (1..pageCount).map { page ->
            val active = ordered.lastOrNull { it.first <= page } ?: return@map null
            // typed startAt may overflow
            formatNumeral(active.second.startAt + (page - active.first), active.second.style)
        }
    }

    /** The section that numbers a 1-based page; null before the first one starts. */
    fun resolveSection(sections: List<NumberSection>, page: Int): NumberSection? =
        sections.filter { it.fromPage <= page }.maxByOrNull { it.fromPage }

    private val romanTable = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
        50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
    )

    private fun roman(value: Int): String {
        if (value <= 0 || value >= 4000) return value.toString()
        var n = value
        val out = StringBuilder()
        for ((amount, symbol) in romanTable) {
            while (n >= amount) {
                out.append(symbol)
                n -= amount
            }
        }
        return out.toString()
    }

    private fun alpha(value: Int): String {
        if (value <= 0) return value.toString()
        var n = value
        var out = ""
        while (n > 0) {
            out = ('a' + (n - 1) % 26) + out
            n = (n - 1) / 26
        }
        return out
    }


    private val winAnsi: Charset = Charset.forName("windows-1252")

    /**
     * [text] in WinAnsiEncoding, the standard 14 fonts' encoding here.
     * ponytail: characters outside Windows-1252 print as "?"; embed a font subset for other scripts
     */
    fun encode(text: String): ByteArray = text.toByteArray(winAnsi)

    fun width(text: String, font: FontFamily, size: Double): Double =
        encode(text).sumOf { b -> font.widths.getOrElse((b.toInt() and 0xff) - 32) { 0 } } * size / 1000

    private const val FONT = "DTStampF"
    private const val ALPHA = "DTStampA"

    private fun r(value: Double) = PdfSerialiser.real(value)

    private fun show(text: String, x: Double, y: Double, size: Double, grey: Double) =
        "BT /$FONT ${r(size)} Tf ${r(grey)} g ${r(x)} ${r(y)} Td ${String(PdfSerialiser.bytes(PdfString(encode(text))), Charsets.ISO_8859_1)} Tj ET\n"

    /** The stamp operators in display space for one page; empty when nothing is drawn. Second: whether it uses [ALPHA]. */
    fun operators(label: String?, total: Int, size: PdfSize, options: Options): Pair<String, Boolean> {
        val out = StringBuilder()
        val fontSize = maxOf(4.0, options.fontSize)
        if (options.numbersOn && label != null) {
            val text = expandNumber(options.template, label, total.toString())
            val (x, y) = placeText(size, width(text, options.font, fontSize), fontSize, options.numberAnchor, options.margin)
            out.append(show(text, x, y, fontSize, 0.0))
        }
        val stamp = options.stampText
        if (!options.stampOn || stamp.isBlank()) return out.toString() to false
        if (options.stampMode == StampMode.Watermark) {
            val wmSize = watermarkSize(size, stamp.length)
            val w = width(stamp, options.font, wmSize)
            val half = wmSize * 0.35 / 2
            val k = cos(Math.PI / 4)
            val tx = size.width / 2 - (w / 2) * k + half * k
            val ty = size.height / 2 - (w / 2) * k - half * k
            val s = sin(Math.PI / 4)
            out.append("q /$ALPHA gs ${r(k)} ${r(s)} ${r(-s)} ${r(k)} ${r(tx)} ${r(ty)} cm\n")
            out.append(show(stamp, 0.0, 0.0, wmSize, 0.0))
            out.append("Q\n")
            return out.toString() to true
        }
        val (x, y) = placeText(size, width(stamp, options.font, fontSize), fontSize, options.stampAnchor, options.margin)
        out.append(show(stamp, x, y, fontSize, 0.4))
        return out.toString() to false
    }

    /** Display space (the crop box as viewed, bottom-left origin) to the page's user space. */
    fun displayMatrix(box: PdfRect, rotation: Int): List<Double> = when (rotation) {
        90 -> listOf(0.0, 1.0, -1.0, 0.0, box.maxX, box.y)
        180 -> listOf(-1.0, 0.0, 0.0, -1.0, box.maxX, box.maxY)
        270 -> listOf(0.0, -1.0, 1.0, 0.0, box.x, box.maxY)
        else -> listOf(1.0, 0.0, 0.0, 1.0, box.x, box.y)
    }

    /**
     * [file] with the stamps of [pages] (all when null) added as an incremental update: each page gets a `q` stream
     * before its content and the stamp stream after it, and its resources gain the font and alpha state.
     */
    fun stamp(file: PdfFile, options: Options, pages: Collection<Int>? = null, step: (Int) -> Unit = {}): ByteArray {
        val labels = resolvePageNumbers(options.sections, file.pages.size)
        val changes = LinkedHashMap<PdfRef, Any?>()
        var next = file.size
        fun add(value: Any?) = PdfRef(next++).also { changes[it] = value }
        val font = add(
            linkedMapOf("Type" to PdfName("Font"), "Subtype" to PdfName("Type1"), "BaseFont" to PdfName(options.font.baseFont), "Encoding" to PdfName("WinAnsiEncoding")),
        )
        val alpha = add(linkedMapOf("Type" to PdfName("ExtGState"), "ca" to options.watermarkOpacity, "CA" to options.watermarkOpacity))
        val save = add(PdfStream(linkedMapOf(), "q\n".toByteArray()))
        var stamped = 0
        file.pages.forEachIndexed { index, page ->
            if (pages != null && index !in pages) return@forEachIndexed
            step(index)
            val box = PdfRect.of(file.cropBox(page))
            val rotation = file.rotation(page)
            val (ops, usesAlpha) = operators(labels[index], file.pages.size, PdfCropEngine.displaySize(box, rotation), options)
            if (ops.isEmpty()) return@forEachIndexed
            stamped++
            val old = when (val contents = page.dict["Contents"]) {
                null -> emptyList()
                is PdfRef -> if (file.resolve(contents) is List<*>) file.resolve(contents) as List<*> else listOf(contents)
                is List<*> -> contents
                else -> listOf(contents)
            }
            val restores = "Q\n".repeat(openSaves(old.map { file.resolve(it) }) + 1)
            val matrix = displayMatrix(box, rotation).joinToString(" ") { r(it) }
            val content = PdfFilters.deflate("\n${restores}q $matrix cm\n${ops}Q\n".toByteArray(Charsets.ISO_8859_1))
            val stream = add(PdfStream(linkedMapOf("Filter" to PdfName("FlateDecode")), content))
            val resources = LinkedHashMap(file.dict(page.attribute("Resources")) ?: emptyMap())
            resources["Font"] = LinkedHashMap(file.dict(resources["Font"]) ?: emptyMap()).apply { put(FONT, font) }
            if (usesAlpha) resources["ExtGState"] = LinkedHashMap(file.dict(resources["ExtGState"]) ?: emptyMap()).apply { put(ALPHA, alpha) }
            val dict = LinkedHashMap(page.dict)
            dict["Resources"] = resources
            dict["Contents"] = listOf(save) + old + stream
            changes[page.ref] = dict
        }
        if (stamped == 0) return file.bytes
        return file.update(changes)
    }

    /** `q` left unmatched by the content [streams] in order; a stream that cannot be decoded counts none. */
    fun openSaves(streams: List<Any?>): Int {
        var depth = 0
        for (stream in streams) {
            val data = (stream as? PdfStream)?.let { runCatching { PdfFilters.decode(it) }.getOrNull() } ?: continue
            val lexer = PdfLexer(data, 0)
            while (true) {
                lexer.skipWhite()
                if (lexer.pos >= data.size) break
                val at = lexer.pos
                if ("()<>[]{}/%".indexOf((data[at].toInt() and 0xff).toChar()) < 0) {
                    when (lexer.token()) {
                        "q" -> depth++
                        "Q" -> if (depth > 0) depth--
                        "ID" -> lexer.pos = inlineImageEnd(data, lexer.pos)
                    }
                } else {
                    runCatching { lexer.value() }
                    if (lexer.pos == at) lexer.pos++
                }
            }
        }
        return depth
    }

    private fun inlineImageEnd(data: ByteArray, from: Int): Int {
        var at = from
        while (true) {
            val found = indexOf(data, "EI".toByteArray(), at)
            if (found < 0) return data.size
            val before = if (found > 0) data[found - 1].toInt() else 32
            val after = if (found + 2 < data.size) data[found + 2].toInt() else 32
            if (before.isContentWhite() && after.isContentWhite()) return found + 2
            at = found + 1
        }
    }

    private fun Int.isContentWhite() = this == 0 || this == 9 || this == 10 || this == 12 || this == 13 || this == 32
}

// urw nimbus advance widths
private val HELVETICA = intArrayOf(
    278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556, 556, 556, 556, 556, 556,
    556, 556, 278, 278, 584, 584, 584, 556, 1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
    667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556, 333, 556, 556, 500, 556, 556, 278, 556,
    556, 222, 222, 500, 222, 833, 556, 556, 556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584, 350,
    556, 350, 222, 556, 333, 1000, 556, 556, 333, 1000, 667, 333, 1000, 350, 611, 350, 350, 222, 222, 333, 333, 350, 556, 1000,
    333, 1000, 500, 333, 944, 350, 500, 667, 278, 333, 556, 556, 556, 556, 260, 556, 333, 737, 370, 556, 584, 333, 737, 333,
    400, 584, 333, 333, 333, 556, 537, 278, 333, 333, 365, 556, 834, 834, 834, 611, 667, 667, 667, 667, 667, 667, 1000, 722,
    667, 667, 667, 667, 278, 278, 278, 278, 722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611,
    556, 556, 556, 556, 556, 556, 889, 500, 556, 556, 556, 556, 278, 278, 278, 278, 556, 556, 556, 556, 556, 556, 556, 584,
    611, 556, 556, 556, 556, 500, 556, 500,
)

private val TIMES = intArrayOf(
    250, 333, 408, 500, 500, 833, 778, 180, 333, 333, 500, 564, 250, 333, 250, 278, 500, 500, 500, 500, 500, 500, 500, 500,
    500, 500, 278, 278, 564, 564, 564, 444, 921, 722, 667, 667, 722, 611, 556, 722, 722, 333, 389, 722, 611, 889, 722, 722,
    556, 722, 667, 556, 611, 722, 722, 944, 722, 722, 611, 333, 278, 333, 469, 500, 333, 444, 500, 444, 500, 444, 333, 500,
    500, 278, 278, 500, 278, 778, 500, 500, 500, 500, 333, 389, 278, 500, 500, 722, 500, 500, 444, 480, 200, 480, 541, 350,
    500, 350, 333, 500, 444, 1000, 500, 500, 333, 1000, 556, 333, 889, 350, 611, 350, 350, 333, 333, 444, 444, 350, 500, 1000,
    333, 980, 389, 333, 722, 350, 444, 722, 250, 333, 500, 500, 500, 500, 200, 500, 333, 760, 276, 500, 564, 333, 760, 333,
    400, 564, 300, 300, 333, 500, 453, 250, 333, 300, 310, 500, 750, 750, 750, 444, 722, 722, 722, 722, 722, 722, 889, 667,
    611, 611, 611, 611, 333, 333, 333, 333, 722, 722, 722, 722, 722, 722, 722, 564, 722, 722, 722, 722, 722, 722, 556, 500,
    444, 444, 444, 444, 444, 444, 667, 444, 444, 444, 444, 444, 278, 278, 278, 278, 500, 500, 500, 500, 500, 500, 500, 564,
    500, 500, 500, 500, 500, 500, 500, 500,
)
