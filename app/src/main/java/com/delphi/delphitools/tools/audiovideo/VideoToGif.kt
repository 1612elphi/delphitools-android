package com.delphi.delphitools.tools.audiovideo

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDecimalField
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonReadout
import com.delphi.delphitools.canon.CanonReadoutBand
import com.delphi.delphitools.canon.CanonReadoutRowHeight
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.canonSmpte
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val THUMBNAIL_COUNT = 24
private val STRIP_HEIGHT = 56.dp
private val EDITOR_HEIGHT = 72.dp

// height over width
private const val TALLEST = 1.25f
private val TALLEST_PREVIEW = 340.dp

// seconds initially selected
private const val FIRST_CUT = 5.0


/** dt-ios AVEditFrames.gif: width-bound frames, one per 1 / fps, each quantised and written before the next decodes. */
// ponytail: one exact seek per frame through MediaMetadataRetriever; a sequential MediaCodec decode if long GOPs make it slow
private suspend fun writeGif(context: Context, uri: Uri, start: Double, end: Double, fps: Int, width: Int, output: File, progress: AVEditProgress) =
    withContext(Dispatchers.IO) {
        val frames = VideoToGifEncoder.frameCount(start, end, fps)
        check(frames <= VideoToGifEncoder.MAX_FRAMES)
        val from = min(start, end)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            output.outputStream().buffered().use { out ->
                var size: Pair<Int, Int>? = null
                var pixels = IntArray(0)
                for (index in 0 until frames) {
                    ensureActive()
                    val us = AVEditEngine.micros(from + index.toDouble() / fps)
                    val image = retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST, width, width * 8)
                        ?: checkNotNull(retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST, width, width * 8))
                    val (w, h) = size ?: (image.width to image.height).also {
                        size = it
                        pixels = IntArray(it.first * it.second)
                        out.write(VideoToGifEncoder.header(it.first, it.second))
                    }
                    val fitted = if (image.width == w && image.height == h) image else Bitmap.createScaledBitmap(image, w, h, true)
                    fitted.getPixels(pixels, 0, w, 0, 0, w, h)
                    if (fitted !== image) fitted.recycle()
                    image.recycle()
                    out.write(VideoToGifEncoder.frame(pixels, w, h, 1000.0 / fps))
                    progress((index + 1).toDouble() / frames)
                }
                out.write(VideoToGifEncoder.trailer)
            }
        } finally {
            retriever.release()
        }
    }

/** dt-ios VideoToGIFViewController: a span of a video as a looping GIF. */
class VideoToGifModel(application: Application) : AndroidViewModel(application) {
    val source = AVEditSource(application, viewModelScope)
    val playback = AVEditPlayback(application, viewModelScope)
    val job = AVEditJob(viewModelScope, avEditRoot(application, "video-to-gif"))
    var start by mutableDoubleStateOf(0.0)
        private set
    var end by mutableDoubleStateOf(0.0)
        private set
    var fps by mutableFloatStateOf(10f)
        private set
    var width by mutableIntStateOf(480)
        private set

    val frames get() = VideoToGifEncoder.frameCount(start, end, fps.toInt())
    val isOverCap get() = frames > VideoToGifEncoder.MAX_FRAMES

    val outputHeight get() = source.facts?.takeIf { it.width > 0 }?.let { (width.toDouble() * it.height / it.width).roundToInt() } ?: 0

    fun load(uri: Uri) {
        clear()
        source.load(uri, THUMBNAIL_COUNT) { facts ->
            start = 0.0
            end = floor(min(facts.duration, FIRST_CUT) * 10) / 10
            playback.fps = facts.fps
            playback.limits = 0.0..facts.duration
            playback.open(uri, start..end)
        }
    }

    fun clear() {
        playback.release()
        job.reset()
        source.clear()
        start = 0.0
        end = 0.0
    }

    fun select(newStart: Double, newEnd: Double) {
        val (from, to) = AVEditEngine.select(newStart, newEnd, source.duration)
        start = from
        end = to
        playback.range = from..to
        job.reset()
    }

    /** dt-ios AVEditTrimEditor.onChange: the moved point shows in the player. */
    fun drag(newStart: Double, newEnd: Double) {
        val movedStart = newStart != start
        select(newStart, newEnd)
        playback.scrub(if (movedStart) start else end)
    }

    fun changeFps(value: Float) {
        fps = value
        job.reset()
    }

    fun changeWidth(value: Int) {
        width = value
        job.reset()
    }

    fun encode() {
        val uri = source.uri ?: return
        if (isOverCap || end <= start) return
        playback.pause()
        val context = getApplication<Application>()
        val start = start
        val end = end
        val fps = fps.toInt()
        val width = width
        job.start("${AVEditEngine.baseName(source.name, "clip")}.gif") { output, progress ->
            writeGif(context, uri, start, end, fps, width, output, progress)
        }
    }

    override fun onCleared() = clear()
}

@Composable
fun VideoToGif(model: VideoToGifModel = viewModel()) {
    val source = model.source
    val playback = model.playback
    val job = model.job
    AVEditPageEffects(playback, model::clear)

    val facts = source.facts
    val duration = source.duration
    val loaded = source.uri != null
    val ready = duration > 0 && model.end > model.start
    val selection = if (ready) model.start..model.end else null
    val aspect = if (facts != null && facts.height > 0) facts.width.toFloat() / facts.height else 16f / 9
    val hasPlayhead by remember(playback) { derivedStateOf { playback.playhead != null } }
    val frames = model.frames
    val over = ready && model.isOverCap
    val rate = model.fps.toInt()
    val output = job.output.takeIf { !job.busy && !job.failed }

    val sourceRows = CanonSection.of {
        CanonRows {
            if (!loaded) {
                AVEditDrop(model::load)
            } else {
                CanonHeading("Source")
                AVEditFileRow(source.name, facts?.meta ?: "", if (source.failed) "Failed to load video." else null, model::clear)
            }
        }
    }
    val canvas = CanonSection.canvas(
        CanonCanvasSize.Aspect(max(aspect, 1 / TALLEST)),
        background = Color.Black,
        maxHeight = TALLEST_PREVIEW,
        boxing = CanonCanvasBoxing.UnlessWide,
    ) { AVEditPlayerView(playback, aspect) }
    val editor = CanonSection.canvas(
        CanonCanvasSize.Fixed(EDITOR_HEIGHT),
        background = LocalDelphiColours.current.muted,
        boxing = CanonCanvasBoxing.UnlessWide,
    ) {
        AVEditTrimEditor(duration, model.start, model.end, ready, model::drag) { inset ->
            AVEditFilmstrip(source.thumbnails, duration, playback.playhead, inset = inset, selection = selection)
        }
    }
    val cut = CanonSection.of {
        CanonRows {
            CanonRow("Start", flush = true) {
                CanonDecimalField(model.start, { model.select(it, model.end) }, range = 0.0..max(0.0, model.end - AVEditEngine.MINIMUM_SPAN), enabled = ready)
            }
            CanonRow("End", flush = true) {
                CanonDecimalField(
                    model.end,
                    { model.select(model.start, it) },
                    range = min(duration, model.start + AVEditEngine.MINIMUM_SPAN)..max(duration, 0.0),
                    enabled = ready,
                )
            }
            CanonGrid {
                CanonButton("Set in", Modifier.weight(1f).fillMaxHeight(), enabled = hasPlayhead, icon = pencil(R.drawable.pencil_arrow_right_to_line)) {
                    playback.playhead?.let { model.select(it, model.end) }
                }
                CanonButton("Set out", Modifier.weight(1f).fillMaxHeight(), enabled = hasPlayhead, icon = pencil(R.drawable.pencil_arrow_left_to_line)) {
                    playback.playhead?.let { model.select(model.start, it) }
                }
            }
        }
    }
    val settings = CanonSection.of {
        CanonRows {
            CanonHeading("Range")
            // ponytail: the slider stays live while no range is ready; CanonSlider has no enabled state
            CanonRow("FPS", flush = true) {
                CanonSlider(model.fps, model::changeFps, Modifier.fillMaxSize(), range = 1f..50f, step = 1f, readout = "$rate")
            }
            CanonRow("Width", flush = true) {
                CanonNumberField(model.width, model::changeWidth, range = 16..1920, step = 16, unit = "px", enabled = ready)
            }
            val height = model.outputHeight
            CanonTiles(
                listOf(
                    CanonTile("Frames", "$frames frames", min(frames, VideoToGifEncoder.MAX_FRAMES).toFloat() / VideoToGifEncoder.MAX_FRAMES),
                    CanonTile("Size", if (height > 0) "${model.width} × $height" else "—"),
                )
            )
            if (over) CanonStatusRow("Over 300 frames. Please lower the fps or shorten the range", CanonTone.Warn)
        }
    }
    val result = CanonSection.of {
        CanonRows {
            AVEditJobPanel(job, "Create GIF", pencil(R.drawable.pencil_images), "Couldn't read frames from this video.", enabled = ready && !over, onStart = model::encode)
            if (output != null) GifPreview(output, aspect)
        }
    }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(STRIP_HEIGHT + CanonReadoutRowHeight)) {
        CanonReadoutBand(
            playback.transport,
            Modifier.fillMaxSize(),
            readout = CanonReadout.Below,
            format = canonSmpte(max(1, (facts?.fps ?: 30.0).roundToInt())),
            idle = if (duration > 0) max(0.0, model.end - model.start) else null,
        ) { playhead ->
            val marks = if (ready && !over) List(frames) { model.start + it.toDouble() / rate } else emptyList()
            AVEditFilmstrip(source.thumbnails, duration, playhead, selection = selection, dim = marks) { if (ready) playback.scrub(it) }
        }
    }

    CanonSplit(
        sidebar = if (loaded) listOf(settings, result) else emptyList(),
        main = if (loaded) listOf(sourceRows, canvas, editor, cut) else listOf(sourceRows),
        banner = banner,
        bannerSticks = true,
        mainLeadsStacked = true,
        isFooterHidden = !loaded,
        footer = {
            CanonGrid {
                CanonButton(
                    "",
                    Modifier.weight(1f).fillMaxHeight(),
                    enabled = ready,
                    icon = pencil(R.drawable.pencil_chevron_left),
                    repeats = true,
                    contentDescription = "Back one frame",
                ) { playback.step(-1) }
                AVEditLoopButton(playback, ready)
                AVEditPlayButton(playback, ready)
                AVEditStopButton(playback)
                CanonButton(
                    "",
                    Modifier.weight(1f).fillMaxHeight(),
                    enabled = ready,
                    icon = pencil(R.drawable.pencil_chevron_right),
                    repeats = true,
                    contentDescription = "Forward one frame",
                ) { playback.step(1) }
            }
        },
    )
}

// dt-ios GIFView
@Composable
private fun GifPreview(file: File, aspect: Float) {
    val drawable by produceState<Drawable?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) }.getOrNull() }
    }
    DisposableEffect(drawable) {
        (drawable as? AnimatedImageDrawable)?.start()
        onDispose { (drawable as? AnimatedImageDrawable)?.stop() }
    }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { it.setImageDrawable(drawable) },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect.coerceAtLeast(0.01f))
            .background(LocalDelphiColours.current.muted)
            .semantics { contentDescription = "Preview" },
    )
}
