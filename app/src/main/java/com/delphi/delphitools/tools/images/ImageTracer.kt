package com.delphi.delphitools.tools.images

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonChecksPaper
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonColourStrip
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonImageCompare
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMenuCell
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.colour.rgbOf
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiLight
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.roundToInt

// tracer works at 600px
private const val TRACE_DISPLAY_PIXELS = 1600
private const val TRACE_PIXELS = 600
private const val TRACE_FAILED = "This image could not be traced"

private class TraceSource(val display: Bitmap, val grounded: ImageBitmap, val name: String, val width: Int, val height: Int)

private class TraceResult(val svg: String, val vectors: TraceVectors, val filled: ImageBitmap, val outlined: ImageBitmap)

private class TraceInk(val lines: Int, val curves: Int, val wash: Int)

private fun checkerboard(canvas: android.graphics.Canvas, width: Int, height: Int) {
    val square = max(width, height) / 48f
    val (light, dark) = CanonChecksPaper
    canvas.drawColor(light.toArgb())
    val paint = Paint().apply { color = dark.toArgb() }
    var y = 0
    while (y * square < height) {
        var x = y % 2
        while (x * square < width) {
            canvas.drawRect(x * square, y * square, (x + 1) * square, (y + 1) * square, paint)
            x += 2
        }
        y++
    }
}

private fun grounded(image: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    checkerboard(canvas, image.width, image.height)
    canvas.drawBitmap(image, 0f, 0f, null)
    return out
}

// dt-core TraceVectors.render
private fun TraceVectors.render(width: Int, height: Int, ink: TraceInk?): Bitmap? {
    if (width <= 0 || height <= 0 || this.width <= 0 || this.height <= 0) return null
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    checkerboard(canvas, width, height)
    val sx = (width / this.width).toFloat()
    val sy = (height / this.height).toFloat()
    canvas.save()
    canvas.scale(sx, sy)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeJoin = Paint.Join.MITER
        strokeMiter = 4f
    }
    for (layer in layers) {
        if (layer.opacity <= 0) continue
        val path = Path().apply { fillType = Path.FillType.WINDING }
        for ((command, p) in layer.fill) {
            when (command) {
                'M' -> path.moveTo(p[0].toFloat(), p[1].toFloat())
                'L' -> path.lineTo(p[0].toFloat(), p[1].toFloat())
                'Q' -> path.quadTo(p[0].toFloat(), p[1].toFloat(), p[2].toFloat(), p[3].toFloat())
                'Z' -> path.close()
            }
        }
        paint.color = android.graphics.Color.rgb(layer.red, layer.green, layer.blue)
        paint.alpha = (layer.opacity.coerceIn(0.0, 1.0) * 255).roundToInt()
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
        if (layer.strokeWidth > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = layer.strokeWidth.toFloat()
            canvas.drawPath(path, paint)
        }
    }
    canvas.restore()
    ink ?: return out

    canvas.drawColor(ink.wash)
    // ~1.5 device px
    val unit = max(width, height) / 500f
    canvas.save()
    canvas.scale(sx, sy)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = unit / max(sx, sy)
        strokeJoin = Paint.Join.ROUND
    }
    line.color = ink.lines
    val lines = Path()
    layers.forEach { layer -> layer.lines.forEach { lines.moveTo(it[0].toFloat(), it[1].toFloat()); lines.lineTo(it[2].toFloat(), it[3].toFloat()) } }
    canvas.drawPath(lines, line)
    line.color = ink.curves
    val curves = Path()
    layers.forEach { layer ->
        layer.curves.forEach {
            curves.moveTo(it[0].toFloat(), it[1].toFloat())
            curves.quadTo(it[2].toFloat(), it[3].toFloat(), it[4].toFloat(), it[5].toFloat())
        }
    }
    canvas.drawPath(curves, line)
    val node = unit / max(sx, sy) * 2.5f
    val dot = Paint().apply { color = ink.lines }
    for (layer in layers) for (p in layer.nodes) {
        val x = p[0].toFloat()
        val y = p[1].toFloat()
        canvas.drawRect(x - node / 2, y - node / 2, x + node / 2, y + node / 2, dot)
    }
    canvas.restore()
    return out
}

/** dt-ios ImageTracerViewController: an image traced to SVG paths, live as the options change. */
class ImageTracerModel(application: Application) : AndroidViewModel(application) {
    var settings by mutableStateOf(TraceSettings())
        private set

    /** Null once an option no longer matches the chosen preset. */
    var presetIndex by mutableStateOf<Int?>(0)
        private set
    private var source by mutableStateOf<TraceSource?>(null)
    private var result by mutableStateOf<TraceResult?>(null)
    var failure by mutableStateOf<String?>(null)
        private set
    var tracing by mutableStateOf(false)
        private set
    var outlines by mutableStateOf(false)

    val loaded get() = source != null
    val name get() = source?.name ?: "Image"
    val pixels get() = source?.let { it.width to it.height }
    val displayAspect get() = source?.display?.let { (it.width.toFloat() / max(it.height, 1)).coerceIn(0.8f, 1.6f) } ?: (4f / 3f)
    val grounded get() = source?.grounded
    val picture get() = result?.let { if (outlines) it.outlined else it.filled }
    val vectors get() = result?.vectors
    val svg get() = result?.svg

    private var generation = 0
    private var loading: Job? = null
    private var scheduled: Job? = null
    private var running: Job? = null
    private var queued = false

    fun load(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        generation++
        val run = generation
        loading?.cancel()
        loading = viewModelScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                val name = resolver.canonDisplayName(uri)?.substringBeforeLast('.')?.ifEmpty { null } ?: "Image"
                val decoded = runCatching { ImageFiles.decode(ImageDecoder.createSource(resolver, uri), TRACE_DISPLAY_PIXELS) }.getOrNull()
                    ?: return@withContext null
                TraceSource(decoded.image, grounded(decoded.image).asImageBitmap(), name, decoded.width, decoded.height)
            }
            if (run != generation) return@launch
            generation++
            source = prepared
            result = null
            failure = if (prepared == null) TRACE_FAILED else null
            trace()
        }
    }

    fun clear() {
        generation++
        loading?.cancel()
        source = null
        result = null
        failure = null
    }

    fun choosePreset(index: Int) {
        presetIndex = index
        settings = settings.apply(TraceSettings.presets[index].first)
        schedule()
    }

    fun edit(change: (TraceSettings) -> TraceSettings) {
        val next = change(settings)
        if (next == settings) return
        settings = next
        presetIndex = null
        schedule()
    }

    fun chooseScale(scale: Double) {
        if (scale == settings.scale) return
        settings = settings.copy(scale = scale)
        schedule()
    }

    private fun schedule() {
        scheduled?.cancel()
        scheduled = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            trace()
        }
    }

    private fun trace() {
        val source = source ?: return
        if (tracing) {
            queued = true
            return
        }
        tracing = true
        queued = false
        val run = generation
        val options = settings.options
        val ink = TraceInk(DelphiLight.foreground.toArgb(), DelphiLight.primary.toArgb(), android.graphics.Color.argb(199, 255, 255, 255))
        running = viewModelScope.launch {
            val traced = withContext(Dispatchers.Default) {
                runCatching {
                    val small = ImageCompressEngine.preview(source.display, TRACE_PIXELS)
                    val buffer = ByteBuffer.allocate(small.byteCount)
                    small.copyPixelsToBuffer(buffer)
                    val svg = ImageTraceEngine.trace(buffer.array(), small.width, small.height, options)
                    val vectors = TraceVectors.parse(svg) ?: return@runCatching null
                    val w = source.display.width
                    val h = source.display.height
                    val filled = vectors.render(w, h, null) ?: return@runCatching null
                    val outlined = vectors.render(w, h, ink) ?: return@runCatching null
                    TraceResult(svg, vectors, filled.asImageBitmap(), outlined.asImageBitmap())
                }.getOrNull()
            }
            if (run == generation) {
                if (traced == null) failure = TRACE_FAILED else {
                    result = traced
                    failure = null
                }
            }
            tracing = false
            if (queued || run != generation) trace()
        }
    }

    fun export(context: Context) {
        val svg = svg ?: return
        val stem = source?.name?.ifEmpty { null } ?: "image"
        viewModelScope.launch { FileExport.share(context, svg.toByteArray(), "$stem-traced.svg", "image/svg+xml") }
    }
}

private val headline = TextStyle(fontFamily = DelphiFont, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun ImageTracer(model: ImageTracerModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val settings = model.settings
    val vectors = model.vectors
    val loaded = model.loaded

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(model.displayAspect), background = c.muted, maxHeight = 640.dp) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(top = LocalCanonCanvas.current.top)) {
                CanonImageCompare(model.grounded, model.picture, Modifier.fillMaxSize(), beforeTitle = "Original", afterTitle = "SVG")
                if (!loaded) {
                    Column(
                        Modifier.align(Alignment.Center),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Outlined.Gesture, null, tint = c.mutedForeground, modifier = Modifier.size(34.dp))
                        Text("Image", style = headline, color = c.mutedForeground)
                    }
                }
                if (model.tracing) {
                    Box(
                        Modifier
                            .align(if (model.picture != null) Alignment.BottomEnd else Alignment.Center)
                            .padding(12.dp)
                            .background(c.card)
                            .padding(10.dp),
                    ) { CanonLoader() }
                }
            }
            val palette = vectors?.palette.orEmpty()
            if (palette.isNotEmpty()) CanonColourStrip(rgbOf(0x808080), palette = palette.map(::rgbOf))
        }
    }

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            val pixels = model.pixels
            if (pixels == null) {
                CanonDropZone("Drop an image", onFiles = { uris -> uris.firstOrNull()?.let(model::load) })
            } else {
                CanonRow(model.name, detail = "${pixels.first} × ${pixels.second}", flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                }
            }
        }
    }

    val trace = CanonSection.of {
        CanonRows {
            CanonHeading("Trace")
            CanonRow("Preset", flush = true) {
                CanonMenuCell(TraceSettings.presets.map { it.second }, model.presetIndex, model::choosePreset, placeholder = "Custom")
            }
            CanonRow("Colours", flush = true) {
                CanonNumberField(settings.colours, { value -> model.edit { it.copy(colours = value) } }, range = 2..64)
            }
            CanonRow("Line smoothing", flush = true) {
                CanonSlider(
                    settings.lineThreshold.toFloat(), { value -> model.edit { it.copy(lineThreshold = value.toDouble()) } }, Modifier.fillMaxSize(),
                    range = 0f..10f, step = 0.1f, readout = TraceSettings.smoothing(settings.lineThreshold),
                )
            }
            CanonRow("Curve smoothing", flush = true) {
                CanonSlider(
                    settings.curveThreshold.toFloat(), { value -> model.edit { it.copy(curveThreshold = value.toDouble()) } }, Modifier.fillMaxSize(),
                    range = 0f..10f, step = 0.1f, readout = TraceSettings.smoothing(settings.curveThreshold),
                )
            }
            CanonRow("Min path size", flush = true) {
                CanonSlider(
                    settings.pathOmit.toFloat(), { value -> model.edit { it.copy(pathOmit = value.toDouble()) } }, Modifier.fillMaxSize(),
                    range = 0f..200f, step = 1f, readout = "${settings.pathOmit.toInt()}",
                )
            }
            CanonHeading("Stroke Width")
            CanonSegmented(TraceSettings.strokes.map(TraceSettings::strokeLabel), TraceSettings.strokes.indexOf(settings.strokeWidth), columns = 6) { index ->
                model.edit { it.copy(strokeWidth = TraceSettings.strokes[index]) }
            }
            CanonHeading("Scale")
            CanonSegmented(
                TraceSettings.scales.map { "${TraceSettings.number(it)}×" },
                TraceSettings.scales.indexOf(settings.scale).takeIf { it >= 0 } ?: 1,
                columns = 5,
            ) { model.chooseScale(TraceSettings.scales[it]) }
        }
    }

    val advanced = CanonSection.of {
        CanonRows {
            CanonHeading("Advanced")
            CanonRow("Blur radius", flush = true) {
                CanonSlider(
                    settings.blurRadius.toFloat(), { value -> model.edit { it.copy(blurRadius = value.toDouble()) } }, Modifier.fillMaxSize(),
                    range = 0f..20f, step = 1f, readout = "${settings.blurRadius.toInt()}",
                )
            }
            CanonRow("Blur delta", flush = true) {
                CanonSlider(
                    settings.blurDelta.toFloat(), { value -> model.edit { it.copy(blurDelta = value.toDouble()) } }, Modifier.fillMaxSize(),
                    range = 0f..256f, step = 1f, readout = "${settings.blurDelta.toInt()}",
                )
            }
            CanonHeading("Colour sampling")
            CanonSegmented(listOf("Generated", "Random", "Deterministic"), settings.colourSampling) { index ->
                model.edit { it.copy(colourSampling = index) }
            }
            CanonRow("Min colour ratio", flush = true) {
                CanonSlider(
                    settings.minColourRatio.toFloat(), { value -> model.edit { it.copy(minColourRatio = value.toDouble()) } }, Modifier.fillMaxSize(),
                    range = 0f..1f, step = 0.01f, readout = String.format(java.util.Locale.ROOT, "%.2f", settings.minColourRatio),
                )
            }
            CanonRow("Coordinate rounding", flush = true) {
                CanonSlider(
                    settings.roundCoords.toFloat(), { value -> model.edit { it.copy(roundCoords = value.toDouble()) } }, Modifier.fillMaxSize(),
                    range = 0f..5f, step = 1f, readout = "${settings.roundCoords.toInt()}",
                )
            }
        }
    }

    val result = CanonSection.of {
        if (!loaded) return@of
        CanonRows {
            CanonHeading("Traced SVG")
            if (model.tracing) {
                CanonRow("Tracing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground) }
            }
            model.failure?.let { CanonStatusRow(it, CanonTone.Warn) }
            val svg = model.svg
            if (vectors != null && svg != null) {
                CanonRow("Outlines") { CanonSwitch(model.outlines) { model.outlines = it } }
                val numbers = NumberFormat.getIntegerInstance()
                val segments = max(vectors.lineCount + vectors.curveCount, 1)
                CanonTiles(
                    listOf(
                        CanonTile("Colours", numbers.format(vectors.palette.size)),
                        CanonTile("Paths", numbers.format(vectors.paths)),
                        CanonTile("Lines", numbers.format(vectors.lineCount)),
                        CanonTile("Curves", numbers.format(vectors.curveCount), level = vectors.curveCount.toFloat() / segments),
                        CanonTile("Size", "${TraceSettings.number(vectors.width)} × ${TraceSettings.number(vectors.height)}"),
                        CanonTile("File Size", Formatter.formatFileSize(context, svg.length.toLong())),
                    ),
                )
                CanonCodeBlock(SvgMarkup.outline(svg), maxHeight = 240.dp, copyText = svg)
                // ponytail: no Optimise button; dt-ios hands the SVG to svg-optimiser, and the shell has no hand-over yet
                CanonGrid {
                    CanonButton(
                        "Export",
                        Modifier.weight(1f).fillMaxHeight(),
                        kind = CanonButtonKind.Primary,
                        icon = Icons.Outlined.Share,
                        oversized = true,
                    ) { model.export(context) }
                }
            }
        }
    }

    CanonSplit(sidebar = listOf(canvas), main = listOf(input, trace, advanced, result), halves = true, collapsesTo = 240.dp)
}
