package com.delphi.delphitools.tools.print

import android.app.Application
import android.content.ClipboardManager
import android.content.ClipDescription
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
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
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.tools.images.ImageCompressEngine
import com.delphi.delphitools.tools.images.ImageFiles
import com.delphi.delphitools.tools.pdf.PaperChoice
import com.delphi.delphitools.tools.pdf.PaperSizePicker
import com.delphi.delphitools.tools.pdf.PdfDocuments
import com.delphi.delphitools.tools.pdf.PdfJob
import com.delphi.delphitools.tools.pdf.PdfJobPanel
import com.delphi.delphitools.tools.pdf.PdfSize
import com.delphi.delphitools.tools.pdf.compose
import com.delphi.delphitools.tools.pdf.uris
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

private val dpis = listOf(72, 150, 300, 600)
private const val MAX_EDGE = 3072
private const val THUMBNAIL_EDGE = 600
private const val JPEG_QUALITY = 90

/** A slot's image, its thumbnail, and whether it fills its cell. */
class ZineImage(val image: Bitmap, val thumbnail: ImageBitmap, val fill: Boolean = false, val id: UUID = UUID.randomUUID()) {
    fun filling(fill: Boolean) = ZineImage(image, thumbnail, fill, id)
}

class ZineImposerModel(application: Application) : AndroidViewModel(application) {
    val job = PdfJob(viewModelScope, application)
    var fold by mutableStateOf(ZineFold.MiniEight)
        private set
    var panels by mutableIntStateOf(8)
        private set
    var doubleSided by mutableStateOf(false)
        private set
    var split by mutableStateOf(false)
        private set
    val images = mutableStateListOf<ZineImage?>().apply { addAll(List(8) { null }) }
    var paper by mutableStateOf<PaperChoice>(PaperChoice.Paper(PaperPick.named("A4")!!))
        private set
    var dpi by mutableIntStateOf(300)

    /** 1-based page. */
    var lit by mutableStateOf<Int?>(null)
    var showsBack by mutableStateOf(false)
    private val clipboard = application.getSystemService(ClipboardManager::class.java)

    val layout get() = ZineImposerEngine.build(fold, panels, doubleSided, split)
    private val paperMm get() = paper.millimetres!!
    val sheetWidthMm get() = maxOf(paperMm.width, paperMm.height)
    val sheetHeightMm get() = minOf(paperMm.width, paperMm.height)
    val cellWidthMm get() = sheetWidthMm / layout.cols
    val cellHeightMm get() = sheetHeightMm / layout.rows
    val cellWidthPx get() = (cellWidthMm / 25.4 * dpi).toInt()
    val cellHeightPx get() = (cellHeightMm / 25.4 * dpi).toInt()
    val hasAnyImage get() = images.any { it != null }

    private fun change(body: () -> Unit) {
        body()
        job.reset()
    }

    private fun reconfigure() {
        val target = layout.pageCount
        while (images.size > target) images.removeAt(images.size - 1)
        while (images.size < target) images.add(null)
        if (layout.sides.size < 2) showsBack = false
        lit?.let { if (it > target) lit = null }
    }

    fun fold(value: ZineFold) = change {
        fold = value
        reconfigure()
    }

    fun panels(value: Int) = change {
        panels = value
        reconfigure()
    }

    fun doubleSided(value: Boolean) = change {
        doubleSided = value
        reconfigure()
    }

    fun split(value: Boolean) = change {
        split = value
        reconfigure()
    }

    fun pickPaper(choice: PaperChoice) = change { if (choice !is PaperChoice.Extra) paper = choice }

    fun clearAll() = change {
        for (i in images.indices) images[i] = null
    }

    fun remove(index: Int) = change { if (index in images.indices) images[index] = null }

    fun fit(index: Int, fill: Boolean) = change { images.getOrNull(index)?.let { images[index] = it.filling(fill) } }

    fun move(from: Int, to: Int) {
        change { images.add(to, images.removeAt(from)) }
        lit = to + 1
    }

    fun light(page: Int) {
        lit = page
        layout.sides.indexOfFirst { side -> side.placements.any { it.pageNumber == page } }.takeIf { it >= 0 }?.let { showsBack = it == 1 }
    }

    fun flip() {
        if (layout.sides.size > 1) showsBack = !showsBack
    }

    /** Decoded images go into the empty slots from [start] on, wrapping round. */
    fun add(uris: List<Uri>, start: Int) {
        if (uris.isEmpty()) return
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val decoded = ImageFiles.decode(ImageDecoder.createSource(resolver, uri), MAX_EDGE) ?: return@mapNotNull null
                    val image = decoded.image
                    ZineImage(image, ImageCompressEngine.preview(image, THUMBNAIL_EDGE).asImageBitmap())
                }
            }
            val slots = ZineImposerEngine.fillOrder(images.map { it != null }, start)
            change { for ((image, slot) in prepared.zip(slots)) images[slot] = image }
        }
    }

    fun replace(slot: Int, uri: Uri) {
        if (slot !in images.indices) return
        remove(slot)
        add(listOf(uri), slot)
    }

    fun clipboardHasImage() = clipboard?.primaryClipDescription?.let { d ->
        (0 until d.mimeTypeCount).any { ClipDescription.compareMimeTypes(d.getMimeType(it), "image/*") }
    } == true

    fun paste(slot: Int) {
        clipboard?.primaryClip?.uris()?.firstOrNull()?.let { replace(slot, it) }
    }

    fun export() {
        if (!hasAnyImage) return
        val layout = layout
        val shown = images.toList()
        val (width, height) = sheetWidthMm to sheetHeightMm
        val name = "zine-${if (fold == ZineFold.Accordion) "accordion" else "mini8"}${if (layout.sides.size > 1) "-duplex" else ""}.pdf"
        job.start(name) { output, progress ->
            val context = coroutineContext
            val slots = shown.mapIndexed { index, image ->
                context.ensureActive()
                progress(index.toFloat() / shown.size)
                image?.let { ZineImposerEngine.Slot(PdfDocuments.picture(it.image, JPEG_QUALITY) ?: error("encode"), it.fill) }
            }
            output.outputStream().buffered().use { ZineImposerEngine.write(layout, slots, width, height, it) }
            progress(1f)
        }
    }
}

private fun millimetres(width: Double, height: Double) =
    "${PaperUnit.decimal((width * 10).roundToInt() / 10.0)} × ${PaperUnit.decimal((height * 10).roundToInt() / 10.0)} mm"

private fun placement(image: Size, cell: Size, fills: Boolean): Rect {
    val aspect = image.width / maxOf(image.height, 1f)
    val byWidth = (aspect > cell.width / maxOf(cell.height, 1f)) != fills
    val size = if (byWidth) Size(cell.width, cell.width / aspect) else Size(cell.height * aspect, cell.height)
    return Rect(Offset((cell.width - size.width) / 2, (cell.height - size.height) / 2), size)
}

@Composable
fun ZineImposer(model: ZineImposerModel = viewModel()) {
    val c = LocalDelphiColours.current
    var picking by remember { mutableStateOf(false) }
    var photoSlot by remember { mutableStateOf<Int?>(null) }
    val photo = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        val slot = photoSlot
        photoSlot = null
        if (uri != null && slot != null) model.replace(slot, uri)
    }
    val layout = model.layout
    val duplex = layout.sides.size > 1
    val fold = model.fold

    val foldRows = CanonSection.of {
        CanonRows {
            CanonHeading("Fold Type")
            CanonSegmented(ZineFold.entries.map { it.title }, fold.ordinal) { model.fold(ZineFold.entries[it]) }
            CanonStatusRow(fold.tagline, CanonTone.Muted)
            if (fold.configurable) {
                CanonHeading("Panels")
                CanonSegmented(accordionPanelOptions.map { "$it" }, accordionPanelOptions.indexOf(model.panels).takeIf { it >= 0 } ?: 2) {
                    model.panels(accordionPanelOptions[it])
                }
                CanonRow("Double-sided") { CanonSwitch(model.doubleSided, onCheckedChange = model::doubleSided) }
                CanonRow("Split in half (two-up)") { CanonSwitch(model.split, onCheckedChange = model::split) }
            }
        }
    }

    val pages = CanonSection.of {
        CanonRows {
            CanonHeading("Pages")
            CanonDropZone("Drop images here", multiple = true, onFiles = { uris -> model.add(uris, 0) })
            if (model.hasAnyImage) {
                CanonGrid {
                    CanonButton("Clear", Modifier.weight(1f).fillMaxHeight(), icon = pencil(R.drawable.pencil_trash_2), onClick = model::clearAll)
                }
            }
        }
    }

    val cellW = model.cellWidthMm
    val cellH = model.cellHeightMm
    val paper = CanonSection.of {
        CanonRows {
            CanonHeading("Paper")
            CanonButton(model.paper.title(null), icon = pencil(R.drawable.pencil_chevron_right)) { picking = true }
            CanonHeading("Reference DPI")
            CanonSegmented(dpis.map { "$it DPI" }, dpis.indexOf(model.dpi).takeIf { it >= 0 } ?: 2) { model.dpi = dpis[it] }
            CanonTiles(
                listOf(
                    CanonTile("Sheet", millimetres(model.sheetWidthMm, model.sheetHeightMm)),
                    CanonTile("Page", millimetres(cellW, cellH)),
                    CanonTile("Ratio", "${PaperUnit.decimal((cellW / cellH * 1000).roundToInt() / 1000.0)}:1"),
                ),
            )
        }
    }

    val action = CanonSection.of {
        CanonRows {
            PdfJobPanel(model.job, "Export PDF", pencil(R.drawable.pencil_book_open_text), "Failed", enabled = model.hasAnyImage, onStart = model::export)
        }
    }

    val images = model.images.toList()
    val pictures = images.withIndex().mapNotNull { (index, image) -> image?.let { index + 1 to it.thumbnail } }.toMap()
    val sides = layout.sides.map { side ->
        ZineImposerEngine.sheet(side, layout, model.sheetWidthMm, model.sheetHeightMm) { images.getOrNull(it)?.fill == true }
    }
    val sheetCanvas = CanonSection.canvas(CanonCanvasSize.Fitting, background = c.muted, boxing = CanonCanvasBoxing.UnlessWide) {
        PrintSheetView(
            front = sides.firstOrNull(),
            back = sides.getOrNull(1),
            showsBack = model.showsBack,
            horizontal = false,
            legend = if (duplex) (if (model.showsBack) "Back" else "Front") else fold.title,
            detail = model.paper.title(null),
            figure = "${model.cellWidthPx}×${model.cellHeightPx}",
            figureUnit = "px",
            hint = null,
            pictures = pictures,
            lit = setOfNotNull(model.lit),
            sheetKey = listOf(fold, model.panels, model.doubleSided, model.split, model.sheetWidthMm, model.sheetHeightMm),
            onTap = model::flip,
        )
    }

    val steps = CanonSection.of {
        CanonRows {
            CanonHeading("How to Fold")
            FoldKey(layout.foldLines.any { it.valley }, layout.foldLines.any { !it.valley }, layout.cutLines.isNotEmpty())
            layout.instructions.forEachIndexed { index, text -> FoldStep(index + 1, text) }
        }
    }

    val cell = Size(cellW.toFloat(), cellH.toFloat())
    val half = layout.pageCount / 2
    val strip = CanonSection.canvas(CanonCanvasSize.Fixed(CanonPageStripHeight), background = c.muted) {
        CanonPageStrip(
            images.mapIndexed { index, image ->
                val name = if (duplex) (if (index < half) "Front" else "Back") else null
                val size = PdfSize(cellW, cellH).compose()
                if (image == null) {
                    CanonPageStripPage(size, null, name)
                } else {
                    val picture = Size(image.image.width.toFloat(), image.image.height.toFloat())
                    CanonPageStripPage(size, image.thumbnail, name, placement = placement(picture, cell, image.fill))
                }
            },
            selected = model.lit?.let { it - 1 },
            onSelect = { model.light(it + 1) },
            reorders = true,
            onMove = model::move,
            onDrop = { index, clip -> model.add(clip.uris(), index) },
            menu = { index ->
                if (model.job.busy || index !in model.images.indices) return@CanonPageStrip emptyList()
                buildList {
                    add("Photos" to {
                        photoSlot = index
                        photo.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                    })
                    if (model.clipboardHasImage()) add("Paste" to { model.paste(index) })
                    val image = model.images[index] ?: return@buildList
                    if (image.fill) add("Fit" to { model.fit(index, false) }) else add("Fill" to { model.fit(index, true) })
                    add("Remove" to { model.remove(index) })
                }
            },
        )
    }

    CanonSplit(
        sidebar = listOf(foldRows, pages, paper, action),
        main = listOf(sheetCanvas, steps),
        banner = strip,
    )

    if (picking) PaperSizePicker(null, model.paper, onDismiss = { picking = false }, onPick = model::pickPaper)
}

// dt-ios FoldKeyView
@Composable
private fun FoldKey(valley: Boolean, mountain: Boolean, cut: Boolean) {
    val c = LocalDelphiColours.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        Modifier.fillMaxWidth().background(c.card).padding(horizontal = CanonMetrics.panelPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val items = listOf(
            Triple("Valley fold", floatArrayOf(6f, 4f), c.primary).takeIf { valley },
            Triple("Mountain fold", floatArrayOf(8f, 3f, 1.5f, 3f), c.primary).takeIf { mountain },
            Triple("Cut", null, c.destructive).takeIf { cut },
        )
        for ((name, dashes, colour) in items.filterNotNull()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(Modifier.width(26.dp).size(26.dp, 12.dp)) {
                    val y = size.height / 2
                    val (from, to) = if (rtl) size.width to 0f else 0f to size.width
                    drawLine(
                        colour, Offset(from, y), Offset(to, y),
                        (if (dashes == null) 1.5 else 1.0).dp.toPx(),
                        pathEffect = dashes?.let { d -> PathEffect.dashPathEffect(d.map { it.dp.toPx() }.toFloatArray()) },
                    )
                }
                Text(name, style = CanonText.captionSemibold, color = c.mutedForeground)
            }
        }
    }
}

// dt-ios FoldStepRow
@Composable
private fun FoldStep(number: Int, text: String) {
    val c = LocalDelphiColours.current
    Row(
        Modifier.fillMaxWidth().background(c.card).padding(horizontal = CanonMetrics.panelPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "$number",
            Modifier.width(22.dp).alignByBaseline(),
            style = CanonText.subheadline.copy(fontFamily = DelphiMono, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = c.primary,
            textAlign = TextAlign.End,
        )
        Text(text, Modifier.weight(1f).alignByBaseline(), style = CanonText.subheadline, color = c.foreground)
    }
}
