package com.delphi.delphitools.tools.typography

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourWell
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonSwitchGrid
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.MenuCellItem
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.rememberCanonTextFilePicker
import com.delphi.delphitools.colour.rgbOf
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.tools.typography.TextDoc.Style
import com.delphi.delphitools.tools.typography.TextEditorEngine.Look
import com.delphi.delphitools.tools.typography.TextEditorEngine.Span
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DRAFT_FILE = "text-editor-draft.md"
private const val DRAFT_DELAY_MS = 1000L
private const val DIMMED = 0.28f
private val measure = 680.dp
private val fontNames = listOf("Sans", "Serif", "Mono")
private val families = listOf(FontFamily.SansSerif, FontFamily.Serif, FontFamily.Monospace)

// gutter: lead, labels, rule, then the gap before the text
private val gutterLead = 12.dp
private val gutterLabels = 40.dp
private val ruleX = 60.dp
private val gutterText = 12.dp

private fun rgb(red: Double, green: Double, blue: Double) = TextDoc.Rgb(red, green, blue)

private val presets = listOf(
    "Paper" to Style(Style.Family.Sans, 1.4, rgb(0.1, 0.1, 0.1), rgb(1.0, 1.0, 1.0)),
    "Classic" to Style(Style.Family.Serif, 1.6, rgb(0.17, 0.13, 0.09), rgb(0.97, 0.94, 0.87)),
    "Night" to Style(Style.Family.Sans, 1.5, rgb(0.91, 0.9, 0.87), rgb(0.09, 0.09, 0.1)),
    "Terminal" to Style(Style.Family.Mono, 1.5, rgb(0.3, 0.9, 0.45), rgb(0.03, 0.06, 0.04)),
)

private val TextDoc.Rgb.colour get() = Color(red.toFloat(), green.toFloat(), blue.toFloat())

private fun Style.fontFamily() = families[family.ordinal]

private fun com.delphi.delphitools.colour.Rgb.doc() = clamped.let { TextDoc.Rgb(it.r, it.g, it.b) }

/** dt-ios TextEditorViewModel and the controller's settings. */
class TextEditorModel(application: Application) : AndroidViewModel(application) {
    val state = TextFieldState()
    val text get() = state.text.toString()

    /** Blocks and Markdown marks of [parsedText]; a long text parses after typing settles. */
    var spans by mutableStateOf(emptyList<Span>())
        private set
    var marks by mutableStateOf(emptyList<TextEditorEngine.Mark>())
        private set
    var parsedText by mutableStateOf("")
        private set

    /** The text the counts and the preview show, and its blocks; typing settles first. */
    var settled by mutableStateOf("")
        private set
    var settledSpans by mutableStateOf(emptyList<Span>())
        private set
    var editorFont by mutableIntStateOf(0)
    var focus by mutableIntStateOf(0)
    var typewriter by mutableStateOf(false)
    var gutter by mutableStateOf(true)
    var style by mutableStateOf(presets[0].second)
    var previewShown by mutableStateOf(false)
    var fullScreen by mutableStateOf(false)

    private var saving: Job? = null
    private var settling: Job? = null
    private var loaded = false
    private val draft get() = File(getApplication<Application>().filesDir, DRAFT_FILE)

    init {
        viewModelScope.launch { snapshotFlow { state.text.toString() }.drop(1).collect(::changed) }
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { draft.takeIf { it.exists() }?.readText() }.getOrNull() }.orEmpty()
            loaded = true
            if (state.text.isEmpty()) {
                state.edit {
                    replace(0, length, saved)
                    selection = TextRange.Zero
                }
            }
        }
    }

    /** A whole replace; the caret goes to the end of what changed. */
    fun replace(new: String) {
        val old = text
        if (new == old) return
        state.edit {
            replace(0, length, new)
            selection = TextRange(TextEditorEngine.changeEnd(old, new))
        }
    }

    private fun parsed(now: String, blocks: List<Span>, found: List<TextEditorEngine.Mark>) {
        spans = blocks
        marks = found
        parsedText = now
    }

    private fun changed(now: String) {
        // the field highlights a short text itself
        if (now.length <= TextEditorEngine.LIVE_LIMIT) parsed(now, TextEditorEngine.spans(now), emptyList())
        settling?.cancel()
        settling = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            if (parsedText != now) {
                val (blocks, found) = withContext(Dispatchers.Default) { TextEditorEngine.spans(now) to TextEditorEngine.highlight(now) }
                parsed(now, blocks, found)
            }
            settled = now
            settledSpans = spans
        }
        saving?.cancel()
        saving = viewModelScope.launch {
            delay(DRAFT_DELAY_MS)
            write(now)
        }
    }

    private suspend fun write(text: String) = withContext(Dispatchers.IO) { runCatching { draft.writeText(text) } }

    /** The debounced save is lost when the process is killed. */
    fun saveNow() {
        if (!loaded) return
        saving?.cancel()
        val now = text
        CoroutineScope(Dispatchers.IO).launch { write(now) }
    }

    override fun onCleared() = saveNow()
}

@Composable
fun TextEditor(model: TextEditorModel = viewModel()) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity
    val scope = rememberCoroutineScope()
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.saveNow() }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
            model.saveNow()
        }
    }
    var anchor by remember { mutableStateOf<Int?>(null) }
    val empty = model.text.isEmpty()
    val picker = rememberCanonTextFilePicker(arrayOf("text/plain", "text/markdown", "text/x-markdown", "text/*")) { model.replace(it) }

    val canvas = CanonSection.canvas(CanonCanvasSize.Fixed(2000.dp)) {
        Manuscript(model, 17f, Modifier.fillMaxSize(), top = LocalCanonCanvas.current.top, onAnchor = { anchor = it })
    }

    val document = CanonSection.of {
        CanonRows {
            CanonHeading("Document")
            CanonGrid(columns = 2) {
                CanonButton("Files", icon = Icons.Outlined.Folder, onClick = picker::open)
                CanonButton("Clear", icon = Icons.Outlined.Delete, enabled = !empty) { model.replace("") }
                CanonButton("Full Screen", icon = Icons.Outlined.OpenInFull) { model.fullScreen = true }
                CanonButton("Preview", icon = Icons.Outlined.Visibility, selected = model.previewShown) { model.previewShown = !model.previewShown }
            }
            CanonGrid {
                var open by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    CanonButton("Share", Modifier.fillMaxSize(), icon = Icons.Outlined.IosShare, enabled = !empty) { open = true }
                    MenuCellPopup(open, { open = false }) {
                        val exports = listOf<Triple<String, String, String>>(
                            Triple("Markdown", "md", "text/markdown"),
                            Triple("HTML", "html", "text/html"),
                            Triple("Rich Text", "rtf", "application/rtf"),
                            Triple("PDF", "pdf", "application/pdf"),
                        )
                        exports.forEach { (title, ext, mime) ->
                            MenuCellItem(title) {
                                open = false
                                val text = model.text
                                val style = model.style
                                scope.launch {
                                    val data = withContext(Dispatchers.Default) {
                                        runCatching {
                                            when (ext) {
                                                "md" -> text.toByteArray()
                                                "html" -> TextDoc.html(DocConvertEngine.parseMarkdown(text), standalone = true, style = style).toByteArray()
                                                "rtf" -> TextDoc.rtf(DocConvertEngine.parseMarkdown(text), style).toByteArray(Charsets.US_ASCII)
                                                else -> pdf(DocConvertEngine.parseMarkdown(text), style)
                                            }
                                        }.getOrNull()
                                    } ?: return@launch
                                    FileExport.share(context, data, "document.$ext", mime)
                                }
                            }
                        }
                    }
                }
                CanonButton(
                    "Copy",
                    Modifier.weight(1f).fillMaxHeight(),
                    kind = CanonButtonKind.Primary,
                    enabled = !empty,
                    icon = Icons.Rounded.ContentCopy,
                    oversized = true,
                    flashesCopied = true,
                ) {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", model.text))
                }
            }
        }
    }

    val settledSpans = model.settledSpans
    val preview = CanonSection.of {
        CanonRows {
            CanonHeading("Preview")
            if (settledSpans.isEmpty()) CanonEmptyRow("None")
            else ManuscriptPreview(settledSpans.map { it.block }, model.style, anchor)
        }
    }

    val counts = CanonSection.of {
        CanonRows {
            val text = model.settled
            val number = remember { NumberFormat.getIntegerInstance() }
            CanonTiles(
                listOf(
                    CanonTile("Words", number.format(TextEditorEngine.words(text))),
                    CanonTile("Characters", number.format(TextEditorEngine.characters(text))),
                    CanonTile("Lines", number.format(TextEditorEngine.lines(text))),
                    CanonTile("Reading time", "${TextEditorEngine.readingMinutes(text)} min"),
                ),
            )
        }
    }

    val editorSettings = CanonSection.of {
        CanonRows {
            CanonHeading("Editor Settings")
            CanonRow("Font", flush = true) { CanonSegmented(fontNames, model.editorFont) { model.editorFont = it } }
            CanonRow("Focus", flush = true) { CanonSegmented(listOf("Off", "Sentence", "Paragraph"), model.focus) { model.focus = it } }
            CanonSwitchGrid(listOf("Typewriter" to model.typewriter, "Block marks" to model.gutter), columns = 2) { index, on ->
                if (index == 0) model.typewriter = on else model.gutter = on
            }
        }
    }

    val previewSettings = CanonSection.of {
        CanonRows {
            val style = model.style
            CanonHeading("Preview Settings")
            StylePicker(presets.indexOfFirst { it.second == style }.takeIf { it >= 0 }) { model.style = presets[it].second }
            CanonRow("Font", flush = true) {
                CanonSegmented(fontNames, style.family.ordinal) { model.style = style.copy(family = Style.Family.entries[it]) }
            }
            CanonRow("Line Height", flush = true) {
                CanonSlider(
                    style.lineHeight.toFloat(),
                    { model.style = model.style.copy(lineHeight = it.toDouble()) },
                    range = 1f..2f,
                    step = 0.05f,
                    readout = PxToRemEngine.format((style.lineHeight * 100).roundToInt() / 100.0) + "×",
                )
            }
            CanonGrid(columns = 2) {
                CanonColourWell(rgbOf(style.text.colour.toArgb()), { model.style = model.style.copy(text = it.doc()) }, label = "Text")
                CanonColourWell(rgbOf(style.background.colour.toArgb()), { model.style = model.style.copy(background = it.doc()) }, label = "Background")
            }
        }
    }

    CanonSplit(
        sidebar = listOf(canvas),
        main = listOfNotNull(document, preview.takeIf { model.previewShown }, counts, editorSettings, previewSettings),
        halves = true,
        collapsesTo = 180.dp,
    )

    if (model.fullScreen) FullScreenEditor(model) { model.fullScreen = false }
}

/** The presets as swatches set in their own look; the chosen one is ringed. */
@Composable
private fun StylePicker(selected: Int?, onSelect: (Int) -> Unit) {
    val c = LocalDelphiColours.current
    CanonGrid(columns = presets.size) {
        presets.forEachIndexed { index, (name, style) ->
            Column(
                Modifier
                    .background(style.background.colour)
                    .then(if (index == selected) Modifier.border(3.dp, c.primary) else Modifier)
                    .canonClickable { if (index != selected) onSelect(index) }
                    .semantics {
                        contentDescription = name
                        this.selected = index == selected
                    }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Aa", style = TextStyle(fontFamily = style.fontFamily(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = style.text.colour, maxLines = 1)
                Text(name, style = CanonText.captionSemibold, color = style.text.colour.copy(alpha = 0.75f), maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * TextEditorHighlighter as an output transformation: Markdown syntax styled in place, offsets unchanged.
 * A short text is highlighted as it is typed; a long one takes [marks], which apply only to [parsed].
 */
private fun highlight(parsed: String, marks: List<TextEditorEngine.Mark>, base: Float, family: FontFamily, muted: Color, primary: Color, codeBackground: Color) = OutputTransformation {
    val shown = when {
        length <= TextEditorEngine.LIVE_LIMIT -> TextEditorEngine.highlight(asCharSequence().toString())
        asCharSequence().contentEquals(parsed) -> marks
        else -> return@OutputTransformation
    }
    for (mark in shown) {
        val style = when {
            mark.level > 0 -> SpanStyle(fontSize = (base * TextEditorEngine.editorHeadingScale[min(mark.level, 6) - 1]).sp, fontWeight = FontWeight.Bold)
            mark.look == Look.Muted -> SpanStyle(color = muted)
            mark.look == Look.Primary -> SpanStyle(color = primary)
            mark.look == Look.Code -> SpanStyle(fontFamily = FontFamily.Monospace, fontSize = (base * 0.95).sp, background = codeBackground)
            mark.look == Look.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
            mark.look == Look.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
            mark.look == Look.Quote -> SpanStyle(color = muted, fontStyle = FontStyle.Italic, fontFamily = family)
            else -> continue
        }
        addStyle(style, mark.start, mark.end)
    }
}

/**
 * ManuscriptView: the Markdown editor, a block mark and word count for each block in a gutter beside the text,
 * the focus veil over all but the sentence or paragraph being written, and the typewriter's centred caret.
 */
@Composable
private fun Manuscript(
    model: TextEditorModel,
    baseSize: Float,
    modifier: Modifier,
    top: Dp = 0.dp,
    maxMeasure: Dp? = null,
    focusRequester: FocusRequester? = null,
    onAnchor: (Int?) -> Unit = {},
) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var focused by remember { mutableStateOf(false) }
    val text = model.state.text.toString()
    val selection = model.state.selection
    val spans = model.spans
    val family = families[model.editorFont]
    val gutter = model.gutter
    val anchorTo by rememberUpdatedState(onAnchor)

    BoxWithConstraints(modifier.background(c.card).padding(top = top)) {
        val viewport = constraints.maxHeight
        val number = remember { NumberFormat.getIntegerInstance() }
        val base = if (gutter) ruleX + gutterText else CanonMetrics.panelPadding
        val shift = maxMeasure?.let { max(0f, ((maxWidth - base - CanonMetrics.panelPadding - it) / 2).value).roundToInt().dp } ?: 0.dp
        val leading = base + shift
        val topPad = 16.dp

        val styled = remember(model.parsedText, model.marks, family, c, baseSize) {
            highlight(model.parsedText, model.marks, baseSize, family, c.mutedForeground, c.primary, c.muted)
        }
        val caret = selection.start
        val active = if (!focused) null else TextEditorEngine.block(caret, spans)?.takeIf { spans[it].end >= caret }
        val anchor by remember(spans, active) {
            derivedStateOf {
                active ?: layout?.let { result ->
                    val y = scroll.value - with(density) { topPad.toPx() } + with(density) { 16.dp.toPx() }
                    val offset = result.getOffsetForPosition(Offset(0f, max(0f, y)))
                    TextEditorEngine.block(offset, spans) ?: if (spans.isEmpty()) null else 0
                }
            }
        }
        LaunchedEffect(anchor) { anchorTo(anchor) }
        LaunchedEffect(selection, layout, model.typewriter, focused, viewport) {
            val result = layout ?: return@LaunchedEffect
            if (!model.typewriter || !focused) return@LaunchedEffect
            val rect = result.getCursorRect(selection.end.coerceIn(0, result.layoutInput.text.length))
            val target = (with(density) { topPad.toPx() } + rect.center.y - viewport / 2f).roundToInt()
            scroll.animateScrollTo(target.coerceIn(0, scroll.maxValue))
        }

        val markStyle = CanonText.captionSemibold
        val countStyle = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp)
        val focusMode = model.focus
        // a scrolled parent measures the field at full height, past the 262,143 px constraint limit
        BasicTextField(
            model.state,
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .drawBehind {
                    if (gutter) drawRect(c.border, Offset((shift + ruleX).toPx(), 0f), Size(CanonMetrics.hairline.toPx(), size.height))
                }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = "Text" }
                .drawWithContent {
                    drawContent()
                    val result = layout ?: return@drawWithContent
                    val origin = Offset(leading.toPx(), topPad.toPx() - scroll.value)
                    if (focusMode != 0 && focused) {
                        val length = text.length
                        val block = active?.let { spans.getOrNull(it) }
                        val lit = if (block != null) block.start..min(block.end, length) else paragraph(text, min(caret, length))
                        val range = if (focusMode == 1) TextEditorEngine.sentence(caret, text, lit.first, lit.last)?.let { it.first..(it.last + 1) } ?: lit else lit
                        val veil = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(origin.x - 4.dp.toPx(), 0f, size.width, size.height))
                            if (range.last > range.first) addPath(result.getPathForRange(range.first, range.last), origin)
                        }
                        drawPath(veil, c.card.copy(alpha = 1 - DIMMED))
                    }
                    if (gutter && spans.isNotEmpty()) {
                        val from = -viewport
                        val to = viewport * 2
                        val width = (gutterLead + gutterLabels).roundToPx()
                        for ((index, span) in spans.withIndex()) {
                            if (span.start > result.layoutInput.text.length) break
                            val line = result.getLineForOffset(span.start)
                            val middle = origin.y + (result.getLineTop(line) + result.getLineBottom(line)) / 2
                            if (middle < from) continue
                            if (middle > to) break
                            val lit = index == active
                            val ink = if (lit) c.primary else c.mutedForeground
                            val label = buildAnnotatedString {
                                withStyle(markStyle.toSpanStyle().copy(color = ink)) { append(span.block.mark) }
                                val words = TextEditorEngine.words(span.block.text)
                                if (words > 0) {
                                    withStyle(countStyle.toSpanStyle().copy(color = ink.copy(alpha = if (lit) 0.8f else 0.7f))) {
                                        append("\n" + number.format(words))
                                    }
                                }
                            }
                            val measured = measurer.measure(label, TextStyle(textAlign = TextAlign.End, lineHeight = 13.sp), constraints = Constraints.fixedWidth(width))
                            val markHeight = measurer.measure(span.block.mark, markStyle).size.height
                            translate(shift.toPx(), (middle - markHeight / 2f).roundToInt().toFloat()) { drawText(measured) }
                        }
                    }
                },
            textStyle = TextStyle(fontFamily = family, fontSize = baseSize.sp, color = c.foreground),
            cursorBrush = SolidColor(c.primary),
            outputTransformation = styled,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = true),
            onTextLayout = { result -> layout = result() },
            scrollState = scroll,
            decorator = { inner ->
                Box(Modifier.padding(start = leading, top = topPad, end = CanonMetrics.panelPadding + shift, bottom = 32.dp)) {
                    if (text.isEmpty()) {
                        Text(
                            "The Karman Institute for Planetary Observation has confirmed that Earth is no longer able to sustain organic life. After decades of atmospheric collapse, sensor data from the Donna Shirley Space Telescope indicates that oxygen and nitrogen concentrations in the atmosphere have reached irreversible levels of depletion. Carbon dioxide levels, combined with solar radiation and the absence of a protective ozone layer, have rendered the surface barren. The final viable microbial traces recorded last year by automated spectroscopic satellites have now vanished.",
                            style = TextStyle(fontFamily = family, fontSize = baseSize.sp),
                            color = c.mutedForeground,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/** The paragraph around `caret`, as NSString.paragraphRange: up to and including its line break. */
private fun paragraph(text: String, caret: Int): IntRange {
    val start = text.lastIndexOf('\n', caret - 1).let { if (it < 0) 0 else it + 1 }
    val end = text.indexOf('\n', caret).let { if (it < 0) text.length else it + 1 }
    return start..end
}

/** ManuscriptPreview: the blocks set in the preview style, scrolled to the block being written. */
@Composable
private fun ManuscriptPreview(blocks: List<DocBlock>, style: Style, anchor: Int?) {
    val list = rememberLazyListState()
    val screen = LocalConfiguration.current.screenHeightDp
    val height = min(640, max(280, (screen * 0.55f).roundToInt())).dp
    LaunchedEffect(anchor, blocks.size) {
        if (anchor != null && anchor in blocks.indices) list.animateScrollToItem(anchor)
    }
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(style.background.colour)
            .semantics { contentDescription = "Preview" },
        state = list,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = CanonMetrics.panelPadding, top = 16.dp, end = CanonMetrics.panelPadding, bottom = 24.dp),
    ) {
        itemsIndexed(blocks) { _, block -> PreviewBlock(block, style, 15f) }
    }
}

@Composable
private fun PreviewBlock(block: DocBlock, style: Style, body: Float) {
    val ink = style.text.colour
    val family = style.fontFamily()
    fun runs(runs: List<DocInline>, size: Float, bold: Boolean, italic: Boolean) = buildAnnotatedString {
        for (run in runs) {
            val span = if (run.code) SpanStyle(fontFamily = FontFamily.Monospace, fontSize = (size * 0.9f).sp)
            else SpanStyle(
                fontWeight = if (run.bold || bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (run.italic || italic) FontStyle.Italic else FontStyle.Normal,
            )
            withStyle(if (run.link != null) span.copy(textDecoration = TextDecoration.Underline) else span) { append(run.text) }
        }
    }
    fun text(size: Float) = TextStyle(fontFamily = family, fontSize = size.sp, color = ink, lineHeight = (size * 1.2f * style.lineHeight.toFloat()).sp)
    when (block) {
        is DocBlock.Heading -> {
            val size = body * TextDoc.headingSizes[block.level.coerceIn(1, 6) - 1].toFloat()
            Text(runs(block.runs, size, bold = true, italic = false), Modifier.padding(top = body.dp, bottom = (body * 0.8f).dp), style = text(size))
        }
        is DocBlock.Paragraph -> Text(runs(block.runs, body, bold = false, italic = false), Modifier.padding(bottom = (body * 0.8f).dp), style = text(body))
        is DocBlock.Quote -> Text(runs(block.runs, body, bold = false, italic = true), Modifier.padding(start = (body * 2).dp, bottom = (body * 0.8f).dp), style = text(body))
        is DocBlock.Bullets -> {
            val joined = buildAnnotatedString {
                block.items.forEachIndexed { offset, item ->
                    withStyle(ParagraphStyle(textIndent = TextIndent(body.sp, (body * 2).sp))) {
                        append(runs(listOf(DocInline(if (block.ordered) "${offset + 1}. " else "•  ")) + item, body, bold = false, italic = false))
                    }
                }
            }
            Text(joined, Modifier.padding(bottom = (body * 0.8f).dp), style = text(body))
        }
        is DocBlock.Code -> Text(
            block.body,
            Modifier.padding(start = body.dp, bottom = (body * 0.8f).dp),
            style = text(body).copy(fontFamily = FontFamily.Monospace, fontSize = (body * 0.9f).sp),
        )
        DocBlock.Rule -> Text("─".repeat(5), Modifier.padding(bottom = (body * 0.8f).dp), style = text(body).copy(color = ink.copy(alpha = 0.5f)))
    }
}

/** DocConvert.pdfData: A4 with 48 pt margins, an 11 pt body, paginated by line. */
private fun pdf(blocks: List<DocBlock>, style: Style, body: Float = 11f): ByteArray {
    val pageWidth = 595
    val pageHeight = 842
    val margin = 48f
    val width = (pageWidth - margin * 2).toInt()
    val ink = style.text.colour.toArgb()
    val paper = style.background.colour.toArgb()
    val face = when (style.family) {
        Style.Family.Sans -> Typeface.SANS_SERIF
        Style.Family.Serif -> Typeface.SERIF
        Style.Family.Mono -> Typeface.MONOSPACE
    }
    val document = PdfDocument()
    var page: PdfDocument.Page? = null
    var y = margin

    fun newPage(number: Int) {
        page?.let(document::finishPage)
        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, number).create()).also { it.canvas.drawColor(paper) }
        y = margin
    }

    fun spanned(runs: List<DocInline>, italic: Boolean): CharSequence {
        val out = SpannableStringBuilder()
        for (run in runs) {
            val start = out.length
            out.append(run.text)
            val end = out.length
            if (run.code) {
                out.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(RelativeSizeSpan(0.9f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                continue
            }
            val slant = run.italic || italic
            val kind = when {
                run.bold && slant -> Typeface.BOLD_ITALIC
                run.bold -> Typeface.BOLD
                slant -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (kind != Typeface.NORMAL) out.setSpan(StyleSpan(kind), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (run.link != null) {
                out.setSpan(ForegroundColorSpan(0xFF007AFF.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return out
    }

    fun place(text: CharSequence, size: Float, bold: Boolean = false, typeface: Typeface = face, colour: Int = ink, before: Float = 0f, after: Float = body * 0.8f, left: Float = 0f, rest: Float = left) {
        if (text.isEmpty()) return
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.typeface = Typeface.create(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
            color = colour
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIndents(intArrayOf(left.roundToInt(), rest.roundToInt()), null)
            .setLineSpacing(0f, style.lineHeight.toFloat())
            .build()
        y += before
        for (line in 0 until layout.lineCount) {
            val lineTop = layout.getLineTop(line).toFloat()
            val lineBottom = layout.getLineBottom(line).toFloat()
            if (y + lineBottom - lineTop > pageHeight - margin && y > margin) newPage(document.pages.size + 2)
            val canvas = page!!.canvas
            canvas.save()
            canvas.translate(margin, y - lineTop)
            canvas.clipRect(0f, lineTop, width.toFloat(), lineBottom)
            layout.draw(canvas)
            canvas.restore()
            y += lineBottom - lineTop
        }
        y += after
    }

    newPage(1)
    for (block in blocks) {
        when (block) {
            is DocBlock.Heading -> {
                val size = body * TextDoc.headingSizes[block.level.coerceIn(1, 6) - 1].toFloat()
                place(spanned(block.runs, false), size, bold = true, before = body)
            }
            is DocBlock.Paragraph -> place(spanned(block.runs, false), body)
            is DocBlock.Quote -> place(spanned(block.runs, true), body, left = body * 2)
            is DocBlock.Bullets -> block.items.forEachIndexed { offset, item ->
                place(spanned(listOf(DocInline(if (block.ordered) "${offset + 1}. " else "•  ")) + item, false), body, after = body * 0.3f, left = body, rest = body * 2)
            }
            is DocBlock.Code -> place(block.body, body * 0.9f, typeface = Typeface.MONOSPACE, left = body)
            DocBlock.Rule -> place("─".repeat(5), body, colour = (ink and 0x00FFFFFF) or (0x80 shl 24))
        }
    }
    page?.let(document::finishPage)
    return ByteArrayOutputStream().use { out ->
        document.writeTo(out)
        document.close()
        out.toByteArray()
    }
}

/** The editor alone at a 20 pt body, the column held to a readable measure. The close button writes the text back. */
@Composable
private fun FullScreenEditor(model: TextEditorModel, onClose: () -> Unit) {
    val c = LocalDelphiColours.current
    val focus = remember { FocusRequester() }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.statusBars())
            }
            onDispose {}
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Box(Modifier.fillMaxSize().background(c.card).imePadding()) {
            Manuscript(model, 20f, Modifier.fillMaxSize().padding(top = 48.dp), maxMeasure = measure, focusRequester = focus)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 8.dp)
                    .size(44.dp)
                    .canonClickable(onClick = onClose)
                    .semantics {
                        contentDescription = "Close"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = c.mutedForeground.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
            }
        }
    }
}
