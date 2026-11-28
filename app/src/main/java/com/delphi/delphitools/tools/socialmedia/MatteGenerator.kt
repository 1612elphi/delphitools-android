package com.delphi.delphitools.tools.socialmedia

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourWell
import com.delphi.delphitools.canon.CanonDimensionLine
import com.delphi.delphitools.canon.CanonDimensionPlacement
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonDimensionLines
import com.delphi.delphitools.canon.canonDimensionSheet
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.canonPlaced
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.rgbOf
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.images.ImageCompressEngine
import com.delphi.delphitools.tools.images.ImageFiles
import com.delphi.delphitools.tools.images.argb
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// portrait; swap for landscape
private val ratios = listOf(1 to 1, 4 to 5, 3 to 4, 9 to 16)
private val widths = listOf(1080, 1200, 1440, 2048)

// ponytail: decoded at 4096 on the long side as on iOS; an 8192 export of a larger photo upscales that
private const val IMAGE_PIXELS = 4096
private const val PREVIEW_PIXELS = 1600

// sigma px at blur size
private const val BLUR_SIGMA = 8.0

private class MatteSource(val image: Bitmap, val preview: ImageBitmap, val width: Int, val height: Int, val stem: String)

private val filtered = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

private fun MatteRect.rectF() = RectF(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())

/** MatteSpec.backdrop over [area] of [image] (all of it when null): covering the box, blurred, at a reduced size the caller scales up. */
internal fun backdrop(image: Bitmap, area: android.graphics.Rect?, boxWidth: Int, boxHeight: Int, blur: Double): Bitmap {
    val scale = min(1.0, BLUR_SIGMA / max(blur, 1e-9))
    val width = max(1, (boxWidth * scale).roundToInt())
    val height = max(1, (boxHeight * scale).roundToInt())
    val small = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val (areaWidth, areaHeight) = if (area == null) image.width to image.height else area.width() to area.height()
    val cover = MatteSpec.cover(areaWidth.toDouble(), areaHeight.toDouble(), width.toDouble(), height.toDouble(), MatteSpec.OVERSCAN)
    android.graphics.Canvas(small).drawBitmap(image, area, cover.rectF(), filtered)
    return Bitmap.createBitmap(MatteEngine.blur(small.argb(), width, height, blur * scale), width, height, Bitmap.Config.ARGB_8888)
}

private fun render(spec: MatteSpec, photo: Bitmap): Bitmap {
    val w = spec.width
    val h = spec.height
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    when (spec.style) {
        MatteSpec.Style.Solid -> canvas.drawColor(spec.colour.argb)
        MatteSpec.Style.Gradient -> canvas.drawRect(
            0f, 0f, w.toFloat(), h.toFloat(),
            Paint().apply {
                shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), spec.dominant.argb, spec.dominant.shifted(MatteSpec.FALLOFF).argb, Shader.TileMode.CLAMP)
            },
        )
        MatteSpec.Style.Blurred -> canvas.drawBitmap(backdrop(photo, null, w, h, MatteSpec.BLUR), null, RectF(0f, 0f, w.toFloat(), h.toFloat()), filtered)
    }
    val fit = MatteSpec.fit(photo.width.toDouble(), photo.height.toDouble(), w.toDouble(), h.toDouble(), spec.padding.toDouble())
    canvas.drawBitmap(photo, null, fit.rectF(), filtered)
    return out
}

private fun MatteRgb.rgb() = rgbOf(argb)

/** dt-ios MatteGeneratorViewController's state. */
class MatteGeneratorModel(application: Application) : AndroidViewModel(application) {
    var spec by mutableStateOf(MatteSpec())
        private set
    var isCustom by mutableStateOf(false)
        private set
    private var source by mutableStateOf<MatteSource?>(null)
    var exporting by mutableStateOf(false)
        private set

    /** Bumped on a padding change; the picture shows its guides for a second. */
    var guideFlash by mutableIntStateOf(0)
        private set

    private var loading: Job? = null

    val loaded get() = source != null
    val preview get() = source?.preview
    val stem get() = source?.stem.orEmpty()
    val fileSize get() = source?.let { "${it.width} × ${it.height}" }.orEmpty()
    val sourceWidth get() = source?.width ?: 0
    val sourceHeight get() = source?.height ?: 0

    private fun update(next: MatteSpec) {
        spec = next.copy(width = next.width.coerceIn(100, next.widthLimit))
    }

    fun load(uri: Uri?) {
        loading?.cancel()
        if (uri == null) {
            source = null
            update(spec.copy(dominant = MatteSpec().dominant))
            return
        }
        val resolver = getApplication<Application>().contentResolver
        loading = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val decoded = ImageFiles.decode(ImageDecoder.createSource(resolver, uri), IMAGE_PIXELS) ?: return@withContext null
                val preview = ImageFiles.unlessOutOfMemory { ImageCompressEngine.preview(decoded.image, PREVIEW_PIXELS) } ?: return@withContext null
                val dominant = MatteEngine.average(preview.argb()) ?: MatteSpec().dominant
                val stem = resolver.canonDisplayName(uri)?.substringBeforeLast('.')?.ifEmpty { null } ?: "image"
                MatteSource(decoded.image, preview.asImageBitmap(), decoded.width, decoded.height, stem) to dominant
            } ?: return@launch
            source = loaded.first
            update(spec.copy(dominant = loaded.second))
        }
    }

    fun style(style: MatteSpec.Style) = update(spec.copy(style = style))

    fun colour(colour: Rgb) = update(spec.copy(colour = MatteRgb((colour.clamped.r * 255).roundToInt(), (colour.clamped.g * 255).roundToInt(), (colour.clamped.b * 255).roundToInt())))

    fun pickRatio(index: Int) {
        isCustom = index == ratios.size
        if (isCustom) return
        val (short, long) = ratios[index]
        update(if (spec.ratioW > spec.ratioH) spec.copy(ratioW = long, ratioH = short) else spec.copy(ratioW = short, ratioH = long))
    }

    fun ratio(across: Int, down: Int) = update(spec.copy(ratioW = across, ratioH = down))

    fun swap() = update(spec.copy(ratioW = spec.ratioH, ratioH = spec.ratioW))

    fun width(width: Int) = update(spec.copy(width = width))

    fun padding(padding: Int) {
        if (padding == spec.padding) return
        update(spec.copy(padding = padding))
        guideFlash++
    }

    fun export(context: Context) {
        val source = source ?: return
        if (exporting) return
        val spec = spec
        exporting = true
        viewModelScope.launch {
            // ponytail: a failed allocation at the 8192 px cap fails the export
            val data = withContext(Dispatchers.Default) { runCatching { ImageFiles.png(render(spec, source.image)) }.getOrNull() }
            exporting = false
            if (data != null) FileExport.share(context, data, "${source.stem}-matte-${spec.width}x${spec.height}.png", "image/png")
        }
    }
}

@Composable
fun MatteGenerator(model: MatteGeneratorModel = viewModel()) {
    val context = LocalContext.current
    val spec = model.spec

    val canvas = CanonSection.canvas(
        CanonCanvasSize.Aspect((spec.ratioW.toFloat() / spec.ratioH).coerceIn(0.8f, 1.5f)),
        background = LocalDelphiColours.current.muted,
        showsPattern = true,
    ) { MattePicture(model) }

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            if (model.loaded) {
                CanonRow(model.stem, detail = model.fileSize, flush = true) {
                    CanonButton("Clear", icon = pencil(R.drawable.pencil_trash_2)) { model.load(null) }
                }
            } else {
                CanonDropZone("Drop an image", paste = false, onFiles = { uris -> model.load(uris.firstOrNull()) })
            }
        }
    }

    val ratioIndex = if (model.isCustom) ratios.size
    else ratios.indexOfFirst { setOf(it.first, it.second) == setOf(spec.ratioW, spec.ratioH) }.let { if (it < 0) ratios.size else it }
    val shape = when {
        spec.ratioW == spec.ratioH -> "Square"
        spec.ratioW > spec.ratioH -> "Landscape"
        else -> "Portrait"
    }
    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Style")
            CanonSegmented(listOf("Blur", "Solid", "Gradient"), spec.style.ordinal) { model.style(MatteSpec.Style.entries[it]) }
            if (spec.style == MatteSpec.Style.Solid) CanonColourWell(spec.colour.rgb(), model::colour, label = "Colour")
            CanonHeading("Ratio")
            CanonSegmented(ratios.map { "${it.first}:${it.second}" } + "Custom", ratioIndex, onSelect = model::pickRatio)
            if (model.isCustom) {
                CanonGrid(columns = 2) {
                    CanonNumberField(spec.ratioW, { model.ratio(it, spec.ratioH) }, range = 1..32, label = "Ratio width")
                    CanonNumberField(spec.ratioH, { model.ratio(spec.ratioW, it) }, range = 1..32, label = "Ratio height")
                }
            }
            CanonRow("${spec.ratioW}:${spec.ratioH}", detail = shape, flush = true) {
                CanonButton("Swap", icon = pencil(R.drawable.pencil_arrow_left_right), enabled = spec.ratioW != spec.ratioH, onClick = model::swap)
            }
            CanonHeading("Output")
            CanonSegmented(widths.map { it.toString() }, widths.indexOf(spec.width)) { model.width(widths[it]) }
            CanonRow("Width", flush = true) { CanonNumberField(spec.width, model::width, range = 100..spec.widthLimit, step = 10, unit = "px") }
            CanonRow("Padding", flush = true) {
                CanonSlider(spec.padding.toFloat(), { model.padding(it.roundToInt()) }, range = 0f..200f, step = 10f, readout = "${spec.padding} px")
            }
            CanonButton("Export", kind = CanonButtonKind.Primary, enabled = model.loaded && !model.exporting, icon = pencil(R.drawable.pencil_share), oversized = true) {
                model.export(context)
            }
        }
    }

    val details = CanonSection.of {
        CanonRows {
            CanonHeading("Image")
            val photo = MatteSpec.fit(model.sourceWidth.toDouble(), model.sourceHeight.toDouble(), spec.width.toDouble(), spec.height.toDouble(), spec.padding.toDouble())
            CanonTiles(
                listOf(
                    CanonTile("Source", model.fileSize),
                    CanonTile("Photo", "${Math.round(photo.width)} × ${Math.round(photo.height)}"),
                    CanonTile("Scale", "${Math.round(photo.width / max(1, model.sourceWidth) * 100)} %"),
                ),
            )
        }
    }

    CanonSplit(
        sidebar = listOf(canvas),
        main = listOf(input, controls) + if (model.loaded) listOf(details) else emptyList(),
        halves = true,
        collapsesTo = 200.dp,
    )
}

// dt-ios MattePicture
@Composable
private fun MattePicture(model: MatteGeneratorModel) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val top = LocalCanonCanvas.current.top
    val spec = model.spec
    val photo = model.preview
    var shownBackdrop by remember { mutableStateOf<ImageBitmap?>(null) }
    var guides by remember { mutableStateOf(false) }
    LaunchedEffect(model.guideFlash) {
        if (model.guideFlash == 0) return@LaunchedEffect
        guides = true
        delay(1000)
        guides = false
    }
    val guideAlpha by animateFloatAsState(if (guides) 1f else 0f, tween(if (guides) 150 else 500))

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .semantics {
                contentDescription = "${spec.width} × ${spec.height}"
                role = Role.Image
            },
    ) {
        with(density) {
            val padding = CanonMetrics.panelPadding.toPx()
            val area = Rect(padding, top.toPx() + padding * 0.5f, constraints.maxWidth - padding, constraints.maxHeight - padding)
            val target = canonDimensionSheet(Size(spec.width.toFloat(), spec.height.toFloat()), area, rightToLeft)
            val sheet by animateRectAsState(target, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "sheet")

            val box = IntSize(target.width.roundToInt(), target.height.roundToInt())
            val blurred = spec.style == MatteSpec.Style.Blurred
            LaunchedEffect(photo, box, spec.width, blurred) {
                if (photo == null) shownBackdrop = null
                if (!blurred || photo == null || box.width < 1 || box.height < 1) return@LaunchedEffect
                val blur = MatteSpec.BLUR * box.width / spec.width
                shownBackdrop = withContext(Dispatchers.Default) {
                    backdrop(photo.asAndroidBitmap(), null, box.width, box.height, blur).asImageBitmap()
                }
            }

            val (across, down) = canonDimensionLines(sheet, rightToLeft)
            val edge = sheet.inflate(CanonMetrics.hairline.toPx())
            Box(Modifier.canonPlaced(CanonDimensionPlacement(edge.center, edge.size)).background(c.border))
            Box(
                Modifier
                    .canonPlaced(CanonDimensionPlacement(sheet.center, sheet.size))
                    .background(if (spec.style == MatteSpec.Style.Solid) Color(spec.colour.argb) else c.card)
                    .clipToBounds(),
            ) {
                val scale = sheet.width / max(1, spec.width)
                val shapeWidth = photo?.width ?: 3
                val shapeHeight = photo?.height ?: 2
                val fit = MatteSpec.fit(shapeWidth.toDouble(), shapeHeight.toDouble(), spec.width.toDouble(), spec.height.toDouble(), spec.padding.toDouble())
                val photoRect = Rect(Offset(fit.x.toFloat() * scale, fit.y.toFloat() * scale), Size(fit.width.toFloat() * scale, fit.height.toFloat() * scale))
                Canvas(Modifier.fillMaxSize()) {
                    when (spec.style) {
                        MatteSpec.Style.Gradient -> drawRect(
                            Brush.linearGradient(
                                listOf(Color(spec.dominant.argb), Color(spec.dominant.shifted(MatteSpec.FALLOFF).argb)),
                                start = Offset.Zero,
                                end = Offset(size.width, size.height),
                            ),
                        )
                        MatteSpec.Style.Blurred -> shownBackdrop?.let {
                            drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Low)
                        }
                        MatteSpec.Style.Solid -> {}
                    }
                    if (photo != null) {
                        drawImage(
                            photo,
                            dstOffset = IntOffset(photoRect.left.roundToInt(), photoRect.top.roundToInt()),
                            dstSize = IntSize(photoRect.width.roundToInt(), photoRect.height.roundToInt()),
                            filterQuality = FilterQuality.Medium,
                        )
                    } else {
                        drawRect(c.muted, photoRect.topLeft, photoRect.size)
                    }
                    if (guideAlpha > 0f) {
                        val inset = spec.padding * scale
                        val line = CanonMetrics.hairline.toPx()
                        val ink = Color.White.copy(alpha = 0.6f * guideAlpha)
                        drawRect(ink, Offset(inset, 0f), Size(line, size.height), blendMode = BlendMode.Difference)
                        drawRect(ink, Offset(size.width - inset - line, 0f), Size(line, size.height), blendMode = BlendMode.Difference)
                        drawRect(ink, Offset(0f, inset), Size(size.width, line), blendMode = BlendMode.Difference)
                        drawRect(ink, Offset(0f, size.height - inset - line), Size(size.width, line), blendMode = BlendMode.Difference)
                    }
                }
                if (photo == null) {
                    Box(Modifier.canonPlaced(CanonDimensionPlacement(photoRect.center, photoRect.size)), contentAlignment = Alignment.Center) {
                        Text("Photo", style = CanonText.footnoteSemibold, color = c.mutedForeground)
                    }
                }
            }
            CanonDimensionLine(spec.width, Modifier.canonPlaced(across))
            CanonDimensionLine(spec.height, Modifier.canonPlaced(down))
            if (model.exporting) {
                val side = 40.dp.toPx()
                val x = if (rightToLeft) area.left + side / 2 else area.right - side / 2
                Box(Modifier.canonPlaced(CanonDimensionPlacement(Offset(x, area.bottom - side / 2), Size(side, side))), contentAlignment = Alignment.Center) {
                    CanonLoader()
                }
            }
        }
    }
}
