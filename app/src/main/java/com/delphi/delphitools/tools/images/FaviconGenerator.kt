package com.delphi.delphitools.tools.images

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.canonGrabPan
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// 512 px largest favicon
private const val FAVICON_MAX_PIXELS = 2048
private const val FAVICON_PREVIEW_PIXELS = 800
private val cropHeight = 220.dp
private val sceneHeight = 196.dp

private class FaviconSource(val image: Bitmap, val preview: ImageBitmap, val name: String) {
    val width get() = image.width
    val height get() = image.height
    val stem get() = name.substringBeforeLast('.').ifEmpty { "image" }
}

private class FaviconIcon(val size: Int, val image: Bitmap) {
    val shown = image.asImageBitmap()
}

/** dt-ios FaviconGeneratorViewController: one image cropped to a square and rendered at every favicon size. */
class FaviconGeneratorModel(application: Application) : AndroidViewModel(application) {
    private var source by mutableStateOf<FaviconSource?>(null)
    val preview get() = source?.preview
    val name get() = source?.name ?: ""
    val stem get() = source?.stem
    val pixels get() = source?.let { IntSize(it.width, it.height) }

    // null: largest centred square
    private var square by mutableStateOf<IntRect?>(null)
    val cropSquare get() = source?.let { square ?: FaviconEngine.centred(it.width, it.height) }

    private var icons by mutableStateOf<Map<Int, FaviconIcon>>(emptyMap())
    val ready get() = icons.isNotEmpty()

    private var loading: Job? = null
    private var rendering: Job? = null
    private val renderer = Dispatchers.Default.limitedParallelism(1)

    fun icon(size: Int) = icons[size]?.shown

    fun load(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        loading?.cancel()
        loading = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val decoded = runCatching { ImageFiles.decode(ImageDecoder.createSource(resolver, uri), FAVICON_MAX_PIXELS) }.getOrNull()
                    ?: return@withContext null
                val preview = ImageCompressEngine.preview(decoded.image, FAVICON_PREVIEW_PIXELS).asImageBitmap()
                FaviconSource(decoded.image, preview, resolver.canonDisplayName(uri) ?: "")
            }
            source = loaded
            square = null
            icons = emptyMap()
            render()
        }
    }

    fun clear() {
        loading?.cancel()
        rendering?.cancel()
        source = null
        icons = emptyMap()
        square = null
    }

    fun changeSquare(next: IntRect) {
        square = next
        render()
    }

    private fun render() {
        val source = source ?: return
        val square = cropSquare ?: return
        rendering?.cancel()
        rendering = viewModelScope.launch {
            val made = withContext(renderer) { renderIcons(source.image, square) }
            if (source === this@FaviconGeneratorModel.source) icons = made
        }
    }

    private fun png(size: Int) = icons[size]?.image?.let(ImageFiles::png)

    fun shareIcon(context: Context, size: Int) {
        viewModelScope.launch {
            val data = withContext(Dispatchers.Default) { png(size) } ?: return@launch
            FileExport.share(context, data, FaviconEngine.fileName(size), "image/png")
        }
    }

    fun shareAll(context: Context) {
        val sizes = icons.keys.sorted()
        viewModelScope.launch {
            val files = withContext(Dispatchers.Default) { sizes.mapNotNull { size -> png(size)?.let { FaviconEngine.fileName(size) to it } } }
            if (files.isNotEmpty()) ImageFiles.shareAll(context, files, "image/png")
        }
    }

    fun shareIco(context: Context) {
        val sizes = icons.keys.filter { it in FaviconEngine.icoSizes }
        viewModelScope.launch {
            val frames = withContext(Dispatchers.Default) { sizes.mapNotNull { size -> png(size)?.let { size to it } } }
            if (frames.isEmpty()) return@launch
            FileExport.share(context, FaviconEngine.ico(frames), "favicon.ico", "image/x-icon")
        }
    }
}

// halving avoids aliasing
private fun renderIcons(image: Bitmap, square: IntRect): Map<Int, FaviconIcon> {
    val cropped = runCatching { Bitmap.createBitmap(image, square.left, square.top, square.width, square.height) }.getOrNull()
        ?: return emptyMap()
    return FaviconEngine.sizes.associateWith { size ->
        var step = cropped
        while (step.width / 2 >= size * 2) step = step.scale(step.width / 2, step.height / 2)
        FaviconIcon(size, step.scale(size, size))
    }
}

@Composable
fun FaviconGenerator(model: FaviconGeneratorModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val preview = model.preview
    val pixels = model.pixels
    val square = model.cropSquare

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            // ponytail: no share-sheet import (iOS acceptShared); add an ACTION_SEND intent filter when wanted
            if (preview == null || pixels == null || square == null) {
                CanonDropZone("Drop an image here", paste = false, onFiles = { uris -> uris.firstOrNull()?.let(model::load) })
            } else {
                CanonRow(
                    model.name,
                    detail = "${pixels.width} × ${pixels.height} → ${square.width} × ${square.width}",
                    flush = true,
                ) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                }
                FaviconCrop(preview, pixels, square, model::changeSquare)
                CanonGrid {
                    CanonButton(
                        "favicon.ico",
                        Modifier.weight(1f).fillMaxHeight(),
                        enabled = model.ready,
                        icon = Icons.Outlined.Download,
                    ) { model.shareIco(context) }
                    CanonButton(
                        "Export All",
                        Modifier.weight(1f).fillMaxHeight(),
                        kind = CanonButtonKind.Primary,
                        enabled = model.ready,
                        icon = Icons.Outlined.Share,
                        oversized = true,
                    ) { model.shareAll(context) }
                }
            }
        }
    }

    val sizes = CanonSection.of {
        CanonRows {
            CanonHeading("Sizes")
            FaviconEngine.sizes.forEach { size -> FaviconSizeRow(size, model.icon(size)) { model.shareIcon(context, size) } }
        }
    }

    val snippet = CanonSection.of { CanonRows { CanonCodeBlock(FaviconEngine.snippet, language = "HTML") } }

    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(sceneHeight), background = c.muted) {
        FaviconScene(model.icon(32), model.icon(180), model.icon(192), model.stem)
    }

    CanonSplit(sidebar = listOf(input), main = listOf(sizes, snippet), banner = banner)
}

// dt-ios FaviconSizeRow
@Composable
private fun FaviconSizeRow(size: Int, image: ImageBitmap?, onShare: () -> Unit) {
    val c = LocalDelphiColours.current
    val side = min(size, 48).dp
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = CanonMetrics.rowHeight)
            .background(c.border),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Box(Modifier.width(72.dp).fillMaxHeight().heightIn(min = side + 16.dp).background(c.muted), contentAlignment = Alignment.Center) {
            if (image == null) {
                Spacer(Modifier.size(side).background(c.card))
            } else {
                Image(image, null, Modifier.size(side), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().background(c.card).padding(start = CanonMetrics.panelPadding, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Text("$size × $size", style = TextStyle(fontFamily = DelphiMono, fontSize = 15.sp), color = c.foreground)
            FaviconEngine.role(size)?.let { Text(it, style = CanonText.caption, color = c.mutedForeground) }
        }
        CanonButton("", Modifier.width(60.dp).fillMaxHeight(), enabled = image != null, icon = Icons.Outlined.Share, contentDescription = "Share", onClick = onShare)
    }
}

// dt-ios FaviconCropView
@Composable
private fun FaviconCrop(preview: ImageBitmap, pixels: IntSize, square: IntRect, onChange: (IntRect) -> Unit) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    var dragged by remember { mutableStateOf<IntRect?>(null) }
    var resizing by remember { mutableStateOf(false) }
    var grab by remember { mutableStateOf<Offset?>(null) }
    val current = dragged ?: square
    val full = min(pixels.width, pixels.height)
    val least = min(FaviconEngine.minimumSide(pixels.width, pixels.height), full)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(cropHeight)
            .background(c.muted)
            .clipToBounds()
            .clearAndSetSemantics {
                contentDescription = "Crop"
                stateDescription = "${current.width} px"
                progressBarRangeInfo = ProgressBarRangeInfo(current.width.toFloat(), least.toFloat()..full.toFloat())
                setProgress { side ->
                    val centre = current.center
                    onChange(FaviconEngine.clamp(centre.x - side / 2, centre.y - side / 2, side, pixels.width, pixels.height))
                    true
                }
            },
    ) {
        val reach = with(density) { ImageCropReach.toPx() }
        val page = Size(pixels.width.toFloat(), pixels.height.toFloat())
        val imageFrame = ImageCrop.pageRect(page, Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()), reach)
        val scale = imageFrame.width / max(pixels.width, 1)
        val shown = Rect(
            imageFrame.left + current.left * scale,
            imageFrame.top + current.top * scale,
            imageFrame.left + current.right * scale,
            imageFrame.top + current.bottom * scale,
        )
        fun nearHandle(point: Offset) = abs(point.x - shown.right) < reach && abs(point.y - shown.bottom) < reach
        fun at(point: Offset) = Offset((point.x - imageFrame.left) / max(scale, 0.0001f), (point.y - imageFrame.top) / max(scale, 0.0001f))
        Canvas(
            Modifier
                .fillMaxSize()
                .canonGrabPan(
                    grabs = { point ->
                        val movable = current.width < pixels.width || current.height < pixels.height
                        nearHandle(point) || (movable && shown.contains(point))
                    },
                    onStart = { point ->
                        dragged = square
                        resizing = nearHandle(point)
                        if (!resizing) {
                            val p = at(point)
                            grab = Offset(p.x - square.left, p.y - square.top)
                        }
                    },
                    onEnd = {
                        dragged = null
                        resizing = false
                        grab = null
                    },
                ) { change, _ ->
                    val start = dragged ?: return@canonGrabPan
                    val p = at(change.position)
                    val moving = grab
                    val next = when {
                        resizing -> {
                            val side = max(p.x - start.left, p.y - start.top)
                            val room = min(pixels.width - start.left, pixels.height - start.top).toFloat()
                            FaviconEngine.clamp(start.left.toFloat(), start.top.toFloat(), min(side, room), pixels.width, pixels.height)
                        }
                        moving != null -> FaviconEngine.clamp(p.x - moving.x, p.y - moving.y, start.width.toFloat(), pixels.width, pixels.height)
                        else -> return@canonGrabPan
                    }
                    if (next == start) return@canonGrabPan
                    dragged = next
                    onChange(next)
                },
        ) {
            drawImage(
                preview,
                dstOffset = IntOffset(imageFrame.left.roundToInt(), imageFrame.top.roundToInt()),
                dstSize = IntSize(imageFrame.width.roundToInt(), imageFrame.height.roundToInt()),
            )
            val dim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(imageFrame)
                addRect(shown)
            }
            drawPath(dim, Color.Black.copy(alpha = 0.45f))
            drawRect(c.primary, shown.topLeft, shown.size, style = Stroke(CanonMetrics.frame.toPx()))
            val mark = 7.dp.toPx()
            drawRect(c.primary, Offset(shown.right - mark, shown.bottom - mark), Size(mark * 2, mark * 2))
        }
    }
}

private val sceneWindow = 380.dp
private val sceneTouch = 60.dp
private val sceneApp = 52.dp

// dt-ios FaviconScene
@Composable
private fun FaviconScene(tab: ImageBitmap?, touch: ImageBitmap?, app: ImageBitmap?, name: String?) {
    val top = LocalCanonCanvas.current.top
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .clearAndSetSemantics {}
            .padding(start = CanonMetrics.panelPadding, end = CanonMetrics.panelPadding, top = top + 10.dp, bottom = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val window = min(sceneWindow.value, maxWidth.value).dp
        val iconsWidth = sceneTouch + 28.dp + sceneApp
        val beside = maxWidth >= window + 48.dp + iconsWidth
        Crossfade(Triple(tab, touch, app), label = "icons") { (tab, touch, app) ->
            if (beside) {
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    SceneWindow(window, tab, name)
                    SceneIcons(touch, app, name)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SceneWindow(window, tab, name)
                    SceneIcons(touch, app, name)
                }
            }
        }
    }
}

@Composable
private fun SceneIcon(image: ImageBitmap?, modifier: Modifier) {
    val c = LocalDelphiColours.current
    if (image == null) {
        Box(modifier.background(c.card).border(CanonMetrics.hairline, c.border))
    } else {
        Image(image, null, modifier, contentScale = ContentScale.FillBounds)
    }
}

@Composable
private fun SceneWindow(width: Dp, tab: ImageBitmap?, name: String?) {
    val c = LocalDelphiColours.current
    val tabHeight = 34.dp
    Column(Modifier.width(width).height(72.dp).background(c.border).padding(CanonMetrics.hairline)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(tabHeight).background(c.muted)) {
            val back = min(110f, maxWidth.value * 0.3f).dp
            val front = min(220f, maxWidth.value - back.value - 6f).dp
            Row(Modifier.fillMaxSize().padding(start = 6.dp), verticalAlignment = Alignment.Bottom) {
                Row(Modifier.width(back).height(tabHeight - 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(14.dp).background(c.card).border(CanonMetrics.hairline, c.border))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(max(0f, back.value - 44f).dp).height(4.dp).background(c.mutedForeground.copy(alpha = 0.25f)))
                }
                Row(Modifier.width(front).height(tabHeight - 4.dp).background(c.card), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(10.dp))
                    SceneIcon(tab, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        name ?: "",
                        Modifier.weight(1f),
                        style = CanonText.caption.copy(fontSize = 12.sp),
                        color = c.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(16.dp))
                    Icon(Icons.Rounded.Close, null, Modifier.size(14.dp).padding(2.dp), tint = c.mutedForeground)
                    Spacer(Modifier.width(10.dp))
                }
            }
        }
        Box(Modifier.fillMaxSize().background(c.card).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Box(Modifier.fillMaxSize().background(c.muted))
        }
    }
}

@Composable
private fun SceneIcons(touch: ImageBitmap?, app: ImageBitmap?, name: String?) {
    val c = LocalDelphiColours.current
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        listOf(Triple(touch, sceneTouch, RoundedCornerShape(sceneTouch * 0.225f)), Triple(app, sceneApp, CircleShape)).forEach { (image, side, shape) ->
            Column(Modifier.width(side), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(side, sceneTouch), contentAlignment = Alignment.Center) {
                    SceneIcon(image, Modifier.size(side).clip(shape))
                }
                Text(
                    name ?: "",
                    Modifier.requiredWidth(80.dp).padding(top = 4.dp),
                    style = CanonText.caption,
                    color = c.foreground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
