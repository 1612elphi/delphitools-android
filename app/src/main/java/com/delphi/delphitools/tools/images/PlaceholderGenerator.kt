package com.delphi.delphitools.tools.images

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonColourWell
import com.delphi.delphitools.canon.CanonDimensionLine
import com.delphi.delphitools.canon.CanonDimensionPlacement
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonDimensionLines
import com.delphi.delphitools.canon.canonDimensionSheet
import com.delphi.delphitools.canon.canonPlaced
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.parseHex
import com.delphi.delphitools.colour.toArgb
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private class Preset(val label: String, val width: Int, val height: Int)

private val presets = listOf(
    Preset("HD", 1920, 1080), Preset("Square", 1000, 1000), Preset("Banner", 1200, 400),
    Preset("Thumb", 300, 200), Preset("Social", 1200, 630), Preset("Avatar", 400, 400),
)
private val formats = listOf("PNG", "SVG")
private const val TYPING_MS = 400L
private const val MEASURE_MS = 300L
private val figureSize = 96.sp

private class Encoded(val spec: PlaceholderSpec, val data: ByteArray)

/** dt-ios PlaceholderGeneratorViewController's state; the spec follows the controls, typing settles first. */
class PlaceholderGeneratorModel(application: Application) : AndroidViewModel(application) {
    var width by mutableStateOf(800)
        private set
    var height by mutableStateOf(600)
        private set
    var background by mutableStateOf(parseHex("#e2e2e2")!!)
        private set
    var ink by mutableStateOf(parseHex("#666666")!!)
        private set
    var text by mutableStateOf("")
        private set
    var isSvg by mutableStateOf(false)
        private set
    var spec by mutableStateOf(current())
        private set
    private var encoded by mutableStateOf<Encoded?>(null)
    val pngBytes get() = encoded?.takeIf { it.spec == spec }?.data?.size

    private var typing: Job? = null
    private var measuring: Job? = null

    // one 4096 px bitmap at a time
    private val encoder = Dispatchers.Default.limitedParallelism(1)

    private fun current() = PlaceholderSpec(width, height, background.hex.lowercase(), ink.hex.lowercase(), text)

    private fun refresh(settles: Boolean = false) {
        typing?.cancel()
        if (settles) {
            typing = viewModelScope.launch {
                delay(TYPING_MS)
                refresh()
            }
            return
        }
        spec = current()
        if (isSvg || encoded?.spec == spec) return
        val spec = spec
        measuring?.cancel()
        // big pngs encode slowly
        measuring = viewModelScope.launch {
            delay(MEASURE_MS)
            png(spec)
        }
    }

    fun changeWidth(value: Int, typed: Boolean) {
        width = value
        refresh(typed)
    }

    fun changeHeight(value: Int, typed: Boolean) {
        height = value
        refresh(typed)
    }

    fun preset(index: Int) {
        width = presets[index].width
        height = presets[index].height
        refresh()
    }

    fun changeBackground(colour: Rgb) {
        background = colour
        refresh()
    }

    fun changeInk(colour: Rgb) {
        ink = colour
        refresh()
    }

    fun changeText(value: String) {
        text = value
        refresh(settles = true)
    }

    fun selectFormat(index: Int) {
        isSvg = index == 1
        refresh()
    }

    private suspend fun png(spec: PlaceholderSpec): ByteArray? {
        encoded?.takeIf { it.spec == spec }?.let { return it.data }
        val data = withContext(encoder) { render(spec) } ?: return null
        encoded = Encoded(spec, data)
        return data
    }

    fun copyDataUri(context: Context) {
        val spec = spec
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        viewModelScope.launch {
            val uri = if (isSvg) spec.svgDataUri() else png(spec)?.let(PlaceholderSpec::pngDataUri) ?: return@launch
            clipboard?.setPrimaryClip(ClipData.newPlainText("", uri))
        }
    }

    fun export(context: Context) {
        val spec = spec
        viewModelScope.launch {
            if (isSvg) {
                FileExport.share(context, spec.svg.toByteArray(), "${spec.baseName}.svg", "image/svg+xml")
            } else {
                val data = png(spec) ?: return@launch
                FileExport.share(context, data, "${spec.baseName}.png", "image/png")
            }
        }
    }

    private fun render(spec: PlaceholderSpec): ByteArray? = runCatching {
        val bitmap = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(parseHex(spec.background)?.toArgb() ?: 0)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parseHex(spec.foreground)?.toArgb() ?: 0
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = spec.fontSize.toFloat()
            textAlign = Paint.Align.CENTER
        }
        val metrics = paint.fontMetrics
        canvas.drawText(spec.label, spec.width / 2f, spec.height / 2f - (metrics.ascent + metrics.descent) / 2, paint)
        ByteArrayOutputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
            bitmap.recycle()
            out.toByteArray()
        }
    }.getOrNull()
}

@Composable
fun PlaceholderGenerator(model: PlaceholderGeneratorModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val spec = model.spec
    val formatIndex = if (model.isSvg) 1 else 0

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(1.25f), background = c.muted, showsPattern = true) {
        PlaceholderPicture(spec, model.background, model.ink)
    }

    val controls = CanonSection.of {
        CanonRows {
            var widthTyped by remember { mutableStateOf(false) }
            var heightTyped by remember { mutableStateOf(false) }
            CanonHeading("Size")
            CanonRow("Width", flush = true) {
                CanonNumberField(
                    model.width,
                    { model.changeWidth(it, widthTyped) },
                    Modifier.onFocusChanged { widthTyped = it.hasFocus },
                    range = PlaceholderSpec.sizes,
                    step = 10,
                    unit = "px",
                )
            }
            CanonRow("Height", flush = true) {
                CanonNumberField(
                    model.height,
                    { model.changeHeight(it, heightTyped) },
                    Modifier.onFocusChanged { heightTyped = it.hasFocus },
                    range = PlaceholderSpec.sizes,
                    step = 10,
                    unit = "px",
                )
            }
            CanonHeading("Presets")
            CanonSegmented(
                presets.map { it.label },
                presets.indexOfFirst { it.width == spec.width && it.height == spec.height },
                columns = 3,
                onSelect = model::preset,
            )
            CanonHeading("Colours")
            CanonColourWell(model.background, model::changeBackground, label = "Background")
            CanonColourWell(model.ink, model::changeInk, label = "Text")
            CanonHeading("Label")
            CanonTextCell(
                model.text,
                model::changeText,
                Modifier.fillMaxWidth(),
                placeholder = spec.copy(text = "").label,
            )
            CanonHeading("Format")
            CanonSegmented(formats, formatIndex, onSelect = model::selectFormat)
            CanonGrid {
                CanonButton(
                    "Copy Data URI",
                    Modifier.weight(1f),
                    icon = Icons.Rounded.ContentCopy,
                    flashesCopied = true,
                ) { model.copyDataUri(context) }
                CanonButton(
                    "Export",
                    Modifier.weight(1f),
                    kind = CanonButtonKind.Primary,
                    icon = Icons.Outlined.Share,
                    oversized = true,
                ) { model.export(context) }
            }
        }
    }

    val output = CanonSection.of {
        CanonRows {
            val svg = spec.svg
            val bytes = if (model.isSvg) svg.toByteArray().size else model.pngBytes
            CanonHeading("Image")
            CanonTiles(
                listOf(
                    CanonTile("Aspect", PlaceholderSpec.aspect(spec.width, spec.height)),
                    CanonTile("Pixels", String.format(Locale.getDefault(), "%.2f MP", spec.width.toDouble() * spec.height / 1_000_000)),
                    CanonTile("Text", "${spec.fontSize.roundToInt()} px"),
                    CanonTile(formats[formatIndex], bytes?.let { Formatter.formatShortFileSize(context, it.toLong()) } ?: "…"),
                )
            )
            if (model.isSvg) CanonCodeBlock(svg, language = "SVG")
        }
    }

    CanonSplit(sidebar = listOf(canvas), main = listOf(controls, output), halves = true, collapsesTo = 200.dp)
}

/** The placeholder as it will be written, fitted to the pane, with dimension lines for its width and height. */
@Composable
private fun PlaceholderPicture(spec: PlaceholderSpec, background: Rgb, ink: Rgb) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val top = LocalCanonCanvas.current.top
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .semantics {
                contentDescription = spec.label
                role = Role.Image
            }
    ) {
        with(density) {
            val padding = CanonMetrics.panelPadding.toPx()
            val area = Rect(
                padding,
                top.toPx() + padding * 0.5f,
                constraints.maxWidth - padding,
                constraints.maxHeight - padding,
            )
            val target = canonDimensionSheet(Size(spec.width.toFloat(), spec.height.toFloat()), area, rightToLeft)
            val sheet by animateRectAsState(target, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "sheet")
            val fill by animateColorAsState(background.toCanonColor(), spring(dampingRatio = 0.82f), label = "fill")
            val (across, down) = canonDimensionLines(sheet, rightToLeft)
            val hairline = CanonMetrics.hairline.toPx()
            val edge = sheet.inflate(hairline)
            Box(Modifier.canonPlaced(CanonDimensionPlacement(edge.center, edge.size)).background(c.border))
            Box(Modifier.canonPlaced(CanonDimensionPlacement(sheet.center, sheet.size)).background(fill).clipToBounds()) {
                // export font size, scaled
                val points = spec.fontSize.toFloat() * sheet.width / max(1, spec.width)
                val scale = max(0.001f, points / figureSize.toPx())
                CanonFigure(
                    spec.label,
                    Modifier
                        .align(Alignment.Center)
                        .wrapContentSize(unbounded = true)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    size = figureSize,
                    colour = ink.toCanonColor(),
                )
            }
            CanonDimensionLine(spec.width, Modifier.canonPlaced(across))
            CanonDimensionLine(spec.height, Modifier.canonPlaced(down))
        }
    }
}
