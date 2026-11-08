package com.delphi.delphitools.tools.typography

import java.text.BreakIterator

private val punctuation = setOf(
    Character.CONNECTOR_PUNCTUATION,
    Character.DASH_PUNCTUATION,
    Character.START_PUNCTUATION,
    Character.END_PUNCTUATION,
    Character.INITIAL_QUOTE_PUNCTUATION,
    Character.FINAL_QUOTE_PUNCTUATION,
    Character.OTHER_PUNCTUATION,
).map { it.toInt() }.toSet()

private val whitespace = Regex("""\s+""")

private fun String.trimPunctuation() = trim { Character.getType(it) in punctuation }

private fun segments(text: String, iterator: BreakIterator): Int {
    iterator.setText(text)
    var count = 0
    while (iterator.next() != BreakIterator.DONE) count++
    return count
}

/** dt-ios WordCounterViewModel: the counts for one text. */
class WordStats(val text: String) {
    val characters = segments(text, BreakIterator.getCharacterInstance())
    val charactersNoSpaces = segments(text.filterNot(Char::isWhitespace), BreakIterator.getCharacterInstance())
    val words = text.split(whitespace).count { it.isNotEmpty() }

    val sentences = if (text.isBlank()) 0 else segments(text, BreakIterator.getSentenceInstance())

    val paragraphs = text.trim().let { trimmed ->
        if (trimmed.isEmpty()) 0 else trimmed.split('\n').count { it.isNotBlank() }
    }

    val lines = if (text.isEmpty()) 0 else text.split('\n').size

    /** Brysbaert 2019: 238 words a minute. */
    val readingMinutes = words / 238.0
    val speakingMinutes = words / 150.0
    val averageWordLength = if (words == 0) 0.0 else charactersNoSpaces.toDouble() / words

    /** Case-insensitive, three or more characters, ties alphabetical. */
    fun topWords(limit: Int): List<Pair<String, Int>> =
        text.lowercase()
            .split(whitespace)
            .map { it.trimPunctuation() }
            .filter { it.length >= 3 }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key to it.value }

    companion object {
        /** A word as the picture matches it against the most frequent ones. */
        fun key(word: String) = word.lowercase().trimPunctuation()
    }
}

/** dt-ios WordCounterPicture: each word a bar as long as its characters, lines wrapped into columns. Sizes in points. */
object WordBars {
    const val LARGEST = 8f
    const val SMALLEST = 2f

    /** Gap between rows and a character's width, as fractions of the bar height. */
    const val GAP = 0.6f
    const val CHARACTER = 0.55f
    const val COLUMN_GAP = 20f

    /** Placeholder word lengths. */
    val ghost = listOf(listOf(5, 3, 7, 4, 6, 2, 5, 8, 3), listOf(4, 6, 3, 5, 7), listOf(), listOf(6, 2, 4, 5, 3, 7, 4, 3), listOf(3, 5, 4))

    data class Fit(val bar: Float, val character: Float, val columnCharacters: Int, val columnWidth: Float, val columns: Int, val perColumn: Int)

    /** The largest bar, in half-point steps, that fits every row; the smallest when none does. */
    fun fit(lines: List<List<Int>>, width: Float, height: Float): Fit {
        val longest = lines.maxOfOrNull { line -> line.sum() + maxOf(0, line.size - 1) } ?: 1
        var bar = LARGEST
        while (true) {
            val character = bar * CHARACTER
            val columnCharacters = maxOf(1, minOf((width / character).toInt(), longest))
            val columnWidth = columnCharacters * character
            val columns = maxOf(1, ((width + COLUMN_GAP) / (columnWidth + COLUMN_GAP)).toInt())
            val perColumn = maxOf(1, (height / (bar * (1 + GAP))).toInt())
            if (rows(lines, columnCharacters) <= columns * perColumn || bar <= SMALLEST) {
                return Fit(bar, character, columnCharacters, columnWidth, columns, perColumn)
            }
            bar -= 0.5f
        }
    }

    /** Rows the lines wrap to; an empty line counts. */
    fun rows(lines: List<List<Int>>, columnCharacters: Int): Int = lines.sumOf { line ->
        var rows = 1
        var used = 0
        for (length in line) {
            if (used > 0 && used + 1 + length > columnCharacters) {
                rows++
                used = length
            } else {
                used += (if (used == 0) 0 else 1) + length
            }
        }
        rows
    }
}
