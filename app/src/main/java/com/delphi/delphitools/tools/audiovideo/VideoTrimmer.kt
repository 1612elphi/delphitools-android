package com.delphi.delphitools.tools.audiovideo

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
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
import com.delphi.delphitools.canon.CanonReadout
import com.delphi.delphitools.canon.CanonReadoutBand
import com.delphi.delphitools.canon.CanonReadoutRowHeight
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.canonSmpte
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val THUMBNAIL_COUNT = 24
private val STRIP_HEIGHT = 56.dp
private val EDITOR_HEIGHT = 72.dp
private val GRIP = 14.dp

// height over width
private const val TALLEST = 1.25f
private val TALLEST_PREVIEW = 340.dp

/** dt-ios VideoTrimmerViewController: an in and out point, and the span between them copied to a new file. */
class VideoTrimmerModel(application: Application) : AndroidViewModel(application) {
    val source = AVEditSource(application, viewModelScope)
    val playback = AVEditPlayback(application, viewModelScope)
    val job = AVEditJob(viewModelScope, avEditRoot(application, "video-trimmer"))
    var start by mutableDoubleStateOf(0.0)
        private set
    var end by mutableDoubleStateOf(0.0)
        private set
    var details by mutableStateOf<List<AVEditProbeSection>>(emptyList())
        private set
    private var shown: File? = null

    fun load(uri: Uri) {
        clear()
        source.load(uri, THUMBNAIL_COUNT) { facts ->
            start = 0.0
            end = floor(facts.duration * 1000) / 1000
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
        details = emptyList()
        shown = null
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

    fun trim() {
        val uri = source.uri ?: return
        if (end <= start) return
        playback.pause()
        val context = getApplication<Application>()
        val range = start..end
        job.start("${source.baseName}-${AVEditEngine.fileStamp(start)}.mp4") { output, progress ->
            AVEditMedia.remux(context, uri, output, AVEditEngine::mediaTracks, range, progress)
        }
    }

    fun show(output: File?) {
        if (output == shown) return
        shown = output
        details = emptyList()
        if (output == null) return
        viewModelScope.launch {
            val sections = AVEditMedia.probe(getApplication(), output)
            if (shown == output) details = sections
        }
    }

    override fun onCleared() = clear()
}

@Composable
fun VideoTrimmer(model: VideoTrimmerModel = viewModel()) {
    val source = model.source
    val playback = model.playback
    val job = model.job
    AVEditPageEffects(playback, model::clear)
    val output = job.output.takeIf { !job.busy && !job.failed }
    LaunchedEffect(output) { model.show(output) }

    val facts = source.facts
    val duration = source.duration
    val loaded = source.uri != null
    val ready = duration > 0 && model.end > model.start
    val selection = if (ready) model.start..model.end else null
    val aspect = if (facts != null && facts.height > 0) facts.width.toFloat() / facts.height else 16f / 9
    val hasPlayhead = playback.playhead != null

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
        TrimEditor(source, model.start, model.end, playback.playhead, ready, model::drag)
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
                CanonButton("Set in", Modifier.weight(1f).fillMaxHeight(), enabled = hasPlayhead, icon = Icons.AutoMirrored.Filled.LastPage) {
                    playback.playhead?.let { model.select(it, model.end) }
                }
                CanonButton("Set out", Modifier.weight(1f).fillMaxHeight(), enabled = hasPlayhead, icon = Icons.Filled.FirstPage) {
                    playback.playhead?.let { model.select(model.start, it) }
                }
            }
        }
    }
    // ponytail: no MP4/MOV choice; MediaMuxer writes MP4 only, add MOV with a muxer that writes QuickTime
    val settings = CanonSection.of {
        CanonRows { AVEditJobPanel(job, "Trim", Icons.Outlined.ContentCut, "Couldn't write the trimmed file.", enabled = ready, onStart = model::trim) }
    }
    val info = CanonSection.of { CanonRows { AVEditDetails(model.details) } }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(STRIP_HEIGHT + CanonReadoutRowHeight)) {
        CanonReadoutBand(
            playback.transport,
            Modifier.fillMaxSize(),
            readout = CanonReadout.Below,
            format = canonSmpte(max(1, (facts?.fps ?: 30.0).roundToInt())),
            idle = if (duration > 0) max(0.0, model.end - model.start) else null,
        ) { playhead ->
            AVEditFilmstrip(source.thumbnails, duration, playhead, selection = selection) { if (ready) playback.scrub(it) }
        }
    }

    CanonSplit(
        sidebar = if (loaded) listOf(settings, info) else emptyList(),
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
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
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
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    repeats = true,
                    contentDescription = "Forward one frame",
                ) { playback.step(1) }
            }
        },
    )
}

/** dt-ios AVEditTrimEditor: the filmstrip between two grips that drag the in and out points, with the selection framed. */
// ponytail: grips are not TalkBack-adjustable; the Start and End fields are the accessible way in
@Composable
private fun TrimEditor(
    source: AVEditSource,
    start: Double,
    end: Double,
    playhead: Double?,
    enabled: Boolean,
    onChange: (Double, Double) -> Unit,
) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val duration = source.duration
    BoxWithConstraints(Modifier.fillMaxSize()) {
        AVEditFilmstrip(source.thumbnails, duration, playhead, inset = GRIP, selection = if (enabled) start..end else null)
        if (!enabled) return@BoxWithConstraints
        val width = constraints.maxWidth.toFloat()
        val grip = with(density) { GRIP.toPx() }
        val target = with(density) { CanonMetrics.controlHeight.toPx() }
        val axis = AVEditAxis(duration, width, grip, rightToLeft)
        val (left, right) = listOf(axis.x(start), axis.x(end)).sorted()
        with(density) {
            Box(
                Modifier
                    .offset { IntOffset(left.roundToInt(), 0) }
                    .width((right - left).toDp())
                    .fillMaxHeight()
                    .border(CanonMetrics.frame, c.primary)
            )
        }
        val change by rememberUpdatedState(onChange)
        val selection by rememberUpdatedState(start to end)
        val stripWidth = max(1f, width - grip * 2)
        for (isStart in listOf(true, false)) {
            // the start grip sits left of the selection unless right to left
            val onLeft = isStart != rightToLeft
            val barX = if (onLeft) left - grip else right
            val targetX = (if (onLeft) barX - (target - grip) else barX).coerceIn(0f, max(0f, width - target))
            val seconds = if (isStart) start else end
            Box(
                Modifier
                    .offset { IntOffset(targetX.roundToInt(), 0) }
                    .width(with(density) { target.toDp() })
                    .fillMaxHeight()
                    .semantics {
                        contentDescription = if (isStart) "Start" else "End"
                        stateDescription = AVEditEngine.timestamp(seconds)
                    }
                    .pointerInput(isStart) {
                        var origin = 0.0
                        var moved = 0f
                        detectHorizontalDragGestures(onDragStart = {
                            origin = if (isStart) selection.first else selection.second
                            moved = 0f
                        }) { pointer, amount ->
                            pointer.consume()
                            moved += amount
                            val delta = moved / stripWidth * duration * if (rightToLeft) -1 else 1
                            val next = ((origin + delta) * 1000).roundToInt() / 1000.0
                            if (isStart) change(next, selection.second) else change(selection.first, next)
                        }
                    }
            ) {
                Box(
                    Modifier
                        .offset { IntOffset((barX - targetX).roundToInt(), 0) }
                        .width(GRIP)
                        .fillMaxHeight()
                        .background(c.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (onLeft) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = c.primaryForeground,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
