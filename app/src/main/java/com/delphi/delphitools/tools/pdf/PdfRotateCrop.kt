package com.delphi.delphitools.tools.pdf

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropRotate
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDecimalField
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonPageStrip
import com.delphi.delphitools.canon.CanonPageStripHeight
import com.delphi.delphitools.canon.CanonPageStripPage
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.tools.images.ImageCropCanvas
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private typealias Edit = PdfRotateCropWriter.Edit

class PdfRotateCropModel(application: Application) : AndroidViewModel(application) {
    val pdf = PdfSourceLoader(viewModelScope, application)
    private var file: PdfFile? = null
    val geometry = mutableStateListOf<PdfRotateCropWriter.Geometry>()
    val edits = mutableStateListOf<Edit>()
    var page by mutableIntStateOf(0)
        private set

    /** Per page; edits clear them. */
    val papers = mutableStateMapOf<Int, PaperChoice>()
    val job = PdfJob(viewModelScope, application)

    init {
        pdf.onLoad = { source ->
            viewModelScope.launch {
                val read = withContext(Dispatchers.IO) { runCatching { PdfFile(source.file.readBytes()) }.getOrNull() }
                if (pdf.source !== source || read == null || read.pages.size != source.pageCount) return@launch
                file = read
                geometry.addAll(PdfRotateCropWriter.geometry(read))
                edits.addAll(List(read.pages.size) { Edit() })
                job.reset()
            }
        }
    }

    fun load(uri: Uri) {
        clear()
        pdf.load(uri)
    }

    fun clear() {
        job.reset()
        pdf.clear()
        file = null
        geometry.clear()
        edits.clear()
        papers.clear()
        page = 0
    }

    fun select(index: Int) {
        if (index in edits.indices) page = index
    }

    fun totalRotation(index: Int) = PdfPagesEngine.normaliseRotation(geometry[index].rotation + edits[index].rotation)

    fun displaySize(index: Int) = PdfCropEngine.displaySize(geometry[index].box, totalRotation(index))

    /** Display space, the uncropped whole when there is no crop. */
    fun cropRect(index: Int): PdfRect {
        val size = displaySize(index)
        val whole = PdfRect(0.0, 0.0, size.width, size.height)
        val crop = edits[index].crop ?: return whole
        // paper crops may overflow
        val shown = PdfCropEngine.toDisplay(crop, geometry[index].box, totalRotation(index))
        return PdfCropEngine.intersectBox(shown, whole) ?: whole
    }

    fun setCrop(rect: PdfRect?, index: Int = page, keepsPaper: Boolean = false) {
        if (index !in edits.indices) return
        if (!keepsPaper) papers.remove(index)
        val size = displaySize(index)
        val whole = PdfRect(0.0, 0.0, size.width, size.height)
        val crop = if (rect != null && rect.width >= 4 && rect.height >= 4 && rect.integral() != whole.integral()) {
            PdfCropEngine.toPage(rect, geometry[index].box, totalRotation(index))
        } else {
            null
        }
        edits[index] = edits[index].copy(crop = crop)
        job.reset()
    }

    fun dragged(rect: PdfRect) {
        val before = cropRect(page)
        setCrop(rect, keepsPaper = abs(rect.width - before.width) < 0.5 && abs(rect.height - before.height) < 0.5)
    }

    fun rotate(delta: Int, all: Boolean) {
        for (index in if (all) edits.indices.toList() else listOf(page)) {
            if (index in edits.indices) edits[index] = edits[index].copy(rotation = PdfPagesEngine.normaliseRotation(edits[index].rotation + delta))
        }
        job.reset()
    }

    /** The same fraction of every page. */
    fun cropAllPages() {
        if (page !in edits.indices) return
        val size = displaySize(page)
        if (size.width <= 0 || size.height <= 0) return
        val rect = cropRect(page)
        for (index in edits.indices) {
            if (index == page) continue
            val target = displaySize(index)
            if (target.width <= 0 || target.height <= 0) continue
            if (target == size) papers[page]?.let { papers[index] = it } else papers.remove(index)
            val scaled = PdfRect(
                rect.x / size.width * target.width, rect.y / size.height * target.height,
                rect.width / size.width * target.width, rect.height / size.height * target.height,
            )
            val whole = PdfRect(0.0, 0.0, target.width, target.height)
            edits[index] = edits[index].copy(
                crop = if (scaled.integral() == whole.integral()) null else PdfCropEngine.toPage(scaled, geometry[index].box, totalRotation(index)),
            )
        }
        job.reset()
    }

    /** One margin field changed: [side] is left, top, right, bottom; [mm] its new value. */
    fun margin(side: Int, mm: Double) {
        if (page !in edits.indices) return
        val size = displaySize(page)
        val current = PdfCropEngine.insets(cropRect(page), size)
        val points = mm * PdfPagesEngine.MM_TO_POINTS
        val insets = when (side) {
            0 -> current.copy(left = points)
            1 -> current.copy(top = points)
            2 -> current.copy(right = points)
            else -> current.copy(bottom = points)
        }
        PdfCropEngine.cropFromInsets(size, insets)?.let { setCrop(it) }
    }

    fun pickPaper(choice: PaperChoice) {
        val mm = choice.millimetres ?: return
        if (page !in edits.indices) return
        val size = displaySize(page)
        val points = PdfSize(mm.width * PdfPagesEngine.MM_TO_POINTS, mm.height * PdfPagesEngine.MM_TO_POINTS)
        // paper turns for landscape
        val turned = size.width > size.height && points.width < points.height
        val target = PdfCropEngine.cropToPaper(size, if (turned) PdfSize(points.height, points.width) else points)
        setCrop(PdfCropEngine.intersectBox(target, PdfRect(0.0, 0.0, size.width, size.height)), keepsPaper = true)
        papers[page] = choice
    }

    fun export() {
        val file = file ?: return
        val source = pdf.source ?: return
        val edits = edits.toList()
        job.start("${source.stem}-rotate-crop.pdf") { output, progress ->
            output.writeBytes(PdfRotateCropWriter.apply(file, edits))
            progress(1f)
        }
    }

    override fun onCleared() = clear()
}

@Composable
fun PdfRotateCrop(model: PdfRotateCropModel = viewModel()) {
    val c = LocalDelphiColours.current
    var picking by remember { mutableStateOf(false) }
    val loader = model.pdf
    val loaded = loader.opened
    val page = model.page
    val ready = page in model.edits.indices

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

    val rotate = CanonSection.of {
        CanonRows {
            CanonHeading("Rotate")
            for ((title, all) in listOf("This page" to false, "All pages" to true)) {
                CanonRow(title, flush = true) {
                    CanonGrid {
                        val cell = Modifier.weight(1f).fillMaxHeight()
                        CanonButton("", cell, icon = Icons.AutoMirrored.Outlined.RotateLeft, contentDescription = "Rotate left", enabled = ready) { model.rotate(-90, all) }
                        CanonButton("", cell, icon = Icons.AutoMirrored.Outlined.RotateRight, contentDescription = "Rotate right", enabled = ready) { model.rotate(90, all) }
                    }
                }
            }
        }
    }

    val crop = CanonSection.of {
        CanonRows {
            CanonHeading("Crop margins (mm)")
            val size = if (ready) model.displaySize(page) else PdfSize(0.0, 0.0)
            val insets = if (ready) PdfCropEngine.insets(model.cropRect(page), size) else PdfCropEngine.Insets()
            val values = listOf(insets.left, insets.top, insets.right, insets.bottom)
            val limit = maxOf(size.width, size.height) / PdfPagesEngine.MM_TO_POINTS
            listOf("Left", "Top", "Right", "Bottom").forEachIndexed { side, title ->
                CanonRow(title, flush = true) {
                    CanonDecimalField(
                        values[side] / PdfPagesEngine.MM_TO_POINTS,
                        { model.margin(side, it) },
                        unit = "mm",
                        step = 1.0,
                        decimals = 1,
                        range = 0.0..maxOf(limit, 0.0),
                        enabled = ready,
                    )
                }
            }
            CanonRow("Crop to size", flush = true) {
                CanonButton(
                    model.papers[page]?.title(null) ?: "None",
                    icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    enabled = ready,
                ) { picking = true }
            }
            CanonGrid {
                val cell = Modifier.weight(1f).fillMaxHeight()
                CanonButton("All pages", cell, icon = Icons.Outlined.FilterNone, enabled = ready, onClick = model::cropAllPages)
                CanonButton("Clear", cell, icon = Icons.Outlined.Close, enabled = ready && model.edits[page].crop != null) { model.setCrop(null) }
            }
        }
    }

    val action = CanonSection.of {
        CanonRows {
            PdfJobPanel(model.job, "Export PDF", Icons.Outlined.CropRotate, "Save failed", enabled = ready && loader.source != null, onStart = model::export)
        }
    }

    val pageSize = if (ready) model.displaySize(page) else PdfSize(210.0, 297.0)
    val rotation = if (ready) model.edits[page].rotation else 0
    val source = loader.source
    val picture by produceState<ImageBitmap?>(null, source, page, rotation, ready) {
        value = null
        if (source == null || !ready) return@produceState
        value = withContext(Dispatchers.IO) {
            PdfDocuments.open(source.file) { PdfDocuments.thumbnail(it, page, 1400, rotation)?.asImageBitmap() }
        }
    }
    val cropCanvas = CanonSection.canvas(
        CanonCanvasSize.Aspect(maxOf(0.2, pageSize.width / maxOf(pageSize.height, 1.0)).toFloat()),
        background = c.muted,
        maxHeight = 640.dp,
        boxing = CanonCanvasBoxing.UnlessWide,
    ) {
        if (ready) {
            ImageCropCanvas(
                picture,
                pageSize.compose(),
                model.cropRect(page).compose(),
                onDrag = {},
                onChange = { model.dragged(it.pdf()) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    val pager = CanonSection.of {
        CanonRows {
            CanonGrid {
                val count = model.edits.size
                CanonButton("", Modifier.fillMaxHeight(), icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous page", enabled = page > 0) {
                    model.select(page - 1)
                }
                Box(Modifier.weight(1f).fillMaxHeight().background(c.card), contentAlignment = Alignment.Center) {
                    Text(if (count == 0) "" else "${page + 1} / $count", style = CanonText.bodySemibold, color = c.foreground)
                }
                CanonButton("", Modifier.fillMaxHeight(), icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next page", enabled = page < count - 1) {
                    model.select(page + 1)
                }
            }
        }
    }

    val strip = CanonSection.canvas(CanonCanvasSize.Fixed(CanonPageStripHeight), background = c.muted) {
        CanonPageStrip(
            model.edits.indices.map { index ->
                val full = model.displaySize(index)
                val rect = model.cropRect(index)
                CanonPageStripPage(
                    PdfSize(rect.width, rect.height).compose(),
                    loader.thumbnails[index],
                    if (model.edits[index].crop == null) null else "Cropped",
                    // pdf points, bottom-left origin
                    placement = Rect((-rect.x).toFloat(), (rect.maxY - full.height).toFloat(), (full.width - rect.x).toFloat(), rect.maxY.toFloat()),
                    turn = model.edits[index].rotation,
                )
            },
            selected = if (model.edits.isEmpty()) null else page,
            onSelect = model::select,
        )
    }

    CanonSplit(
        sidebar = listOf(input, rotate, crop, action),
        main = if (loaded) listOf(cropCanvas, pager) else emptyList(),
        banner = strip,
        mainLeadsStacked = true,
    )

    if (picking) PaperSizePicker(null, model.papers[page], onDismiss = { picking = false }, onPick = model::pickPaper)
}

private fun Rect.pdf() = PdfRect(left.toDouble(), top.toDouble(), width.toDouble(), height.toDouble())
