package com.delphi.delphitools.tools.audiovideo

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLamp
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonReadout
import com.delphi.delphitools.canon.CanonReadoutBand
import com.delphi.delphitools.canon.CanonReadoutRowHeight
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.MenuCellItem
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.canonSmpte
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

private const val THUMBNAIL_COUNT = 24
private val STRIP_HEIGHT = 56.dp

// height over width
private const val TALLEST = 1.25f
private val TALLEST_PREVIEW = 340.dp
private const val SHEET_MARKS_LINGER_MS = 1500L
private val CELL_WIDTH = 104.dp
private val PICTURE_HEIGHT = 64.dp

/** FrameExtractorStills.Still: a grabbed frame's PNG and how the grid shows it. */
class FrameStill(val file: File, val label: String, val seconds: Double, val thumbnail: ImageBitmap) {
    val id: String = UUID.randomUUID().toString()
}

/** dt-ios FrameExtractorViewController: exact frames grabbed at the playhead, and a contact sheet of the whole video. */
class FrameExtractorModel(application: Application) : AndroidViewModel(application) {
    val source = AVEditSource(application, viewModelScope)
    val playback = AVEditPlayback(application, viewModelScope)
    private val root = avEditRoot(application, "frame-extractor")
    val sheetJob = AVEditJob(viewModelScope, File(root, "sheets"))
    var stills by mutableStateOf<List<FrameStill>>(emptyList())
        private set
    var grabbing by mutableStateOf(false)
        private set
    var grabFailed by mutableStateOf(false)
        private set
    var sheetCount by mutableFloatStateOf(12f)
        private set
    var showsSheetMarks by mutableStateOf(false)
        private set
    var sheetImage by mutableStateOf<ImageBitmap?>(null)
        private set
    private var shownSheet: File? = null
    private var marksTask: Job? = null
    private var grabTask: Job? = null

    val label: (Double) -> String get() = canonSmpte(max(1, (source.facts?.fps ?: 30.0).roundToInt()))

    fun load(uri: Uri) {
        clear()
        source.load(uri, THUMBNAIL_COUNT) { facts ->
            playback.fps = facts.fps
            playback.open(uri, 0.0..facts.duration)
        }
    }

    fun clear() {
        playback.release()
        sheetJob.reset()
        source.clear()
        grabTask?.cancel()
        grabbing = false
        stills.forEach { avEditDiscard(it.file.parentFile) }
        stills = emptyList()
        grabFailed = false
    }

    fun jump(seconds: Double) {
        if (source.duration > 0) playback.scrub((playback.playhead ?: 0.0) + seconds)
    }

    // grab needs the player clock
    fun grab() {
        val uri = source.uri ?: return
        val seconds = playback.position() ?: return
        if (grabbing) return
        playback.pause()
        val label = label(seconds)
        val file = File(File(File(root, "stills"), UUID.randomUUID().toString()), "${source.baseName}-${AVEditEngine.fileStamp(seconds)}.png")
        grabbing = true
        grabFailed = false
        grabTask = viewModelScope.launch {
            val thumbnail = try {
                AVEditMedia.still(getApplication(), uri, seconds, file)
            } catch (e: CancellationException) {
                avEditDiscard(file.parentFile)
                throw e
            } catch (_: Exception) {
                null
            }
            grabbing = false
            if (thumbnail == null || source.uri != uri) {
                avEditDiscard(file.parentFile)
                grabFailed = thumbnail == null
                return@launch
            }
            stills = stills + FrameStill(file, label, seconds, thumbnail)
        }
    }

    fun remove(still: FrameStill) {
        stills = stills.filter { it.id != still.id }
        avEditDiscard(still.file.parentFile)
    }

    fun changeSheetCount(count: Float) {
        if (count == sheetCount) return
        sheetCount = count
        sheetJob.reset()
        showsSheetMarks = true
        marksTask?.cancel()
        marksTask = viewModelScope.launch {
            delay(SHEET_MARKS_LINGER_MS)
            showsSheetMarks = false
        }
    }

    fun buildSheet() {
        val uri = source.uri ?: return
        playback.pause()
        val context = getApplication<Application>()
        val duration = source.duration
        val count = sheetCount.toInt()
        sheetJob.start("${source.baseName}-contact-sheet.png") { output, progress ->
            AVEditMedia.contactSheet(context, uri, duration, count, output, progress)
        }
    }

    // png decode off main
    fun showSheet(output: File?) {
        if (output == shownSheet) return
        shownSheet = output
        sheetImage = null
        if (output == null) return
        viewModelScope.launch {
            val image = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(output.path)?.asImageBitmap() }
            if (shownSheet == output) sheetImage = image
        }
    }

    override fun onCleared() = clear()
}

@Composable
fun FrameExtractor(model: FrameExtractorModel = viewModel()) {
    val source = model.source
    val playback = model.playback
    val sheetJob = model.sheetJob
    AVEditPageEffects(playback, model::clear)
    val sheetOutput = sheetJob.output.takeIf { !sheetJob.busy && !sheetJob.failed }
    LaunchedEffect(sheetOutput) { model.showSheet(sheetOutput) }

    val context = LocalContext.current
    val facts = source.facts
    val duration = source.duration
    val loaded = source.uri != null
    val ready = duration > 0
    val aspect = if (facts != null && facts.height > 0) facts.width.toFloat() / facts.height else 16f / 9
    val stills = model.stills
    val sheetMarks = model.showsSheetMarks || sheetJob.busy || sheetJob.output != null

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
    val grabRows = CanonSection.of {
        CanonRows {
            CanonButton(
                "Grab frame",
                kind = CanonButtonKind.Primary,
                enabled = ready && !model.grabbing && playback.ready,
                icon = Icons.Outlined.CenterFocusStrong,
                oversized = true,
                onClick = model::grab,
            )
            if (model.grabbing) CanonRow("Processing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = LocalDelphiColours.current.mutedForeground) }
            if (model.grabFailed) CanonStatusRow("Couldn't save that frame.", CanonTone.Warn)
        }
    }
    val stillRows = CanonSection.of {
        CanonRows {
            CanonHeading(if (stills.size == 1) "1 frame" else "${stills.size} frames")
            if (stills.isEmpty()) {
                CanonEmptyRow("None")
            } else {
                StillGrid(stills, onShare = { avEditShare(context, it.file) }, onRemove = model::remove)
                CanonButton("Share all", icon = Icons.Outlined.Share) {
                    context.startActivity(FileExport.chooser(context, stills.map { it.file }, "image/png"))
                }
            }
        }
    }
    val sheetRows = CanonSection.of {
        CanonRows {
            CanonHeading("Contact sheet")
            CanonRow("Frames", flush = true) {
                CanonSlider(
                    model.sheetCount,
                    model::changeSheetCount,
                    Modifier.fillMaxSize(),
                    range = 2f..48f,
                    step = 1f,
                    readout = "${model.sheetCount.toInt()}",
                )
            }
            AVEditJobPanel(
                sheetJob,
                "Contact sheet",
                Icons.Outlined.GridOn,
                "Couldn't build the contact sheet.",
                enabled = ready,
                oversized = false,
                onStart = model::buildSheet,
            )
            model.sheetImage?.let { image ->
                Image(
                    image,
                    contentDescription = "Contact sheet",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(image.width.toFloat() / max(1, image.height))
                        .background(LocalDelphiColours.current.muted),
                )
            }
        }
    }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(STRIP_HEIGHT + CanonReadoutRowHeight)) {
        CanonReadoutBand(
            playback.transport,
            Modifier.fillMaxSize(),
            readout = CanonReadout.Below,
            lamps = listOf(CanonLamp.Play, CanonLamp.Pause),
            format = model.label,
            idle = duration.takeIf { it > 0 },
        ) { playhead ->
            AVEditFilmstrip(
                source.thumbnails,
                duration,
                playhead,
                lit = stills.map { it.seconds },
                dim = if (sheetMarks) AVEditEngine.sheetTimes(duration, model.sheetCount.toInt()) else emptyList(),
            ) { if (ready) playback.scrub(it) }
        }
    }

    CanonSplit(
        sidebar = if (loaded) listOf(stillRows, sheetRows) else emptyList(),
        main = if (loaded) listOf(sourceRows, canvas, grabRows) else listOf(sourceRows),
        banner = banner,
        bannerSticks = true,
        mainLeadsStacked = true,
        isFooterHidden = !loaded,
        footer = {
            CanonGrid {
                val cell = Modifier.weight(1f).fillMaxHeight()
                CanonButton("−1 s", cell, enabled = ready, repeats = true, contentDescription = "Back 1 second") { model.jump(-1.0) }
                CanonButton(
                    "",
                    cell,
                    enabled = ready,
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    repeats = true,
                    contentDescription = "Back one frame",
                ) { playback.step(-1) }
                AVEditPlayButton(playback, ready)
                CanonButton(
                    "",
                    cell,
                    enabled = ready,
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    repeats = true,
                    contentDescription = "Forward one frame",
                ) { playback.step(1) }
                CanonButton("+1 s", cell, enabled = ready, repeats = true, contentDescription = "Forward 1 second") { model.jump(1.0) }
            }
        },
    )
}

/** FrameExtractorStills: grabbed frames in a grid, at least two to a row; a cell opens Share and Remove. */
@Composable
private fun StillGrid(stills: List<FrameStill>, onShare: (FrameStill) -> Unit, onRemove: (FrameStill) -> Unit) {
    val c = LocalDelphiColours.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = max(2, (maxWidth / CELL_WIDTH).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
            for (row in stills.chunked(columns)) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
                    for (still in row) StillCell(still, Modifier.weight(1f).fillMaxHeight(), onShare, onRemove)
                    // blanks complete the last row
                    repeat(columns - row.size) { Box(Modifier.weight(1f).fillMaxHeight().background(c.card)) }
                }
            }
        }
    }
}

@Composable
private fun StillCell(still: FrameStill, modifier: Modifier, onShare: (FrameStill) -> Unit, onRemove: (FrameStill) -> Unit) {
    val c = LocalDelphiColours.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .background(c.card)
                .semantics(mergeDescendants = true) { contentDescription = still.label }
                .canonClickable { open = true }
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Image(
                still.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(PICTURE_HEIGHT).background(c.muted),
            )
            Text(
                still.label,
                style = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp),
                color = c.foreground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
            )
        }
        MenuCellPopup(open, { open = false }) {
            MenuCellItem("Share", icon = Icons.Outlined.Share) {
                open = false
                onShare(still)
            }
            MenuCellItem("Remove", icon = Icons.Outlined.Close) {
                open = false
                onRemove(still)
            }
        }
    }
}
