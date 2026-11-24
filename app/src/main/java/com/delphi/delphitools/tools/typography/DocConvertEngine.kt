package com.delphi.delphitools.tools.typography

enum class DocFormat(val label: String, val ext: String, val readable: Boolean) {
    Markdown("Markdown", "md", true),
    Html("HTML", "html", true),
    Rtf("Rich Text", "rtf", false),
    Plain("Plain text", "txt", true),
    Pdf("PDF", "pdf", false);

    companion object {
        val inputs = entries.filter { it.readable }
        val outputs = entries.toList()
    }
}

data class DocInline(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
)

sealed interface DocBlock {
    data class Heading(val level: Int, val runs: List<DocInline>) : DocBlock
    data class Paragraph(val runs: List<DocInline>) : DocBlock
    data class Bullets(val ordered: Boolean, val items: List<List<DocInline>>) : DocBlock
    data class Quote(val runs: List<DocInline>) : DocBlock
    data class Code(val body: String) : DocBlock
    data object Rule : DocBlock

    val mark: String
        get() = when (this) {
            is Heading -> "H$level"
            is Paragraph -> "¶"
            is Bullets -> if (ordered) "1." else "•"
            is Quote -> "“"
            is Code -> "{ }"
            Rule -> "—"
        }

    val text: String
        get() = when (this) {
            is Heading -> runs.joinToString("") { it.text }
            is Paragraph -> runs.joinToString("") { it.text }
            is Quote -> runs.joinToString("") { it.text }
            is Bullets -> items.joinToString(" ") { item -> item.joinToString("") { it.text } }
            is Code -> body
            Rule -> ""
        }

    val words: Int get() = text.split(Regex("\\s+")).count { it.isNotEmpty() }
}

/** One run of a styled paragraph, sized in points. */
data class DocRun(
    val text: String,
    val size: Float,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val mono: Boolean = false,
    val link: String? = null,
)

/** dt-ios DocConvert.attributed as plain data: one paragraph's runs, indents and spacing in points. */
data class DocParagraph(
    val runs: List<DocRun>,
    val firstIndent: Float = 0f,
    val headIndent: Float = 0f,
    val spacingBefore: Float = 0f,
    val spacingAfter: Float = 0f,
    val lineSpacing: Float = 0f,
)

enum class DocElement(val mark: String) {
    Heading("H"),
    Paragraph("¶"),
    Bullets("•"),
    Numbered("1."),
    Quote("“"),
    Code("{ }"),
    Rule("—"),
    Bold("B"),
    Italic("I"),
    CodeSpan("` `"),
    Link("↗"),
}

data class DocLedgerRow(val element: DocElement, val count: Int, val example: DocBlock)

/**
 * dt-ios DocConvert: reads Markdown, HTML and plain text into blocks; writes Markdown, HTML, plain text and RTF.
 * PDF is drawn from [attributed] by the screen, which has the platform's text layout.
 */
object DocConvertEngine {
    fun parse(source: String, format: DocFormat): List<DocBlock> = when (format) {
        DocFormat.Markdown -> parseMarkdown(source)
        DocFormat.Html -> parseHtml(source)
        else -> parsePlain(source)
    }

    /** The text of a text format, or RTF; null for PDF. */
    fun render(blocks: List<DocBlock>, format: DocFormat, standalone: Boolean): String? = when (format) {
        DocFormat.Markdown -> markdown(blocks)
        DocFormat.Html -> html(blocks, standalone)
        DocFormat.Plain -> plain(blocks)
        DocFormat.Rtf -> rtf(attributed(blocks))
        DocFormat.Pdf -> null
    }

    fun format(filename: String): DocFormat? = when (filename.substringAfterLast('.', "").lowercase()) {
        "md", "markdown", "mdown", "mkd" -> DocFormat.Markdown
        "html", "htm", "xhtml" -> DocFormat.Html
        "txt", "text" -> DocFormat.Plain
        else -> null
    }

    private val htmlStart = Regex("^<(\\?xml|!doctype html|html|head|body|div|p|h[1-6]|article|section|table|ul|ol)[\\s>/]", RegexOption.IGNORE_CASE)
    private val markdownLine = Regex("^(#{1,6}\\s|[-*+]\\s|>\\s|```|\\d+\\.\\s)", RegexOption.MULTILINE)

    /** Sniffed from the opening of the document only. */
    fun formatOf(source: String): DocFormat {
        val text = source.take(4096).replace("\uFEFF", "").trim()
        if (text.isEmpty()) return DocFormat.Markdown
        if (htmlStart.containsMatchIn(text)) return DocFormat.Html
        if (markdownLine.containsMatchIn(text)) return DocFormat.Markdown
        return DocFormat.Plain
    }

    // Markdown reader

    fun parseMarkdown(source: String): List<DocBlock> = markdownBlocks(source).map { it.first }

    fun markdownBlocks(source: String): List<Pair<DocBlock, IntRange>> {
        val blocks = mutableListOf<Pair<DocBlock, IntRange>>()
        val paragraph = mutableListOf<String>()
        var paragraphStart = 0
        val lines = source.replace("\r\n", "\n").split("\n")
        var index = 0

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            blocks += DocBlock.Paragraph(parseInline(paragraph.joinToString(" "))) to (paragraphStart until index)
            paragraph.clear()
        }

        while (index < lines.size) {
            val line = lines[index].trimSpaces()
            if (line.isEmpty()) {
                flushParagraph()
                index++
                continue
            }
            val start = index
            if (line.startsWith("```") || line.startsWith("~~~")) {
                flushParagraph()
                val fence = line.take(3)
                val body = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trimSpaces().startsWith(fence)) {
                    body += lines[index]
                    index++
                }
                if (index < lines.size) index++
                blocks += DocBlock.Code(body.joinToString("\n")) to (start until index)
                continue
            }
            val heading = headingParts(line)
            if (heading != null) {
                flushParagraph()
                index++
                blocks += DocBlock.Heading(heading.first, parseInline(heading.second)) to (start until index)
                continue
            }
            val setext = if (paragraph.isNotEmpty()) setextLevel(line) else null
            if (setext != null) {
                val runs = parseInline(paragraph.joinToString(" "))
                paragraph.clear()
                index++
                blocks += DocBlock.Heading(setext, runs) to (paragraphStart until index)
                continue
            }
            if (isRule(line)) {
                flushParagraph()
                index++
                blocks += DocBlock.Rule to (start until index)
                continue
            }
            if (line.startsWith(">")) {
                flushParagraph()
                val body = mutableListOf<String>()
                while (index < lines.size) {
                    val quoted = lines[index].trimSpaces()
                    if (!quoted.startsWith(">")) break
                    body += quoted.drop(1).trimSpaces()
                    index++
                }
                blocks += DocBlock.Quote(parseInline(body.joinToString(" "))) to (start until index)
                continue
            }
            if (bulletBody(line) != null || orderedBody(line) != null) {
                val ordered = bulletBody(line) == null
                flushParagraph()
                val items = mutableListOf<List<DocInline>>()
                while (index < lines.size) {
                    val item = lines[index].trimSpaces()
                    val body = (if (ordered) orderedBody(item) else bulletBody(item)) ?: break
                    items += parseInline(body)
                    index++
                }
                blocks += DocBlock.Bullets(ordered, items) to (start until index)
                continue
            }
            if (paragraph.isEmpty()) paragraphStart = index
            paragraph += line
            index++
        }
        flushParagraph()
        return blocks
    }

    private fun String.trimSpaces() = trim { it.isWhitespace() && it != '\n' }

    private fun headingParts(line: String): Pair<Int, String>? {
        val hashes = line.takeWhile { it == '#' }.length
        if (hashes !in 1..6) return null
        val rest = line.drop(hashes)
        if (!rest.startsWith(" ") && rest.isNotEmpty()) return null
        return hashes to rest.trimSpaces().trimEnd('#').trimSpaces()
    }

    // commonmark setext underline
    private fun setextLevel(line: String): Int? {
        val first = line.firstOrNull() ?: return null
        if (line.length < 2 || (first != '=' && first != '-') || line.any { it != first }) return null
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

    fun parseInline(source: String): List<DocInline> {
        val runs = mutableListOf<DocInline>()
        val buffer = StringBuilder()
        val chars = source
        var index = 0
        fun flush() {
            if (buffer.isEmpty()) return
            runs += DocInline(buffer.toString())
            buffer.clear()
        }
        fun find(needle: Char, from: Int) = chars.indexOf(needle, from).takeIf { it >= 0 }
        fun findMarker(marker: String, from: Int) = chars.indexOf(marker, from).takeIf { it >= 0 }
        // a forward-only cursor: `[` with no `]` after it would otherwise rescan the rest for every bracket
        var bracketCursor = 0
        fun findCloseBracket(from: Int): Int? {
            bracketCursor = maxOf(bracketCursor, from)
            while (bracketCursor < chars.length && chars[bracketCursor] != ']') bracketCursor++
            return bracketCursor.takeIf { it < chars.length }
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
                    runs += DocInline(chars.substring(index + 1, end), code = true)
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
                    flush()
                    runs += parseInline(chars.substring(index + marker.length, end)).map { if (doubled) it.copy(bold = true) else it.copy(italic = true) }
                    index = end + marker.length
                    continue
                }
            }
            if (character == '[') {
                val close = findCloseBracket(index + 1)
                if (close != null && close + 1 < chars.length && chars[close + 1] == '(') {
                    val paren = find(')', close + 2)
                    if (paren != null) {
                        val url = chars.substring(close + 2, paren)
                        flush()
                        runs += parseInline(chars.substring(index + 1, close)).map { it.copy(link = url) }
                        index = paren + 1
                        continue
                    }
                }
            }
            buffer.append(character)
            index++
        }
        flush()
        return runs
    }

    // Plain text reader

    fun parsePlain(source: String): List<DocBlock> =
        source.replace("\r\n", "\n").split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
            .map { DocBlock.Paragraph(listOf(DocInline(it.replace("\n", " ")))) }

    // HTML reader

    private val dotAll = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val stripped = listOf(
        Regex("<!--.*?-->", dotAll),
        Regex("<script\\b[^>]*>.*?</script>", dotAll),
        Regex("<style\\b[^>]*>.*?</style>", dotAll),
        Regex("<head\\b[^>]*>.*?</head>", dotAll),
    )
    private val spaces = Regex("\\s+")
    private val href = Regex("href\\s*=\\s*[\"']([^\"']*)[\"']")

    fun parseHtml(source: String): List<DocBlock> {
        val cleaned = stripped.fold(source) { text, pattern -> pattern.replace(text, "") }
        val blocks = mutableListOf<DocBlock>()
        var runs = mutableListOf<DocInline>()
        val text = StringBuilder()
        var bold = 0
        var italic = 0
        var code = 0
        var link: String? = null
        var pending: String? = null
        var pendingLevel = 1
        var listItems = mutableListOf<List<DocInline>>()
        var listOrdered = false
        var inList = false

        fun flushText() {
            if (text.isEmpty()) return
            runs += DocInline(text.toString(), bold > 0, italic > 0, code > 0, link)
            text.clear()
        }

        fun closeBlock() {
            flushText()
            val content = runs
            runs = mutableListOf()
            val empty = content.all { it.text.isBlank() }
            val kind = pending
            pending = null
            if (empty) return
            when (kind) {
                "li" -> listItems += content
                "pre" -> blocks += DocBlock.Code(content.joinToString("") { it.text })
                "blockquote" -> blocks += DocBlock.Quote(content)
                "h" -> blocks += DocBlock.Heading(pendingLevel, content)
                else -> blocks += DocBlock.Paragraph(content)
            }
        }

        fun closeList() {
            closeBlock()
            if (listItems.isNotEmpty()) blocks += DocBlock.Bullets(listOrdered, listItems)
            listItems = mutableListOf()
            inList = false
        }

        var index = 0
        while (index < cleaned.length) {
            if (cleaned[index] != '<') {
                val end = cleaned.indexOf('<', index).let { if (it < 0) cleaned.length else it }
                val decoded = decodeEntities(cleaned.substring(index, end))
                index = end
                val collapsed = if (pending == "pre") decoded else spaces.replace(decoded, " ")
                if (collapsed.isBlank()) {
                    // keeps inter-tag word gaps
                    if (runs.isNotEmpty() || text.isNotEmpty()) text.append(' ')
                } else {
                    text.append(collapsed)
                }
                continue
            }
            val close = cleaned.indexOf('>', index)
            if (close < 0) break
            val tag = cleaned.substring(index + 1, close)
            index = close + 1
            val closing = tag.startsWith("/")
            val body = if (closing) tag.drop(1) else tag
            val name = body.takeWhile { !it.isWhitespace() && it != '/' }.lowercase()
            when (name) {
                "p", "div", "blockquote", "pre", "li", "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    closeBlock()
                    if (!closing) {
                        val level = if (name.startsWith("h")) name.drop(1).toIntOrNull() else null
                        if (level != null) {
                            pending = "h"
                            pendingLevel = level
                        } else {
                            pending = if (name == "div") "p" else name
                        }
                    }
                }
                "ul", "ol" -> if (closing) {
                    closeList()
                } else {
                    if (inList) closeList()
                    closeBlock()
                    inList = true
                    listOrdered = name == "ol"
                    listItems = mutableListOf()
                }
                "hr" -> if (!closing) {
                    closeBlock()
                    blocks += DocBlock.Rule
                }
                "br" -> text.append(' ')
                "strong", "b" -> {
                    flushText()
                    bold = maxOf(0, bold + if (closing) -1 else 1)
                }
                "em", "i" -> {
                    flushText()
                    italic = maxOf(0, italic + if (closing) -1 else 1)
                }
                "code" -> {
                    flushText()
                    code = maxOf(0, code + if (closing) -1 else 1)
                }
                "a" -> {
                    flushText()
                    if (closing) link = null else href.find(body)?.let { link = it.groupValues[1] }
                }
            }
        }
        // unconditional: `<li>` outside any list still has items to flush
        closeList()
        return blocks
    }

    private val entities = listOf(
        "&nbsp;" to "\u00A0", "&mdash;" to "—", "&ndash;" to "–",
        "&hellip;" to "…", "&rsquo;" to "’", "&lsquo;" to "‘",
        "&ldquo;" to "“", "&rdquo;" to "”", "&quot;" to "\"",
        "&apos;" to "'", "&#39;" to "'", "&lt;" to "<", "&gt;" to ">",
    )
    private val decimal = Regex("&#(\\d+);")
    private val hex = Regex("&#[xX]([0-9a-fA-F]+);")

    private fun scalar(code: Long?, fallback: String): String =
        if (code == null || code > 0x10FFFF || code in 0xD800..0xDFFF) fallback else String(Character.toChars(code.toInt()))

    internal fun decodeEntities(source: String): String {
        if ('&' !in source) return source
        var out = entities.fold(source) { text, (entity, value) -> text.replace(entity, value) }
        out = decimal.replace(out) { scalar(it.groupValues[1].toLongOrNull(), it.value) }
        out = hex.replace(out) { scalar(it.groupValues[1].toLongOrNull(16), it.value) }
        return out.replace("&amp;", "&")
    }

    // Writers

    fun markdown(blocks: List<DocBlock>): String {
        val rendered = blocks.map { block ->
            when (block) {
                is DocBlock.Heading -> "#".repeat(block.level) + " " + inlineMarkdown(block.runs)
                is DocBlock.Paragraph -> inlineMarkdown(block.runs)
                is DocBlock.Quote -> "> " + inlineMarkdown(block.runs)
                is DocBlock.Code -> "```\n" + block.body + "\n```"
                DocBlock.Rule -> "---"
                is DocBlock.Bullets -> block.items.withIndex().joinToString("\n") { (offset, item) ->
                    (if (block.ordered) "${offset + 1}. " else "- ") + inlineMarkdown(item)
                }
            }
        }
        return rendered.joinToString("\n\n") + if (rendered.isEmpty()) "" else "\n"
    }

    private fun inlineMarkdown(runs: List<DocInline>) = runs.joinToString("") { run ->
        var out = run.text
        if (run.code) out = "`$out`"
        if (run.bold) out = "**$out**"
        if (run.italic) out = "*$out*"
        if (run.link != null) out = "[$out](${run.link})"
        out
    }

    fun html(blocks: List<DocBlock>, standalone: Boolean): String {
        val body = blocks.joinToString("\n") { block ->
            when (block) {
                is DocBlock.Heading -> "<h${block.level}>${inlineHtml(block.runs)}</h${block.level}>"
                is DocBlock.Paragraph -> "<p>${inlineHtml(block.runs)}</p>"
                is DocBlock.Quote -> "<blockquote><p>${inlineHtml(block.runs)}</p></blockquote>"
                is DocBlock.Code -> "<pre><code>${escapeHtml(block.body)}</code></pre>"
                DocBlock.Rule -> "<hr>"
                is DocBlock.Bullets -> (if (block.ordered) "<ol>\n" else "<ul>\n") +
                    block.items.joinToString("\n") { "<li>${inlineHtml(it)}</li>" } +
                    (if (block.ordered) "\n</ol>" else "\n</ul>")
            }
        }
        if (!standalone) return body
        return "<!DOCTYPE html>\n<html>\n<head><meta charset=\"utf-8\"><title>Document</title></head>\n<body>\n$body\n</body>\n</html>"
    }

    private fun inlineHtml(runs: List<DocInline>) = runs.joinToString("") { run ->
        var out = escapeHtml(run.text)
        if (run.code) out = "<code>$out</code>"
        if (run.bold) out = "<strong>$out</strong>"
        if (run.italic) out = "<em>$out</em>"
        if (run.link != null) out = "<a href=\"${escapeHtml(run.link)}\">$out</a>"
        out
    }

    private fun escapeHtml(source: String) =
        source.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun plain(blocks: List<DocBlock>): String {
        val rendered = blocks.map { block ->
            when (block) {
                is DocBlock.Heading, is DocBlock.Paragraph, is DocBlock.Quote -> block.text
                is DocBlock.Code -> block.body
                DocBlock.Rule -> "---"
                is DocBlock.Bullets -> block.items.withIndex().joinToString("\n") { (offset, item) ->
                    (if (block.ordered) "${offset + 1}. " else "• ") + item.joinToString("") { it.text }
                }
            }
        }
        return rendered.joinToString("\n\n") + if (rendered.isEmpty()) "" else "\n"
    }

    // Rich text

    private val headingSizes = listOf(2.0f, 1.6f, 1.35f, 1.2f, 1.1f, 1.0f)

    fun attributed(blocks: List<DocBlock>, bodySize: Float = 12f): List<DocParagraph> {
        val out = mutableListOf<DocParagraph>()
        val spacing = bodySize * 0.8f
        val lineSpacing = bodySize * 0.2f
        fun runs(inline: List<DocInline>, size: Float, bold: Boolean = false, italic: Boolean = false) = inline.map { run ->
            if (run.code) {
                DocRun(run.text, size * 0.9f, mono = true, link = run.link)
            } else {
                DocRun(run.text, size, bold = run.bold || bold, italic = run.italic || italic, link = run.link)
            }
        }
        for (block in blocks) {
            out += when (block) {
                is DocBlock.Heading -> DocParagraph(
                    runs(block.runs, bodySize * headingSizes[block.level.coerceIn(1, 6) - 1], bold = true),
                    spacingBefore = bodySize,
                    spacingAfter = spacing,
                    lineSpacing = lineSpacing,
                )
                is DocBlock.Paragraph -> DocParagraph(runs(block.runs, bodySize), spacingAfter = spacing, lineSpacing = lineSpacing)
                is DocBlock.Quote -> DocParagraph(
                    runs(block.runs, bodySize, italic = true),
                    firstIndent = bodySize * 2,
                    headIndent = bodySize * 2,
                    spacingAfter = spacing,
                    lineSpacing = lineSpacing,
                )
                is DocBlock.Bullets -> {
                    block.items.forEachIndexed { offset, item ->
                        val marker = if (block.ordered) "${offset + 1}. " else "•\t"
                        out += DocParagraph(
                            listOf(DocRun(marker, bodySize)) + runs(item, bodySize),
                            firstIndent = bodySize,
                            headIndent = bodySize * 2,
                            spacingAfter = bodySize * 0.3f,
                            lineSpacing = lineSpacing,
                        )
                    }
                    continue
                }
                is DocBlock.Code -> DocParagraph(
                    listOf(DocRun(block.body, bodySize * 0.9f, mono = true)),
                    firstIndent = bodySize,
                    headIndent = bodySize,
                    spacingAfter = spacing,
                    lineSpacing = lineSpacing,
                )
                DocBlock.Rule -> DocParagraph(listOf(DocRun("─────", bodySize)), spacingAfter = spacing, lineSpacing = lineSpacing)
            }
        }
        return out
    }

    /** RTF 1.x: Helvetica and Courier, links as HYPERLINK fields, text outside ASCII as \u escapes. */
    fun rtf(paragraphs: List<DocParagraph>): String {
        val out = StringBuilder("{\\rtf1\\ansi\\ansicpg1252\\deff0\n{\\fonttbl{\\f0\\fswiss Helvetica;}{\\f1\\fmodern Courier;}}\n{\\colortbl;\\red0\\green122\\blue255;}\n")
        fun twips(points: Float) = Math.round(points * 20)
        for (paragraph in paragraphs) {
            out.append("\\pard")
            out.append("\\fi").append(twips(paragraph.firstIndent - paragraph.headIndent))
            out.append("\\li").append(twips(paragraph.headIndent))
            out.append("\\sb").append(twips(paragraph.spacingBefore))
            out.append("\\sa").append(twips(paragraph.spacingAfter))
            if (paragraph.headIndent > 0) out.append("\\tx").append(twips(paragraph.headIndent))
            out.append(' ')
            for (run in paragraph.runs) {
                val format = StringBuilder(if (run.mono) "\\f1" else "\\f0")
                format.append("\\fs").append(Math.round(run.size * 2))
                if (run.bold) format.append("\\b")
                if (run.italic) format.append("\\i")
                val text = rtfEscape(run.text)
                if (run.link != null) {
                    out.append("{\\field{\\*\\fldinst{HYPERLINK \"").append(rtfEscape(run.link.replace("\"", "%22"))).append("\"}}{\\fldrslt{")
                        .append(format).append("\\cf1\\ul ").append(text).append("}}}")
                } else {
                    out.append('{').append(format).append(' ').append(text).append('}')
                }
            }
            out.append("\\par\n")
        }
        return out.append('}').toString()
    }

    private fun rtfEscape(text: String): String {
        val out = StringBuilder()
        for (char in text) {
            when {
                char == '\\' || char == '{' || char == '}' -> out.append('\\').append(char)
                char == '\n' -> out.append("\\line ")
                char == '\t' -> out.append("\\tab ")
                char.code < 0x80 -> out.append(char)
                // rtf \u takes a signed 16-bit value
                else -> out.append("\\u").append(char.code.toShort().toInt()).append('?')
            }
        }
        return out.toString()
    }

    // Ledger

    fun ledger(blocks: List<DocBlock>): List<DocLedgerRow> {
        val counts = mutableMapOf<DocElement, Int>()
        val examples = mutableMapOf<DocElement, DocBlock>()
        fun add(element: DocElement, example: () -> DocBlock) {
            counts[element] = (counts[element] ?: 0) + 1
            if (element !in examples) examples[element] = example()
        }
        fun inline(runs: List<DocInline>) = runs.forEach { run ->
            if (run.bold) add(DocElement.Bold) { DocBlock.Paragraph(listOf(run)) }
            if (run.italic) add(DocElement.Italic) { DocBlock.Paragraph(listOf(run)) }
            if (run.code) add(DocElement.CodeSpan) { DocBlock.Paragraph(listOf(run)) }
            if (run.link != null) add(DocElement.Link) { DocBlock.Paragraph(listOf(run)) }
        }
        for (block in blocks) {
            when (block) {
                is DocBlock.Heading -> add(DocElement.Heading) { block }.also { inline(block.runs) }
                is DocBlock.Paragraph -> add(DocElement.Paragraph) { block }.also { inline(block.runs) }
                is DocBlock.Quote -> add(DocElement.Quote) { block }.also { inline(block.runs) }
                is DocBlock.Bullets -> {
                    add(if (block.ordered) DocElement.Numbered else DocElement.Bullets) { DocBlock.Bullets(block.ordered, block.items.take(1)) }
                    block.items.forEach(::inline)
                }
                is DocBlock.Code -> add(DocElement.Code) { block }
                DocBlock.Rule -> add(DocElement.Rule) { block }
            }
        }
        return DocElement.entries.mapNotNull { element -> counts[element]?.let { DocLedgerRow(element, it, examples.getValue(element)) } }
    }

    fun isLost(element: DocElement, format: DocFormat) =
        format == DocFormat.Plain && element in setOf(DocElement.Heading, DocElement.Quote, DocElement.Bold, DocElement.Italic, DocElement.CodeSpan, DocElement.Link)

    /** A block written in a text format, on one line. */
    fun sample(block: DocBlock, format: DocFormat): String {
        val text = when (format) {
            DocFormat.Markdown -> markdown(listOf(block))
            DocFormat.Html -> html(listOf(block), standalone = false)
            else -> plain(listOf(block))
        }
        return text.split("\n").map { it.trimSpaces() }.filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** A block set as rich text, to its first line break, tabs as spaces. */
    fun richSample(block: DocBlock): List<DocRun> {
        val out = mutableListOf<DocRun>()
        for (run in attributed(listOf(block), bodySize = 11f).firstOrNull()?.runs ?: emptyList()) {
            val cut = run.text.indexOf('\n')
            out += run.copy(text = (if (cut >= 0) run.text.substring(0, cut) else run.text).replace('\t', ' '))
            if (cut >= 0) break
        }
        return out
    }
}
