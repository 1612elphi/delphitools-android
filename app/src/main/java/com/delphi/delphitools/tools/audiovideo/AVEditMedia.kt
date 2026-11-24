package com.delphi.delphitools.tools.audiovideo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.delphi.delphitools.canon.CanonTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What a video tool shows of its source before any work. */
@Immutable
data class AVEditFacts(
    val duration: Double,
    val width: Int,
    val height: Int,
    val fps: Double,
    val audioTracks: Int,
) {
    val meta get() = AVEditEngine.meta(width, height, fps, duration)
}

/** dt-ios AVInfoAudioDSP.Peaks: the lowest and highest sample, -1 to 1, of each equal span of the file. */
class AVEditPeaks(val min: FloatArray, val max: FloatArray)

@Immutable
data class AVEditProbeSection(val title: String, val tiles: List<CanonTile>)

typealias AVEditProgress = (Double) -> Unit

private fun MediaFormat.long(key: String): Long? = if (containsKey(key)) getLong(key) else null

private fun MediaFormat.int(key: String): Int? = if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

private fun MediaFormat.fps(): Double? =
    if (!containsKey(MediaFormat.KEY_FRAME_RATE)) null
    else runCatching { getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() }.getOrElse { runCatching { getFloat(MediaFormat.KEY_FRAME_RATE).toDouble() }.getOrNull() }

private fun MediaExtractor.formats(): List<MediaFormat> = (0 until trackCount).map(::getTrackFormat)

private fun List<MediaFormat>.mimes(): List<String?> = map { it.getString(MediaFormat.KEY_MIME) }

/** MediaExtractor, MediaMuxer, MediaCodec and MediaMetadataRetriever work of the video tools; every call runs on Dispatchers.IO. */
object AVEditMedia {
    suspend fun facts(context: Context, uri: Uri): AVEditFacts? = withContext(Dispatchers.IO) {
        runCatching {
            val formats = MediaExtractor().run {
                try {
                    setDataSource(context, uri, null)
                    formats()
                } finally {
                    release()
                }
            }
            val mimes = formats.mimes()
            val video = formats[AVEditEngine.videoTracks(mimes).first()]
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                fun meta(key: Int) = retriever.extractMetadata(key)?.toDoubleOrNull()
                val duration = (meta(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: 0.0) / 1000
                if (duration <= 0) return@runCatching null
                val width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
                val height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
                val turned = (meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0) % 180 != 0
                val counted = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.let { it / duration }
                val fps = video.fps()?.takeIf { it > 0 } ?: counted?.takeIf { it > 0 } ?: 30.0
                AVEditFacts(
                    duration,
                    if (turned) height else width,
                    if (turned) width else height,
                    fps,
                    mimes.count { it?.startsWith("audio/") == true },
                )
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    /** Frames from the middle of [count] equal spans, 120 px high. */
    suspend fun thumbnails(context: Context, uri: Uri, duration: Double, count: Int): List<ImageBitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            AVEditEngine.sheetTimes(duration, count).mapNotNull { seconds ->
                ensureActive()
                // sync frames repeat when spans are shorter than a GOP
                frame(retriever, seconds, 120, exact = duration / count < 2)?.asImageBitmap()
            }
        } catch (_: RuntimeException) {
            emptyList()
        } finally {
            retriever.release()
        }
    }

    /** A frame at most [height] px high, or full size when 0; inexact takes the nearest sync frame, which skips a decode run. */
    internal fun frame(retriever: MediaMetadataRetriever, seconds: Double, height: Int, exact: Boolean): Bitmap? {
        val us = AVEditEngine.micros(seconds)
        val option = if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        return if (height > 0) retriever.getScaledFrameAtTime(us, option, height * 4, height) else retriever.getFrameAtTime(us, option)
    }

    private fun writePng(image: Bitmap, output: File) {
        output.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    /** The exact frame at [seconds] as a full-size PNG at [output]; returns a thumbnail of it. */
    suspend fun still(context: Context, uri: Uri, seconds: Double, output: File): ImageBitmap = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val image = checkNotNull(frame(retriever, seconds, 0, exact = true))
            output.parentFile?.mkdirs()
            writePng(image, output)
            val scale = min(216f / image.width, 132f / image.height).coerceAtMost(1f)
            val thumbnail = Bitmap.createScaledBitmap(image, max(1, (image.width * scale).roundToInt()), max(1, (image.height * scale).roundToInt()), true)
            if (thumbnail !== image) image.recycle()
            thumbnail.asImageBitmap()
        } finally {
            retriever.release()
        }
    }

    /** dt-ios AVEditFrames.contactSheet: [count] frames 320 px wide, four to a row, each over its timestamp. */
    suspend fun contactSheet(context: Context, uri: Uri, duration: Double, count: Int, output: File, progress: AVEditProgress) = withContext(Dispatchers.IO) {
        val (columns, rows) = AVEditEngine.sheetGrid(count)
        val width = AVEditEngine.SHEET_THUMB_WIDTH
        val seconds = AVEditEngine.sheetTimes(duration, count)
        val retriever = MediaMetadataRetriever()
        val thumbs = mutableListOf<Bitmap>()
        try {
            retriever.setDataSource(context, uri)
            for ((index, time) in seconds.withIndex()) {
                ensureActive()
                val us = AVEditEngine.micros(time)
                // a failed frame retries once
                val thumb = retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST, width, width * 8)
                    ?: retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST, width, width * 8)
                thumbs += checkNotNull(thumb)
                progress((index + 1).toDouble() / count)
            }
            val first = thumbs.first()
            val height = (width.toFloat() * first.height / first.width).roundToInt()
            val sheet = Bitmap.createBitmap(columns * width, rows * height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.BLACK)
            val label = AVEditEngine.SHEET_LABEL_HEIGHT.toFloat()
            val band = Paint().apply { color = Color.argb(153, 0, 0, 0) }
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 13f
                typeface = Typeface.MONOSPACE
            }
            val image = Paint(Paint.FILTER_BITMAP_FLAG)
            for ((index, thumb) in thumbs.withIndex()) {
                val left = (index % columns * width).toFloat()
                val top = (index / columns * height).toFloat()
                canvas.drawBitmap(thumb, null, RectF(left, top, left + width, top + height), image)
                canvas.drawRect(left, top + height - label, left + width, top + height, band)
                val baseline = top + height - label / 2 - (text.descent() + text.ascent()) / 2
                canvas.drawText(AVEditEngine.timestamp(seconds[index]), left + 8, baseline, text)
            }
            writePng(sheet, output)
            sheet.recycle()
        } finally {
            thumbs.forEach(Bitmap::recycle)
            retriever.release()
        }
    }

    /**
     * Copies the tracks [pick] chooses into an MP4 at [output], sample by sample with no re-encode.
     * With [range] in seconds, the copy starts on the video's sync sample at or before the range start.
     */
    // ponytail: sample copy, so a trim starts on the sync frame at or before the requested in point; iOS re-encodes for a frame-exact start; MediaCodec re-encode if that matters
    suspend fun remux(
        context: Context,
        uri: Uri,
        output: File,
        pick: (List<String?>) -> List<Int>,
        range: ClosedFloatingPointRange<Double>? = null,
        progress: AVEditProgress,
    ) = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, uri, null)
            val formats = extractor.formats()
            val mimes = formats.mimes()
            val chosen = pick(mimes)
            require(chosen.isNotEmpty())
            val video = chosen.firstOrNull { mimes[it]?.startsWith("video/") == true }
            var fromUs = 0L
            var endUs: Long? = null
            var keptVideo = Int.MAX_VALUE
            if (range != null) {
                val end = AVEditEngine.micros(range.endInclusive)
                endUs = end
                fromUs = AVEditEngine.micros(range.start)
                if (video != null) {
                    extractor.selectTrack(video)
                    extractor.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    fromUs = max(0, extractor.sampleTime)
                    val samples = mutableListOf<AVEditSample>()
                    while (extractor.sampleTime >= 0) {
                        ensureActive()
                        val sample = AVEditSample(extractor.sampleTime, extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                        if (samples.isNotEmpty() && sample.sync && sample.ptsUs >= end) break
                        samples += sample
                        extractor.advance()
                    }
                    keptVideo = AVEditEngine.keptVideoSamples(samples, end)
                    extractor.unselectTrack(video)
                }
            }
            val toUs = endUs ?: chosen.maxOf { formats[it].long(MediaFormat.KEY_DURATION) ?: 0 }

            val mux = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            val outTrack = chosen.associateWith { mux.addTrack(formats[it]) }
            video?.let { formats[it].int(MediaFormat.KEY_ROTATION) }?.let(mux::setOrientationHint)
            chosen.forEach(extractor::selectTrack)
            extractor.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            mux.start()

            var buffer = ByteBuffer.allocateDirect(1 shl 20)
            val info = MediaCodec.BufferInfo()
            val done = mutableSetOf<Int>()
            var videoSamples = 0
            var reported = 0.0
            while (done.size < chosen.size) {
                ensureActive()
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val pts = extractor.sampleTime
                val shifted = when {
                    track == video && videoSamples++ >= keptVideo -> null.also { done += track }
                    track == video -> AVEditEngine.shifted(pts, fromUs, null)
                    else -> AVEditEngine.shifted(pts, fromUs, endUs).also { if (endUs != null && pts >= endUs) done += track }
                }
                if (shifted != null) {
                    if (extractor.sampleSize > buffer.capacity()) buffer = ByteBuffer.allocateDirect(extractor.sampleSize.toInt())
                    val size = extractor.readSampleData(buffer, 0)
                    val sync = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                    info.set(0, size, shifted, if (sync) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    mux.writeSampleData(outTrack.getValue(track), buffer, info)
                    val fraction = AVEditEngine.fraction(pts, fromUs, toUs)
                    if (fraction - reported >= 0.01) {
                        reported = fraction
                        progress(fraction)
                    }
                }
                extractor.advance()
            }
            mux.stop()
            progress(1.0)
        } finally {
            extractor.release()
            runCatching { muxer?.release() }
        }
    }

    /**
     * Decodes the first audio track to 16-bit interleaved PCM: [chunk] gets each buffer with its sample rate,
     * channel count and start time. Returns the track's duration in microseconds.
     */
    private suspend fun decode(
        context: Context,
        uri: Uri,
        chunk: (pcm: ShortArray, rate: Int, channels: Int, ptsUs: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val formats = extractor.formats()
            val track = AVEditEngine.firstAudioTrack(formats.mimes()) ?: error("no audio")
            val format = formats[track]
            extractor.selectTrack(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            var rate = format.int(MediaFormat.KEY_SAMPLE_RATE) ?: 44100
            var channels = format.int(MediaFormat.KEY_CHANNEL_COUNT) ?: 2
            var float = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                ensureActive()
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val size = extractor.readSampleData(decoder.getInputBuffer(index)!!, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, 10_000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = decoder.outputFormat
                    rate = out.int(MediaFormat.KEY_SAMPLE_RATE) ?: rate
                    channels = out.int(MediaFormat.KEY_CHANNEL_COUNT) ?: channels
                    float = out.int(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (index >= 0) {
                    val data = decoder.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                    data.position(info.offset)
                    data.limit(info.offset + info.size)
                    val pcm = if (float) {
                        val floats = data.asFloatBuffer()
                        ShortArray(floats.remaining()) { (floats.get(it).coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort() }
                    } else {
                        ShortArray(info.size / 2).also { data.asShortBuffer().get(it) }
                    }
                    decoder.releaseOutputBuffer(index, false)
                    if (pcm.isNotEmpty()) chunk(pcm, rate, channels, info.presentationTimeUs)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            format.long(MediaFormat.KEY_DURATION) ?: 0L
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    /** The first audio track as 16-bit PCM WAV; fails past the 4 GB RIFF limit. */
    suspend fun wav(context: Context, uri: Uri, output: File, duration: Double, progress: AVEditProgress) {
        RandomAccessFile(output, "rw").use { file ->
            file.write(ByteArray(44))
            var bytes = 0L
            var format = 0 to 0
            var reported = 0.0
            val durationUs = AVEditEngine.micros(duration)
            decode(context, uri) { pcm, rate, channels, ptsUs ->
                format = rate to channels
                val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                data.asShortBuffer().put(pcm)
                file.write(data.array())
                bytes += data.capacity()
                check(AVEditEngine.fitsWav(bytes / (2 * channels), channels))
                val fraction = AVEditEngine.fraction(ptsUs, 0, durationUs)
                if (fraction - reported >= 0.01) {
                    reported = fraction
                    progress(fraction)
                }
            }
            file.seek(0)
            file.write(AVEditEngine.wavHeader(format.first, format.second, bytes))
        }
        progress(1.0)
    }

    suspend fun peaks(context: Context, uri: Uri, duration: Double, buckets: Int): AVEditPeaks? = runCatching {
        val low = FloatArray(buckets) { Float.POSITIVE_INFINITY }
        val high = FloatArray(buckets) { Float.NEGATIVE_INFINITY }
        val durationUs = AVEditEngine.micros(duration)
        decode(context, uri) { pcm, rate, channels, ptsUs ->
            for (frame in 0 until pcm.size / channels) {
                val bucket = AVEditEngine.bucket(ptsUs + frame * 1_000_000L / rate, durationUs, buckets)
                for (channel in 0 until channels) {
                    val sample = pcm[frame * channels + channel] / 32768f
                    if (sample < low[bucket]) low[bucket] = sample
                    if (sample > high[bucket]) high[bucket] = sample
                }
            }
        }
        for (bucket in 0 until buckets) {
            if (low[bucket] == Float.POSITIVE_INFINITY) {
                low[bucket] = 0f
                high[bucket] = 0f
            }
        }
        AVEditPeaks(low, high)
    }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else null }

    /** dt-ios AVInfoProbe.probe: General, then a section per track, from what MediaExtractor reports. */
    suspend fun probe(context: Context, file: File): List<AVEditProbeSection> = withContext(Dispatchers.IO) {
        runCatching {
            val formats = MediaExtractor().run {
                try {
                    setDataSource(file.path)
                    formats()
                } finally {
                    release()
                }
            }
            val mimes = formats.mimes()
            val bytes = file.length()
            val duration = (formats.maxOfOrNull { it.long(MediaFormat.KEY_DURATION) ?: 0 } ?: 0) / 1e6
            val counts = listOf("video", "audio", "text").mapNotNull { kind ->
                val count = mimes.count { it?.startsWith("$kind/") == true }
                if (count > 0) "$count $kind" else null
            }
            val general = buildList {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)?.let { add(CanonTile("Container", it)) }
                if (bytes > 0) add(CanonTile("Size", Formatter.formatShortFileSize(context, bytes)))
                if (duration > 0) {
                    add(CanonTile("Duration", AVEditEngine.timestamp(duration)))
                    if (bytes > 0) add(CanonTile("Overall bitrate", AVEditEngine.bitrate(bytes * 8 / duration)))
                }
                if (counts.isNotEmpty()) add(CanonTile("Streams", counts.joinToString(" · ")))
            }
            val sections = mutableListOf(AVEditProbeSection("General", general))
            for (kind in listOf("video", "audio")) {
                formats.filterIndexed { index, _ -> mimes[index]?.startsWith("$kind/") == true }.forEachIndexed { index, format ->
                    val title = "${kind.replaceFirstChar(Char::uppercaseChar)} #${index + 1}"
                    sections += AVEditProbeSection(title, trackTiles(format))
                }
            }
            sections.filter { it.tiles.isNotEmpty() }
        }.getOrElse { emptyList() }
    }

    private fun trackTiles(format: MediaFormat): List<CanonTile> = buildList {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return@buildList
        add(CanonTile("Codec", AVEditEngine.codecName(mime)))
        add(CanonTile("Codec ID", mime))
        val seconds = (format.long(MediaFormat.KEY_DURATION) ?: 0) / 1e6
        if (mime.startsWith("video/")) {
            val rotation = format.int(MediaFormat.KEY_ROTATION) ?: 0
            val turned = rotation % 180 != 0
            val width = format.int(MediaFormat.KEY_WIDTH) ?: 0
            val height = format.int(MediaFormat.KEY_HEIGHT) ?: 0
            val (w, h) = if (turned) height to width else width to height
            if (w > 0 && h > 0) add(CanonTile("Frame size", "$w × $h (${AVEditEngine.ratio(w.toDouble() / h)})"))
            if (rotation != 0) add(CanonTile("Rotation", "${(rotation + 360) % 360}°"))
            format.fps()?.takeIf { it > 0 }?.let { fps ->
                add(CanonTile("Frame rate", AVEditEngine.fpsLabel(fps)))
                if (seconds > 0) add(CanonTile("Frames", "${(seconds * fps).roundToInt()}"))
            }
        } else {
            format.int(MediaFormat.KEY_CHANNEL_COUNT)?.let { add(CanonTile("Channels", "$it")) }
            format.int(MediaFormat.KEY_SAMPLE_RATE)?.takeIf { it > 0 }?.let { add(CanonTile("Sample rate", "$it Hz")) }
            if (mime == "audio/raw" && (format.int(MediaFormat.KEY_PCM_ENCODING) ?: AudioFormat.ENCODING_PCM_16BIT) == AudioFormat.ENCODING_PCM_16BIT) {
                add(CanonTile("Bit depth", "16 bit"))
            }
        }
        format.int(MediaFormat.KEY_BIT_RATE)?.takeIf { it > 0 }?.let { add(CanonTile("Bitrate", AVEditEngine.bitrate(it.toDouble()))) }
        if (seconds > 0) add(CanonTile("Duration", AVEditEngine.timestamp(seconds)))
        format.getString(MediaFormat.KEY_LANGUAGE)?.takeIf { it != "und" }?.let { add(CanonTile("Language", it)) }
    }
}
