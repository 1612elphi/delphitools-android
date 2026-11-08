package com.delphi.delphitools.tools.audiovideo

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** dt-ios AVInfoTimecode: SMPTE timecode arithmetic, drop-frame included. */
object Timecode {
    data class Parts(val h: Int, val m: Int, val s: Int, val f: Int, val negative: Boolean = false)

    data class Rate(val id: String, val label: String, val nominal: Int, val exact: Double, val drop: Boolean)

    val rates = listOf(
        Rate("23.976", "23.976", 24, 24000.0 / 1001, false),
        Rate("24", "24", 24, 24.0, false),
        Rate("25", "25", 25, 25.0, false),
        Rate("29.97ndf", "29.97 NDF", 30, 30000.0 / 1001, false),
        Rate("29.97df", "29.97 DF", 30, 30000.0 / 1001, true),
        Rate("30", "30", 30, 30.0, false),
        Rate("50", "50", 50, 50.0, false),
        Rate("59.94ndf", "59.94 NDF", 60, 60000.0 / 1001, false),
        Rate("59.94df", "59.94 DF", 60, 60000.0 / 1001, true),
        Rate("60", "60", 60, 60.0, false),
    )

    fun rate(id: String): Rate = rates.firstOrNull { it.id == id } ?: rates[5]

    private fun dropPerMinute(nominal: Int) = if (nominal >= 60) 4 else 2

    /** About 385 days at 30 fps; low enough that the conversions cannot overflow on pasted input. */
    const val FRAME_LIMIT = 1_000_000_000

    fun frames(parts: Parts, rate: Rate): Int {
        val base = rate.nominal.toDouble()
        var frames = ((parts.h.toDouble() * 60 + parts.m) * 60 + parts.s) * base + parts.f
        if (rate.drop) {
            val dpm = dropPerMinute(rate.nominal).toDouble()
            val totalMinutes = floor(parts.h.toDouble() * 60 + parts.m)
            frames -= dpm * (totalMinutes - floor(totalMinutes / 10))
        }
        return clamp(if (parts.negative) -frames else frames)
    }

    private fun clamp(frames: Double) = when {
        frames >= FRAME_LIMIT -> FRAME_LIMIT
        frames <= -FRAME_LIMIT -> -FRAME_LIMIT
        else -> frames.toInt()
    }

    fun parts(frames: Int, rate: Rate): Parts {
        val frameNumber = frames.coerceIn(-FRAME_LIMIT, FRAME_LIMIT)
        var fn = abs(frameNumber)
        val base = rate.nominal
        if (rate.drop) {
            val dpm = dropPerMinute(base)
            val per10Min = base * 60 * 10 - dpm * 9
            val perMin = base * 60 - dpm
            val d = fn / per10Min
            val mod = fn % per10Min
            fn += if (mod >= dpm) dpm * 9 * d + dpm * ((mod - dpm) / perMin) else dpm * 9 * d
        }
        return Parts(fn / (base * 3600), (fn / (base * 60)) % 60, (fn / base) % 60, fn % base, frameNumber < 0)
    }

    fun seconds(frames: Int, rate: Rate) = frames / rate.exact

    fun format(parts: Parts, rate: Rate): String {
        val sep = if (rate.drop) ";" else ":"
        val sign = if (parts.negative) "-" else ""
        return sign + listOf(parts.h, parts.m, parts.s).joinToString(":", transform = ::pad) + sep + pad(parts.f)
    }

    fun clock(seconds: Double): String {
        if (!seconds.isFinite() || abs(seconds) >= 1e12) return "—"
        val sign = if (seconds < 0) "-" else ""
        val total = (abs(seconds) * 1000).roundToLong()
        val whole = total / 1000
        val ms = total - whole * 1000
        val parts = listOf(whole / 3600, (whole / 60) % 60, whole % 60).joinToString(":") { String.format(Locale.ROOT, "%02d", it) }
        return "$sign$parts.${String.format(Locale.ROOT, "%03d", ms)}"
    }

    private fun pad(n: Int) = String.format(Locale.ROOT, "%02d", n)

    sealed interface ParseError {
        data object Empty : ParseError
        data object TooMany : ParseError
        data object NotNumeric : ParseError
        data class FrameRange(val max: Int) : ParseError
        data object SecondsRange : ParseError
        data object MinutesRange : ParseError
    }

    sealed interface ParseResult {
        data class Ok(val parts: Parts, val frames: Int, val snappedFrom: String?) : ParseResult
        data class Failed(val error: ParseError) : ParseResult
    }

    fun parse(input: String, rate: Rate): ParseResult {
        val raw = input.trim(' ', '\t')
        if (raw.isEmpty()) return ParseResult.Failed(ParseError.Empty)
        val negative = raw.startsWith("-")
        val body = if (negative) raw.drop(1).trim(' ', '\t') else raw
        val tokens = body.split(':', ';', '.', ',').map { it.trim(' ', '\t') }
        if (tokens.size > 4) return ParseResult.Failed(ParseError.TooMany)
        val nums = tokens.mapNotNull { token -> token.takeIf { t -> t.isNotEmpty() && t.all { it in '0'..'9' } }?.toIntOrNull() }
        if (nums.size != tokens.size || nums.isEmpty()) return ParseResult.Failed(ParseError.NotNumeric)

        val padded = IntArray(4)
        nums.forEachIndexed { i, n -> padded[4 - nums.size + i] = n }
        val (h, m, s, f) = padded
        if (f >= rate.nominal) return ParseResult.Failed(ParseError.FrameRange(rate.nominal - 1))
        if (s > 59) return ParseResult.Failed(ParseError.SecondsRange)
        if (m > 59) return ParseResult.Failed(ParseError.MinutesRange)

        var parts = Parts(h, m, s, f, negative)
        var snappedFrom: String? = null
        // skip invalid drop-frame numbers
        if (rate.drop) {
            val dpm = dropPerMinute(rate.nominal)
            if (m % 10 != 0 && s == 0 && f < dpm) {
                snappedFrom = format(Parts(h, m, s, f), rate)
                parts = parts.copy(f = dpm)
            }
        }
        return ParseResult.Ok(parts, frames(parts, rate), snappedFrom)
    }

    private const val FIELD_DIGITS = 8

    /** The last eight digits of [text], as the timecode field keeps them. */
    fun fieldDigits(text: String) = text.filter { it in '0'..'9' }.takeLast(FIELD_DIGITS)

    /** Up to eight digits, filled from the right, as `HH:MM:SS:FF`; none gives an empty string. */
    fun fieldFormat(digits: String, drop: Boolean): String {
        if (digits.isEmpty()) return ""
        val padded = digits.takeLast(FIELD_DIGITS).padStart(FIELD_DIGITS, '0')
        val pairs = padded.chunked(2)
        return pairs.take(3).joinToString(":") + (if (drop) ";" else ":") + pairs[3]
    }

    /** Hyphens in the rate's shape. */
    fun blank(rate: Rate) = "--:--:--" + (if (rate.drop) "." else ":") + "--"
}
