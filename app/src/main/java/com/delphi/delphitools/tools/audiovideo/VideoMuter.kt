package com.delphi.delphitools.tools.audiovideo

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonReadout
import com.delphi.delphitools.canon.CanonReadoutBand
import com.delphi.delphitools.canon.CanonReadoutRowHeight
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.canonSmpte
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private const val PEAK_BUCKETS = 800
private const val THUMBNAIL_COUNT = 24

// height over width
private const val TALLEST = 1.25f
private val TALLEST_PREVIEW = 340.dp

/** dt-ios VideoMuterViewController: the video tracks copied into a new file without the audio. */
class VideoMuterModel(application: Application) : AndroidViewModel(application) {
    val source = AVEditSource(application, viewModelScope)
    val playback = AVEditPlayback(application, viewModelScope)
    val job = AVEditJob(viewModelScope, avEditRoot(application, "video-muter"))
    var details by mutableStateOf<List<AVEditProbeSection>>(emptyList())
        private set
    private var shown: File? = null

    fun load(uri: Uri) {
        clear()
        source.load(uri, THUMBNAIL_COUNT, PEAK_BUCKETS) { facts ->
            playback.fps = facts.fps
            playback.open(uri, 0.0..facts.duration)
        }
    }

    fun clear() {
        playback.release()
        job.reset()
        source.clear()
        details = emptyList()
        shown = null
    }

    fun mute() {
        val uri = source.uri ?: return
        playback.pause()
        val context = getApplication<Application>()
        job.start("${source.baseName}-muted.mp4") { output, progress ->
            AVEditMedia.remux(context, uri, output, AVEditEngine::videoTracks, progress = progress)
        }
    }

    /** The result plays from now, and its details fill the sidebar. */
    fun show(output: File?) {
        if (output == shown) return
        shown = output
        details = emptyList()
        val uri = output?.let(Uri::fromFile) ?: source.uri ?: return
        if (source.duration > 0) playback.open(uri, 0.0..source.duration)
        if (output == null) return
        viewModelScope.launch {
            val sections = AVEditMedia.probe(getApplication(), output)
            if (shown == output) details = sections
        }
    }

    override fun onCleared() = clear()
}

@Composable
fun VideoMuter(model: VideoMuterModel = viewModel()) {
    val source = model.source
    val playback = model.playback
    val job = model.job
    AVEditPageEffects(playback, model::clear)
    val output = job.output.takeIf { !job.busy && !job.failed }
    LaunchedEffect(output) { model.show(output) }

    val facts = source.facts
    val loaded = source.uri != null
    val tracks = facts?.audioTracks
    val ready = (facts?.duration ?: 0.0) > 0
    val warning = when {
        source.failed -> "Failed to load video."
        tracks == 0 -> "No audio track"
        else -> null
    }
    var detail = facts?.meta ?: ""
    if (tracks != null && detail.isNotEmpty()) detail += " · " + if (tracks == 1) "1 audio track" else "$tracks audio tracks"
    val aspect = if (facts != null && facts.height > 0) facts.width.toFloat() / facts.height else 16f / 9

    val sourceRows = CanonSection.of {
        CanonRows {
            if (!loaded) {
                AVEditDrop(model::load)
            } else {
                CanonHeading("Source")
                AVEditFileRow(source.name, detail, warning, model::clear)
            }
        }
    }
    val canvas = CanonSection.canvas(
        CanonCanvasSize.Aspect(max(aspect, 1 / TALLEST)),
        background = Color.Black,
        maxHeight = TALLEST_PREVIEW,
        boxing = CanonCanvasBoxing.UnlessWide,
    ) { AVEditPlayerView(playback, aspect) }
    // ponytail: no MP4/MOV choice; MediaMuxer writes MP4 only, add MOV with a muxer that writes QuickTime
    val settings = CanonSection.of {
        CanonRows {
            AVEditJobPanel(
                job,
                "Mute",
                Icons.AutoMirrored.Outlined.VolumeOff,
                "Couldn't copy video tracks",
                enabled = (tracks ?: 0) > 0,
                onStart = model::mute,
            )
        }
    }
    val info = CanonSection.of { CanonRows { AVEditDetails(model.details) } }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(FILMSTRIP_HEIGHT + WAVEFORM_HEIGHT + CanonReadoutRowHeight)) {
        CanonReadoutBand(
            playback.transport,
            Modifier.fillMaxSize(),
            readout = CanonReadout.Below,
            format = canonSmpte(max(1, (facts?.fps ?: 30.0).roundToInt())),
            idle = facts?.duration?.takeIf { it > 0 },
        ) { playhead -> AVEditTrackStrips(source, playhead, removed = output != null) { if (ready) playback.scrub(it) } }
    }

    CanonSplit(
        sidebar = listOf(info),
        main = if (loaded) listOf(sourceRows, canvas, settings) else listOf(sourceRows),
        banner = banner,
        bannerSticks = true,
        mainLeadsStacked = true,
        isFooterHidden = !loaded,
        footer = {
            CanonGrid {
                AVEditLoopButton(playback, ready)
                AVEditPlayButton(playback, ready)
                AVEditStopButton(playback)
            }
        },
    )
}
