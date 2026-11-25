package com.delphi.delphitools.tools.audiovideo

import android.media.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.tan

/** dt-ios AVInfoAudioDSP.Scan: peaks, levels and loudness of a decoded file. */
class AudioScan(
    val peaks: AVEditPeaks,
    /** Negative infinity when the file is digital silence. */
    val peakDb: Double,
    val rmsDb: Double,
    /** Null when shorter than one 400 ms gating block, or not measured. */
    val lufs: Double?,
    val sampleRate: Int,
    val channels: Int,
    val duration: Double,
)

/** dt-ios AVEditAudio.Plan. */
data class AudioGainPlan(val gainDb: Double, val limited: Boolean, val outPeakDb: Double)

/** A rectangle in image pixels, y counting down. */
data class AudioBar(val x: Double, val y: Double, val width: Double, val height: Double)

private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }
}

/** BS.1770-4 K-weighting: a high shelf, then a high pass. */
internal class KWeighting(rate: Double) {
    private val shelf: Biquad
    private val highpass: Biquad

    init {
        var f0 = 1681.974450955533
        var q = 0.7071752369554196
        val gainDb = 3.999843853973347
        var k = tan(PI * f0 / rate)
        val vh = 10.0.pow(gainDb / 20)
        val vb = vh.pow(0.4996667741545416)
        var a0 = 1 + k / q + k * k
        shelf = Biquad(
            (vh + (vb * k) / q + k * k) / a0,
            (2 * (k * k - vh)) / a0,
            (vh - (vb * k) / q + k * k) / a0,
            (2 * (k * k - 1)) / a0,
            (1 - k / q + k * k) / a0,
        )
        f0 = 38.13547087602444
        q = 0.5003270373238773
        k = tan(PI * f0 / rate)
        a0 = 1 + k / q + k * k
        highpass = Biquad(1.0, -2.0, 1.0, (2 * (k * k - 1)) / a0, (1 - k / q + k * k) / a0)
    }

    fun process(x: Double) = highpass.process(shelf.process(x))
}

/**
 * dt-ios AVInfoAudioDSP.scan, fed chunk by chunk: 16-bit interleaved PCM in, an [AudioScan] out.
 * [spanFrames] is the expected length, which places frames in [buckets]; frames past it go in the last bucket.
 * The measuring is [AudioScanner]'s over the whole file.
 */
class AudioMeter(rate: Int, private val channels: Int, spanFrames: Long, buckets: Int, loudness: Boolean) {
    private val scanner = AudioScanner(rate, channels, 0, max(1L, spanFrames), buckets, open = true, measureLoudness = loudness)
    var frames = 0L
        private set

    fun feed(pcm: ShortArray) {
        val count = pcm.size / channels
        scanner.feed(FloatArray(count * channels) { pcm[it] / 32768f }, count, frames)
        frames += count
    }

    fun snapshot(): AudioScan = scanner.snapshot()

    fun finish(): AudioScan = scanner.finish()
}

/**
 * Interleaved 16-bit PCM from [from] to [to] Hz by linear interpolation, fed chunk by chunk; the last frame of each chunk
 * carries over, so the output is the same however the input is split.
 */
// ponytail: no anti-alias filter, content above the new Nyquist folds back; add a windowed-sinc low pass if that is heard
class PcmResampler(from: Int, to: Int, private val channels: Int) {
    private val step = from.toDouble() / to
    private val previous = ShortArray(channels)
    private var consumed = 0L
    private var produced = 0L

    fun process(pcm: ShortArray): ShortArray {
        val end = consumed + pcm.size / channels
        val out = ShortArray(((end - consumed) / step + 2).toInt() * channels)
        var frames = 0
        while (true) {
            val position = produced * step
            val index = position.toLong()
            if (index + 1 >= end) break
            val fraction = position - index
            for (channel in 0 until channels) {
                fun at(frame: Long) = if (frame < consumed) previous[channel] else pcm[((frame - consumed) * channels).toInt() + channel]
                val a = at(index)
                out[frames * channels + channel] = (a + (at(index + 1) - a) * fraction).roundToInt().toShort()
            }
            frames++
            produced++
        }
        if (end > consumed) pcm.copyInto(previous, 0, pcm.size - channels, pcm.size)
        consumed = end
        return out.copyOf(frames * channels)
    }
}

/** dt-ios AVEditAudio, AVInfoAudioDSP and AVInfoWaveformRender arithmetic, with no device in it. */
object AVEditAudioEngine {
    const val LEVEL_FLOOR = -60.0

    /** The highest rate the platform AAC encoder takes. */
    const val AAC_MAX_RATE = 48_000

    /** dt-ios AVInfoProbe.levelShare: a level bar's share, 0 to 1, for decibels between [LEVEL_FLOOR] and full scale. */
    fun levelShare(decibels: Double): Float = (1 - decibels / LEVEL_FLOOR).coerceIn(0.0, 1.0).toFloat()

    fun audioDetail(scan: AudioScan): String =
        String.format(Locale.ROOT, "%.2f s · %d Hz · %d ch", scan.duration, scan.sampleRate, scan.channels)

    // bs.1770-4 gating blocks
    fun gatedLoudness(subBlockSums: List<Double>, subBlockFrames: Int): Double? {
        if (subBlockSums.size < 4 || subBlockFrames <= 0) return null
        val blockFrames = subBlockFrames * 4.0
        val energies = (0..subBlockSums.size - 4).map { start -> (start until start + 4).sumOf { subBlockSums[it] } / blockFrames }
        fun loudness(energy: Double) = -0.691 + 10 * log10(energy)
        val aboveAbsolute = energies.filter { loudness(it) > -70 }
        if (aboveAbsolute.isEmpty()) return null
        val gate = loudness(aboveAbsolute.sum() / aboveAbsolute.size) - 10
        val gated = energies.filter { loudness(it) > gate }
        if (gated.isEmpty()) return null
        return loudness(gated.sum() / gated.size)
    }

    /** Re-buckets peaks to a coarser width; returns them unchanged when already coarser. */
    fun resample(peaks: AVEditPeaks, buckets: Int): AVEditPeaks {
        val source = peaks.min.size
        if (buckets <= 0 || source <= 0) return AVEditPeaks(FloatArray(0), FloatArray(0))
        if (buckets >= source) return peaks
        val low = FloatArray(buckets)
        val high = FloatArray(buckets)
        for (bucket in 0 until buckets) {
            val from = bucket * source / buckets
            val to = max(from + 1, (bucket + 1) * source / buckets)
            low[bucket] = (from until to).minOf { peaks.min[it] }
            high[bucket] = (from until to).maxOf { peaks.max[it] }
        }
        return AVEditPeaks(low, high)
    }

    fun planGain(lufs: Double?, peakDb: Double, targetLufs: Double, ceilingDb: Double = -1.0): AudioGainPlan {
        if (lufs == null || !lufs.isFinite() || !peakDb.isFinite()) return AudioGainPlan(0.0, false, peakDb)
        val wanted = targetLufs - lufs
        val headroom = ceilingDb - peakDb
        val limited = wanted > headroom
        val gainDb = if (limited) headroom else wanted
        return AudioGainPlan(gainDb, limited, peakDb + gainDb)
    }

    fun dbToGain(db: Double): Float = 10.0.pow(db / 20).toFloat()

    /** First and end frame of [range] seconds at [rate]: the start rounded down, the end up. */
    fun frames(range: ClosedFloatingPointRange<Double>, rate: Int): LongRange {
        val from = max(0L, floor(range.start * rate).toLong())
        return from until max(from + 1, ceil(range.endInclusive * rate).toLong())
    }

    /**
     * dt-ios AVEditAudio.render's per-chunk step, in place: gain, then a linear fade over the first [inFrames]
     * and last [outFrames] of [total] frames, clipped. [written] is the frames rendered before this chunk.
     */
    fun shape(pcm: ShortArray, channels: Int, written: Long, total: Long, gain: Float, inFrames: Long, outFrames: Long) {
        val count = pcm.size / channels
        val fadeInEnd = max(0L, min(count.toLong(), inFrames - written)).toInt()
        val fadeOutStart = min(count.toLong(), max(0L, total - outFrames - written)).toInt()
        for (frame in 0 until count) {
            var scale = gain
            if (frame < fadeInEnd) scale *= (written + frame).toFloat() / inFrames
            // decode may overrun length
            if (outFrames > 0 && frame >= fadeOutStart) scale *= max(0f, (total - 1 - written - frame).toFloat() / outFrames)
            if (scale == 1f) continue
            for (channel in 0 until channels) {
                val index = frame * channels + channel
                val sample = (pcm[index] / 32768f * scale).coerceIn(-1f, 1f)
                pcm[index] = (sample * 32767).roundToInt().toShort()
            }
        }
    }

    /** dt-ios AudioWaveformView.envelope: the export's gain at [seconds] of a [start] to [end] selection. */
    fun envelope(seconds: Double, start: Double, end: Double, fadeIn: Double, fadeOut: Double): Double {
        var gain = 1.0
        if (fadeIn > 0) gain = min(gain, (seconds - start) / fadeIn)
        if (fadeOut > 0) gain = min(gain, (end - seconds) / fadeOut)
        return max(0.0, gain)
    }

    private fun unit(sample: Float): Double = if (sample.isFinite()) sample.coerceIn(-1f, 1f).toDouble() else 0.0

    /** dt-ios AVInfoWaveformRender.bars: one bar per peak column inside a [width] × [height] box. */
    fun bars(peaks: AVEditPeaks, width: Double, height: Double): List<AudioBar> {
        val columns = min(peaks.min.size, peaks.max.size)
        if (columns <= 0 || width <= 0 || height <= 0) return emptyList()
        val mid = height / 2
        val barWidth = width / columns
        return List(columns) { column ->
            val top = mid - unit(peaks.max[column]) * mid
            val bottom = mid - unit(peaks.min[column]) * mid
            AudioBar(column * barWidth, top, max(barWidth, 0.5), max(1.0, bottom - top))
        }
    }

    fun svg(peaks: AVEditPeaks, width: Int, height: Int, waveHex: String, backgroundHex: String): String {
        val rects = bars(peaks, width.toDouble(), height.toDouble()).joinToString("") {
            String.format(Locale.ROOT, "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\"/>", it.x, it.y, it.width, it.height)
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" viewBox=\"0 0 $width $height\">" +
            "<rect width=\"$width\" height=\"$height\" fill=\"$backgroundHex\"/><g fill=\"$waveHex\">$rects</g></svg>"
    }

    /** dt-ios WaveformGeneratorPicture.fit: [width] × [height] scaled into the area, centred; the area when either is empty. */
    fun fit(width: Double, height: Double, left: Double, top: Double, areaWidth: Double, areaHeight: Double): DoubleArray {
        if (width <= 0 || height <= 0 || areaWidth <= 0 || areaHeight <= 0) return doubleArrayOf(left, top, areaWidth, areaHeight)
        val scale = min(areaWidth / width, areaHeight / height)
        val w = width * scale
        val h = height * scale
        return doubleArrayOf(left + (areaWidth - w) / 2, top + (areaHeight - h) / 2, w, h)
    }

    /** Peak columns merged so there is about one per [points] of width: `(columns, merge)`. */
    fun merge(buckets: Int, points: Float): Pair<Int, Int> {
        val merge = max(1, ceil(buckets / max(1f, points)).toInt())
        return (buckets + merge - 1) / merge to merge
    }

    /** `"%.1f unit"`, or an en dash when missing or infinite. */
    fun decibels(value: Double?, unit: String): String =
        if (value == null || !value.isFinite()) "–" else String.format(Locale.ROOT, "%.1f %s", value, unit)

    fun signedGain(db: Double): String = String.format(Locale.ROOT, "%s%.1f dB", if (db >= 0) "+" else "", db)

    fun micros(frames: Long, rate: Int): Long = if (rate > 0) (frames * 1e6 / rate).roundToLong() else 0

    /** A decoder output buffer, from its position to its limit, as samples from -1 to 1; null for an encoding not handled. */
    fun pcmFloats(data: ByteBuffer, encoding: Int): FloatArray? {
        val bytes = data.slice().order(ByteOrder.LITTLE_ENDIAN)
        return when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> bytes.asShortBuffer().let { s -> FloatArray(s.remaining()) { s.get(it) / 32768f } }
            AudioFormat.ENCODING_PCM_FLOAT -> bytes.asFloatBuffer().let { f -> FloatArray(f.remaining()).also(f::get) }
            AudioFormat.ENCODING_PCM_32BIT -> bytes.asIntBuffer().let { s -> FloatArray(s.remaining()) { s.get(it) / 2147483648f } }
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> FloatArray(bytes.remaining() / 3) {
                val at = it * 3
                val sample = (bytes.get(at).toInt() and 0xFF) or (bytes.get(at + 1).toInt() and 0xFF shl 8) or (bytes.get(at + 2).toInt() shl 16)
                sample / 8388608f
            }
            else -> null
        }
    }
}
