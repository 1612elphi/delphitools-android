package com.delphi.delphitools.tools.socialmedia

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
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
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.canonGrabPan
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.images.ImageCrop
import com.delphi.delphitools.tools.images.ImageFiles
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val CROP_MAX_PIXELS = 3072
private val emptyPixels = Size(1600f, 1200f)
private const val hintWords = "Drag to reposition"
private val tagStyle = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

/** dt-ios SocialCropperViewController: one image cropped to a platform's ratio, the crop dragged into place. */
class SocialCropperModel(application: Application) : AndroidViewModel(application) {
    private var source by mutableStateOf<Bitmap?>(null)
    var preview by mutableStateOf<ImageBitmap?>(null)
        private set
    var name by mutableStateOf("image")
        private set
    var crop by mutableStateOf(SocialCrop())
        private set
    var exporting by mutableStateOf(false)
        private set

    private var loading: Job? = null

    private val filename get() = "$name-${crop.platform.id}-${crop.ratio.label}.png"

    fun load(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        loading?.cancel()
        loading = viewModelScope.launch {
            val (image, shownName) = withContext(Dispatchers.IO) {
                val decoded = runCatching { ImageFiles.decode(ImageDecoder.createSource(resolver, uri), CROP_MAX_PIXELS) }.getOrNull()
                    ?: return@withContext null
                decoded.image to resolver.canonDisplayName(uri)?.substringBeforeLast('.')
            } ?: return@launch
            name = shownName?.ifEmpty { null } ?: "image"
            source = image
            preview = image.asImageBitmap()
            crop = crop.withImage(Size(image.width.toFloat(), image.height.toFloat()))
        }
    }

    fun clear() {
        loading?.cancel()
        source = null
        preview = null
        crop = crop.copy(imageSize = Size.Zero, centre = Offset.Zero)
    }

    fun select(platform: CropPlatform) {
        crop = crop.select(platform)
    }

    fun select(ratio: CropRatio) {
        crop = crop.select(ratio)
    }

    fun drag(delta: Offset, shown: Size) {
        crop = crop.dragged(delta, shown)
    }

    private suspend fun croppedPng(): ByteArray? {
        val image = source ?: return null
        val rect = crop.rect
        if (rect.width <= 0f || rect.height <= 0f) return null
        return withContext(Dispatchers.Default) {
            ImageFiles.unlessOutOfMemory {
                val cut = Bitmap.createBitmap(image, rect.left.roundToInt(), rect.top.roundToInt(), rect.width.roundToInt(), rect.height.roundToInt())
                ImageFiles.png(cut)
            }
        }
    }

    fun copy(context: Context) {
        val name = filename
        viewModelScope.launch { croppedPng()?.let { ImageFiles.copyPng(context, it, name) } }
    }

    fun export(context: Context) {
        if (exporting || source == null) return
        val name = filename
        exporting = true
        viewModelScope.launch {
            val data = croppedPng()
            exporting = false
            if (data != null) FileExport.share(context, data, name, "image/png")
        }
    }
}

@Composable
fun SocialCropper(model: SocialCropperModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val crop = model.crop
    val loaded = model.preview != null
    val size = crop.imageSize
    val pixels = if (loaded) size else emptyPixels
    val rect = if (loaded) crop.rect else SocialCrop.crop(crop.ratio.aspectRatio.toFloat(), pixels, pixels.center)
    val others = crop.platform.ratios.filter { it != crop.ratio }.map { it.label to SocialCrop.crop(it.aspectRatio.toFloat(), pixels, rect.center) }

    val canvas = CanonSection.canvas(
        CanonCanvasSize.Aspect(maxOf(0.5f, pixels.width / maxOf(pixels.height, 1f))),
        background = c.muted,
        showsPattern = true,
    ) {
        SocialCropBoard(model.preview, pixels, rect, crop.ratio.id, crop.ratio.label, others, model::drag)
    }

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            // ponytail: no share-sheet import (iOS acceptShared); add an ACTION_SEND intent filter when wanted
            if (!loaded) {
                CanonDropZone("Drop an image to crop", onFiles = { uris -> uris.firstOrNull()?.let(model::load) })
            } else {
                CanonRow(model.name, detail = "${size.width.toInt()} × ${size.height.toInt()}", flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                }
            }
        }
    }

    val settings = CanonSection.of {
        CanonRows {
            CanonHeading("Platform")
            CanonSegmented(CropPresets.all.map { it.name }, CropPresets.all.indexOf(crop.platform), columns = 3) {
                model.select(CropPresets.all[it])
            }
            CanonHeading("Ratio")
            val ratios = crop.platform.ratios
            CanonSegmented(ratios.map { "${it.name} ${it.label}" }, ratios.indexOf(crop.ratio), columns = 2) { model.select(ratios[it]) }
        }
    }

    val dash = "—"
    val result = CanonSection.of {
        CanonRows {
            CanonHeading("Crop")
            CanonTiles(
                listOf(
                    CanonTile("Crop size", if (loaded) "${rect.width.roundToInt()} × ${rect.height.roundToInt()} px" else dash),
                    CanonTile("Offset", if (loaded) "${rect.left.roundToInt()}, ${rect.top.roundToInt()}" else dash),
                    CanonTile("Original", if (loaded) "${size.width.toInt()} × ${size.height.toInt()} px" else dash),
                )
            )
            CanonGrid {
                CanonButton(
                    "Copy",
                    Modifier.weight(1f).fillMaxHeight(),
                    enabled = loaded,
                    icon = Icons.Rounded.ContentCopy,
                    flashesCopied = true,
                ) { model.copy(context) }
                CanonButton(
                    "Export",
                    Modifier.weight(1f).fillMaxHeight(),
                    kind = CanonButtonKind.Primary,
                    enabled = loaded && !model.exporting,
                    icon = Icons.Outlined.Share,
                    oversized = true,
                ) { model.export(context) }
            }
            if (model.exporting) {
                CanonRow("Processing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground) }
            }
        }
    }

    CanonSplit(sidebar = listOf(canvas), main = listOf(input, settings, result), halves = true, collapsesTo = 240.dp)
}

/**
 * dt-ios SocialCropBoard: the image dimmed outside the crop, thirds inside, the platform's other ratios as dashed
 * outlines round the same centre. A change of ratio springs; a drag on the crop moves it.
 */
@Composable
private fun SocialCropBoard(
    preview: ImageBitmap?,
    pixels: Size,
    crop: Rect,
    ratioId: String,
    label: String,
    others: List<Pair<String, Rect>>,
    onDrag: (Offset, Size) -> Unit,
) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val top = LocalCanonCanvas.current.top
    val measurer = rememberTextMeasurer()
    val hasImage = preview != null
    var dragging by remember { mutableStateOf(false) }
    var shownRatio by remember { mutableStateOf(ratioId) }
    val springs = shownRatio != ratioId && !dragging
    SideEffect { shownRatio = ratioId }
    val animated by animateRectAsState(
        crop,
        if (springs) spring(dampingRatio = 0.725f, stiffness = 190f) else snap(),
        label = "crop",
    )
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .semantics {
                contentDescription = label
                if (hasImage) stateDescription = "${crop.left.roundToInt()}, ${crop.top.roundToInt()}"
            },
    ) {
        val padding = with(density) { CanonMetrics.panelPadding.toPx() }
        val area = Rect(padding, with(density) { top.toPx() } + padding, constraints.maxWidth - padding, constraints.maxHeight - padding)
        val image = ImageCrop.fit(pixels, area)
        val scale = image.width / maxOf(pixels.width, 1f)
        fun shown(rect: Rect) = Rect(image.left + rect.left * scale, image.top + rect.top * scale, image.left + rect.right * scale, image.top + rect.bottom * scale)
        val cropped = shown(animated)
        val shownOthers = others.map { it.first to shown(it.second) }
        val movable = hasImage && (crop.width < pixels.width - 0.5f || crop.height < pixels.height - 0.5f)
        val ghostInk = if (hasImage) Color.White.copy(alpha = 0.8f) else c.mutedForeground

        Canvas(
            Modifier
                .fillMaxSize()
                .canonGrabPan(
                    grabs = { point -> movable && cropped.inflate(8f).contains(point) },
                    onStart = { dragging = true },
                    onEnd = { dragging = false },
                ) { _, delta -> onDrag(delta, image.size) },
        ) {
            if (image.isEmpty) return@Canvas
            if (preview != null) {
                drawImage(
                    preview,
                    dstOffset = IntOffset(image.left.roundToInt(), image.top.roundToInt()),
                    dstSize = IntSize(image.width.roundToInt(), image.height.roundToInt()),
                )
                val dim = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(image)
                    addRect(cropped)
                }
                drawPath(dim, Color.Black.copy(alpha = 0.5f))
            } else {
                drawRect(c.card, image.topLeft, image.size)
                drawRect(c.border, image.topLeft, image.size, style = Stroke(CanonMetrics.hairline.toPx()))
                drawRect(c.accentBg, cropped.topLeft, cropped.size)
            }
            val gridInk = if (hasImage) Color.White.copy(alpha = 0.35f) else c.border
            val line = CanonMetrics.hairline.toPx()
            for (share in listOf(1f / 3, 2f / 3)) {
                val x = cropped.left + cropped.width * share
                val y = cropped.top + cropped.height * share
                drawLine(gridInk, Offset(x, cropped.top), Offset(x, cropped.bottom), line)
                drawLine(gridInk, Offset(cropped.left, y), Offset(cropped.right, y), line)
            }
            val dashes = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            shownOthers.forEach { (_, rect) -> drawRect(ghostInk, rect.topLeft, rect.size, style = Stroke(1.dp.toPx(), pathEffect = dashes)) }
            drawRect(c.primary, cropped.topLeft, cropped.size, style = Stroke(CanonMetrics.frame.toPx()))
        }

        val tagText = measurer.measure(label, tagStyle).size
        val tag = Rect(Offset(cropped.left, cropped.top), Size(tagText.width + with(density) { 10.dp.toPx() }, tagText.height + with(density) { 4.dp.toPx() }))
        Text(
            label,
            Modifier.placedAt(tag.topLeft).background(c.primary).padding(horizontal = 5.dp, vertical = 2.dp),
            style = tagStyle,
            color = c.primaryForeground,
            maxLines = 1,
        )

        val hintStyle = CanonText.captionSemibold.copy(fontSize = 12.sp)
        val hintText = measurer.measure(hintWords, hintStyle).size
        val hintSize = Size(hintText.width + with(density) { 38.dp.toPx() }, maxOf(hintText.height.toFloat(), with(density) { 14.dp.toPx() }) + with(density) { 10.dp.toPx() })
        val hint = hintPlace(image, cropped, hintSize)
        val hintVisible = movable && !dragging && image.width > hintSize.width + with(density) { 16.dp.toPx() }
        val hintAlpha by animateFloatAsState(if (hintVisible) 1f else 0f, tween(200), label = "hint")
        val sideways = cropped.width < image.width - 1
        val upDown = cropped.height < image.height - 1
        Row(
            Modifier
                .centredAt(hint)
                .alpha(hintAlpha)
                .background(c.card.copy(alpha = 0.88f))
                .padding(start = 8.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = when {
                sideways && upDown -> Icons.Rounded.OpenWith
                sideways -> Icons.Rounded.SwapHoriz
                else -> Icons.Rounded.SwapVert
            }
            Icon(icon, null, Modifier.size(14.dp), tint = c.foreground)
            Text(hintWords, style = hintStyle, color = c.foreground, maxLines = 1)
        }

        val taken = mutableListOf(tag)
        if (hintVisible) taken += Rect(Offset(hint.x - hintSize.width / 2, hint.y - hintSize.height / 2), hintSize)
        val gapX = with(density) { 6.dp.toPx() }
        val gapY = with(density) { 4.dp.toPx() }
        shownOthers.forEach { (text, rect) ->
            val textSize = measurer.measure(text, tagStyle).size
            val w = textSize.width.toFloat()
            val h = textSize.height.toFloat()
            val left = rect.left + gapX
            val right = rect.right - w - gapX
            val upper = rect.top + gapY
            val lower = rect.bottom - h - gapY
            val corners = listOf(right to lower, right to upper, left to lower, left to upper).map { (x, y) -> Rect(Offset(x, y), Size(w, h)) }
            val spot = if (rect.width < w + gapX * 2) null else corners.firstOrNull { corner -> taken.none { it.inflate(3f).overlaps(corner) } }
            if (spot != null) {
                taken += spot
                Text(text, Modifier.placedAt(spot.topLeft), style = tagStyle, color = ghostInk, maxLines = 1)
            }
        }
    }
}

private fun hintPlace(image: Rect, crop: Rect, size: Size): Offset {
    val strips = listOf(
        Rect(image.left, image.top, crop.left, image.bottom),
        Rect(crop.right, image.top, image.right, image.bottom),
        Rect(image.left, image.top, image.right, crop.top),
        Rect(image.left, crop.bottom, image.right, image.bottom),
    )
    val room = strips.filter { it.width >= size.width + 8 && it.height >= size.height + 8 }.maxByOrNull { it.width * it.height }
    return room?.center ?: Offset(crop.center.x, crop.bottom - size.height / 2 - 12)
}

private fun Modifier.placedAt(point: Offset): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(point.x.roundToInt(), point.y.roundToInt()) }
}

private fun Modifier.centredAt(point: Offset): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place((point.x - placeable.width / 2f).roundToInt(), (point.y - placeable.height / 2f).roundToInt())
    }
}
