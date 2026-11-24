package com.delphi.delphitools.tools.typography

import java.text.BreakIterator
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** dt-ios LargeTypeEngine and LargeTypeLayout: the text a character per cell, sized and wrapped to fit. */
object LargeTypeEngine {
    enum class CharClass { Digit, Space, Letter, Special }

    const val MIN_SIZE = 28.0
    const val MAX_SIZE = 360.0
    const val DEFAULT_SIZE = 112.0
    const val SIZE_STEP = 4.0
    const val DEFAULT_ASPECT = 0.6
    const val SPACE_GLYPH = "␣"

    /** The cells shown are capped; the character count still reports the whole string. */
    const val MAX_GLYPHS = 400

    const val CELL_PADDING = 12.0
    const val POSITION_HEIGHT = 18.0

    /** Line height of the monospaced font, as a share of its point size. */
    const val LINE_HEIGHT = 1.2

    /** Advance of the monospaced font, as a share of its point size. */
    const val ADVANCE_SHARE = 0.6

    /** Marquee: blank characters between the end of the text and its next start. */
    const val MARQUEE_GAP = 4

    /** Marquee: characters a second at a speed of 1. */
    const val MARQUEE_PACE = 3.0

    data class Cell(val width: Double, val height: Double)

    data class Frame(val x: Double, val y: Double, val width: Double, val height: Double) {
        val maxY get() = y + height
    }

    /** User-perceived characters, as Swift's Character. */
    fun characters(text: String): List<String> {
        val breaks = BreakIterator.getCharacterInstance().apply { setText(text) }
        val out = ArrayList<String>()
        var start = breaks.first()
        var end = breaks.next()
        while (end != BreakIterator.DONE) {
            out.add(text.substring(start, end))
            start = end
            end = breaks.next()
        }
        return out
    }

    fun glyphs(text: String): List<String> = characters(text).take(MAX_GLYPHS)

    fun isSpace(glyph: String) = glyph.isNotEmpty() && glyph.all { it.isWhitespace() }

    fun charClass(glyph: String): CharClass {
        val first = glyph.codePointAt(0)
        return when {
            Character.getType(first).let {
                it == Character.DECIMAL_DIGIT_NUMBER.toInt() || it == Character.LETTER_NUMBER.toInt() || it == Character.OTHER_NUMBER.toInt()
            } -> CharClass.Digit
            isSpace(glyph) -> CharClass.Space
            Character.isLetter(first) -> CharClass.Letter
            else -> CharClass.Special
        }
    }

    fun cell(size: Double, widest: Double, positions: Boolean) =
        Cell(ceil(size * widest) + CELL_PADDING, ceil(size * LINE_HEIGHT) + CELL_PADDING + if (positions) POSITION_HEIGHT else 0.0)

    /** Largest size in the steps at which `glyphs` fit the columns without scrolling; the smallest size otherwise. */
    fun fit(glyphs: List<String>, widest: Double, widths: List<Double>, height: Double, positions: Boolean): Double {
        if (glyphs.isEmpty() || widths.isEmpty() || height <= 0) return DEFAULT_SIZE
        var size = MAX_SIZE
        while (size > MIN_SIZE) {
            val cell = cell(size, widest, positions)
            val rows = (height / cell.height).toInt()
            val perRow = widths.map { (it / cell.width).toInt() }
            if (rows > 0 && 0 !in perRow && wrap(glyphs, perRow, rows).size <= rows * widths.size) return size
            size -= SIZE_STEP
        }
        return MIN_SIZE
    }

    /**
     * Cells in each row, in reading order. Each column takes `rowsPerColumn` rows, the last takes the rest.
     * A word starting mid-row moves to the next row when the next row holds it whole.
     */
    fun wrap(glyphs: List<String>, perRow: List<Int>, rowsPerColumn: Int): List<Int> {
        fun capacity(row: Int) = perRow[min(row / rowsPerColumn, perRow.size - 1)]
        val rows = ArrayList<Int>()
        var index = 0
        while (index < glyphs.size) {
            var end = min(index + capacity(rows.size), glyphs.size)
            if (end < glyphs.size && !isSpace(glyphs[end]) && !isSpace(glyphs[end - 1])) {
                var start = end - 1
                while (start > index && !isSpace(glyphs[start - 1])) start--
                val next = (start until glyphs.size).firstOrNull { isSpace(glyphs[it]) } ?: glyphs.size
                if (start > index && next - start <= capacity(rows.size + 1)) end = start
            }
            rows.add(end - index)
            index = end
        }
        return rows
    }

    /** Frames from a top of zero. With two columns the rows are shared as evenly as the wrap allows, the first column first. */
    fun frames(glyphs: List<String>, size: Double, widest: Double, columns: List<ClosedFloatingPointRange<Double>>, positions: Boolean): List<Frame> {
        if (glyphs.isEmpty() || columns.isEmpty()) return emptyList()
        val cell = cell(size, widest, positions)
        val perRow = columns.map { max(1, ((it.endInclusive - it.start) / cell.width).toInt()) }
        var rowsPerColumn = if (columns.size > 1) max(1, ceil(glyphs.size.toDouble() / perRow.sum()).toInt()) else Int.MAX_VALUE
        var rows = wrap(glyphs, perRow, rowsPerColumn)
        while (rows.size > rowsPerColumn.toLong() * columns.size) {
            rowsPerColumn++
            rows = wrap(glyphs, perRow, rowsPerColumn)
        }
        val frames = ArrayList<Frame>()
        rows.forEachIndexed { index, inRow ->
            val column = min(index / rowsPerColumn, columns.size - 1)
            val row = index - column * rowsPerColumn
            val span = columns[column]
            val left = span.start + ((span.endInclusive - span.start) - inRow * cell.width) / 2
            for (slot in 0 until inRow) frames.add(Frame(left + slot * cell.width, row * cell.height, cell.width, cell.height))
        }
        return frames
    }

    /** Marquee: indices of the endless repeat with any part on screen; index `g` spans `width + g × advance − scrolled` onwards. */
    fun visible(scrolled: Double, width: Double, advance: Double): IntRange? {
        if (advance <= 0) return null
        val first = max(0, floor((scrolled - width) / advance).toInt())
        val last = ceil(scrolled / advance).toInt() - 1
        return if (last >= first) first..last else null
    }
}
