package com.delphi.delphitools.tools.audiovideo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourWell
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.toArgb
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import java.io.File
import java.util.Locale
import kotlin.math.max

// finest scan, rebucketed later
private const val SCAN_BUCKETS = 4000

private class WaveformSize(val label: String, val width: Int, val height: Int)

private val sizes = listOf(
    WaveformSize("SoundCloud", 1500, 500), WaveformSize("Open Graph", 1200, 630), WaveformSize("Full HD", 1920, 1080),
    WaveformSize("Square", 1200, 1200), WaveformSize("Custom", 0, 0),
)
private val customRange = 100..4000
private val details = listOf(1, 2, 4, 8)
private val formats = listOf("PNG", "SVG")

/** dt-ios WaveformGeneratorViewController: an audio file's waveform as a PNG or SVG image. */
class WaveformGeneratorModel(application: Application) : AndroidViewModel(application) {
    val source = AVEditAudioSource(application, viewModelScope)
    val job = AVEditJob(viewModelScope, avEditRoot(application, "waveform-genny"))
    var size by mutableIntStateOf(0)
        private set
    var width by mutableIntStateOf(1500)
        private set
    var height by mutableIntStateOf(500)
        private set
    var detail by mutableIntStateOf(1)
        private set
    var wave by mutableStateOf(Rgb(46 / 255.0, 125 / 255.0, 50 / 255.0))
        private set
    var background by mutableStateOf(Rgb(250 / 255.0, 245 / 255.0, 230 / 255.0))
        private set
    var format by mutableIntStateOf(0)
        private set

    val isCustom get() = size == sizes.lastIndex
    val pixels get() = if (isCustom) width to height else sizes[size].width to sizes[size].height
    val columns get() = max(10, pixels.first / details[detail])

    fun load(uri: Uri) {
        clear()
        source.load(uri, SCAN_BUCKETS)
    }

    fun clear() {
        job.reset()
        source.clear()
    }

    private fun edited(change: () -> Unit) {
        change()
        job.reset()
    }

    fun selectSize(index: Int) = edited { size = index }
    fun changeWidth(value: Int) = edited { width = value }
    fun changeHeight(value: Int) = edited { height = value }
    fun selectDetail(index: Int) = edited { detail = index }
    fun changeWave(colour: Rgb) = edited { wave = colour }
    fun changeBackground(colour: Rgb) = edited { background = colour }
    fun selectFormat(index: Int) = edited { format = index }

    fun render() {
        val scan = source.scan ?: return
        val (width, height) = pixels
        val peaks = AVEditAudioEngine.resample(scan.peaks, columns)
        val ink = wave
        val paper = background
        val svg = format == 1
        job.start("${source.baseName}-waveform.${if (svg) "svg" else "png"}") { output, progress ->
            if (svg) output.writeText(AVEditAudioEngine.svg(peaks, width, height, ink.hex, paper.hex)) else png(peaks, width, height, ink, paper, output)
            progress(1.0)
        }
    }

    private fun png(peaks: AVEditPeaks, width: Int, height: Int, wave: Rgb, background: Rgb, output: File) {
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = android.graphics.Canvas(image)
            canvas.drawColor(background.toArgb())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = wave.toArgb() }
            for (bar in AVEditAudioEngine.bars(peaks, width.toDouble(), height.toDouble())) {
                canvas.drawRect(bar.x.toFloat(), bar.y.toFloat(), (bar.x + bar.width).toFloat(), (bar.y + bar.height).toFloat(), paint)
            }
            output.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            image.recycle()
        }
    }

    override fun onCleared() = clear()
}

@Composable
fun WaveformGenerator(model: WaveformGeneratorModel = viewModel()) {
    val source = model.source
    val job = model.job
    AVEditPageEffects(null, model::clear)
    val c = LocalDelphiColours.current
    val scan = source.scan
    val (width, height) = model.pixels
    val columns = model.columns
    val peaks = remember(scan, columns) { scan?.let { AVEditAudioEngine.resample(it.peaks, columns) } }

    val controls = CanonSection.of {
        CanonRows {
            if (source.uri == null) {
                AVEditAudioDrop(Icons.Outlined.GraphicEq, model::load)
            } else {
                CanonHeading("Source")
                AVEditFileRow(source.name, scan?.let(AVEditAudioEngine::audioDetail) ?: "", if (source.failed) "Failed to decode audio." else null, model::clear)
                CanonHeading("Size")
                CanonSegmented(sizes.map { it.label }, model.size, columns = 3, onSelect = model::selectSize)
                if (model.isCustom) {
                    CanonRow("Width", flush = true) {
                        CanonNumberField(model.width, model::changeWidth, range = customRange, step = 10, unit = "px")
                    }
                    CanonRow("Height", flush = true) {
                        CanonNumberField(model.height, model::changeHeight, range = customRange, step = 10, unit = "px")
                    }
                }
                CanonHeading("Detail")
                CanonSegmented(details.map { "$it px" }, model.detail, onSelect = model::selectDetail)
                CanonHeading("Colours")
                CanonColourWell(model.wave, model::changeWave, label = "Wave")
                CanonColourWell(model.background, model::changeBackground, label = "Background")
                CanonHeading("Format")
                CanonSegmented(formats, model.format, onSelect = model::selectFormat)
                AVEditJobPanel(job, "Render", Icons.Outlined.GraphicEq, "Couldn't write image.", enabled = scan != null, onStart = model::render)
            }
        }
    }
    val readings = CanonSection.of {
        CanonRows {
            if (scan == null) return@CanonRows
            CanonHeading("Audio")
            CanonTiles(
                listOf(
                    CanonTile("Duration", String.format(Locale.ROOT, "%.2f s", scan.duration)),
                    CanonTile("Sample rate", "${scan.sampleRate} Hz"),
                    CanonTile("Channels", "${scan.channels}"),
                    CanonTile("Peak", String.format(Locale.ROOT, "%.1f dBFS", scan.peakDb), AVEditAudioEngine.levelShare(scan.peakDb)),
                )
            )
            CanonHeading("Image")
            CanonTiles(
                listOf(
                    CanonTile("Width", "$width px"),
                    CanonTile("Height", "$height px"),
                    CanonTile("Columns", "$columns"),
                    CanonTile("Pixels", String.format(Locale.ROOT, "%.2f MP", width.toDouble() * height / 1_000_000)),
                )
            )
        }
    }
    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(width.toFloat() / max(1, height)), background = c.muted) {
        WaveformPicture(peaks, width, height, model.wave, model.background)
    }

    CanonSplit(
        sidebar = listOf(controls),
        main = if (scan != null && !source.failed) listOf(readings) else emptyList(),
        banner = canvas,
    )
}

/** dt-ios WaveformGeneratorPicture: the export, letterboxed, with its bars drawn in image pixels. */
@Composable
private fun WaveformPicture(peaks: AVEditPeaks?, width: Int, height: Int, wave: Rgb, background: Rgb) {
    val c = LocalDelphiColours.current
    val top = LocalCanonCanvas.current.top
    val bars = remember(peaks, width, height) {
        Path().apply {
            if (peaks != null) {
                for (bar in AVEditAudioEngine.bars(peaks, width.toDouble(), height.toDouble())) {
                    addRect(Rect(bar.x.toFloat(), bar.y.toFloat(), (bar.x + bar.width).toFloat(), (bar.y + bar.height).toFloat()))
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val inset = top.toPx()
        val (x, y, w, h) = AVEditAudioEngine.fit(width.toDouble(), height.toDouble(), 0.0, inset.toDouble(), size.width.toDouble(), (size.height - inset).toDouble())
            .map(Double::toFloat)
        drawRect(background.toCanonColor(), Offset(x, y), Size(w, h))
        val hairline = CanonMetrics.hairline.toPx()
        drawRect(c.border, Offset(x + hairline / 2, y + hairline / 2), Size(w - hairline, h - hairline), style = Stroke(hairline))
        if (peaks == null || peaks.min.isEmpty()) return@Canvas
        translate(x, y) {
            withTransform({ scale(w / width, h / height, Offset.Zero) }) { drawPath(bars, wave.toCanonColor()) }
        }
    }
}
