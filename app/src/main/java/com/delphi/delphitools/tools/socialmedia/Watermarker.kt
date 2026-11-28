package com.delphi.delphitools.tools.socialmedia

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourWell
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.toArgb
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.tools.images.ImageCrop
import com.delphi.delphitools.tools.images.ImageFiles
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val SOURCE_MAX_PIXELS = 3072
private const val STAMP_MAX_PIXELS = 512
private const val SETTLE_MS = 250L
private const val TILE_GAP = 60f
private val markSide = 7.dp
private val markStroke = 1.5.dp

private val positionIcons @Composable get() = listOf(pencil(R.drawable.pencil_arrow_up_left), pencil(R.drawable.pencil_arrow_up), pencil(R.drawable.pencil_arrow_up_right), pencil(R.drawable.pencil_arrow_left), Icons.Rounded.Circle, pencil(R.drawable.pencil_arrow_right), pencil(R.drawable.pencil_arrow_down_left), pencil(R.drawable.pencil_arrow_down), pencil(R.drawable.pencil_arrow_down_right))

private class WatermarkSettings(
    val kind: WatermarkKind,
    val text: String,
    val fontSize: Float,
    val colour: Int,
    val stamp: Bitmap?,
    val opacity: Float,
    val position: WatermarkPosition,
    val tiling: Boolean,
)

/** dt-ios WatermarkerViewModel and the controller's file state: the source re-rendered with its mark on every change. */
class WatermarkerModel(application: Application) : AndroidViewModel(application) {
    private var source by mutableStateOf<Bitmap?>(null)
    var sourcePreview by mutableStateOf<ImageBitmap?>(null)
        private set
    var sourceName by mutableStateOf("Image")
        private set
    var sourceDetail by mutableStateOf("")
        private set

    var kind by mutableStateOf(WatermarkKind.Text)
        private set
    var text by mutableStateOf("Watermark")
        private set
    var fontSize by mutableFloatStateOf(48f)
        private set
    var colour by mutableStateOf(Rgb.white)
        private set
    private var stamp by mutableStateOf<Bitmap?>(null)
    val hasStamp get() = stamp != null
    var stampName by mutableStateOf("")
        private set
    var stampDetail by mutableStateOf("")
        private set
    var opacity by mutableFloatStateOf(0.3f)
        private set
    var position by mutableStateOf(WatermarkPosition.BottomRight)
        private set
    var tiling by mutableStateOf(false)
        private set

    private var resultImage: Bitmap? = null
    var result by mutableStateOf<ImageBitmap?>(null)
        private set

    private var loading: Job? = null
    private var task: Job? = null
    // serialised: full canvas each
    private val renderer = Dispatchers.Default.limitedParallelism(1)

    val stem get() = sourceName.substringBeforeLast('.').ifEmpty { "image" }

    private suspend fun decode(uri: Uri, maxPixels: Int) = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val decoded = runCatching { ImageFiles.decode(ImageDecoder.createSource(resolver, uri), maxPixels) }.getOrNull()
        decoded?.let { Triple(it, resolver.canonDisplayName(uri), "${it.width} × ${it.height}") }
    }

    fun load(uri: Uri) {
        loading?.cancel()
        loading = viewModelScope.launch {
            val (decoded, name, _) = decode(uri, SOURCE_MAX_PIXELS) ?: return@launch
            sourceName = name ?: "Image"
            sourceDetail = "${decoded.image.width} × ${decoded.image.height}"
            source = decoded.image
            sourcePreview = decoded.image.asImageBitmap()
            resultImage = null
            result = null
            generate()
        }
    }

    fun clear() {
        loading?.cancel()
        task?.cancel()
        source = null
        sourcePreview = null
        resultImage = null
        result = null
    }

    fun loadStamp(uri: Uri) {
        viewModelScope.launch {
            val (decoded, name, detail) = decode(uri, STAMP_MAX_PIXELS) ?: return@launch
            stampName = name ?: "Logo"
            stampDetail = detail
            stamp = decoded.image
            generate()
        }
    }

    fun clearStamp() {
        stamp = null
        generate()
    }

    fun selectKind(kind: WatermarkKind) {
        this.kind = kind
        generate(SETTLE_MS)
    }

    fun changeText(text: String) {
        this.text = text
        generate()
    }

    fun changeFontSize(size: Float) {
        fontSize = size
        generate(SETTLE_MS)
    }

    fun changeColour(colour: Rgb) {
        this.colour = colour
        generate(SETTLE_MS)
    }

    fun changeOpacity(value: Float) {
        opacity = value
        generate(SETTLE_MS)
    }

    fun choose(position: WatermarkPosition) {
        if (position == this.position) return
        this.position = position
        generate(SETTLE_MS)
    }

    fun changeTiling(on: Boolean) {
        tiling = on
        generate(SETTLE_MS)
    }

    fun export(context: Context) {
        val image = resultImage ?: return
        viewModelScope.launch {
            val data = withContext(Dispatchers.Default) { ImageFiles.png(image) } ?: return@launch
            FileExport.share(context, data, "$stem-watermarked.png", "image/png")
        }
    }

    private fun generate(delayMs: Long = 0) {
        task?.cancel()
        val source = source ?: run {
            resultImage = null
            result = null
            return
        }
        val settings = WatermarkSettings(kind, text, fontSize, colour.toArgb(), stamp, opacity, position, tiling)
        task = viewModelScope.launch {
            delay(delayMs)
            val made = withContext(renderer) { ImageFiles.unlessOutOfMemory { renderWatermark(source, settings) } } ?: return@launch
            resultImage = made
            result = made.asImageBitmap()
        }
    }
}

// dt-ios WatermarkerViewModel.render
private fun renderWatermark(source: Bitmap, settings: WatermarkSettings): Bitmap {
    val width = source.width.toFloat()
    val height = source.height.toFloat()
    val canvasSize = Size(width, height)
    val out = createBitmap(source.width, source.height)
    val canvas = android.graphics.Canvas(out)
    canvas.drawBitmap(source, 0f, 0f, null)
    canvas.saveLayerAlpha(0f, 0f, width, height, (settings.opacity * 255).roundToInt())

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textSize = settings.fontSize
        color = settings.colour
    }
    val metrics = paint.fontMetrics
    val stamp = settings.stamp
    val drawn: Size
    val draw: (Float, Float) -> Unit
    when (settings.kind) {
        WatermarkKind.Text -> {
            drawn = Size(paint.measureText(settings.text), metrics.descent - metrics.ascent)
            draw = { x, y -> canvas.drawText(settings.text, x, y - metrics.ascent, paint) }
        }
        WatermarkKind.Image -> {
            if (stamp == null) {
                canvas.restore()
                return out
            }
            drawn = WatermarkEngine.stampSize(Size(stamp.width.toFloat(), stamp.height.toFloat()), canvasSize, if (settings.tiling) 0.12f else 0.25f)
            draw = { x, y -> canvas.drawBitmap(stamp, null, android.graphics.RectF(x, y, x + drawn.width, y + drawn.height), paint) }
        }
    }

    if (settings.tiling) {
        canvas.withSave {
            translate(width / 2, height / 2)
            rotate(-30f)
            translate(-width, -height)
            var y = 0f
            while (y < height * 3) {
                var x = 0f
                while (x < width * 3) {
                    draw(x, y)
                    x += drawn.width + TILE_GAP
                }
                y += drawn.height + TILE_GAP
            }
        }
    } else {
        val at = WatermarkEngine.anchor(settings.position, Rect(Offset.Zero, canvasSize))
        draw(at.x - drawn.width / 2, at.y - drawn.height / 2)
    }
    canvas.restore()
    return out
}

@Composable
fun Watermarker(model: WatermarkerModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val loaded = model.sourcePreview != null

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(1.25f), background = c.muted, showsPattern = true) {
        WatermarkPicture(model.result ?: model.sourcePreview, model.result != null, if (model.tiling) null else model.position, model::choose)
    }

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            // ponytail: no share-sheet import (iOS acceptShared); add an ACTION_SEND intent filter when wanted
            if (!loaded) {
                CanonDropZone("Drop an image to watermark", onFiles = { uris -> uris.firstOrNull()?.let(model::load) })
            } else {
                CanonRow(model.sourceName, detail = model.sourceDetail, flush = true) {
                    CanonButton("Clear", icon = pencil(R.drawable.pencil_trash_2), onClick = model::clear)
                }
            }
        }
    }

    val mark = CanonSection.of {
        CanonRows {
            CanonHeading("Watermark")
            val kinds = WatermarkKind.entries
            CanonSegmented(kinds.map { it.label }, kinds.indexOf(model.kind)) { model.selectKind(kinds[it]) }
            if (model.kind == WatermarkKind.Text) {
                var typed by remember { mutableStateOf(model.text) }
                LaunchedEffect(typed) {
                    if (typed == model.text) return@LaunchedEffect
                    delay(TYPING_DELAY_MS)
                    model.changeText(typed)
                }
                CanonTextCell(typed, { typed = it }, Modifier.fillMaxWidth(), placeholder = "Watermark text")
                CanonRow("Font Size", flush = true) {
                    CanonSlider(
                        model.fontSize,
                        model::changeFontSize,
                        Modifier.fillMaxSize(),
                        range = 12f..200f,
                        step = 1f,
                        readout = "${model.fontSize.roundToInt()} pt",
                    )
                }
                CanonColourWell(model.colour, model::changeColour, label = "Text Colour")
            } else if (!model.hasStamp) {
                CanonDropZone("Drop a watermark image", onFiles = { uris -> uris.firstOrNull()?.let(model::loadStamp) })
            } else {
                CanonRow(model.stampName, detail = model.stampDetail, flush = true) {
                    CanonButton("Clear", icon = pencil(R.drawable.pencil_trash_2), onClick = model::clearStamp)
                }
            }
            CanonRow("Opacity", flush = true) {
                CanonSlider(
                    model.opacity,
                    model::changeOpacity,
                    Modifier.fillMaxSize(),
                    range = 0.05f..1f,
                    step = 0.05f,
                    readout = "${(model.opacity * 100).roundToInt()}%",
                )
            }
        }
    }

    val placement = CanonSection.of {
        CanonRows {
            CanonHeading("Position")
            val positions = WatermarkPosition.entries
            CanonSegmented(
                positions.map { it.label },
                positions.indexOf(model.position),
                columns = 3,
                enabled = !model.tiling,
                icons = positionIcons,
            ) { model.choose(positions[it]) }
            CanonRow("Tile Watermark") { CanonSwitch(model.tiling, onCheckedChange = model::changeTiling) }
            CanonButton(
                "Export Image",
                kind = CanonButtonKind.Primary,
                enabled = model.result != null,
                icon = pencil(R.drawable.pencil_share),
                oversized = true,
            ) { model.export(context) }
        }
    }

    CanonSplit(sidebar = listOf(canvas), main = listOf(input, mark, placement), halves = true, collapsesTo = 200.dp)
}

// dt-ios WatermarkPicture
@Composable
private fun WatermarkPicture(
    image: ImageBitmap?,
    isFinal: Boolean,
    position: WatermarkPosition?,
    onPosition: (WatermarkPosition) -> Unit,
) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val top = LocalCanonCanvas.current.top
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .semantics {
                contentDescription = "Preview"
                role = Role.Image
            },
    ) {
        val rect = with(density) {
            val padding = CanonMetrics.panelPadding.toPx()
            val area = Rect(padding, top.toPx() + padding, constraints.maxWidth - padding, constraints.maxHeight - padding)
            image?.let { ImageCrop.fit(Size(it.width.toFloat(), it.height.toFloat()), area) } ?: Rect.Zero
        }
        Crossfade(image, animationSpec = tween(200), label = "picture") { shown ->
            Canvas(Modifier.fillMaxSize()) {
                if (shown == null || rect.isEmpty) return@Canvas
                drawImage(
                    shown,
                    dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                    dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                )
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(position, rect) {
                    detectTapGestures { point ->
                        if (position == null) return@detectTapGestures
                        WatermarkEngine.zone(point, rect)?.let(onPosition)
                    }
                },
        ) {
            if (position == null || rect.isEmpty) return@Canvas
            val side = markSide.toPx()
            for (zone in WatermarkPosition.entries) {
                if (zone == position) continue
                val point = WatermarkEngine.anchor(zone, rect)
                val corner = Offset(point.x - side / 2, point.y - side / 2)
                drawRect(c.card.copy(alpha = 0.85f), corner, Size(side, side))
                drawRect(c.primary, corner, Size(side, side), style = Stroke(markStroke.toPx()))
            }
        }
        if (image != null && !isFinal) Box(Modifier.align(Alignment.Center)) { CanonLoader() }
    }
}
