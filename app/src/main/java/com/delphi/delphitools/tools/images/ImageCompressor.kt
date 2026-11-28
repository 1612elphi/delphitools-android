package com.delphi.delphitools.tools.images

import android.app.Application
import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCompressionScale
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonImageCompare
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val PREVIEW_PIXELS = 2048
private const val QUALITY_SETTLE_MS = 250L

private class Output(val data: ByteArray, val preview: ImageBitmap)

/** dt-ios ImageCompressorViewModel: one source, re-encoded whenever the format or quality changes. */
class ImageCompressorModel(application: Application) : AndroidViewModel(application) {
    var sourcePreview by mutableStateOf<ImageBitmap?>(null)
        private set
    var sourceName by mutableStateOf("")
        private set
    var sourceMeta by mutableStateOf("")
        private set
    var sourceBytes by mutableLongStateOf(0)
        private set
    var format by mutableStateOf(ImageCompressFormat.Jpeg)
        private set
    var quality by mutableFloatStateOf(75f)
        private set
    private var output by mutableStateOf<Output?>(null)
    val result get() = output?.preview
    val resultBytes get() = output?.data?.size?.toLong() ?: 0
    var failed by mutableStateOf(false)
        private set
    var isWorking by mutableStateOf(false)
        private set

    private var source: android.graphics.Bitmap? = null
    private var task: Job? = null

    // serial, uninterruptible encodes
    private val encoder = Dispatchers.Default.limitedParallelism(1)

    private val exportName: String
        get() = "${sourceName.substringBeforeLast('.').ifEmpty { "image" }}.${format.fileExtension}"

    fun load(uri: Uri) {
        task?.cancel()
        isWorking = false
        val app = getApplication<Application>()
        task = viewModelScope.launch {
            val resolver = app.contentResolver
            val loaded = withContext(Dispatchers.IO) {
                val data = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    ?: return@withContext null
                val image = ImageCompressEngine.decode(data) ?: return@withContext null
                ImageFiles.unlessOutOfMemory { Triple(image, ImageCompressEngine.preview(image, PREVIEW_PIXELS), data.size.toLong()) }
            } ?: return@launch
            val (image, preview, bytes) = loaded
            source = image
            sourcePreview = preview.asImageBitmap()
            sourceName = resolver.canonDisplayName(uri) ?: ""
            sourceBytes = bytes
            sourceMeta = "${image.width} × ${image.height} · ${Formatter.formatShortFileSize(app, bytes)}"
            compress()
        }
    }

    fun clear() {
        task?.cancel()
        source = null
        sourcePreview = null
        sourceName = ""
        sourceMeta = ""
        sourceBytes = 0
        output = null
        failed = false
        isWorking = false
    }

    fun select(format: ImageCompressFormat) {
        if (format == this.format) return
        this.format = format
        compress()
    }

    fun changeQuality(quality: Float) {
        if (quality == this.quality) return
        this.quality = quality
        compress(QUALITY_SETTLE_MS)
    }

    fun export(context: Context) {
        val data = output?.data ?: return
        viewModelScope.launch { FileExport.share(context, data, exportName, format.mimeType) }
    }

    private fun compress(delayMs: Long = 0) {
        val source = source ?: return
        val format = format
        val quality = quality.roundToInt()
        val scratch = getApplication<Application>().cacheDir
        task?.cancel()
        failed = false
        isWorking = true
        task = viewModelScope.launch {
            delay(delayMs)
            val data = withContext(encoder) { ImageCompressEngine.encode(source, format, quality, scratch) }
            val preview = withContext(Dispatchers.Default) { data?.let { ImageCompressEngine.thumbnail(it, PREVIEW_PIXELS) } }
            isWorking = false
            failed = data == null || preview == null
            output = if (data != null && preview != null) Output(data, preview.asImageBitmap()) else null
        }
    }
}

@Composable
fun ImageCompressor(model: ImageCompressorModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val formats = ImageCompressFormat.available
    val preview = model.sourcePreview

    val controls = CanonSection.of {
        CanonRows {
            if (preview == null) {
                CanonDropZone("Drop an image", paste = false, onFiles = { uris -> uris.firstOrNull()?.let(model::load) })
            } else {
                CanonHeading("Source")
                CanonRow(model.sourceName, detail = model.sourceMeta, flush = true) {
                    CanonButton("Clear", icon = pencil(R.drawable.pencil_trash_2), onClick = model::clear)
                }
                CanonHeading("Format")
                CanonSegmented(formats.map { it.label }, formats.indexOf(model.format)) { model.select(formats[it]) }
                if (model.format.hasQuality) {
                    CanonRow("Quality", flush = true) {
                        CanonSlider(
                            model.quality,
                            model::changeQuality,
                            Modifier.fillMaxSize(),
                            range = 1f..100f,
                            step = 1f,
                            readout = model.quality.roundToInt().toString(),
                        )
                    }
                }
                CanonButton(
                    "Export",
                    kind = CanonButtonKind.Primary,
                    enabled = model.result != null,
                    icon = pencil(R.drawable.pencil_share),
                    oversized = true,
                ) { model.export(context) }
            }
        }
    }

    val picture = if (preview == null) {
        CanonSection.of { CanonRows { CanonEmptyRow("Add an image to compare") } }
    } else {
        val aspect = maxOf(1f, preview.width.toFloat() / maxOf(preview.height, 1))
        CanonSection.canvas(CanonCanvasSize.Aspect(aspect), background = c.muted, boxing = CanonCanvasBoxing.UnlessWide) {
            CanonImageCompare(preview, model.result, Modifier.fillMaxSize(), failed = model.failed)
        }
    }

    val scale = CanonSection.canvas(CanonCanvasSize.Fixed(176.dp), background = c.card) {
        CanonCompressionScale(
            model.sourceBytes,
            model.resultBytes,
            if (preview == null) "Compressed" else model.format.label,
            Modifier.padding(top = LocalCanonCanvas.current.top),
            isWorking = model.isWorking,
        )
    }

    CanonSplit(sidebar = listOf(controls), main = listOf(picture), banner = scale)
}
