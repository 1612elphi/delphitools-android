package com.delphi.delphitools.tools.audiovideo

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Seconds. */
data class SubtitleWord(val text: String, val start: Double, val end: Double)

/** One recogniser part: its text and, when the recogniser reported word timing, its offset in the session. */
data class HeardPart(val text: String, val ms: Long?)

/** dt-ios SubtitleCodec.cues(from:) and SubtitleTranscriber.mostSpokenFirst, plus the PCM work the recogniser needs. */
object AutoSubtitleEngine {
    const val RATE = 16_000
    const val FRAME_SAMPLES = RATE / 10
    private const val MAX_CHARS = 42
    private const val MAX_DURATION_MS = 6000.0
    private const val GAP_BREAK_SECONDS = 0.8
    // ponytail: RecognitionPart has no end time, so a word ends at the next word's start or 0.8 s after its own
    private const val MAX_WORD_SECONDS = 0.8
    private const val LIMIT_MS = 3.6e12

    // ethnologue 2026, total speakers
    val mostSpoken = listOf("en", "zh", "hi", "es", "ar", "fr", "bn", "pt", "id", "ur")

    /** Languages in [mostSpoken] first, in its order, each language's variants by name; the rest by name. */
    fun <T> mostSpokenFirst(items: List<T>, code: (T) -> String?, name: (T) -> String): List<T> {
        fun rank(item: T) = code(item)?.let(mostSpoken::indexOf)?.takeIf { it >= 0 } ?: mostSpoken.size
        return items.sortedWith(compareBy<T>(::rank).thenComparator { a, b -> name(a).compareTo(name(b), ignoreCase = true) })
    }

    private fun bounded(ms: Double): Long = if (ms.isNaN()) 0 else min(max(ms, -LIMIT_MS), LIMIT_MS).roundToLong()

    private val sentenceEnd = Regex("[.!?…][\"')\\]]?$")

    fun cues(words: List<SubtitleWord>): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val buffer = mutableListOf<SubtitleWord>()
        fun flush() {
            val first = buffer.firstOrNull() ?: return
            val start = max(0, bounded(first.start * 1000))
            cues += SubtitleCue(start, max(start, bounded(buffer.last().end * 1000)), buffer.joinToString(" ") { it.text })
            buffer.clear()
        }
        for (word in words) {
            if (buffer.isNotEmpty()) {
                val gap = word.start - buffer.last().end
                val length = buffer.sumOf { it.text.length + 1 } + word.text.length
                val duration = word.end * 1000 - buffer.first().start * 1000
                if (gap > GAP_BREAK_SECONDS || length > MAX_CHARS || duration > MAX_DURATION_MS) flush()
            }
            buffer += word
            if (sentenceEnd.containsMatchIn(word.text)) flush()
        }
        flush()
        return cues
    }

    /**
     * Words of one chunk from [start] to [end] seconds: timed from [parts] when any carries an offset,
     * else the words of [texts] spread evenly over the chunk.
     */
    fun words(parts: List<HeardPart>, texts: List<String>, start: Double, end: Double): List<SubtitleWord> {
        val timed = parts.filter { it.text.isNotBlank() }
        if (timed.any { (it.ms ?: 0) > 0 }) {
            val starts = timed.map { min(end, start + (it.ms ?: 0) / 1000.0) }
            return timed.mapIndexed { index, part ->
                val next = starts.getOrNull(index + 1) ?: end
                SubtitleWord(part.text.trim(), starts[index], max(starts[index], min(next, starts[index] + MAX_WORD_SECONDS)))
            }
        }
        val spoken = texts.flatMap { it.split(Regex("\\s+")) }.filter { it.isNotEmpty() }
        if (spoken.isEmpty()) return emptyList()
        val step = (end - start) / spoken.size
        return spoken.mapIndexed { index, text -> SubtitleWord(text, start + step * index, start + step * (index + 1)) }
    }

    /**
     * Chunks of about [target] frames, each cut at the quietest frame within [slack] of the target,
     * as frame ranges over [energy] (one mean square per [FRAME_SAMPLES]).
     */
    fun chunks(energy: FloatArray, target: Int = 200, slack: Int = 50): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = 0
        while (energy.size - start > target + slack) {
            val from = start + target - slack
            val cut = (from..start + target + slack).minBy { energy[it] }
            out += start until cut
            start = cut
        }
        if (start < energy.size) out += start until energy.size
        return out
    }
}

/**
 * Interleaved 16-bit PCM at any rate to 16 kHz mono: channels averaged, rate changed by averaging
 * the input frames behind each output sample (a sample repeats when upsampling). Tracks one mean
 * square per [AutoSubtitleEngine.FRAME_SAMPLES] output samples.
 */
class SpeechPcm(private val rate: Int, private val channels: Int) {
    private var phase = 0L
    private var sum = 0.0
    private var count = 0
    private var frameSquares = 0.0
    private var frameCount = 0
    private val energies = ArrayList<Float>()

    fun feed(pcm: ShortArray): ShortArray {
        val out = ShortArray((pcm.size / channels * AutoSubtitleEngine.RATE.toLong() / rate + 2).toInt())
        var written = 0
        for (frame in 0 until pcm.size / channels) {
            var mono = 0
            for (channel in 0 until channels) mono += pcm[frame * channels + channel]
            sum += mono.toDouble() / channels
            count++
            phase += AutoSubtitleEngine.RATE
            while (phase >= rate) {
                phase -= rate
                val sample = (sum / count).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                out[written++] = sample.toShort()
                measure(sample)
                if (phase < rate) {
                    sum = 0.0
                    count = 0
                }
            }
        }
        return out.copyOf(written)
    }

    private fun measure(sample: Int) {
        frameSquares += sample.toDouble() * sample
        if (++frameCount == AutoSubtitleEngine.FRAME_SAMPLES) {
            energies += (frameSquares / frameCount).toFloat()
            frameSquares = 0.0
            frameCount = 0
        }
    }

    /** Mean square per frame, the last partial frame included. */
    fun energy(): FloatArray {
        val all = energies.toFloatArray()
        return if (frameCount > 0) all + (frameSquares / frameCount).toFloat() else all
    }
}
