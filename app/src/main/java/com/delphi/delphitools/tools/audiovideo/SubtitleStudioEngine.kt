package com.delphi.delphitools.tools.audiovideo

import kotlin.math.max
import kotlin.math.min

enum class SubtitleFontChoice(val label: String) { Sans("Sans"), Serif("Serif"), Mono("Mono"), Rounded("Rounded") }

enum class SubtitleTextStyle(val label: String) { Outline("Outline"), Box("Box"), Shadow("Shadow"), Plain("Plain") }

/** dt-ios SubtitleBurnStyle. Colours are components 0 to 1. */
data class SubtitleStyle(
    val font: SubtitleFontChoice = SubtitleFontChoice.Sans,
    val bold: Boolean = false,
    /** Fraction of frame height. */
    val size: Double = 0.05,
    /** Fraction of frame width. */
    val width: Double = 0.9,
    val red: Double = 1.0,
    val green: Double = 1.0,
    val blue: Double = 1.0,
    val text: SubtitleTextStyle = SubtitleTextStyle.Outline,
    /** Outline, box and shadow colour. */
    val backingRed: Double = 0.0,
    val backingGreen: Double = 0.0,
    val backingBlue: Double = 0.0,
    /** Fraction of frame width, from centre. */
    val x: Double = 0.0,
    /** Fraction of frame height, from the default baseline. */
    val y: Double = 0.0,
) {
    fun clamped() = copy(x = x.coerceIn(X_RANGE), y = y.coerceIn(Y_RANGE))

    companion object {
        val X_RANGE = -0.45..0.45
        val Y_RANGE = -0.9..0.05
        val SIZE_RANGE = 0.02f..0.14f
        val WIDTH_RANGE = 0.3f..0.9f
    }
}

/** One wrapped line: its text, baseline and width in px, and its backing box. */
class SubtitleLine(val text: String, val baseline: Float, val width: Float, val left: Float, val top: Float, val right: Float, val bottom: Float)

/** dt-ios SubtitleBurnRenderer and SubtitleStudioBoard maths, with no device in it. */
object SubtitleStudioEngine {
    private const val LINE_HEIGHT = 1.25

    /** Fraction from frame top. */
    const val BASELINE = 0.94

    /** SMPTE ST 2046-1 graphics-safe margin. */
    const val UNSAFE = 0.05

    /** Top half stacks downward. */
    fun growsDown(y: Double) = BASELINE + y < 0.5

    private val tags = Regex("<[^>]+>")

    fun stripTags(text: String) = text.replace(tags, "")

    /** End exclusive. */
    fun activeIndex(cues: List<SubtitleCue>, ms: Double): Int? = cues.indexOfFirst { ms >= it.start && ms < it.end }.takeIf { it >= 0 }

    /** The cue on screen at [ms], or the next one as a stand-in between cues (`ghost`). */
    fun shown(cues: List<SubtitleCue>, ms: Double): Pair<Int, Boolean>? {
        if (cues.isEmpty()) return null
        activeIndex(cues, ms)?.let { return it to false }
        return (cues.indexOfFirst { it.start > ms }.takeIf { it >= 0 } ?: (cues.size - 1)) to true
    }

    /** Top, middle and bottom presets as a y offset. */
    fun placement(place: Int, size: Double): Double {
        val baseline = when (place) {
            0 -> UNSAFE + size
            1 -> 0.5 + size * 0.35
            else -> BASELINE
        }
        return (baseline - BASELINE).coerceIn(SubtitleStyle.Y_RANGE)
    }

    fun wrap(text: String, maxWidth: Float, width: (String) -> Float): List<String> {
        val out = mutableListOf<String>()
        for (raw in text.split("\n")) {
            var line = ""
            for (word in raw.split(' ', '\t').filter { it.isNotEmpty() }) {
                val next = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && width(next) > maxWidth) {
                    out += line
                    line = word
                } else {
                    line = next
                }
            }
            if (line.isNotEmpty()) out += line
        }
        return out
    }

    /** Font size in px of a [frameHeight] px frame. */
    fun fontSize(style: SubtitleStyle, frameHeight: Float): Float = max(8f, Math.round(style.size * frameHeight).toFloat())

    /** The lines of [text] laid out in a [frameWidth] × [frameHeight] px frame, top-left origin. */
    fun layout(text: String, frameWidth: Float, frameHeight: Float, style: SubtitleStyle, measure: (String) -> Float): List<SubtitleLine> {
        val px = fontSize(style, frameHeight)
        val lines = wrap(stripTags(text), frameWidth * style.width.toFloat(), measure)
        val advance = px * LINE_HEIGHT.toFloat()
        val centreX = frameWidth / 2 + style.x.toFloat() * frameWidth
        val bottom = frameHeight * BASELINE.toFloat() + style.y.toFloat() * frameHeight
        val padX = px * 0.3f
        val padY = px * 0.15f
        val down = growsDown(style.y)
        return lines.mapIndexed { index, line ->
            val baseline = if (down) bottom + index * advance else bottom - (lines.size - 1 - index) * advance
            val width = measure(line)
            SubtitleLine(
                line, baseline, width,
                centreX - width / 2 - padX, baseline - px * 0.85f - padY,
                centreX + width / 2 + padX, baseline - px * 0.85f - padY + px * 1.1f + padY * 2,
            )
        }
    }

    /** Overhang of stroke and shadow past the line boxes, in px: sideways, then up and down. */
    fun overhang(style: SubtitleStyle, px: Float): Pair<Float, Float> {
        val stroke = if (style.text == SubtitleTextStyle.Outline) max(2f, px / 8) else 0f
        val shadow = if (style.text == SubtitleTextStyle.Shadow) px * 0.08f else 0f
        return (stroke + shadow * 3) to (stroke + shadow * 3 + px * 0.3f)
    }

    /** dt-ios SubtitleStudio previous and next cue: the last cue starting 250 ms before [ms], or the first after it. */
    fun jump(cues: List<SubtitleCue>, ms: Double, offset: Int): Int? =
        if (offset < 0) cues.indexOfLast { it.start < ms - 250 }.takeIf { it >= 0 }
        else cues.indexOfFirst { it.start > ms + 1 }.takeIf { it >= 0 }

    /** Where a seek to a cue lands: one frame in, as an exact seek lands early, but never past its end. */
    fun cueSeek(cue: SubtitleCue, fps: Double): Double = min(cue.start / 1000.0 + 1 / max(1.0, fps), cue.end / 1000.0)
}
