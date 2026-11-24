package com.delphi.delphitools.tools.images

import android.animation.ValueAnimator
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDimensionLine
import com.delphi.delphitools.canon.CanonDimensionPlacement
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonChecks
import com.delphi.delphitools.canon.canonDimensionLines
import com.delphi.delphitools.canon.canonDimensionSheet
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.canonPlaced
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

// ponytail: longest edge decoded; a larger PNG is clipped at this size, not its own
private const val CLIP_MAX_PIXELS = 8192
private const val CLIP_PREVIEW_PIXELS = 1600

private val emptyPixels = IntSize(1600, 1200)
private val emptyKept = IntRect(340, 220, 1370, 920)

private class ClipSource(val image: Bitmap, val preview: ImageBitmap, val name: String, val width: Int, val height: Int) {
    val stem get() = name.substringBeforeLast('.').ifEmpty { "image" }
}

/** dt-ios ImageClipperViewController: the transparent edges of an image found and cut off. */
class ImageClipperModel(application: Application) : AndroidViewModel(application) {
    private var source by mutableStateOf<ClipSource?>(null)
    val preview get() = source?.preview
    val name get() = source?.name ?: ""
    val fileSize get() = source?.let { IntSize(it.width, it.height) }
    val pixels get() = source?.let { IntSize(it.image.width, it.image.height) }

    /** In decoded pixels; null for a fully transparent image. */
    var kept by mutableStateOf<IntRect?>(null)
        private set
    var working by mutableStateOf(false)
        private set
    var exporting by mutableStateOf(false)
        private set
    var failedName by mutableStateOf<String?>(null)
        private set

    private var loading: Job? = null

    fun load(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        loading?.cancel()
        working = true
        loading = viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { resolver.canonDisplayName(uri) ?: "" }
            val loaded = withContext(Dispatchers.IO) {
                val decoded = runCatching { ImageFiles.decode(ImageDecoder.createSource(resolver, uri), CLIP_MAX_PIXELS) }.getOrNull()
                    ?: return@withContext null
                val image = decoded.image
                val preview = ImageCompressEngine.preview(image, CLIP_PREVIEW_PIXELS).asImageBitmap()
                val kept = if (!image.hasAlpha()) {
                    IntRect(0, 0, image.width, image.height)
                } else {
                    ImageClipEngine.boundingRect(image.width, image.height) { y, row -> image.getPixels(row, 0, image.width, 0, y, image.width, 1) }
                }
                ClipSource(image, preview, name, decoded.width, decoded.height) to kept
            }
            working = false
            failedName = if (loaded == null) name else null
            if (loaded != null) {
                source = loaded.first
                kept = loaded.second
            }
        }
    }

    fun clear() {
        loading?.cancel()
        source = null
        kept = null
        working = false
        failedName = null
    }

    private suspend fun clippedPng(): ByteArray? {
        val source = source ?: return null
        val kept = kept ?: return null
        return withContext(Dispatchers.Default) {
            ImageFiles.png(Bitmap.createBitmap(source.image, kept.left, kept.top, kept.width, kept.height))
        }
    }

    fun copy(context: Context) {
        val stem = source?.stem ?: return
        viewModelScope.launch {
            val data = clippedPng() ?: return@launch
            ImageFiles.copyPng(context, data, "$stem-clipped.png")
        }
    }

    fun export(context: Context) {
        val stem = source?.stem ?: return
        if (exporting || kept == null) return
        exporting = true
        viewModelScope.launch {
            val data = clippedPng()
            exporting = false
            if (data != null) FileExport.share(context, data, "$stem-clipped.png", "image/png")
        }
    }
}

@Composable
fun ImageClipper(model: ImageClipperModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val pixels = model.pixels
    val kept = model.kept
    val loaded = pixels != null

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(4f / 3f), background = c.muted, showsPattern = true) {
        ClipBoard(model.preview, pixels, kept)
    }

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            // ponytail: no share-sheet import (iOS acceptShared); add an ACTION_SEND intent filter when wanted
            val size = model.fileSize
            if (size == null) {
                CanonDropZone("Drop a transparent PNG to clip", paste = false, onFiles = { uris -> uris.firstOrNull()?.let(model::load) })
            } else {
                CanonRow(model.name, detail = "${size.width} × ${size.height}", flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                }
            }
            model.failedName?.let { CanonStatusRow("Could not read $it", CanonTone.Warn) }
        }
    }

    val margins = if (pixels != null && kept != null) ClipMargins.of(kept, pixels.width, pixels.height) else null
    val dash = "—"
    val pair = { size: IntSize -> "${size.width} × ${size.height} px" }
    val px = { value: Int -> "$value px" }
    val result = CanonSection.of {
        if (!loaded && !model.working) return@of
        CanonRows {
            CanonHeading("Output")
            CanonTiles(
                listOf(
                    CanonTile("Original", pixels?.let(pair) ?: dash),
                    CanonTile("Clipped", kept?.let { pair(it.size) } ?: dash),
                    CanonTile(
                        "Area",
                        margins?.let { if (it.savedPercent > 0) "−${it.savedPercent} %" else "0 %" } ?: dash,
                        level = margins?.let { it.savedPercent / 100f },
                    ),
                    CanonTile("Top", margins?.let { px(it.top) } ?: dash),
                    CanonTile("Right", margins?.let { px(it.right) } ?: dash),
                    CanonTile("Bottom", margins?.let { px(it.bottom) } ?: dash),
                    CanonTile("Left", margins?.let { px(it.left) } ?: dash),
                )
            )
            val note = when {
                !loaded -> null
                kept == null -> "Fully transparent"
                margins?.isEmpty == true -> "No transparent edges"
                else -> null
            }
            if (note != null) CanonStatusRow(note, CanonTone.Muted)
            CanonGrid {
                CanonButton(
                    "Copy",
                    Modifier.weight(1f).fillMaxHeight(),
                    enabled = kept != null,
                    icon = Icons.Rounded.ContentCopy,
                    flashesCopied = true,
                ) { model.copy(context) }
                CanonButton(
                    "Export PNG",
                    Modifier.weight(1f).fillMaxHeight(),
                    kind = CanonButtonKind.Primary,
                    enabled = kept != null && !model.exporting,
                    icon = Icons.Outlined.Share,
                    oversized = true,
                ) { model.export(context) }
            }
            if (model.working || model.exporting) {
                CanonRow("Processing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground) }
            }
        }
    }

    CanonSplit(sidebar = listOf(canvas), main = listOf(input, result), halves = true, collapsesTo = 240.dp)
}

/**
 * dt-ios ClipBoard: the image over the checks, the cut-off edges hatched, the kept rect boxed and measured,
 * each edge's pixels in its band. A new clip springs in from the whole image.
 */
@Composable
private fun ClipBoard(preview: ImageBitmap?, pixels: IntSize?, kept: IntRect?) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val top = LocalCanonCanvas.current.top
    val empty = pixels == null
    val shape = pixels ?: emptyPixels
    val clip = if (empty) emptyKept else kept
    val grow = remember { Animatable(1f) }
    LaunchedEffect(preview, kept) {
        if (preview == null || !ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect grow.snapTo(1f)
        grow.snapTo(0f)
        grow.animateTo(1f, spring(dampingRatio = 0.71f, stiffness = 160f))
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .semantics {
                role = Role.Image
                clip?.let { contentDescription = "${it.width} × ${it.height} px" }
            },
    ) {
        with(density) {
            val padding = CanonMetrics.panelPadding.toPx()
            val area = Rect(padding, top.toPx() + padding * 0.5f, constraints.maxWidth - padding, constraints.maxHeight - padding)
            val image = canonDimensionSheet(Size(shape.width.toFloat(), shape.height.toFloat()), area, rightToLeft)
            val scale = image.width / max(shape.width, 1)
            val target = clip?.let {
                Rect(image.left + it.left * scale, image.top + it.top * scale, image.left + it.right * scale, image.top + it.bottom * scale)
            }
            val inner = target?.let { lerp(image, it, grow.value) }

            Box(Modifier.canonPlaced(CanonDimensionPlacement(image.center, image.size)).alpha(if (empty) 0.6f else 1f).canonChecks())
            val hatchInk = c.mutedForeground.copy(alpha = 0.5f)
            val boxInk = if (empty) c.mutedForeground else c.primary
            Canvas(Modifier.fillMaxSize()) {
                if (preview != null) {
                    drawImage(
                        preview,
                        dstOffset = IntOffset(image.left.roundToInt(), image.top.roundToInt()),
                        dstSize = IntSize(image.width.roundToInt(), image.height.roundToInt()),
                    )
                }
                val bands = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(image)
                    if (inner != null) addRect(inner)
                }
                clipPath(bands) {
                    val step = 7.dp.toPx()
                    val line = 1.dp.toPx()
                    val ink = if (empty) hatchInk.copy(alpha = hatchInk.alpha * 0.5f) else hatchInk
                    translate(image.left, image.top) {
                        var x = -image.height
                        while (x < image.width) {
                            drawLine(ink, Offset(x, image.height), Offset(x + image.height, 0f), line)
                            x += step
                        }
                    }
                }
                if (inner != null || empty) {
                    val boxed = inner ?: image
                    val dashes = if (empty) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null
                    drawRect(boxInk, boxed.topLeft, boxed.size, style = Stroke(1.5.dp.toPx(), pathEffect = dashes))
                }
            }
            val span = inner ?: image
            val (across, down) = canonDimensionLines(span, rightToLeft, edge = image)
            if (inner != null || empty) {
                CanonDimensionLine(clip?.width ?: 0, Modifier.canonPlaced(across), blank = empty)
                CanonDimensionLine(clip?.height ?: 0, Modifier.canonPlaced(down), blank = empty)
            }
            if (!empty && inner != null && pixels != null && kept != null) {
                val margins = ClipMargins.of(kept, pixels.width, pixels.height)
                val bandRects = listOf(
                    Rect(image.left, image.top, image.right, inner.top),
                    Rect(inner.right, inner.top, image.right, inner.bottom),
                    Rect(image.left, inner.bottom, image.right, image.bottom),
                    Rect(image.left, inner.top, inner.left, inner.bottom),
                )
                val centres = listOf(
                    Offset(inner.center.x, bandRects[0].center.y),
                    Offset(bandRects[1].center.x, inner.center.y),
                    Offset(inner.center.x, bandRects[2].center.y),
                    Offset(bandRects[3].center.x, inner.center.y),
                )
                listOf(margins.top, margins.right, margins.bottom, margins.left).forEachIndexed { index, value ->
                    if (value == 0) return@forEachIndexed
                    Text(
                        " $value px ",
                        style = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = c.foreground,
                        maxLines = 1,
                        modifier = Modifier.centredIfFits(bandRects[index], centres[index]).background(c.card).padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** Places the element centred on [centre], or leaves it out when [band] cannot hold it with 4 px to spare. */
private fun Modifier.centredIfFits(band: Rect, centre: Offset): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        val spare = 4.dp.toPx()
        if (band.width >= placeable.width + spare && band.height >= placeable.height + spare) {
            placeable.place((centre.x - placeable.width / 2f).roundToInt(), (centre.y - placeable.height / 2f).roundToInt())
        }
    }
}
