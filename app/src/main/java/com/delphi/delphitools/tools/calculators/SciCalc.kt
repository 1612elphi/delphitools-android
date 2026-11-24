package com.delphi.delphitools.tools.calculators

import android.content.ClipData
import android.content.ClipboardManager
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.MenuCellItem
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.canon.SplitLayout
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.canonPress
import com.delphi.delphitools.canon.mix
import com.delphi.delphitools.canon.rememberCanonPress
import com.delphi.delphitools.canon.splitLayout
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

private val FluoWrite = FontFamily(Font(R.font.fluo_write))

private val digitKeys = listOf(
    listOf("(", ")", "mod", "⌫", "C"),
    listOf("7", "8", "9", "÷", "Ans"),
    listOf("4", "5", "6", "×", "EE"),
    listOf("1", "2", "3", "−", "="),
    listOf("0", ".", "±", "+", "="),
)
private val functionsNarrow = listOf(
    listOf("sin", "cos", "tan", "π", "e"),
    listOf("asin", "acos", "atan", "x²", "xʸ"),
    listOf("log", "ln", "10ˣ", "eˣ", "√"),
    listOf("ʸ√x", ",", "!", "|x|", "Const"),
)
private val functionsWide = listOf(
    listOf("sin", "cos", "tan", "π"),
    listOf("asin", "acos", "atan", "e"),
    listOf("x²", "xʸ", "√", "ʸ√x"),
    listOf("log", "ln", "10ˣ", "eˣ"),
    listOf("!", "|x|", ",", "Const"),
)
private val keyIds = (functionsNarrow + digitKeys).flatten().distinct()

private const val KEY_ASPECT = 0.7f
// ponytail: a fixed keypad height; iOS gives the keypad whatever height the page has left so it never scrolls
private val KEYPAD_HEIGHT = (9 * 52 + 10).dp
private const val TYPE_HEIGHT = 58f
private const val EXPRESSION_SIZE = 21f
private const val RESULT_SIZE = 50f
private const val EXPRESSION_LINE = 28f
private const val RESULT_LINE = 60f
private const val DIM = 0.3f
// share of point size
private const val STOP_PADDING = 0.08f

class SciEntry(val id: Long, val expression: String, val result: String)

private sealed interface SciPhase {
    data object Entry : SciPhase

    /** Just evaluated; holds the expression as it was typed. */
    data class Result(val typed: String) : SciPhase
    data object Failure : SciPhase
}

sealed interface SciReadout {
    /** Nothing typed. */
    data object Idle : SciReadout

    /** Typed, but not a value yet. */
    data object Blank : SciReadout
    data class Preview(val text: String) : SciReadout
    data class Result(val text: String) : SciReadout
    data class Failure(val text: String) : SciReadout
}

/** dt-ios ScientificCalculatorViewModel and the controller's phases. */
class SciCalcModel : ViewModel() {
    var expression by mutableStateOf("")
        private set
    private var display = "0"
    var degrees by mutableStateOf(true)
        private set
    var history by mutableStateOf<List<SciEntry>>(emptyList())
        private set
    private var lastAnswer = 0.0
    private var phase: SciPhase = SciPhase.Entry
    private var lastPreview: String? = null
    private var serial = 0L

    /** The expression as shown: what was typed, dimmed after =, with brackets still to close. */
    var shownExpression by mutableStateOf("")
        private set
    var dimmed by mutableStateOf(false)
        private set
    var ghosts by mutableStateOf(0)
        private set
    var readout by mutableStateOf<SciReadout>(SciReadout.Idle)
        private set

    val readoutText get() = when (val r = readout) {
        is SciReadout.Preview -> r.text
        is SciReadout.Result -> r.text
        else -> null
    }

    fun press(key: String) {
        when (key) {
            "DEG", "RAD" -> degrees = key == "DEG"
            "=" -> evaluate()
            "C", "⌫" -> {
                input(key)
                phase = SciPhase.Entry
            }
            "±" -> {
                val toggled = SciCalcEntry.toggledSign(expression)
                if (toggled != null) {
                    expression = toggled
                    display = toggled
                } else {
                    input("±")
                }
                phase = SciPhase.Entry
            }
            "Ans" -> insert(SciCalcEngine.format(lastAnswer), SciKind.Factor)
            else -> insert(SciCalcEntry.text(key), SciCalcEntry.kind(key))
        }
        refresh()
    }

    /** After a result, a number or a function starts a new expression and an operator carries the result on. */
    fun insert(text: String, kind: SciKind) {
        if (phase is SciPhase.Result && kind != SciKind.Other) input("C")
        expression = SciCalcEntry.join(expression, text, kind)
        display = expression
        phase = SciPhase.Entry
        refresh()
    }

    fun load(entry: SciEntry) {
        expression = entry.expression
        display = entry.expression
        phase = SciPhase.Entry
        refresh()
    }

    fun clearHistory() {
        history = emptyList()
    }

    private fun input(token: String) {
        when (token) {
            "C" -> {
                expression = ""
                display = "0"
            }
            "⌫" -> if (expression.isNotEmpty()) {
                expression = expression.dropLast(1)
                display = expression.ifEmpty { "0" }
            }
            "±" -> {
                if (expression.isEmpty() || expression == "0") return
                expression = if (expression.startsWith("-")) expression.drop(1) else "-$expression"
                display = expression
            }
        }
    }

    private fun evaluate() {
        if (phase != SciPhase.Entry || expression.isEmpty()) return
        expression += ")".repeat(SciCalcEntry.openParens(expression))
        val original = expression
        val result = SciCalcEngine.evaluate(expression, degrees)
        if (result.isNaN() || result.isInfinite()) {
            display = if (result.isNaN()) "Error" else if (result > 0) "∞" else "-∞"
            phase = SciPhase.Failure
            return
        }
        val formatted = SciCalcEngine.format(result)
        display = formatted
        lastAnswer = result
        history = (listOf(SciEntry(serial++, original, formatted)) + history).take(20)
        expression = formatted
        phase = SciPhase.Result(original)
    }

    private fun preview(raw: String): Double? =
        SciCalcEngine.evaluate(raw + ")".repeat(SciCalcEntry.openParens(raw)), degrees).takeIf { it.isFinite() }

    private fun refresh() {
        val raw = expression
        when (val p = phase) {
            SciPhase.Entry -> {
                shownExpression = raw
                dimmed = false
                ghosts = SciCalcEntry.openParens(raw)
                lastPreview = if (raw.isEmpty()) null else preview(raw)?.let(SciCalcEngine::format) ?: lastPreview
                readout = if (raw.isEmpty()) SciReadout.Idle else lastPreview?.let { SciReadout.Preview(it) } ?: SciReadout.Blank
            }
            is SciPhase.Result -> {
                lastPreview = null
                shownExpression = p.typed
                dimmed = true
                ghosts = 0
                readout = SciReadout.Result(display)
            }
            SciPhase.Failure -> {
                shownExpression = raw
                dimmed = false
                ghosts = 0
                readout = SciReadout.Failure(display)
            }
        }
    }
}

private fun typed(character: Char): String? = when (character) {
    '*' -> "×"
    '/' -> "÷"
    '-' -> "−"
    '^' -> "xʸ"
    '%' -> "mod"
    '=' -> "="
    'p' -> "π"
    'e' -> "e"
    else -> if (character in "0123456789.()+!,") character.toString() else null
}

@Composable
fun SciCalc(model: SciCalcModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val window = LocalWindowInfo.current.containerSize
    val stacked = splitLayout((window.width / density).dp, (window.height / density).dp, beside = false, across = false, halves = false) == SplitLayout.Stacked
    var showsHistory by remember { mutableStateOf(false) }
    var showsConstants by remember { mutableStateOf(false) }
    if (!stacked) showsHistory = false

    fun press(key: String) {
        view.performHapticFeedback(if (key == "=" || key == "C") HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.KEYBOARD_TAP)
        if (key == "Const") showsConstants = true else model.press(key)
    }

    val historyRows: @Composable (Boolean) -> Unit = { inDialog ->
        CanonHeading("History")
        if (model.history.isEmpty()) {
            CanonEmptyRow("No history yet")
        } else {
            model.history.forEach { entry ->
                TapeRow(
                    entry,
                    onLoad = {
                        model.load(entry)
                        if (inDialog) showsHistory = false
                    },
                    onUse = {
                        model.insert(entry.result, SciKind.Factor)
                        if (inDialog) showsHistory = false
                    },
                )
            }
            CanonGrid {
                CanonButton("Clear", Modifier.weight(1f), icon = Icons.Rounded.Delete, onClick = model::clearHistory)
            }
        }
    }

    val display = CanonSection.canvas(
        CanonCanvasSize.Fixed((EXPRESSION_LINE + 8 + RESULT_LINE + 4 + 10).dp + CanonMetrics.frame * 3),
        background = c.card,
        showsPattern = true,
    ) {
        SciDisplay(model.shownExpression, model.dimmed, model.ghosts, model.readout, model.degrees)
    }
    val keys = CanonSection.of {
        CanonRows {
            CanonGrid {
                CanonButton("DEG", Modifier.weight(1f), kind = CanonButtonKind.Segment, selected = model.degrees) { press("DEG") }
                CanonButton("RAD", Modifier.weight(1f), kind = CanonButtonKind.Segment, selected = !model.degrees) { press("RAD") }
                if (stacked) CanonButton("History", Modifier.weight(1f), icon = Icons.Rounded.History) { showsHistory = true }
                CanonButton("Copy", Modifier.weight(1f), enabled = model.readoutText != null, icon = Icons.Rounded.ContentCopy, flashesCopied = true) {
                    val text = model.readoutText ?: return@CanonButton
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", text))
                }
            }
            SciKeypad(::press)
        }
    }
    val nameplate = CanonSection.of { Nameplate() }
    val history = CanonSection.of { CanonRows { historyRows(false) } }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    CanonSplit(
        sidebar = if (stacked) emptyList() else listOf(history),
        main = listOf(display, keys, nameplate),
        modifier = Modifier
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return@onPreviewKeyEvent false
                val id = when (event.key) {
                    Key.Enter, Key.NumPadEnter -> "="
                    Key.Backspace -> "⌫"
                    Key.Escape -> "C"
                    else -> event.utf16CodePoint.takeIf { it > 0 }?.let { typed(it.toChar()) }
                } ?: return@onPreviewKeyEvent false
                press(id)
                true
            },
        mainLeadsStacked = true,
    )

    if (showsHistory) {
        SciDialog(380, { showsHistory = false }) { CanonRows { historyRows(true) } }
    }
    if (showsConstants) {
        SciDialog(460, { showsConstants = false }) {
            SciConstantCategory.entries.forEach { category ->
                CanonRows {
                    CanonHeading(category.title)
                    CanonGrid(columns = 2) {
                        SciCalcEngine.constants.filter { it.category == category }.forEach { constant ->
                            ConstantCell(constant) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                model.insert(SciCalcEngine.format(constant.value), SciKind.Factor)
                                showsConstants = false
                            }
                        }
                    }
                }
            }
        }
    }
}

// ponytail: a dialog; iOS shows a popover beside its key, or a sheet on a compact width
@Composable
private fun SciDialog(width: Int, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalDelphiColours.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = width.dp)
                .background(c.background)
                .verticalScroll(rememberScrollState())
                .padding(CanonMetrics.panelPadding),
            verticalArrangement = Arrangement.spacedBy(CanonMetrics.panelPadding),
        ) { content() }
    }
}

@Composable
private fun ConstantCell(constant: SciConstant, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    val value = listOfNotNull(SciCalcEntry.written(AlgebraFormat.significant(constant.value)), constant.unit).joinToString(" ")
    Column(
        Modifier
            .canonClickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = constant.name
                stateDescription = value
            }
            .background(c.card)
            .padding(start = 14.dp, top = 10.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(constant.symbol, Modifier.padding(bottom = 2.dp), style = TextStyle(fontFamily = DelphiFont, fontSize = 26.sp, fontWeight = FontWeight.Bold), color = c.primary)
        Text(constant.name, style = CanonText.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.foreground)
        Text(value, style = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp), color = c.mutedForeground)
    }
}

@Composable
private fun styled(raw: String, dimmed: Boolean = false, ghosts: Int = 0): AnnotatedString {
    val c = LocalDelphiColours.current
    return buildAnnotatedString {
        for (piece in SciCalcSyntax.pieces(raw) + List(ghosts) { SciPiece(")", SciPieceKind.Ghost) }) {
            val colour = if (dimmed) {
                c.mutedForeground
            } else {
                when (piece.kind) {
                    SciPieceKind.Number -> c.foreground
                    SciPieceKind.Function, SciPieceKind.Operation -> c.primary
                    SciPieceKind.Bracket -> c.mutedForeground
                    SciPieceKind.Ghost -> c.mutedForeground.copy(alpha = 0.35f)
                }
            }
            val style = if (piece.raised) {
                SpanStyle(colour, fontSize = 0.62.em, fontWeight = FontWeight.SemiBold, baselineShift = BaselineShift(0.4f / 0.62f))
            } else {
                SpanStyle(colour)
            }
            withStyle(style) { append(piece.text) }
        }
    }
}

@Composable
private fun SciDisplay(expression: String, dimmed: Boolean, ghosts: Int, readout: SciReadout, degrees: Boolean) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val top = LocalCanonCanvas.current.top
    val text = styled(expression, dimmed, ghosts)
    val result = readoutText(readout)
    val lamp = TextStyle(fontFamily = DelphiFont, fontSize = 12.sp, fontWeight = FontWeight.Black)
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = CanonMetrics.panelPadding, top = top, end = CanonMetrics.panelPadding)
            .semantics {
                contentDescription = "Result"
                stateDescription = listOf(text.text, result.text).filter { it.isNotBlank() }.joinToString(", ")
            },
    ) {
        Row(Modifier.fillMaxWidth().height(EXPRESSION_LINE.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DEG", style = lamp, color = c.primary.copy(alpha = if (degrees) 1f else DIM))
                Text("RAD", style = lamp, color = c.primary.copy(alpha = if (degrees) DIM else 1f))
            }
            Spacer(Modifier.size(16.dp))
            BasicText(
                text,
                Modifier.weight(1f),
                style = TextStyle(fontFamily = DelphiMono, fontSize = EXPRESSION_SIZE.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End),
                overflow = TextOverflow.StartEllipsis,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = (EXPRESSION_SIZE * 0.7f).sp, maxFontSize = EXPRESSION_SIZE.sp, stepSize = 0.5.sp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(CanonMetrics.frame).background(c.primary))
        Spacer(Modifier.height(4.dp))
        val glows = readout is SciReadout.Result || readout is SciReadout.Failure
        val colour = when (readout) {
            is SciReadout.Result -> c.primary
            is SciReadout.Failure -> c.destructive
            else -> c.primary.copy(alpha = DIM)
        }
        BasicText(
            result,
            Modifier.fillMaxWidth().height(RESULT_LINE.dp),
            style = TextStyle(
                fontFamily = FluoWrite,
                fontSize = RESULT_SIZE.sp,
                lineHeight = RESULT_LINE.sp,
                color = colour,
                textAlign = TextAlign.End,
                shadow = if (glows) Shadow(colour.copy(alpha = 0.55f), Offset.Zero, with(density) { 8.dp.toPx() }) else null,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = (RESULT_SIZE * 0.4f).sp, maxFontSize = RESULT_SIZE.sp, stepSize = 1.sp),
        )
    }
}

private fun readoutText(readout: SciReadout): AnnotatedString {
    val text = when (readout) {
        SciReadout.Idle -> "0"
        SciReadout.Blank -> " "
        is SciReadout.Preview -> readout.text
        is SciReadout.Result -> readout.text
        is SciReadout.Failure -> readout.text
    }
    // segment font lacks letters
    if (readout is SciReadout.Failure) {
        val infinite = '∞' in text
        return buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = FontFamily.Default, fontSize = (if (infinite) 0.9 else 0.62).em, fontWeight = if (infinite) FontWeight.SemiBold else FontWeight.Black)) {
                append(text)
            }
        }
    }
    val (mantissa, exponent) = SciCalcEntry.parts(text)
    val breaks = SciCalcEntry.groupBreaks(mantissa).toSet()
    return buildAnnotatedString {
        mantissa.forEachIndexed { index, character ->
            // stop glyph lacks bearing
            val spacing = when {
                index in breaks -> 0.22.em
                character == '.' || mantissa.getOrNull(index + 1) == '.' -> STOP_PADDING.em
                else -> null
            }
            if (spacing != null) withStyle(SpanStyle(letterSpacing = spacing)) { append(character) } else append(character)
        }
        if (exponent != null) {
            withStyle(SpanStyle(fontFamily = FontFamily.Default, fontSize = 0.42.em, fontWeight = FontWeight.SemiBold)) { append(" ×") }
            withStyle(SpanStyle(fontSize = 0.55.em)) { append("10") }
            withStyle(SpanStyle(fontSize = 0.42.em, baselineShift = BaselineShift(0.34f / 0.42f))) { append(exponent) }
        }
    }
}

private enum class KeyRole { Digit, Operation, Function, Constant, Clear, Equals }

private fun role(id: String) = when (id) {
    "=" -> KeyRole.Equals
    "C", "⌫" -> KeyRole.Clear
    "÷", "×", "−", "+" -> KeyRole.Operation
    "Const", "Ans", "EE" -> KeyRole.Constant
    "±" -> KeyRole.Digit
    else -> if (SciCalcEntry.kind(id) == SciKind.Digit) KeyRole.Digit else KeyRole.Function
}

private fun spoken(id: String) = when (id) {
    "⌫" -> "Delete"
    "C" -> "Clear"
    "x²" -> "Square"
    "xʸ" -> "Power"
    "√" -> "Square root"
    "ʸ√x" -> "Root"
    "|x|" -> "Absolute value"
    "±" -> "Sign"
    "!" -> "Factorial"
    "EE" -> "Exponent"
    "Const" -> "Constants"
    else -> id
}

private fun sideBySide(width: Float, height: Float): Boolean {
    fun miss(columns: Float, rows: Float) = abs(ln(max(height / rows, 1f) / max(width / columns, 1f) / KEY_ASPECT))
    return miss(9f, 5f) < miss(5f, 9f)
}

@Composable
private fun SciKeypad(onPress: (String) -> Unit) {
    val c = LocalDelphiColours.current
    Layout(
        content = { keyIds.forEach { id -> androidx.compose.runtime.key(id) { SciKey(id, onPress) } } },
        modifier = Modifier
            .fillMaxWidth()
            .height(KEYPAD_HEIGHT)
            .background(c.border),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val wide = sideBySide(width.toFloat(), height.toFloat())
        val seam = CanonMetrics.frame.toPx()
        val line = CanonMetrics.hairline.toPx()
        val columnGaps = List(if (wide) 8 else 4) { if (wide && it == 3) seam else line }
        val rowGaps = List(if (wide) 4 else 8) { if (!wide && it == 3) seam else line }
        val keyWidth = (width - columnGaps.sum()) / (columnGaps.size + 1)
        val keyHeight = (height - rowGaps.sum()) / (rowGaps.size + 1)
        fun starts(size: Float, gaps: List<Float>) = gaps.runningFold(0f) { start, gap -> start + size + gap }
        val xs = starts(keyWidth, columnGaps)
        val ys = starts(keyHeight, rowGaps)
        val blocks = if (wide) listOf(Triple(functionsWide, 0, 0), Triple(digitKeys, 0, 4)) else listOf(Triple(functionsNarrow, 0, 0), Triple(digitKeys, 4, 0))
        val frames = HashMap<String, IntArray>()
        for ((grid, blockRow, blockColumn) in blocks) {
            grid.forEachIndexed { row, ids ->
                ids.forEachIndexed { column, id ->
                    // repeated id spans
                    if ((row > 0 && grid[row - 1][column] == id) || (column > 0 && ids[column - 1] == id)) return@forEachIndexed
                    var lastRow = row
                    while (lastRow + 1 < grid.size && grid[lastRow + 1][column] == id) lastRow++
                    var lastColumn = column
                    while (lastColumn + 1 < ids.size && ids[lastColumn + 1] == id) lastColumn++
                    val x = xs[blockColumn + column]
                    val y = ys[blockRow + row]
                    val right = xs[blockColumn + lastColumn] + keyWidth
                    val bottom = ys[blockRow + lastRow] + keyHeight
                    frames[id] = intArrayOf(x.roundToInt(), y.roundToInt(), right.roundToInt(), bottom.roundToInt())
                }
            }
        }
        val placeables = measurables.mapIndexed { index, measurable ->
            val f = frames.getValue(keyIds[index])
            measurable.measure(Constraints.fixed(f[2] - f[0], f[3] - f[1]))
        }
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val f = frames.getValue(keyIds[index])
                placeable.place(f[0], f[1])
            }
        }
    }
}

@Composable
private fun SciKey(id: String, onPress: (String) -> Unit) {
    val c = LocalDelphiColours.current
    val role = role(id)
    val (base, ink) = when (role) {
        KeyRole.Digit -> c.muted to c.foreground
        KeyRole.Operation -> c.accentBg to c.primary
        KeyRole.Function -> c.card to c.foreground
        KeyRole.Constant -> c.accentBg to c.foreground
        KeyRole.Clear -> mix(c.card, c.destructive, 0.1f) to c.destructive
        KeyRole.Equals -> c.primary to c.primaryForeground
    }
    val (size, weight) = when {
        role == KeyRole.Digit && id == "±" -> 18f to FontWeight.SemiBold
        role == KeyRole.Digit || role == KeyRole.Operation -> 20f to FontWeight.SemiBold
        role == KeyRole.Equals -> 26f to FontWeight.SemiBold
        role == KeyRole.Clear -> 18f to FontWeight.Bold
        else -> 15f to FontWeight.Medium
    }
    val press = rememberCanonPress()
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .canonPress(press, enabled = true, role = androidx.compose.ui.semantics.Role.Button, repeats = id == "⌫") { onPress(id) }
            .background(base)
            .semantics { contentDescription = spoken(id) },
        contentAlignment = Alignment.Center,
    ) {
        val scale = (maxHeight.value / TYPE_HEIGHT).coerceIn(0.85f, 1.5f)
        if (id == "⌫") {
            Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null, tint = ink, modifier = Modifier.size((20 * scale).dp))
        } else {
            BasicText(
                id,
                Modifier.padding(horizontal = 4.dp).clearAndSetSemantics {},
                style = TextStyle(fontFamily = DelphiFont, fontSize = (size * scale).sp, fontWeight = weight, color = ink, textAlign = TextAlign.Center),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = (size * scale * 0.6f).sp, maxFontSize = (size * scale).sp, stepSize = 0.5.sp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TapeRow(entry: SciEntry, onLoad: () -> Unit, onUse: () -> Unit) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val expression = styled(entry.expression)
    Box {
        // ponytail: no Canon press here; a hold opens the menu
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = CanonMetrics.rowHeight)
                .combinedClickable(onLongClick = { menu = true }, onClick = onLoad)
                .clearAndSetSemantics {
                    contentDescription = expression.text
                    stateDescription = entry.result
                }
                .background(if (menu) c.accentBg else c.card)
                .padding(horizontal = CanonMetrics.panelPadding, vertical = 10.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText(
                expression,
                style = TextStyle(fontFamily = DelphiMono, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
            Text(
                "= " + SciCalcEntry.written(entry.result),
                style = TextStyle(fontFamily = DelphiMono, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                color = c.foreground,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
        }
        MenuCellPopup(menu, { menu = false }) {
            MenuCellItem("Copy", icon = Icons.Rounded.ContentCopy) {
                menu = false
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", entry.result))
            }
            MenuCellItem("Use", icon = Icons.Rounded.SubdirectoryArrowRight) {
                menu = false
                onUse()
            }
        }
    }
}

@Composable
private fun Nameplate() {
    val c = LocalDelphiColours.current
    val style = TextStyle(fontFamily = DelphiFont, fontSize = 11.sp, fontWeight = FontWeight.Black)
    // ponytail: no expanded width; iOS sets the system font's expanded width
    Row(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
            .padding(horizontal = 2.dp)
            .alpha(0.35f)
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("DELPHI ELECTRONICS", style = style.copy(letterSpacing = 2.4.sp), color = c.mutedForeground)
        Spacer(Modifier.weight(1f))
        Text("SC-1", style = style.copy(letterSpacing = 1.2.sp), color = c.primary)
    }
}
