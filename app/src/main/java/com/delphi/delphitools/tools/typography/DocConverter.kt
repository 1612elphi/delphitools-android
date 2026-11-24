package com.delphi.delphitools.tools.typography

import android.app.Application
import android.content.ClipboardManager
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TabStopSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonFromTo
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonPageStrip
import com.delphi.delphitools.canon.CanonPageStripHeight
import com.delphi.delphitools.canon.CanonPageStripPage
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonRule
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonTextArea
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.canon.CanonTextFilePicker
import com.delphi.delphitools.canon.rememberCanonTextFilePicker
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.tools.audiovideo.AVEditJob
import com.delphi.delphitools.tools.audiovideo.AVEditJobPanel
import com.delphi.delphitools.tools.audiovideo.avEditRoot
import com.delphi.delphitools.tools.pdf.PdfDocuments
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

private const val PAGE_LIMIT = 40
private const val DASH = "—"
private val sampleMono = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp)
private val countMono = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp)
private val linkBlue = Color(0xFF007AFF)

/** What the finished file shows: its text, its rich text, or its pages. */
sealed interface DocResult {
    val file: File
    val format: DocFormat

    class Text(override val file: File, override val format: DocFormat, val text: String) : DocResult
    class Rich(override val file: File, val paragraphs: List<DocParagraph>) : DocResult {
        override val format = DocFormat.Rtf
    }
    class Pages(override val file: File, val pages: List<CanonPageStripPage>) : DocResult {
        override val format = DocFormat.Pdf
    }
}

/** dt-ios DocConverterViewController's state: the source, the chosen formats, the parsed blocks and the converted file. */
class DocConverterModel(application: Application) : AndroidViewModel(application) {
    val job = AVEditJob(viewModelScope, avEditRoot(application, "doc-converter"))
    var text by mutableStateOf("")
        private set
    var fileName by mutableStateOf<String?>(null)
        private set
    var from by mutableStateOf(0)
        private set
    var to by mutableStateOf(1)
        private set
    var standalone by mutableStateOf(true)
        private set
    var blocks by mutableStateOf<List<DocBlock>>(emptyList())
        private set
    var result by mutableStateOf<DocResult?>(null)
        private set

    /** Stops format detection. */
    private var sourcePicked = false
    private var typing: Job? = null
    private var parsing: Job? = null
    private var showing: Job? = null

    val source get() = DocFormat.inputs[from]
    val target get() = DocFormat.outputs[to]

    init {
        viewModelScope.launch { snapshotFlow { job.output }.collect(::show) }
    }

    fun type(new: String) {
        text = new
        job.reset()
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            refresh()
        }
    }

    fun load(new: String, name: String?) {
        fileName = name
        if (new.isEmpty()) sourcePicked = false
        text = new
        job.reset()
        refresh()
    }

    fun chooseFrom(index: Int) {
        if (index != from) sourcePicked = true
        from = index
        changed()
    }

    fun chooseTo(index: Int) {
        to = index
        changed()
    }

    fun chooseStandalone(on: Boolean) {
        standalone = on
        changed()
    }

    private fun changed() {
        job.reset()
        refresh()
    }

    private fun refresh() {
        typing?.cancel()
        if (!sourcePicked) {
            val detected = fileName?.let(DocConvertEngine::format) ?: DocConvertEngine.formatOf(text)
            from = DocFormat.inputs.indexOf(detected).coerceAtLeast(0)
        }
        val text = text
        val source = source
        parsing?.cancel()
        parsing = viewModelScope.launch {
            blocks = runInterruptible(Dispatchers.Default) { DocConvertEngine.parse(text, source) }
        }
    }

    fun convert() {
        refresh()
        val text = text
        val from = source
        val to = target
        val wrap = standalone
        if (text.isEmpty()) return
        val stem = fileName?.substringBeforeLast('.') ?: "document"
        job.start("$stem.${to.ext}") { output, progress ->
            val blocks = DocConvertEngine.parse(text, from)
            val rendered = DocConvertEngine.render(blocks, to, wrap)
            if (rendered != null) output.writeText(rendered) else writePdf(DocConvertEngine.attributed(blocks, bodySize = 11f), output)
            progress(1.0)
        }
    }

    private fun show(output: File?) {
        showing?.cancel()
        result = null
        output ?: return
        val format = DocFormat.entries.firstOrNull { it.ext == output.extension } ?: target
        val text = text
        val from = source
        showing = viewModelScope.launch {
            when (format) {
                DocFormat.Pdf -> {
                    val sizes = PdfDocuments.source(output, output.name)?.sizes ?: return@launch
                    var shown = sizes.map { CanonPageStripPage(Size(it.width.toFloat(), it.height.toFloat())) }
                    result = DocResult.Pages(output, shown)
                    launch {
                        PdfDocuments.streamThumbnails(output, 360) { batch ->
                            shown = shown.mapIndexed { index, page -> batch[index]?.takeIf { index < PAGE_LIMIT }?.let { page.copy(picture = it) } ?: page }
                            result = DocResult.Pages(output, shown)
                            if (batch.keys.any { it >= PAGE_LIMIT - 1 }) cancel()
                        }
                    }
                }
                DocFormat.Rtf -> result = DocResult.Rich(output, withContext(Dispatchers.Default) { DocConvertEngine.attributed(DocConvertEngine.parse(text, from)) })
                else -> result = DocResult.Text(output, format, withContext(Dispatchers.IO) { runCatching { output.readText() }.getOrDefault("") })
            }
        }
    }
}

/** A4 with 48 pt margins, one page per screenful of lines, as dt-ios pdfData paginates with CoreText. */
private fun writePdf(paragraphs: List<DocParagraph>, file: File) {
    val width = 595
    val height = 842
    val margin = 48f
    val bodySize = 11f
    val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = bodySize }
    val document = PdfDocument()
    var number = 0
    var page: PdfDocument.Page? = null
    var y = margin
    fun next(): PdfDocument.Page {
        page?.let(document::finishPage)
        y = margin
        return document.startPage(PdfDocument.PageInfo.Builder(width, height, ++number).create()).also { page = it }
    }
    var current = next()
    try {
        for (paragraph in paragraphs) {
            val text = SpannableStringBuilder()
            for (run in paragraph.runs) {
                val start = text.length
                text.append(run.text)
                val end = text.length
                if (start == end) continue
                fun span(what: Any) = text.setSpan(what, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                span(RelativeSizeSpan(run.size / bodySize))
                if (run.mono) span(TypefaceSpan("monospace"))
                val style = (if (run.bold) Typeface.BOLD else 0) or (if (run.italic) Typeface.ITALIC else 0)
                if (style != 0) span(StyleSpan(style))
                if (run.link != null) {
                    span(URLSpan(run.link))
                    span(ForegroundColorSpan(android.graphics.Color.rgb(0, 122, 255)))
                }
            }
            if (text.isEmpty()) text.append(" ")
            text.setSpan(LeadingMarginSpan.Standard(paragraph.firstIndent.toInt(), paragraph.headIndent.toInt()), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            if (paragraph.headIndent > 0) text.setSpan(TabStopSpan.Standard(paragraph.headIndent.toInt()), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, (width - margin * 2).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(paragraph.lineSpacing, 1f)
                .setIncludePad(false)
                .build()
            if (y > margin) y += paragraph.spacingBefore
            for (line in 0 until layout.lineCount) {
                val top = layout.getLineTop(line).toFloat()
                val lineHeight = layout.getLineBottom(line) - top
                if (y + lineHeight > height - margin && y > margin) current = next()
                val canvas = current.canvas
                canvas.save()
                canvas.clipRect(margin, y, width - margin, y + lineHeight)
                canvas.translate(margin, y - top)
                layout.draw(canvas)
                canvas.restore()
                y += lineHeight
            }
            y += paragraph.spacingAfter
        }
        document.finishPage(current)
        page = null
        file.outputStream().use(document::writeTo)
    } finally {
        document.close()
    }
}

@Composable
fun DocConverter(model: DocConverterModel = viewModel()) {
    val context = LocalContext.current
    val text = model.text
    lateinit var picker: CanonTextFilePicker
    picker = rememberCanonTextFilePicker(arrayOf("text/plain", "text/html", "text/markdown", "text/*")) { model.load(it, picker.fileName) }

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            CanonTextArea(text, model::type, placeholder = "Paste or type text here…", height = 240.dp, label = "Source")
            CanonGrid(columns = 3) {
                CanonButton("Paste", icon = Icons.Outlined.ContentPaste) {
                    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                    clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.let { model.load(it, null) }
                }
                CanonButton("Files", icon = Icons.Outlined.Folder) { picker.open() }
                CanonButton("Clear", icon = Icons.Outlined.Delete, enabled = text.isNotEmpty()) { model.load("", null) }
            }
        }
    }

    val settings = CanonSection.of {
        CanonRows {
            CanonHeading("Convert")
            CanonFromTo(
                DocFormat.inputs.map { it.label },
                model.from,
                model.to,
                model::chooseFrom,
                model::chooseTo,
                targets = DocFormat.outputs.map { it.label },
            )
            if (model.target == DocFormat.Html) StandaloneRow(model.standalone, model::chooseStandalone)
        }
    }

    val action = CanonSection.of {
        CanonRows {
            AVEditJobPanel(model.job, "Convert", Icons.Outlined.Transform, "Failed", enabled = text.isNotEmpty(), onStart = model::convert)
        }
    }

    val sourceBlock = CanonSection.of {
        CanonRows {
            CanonHeading("Input")
            val words = remember(text) { text.split(Regex("\\s+")).count { it.isNotEmpty() } }
            CanonTiles(
                listOf(
                    CanonTile("Name", model.fileName ?: DASH),
                    CanonTile("Format", model.source.label),
                    CanonTile("Size", Formatter.formatFileSize(context, text.toByteArray().size.toLong())),
                    CanonTile("Words", String.format("%,d", words)),
                ),
            )
        }
    }

    val result = model.result
    val resultBlock = CanonSection.of {
        CanonRows {
            CanonHeading("Output")
            if (result == null) {
                CanonEmptyRow("None")
                return@CanonRows
            }
            val tiles = mutableListOf(
                CanonTile("Name", result.file.name),
                CanonTile("Format", result.format.label),
                CanonTile("Size", Formatter.formatFileSize(context, result.file.length())),
            )
            when (result) {
                is DocResult.Text -> {
                    tiles += CanonTile("Lines", String.format("%,d", result.text.split("\n").size))
                    CanonTiles(tiles)
                    CanonCodeBlock(result.text, maxHeight = 360.dp)
                }
                is DocResult.Rich -> {
                    CanonTiles(tiles)
                    RichText(result.paragraphs)
                }
                is DocResult.Pages -> {
                    tiles[1] = CanonTile("Format", "PDF")
                    tiles += CanonTile("Pages", String.format("%,d", result.pages.size))
                    CanonTiles(tiles)
                }
            }
        }
    }

    val pages = (result as? DocResult.Pages)?.let { shown ->
        CanonSection.canvas(CanonCanvasSize.Fixed(CanonPageStripHeight), background = LocalDelphiColours.current.muted, boxing = CanonCanvasBoxing.UnlessWide) {
            CanonPageStrip(shown.pages, Modifier.fillMaxSize())
        }
    }

    val banner = CanonSection.canvas(CanonCanvasSize.Fitting) { DocLedger(model.blocks, model.source, model.target) }

    CanonSplit(
        sidebar = listOf(input, settings, action),
        main = listOfNotNull(sourceBlock, resultBlock, pages),
        banner = banner,
    )
}

@Composable
private fun StandaloneRow(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalDelphiColours.current
    var tip by remember { mutableStateOf(false) }
    CanonRow("Standalone", flush = true) {
        Row(Modifier.height(IntrinsicSize.Min).background(c.card), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(horizontal = 16.dp)) { CanonSwitch(on, onCheckedChange = onChange) }
            CanonRule(vertical = true)
            Box(propagateMinConstraints = true) {
                CanonButton("", Modifier.width(52.dp).fillMaxHeight(), icon = Icons.Outlined.Info, contentDescription = "Standalone") { tip = true }
                MenuCellPopup(tip, { tip = false }) {
                    Column(Modifier.widthIn(max = 300.dp).padding(CanonMetrics.panelPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Standalone", style = CanonText.bodySemibold, color = c.foreground)
                        Text(
                            "This sets whether or not to include HTML and HEAD elements so you can open the file in a browser. Leave it off to only generate tags to paste into an existing HTML file.",
                            style = CanonText.body,
                            color = c.mutedForeground,
                        )
                    }
                }
            }
        }
    }
}

private fun richString(runs: List<DocRun>, ink: Color, link: Color, underlinesLinks: Boolean): AnnotatedString = buildAnnotatedString {
    for (run in runs) {
        val style = SpanStyle(
            color = if (run.link != null) link else ink,
            fontSize = run.size.sp,
            fontWeight = if (run.bold) FontWeight.Bold else null,
            fontStyle = if (run.italic) FontStyle.Italic else null,
            fontFamily = if (run.mono) FontFamily.Monospace else null,
            textDecoration = if (run.link != null && underlinesLinks) TextDecoration.Underline else null,
        )
        withStyle(style) { append(run.text) }
    }
}

/** dt-ios CanonOutputText showing the RTF: the same paragraphs, set in Compose. */
@Composable
private fun RichText(paragraphs: List<DocParagraph>) {
    val c = LocalDelphiColours.current
    LazyColumn(Modifier.fillMaxWidth().height(360.dp).background(c.card), contentPadding = androidx.compose.foundation.layout.PaddingValues(CanonMetrics.panelPadding)) {
        items(paragraphs) { paragraph ->
            Text(
                richString(paragraph.runs, c.foreground, linkBlue, underlinesLinks = false),
                Modifier.padding(top = paragraph.spacingBefore.dp, bottom = paragraph.spacingAfter.dp),
                style = TextStyle(
                    fontSize = 12.sp,
                    textIndent = TextIndent(paragraph.firstIndent.sp, paragraph.headIndent.sp),
                ),
            )
        }
    }
}

private const val TWO_COLUMNS = 720
private val ledgerRow = 34.dp
private val ledgerHead = 30.dp
private val ghost = listOf(0.55f to 0.7f, 0.9f to 0.8f, 0.4f to 0.5f)

/** dt-ios DocConvertLedger: each kind of element in the source, its count, and how one is written before and after. */
@Composable
private fun DocLedger(blocks: List<DocBlock>, from: DocFormat, to: DocFormat) {
    val c = LocalDelphiColours.current
    val rows = remember(blocks) { DocConvertEngine.ledger(blocks) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(top = LocalCanonCanvas.current.top)) {
        val pad = CanonMetrics.panelPadding
        val columns = if (maxWidth.value >= TWO_COLUMNS) 2 else 1
        val count = if (rows.isEmpty()) ghost.size else rows.size
        val perColumn = (count + columns - 1) / columns
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = pad, end = pad, bottom = pad)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${from.label} → ${to.label}"
                    if (rows.isNotEmpty()) stateDescription = rows.joinToString(", ") { "${it.element.mark} ${it.count}" }
                },
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            for (column in 0 until columns) {
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth().height(ledgerHead).padding(top = 8.dp)) {
                        if (column == 0) {
                            Box(Modifier.width(28.dp))
                            Text(from.label, Modifier.weight(1f), style = CanonText.footnoteSemibold, color = c.mutedForeground, maxLines = 1)
                            Box(Modifier.width(24.dp))
                            Text(to.label, Modifier.weight(1f), style = CanonText.footnoteSemibold, color = c.mutedForeground, maxLines = 1)
                            Box(Modifier.width(44.dp))
                        }
                    }
                    for (index in column * perColumn until minOf(count, (column + 1) * perColumn)) {
                        if (rows.isEmpty()) GhostRow(ghost[index]) else LedgerRow(rows[index], from, to)
                    }
                }
            }
        }
    }
}

@Composable
private fun GhostRow(widths: Pair<Float, Float>) {
    val bar = LocalDelphiColours.current.mutedForeground.copy(alpha = 0.14f)
    Row(Modifier.fillMaxWidth().height(ledgerRow), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(28.dp))
        Box(Modifier.weight(1f)) { Box(Modifier.fillMaxWidth(widths.first).height(8.dp).background(bar)) }
        Box(Modifier.width(24.dp))
        Box(Modifier.weight(1f)) { Box(Modifier.fillMaxWidth(widths.second).height(8.dp).background(bar)) }
        Box(Modifier.width(44.dp))
    }
}

@Composable
private fun LedgerRow(row: DocLedgerRow, from: DocFormat, to: DocFormat) {
    val c = LocalDelphiColours.current
    val lost = DocConvertEngine.isLost(row.element, to)
    val rich = to == DocFormat.Rtf || to == DocFormat.Pdf
    Row(Modifier.fillMaxWidth().height(ledgerRow), verticalAlignment = Alignment.CenterVertically) {
        Text(row.element.mark, Modifier.width(28.dp), style = CanonText.captionSemibold, color = c.mutedForeground, maxLines = 1)
        Text(DocConvertEngine.sample(row.example, from), Modifier.weight(1f).padding(end = 8.dp), style = sampleMono, color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.width(24.dp).height(12.dp).padding(end = 6.dp), tint = c.mutedForeground)
        val ink = if (lost) c.mutedForeground.copy(alpha = 0.6f) else if (rich) c.foreground else c.primary
        val target = if (rich) {
            richString(DocConvertEngine.richSample(row.example), ink, if (lost) ink else c.primary, underlinesLinks = true)
        } else {
            AnnotatedString(DocConvertEngine.sample(row.example, to))
        }
        Text(target, Modifier.weight(1f).padding(end = 4.dp), style = sampleMono, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("×${String.format("%,d", row.count)}", Modifier.width(44.dp), style = countMono, color = c.mutedForeground, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
