package com.delphi.delphitools.tools.audiovideo

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonReadoutBand
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.canonSidewaysPan
import com.delphi.delphitools.tools.images.ImageFiles
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val OVERVIEW_BUCKETS = 1000
private const val MINIMUM_SPAN = 0.05
private const val SETTLE_MS = 200L
private const val SPECTROGRAM_COLUMNS = 512
private const val SPECTROGRAM_ROWS = 256
private const val ARTWORK_EDGE = 512
private val WAVEFORM_HEIGHT_SHEET = 120.dp
private val RULER_HEIGHT = 24.dp
private val SPECTROGRAM_HEIGHT = 160.dp

// palette floor, both themes
private val PLATE = Color(16, 18, 16)

/** Decoding of the first audio track for audio-atlas and voice-recorder; every call runs on Dispatchers.IO. */
internal object AudioAtlasMedia {
    /**
     * Decodes from [fromUs] to [toUs] (null: to the end). [chunk] gets interleaved floats with the index of their first frame
     * from the file start, the sample rate and channel count; [onStart] runs once before the first chunk with the track's length in µs.
     */
    suspend fun decode(
        context: Context,
        uri: Uri,
        fromUs: Long,
        toUs: Long?,
        onStart: (rate: Int, channels: Int, durationUs: Long) -> Unit,
        chunk: (samples: FloatArray, frames: Int, firstFrame: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
            val track = AVEditEngine.firstAudioTrack(formats.map { it.getString(MediaFormat.KEY_MIME) }) ?: error("no audio")
            val format = formats[track]
            extractor.selectTrack(track)
            if (fromUs > 0) extractor.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            fun MediaFormat.int(key: String, fallback: Int) = if (containsKey(key)) getInteger(key) else fallback
            var rate = format.int(MediaFormat.KEY_SAMPLE_RATE, 44100)
            var channels = format.int(MediaFormat.KEY_CHANNEL_COUNT, 2)
            var float = false
            var started = false
            var next = -1L
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                ensureActive()
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val size = extractor.readSampleData(decoder.getInputBuffer(index)!!, 0)
                        if (size < 0 || toUs != null && extractor.sampleTime > toUs) {
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
                    rate = out.int(MediaFormat.KEY_SAMPLE_RATE, rate)
                    channels = out.int(MediaFormat.KEY_CHANNEL_COUNT, channels)
                    float = out.int(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (index >= 0) {
                    val data = decoder.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                    data.position(info.offset)
                    data.limit(info.offset + info.size)
                    val samples = if (float) {
                        val floats = data.asFloatBuffer()
                        FloatArray(floats.remaining()).also(floats::get)
                    } else {
                        val shorts = data.asShortBuffer()
                        FloatArray(shorts.remaining()) { shorts.get(it) / 32768f }
                    }
                    decoder.releaseOutputBuffer(index, false)
                    val frames = samples.size / max(1, channels)
                    if (frames > 0) {
                        if (!started) {
                            started = true
                            onStart(rate, channels, durationUs)
                        }
                        // the first buffer places the run; later ones follow on without drift
                        if (next < 0) next = if (fromUs > 0) (info.presentationTimeUs * rate / 1_000_000.0).roundToLong() else 0L
                        chunk(samples, frames, next)
                        next += frames
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    /**
     * dt-ios AVInfoAudioDSP.scan over the whole file ([range] null) or a window, with a spectrogram of the same span when [columns] > 0.
     * Null when the file has no decodable audio.
     */
    suspend fun scan(
        context: Context,
        uri: Uri,
        range: ClosedFloatingPointRange<Double>?,
        buckets: Int,
        measureLoudness: Boolean = false,
        columns: Int = 0,
    ): Pair<AudioScan, IntArray?>? = runCatching {
        var scanner: AudioScanner? = null
        var spectrogram: SpectrogramBuilder? = null
        decode(
            context,
            uri,
            AVEditEngine.micros(range?.start ?: 0.0),
            range?.let { AVEditEngine.micros(it.endInclusive) },
            onStart = { rate, channels, durationUs ->
                val start = ((range?.start ?: 0.0) * rate).roundToLong()
                val end = range?.let { (it.endInclusive * rate).roundToLong() } ?: (durationUs * rate / 1_000_000)
                val span = max(1L, end - start)
                spectrogram = if (columns > 0 && span > AudioAtlasEngine.FFT_SIZE) SpectrogramBuilder(columns, SPECTROGRAM_ROWS, span) else null
                scanner = AudioScanner(rate, channels, start, span, buckets, range == null, measureLoudness, spectrogram)
            },
        ) { samples, frames, first -> scanner?.feed(samples, frames, first) }
        scanner?.finish()?.let { it to spectrogram?.pixels }
    }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else null }
}

/** dt-ios AudioAtlasModel: the file's facts and loudness, an overview, and a zoomable window over its waveform and spectrogram. */
class AudioAtlasModel(application: Application) : AndroidViewModel(application) {
    val playback = AVEditPlayback(application, viewModelScope)
    var uri by mutableStateOf<Uri?>(null)
        private set
    var name by mutableStateOf("")
        private set
    var sections by mutableStateOf<List<AVEditProbeSection>>(emptyList())
        private set
    var tags by mutableStateOf(AtlasTags())
        private set
    var artwork by mutableStateOf<ImageBitmap?>(null)
        private set
    var overview by mutableStateOf<AVEditPeaks?>(null)
        private set
    var window by mutableStateOf<AVEditPeaks?>(null)
        private set
    var spectrogram by mutableStateOf<ImageBitmap?>(null)
        private set
    var spectrogramRange by mutableStateOf(0.0..0.0)
        private set
    var sampleRate by mutableStateOf(0)
        private set
    var detail by mutableStateOf("")
        private set
    var duration by mutableDoubleStateOf(0.0)
        private set
    var viewStart by mutableDoubleStateOf(0.0)
        private set
    var viewEnd by mutableDoubleStateOf(0.0)
        private set
    var busy by mutableStateOf(false)
        private set
    var rendering by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private var work: Job? = null
    private var settle: Job? = null
    private var rangeSettle: Job? = null
    private var fullSpectrogram: ImageBitmap? = null

    val span get() = max(viewEnd - viewStart, 0.0)
    val isZoomed get() = viewStart > 0 || viewEnd < duration - 1e-6

    /** Loop: the window; else the file. */
    private val playRange get() = if (playback.looping) viewStart..max(viewStart, viewEnd) else 0.0..duration

    fun load(uri: Uri) {
        // failed load keeps nothing
        clear()
        this.uri = uri
        busy = true
        val context = getApplication<Application>()
        work = viewModelScope.launch {
            name = withContext(Dispatchers.IO) { context.contentResolver.canonDisplayName(uri) ?: "" }
            val probed = async { AtlasProbe.probe(context, uri) }
            val scanned = AudioAtlasMedia.scan(context, uri, null, OVERVIEW_BUCKETS, measureLoudness = true, columns = SPECTROGRAM_COLUMNS)
            val probe = probed.await()
            busy = false
            val (scan, pixels) = scanned ?: run {
                failed = true
                return@launch
            }
            duration = scan.duration
            sampleRate = scan.sampleRate
            detail = AudioAtlasEngine.detail(scan)
            viewStart = 0.0
            viewEnd = scan.duration
            overview = scan.peaks
            window = scan.peaks
            tags = probe?.tags ?: AtlasTags()
            artwork = withContext(Dispatchers.IO) {
                tags.artwork?.let { ImageFiles.decode(ImageDecoder.createSource(ByteBuffer.wrap(it)), ARTWORK_EDGE) }?.image?.asImageBitmap()
            }
            sections = (probe?.sections ?: emptyList()) + AudioAtlasEngine.loudnessSection(scan)
            fullSpectrogram = pixels?.let(::image)
            spectrogram = fullSpectrogram
            spectrogramRange = 0.0..scan.duration
            playback.open(uri, playRange)
        }
    }

    private fun image(pixels: IntArray) = Bitmap.createBitmap(pixels, SPECTROGRAM_COLUMNS, SPECTROGRAM_ROWS, Bitmap.Config.ARGB_8888).asImageBitmap()

    fun clear() {
        playback.release()
        work?.cancel()
        settle?.cancel()
        rangeSettle?.cancel()
        rendering = false
        uri = null
        name = ""
        sections = emptyList()
        tags = AtlasTags()
        artwork = null
        overview = null
        window = null
        spectrogram = null
        fullSpectrogram = null
        sampleRate = 0
        detail = ""
        duration = 0.0
        viewStart = 0.0
        viewEnd = 0.0
        failed = false
        busy = false
    }

    // unzoomed window and spectrogram are the load's
    private fun refreshWindow() {
        work?.cancel()
        val uri = uri ?: return
        if (duration <= 0) return
        if (!isZoomed) {
            window = overview
            spectrogram = fullSpectrogram
            spectrogramRange = 0.0..duration
            rendering = false
            return
        }
        val range = viewStart..max(viewStart + MINIMUM_SPAN, viewEnd)
        val fallback = overview
        rendering = true
        work = viewModelScope.launch {
            val scanned = AudioAtlasMedia.scan(getApplication(), uri, range, OVERVIEW_BUCKETS, columns = SPECTROGRAM_COLUMNS)
            window = scanned?.first?.peaks ?: fallback
            spectrogram = scanned?.second?.let(::image)
            spectrogramRange = range
            rendering = false
        }
    }

    private fun setWindow(start: Double, end: Double) {
        val from = max(0.0, start)
        val to = min(duration, end)
        if (to - from < MINIMUM_SPAN || from == viewStart && to == viewEnd) return
        viewStart = from
        viewEnd = to
        // coarse now, rescan later
        window = AudioAtlasEngine.coarseWindow(overview ?: return, duration, from, to)
        settle?.cancel()
        settle = viewModelScope.launch {
            delay(SETTLE_MS)
            refreshWindow()
        }
        if (playback.looping) settleRange()
    }

    // moves rebuild nothing, but a range change mid-play waits for the window to rest
    private fun settleRange() {
        rangeSettle?.cancel()
        rangeSettle = viewModelScope.launch {
            delay(250)
            if (playback.looping) playback.range = playRange
        }
    }

    fun moveWindow(start: Double, span: Double) {
        val length = min(max(span, MINIMUM_SPAN), duration)
        val from = min(max(0.0, start), duration - length)
        setWindow(from, from + length)
    }

    fun zoomIn() {
        val centre = (viewStart + viewEnd) / 2
        val half = max(MINIMUM_SPAN / 2, span / 4)
        setWindow(centre - half, centre + half)
    }

    fun zoomOut() {
        val centre = (viewStart + viewEnd) / 2
        setWindow(centre - span, centre + span)
    }

    fun fit() = setWindow(0.0, duration)

    fun toggleLoop() {
        playback.looping = !playback.looping
        playback.range = playRange
    }

    fun seek(seconds: Double) {
        if (duration > 0) playback.scrub(seconds)
    }

    override fun onCleared() = clear()
}

@Composable
fun AudioAtlas(model: AudioAtlasModel = viewModel()) {
    val playback = model.playback
    AVEditPageEffects(playback, model::clear)
    val loaded = model.uri != null
    val ready = model.duration > 0

    val controls = CanonSection.of {
        CanonRows {
            if (!loaded) {
                CanonDropZone(
                    "Choose audio file",
                    icon = Icons.Outlined.GraphicEq,
                    mimeTypes = arrayOf("audio/*"),
                    photos = null,
                    paste = false,
                    onFiles = { it.firstOrNull()?.let(model::load) },
                )
            } else {
                CanonHeading("Source")
                AVEditFileRow(model.name, model.detail, if (model.failed) "File couldn't be decoded. Try a WAV or MP3." else null, model::clear)
                CanonRow(
                    "View",
                    flush = true,
                    detail = if (ready) "${AudioAtlasEngine.rulerLabel(model.viewStart, 0.01)} – ${AudioAtlasEngine.rulerLabel(model.viewEnd, 0.01)}" else " ",
                ) {
                    CanonGrid {
                        val cell = Modifier.weight(1f).fillMaxHeight()
                        CanonButton("", cell, enabled = ready && model.isZoomed, icon = Icons.Outlined.ZoomOut, contentDescription = "Zoom out", onClick = model::zoomOut)
                        CanonButton("", cell, enabled = ready, icon = Icons.Outlined.ZoomIn, contentDescription = "Zoom in", onClick = model::zoomIn)
                        CanonButton("", cell, enabled = ready && model.isZoomed, icon = Icons.Outlined.SwapHoriz, contentDescription = "Fit", onClick = model::fit)
                    }
                }
            }
        }
    }
    val canvas = CanonSection.canvas(
        CanonCanvasSize.Fixed(WAVEFORM_HEIGHT_SHEET + RULER_HEIGHT + SPECTROGRAM_HEIGHT),
        background = LocalDelphiColours.current.muted,
        boxing = CanonCanvasBoxing.UnlessWide,
    ) { AudioAtlasSheet(model, playback.playhead) }
    val info = CanonSection.of {
        CanonRows {
            if (!model.tags.isEmpty) AudioAtlasTagRow(model.tags, model.artwork)
            if (model.sections.isEmpty() && model.tags.isEmpty) {
                CanonHeading("Details")
                CanonEmptyRow("None")
            }
            for (section in model.sections) {
                CanonHeading(section.title)
                CanonTiles(section.tiles)
            }
        }
    }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(150.dp)) {
        CanonReadoutBand(playback.transport, Modifier.fillMaxSize(), idle = model.span.takeIf { ready }) { playhead ->
            AudioAtlasOverview(model, playhead)
        }
    }

    CanonSplit(
        sidebar = listOf(info),
        main = if (loaded) listOf(controls, canvas) else listOf(controls),
        banner = banner,
        bannerSticks = true,
        mainLeadsStacked = true,
        isFooterHidden = !loaded,
        footer = {
            CanonGrid {
                CanonButton("Loop", Modifier.weight(1f).fillMaxHeight(), selected = playback.looping, enabled = ready, icon = Icons.Filled.Repeat, onClick = model::toggleLoop)
                AVEditPlayButton(playback, ready)
                AVEditStopButton(playback)
            }
        },
    )
}

/** The whole file's waveform with the window shown and the rest shaded, while zoomed. */
@Composable
private fun AudioAtlasOverview(model: AudioAtlasModel, playhead: Double?) {
    val c = LocalDelphiColours.current
    val flipped = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(Modifier.fillMaxSize()) {
        AVEditWaveform(model.overview, model.duration, playhead, onScrub = model::seek)
        if (model.isZoomed && model.duration > 0) {
            Canvas(Modifier.fillMaxSize()) {
                fun x(seconds: Double): Float {
                    val share = (seconds / model.duration).toFloat() * size.width
                    return if (flipped) size.width - share else share
                }
                val (left, right) = listOf(x(model.viewStart), x(model.viewEnd)).sorted()
                val shade = c.background.copy(alpha = 0.7f)
                drawRect(shade, Offset.Zero, Size(left, size.height))
                drawRect(shade, Offset(right, 0f), Size(size.width - right, size.height))
            }
        }
    }
}

/** dt-ios AudioAtlasSheet: the window's waveform over a time ruler over its spectrogram; tap seeks, pinch zooms, a sideways drag pans. */
@Composable
private fun AudioAtlasSheet(model: AudioAtlasModel, playhead: Double?) {
    val c = LocalDelphiColours.current
    val flipped = LocalLayoutDirection.current == LayoutDirection.Rtl
    val measurer = rememberTextMeasurer()
    val start = model.viewStart
    val end = model.viewEnd
    val span = model.span
    val current by rememberUpdatedState(model)
    var width by remember { mutableStateOf(1f) }
    fun share(x: Float): Double {
        val share = (x / max(1f, width)).toDouble()
        return (if (flipped) 1 - share else share).coerceIn(0.0, 1.0)
    }
    val shown = playhead?.takeIf { span > 0 && it >= start && it <= end }

    Column(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { width = it.width.toFloat() }
            .semantics {
                contentDescription = "Waveform"
                stateDescription = "${AudioAtlasEngine.rulerLabel(start, 0.01)} – ${AudioAtlasEngine.rulerLabel(end, 0.01)}"
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val m = current
                    if (m.span > 0) m.seek(m.viewStart + share(offset.x) * m.span)
                }
            }
            .pointerInput(Unit) {
                // two fingers: the time under them stays put
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var grabbed: Triple<Double, Double, Double>? = null
                    var distance = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size < 2) continue
                        val (a, b) = pressed
                        val gap = hypot(a.position.x - b.position.x, a.position.y - b.position.y)
                        val m = current
                        if (grabbed == null) {
                            if (m.span <= 0 || gap <= 0) continue
                            grabbed = Triple(m.viewStart, m.span, share((a.position.x + b.position.x) / 2))
                            distance = gap
                        } else {
                            val (from, length, at) = grabbed
                            val anchor = from + at * length
                            val next = length * distance / max(1f, gap)
                            m.moveWindow(anchor - at * next, next)
                        }
                        pressed.forEach { it.consume() }
                    }
                }
            }
            .then(
                if (model.isZoomed) Modifier.canonSidewaysPan(onStart = { }) { _, delta ->
                    val m = current
                    val moved = delta / max(1f, width) * m.span
                    m.moveWindow(m.viewStart + if (flipped) moved else -moved, m.span)
                } else Modifier
            )
    ) {
        Box(Modifier.fillMaxWidth().height(WAVEFORM_HEIGHT_SHEET)) {
            // the window fills the strip
            AVEditWaveform(model.window, end - start, shown?.minus(start))
        }
        Canvas(Modifier.fillMaxWidth().height(RULER_HEIGHT).background(c.card)) {
            if (end <= start) return@Canvas
            val step = AudioAtlasEngine.rulerStep(span, size.width.toDp().value.toDouble())
            val minor = step / 5
            val first = kotlin.math.ceil(start / minor).toLong()
            val last = kotlin.math.floor(end / minor).toLong()
            // huge span: unreadable ticks
            if (last < first || last - first >= 2000) return@Canvas
            val ink = c.mutedForeground.copy(alpha = 0.6f)
            val style = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = c.mutedForeground)
            val hairline = CanonMetrics.hairline.toPx()
            for (index in first..last) {
                val seconds = index * minor
                val share = ((seconds - start) / span).toFloat()
                val x = (if (flipped) 1 - share else share) * size.width
                val labelled = index % 5 == 0L
                drawRect(ink, Offset(x, 0f), Size(hairline, (if (labelled) 9 else 4).dp.toPx()))
                if (!labelled) continue
                val text = measurer.measure(AudioAtlasEngine.rulerLabel(seconds, step), style)
                val gap = 4.dp.toPx()
                drawText(text, topLeft = Offset(if (flipped) x - gap - text.size.width else x + gap, size.height - text.size.height - 2.dp.toPx()))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(PLATE).clipToBounds()) {
            val image = model.spectrogram
            Canvas(Modifier.fillMaxSize()) {
                if (image != null && span > 0) {
                    fun x(seconds: Double): Float {
                        val share = ((seconds - start) / span).toFloat() * size.width
                        return if (flipped) size.width - share else share
                    }
                    val from = x(model.spectrogramRange.start)
                    val to = x(model.spectrogramRange.endInclusive)
                    // image pinned to seconds
                    scale(if (flipped) -1f else 1f, 1f, pivot = Offset((from + to) / 2, size.height / 2)) {
                        drawImage(
                            image,
                            dstOffset = IntOffset(min(from, to).roundToInt(), 0),
                            dstSize = IntSize(abs(to - from).roundToInt().coerceAtLeast(1), size.height.roundToInt()),
                        )
                    }
                }
                if (shown != null) {
                    val share = ((shown - start) / span).toFloat() * size.width
                    val x = if (flipped) size.width - share else share
                    val frame = CanonMetrics.frame.toPx()
                    drawRect(Color.White, Offset(x - frame / 2, 0f), Size(frame, size.height))
                }
            }
            if (model.sampleRate > 0) {
                val label = TextStyle(
                    fontFamily = DelphiMono, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f),
                    shadow = Shadow(Color.Black, Offset(0f, 1f)),
                )
                Canvas(Modifier.fillMaxSize()) {
                    for (quarter in 1..3) {
                        val text = measurer.measure(AudioAtlasEngine.kilohertz(model.sampleRate, quarter), label)
                        val pad = 8.dp.toPx()
                        val x = if (flipped) size.width - pad - text.size.width else pad
                        drawText(text, topLeft = Offset(x, size.height * (1 - quarter / 4f) - text.size.height / 2))
                    }
                }
            }
            if (model.busy || model.rendering) {
                Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { CanonLoader(cell = 5.dp, gap = 1.dp, colour = Color.White) }
            }
        }
    }
}

/** dt-ios AudioAtlasTagRow: artwork beside title, artist and album. */
@Composable
private fun AudioAtlasTagRow(tags: AtlasTags, artwork: ImageBitmap?) {
    val c = LocalDelphiColours.current
    Row(
        Modifier.fillMaxWidth().background(c.card).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (artwork != null) Image(artwork, contentDescription = null, Modifier.size(72.dp), contentScale = ContentScale.Crop)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            tags.title?.let { Text(it, style = CanonText.bodySemibold, color = c.foreground) }
            tags.artist?.let { Text(it, style = CanonText.subheadline, color = c.mutedForeground) }
            tags.album?.let { Text(it, style = CanonText.caption, color = c.mutedForeground) }
        }
    }
}
