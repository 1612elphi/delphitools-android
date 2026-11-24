package com.delphi.delphitools.tools.audiovideo

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * dt-ios AVInfoRecorder.Held: the take as it is held, mono 16-bit little-endian at the input's rate in [file], with the peak
 * of every [window] frames in memory. [head] is the frame the next captured sample goes to; behind [count] after a scrub back.
 * Appends come from the capture thread while the page reads the peaks; up to [VoiceRecorderEngine.MAX_SECONDS] is kept.
 */
class VoiceHeld(val rate: Int, val file: File) : Closeable {
    val window = max(1, (rate * VoiceRecorderEngine.PEAK_INTERVAL).roundToInt())
    private val limit = (VoiceRecorderEngine.MAX_SECONDS * rate).roundToInt()
    private val data = RandomAccessFile(file, "rw").apply { setLength(0) }

    @Volatile
    var count = 0
        private set
    val peaks = FloatArray(limit / window + 1)

    @Volatile
    var peakCount = 0
        private set

    @Volatile
    var head = 0
        private set

    val position get() = head.toDouble() / rate
    val duration get() = count.toDouble() / rate

    /** Records at the head: over what is held there after a scrub back, and on past the end. */
    @Synchronized
    fun append(floats: FloatArray, length: Int = floats.size) {
        val frames = min(length, limit - head)
        if (frames <= 0) return
        val bytes = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until frames) bytes.putShort((floats[index].coerceIn(-1f, 1f) * 32767).roundToInt().toShort())
        data.seek(head * 2L)
        data.write(bytes.array())
        val end = head + frames
        val total = max(count, end)
        val first = head / window
        // partial windows keep neighbours
        val last = min(total / window, (end + window - 1) / window)
        if (last > first) {
            val span = read(first * window, (last - first) * window)
            for (index in first until last) {
                var loudest = 0
                for (frame in (index - first) * window until (index - first + 1) * window) loudest = max(loudest, abs(span[frame].toInt()))
                peaks[index] = loudest / 32768f
            }
            peakCount = max(peakCount, last)
        }
        count = total
        head = end
    }

    @Synchronized
    fun scrub(seconds: Double) {
        head = (seconds * rate).roundToInt().coerceIn(0, count)
    }

    /** [frames] samples from [start]. */
    @Synchronized
    fun read(start: Int, frames: Int): ShortArray {
        val bytes = ByteArray(frames * 2)
        data.seek(start * 2L)
        data.readFully(bytes)
        return ShortArray(frames).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(it) }
    }

    override fun close() = data.close()
}

/** What the take strip draws: peaks behind the head, peaks ahead of it (dim) after [gap] empty columns, and the slip between peaks in px. */
class VoiceStripFrame(val behind: FloatArray, val ahead: FloatArray, val gap: Int, val slip: Float)

/** dt-ios AVInfoRecorder, VoiceTakeStrip and VoicePlatter maths, with no device in it. */
object VoiceRecorderEngine {
    /** Seconds of audio behind each peak. */
    const val PEAK_INTERVAL = 0.02

    /** Longest take held: an hour of 16-bit at 48 kHz is 346 MB of scratch file and 703 KB of peaks. */
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
