package com.delphi.delphitools.tools.typography

import java.text.BreakIterator
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** dt-ios DocConvert, the parts the text editor uses: the Markdown reader, the HTML and RTF writers. */
object TextDoc {
    data class Inline(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false, val link: String? = null)

    sealed interface Block {
        data class Heading(val level: Int, val runs: List<Inline>) : Block
        data class Paragraph(val runs: List<Inline>) : Block
        data class Bullets(val ordered: Boolean, val items: List<List<Inline>>) : Block
        data class Quote(val runs: List<Inline>) : Block
        data class Code(val body: String) : Block
        data object Rule : Block
    }

    data class Rgb(val red: Double, val green: Double, val blue: Double) {
        val css get() = "#%02x%02x%02x".format((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
    }

    data class Style(val family: Family, val lineHeight: Double, val text: Rgb, val background: Rgb) {
        enum class Family { Sans, Serif, Mono }

        val cssFamily
            get() = when (family) {
                Family.Sans -> "-apple-system, system-ui, sans-serif"
                Family.Serif -> "ui-serif, Georgia, serif"
                Family.Mono -> "ui-monospace, Menlo, monospace"
            }
    }

    val headingSizes = listOf(2.0, 1.6, 1.35, 1.2, 1.1, 1.0)

    val Block.mark
        get() = when (this) {
            is Block.Heading -> "H$level"
            is Block.Paragraph -> "¶"
            is Block.Bullets -> if (ordered) "1." else "•"
            is Block.Quote -> "“"
            is Block.Code -> "{ }"
            Block.Rule -> "—"
        }

    val Block.text: String
        get() = when (this) {
            is Block.Heading -> runs.joinToString("") { it.text }
            is Block.Paragraph -> runs.joinToString("") { it.text }
            is Block.Quote -> runs.joinToString("") { it.text }
            is Block.Bullets -> items.joinToString(" ") { item -> item.joinToString("") { it.text } }
            is Block.Code -> body
            Block.Rule -> ""
        }

    val Block.words get() = TextEditorEngine.words(text)

    fun parseMarkdown(source: String) = markdownBlocks(source).map { it.first }

    /** Blocks with the source lines each spans. */
    fun markdownBlocks(source: String): List<Pair<Block, IntRange>> {
        val blocks = ArrayList<Pair<Block, IntRange>>()
        var paragraph = ArrayList<String>()
        var paragraphStart = 0
        val lines = source.replace("\r\n", "\n").split("\n")
        var index = 0

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            blocks.add(Block.Paragraph(parseInline(paragraph.joinToString(" "))) to (paragraphStart until index))
            paragraph = ArrayList()
        }

        while (index < lines.size) {
            val line = lines[index].trim(' ', '\t')
            if (line.isEmpty()) {
                flushParagraph()
                index++
                continue
            }
            val start = index
            if (line.startsWith("```") || line.startsWith("~~~")) {
                flushParagraph()
                val fence = line.take(3)
                val body = ArrayList<String>()
                index++
                while (index < lines.size && !lines[index].trim(' ', '\t').startsWith(fence)) {
                    body.add(lines[index])
                    index++
                }
                if (index < lines.size) index++
                blocks.add(Block.Code(body.joinToString("\n")) to (start until index))
                continue
            }
            val heading = headingParts(line)
            if (heading != null) {
                flushParagraph()
                index++
                blocks.add(Block.Heading(heading.first, parseInline(heading.second)) to (start until index))
                continue
            }
            val setext = if (paragraph.isEmpty()) null else setextLevel(line)
            if (setext != null) {
                val runs = parseInline(paragraph.joinToString(" "))
                paragraph = ArrayList()
                index++
                blocks.add(Block.Heading(setext, runs) to (paragraphStart until index))
                continue
            }
            if (isRule(line)) {
                flushParagraph()
                index++
                blocks.add(Block.Rule to (start until index))
                continue
            }
            if (line.startsWith(">")) {
                flushParagraph()
                val body = ArrayList<String>()
                while (index < lines.size) {
                    val quoted = lines[index].trim(' ', '\t')
                    if (!quoted.startsWith(">")) break
                    body.add(quoted.drop(1).trim(' ', '\t'))
                    index++
                }
                blocks.add(Block.Quote(parseInline(body.joinToString(" "))) to (start until index))
                continue
            }
            if (bulletBody(line) != null || orderedBody(line) != null) {
                val ordered = bulletBody(line) == null
                flushParagraph()
                val items = ArrayList<List<Inline>>()
                while (index < lines.size) {
                    val item = lines[index].trim(' ', '\t')
                    val body = (if (ordered) orderedBody(item) else bulletBody(item)) ?: break
                    items.add(parseInline(body))
                    index++
                }
                blocks.add(Block.Bullets(ordered, items) to (start until index))
                continue
            }
            if (paragraph.isEmpty()) paragraphStart = index
            paragraph.add(line)
            index++
        }
        flushParagraph()
        return blocks
    }

    private fun headingParts(line: String): Pair<Int, String>? {
        val hashes = line.takeWhile { it == '#' }.length
        if (hashes !in 1..6) return null
        val rest = line.drop(hashes)
        if (!rest.startsWith(" ") && rest.isNotEmpty()) return null
        return hashes to rest.trim(' ', '\t').trimEnd('#').trim(' ', '\t')
    }

    // commonmark setext underline
    private fun setextLevel(line: String): Int? {
        val first = line.firstOrNull() ?: return null
        if (line.length < 2 || (first != '=' && first != '-') || !line.all { it == first }) return null
        return if (first == '=') 1 else 2
    }

    private fun isRule(line: String): Boolean {
        val squeezed = line.replace(" ", "")
        val first = squeezed.firstOrNull() ?: return false
        return squeezed.length >= 3 && first in "-*_" && squeezed.all { it == first }
    }

    private fun bulletBody(line: String): String? = listOf("- ", "* ", "+ ").firstOrNull { line.startsWith(it) }?.let { line.drop(it.length) }

    private fun orderedBody(line: String): String? {
        val digits = line.takeWhile { it.isDigit() }
        if (digits.isEmpty()) return null
        val rest = line.drop(digits.length)
        val separator = rest.firstOrNull() ?: return null
        if ((separator != '.' && separator != ')') || !rest.drop(1).startsWith(" ")) return null
        return rest.drop(2)
    }

    fun parseInline(source: String): List<Inline> {
        val runs = ArrayList<Inline>()
        val buffer = StringBuilder()
        val chars = source
        var index = 0

        fun flush() {
            if (buffer.isEmpty()) return
            runs.add(Inline(buffer.toString()))
            buffer.clear()
        }

        fun find(needle: Char, from: Int) = chars.indexOf(needle, from).takeIf { it >= 0 }

        fun findMarker(marker: String, from: Int) = chars.indexOf(marker, from).takeIf { it >= 0 }

        // forward only: a `[` with no `]` after it would otherwise rescan the paragraph for every bracket
        var bracketCursor = 0
        fun findCloseBracket(from: Int): Int? {
            bracketCursor = max(bracketCursor, from)
            while (bracketCursor < chars.length && chars[bracketCursor] != ']') bracketCursor++
            return if (bracketCursor < chars.length) bracketCursor else null
        }

        while (index < chars.length) {
            val character = chars[index]
            if (character == '\\' && index + 1 < chars.length) {
                buffer.append(chars[index + 1])
                index += 2
                continue
            }
            if (character == '`') {
                val end = find('`', index + 1)
                if (end != null && end > index + 1) {
                    flush()
                    runs.add(Inline(chars.substring(index + 1, end), code = true))
                    index = end + 1
                    continue
                }
            }
            if (character == '*' || character == '_') {
                val doubled = index + 1 < chars.length && chars[index + 1] == character
                val marker = character.toString().repeat(if (doubled) 2 else 1)
                // commonmark: no intraword emphasis
                val wordBoundary = character == '*' || index == 0 || !chars[index - 1].isLetter()
                val end = if (wordBoundary) findMarker(marker, index + marker.length) else null
                if (end != null && end > index + marker.length &&
                    (character == '*' || end + marker.length >= chars.length || !chars[end + marker.length].isLetter())
                ) {
                    val inner = chars.substring(index + marker.length, end)
                    flush()
                    runs.addAll(parseInline(inner).map { if (doubled) it.copy(bold = true) else it.copy(italic = true) })
                    index = end + marker.length
                    continue
                }
            }
            if (character == '[') {
                val close = findCloseBracket(index + 1)
                val paren = if (close != null && close + 1 < chars.length && chars[close + 1] == '(') find(')', close + 2) else null
                if (close != null && paren != null) {
                    val label = chars.substring(index + 1, close)
                    val url = chars.substring(close + 2, paren)
                    flush()
                    runs.addAll(parseInline(label).map { it.copy(link = url) })
                    index = paren + 1
                    continue
                }
            }
            buffer.append(character)
            index++
        }
        flush()
        return runs
    }

    fun html(blocks: List<Block>, standalone: Boolean, style: Style? = null): String {
        val body = blocks.joinToString("\n") { block ->
            when (block) {
                is Block.Heading -> "<h${block.level}>${inlineHtml(block.runs)}</h${block.level}>"
                is Block.Paragraph -> "<p>${inlineHtml(block.runs)}</p>"
                is Block.Quote -> "<blockquote><p>${inlineHtml(block.runs)}</p></blockquote>"
                is Block.Code -> "<pre><code>${escapeHtml(block.body)}</code></pre>"
                Block.Rule -> "<hr>"
                is Block.Bullets -> (if (block.ordered) "<ol>\n" else "<ul>\n") +
                    block.items.joinToString("\n") { "<li>${inlineHtml(it)}</li>" } +
                    if (block.ordered) "\n</ol>" else "\n</ul>"
            }
        }
        if (!standalone) return body
        val css = style?.let {
            "<style>body { font-family: ${it.cssFamily}; line-height: ${it.lineHeight}; color: ${it.text.css}; background: ${it.background.css}; max-width: 40em; margin: 2em auto; padding: 0 1em; }</style>"
        }.orEmpty()
        return "<!DOCTYPE html>\n<html>\n<head><meta charset=\"utf-8\"><title>Document</title>$css</head>\n<body>\n$body\n</body>\n</html>"
    }

    private fun inlineHtml(runs: List<Inline>) = runs.joinToString("") { run ->
        var out = escapeHtml(run.text)
        if (run.code) out = "<code>$out</code>"
        if (run.bold) out = "<strong>$out</strong>"
        if (run.italic) out = "<em>$out</em>"
        if (run.link != null) out = "<a href=\"${escapeHtml(run.link)}\">$out</a>"
        out
    }

    private fun escapeHtml(source: String) = source.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /**
     * Rich Text Format at a 12 pt body, set as DocConvert.attributed sets it.
     * ponytail: no page background colour (Cocoa writes it as a background shape); add it if a reader needs it
     */
    fun rtf(blocks: List<Block>, style: Style? = null, bodySize: Double = 12.0): String {
        fun twips(points: Double) = (points * 20).roundToInt()
        fun channel(v: Double) = (v.coerceIn(0.0, 1.0) * 255).roundToInt()
        val ink = style?.text ?: Rgb(0.0, 0.0, 0.0)
        val paper = style?.background ?: Rgb(1.0, 1.0, 1.0)
        val faint = Rgb((ink.red + paper.red) / 2, (ink.green + paper.green) / 2, (ink.blue + paper.blue) / 2)
        val link = Rgb(0.0, 0.48, 1.0)
        val face = when (style?.family) {
            Style.Family.Serif -> "\\froman Times New Roman"
            Style.Family.Mono -> "\\fmodern Courier New"
            else -> "\\fswiss Helvetica"
        }
        val out = StringBuilder("{\\rtf1\\ansi\\ansicpg1252\\deff0{\\fonttbl{\\f0$face;}{\\f1\\fmodern Courier New;}}")
        out.append("{\\colortbl;")
        for (c in listOf(ink, faint, link)) out.append("\\red${channel(c.red)}\\green${channel(c.green)}\\blue${channel(c.blue)};")
        out.append("}\n")
        val spacing = if (style == null) "" else "\\sl${(240 * style.lineHeight).roundToInt()}\\slmult1"

        fun escape(text: String) = buildString {
            for (ch in text) when {
                ch == '\\' || ch == '{' || ch == '}' -> append('\\').append(ch)
                ch == '\t' -> append("\\tab ")
                ch == '\n' -> append("\\line ")
                ch.code < 128 -> append(ch)
                else -> append("\\u${ch.code.toShort()}?")
            }
        }

        fun runs(items: List<Inline>, size: Double, bold: Boolean, italic: Boolean) = buildString {
            for (run in items) {
                val font = if (run.code) "\\f1\\fs${(size * 0.9 * 2).roundToInt()}" else "\\f0\\fs${(size * 2).roundToInt()}"
                val weight = if (run.bold || bold) "\\b" else "\\b0"
                val slant = if (!run.code && (run.italic || italic)) "\\i" else "\\i0"
                val body = "{$font$weight$slant\\cf${if (run.link != null) 3 else 1} ${escape(run.text)}}"
                append(if (run.link != null) "{\\field{\\*\\fldinst{HYPERLINK \"${escape(run.link.replace("\"", "%22"))}\"}}{\\fldrslt$body}}" else body)
            }
        }

        fun paragraph(before: Double, after: Double, left: Double, first: Double, content: String) {
            out.append("\\pard\\sb${twips(before)}\\sa${twips(after)}\\li${twips(left)}\\fi${twips(first)}$spacing $content\\par\n")
        }

        for (block in blocks) {
            val after = bodySize * 0.8
            when (block) {
                is Block.Heading -> {
                    val size = bodySize * headingSizes[block.level.coerceIn(1, 6) - 1]
                    paragraph(bodySize, after, 0.0, 0.0, runs(block.runs, size, bold = true, italic = false))
                }
                is Block.Paragraph -> paragraph(0.0, after, 0.0, 0.0, runs(block.runs, bodySize, bold = false, italic = false))
                is Block.Quote -> paragraph(0.0, after, bodySize * 2, 0.0, runs(block.runs, bodySize, bold = false, italic = true))
                is Block.Bullets -> block.items.forEachIndexed { offset, item ->
                    val marker = if (block.ordered) "${offset + 1}. " else "•\t"
                    paragraph(0.0, bodySize * 0.3, bodySize * 2, -bodySize, runs(listOf(Inline(marker)) + item, bodySize, bold = false, italic = false))
                }
                is Block.Code -> paragraph(0.0, after, bodySize, 0.0, "{\\f1\\fs${(bodySize * 0.9 * 2).roundToInt()}\\cf1 ${escape(block.body)}}")
                Block.Rule -> paragraph(0.0, after, 0.0, 0.0, "{\\f0\\fs${(bodySize * 2).roundToInt()}\\cf2 ${escape("─".repeat(5))}}")
            }
        }
        out.append("}")
        return out.toString()
    }
}

/** dt-ios TextEditorViewModel's counts, ManuscriptView's block spans and focus, TextEditorHighlighter's marks. */
object TextEditorEngine {
    /** Unstyled beyond this length. */
    const val STYLE_LIMIT = 60_000

    /** Longer text parses its blocks after typing settles. */
    const val LIVE_LIMIT = 30_000

    val editorHeadingScale = listOf(1.6, 1.42, 1.28, 1.16, 1.08, 1.0)

    /** A block and the UTF-16 range of its source lines. */
    data class Span(val block: TextDoc.Block, val start: Int, val length: Int) {
        val end get() = start + length
    }

    enum class Look { Muted, Primary, Code, Bold, Italic, Quote }

    /** A styled range; `level` is set for a heading's whole line. */
    data class Mark(val start: Int, val end: Int, val look: Look? = null, val level: Int = 0)

    fun words(text: String): Int {
        var count = 0
        var inWord = false
        for (ch in text) {
            if (ch.isWhitespace()) inWord = false
            else if (!inWord) {
                inWord = true
                count++
            }
        }
        return count
    }

    fun characters(text: String): Int {
        val breaks = BreakIterator.getCharacterInstance().apply { setText(text) }
        var count = 0
        breaks.first()
        while (breaks.next() != BreakIterator.DONE) count++
        return count
    }

    // as NSString: CR LF counts as two separators
    fun lines(text: String) = if (text.isEmpty()) 0 else 1 + text.count { it in "\n\u000B\u000C\r\u0085  " }

    // 200 words per minute
    fun readingMinutes(text: String): Int {
        val words = words(text)
        return if (words == 0) 0 else max(1, ceil(words / 200.0).toInt())
    }

    fun spans(text: String): List<Span> {
        val starts = ArrayList<Int>().apply { add(0) }
        text.forEachIndexed { offset, ch -> if (ch == '\n') starts.add(offset + 1) }
        starts.add(text.length + 1)
        return TextDoc.markdownBlocks(text).map { (block, lines) ->
            val start = starts[lines.first]
            val end = starts[min(lines.last + 1, starts.size - 1)] - 1
            Span(block, start, max(0, end - start))
        }
    }

    /** The last span starting at or before `location`. */
    fun block(location: Int, spans: List<Span>): Int? {
        var low = 0
        var high = spans.size
        while (low < high) {
            val middle = (low + high) / 2
            if (spans[middle].start <= location) low = middle + 1 else high = middle
        }
        return if (low == 0) null else low - 1
    }

    /** The sentence holding the caret within `range`, trailing space left out. */
    fun sentence(caret: Int, text: String, start: Int, end: Int): IntRange? {
        val breaks = BreakIterator.getSentenceInstance().apply { setText(text.substring(start, end)) }
        var found: IntRange? = null
        var from = breaks.first()
        var to = breaks.next()
        while (to != BreakIterator.DONE) {
            val enclosing = (start + from)..(start + to)
            if (caret in enclosing) {
                var last = start + to
                while (last > start + from && text[last - 1].isWhitespace()) last--
                found = (start + from) until last
                if (caret < start + to) break
            }
            from = to
            to = breaks.next()
        }
        return found
    }

    /** The end of what changed from `old` to `new`, for the caret after a whole replace. */
    fun changeEnd(old: String, new: String): Int {
        val prefix = old.commonPrefixWith(new).length
        val suffix = min(old.length, new.length).minus(prefix).let { room -> old.reversed().commonPrefixWith(new.reversed()).length.coerceAtMost(room) }
        return new.length - suffix
    }

    private val heading = Regex("(?m)^(#{1,6})[ \\t]+(.*)$")
    private val fenced = Regex("(?ms)^(```|~~~).*?^\\1[ \\t]*$")
    private val inlineCode = Regex("`[^`\\n]+`")
    private val strong = Regex("(\\*\\*|__)(.+?)\\1")
    private val emphasis = Regex("(?<![*_\\w])([*_])([^*_\\n]+)\\1(?![*_\\w])")
    private val quote = Regex("(?m)^>[ \\t]?.*$")
    private val marker = Regex("(?m)^[ \\t]*([-*+]|\\d+[.)])[ \\t]")
    private val link = Regex("\\[([^\\]\\n]*)\\]\\(([^)\\n]*)\\)")
    private val rule = Regex("(?m)^([*_-])\\1{2,}[ \\t]*$")

    /** TextEditorHighlighter: Markdown syntax marks, in the order they apply. */
    fun highlight(text: String): List<Mark> {
        if (text.length > STYLE_LIMIT) return emptyList()
        val marks = ArrayList<Mark>()
        fun MatchResult.group(index: Int) = groups[index]?.range
        for (match in heading.findAll(text)) {
            marks.add(Mark(match.range.first, match.range.last + 1, level = match.groupValues[1].length))
            match.group(1)?.let { marks.add(Mark(it.first, it.last + 1, Look.Muted)) }
        }
        for (match in quote.findAll(text)) marks.add(Mark(match.range.first, match.range.last + 1, Look.Quote))
        for (match in marker.findAll(text)) match.group(1)?.let { marks.add(Mark(it.first, it.last + 1, Look.Primary)) }
        for (match in rule.findAll(text)) marks.add(Mark(match.range.first, match.range.last + 1, Look.Muted))
        for (match in fenced.findAll(text) + inlineCode.findAll(text)) marks.add(Mark(match.range.first, match.range.last + 1, Look.Code))
        for ((pattern, look) in listOf(strong to Look.Bold, emphasis to Look.Italic)) {
            for (match in pattern.findAll(text)) {
                match.group(2)?.let { marks.add(Mark(it.first, it.last + 1, look)) }
                val width = match.groupValues[1].length
                marks.add(Mark(match.range.first, match.range.first + width, Look.Muted))
                marks.add(Mark(match.range.last + 1 - width, match.range.last + 1, Look.Muted))
            }
        }
        for (match in link.findAll(text)) {
            match.group(1)?.let { marks.add(Mark(it.first, it.last + 1, Look.Primary)) }
            match.group(2)?.let { marks.add(Mark(it.first, it.last + 1, Look.Muted)) }
        }
        return marks
    }
}
