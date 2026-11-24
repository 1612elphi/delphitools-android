package com.delphi.delphitools.tools.audiovideo

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonReadout
import com.delphi.delphitools.canon.CanonReadoutBand
import com.delphi.delphitools.canon.CanonReadoutRowHeight
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTimecode
import com.delphi.delphitools.canon.canonSmpte
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import com.delphi.delphitools.ui.theme.selectedTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val THUMBNAIL_COUNT = 24
private val STRIP_HEIGHT = 56.dp
private val SCOPES_HEIGHT = 180.dp

// height over width
private const val TALLEST = 1.25f
private val TALLEST_PREVIEW = 340.dp

// palette floor, both themes
private val SCOPE_PLATE = Color(16, 18, 16)

/** dt-ios VideoAtlasModel: a video's container and track facts, thumbnails and a parked player. */
class VideoAtlasModel(application: Application) : AndroidViewModel(application) {
    val source = AVEditSource(application, viewModelScope)
    val playback = AVEditPlayback(application, viewModelScope)
    var sections by mutableStateOf<List<AVEditProbeSection>>(emptyList())
        private set
    var probeFailed by mutableStateOf(false)
        private set
    private var loaded: Uri? = null

    fun load(uri: Uri) {
        clear()
        loaded = uri
        source.load(uri, THUMBNAIL_COUNT) { facts ->
            playback.fps = facts.fps
            playback.open(uri, 0.0..facts.duration)
        }
        viewModelScope.launch {
            val result = AtlasProbe.probe(getApplication(), uri)
            if (loaded != uri) return@launch
            sections = result?.sections ?: emptyList()
            probeFailed = result == null
        }
    }

    fun clear() {
        playback.release()
        source.clear()
        loaded = null
        sections = emptyList()
        probeFailed = false
    }

    override fun onCleared() = clear()
}

@Composable
fun VideoAtlas(model: VideoAtlasModel = viewModel()) {
    val source = model.source
    val playback = model.playback
    val context = LocalContext.current
    AVEditPageEffects(playback, model::clear)
    var texture by remember { mutableStateOf<TextureView?>(null) }
    var fullScreen by remember { mutableStateOf(false) }

    val facts = source.facts
    val loaded = source.uri != null
    val ready = (facts?.duration ?: 0.0) > 0
    val aspect = if (facts != null && facts.height > 0) facts.width.toFloat() / facts.height else 16f / 9
    val format = canonSmpte(max(1, (facts?.fps ?: 30.0).roundToInt()))

    val controls = CanonSection.of {
        CanonRows {
            if (!loaded) {
                AVEditDrop(model::load)
            } else {
                CanonHeading("Source")
                AVEditFileRow(
                    source.name,
                    VideoAtlasEngine.streamSummary(model.sections) ?: "",
                    if (model.probeFailed) "This file's container can't be read." else null,
                    model::clear,
                )
                CanonButton("Copy report", kind = CanonButtonKind.Primary, enabled = model.sections.isNotEmpty(), icon = Icons.Outlined.ContentCopy, flashesCopied = true) {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("", VideoAtlasEngine.report(model.sections)))
                }
            }
        }
    }
    val canvas = CanonSection.canvas(
        CanonCanvasSize.Aspect(max(aspect, 1 / TALLEST)),
        background = Color.Black,
        maxHeight = TALLEST_PREVIEW,
        boxing = CanonCanvasBoxing.UnlessWide,
    ) {
        // the full screen view holds the player's surface while it is open
        if (!fullScreen) AVEditPlayerView(playback, aspect, onTexture = { texture = it })
    }
    val scopes = CanonSection.canvas(CanonCanvasSize.Fixed(SCOPES_HEIGHT), background = SCOPE_PLATE, boxing = CanonCanvasBoxing.UnlessWide) {
        VideoScopes(texture.takeIf { !fullScreen })
    }
    val info = CanonSection.of { CanonRows { AVEditDetails(model.sections) } }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(STRIP_HEIGHT + CanonReadoutRowHeight)) {
        CanonReadoutBand(
            playback.transport,
            Modifier.fillMaxSize(),
            readout = CanonReadout.Below,
            format = format,
            idle = facts?.duration?.takeIf { it > 0 },
        ) { playhead ->
            AVEditFilmstrip(source.thumbnails, source.duration, playhead) { if (ready) playback.scrub(it) }
        }
    }

    // ponytail: no Picture in Picture; Android PiP takes the whole activity, add it with a shell-level PiP mode
    CanonSplit(
        sidebar = listOf(info),
        main = if (loaded) listOf(controls, canvas, scopes) else listOf(controls),
        banner = banner,
        bannerSticks = true,
        mainLeadsStacked = true,
        isFooterHidden = !loaded,
        footer = {
            CanonGrid {
                AVEditLoopButton(playback, ready)
                AVEditPlayButton(playback, ready)
                AVEditStopButton(playback)
                CanonButton(
                    "",
                    Modifier.weight(1f).fillMaxHeight(),
                    enabled = playback.ready,
                    icon = Icons.Filled.Fullscreen,
                    contentDescription = "Full Screen",
                ) { fullScreen = true }
            }
        },
    )

    if (fullScreen) VideoAtlasFullScreen(model, aspect, format) { fullScreen = false }
}

/** dt-ios AVEditFullScreenViewController: the picture on black, one row of transport cells, filmstrip and counter; a tap hides the controls. */
@Composable
private fun VideoAtlasFullScreen(model: VideoAtlasModel, aspect: Float, format: (Double) -> String, close: () -> Unit) {
    val source = model.source
    val playback = model.playback
    var controls by remember { mutableStateOf(true) }
    Dialog(close, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // dark tokens on black
        CompositionLocalProvider(LocalDelphiColours provides selectedTheme().colours(true)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AVEditPlayerView(
                    playback,
                    aspect,
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { controls = !controls },
                )
                if (controls) {
                    Box(
                        Modifier
                            .safeDrawingPadding()
                            .padding(start = CanonMetrics.panelPadding - 8.dp, top = 4.dp)
                            .size(CanonMetrics.controlHeight)
                            .clickable(onClick = close),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.55f))
                    }
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .safeDrawingPadding()
                            .fillMaxWidth()
                            .padding(start = CanonMetrics.panelPadding, end = CanonMetrics.panelPadding, bottom = 8.dp)
                            .height(44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CanonGrid(Modifier.width(44.dp * 3 + CanonMetrics.hairline * 2)) {
                            val cell = Modifier.weight(1f).fillMaxHeight()
                            CanonButton(
                                "",
                                cell,
                                kind = CanonButtonKind.Segment,
                                selected = playback.playing,
                                icon = if (playback.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playback.playing) "Pause" else "Play",
                                onClick = playback::toggle,
                            )
                            CanonButton("", cell, kind = CanonButtonKind.Segment, enabled = playback.playing || playback.paused, icon = Icons.Filled.Stop, contentDescription = "Stop", onClick = playback::stop)
                            CanonButton("", cell, kind = CanonButtonKind.Segment, selected = playback.looping, icon = Icons.Filled.Repeat, contentDescription = "Loop") {
                                playback.looping = !playback.looping
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp)) {
                            AVEditFilmstrip(source.thumbnails, source.duration, playback.playhead) { playback.scrub(it) }
                        }
                        CanonTimecode(playback.playhead ?: source.duration, size = 20.sp, halo = Color.Black, format = format)
                    }
                }
            }
        }
    }
}

/**
 * dt-ios AVEditScopes: a vectorscope (Cb across, Cr up) beside a luma waveform (picture column across, brightness up),
 * from a 160 × 90 copy of the frame on [texture], 15 times a second.
 */
@Composable
private fun VideoScopes(texture: TextureView?) {
    var vector by remember { mutableStateOf<ImageBitmap?>(null) }
    var wave by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(texture) {
        vector = null
        wave = null
        if (texture == null) return@LaunchedEffect
        val width = VideoAtlasEngine.SAMPLE_WIDTH
        val height = VideoAtlasEngine.SAMPLE_HEIGHT
        val cells = VideoAtlasEngine.CELLS
        val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        val vectorCells = IntArray(cells * cells)
        val waveCells = IntArray(width * cells)
        while (isActive) {
            if (texture.isAvailable) {
                texture.getBitmap(frame)
                frame.getPixels(pixels, 0, width, 0, 0, width, height)
                val (v, w) = withContext(Dispatchers.Default) {
                    VideoAtlasEngine.plot(pixels, width, height, vectorCells, waveCells)
                    val vectorOut = IntArray(vectorCells.size)
                    val waveOut = IntArray(waveCells.size)
                    VideoAtlasEngine.image(vectorCells, VideoAtlasEngine.vectorInk, vectorOut)
                    VideoAtlasEngine.image(waveCells, null, waveOut)
                    Bitmap.createBitmap(vectorOut, cells, cells, Bitmap.Config.ARGB_8888).asImageBitmap() to
                        Bitmap.createBitmap(waveOut, width, cells, Bitmap.Config.ARGB_8888).asImageBitmap()
                }
                vector = v
                wave = w
            }
            delay(66)
        }
    }
    val flipped = LocalLayoutDirection.current == LayoutDirection.Rtl
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = DelphiMono, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.6f))
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
        val side = min(size.height, size.width / 2)
        val square = Rect(Offset(if (flipped) size.width - side else 0f, (size.height - side) / 2), Size(side, side))
        val inset = 12.dp.toPx()
        val rest = Rect(if (flipped) inset else side + inset, inset, if (flipped) size.width - side - inset else size.width - inset, size.height - inset)
        vector?.let { plotImage(it, square) }
        if (rest.width > 0) wave?.let { plotImage(it, rest) }

        val ink = Color.White.copy(alpha = 0.28f)
        val hairline = CanonMetrics.hairline.toPx()
        val centre = square.center
        val radius = side / 2 * VideoAtlasEngine.REACH.toFloat()
        drawCircle(ink, radius, centre, style = Stroke(hairline))
        drawLine(ink, Offset(centre.x - radius, centre.y), Offset(centre.x + radius, centre.y), hairline)
        drawLine(ink, Offset(centre.x, centre.y - radius), Offset(centre.x, centre.y + radius), hairline)
        // skin tone: 123 degrees
        val skin = 123.0 * PI / 180
        drawLine(ink, centre, Offset(centre.x + radius * cos(skin).toFloat(), centre.y - radius * sin(skin).toFloat()), hairline)
        val bars = listOf(
            "R" to Triple(0.75, 0.0, 0.0), "Mg" to Triple(0.75, 0.0, 0.75), "B" to Triple(0.0, 0.0, 0.75),
            "Cy" to Triple(0.0, 0.75, 0.75), "G" to Triple(0.0, 0.75, 0.0), "Yl" to Triple(0.75, 0.75, 0.0),
        )
        val box = 8.dp.toPx()
        for ((name, rgb) in bars) {
            val part = VideoAtlasEngine.components(rgb.first, rgb.second, rgb.third)
            val point = Offset(centre.x + (part.cb * 2).toFloat() * radius, centre.y - (part.cr * 2).toFloat() * radius)
            drawRect(ink, Offset(point.x - box / 2, point.y - box / 2), Size(box, box), style = Stroke(hairline))
            val text = measurer.measure(name, label)
            // label outward of box
            val out = point - centre
            val length = max(1f, hypot(out.x, out.y))
            val away = 12.dp.toPx()
            drawText(text, topLeft = Offset(point.x + out.x / length * away - text.size.width / 2, point.y + out.y / length * away - text.size.height / 2))
        }
        if (rest.width > 0) {
            for (quarter in 0..4) {
                val y = rest.bottom - rest.height * quarter / 4
                drawLine(ink, Offset(rest.left, y), Offset(rest.right, y), hairline)
                val text = measurer.measure("${quarter * 25}", label)
                drawText(text, topLeft = Offset(rest.left + 3.dp.toPx(), min(y + 1, rest.bottom - text.size.height)))
            }
        }
    }
}

private fun DrawScope.plotImage(image: ImageBitmap, rect: Rect) {
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
    )
}
