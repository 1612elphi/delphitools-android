package com.delphi.delphitools.tools.devtools

sealed interface DiffLine {
    val text: String

    data class Unchanged(override val text: String, val leftIndex: Int, val rightIndex: Int) : DiffLine
    data class Removed(override val text: String, val leftIndex: Int) : DiffLine
    data class Added(override val text: String, val rightIndex: Int) : DiffLine
}


/** dt-core TextDiffer: a line diff that keeps unchanged lines in their order. */
object TextDiffer {
    // Character.isNewline; \r\n is one Character in Swift, so it splits once
    private val newline = Regex("\r\n|[\n\r\u000B\u000C\u0085  ]")

    fun lines(text: String) = text.split(newline)

    fun diff(left: String, right: String): List<DiffLine> {
        val leftLines = lines(left)
        val rightLines = lines(right)
        val difference = difference(leftLines, rightLines)
        val removals = difference.removals.toSet()
        val insertions = difference.insertions.toSet()
        val result = mutableListOf<DiffLine>()
        var li = 0
        var ri = 0
        while (li < leftLines.size || ri < rightLines.size) {
            if (li in removals) {
                result += DiffLine.Removed(leftLines[li], li)
                li++
                continue
            }
            if (ri in insertions) {
                result += DiffLine.Added(rightLines[ri], ri)
                ri++
                continue
            }
            if (li < leftLines.size && ri < rightLines.size) {
                result += DiffLine.Unchanged(leftLines[li], li, ri)
                li++
                ri++
            } else {
                break
            }
        }
        return result
    }

    /** Matches the web patch. */
    fun patch(diff: List<DiffLine>) = diff.joinToString("\n") { line ->
        when (line) {
            is DiffLine.Unchanged -> "  " + line.text
            is DiffLine.Removed -> "- " + line.text
            is DiffLine.Added -> "+ " + line.text
        }
    }
}

/** dt-ios DiffWords: the changed words of two similar lines. */
object DiffWords {
    private const val LEAST_SHARED = 0.4

    class Token(val text: String, val range: TextSpan)

    /** UTF-16 ranges; null when the lines share too little to pair. */
    fun changes(old: String, new: String): Pair<List<TextSpan>, List<TextSpan>>? {
        val before = tokens(old)
        val after = tokens(new)
        val longest = maxOf(before.size, after.size)
        if (longest == 0) return null
        val difference = difference(before.map { it.text }, after.map { it.text })
        val shared = before.size - difference.removals.size
        if (shared.toDouble() / longest < LEAST_SHARED) return null
        return merged(difference.removals.map { before[it].range }) to merged(difference.insertions.map { after[it].range })
    }

    // CharacterSet.alphanumerics: letters, marks and numbers
    private fun kind(codePoint: Int): Int = when (Character.getType(codePoint).toByte()) {
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER, Character.MODIFIER_LETTER,
        Character.OTHER_LETTER, Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK,
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER,
        -> 0
        Character.SPACE_SEPARATOR -> 1
        else -> when (codePoint) {
            '_'.code -> 0
            '\t'.code -> 1
            else -> 2
        }
    }

    /** Word, space and symbol runs; each symbol is a run of its own. */
    fun tokens(line: String): List<Token> {
        val out = mutableListOf<Token>()
        var start = 0
        var kind = -1
        var offset = 0
        while (offset < line.length) {
            val codePoint = line.codePointAt(offset)
            val next = kind(codePoint)
            if ((next != kind || next == 2) && offset > start) {
                out += Token(line.substring(start, offset), TextSpan(start, offset))
                start = offset
            }
            kind = next
            offset += Character.charCount(codePoint)
        }
        if (offset > start) out += Token(line.substring(start, offset), TextSpan(start, offset))
        return out
    }

    private fun merged(ranges: List<TextSpan>): List<TextSpan> = ranges.sortedBy { it.start }.fold(mutableListOf()) { out, range ->
        val last = out.lastOrNull()
        if (last != null && last.end == range.start) out[out.size - 1] = TextSpan(last.start, range.end) else out += range
        out
    }
}
