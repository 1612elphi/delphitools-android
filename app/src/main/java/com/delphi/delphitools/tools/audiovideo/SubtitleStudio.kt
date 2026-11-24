package com.delphi.delphitools.tools.audiovideo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignCenter
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourWell
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLamp
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonReadout
import com.delphi.delphitools.canon.CanonReadoutBand
import com.delphi.delphitools.canon.CanonReadoutRowHeight
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.canonGrabPan
import com.delphi.delphitools.canon.canonSidewaysPan
import com.delphi.delphitools.canon.canonSmpte
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val THUMBNAIL_COUNT = 24

// tallest preview, width share
private const val TALLEST = 1.25f
private val TALLEST_PREVIEW = 420.dp
private val CUE_LANE_HEIGHT = 20.dp
private val SNAP_DISTANCE = 10.dp

/** dt-ios SubtitleStudioViewController's state: a video, its cues and the style they show in over the picture. */
class SubtitleStudioModel(application: Application) : AndroidViewModel(application) {
    val source = AVEditSource(application, viewModelScope)
    val playback = AVEditPlayback(application, viewModelScope)
    var cues by mutableStateOf<List<SubtitleCue>>(emptyList())
        private set
    var subtitlesName by mutableStateOf<String?>(null)
        private set
    var subtitlesFailed by mutableStateOf(false)
        private set
    var style by mutableStateOf(SubtitleStyle())
        private set

    fun load(uri: Uri) {
        clear()
        source.load(uri, THUMBNAIL_COUNT) { facts ->
            playback.fps = facts.fps
            playback.open(uri, 0.0..facts.duration)
        }
    }

    fun clear() {
        playback.release()
        source.clear()
    }

    fun loadSubtitles(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val (text, name) = withContext(Dispatchers.IO) {
                // lossy utf-8, matches web
                (runCatching { resolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } }.getOrNull() ?: "") to resolver.canonDisplayName(uri)
            }
            val parsed = SubtitleCodec.parse(text).sortedBy { it.start }
            if (parsed.isEmpty()) subtitlesFailed = true else setCues(parsed, name)
        }
    }

    fun setCues(cues: List<SubtitleCue>, name: String?) {
        this.cues = cues
        subtitlesName = name
        subtitlesFailed = false
    }

    fun changeCue(index: Int, cue: SubtitleCue) {
        if (index in cues.indices && cues[index] != cue) cues = cues.toMutableList().also { it[index] = cue }
    }

    fun restyle(change: (SubtitleStyle) -> SubtitleStyle) {
        style = change(style).clamped()
    }

    fun place(index: Int) = restyle { it.copy(x = 0.0, y = SubtitleStudioEngine.placement(index, it.size)) }

    fun scrub(seconds: Double) {
        if (source.duration > 0) playback.scrub(seconds)
    }

    fun seek(toCue: Int) {
        val cue = cues.getOrNull(toCue) ?: return
        playback.pause()
        scrub(SubtitleStudioEngine.cueSeek(cue, source.facts?.fps ?: 30.0))
    }

    fun jump(offset: Int) {
        SubtitleStudioEngine.jump(cues, (playback.playhead ?: 0.0) * 1000, offset)?.let(::seek)
    }

    override fun onCleared() = clear()
}

private fun Rgb.toColour() = Color(r.toFloat(), g.toFloat(), b.toFloat())

@Composable
fun SubtitleStudio(model: SubtitleStudioModel = viewModel()) {
    val source = model.source
    val playback = model.playback
    val style = model.style
    AVEditPageEffects(playback, model::clear)

    val facts = source.facts
    val loaded = source.uri != null
    val ready = (facts?.duration ?: 0.0) > 0
    val cues = model.cues
    val width = facts?.width?.takeIf { it > 0 } ?: 16
    val height = facts?.height?.takeIf { it > 0 } ?: 9
    val aspect = width.toFloat() / height
    val shown = SubtitleStudioEngine.shown(cues, (playback.playhead ?: 0.0) * 1000)
    val marked = shown?.takeIf { !it.second }?.first

    val board = CanonSection.canvas(
        CanonCanvasSize.Aspect(max(aspect, 1 / TALLEST)),
        background = Color.Black,
        maxHeight = TALLEST_PREVIEW,
        boxing = CanonCanvasBoxing.UnlessWide,
    ) {
        SubtitleStudioBoard(playback, aspect, shown?.let { cues[it.first].text }, shown?.second ?: false, style) { x, y ->
            model.restyle { it.copy(x = x, y = y) }
        }
    }
    val sourceRows = CanonSection.of {
        CanonRows {
            if (!loaded) {
                AVEditDrop(model::load)
                return@CanonRows
            }
            CanonHeading("Source")
            AVEditFileRow(source.name, facts?.meta ?: "", if (source.failed) "Failed to load video." else null, model::clear)
            CanonHeading("Subtitles")
            if (cues.isEmpty()) {
                CanonDropZone(
                    "SRT or VTT",
                    icon = Icons.Outlined.Subtitles,
                    mimeTypes = arrayOf("text/*", "application/x-subrip", "application/octet-stream"),
                    photos = null,
                    paste = false,
                    onFiles = { uris -> uris.firstOrNull()?.let(model::loadSubtitles) },
                )
            } else {
                AVEditFileRow(model.subtitlesName ?: "", "${cues.size} cues", null) { model.setCues(emptyList(), null) }
            }
            if (model.subtitlesFailed) CanonStatusRow("Not a valid subtitle file.", CanonTone.Warn)
        }
    }
    val look = CanonSection.of {
        CanonRows {
            CanonHeading("Font")
            CanonSegmented(SubtitleFontChoice.entries.map { it.label }, style.font.ordinal, columns = 4) { index ->
                model.restyle { it.copy(font = SubtitleFontChoice.entries[index]) }
            }
            CanonSegmented(listOf("Regular", "Bold"), if (style.bold) 1 else 0) { index -> model.restyle { it.copy(bold = index == 1) } }
            CanonRow("Size", flush = true) {
                CanonSlider(
                    style.size.toFloat(),
                    { value -> model.restyle { it.copy(size = value.toDouble()) } },
                    Modifier.fillMaxSize(),
                    range = SubtitleStyle.SIZE_RANGE,
                    step = 0.01f,
                    readout = "${(style.size * 100).roundToInt()}%",
                )
            }
            CanonRow("Width", flush = true) {
                CanonSlider(
                    style.width.toFloat(),
                    { value -> model.restyle { it.copy(width = value.toDouble()) } },
                    Modifier.fillMaxSize(),
                    range = SubtitleStyle.WIDTH_RANGE,
                    step = 0.05f,
                    readout = "${(style.width * 100).roundToInt()}%",
                )
            }
            CanonHeading("Style")
            CanonSegmented(SubtitleTextStyle.entries.map { it.label }, style.text.ordinal, columns = 4) { index ->
                model.restyle { it.copy(text = SubtitleTextStyle.entries[index]) }
            }
            CanonColourWell(Rgb(style.red, style.green, style.blue), { colour ->
                model.restyle { it.copy(red = colour.r, green = colour.g, blue = colour.b) }
            }, label = "Text")
            if (style.text != SubtitleTextStyle.Plain) {
                CanonColourWell(Rgb(style.backingRed, style.backingGreen, style.backingBlue), { colour ->
                    model.restyle { it.copy(backingRed = colour.r, backingGreen = colour.g, backingBlue = colour.b) }
                }, label = "Backing")
            }
            CanonHeading("Position")
            CanonGrid {
                val cell = Modifier.weight(1f).fillMaxHeight()
                listOf(
                    Triple("Top", Icons.Outlined.VerticalAlignTop, 0),
                    Triple("Middle", Icons.Outlined.VerticalAlignCenter, 1),
                    Triple("Bottom", Icons.Outlined.VerticalAlignBottom, 2),
                ).forEach { (title, icon, index) ->
                    CanonButton(title, cell, enabled = cues.isNotEmpty(), icon = icon) { model.place(index) }
                }
            }
        }
    }
    val list = CanonSection.of {
        CanonRows {
            CanonHeading("Subtitles")
            cues.forEachIndexed { index, cue ->
                SubtitleCueRow(index, cue, index == marked, { model.changeCue(index, it) }) { model.seek(index) }
            }
        }
    }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(FILMSTRIP_HEIGHT + CUE_LANE_HEIGHT + CanonReadoutRowHeight)) {
        CanonReadoutBand(
            playback.transport,
            Modifier.fillMaxSize(),
            readout = CanonReadout.Below,
            lamps = listOf(CanonLamp.Play, CanonLamp.Pause),
            format = canonSmpte(max(1, (facts?.fps ?: 30.0).roundToInt())),
            idle = facts?.duration?.takeIf { it > 0 },
        ) { playhead ->
            Column(Modifier.fillMaxSize()) {
                AVEditFilmstrip(source.thumbnails, source.duration, playhead, Modifier.height(FILMSTRIP_HEIGHT)) { if (ready) model.scrub(it) }
                SubtitleCueLane(cues, source.duration, playhead, Modifier.weight(1f), onScrub = { if (ready) model.scrub(it) }, onCue = model::seek)
            }
        }
    }

    // ponytail: no Burn and no Format choice; burning in needs a MediaCodec decode, draw and re-encode pipeline, add one with a GL surface compositor
    val sidebar = if (loaded && cues.isNotEmpty()) listOf(list) else emptyList()
    CanonSplit(
        sidebar = sidebar,
        main = if (loaded) listOf(board, sourceRows, look) else listOf(sourceRows),
        banner = banner,
        bannerSticks = true,
        mainLeadsStacked = true,
        oneColumn = sidebar.isEmpty(),
        isFooterHidden = !loaded,
        footer = {
            CanonGrid {
                val cell = Modifier.weight(1f).fillMaxHeight()
                CanonButton("", cell, enabled = ready && cues.isNotEmpty(), icon = Icons.Filled.SkipPrevious, contentDescription = "Previous cue") { model.jump(-1) }
                CanonButton("", cell, enabled = ready, icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, repeats = true, contentDescription = "Back one frame") { playback.step(-1) }
                AVEditPlayButton(playback, ready)
                CanonButton("", cell, enabled = ready, icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, repeats = true, contentDescription = "Forward one frame") { playback.step(1) }
                CanonButton("", cell, enabled = ready && cues.isNotEmpty(), icon = Icons.Filled.SkipNext, contentDescription = "Next cue") { model.jump(1) }
            }
        },
    )
}

/** dt-ios SubtitleBurnRenderer.bitmap: the wrapped lines in the style, cut to their bounds; the rect is in frame pixels. */
private object SubtitleStudioRenderer {
    class Rendered(val image: ImageBitmap, val left: Int, val top: Int)

    private fun typeface(choice: SubtitleFontChoice, bold: Boolean): Typeface {
        // ponytail: Rounded falls back to sans; Android has no system rounded face, bundle one to match iOS
        val family = when (choice) {
            SubtitleFontChoice.Serif -> Typeface.SERIF
            SubtitleFontChoice.Mono -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }
        return Typeface.create(family, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun bitmap(text: String, frameWidth: Int, frameHeight: Int, style: SubtitleStyle): Rendered? {
        if (frameWidth <= 0 || frameHeight <= 0) return null
        val px = SubtitleStudioEngine.fontSize(style, frameHeight.toFloat())
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typeface(style.font, style.bold)
            textSize = px
            color = Color(style.red.toFloat(), style.green.toFloat(), style.blue.toFloat()).toArgb()
        }
        val lines = SubtitleStudioEngine.layout(text, frameWidth.toFloat(), frameHeight.toFloat(), style, fill::measureText)
        if (lines.isEmpty()) return null
        val (dx, dy) = SubtitleStudioEngine.overhang(style, px)
        // ascenders, stroke, shadow overhang
        val left = floor(lines.minOf { it.left } - dx).toInt()
        val top = floor(lines.minOf { it.top } - dy).toInt()
        val right = ceil(lines.maxOf { it.right } + dx).toInt()
        val bottom = ceil(lines.maxOf { it.bottom } + dy).toInt()
        if (right <= left || bottom <= top) return null
        val bitmap = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.translate(-left.toFloat(), -top.toFloat())
        val backing = Color(style.backingRed.toFloat(), style.backingGreen.toFloat(), style.backingBlue.toFloat())
        val shadow = if (style.text == SubtitleTextStyle.Shadow) px * 0.08f else 0f
        if (shadow > 0) fill.setShadowLayer(shadow * 2, 0f, shadow, backing.copy(alpha = 0.85f).toArgb())
        val stroke = Paint(fill).apply {
            clearShadowLayer()
            this.style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = max(2f, px / 8)
            color = backing.toArgb()
        }
        val box = Paint().apply { color = backing.copy(alpha = 0.65f).toArgb() }
        for (line in lines) {
            val x = (line.left + line.right) / 2 - line.width / 2
            when (style.text) {
                SubtitleTextStyle.Box -> canvas.drawRect(line.left, line.top, line.right, line.bottom, box)
                SubtitleTextStyle.Outline -> canvas.drawText(line.text, x, line.baseline, stroke)
                else -> Unit
            }
            canvas.drawText(line.text, x, line.baseline, fill)
        }
        return Rendered(bitmap.asImageBitmap(), left, top)
    }
}

/** dt-ios SubtitleStudioBoard: the subtitle drawn as it would burn over the video, dragged by a handle round it, with safe corners and snap guides. */
@Composable
private fun SubtitleStudioBoard(
    playback: AVEditPlayback,
    aspect: Float,
    text: String?,
    ghost: Boolean,
    style: SubtitleStyle,
    onMove: (Double, Double) -> Unit,
) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val density = LocalDensity.current
    val move by rememberUpdatedState(onMove)
    val current by rememberUpdatedState(style)
    var origin by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var travelled by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val bounds = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val picture = run {
            val scale = min(bounds.width / aspect, bounds.height)
            val size = Size(scale * aspect, scale)
            Rect(Offset((bounds.width - size.width) / 2, (bounds.height - size.height) / 2), size)
        }
        AVEditPlayerView(playback, aspect)
        // one bitmap per direction
        val placed = style.copy(x = 0.0, y = if (SubtitleStudioEngine.growsDown(style.y)) -0.5 else 0.0)
        val pixelsWide = picture.width.roundToInt()
        val pixelsHigh = picture.height.roundToInt()
        val rendered = remember(text, placed, pixelsWide, pixelsHigh) { text?.let { SubtitleStudioRenderer.bitmap(it, pixelsWide, pixelsHigh, placed) } }
        val subtitle = rendered?.let {
            // whole pixels, as burnt
            val dx = (style.x * pixelsWide).roundToInt()
            val dy = ((style.y - placed.y) * pixelsHigh).roundToInt()
            Rect(Offset(picture.left + it.left + dx, picture.top + it.top + dy), Size(it.image.width.toFloat(), it.image.height.toFloat()))
        }
        // wrap column, text height
        val handle = subtitle?.let {
            val column = style.width.toFloat() * picture.width
            Rect(Offset(picture.center.x + style.x.toFloat() * picture.width - column / 2, it.top), Size(column, it.height))
        }
        val dragging = origin != null
        Canvas(
            Modifier
                .fillMaxSize()
                .canonGrabPan(
                    grabs = { point -> handle?.inflate(with(density) { 20.dp.toPx() })?.contains(point) == true },
                    onStart = {
                        origin = current.x to current.y
                        travelled = Offset.Zero
                    },
                    onEnd = { origin = null },
                ) { _, delta ->
                    val (x0, y0) = origin ?: return@canonGrabPan
                    if (picture.width <= 0 || picture.height <= 0) return@canonGrabPan
                    travelled += delta
                    var x = x0 + travelled.x / picture.width
                    var y = y0 + travelled.y / picture.height
                    val snap = with(density) { SNAP_DISTANCE.toPx() }
                    val snapsX = abs(x) * picture.width < snap
                    val snapsY = abs(y) * picture.height < snap
                    if (snapsX) x = 0.0
                    if (snapsY) y = 0.0
                    if (snapsX && current.x != 0.0 || snapsY && current.y != 0.0) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    move(x, y)
                }
        ) {
            if (rendered != null && subtitle != null) {
                drawImage(rendered.image, topLeft = subtitle.topLeft, alpha = if (ghost) 0.45f else 1f)
            }
            val line = max(1f, CanonMetrics.hairline.toPx())
            if (handle != null) {
                drawRect(c.primary.copy(alpha = if (dragging) 1f else 0.5f), handle.topLeft + Offset(line / 2, line / 2), Size(handle.width - line, handle.height - line), style = Stroke(line))
                val grip = 5.dp.toPx()
                for (x in listOf(handle.left, handle.right)) drawRect(c.primary, Offset(x - grip / 2, handle.center.y - grip), Size(grip, grip * 2))
            }
            val tick = 12.dp.toPx()
            val safe = picture.deflate(0f).let {
                Rect(
                    it.left + it.width * SubtitleStudioEngine.UNSAFE.toFloat(), it.top + it.height * SubtitleStudioEngine.UNSAFE.toFloat(),
                    it.right - it.width * SubtitleStudioEngine.UNSAFE.toFloat(), it.bottom - it.height * SubtitleStudioEngine.UNSAFE.toFloat(),
                )
            }
            val corners = c.primary.copy(alpha = 0.7f)
            for ((corner, sign) in listOf(safe.topLeft to Offset(1f, 1f), safe.topRight to Offset(-1f, 1f), safe.bottomLeft to Offset(1f, -1f), safe.bottomRight to Offset(-1f, -1f))) {
                drawLine(corners, Offset(corner.x + sign.x * tick, corner.y), corner, line)
                drawLine(corners, corner, Offset(corner.x, corner.y + sign.y * tick), line)
            }
            if (dragging) {
                val baseline = picture.top + picture.height * SubtitleStudioEngine.BASELINE.toFloat()
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                for ((on, from, to) in listOf(
                    Triple(style.x == 0.0, Offset(picture.center.x, picture.top), Offset(picture.center.x, picture.bottom)),
                    Triple(style.y == 0.0, Offset(picture.left, baseline), Offset(picture.right, baseline)),
                )) {
                    drawLine(c.primary.copy(alpha = if (on) 1f else 0.5f), from, to, line, pathEffect = if (on) null else dash)
                }
            }
        }
        // edge away from subtitle
        val hint by animateFloatAsState(if (handle != null && !dragging) 1f else 0f, tween(200), label = "hint")
        if (handle != null && hint > 0) {
            var plate by remember { mutableStateOf(0) }
            val edge = with(density) { 12.dp.toPx() }
            val y = if (handle.center.y < picture.center.y) picture.bottom - edge - plate else picture.top + edge
            Text(
                "Drag around the subtitle to where you want it",
                Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, y.roundToInt()) }
                    .onSizeChanged { plate = it.height }
                    .widthIn(max = with(density) { (picture.width - 32.dp.toPx()).coerceAtLeast(0f).toDp() })
                    .alpha(hint)
                    .background(c.background.copy(alpha = 0.85f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                style = CanonText.captionSemibold,
                color = c.foreground,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** dt-ios SubtitleStudioStrips.CueLane: each cue as a block on the file's time axis, the one playing lit; a tap on a cue seeks to it. */
@Composable
private fun SubtitleCueLane(
    cues: List<SubtitleCue>,
    duration: Double,
    playhead: Double?,
    modifier: Modifier,
    onScrub: (Double) -> Unit,
    onCue: (Int) -> Unit,
) {
    val c = LocalDelphiColours.current
    val flipped = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scrub by rememberUpdatedState(onScrub)
    val cue by rememberUpdatedState(onCue)
    val list by rememberUpdatedState(cues)
    val length by rememberUpdatedState(duration)
    var width by remember { mutableStateOf(1f) }
    fun seconds(x: Float): Double {
        var share = x / max(1f, width)
        if (flipped) share = 1 - share
        return share.coerceIn(0f, 1f) * length
    }
    val active = playhead?.let { SubtitleStudioEngine.activeIndex(cues, it * 1000) }
    Canvas(
        modifier
            .fillMaxWidth()
            .background(c.muted)
            .onSizeChanged { width = it.width.toFloat() }
            .canonSidewaysPan { change, _ -> if (length > 0) scrub(seconds(change.position.x)) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (length <= 0) return@detectTapGestures
                    val ms = seconds(offset.x) * 1000
                    SubtitleStudioEngine.activeIndex(list, ms)?.let(cue) ?: scrub(ms / 1000)
                }
            }
    ) {
        if (duration <= 0) return@Canvas
        fun x(seconds: Double): Float {
            val x = (seconds / duration).coerceIn(0.0, 1.0).toFloat() * size.width
            return if (flipped) size.width - x else x
        }
        val inset = 4.dp.toPx()
        val dim = c.mutedForeground.copy(alpha = 0.45f)
        cues.forEachIndexed { index, item ->
            val a = x(item.start / 1000.0)
            val b = x(item.end / 1000.0)
            drawRect(if (index == active) c.primary else dim, Offset(min(a, b), inset), Size(max(1f, abs(b - a) - 1), size.height - inset * 2))
        }
        if (playhead != null) {
            val frame = CanonMetrics.frame.toPx()
            drawRect(c.foreground, Offset(x(playhead) - frame / 2, 0f), Size(frame, size.height))
        }
    }
}

/** dt-ios SubtitleCueRow: number, start and end over the text, all editable; a tap seeks to the cue, a bar marks the one playing. */
@Composable
private fun SubtitleCueRow(index: Int, cue: SubtitleCue, marked: Boolean, onChange: (SubtitleCue) -> Unit, onSelect: () -> Unit) {
    val c = LocalDelphiColours.current
    val mono = CanonText.caption.copy(fontFamily = DelphiMono, fontSize = CanonText.footnoteSemibold.fontSize)
    val select by rememberUpdatedState(onSelect)
    Box(
        Modifier
            .fillMaxWidth()
            .background(c.card)
            // fields keep their taps
            .pointerInput(Unit) { detectTapGestures { select() } }
    ) {
        Column(Modifier.padding(start = 14.dp, top = 8.dp, end = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${index + 1}", style = mono.copy(fontWeight = CanonText.footnoteSemibold.fontWeight), color = c.mutedForeground)
                CueTime(cue.start, mono) { onChange(cue.copy(start = it)) }
                CueTime(cue.end, mono, Modifier.weight(1f)) { onChange(cue.copy(end = it)) }
            }
            CanonTextCell(cue.text, { onChange(cue.copy(text = it)) }, Modifier.fillMaxWidth())
        }
        if (marked) Box(Modifier.align(Alignment.CenterStart).width(2.dp).fillMaxHeight().background(c.primary))
    }
}

/** A cue time field: a valid timestamp applies when editing ends, anything else reverts. */
@Composable
private fun CueTime(ms: Long, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier, onChange: (Long) -> Unit) {
    var text by remember(ms) { mutableStateOf(SubtitleCodec.formatTimestamp(ms, '.')) }
    CanonTextCell(
        text,
        { text = it },
        modifier,
        width = 110.dp,
        textStyle = style,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
        onEndEditing = {
            val value = SubtitleCodec.parseTimestamp(text)
            if (value != null && value != ms) onChange(value) else text = SubtitleCodec.formatTimestamp(ms, '.')
        },
    )
}
