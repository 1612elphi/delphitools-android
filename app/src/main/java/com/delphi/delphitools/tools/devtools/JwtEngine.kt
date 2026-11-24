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

/** JsonFormatEngine's parse and two-space format, without error positions. */
internal object Json {
    fun parse(source: String): JsonValue? = (JsonFormatEngine.parse(source) as? JsonParsed.Valid)?.value

    fun format(value: JsonValue): String = JsonFormatEngine.format(value, JsonIndent.Two)
}
