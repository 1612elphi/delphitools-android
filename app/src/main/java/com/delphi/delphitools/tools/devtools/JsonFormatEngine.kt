package com.delphi.delphitools.tools.devtools

class JsonParseError(
    val message: String,
    val line: Int,
    /** 1-based, counted in code points. */
    val column: Int,
    /** The error line, windowed to [JsonFormatEngine.EXCERPT_RADIUS] code points each side. */
    val excerpt: String,
    /** Code points of [excerpt] before the error. */
    val caret: Int,
)

sealed interface JsonParsed {
    class Valid(val value: JsonValue) : JsonParsed
    class Invalid(val error: JsonParseError) : JsonParsed
}

enum class JsonIndent(val label: String, val unit: String?) {
    Two("2 spaces", "  "),
    Four("4 spaces", "    "),
    Tab("Tabs", "\t"),
    Minify("Minify", null),
}

/** [id] is the index path from the root, the collapse-state key. */
class JsonTreeRow(val id: String, val key: String?, val depth: Int, val display: String, val isContainer: Boolean, val isCollapsed: Boolean)

/** dt-core JSONFormatEngine: a parser with error positions, an indenting writer and the tree rows. */
object JsonFormatEngine {
    const val MAX_DEPTH = 128
    const val EXCERPT_RADIUS = 24

    private class Failure(val error: JsonParseError) : Exception()

    fun parse(source: String): JsonParsed = try {
        JsonParsed.Valid(Scanner(source).document())
    } catch (failure: Failure) {
        JsonParsed.Invalid(failure.error)
    }

    private class Scanner(val s: String) {
        var i = 0
        var depth = 0
        val current get() = s.getOrNull(i)

        fun fail(message: String, at: Int = i): Failure {
            val end = minOf(at, s.length)
            val lineStart = s.lastIndexOf('\n', end - 1) + 1
            val line = 1 + (0 until lineStart).count { s[it] == '\n' }
            val before = s.substring(lineStart, end)
            val lead = before.takeLastCodePoints(EXCERPT_RADIUS)
            val after = s.substring(end).takeWhile { it != '\n' && it != '\r' }.takeCodePoints(EXCERPT_RADIUS)
            val error = JsonParseError(message, line, before.codePointCount(0, before.length) + 1, lead + after, lead.codePointCount(0, lead.length))
            return Failure(error)
        }

        fun skipWhitespace() {
            while (current == ' ' || current == '\t' || current == '\n' || current == '\r') i++
        }

        fun document(): JsonValue {
            val value = value()
            skipWhitespace()
            if (current != null) throw fail("Trailing content")
            return value
        }

        fun value(): JsonValue {
            skipWhitespace()
            return when (val c = current ?: throw fail("Unexpected end")) {
                '{', '[' -> {
                    if (depth >= MAX_DEPTH) throw fail("Nesting too deep")
                    depth++
                    (if (c == '{') obj() else arr()).also { depth-- }
                }
                '"' -> JsonValue.Str(string())
                't' -> expect("true", JsonValue.Bool(true))
                'f' -> expect("false", JsonValue.Bool(false))
                'n' -> expect("null", JsonValue.Null)
                '-', in '0'..'9' -> JsonValue.Num(number())
                else -> throw fail("Unexpected token")
            }
        }

        fun expect(word: String, value: JsonValue): JsonValue {
            if (!s.startsWith(word, i)) throw fail("Unexpected token")
            i += word.length
            return value
        }

        fun endOfMembers(): Failure = if (current == null) fail("Unexpected end") else fail("Expected comma")

        fun obj(): JsonValue {
            i++
            val members = mutableListOf<Pair<String, JsonValue>>()
            skipWhitespace()
            if (current == '}') {
                i++
                return JsonValue.Obj(members)
            }
            while (true) {
                skipWhitespace()
                if (current != '"') throw fail("Expected key")
                val key = string()
                skipWhitespace()
                if (current != ':') throw fail("Expected colon")
                i++
                members += key to value()
                skipWhitespace()
                when (current) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return JsonValue.Obj(members)
                    }
                    else -> throw endOfMembers()
                }
            }
        }

        fun arr(): JsonValue {
            i++
            val items = mutableListOf<JsonValue>()
            skipWhitespace()
            if (current == ']') {
                i++
                return JsonValue.Arr(items)
            }
            while (true) {
                items += value()
                skipWhitespace()
                when (current) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return JsonValue.Arr(items)
                    }
                    else -> throw endOfMembers()
                }
            }
        }

        fun string(): String {
            val start = i
            i++
            val out = StringBuilder()
            while (true) {
                val c = current ?: throw fail("Unterminated string", start)
                when {
                    c == '"' -> {
                        i++
                        return out.toString()
                    }
                    c == '\\' -> {
                        i++
                        val escape = current ?: throw fail("Unterminated string", start)
                        i++
                        when (escape) {
                            '"', '\\', '/' -> out.append(escape)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> out.appendCodePoint(unicodeEscape())
                            else -> throw fail("Invalid escape", i - 2)
                        }
                    }
                    c < ' ' -> throw fail("Control character")
                    else -> {
                        out.append(c)
                        i++
                    }
                }
            }
        }

        fun unicodeEscape(): Int {
            val escapeStart = i - 2
            var code = hex4()
            if (code in 0xD800..0xDBFF && current == '\\' && s.getOrNull(i + 1) == 'u') {
                val mark = i
                i += 2
                val low = hex4()
                if (low in 0xDC00..0xDFFF) code = 0x10000 + ((code - 0xD800) shl 10) + (low - 0xDC00) else i = mark
            }
            // no lone surrogates
            if (code in 0xD800..0xDFFF) throw fail("Invalid escape", escapeStart)
            return code
        }

        fun hex4(): Int {
            var value = 0
            repeat(4) {
                val digit = current?.let { "0123456789abcdef".indexOf(it.lowercaseChar()) } ?: -1
                if (digit < 0) throw fail("Invalid escape")
                value = value * 16 + digit
                i++
            }
            return value
        }

        fun digits(numberStart: Int) {
            if (current?.let { it in '0'..'9' } != true) throw fail("Invalid number", numberStart)
            while (current?.let { it in '0'..'9' } == true) i++
        }

        fun number(): String {
            val start = i
            if (current == '-') i++
            if (current == '0') i++ else digits(start)
            if (current == '.') {
                i++
                digits(start)
            }
            if (current == 'e' || current == 'E') {
                i++
                if (current == '+' || current == '-') i++
                digits(start)
            }
            return s.substring(start, i)
        }
    }

    fun format(value: JsonValue, indent: JsonIndent): String = StringBuilder().also { write(value, indent.unit, 0, it) }.toString()

    private fun write(value: JsonValue, unit: String?, depth: Int, out: StringBuilder) {
        when (value) {
            JsonValue.Null -> out.append("null")
            is JsonValue.Bool -> out.append(value.value)
            is JsonValue.Num -> out.append(value.literal)
            is JsonValue.Str -> out.append(escaped(value.text))
            is JsonValue.Arr -> container('[', ']', value.items.size, unit, depth, out) { write(value.items[it], unit, depth + 1, out) }
            is JsonValue.Obj -> container('{', '}', value.members.size, unit, depth, out) {
                out.append(escaped(value.members[it].first)).append(if (unit == null) ":" else ": ")
                write(value.members[it].second, unit, depth + 1, out)
            }
        }
    }

    private fun container(open: Char, close: Char, count: Int, unit: String?, depth: Int, out: StringBuilder, entry: (Int) -> Unit) {
        out.append(open)
        if (count == 0) {
            out.append(close)
            return
        }
        for (index in 0 until count) {
            if (unit != null) out.append('\n').append(unit.repeat(depth + 1))
            entry(index)
            if (index < count - 1) out.append(',')
        }
        if (unit != null) out.append('\n').append(unit.repeat(depth))
        out.append(close)
    }

    fun escaped(text: String): String = Json.escaped(text)

    fun rows(root: JsonValue, collapsed: Set<String>): List<JsonTreeRow> {
        val rows = mutableListOf<JsonTreeRow>()
        fun walk(value: JsonValue, key: String?, path: String, depth: Int) {
            val children = contents(value)
            if (value is JsonValue.Obj || value is JsonValue.Arr) {
                val summary = if (value is JsonValue.Obj) "{${children.size}}" else "[${children.size}]"
                val isCollapsed = path in collapsed
                rows += JsonTreeRow(path, key, depth, summary, true, isCollapsed)
                if (!isCollapsed) children.forEachIndexed { index, (childKey, child) -> walk(child, childKey, "$path.$index", depth + 1) }
                return
            }
            val display = when (value) {
                is JsonValue.Str -> escaped(clipped(value.text))
                else -> leafText(value)
            }
            rows += JsonTreeRow(path, key, depth, display, false, false)
        }
        walk(root, null, "root", 0)
        return rows
    }

    internal fun contents(value: JsonValue): List<Pair<String, JsonValue>> = when (value) {
        is JsonValue.Obj -> value.members
        is JsonValue.Arr -> value.items.mapIndexed { index, item -> index.toString() to item }
        else -> emptyList()
    }

    internal fun leafText(value: JsonValue): String = when (value) {
        JsonValue.Null -> "null"
        is JsonValue.Bool -> if (value.value) "true" else "false"
        is JsonValue.Num -> value.literal
        is JsonValue.Str -> escaped(value.text)
        else -> ""
    }

    // ponytail: rows are one line; a full multi-megabyte string stalls text layout
    private fun clipped(text: String): String {
        val limit = 200
        if (text.length <= limit * 8) return text
        return text.takeCodePoints(limit) + "…" + text.takeLastCodePoints(limit)
    }
}

internal fun String.takeCodePoints(n: Int): String =
    if (codePointCount(0, length) <= n) this else substring(0, offsetByCodePoints(0, n))

internal fun String.takeLastCodePoints(n: Int): String {
    val count = codePointCount(0, length)
    return if (count <= n) this else substring(offsetByCodePoints(0, count - n))
}

enum class JsonKind { Object, Array, Str, Number, Literal }

sealed interface JsonToken {
    data object Key : JsonToken
    data class Value(val kind: JsonKind) : JsonToken
    data object Punctuation : JsonToken
}

/** dt-ios JSONListing.tokens: colour runs of formatted JSON, UTF-16 ranges. */
object JsonListing {
    fun tokens(source: String): List<Pair<TextSpan, JsonToken>> {
        val out = mutableListOf<Pair<TextSpan, JsonToken>>()
        var index = 0
        fun isSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\r'
        while (index < source.length) {
            val start = index
            when (source[index]) {
                '"' -> {
                    index++
                    while (index < source.length && source[index] != '"') index += if (source[index] == '\\') 2 else 1
                    index = minOf(index + 1, source.length)
                    var next = index
                    while (next < source.length && isSpace(source[next])) next++
                    out += TextSpan(start, index) to if (next < source.length && source[next] == ':') JsonToken.Key else JsonToken.Value(JsonKind.Str)
                }
                '-', in '0'..'9' -> {
                    while (index < source.length && (source[index] in "+-.Ee" || source[index] in '0'..'9')) index++
                    out += TextSpan(start, index) to JsonToken.Value(JsonKind.Number)
                }
                in 'a'..'z' -> {
                    while (index < source.length && source[index] in 'a'..'z') index++
                    out += TextSpan(start, index) to JsonToken.Value(JsonKind.Literal)
                }
                '{', '}', '[', ']', ':', ',' -> {
                    index++
                    out += TextSpan(start, start + 1) to JsonToken.Punctuation
                }
                else -> index++
            }
        }
        return out
    }

    /** The error excerpt's marked character as a UTF-16 range, or null when the caret is past the end. */
    fun caretRange(error: JsonParseError): TextSpan? {
        val line = error.excerpt.ifEmpty { " " }
        if (error.caret >= line.codePointCount(0, line.length)) return null
        val at = caretOffset(error)
        return TextSpan(at, line.offsetByCodePoints(at, 1))
    }

    fun caretOffset(error: JsonParseError): Int {
        val line = error.excerpt.ifEmpty { " " }
        return line.offsetByCodePoints(0, minOf(error.caret, line.codePointCount(0, line.length)))
    }
}

/** Fractions of the whole document; [path] is like root.3.0, [line] the zero-based indented line. */
class JsonShapeCell(
    val depth: Int,
    val start: Double,
    val end: Double,
    val kind: JsonKind,
    val label: String,
    val detail: String,
    val path: String,
    val line: Int,
    val opens: Boolean,
)

/** dt-ios JSONShape: the document as nested cells sized by formatted bytes, zoomed to [focus]. */
class JsonShape private constructor() {
    val cells = mutableListOf<JsonShapeCell>()

    /** 0: bare value. */
    var depth = 0
        private set
    var keys = 0
        private set
    var values = 0
        private set
    var focus = "root"
        private set

    val focusCell get() = cells.firstOrNull { it.path == focus }

    val levels: Int
        get() {
            val top = focusCell?.depth ?: 0
            return (cells.maxOfOrNull { it.depth } ?: top) - top + 1
        }

    private class Node(
        val kind: JsonKind,
        val label: String,
        val detail: String,
        val weight: Int,
        val lines: Int,
        val line: Int,
        val path: String,
        val total: Int,
        val children: List<Pair<Int, Node>>,
    )

    private sealed interface Track {
        /** Next path component. */
        class Ancestor(val step: Int) : Track

        /** Levels below focus. */
        class Inside(val below: Int) : Track
        data object Outside : Track
    }

    companion object {
        const val ROWS = 7
        const val LEAST = 0.0015

        fun parent(path: String) = path.split('.').dropLast(1).joinToString(".")

        /** An unknown path falls back to the root. */
        fun of(root: JsonValue, focus: String = "root"): JsonShape {
            val steps = focus.split('.').drop(1).mapNotNull { it.toIntOrNull() }
            val shape = JsonShape()
            val node = shape.build(root, "", "root", 0, 0, if (steps.isEmpty()) Track.Inside(0) else Track.Ancestor(0), steps)
            var from = 0.0
            var to = 1.0
            var current = node
            val trail = mutableListOf<String>()
            for (step in steps) {
                val child = current.children.firstOrNull { it.second.path.endsWith(".$step") } ?: return of(root)
                val width = to - from
                val start = from + width * child.first / current.total
                to = start + width * child.second.weight / current.total
                from = start
                current = child.second
                trail += current.label
            }
            shape.focus = current.path
            shape.place(node, 0, 0.0, 1.0, (to - from) * LEAST, trail.joinToString(" › "))
            return shape
        }

        /** Matches the formatter's byte count. */
        private fun leafWeight(value: JsonValue) = when (value) {
            is JsonValue.Str -> value.text.toByteArray().size + 2
            is JsonValue.Num -> value.literal.length
            is JsonValue.Bool -> if (value.value) 4 else 5
            JsonValue.Null -> 4
            else -> 0
        }
    }

    private fun describe(value: JsonValue, level: Int): Pair<JsonKind, String> = when (value) {
        is JsonValue.Obj -> {
            depth = maxOf(depth, level + 1)
            keys += value.members.size
            JsonKind.Object to "{${value.members.size}}"
        }
        is JsonValue.Arr -> {
            depth = maxOf(depth, level + 1)
            JsonKind.Array to "[${value.items.size}]"
        }
        is JsonValue.Str -> {
            values++
            JsonKind.Str to JsonFormatEngine.escaped(value.text.takeCodePoints(40))
        }
        is JsonValue.Num -> {
            values++
            JsonKind.Number to value.literal
        }
        else -> {
            values++
            JsonKind.Literal to JsonFormatEngine.leafText(value)
        }
    }

    private fun build(value: JsonValue, label: String, path: String, line: Int, level: Int, track: Track, steps: List<Int>): Node {
        val (kind, detail) = describe(value, level)
        val members = JsonFormatEngine.contents(value)
        val container = kind == JsonKind.Object || kind == JsonKind.Array
        if (members.isEmpty()) return Node(kind, label, detail, if (container) 2 else leafWeight(value), 1, line, path, 0, emptyList())
        var weight = 1 + members.size
        var total = 0
        var lines = 2
        var childLine = line + 1
        val children = mutableListOf<Pair<Int, Node>>()
        members.forEachIndexed { index, (key, member) ->
            val next: Track = when {
                track is Track.Ancestor && steps[track.step] == index -> if (track.step + 1 == steps.size) Track.Inside(0) else Track.Ancestor(track.step + 1)
                track is Track.Inside && track.below + 1 < ROWS -> Track.Inside(track.below + 1)
                else -> Track.Outside
            }
            val childWeight: Int
            val childLines: Int
            if (next == Track.Outside) {
                val measured = measure(member, level + 1)
                childWeight = measured.first
                childLines = measured.second
            } else {
                val node = build(member, key, "$path.$index", childLine, level + 1, next, steps)
                children += total to node
                childWeight = node.weight
                childLines = node.lines
            }
            if (kind == JsonKind.Object) weight += key.toByteArray().size + 2
            weight += childWeight
            total += childWeight
            lines += childLines
            childLine += childLines
        }
        return Node(kind, label, detail, weight, lines, line, path, total, children)
    }

    private fun measure(value: JsonValue, level: Int): Pair<Int, Int> {
        val (kind, _) = describe(value, level)
        val members = JsonFormatEngine.contents(value)
        if (members.isEmpty()) return (if (kind == JsonKind.Object || kind == JsonKind.Array) 2 else leafWeight(value)) to 1
        var weight = 1 + members.size
        var lines = 2
        for ((key, member) in members) {
            val (childWeight, childLines) = measure(member, level + 1)
            if (kind == JsonKind.Object) weight += key.toByteArray().size + 2
            weight += childWeight
            lines += childLines
        }
        return weight to lines
    }

    private fun place(node: Node, level: Int, start: Double, end: Double, least: Double, trail: String) {
        val isFocus = node.path == focus
        cells += JsonShapeCell(level, start, end, node.kind, if (isFocus && trail.isNotEmpty()) trail else node.label, node.detail, node.path, node.line, node.total > 0)
        if (node.total <= 0) return
        for ((offset, child) in node.children) {
            val from = start + (end - start) * offset / node.total
            val to = from + (end - start) * child.weight / node.total
            val onPath = focus == child.path || focus.startsWith(child.path + ".")
            if (onPath || to - from >= least) place(child, level + 1, from, to, least, trail)
        }
    }
}
