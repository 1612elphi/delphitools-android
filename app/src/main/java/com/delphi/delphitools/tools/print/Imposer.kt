package com.delphi.delphitools.tools.print

import android.app.Application
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Print
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonNumberField
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
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.tools.pdf.PaperChoice
import com.delphi.delphitools.tools.pdf.PaperSizePicker
import com.delphi.delphitools.tools.pdf.PdfDocuments
import com.delphi.delphitools.tools.pdf.PdfFile
import com.delphi.delphitools.tools.pdf.PdfFileRow
import com.delphi.delphitools.tools.pdf.PdfJob
import com.delphi.delphitools.tools.pdf.PdfJobPanel
import com.delphi.delphitools.tools.pdf.PdfPager
import com.delphi.delphitools.tools.pdf.PdfSize
import com.delphi.delphitools.tools.pdf.PdfSourceLoader
import com.delphi.delphitools.tools.pdf.compose
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import com.delphi.delphitools.ui.theme.selectedTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

private val layouts = ImpositionLayoutId.entries
private val copiesChoices = listOf(2, 4, 6, 8, 9)

class ImposerModel(application: Application) : AndroidViewModel(application) {
    val pdf = PdfSourceLoader(viewModelScope, application)
    val job = PdfJob(viewModelScope, application)
    var layout by mutableStateOf(ImpositionLayoutId.SaddleStitch)
        private set
    var paper by mutableStateOf<PaperChoice>(PaperChoice.Paper(PaperPick.named("A4")!!))
        private set
    var orientation by mutableStateOf(ImpositionOrientation.Landscape)
    var marginMm by mutableStateOf(5.0)
    var gutterMm by mutableStateOf(2.0)
    var creepMm by mutableStateOf(0.0)
    var scaling by mutableStateOf(ImpositionScaling.Fit)
    var cropMarks by mutableStateOf(true)
    var nUp by mutableIntStateOf(4)
    var customRows by mutableIntStateOf(2)
    var customCols by mutableIntStateOf(2)
    var signaturePages by mutableIntStateOf(16)
    var duplexFlip by mutableStateOf(DuplexFlip.LongEdge)
    var blankPageCount by mutableIntStateOf(12)
    var activeSheet by mutableIntStateOf(0)
    var isFlipped by mutableStateOf(false)

    /** Sharper pictures of the shown sheet's pages, by 0-based page. */
    val sharp = mutableStateMapOf<Int, ImageBitmap>()
    private val sharpening = HashSet<Int>()

    init {
        pdf.onLoad = { resetNavigation() }
    }

    val config: ImpositionConfig
        get() {
            val mm = paper.millimetres!!
            return ImpositionConfig(
                layout, mm.width, mm.height, orientation, marginMm, gutterMm, creepMm, scaling, cropMarks, nUp, customRows, customCols,
                duplexFlip, signaturePages,
            )
        }

    val sourcePages get() = pdf.source?.pageCount ?: blankPageCount

    val result get() = layout.calculate(sourcePages, config)

    /** Wraps a change: the finished file no longer matches. */
    fun change(body: ImposerModel.() -> Unit) {
        body()
        job.reset()
    }

    fun chooseLayout(id: ImpositionLayoutId) = change {
        layout = id
        if (id == ImpositionLayoutId.SaddleStitch || id == ImpositionLayoutId.PerfectBind || id == ImpositionLayoutId.StepAndRepeat) {
            orientation = ImpositionOrientation.Landscape
        }
        resetNavigation()
    }

    fun pickPaper(choice: PaperChoice) = change { if (choice !is PaperChoice.Extra) paper = choice }

    fun resetNavigation() {
        activeSheet = 0
        isFlipped = false
    }

    fun show(sheet: Int, back: Boolean) {
        if (sheet !in result.sheets.indices) return
        activeSheet = sheet
        isFlipped = back
    }

    fun load(uri: Uri) {
        clear()
        pdf.load(uri)
    }

    fun clear() {
        job.reset()
        pdf.clear()
        sharp.clear()
        sharpening.clear()
        resetNavigation()
    }

    fun sharpen(pages: List<Int>) {
        val source = pdf.source ?: return
        val missing = pages.toSet() - sharp.keys - sharpening
        if (missing.isEmpty()) return
        // ponytail: keeps one sheet's worth; a cache by recency if paging back and forth re-renders too often
        if (sharp.size > 32) sharp.keys.retainAll(pages.toSet())
        sharpening += missing
        viewModelScope.launch {
            val rendered = withContext(Dispatchers.IO) {
                PdfDocuments.open(source.file) { renderer ->
                    missing.mapNotNull { page -> PdfDocuments.thumbnail(renderer, page, 1000)?.let { page to it.asImageBitmap() } }
                }.orEmpty()
            }
            if (pdf.source !== source) return@launch
            sharpening -= missing
            sharp.putAll(rendered)
        }
    }

    fun export() {
        val source = pdf.source ?: return
        val result = result
        val config = config
        val paperId = (paper as? PaperChoice.Paper)?.pick?.size?.id ?: "custom"
        job.start("imposed-${layout.id}-$paperId.pdf") { output, progress ->
            val context = coroutineContext
            val file = PdfFile(source.file.readBytes())
            output.outputStream().buffered().use { out ->
                ImposedPdfWriter.write(file, result, config, out) {
                    context.ensureActive()
                    progress(it.toFloat())
                }
            }
        }
    }

    override fun onCleared() = clear()
}

private fun millimetres(width: Double, height: Double) = "${PaperUnit.decimal(width)} × ${PaperUnit.decimal(height)} mm"

@Composable
fun Imposer(model: ImposerModel = viewModel()) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val loader = model.pdf
    val loaded = loader.opened
    var picking by remember { mutableStateOf(false) }
    val config = model.config
    val result = model.result
    val layout = model.layout
    val sheets = result.sheets
    val active = model.activeSheet.coerceAtMost(maxOf(0, sheets.size - 1))
    val back = model.isFlipped
    val sizes = loader.source?.sizes.orEmpty()
    val pageSizes = sizes.map { it.width to it.height }

    val source = CanonSection.of {
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
                CanonRow("Pages", flush = true) {
                    CanonNumberField(
                        model.blankPageCount,
                        { value ->
                            model.change {
                                blankPageCount = value
                                resetNavigation()
                            }
                        },
                        range = 4..400,
                        step = 4,
                    )
                }
            } else {
                PdfFileRow(loader.source, loader.name, loader.failed, model::clear)
            }
        }
    }

    val layoutRows = CanonSection.of {
        CanonRows {
            CanonHeading("Layout")
            CanonSegmented(layouts.map { it.title }, layout.ordinal, columns = 2) { model.chooseLayout(layouts[it]) }
            CanonStatusRow(layout.useCase, CanonTone.Muted)
            if (layout == ImpositionLayoutId.PerfectBind) {
                CanonRow("Pages per signature", flush = true) {
                    CanonNumberField(model.signaturePages, { value -> model.change { signaturePages = value } }, range = 4..128, step = 4)
                }
            }
            if (layout.supportsDuplex) {
                CanonHeading("Duplex Flip")
                CanonSegmented(listOf("Long edge", "Short edge"), model.duplexFlip.ordinal) { model.change { duplexFlip = DuplexFlip.entries[it] } }
            }
            if (layout == ImpositionLayoutId.GangRun) {
                CanonHeading("Copies per Sheet")
                CanonSegmented(copiesChoices.map { "$it" }, copiesChoices.indexOf(model.nUp).takeIf { it >= 0 } ?: 1) {
                    model.change { nUp = copiesChoices[it] }
                }
            }
            if (layout == ImpositionLayoutId.CustomNUp) {
                CanonRow("Rows", flush = true) { CanonNumberField(model.customRows, { value -> model.change { customRows = value } }, range = 1..10) }
                CanonRow("Columns", flush = true) { CanonNumberField(model.customCols, { value -> model.change { customCols = value } }, range = 1..10) }
            }
        }
    }

    val paper = CanonSection.of {
        CanonRows {
            CanonHeading("Paper")
            CanonButton(model.paper.title(null), icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight) { picking = true }
            CanonHeading("Orientation")
            CanonSegmented(listOf("Portrait", "Landscape"), model.orientation.ordinal) { model.change { orientation = ImpositionOrientation.entries[it] } }
            CanonHeading("Scaling")
            CanonSegmented(listOf("Fit", "Fill", "Actual size"), model.scaling.ordinal, enabled = loaded) { model.change { scaling = ImpositionScaling.entries[it] } }
        }
    }

    val spacing = CanonSection.of {
        CanonRows {
            CanonHeading("Spacing")
            CanonRow("Margin", flush = true) {
                CanonSlider(model.marginMm.toFloat(), { value -> model.change { marginMm = value.roundToInt().toDouble() } }, range = 0f..20f, step = 1f, readout = "${model.marginMm.roundToInt()} mm")
            }
            CanonRow("Gutter", flush = true) {
                CanonSlider(model.gutterMm.toFloat(), { value -> model.change { gutterMm = value.roundToInt().toDouble() } }, range = 0f..10f, step = 1f, readout = "${model.gutterMm.roundToInt()} mm")
            }
            if (layout.supportsCreep) {
                CanonRow("Creep", flush = true) {
                    CanonSlider(
                        model.creepMm.toFloat(),
                        { value -> model.change { creepMm = (value * 10).roundToInt() / 10.0 } },
                        range = 0f..2f,
                        step = 0.1f,
                        readout = String.format(Locale.ROOT, "%.1f mm", model.creepMm),
                    )
                }
            }
            CanonRow("Crop Marks") { CanonSwitch(model.cropMarks) { on -> model.change { cropMarks = on } } }
        }
    }

    val action = CanonSection.of {
        CanonRows {
            PdfJobPanel(model.job, "Export PDF", Icons.Outlined.Print, "Failed", enabled = loader.source != null, onStart = model::export)
        }
    }

    val thumbnails = if (loaded) loader.thumbnails.mapKeys { it.key + 1 } else emptyMap()
    val sheet = sheets.getOrNull(active)
    val pages = sheet?.let { (it.front + it.back).map { p -> p.pageNumber - 1 }.filter { p -> p >= 0 } }.orEmpty()
    LaunchedEffect(pages, loaded, loader.source) { if (loaded) model.sharpen(pages) }
    val pictures = thumbnails + model.sharp.filterKeys { it in pages }.mapKeys { it.key + 1 }

    val sheetCanvas = CanonSection.canvas(CanonCanvasSize.Fitting, background = c.muted, boxing = CanonCanvasBoxing.UnlessWide) {
        if (sheet == null) return@canvas
        val side = if (back) "Back" else "Front"
        var detail = "${model.sourcePages} pages"
        if (result.blanksAdded > 0) detail += " · ${result.blanksAdded} blank${if (result.blanksAdded == 1) "" else "s"} added"
        PrintSheetView(
            front = ImpositionEngine.sheet(sheet.front, config, false, pageSizes),
            back = ImpositionEngine.sheet(sheet.back, config, true, pageSizes),
            showsBack = back,
            horizontal = layout.supportsDuplex && config.turnsTopOverBottom,
            legend = "Sheet ${sheet.sheetNumber} — $side",
            detail = detail,
            figure = "${result.totalSheets}",
            figureUnit = if (result.totalSheets == 1) "Sheet" else "Sheets",
            hint = "Tap to flip",
            pictures = pictures,
            sheetKey = active,
        ) { model.isFlipped = !model.isFlipped }
    }

    val folded = layout == ImpositionLayoutId.SaddleStitch || layout == ImpositionLayoutId.PerfectBind
    val block = CanonSection.canvas(CanonCanvasSize.Fixed(128.dp), background = c.muted, boxing = CanonCanvasBoxing.UnlessWide) {
        SignatureBlock(ImpositionEngine.signatures(sheets), active, layout == ImpositionLayoutId.PerfectBind) { model.show(it, false) }
    }

    val pager = CanonSection.of {
        CanonRows { PdfPager(active, sheets.size) { model.show(active + it, false) } }
    }

    val facts = CanonSection.of {
        val cell = sheets.firstOrNull()?.front?.firstOrNull()
        CanonRows {
            CanonTiles(
                listOf(
                    CanonTile("Sheet", millimetres(config.sheetW, config.sheetH)),
                    CanonTile("Cell", cell?.let { millimetres(it.width, it.height) } ?: "—"),
                ),
            )
        }
    }

    // per-batch redraw froze 400pp
    val streamed = if (thumbnails.size >= sizes.size) -1 else thumbnails.size / 64
    val paperColours = selectedTheme().colours(false)
    val resolver = LocalFontFamilyResolver.current
    var stripPictures by remember { mutableStateOf<List<CanonPageStripPage>>(emptyList()) }
    val stripKey = listOf(config, streamed, sheets, paperColours)
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(stripKey) {
        if (!first[0]) delay(120)
        first[0] = false
        val shown = thumbnails
        stripPictures = withContext(Dispatchers.Default) {
            val measurer = TextMeasurer(resolver, density, LayoutDirection.Ltr, 0)
            sheets.flatMap { definition ->
                listOf(false, true).map { isBack ->
                    val side = ImpositionEngine.sheet(if (isBack) definition.back else definition.front, config, isBack, pageSizes)
                    CanonPageStripPage(
                        PdfSize(side.width, side.height).compose(),
                        renderPrintSheet(side, sheets.size * 2, density.density, paperColours, measurer, shown),
                        "Sheet ${definition.sheetNumber} — ${if (isBack) "Back" else "Front"}",
                    )
                }
            }
        }
    }
    val strip = CanonSection.canvas(CanonCanvasSize.Fixed(CanonPageStripHeight), background = c.muted) {
        CanonPageStrip(
            stripPictures,
            selected = active * 2 + if (back) 1 else 0,
            onSelect = { model.show(it / 2, it % 2 == 1) },
            empty = "Sheets",
        )
    }

    CanonSplit(
        sidebar = listOf(source, layoutRows, paper, spacing, action),
        main = listOfNotNull(sheetCanvas, block.takeIf { folded }, pager, facts),
        banner = strip,
        mainLeadsStacked = true,
    )

    if (picking) PaperSizePicker(null, model.paper, onDismiss = { picking = false }, onPick = model::pickPaper)
}
