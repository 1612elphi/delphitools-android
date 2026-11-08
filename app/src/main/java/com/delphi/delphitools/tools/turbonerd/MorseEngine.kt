package com.delphi.delphitools.tools.turbonerd

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object MorseEngine {
    // itu-r m.1677-1, plus !;&_$
    val table: Map<Char, String> = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '\'' to ".----.",
        '!' to "-.-.--", '/' to "-..-.", '(' to "-.--.", ')' to "-.--.-",
        '&' to ".-...", ':' to "---...", ';' to "-.-.-.", '=' to "-...-",
        '+' to ".-.-.", '-' to "-....-", '_' to "..--.-", '"' to ".-..-.",
        '$' to "...-..-", '@' to ".--.-.",
    )

    val reverse: Map<String, Char> = table.entries.associate { (char, code) -> code to char }

    fun encode(text: String): String = text.uppercase()
        .split(Regex("\\s+"))
        .map { word -> word.mapNotNull { table[it] }.joinToString(" ") }
        .filter { it.isNotEmpty() }
        .joinToString(" / ")

    fun decode(morse: String): String =
        words(morse).joinToString(" ") { word -> word.map { reverse[it] ?: '�' }.joinToString("") }

    fun looksLikeMorse(text: String): Boolean {
        val stripped = text.filterNot { it.isWhitespace() }
        return stripped.isNotEmpty() && stripped.all { it in ".·•-–—_/" }
    }

    data class Timing(val on: Boolean, val ms: Double)

    // paris timing, 50 units
    fun timings(morse: String, wpm: Int): List<Timing> {
        val unit = 1200.0 / wpm
        val out = mutableListOf<Timing>()
        words(morse).forEachIndexed { w, word ->
            if (w > 0) out += Timing(false, 7 * unit)
            word.forEachIndexed { c, code ->
                if (c > 0) out += Timing(false, 3 * unit)
                code.forEachIndexed { s, symbol ->
                    if (s > 0) out += Timing(false, unit)
                    out += Timing(true, (if (symbol == '-') 3 else 1) * unit)
                }
            }
        }
        return out
    }

    /** Words of codes. A slash or a run of [minimumGap] or more spaces ends a word. */
    fun words(morse: String, minimumGap: Int = 3): List<List<String>> {
        val marked = StringBuilder()
        var run = 0
        for (char in morse.trim()) {
            if (char.isWhitespace()) {
                run += 1
                continue
            }
            if (run > 0) marked.append(if (run >= minimumGap) '/' else ' ')
            run = 0
            marked.append(
                when (char) {
                    '·', '•' -> '.'
                    '–', '—', '_' -> '-'
                    else -> char
                },
            )
        }
        return marked.split('/')
            .map { part -> part.split(' ').filter { it.isNotEmpty() } }
            .filter { it.isNotEmpty() }
    }

    const val SAMPLE_RATE = 22_050
    private const val TONE_HZ = 600.0

    // an instant gain step clicks
    private const val RAMP_SECONDS = 0.002

    // ponytail: one buffer holds the message, so playback stops here; streaming per element if long messages matter
    private const val MAXIMUM_SECONDS = 120.0

    // no run this long fits MAXIMUM_SECONDS, even at 40 WPM
    const val MAXIMUM_SYMBOLS = 20_000

    /** The message as a 16-bit mono sine tone at [SAMPLE_RATE], a word gap after it for looping; null when silent. */
    fun render(morse: String, wpm: Int): ShortArray? {
        val steps = timings(morse.take(MAXIMUM_SYMBOLS), wpm).toMutableList()
        if (steps.isEmpty()) return null
        // loop gap: word space
        steps += Timing(false, 7 * 1200.0 / wpm)
        val total = min(MAXIMUM_SECONDS, steps.sumOf { it.ms / 1000 })
        val frames = ceil(total * SAMPLE_RATE).toInt() + 1
        val samples = ShortArray(frames)
        val ramp = (RAMP_SECONDS * SAMPLE_RATE).toInt()
        val step = 2 * PI * TONE_HZ / SAMPLE_RATE
        var index = 0
        var phase = 0.0
        for (element in steps) {
            val count = min((element.ms / 1000 * SAMPLE_RATE).roundToInt(), frames - index)
            if (count <= 0) continue
            for (i in 0 until count) {
                var gain = if (element.on) 1.0 else 0.0
                if (element.on && ramp > 0) gain = min(i.toDouble() / ramp, (count - 1 - i).toDouble() / ramp).coerceIn(0.0, 1.0)
                samples[index + i] = (sin(phase) * gain * 0.6 * Short.MAX_VALUE).toInt().toShort()
                phase += step
            }
            index += count
        }
        return samples
    }
}
