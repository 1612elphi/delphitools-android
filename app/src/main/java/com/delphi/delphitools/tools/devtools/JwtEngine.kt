package com.delphi.delphitools.tools.devtools

import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import kotlin.math.floor

enum class JwtSegmentError(val label: String) {
    NotBase64Url("not base64url"),
    NotJson("not JSON"),
    NotAnObject("not an object"),
}

class JwtJsonSegment(
    val byteCount: Int?,
    val members: List<Pair<String, JsonValue>>?,
    val pretty: String?,
    val error: JwtSegmentError?,
)

class JwtSignature(val raw: String, val byteCount: Int?, val error: JwtSegmentError?)

data class JwtTimeClaim(val key: String, val label: String, val seconds: Double, val absolute: String, val isPast: Boolean)

class JwtDecoded(
    val header: JwtJsonSegment,
    val payload: JwtJsonSegment,
    val signature: JwtSignature,
    val algorithm: String?,
    val tokenType: String?,
    val timeClaims: List<JwtTimeClaim>,
)

sealed interface JwtResult {
    data object Empty : JwtResult
    data class Segments(val found: Int) : JwtResult
    class Decoded(val decoded: JwtDecoded) : JwtResult
}

enum class JwtState(val text: String) {
    Valid("Valid"),
    Expired("Expired"),
    Early("Not yet valid"),
}

/** dt-ios JWTEngine: splits and decodes a token; signatures are not verified. */
object JwtEngine {
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    // LocalDateTime's year range
    private const val SECONDS_LIMIT = 3.1e16

    fun formatAbsolute(seconds: Double, zone: ZoneId = ZoneId.systemDefault()): String {
        val instant = Instant.ofEpochSecond(floor(seconds.coerceIn(-SECONDS_LIMIT, SECONDS_LIMIT)).toLong())
        val t = instant.atZone(zone)
        fun pad(n: Int) = n.toString().padStart(2, '0')
        return "${pad(t.dayOfMonth)} ${months[t.monthValue - 1]} ${t.year}, ${pad(t.hour)}:${pad(t.minute)}:${pad(t.second)}"
    }

    fun base64UrlToData(segment: String): ByteArray? {
        val unpadded = segment.trimEnd('=')
        val allowed = unpadded.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }
        if (!allowed || unpadded.length % 4 == 1) return null
        val padding = (4 - unpadded.length % 4) % 4
        val base64 = unpadded.replace('-', '+').replace('_', '/') + "=".repeat(padding)
        return runCatching { Base64.getDecoder().decode(base64) }.getOrNull()
    }

    private fun decodeJsonSegment(segment: String): JwtJsonSegment {
        val data = base64UrlToData(segment) ?: return JwtJsonSegment(null, null, null, JwtSegmentError.NotBase64Url)
        // lossy like TextDecoder
        val value = Json.parse(String(data, Charsets.UTF_8))
            ?: return JwtJsonSegment(data.size, null, null, JwtSegmentError.NotJson)
        if (value !is JsonValue.Obj) return JwtJsonSegment(data.size, null, null, JwtSegmentError.NotAnObject)
        return JwtJsonSegment(data.size, value.members, Json.format(value), null)
    }

    private val claimLabels = listOf("iat" to "Issued", "nbf" to "Not before", "exp" to "Expires")

    fun timeClaims(members: List<Pair<String, JsonValue>>, nowSeconds: Double): List<JwtTimeClaim> =
        claimLabels.mapNotNull { (key, label) ->
            val literal = (members.firstOrNull { it.first == key }?.second as? JsonValue.Num)?.literal ?: return@mapNotNull null
            val seconds = literal.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
            JwtTimeClaim(key, label, seconds, formatAbsolute(seconds), seconds <= nowSeconds)
        }

    private fun stringClaim(members: List<Pair<String, JsonValue>>?, key: String) =
        (members?.firstOrNull { it.first == key }?.second as? JsonValue.Str)?.text

    fun decode(input: String, nowSeconds: Double = System.currentTimeMillis() / 1000.0): JwtResult {
        val token = input.trim()
        if (token.isEmpty()) return JwtResult.Empty
        val segments = token.split(".")
        if (segments.size != 3) return JwtResult.Segments(segments.size)
        val header = decodeJsonSegment(segments[0])
        val payload = decodeJsonSegment(segments[1])
        val signature = base64UrlToData(segments[2])
        return JwtResult.Decoded(
            JwtDecoded(
                header,
                payload,
                JwtSignature(segments[2], signature?.size, if (signature == null) JwtSegmentError.NotBase64Url else null),
                stringClaim(header.members, "alg"),
                stringClaim(header.members, "typ"),
                payload.members?.let { timeClaims(it, nowSeconds) } ?: emptyList(),
            ),
        )
    }

    fun status(decoded: JwtDecoded, nowSeconds: Double): JwtState? {
        val claims = decoded.timeClaims
        claims.firstOrNull { it.key == "exp" }?.let { if (it.seconds <= nowSeconds) return JwtState.Expired }
        claims.firstOrNull { it.key == "nbf" }?.let { if (it.seconds > nowSeconds) return JwtState.Early }
        return if (claims.any { it.key == "exp" }) JwtState.Valid else null
    }

    /** The largest whole unit in [seconds] and its abbreviation. */
    fun span(seconds: Double): Pair<String, String> {
        // ponytail: English abbreviations and a truncated count; iOS uses DateComponentsFormatter's localised ones
        val whole = floor(maxOf(0.0, seconds)).toLong()
        val units = listOf(31_536_000L to "y", 86_400L to "d", 3_600L to "h", 60L to "m")
        val (size, unit) = units.firstOrNull { whole >= it.first } ?: (1L to "s")
        return (whole / size).toString() to unit
    }
}

/** dt-ios JSONValue: numbers keep their source text. */
sealed interface JsonValue {
    class Obj(val members: List<Pair<String, JsonValue>>) : JsonValue
    class Arr(val items: List<JsonValue>) : JsonValue
    class Str(val text: String) : JsonValue
    class Num(val literal: String) : JsonValue
    class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

/** The parse and two-space format of dt-ios JSONFormatEngine, without error positions. */
internal object Json {
    private const val MAX_DEPTH = 128

    private class Failure : Exception()

    fun parse(source: String): JsonValue? = try {
        Scanner(source).document()
    } catch (_: Failure) {
        null
    }

    private class Scanner(val s: String) {
        var i = 0
        var depth = 0
        val current get() = s.getOrNull(i)

        fun skipWhitespace() {
            while (current == ' ' || current == '\t' || current == '\n' || current == '\r') i++
        }

        fun document(): JsonValue {
            val value = value()
            skipWhitespace()
            if (current != null) throw Failure()
            return value
        }

        fun value(): JsonValue {
            skipWhitespace()
            return when (val c = current ?: throw Failure()) {
                '{', '[' -> {
                    if (depth >= MAX_DEPTH) throw Failure()
                    depth++
                    (if (c == '{') obj() else arr()).also { depth-- }
                }
                '"' -> JsonValue.Str(string())
                't' -> expect("true", JsonValue.Bool(true))
                'f' -> expect("false", JsonValue.Bool(false))
                'n' -> expect("null", JsonValue.Null)
                '-', in '0'..'9' -> JsonValue.Num(number())
                else -> throw Failure()
            }
        }

        fun expect(word: String, value: JsonValue): JsonValue {
            if (!s.startsWith(word, i)) throw Failure()
            i += word.length
            return value
        }

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
                if (current != '"') throw Failure()
                val key = string()
                skipWhitespace()
                if (current != ':') throw Failure()
                i++
                members += key to value()
                skipWhitespace()
                when (current) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return JsonValue.Obj(members)
                    }
                    else -> throw Failure()
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
                    else -> throw Failure()
                }
            }
        }

        fun string(): String {
            i++
            val out = StringBuilder()
            while (true) {
                val c = current ?: throw Failure()
                i++
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> {
                        when (val escape = current ?: throw Failure()) {
                            '"', '\\', '/' -> out.append(escape)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                i++
                                out.appendCodePoint(unicodeEscape())
                                continue
                            }
                            else -> throw Failure()
                        }
                        i++
                    }
                    c < ' ' -> throw Failure()
                    else -> out.append(c)
                }
            }
        }

        fun unicodeEscape(): Int {
            var code = hex4()
            if (code in 0xD800..0xDBFF && current == '\\' && s.getOrNull(i + 1) == 'u') {
                val mark = i
                i += 2
                val low = hex4()
                if (low in 0xDC00..0xDFFF) code = 0x10000 + ((code - 0xD800) shl 10) + (low - 0xDC00) else i = mark
            }
            // no lone surrogates
            if (code in 0xD800..0xDFFF) throw Failure()
            return code
        }

        fun hex4(): Int {
            var value = 0
            repeat(4) {
                val digit = current?.let { "0123456789abcdef".indexOf(it.lowercaseChar()) } ?: -1
                if (digit < 0) throw Failure()
                value = value * 16 + digit
                i++
            }
            return value
        }

        fun digits() {
            if (current?.let { it in '0'..'9' } != true) throw Failure()
            while (current?.let { it in '0'..'9' } == true) i++
        }

        fun number(): String {
            val start = i
            if (current == '-') i++
            if (current == '0') i++ else digits()
            if (current == '.') {
                i++
                digits()
            }
            if (current == 'e' || current == 'E') {
                i++
                if (current == '+' || current == '-') i++
                digits()
            }
            return s.substring(start, i)
        }
    }

    fun format(value: JsonValue): String = StringBuilder().also { write(value, 0, it) }.toString()

    private fun write(value: JsonValue, depth: Int, out: StringBuilder) {
        when (value) {
            JsonValue.Null -> out.append("null")
            is JsonValue.Bool -> out.append(value.value)
            is JsonValue.Num -> out.append(value.literal)
            is JsonValue.Str -> out.append(escaped(value.text))
            is JsonValue.Arr -> container('[', ']', value.items.size, depth, out) { write(value.items[it], depth + 1, out) }
            is JsonValue.Obj -> container('{', '}', value.members.size, depth, out) {
                out.append(escaped(value.members[it].first)).append(": ")
                write(value.members[it].second, depth + 1, out)
            }
        }
    }

    private fun container(open: Char, close: Char, count: Int, depth: Int, out: StringBuilder, entry: (Int) -> Unit) {
        out.append(open)
        if (count == 0) {
            out.append(close)
            return
        }
        for (index in 0 until count) {
            out.append('\n').append("  ".repeat(depth + 1))
            entry(index)
            if (index < count - 1) out.append(',')
        }
        out.append('\n').append("  ".repeat(depth)).append(close)
    }

    fun escaped(text: String): String {
        val out = StringBuilder("\"")
        for (c in text) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        return out.append('"').toString()
    }
}
