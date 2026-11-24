package com.delphi.delphitools.tools.audiovideo

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDecimalField
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonReadoutBand
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val PEAK_BUCKETS = 800
private val BAND_HEIGHT = 150.dp
private val EDITOR_HEIGHT = 180.dp
private const val LONGEST_FADE = 10.0

/** dt-ios AudioTrimmerViewController: an in and out point with fades, written as WAV. */
class AudioTrimmerModel(application: Application) : AndroidViewModel(application) {
    val source = AVEditAudioSource(application, viewModelScope)
    val playback = AVEditPlayback(application, viewModelScope)
    val job = AVEditJob(viewModelScope, avEditRoot(application, "audio-trimmer"))
    var start by mutableDoubleStateOf(0.0)
        private set
    var end by mutableDoubleStateOf(0.0)
        private set
    var fadeIn by mutableDoubleStateOf(0.0)
        private set
    var fadeOut by mutableDoubleStateOf(0.0)
        private set

    val longestFade get() = max(0.01, min(LONGEST_FADE, end - start))

    fun load(uri: Uri) {
        clear()
        source.load(uri, PEAK_BUCKETS) { scan ->
            start = 0.0
            end = floor(scan.duration * 100) / 100
            playback.open(uri, start..end)
        }
    }

    fun clear() {
        playback.release()
        job.reset()
        source.clear()
        start = 0.0
        end = 0.0
        fadeIn = 0.0
        fadeOut = 0.0
    }

    /** [keepsPlayback] for points set from the playhead. */
    fun select(newStart: Double, newEnd: Double, keepsPlayback: Boolean = false) {
        val (from, to) = AVEditEngine.select(newStart, newEnd, source.duration)
        start = from
        end = to
        fadeIn = min(fadeIn, longestFade)
        fadeOut = min(fadeOut, longestFade)
        playback.range = from..to
        if (keepsPlayback) job.reset() else edited()
    }

    fun changeFadeIn(seconds: Double) {
        fadeIn = seconds
        edited()
    }

    fun changeFadeOut(seconds: Double) {
        fadeOut = seconds
        edited()
    }

    private fun edited() {
        playback.stop()
        job.reset()
    }

    fun export() {
        val uri = source.uri ?: return
        if (end <= start) return
        playback.stop()
        val context = getApplication<Application>()
        val range = start..end
        val fadeIn = fadeIn
        val fadeOut = fadeOut
        job.start("${source.baseName}-trimmed.wav") { output, progress ->
            AVEditAudio.render(context, uri, output, AudioExtractFormat.Wav, range, fadeIn = fadeIn, fadeOut = fadeOut, progress = progress)
        }
    }

    override fun onCleared() = clear()
}

@Composable
fun AudioTrimmer(model: AudioTrimmerModel = viewModel()) {
    val source = model.source
    val playback = model.playback
    val job = model.job
    AVEditPageEffects(playback, model::clear)
    val scan = source.scan
    val loaded = source.uri != null
    val ready = scan != null
    val duration = source.duration
    val longest = model.longestFade.toFloat()

    val controls = CanonSection.of {
        CanonRows {
            if (!loaded) {
                AVEditAudioDrop(Icons.Outlined.GraphicEq, model::load)
            } else {
                CanonHeading("Source")
                AVEditFileRow(source.name, scan?.let(AVEditAudioEngine::audioDetail) ?: "", if (source.failed) "Failed to decode audio." else null, model::clear)
                CanonHeading("Fades")
                CanonRow("Fade in (s)", flush = true) {
                    CanonSlider(
                        model.fadeIn.toFloat(),
                        { model.changeFadeIn(it.toDouble()) },
                        Modifier.fillMaxSize(),
                        range = 0f..longest,
                        step = 0.01f,
                        readout = String.format(Locale.ROOT, "%.2f", model.fadeIn),
                    )
                }
                CanonRow("Fade out (s)", flush = true) {
                    CanonSlider(
                        model.fadeOut.toFloat(),
                        { model.changeFadeOut(it.toDouble()) },
                        Modifier.fillMaxSize(),
                        range = 0f..longest,
                        step = 0.01f,
                        readout = String.format(Locale.ROOT, "%.2f", model.fadeOut),
                        fillsFromTrailing = true,
                    )
                }
                AVEditJobPanel(
                    job,
                    "Export WAV",
                    Icons.Outlined.Download,
                    "Couldn't write the trimmed file.",
                    enabled = ready && model.end > model.start,
                    oversized = false,
                    onStart = model::export,
                )
            }
        }
    }
    val editor = CanonSection.canvas(
        CanonCanvasSize.Fixed(EDITOR_HEIGHT),
        background = LocalDelphiColours.current.muted,
        boxing = CanonCanvasBoxing.UnlessWide,
    ) {
        AVEditTrimEditor(duration, model.start, model.end, ready, { start, end -> model.select(start, end) }) { inset ->
            AudioTrimWaveform(scan?.peaks, duration, model.start, model.end, model.fadeIn, model.fadeOut, playback.playhead, inset, showsFadeCurves = true)
        }
    }
    val hasPlayhead = playback.playhead != null
    val transport = CanonSection.of {
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
                CanonButton("Set in", Modifier.weight(1f).fillMaxHeight(), enabled = hasPlayhead, icon = Icons.AutoMirrored.Filled.LastPage) {
                    playback.playhead?.let { model.select(it, model.end, keepsPlayback = true) }
                }
                CanonButton("Set out", Modifier.weight(1f).fillMaxHeight(), enabled = hasPlayhead, icon = Icons.Filled.FirstPage) {
                    playback.playhead?.let { model.select(model.start, it, keepsPlayback = true) }
                }
            }
            CanonGrid {
                AVEditLoopButton(playback, ready)
                AVEditPlayButton(playback, ready)
                AVEditStopButton(playback)
            }
        }
    }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(BAND_HEIGHT)) {
        CanonReadoutBand(playback.transport, Modifier.fillMaxSize(), idle = if (ready) max(0.0, model.end - model.start) else null) { playhead ->
            AudioTrimWaveform(scan?.peaks, duration, model.start, model.end, model.fadeIn, model.fadeOut, playhead, 0.dp) {
                if (ready) playback.scrub(it)
            }
        }
    }

    CanonSplit(
        sidebar = listOf(controls),
        main = if (loaded) listOf(editor, transport) else listOf(editor),
        banner = banner,
        bannerSticks = true,
    )
}

/**
 * dt-ios AudioWaveformView: a column per point, the selection lit and shaped by the fades, the rest dimmed;
 * [showsFadeCurves] draws the export's gain over it. [inset] matches the trim editor's grips.
 */
@Composable
private fun AudioTrimWaveform(
    peaks: AVEditPeaks?,
    duration: Double,
    start: Double,
    end: Double,
    fadeIn: Double,
    fadeOut: Double,
    playhead: Double?,
    inset: Dp,
    showsFadeCurves: Boolean = false,
    onScrub: ((Double) -> Unit)? = null,
) {
    val c = LocalDelphiColours.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val axis = remember { arrayOfNulls<AVEditAxis>(1) }
    val scrub by rememberUpdatedState(onScrub)
    val gestures = if (onScrub == null) Modifier else Modifier
        .pointerInput(Unit) { detectTapGestures { offset -> axis[0]?.seconds(offset.x)?.let { scrub?.invoke(it) } } }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(onDragStart = { offset -> axis[0]?.seconds(offset.x)?.let { scrub?.invoke(it) } }) { change, _ ->
                change.consume()
                axis[0]?.seconds(change.position.x)?.let { scrub?.invoke(it) }
            }
        }
    Canvas(Modifier.fillMaxSize().background(c.muted).then(gestures)) {
        val current = AVEditAxis(duration, size.width, inset.toPx(), rightToLeft).also { axis[0] = it }
        val mid = size.height / 2
        val strip = current.strip
        val left = inset.toPx()
        val hairline = CanonMetrics.hairline.toPx()
        drawRect(c.border, Offset(left, mid), Size(strip, hairline))
        if (peaks == null || peaks.min.isEmpty() || duration <= 0) return@Canvas
        val buckets = peaks.min.size
        val (columns, merge) = AVEditAudioEngine.merge(buckets, strip)
        val width = max(1f, strip / columns)
        val lit = Path()
        val dim = Path()
        for (column in 0 until columns) {
            val seconds = (column + 0.5) / columns * duration
            val selected = seconds in start..end
            val gain = if (selected) AVEditAudioEngine.envelope(seconds, start, end, fadeIn, fadeOut).toFloat() else 1f
            val from = column * merge
            val to = min(buckets, (column + 1) * merge)
            var high = peaks.max[from]
            var low = peaks.min[from]
            for (index in from + 1 until to) {
                high = max(high, peaks.max[index])
                low = min(low, peaks.min[index])
            }
            val top = mid - high * mid * gain
            val bottom = mid - low * mid * gain
            val x0 = left + column.toFloat() / columns * strip
            val x = if (rightToLeft) size.width - x0 - width else x0
            (if (selected) lit else dim).addRect(Rect(Offset(x, top), Size(width, max(1f, bottom - top))))
        }
        drawPath(dim, c.mutedForeground.copy(alpha = 0.45f))
        drawPath(lit, c.primary)

        if (showsFadeCurves && end > start && (fadeIn > 0 || fadeOut > 0)) {
            val frame = CanonMetrics.frame.toPx()
            val curve = Path()
            val steps = max(2, (abs(current.x(end) - current.x(start)) / 2).toInt())
            for (step in 0..steps) {
                val seconds = start + (end - start) * step / steps
                val y = size.height - frame - min(1.0, AVEditAudioEngine.envelope(seconds, start, end, fadeIn, fadeOut)).toFloat() * (size.height - frame * 2)
                if (step == 0) curve.moveTo(current.x(seconds), y) else curve.lineTo(current.x(seconds), y)
            }
            drawPath(curve, c.foreground, style = Stroke(frame, join = StrokeJoin.Round))
        }
        if (playhead != null) {
            val frame = CanonMetrics.frame.toPx()
            drawRect(c.foreground, Offset(current.x(playhead) - frame / 2, 0f), Size(frame, size.height))
        }
    }
}
