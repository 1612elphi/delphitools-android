package com.delphi.delphitools.tools.images

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.util.Half
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonBar
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourWell
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonChecks
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.audiovideo.WhisperFile
import com.delphi.delphitools.tools.audiovideo.WhisperModel
import com.delphi.delphitools.tools.audiovideo.WhisperModelStore
import com.delphi.delphitools.tools.audiovideo.WhisperState
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_PIXELS = 3072
private const val PREVIEW_PIXELS = 1600
private const val WEIGHTS = "model.onnx"

// ponytail: the Whisper download store and its types hold this model too; rename them when a third model arrives
// fp16 matches the web tool
private val RMBG = WhisperModel(
    "rmbg-1.4", "RMBG 1.4", "briaai/RMBG-1.4", "da1d078376e6691105a5530a374a010627fc60b5",
    listOf(WhisperFile("onnx/model_fp16.onnx", WEIGHTS, 88217533L, "9fdfdb41866d872e0acf4a010c35c1a8547bf0eebe0d1544406bbf1c824cb59d")),
)

private const val TRACE_ALPHA = 0.3f
private const val TRACE_FADE_MS = 250
private const val TRACE_FIRST_MS = 500L
private const val TRACE_TAPPED_MS = 3000L

private class CutSource(val image: Bitmap, val preview: ImageBitmap, val name: String) {
    val stem get() = name.substringBeforeLast('.').ifEmpty { "image" }
}

private class Cutout(val image: Bitmap, val preview: ImageBitmap, val kept: Float)

/** Web background remover: briaai/RMBG-1.4 in ONNX Runtime, the matte applied as alpha. */
class BackgroundRemoverModel(application: Application) : AndroidViewModel(application) {
    private var source by mutableStateOf<CutSource?>(null)
    private var cutout by mutableStateOf<Cutout?>(null)
    val name get() = source?.name ?: ""
    val pixels get() = source?.image?.let { it.width to it.height }
    val ghost get() = source?.preview
    val cut get() = cutout?.preview
    val kept get() = cutout?.kept
    val ready get() = cutout != null && !busy

    var busy by mutableStateOf(false)
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    var mode by mutableIntStateOf(0)
    var colour by mutableStateOf(Rgb.white)

    val download get() = (WhisperModelStore.state(RMBG) as? WhisperState.Downloading)?.fraction

    private var session: OrtSession? = null
    private var job: Job? = null

    fun load(uri: Uri) {
        clear()
        val resolver = getApplication<Application>().contentResolver
        busy = true
        job = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val name = resolver.canonDisplayName(uri) ?: ""
                val decoded = runCatching { ImageFiles.decode(ImageDecoder.createSource(resolver, uri), MAX_PIXELS) }.getOrNull()
                ImageFiles.unlessOutOfMemory {
                    decoded?.let { CutSource(it.image, ImageCompressEngine.preview(it.image, PREVIEW_PIXELS).asImageBitmap(), name) }
                }
            }
            source = loaded
            if (loaded == null) {
                busy = false
                failure = "Build failed"
            } else {
                remove(loaded)
            }
        }
    }

    fun retry() {
        val source = source ?: return
        if (busy) return
        busy = true
        failure = null
        job = viewModelScope.launch { remove(source) }
    }

    private suspend fun remove(source: CutSource) {
        val context = getApplication<Application>()
        WhisperModelStore.refresh(context, listOf(RMBG))
        if (WhisperModelStore.state(RMBG) != WhisperState.Installed) {
            WhisperModelStore.download(context, RMBG, viewModelScope)
            snapshotFlow { WhisperModelStore.state(RMBG) }.first { it !is WhisperState.Downloading }
        }
        if (WhisperModelStore.state(RMBG) != WhisperState.Installed) {
            busy = false
            failure = "Download failed"
            return
        }
        val made = withContext(Dispatchers.Default) {
            runCatching { ImageFiles.unlessOutOfMemory { matte(File(WhisperModelStore.folder(context, RMBG), WEIGHTS), source.image) } }
                .onFailure { android.util.Log.w("BackgroundRemover", "inference failed", it) }
                .getOrNull()
        }
        busy = false
        cutout = made
        failure = if (made == null) "Build failed" else null
    }

    private fun matte(weights: File, image: Bitmap): Cutout {
        val env = OrtEnvironment.getEnvironment()
        // ort 1.31.1 has no fp16 nhwc MaxPool(11) kernel on arm64; the nhwc rewrite is level 3
        val options = OrtSession.SessionOptions().apply { setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT) }
        val session = session ?: env.createSession(weights.path, options).also { session = it }
        val side = BackgroundRemoveEngine.SIDE
        val square = image.scale(side, side)
        val planes = BackgroundRemoveEngine.planes(square.argb())
        if (square !== image) square.recycle()
        val shape = longArrayOf(1, 3, side.toLong(), side.toLong())
        val input = session.inputInfo.entries.first()
        // an fp16 export may keep float32 inputs
        val tensor = if ((input.value.info as TensorInfo).type == OnnxJavaType.FLOAT16) {
            val halves = ByteBuffer.allocateDirect(planes.size * 2).order(ByteOrder.nativeOrder())
            for (value in planes) halves.putShort(Half.toHalf(value))
            halves.rewind()
            OnnxTensor.createTensor(env, halves, shape, OnnxJavaType.FLOAT16)
        } else {
            OnnxTensor.createTensor(env, FloatBuffer.wrap(planes), shape)
        }
        val (alpha, kept) = tensor.use {
            session.run(mapOf(input.key to tensor)).use { result -> BackgroundRemoveEngine.alpha((result[0] as OnnxTensor).floatBuffer) }
        }
        val mask = Bitmap.createBitmap(alpha, side, side, Bitmap.Config.ARGB_8888)
        val out = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawBitmap(image, 0f, 0f, null)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
            drawBitmap(mask, null, Rect(0, 0, image.width, image.height), paint)
        }
        mask.recycle()
        return Cutout(out, ImageCompressEngine.preview(out, PREVIEW_PIXELS).asImageBitmap(), kept)
    }

    fun clear() {
        job?.cancel()
        WhisperModelStore.cancel(RMBG)
        source = null
        cutout = null
        busy = false
        failure = null
    }

    private suspend fun png(): ByteArray? {
        val image = cutout?.image ?: return null
        val fill = colour.takeIf { mode == 1 }
        return withContext(Dispatchers.Default) {
            ImageFiles.unlessOutOfMemory {
                if (fill == null) return@unlessOutOfMemory ImageFiles.png(image)
                val flat = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                Canvas(flat).apply {
                    drawColor(fill.argb)
                    drawBitmap(image, 0f, 0f, null)
                }
                ImageFiles.png(flat).also { flat.recycle() }
            }
        }
    }

    fun copy(context: Context) {
        val stem = source?.stem ?: return
        viewModelScope.launch { png()?.let { ImageFiles.copyPng(context, it, "$stem-cutout.png") } }
    }

    fun export(context: Context) {
        val stem = source?.stem ?: return
        viewModelScope.launch { png()?.let { FileExport.share(context, it, "$stem-cutout.png", "image/png") } }
    }

    override fun onCleared() {
        clear()
        session?.close()
    }
}

private val Rgb.argb
    get() = clamped.let { android.graphics.Color.rgb((it.r * 255).roundToInt(), (it.g * 255).roundToInt(), (it.b * 255).roundToInt()) }

@Composable
fun BackgroundRemover(model: BackgroundRemoverModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val pixels = model.pixels
    val fill = model.colour.takeIf { model.mode == 1 }

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(1f), background = c.muted, showsPattern = true) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(top = LocalCanonCanvas.current.top).padding(CanonMetrics.panelPadding),
            contentAlignment = Alignment.Center,
        ) {
            val ghost = model.ghost
            if (ghost != null) {
                val ratio = ghost.width.toFloat() / ghost.height
                val wide = ratio > maxWidth / maxHeight
                val cut = model.cut
                var taps by remember(cut) { mutableIntStateOf(0) }
                val trace = remember { Animatable(1f) }
                LaunchedEffect(cut, taps) {
                    if (cut == null) return@LaunchedEffect trace.snapTo(1f)
                    trace.animateTo(TRACE_ALPHA, tween(TRACE_FADE_MS))
                    delay(if (taps == 0) TRACE_FIRST_MS else TRACE_TAPPED_MS)
                    trace.animateTo(0f, tween(TRACE_FADE_MS))
                }
                Box(
                    Modifier
                        .size(if (wide) maxWidth else maxHeight * ratio, if (wide) maxWidth / ratio else maxHeight)
                        .canonChecks()
                        .pointerInput(cut) { detectTapGestures { taps++ } },
                ) {
                    if (fill != null) Box(Modifier.fillMaxSize().background(Color(fill.argb)))
                    // dt-ios CutoutPicture.trace
                    Image(ghost, null, Modifier.fillMaxSize().graphicsLayer { alpha = trace.value }, contentScale = ContentScale.FillBounds)
                    if (cut != null) Image(cut, "Preview", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                }
            }
            if (model.busy) CanonLoader(colour = c.mutedForeground)
        }
    }

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            if (pixels == null && !model.busy) {
                CanonDropZone("Drop an image here", paste = false, onFiles = { uris -> uris.firstOrNull()?.let(model::load) })
            } else {
                CanonRow(model.name.ifEmpty { "Image" }, detail = pixels?.let { "${it.first} × ${it.second}" }, flush = true) {
                    CanonButton("Clear", icon = pencil(R.drawable.pencil_trash_2), onClick = model::clear)
                }
            }
            model.download?.let { fraction ->
                CanonRow("Downloading model", detail = "${(fraction * 100).roundToInt()}%")
                CanonBar(fraction.toFloat(), Modifier.fillMaxWidth().padding(vertical = 2.dp))
            }
            model.failure?.let { failure ->
                CanonStatusRow(failure, CanonTone.Warn)
                if (pixels != null) CanonButton("Retry", Modifier.fillMaxWidth(), icon = pencil(R.drawable.pencil_refresh_cw), onClick = model::retry)
            }
        }
    }

    val subject = CanonSection.of {
        if (pixels == null) return@of
        CanonRows {
            CanonHeading("Subject")
            // ponytail: one matte for the whole picture; dt-ios Vision splits it into subjects to keep or drop
            val kept = model.kept
            CanonTiles(
                listOf(
                    CanonTile("Kept", kept?.let { "${(it * 100).roundToInt()} %" } ?: "—", level = kept),
                    CanonTile("Size", "${pixels.first} × ${pixels.second} px"),
                )
            )
        }
    }

    val backdrop = CanonSection.of {
        CanonRows {
            CanonHeading("Background")
            CanonSegmented(listOf("Transparent", "Colour"), model.mode, onSelect = { model.mode = it })
            if (model.mode == 1) CanonColourWell(model.colour, { model.colour = it }, label = "Background Colour")
        }
    }

    val export = CanonSection.of {
        CanonRows {
            CanonGrid {
                val cell = Modifier.weight(1f).fillMaxHeight()
                CanonButton("Copy Image", cell, enabled = model.ready, icon = pencil(R.drawable.pencil_copy), flashesCopied = true) { model.copy(context) }
                CanonButton("Export PNG", cell, kind = CanonButtonKind.Primary, enabled = model.ready, icon = pencil(R.drawable.pencil_share), oversized = true) {
                    model.export(context)
                }
            }
        }
    }

    CanonSplit(sidebar = listOf(canvas), main = listOf(input, subject, backdrop, export), halves = true, collapsesTo = 200.dp)
}
