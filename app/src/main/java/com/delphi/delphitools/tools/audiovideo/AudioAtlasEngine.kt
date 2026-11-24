package com.delphi.delphitools.tools.audiovideo

import com.delphi.delphitools.canon.CanonTile
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * dt-ios AVInfoAudioDSP.scan and spectrogram, fed in chunks: interleaved float frames with their frame index from the file start.
 * Frames before [startFrame] are skipped; past [spanFrames] they land in the last bucket when [open], else are skipped.
 */
class AudioScanner(
    private val rate: Int,
    private val channels: Int,
    private val startFrame: Long,
    private val spanFrames: Long,
    private val buckets: Int,
    private val open: Boolean,
    measureLoudness: Boolean,
    private val spectrogram: SpectrogramBuilder? = null,
) {
    private val low = FloatArray(buckets) { Float.POSITIVE_INFINITY }
    private val high = FloatArray(buckets) { Float.NEGATIVE_INFINITY }
    private var peak = 0f
    private var squareSum = 0.0
    private var sampleCount = 0L
    private var frames = 0L
    private val filters = if (measureLoudness) List(channels) { KWeighting(rate.toDouble()) } else emptyList()

    // bs.1770-4 table 3 for 5.1 in Android order (FL FR FC LFE BL BR): no LFE, surrounds +1.5 dB
    private val weights = if (channels == 6) doubleArrayOf(1.0, 1.0, 1.0, 0.0, 1.41, 1.41) else DoubleArray(channels) { 1.0 }
    private val subBlockFrames = (0.1 * rate).roundToInt()
    private val subBlockSums = mutableListOf<Double>()
    private var subBlockEnergy = 0.0
    private var subBlockFilled = 0

    fun feed(samples: FloatArray, count: Int, firstFrame: Long) {
        for (frame in 0 until count) {
            val position = firstFrame + frame - startFrame
            if (position < 0) continue
            if (position >= spanFrames && !open) return
            val bucket = min(buckets - 1, (position * buckets / max(1, spanFrames)).toInt())
            var energy = 0.0
            var mono = 0f
            for (channel in 0 until channels) {
                val sample = samples[frame * channels + channel]
                val magnitude = abs(sample)
                if (magnitude > peak) peak = magnitude
                squareSum += sample.toDouble() * sample
                if (sample < low[bucket]) low[bucket] = sample
                if (sample > high[bucket]) high[bucket] = sample
                mono += sample
                if (filters.isNotEmpty()) {
                    val weighted = filters[channel].process(sample.toDouble())
                    energy += weights[channel] * weighted * weighted
                }
            }
            sampleCount += channels
            frames++
            spectrogram?.feed(mono / channels, position)
            if (filters.isNotEmpty() && subBlockFrames > 0) {
                subBlockEnergy += energy
                if (++subBlockFilled == subBlockFrames) {
                    subBlockSums += subBlockEnergy
                    subBlockEnergy = 0.0
                    subBlockFilled = 0
                }
            }
        }
    }

    fun finish(): AudioScan {
        for (bucket in 0 until buckets) {
            if (low[bucket] == Float.POSITIVE_INFINITY) {
                low[bucket] = 0f
                high[bucket] = 0f
            }
        }
        val rms = if (sampleCount > 0) sqrt(squareSum / sampleCount) else 0.0
        return AudioScan(
            AVEditPeaks(low, high),
            if (peak == 0f) Double.NEGATIVE_INFINITY else 20 * log10(peak.toDouble()),
            if (rms == 0.0) Double.NEGATIVE_INFINITY else 20 * log10(rms),
            if (filters.isNotEmpty()) AVEditAudioEngine.gatedLoudness(subBlockSums, subBlockFrames) else null,
            rate,
            channels,
            if (open) frames.toDouble() / rate else spanFrames.toDouble() / rate,
        )
    }
}

/**
 * dt-ios AVInfoAudioDSP.spectrogram: [columns] FFTs of 1024 mono frames, evenly spread over [spanFrames], each as a column of
 * [rows] palette pixels, low frequencies at the bottom. Fed one frame at a time; columns it never reaches stay white.
 */
class SpectrogramBuilder(private val columns: Int, private val rows: Int, spanFrames: Long) {
    val pixels = IntArray(columns * rows) { -1 }
    private val fft = AudioAtlasEngine.FFT_SIZE
    private val hop = max(1L, (max(spanFrames, fft.toLong()) - fft) / max(1, columns - 1))
    private val ring = FloatArray(fft)
    private var filled = 0L
    private var column = 0
    private val block = DoubleArray(fft)

    fun feed(mono: Float, position: Long) {
        ring[(position % fft).toInt()] = mono
        filled++
        if (column >= columns || filled < fft || position != column * hop + fft - 1) return
        val oldest = position - fft + 1
        for (index in 0 until fft) block[index] = ring[((oldest + index) % fft).toInt()] * AudioAtlasEngine.hann[index]
        val magnitudes = AudioAtlasEngine.fftMagnitudes(block)
        val binsPerRow = (fft / 2).toDouble() / rows
        for (row in 0 until rows) {
            val bin = ((rows - 1 - row) * binsPerRow).toInt()
            val db = 20 * log10(magnitudes[min(bin, magnitudes.size - 1)] + 1e-12)
            val t = (1 - db / AudioAtlasEngine.FLOOR_DB).coerceIn(0.0, 1.0)
            pixels[row * columns + column] = AudioAtlasEngine.palette[(t * 255).roundToInt()]
        }
        column++
    }
}

/** dt-ios AVInfoAudioDSP, AudioAtlasModel and AudioAtlasRuler maths, with no device in it. */
object AudioAtlasEngine {
    const val FFT_SIZE = 1024
    const val FLOOR_DB = -90.0

    internal val hann = DoubleArray(FFT_SIZE) { 0.5 * (1 - cos(2 * PI * it / (FFT_SIZE - 1))) }

    /** 256 opaque ARGB entries: plate, green, near white. */
    internal val palette: IntArray = run {
        val stops = listOf(Triple(16.0, 18.0, 16.0), Triple(46.0, 125.0, 50.0), Triple(240.0, 255.0, 240.0))
        IntArray(256) { i ->
            val t = i / 255.0
            val segment = if (t < 0.5) 0 else 1
            val local = (t - segment * 0.5) * 2
            val from = stops[segment]
            val to = stops[segment + 1]
            val r = (from.first + (to.first - from.first) * local).toInt()
            val g = (from.second + (to.second - from.second) * local).toInt()
            val b = (from.third + (to.third - from.third) * local).toInt()
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    /** Radix-2 FFT; magnitudes of the first half only, empty unless the count is a power of two. */
    fun fftMagnitudes(samples: DoubleArray): DoubleArray {
        val n = samples.size
        if (n == 0 || n and (n - 1) != 0) return DoubleArray(0)
        val re = samples.copyOf()
        val im = DoubleArray(n)
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) re[i] = re[j].also { re[j] = re[i] }
        }
        var length = 2
        while (length <= n) {
            val angle = -2 * PI / length
            val wRe = cos(angle)
            val wIm = sin(angle)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until length / 2) {
                    val a = i + k
                    val b = a + length / 2
                    val tRe = re[b] * curRe - im[b] * curIm
                    val tIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] += tRe
                    im[a] += tIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += length
            }
            length = length shl 1
        }
        return DoubleArray(n / 2) { sqrt(re[it] * re[it] + im[it] * im[it]) / n }
    }

    private fun decibels(value: Double) = if (value.isFinite()) String.format(Locale.ROOT, "%.1f dBFS", value) else "-∞ dBFS"

    fun loudnessSection(scan: AudioScan): AVEditProbeSection = AVEditProbeSection(
        "Loudness",
        buildList {
            add(CanonTile("Peak", decibels(scan.peakDb), AVEditAudioEngine.levelShare(scan.peakDb)))
            add(CanonTile("RMS", decibels(scan.rmsDb), AVEditAudioEngine.levelShare(scan.rmsDb)))
            scan.lufs?.let { add(CanonTile("Loudness", String.format(Locale.ROOT, "%.1f LUFS", it), AVEditAudioEngine.levelShare(it))) }
        },
    )

    /** The peaks of the window from [start] to [end] seconds, cut from [overview] of the whole file. */
    fun coarseWindow(overview: AVEditPeaks, duration: Double, start: Double, end: Double): AVEditPeaks? {
        val count = overview.min.size
        if (count == 0 || duration <= 0) return null
        val from = min(count - 1, (start / duration * count).toInt())
        val to = max(from + 1, min(count, kotlin.math.ceil(end / duration * count).toInt()))
        return AVEditPeaks(overview.min.copyOfRange(from, to), overview.max.copyOfRange(from, to))
    }

    private val steps = listOf(0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 60.0, 120.0, 300.0, 600.0, 1800.0, 3600.0)

    // min dp between labels
    private const val RULER_SPACING = 64.0

    fun rulerStep(span: Double, width: Double): Double {
        if (span <= 0 || width <= 0) return steps[0]
        val least = span / width * RULER_SPACING
        return steps.firstOrNull { it >= least } ?: steps.last()
    }

    /** Seconds, then m:ss. */
    fun rulerLabel(seconds: Double, step: Double): String {
        val decimals = if (step < 0.1) 2 else if (step < 1) 1 else 0
        if (seconds < 60) return String.format(Locale.ROOT, "%.${decimals}f", seconds)
        val width = if (decimals == 0) 2 else decimals + 3
        return String.format(Locale.ROOT, "%d:%0$width.${decimals}f", seconds.toInt() / 60, seconds % 60)
    }

    /** The spectrogram scale at a quarter, half and three quarters of Nyquist. */
    fun kilohertz(sampleRate: Int, quarter: Int): String {
        val value = Math.round(sampleRate / 2.0 * quarter / 4 / 100).toDouble() / 10
        return if (value == floor(value)) "${value.toInt()} kHz" else "$value kHz"
    }
}
