package com.delphi.delphitools.tools.audiovideo

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * dt-ios AVInfoRecorder.Held: the take as it is held, mono 16-bit at the input's rate, with the peak of every [window] frames.
 * [head] is the frame the next captured sample goes to; behind [count] after a scrub back.
 */
class VoiceHeld(val rate: Int) {
    val window = max(1, (rate * VoiceRecorderEngine.PEAK_INTERVAL).roundToInt())
    var samples = ShortArray(rate * 10)
        private set
    var count = 0
        private set
    var peaks = FloatArray(512)
        private set
    var peakCount = 0
        private set
    var head = 0
        private set

    val position get() = head.toDouble() / rate
    val duration get() = count.toDouble() / rate

    /** Records at the head: over what is held there after a scrub back, and on past the end. */
    fun append(floats: FloatArray, length: Int = floats.size) {
        val end = head + length
        if (end > samples.size) samples = samples.copyOf(max(end, samples.size * 2))
        for (index in 0 until length) samples[head + index] = (floats[index].coerceIn(-1f, 1f) * 32767).roundToInt().toShort()
        count = max(count, end)
        val first = head / window
        head = end
        // only windows written
        for (index in first until min(count / window, (end + window - 1) / window)) {
            var loudest = 0
            for (frame in index * window until (index + 1) * window) loudest = max(loudest, abs(samples[frame].toInt()))
            val peak = loudest / 32768f
            if (index < peakCount) {
                peaks[index] = peak
            } else {
                if (peakCount == peaks.size) peaks = peaks.copyOf(peaks.size * 2)
                peaks[peakCount++] = peak
            }
        }
    }

    fun scrub(seconds: Double) {
        head = (seconds * rate).roundToInt().coerceIn(0, count)
    }
}

/** What the take strip draws: peaks behind the head, peaks ahead of it (dim) after [gap] empty columns, and the slip between peaks in px. */
class VoiceStripFrame(val behind: FloatArray, val ahead: FloatArray, val gap: Int, val slip: Float)

/** dt-ios AVInfoRecorder, VoiceTakeStrip and VoicePlatter maths, with no device in it. */
object VoiceRecorderEngine {
    /** Seconds of audio behind each peak. */
    const val PEAK_INTERVAL = 0.02

    /** Longest take held: an hour of 16-bit at 48 kHz is 346 MB. */
    const val MAX_SECONDS = 3600.0

    /** dp per peak; with 20 ms peaks the take runs at 100 dp/s. */
    const val POINTS_PER_PEAK = 2f

    /** Where the head sits across the strip's width. */
    const val HEAD_SHARE = 0.75f

    /** dp of platter travel per second of audio. */
    const val PLATTER_SPEED = POINTS_PER_PEAK / PEAK_INTERVAL

    /** Platter hatch: diagonal lines this far apart, this wide, in dp. */
    const val STRIPE_PERIOD = 10.0
    const val STRIPE_WIDTH = 1.5f

    /**
     * dt-ios VoiceTakeStrip.show. [position] is the head as shown, in seconds; [recorded] the capture position, past which the held
     * take is the tail a punch-in records over. [tail] holds that tail's peaks as they were when recording resumed, from peak [tailStart].
     * [peakCount] of [peaks] are set. [room] is how many columns fit; [step] the px per column.
     */
    fun strip(peaks: FloatArray, peakCount: Int, position: Double, recorded: Double, tail: FloatArray, tailStart: Int, room: Int, step: Float): VoiceStripFrame {
        val exact = position / PEAK_INTERVAL
        val index = exact.toInt().coerceIn(0, peakCount)
        val slip = ((exact - index) * step).roundToInt().toFloat()
        val behind = peaks.copyOfRange(max(0, index - (room * HEAD_SHARE).toInt()), index)
        var ahead = FloatArray(0)
        val gap: Int
        if (tail.isNotEmpty() && recorded > position) {
            val from = max(index, tailStart)
            gap = from - index
            if (from - tailStart < tail.size) ahead = tail.copyOfRange(from - tailStart, min(tail.size, from - tailStart + room))
        } else {
            val start = min(peakCount, max(index, (recorded / PEAK_INTERVAL + 1e-6).toInt()))
            ahead = peaks.copyOfRange(start, min(peakCount, max(start, index + room)))
            gap = start - index
        }
        return VoiceStripFrame(behind, ahead, gap, slip)
    }

    /** `name`, or `name-2` and on, whichever [taken] does not hold. */
    fun freeName(name: String, extension: String, taken: (String) -> Boolean): String {
        var candidate = "$name.$extension"
        var index = 2
        while (taken(candidate) && index < 1000) candidate = "$name-${index++}.$extension"
        return candidate
    }

    /** A rename's file name, or null when it changes nothing. */
    fun safeName(name: String, current: String): String? =
        name.trim().replace('/', '-').replace(':', '-').takeIf { it.isNotEmpty() && it != current }

    fun length(seconds: Double): String {
        val total = seconds.roundToInt()
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }
}
