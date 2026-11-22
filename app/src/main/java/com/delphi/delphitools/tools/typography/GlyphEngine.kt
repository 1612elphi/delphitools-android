package com.delphi.delphitools.tools.typography

/** dt-ios UnicodeBlockList: the Unicode 18.0 blocks from Blocks.txt. */
data class UnicodeBlock(val name: String, val range: IntRange)

/** dt-ios GlyphChart.Section: a run of code points, and a bit per code point that has something to show. */
class GlyphSection(val title: String?, val range: IntRange) {
    val masks: IntArray = IntArray(range.last / 16 - range.first / 16 + 1) { row ->
        var mask = 0
        for (bit in 0 until 16) {
            val value = (range.first / 16 + row) * 16 + bit
            if (value in range && GlyphEngine.isVisible(value)) mask = mask or (1 shl bit)
        }
        mask
    }

    val isEmpty get() = masks.all { it == 0 }

    fun shows(codePoint: Int): Boolean {
        if (codePoint !in range) return false
        return masks[codePoint / 16 - range.first / 16] and (1 shl (codePoint % 16)) != 0
    }
}

/** dt-ios GlyphChart.Geometry, in dp: a heading per section, then one row per 16 or 8 code points with something to show. */
class GlyphGeometry(sections: List<GlyphSection>, val width: Float) {
    sealed interface Item {
        data class Heading(val section: Int) : Item

        data class Row(val start: Int, val section: Int) : Item
    }

    val columns: Int
    val cell: Float
    val items: List<Item>

    /** Each item's top, then the total height. */
    val tops: FloatArray

    init {
        val room = maxOf(0f, width - ROW_HEADER)
        columns = if (room / 16 >= MIN_CELL) 16 else 8
        cell = room / columns
        val items = mutableListOf<Item>()
        val tops = mutableListOf<Float>()
        var y = 0f
        sections.forEachIndexed { index, section ->
            if (section.isEmpty) return@forEachIndexed
            items += Item.Heading(index)
            tops += y
            y += if (section.title == null) DIGITS else HEADING
            val base = section.range.first / 16 * 16
            section.masks.forEachIndexed { row, mask ->
                for (half in 0 until 16 / columns) {
                    val bits = if (columns == 16) mask else (mask shr (half * 8)) and 0xFF
                    if (bits == 0) continue
                    items += Item.Row(base + row * 16 + half * columns, index)
                    tops += y
                    y += cell
                }
            }
        }
        tops += y
        this.items = items
        this.tops = tops.toFloatArray()
    }

    val height get() = tops.last()

    fun height(index: Int) = tops[index + 1] - tops[index]

    fun item(y: Float): Int? {
        if (items.isEmpty() || y < 0 || y >= height) return null
        var low = 0
        var high = items.size - 1
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (tops[middle] <= y) low = middle else high = middle - 1
        }
        return low
    }

    fun row(codePoint: Int): Int? {
        var low = 0
        var high = items.size - 1
        var found: Int? = null
        while (low <= high) {
            val middle = (low + high) / 2
            var probe = middle
            while (probe <= high && items[probe] is Item.Heading) probe++
            val row = items.getOrNull(probe) as? Item.Row
            if (probe > high || row == null) {
                high = middle - 1
                continue
            }
            if (row.start <= codePoint) {
                found = probe
                low = probe + 1
            } else {
                high = middle - 1
            }
        }
        val row = found?.let { items[it] as Item.Row } ?: return null
        return found.takeIf { codePoint < row.start + columns }
    }

    fun heading(section: Int) = items.indexOf(Item.Heading(section)).takeIf { it >= 0 }

    companion object {
        const val DIGITS = 24f
        const val HEADING = 58f
        const val ROW_HEADER = 52f
        const val MIN_CELL = 34f
    }
}

object GlyphEngine {
    /** g: descender and bowl */
    const val FIRST_GLYPH = 0x67

    sealed interface Find {
        data class CodePoint(val value: Int) : Find

        data class Block(val index: Int) : Find
    }

    private val blockRow = Regex("""\[\s*(\d+)\s*,\s*(\d+)\s*,\s*"([^"]*)"\s*]""")

    /** Rows of start, end and name; the names carry no escapes. */
    fun parseBlocks(json: String): List<UnicodeBlock> = blockRow.findAll(json).map { match ->
        val (start, end, name) = match.destructured
        UnicodeBlock(name, start.toInt()..end.toInt())
    }.toList()

    /** Blocks with something to show, and their chart sections. */
    fun chart(blocks: List<UnicodeBlock>): Pair<List<UnicodeBlock>, List<GlyphSection>> =
        blocks.map { it to GlyphSection(it.name, it.range) }.filter { !it.second.isEmpty }.unzip()

    fun index(codePoint: Int, blocks: List<UnicodeBlock>) = blocks.indexOfFirst { codePoint in it.range }.takeIf { it >= 0 }

    private fun isScalar(value: Long) = value in 0..0x10FFFF && value !in 0xD800..0xDFFF

    fun find(query: String, blocks: List<UnicodeBlock>): Find? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        for (prefix in listOf("u+", "0x")) {
            if (!lower.startsWith(prefix)) continue
            return lower.drop(2).toLongOrNull(16)?.takeIf(::isScalar)?.let { Find.CodePoint(it.toInt()) }
        }
        if (trimmed.codePointCount(0, trimmed.length) == 1) return Find.CodePoint(trimmed.codePointAt(0))
        if (trimmed.length in 4..6) trimmed.toLongOrNull(16)?.takeIf(::isScalar)?.let { return Find.CodePoint(it.toInt()) }
        return blocks.indexOfFirst { lower in it.name.lowercase() }.takeIf { it >= 0 }?.let { Find.Block(it) }
    }

    fun isVisible(codePoint: Int) = when (Character.getType(codePoint).toByte()) {
        Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.PRIVATE_USE, Character.UNASSIGNED,
        Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> false
        else -> true
    }

    private fun isMark(codePoint: Int) = when (Character.getType(codePoint).toByte()) {
        Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
        else -> false
    }

    /** Marks on a dotted-circle base. */
    fun shown(codePoint: Int): String = (if (isMark(codePoint)) "◌" else "") + String(Character.toChars(codePoint))

    fun label(codePoint: Int) = "U+%04X".format(codePoint)

    fun name(codePoint: Int): String = Character.getName(codePoint) ?: label(codePoint)

    // untranslated, like names; Swift's Unicode.GeneralCategory case names
    private val categories = mapOf(
        Character.UPPERCASE_LETTER to "Uppercase Letter",
        Character.LOWERCASE_LETTER to "Lowercase Letter",
        Character.TITLECASE_LETTER to "Titlecase Letter",
        Character.MODIFIER_LETTER to "Modifier Letter",
        Character.OTHER_LETTER to "Other Letter",
        Character.NON_SPACING_MARK to "Nonspacing Mark",
        Character.COMBINING_SPACING_MARK to "Spacing Mark",
        Character.ENCLOSING_MARK to "Enclosing Mark",
        Character.DECIMAL_DIGIT_NUMBER to "Decimal Number",
        Character.LETTER_NUMBER to "Letter Number",
        Character.OTHER_NUMBER to "Other Number",
        Character.CONNECTOR_PUNCTUATION to "Connector Punctuation",
        Character.DASH_PUNCTUATION to "Dash Punctuation",
        Character.START_PUNCTUATION to "Open Punctuation",
        Character.END_PUNCTUATION to "Close Punctuation",
        Character.INITIAL_QUOTE_PUNCTUATION to "Initial Punctuation",
        Character.FINAL_QUOTE_PUNCTUATION to "Final Punctuation",
        Character.OTHER_PUNCTUATION to "Other Punctuation",
        Character.MATH_SYMBOL to "Math Symbol",
        Character.CURRENCY_SYMBOL to "Currency Symbol",
        Character.MODIFIER_SYMBOL to "Modifier Symbol",
        Character.OTHER_SYMBOL to "Other Symbol",
        Character.SPACE_SEPARATOR to "Space Separator",
        Character.LINE_SEPARATOR to "Line Separator",
        Character.PARAGRAPH_SEPARATOR to "Paragraph Separator",
        Character.CONTROL to "Control",
        Character.FORMAT to "Format",
        Character.SURROGATE to "Surrogate",
        Character.PRIVATE_USE to "Private Use",
        Character.UNASSIGNED to "Unassigned",
    )

    fun category(codePoint: Int): String = categories[Character.getType(codePoint).toByte()] ?: "Unassigned"

    /** Astral needs the braced form. */
    fun js(codePoint: Int) = if (codePoint <= 0xFFFF) "\\u%04X".format(codePoint) else "\\u{%X}".format(codePoint)

    fun valueLines(codePoint: Int): List<Pair<String, String>> {
        val character = String(Character.toChars(codePoint))
        return listOf(
            "Character" to character,
            "Decimal" to "$codePoint",
            "UTF-8" to character.toByteArray(Charsets.UTF_8).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) },
            "HTML" to "&#$codePoint;",
            "CSS" to "\\%04X".format(codePoint),
            "JS" to js(codePoint),
        )
    }

    fun jumpDetail(block: UnicodeBlock) = "U+%04X–%04X".format(block.range.first, block.range.last)

    /**
     * A font file name as a family: "NotoSansShavian-Regular.ttf" reads "Noto Sans Shavian".
     * ponytail: the file name, not the name table; read the family from the file's name table if a font misreads
     */
    fun familyFromFile(fileName: String): String {
        val stem = fileName.substringBeforeLast('.').substringBefore('-')
        return stem.replace(Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Za-z])(?=[0-9])"), " ")
    }
}
