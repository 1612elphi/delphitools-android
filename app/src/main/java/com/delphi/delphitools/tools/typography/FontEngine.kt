package com.delphi.delphitools.tools.typography

import java.util.zip.DataFormatException
import java.util.zip.Inflater
import kotlin.math.roundToInt

/** What dt-ios FontExplorerViewModel.FontInfo reads through CoreText, here read from the sfnt tables; metrics in font units. */
data class FontInfo(
    val fileName: String,
    val familyName: String,
    val styleName: String,
    val fullName: String,
    val postScriptName: String,
    val unitsPerEm: Int,
    val ascent: Int,
    /** Positive below the baseline. */
    val descent: Int,
    val lineGap: Int,
    /** Null when OS/2 is older than version 2 or missing. */
    val capHeight: Int?,
    val xHeight: Int?,
    val italicAngle: Double,
    val boxWidth: Int,
    val boxHeight: Int,
    val glyphCount: Int,
    val tables: List<String>,
    val coverage: FontCoverage,
)

/** The code points a font's cmap maps to a glyph other than 0, as sorted inclusive ranges. */
class FontCoverage(private val starts: IntArray, private val ends: IntArray) {
    fun covers(codePoint: Int): Boolean {
        var low = 0
        var high = starts.size - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            when {
                codePoint < starts[middle] -> high = middle - 1
                codePoint > ends[middle] -> low = middle + 1
                else -> return true
            }
        }
        return false
    }

    companion object {
        fun of(ranges: List<IntRange>): FontCoverage {
            val sorted = ranges.filter { !it.isEmpty() }.sortedBy { it.first }
            val starts = ArrayList<Int>()
            val ends = ArrayList<Int>()
            for (range in sorted) {
                if (ends.isNotEmpty() && range.first <= ends.last() + 1) {
                    ends[ends.size - 1] = maxOf(ends.last(), range.last)
                } else {
                    starts += range.first
                    ends += range.last
                }
            }
            return FontCoverage(starts.toIntArray(), ends.toIntArray())
        }
    }
}

class FontFormatException(message: String) : Exception(message)

object FontEngine {
    private const val WOFF1 = 0x774F4646
    private const val WOFF2 = 0x774F4632
    private const val TRUETYPE = 0x00010000
    private const val OPENTYPE = 0x4F54544F
    private const val APPLE_TRUE = 0x74727565
    private const val COLLECTION = 0x74746366

    // untrusted header sizes
    private const val MAX_SFNT = 256 shl 20

    private fun ByteArray.u8(at: Int) = this[at].toInt() and 0xFF
    private fun ByteArray.u16(at: Int) = (u8(at) shl 8) or u8(at + 1)
    private fun ByteArray.i16(at: Int) = u16(at).toShort().toInt()
    private fun ByteArray.u32(at: Int) = (u16(at) shl 16) or u16(at + 2)
    private fun ByteArray.tag(at: Int) = String(CharArray(4) { (u8(at + it)).toChar() })

    /**
     * An sfnt from TrueType, OpenType, a collection (passed through) or WOFF 1.0.
     * ponytail: WOFF2 needs a Brotli decoder, which Android does not expose; it fails here as an invalid font
     */
    fun decode(data: ByteArray): ByteArray {
        if (data.size < 4) throw FontFormatException("signature")
        return when (data.u32(0)) {
            TRUETYPE, OPENTYPE, APPLE_TRUE, COLLECTION -> data
            WOFF1 -> decodeWoff(data)
            else -> throw FontFormatException("signature")
        }
    }

    private fun decodeWoff(data: ByteArray): ByteArray {
        if (data.size < 44) throw FontFormatException("woff header")
        val flavour = data.u32(4)
        val count = data.u16(12)
        if (count == 0 || data.size < 44 + count * 20) throw FontFormatException("woff directory")
        class Entry(val tag: Int, val offset: Int, val compressed: Int, val original: Int, val checksum: Int)
        val entries = (0 until count).map {
            val base = 44 + it * 20
            Entry(data.u32(base), data.u32(base + 4), data.u32(base + 8), data.u32(base + 12), data.u32(base + 16))
        }
        if (entries.sumOf { it.original.toLong() and 0xFFFFFFFFL } > MAX_SFNT) throw FontFormatException("woff size")
        val tables = entries.map { entry ->
            val end = entry.offset.toLong() + entry.compressed
            if (entry.offset < 0 || entry.compressed < 0 || end > data.size) throw FontFormatException("woff table")
            if (entry.compressed == entry.original) {
                data.copyOfRange(entry.offset, entry.offset + entry.compressed)
            } else {
                inflate(data, entry.offset, entry.compressed, entry.original)
            }
        }
        return buildSfnt(flavour, entries.map { it.tag to it.checksum }, tables)
    }

    private fun inflate(data: ByteArray, offset: Int, length: Int, expected: Int): ByteArray {
        if (expected < 0 || expected > MAX_SFNT) throw FontFormatException("woff size")
        val inflater = Inflater()
        try {
            inflater.setInput(data, offset, length)
            val out = ByteArray(expected)
            var filled = 0
            while (filled < expected && !inflater.finished()) {
                val n = inflater.inflate(out, filled, expected - filled)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                filled += n
            }
            if (filled != expected) throw FontFormatException("woff inflate")
            return out
        } catch (_: DataFormatException) {
            throw FontFormatException("woff inflate")
        } finally {
            inflater.end()
        }
    }

    private fun buildSfnt(flavour: Int, tables: List<Pair<Int, Int>>, data: List<ByteArray>): ByteArray {
        val count = tables.size
        if (count == 0 || count >= 4096) throw FontFormatException("table count")
        val header = 12 + count * 16
        val offsets = IntArray(count)
        var size = header
        data.forEachIndexed { i, table ->
            offsets[i] = size
            size += (table.size + 3) and 3.inv()
        }
        val out = ByteArray(size)
        fun put16(at: Int, value: Int) {
            out[at] = (value shr 8).toByte()
            out[at + 1] = value.toByte()
        }
        fun put32(at: Int, value: Int) {
            put16(at, value ushr 16)
            put16(at + 2, value)
        }
        val power = Integer.highestOneBit(count)
        put32(0, flavour)
        put16(4, count)
        put16(6, power * 16)
        put16(8, Integer.numberOfTrailingZeros(power))
        put16(10, count * 16 - power * 16)
        tables.forEachIndexed { i, (tag, checksum) ->
            val base = 12 + i * 16
            put32(base, tag)
            put32(base + 4, checksum)
            put32(base + 8, offsets[i])
            put32(base + 12, data[i].size)
        }
        data.forEachIndexed { i, table -> table.copyInto(out, offsets[i]) }
        return out
    }

    /** The first font of [sfnt]; a collection gives its first face. */
    fun parse(sfnt: ByteArray, fileName: String): FontInfo {
        try {
            val start = if (sfnt.u32(0) == COLLECTION) sfnt.u32(12) else 0
            val count = sfnt.u16(start + 4)
            val tables = HashMap<String, Pair<Int, Int>>()
            for (i in 0 until count) {
                val base = start + 12 + i * 16
                val offset = sfnt.u32(base + 8)
                val length = sfnt.u32(base + 12)
                if (offset < 0 || length < 0 || offset.toLong() + length > sfnt.size) continue
                tables[sfnt.tag(base)] = offset to length
            }
            fun table(tag: String) = tables[tag]?.first
            val head = table("head") ?: throw FontFormatException("head")
            val hhea = table("hhea") ?: throw FontFormatException("hhea")
            val maxp = table("maxp") ?: throw FontFormatException("maxp")
            val os2 = tables["OS/2"]?.takeIf { sfnt.u16(it.first) >= 2 && it.second >= 90 }?.first
            val post = table("post")
            val names = tables["name"]?.let { (offset, length) -> names(sfnt, offset, length) }.orEmpty()
            val family = names[16] ?: names[1] ?: ""
            return FontInfo(
                fileName = fileName,
                familyName = family,
                styleName = names[17] ?: names[2] ?: "Regular",
                fullName = names[4] ?: family,
                postScriptName = names[6] ?: "",
                unitsPerEm = sfnt.u16(head + 18),
                ascent = sfnt.i16(hhea + 4),
                descent = -sfnt.i16(hhea + 6),
                lineGap = sfnt.i16(hhea + 8),
                capHeight = os2?.let { sfnt.i16(it + 88) },
                xHeight = os2?.let { sfnt.i16(it + 86) },
                italicAngle = post?.let { sfnt.u32(it + 4) / 65536.0 } ?: 0.0,
                boxWidth = sfnt.i16(head + 40) - sfnt.i16(head + 36),
                boxHeight = sfnt.i16(head + 42) - sfnt.i16(head + 38),
                glyphCount = sfnt.u16(maxp + 4),
                tables = tables.keys.filter { tag -> tag.any { it != ' ' } }.sorted(),
                coverage = tables["cmap"]?.let { cmap(sfnt, it.first) } ?: FontCoverage.of(emptyList()),
            )
        } catch (_: IndexOutOfBoundsException) {
            throw FontFormatException("truncated")
        }
    }

    // windows, unicode, mac roman
    private fun names(data: ByteArray, table: Int, length: Int): Map<Int, String> {
        val count = data.u16(table + 2)
        val strings = table + data.u16(table + 4)
        val best = HashMap<Int, Pair<Int, String>>()
        for (i in 0 until count) {
            val record = table + 6 + i * 12
            val platform = data.u16(record)
            val encoding = data.u16(record + 2)
            val language = data.u16(record + 4)
            val id = data.u16(record + 6)
            val size = data.u16(record + 8)
            val offset = strings + data.u16(record + 10)
            if (offset + size > table + length || offset + size > data.size) continue
            val rank = when {
                platform == 3 && (encoding == 1 || encoding == 10) && language == 0x409 -> 0
                platform == 0 || platform == 3 && (encoding == 1 || encoding == 10) -> 1
                platform == 1 && encoding == 0 && language == 0 -> 2
                else -> continue
            }
            if ((best[id]?.first ?: Int.MAX_VALUE) <= rank) continue
            val text = if (platform == 1) {
                String(data, offset, size, Charsets.ISO_8859_1)
            } else {
                String(data, offset, size, Charsets.UTF_16BE)
            }
            best[id] = rank to text
        }
        return best.mapValues { it.value.second }
    }

    // prefers format 12, 13
    private fun cmap(data: ByteArray, table: Int): FontCoverage {
        val count = data.u16(table + 2)
        var chosen: Int? = null
        var chosenRank = Int.MAX_VALUE
        for (i in 0 until count) {
            val record = table + 4 + i * 8
            val platform = data.u16(record)
            val encoding = data.u16(record + 2)
            val offset = table + data.u32(record + 4)
            if (offset < 0 || offset + 2 > data.size) continue
            val format = data.u16(offset)
            val unicode = platform == 0 || platform == 3 && (encoding == 1 || encoding == 10)
            if (!unicode || format !in listOf(0, 4, 6, 12, 13)) continue
            val rank = if (format == 12 || format == 13) 0 else 1
            if (rank < chosenRank) {
                chosen = offset
                chosenRank = rank
            }
        }
        val subtable = chosen ?: return FontCoverage.of(emptyList())
        val ranges = mutableListOf<IntRange>()
        when (data.u16(subtable)) {
            0 -> (0 until 256).forEach { if (data.u8(subtable + 6 + it) != 0) ranges += it..it }
            4 -> {
                val segments = data.u16(subtable + 6) / 2
                val ends = subtable + 14
                val starts = ends + segments * 2 + 2
                val deltas = starts + segments * 2
                val rangeOffsets = deltas + segments * 2
                // bounds crafted segment counts
                val covered = java.util.BitSet(0x10000)
                var budget = 0x10000
                for (s in 0 until segments) {
                    val end = data.u16(ends + s * 2)
                    val first = data.u16(starts + s * 2)
                    val delta = data.u16(deltas + s * 2)
                    val rangeOffset = data.u16(rangeOffsets + s * 2)
                    if (first > end || first == 0xFFFF) continue
                    if (rangeOffset == 0) {
                        // glyph 0 is missing
                        val missing = (0x10000 - delta) and 0xFFFF
                        covered.set(first, end + 1)
                        if (missing in first..end) covered.clear(missing)
                    } else {
                        budget -= end - first + 1
                        if (budget < 0) break
                        for (code in first..end) {
                            val at = rangeOffsets + s * 2 + rangeOffset + (code - first) * 2
                            if (at + 1 < data.size && data.u16(at) != 0) covered.set(code)
                        }
                    }
                }
                var run = covered.nextSetBit(0)
                while (run >= 0) {
                    val stop = covered.nextClearBit(run)
                    ranges += run until stop
                    run = covered.nextSetBit(stop)
                }
            }
            6 -> {
                val first = data.u16(subtable + 6)
                val entries = data.u16(subtable + 8)
                for (i in 0 until entries) if (data.u16(subtable + 10 + i * 2) != 0) ranges += (first + i)..(first + i)
            }
            12, 13 -> {
                val format = data.u16(subtable)
                val groups = data.u32(subtable + 12)
                for (g in 0 until groups) {
                    val base = subtable + 16 + g * 12
                    val first = data.u32(base)
                    val last = minOf(data.u32(base + 4), 0x10FFFF)
                    val glyph = data.u32(base + 8)
                    if (first < 0 || first > last) continue
                    // 13: one glyph per group
                    if (format == 12 && glyph == 0) {
                        if (first < last) ranges += (first + 1)..last
                    } else if (glyph != 0) {
                        ranges += first..last
                    }
                }
            }
        }
        return FontCoverage.of(ranges)
    }

    const val SPECIMEN_LETTERS = "Hxdp"

    fun specimen(covers: (Int) -> Boolean): String {
        if (SPECIMEN_LETTERS.all { covers(it.code) }) return SPECIMEN_LETTERS
        val found = StringBuilder()
        var count = 0
        for (value in 0x21..0x1FFFF) {
            if (!Character.isAlphabetic(value) || !covers(value)) continue
            found.appendCodePoint(value)
            if (++count == 4) break
        }
        return if (count == 0) SPECIMEN_LETTERS else found.toString()
    }

    fun chartRange(specimen: String, blocks: List<UnicodeBlock>): IntRange {
        val first = specimen.codePointAt(0)
        if (first <= 0x17F) return 0x20..0x17F
        return blocks.firstOrNull { first in it.range }?.range ?: (first / 16 * 16).let { it..(it + 0x7F) }
    }

    fun signed(value: Int) = if (value < 0) "−${-value}" else "$value"

    fun css(family: String, fileName: String): String {
        val format = mapOf("woff2" to "woff2", "woff" to "woff", "otf" to "opentype")[fileName.substringAfterLast('.', "").lowercase()] ?: "truetype"
        return """
            @font-face {
              font-family: '$family';
              src: url('$fileName') format('$format');
              font-weight: normal;
              font-style: normal;
            }

            .my-text {
              font-family: '$family', sans-serif;
            }
        """.trimIndent()
    }

    fun overviewLines(info: FontInfo) = listOf(
        "Family" to info.familyName,
        "Style" to info.styleName,
        "Full Name" to info.fullName,
        "PostScript Name" to info.postScriptName,
        "File Name" to info.fileName,
    )

    /** [capHeight] and [xHeight] stand in when the font's OS/2 has none. */
    fun metricLines(info: FontInfo, capHeight: Int, xHeight: Int) = listOf(
        "Units per Em" to "${info.unitsPerEm}",
        "Glyph Count" to "%,d".format(info.glyphCount),
        "Ascent" to signed(info.ascent),
        "Descent" to signed(-info.descent),
        "Line Gap" to signed(info.lineGap),
        "Cap Height" to signed(info.capHeight ?: capHeight),
        "x-Height" to signed(info.xHeight ?: xHeight),
        "Italic Angle" to signed(info.italicAngle.roundToInt()) + "°",
        "Bounding Box" to "${info.boxWidth} × ${info.boxHeight}",
    )

    /** dt-ios FontMetricsPicture.fit: the em that fills the height, shrunk to fit the width down to half, and its baseline. */
    fun fit(ascent: Float, descent: Float, advance: Float, top: Float, width: Float, height: Float, labelRoom: Float): Pair<Float, Float> {
        val depth = maxOf(ascent + descent, 0.01f)
        val room = maxOf(0f, height - labelRoom)
        val tall = room / depth
        val wide = if (advance > 0) width / advance else tall
        val em = maxOf(minOf(tall, wide), tall * 0.5f)
        val emTop = top + labelRoom + (room - em * depth) / 2
        return em to emTop + ascent * em
    }

    val tableDescriptions = mapOf(
        "GDEF" to "Glyph definition — glyph classes, attachment points, ligature carets",
        "GPOS" to "Glyph positioning — kerning, mark attachment, cursive connection",
        "GSUB" to "Glyph substitution — ligatures, alternates, contextual forms",
        "OS/2" to "OS/2 and Windows metrics — weight, width, type, Unicode ranges",
        "cmap" to "Character-to-glyph mapping — Unicode → glyph ID",
        "glyf" to "Glyph outlines — TrueType quadratic Bezier contours",
        "head" to "Font header — units per em, created/modified dates, flags",
        "hhea" to "Horizontal header — ascender, descender, line gap, caret slope",
        "hmtx" to "Horizontal metrics — advance widths and left side bearings",
        "kern" to "Legacy kerning — pair-based kern values (replaced by GPOS)",
        "loca" to "Index to location — glyph offset table for 'glyf'",
        "maxp" to "Maximum profile — glyph count, max points/contours/components",
        "name" to "Naming table — family, style, copyright, license, designer",
        "post" to "PostScript — glyph names, italic angle, underline metrics",
        "CFF " to "Compact Font Format — PostScript cubic Bezier outlines",
        "CFF2" to "CFF version 2 — variable font outline data",
        "DSIG" to "Digital signature — font integrity verification",
        "MATH" to "Maths typesetting — constants, glyph variants, accent positions",
        "SVG " to "SVG glyph outlines — colour vector graphics per glyph",
        "COLR" to "Colour glyph layers — multi-layer colour glyphs",
        "CPAL" to "Colour palette — palettes for COLR glyphs",
        "fvar" to "Font variations — axes (weight, width, slant, etc.)",
        "gvar" to "Glyph variations — TrueType variation deltas",
        "STAT" to "Style attributes — axis values and design-space labels",
    )
}
