package com.delphi.delphitools.tools.devtools

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonRule
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatus
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.CanonValueLayout
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.rememberCanonTextFilePicker
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

private const val STORAGE_KEY = "delphitools-scratchpad"
private const val SAVE_DELAY_MS = 1000L
private const val LISTED = 50
private val editorFont = TextStyle(fontFamily = DelphiMono, fontSize = 16.sp)
private val fieldFont = TextStyle(fontFamily = DelphiMono, fontSize = 16.sp)

private class FoundIn(val text: String, val find: String, val isRegex: Boolean, val ranges: List<TextSpan>?)

/** dt-ios TextScratchpadViewModel: the draft, saved a second after the last edit, and the find state. */
class TextScratchpadModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("delphitools", Context.MODE_PRIVATE)
    var text by mutableStateOf(prefs.getString(STORAGE_KEY, "") ?: "")
        private set
    var find by mutableStateOf("")
        private set
    var replacement by mutableStateOf("")
    var regex by mutableStateOf(false)
        private set
    var counts by mutableStateOf(ScratchpadCounts(0, 0, 0))
        private set
    private var found by mutableStateOf<FoundIn?>(null)
    var extracted by mutableStateOf<List<String>?>(null)
        private set
    var decodeFailed by mutableStateOf(false)
        private set

    private var saving: Job? = null
    private var typing: Job? = null
    private var searching: Job? = null

    /** Matches in the text as it is now; empty while a search is pending. */
    val marks: List<TextSpan>
        get() = current?.ranges ?: emptyList()

    private val current get() = found?.takeIf { it.text == text && it.find == find && it.isRegex == regex }

    val matchCount get() = current?.ranges?.size ?: 0
    val badPattern get() = current?.let { it.ranges == null } ?: false

    init {
        refresh(0)
    }

    fun type(new: String) {
        if (new == text) return
        text = new
        save()
        refresh(TYPING_DELAY_MS)
    }

    fun typeFind(new: String) {
        find = new
        refresh(TYPING_DELAY_MS)
    }

    fun toggleRegex(on: Boolean) {
        regex = on
        refresh(0)
    }

    fun replace(with: String) {
        decodeFailed = false
        if (with == text) return
        text = with
        save()
        refresh(0)
    }

    fun run(transform: (String) -> String) = replace(transform(text))

    fun decodeUrl() {
        val decoded = TextScratchpadEngine.urlDecode(text)
        replace(decoded ?: text)
        decodeFailed = decoded == null
    }

    fun replaceOne() = run { TextScratchpadEngine.replace(it, find, replacement, regex, all = false) }

    fun replaceAll() = run { TextScratchpadEngine.replace(it, find, replacement, regex, all = true) }

    fun extract(kind: (String) -> List<String>) {
        extracted = kind(text)
    }

    private fun save() {
        saving?.cancel()
        saving = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            prefs.edit().putString(STORAGE_KEY, text).apply()
        }
    }

    private fun refresh(delayMs: Long) {
        typing?.cancel()
        searching?.cancel()
        val text = text
        val find = find
        val regex = regex
        typing = viewModelScope.launch {
            delay(delayMs)
            searching = launch {
                val result = runInterruptible(Dispatchers.Default) {
                    TextScratchpadEngine.counts(text) to TextScratchpadEngine.matches(find, regex, text)
                }
                counts = result.first
                found = FoundIn(text, find, regex, result.second)
            }
        }
    }

    override fun onCleared() {
        if (saving?.isActive == true) prefs.edit().putString(STORAGE_KEY, text).apply()
    }
}

private class Lit(private val ranges: List<TextSpan>, private val colour: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styles = ranges.filter { it.end <= text.length }.map { AnnotatedString.Range(SpanStyle(background = colour), it.start, it.end) }
        return TransformedText(AnnotatedString(text.text, styles), OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is Lit && other.ranges == ranges && other.colour == colour

    override fun hashCode() = ranges.hashCode() * 31 + colour.hashCode()
}

private fun counted(count: Int) = String.format("%,d", count)

// ponytail: no undo or redo and no line-slide or cross-fade on a transform; the android shell has no undo commands
@Composable
fun TextScratchpad(model: TextScratchpadModel = viewModel()) {
    val context = LocalContext.current
    val picker = rememberCanonTextFilePicker(onText = model::replace)
    val hasText = model.text.isNotEmpty()

    val editor = CanonSection.canvas(CanonCanvasSize.Fixed(2000.dp)) { ScratchpadEditor(model) }

    val document = CanonSection.of {
        CanonRows {
            CanonHeading("Text")
            CanonGrid(columns = 2) {
                CanonButton("Files", icon = Icons.Outlined.Folder) { picker.open() }
                CanonButton("Clear", icon = Icons.Outlined.Delete, enabled = hasText) { model.replace("") }
            }
            CanonGrid {
                CanonButton("Share", Modifier.weight(1f).fillMaxHeight(), kind = CanonButtonKind.Secondary, enabled = hasText, icon = Icons.Outlined.Share) {
                    val text = model.text
                    model.viewModelScope.launch { FileExport.share(context, text.toByteArray(), "scratchpad.txt", "text/plain") }
                }
                CanonButton(
                    "Copy",
                    Modifier.weight(1f).fillMaxHeight(),
                    kind = CanonButtonKind.Primary,
                    enabled = hasText,
                    icon = Icons.Outlined.ContentCopy,
                    oversized = true,
                    flashesCopied = true,
                ) {
                    // clip past binder throws
                    runCatching { context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", model.text)) }
                }
            }
        }
    }

    val e = TextScratchpadEngine
    val case = block(
        "Case",
        listOf(
            "UPPERCASE" to e::upperCase,
            "lowercase" to e::lowerCase,
            "Title Case" to e::titleCase,
            "Sentence case" to e::sentenceCase,
            "camelCase" to e::camelCase,
            "snake_case" to e::snakeCase,
            "kebab-case" to e::kebabCase,
        ),
        model,
    )
    val lines = block(
        "Lines",
        listOf(
            "Sort A–Z" to e::sortAZ,
            "Sort Z–A" to e::sortZA,
            "Sort by Length" to e::sortByLength,
            "Reverse Order" to e::reverseOrder,
            "Shuffle" to { text: String -> e.shuffle(text) },
            "Remove Duplicates" to e::removeDuplicates,
            "Add Line Numbers" to e::addLineNumbers,
            "Remove Line Numbers" to e::removeLineNumbers,
        ),
        model,
    )
    val clean = block(
        "Clean Up",
        listOf(
            "Trim Lines" to e::trimLines,
            "Remove Empty Lines" to e::removeEmptyLines,
            "Remove Extra Spaces" to e::removeExtraSpaces,
            "Join Lines" to e::joinLines,
            "Wrap at 80" to { text: String -> e.wrapAt(text, 80) },
            "Wrap at 120" to { text: String -> e.wrapAt(text, 120) },
            "URL Encode" to e::urlEncode,
        ),
        model,
        last = { CanonButton("URL Decode", onClick = model::decodeUrl) },
        extra = { if (model.decodeFailed) CanonStatusRow("Invalid escape", CanonTone.Warn) },
    )

    val findBlock = CanonSection.of {
        val c = LocalDelphiColours.current
        val count = model.matchCount
        CanonRows {
            CanonHeading("Find & Replace")
            FieldRow(Icons.Outlined.Search, model.find, model::typeFind, "Find")
            FieldRow(Icons.Outlined.SubdirectoryArrowRight, model.replacement, { model.replacement = it }, "Replace")
            Row(Modifier.fillMaxWidth().background(c.card), verticalAlignment = Alignment.CenterVertically) {
                CanonRow("Regex", Modifier.weight(1f)) { CanonSwitch(model.regex, onCheckedChange = model::toggleRegex) }
                if (model.badPattern) {
                    CanonStatus("Invalid regex", CanonTone.Warn, Modifier.padding(end = CanonMetrics.panelPadding))
                } else if (model.find.isNotEmpty()) {
                    CanonFigure(counted(count), Modifier.padding(end = CanonMetrics.panelPadding), unit = if (count == 1) "match" else "matches", size = 17.sp, colour = c.foreground)
                }
            }
            CanonGrid(columns = 2) {
                CanonButton("Replace", enabled = count > 0, onClick = model::replaceOne)
                CanonButton("Replace All", enabled = count > 0, onClick = model::replaceAll)
            }
        }
    }

    val extractBlock = CanonSection.of {
        val results = model.extracted
        CanonRows {
            CanonHeading(if (results.isNullOrEmpty()) "Extract" else "${results.size} found")
            CanonGrid(columns = 3) {
                CanonButton("Emails") { model.extract(e::extractEmails) }
                CanonButton("URLs") { model.extract(e::extractUrls) }
                CanonButton("Numbers") { model.extract(e::extractNumbers) }
            }
            if (results != null) {
                if (results.isEmpty()) {
                    CanonEmptyRow("None")
                } else {
                    results.take(LISTED).forEachIndexed { index, value -> CanonValueLine("${index + 1}", value, layout = CanonValueLayout.Numbered) }
                    CanonButton("Copy All", icon = Icons.Outlined.ContentCopy, flashesCopied = true) {
                        runCatching { context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", results.joinToString("\n"))) }
                    }
                }
            }
        }
    }

    CanonSplit(
        sidebar = listOf(editor),
        main = listOf(document, case, lines, clean, findBlock, extractBlock),
        halves = true,
        collapsesTo = 180.dp,
    )
}

private fun block(
    title: String,
    cells: List<Pair<String, (String) -> String>>,
    model: TextScratchpadModel,
    last: (@Composable () -> Unit)? = null,
    extra: @Composable () -> Unit = {},
) = CanonSection.of {
    CanonRows {
        CanonHeading(title)
        CanonGrid(columns = 2) {
            cells.forEach { (name, transform) -> CanonButton(name) { model.run(transform) } }
            last?.invoke()
        }
        extra()
    }
}

@Composable
private fun FieldRow(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, onChange: (String) -> Unit, label: String) {
    val c = LocalDelphiColours.current
    Row(Modifier.fillMaxWidth().background(c.card).padding(start = CanonMetrics.panelPadding), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.mutedForeground, modifier = Modifier.size(16.dp))
        CanonTextCell(
            value,
            onChange,
            Modifier.weight(1f).semantics { contentDescription = label },
            placeholder = label,
            textStyle = fieldFont,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        )
    }
}

// dt-ios ScratchpadEditor
@Composable
private fun ScratchpadEditor(model: TextScratchpadModel) {
    val c = LocalDelphiColours.current
    val counts = model.counts
    val lit = model.marks
    val colour = c.primary.copy(alpha = 0.2f)
    val transformation = remember(lit, colour) { if (lit.isEmpty()) VisualTransformation.None else Lit(lit, colour) }
    Column(Modifier.fillMaxSize().padding(top = LocalCanonCanvas.current.top)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(CanonMetrics.controlHeight)
                .padding(horizontal = CanonMetrics.panelPadding)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CanonFigure(counted(counts.words), unit = "words", size = 15.sp, colour = c.foreground)
            CanonFigure(counted(counts.characters), unit = "chars", size = 15.sp, colour = c.foreground)
            CanonFigure(counted(counts.lines), unit = "lines", size = 15.sp, colour = c.foreground)
            Spacer(Modifier.weight(1f))
        }
        CanonRule()
        BasicTextField(
            model.text,
            model::type,
            Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = "Text" },
            textStyle = editorFont.copy(color = c.foreground),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            visualTransformation = transformation,
            decorationBox = { field ->
                androidx.compose.foundation.layout.Box(Modifier.padding(start = CanonMetrics.panelPadding, top = 14.dp, end = CanonMetrics.panelPadding, bottom = 32.dp)) {
                    if (model.text.isEmpty()) Text("Paste or type text here…", style = editorFont, color = c.mutedForeground)
                    field()
                }
            },
        )
    }
}
