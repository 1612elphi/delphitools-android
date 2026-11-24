package com.delphi.delphitools.tools.images

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDimensionLine
import com.delphi.delphitools.canon.CanonDimensionLineHeight
import com.delphi.delphitools.canon.CanonDimensionPlacement
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonChecks
import com.delphi.delphitools.canon.canonDimensionLines
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.canonGrabPan
import com.delphi.delphitools.canon.canonPlaced
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private const val MASK_MAX_PIXELS = 4096
private const val MASK_PREVIEW_PIXELS = 1600
private const val THUMB_SIDE = 144
private const val THUMB_DELAY_MS = 250L
private const val GHOST_ALPHA = 0.22f
private val scales = listOf(0.5, 1.0, 2.0, 3.0)
private val placeholderColour = Color(MaskSpec.PLACEHOLDER)

private fun log2(value: Double) = ln(value) / ln(2.0)

private fun MaskShape.androidPath(scale: Float): android.graphics.Path = android.graphics.Path().also { path ->
    for ((command, p) in commands) {
        val q = FloatArray(p.size) { (p[it] * scale).toFloat() }
        when (command) {
            'M' -> path.moveTo(q[0], q[1])
            'L' -> path.lineTo(q[0], q[1])
            'C' -> path.cubicTo(q[0], q[1], q[2], q[3], q[4], q[5])
            'Z' -> path.close()
        }
    }
}

private fun MaskShape.composePath(scale: Float, left: Float = 0f, top: Float = 0f): Path = Path().also { path ->
    for ((command, p) in commands) {
        fun x(i: Int) = left + (p[i] * scale).toFloat()
        fun y(i: Int) = top + (p[i] * scale).toFloat()
        when (command) {
            'M' -> path.moveTo(x(0), y(1))
            'L' -> path.lineTo(x(0), y(1))
            'C' -> path.cubicTo(x(0), y(1), x(2), y(3), x(4), y(5))
            'Z' -> path.close()
        }
    }
}

// dt-core MaskSpec.render
private fun maskRender(image: Bitmap?, spec: MaskSpec, shape: MaskShape, side: Int): Bitmap {
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    if (image == null || !spec.covers(image.width, image.height, side.toDouble())) canvas.drawColor(MaskSpec.PLACEHOLDER)
    if (image != null) {
        val (x, y, w, h) = spec.imageFrame(image.width, image.height, side.toDouble())
        canvas.drawBitmap(image, null, RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    // clipPath is not anti-aliased
    val mask = Bitmap.createBitmap(side, side, Bitmap.Config.ALPHA_8)
    android.graphics.Canvas(mask).drawPath(shape.androidPath(side / 100f), Paint(Paint.ANTI_ALIAS_FLAG))
    canvas.drawBitmap(mask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
    return out
}

private class MaskSource(val image: Bitmap, val preview: Bitmap, val name: String, val width: Int, val height: Int) {
    val shownPreview = preview.asImageBitmap()
    val stem get() = name.substringBeforeLast('.').ifEmpty { "mask" }
}

/** dt-ios ImageMaskerViewController: an image framed in a square and cut to a shape. */
class ImageMaskerModel(application: Application) : AndroidViewModel(application) {
    var spec by mutableStateOf(MaskSpec())
        private set
    var shape by mutableStateOf(MaskShapes.default)
        private set
    private var source by mutableStateOf<MaskSource?>(null)
    var failedName by mutableStateOf<String?>(null)
        private set
    var exporting by mutableStateOf(false)
        private set
    var thumb by mutableStateOf<ImageBitmap?>(null)
        private set
    private var loading: Job? = null
    private var thumbing: Job? = null

    val loaded get() = source != null
    val name get() = source?.name ?: ""
    val pixels get() = source?.let { IntSize(it.width, it.height) }
    val preview get() = source?.shownPreview
    val side get() = spec.side(source?.image?.width, source?.image?.height)
    val previewPixels get() = source?.let { IntSize(it.preview.width, it.preview.height) }

    fun load(uri: Uri) {
        loading?.cancel()
        val resolver = getApplication<Application>().contentResolver
        loading = viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { resolver.canonDisplayName(uri) ?: "" }
            val loaded = withContext(Dispatchers.IO) {
                val decoded = runCatching { ImageFiles.decode(ImageDecoder.createSource(resolver, uri), MASK_MAX_PIXELS) }.getOrNull()
                    ?: return@withContext null
                ImageFiles.unlessOutOfMemory { MaskSource(decoded.image, ImageCompressEngine.preview(decoded.image, MASK_PREVIEW_PIXELS), name, decoded.width, decoded.height) }
            }
            failedName = if (loaded == null) name else null
            if (loaded == null) return@launch
            source = loaded
            spec = spec.copy(zoom = 1.0, offsetX = 0.0, offsetY = 0.0)
            redrawThumb(now = true)
        }
    }

    fun clear() {
        loading?.cancel()
        source = null
        spec = spec.copy(zoom = 1.0, offsetX = 0.0, offsetY = 0.0)
        redrawThumb(now = true)
    }

    fun pick(picked: MaskShape) {
        shape = picked
    }

    /** [value] is log2 of the zoom: 100 % sits mid-track. */
    fun zoomTo(value: Float) {
        spec = spec.copy(zoom = 2.0.pow(value.toDouble()))
        redrawThumb(now = false)
    }

    fun drag(dx: Float, dy: Float, side: Float) {
        spec = spec.copy(offsetX = spec.offsetX + dx / side, offsetY = spec.offsetY + dy / side)
        redrawThumb(now = false)
    }

    fun pinch(factor: Float) {
        if (source == null) return
        spec = spec.copy(zoom = (spec.zoom * factor).coerceIn(MaskSpec.MIN_ZOOM, MaskSpec.MAX_ZOOM))
        redrawThumb(now = false)
    }

    fun reset() {
        if (spec.zoom == 1.0 && spec.offsetX == 0.0 && spec.offsetY == 0.0) return
        spec = spec.copy(zoom = 1.0, offsetX = 0.0, offsetY = 0.0)
        redrawThumb(now = true)
    }

    fun chooseScale(index: Int) {
        spec = spec.copy(scale = scales[index])
    }

    private fun redrawThumb(now: Boolean) {
        thumbing?.cancel()
        val preview = source?.preview
        if (preview == null) {
            thumb = null
            return
        }
        val spec = spec
        thumbing = viewModelScope.launch {
            if (!now) delay(THUMB_DELAY_MS)
            thumb = withContext(Dispatchers.Default) { maskRender(preview, spec, MaskShapes.square, THUMB_SIDE).asImageBitmap() }
        }
    }

    private suspend fun rendered(): ByteArray? {
        val image = source?.image
        val spec = spec
        val shape = shape
        val side = side
        return withContext(Dispatchers.Default) { runCatching { ImageFiles.png(maskRender(image, spec, shape, side)) }.getOrNull() }
    }

    private val filename get() = "${source?.stem ?: "mask"}-${shape.id}.png"

    fun copy(context: Context) {
        val name = filename
        viewModelScope.launch { rendered()?.let { ImageFiles.copyPng(context, it, name) } }
    }

    fun export(context: Context) {
        if (exporting) return
        exporting = true
        val name = filename
        viewModelScope.launch {
            val data = rendered()
            exporting = false
            if (data != null) FileExport.share(context, data, name, "image/png")
        }
    }
}

@Composable
fun ImageMasker(model: ImageMaskerModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val loaded = model.loaded
    val spec = model.spec
    val side = model.side

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(1f), background = c.muted, showsPattern = true) {
        MaskBoard(model.preview, model.previewPixels, model.shape, spec, side, onDrag = model::drag, onPinch = model::pinch)
    }

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            val pixels = model.pixels
            if (pixels == null) {
                CanonDropZone("Drop an image", paste = false, onFiles = { uris -> uris.firstOrNull()?.let(model::load) })
            } else {
                CanonRow(model.name, detail = "${pixels.width} × ${pixels.height}", flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                }
            }
            model.failedName?.let { CanonStatusRow("Could not read $it", CanonTone.Warn) }
        }
    }

    // ponytail: no custom SVG masks; dt-ios rasterises them in an offscreen WKWebView, add an offscreen WebView snapshot when wanted
    val shapes = CanonSection.of {
        CanonRows {
            CanonHeading("Shape")
            MaskShapePicker(model.shape, model.thumb, model::pick)
        }
    }

    val framing = CanonSection.of {
        CanonRows {
            CanonHeading("Frame")
            CanonRow("Zoom", flush = true) {
                Box(Modifier.alpha(if (loaded) 1f else 0.4f)) {
                    CanonSlider(
                        log2(spec.zoom).toFloat(),
                        { if (loaded) model.zoomTo(it) },
                        Modifier.fillMaxSize(),
                        range = log2(MaskSpec.MIN_ZOOM).toFloat()..log2(MaskSpec.MAX_ZOOM).toFloat(),
                        step = 0.01f,
                        readout = "${(spec.zoom * 100).roundToInt()} %",
                    )
                }
            }
            CanonGrid {
                CanonButton(
                    "Reset",
                    Modifier.weight(1f).fillMaxHeight(),
                    enabled = loaded && (spec.zoom != 1.0 || spec.offsetX != 0.0 || spec.offsetY != 0.0),
                    icon = Icons.Rounded.RestartAlt,
                    onClick = model::reset,
                )
            }
        }
    }

    val output = CanonSection.of {
        CanonRows {
            CanonHeading("Output")
            CanonSegmented(scales.map { "${TraceSettings.number(it)}×" }, scales.indexOf(spec.scale).takeIf { it >= 0 } ?: 1, onSelect = model::chooseScale)
            CanonTiles(listOf(CanonTile("Size", "$side × $side px"), CanonTile("Shape", model.shape.title)))
            CanonGrid {
                CanonButton("Copy", Modifier.weight(1f).fillMaxHeight(), icon = Icons.Rounded.ContentCopy, flashesCopied = true) { model.copy(context) }
                CanonButton(
                    "Export PNG",
                    Modifier.weight(1f).fillMaxHeight(),
                    kind = CanonButtonKind.Primary,
                    enabled = !model.exporting,
                    icon = Icons.Outlined.Share,
                    oversized = true,
                ) { model.export(context) }
            }
            if (model.exporting) {
                CanonRow("Processing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground) }
            }
        }
    }

    CanonSplit(sidebar = listOf(canvas), main = listOf(input, shapes, framing, output), halves = true, collapsesTo = 240.dp)
}

/**
 * dt-ios MaskBoard: the square over the checks, the image's overhang ghosted round it, the cut lit inside the shape
 * with its outline, the export side measured above. Dragging moves the image, pinching zooms it.
 */
@Composable
private fun MaskBoard(
    preview: ImageBitmap?,
    pixels: IntSize?,
    shape: MaskShape,
    spec: MaskSpec,
    side: Int,
    onDrag: (Float, Float, Float) -> Unit,
    onPinch: (Float) -> Unit,
) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val top = LocalCanonCanvas.current.top
    var dragging by remember { mutableStateOf(false) }
    val pinch by rememberUpdatedState(onPinch)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .semantics {
                contentDescription = shape.title
                stateDescription = "$side × $side px"
            },
    ) {
        val square = with(density) {
            val padding = CanonMetrics.panelPadding.toPx()
            val dimension = (CanonDimensionLineHeight + 8.dp).toPx()
            val area = Rect(padding, top.toPx() + padding * 0.5f + dimension, constraints.maxWidth - padding, constraints.maxHeight - padding)
            val length = max(0f, min(area.width, area.height)).roundToInt().toFloat()
            Rect(Offset((area.center.x - length / 2).roundToInt().toFloat(), (area.center.y - length / 2).roundToInt().toFloat()), Size(length, length))
        }
        val squareNow by rememberUpdatedState(square)
        val frame = pixels?.let {
            val (x, y, w, h) = spec.imageFrame(it.width, it.height, square.width.toDouble())
            Rect(square.left + x.toFloat(), square.top + y.toFloat(), square.left + (x + w).toFloat(), square.top + (y + h).toFloat())
        }
        val slack = with(density) { 8.dp.toPx() }
        Box(Modifier.canonPlaced(CanonDimensionPlacement(square.center, square.size)).canonChecks())
        Canvas(
            Modifier
                .fillMaxSize()
                .canonGrabPan(
                    grabs = { preview != null && squareNow.inflate(slack).contains(it) },
                    onStart = { dragging = true },
                    onEnd = { dragging = false },
                ) { _, delta -> onDrag(delta.x, delta.y, max(squareNow.width, 1f)) }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        var previous: Float? = null
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val down = event.changes.filter { it.pressed }
                            if (down.size < 2) {
                                previous = null
                                continue
                            }
                            val spread = (down[0].position - down[1].position).getDistance()
                            previous?.let { if (it > 0f && spread > 0f) pinch(spread / it) }
                            previous = spread
                        }
                    }
                },
        ) {
            val path = shape.composePath(square.width / 100f, square.left, square.top)
            if (preview != null && frame != null) {
                drawImage(
                    preview,
                    dstOffset = IntOffset(frame.left.roundToInt(), frame.top.roundToInt()),
                    dstSize = IntSize(frame.width.roundToInt(), frame.height.roundToInt()),
                    alpha = GHOST_ALPHA,
                )
            }
            val hair = CanonMetrics.hairline.toPx()
            drawRect(
                c.mutedForeground,
                Offset(square.left - hair / 2, square.top - hair / 2),
                Size(square.width + hair, square.height + hair),
                style = Stroke(hair, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )
            clipPath(path) {
                drawRect(placeholderColour, square.topLeft, square.size)
                if (preview != null && frame != null) {
                    drawImage(
                        preview,
                        dstOffset = IntOffset(frame.left.roundToInt(), frame.top.roundToInt()),
                        dstSize = IntSize(frame.width.roundToInt(), frame.height.roundToInt()),
                    )
                }
            }
            drawPath(path, c.primary, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
        }
        val (across, _) = with(density) { canonDimensionLines(square, rightToLeft, gap = 8.dp) }
        CanonDimensionLine(side, Modifier.canonPlaced(across))
        MaskHint(square, visible = preview != null && !dragging)
    }
}

@Composable
private fun MaskHint(square: Rect, visible: Boolean) {
    val c = LocalDelphiColours.current
    val gap = with(LocalDensity.current) { 10.dp.toPx() }
    var fits by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible && fits) 1f else 0f, tween(200))
    Layout(
        content = {
            Row(
                Modifier.alpha(alpha).background(c.card.copy(alpha = 0.88f)).padding(start = 8.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.OpenWith, null, tint = c.foreground, modifier = Modifier.size(14.dp))
                Text("Drag to reposition", style = CanonText.captionSemibold, color = c.foreground, maxLines = 1)
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val chip = measurables[0].measure(Constraints())
        fits = square.width > chip.width * 1.8f
        layout(constraints.maxWidth, constraints.maxHeight) {
            chip.place((square.center.x - chip.width / 2f).roundToInt(), (square.bottom - chip.height - gap).roundToInt())
        }
    }
}

private val wideWidth = 600.dp

// dt-ios MaskShapePicker
@Composable
private fun MaskShapePicker(chosen: MaskShape, thumb: ImageBitmap?, onPick: (MaskShape) -> Unit) {
    val c = LocalDelphiColours.current
    val families = MaskShapes.grouped
    val family = families.indexOfFirst { it.first == chosen.family }.coerceAtLeast(0)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= wideWidth
        Column(Modifier.fillMaxWidth().background(c.border), verticalArrangement = Arrangement.spacedBy(CanonMetrics.frame)) {
            CanonGrid(columns = if (wide) 14 else 7) {
                families.forEachIndexed { index, (name, shapes) ->
                    MaskShapeCell(shapes.first(), name.replaceFirstChar { it.uppercase() }, null, chosen = index == family) {
                        val variant = (chosen.variant - 1).coerceIn(0, shapes.size - 1)
                        onPick(shapes[variant])
                    }
                }
            }
            CanonGrid(columns = if (wide) 12 else 6) {
                families[family].second.forEach { variant ->
                    MaskShapeCell(variant, variant.title, thumb, chosen = variant.id == chosen.id) { onPick(variant) }
                }
            }
        }
    }
}

private val cellInset = 7.dp

@Composable
private fun MaskShapeCell(shape: MaskShape, title: String, thumb: ImageBitmap?, chosen: Boolean, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    Canvas(
        Modifier
            .aspectRatio(1f)
            .background(if (chosen) c.primary else c.card)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = title
                role = Role.Button
                selected = chosen
            },
    ) {
        val inset = cellInset.toPx()
        val length = max(0f, min(size.width, size.height) - inset * 2)
        val left = (size.width - length) / 2
        val top = (size.height - length) / 2
        val path = shape.composePath(length / 100f, left, top)
        if (thumb != null) {
            clipPath(path) {
                drawImage(
                    thumb,
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(length.roundToInt(), length.roundToInt()),
                )
            }
        } else {
            drawPath(path, if (chosen) c.primaryForeground else c.foreground)
        }
    }
}
