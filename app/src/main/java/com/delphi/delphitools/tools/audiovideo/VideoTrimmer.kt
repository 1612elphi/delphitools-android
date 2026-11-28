package com.delphi.delphitools.tools.audiovideo

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import com.delphi.delphitools.canon.CanonReadout
import com.delphi.delphitools.canon.CanonReadoutBand
import com.delphi.delphitools.canon.CanonReadoutRowHeight
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.canonSmpte
import com.delphi.delphitools.canon.pencil
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
    val hasPlayhead by remember(playback) { derivedStateOf { playback.playhead != null } }

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
    // ponytail: no MP4/MOV choice; MediaMuxer writes MP4 only, add MOV with a muxer that writes QuickTime
    val settings = CanonSection.of {
        CanonRows { AVEditJobPanel(job, "Trim", pencil(R.drawable.pencil_scissors), "Couldn't write the trimmed file.", enabled = ready, onStart = model::trim) }
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
