package com.delphi.delphitools.tools.audiovideo

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonReadout
import com.delphi.delphitools.canon.CanonReadoutBand
import com.delphi.delphitools.canon.CanonReadoutRowHeight
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.canonSmpte
import com.delphi.delphitools.canon.pencil
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private const val PEAK_BUCKETS = 800
private const val THUMBNAIL_COUNT = 24

/** dt-ios AVEditAudioFormat. */
enum class AudioExtractFormat(val label: String, val fileExtension: String) {
    Wav("WAV", "wav"),
    M4a("M4A (AAC)", "m4a"),
}

/** dt-ios AudioExtractorViewController: a video's first audio track as its own file. */
class AudioExtractorModel(application: Application) : AndroidViewModel(application) {
    val source = AVEditSource(application, viewModelScope)
    val playback = AVEditPlayback(application, viewModelScope)
    val job = AVEditJob(viewModelScope, avEditRoot(application, "audio-extractor"))
    var format by mutableStateOf(AudioExtractFormat.Wav)
        private set
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

    fun select(format: AudioExtractFormat) {
        this.format = format
        job.reset()
    }

    fun extract() {
        val uri = source.uri ?: return
        playback.pause()
        val context = getApplication<Application>()
        val format = format
        val duration = source.duration
        job.start("${AVEditEngine.baseName(source.name, "audio")}.${format.fileExtension}") { output, progress ->
            when (format) {
                AudioExtractFormat.Wav -> AVEditMedia.wav(context, uri, output, duration, progress)
                // ponytail: AAC copies through; iOS re-encodes other codecs to AAC, MediaCodec AAC encode if that matters
                AudioExtractFormat.M4a -> AVEditMedia.remux(context, uri, output, { mimes ->
                    listOfNotNull(AVEditEngine.firstAudioTrack(mimes)?.takeIf { mimes[it] == AVEditEngine.AAC_MIME })
                }, progress = progress)
            }
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
fun AudioExtractor(model: AudioExtractorModel = viewModel()) {
    val source = model.source
    val playback = model.playback
    val job = model.job
    AVEditPageEffects(playback, model::clear)
    val output = job.output.takeIf { !job.busy && !job.failed }
    LaunchedEffect(output) { model.show(output) }

    val facts = source.facts
    val hasAudio = (facts?.audioTracks ?: 0) > 0
    val warning = when {
        source.failed -> "Failed to load video."
        facts?.audioTracks == 0 -> "No audio track detected"
        else -> null
    }
    val formats = AudioExtractFormat.entries

    val controls = CanonSection.of {
        CanonRows {
            if (source.uri == null) {
                AVEditDrop(model::load)
            } else {
                CanonHeading("Source")
                AVEditFileRow(source.name, facts?.let { AVEditEngine.timestamp(it.duration) } ?: "", warning, model::clear)
                CanonHeading("Format")
                CanonSegmented(formats.map { it.label }, formats.indexOf(model.format)) { model.select(formats[it]) }
                AVEditJobPanel(job, "Extract", pencil(R.drawable.pencil_audio_waveform), "Couldn't extract, try WAV format", enabled = hasAudio, onStart = model::extract)
            }
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
        ) { playhead -> AVEditTrackStrips(source, playhead, removed = false) { if (facts != null) playback.scrub(it) } }
    }

    CanonSplit(
        sidebar = listOf(info),
        main = listOf(controls),
        banner = banner,
        bannerSticks = true,
        mainLeadsStacked = true,
        isFooterHidden = source.uri == null,
        footer = {
            CanonGrid {
                AVEditLoopButton(playback, hasAudio)
                AVEditPlayButton(playback, hasAudio)
                AVEditStopButton(playback)
            }
        },
    )
}
