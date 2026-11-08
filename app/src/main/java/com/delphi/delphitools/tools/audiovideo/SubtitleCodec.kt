package com.delphi.delphitools.tools.audiovideo

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Times in milliseconds. */
data class SubtitleCue(val start: Long, val end: Long, val text: String)

enum class SubtitleFormat(val extension: String, val mimeType: String) {
    Srt("srt", "application/x-subrip"),
    Vtt("vtt", "text/vtt");

    val label get() = extension.uppercase()
}

/** dt-ios SubtitleCodec: SRT and WebVTT parsing, writing and retiming. */
object SubtitleCodec {
    // js trim strips u+feff
    private fun trim(value: String) = value.trim { it.isWhitespace() || it == '﻿' }

    private fun digits(value: String, max: Int): Long? =
        if (value.length in 1..max && value.all { it in '0'..'9' }) value.toLong() else null

    /** `[h+:]m{1,2}:s{1,2}[.,]f{1,3}` */
    fun parseTimestamp(value: String): Long? {
        val parts = trim(value).split(':')
        if (parts.size != 2 && parts.size != 3) return null
        val tail = parts.last().split('.', ',')
        if (tail.size != 2) return null
        val minutes = digits(parts[parts.size - 2], 2) ?: return null
        val seconds = digits(tail[0], 2) ?: return null
        digits(tail[1], 3) ?: return null
        val millis = tail[1].padEnd(3, '0').toLong()
        val hours = if (parts.size == 3) digits(parts[0], 9) ?: return null else 0
        return hours * 3_600_000 + minutes * 60_000 + seconds * 1000 + millis
    }

    fun formatTimestamp(ms: Long, separator: Char): String {
        val t = max(0, ms)
        return String.format(Locale.ROOT, "%02d:%02d:%02d%c%03d", t / 3_600_000, (t / 60_000) % 60, (t / 1000) % 60, separator, t % 1000)
    }

    fun detectFormat(text: String): SubtitleFormat? = when {
        trim(text).startsWith("WEBVTT") -> SubtitleFormat.Vtt
        text.contains("-->") -> SubtitleFormat.Srt
        else -> null
    }

    /** Blocks without a timing line (index, WEBVTT header, NOTE, STYLE) are skipped. */
    fun parse(text: String): List<SubtitleCue> {
        val normalised = text.replace("\r\n", "\n").replace("\r", "\n")
        return normalised.split("\n\n").mapNotNull { block ->
            val lines = block.split("\n")
            val timing = lines.indexOfFirst { it.contains("-->") }
            if (timing < 0) return@mapNotNull null
            val sides = lines[timing].split("-->")
            // settings follow end time
            val endRaw = trim(sides[1]).split(Regex("\\s+")).firstOrNull() ?: ""
            val start = parseTimestamp(sides[0]) ?: return@mapNotNull null
            val end = parseTimestamp(endRaw) ?: return@mapNotNull null
            SubtitleCue(start, end, trim(lines.drop(timing + 1).joinToString("\n")))
        }
    }

    fun writeSrt(cues: List<SubtitleCue>): String =
        cues.mapIndexed { index, cue ->
            "${index + 1}\n${formatTimestamp(cue.start, ',')} --> ${formatTimestamp(cue.end, ',')}\n${cue.text}"
        }.joinToString("\n\n") + "\n"

    fun writeVtt(cues: List<SubtitleCue>, kind: String = "", language: String = ""): String {
        var header = "WEBVTT"
        if (trim(kind).isNotEmpty()) header += "\nKind: ${trim(kind)}"
        if (trim(language).isNotEmpty()) header += "\nLanguage: ${trim(language)}"
        return header + "\n\n" + cues.joinToString("\n\n") { cue ->
            "${formatTimestamp(cue.start, '.')} --> ${formatTimestamp(cue.end, '.')}\n${cue.text}"
        } + "\n"
    }

    fun write(cues: List<SubtitleCue>, format: SubtitleFormat, kind: String = "", language: String = ""): String =
        if (format == SubtitleFormat.Srt) writeSrt(cues) else writeVtt(cues, kind, language)

    private const val LIMIT_MS = 3.6e12

    /** Swift `rounded()`: halves away from zero. */
    private fun bounded(ms: Double): Long {
        if (ms.isNaN()) return 0
        val clamped = min(max(ms, -LIMIT_MS), LIMIT_MS)
        return if (clamped < 0) -(-clamped).roundToLong() else clamped.roundToLong()
    }

    fun shift(cues: List<SubtitleCue>, byMs: Long): List<SubtitleCue> = cues.map { cue ->
        val start = max(0, cue.start + byMs)
        SubtitleCue(start, max(start, cue.end + byMs), cue.text)
    }

    fun scale(cues: List<SubtitleCue>, factor: Double): List<SubtitleCue> = cues.map { cue ->
        SubtitleCue(bounded(cue.start * factor), bounded(cue.end * factor), cue.text)
    }

    /** Scale before shift, as the web tool does. */
    fun retime(cues: List<SubtitleCue>, shiftSeconds: Double, factor: Double): List<SubtitleCue> {
        var out = cues
        if (factor != 1.0 && factor > 0 && factor.isFinite()) out = scale(out, factor)
        if (shiftSeconds != 0.0 && shiftSeconds.isFinite()) out = shift(out, bounded(shiftSeconds * 1000))
        return out
    }
}
