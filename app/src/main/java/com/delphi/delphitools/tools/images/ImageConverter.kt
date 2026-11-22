package com.delphi.delphitools.tools.images

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourWell
import com.delphi.delphitools.canon.CanonDimensionLine
import com.delphi.delphitools.canon.CanonDimensionPlacement
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonFigure
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
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonChecks
import com.delphi.delphitools.canon.canonDimensionLines
import com.delphi.delphitools.canon.canonDimensionSheet
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.canonPlaced
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.toArgb
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val PREVIEW_PIXELS = 1600
private const val SLIDER_SETTLE_MS = 250L
private const val TYPING_SETTLE_MS = 400L

/** dt-ios ImageConverterViewModel and the controller's file state: one source, re-encoded on every change. */
class ImageConverterModel(application: Application) : AndroidViewModel(application) {
    private var source: Bitmap? = null
    var sourcePreview by mutableStateOf<ImageBitmap?>(null)
        private set
    var sourceName by mutableStateOf("")
        private set
    var sourceDetail by mutableStateOf("")
        private set
    var sourceWidth by mutableIntStateOf(0)
        private set
    var sourceHeight by mutableIntStateOf(0)
        private set
    var hasAlpha by mutableStateOf(false)
        private set

    var format by mutableStateOf(ImageOutputFormat.Png)
        private set
    var quality by mutableFloatStateOf(85f)
        private set
    var flatten by mutableStateOf(Rgb.white)
        private set
    var resize by mutableStateOf(ImageResizeMode.Original)
        private set
    var percent by mutableFloatStateOf(100f)
        private set
    var customWidth by mutableIntStateOf(0)
        private set
    var customHeight by mutableIntStateOf(0)
        private set
    var lock by mutableStateOf(true)
        private set

    var converted by mutableStateOf<ByteArray?>(null)
        private set
    var resultPreview by mutableStateOf<ImageBitmap?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var isConverting by mutableStateOf(false)
        private set
    var convertedBytes by mutableLongStateOf(0)
        private set

    private var loading: Job? = null
    private var task: Job? = null
    private val encoder = Dispatchers.Default.limitedParallelism(1)

    val loaded get() = sourcePreview != null
    val output get() = ImageConvert.outputSize(resize, sourceWidth, sourceHeight, percent.toDouble(), customWidth, customHeight)
    val percentLimit get() = ImageConvert.percentLimit(sourceWidth, sourceHeight)
    val exportName get() = "${sourceName.substringBeforeLast('.').ifEmpty { "image" }}.${format.fileExtension}"

    fun load(uri: Uri) {
        loading?.cancel()
        val app = getApplication<Application>()
        val resolver = app.contentResolver
        loading = viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                ImageFiles.decode(ImageDecoder.createSource(resolver, uri), ImageConvert.LONGEST_SIDE)?.let { it to ImageCompressEngine.preview(it.image, PREVIEW_PIXELS) }
            } ?: return@launch
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else 0L }
                }.getOrNull() ?: 0L
            }
            val (file, preview) = decoded
            source = file.image
            hasAlpha = file.image.hasAlpha()
            sourceWidth = file.image.width
            sourceHeight = file.image.height
            customWidth = file.image.width
            customHeight = file.image.height
            sourceName = resolver.canonDisplayName(uri) ?: ""
            sourceDetail = "${file.width} × ${file.height} · ${Formatter.formatShortFileSize(app, bytes)}"
            resultPreview = null
            sourcePreview = preview.asImageBitmap()
            convert()
        }
    }

    fun clear() {
        loading?.cancel()
        task?.cancel()
        source = null
        sourcePreview = null
        sourceName = ""
        sourceWidth = 0
        sourceHeight = 0
        resultPreview = null
        converted = null
        convertedBytes = 0
        failed = false
        isConverting = false
    }

    fun selectFormat(format: ImageOutputFormat) {
        this.format = format
        convert()
    }

    fun changeQuality(value: Float) {
        quality = value
        convert(SLIDER_SETTLE_MS)
    }

    fun changeFlatten(colour: Rgb) {
        flatten = colour
        convert()
    }

    fun selectResize(mode: ImageResizeMode) {
        resize = mode
        convert()
    }

    fun preset(index: Int) {
        percent = ImageConvert.percentagePresets[index].toFloat()
        convert()
    }

    fun changePercent(value: Float) {
        percent = value
        convert(SLIDER_SETTLE_MS)
    }

    fun changeWidth(value: Int, typing: Boolean) {
        customWidth = value
        if (lock && sourceWidth > 0) customHeight = ImageConvert.locked(value, sourceWidth, sourceHeight)
        convert(if (typing) TYPING_SETTLE_MS else 0)
    }

    fun changeHeight(value: Int, typing: Boolean) {
        customHeight = value
        if (lock && sourceHeight > 0) customWidth = ImageConvert.locked(value, sourceHeight, sourceWidth)
        convert(if (typing) TYPING_SETTLE_MS else 0)
    }

    fun changeLock(on: Boolean) {
        lock = on
        // relocking follows the width
        if (on && sourceWidth > 0) customHeight = ImageConvert.locked(customWidth, sourceWidth, sourceHeight)
        convert()
    }

    fun copy(context: Context) {
        val data = converted ?: return
        viewModelScope.launch { ImageFiles.copyPng(context, data, exportName) }
    }

    fun export(context: Context) {
        val data = converted ?: return
        viewModelScope.launch { FileExport.share(context, data, exportName, format.mimeType) }
    }

    private fun convert(delayMs: Long = 0) {
        val image = source ?: return
        val limit = percentLimit.toFloat()
        if (resize == ImageResizeMode.Percentage && percent > limit) percent = limit
        if (resize == ImageResizeMode.Custom) {
            val (width, height) = ImageConvert.capped(customWidth, customHeight)
            customWidth = width
            customHeight = height
        }
        val format = format
        val quality = quality.roundToInt()
        val (width, height) = output
        val flatten = if (format.supportsTransparency) null else flatten.toArgb()
        val scratch = getApplication<Application>().cacheDir
        task?.cancel()
        task = viewModelScope.launch {
            delay(delayMs)
            isConverting = true
            val data = withContext(encoder) { ImageConvertEngine.encode(image, format, width, height, quality, flatten, scratch) }
            val preview = withContext(Dispatchers.Default) { data?.let { ImageCompressEngine.thumbnail(it, PREVIEW_PIXELS) } }
            isConverting = false
            converted = data
            convertedBytes = data?.size?.toLong() ?: 0
            failed = data == null
            resultPreview = preview?.asImageBitmap()
        }
    }
}

@Composable
fun ImageConverter(model: ImageConverterModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val formats = ImageOutputFormat.available
    val (width, height) = model.output
    val data = model.converted

    val canvas = CanonSection.canvas(
        CanonCanvasSize.Aspect((maxOf(width, 1).toFloat() / maxOf(height, 1)).coerceIn(0.8f, 1.5f)),
        background = c.muted,
        showsPattern = true,
    ) {
        ConversionPicture(
            source = Size(model.sourceWidth.toFloat(), model.sourceHeight.toFloat()),
            output = Size(width.toFloat(), height.toFloat()),
            image = model.resultPreview ?: model.sourcePreview,
            isResult = model.resultPreview != null,
            bytes = data?.let { Formatter.formatShortFileSize(context, it.size.toLong()) },
            isWorking = model.isConverting,
            failed = model.failed,
            lamps = listOf(
                if (model.format.supportsTransparency && model.hasAlpha) WordLamp.On else WordLamp.Off,
                if (model.format.supportsQuality) WordLamp.On else WordLamp.Off,
            ),
        )
    }

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            if (!model.loaded) {
                CanonDropZone("Drop an image", paste = false, onFiles = { uris -> uris.firstOrNull()?.let(model::load) })
            } else {
                CanonRow(model.sourceName, detail = model.sourceDetail, flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                }
            }
        }
    }

    val output = CanonSection.of {
        CanonRows {
            CanonHeading("Output Format")
            CanonSegmented(formats.map { it.label }, formats.indexOf(model.format), columns = formats.size) { model.selectFormat(formats[it]) }
            if (model.format.supportsQuality) {
                CanonRow("Quality", flush = true) {
                    CanonSlider(model.quality, model::changeQuality, Modifier.fillMaxSize(), range = 1f..100f, step = 1f, readout = model.quality.roundToInt().toString())
                }
            }
            if (!model.format.supportsTransparency && model.hasAlpha) {
                CanonColourWell(model.flatten, model::changeFlatten, label = "Alpha Background")
            }
        }
    }

    val main = buildList {
        add(input)
        add(output)
        if (!model.loaded) return@buildList
        add(
            CanonSection.of {
                CanonRows {
                    CanonHeading("Resize")
                    val modes = ImageResizeMode.entries
                    CanonSegmented(modes.map { it.label }, modes.indexOf(model.resize)) { model.selectResize(modes[it]) }
                    val limit = model.percentLimit
                    if (model.resize == ImageResizeMode.Percentage) {
                        val presets = ImageConvert.percentagePresets
                        CanonSegmented(
                            presets.map { "$it%" },
                            presets.indexOfFirst { it.toFloat() == model.percent },
                            disabledIndices = presets.indices.filter { presets[it] > limit }.toSet(),
                            onSelect = model::preset,
                        )
                        CanonRow("Scale", flush = true) {
                            CanonSlider(
                                model.percent,
                                model::changePercent,
                                Modifier.fillMaxSize(),
                                range = 1f..limit.toFloat(),
                                step = 1f,
                                readout = "${model.percent.roundToInt()}%",
                            )
                        }
                    }
                    if (model.resize == ImageResizeMode.Custom) {
                        var widthTyped by remember { mutableStateOf(false) }
                        var heightTyped by remember { mutableStateOf(false) }
                        CanonRow("Width", flush = true) {
                            CanonNumberField(
                                model.customWidth,
                                { model.changeWidth(it, widthTyped) },
                                Modifier.onFocusChanged { widthTyped = it.hasFocus },
                                range = 1..ImageConvert.LONGEST_SIDE,
                                unit = "px",
                            )
                        }
                        CanonRow("Height", flush = true) {
                            CanonNumberField(
                                model.customHeight,
                                { model.changeHeight(it, heightTyped) },
                                Modifier.onFocusChanged { heightTyped = it.hasFocus },
                                range = 1..ImageConvert.LONGEST_SIDE,
                                unit = "px",
                            )
                        }
                        CanonRow("Aspect Ratio", detail = PlaceholderSpec.aspect(model.sourceWidth, model.sourceHeight)) {
                            CanonSwitch(model.lock, onCheckedChange = model::changeLock)
                        }
                    }
                }
            },
        )
        add(
            CanonSection.of {
                CanonRows {
                    CanonHeading("Result")
                    CanonRow(
                        model.exportName,
                        detail = data?.let { "$width × $height · ${Formatter.formatShortFileSize(context, it.size.toLong())}" } ?: "—",
                    )
                    if (model.failed && !model.isConverting) CanonStatusRow("Conversion failed for this format.", CanonTone.Warn)
                    val ready = data != null && !model.isConverting
                    CanonGrid {
                        CanonButton(
                            "Copy",
                            Modifier.weight(1f).fillMaxHeight(),
                            kind = CanonButtonKind.Secondary,
                            enabled = ready,
                            icon = Icons.Rounded.ContentCopy,
                            flashesCopied = true,
                        ) { model.copy(context) }
                        CanonButton(
                            "Export",
                            Modifier.weight(1f).fillMaxHeight(),
                            kind = CanonButtonKind.Primary,
                            enabled = ready,
                            icon = Icons.Outlined.Share,
                            oversized = true,
                        ) { model.export(context) }
                    }
                }
            },
        )
    }

    CanonSplit(sidebar = listOf(canvas), main = main, halves = true, collapsesTo = 200.dp)
}

private val footer = 40.dp
private val lampNames = listOf("Alpha", "Lossy")

/**
 * dt-ios ConversionPicture: the output decoded back from its own bytes, to scale against a dashed outline of the
 * source when resized, over the checks; dimension lines for its pixels, lamps for what the format keeps, its size.
 */
@Composable
private fun ConversionPicture(
    source: Size,
    output: Size,
    image: ImageBitmap?,
    isResult: Boolean,
    bytes: String?,
    isWorking: Boolean,
    failed: Boolean,
    lamps: List<WordLamp>,
) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val top = LocalCanonCanvas.current.top
    val empty = output.width <= 0f
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .semantics {
                contentDescription = if (empty) "Result" else "${output.width.toInt()} × ${output.height.toInt()}"
                role = Role.Image
            },
    ) {
        with(density) {
            val padding = CanonMetrics.panelPadding.toPx()
            val area = Rect(padding, top.toPx() + padding * 0.5f, constraints.maxWidth - padding, constraints.maxHeight - padding - footer.toPx())
            val shownOutput = if (empty) Size(3f, 2f) else output
            val shownSource = if (source.width > 0f) source else shownOutput
            val box = Size(maxOf(shownSource.width, shownOutput.width), maxOf(shownSource.height, shownOutput.height))
            val (outputFrame, sourceFrame) = ImageConvert.frames(shownSource, shownOutput, canonDimensionSheet(box, area, rightToLeft))
            val spring = spring<Rect>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
            val sheet by animateRectAsState(outputFrame, spring, label = "sheet")
            val outline by animateRectAsState(sourceFrame, spring, label = "outline")
            val resized = source.width > 0f && source != output
            val edge = sheet.inflate(CanonMetrics.hairline.toPx())

            Box(Modifier.canonPlaced(CanonDimensionPlacement(edge.center, edge.size)).background(c.border))
            Box(Modifier.canonPlaced(CanonDimensionPlacement(sheet.center, sheet.size)).background(c.card).clipToBounds()) {
                if (image == null) {
                    Text("Result", Modifier.align(Alignment.Center), style = TextStyle(fontSize = 12.sp), color = c.mutedForeground)
                } else {
                    Box(Modifier.fillMaxSize().canonChecks())
                    Crossfade(image, label = "image") { shown ->
                        Image(
                            shown,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().alpha(if (isResult) 1f else 0.35f),
                            contentScale = ContentScale.FillBounds,
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
            }
            if (resized) {
                val ink = c.foreground.copy(alpha = 0.55f)
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        ink,
                        outline.topLeft,
                        outline.size,
                        style = Stroke(1.5f * density.density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * density.density, 4 * density.density))),
                    )
                }
                Text(
                    " ${source.width.toInt()} × ${source.height.toInt()} ",
                    style = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp),
                    color = c.mutedForeground,
                    maxLines = 1,
                    modifier = Modifier
                        .placedAtCorner(outline, rightToLeft, 4.dp.toPx())
                        .background(c.muted.copy(alpha = 0.85f)),
                )
            }
            val (across, down) = canonDimensionLines(sheet, rightToLeft)
            CanonDimensionLine(output.width.toInt(), Modifier.canonPlaced(across), blank = empty)
            CanonDimensionLine(output.height.toInt(), Modifier.canonPlaced(down), blank = empty)

            val rowWidth = (constraints.maxWidth - padding * 2).toDp()
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = CanonMetrics.panelPadding, end = CanonMetrics.panelPadding, bottom = CanonMetrics.panelPadding - 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WordLamps(lampNames, lamps, columns = 3, modifier = Modifier.width(minOf(maxOf(0.dp, rowWidth - 150.dp), 330.dp)).padding(bottom = 2.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    if (isWorking) CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground)
                    val (number, unit) = bytes?.let(ImageConvert::figure) ?: ("—" to "")
                    CanonFigure(number, unit = unit, size = 28.sp, colour = if (failed) c.destructive else c.primary, halo = c.muted)
                }
            }
        }
    }
}

/** Places the element inside [rect]'s bottom trailing corner, [inset] pixels in. */
private fun Modifier.placedAtCorner(rect: Rect, rightToLeft: Boolean, inset: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        val x = if (rightToLeft) rect.left + inset else rect.right - placeable.width - inset
        placeable.place(x.roundToInt(), (rect.bottom - placeable.height - inset).roundToInt())
    }
}
