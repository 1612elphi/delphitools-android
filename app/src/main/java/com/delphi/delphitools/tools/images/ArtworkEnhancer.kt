package com.delphi.delphitools.tools.images

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonImageCompare
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private const val ARTWORK_PIXELS = 3072
private const val DETAIL_SIDE = 64
private const val DETAIL_FACTOR = 8
private const val OPACITY_SETTLE_MS = 120L

private fun bitmap(pixels: IntArray, width: Int, height: Int): Bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

private fun detail(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val (big, side) = ArtworkEnhanceEngine.magnified(pixels, width, height, DETAIL_SIDE, DETAIL_FACTOR)
    return bitmap(big, side, side).asImageBitmap()
}

/** dt-ios ArtworkEnhancerViewModel: one artwork with a seeded noise overlay, re-rendered on every change. */
class ArtworkEnhancerModel(application: Application) : AndroidViewModel(application) {
    private var pixels: IntArray? = null
    private var resultBitmap: Bitmap? = null
    var size by mutableStateOf(IntSize.Zero)
        private set
    var name by mutableStateOf("artwork")
        private set
    var source by mutableStateOf<ImageBitmap?>(null)
        private set
    var sourceDetail by mutableStateOf<ImageBitmap?>(null)
        private set
    var result by mutableStateOf<ImageBitmap?>(null)
        private set
    var resultDetail by mutableStateOf<ImageBitmap?>(null)
        private set
    var opacity by mutableFloatStateOf(2f)
        private set
    var noiseScale by mutableIntStateOf(1)
        private set
    private var seed = 0
    var exporting by mutableStateOf(false)
        private set
    private var loading: Job? = null
    private var processing: Job? = null

    fun load(uri: Uri) {
        loading?.cancel()
        val resolver = getApplication<Application>().contentResolver
        loading = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val decoded = runCatching { ImageFiles.decode(ImageDecoder.createSource(resolver, uri), ARTWORK_PIXELS) }.getOrNull()
                    ?: return@withContext null
                val image = decoded.image
                val argb = image.argb()
                Triple(resolver.canonDisplayName(uri)?.substringBeforeLast('.'), image, argb to detail(argb, image.width, image.height))
            } ?: return@launch
            val (fileName, image, prepared) = loaded
            name = fileName?.ifEmpty { null } ?: "artwork"
            pixels = prepared.first
            size = IntSize(image.width, image.height)
            source = image.asImageBitmap()
            sourceDetail = prepared.second
            process()
        }
    }

    fun clear() {
        loading?.cancel()
        processing?.cancel()
        pixels = null
        resultBitmap = null
        size = IntSize.Zero
        source = null
        sourceDetail = null
        result = null
        resultDetail = null
    }

    fun changeOpacity(value: Float) {
        if (value == opacity) return
        opacity = value
        process(OPACITY_SETTLE_MS)
    }

    fun changeScale(value: Int) {
        noiseScale = value
        process()
    }

    fun regenerate() {
        seed += 1
        process()
    }

    private fun process(delayMs: Long = 0) {
        val pixels = pixels ?: return
        val width = size.width
        val height = size.height
        val alpha = opacity / 100f
        val scale = noiseScale
        val seed = seed
        processing?.cancel()
        processing = viewModelScope.launch {
            delay(delayMs)
            val (image, crop) = withContext(Dispatchers.Default) {
                val out = ArtworkEnhanceEngine.enhance(pixels, width, alpha, scale, seed)
                bitmap(out, width, height) to detail(out, width, height)
            }
            resultBitmap = image
            result = image.asImageBitmap()
            resultDetail = crop
        }
    }

    fun export(context: Context) {
        val image = resultBitmap ?: return
        if (exporting) return
        exporting = true
        val filename = "$name-enhanced.png"
        viewModelScope.launch {
            val data = withContext(Dispatchers.Default) { ImageFiles.png(image) }
            exporting = false
            if (data != null) FileExport.share(context, data, filename, "image/png")
        }
    }
}

@Composable
fun ArtworkEnhancer(model: ArtworkEnhancerModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val loaded = model.source != null
    val size = model.size

    val aspect = if (loaded) (size.width.toFloat() / maxOf(size.height, 1)).coerceIn(0.6f, 2f) else 4f / 3f
    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(aspect), background = c.muted, maxHeight = 640.dp, showsPattern = true) {
        ArtworkFrame(if (loaded) size else null) {
            CanonImageCompare(model.source, model.result, beforeTitle = "Original", afterTitle = "Result")
        }
    }

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            if (!loaded) {
                CanonDropZone("Drop artwork to add noise overlay", paste = false, onFiles = { uris -> uris.firstOrNull()?.let(model::load) })
            } else {
                CanonRow(model.name, detail = "${size.width} × ${size.height}", flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                }
            }
        }
    }

    val crop = ArtworkEnhanceEngine.detailRect(size.width, size.height, DETAIL_SIDE)
    val grain = CanonSection.canvas(CanonCanvasSize.Aspect(1.5f), background = c.muted, maxHeight = 360.dp, boxing = CanonCanvasBoxing.UnlessWide) {
        ArtworkFrame(IntSize(crop.width, crop.height), caption = "${crop.width}×${crop.height} px · ${DETAIL_FACTOR}×") {
            CanonImageCompare(model.sourceDetail, model.resultDetail, beforeTitle = "Original", afterTitle = "Result")
        }
    }

    val settings = CanonSection.of {
        CanonRows {
            CanonHeading("Noise")
            CanonRow("Opacity", flush = true) {
                CanonSlider(
                    model.opacity,
                    model::changeOpacity,
                    Modifier.fillMaxSize(),
                    range = 1f..20f,
                    step = 1f,
                    readout = "${model.opacity.roundToInt()} %",
                )
            }
            CanonHeading("Scale")
            CanonSegmented((1..4).map { "$it×" }, model.noiseScale - 1) { model.changeScale(it + 1) }
            CanonGrid {
                val cell = Modifier.weight(1f).fillMaxHeight()
                CanonButton("Regenerate", cell, enabled = loaded, icon = Icons.Outlined.Autorenew) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    model.regenerate()
                }
                CanonButton(
                    "Export PNG",
                    cell,
                    kind = CanonButtonKind.Primary,
                    enabled = model.result != null && !model.exporting,
                    icon = Icons.Outlined.Share,
                    oversized = true,
                ) { model.export(context) }
            }
            if (model.exporting) {
                CanonRow("Processing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground) }
            }
        }
    }

    val main = if (loaded) listOf(input, grain, settings) else listOf(input, settings)
    CanonSplit(sidebar = listOf(canvas), main = main, halves = true, collapsesTo = 240.dp)
}

/** dt-ios ArtworkFrame: [content] fitted at [aspect] inside the panel padding, a caption under it; the empty state without an aspect. */
@Composable
private fun ArtworkFrame(aspect: IntSize?, caption: String? = null, content: @Composable () -> Unit) {
    val c = LocalDelphiColours.current
    val top = LocalCanonCanvas.current.top
    val padding = CanonMetrics.panelPadding
    if (aspect == null || aspect.width <= 0 || aspect.height <= 0) {
        Column(
            Modifier.fillMaxSize().padding(top = top),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = c.mutedForeground, modifier = Modifier.size(34.dp))
            Text("Original", style = CanonText.bodySemibold, color = c.mutedForeground)
        }
        return
    }
    val captionHeight = if (caption == null) 0.dp else 22.dp
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().padding(start = padding, top = top + padding, end = padding, bottom = padding + captionHeight)) {
        val scale = min(maxWidth.value / aspect.width, maxHeight.value / aspect.height)
        val width = (aspect.width * scale).dp
        val height = (aspect.height * scale).dp
        Box(Modifier.align(Alignment.Center).size(width, height)) { content() }
        if (caption != null) {
            Text(
                caption,
                Modifier.align(Alignment.Center).fillMaxWidth().offset(y = height / 2 + 6.dp + 7.dp),
                style = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = c.mutedForeground,
                textAlign = TextAlign.Center,
            )
        }
    }
}
