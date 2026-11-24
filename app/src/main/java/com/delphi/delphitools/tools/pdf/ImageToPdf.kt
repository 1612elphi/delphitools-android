package com.delphi.delphitools.tools.pdf

import android.app.Application
import android.content.ClipData
import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import com.delphi.delphitools.canon.CanonPageStrip
import com.delphi.delphitools.canon.CanonPageStripHeight
import com.delphi.delphitools.canon.CanonPageStripPage
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.tools.images.ImageFiles
import com.delphi.delphitools.tools.print.PaperPick
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val margins = listOf(0.0, 5.0, 10.0, 20.0)
private val scales = listOf(1.0, 2.0, 3.0)
private const val MATCH = "Match image"

// ponytail: images decoded at most 4096 px on the long edge (about 350 DPI on A4); raise it if posters need more
private const val MAX_EDGE = 4096

/** dt-ios ImageToPDFItem: an image copied into the cache, its upright pixel size and a thumbnail. */
class ImageToPdfItem(val file: File, val name: String, val pixels: PdfSize, val thumbnail: ImageBitmap) {
    val id: String = UUID.randomUUID().toString()

    companion object {
        suspend fun load(context: Context, uri: Uri): ImageToPdfItem? {
            val (file, name) = PdfDocuments.copy(context, uri, "image") ?: return null
            return withContext(Dispatchers.IO) {
                val decoded = ImageFiles.decode(ImageDecoder.createSource(file), 480)
                if (decoded == null) file.delete()
                decoded?.let { ImageToPdfItem(file, name, PdfSize(it.width.toDouble(), it.height.toDouble()), it.image.asImageBitmap()) }
            }
        }
    }
}

class ImageToPdfModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<Application>()
    var toImage by mutableStateOf(false)
        private set
    var paper by mutableStateOf<PaperChoice>(PaperPick.named("A4")?.let(PaperChoice::Paper) ?: PaperChoice.Extra)
        private set
    var orientation by mutableIntStateOf(0)
        private set
    var fit by mutableIntStateOf(0)
        private set
    var margin by mutableIntStateOf(0)
        private set
    var resolution by mutableIntStateOf(1)
    val items = mutableStateListOf<ImageToPdfItem>()
    val opened = PdfSourceLoader(viewModelScope, application)
    val job = PdfJob(viewModelScope, application)
    var rendering by mutableStateOf(false)
        private set
    var renderFailed by mutableStateOf(false)
        private set

    val options
        get() = ImageToPdfEngine.Options(
            paperMm = paper.millimetres,
            orientation = PdfPagesEngine.PageOrientation.entries[orientation],
            fit = PdfPagesEngine.FitMode.entries[fit],
            marginMm = margins[margin],
        )

    fun direction(toImage: Boolean) {
        this.toImage = toImage
        job.reset()
    }

    fun pick(choice: PaperChoice) {
        paper = choice
        job.reset()
    }

    fun orientation(index: Int) {
        orientation = index
        job.reset()
    }

    fun fit(index: Int) {
        fit = index
        job.reset()
    }

    fun margin(index: Int) {
        margin = index
        job.reset()
    }

    fun add(uris: List<Uri>, at: Int) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val loaded = uris.mapNotNull { ImageToPdfItem.load(app, it) }
            items.addAll(minOf(at, items.size), loaded)
            job.reset()
        }
    }

    fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        job.reset()
    }

    fun moveToEnd(id: String, toStart: Boolean) {
        val from = items.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        val item = items.removeAt(from)
        items.add(if (toStart) 0 else items.size, item)
        job.reset()
    }

    fun remove(id: String) {
        items.removeAll { (it.id == id).also { gone -> if (gone) it.file.delete() } }
        job.reset()
    }

    fun clearImages() {
        items.forEach { it.file.delete() }
        items.clear()
        job.reset()
    }

    fun sort(ascending: Boolean) {
        val sorted = ImageToPdfEngine.sortedByName(items.toList(), ascending) { it.name }
        items.clear()
        items.addAll(sorted)
        job.reset()
    }

    fun buildPdf() {
        val files = items.map { it.file to it.pixels }
        if (files.isEmpty()) return
        val options = options
        job.start("images.pdf") { output, progress ->
            output.outputStream().buffered().use { out ->
                val document = PdfImageDocument(out)
                files.forEachIndexed { index, (file, pixels) ->
                    kotlin.coroutines.coroutineContext.ensureActive()
                    val decoded = ImageFiles.decode(ImageDecoder.createSource(file), MAX_EDGE) ?: error("decode")
                    val layout = ImageToPdfEngine.layout(pixels, options) ?: error("layout")
                    val picture = PdfDocuments.picture(decoded.image, 90) ?: error("encode")
                    decoded.image.recycle()
                    document.add(layout.page, layout.placement, picture, clip = layout.area)
                    progress((index + 1f) / files.size)
                }
                document.finish()
            }
        }
    }

    fun loadPdf(uri: Uri) {
        renderFailed = false
        opened.load(uri)
    }

    fun clearPdf() {
        opened.clear()
        renderFailed = false
    }

    /** dt-ios exportPNGs: [pages] rendered at the chosen resolution and handed to the share sheet. */
    fun exportPngs(context: Context, pages: List<Int>) {
        val source = opened.source ?: return
        if (rendering || pages.isEmpty()) return
        val scale = scales[resolution]
        rendering = true
        renderFailed = false
        viewModelScope.launch {
            // ponytail: every PNG held in memory until shared; write them straight to the export folder if long PDFs run out
            val files = withContext(Dispatchers.IO) {
                PdfDocuments.open(source.file) { renderer ->
                    pages.map { index ->
                        val bitmap = PdfDocuments.render(renderer, index, scale) ?: return@open null
                        val png = ImageFiles.png(bitmap) ?: return@open null
                        bitmap.recycle()
                        "${source.stem}-page-${index + 1}.png" to png
                    }
                }
            }
            rendering = false
            renderFailed = files == null
            if (files != null) ImageFiles.shareAll(context, files, "image/png")
        }
    }

    override fun onCleared() {
        items.forEach { it.file.delete() }
        opened.clear()
        job.reset()
    }
}

@Composable
fun ImageToPdf(model: ImageToPdfModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val busy = model.job.busy
    val toImage = model.toImage
    val matches = model.paper.millimetres == null

    val mode = CanonSection.of {
        CanonRows {
            CanonHeading("Direction")
            CanonSegmented(listOf("Image to PDF", "PDF to Image"), if (toImage) 1 else 0, enabled = !busy) { model.direction(it == 1) }
        }
    }

    val input = CanonSection.of {
        CanonRows {
            if (!toImage) {
                CanonHeading("Images")
                CanonDropZone(
                    "Drop images here",
                    icon = Icons.Outlined.Collections,
                    photos = PickVisualMedia.ImageOnly,
                    multiple = true,
                    paste = false,
                    onFiles = { if (!busy) model.add(it, model.items.size) },
                )
                if (model.items.isNotEmpty()) {
                    CanonGrid {
                        val cell = Modifier.weight(1f).fillMaxHeight()
                        CanonButton("A–Z", cell, icon = Icons.Outlined.ArrowDownward, enabled = !busy) { model.sort(true) }
                        CanonButton("Z–A", cell, icon = Icons.Outlined.ArrowUpward, enabled = !busy) { model.sort(false) }
                        CanonButton("Clear", cell, icon = Icons.Outlined.Delete, enabled = !busy, onClick = model::clearImages)
                    }
                }
            } else {
                CanonHeading("Source")
                if (!model.opened.opened) {
                    CanonDropZone(
                        "Drop PDF here",
                        icon = Icons.Outlined.Description,
                        mimeTypes = arrayOf(PdfDocuments.MIME),
                        photos = null,
                        paste = false,
                        onFiles = { uris -> uris.firstOrNull()?.let(model::loadPdf) },
                    )
                } else {
                    PdfFileRow(model.opened.source, model.opened.name, model.opened.failed, model::clearPdf)
                }
            }
        }
    }

    val paper = CanonSection.of {
        CanonRows {
            CanonHeading("Page size")
            CanonButton(model.paper.title(MATCH), Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight, enabled = !busy) {
                picking = true
            }
            CanonHeading("Orientation")
            CanonSegmented(listOf("Auto", "Portrait", "Landscape"), model.orientation, enabled = !matches && !busy, onSelect = model::orientation)
            CanonHeading("Fit")
            CanonSegmented(listOf("Fit", "Fill", "Stretch"), model.fit, enabled = !matches && !busy, onSelect = model::fit)
            CanonHeading("Margin")
            CanonSegmented(margins.map { if (it == 0.0) "None" else "${it.toInt()} mm" }, model.margin, enabled = !busy, onSelect = model::margin)
        }
    }

    val export = CanonSection.of {
        CanonRows {
            if (!toImage) {
                PdfJobPanel(model.job, "Export PDF", Icons.Outlined.PictureAsPdf, "Build failed", enabled = model.items.isNotEmpty(), onStart = model::buildPdf)
            } else {
                CanonHeading("Resolution")
                CanonSegmented(scales.map { "${(it * 72).toInt()} DPI" }, model.resolution) { model.resolution = it }
                CanonButton(
                    "Export All",
                    kind = CanonButtonKind.Primary,
                    enabled = model.opened.source != null && !model.rendering,
                    icon = Icons.Outlined.Share,
                    oversized = true,
                ) { model.exportPngs(context, (0 until (model.opened.source?.pageCount ?: 0)).toList()) }
                if (model.rendering) {
                    CanonRow("Processing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground) }
                }
                if (model.renderFailed) CanonStatusRow("Render failed", CanonTone.Warn)
            }
        }
    }

    val options = model.options
    val pages = if (!toImage) {
        model.items.mapNotNull { item ->
            ImageToPdfEngine.layout(item.pixels, options)?.let {
                CanonPageStripPage(it.page.compose(), item.thumbnail, item.name, it.area.compose(), it.placement.compose())
            }
        }
    } else {
        model.opened.source?.sizes?.mapIndexed { index, size -> CanonPageStripPage(size.compose(), model.opened.thumbnails[index]) }.orEmpty()
    }
    val strip = CanonSection.canvas(CanonCanvasSize.Fixed(CanonPageStripHeight), background = c.muted) {
        CanonPageStrip(
            pages,
            reorders = !toImage && !busy,
            onMove = model::move,
            onDrop = { index, clip -> model.add(clip.uris(), index) },
            menu = { index ->
                when {
                    busy -> emptyList()
                    !toImage -> model.items.getOrNull(index)?.id?.let { id ->
                        listOf(
                            "Move to start" to { model.moveToEnd(id, true) },
                            "Move to end" to { model.moveToEnd(id, false) },
                            "Remove" to { model.remove(id) },
                        )
                    }.orEmpty()
                    else -> listOf("Share" to { model.exportPngs(context, listOf(index)) })
                }
            },
        )
    }

    CanonSplit(
        sidebar = listOfNotNull(paper.takeIf { !toImage }, export),
        main = listOf(mode, input),
        banner = strip,
        mainLeadsStacked = true,
    )

    if (picking) PaperSizePicker(MATCH, model.paper, onDismiss = { picking = false }, onPick = model::pick)
}

internal fun ClipData.uris(): List<Uri> = (0 until itemCount).mapNotNull { getItemAt(it).uri }

internal fun PdfSize.compose() = Size(width.toFloat(), height.toFloat())

internal fun PdfRect.compose() = Rect(x.toFloat(), y.toFloat(), maxX.toFloat(), maxY.toFloat())
