package com.delphi.delphitools.tools.turbonerd

/** Unified English Braille, grade 1. */
object BrailleEngine {
    private const val BLANK = 0x2800

    private fun cell(dots: String): String {
        var mask = 0
        for (dot in dots) if (dot in '1'..'8') mask = mask or (1 shl (dot - '1'))
        return (BLANK + mask).toChar().toString()
    }

    private const val LETTERS = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "1234567890"

    private val cells = (
        "1 12 14 145 15 124 1245 125 24 245 13 123 134 1345 135 1234 12345 " +
            "1235 234 2345 136 1236 2456 1346 13456 1356"
        ).split(" ").map(::cell)

    private val letterOf = cells.withIndex().associate { (index, pattern) -> pattern to LETTERS[index] }

    private val punctCells = listOf(
        ',' to "2", ';' to "23", ':' to "25", '.' to "256", '!' to "235",
        '?' to "236", '\'' to "3", '-' to "36",
        '(' to "5 126", ')' to "5 345", '/' to "456 34",
    ).associate { (char, dots) -> char to dots.split(" ").joinToString("") { cell(it) } }

    private val punctOf = punctCells.entries.associate { (char, pattern) -> pattern to char }

    private val capital = cell("6")
    private val number = cell("3456")
    private val grade1 = cell("56")

    // UEB §7.6
    private val openQuote = cell("236")
    private val closeQuote = cell("356")

    private fun atWordStart(out: CharSequence) = out.isEmpty() || out.last().isWhitespace()

    private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

    private fun Char.isAsciiDigit() = this in '0'..'9'

    /** One string per code point. */
    internal fun codePoints(text: String): List<String> {
        val out = ArrayList<String>(text.length)
        var i = 0
        while (i < text.length) {
            val n = Character.charCount(text.codePointAt(i))
            out += text.substring(i, i + n)
            i += n
        }
        return out
    }

    fun toBraille(text: String): String {
        val out = StringBuilder()
        var numeric = false
        for (token in tokenise(text)) {
            val first = token.first()
            if (first.isAsciiDigit()) {
                if (!numeric) out.append(number)
                numeric = true
                for (digit in token) out.append(cells[DIGITS.indexOf(digit)])
                continue
            }
            if (first.isAsciiLetter()) {
                // UEB §6.5.1
                if (numeric && first.lowercaseChar() in "abcdefghij") out.append(grade1)
                numeric = false
                val capsWord = token.length > 1 && token == token.uppercase()
                if (capsWord) out.append(capital).append(capital)
                for (char in token) {
                    val lower = char.lowercaseChar()
                    if (!capsWord && char != lower) out.append(capital)
                    out.append(cells[LETTERS.indexOf(lower)])
                }
                continue
            }
            // UEB §6.2
            numeric = numeric && (token == "." || token == ",")
            val pattern = punctCells[first]
            when {
                token == "\"" -> out.append(if (atWordStart(out)) openQuote else closeQuote)
                pattern != null && token.length == 1 -> out.append(pattern)
                else -> out.append(token)
            }
        }
        return out.toString()
    }

    // letter runs, digit runs, singles
    private fun tokenise(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var kind = 0 // 1 letters, 2 digits
        for (char in codePoints(text)) {
            val c = char[0]
            val next = if (char.length == 1 && c.isAsciiLetter()) 1 else if (char.length == 1 && c.isAsciiDigit()) 2 else 0
            if (next != 0 && next == kind) {
                current.append(char)
                continue
            }
            if (current.isNotEmpty()) out += current.toString()
            current.clear().append(char)
            kind = next
            if (next == 0) {
                out += current.toString()
                current.clear()
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    /** One step of reading braille: the cells taken and the text they give. An indicator gives no text. */
    data class Reading(val cells: String, val text: String, val kind: Kind) {
        enum class Kind { Letter, Indicator, Other }
    }

    fun fromBraille(cellString: String) = read(cellString).joinToString("") { it.text }

    fun read(cellString: String): List<Reading> {
        val chars = codePoints(cellString)
        val readings = mutableListOf<Reading>()
        val out = StringBuilder()
        var numeric = false
        var capsWord = false
        var capsNext = false
        var i = 0
        fun emit(count: Int, text: String, kind: Reading.Kind) {
            readings += Reading(chars.subList(i, i + count).joinToString(""), text, kind)
            out.append(text)
            i += count
        }
        while (i < chars.size) {
            val c = chars[i]
            if (c == capital) {
                if (i + 1 < chars.size && chars[i + 1] == capital) {
                    capsWord = true
                    emit(2, "", Reading.Kind.Indicator)
                } else {
                    capsNext = true
                    emit(1, "", Reading.Kind.Indicator)
                }
                continue
            }
            if (c == number) {
                numeric = true
                emit(1, "", Reading.Kind.Indicator)
                continue
            }
            if (c == grade1) {
                numeric = false
                emit(1, "", Reading.Kind.Indicator)
                continue
            }
            val letter = letterOf[c]
            if (letter != null) {
                val index = LETTERS.indexOf(letter)
                if (numeric && index < 10) {
                    emit(1, DIGITS[index].toString(), Reading.Kind.Letter)
                    continue
                }
                numeric = false
                val text = if (capsNext || capsWord) letter.uppercase() else letter.toString()
                capsNext = false
                emit(1, text, Reading.Kind.Letter)
                continue
            }
            capsWord = false
            val pair = punctOf[c + (if (i + 1 < chars.size) chars[i + 1] else "")]
            if (pair != null) {
                numeric = false
                // last cell: one-cell key
                emit(minOf(2, chars.size - i), pair.toString(), Reading.Kind.Letter)
                continue
            }
            if (c == openQuote) {
                numeric = false
                emit(1, if (atWordStart(out)) "\"" else "?", Reading.Kind.Letter)
                continue
            }
            if (c == closeQuote) {
                numeric = false
                emit(1, "\"", Reading.Kind.Letter)
                continue
            }
            val punct = punctOf[c]?.toString()
            val text = punct ?: c
            numeric = numeric && (text == "." || text == ",")
            emit(1, text, if (punct == null) Reading.Kind.Other else Reading.Kind.Letter)
        }
        return readings
    }

    fun looksLikeBraille(text: String) = text.any { it.code in 0x2800..0x28ff }

    /** Dot numbers of one cell, e.g. "1-2-5", or empty for anything else. */
    fun dots(pattern: String): String {
        if (pattern.isEmpty()) return ""
        val mask = pattern.codePointAt(0) - BLANK
        if (mask <= 0 || mask > 0xff) return ""
        return (1..8).filter { mask and (1 shl (it - 1)) != 0 }.joinToString("-")
    }
}
