package com.delphi.delphitools.tools.audiovideo

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** A video sample's presentation time and whether it is a sync sample, in decode order. */
data class AVEditSample(val ptsUs: Long, val sync: Boolean)

/** dt-ios AVEditExport and AVEditFrames: names, times and sample arithmetic of the video tools, with no device in it. */
object AVEditEngine {
    const val MINIMUM_SPAN = 0.01
    const val AAC_MIME = "audio/mp4a-latm"

    fun baseName(name: String, fallback: String): String = name.substringBeforeLast('.').ifEmpty { fallback }

    /** `HH:MM:SS.mmm`, as the web's `formatTimestamp(ms, '.')`. */
    fun timestamp(seconds: Double): String {
        val t = (max(0.0, seconds) * 1000).roundToLong()
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", t / 3_600_000, t / 60_000 % 60, t / 1000 % 60, t % 1000)
    }

    fun fileStamp(seconds: Double): String = timestamp(seconds).replace(':', '-').replace('.', '-')

    fun fpsLabel(fps: Double): String = "${String.format(Locale.ROOT, "%.3f", fps).trimEnd('0').trimEnd('.')} fps"

    fun meta(width: Int, height: Int, fps: Double, duration: Double): String =
        if (duration > 0) "$width × $height · ${fpsLabel(fps)} · ${timestamp(duration)}" else ""

    fun bitrate(bitsPerSecond: Double): String =
        if (bitsPerSecond >= 1e6) String.format(Locale.ROOT, "%.2f Mb/s", bitsPerSecond / 1e6) else "${(bitsPerSecond / 1e3).roundToInt()} kb/s"

    private val ratios = listOf(
        16.0 / 9 to "16:9", 4.0 / 3 to "4:3", 9.0 / 16 to "9:16", 1.0 to "1:1", 21.0 / 9 to "21:9",
        2.39 to "2.39:1", 2.35 to "2.35:1", 1.85 to "1.85:1", 3.0 / 2 to "3:2", 5.0 / 4 to "5:4",
    )

    fun ratio(value: Double): String = ratios.firstOrNull { abs(it.first - value) < 0.01 }?.second ?: String.format(Locale.ROOT, "%.2f:1", value)

    private val codecNames = mapOf(
        "video/avc" to "AVC / H.264", "video/hevc" to "HEVC / H.265", "video/av01" to "AV01",
        "video/x-vnd.on2.vp9" to "VP9", "video/x-vnd.on2.vp8" to "VP8", "video/mp4v-es" to "MPEG-4 Visual",
        "video/3gpp" to "H.263", "video/dolby-vision" to "Dolby Vision", AAC_MIME to "AAC", "audio/mpeg" to "MP3",
        "audio/raw" to "Linear PCM", "audio/opus" to "Opus", "audio/vorbis" to "Vorbis", "audio/flac" to "FLAC",
        "audio/ac3" to "AC-3", "audio/eac3" to "E-AC-3", "audio/3gpp" to "AMR-NB", "audio/amr-wb" to "AMR-WB",
        "audio/g711-alaw" to "A-law", "audio/g711-mlaw" to "µ-law",
    )

    fun codecName(mime: String): String = codecNames[mime] ?: mime.substringAfter('/').uppercase(Locale.ROOT)

    fun videoTracks(mimes: List<String?>): List<Int> = mimes.indices.filter { mimes[it]?.startsWith("video/") == true }

    fun mediaTracks(mimes: List<String?>): List<Int> = mimes.indices.filter { mimes[it]?.startsWith("video/") == true || mimes[it]?.startsWith("audio/") == true }

    fun firstAudioTrack(mimes: List<String?>): Int? = mimes.indices.firstOrNull { mimes[it]?.startsWith("audio/") == true }

    fun micros(seconds: Double): Long = (seconds * 1e6).roundToLong()

    /** dt-ios VideoTrimmer select: in and out kept inside the file and at least [MINIMUM_SPAN] apart. */
    fun select(start: Double, end: Double, duration: Double): Pair<Double, Double> {
        val from = min(max(0.0, start), max(0.0, end - MINIMUM_SPAN))
        return from to max(min(duration, end), min(duration, from + MINIMUM_SPAN))
    }

    /**
     * How many video samples a trim to [endUs] keeps, counted in decode order from the sync sample the copy starts on:
     * up to the last sample shown before [endUs], so frames it refers to stay in; never past the next sync sample at or after [endUs].
     */
    fun keptVideoSamples(samples: List<AVEditSample>, endUs: Long): Int {
        var kept = 0
        for ((index, sample) in samples.withIndex()) {
            if (index > 0 && sample.sync && sample.ptsUs >= endUs) break
            if (sample.ptsUs < endUs) kept = index + 1
        }
        return kept
    }

    /** A copied sample's time in the output, or null to drop it: before the copy start (an open GOP's lead-in) or, for audio, at or after the end. */
    fun shifted(ptsUs: Long, fromUs: Long, endUs: Long?): Long? =
        if (ptsUs < fromUs || endUs != null && ptsUs >= endUs) null else ptsUs - fromUs

    fun fraction(ptsUs: Long, fromUs: Long, toUs: Long): Double = if (toUs > fromUs) ((ptsUs - fromUs).toDouble() / (toUs - fromUs)).coerceIn(0.0, 1.0) else 0.0

    // riff sizes are 32-bit
    fun fitsWav(frames: Long, channels: Int): Boolean = frames <= (0xFFFF_FFFFL - 44) / (2 * max(1, channels))

    /** A 16-bit little-endian PCM WAV header for [dataBytes] of samples. */
    fun wavHeader(sampleRate: Int, channels: Int, dataBytes: Long): ByteArray {
        val out = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()).putInt((36 + dataBytes).toInt()).put("WAVE".toByteArray())
        out.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort()).putInt(sampleRate)
        out.putInt(sampleRate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        out.put("data".toByteArray()).putInt(dataBytes.toInt())
        return out.array()
    }

    fun bucket(ptsUs: Long, durationUs: Long, buckets: Int): Int =
        if (durationUs <= 0) 0 else (ptsUs * buckets / durationUs).toInt().coerceIn(0, buckets - 1)

    /** The middle of `count` equal spans, which keeps clear of the first and last frame. */
    fun sheetTimes(duration: Double, count: Int): List<Double> = List(max(0, count)) { duration * (it + 0.5) / count }

    const val SHEET_COLUMNS = 4
    const val SHEET_THUMB_WIDTH = 320
    const val SHEET_LABEL_HEIGHT = 22

    /** Columns and rows of a contact sheet of [count] frames. */
    fun sheetGrid(count: Int): Pair<Int, Int> {
        val columns = min(SHEET_COLUMNS, count)
        return columns to if (columns > 0) ceil(count.toDouble() / columns).toInt() else 0
    }

    /** Indices of `total` thumbnails, evenly spread, for `count` cells. */
    fun spread(count: Int, total: Int): List<Int> =
        if (count <= 0 || total <= 0) emptyList() else List(count) { min(total - 1, ((it + 0.5) / count * total).toInt()) }
}
