package com.delphi.delphitools.tools.images

import android.animation.ValueAnimator
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDimensionLine
import com.delphi.delphitools.canon.CanonDimensionPlacement
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonLoupe
import com.delphi.delphitools.canon.CanonLoupeBorder
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonDimensionLines
import com.delphi.delphitools.canon.canonDimensionSheet
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.canonGrabPan
import com.delphi.delphitools.canon.canonLoupeFrame
import com.delphi.delphitools.canon.canonPlaced
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.colour.PixelPickerSampler
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ponytail: the source decodes at 4096 px on its long side and exports from there; a tiled warp would lift the cap
private const val DESKEW_SOURCE_PIXELS = 4096
private const val DESKEW_CANVAS_PIXELS = 2048
private const val DESKEW_PREVIEW_EDGE = 1024.0
private const val DIVISIONS = 4
private const val LOUPE_RADIUS = 8

private val emptyPixels = IntSize(1600, 1200)
private val emptyQuad = listOf(QuadPoint(470.0, 250.0), QuadPoint(1170.0, 320.0), QuadPoint(1330.0, 980.0), QuadPoint(260.0, 920.0))

/** Perspective-corrects [image] through [quad], in its pixels, into a [width] × [height] bitmap. */
internal fun warp(image: Bitmap, quad: List<QuadPoint>, width: Int, height: Int): Bitmap {
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val matrix = Matrix()
    val src = quad.flatMap { listOf(it.x.toFloat(), it.y.toFloat()) }.toFloatArray()
    val dst = floatArrayOf(0f, 0f, width.toFloat(), 0f, width.toFloat(), height.toFloat(), 0f, height.toFloat())
    matrix.setPolyToPoly(src, 0, dst, 0, 4)
    android.graphics.Canvas(out).drawBitmap(image, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    return out
}

/** dt-ios ImageDeskewerViewModel: a quad dragged over a photo, its perspective corrected live. */
class ImageDeskewerModel(application: Application) : AndroidViewModel(application) {
    private var source: Bitmap? = null
    private var canvasImage: Bitmap? = null
    var preview by mutableStateOf<ImageBitmap?>(null)
        private set
    var fileName by mutableStateOf("")
        private set
    var filePixels by mutableStateOf<IntSize?>(null)
        private set

    /** Clockwise from the top left, in source pixels. */
    var quad by mutableStateOf(emptyList<QuadPoint>())
        private set
    var aspect by mutableStateOf(DeskewAspect.all[0])
        private set
    var result by mutableStateOf<ImageBitmap?>(null)
        private set
    var unreadable by mutableStateOf(false)
        private set
    var isExporting by mutableStateOf(false)
        private set
    var sourceSize by mutableStateOf(IntSize.Zero)
        private set
    private var loadedQuad = emptyList<QuadPoint>()
    private var loading: Job? = null
    private var rendering = false
    private var pending = false

    val loaded get() = preview != null
    val isQuadValid get() = ImageDeskewEngine.isConvex(quad)
    val outputSize get() = ImageDeskewEngine.target(quad, aspect).let { IntSize(it.first, it.second) }

    fun load(uri: Uri) {
        loading?.cancel()
        val resolver = getApplication<Application>().contentResolver
        loading = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val name = resolver.canonDisplayName(uri) ?: ""
                val decoded = runCatching { ImageFiles.decode(ImageDecoder.createSource(resolver, uri), DESKEW_SOURCE_PIXELS) }.getOrNull()
                    ?: return@withContext null
                val canvas = ImageFiles.unlessOutOfMemory { ImageCompressEngine.preview(decoded.image, DESKEW_CANVAS_PIXELS) } ?: return@withContext null
                Triple(name, decoded, canvas)
            }
            if (loaded == null) {
                unreadable = true
                return@launch
            }
            val (name, decoded, canvas) = loaded
            unreadable = false
            fileName = name
            source = decoded.image
            canvasImage = canvas
            filePixels = IntSize(decoded.width, decoded.height)
            sourceSize = IntSize(decoded.image.width, decoded.image.height)
            preview = canvas.asImageBitmap()
            result = null
            // ponytail: no automatic quad; dt-ios seeds it from Vision's VNDetectRectanglesRequest, which pixel maths here does not match
            loadedQuad = ImageDeskewEngine.insetQuad(sourceSize.width.toDouble(), sourceSize.height.toDouble())
            move(loadedQuad)
        }
    }

    fun reset() {
        if (loadedQuad.isNotEmpty()) move(loadedQuad)
    }

    fun move(next: List<QuadPoint>) {
        quad = next
        render()
    }

    fun choose(aspect: DeskewAspect) {
        this.aspect = aspect
        render()
    }

    fun clear() {
        loading?.cancel()
        source = null
        canvasImage = null
        preview = null
        fileName = ""
        filePixels = null
        quad = emptyList()
        loadedQuad = emptyList()
        result = null
        unreadable = false
        sourceSize = IntSize.Zero
    }

    // last request wins
    private fun render() {
        val image = canvasImage ?: return
        val source = source ?: return
        if (!isQuadValid) return
        if (rendering) {
            pending = true
            return
        }
        rendering = true
        pending = false
        val scale = image.width.toDouble() / max(source.width, 1)
        val corners = quad.map { QuadPoint(it.x * scale, it.y * scale) }
        val (width, height) = ImageDeskewEngine.capped(ImageDeskewEngine.fitAspect(ImageDeskewEngine.outputSize(quad), aspect.ratio), DESKEW_PREVIEW_EDGE)
        viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) { warp(image, corners, width.roundToInt(), height.roundToInt()) }
            if (canvasImage === image) result = rendered.asImageBitmap()
            rendering = false
            if (pending) render()
        }
    }

    fun export(context: Context) {
        val source = source ?: return
        if (!isQuadValid || isExporting) return
        isExporting = true
        val corners = quad
        val size = outputSize
        val name = "${fileName.substringBeforeLast('.').ifEmpty { "image" }}-deskewed.png"
        viewModelScope.launch {
            val data = withContext(Dispatchers.Default) { runCatching { ImageFiles.png(warp(source, corners, size.width, size.height)) }.getOrNull() }
            isExporting = false
            if (data != null) FileExport.share(context, data, name, "image/png")
        }
    }
}

@Composable
fun ImageDeskewer(model: ImageDeskewerModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val loaded = model.loaded
    val size = model.sourceSize
    val valid = model.isQuadValid
    var dragging by remember { mutableStateOf(false) }

    val aspect = if (loaded) (size.width.toFloat() / max(size.height, 1)).coerceIn(0.6f, 2f) else 4f / 3f
    val board = CanonSection.canvas(CanonCanvasSize.Aspect(aspect), background = c.muted, showsPattern = true) {
        DeskewBoard(
            model.preview,
            if (loaded) size else emptyPixels,
            if (loaded) model.quad else emptyQuad,
            valid = valid || !loaded,
            onMove = model::move,
            onDragging = { dragging = it },
        )
    }

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            val pixels = model.filePixels
            if (!loaded || pixels == null) {
                CanonDropZone("Drop an image", icon = Icons.Outlined.Transform, paste = false, onFiles = { uris -> uris.firstOrNull()?.let(model::load) })
            } else {
                CanonRow(model.fileName, detail = "${pixels.width} × ${pixels.height}", flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                }
            }
            if (model.unreadable && !loaded) CanonStatusRow("That file can't be read as an image", CanonTone.Warn)
        }
    }

    val output = model.outputSize
    val result = CanonSection.canvas(CanonCanvasSize.Aspect(4f / 3f), background = c.muted, maxHeight = 460.dp, boxing = CanonCanvasBoxing.UnlessWide) {
        DeskewResult(model.result, output, valid, animates = !dragging, guidesKey = model.quad to model.aspect)
    }

    val settings = CanonSection.of {
        CanonRows {
            CanonHeading("Aspect")
            CanonSegmented(DeskewAspect.all.map { it.label }, DeskewAspect.all.indexOf(model.aspect).coerceAtLeast(0), columns = 4) {
                model.choose(DeskewAspect.all[it])
            }
            CanonRow("Corners", flush = true) {
                CanonButton("Reset", enabled = loaded, icon = Icons.Rounded.RestartAlt, onClick = model::reset)
            }
            if (loaded && !valid) CanonStatusRow("Don't let the streams cross!", CanonTone.Warn)
            CanonButton(
                "Export PNG",
                kind = CanonButtonKind.Primary,
                enabled = loaded && valid && model.result != null && !model.isExporting,
                icon = Icons.Outlined.Share,
                oversized = true,
            ) { model.export(context) }
            if (model.isExporting) {
                CanonRow("Processing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground) }
            }
        }
    }

    val main = if (loaded) listOf(input, result, settings) else listOf(input, settings)
    CanonSplit(sidebar = listOf(board), main = main, halves = true, collapsesTo = 240.dp)
}

private val bracketArm = 22.dp
private val bracketStroke = 4.dp
private val loupeLargest = 116.dp
private val loupeGap = 34.dp

/**
 * dt-ios DeskewBoard: the photo with everything outside the quad shaded, the quad's foreshortened grid, outline and
 * corner brackets; a corner, an edge or the whole quad drags, and a loupe shows the pixels under a dragged corner.
 */
@Composable
private fun DeskewBoard(
    preview: ImageBitmap?,
    pixels: IntSize,
    quad: List<QuadPoint>,
    valid: Boolean,
    onMove: (List<QuadPoint>) -> Unit,
    onDragging: (Boolean) -> Unit,
) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val top = LocalCanonCanvas.current.top
    var grab by remember { mutableStateOf<DeskewGrab?>(null) }
    var start by remember { mutableStateOf(emptyList<QuadPoint>()) }
    var from by remember { mutableStateOf(QuadPoint(0.0, 0.0)) }
    var loupeSides by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    val currentQuad by rememberUpdatedState(quad)
    val loaded = preview != null

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val bounds = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val (frame, shown) = with(density) {
            val padding = CanonMetrics.panelPadding.toPx()
            val area = Rect(padding, top.toPx() + padding, bounds.width - padding, bounds.height - padding)
            val scale = if (area.width > 0 && area.height > 0) min(area.width / pixels.width, area.height / pixels.height) else 0f
            val fitted = Size(pixels.width * scale, pixels.height * scale)
            val rect = Rect(Offset(area.center.x - fitted.width / 2, area.center.y - fitted.height / 2), fitted)
            rect to quad.map { Offset(rect.left + (it.x / pixels.width).toFloat() * rect.width, rect.top + (it.y / pixels.height).toFloat() * rect.height) }
        }
        fun imagePixels(point: Offset) = QuadPoint(
            ((point.x - frame.left) / max(frame.width, 1f) * pixels.width).toDouble(),
            ((point.y - frame.top) / max(frame.height, 1f) * pixels.height).toDouble(),
        )
        fun grabAt(point: Offset) = DeskewGrab.at(QuadPoint(point.x.toDouble(), point.y.toDouble()), shown.map { QuadPoint(it.x.toDouble(), it.y.toDouble()) }, density.density.toDouble())
        val shownNow by rememberUpdatedState(shown)

        val ink = when {
            !loaded -> c.mutedForeground
            valid -> c.primary
            else -> c.destructive
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .canonGrabPan(
                    grabs = { point -> loaded && shownNow.size == 4 && grabAt(point) != null },
                    onStart = { point ->
                        grab = grabAt(point)
                        start = currentQuad
                        from = imagePixels(point)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onDragging(true)
                    },
                    onEnd = {
                        grab = null
                        loupeSides = null
                        onDragging(false)
                    },
                ) { change, _ ->
                    val held = grab ?: return@canonGrabPan
                    val now = imagePixels(change.position)
                    onMove(DeskewGrab.move(start, held, now.x - from.x, now.y - from.y, pixels.width.toDouble(), pixels.height.toDouble()))
                },
        ) {
            if (shown.size != 4) return@Canvas
            if (preview != null) {
                drawImage(
                    preview,
                    dstOffset = IntOffset(frame.left.roundToInt(), frame.top.roundToInt()),
                    dstSize = IntSize(frame.width.roundToInt(), frame.height.roundToInt()),
                )
            } else {
                drawRect(c.card, frame.topLeft, frame.size)
                drawRect(c.border, frame.topLeft, frame.size, style = Stroke(CanonMetrics.hairline.toPx()))
            }
            val polygon = Path().apply {
                moveTo(shown[0].x, shown[0].y)
                shown.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            if (loaded) {
                val dim = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(frame)
                    addPath(polygon)
                }
                drawPath(dim, Color.Black.copy(alpha = 0.5f))
            } else {
                drawPath(polygon, c.accentBg)
            }
            if (valid) {
                val grid = if (loaded) Color.White.copy(alpha = 0.45f) else c.border
                for ((a, b) in ImageDeskewEngine.grid(shown.map { QuadPoint(it.x.toDouble(), it.y.toDouble()) }, DIVISIONS)) {
                    drawLine(grid, Offset(a.x.toFloat(), a.y.toFloat()), Offset(b.x.toFloat(), b.y.toFloat()), CanonMetrics.hairline.toPx())
                }
            }
            drawPath(polygon, ink, style = Stroke(CanonMetrics.frame.toPx(), join = StrokeJoin.Miter))
            val brackets = Path()
            for (index in 0 until 4) {
                val corner = shown[index]
                val ends = listOf(shown[(index + 3) % 4], shown[(index + 1) % 4]).map { end ->
                    val length = hypot(end.x - corner.x, end.y - corner.y)
                    if (length <= 0f) corner else corner + (end - corner) * (min(bracketArm.toPx(), length / 3) / length)
                }
                brackets.moveTo(ends[0].x, ends[0].y)
                brackets.lineTo(corner.x, corner.y)
                brackets.lineTo(ends[1].x, ends[1].y)
            }
            drawPath(brackets, ink, style = Stroke(bracketStroke.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Miter))
        }

        if (loaded && shown.size == 4) {
            CornerTargets(shown, quad, pixels, onMove)
            DeskewHint(frame, shown, visible = grab == null)
        }

        val held = grab
        if (preview != null && held is DeskewGrab.Corner && shown.size == 4) {
            val index = held.index
            val corner = shown[index]
            with(density) {
                val side = min(loupeLargest.toPx(), max(72.dp.toPx(), bounds.height * 0.4f))
                val loupe = canonLoupeFrame(corner, side, loupeGap.toPx(), bounds, top.toPx() + 8.dp.toPx(), 8.dp.toPx(), loupeSides)
                if (loupeSides == null) loupeSides = (loupe.center.x < corner.x) to (loupe.center.y < corner.y)
                val image = remember(preview) { preview.asAndroidBitmap() }
                val x = PixelPickerSampler.index(quad[index].x / pixels.width, image.width)
                val y = PixelPickerSampler.index(quad[index].y / pixels.height, image.height)
                val patch = PixelPickerSampler.loupe(image.width, image.height, x, y, LOUPE_RADIUS, image::getPixel)
                val cells = Bitmap.createBitmap(patch, LOUPE_RADIUS * 2 + 1, LOUPE_RADIUS * 2 + 1, Bitmap.Config.ARGB_8888).asImageBitmap()
                CanonLoupe(cells, Color(image.getPixel(x, y)), Modifier.canonPlaced(CanonDimensionPlacement(loupe.center, loupe.size)))
                val border = CanonLoupeBorder.toPx()
                val inner = Rect(loupe.left + border, loupe.top + border, loupe.right - border, loupe.bottom - border)
                val rays = if (valid) c.primary else c.destructive
                Canvas(Modifier.fillMaxSize()) {
                    clipRect(inner.left, inner.top, inner.right, inner.bottom) {
                        for (neighbour in listOf(shown[(index + 3) % 4], shown[(index + 1) % 4])) {
                            val d = neighbour - corner
                            val length = max(hypot(d.x, d.y), 0.001f)
                            drawLine(rays, inner.center, inner.center + d * (side / length), 2.dp.toPx())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CornerTargets(shown: List<Offset>, quad: List<QuadPoint>, pixels: IntSize, onMove: (List<QuadPoint>) -> Unit) {
    val density = LocalDensity.current
    val nudges = listOf("Up" to (0 to -1), "Down" to (0 to 1), "Left" to (-1 to 0), "Right" to (1 to 0))
    DeskewCorner.entries.forEachIndexed { index, corner ->
        val at = shown[index]
        val side = with(density) { 44.dp.toPx() }
        Box(
            Modifier
                .canonPlaced(CanonDimensionPlacement(at, Size(side, side)))
                .semantics {
                    contentDescription = corner.label
                    stateDescription = "${quad[index].x.roundToInt()}, ${quad[index].y.roundToInt()}"
                    customActions = nudges.map { (name, step) ->
                        CustomAccessibilityAction(name) {
                            val dx = step.first * pixels.width / 100.0
                            val dy = step.second * pixels.height / 100.0
                            onMove(DeskewGrab.move(quad, DeskewGrab.Corner(index), dx, dy, pixels.width.toDouble(), pixels.height.toDouble()))
                            true
                        }
                    }
                },
        )
    }
}

@Composable
private fun DeskewHint(image: Rect, quad: List<Offset>, visible: Boolean) {
    val c = LocalDelphiColours.current
    val box = Rect(quad.minOf { it.x }, quad.minOf { it.y }, quad.maxOf { it.x }, quad.maxOf { it.y })
    val strips = listOf(
        Rect(image.left, image.top, image.right, box.top),
        Rect(image.left, box.bottom, image.right, image.bottom),
        Rect(image.left, image.top, box.left, image.bottom),
        Rect(box.right, image.top, image.right, image.bottom),
    )
    val spare = with(LocalDensity.current) { 8.dp.toPx() }
    var fits by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible && fits) 1f else 0f, tween(200))
    Layout(
        content = {
            Row(
                Modifier.alpha(alpha).background(c.card.copy(alpha = 0.88f)).padding(start = 8.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.PanTool, null, tint = c.foreground, modifier = Modifier.size(14.dp))
                Text("Drag the corners", style = CanonText.captionSemibold, color = c.foreground, maxLines = 1)
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val chip = measurables[0].measure(Constraints())
        val room = strips.filter { it.width >= chip.width + spare && it.height >= chip.height + spare }.maxByOrNull { it.width * it.height }
        fits = room != null
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (room != null) chip.place((room.center.x - chip.width / 2f).roundToInt(), (room.center.y - chip.height / 2f).roundToInt())
        }
    }
}

// dt-ios DeskewResultPicture
@Composable
private fun DeskewResult(image: ImageBitmap?, size: IntSize, valid: Boolean, animates: Boolean, guidesKey: Any) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val top = LocalCanonCanvas.current.top
    val guides = remember { Animatable(0f) }
    LaunchedEffect(guidesKey) {
        if (!ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
        guides.animateTo(1f, tween(150))
        delay(1000)
        guides.animateTo(0f, tween(500))
    }
    val shownWidth by animateFloatAsState(size.width.toFloat(), if (animates) tween(450) else tween(0))
    val shownHeight by animateFloatAsState(size.height.toFloat(), if (animates) tween(450) else tween(0))
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().semantics { contentDescription = "Result"; stateDescription = "${size.width} × ${size.height}" }) {
        with(density) {
            val padding = CanonMetrics.panelPadding.toPx()
            val area = Rect(padding, top.toPx() + padding * 0.5f, constraints.maxWidth - padding, constraints.maxHeight - padding)
            val sheet = canonDimensionSheet(Size(max(1f, shownWidth), max(1f, shownHeight)), area, rightToLeft)
            val hair = CanonMetrics.hairline.toPx()
            val edge = Rect(sheet.left - hair, sheet.top - hair, sheet.right + hair, sheet.bottom + hair)
            Box(Modifier.canonPlaced(CanonDimensionPlacement(edge.center, edge.size)).background(c.border))
            Box(Modifier.canonPlaced(CanonDimensionPlacement(sheet.center, sheet.size)).background(c.card).clipToBounds()) {
                if (image != null) {
                    Image(image, null, Modifier.fillMaxSize().alpha(if (valid) 1f else 0.35f), contentScale = ContentScale.FillBounds)
                } else {
                    CanonLoader(Modifier.align(Alignment.Center))
                }
                Canvas(Modifier.fillMaxSize()) {
                    if (guides.value <= 0f) return@Canvas
                    val ink = Color.White.copy(alpha = 0.6f * guides.value)
                    for (step in 1 until DIVISIONS) {
                        val share = step.toFloat() / DIVISIONS
                        drawLine(ink, Offset(this.size.width * share, 0f), Offset(this.size.width * share, this.size.height), hair, blendMode = BlendMode.Difference)
                        drawLine(ink, Offset(0f, this.size.height * share), Offset(this.size.width, this.size.height * share), hair, blendMode = BlendMode.Difference)
                    }
                }
            }
            val (across, down) = canonDimensionLines(sheet, rightToLeft)
            CanonDimensionLine(size.width, Modifier.canonPlaced(across))
            CanonDimensionLine(size.height, Modifier.canonPlaced(down))
        }
    }
}
