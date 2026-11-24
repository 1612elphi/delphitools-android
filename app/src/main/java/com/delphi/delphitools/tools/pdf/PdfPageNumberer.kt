package com.delphi.delphitools.tools.pdf

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.East
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.North
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.South
import androidx.compose.material.icons.outlined.SouthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.West
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
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
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonRule
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt

private typealias Section = PdfStampEngine.NumberSection

private val anchorIcons = listOf(
    Icons.Outlined.NorthWest, Icons.Outlined.North, Icons.Outlined.NorthEast,
    Icons.Outlined.West, Icons.Filled.Circle, Icons.Outlined.East,
    Icons.Outlined.SouthWest, Icons.Outlined.South, Icons.Outlined.SouthEast,
)
private val anchorLabels = PdfStampEngine.Anchor.entries.map { it.label }
private const val PREVIEW_DELAY_MS = 100L

class PdfPageNumbererModel(application: Application) : AndroidViewModel(application) {
    val pdf = PdfSourceLoader(viewModelScope, application)
    val job = PdfJob(viewModelScope, application)
    private var file: PdfFile? = null
    var options by mutableStateOf(PdfStampEngine.Options())
        private set
    var page by mutableIntStateOf(0)
        private set
    var sectionId by mutableStateOf<UUID?>(options.sections.first().id)
        private set
    var preview by mutableStateOf<ImageBitmap?>(null)
        private set
    private var previewTask: Job? = null

    init {
        pdf.onLoad = { source ->
            viewModelScope.launch {
                val read = withContext(Dispatchers.IO) { runCatching { PdfFile(source.file.readBytes()) }.getOrNull() }
                if (pdf.source !== source) return@launch
                file = read
                sectionId = options.sections.firstOrNull()?.id
                changed()
            }
        }
    }

    val sizes get() = pdf.source?.sizes.orEmpty()

    val selectedSection get() = options.sections.indexOfFirst { it.id == sectionId }.takeIf { it >= 0 }

    fun load(uri: Uri) {
        clear()
        pdf.load(uri)
    }

    fun clear() {
        job.reset()
        pdf.clear()
        file = null
        page = 0
        previewTask?.cancel()
        val first = Section()
        options = options.copy(sections = listOf(first))
        sectionId = first.id
        preview = null
    }

    fun update(change: (PdfStampEngine.Options) -> PdfStampEngine.Options) {
        options = change(options)
        changed()
    }

    private fun changed() {
        options = options.copy(sections = options.sections.sortedBy { it.fromPage })
        job.reset()
        schedulePreview()
    }

    fun editSection(body: (Section) -> Section) {
        val index = selectedSection ?: return
        update { it.copy(sections = it.sections.toMutableList().apply { set(index, body(get(index))) }) }
    }

    /** Starts stay unique. */
    fun fromPage(value: Int) {
        val index = selectedSection ?: return
        val sections = options.sections
        val low = if (index > 0) sections[index - 1].fromPage + 1 else 1
        val high = if (index < sections.size - 1) sections[index + 1].fromPage - 1 else maxOf(1, sizes.size)
        editSection { it.copy(fromPage = value.coerceIn(low, maxOf(low, high))) }
    }

    fun moveSection(id: UUID, start: Int) {
        val index = options.sections.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        sectionId = id
        update { it.copy(sections = it.sections.toMutableList().apply { set(index, get(index).copy(fromPage = start + 1)) }) }
    }

    /** Splits at the lit page. */
    fun addSection() {
        val starts = options.sections.map { it.fromPage }.toSet()
        val start = (page + 1..maxOf(page + 1, sizes.size)).firstOrNull { it !in starts } ?: return
        val inherited = PdfStampEngine.resolveSection(options.sections, start)?.style ?: PdfStampEngine.NumeralStyle.Arabic
        val section = Section(start, inherited, 1)
        options = options.copy(sections = options.sections + section)
        sectionId = section.id
        select(start - 1)
        changed()
    }

    fun removeSection() {
        val index = selectedSection ?: return
        if (options.sections.size <= 1) return
        val rest = options.sections.toMutableList().apply { removeAt(index) }
        sectionId = rest[maxOf(0, index - 1)].id
        update { it.copy(sections = rest) }
    }

    fun select(next: Int) {
        if (next !in sizes.indices) return
        page = next
        sectionId = PdfStampEngine.resolveSection(options.sections, next + 1)?.id ?: options.sections.firstOrNull()?.id
        schedulePreview()
    }

    private fun schedulePreview() {
        previewTask?.cancel()
        val file = file ?: return
        val source = pdf.source ?: return
        val index = page
        val options = options
        previewTask = viewModelScope.launch {
            delay(PREVIEW_DELAY_MS)
            val picture = withContext(Dispatchers.IO) {
                val bytes = PdfStampEngine.stamp(file, options, setOf(index))
                coroutineContext.ensureActive()
                if (bytes === file.bytes) return@withContext PdfDocuments.open(source.file) { PdfDocuments.thumbnail(it, index, 1200) }
                // ponytail: the whole file is written per preview; render the page alone if large files lag
                val temp = File.createTempFile("stamp", ".pdf", getApplication<Application>().cacheDir)
                try {
                    temp.writeBytes(bytes)
                    PdfDocuments.open(temp) { PdfDocuments.thumbnail(it, index, 1200) }
                } finally {
                    temp.delete()
                }
            }
            preview = picture?.asImageBitmap()
        }
    }

    fun export() {
        val file = file ?: return
        val source = pdf.source ?: return
        val options = options
        job.start("${source.stem}-numbered.pdf") { output, progress ->
            val context = coroutineContext
            output.writeBytes(
                PdfStampEngine.stamp(file, options) { index ->
                    context.ensureActive()
                    progress(index.toFloat() / file.pages.size)
                },
            )
            progress(1f)
        }
    }

    override fun onCleared() = clear()
}

@Composable
fun PdfPageNumberer(model: PdfPageNumbererModel = viewModel()) {
    val c = LocalDelphiColours.current
    val loader = model.pdf
    val loaded = loader.opened
    val options = model.options
    val sizes = model.sizes
    val page = model.page
    var template by remember { mutableStateOf(options.template) }
    var stampText by remember { mutableStateOf(options.stampText) }
    LaunchedEffect(template) {
        delay(TYPING_DELAY_MS)
        if (template != model.options.template) model.update { it.copy(template = template) }
    }
    LaunchedEffect(stampText) {
        delay(TYPING_DELAY_MS)
        if (stampText != model.options.stampText) model.update { it.copy(stampText = stampText) }
    }

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

    val numbers = CanonSection.of {
        CanonRows {
            CanonRow("Page numbers") { CanonSwitch(options.numbersOn) { on -> model.update { it.copy(numbersOn = on) } } }
            if (options.numbersOn) {
                CanonRow("Format", flush = true) {
                    var tip by remember { mutableStateOf(false) }
                    Row(Modifier.height(CanonMetrics.controlHeight).background(c.card)) {
                        CanonTextCell(template, { template = it }, Modifier.fillMaxHeight(), placeholder = "{n} / {N}")
                        CanonRule(vertical = true)
                        Box {
                            CanonButton("", Modifier.width(52.dp).fillMaxHeight(), icon = Icons.Outlined.Info, contentDescription = "Format") { tip = true }
                            MenuCellPopup(tip, { tip = false }) {
                                Column(Modifier.widthIn(max = 280.dp).padding(horizontal = CanonMetrics.panelPadding, vertical = 10.dp)) {
                                    Text("Format", style = CanonText.bodySemibold, color = c.foreground)
                                    Text(
                                        "{n} stands for the page number, {N} the page count.\nPage {n} of {N} prints Page 3 of 12.",
                                        style = CanonText.subheadline,
                                        color = c.mutedForeground,
                                    )
                                }
                            }
                        }
                    }
                }
                CanonHeading("Position")
                CanonSegmented(anchorLabels, options.numberAnchor.ordinal, columns = 3, icons = anchorIcons) { index ->
                    model.update { it.copy(numberAnchor = PdfStampEngine.Anchor.entries[index]) }
                }
            }
        }
    }

    val sections = CanonSection.of {
        val index = model.selectedSection
        val section = index?.let { options.sections[it] }
        CanonRows {
            CanonHeading("Sections")
            CanonRow("From page", flush = true) {
                CanonNumberField(section?.fromPage ?: 1, model::fromPage, range = 1..maxOf(1, sizes.size))
            }
            CanonHeading("Style")
            CanonSegmented(PdfStampEngine.NumeralStyle.entries.map { it.label }, section?.style?.ordinal ?: 0, columns = 3) { style ->
                model.editSection { it.copy(style = PdfStampEngine.NumeralStyle.entries[style]) }
            }
            CanonRow("Start at", flush = true) {
                CanonNumberField(section?.startAt ?: 1, { value -> model.editSection { it.copy(startAt = value) } }, range = 0..9999)
            }
            CanonGrid {
                val cell = Modifier.weight(1f).fillMaxHeight()
                CanonButton("Add section", cell, icon = Icons.Outlined.Add, enabled = sizes.isNotEmpty(), onClick = model::addSection)
                CanonButton("Remove section", cell, icon = Icons.Outlined.Remove, enabled = options.sections.size > 1, onClick = model::removeSection)
            }
        }
    }

    val stamp = CanonSection.of {
        CanonRows {
            CanonRow("Stamp text") { CanonSwitch(options.stampOn) { on -> model.update { it.copy(stampOn = on) } } }
            if (options.stampOn) {
                CanonRow("Text", flush = true) { CanonTextCell(stampText, { stampText = it }, placeholder = "DRAFT") }
                CanonHeading("Mode")
                CanonSegmented(listOf("Label", "Watermark"), options.stampMode.ordinal) { mode ->
                    model.update { it.copy(stampMode = PdfStampEngine.StampMode.entries[mode]) }
                }
                if (options.stampMode == PdfStampEngine.StampMode.Label) {
                    CanonHeading("Position")
                    CanonSegmented(anchorLabels, options.stampAnchor.ordinal, columns = 3, icons = anchorIcons) { index ->
                        model.update { it.copy(stampAnchor = PdfStampEngine.Anchor.entries[index]) }
                    }
                } else {
                    CanonRow("Opacity", flush = true) {
                        val percent = (options.watermarkOpacity * 100).toFloat()
                        CanonSlider(percent, { value -> model.update { it.copy(watermarkOpacity = value.roundToInt() / 100.0) } }, range = 5f..60f, step = 5f, readout = "${percent.roundToInt()}%")
                    }
                }
            }
        }
    }

    val type = CanonSection.of {
        CanonRows {
            CanonHeading("Font")
            CanonSegmented(listOf("Sans", "Serif", "Mono"), options.font.ordinal) { font ->
                model.update { it.copy(font = PdfStampEngine.FontFamily.entries[font]) }
            }
            CanonRow("Size", flush = true) {
                CanonSlider(options.fontSize.toFloat(), { value -> model.update { it.copy(fontSize = value.roundToInt().toDouble()) } }, range = 6f..48f, step = 1f, readout = "${options.fontSize.roundToInt()} pt")
            }
            CanonRow("Margin", flush = true) {
                CanonSlider(options.margin.toFloat(), { value -> model.update { it.copy(margin = value.roundToInt().toDouble()) } }, range = 0f..120f, step = 2f, readout = "${options.margin.roundToInt()} pt")
            }
        }
    }

    val action = CanonSection.of {
        CanonRows {
            PdfJobPanel(model.job, "Export PDF", Icons.Outlined.FormatListNumbered, "Failed", enabled = loader.source != null, onStart = model::export)
        }
    }

    val anchor = if (options.numbersOn) options.numberAnchor else null
    val pageSize = sizes.getOrNull(page) ?: PdfSize(210.0, 297.0)
    val preview = pageCanvas(pageSize, c.muted) {
        NumberPreview(model.preview, anchor) { picked -> model.update { it.copy(numberAnchor = picked) } }
    }

    val pager = CanonSection.of {
        CanonRows { PdfPager(page, sizes.size) { model.select(page + it) } }
    }

    val labels = PdfStampEngine.resolvePageNumbers(options.sections, sizes.size)
    val lanes = options.sections.map { section ->
        Lane(section.id, section.fromPage - 1, (0 until 3).joinToString(", ") { PdfStampEngine.formatNumeral(section.startAt + it, section.style) })
    }
    val track = CanonSection.canvas(CanonCanvasSize.Fixed(170.dp), background = c.muted) {
        SequenceTrack(sizes, loader.thumbnails, labels, lanes, model.sectionId, page, anchor, model::select, model::moveSection)
    }

    CanonSplit(
        sidebar = listOfNotNull(input, numbers, sections.takeIf { loaded }, stamp, type, action),
        main = if (loaded) listOf(preview, pager) else emptyList(),
        banner = track,
        mainLeadsStacked = true,
    )
}

/** dt-ios PageNumberPreview: the stamped page; a tap picks the anchor of the third it lands in. */
@Composable
private fun NumberPreview(picture: ImageBitmap?, anchor: PdfStampEngine.Anchor?, onAnchor: (PdfStampEngine.Anchor) -> Unit) {
    val c = LocalDelphiColours.current
    val tapped by rememberUpdatedState(onAnchor)
    BoxWithConstraints(Modifier.fillMaxSize().semantics { contentDescription = "Preview" }, contentAlignment = Alignment.Center) {
        if (picture == null) {
            CanonLoader(colour = c.mutedForeground)
            return@BoxWithConstraints
        }
        val density = LocalDensity.current
        val inset = with(density) { 12.dp.toPx() }
        val area = Size(constraints.maxWidth - inset * 2, constraints.maxHeight - inset * 2)
        val scale = minOf(area.width / picture.width, area.height / picture.height)
        val rect = Rect(
            Offset(inset + (area.width - picture.width * scale) / 2, inset + (area.height - picture.height * scale) / 2),
            Size(picture.width * scale, picture.height * scale),
        )
        Box(
            Modifier.fillMaxSize().pointerInput(rect, anchor != null) {
                if (anchor == null) return@pointerInput
                detectTapGestures { point ->
                    val column = ((point.x - rect.left) / rect.width * 3).toInt().coerceIn(0, 2)
                    val row = ((point.y - rect.top) / rect.height * 3).toInt().coerceIn(0, 2)
                    tapped(PdfStampEngine.Anchor.entries[row * 3 + column])
                }
            },
        ) {
            Image(
                picture,
                null,
                Modifier
                    .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                    .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() }),
                contentScale = ContentScale.FillBounds,
            )
            Canvas(Modifier.fillMaxSize()) {
                if (anchor == null) return@Canvas
                val mark = 6.dp.toPx()
                for (zone in PdfStampEngine.Anchor.entries) {
                    if (zone == anchor) continue
                    val x = rect.left + rect.width * (0.08f + 0.42f * zone.column)
                    val y = rect.top + rect.height * (0.06f + 0.44f * zone.row)
                    drawRect(c.primary.copy(alpha = 0.35f), Offset(x - mark / 2, y - mark / 2), Size(mark, mark))
                }
            }
        }
    }
}

private class Lane(val id: UUID, val from: Int, val style: String)

private val trackIndexRow = 16.dp
private val trackLane = 28.dp
private val trackGap = 10.dp

/** dt-ios PageSequenceTrack: pages in a row with their numbers drawn on, and a lane per section whose start is dragged. */
@Composable
private fun SequenceTrack(
    sizes: List<PdfSize>,
    pictures: Map<Int, ImageBitmap>,
    labels: List<String?>,
    lanes: List<Lane>,
    lit: UUID?,
    page: Int,
    anchor: PdfStampEngine.Anchor?,
    onSelect: (Int) -> Unit,
    onMoveSection: (UUID, Int) -> Unit,
) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    var offset by remember { mutableFloatStateOf(0f) }
    val top = LocalCanonCanvas.current.top + 8.dp
    if (sizes.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(top = LocalCanonCanvas.current.top), contentAlignment = Alignment.Center) {
            Text("Pages", style = CanonText.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.mutedForeground)
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val view = constraints.maxWidth.toFloat()
        val frames = with(density) {
            val sheet = maxOf(24.dp.toPx(), constraints.maxHeight - (top + trackIndexRow + trackLane + 18.dp).toPx())
            var x = CanonMetrics.panelPadding.toPx()
            sizes.map { size ->
                val width = maxOf(14.dp.toPx(), sheet * (size.width / maxOf(size.height, 1.0)).toFloat())
                Rect(Offset(x, (top + trackIndexRow).toPx()), Size(width, sheet)).also { x += width + trackGap.toPx() }
            }
        }
        val limit = maxOf(0f, frames.last().right + with(density) { CanonMetrics.panelPadding.toPx() } - view)
        offset = offset.coerceIn(0f, limit)
        val scroll = rememberScrollableState { delta ->
            val next = (offset - delta).coerceIn(0f, limit)
            (offset - next).also { offset = next }
        }
        val laneY = frames.first().bottom + with(density) { 8.dp.toPx() }
        val select by rememberUpdatedState(onSelect)
        val move by rememberUpdatedState(onMoveSection)
        val sections by rememberUpdatedState(lanes)
        LaunchedEffect(page) {
            val frame = frames.getOrNull(page) ?: return@LaunchedEffect
            val margin = with(density) { 40.dp.toPx() }
            val target = when {
                frame.left - margin < offset -> frame.left - margin
                frame.right + margin > offset + view -> frame.right + margin - view
                else -> return@LaunchedEffect
            }
            scroll.animateScrollBy(offset - target.coerceIn(0f, limit))
        }
        val gap = with(density) { trackGap.toPx() }
        fun grip(point: Offset): Lane? {
            val slack = with(density) { 16.dp.toPx() }
            if (point.y < laneY - slack || point.y > laneY + with(density) { trackLane.toPx() } + slack) return null
            val reach = with(density) { 22.dp.toPx() }
            return sections.firstOrNull { it.from in frames.indices && abs(frames[it.from].left - gap / 2 - point.x) < reach }
        }
        // one viewport-wide canvas: a canvas as wide as a long document passes Compose's 262,143 px limit
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(frames) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val lane = grip(down.position + Offset(offset, 0f)) ?: return@awaitEachGesture
                        val ordered = sections.sortedBy { it.from }
                        val at = ordered.indexOfFirst { it.id == lane.id }
                        val low = if (at > 0) ordered[at - 1].from + 1 else 0
                        val high = if (at < ordered.size - 1) ordered[at + 1].from - 1 else frames.size - 1
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val x = change.position.x + offset
                            val nearest = frames.indices.minByOrNull { abs(frames[it].left - x) } ?: continue
                            val start = nearest.coerceIn(low, maxOf(low, high))
                            if (sections.firstOrNull { it.id == lane.id }?.from != start) move(lane.id, start)
                        }
                    }
                }
                .scrollable(scroll, Orientation.Horizontal, reverseDirection = false)
                .pointerInput(frames) {
                    detectTapGestures { point ->
                        val x = point.x + offset
                        frames.indices.minByOrNull { abs(frames[it].center.x - x) }?.let(select)
                    }
                },
        ) {
            translate(-offset, 0f) {
                drawTrack(frames, pictures, labels, lanes, lit, page, anchor, laneY, offset, size.width, measurer, c)
            }
        }
    }
}

private fun DrawScope.drawTrack(
    frames: List<Rect>,
    pictures: Map<Int, ImageBitmap>,
    labels: List<String?>,
    lanes: List<Lane>,
    lit: UUID?,
    page: Int,
    anchor: PdfStampEngine.Anchor?,
    laneY: Float,
    offset: Float,
    width: Float,
    measurer: TextMeasurer,
    c: com.delphi.delphitools.ui.theme.DelphiColours,
) {
    val visibleLeft = offset
    val visibleRight = offset + width
    val paperInk = Color(0xFF1A1A1A)
    val mono = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = paperInk)
    val indexStyle = CanonText.caption.copy(fontWeight = FontWeight.Medium)
    val frameWidth = CanonMetrics.frame.toPx()
    val hairline = CanonMetrics.hairline.toPx()
    for ((i, frame) in frames.withIndex()) {
        if (frame.right < visibleLeft - 20 || frame.left > visibleRight + 20) continue
        val numbered = labels.getOrNull(i) != null
        val isPage = i == page
        drawRect(Color.White, frame.topLeft, frame.size)
        pictures[i]?.let {
            drawImage(
                it,
                dstOffset = IntOffset(frame.left.roundToInt(), frame.top.roundToInt()),
                dstSize = IntSize(frame.width.roundToInt(), frame.height.roundToInt()),
                alpha = if (numbered) 1f else 0.45f,
            )
        }
        val edge = if (isPage) frameWidth else hairline
        drawRect(if (isPage) c.primary else c.border, Offset(frame.left + edge / 2, frame.top + edge / 2), Size(frame.width - edge, frame.height - edge), style = Stroke(edge))
        val number = measurer.measure("${i + 1}", indexStyle.copy(color = if (isPage) c.primary else c.mutedForeground))
        drawText(number, topLeft = Offset(frame.center.x - number.size.width / 2, frame.top - trackIndexRow.toPx()))

        val label = labels.getOrNull(i) ?: continue
        anchor ?: continue
        val text = measurer.measure(label, mono)
        val inset = 4.dp.toPx()
        val x = when (anchor.column) {
            0 -> frame.left + inset
            2 -> frame.right - inset - text.size.width
            else -> frame.center.x - text.size.width / 2
        }
        val y = when (anchor.row) {
            0 -> frame.top + inset
            2 -> frame.bottom - inset - text.size.height
            else -> frame.center.y - text.size.height / 2
        }
        clipRect(frame.left, frame.top, frame.right, frame.bottom) {
            drawRect(Color.White, Offset(x - 2, y), Size(text.size.width + 4f, text.size.height.toFloat()))
            drawText(text, topLeft = Offset(x, y))
        }
    }

    val first = frames.first()
    val last = frames.last()
    val laneHeight = trackLane.toPx()
    val ordered = lanes.filter { it.from in frames.indices }.sortedBy { it.from }
    val caption = CanonText.caption.copy(fontWeight = FontWeight.SemiBold)
    fun lane(from: Float, to: Float, fill: Color, ink: Color, text: String) {
        val rect = Rect(from, laneY, maxOf(from, to), laneY + laneHeight)
        if (rect.right < visibleLeft || rect.left > visibleRight) return
        drawRect(fill, rect.topLeft, rect.size)
        val measured = measurer.measure(text, caption.copy(color = ink))
        val pad = 10.dp.toPx()
        // label follows scrolled span
        val x = minOf(maxOf(rect.left + pad, visibleLeft + pad), rect.right - measured.size.width - pad)
        if (x < rect.left + 4.dp.toPx()) return
        clipRect(rect.left, rect.top, rect.right, rect.bottom) {
            drawText(measured, topLeft = Offset(x, rect.center.y - measured.size.height / 2))
        }
    }
    val start = ordered.firstOrNull()?.from
    if (start != null && start > 0) lane(first.left, frames[start - 1].right, c.border.copy(alpha = 0.5f), c.mutedForeground, "Unnumbered")
    val gap = trackGap.toPx()
    for ((n, section) in ordered.withIndex()) {
        val end = if (n + 1 < ordered.size) frames[maxOf(ordered[n + 1].from - 1, section.from)].right else last.right
        val isLit = section.id == lit
        lane(
            frames[section.from].left, end,
            if (isLit) c.primary else c.primary.copy(alpha = 0.18f),
            if (isLit) c.primaryForeground else c.foreground,
            section.style,
        )
        drawRect(
            if (isLit) c.primary else c.foreground,
            Offset(frames[section.from].left - gap / 2 - 2.dp.toPx(), laneY - 6.dp.toPx()),
            Size(4.dp.toPx(), laneHeight + 12.dp.toPx()),
        )
    }
}
