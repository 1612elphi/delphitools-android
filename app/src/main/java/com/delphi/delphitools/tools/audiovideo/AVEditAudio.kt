package com.delphi.delphitools.tools.audiovideo

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.canonDisplayName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

private class StopDecode : Exception() {
    override fun fillInStackTrace() = this
}

internal interface AudioSink {
    fun write(pcm: ShortArray)
    fun finish()
    fun release()
}

internal class WavSink(output: File, private val rate: Int, private val channels: Int) : AudioSink {
    private val file = RandomAccessFile(output, "rw").apply { write(ByteArray(44)) }
    private var bytes = 0L

    override fun write(pcm: ShortArray) {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.asShortBuffer().put(pcm)
        file.write(data.array())
        bytes += data.capacity()
        check(AVEditEngine.fitsWav(bytes / (2 * channels), channels))
    }

    override fun finish() {
        file.seek(0)
        file.write(AVEditEngine.wavHeader(rate, channels, bytes))
    }

    override fun release() = file.close()
}

/** 16-bit PCM through the platform AAC encoder into an M4A, resampled to the encoder's limit when above it. */
internal class AacSink(output: File, sourceRate: Int, private val channels: Int) : AudioSink {
    private val rate = min(sourceRate, AVEditAudioEngine.AAC_MAX_RATE)
    private val resampler = if (sourceRate > rate) PcmResampler(sourceRate, rate, channels) else null
    private val codec = MediaCodec.createEncoderByType(AVEditEngine.AAC_MIME)
    private val muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var fed = 0L

    init {
        val format = MediaFormat.createAudioFormat(AVEditEngine.AAC_MIME, rate, channels)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        // ponytail: fixed 128 kb/s per channel; AVAudioFile's own default rate is not documented
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128_000 * channels)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1 shl 16)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    override fun write(input: ShortArray) {
        val pcm = resampler?.process(input) ?: input
        var offset = 0
        while (offset < pcm.size) {
            val index = codec.dequeueInputBuffer(10_000)
            if (index >= 0) {
                val buffer = codec.getInputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                val count = min(pcm.size - offset, buffer.remaining() / 2 / channels * channels)
                buffer.asShortBuffer().put(pcm, offset, count)
                codec.queueInputBuffer(index, 0, count * 2, AVEditAudioEngine.micros(fed, rate), 0)
                fed += count / channels
                offset += count
            }
            drain(false)
        }
    }

    override fun finish() {
        while (true) {
            val index = codec.dequeueInputBuffer(10_000)
            if (index >= 0) {
                codec.queueInputBuffer(index, 0, 0, AVEditAudioEngine.micros(fed, rate), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                break
            }
            drain(false)
        }
        drain(true)
        muxer.stop()
    }

    private fun drain(toEnd: Boolean) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (toEnd) 10_000 else 0)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = muxer.addTrack(codec.outputFormat)
                muxer.start()
            } else if (index >= 0) {
                val data = codec.getOutputBuffer(index)!!
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && track >= 0) {
                    data.position(info.offset)
                    data.limit(info.offset + info.size)
                    muxer.writeSampleData(track, data, info)
                }
                codec.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER && !toEnd) {
                return
            }
        }
    }

    override fun release() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { muxer.release() }
    }
}

/** dt-ios AVInfoAudioDSP.scan and AVEditAudio.render over MediaCodec PCM; every call runs on Dispatchers.IO. */
object AVEditAudio {
    /** The first audio track's duration, from the container or, failing that, the retriever. */
    suspend fun durationUs(context: Context, uri: Uri): Long = durationUs(context, uri, format(context, uri))

    private suspend fun format(context: Context, uri: Uri): MediaFormat? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            (0 until extractor.trackCount).map(extractor::getTrackFormat)
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        } finally {
            extractor.release()
        }
    }

    private suspend fun durationUs(context: Context, uri: Uri, format: MediaFormat?): Long = withContext(Dispatchers.IO) {
        val fromTrack = format?.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }?.getLong(MediaFormat.KEY_DURATION)
        fromTrack ?: MediaMetadataRetriever().run {
            try {
                setDataSource(context, uri)
                (extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            } finally {
                release()
            }
        }
    }

    private const val PARALLEL_FROM_US = 30_000_000L
    private const val WARM_UP_US = 200_000L

    /** Sampling starts where a waveform bucket spans this much; below it most packets are decoded anyway. */
    private const val SAMPLE_FROM_STEP_US = 250_000L

    private fun expectedFrames(durationUs: Long, rate: Int) = (durationUs * rate / 1e6).roundToLong()

    /** [partial] gets the scan so far at each step of [progress], from a decoding thread; never when the container has no duration. */
    suspend fun scan(
        context: Context,
        uri: Uri,
        buckets: Int,
        loudness: Boolean,
        partial: ((AudioScan) -> Unit)? = null,
        sampled: Boolean = false,
        progress: AVEditProgress = {},
    ): AudioScan {
        val format = format(context, uri)
        val durationUs = durationUs(context, uri, format)
        val mime = format?.getString(MediaFormat.KEY_MIME)
        // mp3 decodes in process
        if (!loudness && durationUs >= PARALLEL_FROM_US && mime != MediaFormat.MIMETYPE_AUDIO_MPEG) {
            val stepUs = if (sampled && mime in AVEditMedia.sampledMimes) durationUs / buckets else 0L
            return scanParts(context, uri, durationUs, buckets, min(4, Runtime.getRuntime().availableProcessors()), if (stepUs >= SAMPLE_FROM_STEP_US) stepUs else 0L, partial, progress)
        }
        var meter: AudioMeter? = null
        var span = 1L
        var reported = -1.0
        AVEditMedia.decode(context, uri) { pcm, rate, channels, _ ->
            val current = meter ?: AudioMeter(rate, channels, expectedFrames(durationUs, rate), buckets, loudness).also {
                meter = it
                span = max(1L, expectedFrames(durationUs, rate))
            }
            current.feed(pcm)
            val fraction = min(1.0, current.frames.toDouble() / span)
            if (fraction - reported >= 0.01) {
                reported = fraction
                progress(fraction)
                if (durationUs > 0) partial?.invoke(current.snapshot())
            }
        }
        return checkNotNull(meter).finish()
    }

    /** [scan] without loudness, over [parts] time segments decoded at once. */
    // ponytail: positions after a seek come from packet times, off by up to one packet; a vbr mp3 with no seek table gets estimated times. scan in one part if a waveform is seen misaligned
    private suspend fun scanParts(
        context: Context,
        uri: Uri,
        durationUs: Long,
        buckets: Int,
        parts: Int,
        stepUs: Long,
        partial: ((AudioScan) -> Unit)?,
        progress: AVEditProgress,
    ): AudioScan = coroutineScope {
        val lock = Any()
        var scanner: AudioScanner? = null
        var fed = 0L
        var reported = -1.0
        // progress estimate, 25 ms packets
        val share = if (stepUs > 0) min(1.0, AVEditMedia.SAMPLE_BURST * 25_000.0 / stepUs) else 1.0
        (0 until parts).map { part ->
            val fromUs = durationUs * part / parts
            val toUs = durationUs * (part + 1) / parts
            val last = part == parts - 1
            async {
                // first part counts from zero
                var next = if (part == 0) 0L else -1L
                // decoder drops packets after seek
                val range = if (stepUs > 0) fromUs + stepUs / 4..toUs else max(0L, fromUs - WARM_UP_US)..if (last) Long.MAX_VALUE else toUs
                AVEditMedia.decode(context, uri, range, stepUs) { pcm, rate, channels, ptsUs ->
                    val first = if (stepUs == 0L && next >= 0) next else expectedFrames(ptsUs, rate)
                    val count = pcm.size / channels
                    next = first + count
                    val skip = (expectedFrames(fromUs, rate) - first).coerceIn(0L, count.toLong()).toInt()
                    val end = if (last) first + count else min(first + count, expectedFrames(toUs, rate))
                    val keep = (end - first).toInt() - skip
                    if (keep <= 0) return@decode
                    val samples = FloatArray(keep * channels) { pcm[skip * channels + it] / 32768f }
                    synchronized(lock) {
                        val span = max(1L, expectedFrames(durationUs, rate))
                        val current = scanner ?: AudioScanner(rate, channels, 0, span, buckets, open = true, measureLoudness = false).also { scanner = it }
                        current.feed(samples, keep, first + skip)
                        fed += keep
                        val fraction = min(1.0, fed.toDouble() / (span * share))
                        if (fraction - reported >= 0.01) {
                            reported = fraction
                            progress(fraction)
                            partial?.invoke(current.snapshot())
                        }
                    }
                }
            }
        }.awaitAll()
        checkNotNull(scanner).run { if (stepUs > 0) snapshot() else finish() }
    }

    /** The first audio track, [range] of it when given, with [gainDb] then linear fades, as WAV or AAC in M4A. */
    suspend fun render(
        context: Context,
        uri: Uri,
        output: File,
        format: AudioExtractFormat,
        range: ClosedFloatingPointRange<Double>? = null,
        gainDb: Double = 0.0,
        fadeIn: Double = 0.0,
        fadeOut: Double = 0.0,
        progress: AVEditProgress,
    ) = withContext(Dispatchers.IO) {
        val durationUs = durationUs(context, uri)
        val gain = AVEditAudioEngine.dbToGain(gainDb)
        var sink: AudioSink? = null
        var window = LongRange.EMPTY
        var inFrames = 0L
        var outFrames = 0L
        var position = 0L
        var written = 0L
        var reported = 0.0
        try {
            try {
                AVEditMedia.decode(context, uri) { pcm, rate, channels, _ ->
                    val out = sink ?: run {
                        val length = max(1L, expectedFrames(durationUs, rate))
                        window = range?.let { AVEditAudioEngine.frames(it, rate) }?.let { it.first until min(it.last + 1, length) } ?: (0L until length)
                        val total = max(1L, window.last + 1 - window.first)
                        inFrames = min(total, (fadeIn * rate).roundToLong())
                        outFrames = min(total, (fadeOut * rate).roundToLong())
                        (if (format == AudioExtractFormat.Wav) WavSink(output, rate, channels) else AacSink(output, rate, channels)).also { sink = it }
                    }
                    val start = position
                    position += pcm.size / channels
                    val from = max(start, window.first)
                    // decode may overrun length
                    val to = if (range == null) position else min(position, window.last + 1)
                    if (to > from) {
                        val slice = if (from == start && to == position) pcm else pcm.copyOfRange(((from - start) * channels).toInt(), ((to - start) * channels).toInt())
                        val total = window.last + 1 - window.first
                        AVEditAudioEngine.shape(slice, channels, written, total, gain, inFrames, outFrames)
                        out.write(slice)
                        written += to - from
                        val fraction = min(1.0, written.toDouble() / max(1L, total))
                        if (fraction - reported >= 0.01) {
                            reported = fraction
                            progress(fraction)
                        }
                    }
                    if (range != null && position > window.last) throw StopDecode()
                }
            } catch (_: StopDecode) {
            }
            check(written > 0)
            checkNotNull(sink).finish()
        } finally {
            sink?.release()
        }
        progress(1.0)
    }
}

/** An audio file the audio tools work on: its name and, once decoded, its scan. */
class AVEditAudioSource(private val context: Context, private val scope: CoroutineScope) {
    var uri by mutableStateOf<Uri?>(null)
        private set
    var name by mutableStateOf("")
        private set
    var scan by mutableStateOf<AudioScan?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var scanning by mutableStateOf(false)
        private set
    private var task: Job? = null

    val baseName get() = AVEditEngine.baseName(name, "audio")
    val duration get() = scan?.duration ?: 0.0

    /**
     * [live] publishes the scan while it fills, with the container's duration, and calls [then] on the first of those.
     * [sampled] decodes a few packets per bucket of a long file: peaks, levels and duration are then approximate.
     */
    fun load(uri: Uri, buckets: Int, loudness: Boolean = false, live: Boolean = false, sampled: Boolean = false, then: (AudioScan) -> Unit = {}) {
        clear()
        this.uri = uri
        scanning = true
        task = scope.launch {
            name = withContext(Dispatchers.IO) { context.contentResolver.canonDisplayName(uri) ?: "" }
            var started = false
            val show = { partial: AudioScan ->
                scan = partial
                if (!started) then(partial)
                started = true
            }
            val result = try {
                AVEditAudio.scan(context, uri, buckets, loudness, if (live) { partial -> launch { show(partial) } } else null, sampled) { progress = it.toFloat() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            scan = result
            scanning = false
            failed = result == null
            if (!started) result?.let(then)
        }
    }

    fun clear() {
        task?.cancel()
        task = null
        scanning = false
        uri = null
        name = ""
        scan = null
        failed = false
        progress = 0f
    }
}

@Composable
internal fun AVEditAudioDrop(icon: ImageVector, onFile: (Uri) -> Unit) {
    CanonDropZone(
        "Choose audio file",
        icon = icon,
        mimeTypes = arrayOf("audio/*"),
        photos = null,
        paste = false,
        onFiles = { it.firstOrNull()?.let(onFile) },
    )
}
