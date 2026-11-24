package com.delphi.delphitools.tools.images

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourStrip
import com.delphi.delphitools.canon.CanonCopyFeedback
import com.delphi.delphitools.canon.CanonDimensionLine
import com.delphi.delphitools.canon.CanonDimensionLineRoom
import com.delphi.delphitools.canon.CanonDimensionPlacement
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonValueLayout
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonChecks
import com.delphi.delphitools.canon.canonDimensionLines
import com.delphi.delphitools.canon.canonDimensionSheet
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.canonPlaced
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.tools.colour.PaletteExtractorEngine
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val ATLAS_PICTURE_PIXELS = 2048
private const val ATLAS_SCAN_PIXELS = 512
private const val ATLAS_PALETTE_PIXELS = 100

// characters; longer values wrap
private const val LONG_VALUE = 22

// png/jfif housekeeping excluded
private val personalSections = setOf("Camera", "Capture", "Location", "Dates", "IPTC", "XMP")

// coordinate line shows these
private val coordinateLabels = setOf("Latitude", "Latitude Ref", "Longitude", "Longitude Ref")

private val scopesPlate = Color(16 / 255f, 18 / 255f, 16 / 255f)
private val sidebarWidth = 340.dp
private val stackingWidth = 640.dp

private class AtlasReading(
    val atlas: AtlasData,
    val picture: ImageBitmap? = null,
    val pixels: IntSize? = null,
    val scan: ImageColourScan? = null,
    val chroma: ImageBitmap? = null,
    val palette: List<Rgb> = emptyList(),
)

private val exifTags: List<String> by lazy {
    ExifInterface::class.java.fields.filter { it.name.startsWith("TAG_") }.mapNotNull { it.get(null) as? String }.distinct()
}

/** dt-ios ImageAtlasViewController.read: the file's metadata, a picture of it, its colour scan and palette. */
private object AtlasFiles {
    fun read(data: ByteArray, name: String, sizeLabel: String): AtlasReading {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0) return AtlasReading(ImageAtlasReport.build(null))
        val header = header(data)
        val decoded = ImageFiles.decode(ImageDecoder.createSource(ByteBuffer.wrap(data)), ATLAS_PICTURE_PIXELS)
        val tags = runCatching { ExifInterface(ByteArrayInputStream(data)) }.getOrNull()?.let { exif ->
            exifTags.mapNotNull { tag -> exif.getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }?.let { tag to it } }.toMap()
        }.orEmpty()
        // imageio: embedded icc only
        val profile = bounds.outColorSpace?.name?.takeIf { MetadataStripEngine.contains(data, "acsp") }
        val atlas = ImageAtlasReport.build(
            AtlasFacts(
                filename = name,
                byteCount = data.size,
                sizeLabel = sizeLabel,
                mimeType = bounds.outMimeType,
                width = bounds.outWidth,
                height = bounds.outHeight,
                colourModel = header?.model ?: "RGB",
                depth = header?.depth ?: if (bounds.outConfig == Bitmap.Config.RGBA_F16) 16 else 8,
                hasAlpha = header?.alpha ?: decoded?.image?.hasAlpha(),
                profile = profile,
                tags = tags,
                xmp = ImageAtlasReport.xmpPacket(data),
            ),
        )
        decoded ?: return AtlasReading(atlas)
        val image = decoded.image
        val small = ImageCompressEngine.preview(image, ATLAS_SCAN_PIXELS)
        val scan = scan(small, atlas.hasAlpha)
        val tiny = ImageCompressEngine.preview(small, ATLAS_PALETTE_PIXELS)
        val argb = tiny.argb()
        val palette = PaletteExtractorEngine.cluster(PaletteExtractorEngine.oklabPixels(argb), 6)
            .sortedByDescending { it.count }
            .map { it.colour }
        val chroma = scan.chroma?.let {
            Bitmap.createBitmap(it, ImageColourScan.CELLS, ImageColourScan.CELLS, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
        return AtlasReading(atlas, image.asImageBitmap(), IntSize(decoded.width, decoded.height), scan, chroma, palette)
    }

    private class Header(val model: String, val depth: Int, val alpha: Boolean?)

    // imageio colour model and depth: png ihdr, jpeg sof
    private fun header(data: ByteArray): Header? {
        fun byte(at: Int) = data[at].toInt() and 0xFF
        if (PngChunkStrip.isPng(data) && data.size > 26) {
            val type = byte(25)
            val alpha = type == 4 || type == 6 || MetadataStripEngine.contains(data, "tRNS")
            return Header(if (type == 0 || type == 4) "Gray" else "RGB", byte(24), alpha)
        }
        if (data.size < 4 || byte(0) != 0xFF || byte(1) != 0xD8) return null
        var at = 2
        while (at + 9 < data.size && byte(at) == 0xFF) {
            val marker = byte(at + 1)
            val length = byte(at + 2) shl 8 or byte(at + 3)
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                val model = when (byte(at + 9)) {
                    1 -> "Gray"
                    4 -> "CMYK"
                    else -> "RGB"
                }
                return Header(model, byte(at + 4), false)
            }
            at += 2 + length
        }
        return null
    }

    // copyPixelsToBuffer keeps the bitmap's own space; getPixels converts to srgb
    private fun scan(bitmap: Bitmap, hasAlpha: Boolean): ImageColourScan {
        val buffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        val bytes = buffer.array()
        val argb = IntArray(bitmap.width * bitmap.height) { i ->
            val a = bytes[i * 4 + 3].toInt() and 0xFF
            if (a == 0) return@IntArray 0
            fun channel(k: Int) = min(255, (bytes[i * 4 + k].toInt() and 0xFF) * 255 / a)
            (a shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
        }
        val space = bitmap.colorSpace as? ColorSpace.Rgb
        if (space == null || space.isSrgb) return ImageColourScan.scan(argb, hasAlpha)
        val linear = ColorSpace.connect(space, ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB))
        val columns = (0 until 3).map { j -> linear.transform(FloatArray(3) { if (it == j) 1f else 0f }) }
        val matrix = DoubleArray(9) { columns[it % 3][it / 3].toDouble() }
        val eotf = DoubleArray(256) { space.eotf.applyAsDouble(it / 255.0) }
        val primaries = space.primaries.toList().chunked(2) { it[0].toDouble() to it[1].toDouble() }
        return ImageColourScan.scan(argb, hasAlpha, eotf, matrix, primaries)
    }
}

/** dt-ios ImageAtlasViewController: everything an image file says about itself. */
class ImageAtlasModel(application: Application) : AndroidViewModel(application) {
    private var reading by mutableStateOf<AtlasReading?>(null)
    var name by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set
    var showsOther by mutableStateOf(false)
    var land by mutableStateOf(emptyList<FloatArray>())
        private set
    private var task: Job? = null

    val atlas get() = reading?.atlas
    internal val picture get() = reading?.picture
    internal val pixels get() = reading?.pixels
    internal val scan get() = reading?.scan
    internal val chroma get() = reading?.chroma
    internal val palette get() = reading?.palette.orEmpty()

    init {
        val assets = getApplication<Application>().assets
        viewModelScope.launch {
            land = withContext(Dispatchers.IO) { WorldMap.land(assets) }
        }
    }

    fun load(uri: Uri) {
        task?.cancel()
        val app = getApplication<Application>()
        val resolver = app.contentResolver
        reading = null
        loading = true
        task = viewModelScope.launch {
            val shownName = withContext(Dispatchers.IO) { resolver.canonDisplayName(uri) ?: "" }
            name = shownName
            val read = withContext(Dispatchers.IO) {
                val data = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: ByteArray(0)
                AtlasFiles.read(data, shownName, Formatter.formatFileSize(app, data.size.toLong()))
            }
            reading = read
            showsOther = false
            loading = false
        }
    }

    fun clear() {
        task?.cancel()
        reading = null
        name = ""
        loading = false
    }

    fun report(): String? = reading?.takeIf { it.atlas.readable }?.let { ImageAtlasReport.reportText(withScan(it)) }

    internal fun colourRows(): List<Pair<AtlasRow, Float?>> = reading?.let(::colourRows).orEmpty()
}

private fun percent(share: Double): String {
    val text = String.format(Locale.ROOT, "%.1f", share * 100).removeSuffix(".0")
    return "$text%"
}

private fun colourRows(reading: AtlasReading): List<Pair<AtlasRow, Float?>> {
    val rows = reading.atlas.sections.firstOrNull { it.title == "Colour" }?.rows.orEmpty().map { it to null as Float? }.toMutableList()
    val scan = reading.scan ?: return rows
    scan.gamutOverSRGB?.let { rows.add(AtlasRow("Gamut", String.format(Locale.ROOT, "%.2f × sRGB", it)) to null) }
    rows.add(AtlasRow("Outside sRGB", percent(scan.outsideSRGB)) to scan.outsideSRGB.toFloat())
    scan.transparent?.let { rows.add(AtlasRow("Transparent", percent(it)) to it.toFloat()) }
    return rows
}

private fun withScan(reading: AtlasReading): AtlasData {
    val rows = colourRows(reading).map { it.first }
    val sections = reading.atlas.sections.toMutableList()
    val index = sections.indexOfFirst { it.title == "Colour" }
    if (index >= 0) sections[index] = AtlasSection("Colour", rows) else if (rows.isNotEmpty()) sections.add(AtlasSection("Colour", rows))
    return reading.atlas.copy(sections = sections)
}

private fun summary(atlas: AtlasData, sizeLabel: String): String {
    val fields = when (atlas.fieldCount) {
        0 -> "no metadata"
        1 -> "1 field"
        else -> "${atlas.fieldCount} fields"
    }
    return listOfNotNull(atlas.format, sizeLabel, fields).joinToString(" · ")
}

@Composable
private fun AtlasTiles(rows: List<AtlasRow>, levels: List<Float?>? = null) {
    val items = rows.mapIndexed { index, row -> CanonTile(row.label, row.value, levels?.getOrNull(index)) }
    val short = items.filter { it.value.length <= LONG_VALUE }
    if (short.isNotEmpty()) CanonTiles(short)
    for (item in items.filter { it.value.length > LONG_VALUE }) {
        CanonValueLine(item.label, item.value, layout = CanonValueLayout.Above)
    }
}

@Composable
fun ImageAtlas(model: ImageAtlasModel = viewModel()) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val atlas = model.atlas
    val loaded = model.name.isNotEmpty() || model.loading
    val unreadable = atlas?.readable == false
    val scan = model.scan

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // CanonSplit's sidebar layout: the banner's scopes sit over the sidebar
        val beside = maxWidth >= stackingWidth && maxHeight <= maxWidth

        val controls = CanonSection.of {
            CanonRows {
                if (!loaded) {
                    CanonDropZone("Drop an image", paste = false, onFiles = { uris -> uris.firstOrNull()?.let(model::load) })
                } else {
                    CanonHeading("Source")
                    val size = atlas?.byteCount?.let { Formatter.formatFileSize(view.context, it.toLong()) }
                    CanonRow(
                        model.name,
                        detail = when {
                            unreadable -> "Couldn't read metadata"
                            atlas != null && size != null -> summary(atlas, size)
                            else -> " "
                        },
                        detailColour = if (unreadable) c.destructive else null,
                        flush = true,
                    ) {
                        CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                    }
                    if (!unreadable) {
                        CanonButton(
                            "Copy report",
                            kind = CanonButtonKind.Primary,
                            enabled = atlas?.readable == true,
                            icon = Icons.Outlined.ContentCopy,
                        ) { model.report()?.let { CanonCopyFeedback.copy(view, it) } }
                    }
                }
            }
        }

        val main = buildList {
            add(controls)
            if (scan != null && !beside) {
                add(
                    CanonSection.canvas(CanonCanvasSize.Fixed(208.dp), background = scopesPlate, boxing = CanonCanvasBoxing.UnlessWide) {
                        AtlasScopes(scan, model.chroma, atlas?.profile, Modifier.fillMaxSize())
                    },
                )
            }
            if (atlas?.readable == true) {
                add(
                    CanonSection.of {
                        CanonRows {
                            CanonHeading("Colour")
                            val palette = model.palette
                            if (palette.isNotEmpty()) CanonColourStrip(Rgb(0.5, 0.5, 0.5), palette = palette)
                            val rows = model.colourRows()
                            AtlasTiles(rows.map { it.first }, rows.map { it.second })
                        }
                    },
                )
            }
            val section = atlas?.sections?.firstOrNull { it.title == "Location" }
            val coordinate = atlas?.coordinate
            if (section != null || coordinate != null) {
                add(
                    CanonSection.of {
                        CanonRows {
                            CanonHeading("Location")
                            if (coordinate != null) {
                                AtlasLocationMap(coordinate, atlas.heading, model.land)
                                CanonValueLine(
                                    "GPS",
                                    String.format(Locale.ROOT, "%.6f, %.6f", coordinate.first, coordinate.second),
                                    labelWidth = 36.dp,
                                )
                            }
                            AtlasTiles(section?.rows.orEmpty().filter { coordinate == null || it.label !in coordinateLabels })
                        }
                    },
                )
            }
        }

        val info = CanonSection.of {
            CanonRows {
                if (atlas == null || !atlas.readable) {
                    CanonHeading("Details")
                    CanonEmptyRow("None")
                    return@CanonRows
                }
                for (section in atlas.sections) {
                    if (section.title == "Colour" || section.title == "Location") continue
                    if (section.title != "Other") {
                        CanonHeading(section.title)
                        AtlasTiles(section.rows)
                        continue
                    }
                    CanonRow(section.title, detail = "${section.rows.size} fields", flush = true) {
                        CanonButton(
                            if (model.showsOther) "Hide" else "Show",
                            icon = if (model.showsOther) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        ) { model.showsOther = !model.showsOther }
                    }
                    if (model.showsOther) AtlasTiles(section.rows)
                }
                if (atlas.sections.none { it.title in personalSections }) {
                    CanonHeading("Clean")
                    CanonEmptyRow("No EXIF, XMP or IPTC metadata found")
                }
            }
        }

        val banner = CanonSection.canvas(CanonCanvasSize.Fitting, background = c.muted) {
            AtlasPicture(
                model.picture,
                model.pixels,
                transparent = (scan?.transparent ?: 0.0) > 0,
                loading = model.loading,
                scopes = if (beside && scan != null) {
                    { AtlasScopes(scan, model.chroma, atlas?.profile, it) }
                } else {
                    null
                },
            )
        }

        CanonSplit(sidebar = listOf(info), main = main, banner = banner, mainLeadsStacked = true)
    }
}

private val headline = TextStyle(fontFamily = DelphiFont, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
private val captionMono = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp)
private val emptyPixels = IntSize(1600, 1200)
private const val CAPTION_HEIGHT = 16
private const val TALLEST_SHARE = 0.75f
private val tallest = 420.dp
private val scopesHeight = 180.dp

/**
 * dt-ios ImageAtlasPicture: the image to scale with dimension lines for its pixel width and height, checks where it lets
 * the background through, aspect ratio and megapixels under its trailing corner. [scopes] beside a sidebar takes the
 * sidebar's column, so neither half of the banner is empty.
 */
@Composable
private fun AtlasPicture(
    picture: ImageBitmap?,
    pixels: IntSize?,
    transparent: Boolean,
    loading: Boolean,
    scopes: (@Composable (Modifier) -> Unit)?,
) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val top = LocalCanonCanvas.current.top
    val shown = pixels ?: emptyPixels
    BoxWithConstraints(Modifier.fillMaxWidth().background(c.muted)) {
        val full = maxWidth
        val padding = CanonMetrics.panelPadding
        val columnWidth = if (scopes != null) maxWidth - sidebarWidth else maxWidth
        val room = max(1f, (columnWidth - padding * 2 - CanonDimensionLineRoom).value)
        val tallestImage = min(columnWidth.value * TALLEST_SHARE, tallest.value)
        val image = max(96f, min(room * shown.height / shown.width, tallestImage)).dp
        val height = top + padding * 0.5f + CanonDimensionLineRoom + image + 6.dp + CAPTION_HEIGHT.dp + padding
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clipToBounds()
                .semantics { contentDescription = pixels?.let { "${it.width} × ${it.height}" } ?: "Image" },
        ) {
            with(density) {
                val scopesColumn = if (scopes == null) null else {
                    val x = if (rightToLeft) (full - sidebarWidth).toPx() else 0f
                    Rect(x, 0f, x + sidebarWidth.toPx(), height.toPx())
                }
                val columnLeft = if (scopes != null && !rightToLeft) sidebarWidth.toPx() else 0f
                val column = Rect(columnLeft, 0f, columnLeft + columnWidth.toPx(), height.toPx())
                val pad = padding.toPx()
                val area = Rect(
                    column.left + pad,
                    top.toPx() + pad * 0.5f,
                    column.right - pad,
                    column.bottom - pad - (6 + CAPTION_HEIGHT).dp.toPx(),
                )
                val frame = canonDimensionSheet(Size(shown.width.toFloat(), shown.height.toFloat()), area, rightToLeft)
                val hair = CanonMetrics.hairline.toPx()
                val edge = Rect(frame.left - hair, frame.top - hair, frame.right + hair, frame.bottom + hair)
                Box(Modifier.canonPlaced(CanonDimensionPlacement(edge.center, edge.size)).background(c.border))
                val sheet = Modifier.canonPlaced(CanonDimensionPlacement(frame.center, frame.size))
                Box(if (transparent && picture != null) sheet.canonChecks() else sheet.background(c.card))
                if (picture != null) {
                    androidx.compose.foundation.Image(picture, null, sheet, contentScale = ContentScale.FillBounds)
                }
                val (across, down) = canonDimensionLines(frame, rightToLeft)
                CanonDimensionLine(shown.width, Modifier.canonPlaced(across), blank = pixels == null)
                CanonDimensionLine(shown.height, Modifier.canonPlaced(down), blank = pixels == null)
                if (pixels != null) {
                    val caption = Rect(frame.left, frame.bottom + 6.dp.toPx(), frame.right, frame.bottom + (6 + CAPTION_HEIGHT).dp.toPx())
                    Text(
                        imageAtlasCaption(pixels.width, pixels.height),
                        Modifier.canonPlaced(CanonDimensionPlacement(caption.center, caption.size)),
                        style = captionMono,
                        color = c.mutedForeground,
                        maxLines = 1,
                        textAlign = if (rightToLeft) TextAlign.Start else TextAlign.End,
                    )
                } else if (!loading) {
                    Column(
                        Modifier.canonPlaced(CanonDimensionPlacement(frame.center, frame.size)),
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, null, tint = c.mutedForeground, modifier = Modifier.size(34.dp))
                        Text("Image", style = headline, color = c.mutedForeground)
                    }
                }
                if (scopesColumn != null && scopes != null) {
                    val inner = Rect(scopesColumn.left + pad, top.toPx() + pad * 0.5f, scopesColumn.right - pad, scopesColumn.bottom - pad)
                    val tall = max(frame.height, min(scopesHeight.toPx(), inner.height))
                    val y = min(max(frame.center.y - tall / 2, inner.top), inner.bottom - tall)
                    val box = Rect(inner.left, y, inner.right, y + tall)
                    Box(
                        Modifier
                            .canonPlaced(CanonDimensionPlacement(box.center, Size(box.width + hair * 2, box.height + hair * 2)))
                            .background(c.border)
                            .padding(CanonMetrics.hairline),
                    ) { scopes(Modifier.fillMaxSize()) }
                }
                if (loading) {
                    val side = 30.dp.toPx()
                    val x = if (rightToLeft) column.left + pad else column.right - pad - side
                    val at = Offset(x + side / 2, column.bottom - pad - side / 2)
                    CanonLoader(Modifier.canonPlaced(CanonDimensionPlacement(at, Size(side, side))))
                }
            }
        }
    }
}

private val channelInks = listOf(Color(1f, 0.28f, 0.24f, 0.7f), Color(0.3f, 0.92f, 0.36f, 0.7f), Color(0.3f, 0.5f, 1f, 0.7f))
private val scopeInk = Color.White.copy(alpha = 0.28f)
private val scopeLabels = TextStyle(fontFamily = DelphiMono, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.6f))

/**
 * dt-ios ImageColourScopes: the image's colours on the CIE 1931 chromaticity diagram, inside the spectral locus with the
 * sRGB triangle and the image space's own; beside it the red, green and blue histograms, screened together.
 */
@Composable
private fun AtlasScopes(scan: ImageColourScan, chroma: ImageBitmap?, profile: String?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.background(scopesPlate)) {
        val (diagram, histogram) = scopePlots()
        drawDiagram(diagram, scan, chroma, profile, measurer)
        drawHistogram(histogram, scan, measurer)
    }
}

private fun DrawScope.scopePlots(): Pair<Rect, Rect> {
    val w = size.width
    val h = size.height
    fun Rect.inset(dx: Float, dy: Float, bottom: Float = 0f) = Rect(left + dx, top + dy, right - dx, bottom.let { this.bottom - dy - it })
    val label = 12.dp.toPx()
    if (h > w * 0.8f) {
        val side = min(w, h * 0.6f)
        val square = Rect((w - side) / 2, 0f, (w + side) / 2, side)
        val rest = Rect(0f, side, w, h)
        return square.inset(14.dp.toPx(), 12.dp.toPx()) to rest.inset(12.dp.toPx(), 8.dp.toPx(), label)
    }
    val side = min(h, w / 2)
    val flipped = layoutDirection == LayoutDirection.Rtl
    val square = Rect(if (flipped) w - side else 0f, (h - side) / 2, if (flipped) w else side, (h + side) / 2)
    val rest = Rect(if (flipped) 0f else side, 0f, if (flipped) w - side else w, h)
    return square.inset(14.dp.toPx(), 12.dp.toPx()) to rest.inset(12.dp.toPx(), 12.dp.toPx(), label)
}

private fun DrawScope.drawDiagram(square: Rect, scan: ImageColourScan, chroma: ImageBitmap?, profile: String?, measurer: TextMeasurer) {
    val plotW = ImageColourScan.PLOT_WIDTH.toFloat()
    val plotH = ImageColourScan.PLOT_HEIGHT.toFloat()
    val scale = min(square.width / plotW, square.height / plotH)
    val origin = Offset(square.center.x - plotW * scale / 2, square.center.y + plotH * scale / 2)
    val area = Rect(origin.x, origin.y - plotH * scale, origin.x + plotW * scale, origin.y)
    fun point(xy: Pair<Double, Double>) = Offset(origin.x + xy.first.toFloat() * scale, origin.y - xy.second.toFloat() * scale)
    val hair = CanonMetrics.hairline.toPx()

    val locus = Path().apply {
        ImageColourScan.locus.forEachIndexed { i, xy -> point(xy).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        close()
    }
    drawPath(locus, Color.White.copy(alpha = 0.04f))
    if (chroma != null) {
        clipPath(locus) {
            drawImage(
                chroma,
                dstOffset = IntOffset(area.left.roundToInt(), area.top.roundToInt()),
                dstSize = IntSize(area.width.roundToInt(), area.height.roundToInt()),
                filterQuality = FilterQuality.None,
            )
        }
    }
    drawPath(locus, scopeInk, style = Stroke(hair))

    val white = point(ImageColourScan.WHITE)
    ImageColourScan.locus.forEachIndexed { index, xy ->
        val nanometres = ImageColourScan.wavelength(index)
        if (nanometres !in 460..620 || nanometres % 20 != 0) return@forEachIndexed
        val at = point(xy)
        val out = at - white
        val length = max(1f, hypot(out.x, out.y))
        val unit = Offset(out.x / length, out.y / length)
        drawLine(scopeInk, at, at + unit * 4.dp.toPx(), hair)
        if (nanometres % 40 != 20 && nanometres != 500) return@forEachIndexed
        val text = measurer.measure("$nanometres", scopeLabels)
        val tw = text.size.width
        val th = text.size.height
        drawText(text, topLeft = Offset(at.x + unit.x * (6.dp.toPx() + tw / 2f) - tw / 2f, at.y + unit.y * (6.dp.toPx() + th / 2f) - th / 2f))
    }

    val own = scan.primaries
    val isSrgb = own?.let { it.zip(ImageColourScan.SRGB).all { (a, b) -> kotlin.math.abs(a.first - b.first) < 0.004 && kotlin.math.abs(a.second - b.second) < 0.004 } } ?: true
    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
    fun triangle(corners: List<Offset>, colour: Color, dashed: Boolean) {
        val path = Path().apply {
            moveTo(corners[0].x, corners[0].y)
            corners.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, colour, style = Stroke(1.dp.toPx(), pathEffect = if (dashed) dash else null))
    }
    triangle(ImageColourScan.SRGB.map(::point), Color.White.copy(alpha = if (isSrgb) 0.9f else 0.5f), dashed = !isSrgb)
    if (own != null && !isSrgb) triangle(own.map(::point), Color.White.copy(alpha = 0.9f), dashed = false)

    val ring = 3.dp.toPx()
    drawCircle(scopesPlate.copy(alpha = 0.8f), ring, white, style = Stroke(2.5f.dp.toPx()))
    drawCircle(Color.White.copy(alpha = 0.85f), ring, white, style = Stroke(1.dp.toPx()))

    // locus leaves corner empty
    val entries = buildList {
        if (own != null && !isSrgb && profile != null) add(profile to false)
        add("sRGB" to !isSrgb)
    }
    var y = area.top + 2.dp.toPx()
    for ((name, dashed) in entries) {
        val text = measurer.measure(name, scopeLabels)
        val right = square.right
        val sampleY = y + text.size.height / 2f
        val sampleLeft = right - text.size.width - 20.dp.toPx()
        drawLine(
            Color.White.copy(alpha = if (dashed) 0.5f else 0.9f),
            Offset(sampleLeft, sampleY),
            Offset(sampleLeft + 14.dp.toPx(), sampleY),
            1.dp.toPx(),
            pathEffect = if (dashed) dash else null,
        )
        drawText(text, topLeft = Offset(right - text.size.width, y))
        y += text.size.height + 3.dp.toPx()
    }
}

private fun DrawScope.drawHistogram(plot: Rect, scan: ImageColourScan, measurer: TextMeasurer) {
    if (plot.width <= 20.dp.toPx() || plot.height <= 20.dp.toPx()) return
    val hair = CanonMetrics.hairline.toPx()
    for (quarter in 0..4) {
        val x = plot.left + plot.width * quarter / 4
        drawLine(scopeInk, Offset(x, plot.top), Offset(x, plot.bottom), hair)
        val text = measurer.measure("${min(255, quarter * 64)}", scopeLabels)
        val width = text.size.width
        val left = when (quarter) {
            0 -> x
            4 -> x - width
            else -> x - width / 2f
        }
        drawText(text, topLeft = Offset(left, plot.bottom + 2.dp.toPx()))
    }
    drawLine(scopeInk, Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), hair)
    val peak = ImageColourScan.peak(scan.histogram)
    if (peak <= 0) return
    clipRect(plot.left, plot.top, plot.right, plot.bottom) {
        scan.histogram.forEachIndexed { channel, counts ->
            val path = Path().apply {
                moveTo(plot.left, plot.bottom)
                counts.forEachIndexed { bin, count ->
                    lineTo(plot.left + bin / 255f * plot.width, plot.bottom - min(1f, count.toFloat() / peak) * plot.height)
                }
                lineTo(plot.right, plot.bottom)
                close()
            }
            drawPath(path, channelInks[channel], blendMode = BlendMode.Screen)
        }
    }
}

/**
 * dt-ios PhotoLocationMap: the world's land faint, the point lit with lines of latitude and longitude through it,
 * and a wedge the way the camera faced when the file records it.
 */
@Composable
private fun AtlasLocationMap(coordinate: Pair<Double, Double>, heading: Double?, land: List<FloatArray>) {
    val c = LocalDelphiColours.current
    val (latitude, longitude) = coordinate
    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(coordinate) { grown = true }
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(WorldMap.ASPECT)
            .background(c.card)
            .semantics { contentDescription = "Location" }
            .drawWithCache {
                val sx = size.width / 360f
                val sy = size.height / (WorldMap.NORTH - WorldMap.SOUTH).toFloat()
                val path = Path()
                for (ring in land) {
                    path.moveTo(ring[0] * sx, ring[1] * sy)
                    for (i in 1 until ring.size / 2) path.lineTo(ring[i * 2] * sx, ring[i * 2 + 1] * sy)
                    path.close()
                }
                onDrawBehind {
                    val ink = c.foreground
                    for (lon in -150..150 step 30) {
                        val x = WorldMap.point(0.0, lon.toDouble(), size.width, size.height).first
                        drawLine(ink.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, size.height), 1f)
                    }
                    for (lat in -30..60 step 30) {
                        val y = WorldMap.point(lat.toDouble(), 0.0, size.width, size.height).second
                        drawLine(ink.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), 1f)
                    }
                    drawPath(path, ink.copy(alpha = 0.08f))
                    drawPath(path, ink.copy(alpha = 0.22f), style = Stroke(0.5f * density))
                }
            },
    ) {
        val (x, y) = WorldMap.point(latitude, longitude, size.width, size.height)
        val spot = Offset(x, y)
        val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
        val line = c.primary.copy(alpha = 0.55f)
        drawLine(line, Offset(0f, y), Offset(size.width, y), CanonMetrics.hairline.toPx(), pathEffect = dash)
        drawLine(line, Offset(x, 0f), Offset(x, size.height), CanonMetrics.hairline.toPx(), pathEffect = dash)
        if (heading != null && grown) {
            // 0 north, clockwise
            val reach = max(18.dp.toPx(), size.height * 0.16f)
            val centre = Math.toRadians(heading - 90)
            val wedge = Path().apply {
                moveTo(x, y)
                for (step in 0..12) {
                    val angle = centre - 0.35 + 0.7 * step / 12
                    lineTo(x + reach * cos(angle).toFloat(), y + reach * sin(angle).toFloat())
                }
                close()
            }
            drawPath(wedge, c.primary.copy(alpha = 0.3f))
        }
        val radius = 5.dp.toPx()
        drawCircle(c.primary, radius, spot)
        drawCircle(c.card, radius, spot, style = Stroke(2.dp.toPx()))
    }
}
