package com.delphi.delphitools.tools.devtools

import java.text.BreakIterator
import java.util.regex.Pattern

data class ScratchpadCounts(val words: Int, val characters: Int, val lines: Int)

/** dt-ios TextScratchpadViewModel's transforms and ScratchpadEditor's find and counts, on whole strings. */
object TextScratchpadEngine {
    private const val REGEX_TIME_LIMIT_MS = 400L
    private const val TYPING_RUN_MS = 1000L

    /** dt-ios ScratchpadEditor's typing run: a keystroke a second or more after the last one, or the first, starts an undo step. */
    fun startsUndoStep(lastTypedMs: Long?, nowMs: Long) = lastTypedMs == null || nowMs - lastTypedMs >= TYPING_RUN_MS
    private val newline = Regex("\r\n|[\n\r\u000B\u000C\u0085  ]")
    private val lineNumber = Regex("^\\d+[.):]\\s?")
    private val extraSpaces = Regex(" {2,}")
    private const val QUERY_ALLOWED = "!$&'()*+,-./:;=?@_~"

    private fun lines(text: String) = text.split(newline)

    private fun words(text: String) = text.split { !it.isLetterOrDigit() }.filter { it.isNotEmpty() }

    private inline fun String.split(crossinline separator: (Char) -> Boolean): List<String> {
        val out = mutableListOf<String>()
        var start = 0
        for (index in indices) {
            if (separator(this[index])) {
                out += substring(start, index)
                start = index + 1
            }
        }
        out += substring(start)
        return out
    }

    fun upperCase(text: String) = text.uppercase()

    fun lowerCase(text: String) = text.lowercase()

    /** NSString.capitalized: words are runs between whitespace. */
    fun titleCase(text: String): String {
        val out = StringBuilder(text.length)
        var start = true
        for (char in text) {
            out.append(if (start) char.titlecase() else char.lowercase())
            start = char.isWhitespace()
        }
        return out.toString()
    }

    fun sentenceCase(text: String): String {
        val out = StringBuilder(text.length)
        var capitaliseNext = true
        for (char in text.lowercase()) {
            if (capitaliseNext && char.isLetter()) {
                out.append(char.uppercase())
                capitaliseNext = false
            } else {
                out.append(char)
            }
            if (char == '.' || char == '!' || char == '?') capitaliseNext = true
        }
        return out.toString()
    }

    fun camelCase(text: String): String {
        val words = words(text)
        val first = words.firstOrNull() ?: return text
        return first.lowercase() + words.drop(1).joinToString("") { it.take(1).uppercase() + it.drop(1).lowercase() }
    }

    fun snakeCase(text: String) = words(text).joinToString("_") { it.lowercase() }

    fun kebabCase(text: String) = words(text).joinToString("-") { it.lowercase() }

    fun sortAZ(text: String) = lines(text).sorted().joinToString("\n")

    fun sortZA(text: String) = lines(text).sorted().reversed().joinToString("\n")

    fun sortByLength(text: String) = lines(text).sortedBy { it.codePointCount(0, it.length) }.joinToString("\n")

    fun reverseOrder(text: String) = lines(text).reversed().joinToString("\n")

    fun shuffle(text: String, random: kotlin.random.Random = kotlin.random.Random) = lines(text).shuffled(random).joinToString("\n")

    fun removeDuplicates(text: String) = lines(text).distinct().joinToString("\n")

    fun addLineNumbers(text: String) = lines(text).mapIndexed { index, line -> "${index + 1}. $line" }.joinToString("\n")

    fun removeLineNumbers(text: String) = lines(text).joinToString("\n") { lineNumber.replaceFirst(it, "") }

    fun trimLines(text: String) = lines(text).joinToString("\n") { it.trim() }

    fun removeEmptyLines(text: String) = lines(text).filter { it.isNotBlank() }.joinToString("\n")

    fun removeExtraSpaces(text: String) = extraSpaces.replace(text, " ")

    fun joinLines(text: String) = lines(text).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    /** Words are split at spaces only, as iOS does; a newline stays inside its word. */
    fun wrapAt(text: String, width: Int): String {
        val out = mutableListOf<String>()
        var current = StringBuilder()
        for (word in text.split(' ').filter { it.isNotEmpty() }) {
            when {
                current.isEmpty() -> current.append(word)
                current.length + 1 + word.length <= width -> current.append(' ').append(word)
                else -> {
                    out += current.toString()
                    current = StringBuilder(word)
                }
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out.joinToString("\n")
    }

    /** addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed). */
    fun urlEncode(text: String): String {
        val out = StringBuilder()
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val value = byte.toInt() and 0xFF
            val char = value.toChar()
            if (value < 0x80 && (char.isLetterOrDigit() || char in QUERY_ALLOWED)) out.append(char) else out.append("%%%02X".format(value))
        }
        return out.toString()
    }

    /** removingPercentEncoding: null for a broken escape or bytes that are not UTF-8. */
    fun urlDecode(text: String): String? {
        val bytes = java.io.ByteArrayOutputStream()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '%') {
                if (index + 2 >= text.length) return null
                val high = Character.digit(text[index + 1], 16)
                val low = Character.digit(text[index + 2], 16)
                if (high < 0 || low < 0) return null
                bytes.write(high * 16 + low)
                index += 3
            } else {
                bytes.write(char.toString().toByteArray(Charsets.UTF_8))
                index++
            }
        }
        return try {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            null
        }
    }

    private fun search(find: String, isRegex: Boolean, text: String, global: Boolean): RegexOutcome =
        RegexTesterEngine.run(
            if (isRegex) find else Pattern.quote(find),
            (if (global) "g" else "") + if (isRegex) "" else "i",
            text,
            limit = Int.MAX_VALUE,
            timeLimitMs = REGEX_TIME_LIMIT_MS,
        )

    /** Every non-empty range of [find] in [text]; plain text ignores case. Null when the pattern does not compile or runs too long. */
    fun matches(find: String, isRegex: Boolean, text: String): List<TextSpan>? {
        if (find.isEmpty()) return emptyList()
        val found = search(find, isRegex, text, global = true) as? RegexOutcome.Matched ?: return null
        return found.matches.map { it.range }.filter { it.length > 0 }
    }

    /** Plain text replaces literally; a pattern expands its template. Unchanged when nothing matches or the pattern fails. */
    fun replace(text: String, find: String, replacement: String, isRegex: Boolean, all: Boolean): String {
        if (find.isEmpty()) return text
        val found = search(find, isRegex, text, global = all) as? RegexOutcome.Matched ?: return text
        if (found.matches.isEmpty()) return text
        val out = StringBuilder()
        var last = 0
        for (match in found.matches) {
            out.append(text, last, match.range.start)
            out.append(if (isRegex) expand(replacement, text, match, found.groups) else replacement)
            last = match.range.end
        }
        out.append(text, last, text.length)
        return out.toString()
    }

    /** NSRegularExpression templates: `$n` is group n, digits taken while the group exists; a backslash escapes the next character. */
    internal fun expand(template: String, text: String, match: RegexMatch, groupCount: Int): String {
        val out = StringBuilder()
        var index = 0
        while (index < template.length) {
            val char = template[index]
            when {
                char == '\\' && index + 1 < template.length -> {
                    out.append(template[index + 1])
                    index += 2
                }
                char == '$' && index + 1 < template.length && template[index + 1].isAsciiDigit() -> {
                    var group = template[index + 1] - '0'
                    index += 2
                    while (index < template.length && template[index].isAsciiDigit() && group * 10 + (template[index] - '0') <= groupCount) {
                        group = group * 10 + (template[index] - '0')
                        index++
                    }
                    val range = if (group == 0) match.range else match.groups.getOrNull(group - 1)
                    if (range != null) out.append(text, range.start, range.end)
                }
                else -> {
                    out.append(char)
                    index++
                }
            }
        }
        return out.toString()
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'

    private fun extract(pattern: Regex, text: String) = pattern.findAll(text).map { it.value }.toList()

    private val email = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val url = Regex("https?://[^\\s]+")
    private val number = Regex("-?\\d+\\.?\\d*")

    fun extractEmails(text: String) = extract(email, text)

    fun extractUrls(text: String) = extract(url, text)

    fun extractNumbers(text: String) = extract(number, text)

    fun counts(text: String): ScratchpadCounts {
        val words = text.split { it.isWhitespace() }.count { it.isNotEmpty() }
        val graphemes = BreakIterator.getCharacterInstance()
        graphemes.setText(text)
        var characters = 0
        while (graphemes.next() != BreakIterator.DONE) characters++
        return ScratchpadCounts(words, characters, if (text.isEmpty()) 0 else lines(text).size)
    }
}
