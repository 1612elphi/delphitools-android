package com.delphi.delphitools.tools.devtools

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonFileCell
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTextInput
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.canonMathInks
import com.delphi.delphitools.canon.decodeTextFile
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.pow

private const val EDITABLE_LIMIT = 1_000_000
private const val TREE_LIMIT = 5000

// ponytail: the listing colours the whole preview; laying out visible lines only lifts this cap
private const val PREVIEW_LIMIT = 100_000

// ponytail: the parsed tree is several times the file size; a streaming parser lifts this cap
private const val MAX_FILE_BYTES = 32L shl 20

private fun InputStream.readAtMost(limit: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), limit - out.size()).toInt())
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

// stays under layout limit
private const val PIECE = 1000
private const val DASH = "—"

private val listingFont = TextStyle(fontFamily = DelphiMono, fontSize = 13.sp)
private val gutterFont = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp)

class JsonFormatted(val text: String, val clippedPreview: String?, val tokens: List<Pair<TextSpan, JsonToken>>, val rows: List<JsonTreeRow>, val shape: JsonShape)

sealed interface JsonFormatterOutput {
    data object Empty : JsonFormatterOutput
    class Invalid(val error: JsonParseError) : JsonFormatterOutput
    class Valid(val result: JsonFormatted) : JsonFormatterOutput
}

/** dt-ios JSONFormatterViewModel, with the picture's focus and selection. */
class JsonFormatterModel(application: Application) : AndroidViewModel(application) {
    /** What the text area shows; empty for a source past the editable limit. */
    var draft by mutableStateOf("")
        private set
    var source by mutableStateOf("")
        private set
    var indent by mutableStateOf(JsonIndent.Two)
        private set
    var tree by mutableStateOf(false)
        private set
    var output by mutableStateOf<JsonFormatterOutput>(JsonFormatterOutput.Empty)
        private set
    var fileName by mutableStateOf<String?>(null)
        private set

    /** Bumped when the source clears, so the file cell forgets its name. */
    var clears by mutableIntStateOf(0)
        private set

    /** The picture: its zoomed shape, the focus path, the selected path and its listing line. */
    var shape by mutableStateOf<JsonShape?>(null)
        private set
    var focus by mutableStateOf("root")
        private set
    var selected by mutableStateOf<String?>(null)
        private set
    var selectedLine by mutableStateOf<Int?>(null)
        private set

    /** Bumped by a picture tap, to scroll the selection into view. */
    var reveals by mutableIntStateOf(0)
        private set

    private var collapsed = setOf<String>()
    private var parsed: JsonParsed? = null
    private var job: Job? = null
    private var zooming: Job? = null

    val downloadName: String
        get() {
            val name = fileName ?: "formatted.json"
            return if (name.endsWith(".json")) name else "$name.json"
        }

    fun edit(text: String) {
        draft = text
    }

    fun type(text: String) {
        if (text.isEmpty()) clear() else setSource(text, keepFile = true)
    }

    fun chooseIndent(value: JsonIndent) {
        indent = value
        refresh()
    }

    fun chooseTree(value: Boolean) {
        tree = value
        refresh()
    }

    private fun setSource(text: String, keepFile: Boolean) {
        if (!keepFile) fileName = null
        source = text
        draft = if (text.length <= EDITABLE_LIMIT) text else ""
        parsed = null
        zooming?.cancel()
        focus = "root"
        selected = null
        selectedLine = null
        refresh()
    }

    fun clear() {
        collapsed = emptySet()
        fileName = null
        clears++
        setSource("", keepFile = false)
    }

    fun load(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null }
                    if (size != null && size > MAX_FILE_BYTES) return@runCatching null
                    // providers may omit size
                    val bytes = resolver.openInputStream(uri)?.use { it.readAtMost(MAX_FILE_BYTES + 1) } ?: return@runCatching null
                    if (bytes.size > MAX_FILE_BYTES) null else decodeTextFile(bytes)
                }.getOrNull()
            } ?: return@launch
            val name = withContext(Dispatchers.IO) { resolver.canonDisplayName(uri) }
            collapsed = emptySet()
            setSource(read, keepFile = true)
            fileName = name
        }
    }

    private fun expand(path: String) {
        val above = collapsed.filter { path.startsWith("$it.") }
        if (above.isEmpty()) return
        collapsed = collapsed - above.toSet()
        refresh()
    }

    fun toggle(path: String) {
        collapsed = if (path in collapsed) collapsed - path else collapsed + path
        refresh()
    }

    private fun refresh() {
        job?.cancel()
        val source = source
        val known = parsed
        val indent = indent
        val rowsFor = if (tree) collapsed else null
        job = viewModelScope.launch {
            val (result, next) = runInterruptible(Dispatchers.Default) { compute(source, known, indent, rowsFor) }
            parsed = result
            output = next
            val valid = (next as? JsonFormatterOutput.Valid)?.result
            if (valid != null && focus != "root") rezoom() else shape = valid?.shape
        }
    }

    private fun compute(source: String, known: JsonParsed?, indent: JsonIndent, collapsed: Set<String>?): Pair<JsonParsed?, JsonFormatterOutput> {
        if (source.isBlank()) return null to JsonFormatterOutput.Empty
        return when (val result = known ?: JsonFormatEngine.parse(source)) {
            is JsonParsed.Invalid -> result to JsonFormatterOutput.Invalid(result.error)
            is JsonParsed.Valid -> {
                val text = JsonFormatEngine.format(result.value, indent)
                val preview = if (text.toByteArray().size > PREVIEW_LIMIT) text.take(PREVIEW_LIMIT) + "\n…" else null
                result to JsonFormatterOutput.Valid(
                    JsonFormatted(
                        text,
                        preview,
                        if (collapsed == null) JsonListing.tokens(preview ?: text) else emptyList(),
                        collapsed?.let { JsonFormatEngine.rows(result.value, it) } ?: emptyList(),
                        JsonShape.of(result.value),
                    ),
                )
            }
        }
    }

    private fun rezoom() {
        val value = (parsed as? JsonParsed.Valid)?.value ?: return
        val focus = focus
        zooming?.cancel()
        zooming = viewModelScope.launch {
            val zoomed = runInterruptible(Dispatchers.Default) { JsonShape.of(value, focus) }
            if (this@JsonFormatterModel.focus == focus) shape = zoomed
        }
    }

    fun share(context: Context) {
        val text = (output as? JsonFormatterOutput.Valid)?.result?.text ?: return
        viewModelScope.launch { FileExport.share(context, text.toByteArray(), downloadName, "application/json") }
    }

    fun tap(cell: JsonShapeCell) {
        val target = when {
            cell.path == focus && focus != "root" -> JsonShape.parent(focus).also { focus = it }
            cell.opens && cell.path != focus -> cell.path.also { focus = it }
            else -> cell.path
        }
        selected = target
        selectedLine = shape?.cells?.firstOrNull { it.path == target }?.line
        rezoom()
        if (tree) expand(target)
        reveals++
    }
}

@Composable
fun JsonFormatter(model: JsonFormatterModel = viewModel()) {
    val context = LocalContext.current
    val view = LocalView.current
    val output = model.output
    val valid = (output as? JsonFormatterOutput.Valid)?.result
    val failure = (output as? JsonFormatterOutput.Invalid)?.error
    val inks = jsonInks()

    val source = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            CanonTextInput(model.draft, model::edit, model::type, height = 240.dp, label = "Source")
            CanonRow("File", flush = true) {
                key(model.clears) { CanonFileCell(model::load, mimeTypes = arrayOf("application/json", "text/plain")) }
            }
        }
    }

    val settings = CanonSection.of {
        CanonRows {
            CanonHeading("Indent")
            CanonSegmented(JsonIndent.entries.map { it.label }, model.indent.ordinal, Modifier.semantics { contentDescription = "Indent" }) {
                model.chooseIndent(JsonIndent.entries[it])
            }
            CanonHeading("View")
            CanonSegmented(listOf("Text", "Tree"), if (model.tree) 1 else 0, Modifier.semantics { contentDescription = "View" }) {
                model.chooseTree(it == 1)
            }
        }
    }

    val facts = CanonSection.of {
        CanonRows {
            val shape = valid?.shape
            CanonTiles(
                listOf(
                    CanonTile("Size", valid?.let { Formatter.formatShortFileSize(context, it.text.toByteArray().size.toLong()) } ?: DASH),
                    CanonTile("Depth", shape?.let { "%,d".format(it.depth) } ?: DASH),
                    CanonTile("Keys", shape?.let { "%,d".format(it.keys) } ?: DASH),
                    CanonTile("Values", shape?.let { "%,d".format(it.values) } ?: DASH),
                ),
            )
        }
    }

    val result = CanonSection.of {
        CanonRows {
            val c = LocalDelphiColours.current
            CanonHeading("Result")
            if (failure != null) {
                CanonStatusRow(failure.message + "  ·  ${failure.line}:${failure.column}", CanonTone.Warn)
                val line = failure.excerpt.ifEmpty { " " }
                val range = JsonListing.caretRange(failure)
                val marks = if (range != null) {
                    TextMarks(spans = listOf(TextMark(range, c.destructive.copy(alpha = 0.18f))), squiggles = listOf(TextMark(range, c.destructive)))
                } else {
                    TextMarks(carets = listOf(TextCaret(JsonListing.caretOffset(failure), c.destructive)))
                }
                MarkedText(line, marks, listingFont, wraps = false, background = c.muted)
            }
            if (valid != null) {
                if (model.tree) {
                    JsonTree(valid.rows, model.selected, model.reveals, inks, model::toggle)
                } else {
                    val line = if (model.indent == JsonIndent.Minify) null else model.selectedLine
                    JsonListingView(valid.clippedPreview ?: valid.text, valid.tokens, line, model.reveals, inks)
                }
            }
            if (valid == null && failure == null) CanonEmptyRow("None")
            CanonGrid {
                CanonButton("Share", Modifier.weight(1f).fillMaxHeight(), kind = CanonButtonKind.Secondary, enabled = valid != null, icon = Icons.Outlined.Share) {
                    model.share(context)
                }
                CanonButton(
                    "Copy",
                    Modifier.weight(1f).fillMaxHeight(),
                    kind = CanonButtonKind.Primary,
                    enabled = valid != null,
                    icon = Icons.Outlined.ContentCopy,
                    oversized = true,
                    flashesCopied = true,
                ) {
                    val text = valid?.text ?: return@CanonButton
                    // clip past binder throws
                    runCatching { context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("JSON", text)) }
                }
            }
        }
    }

    val banner = CanonSection.canvas(CanonCanvasSize.Fitting) {
        JsonShapePicture(model.shape, failure != null, model.selected, inks) { cell ->
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            model.tap(cell)
        }
    }

    CanonSplit(sidebar = listOf(source, settings), main = listOf(facts, result), banner = banner, collapsesTo = 112.dp)
}

private class JsonInks(val object_: Color, val array: Color, val string: Color, val number: Color, val literal: Color, val foreground: Color) {
    fun of(kind: JsonKind) = when (kind) {
        JsonKind.Object -> object_
        JsonKind.Array -> array
        JsonKind.Str -> string
        JsonKind.Number -> number
        JsonKind.Literal -> literal
    }
}

@Composable
private fun jsonInks(): JsonInks {
    val c = LocalDelphiColours.current
    val math = canonMathInks()
    return remember(c, math) { JsonInks(c.primary, math.yellow, math.clay, math.blue, c.mutedForeground, c.foreground) }
}

private class ListingRow(val number: Int?, val start: Int, val end: Int)

// dt-ios JSONListing.text
@Composable
private fun JsonListingView(text: String, tokens: List<Pair<TextSpan, JsonToken>>, selectedLine: Int?, reveals: Int, inks: JsonInks) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val rows = remember(text) {
        val out = mutableListOf<ListingRow>()
        var start = 0
        var number = 1
        while (start <= text.length) {
            val newline = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            var from = start
            do {
                val to = minOf(from + PIECE, newline)
                out += ListingRow(if (from == start) number else null, from, to)
                from = to
            } while (from < newline)
            number++
            start = newline + 1
        }
        out
    }
    val lines = rows.count { it.number != null }
    val digits = lines.toString().length
    val charWidth = remember(density) { with(density) { measurer.measure("0", listingFont).size.width.toDp() } }
    val gutterWidth = remember(density, digits) { with(density) { measurer.measure("0".repeat(digits), gutterFont).size.width.toDp() } }
    val widest = rows.maxOf { it.end - it.start }
    val pad = CanonMetrics.panelPadding
    val contentWidth = pad + gutterWidth + 12.dp + charWidth * widest + pad
    val list = rememberLazyListState()
    val highlight = c.primary.copy(alpha = 0.16f)
    val selectedRows = remember(rows, selectedLine) {
        if (selectedLine == null) IntRange.EMPTY else {
            val first = rows.indexOfFirst { it.number == selectedLine + 1 }
            if (first < 0) IntRange.EMPTY else first until (rows.drop(first + 1).indexOfFirst { it.number != null }.let { if (it < 0) rows.size else first + 1 + it })
        }
    }
    LaunchedEffect(reveals) {
        if (reveals > 0 && !selectedRows.isEmpty()) list.animateScrollToItem(maxOf(0, selectedRows.first - 3))
    }

    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 640.dp).background(c.card).semantics { contentDescription = "Result" }) {
        val width = maxOf(contentWidth, maxWidth)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            val rule = c.border
            LazyColumn(
                Modifier.width(width).drawBehind {
                    val x = (pad + gutterWidth + 6.dp).toPx()
                    drawRect(rule, Offset(x, 0f), Size(CanonMetrics.hairline.toPx(), size.height))
                },
                state = list, contentPadding = PaddingValues(vertical = 12.dp)) {
                itemsIndexed(rows) { index, row ->
                    Row(Modifier.width(width).background(if (index in selectedRows) highlight else Color.Transparent).padding(horizontal = pad)) {
                        Text(
                            row.number?.toString()?.padStart(digits) ?: "",
                            Modifier.width(gutterWidth).padding(top = 2.dp),
                            style = gutterFont,
                            color = c.mutedForeground,
                            maxLines = 1,
                        )
                        Text(
                            colouredRun(text, tokens, row.start, row.end, inks),
                            Modifier.padding(start = 12.dp),
                            style = listingFont,
                            color = c.foreground,
                            softWrap = false,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun colouredRun(text: String, tokens: List<Pair<TextSpan, JsonToken>>, start: Int, end: Int, inks: JsonInks): AnnotatedString {
    var low = 0
    var high = tokens.size
    while (low < high) {
        val mid = (low + high) / 2
        if (tokens[mid].first.end <= start) low = mid + 1 else high = mid
    }
    return buildAnnotatedString {
        append(text, start, end)
        var index = low
        while (index < tokens.size && tokens[index].first.start < end) {
            val (span, token) = tokens[index]
            val colour = when (token) {
                JsonToken.Key -> inks.object_
                JsonToken.Punctuation -> inks.literal
                is JsonToken.Value -> inks.of(token.kind)
            }
            addStyle(SpanStyle(color = colour), maxOf(span.start, start) - start, minOf(span.end, end) - start)
            index++
        }
    }
}

private fun kindOf(display: String) = when (display.firstOrNull()) {
    '"' -> JsonKind.Str
    't', 'f', 'n' -> JsonKind.Literal
    else -> JsonKind.Number
}

// dt-ios JSONListing.tree
@Composable
private fun JsonTree(rows: List<JsonTreeRow>, selected: String?, reveals: Int, inks: JsonInks, onToggle: (String) -> Unit) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val list = rememberLazyListState()
    val shown = rows.take(TREE_LIMIT)
    val selectedIndex = shown.indexOfFirst { it.id == selected }
    val highlight = c.primary.copy(alpha = 0.16f)
    LaunchedEffect(reveals, selectedIndex) {
        if (reveals > 0 && selectedIndex >= 0) list.animateScrollToItem(maxOf(0, selectedIndex - 3))
    }
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 640.dp).background(c.card).semantics { contentDescription = "Tree" },
        state = list,
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        itemsIndexed(shown, key = { _, row -> row.id }) { index, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (index == selectedIndex) highlight else Color.Transparent)
                    .then(
                        if (row.isContainer) Modifier.clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onToggle(row.id)
                        }.semantics { stateDescription = if (row.isCollapsed) "Collapsed" else "Expanded" } else Modifier,
                    )
                    .padding(start = CanonMetrics.panelPadding + (16 * row.depth).dp, end = CanonMetrics.panelPadding, top = 6.dp, bottom = 6.dp),
            ) {
                Text(if (!row.isContainer) "" else if (row.isCollapsed) "▸" else "▾", Modifier.width(20.dp), style = listingFont, color = c.mutedForeground)
                Text(
                    buildAnnotatedString {
                        if (row.key != null) {
                            withStyle(SpanStyle(color = inks.object_, fontWeight = FontWeight.SemiBold)) { append(row.key) }
                            append(": ")
                        }
                        withStyle(SpanStyle(color = if (row.isContainer) inks.literal else inks.of(kindOf(row.display)))) { append(row.display) }
                    },
                    style = listingFont,
                    color = c.mutedForeground,
                )
            }
        }
        if (rows.size > TREE_LIMIT) {
            item { Text("…", Modifier.padding(horizontal = CanonMetrics.panelPadding), style = listingFont, color = c.mutedForeground) }
        }
    }
}

private class Viewport(val start: Double = 0.0, val end: Double = 1.0, val depth: Double = 0.0, val levels: Double = 1.0) {
    fun mixed(other: Viewport, share: Double) = Viewport(
        start + (other.start - start) * share,
        end + (other.end - end) * share,
        depth + (other.depth - depth) * share,
        levels + (other.levels - levels) * share,
    )

    companion object {
        fun of(shape: JsonShape?): Viewport {
            val focus = shape?.focusCell ?: return Viewport()
            return Viewport(focus.start, focus.end, focus.depth.toDouble(), shape.levels.toDouble())
        }
    }
}

private val cubicInOut = Easing { t -> if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).toDouble().pow(3).toFloat() / 2 }
private val shapeLabel = TextStyle(fontFamily = DelphiFont, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
private val shapeDetail = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp)
private val rowHeight = 26.dp
private val legendNames = listOf(
    JsonKind.Object to "Object",
    JsonKind.Array to "Array",
    JsonKind.Str to "String",
    JsonKind.Number to "Number",
    JsonKind.Literal to "true false null",
)

private class Zoom(val from: Viewport, val leaving: List<JsonShapeCell>, val leavingPaths: Set<String>, val arriving: Set<String>)

/**
 * dt-ios JSONShapePicture: the document as rows of cells, one row per level, each cell as wide as its formatted bytes.
 * A tap on a container zooms into it; a tap on the focus zooms out.
 */
@Composable
private fun JsonShapePicture(shape: JsonShape?, invalid: Boolean, selected: String?, inks: JsonInks, onTap: (JsonShapeCell) -> Unit) {
    val c = LocalDelphiColours.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val inset = LocalCanonCanvas.current.top
    var shown by remember { mutableStateOf(shape) }
    var zoom by remember { mutableStateOf<Zoom?>(null) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(shape) {
        val old = shown
        if (old != null && shape != null && old.focus != shape.focus) {
            val kept = shape.cells.map { it.path }.toSet()
            val leaving = old.cells.filter { it.path !in kept }
            val from = zoom?.let { it.from.mixed(Viewport.of(old), progress.value.toDouble()) } ?: Viewport.of(old)
            zoom = Zoom(from, leaving, leaving.map { it.path }.toSet(), kept - old.cells.map { it.path }.toSet())
            shown = shape
            progress.snapTo(0f)
            progress.animateTo(1f, tween(500, easing = cubicInOut))
            zoom = null
        } else {
            progress.snapTo(1f)
            zoom = null
            shown = shape
        }
    }

    val labelHeight = remember(density) { measurer.measure("Ag", shapeLabel).size.height }
    val pad = CanonMetrics.panelPadding
    val levels = maxOf(1, shown?.levels ?: 1)
    val height = with(density) { pad * 0.75f + rowHeight * levels + 10.dp + labelHeight.toDp() + pad * 0.75f }

    val share = if (zoom == null) 1.0 else progress.value.toDouble()
    val viewport = zoom?.from?.mixed(Viewport.of(shown), share) ?: Viewport.of(shown)

    fun frame(cell: JsonShapeCell, view: Viewport, area: Rect, top: Float, row: Float): Rect {
        val scale = area.width / maxOf(view.end - view.start, 1e-12)
        val x = (area.left + (cell.start - view.start) * scale).toFloat()
        val width = ((cell.end - cell.start) * scale).toFloat()
        val y = top + ((cell.depth - view.depth) * row).toFloat()
        val left = if (rtl) area.right - (x - area.left) - width else x
        return Rect(left, y, left + width, y + row)
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(inset + height)
            .semantics {
                contentDescription = "Document"
                shown?.let { stateDescription = "${it.depth}, ${it.keys}, ${it.values}" } ?: run { if (invalid) stateDescription = "Invalid" }
            }
            .pointerInput(Unit) {
                detectTapGestures { point ->
                    val current = shown ?: return@detectTapGestures
                    if (zoom != null) return@detectTapGestures
                    val area = Rect(pad.toPx(), 0f, size.width - pad.toPx(), size.height.toFloat())
                    val top = inset.toPx() + pad.toPx() * 0.75f
                    val focusDepth = current.focusCell?.depth ?: 0
                    current.cells.lastOrNull { it.depth >= focusDepth && frame(it, Viewport.of(current), area, top, rowHeight.toPx()).contains(point) }?.let(onTap)
                }
            },
    ) {
        val area = Rect(pad.toPx(), 0f, size.width - pad.toPx(), size.height)
        val top = inset.toPx() + pad.toPx() * 0.75f
        val row = rowHeight.toPx()
        val hairline = CanonMetrics.hairline.toPx()
        val current = shown
        if (current == null) {
            val colour = if (invalid) c.destructive else c.mutedForeground
            val box = Rect(area.left, top, area.right, top + row - 2.dp.toPx())
            drawRect(
                colour,
                Offset(box.left + hairline / 2, box.top + hairline / 2),
                Size(box.width - hairline, box.height - hairline),
                style = Stroke(hairline, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )
            val name = measurer.measure(if (invalid) "Invalid" else "Document", shapeLabel.copy(color = colour))
            drawText(name, topLeft = Offset(box.left + 5.dp.toPx(), box.center.y - name.size.height / 2))
            return@Canvas
        }

        val rows = Rect(area.left, top, area.right, top + (viewport.levels * row).toFloat())
        val old = zoom?.leavingPaths ?: emptySet()
        val arriving = zoom?.arriving ?: emptySet()
        clipRect(rows.left - 1, rows.top, rows.right + 1, rows.bottom) {
            for (cell in zoom?.leaving ?: emptyList()) drawCell(cell, frame(cell, viewport, area, top, row), (1 - share).toFloat(), current, selected, inks, measurer, area)
            for (cell in current.cells) {
                if (cell.path in old) continue
                drawCell(cell, frame(cell, viewport, area, top, row), if (cell.path in arriving) share.toFloat() else 1f, current, selected, inks, measurer, area)
            }
        }

        var x = area.left
        val y = rows.bottom + 10.dp.toPx()
        val focusDepth = current.focusCell?.depth ?: 0
        val present = current.cells.filter { it.depth >= focusDepth }.map { it.kind }.toSet()
        for ((kind, name) in legendNames) {
            if (kind !in present) continue
            val chip = 8.dp.toPx()
            val text = measurer.measure(name, shapeLabel.copy(color = c.mutedForeground))
            val left = if (rtl) size.width - x - chip else x
            drawRect(inks.of(kind), Offset(left, y + (text.size.height - chip) / 2), Size(chip, chip))
            drawText(text, topLeft = Offset(if (rtl) left - 5.dp.toPx() - text.size.width else left + chip + 5.dp.toPx(), y))
            x += chip + 5.dp.toPx() + text.size.width + 14.dp.toPx()
            if (x > area.right) break
        }
    }
}

private fun DrawScope.drawCell(
    cell: JsonShapeCell,
    box: Rect,
    alpha: Float,
    shape: JsonShape,
    selected: String?,
    inks: JsonInks,
    measurer: TextMeasurer,
    area: Rect,
) {
    if (alpha <= 0.01f) return
    if (box.right <= area.left - 1 || box.left >= area.right + 1 || box.width < 0.5f) return
    val hairline = CanonMetrics.hairline.toPx()
    val cellBox = Rect(box.left, box.top, box.left + maxOf(0.5f, box.width - hairline), box.bottom - hairline)
    val colour = inks.of(cell.kind)
    val container = cell.kind == JsonKind.Object || cell.kind == JsonKind.Array
    drawRect(colour.copy(alpha = alpha * if (container) 0.16f else 0.26f), cellBox.topLeft, cellBox.size)
    drawRect(colour.copy(alpha = alpha), cellBox.topLeft, Size(cellBox.width, 2.dp.toPx()))
    if (cell.path == selected) {
        val inset = 0.75.dp.toPx()
        drawRect(
            inks.foreground.copy(alpha = alpha),
            Offset(cellBox.left + inset, cellBox.top + inset),
            Size(cellBox.width - inset * 2, cellBox.height - inset * 2),
            style = Stroke(1.5.dp.toPx()),
        )
    }
    val left = maxOf(cellBox.left, area.left)
    val right = minOf(cellBox.right, area.right)
    if (right - left <= 26.dp.toPx()) return
    val back = if (cell.path == shape.focus && cell.path != "root") "‹ " else ""
    val label = buildAnnotatedString {
        withStyle(SpanStyle(color = inks.foreground.copy(alpha = alpha))) { append(back + cell.label) }
        withStyle(shapeDetail.toSpanStyle().copy(color = inks.literal.copy(alpha = alpha))) { append((if (cell.label.isEmpty()) "" else " ") + cell.detail) }
    }
    val width = (right - left - 10.dp.toPx()).toInt()
    if (width <= 0) return
    val measured = measurer.measure(label, shapeLabel, overflow = TextOverflow.Ellipsis, maxLines = 1, softWrap = false, constraints = Constraints(maxWidth = width))
    drawText(measured, topLeft = Offset(left + 5.dp.toPx(), cellBox.top + 2.dp.toPx() + (cellBox.height - 2.dp.toPx() - measured.size.height) / 2))
}
