package com.delphi.delphitools.tools.images

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDimensionPlacement
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
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
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonGrabPan
import com.delphi.delphitools.canon.canonPlaced
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.pdf.PdfDocuments
import com.delphi.delphitools.tools.pdf.PdfImageDocument
import com.delphi.delphitools.tools.pdf.PdfJob
import com.delphi.delphitools.tools.pdf.PdfJobPanel
import com.delphi.delphitools.tools.pdf.PdfRect
import com.delphi.delphitools.tools.pdf.PdfSize
import com.delphi.delphitools.tools.pdf.uris
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val SCAN_PIXELS = 3072
private const val THUMBNAIL_PIXELS = 480
private const val SETTLE_MS = 120L

/** dt-ios ScannedPage: the photo, its corners in photo pixels, quarter turns, and the corrected, filtered page. */
class ScanPage(val original: Bitmap) {
    val id: String = UUID.randomUUID().toString()
    var quad by mutableStateOf(ImageDeskewEngine.insetQuad(original.width.toDouble(), original.height.toDouble(), 0.0))
    var turns by mutableIntStateOf(0)
    var page by mutableStateOf<Bitmap?>(null)
    var picture by mutableStateOf<ImageBitmap?>(null)
    var thumbnail by mutableStateOf<ImageBitmap?>(null)
    var rendering = false
    var pending = false

    val pixels get() = page?.let { IntSize(it.width, it.height) } ?: IntSize(original.width, original.height)
}

private fun render(original: Bitmap, quad: List<QuadPoint>, turns: Int, filter: ScanFilter, contrast: Int, threshold: Int): Bitmap {
    val (width, height) = ImageDeskewEngine.target(quad, DeskewAspect.all[0], SCAN_PIXELS.toDouble())
    val warped = warp(original, quad, width, height)
    val turned = if (turns == 0) warped else Bitmap.createBitmap(warped, 0, 0, width, height, Matrix().apply { setRotate(turns * 90f) }, false)
    if (turned !== warped) warped.recycle()
    val pixels = turned.argb()
    val (w, h) = turned.width to turned.height
    turned.recycle()
    return Bitmap.createBitmap(DocumentScanEngine.filter(pixels, filter, contrast, threshold), w, h, Bitmap.Config.ARGB_8888)
        .apply { setHasAlpha(false) }
}

/** dt-ios DocumentScannerViewModel: pages from the camera or from files, each corrected, turned and filtered. */
class DocumentScannerModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<Application>()
    val pages = mutableStateListOf<ScanPage>()
    var selectedId by mutableStateOf<String?>(null)
    var editing by mutableStateOf(false)
    var filter by mutableStateOf(ScanFilter.Colour)
        private set
    var contrast by mutableIntStateOf(0)
        private set
    var threshold by mutableIntStateOf(50)
        private set
    var unreadable by mutableStateOf(false)
        private set
    val job = PdfJob(viewModelScope, application)
    private var generation = 0
    private var settling: Job? = null

    // 150 mb per render
    private val renderer = Dispatchers.Default.limitedParallelism(2)

    val selected get() = pages.firstOrNull { it.id == selectedId }
    val processing get() = pages.any { it.page == null }

    /** A cache file for the camera to write into, under the `media` FileProvider path. */
    fun captureUri(): Uri {
        captures.mkdirs()
        return FileProvider.getUriForFile(app, "${app.packageName}.files", File(captures, "${UUID.randomUUID()}.jpg"))
    }

    private val captures get() = File(File(app.cacheDir, "media"), "document-scanner")

    private fun discardCaptures() {
        val folder = captures
        viewModelScope.launch(Dispatchers.IO) { folder.deleteRecursively() }
    }

    fun add(uris: List<Uri>, at: Int = pages.size) {
        if (uris.isEmpty()) return
        val run = generation
        val resolver = app.contentResolver
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                uris.map { uri -> runCatching { ImageFiles.decode(ImageDecoder.createSource(resolver, uri), SCAN_PIXELS) }.getOrNull() }
            }
            if (run != generation) return@launch
            unreadable = decoded.any { it == null }
            val added = decoded.filterNotNull().map { ScanPage(it.image) }
            if (added.isEmpty()) return@launch
            pages.addAll(min(at, pages.size), added)
            selectedId = added[0].id
            job.reset()
            added.forEach(::process)
        }
    }

    fun move(from: Int, to: Int) {
        if (from !in pages.indices || to !in pages.indices) return
        pages.add(to, pages.removeAt(from))
        job.reset()
    }

    fun moveToEnd(id: String, toStart: Boolean) {
        val from = pages.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        move(from, if (toStart) 0 else pages.size - 1)
    }

    fun remove(id: String) {
        pages.removeAll { it.id == id }
        if (selectedId == id) selectedId = pages.firstOrNull()?.id
        if (pages.isEmpty()) editing = false
        job.reset()
    }

    fun clearAll() {
        generation += 1
        pages.clear()
        selectedId = null
        editing = false
        unreadable = false
        job.reset()
        discardCaptures()
    }

    fun rotate(clockwise: Boolean) {
        val page = selected ?: return
        page.turns = (page.turns + if (clockwise) 1 else 3) % 4
        process(page)
    }

    fun moveCorners(quad: List<QuadPoint>) {
        val page = selected ?: return
        page.quad = quad
        if (ImageDeskewEngine.isConvex(quad)) process(page)
    }

    fun resetCorners() {
        val page = selected ?: return
        moveCorners(ImageDeskewEngine.insetQuad(page.original.width.toDouble(), page.original.height.toDouble(), 0.0))
    }

    fun choose(filter: ScanFilter) {
        this.filter = filter
        processAll()
    }

    fun changeContrast(value: Int) {
        if (value == contrast) return
        contrast = value
        processAll(SETTLE_MS)
    }

    fun changeThreshold(value: Int) {
        if (value == threshold) return
        threshold = value
        processAll(SETTLE_MS)
    }

    private fun processAll(delayMs: Long = 0) {
        settling?.cancel()
        settling = viewModelScope.launch {
            delay(delayMs)
            pages.forEach(::process)
        }
    }

    // last request wins
    private fun process(page: ScanPage) {
        job.reset()
        if (page.rendering) {
            page.pending = true
            return
        }
        page.rendering = true
        page.pending = false
        val filter = filter
        val contrast = contrast
        val threshold = threshold
        val quad = page.quad
        val turns = page.turns
        viewModelScope.launch {
            val done = withContext(renderer) {
                runCatching {
                    val image = render(page.original, quad, turns, filter, contrast, threshold)
                    Triple(image, image.asImageBitmap(), ImageCompressEngine.preview(image, THUMBNAIL_PIXELS).asImageBitmap())
                }.getOrNull()
            }
            page.rendering = false
            if (page.pending) return@launch process(page)
            if (done != null) {
                page.page = done.first
                page.picture = done.second
                page.thumbnail = done.third
            }
        }
    }

    fun exportImage(context: Context, id: String) {
        val index = pages.indexOfFirst { it.id == id }
        val image = pages.getOrNull(index)?.page ?: return
        viewModelScope.launch {
            val data = withContext(Dispatchers.Default) { ImageFiles.png(image) } ?: return@launch
            FileExport.share(context, data, "scan-page-${index + 1}.png", "image/png")
        }
    }

    fun exportPdf() {
        val images = pages.mapNotNull { it.page }
        if (images.isEmpty() || images.size != pages.size) return
        job.start("scan.pdf") { output, progress ->
            output.outputStream().buffered().use { out ->
                val document = PdfImageDocument(out)
                images.forEachIndexed { index, image ->
                    kotlin.coroutines.coroutineContext.ensureActive()
                    val (width, height) = DocumentScanEngine.pageSize(image.width, image.height)
                    val picture = PdfDocuments.picture(image, 80) ?: error("encode")
                    document.add(PdfSize(width, height), PdfRect(0.0, 0.0, width, height), picture)
                    progress((index + 1f) / images.size)
                }
                document.finish()
            }
        }
    }

    override fun onCleared() {
        job.reset()
        captures.deleteRecursively()
    }
}

@Composable
fun DocumentScanner(model: DocumentScannerModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val pages = model.pages
    val has = pages.isNotEmpty()
    val page = model.selected
    val index = pages.indexOfFirst { it.id == model.selectedId }
    val busy = model.job.busy
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var capture by rememberSaveable { mutableStateOf<Uri?>(null) }

    // ponytail: no live edge detection; dt-ios uses VisionKit's VNDocumentCameraViewController, here the corners are set by hand
    lateinit var launchCamera: () -> Unit
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = capture
        if (taken && uri != null) {
            model.add(listOf(uri))
            // reopens as visionkit does
            launchCamera()
        }
    }
    val openCamera = {
        val uri = model.captureUri()
        capture = uri
        try {
            camera.launch(uri)
        } catch (_: ActivityNotFoundException) {
            capture = null
        }
    }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) openCamera()
    }
    // declared camera needs grant
    launchCamera = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
        else ask.launch(Manifest.permission.CAMERA)
    }

    val picture = CanonSection.canvas(CanonCanvasSize.Aspect(0.8f), background = c.muted, showsPattern = true) {
        if (page != null && model.editing) {
            CornerEditor(page, model::moveCorners)
        } else {
            ScanPicture(page?.picture, page?.pixels)
        }
    }

    val strip = CanonSection.canvas(CanonCanvasSize.Fixed(CanonPageStripHeight), background = c.muted) {
        CanonPageStrip(
            pages.map { item ->
                val (width, height) = DocumentScanEngine.pageSize(item.pixels.width, item.pixels.height)
                CanonPageStripPage(Size(width.toFloat(), height.toFloat()), item.thumbnail, if (item.page == null) "…" else null)
            },
            selected = index.takeIf { it >= 0 },
            onSelect = { pages.getOrNull(it)?.let { item -> model.selectedId = item.id } },
            reorders = !busy,
            onMove = model::move,
            onDrop = { at, clip -> model.add(clip.uris(), at) },
            menu = { at ->
                val id = pages.getOrNull(at)?.id
                if (busy || id == null) emptyList() else listOf(
                    "Export Image" to { model.exportImage(context, id) },
                    "Move to start" to { model.moveToEnd(id, true) },
                    "Move to end" to { model.moveToEnd(id, false) },
                    "Remove" to { model.remove(id) },
                )
            },
        )
    }

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            CanonButton("Scan", kind = CanonButtonKind.Primary, enabled = hasCamera && !busy, icon = Icons.Outlined.DocumentScanner, oversized = true) {
                launchCamera()
            }
            if (!hasCamera) CanonStatusRow("No camera", CanonTone.Muted)
            if (denied) CanonStatusRow("Camera access denied", CanonTone.Warn)
            CanonDropZone(
                "Drop images here",
                icon = Icons.Outlined.FileCopy,
                photos = PickVisualMedia.ImageOnly,
                multiple = true,
                paste = false,
                onFiles = { if (!busy) model.add(it) },
            )
            if (model.unreadable) CanonStatusRow("That file can't be read as an image", CanonTone.Warn)
            if (has) {
                CanonGrid {
                    CanonButton("Clear", Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.Delete, enabled = !busy, onClick = model::clearAll)
                }
            }
        }
    }

    val pageSection = CanonSection.of {
        CanonRows {
            CanonHeading(if (index >= 0) "Page ${index + 1}" else " ")
            CanonGrid {
                val cell = Modifier.weight(1f).fillMaxHeight()
                val enabled = page != null && !busy
                CanonButton("Rotate left", cell, enabled = enabled, icon = Icons.AutoMirrored.Outlined.RotateLeft) { model.rotate(false) }
                CanonButton("Rotate right", cell, enabled = enabled, icon = Icons.AutoMirrored.Outlined.RotateRight) { model.rotate(true) }
            }
            CanonRow("Corners", flush = true) {
                CanonGrid {
                    val cell = Modifier.weight(1f).fillMaxHeight()
                    CanonButton(
                        if (model.editing) "Done" else "Edit",
                        cell,
                        selected = model.editing,
                        enabled = page != null && !busy,
                        icon = if (model.editing) Icons.Outlined.Check else Icons.Outlined.Crop,
                    ) { model.editing = !model.editing }
                    CanonButton("Reset", cell, enabled = page != null && !busy, icon = Icons.Rounded.RestartAlt, onClick = model::resetCorners)
                }
            }
            if (page != null && !ImageDeskewEngine.isConvex(page.quad)) CanonStatusRow("Don't let the streams cross!", CanonTone.Warn)
            CanonTiles(listOf(CanonTile("Size", page?.let { "${it.pixels.width} × ${it.pixels.height} px" } ?: "—")))
        }
    }

    val filters = CanonSection.of {
        CanonRows {
            CanonHeading("Filter")
            CanonSegmented(ScanFilter.entries.map { it.label }, model.filter.ordinal, enabled = !busy) { model.choose(ScanFilter.entries[it]) }
            CanonRow("Contrast", flush = true) {
                CanonSlider(model.contrast.toFloat(), { model.changeContrast(it.roundToInt()) }, Modifier.fillMaxSize(), range = 0f..100f, step = 1f, readout = "${model.contrast} %")
            }
            if (model.filter == ScanFilter.BlackWhite) {
                CanonRow("Threshold", flush = true) {
                    CanonSlider(model.threshold.toFloat(), { model.changeThreshold(it.roundToInt()) }, Modifier.fillMaxSize(), range = 1f..99f, step = 1f, readout = "${model.threshold} %")
                }
            }
        }
    }

    val pdf = CanonSection.of {
        CanonRows {
            CanonHeading("PDF")
            PdfJobPanel(model.job, "Export PDF", Icons.Outlined.PictureAsPdf, "Build failed", enabled = has && !model.processing, onStart = model::exportPdf)
        }
    }

    val main = if (has) listOf(strip, input, pageSection, filters, pdf) else listOf(strip, input)
    CanonSplit(sidebar = listOf(picture), main = main, halves = true, collapsesTo = 200.dp)
}

private fun fitted(pixels: IntSize, area: Rect): Rect {
    if (area.width <= 0 || area.height <= 0 || pixels.width <= 0 || pixels.height <= 0) return Rect.Zero
    val scale = min(area.width / pixels.width, area.height / pixels.height)
    val size = Size(pixels.width * scale, pixels.height * scale)
    return Rect(Offset(area.center.x - size.width / 2, area.center.y - size.height / 2), size)
}

// dt-ios ScannedPagePicture
@Composable
private fun ScanPicture(image: ImageBitmap?, pixels: IntSize?) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val top = LocalCanonCanvas.current.top
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val frame = with(density) {
            val padding = CanonMetrics.panelPadding.toPx()
            fitted(pixels ?: IntSize(210, 297), Rect(padding, top.toPx() + padding, constraints.maxWidth - padding, constraints.maxHeight - padding))
        }
        Canvas(Modifier.fillMaxSize()) {
            if (image != null) {
                drawImage(image, dstOffset = IntOffset(frame.left.roundToInt(), frame.top.roundToInt()), dstSize = IntSize(frame.width.roundToInt(), frame.height.roundToInt()))
                drawRect(c.border, frame.topLeft, frame.size, style = Stroke(CanonMetrics.hairline.toPx()))
            } else {
                val inset = CanonMetrics.frame.toPx() / 2
                drawRect(
                    c.mutedForeground.copy(alpha = 0.5f),
                    Offset(frame.left + inset, frame.top + inset),
                    Size(frame.width - inset * 2, frame.height - inset * 2),
                    style = Stroke(CanonMetrics.frame.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            }
        }
        if (image == null && pixels == null) {
            val side = 44.dp
            Icon(
                Icons.Outlined.DocumentScanner,
                null,
                tint = c.mutedForeground.copy(alpha = 0.5f),
                modifier = Modifier.canonPlaced(CanonDimensionPlacement(frame.center, with(density) { Size(side.toPx(), side.toPx()) })),
            )
        }
    }
}

/**
 * The photo with everything outside the quad shaded, the quad's outline and corner handles; a corner, an edge or
 * the whole quad drags, as the Image Deskewer's board.
 */
@Composable
private fun CornerEditor(page: ScanPage, onMove: (List<QuadPoint>) -> Unit) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val top = LocalCanonCanvas.current.top
    val image = remember(page) { page.original.asImageBitmap() }
    val pixels = IntSize(page.original.width, page.original.height)
    val quad = page.quad
    val currentQuad by rememberUpdatedState(quad)
    var grab by remember { mutableStateOf<DeskewGrab?>(null) }
    var start by remember { mutableStateOf(emptyList<QuadPoint>()) }
    var from by remember { mutableStateOf(QuadPoint(0.0, 0.0)) }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val frame = with(density) {
            val padding = CanonMetrics.panelPadding.toPx()
            fitted(pixels, Rect(padding, top.toPx() + padding, constraints.maxWidth - padding, constraints.maxHeight - padding))
        }
        val shown = quad.map { Offset(frame.left + (it.x / pixels.width).toFloat() * frame.width, frame.top + (it.y / pixels.height).toFloat() * frame.height) }
        val shownNow by rememberUpdatedState(shown)
        fun imagePixels(point: Offset) = QuadPoint(
            ((point.x - frame.left) / max(frame.width, 1f) * pixels.width).toDouble(),
            ((point.y - frame.top) / max(frame.height, 1f) * pixels.height).toDouble(),
        )
        fun grabAt(point: Offset) =
            DeskewGrab.at(QuadPoint(point.x.toDouble(), point.y.toDouble()), shownNow.map { QuadPoint(it.x.toDouble(), it.y.toDouble()) }, density.density.toDouble())
        val ink = if (ImageDeskewEngine.isConvex(quad)) c.primary else c.destructive
        Canvas(
            Modifier
                .fillMaxSize()
                .canonGrabPan(
                    grabs = { grabAt(it) != null },
                    onStart = { point ->
                        grab = grabAt(point)
                        start = currentQuad
                        from = imagePixels(point)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    },
                    onEnd = { grab = null },
                ) { change, _ ->
                    val held = grab ?: return@canonGrabPan
                    val now = imagePixels(change.position)
                    onMove(DeskewGrab.move(start, held, now.x - from.x, now.y - from.y, pixels.width.toDouble(), pixels.height.toDouble()))
                },
        ) {
            if (shown.size != 4) return@Canvas
            drawImage(image, dstOffset = IntOffset(frame.left.roundToInt(), frame.top.roundToInt()), dstSize = IntSize(frame.width.roundToInt(), frame.height.roundToInt()))
            val polygon = Path().apply {
                moveTo(shown[0].x, shown[0].y)
                shown.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(Path().apply { fillType = PathFillType.EvenOdd; addRect(frame); addPath(polygon) }, Color.Black.copy(alpha = 0.5f))
            drawPath(polygon, ink, style = Stroke(CanonMetrics.frame.toPx(), join = StrokeJoin.Miter))
            val handle = 7.dp.toPx()
            for (corner in shown) {
                drawCircle(Color.White, handle, corner)
                drawCircle(ink, handle, corner, style = Stroke(CanonMetrics.frame.toPx()))
            }
        }
    }
}
