package com.delphi.delphitools.tools.devtools

import com.delphi.delphitools.tools.TYPING_DELAY_MS
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCopyFeedback
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTextInput
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.CanonValueLayout
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonMathInks
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

private const val LIST_LIMIT = 100
private const val RAIL_LEVELS = 4
private const val WIDEST = 736f
private val patternMono = TextStyle(fontFamily = DelphiMono, fontSize = 15.sp)
private val wordStyle = CanonText.footnoteSemibold

private class RegexFlag(val flag: Char, val label: String)

private val flagOptions = listOf(
    RegexFlag('g', "Global"),
    RegexFlag('i', "Case insensitive"),
    RegexFlag('m', "Multiline"),
    RegexFlag('s', "Dotall"),
)

private class RegexPreset(val name: String, val pattern: String, val flags: String)

private val presets = listOf(
    RegexPreset("Email", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "g"),
    RegexPreset("URL", "https?://[^\\s]+", "g"),
    RegexPreset("Phone", "\\+?[\\d\\s()-]{7,}", "g"),
    RegexPreset("Date", "\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4}", "g"),
)

class RegexTesterModel : ViewModel() {
    var pattern by mutableStateOf("")
        private set
    var flags by mutableStateOf("g")
        private set
    var input by mutableStateOf("")
    var outcome by mutableStateOf<RegexOutcome>(RegexOutcome.Empty)
        private set

    /** The test string the outcome was found in. */
    var tested by mutableStateOf("")
        private set
    private var typing: Job? = null
    private var run: Job? = null

    fun type(text: String) {
        pattern = text
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            refresh()
        }
    }

    fun toggle(flag: Char) {
        flags = if (flag in flags) flags.filter { it != flag } else flags + flag
        refresh()
    }

    fun use(preset: Int) {
        pattern = presets[preset].pattern
        flags = presets[preset].flags
        refresh()
    }

    fun refresh() {
        typing?.cancel()
        run?.cancel()
        val source = pattern
        val flags = flags
        val text = input
        run = viewModelScope.launch {
            val found = runInterruptible(Dispatchers.Default) { RegexTesterEngine.run(source, flags, text) }
            outcome = found
            tested = text
        }
    }
}

private fun groupColour(inks: List<Color>, group: Int) = inks[(group - 1) % inks.size]

private fun counted(count: Int) = String.format("%,d", count)

@Composable
fun RegexTester(model: RegexTesterModel = viewModel()) {
    val view = LocalView.current
    val math = canonMathInks()
    val inks = listOf(math.blue, math.clay, math.green, math.yellow)
    val outcome = model.outcome
    val tested = model.tested

    val patternBlock = CanonSection.of {
        CanonRows {
            CanonHeading("Pattern")
            PatternRow(model.pattern, model.flags, model::type) { CanonCopyFeedback.copy(view, "/${model.pattern}/${model.flags}") }
            when (outcome) {
                is RegexOutcome.Invalid -> CanonStatusRow(outcome.message, CanonTone.Warn)
                RegexOutcome.TimedOut -> CanonStatusRow("Pattern timed out", CanonTone.Warn)
                else -> Unit
            }
            CanonHeading("Options")
            CanonGrid(columns = 2) {
                flagOptions.forEach { option ->
                    CanonButton("${option.flag}  ${option.label}", kind = CanonButtonKind.Segment, selected = option.flag in model.flags) {
                        model.toggle(option.flag)
                    }
                }
            }
            CanonHeading("Presets")
            CanonGrid(columns = 4) {
                presets.forEachIndexed { index, preset -> CanonButton(preset.name) { model.use(index) } }
            }
        }
    }

    val testBlock = CanonSection.of {
        CanonRows {
            CanonHeading("Test String")
            CanonTextInput(model.input, { model.input = it }, { model.refresh() }, height = 150.dp, label = "Test String")
        }
    }

    val matches = CanonSection.of {
        CanonRows {
            val found = (outcome as? RegexOutcome.Matched)?.takeIf { tested.isNotEmpty() }
            if (found == null) {
                CanonHeading("Matches")
                CanonEmptyRow("None")
                return@CanonRows
            }
            val count = counted(found.matches.size) + if (found.truncated) "+" else ""
            val listed = minOf(found.matches.size, LIST_LIMIT)
            CanonHeading("Matches · " + if (listed < found.matches.size) "${counted(listed)} / $count" else count)
            if (found.matches.isEmpty()) CanonEmptyRow("None")
            found.matches.take(listed).forEachIndexed { index, match ->
                CanonValueLine(
                    "${index + 1}",
                    if (match.range.length > 0) tested.substring(match.range.start, match.range.end) else null,
                    layout = CanonValueLayout.NumberedWraps,
                )
                match.groups.forEachIndexed { group, range ->
                    val colour = groupColour(inks, group + 1)
                    CanonValueLine(
                        "$${group + 1}",
                        range?.let { tested.substring(it.start, it.end) },
                        layout = CanonValueLayout.NumberedWraps,
                        tint = { colour },
                    )
                }
            }
        }
    }

    val canvas = CanonSection.canvas(CanonCanvasSize.Fitting) { RegexMatchPicture(tested, outcome, inks) }

    CanonSplit(sidebar = listOf(canvas), main = listOf(patternBlock, testBlock, matches), halves = true, collapsesTo = 150.dp)
}

/** `/ pattern / flags`: the pattern typed between slashes, the flags after, and a cell that copies the whole literal. */
@Composable
private fun PatternRow(pattern: String, flags: String, onType: (String) -> Unit, onCopy: () -> Unit) {
    val c = LocalDelphiColours.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // ponytail: no clear button while editing; the Clear of the test string is the only one
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = CanonMetrics.rowHeight).background(c.border)) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(c.card)
                .clickable(interactionSource = null, indication = null) { focus.requestFocus() }
                .padding(start = CanonMetrics.panelPadding, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("/", style = patternMono, color = c.mutedForeground)
            BasicTextField(
                pattern,
                onType,
                Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Pattern" },
                textStyle = patternMono.copy(color = c.foreground),
                singleLine = true,
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                decorationBox = { field ->
                    Box {
                        if (pattern.isEmpty()) Text("Pattern", style = patternMono, color = c.mutedForeground)
                        field()
                    }
                },
            )
            Text("/", style = patternMono, color = c.mutedForeground)
            Text(
                flags,
                Modifier.semantics { contentDescription = "Options" },
                style = patternMono.copy(fontWeight = FontWeight.SemiBold),
                color = c.primary,
            )
        }
        CanonButton(
            "",
            Modifier.padding(start = CanonMetrics.hairline).fillMaxHeight(),
            icon = Icons.Outlined.ContentCopy,
            contentDescription = "Copy Pattern",
            onClick = onCopy,
        )
    }
}

/**
 * The test string with every match lit in place, neighbouring matches in two shades, each capture group bracketed
 * under its text in its own colour and level, a zero-width match as a caret; the match count as a figure above.
 */
@Composable
private fun RegexMatchPicture(text: String, outcome: RegexOutcome, inks: List<Color>) {
    val c = LocalDelphiColours.current
    val pad = CanonMetrics.panelPadding
    val top = LocalCanonCanvas.current.top + 6.dp
    val found = outcome as? RegexOutcome.Matched
    val count = found?.matches?.size ?: 0
    val groups = found?.groups ?: 0
    val word = when {
        found != null -> "Matches" + if (found.truncated) "+" else ""
        outcome.failed -> "Invalid"
        else -> "Matches"
    }

    BoxWithConstraints(Modifier.fillMaxSize().semantics(mergeDescendants = true) {}, contentAlignment = Alignment.TopCenter) {
        val column = minOf(maxWidth.value, WIDEST).dp
        // ponytail: the text swaps at once; iOS cross-dissolves it over 0.2 s
        Column(Modifier.width(column).fillMaxHeight().padding(top = top)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = pad)
                    .clearAndSetSemantics {
                        contentDescription = "Matches"
                        stateDescription = "$count"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CanonFigure(
                    counted(count),
                    size = 26.sp,
                    colour = when {
                        outcome.failed -> c.destructive
                        count > 0 -> c.primary
                        else -> c.mutedForeground
                    },
                )
                Text(word, Modifier.padding(start = 8.dp, top = 6.dp), style = wordStyle, color = if (outcome.failed) c.destructive else c.mutedForeground)
                if (groups > 0) {
                    Spacer(Modifier.width(24.dp))
                    CanonFigure(counted(groups), size = 26.sp, colour = c.foreground)
                    Text("Groups", Modifier.padding(start = 8.dp, top = 6.dp), style = wordStyle, color = c.mutedForeground)
                }
            }
            if (text.isEmpty()) {
                Box(Modifier.padding(horizontal = pad).fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    val ink = c.mutedForeground
                    Canvas(Modifier.fillMaxSize()) {
                        val hairline = CanonMetrics.hairline.toPx()
                        drawRect(
                            ink,
                            topLeft = Offset(hairline / 2, hairline / 2),
                            size = Size(size.width - hairline, size.height - hairline),
                            style = Stroke(hairline, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                        )
                    }
                    Text("Test String", style = CanonText.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.mutedForeground)
                }
            } else {
                val size = when {
                    column.value >= 560 -> 19
                    column.value >= 420 -> 17
                    else -> 15
                }
                val marks = remember(outcome, inks) { regexMarks(outcome, inks, c.primary) }
                val rails = minOf(groups, RAIL_LEVELS)
                MarkedText(
                    text,
                    marks,
                    TextStyle(fontFamily = DelphiMono, fontSize = size.sp),
                    Modifier.weight(1f).fillMaxWidth(),
                    lineSpacing = (6 + if (rails > 0) rails * 5 + 2 else 0).dp,
                    padding = PaddingValues(start = pad, top = 4.dp, end = pad, bottom = pad),
                    reveal = found?.matches?.firstOrNull()?.range?.start,
                )
            }
        }
    }
}

private fun regexMarks(outcome: RegexOutcome, inks: List<Color>, primary: Color): TextMarks {
    val found = outcome as? RegexOutcome.Matched ?: return TextMarks()
    val spans = mutableListOf<TextMark>()
    val rails = mutableListOf<TextRail>()
    val carets = mutableListOf<TextCaret>()
    found.matches.forEachIndexed { index, match ->
        if (match.range.length == 0) {
            carets += TextCaret(match.range.start, primary)
        } else {
            spans += TextMark(match.range, primary.copy(alpha = if (index % 2 == 0) 0.22f else 0.11f))
        }
        match.groups.take(RAIL_LEVELS).forEachIndexed { group, range ->
            if (range == null) return@forEachIndexed
            val colour = groupColour(inks, group + 1)
            if (range.length == 0) carets += TextCaret(range.start, colour) else rails += TextRail(range, group + 1, colour)
        }
    }
    return TextMarks(spans = spans, rails = rails, carets = carets)
}
