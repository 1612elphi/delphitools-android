package com.delphi.delphitools.tools.socialmedia

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourWell
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonPageStrip
import com.delphi.delphitools.canon.CanonPageStripHeight
import com.delphi.delphitools.canon.CanonPageStripPage
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonToast
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.tools.images.ImageCompressEngine
import com.delphi.delphitools.tools.images.ImageFiles
import com.delphi.delphitools.tools.socialmedia.ScrollGeneratorEngine.Geometry
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PREVIEW_PIXELS = 2400
private val pictureHeight = 200.dp

// the backdrop blurs at a size where sigma is this many px, then scales up
private const val BLUR_SIGMA = 8.0

private val filtered = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

private class ScrollSource(val image: Bitmap, val preview: Bitmap, val name: String, val width: Int, val height: Int) {
    val stem get() = name.substringBeforeLast('.').ifEmpty { "image" }
}

internal class ScrollContent(val geometry: Geometry, val slides: List<ImageBitmap>)

/** MatteSpec.backdrop over `area` of `image`: covering the box, blurred, at a reduced size the caller scales up. */
private fun backdrop(image: Bitmap, area: android.graphics.Rect, boxWidth: Int, boxHeight: Int, blur: Double): Bitmap {
    val scale = min(1.0, BLUR_SIGMA / max(blur, 1e-9))
    val width = max(1, (boxWidth * scale).roundToInt())
    val height = max(1, (boxHeight * scale).roundToInt())
    val small = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val cover = MatteSpec.cover(area.width().toDouble(), area.height().toDouble(), width.toDouble(), height.toDouble(), MatteSpec.OVERSCAN)
    android.graphics.Canvas(small).drawBitmap(image, area, RectF(cover.x.toFloat(), cover.y.toFloat(), (cover.x + cover.width).toFloat(), (cover.y + cover.height).toFloat()), filtered)
    val pixels = IntArray(width * height).also { small.getPixels(it, 0, width, 0, 0, width, height) }
    return Bitmap.createBitmap(MatteEngine.blur(pixels, width, height, blur * scale), width, height, Bitmap.Config.ARGB_8888)
}

/** ScrollEngine.Renderer: slides from `image`, which may be a smaller copy; `fill` is a solid ARGB colour, or null to blur. */
private class ScrollRenderer(private val image: Bitmap, geometry: Geometry, private val fill: Int?) {
    private val scale = image.height.toDouble() / geometry.tileHeight
    private val size = ScrollGeneratorEngine.slideSize(geometry, image.height)
    private val slices = ScrollGeneratorEngine.slices(geometry)

    fun slide(index: Int): Bitmap? {
        val slice = slices.getOrNull(index) ?: return null
        val (width, height) = size
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val crop = if (slice.sourceWidth > 0) {
            val left = (slice.sourceX * scale).roundToInt().coerceIn(0, image.width - 1)
            val right = ((slice.sourceX + slice.sourceWidth) * scale).roundToInt().coerceIn(left + 1, image.width)
            android.graphics.Rect(left, 0, right, image.height)
        } else null
        if (slice.isFillEdge) {
            if (fill != null) canvas.drawColor(fill)
            else if (crop != null) {
                canvas.drawBitmap(backdrop(image, crop, width, height, ScrollGeneratorEngine.BLUR * scale), null, RectF(0f, 0f, width.toFloat(), height.toFloat()), filtered)
            }
        }
        if (crop != null) {
            val x = (slice.drawX * scale).toFloat()
            canvas.drawBitmap(image, crop, RectF(x, 0f, x + (slice.sourceWidth * scale).toFloat(), height.toFloat()), filtered)
        }
        return out
    }
}

/** dt-ios ScrollGeneratorViewController: a wide image cut into carousel slides that scroll as one. */
class ScrollGeneratorModel(application: Application) : AndroidViewModel(application) {
    private var source by mutableStateOf<ScrollSource?>(null)
    var failed by mutableStateOf(false)
        private set
    var shape by mutableStateOf(0)
        private set
    var fillMode by mutableStateOf(0)
        private set
    var colour by mutableStateOf(Rgb(0.0, 0.0, 0.0))
        private set
    internal var content by mutableStateOf<ScrollContent?>(null)
        private set
    var exporting by mutableStateOf(false)
        private set

    private var loading: Job? = null
    private var rendering: Job? = null

    val loaded get() = source != null
    val name get() = source?.name.orEmpty()
    val pixels get() = source?.let { IntSize(it.width, it.height) }

    /** File pixels, so a capped decode does not round. */
    val geometry get() = source?.let { ScrollGeneratorEngine.geometry(it.width, it.height, ScrollGeneratorEngine.ratios[shape]) }

    /** Written slide size, from the decoded image. */
    val slideSize get() = source?.let { s -> geometry?.let { ScrollGeneratorEngine.slideSize(it, s.image.height) } }

    private val fill: Int?
        get() {
            if (fillMode != 1) return null
            val c = colour.clamped
            return (0xFF shl 24) or ((c.r * 255).roundToInt() shl 16) or ((c.g * 255).roundToInt() shl 8) or (c.b * 255).roundToInt()
        }

    fun load(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        loading?.cancel()
        loading = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
                val longest = ScrollGeneratorEngine.longestSide(bounds.outWidth, bounds.outHeight)
                val decoded = runCatching { ImageFiles.decode(ImageDecoder.createSource(resolver, uri), longest) }.getOrNull()
                    ?: return@withContext null
                val preview = ImageCompressEngine.preview(decoded.image, PREVIEW_PIXELS)
                ScrollSource(decoded.image, preview, resolver.canonDisplayName(uri) ?: "", decoded.width, decoded.height)
            }
            source = loaded
            failed = loaded == null
            rerender()
        }
    }

    fun clear() {
        loading?.cancel()
        source = null
        failed = false
        rerender()
    }

    fun pickShape(index: Int) {
        shape = index
        rerender()
    }

    fun pickFill(index: Int) {
        fillMode = index
        rerender()
    }

    fun pickColour(rgb: Rgb) {
        colour = rgb
        rerender()
    }

    private fun rerender() {
        rendering?.cancel()
        val source = source
        val geometry = geometry
        if (source == null || geometry == null) {
            content = null
            return
        }
        val fill = fill
        rendering = viewModelScope.launch {
            val slides = withContext(Dispatchers.Default) {
                val renderer = ScrollRenderer(source.preview, geometry, fill)
                (0 until geometry.slideCount).mapNotNull {
                    ensureActive()
                    runCatching { renderer.slide(it)?.asImageBitmap() }.getOrNull()
                }
            }
            content = ScrollContent(geometry, slides)
        }
    }

    fun copy(view: View, index: Int) {
        val source = source ?: return
        val geometry = geometry ?: return
        val fill = fill
        viewModelScope.launch {
            val data = withContext(Dispatchers.Default) {
                runCatching { ScrollRenderer(source.image, geometry, fill).slide(index)?.let(ImageFiles::png) }.getOrNull()
            } ?: return@launch
            ImageFiles.copyPng(view.context, data, "${source.stem}-scroll-${index + 1}.png")
            CanonToast.show(view)
        }
    }

    /** null: all slides. */
    fun export(context: Context, only: Int? = null) {
        val source = source ?: return
        val geometry = geometry ?: return
        if (exporting) return
        val fill = fill
        exporting = true
        viewModelScope.launch {
            val files = withContext(Dispatchers.Default) {
                runCatching {
                    val renderer = ScrollRenderer(source.image, geometry, fill)
                    (only?.let { listOf(it) } ?: (0 until geometry.slideCount).toList()).map { index ->
                        val data = renderer.slide(index)?.let(ImageFiles::png) ?: return@runCatching null
                        "${source.stem}-scroll-${index + 1}.png" to data
                    }
                }.getOrNull()
            }
            exporting = false
            if (files != null) ImageFiles.shareAll(context, files, "image/png")
        }
    }
}

@Composable
fun ScrollGenerator(model: ScrollGeneratorModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val view = LocalView.current
    val geometry = model.geometry
    val fills = geometry?.needsFill == true
    val dash = "—"
    val slidesTitle = "Slides"

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            val pixels = model.pixels
            if (pixels == null) {
                CanonDropZone("Drop an image here", paste = false, onFiles = { uris -> uris.firstOrNull()?.let(model::load) })
            } else {
                CanonRow(model.name, detail = "${pixels.width} × ${pixels.height} px", flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                }
            }
            if (model.failed) CanonStatusRow("That file can't be read as an image", CanonTone.Warn)
        }
    }

    val settings = CanonSection.of {
        CanonRows {
            CanonHeading("Shape")
            CanonSegmented(listOf("4:5 Portrait", "1:1 Square"), model.shape, onSelect = model::pickShape)
            CanonHeading("Fill")
            CanonSegmented(listOf("Blur", "Solid Colour"), model.fillMode, enabled = fills, onSelect = model::pickFill)
            if (fills && model.fillMode == 1) CanonColourWell(model.colour, model::pickColour, label = "Colour")
            if (geometry != null && !fills) CanonStatusRow("Not needed", CanonTone.Muted)
            CanonButton(
                "Export All",
                kind = CanonButtonKind.Primary,
                enabled = model.loaded && !model.exporting,
                icon = Icons.Outlined.Share,
                oversized = true,
            ) { model.export(context) }
            if (model.exporting) {
                CanonRow("Processing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground) }
            }
        }
    }

    val readings = CanonSection.of {
        CanonRows {
            CanonHeading(slidesTitle)
            val written = model.slideSize
            CanonTiles(
                listOf(
                    CanonTile("Count", geometry?.let { "${it.slideCount}" } ?: dash),
                    CanonTile("Size", written?.let { "${it.first} × ${it.second} px" } ?: dash),
                    CanonTile("Fit", geometry?.let { if (it.needsFill) "Edge fill" else "Perfect fit" } ?: dash),
                ),
            )
        }
    }

    val content = model.content
    val strip = CanonSection.canvas(CanonCanvasSize.Fixed(CanonPageStripHeight), background = c.muted, boxing = CanonCanvasBoxing.UnlessWide) {
        val pageSize = content?.let { Size(it.geometry.tileWidth.toFloat(), it.geometry.tileHeight.toFloat()) }
        CanonPageStrip(
            if (pageSize == null) emptyList() else content.slides.map { CanonPageStripPage(pageSize, it) },
            menu = { index -> listOf("Share" to { model.export(context, index) }, "Copy" to { model.copy(view, index) }) },
            empty = slidesTitle,
        )
    }

    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(pictureHeight), background = c.muted) { CarouselPicture(content) }

    CanonSplit(sidebar = listOf(input, settings), main = listOf(readings, strip), banner = banner)
}

/** ScrollCarouselPicture: the slides side by side as one strip, seams dashed, the image's own edges marked. */
@Composable
private fun CarouselPicture(content: ScrollContent?) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val top = LocalCanonCanvas.current.top
    val chipStyle = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = c.foreground)
    val measurer = rememberTextMeasurer()
    // ponytail: crossfade when the slide count changes; no spring between layouts of the same count
    Crossfade(content, Modifier.fillMaxSize(), animationSpec = tween(250), label = "carousel") { shown ->
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
            if (shown == null) {
                Box(Modifier.fillMaxSize().padding(top = top), contentAlignment = Alignment.Center) {
                    Text("Carousel", style = CanonText.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.mutedForeground)
                }
                return@BoxWithConstraints
            }
            val geometry = shown.geometry
            with(density) {
                val padding = CanonMetrics.panelPadding.toPx()
                val areaTop = top.toPx() + padding
                val place = ScrollGeneratorEngine.place(
                    geometry,
                    padding.toDouble(),
                    areaTop.toDouble(),
                    (constraints.maxWidth - padding * 2).toDouble(),
                    (constraints.maxHeight - areaTop - padding).toDouble(),
                )
                val scale = place.scale.toFloat()
                val stripX = place.stripX.toFloat()
                val y = place.y.toFloat()
                val height = geometry.tileHeight * scale
                val tile = geometry.tileWidth * scale
                val primary = c.primary
                val frame = CanonMetrics.frame.toPx()
                val dashLength = 4.dp.toPx()
                val unit = 1.dp.toPx()
                Canvas(Modifier.fillMaxSize()) {
                    // images never mirror
                    shown.slides.forEachIndexed { index, slide ->
                        drawImage(
                            slide,
                            dstOffset = IntOffset((stripX + index * tile).roundToInt(), y.roundToInt()),
                            dstSize = IntSize(tile.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
                        )
                    }
                    val light = Color.White
                    val dark = Color.Black.copy(alpha = 0.6f)
                    for (seam in 1 until shown.slides.size) {
                        val x = (stripX + seam * tile - unit / 2).roundToInt().toFloat()
                        var at = y
                        while (at < y + height) {
                            drawRect(light, Offset(x, at), Size(unit, min(dashLength, y + height - at)))
                            if (at + dashLength < y + height) drawRect(dark, Offset(x, at + dashLength), Size(unit, min(dashLength, y + height - at - dashLength)))
                            at += dashLength * 2
                        }
                    }
                    if (geometry.needsFill) {
                        val imageX = stripX + geometry.imageStart.toFloat() * scale
                        for (x in listOf(imageX, imageX + geometry.imageWidth * scale)) {
                            val left = x.roundToInt() - unit
                            drawRect(light, Offset(left, y), Size(unit, height))
                            drawRect(dark, Offset(left + unit, y), Size(unit, height))
                        }
                    }
                    drawRect(
                        primary,
                        Offset(stripX - frame / 2, y - frame / 2),
                        Size(tile * geometry.slideCount + frame, height + frame),
                        style = Stroke(frame),
                    )
                }
                val inset = 3.dp.toPx()
                shown.slides.indices.forEach { index ->
                    val label = "${index + 1}"
                    val chipWidth = measurer.measure(label, chipStyle).size.width + 8.dp.toPx()
                    if (tile >= chipWidth + inset * 2) {
                        Text(
                            label,
                            style = chipStyle,
                            maxLines = 1,
                            modifier = Modifier
                                .offset { IntOffset((stripX + index * tile + inset).roundToInt(), (y + inset).roundToInt()) }
                                .background(c.card.copy(alpha = 0.85f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            val count = shown.slides.size
            CanonFigure(
                "$count",
                Modifier.align(Alignment.BottomEnd).padding(end = CanonMetrics.panelPadding, bottom = 6.dp),
                unit = if (count == 1) "slide" else "slides",
                halo = c.muted,
            )
        }
    }
}
