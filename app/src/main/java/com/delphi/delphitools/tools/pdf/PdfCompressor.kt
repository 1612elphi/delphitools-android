package com.delphi.delphitools.tools.pdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCompressionScale
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonImageCompare
import com.delphi.delphitools.canon.CanonPageStrip
import com.delphi.delphitools.canon.CanonPageStripHeight
import com.delphi.delphitools.canon.CanonPageStripPage
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
import com.delphi.delphitools.tools.images.ImageCompressEngine
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

private val resolutions = listOf(72.0, 150.0, 300.0)

// ponytail: PDFKit's screen downsampling target and JPEG quality are undocumented; 1754 px (A4 at 150 DPI) and 75 stand in
private const val SCREEN_EDGE = 1754
private const val RECOMPRESS_QUALITY = 75

class PdfCompressorModel(application: Application) : AndroidViewModel(application) {
    val pdf = PdfSourceLoader(viewModelScope, application)
    val job = PdfJob(viewModelScope, application)
    var page by mutableIntStateOf(0)
    var mode by mutableIntStateOf(0)
        private set
    var downsample by mutableStateOf(true)
        private set
    var resolution by mutableIntStateOf(1)
        private set
    var quality by mutableFloatStateOf(65f)
        private set

    private val engineMode
        get() = when (mode) {
            1 -> PdfCompressEngine.Mode.Keep
            2 -> PdfCompressEngine.Mode.Rasterise(resolutions[resolution], quality / 100.0)
            else -> PdfCompressEngine.Mode.Recompress(downsample)
        }

    fun mode(index: Int) {
        mode = index
        job.reset()
    }

    fun downsample(on: Boolean) {
        downsample = on
        job.reset()
    }

    fun resolution(index: Int) {
        resolution = index
        job.reset()
    }

    fun quality(value: Float) {
        quality = value
        job.reset()
    }

    fun load(uri: Uri) {
        clear()
        pdf.load(uri)
    }

    fun clear() {
        job.reset()
        page = 0
        pdf.clear()
    }

    fun compress() {
        val source = pdf.source ?: return
        val mode = engineMode
        job.start("${source.stem}-small.pdf") { output, progress ->
            val context = coroutineContext
            val step = { index: Int ->
                context.ensureActive()
                progress(index.toFloat() / source.pageCount)
            }
            output.outputStream().buffered().use { out ->
                when (mode) {
                    is PdfCompressEngine.Mode.Rasterise -> rasterise(source.file, mode, out, step)
                    PdfCompressEngine.Mode.Keep -> PdfCompressEngine.rewrite(PdfFile(source.file.readBytes()), out, step = step)
                    is PdfCompressEngine.Mode.Recompress -> PdfCompressEngine.rewrite(
                        PdfFile(source.file.readBytes()),
                        out,
                        encode = { encode(it, mode.downsample) },
                        step = step,
                    )
                }
            }
            progress(1f)
        }
    }

    override fun onCleared() = clear()
}

private fun rasterise(file: File, mode: PdfCompressEngine.Mode.Rasterise, out: java.io.OutputStream, step: (Int) -> Unit) {
    val quality = (mode.quality * 100).roundToInt()
    val written = PdfDocuments.open(file) { renderer ->
        val document = PdfImageDocument(out)
        for (index in 0 until renderer.pageCount) {
            step(index)
            val size = renderer.openPage(index).use { PdfSize(it.width.toDouble(), it.height.toDouble()) }
            val bitmap = PdfDocuments.render(renderer, index, mode.dpi / 72) ?: return@open false
            bitmap.setHasAlpha(false)
            val picture = PdfDocuments.picture(bitmap, quality) ?: return@open false
            bitmap.recycle()
            document.add(size, PdfRect(0.0, 0.0, size.width, size.height), picture)
        }
        document.finish()
        true
    }
    if (written != true) error("rasterise")
}

private fun encode(samples: PdfCompressEngine.ImageSamples, downsample: Boolean): PdfPicture? {
    var decoded: Bitmap? = null
    var bitmap: Bitmap? = null
    try {
        decoded = samples.jpeg?.let { jpeg ->
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            if (downsample) {
                val long = maxOf(samples.width, samples.height)
                while (long / (options.inSampleSize * 2) >= SCREEN_EDGE) options.inSampleSize *= 2
            }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
        } ?: samples.rgb?.let { rgb ->
            val out = Bitmap.createBitmap(samples.width, samples.height, Bitmap.Config.ARGB_8888)
            val row = IntArray(samples.width)
            for (y in 0 until samples.height) {
                val start = y * samples.width * 3
                for (x in row.indices) {
                    val i = start + x * 3
                    row[x] = (0xff shl 24) or ((rgb[i].toInt() and 0xff) shl 16) or ((rgb[i + 1].toInt() and 0xff) shl 8) or (rgb[i + 2].toInt() and 0xff)
                }
                out.setPixels(row, 0, samples.width, 0, y, samples.width, 1)
            }
            out
        } ?: return null
        bitmap = if (downsample) ImageCompressEngine.preview(decoded, SCREEN_EDGE) else decoded
        bitmap.setHasAlpha(false)
        return PdfDocuments.picture(bitmap, RECOMPRESS_QUALITY)
    } catch (_: OutOfMemoryError) {
        return null
    } finally {
        bitmap?.recycle()
        decoded?.recycle()
    }
}

@Composable
fun PdfCompressor(model: PdfCompressorModel = viewModel()) {
    val c = LocalDelphiColours.current
    val loader = model.pdf
    val loaded = loader.opened
    val sizes = loader.source?.sizes.orEmpty()
    val busy = model.job.busy
    val choice = model.mode

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            if (!loaded) {
                CanonDropZone(
                    "Drop PDF here",
                    icon = Icons.Outlined.Description,
                    mimeTypes = arrayOf(PdfDocuments.MIME),
                    photos = null,
                    paste = false,
                    onFiles = { uris -> uris.firstOrNull()?.let(model::load) },
                )
            } else {
                PdfFileRow(loader.source, loader.name, loader.failed, model::clear)
            }
        }
    }

    val settings = CanonSection.of {
        CanonRows {
            CanonHeading("Images")
            CanonSegmented(listOf("Recompress", "Keep", "Rasterise"), choice, enabled = !busy, onSelect = model::mode)
            CanonStatusRow(listOf("Lossy images", "Lossless", "Lossy, text flattened")[choice], CanonTone.Muted)
            if (choice == 0) {
                CanonRow("Downsample images") { CanonSwitch(model.downsample, enabled = !busy, onCheckedChange = model::downsample) }
            }
            if (choice == 2) {
                CanonStatusRow("Pages will be turned into images, and text content will be discarded. Good for scans.", CanonTone.Muted)
                CanonHeading("Resolution")
                CanonSegmented(resolutions.map { "${it.toInt()} DPI" }, model.resolution, enabled = !busy, onSelect = model::resolution)
                CanonRow("Quality", flush = true) {
                    CanonSlider(
                        model.quality,
                        { if (!busy) model.quality(it) },
                        Modifier.fillMaxSize(),
                        range = 10f..100f,
                        step = 1f,
                        readout = model.quality.roundToInt().toString(),
                    )
                }
            }
        }
    }

    val action = CanonSection.of {
        CanonRows {
            PdfJobPanel(model.job, "Compress", Icons.Outlined.Compress, "Failed", enabled = loader.source != null, onStart = model::compress)
        }
    }

    val source = loader.source
    val output = model.job.output
    val page = model.page.coerceAtMost(maxOf(0, sizes.size - 1))
    val comparison by produceState<Triple<ImageBitmap?, ImageBitmap?, Long>>(Triple(null, null, 0L), source, page, output) {
        value = Triple(null, null, 0L)
        if (source == null || page !in source.sizes.indices) return@produceState
        value = withContext(Dispatchers.IO) {
            Triple(
                PdfDocuments.open(source.file) { PdfDocuments.thumbnail(it, page, 700)?.asImageBitmap() },
                output?.let { file -> PdfDocuments.open(file) { PdfDocuments.thumbnail(it, page, 700)?.asImageBitmap() } },
                output?.length() ?: 0L,
            )
        }
    }

    val scale = CanonSection.canvas(CanonCanvasSize.Fixed(150.dp), background = c.card, boxing = CanonCanvasBoxing.UnlessWide) {
        CanonCompressionScale(
            source?.bytes ?: 0,
            if (output == null) 0 else comparison.third,
            "Compressed",
            Modifier.padding(top = LocalCanonCanvas.current.top),
            isWorking = busy,
        )
    }

    val pageSize = sizes.getOrNull(page) ?: PdfSize(210.0, 297.0)
    val compare = pageCanvas(pageSize, c.muted) {
        CanonImageCompare(comparison.first, comparison.second, Modifier.fillMaxSize())
    }

    val strip = CanonSection.canvas(CanonCanvasSize.Fixed(CanonPageStripHeight), background = c.muted) {
        CanonPageStrip(
            sizes.mapIndexed { index, size -> CanonPageStripPage(size.compose(), loader.thumbnails[index]) },
            selected = if (sizes.isEmpty()) null else page,
            onSelect = { model.page = it },
        )
    }

    CanonSplit(
        sidebar = listOf(input, settings, action),
        main = listOfNotNull(scale, compare.takeIf { loaded }),
        banner = strip,
    )
}
