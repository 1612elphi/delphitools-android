package com.delphi.delphitools.tools.calculators

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SubdirectoryArrowLeft
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMathField
import com.delphi.delphitools.canon.CanonMathFieldHandle
import com.delphi.delphitools.canon.CanonMathKeypad
import com.delphi.delphitools.canon.CanonMathView
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.CanonValueLayout
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.MathAtom
import com.delphi.delphitools.canon.MathInk
import com.delphi.delphitools.canon.MathMark
import com.delphi.delphitools.canon.MathMorph
import com.delphi.delphitools.canon.MathPlaced
import com.delphi.delphitools.canon.MathTypesetter
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.math.CanonMathExpression
import com.delphi.delphitools.canon.math.CanonMathItem
import com.delphi.delphitools.canon.math.CanonMathKey
import com.delphi.delphitools.canon.math.CanonMathNode
import com.delphi.delphitools.canon.math.CanonMathRow
import com.delphi.delphitools.canon.mathInk
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private val operations = AlgebraOperation.entries
private val insertKeys = listOf(
    CanonMathKey.Fraction, CanonMathKey.Power, CanonMathKey.Square, CanonMathKey.Root, CanonMathKey.Open,
    CanonMathKey.Symbol("×"), CanonMathKey.Symbol("="), CanonMathKey.Symbol("π"),
)
private val syntax = listOf(
    CanonTile("Power", "x^2"),
    CanonTile("Multiply", "a*b"),
    CanonTile("Divide", "a/b"),
    CanonTile("Square root", "sqrt(x)"),
    CanonTile("Trig functions", "sin(x)"),
    CanonTile("Natural log", "log(x)"),
    CanonTile("Euler's number", "e"),
    CanonTile("Pi", "pi"),
    CanonTile("Absolute value", "abs(x)"),
)
private val PICTURE_HEIGHT = 188.dp
private val variableMono = TextStyle(fontFamily = DelphiMono, fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
private val exampleMono = TextStyle(fontFamily = DelphiMono, fontSize = 13.sp)

/** One evaluation as the results and the picture show it: what was typed then, and what came back. */
class AlgebraShown(
    val expression: String,
    val operation: AlgebraOperation,
    val variable: String,
    val result: AlgebraResult?,
    val failure: String?,
    /** The placeholder's result, shown faint with nothing typed. */
    val ghost: AlgebraResult?,
    val promote: Boolean,
)

/** dt-ios AlgebraCalcViewController's state; nerdamer runs on every pause in typing. */
class AlgebraCalcModel(application: Application) : AndroidViewModel(application) {
    var operation by mutableStateOf(AlgebraOperation.Simplify)
        private set
    var text by mutableStateOf("")
        private set
    var variableText by mutableStateOf("x")
        private set
    var approximate by mutableStateOf(false)
        private set
    var shown by mutableStateOf<AlgebraShown?>(null)
        private set
    var engineFailed by mutableStateOf(false)
        private set

    private var engine: Deferred<AlgebraEngine?>? = null
    private var started: AlgebraEngine? = null
    private var typing: Job? = null
    // drops stale runs
    private var run = 0

    val variable get() = variableText.trim().ifEmpty { "x" }

    init {
        evaluate()
    }

    fun type(text: String) {
        this.text = text
        later()
    }

    fun typeVariable(name: String) {
        // web caps it at two
        variableText = name.take(2)
        later()
    }

    fun choose(operation: AlgebraOperation) {
        this.operation = operation
        evaluate()
    }

    fun load(example: String) {
        text = example
        evaluate()
    }

    fun showApproximate(on: Boolean) {
        approximate = on
    }

    fun useAsInput() {
        val result = shown?.result ?: return
        if (operation == AlgebraOperation.Solve) return
        text = AlgebraFormat.withoutConstant(result.text)
        evaluate(promote = true)
    }

    private fun later() {
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            evaluate()
        }
    }

    fun evaluate(promote: Boolean = false) {
        typing?.cancel()
        val current = ++run
        val expression = text.trim()
        val operation = operation
        val variable = variable
        viewModelScope.launch {
            val engine = engine()
            var result: AlgebraResult? = null
            var failure: String? = null
            var ghost: AlgebraResult? = null
            if (engine != null) {
                if (expression.isNotEmpty()) {
                    engine.evaluate(expression, operation, variable).fold({ result = it }, { failure = it.message })
                } else {
                    ghost = engine.evaluate(operation.placeholder, operation, variable).getOrNull()
                }
            }
            if (current != run) return@launch
            engineFailed = engine == null
            shown = AlgebraShown(expression, operation, variable, result, failure, ghost, promote)
        }
    }

    private suspend fun engine(): AlgebraEngine? {
        if (started?.isLost == true) {
            engine = null
            started = null
        }
        val app = getApplication<Application>()
        val pending = engine ?: viewModelScope.async { AlgebraEngine.start(app).also { started = it } }.also { engine = it }
        return pending.await()
    }

    override fun onCleared() {
        started?.close()
    }
}

private fun solutions(result: AlgebraResult, approximate: Boolean) =
    if (approximate) AlgebraFormat.formatted(result.approxSolutions ?: result.solutions ?: emptyList()) else result.solutions ?: emptyList()

@Composable
fun AlgebraCalc(model: AlgebraCalcModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val handle = remember { CanonMathFieldHandle() }
    val operation = model.operation
    val variable = model.variable
    val shown = model.shown
    val result = shown?.result
    val relation = if (model.approximate) "≈" else "="
    val isSolve = shown?.operation == AlgebraOperation.Solve

    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Operation")
            CanonSegmented(operations.map { it.title }, operations.indexOf(operation), columns = 3) { model.choose(operations[it]) }
            CanonHeading("Expression")
            CanonMathField(
                model.text,
                model::type,
                variable = variable,
                placeholder = operation.placeholder,
                handle = handle,
                onDone = { model.evaluate() },
            )
            if (model.engineFailed) {
                CanonStatusRow("The algebra engine could not start", CanonTone.Warn)
            } else if (shown?.failure != null) {
                CanonStatusRow(shown.failure, CanonTone.Warn)
            }
            CanonGrid(columns = 4) {
                insertKeys.forEach { key -> CanonMathKeypad.cell(key, variable = variable) { handle.edit(listOf(key)) } }
            }
            if (operation.needsVariable) {
                CanonRow("Variable", flush = true) {
                    CanonTextCell(
                        model.variableText,
                        model::typeVariable,
                        width = 72.dp,
                        textStyle = variableMono,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                        onEndEditing = { model.evaluate() },
                    )
                }
            }
        }
    }

    val results = CanonSection.of {
        CanonRows {
            CanonHeading((shown?.operation ?: operation).resultTitle)
            val values = result?.let { solutions(it, model.approximate) } ?: emptyList()
            if (result != null && isSolve) {
                if (values.isEmpty()) {
                    CanonStatusRow("No solution", CanonTone.Muted)
                } else {
                    values.forEachIndexed { index, value ->
                        val name = AlgebraFormat.solutionName(shown.variable, index, values.size)
                        CanonValueLine("$name $relation", value, layout = CanonValueLayout.Wrapping)
                    }
                }
            } else {
                CanonValueLine("=", result?.text, layout = CanonValueLayout.Wrapping)
            }
            val exact = result?.solutions ?: emptyList()
            val hidesExactness = !isSolve || result == null || AlgebraFormat.formatted(result.approxSolutions ?: emptyList()) == exact
            if (!hidesExactness) {
                CanonSegmented(listOf("Exact", "Approx"), if (model.approximate) 1 else 0) { model.showApproximate(it == 1) }
            }
            CanonGrid(columns = 2) {
                CanonButton(
                    "Use as input",
                    kind = CanonButtonKind.Secondary,
                    enabled = result != null && !isSolve,
                    icon = Icons.Outlined.SubdirectoryArrowLeft,
                    onClick = model::useAsInput,
                )
                CanonButton(
                    "Copy",
                    kind = CanonButtonKind.Primary,
                    enabled = result != null,
                    icon = Icons.Rounded.ContentCopy,
                    oversized = true,
                    flashesCopied = true,
                ) {
                    result ?: return@CanonButton
                    val copied = if (isSolve) values.joinToString(", ") { "${shown.variable} $relation $it" } else result.text
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", copied))
                }
            }
        }
    }

    val typed = shown?.expression?.let(CanonMathNode::parse)
    val examples = CanonSection.of {
        CanonRows {
            CanonHeading("Examples")
            operation.examples.forEach { example ->
                AlgebraExampleRow(example, isCurrent = typed != null && CanonMathNode.parse(example) == typed) { model.load(example) }
            }
        }
    }

    val reference = CanonSection.of {
        CanonRows {
            CanonHeading("Syntax Reference")
            CanonTiles(syntax)
        }
    }

    val picture = CanonSection.canvas(CanonCanvasSize.Fixed(PICTURE_HEIGHT), background = c.card) {
        if (shown != null) AlgebraPicture(pictureLines(shown, model.approximate), faint = shown.expression.isEmpty(), shown)
    }

    CanonSplit(sidebar = listOf(controls), main = listOf(results, examples, reference), banner = picture)
}

/** An example set as mathematics, its syntax beside it; a tap loads it. */
@Composable
private fun AlgebraExampleRow(expression: String, isCurrent: Boolean, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CanonMetrics.rowHeight)
            .canonClickable(onClick = onClick)
            .semantics {
                contentDescription = expression
                selected = isCurrent
            }
            .background(if (isCurrent) c.accentBg else c.card)
            .padding(horizontal = CanonMetrics.panelPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f)) { CanonMathView(expression, size = 19.dp) }
        Text(expression, style = exampleMono, color = c.mutedForeground, maxLines = 1, softWrap = false)
    }
}

private class PictureLine(val row: CanonMathRow, val fallback: String, val size: Float, val ink: MathInk)

private fun symbols(vararg symbols: String) = symbols.map { CanonMathItem.Symbol(it) }

/** Items for [text]; text that does not parse is one run of plain text. */
private fun itemsOf(text: String): List<CanonMathItem> =
    if (CanonMathNode.parse(text) == null) symbols(text) else CanonMathExpression.items(text)

// ponytail: rows, not CanonMath.Node trees: no subscripted names (x₁ is a letter and a subscript digit) and no `present` reordering
/** The input with its operation's notation, and the result under it. */
private fun pictureLines(shown: AlgebraShown, approximate: Boolean): List<PictureLine> {
    val input = shown.expression.ifEmpty { shown.operation.placeholder }
    val result = if (shown.expression.isEmpty()) shown.ghost else shown.result
    val variable = shown.variable
    val parsed = CanonMathNode.parse(input)
    val typed = itemsOf(input)
    val inputItems = when {
        parsed == null -> typed
        shown.operation == AlgebraOperation.Derivative -> listOf(CanonMathItem.Derivative(CanonMathRow(typed.toMutableList())))
        shown.operation == AlgebraOperation.Integral -> listOf(CanonMathItem.Integral(null, null, CanonMathRow(typed.toMutableList())))
        shown.operation == AlgebraOperation.Solve && parsed !is CanonMathNode.Relation -> typed + symbols("=", "0")
        else -> typed
    }
    val lines = mutableListOf(PictureLine(CanonMathRow(inputItems.toMutableList()), input, 24f, MathInk.Input))
    if (result != null) {
        val relation = if (approximate) "≈" else "="
        val items: List<CanonMathItem> = when (shown.operation) {
            AlgebraOperation.Solve -> {
                val values = solutions(result, approximate)
                if (values.isEmpty()) {
                    symbols(variable, "∈", "∅")
                } else {
                    values.flatMapIndexed { index, value ->
                        val name = if (values.size > 1) symbols(variable, AlgebraFormat.subscript(index + 1)) else symbols(variable)
                        (if (index > 0) symbols(",") else emptyList()) + name + symbols(relation) + itemsOf(value)
                    }
                }
            }
            AlgebraOperation.Integral -> {
                val bare = AlgebraFormat.withoutConstant(result.text)
                symbols("=") + itemsOf(bare) + if (bare != result.text) symbols("+", "C") else emptyList()
            }
            else -> symbols("=") + itemsOf(result.text)
        }
        lines += PictureLine(CanonMathRow(items.toMutableList()), "= " + result.text, 32f, MathInk.Result)
    }
    return lines
}

private const val FAINT_ALPHA = 0.3f
private const val LINE_GAP = 12f
// largest the maths grows to fill a roomy band, as a share of its set size
private const val GROWTH = 1.25f
private val relationSigns = setOf("=", "≈", "∈")

/**
 * The working as it is written: the input with its operation's notation (d/dx, ∫ … dx, = 0), and under it the result after
 * a relation sign in the primary colour. A change morphs; with nothing typed, the placeholder and its result show faint.
 */
@Composable
private fun AlgebraPicture(lines: List<PictureLine>, faint: Boolean, key: AlgebraShown) {
    val ink = mathInk()
    val top = LocalCanonCanvas.current.top.value
    val morph = remember { MathMorph() }
    var first by remember { mutableStateOf(true) }
    var promoted by remember { mutableStateOf<AlgebraShown?>(null) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Result"
                stateDescription = lines.joinToString(", ") { it.fallback }
            }
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        LaunchedEffect(key, width, height, top) {
            if (key.promote && promoted !== key) {
                promoted = key
                // result keys become input
                morph.rename { if (it.startsWith("1|")) "0|" + it.drop(2) else if (it.startsWith("0|")) "gone|$it" else it }
            }
            val animated = !first
            first = false
            morph.show(arrange(lines, faint, width, height, top), animated)
        }
        Canvas(Modifier.fillMaxSize()) { morph.draw(this, ink) }
    }
}

/**
 * Lines scaled to fit the width, up to [GROWTH], then together to fit the height, as one block centred in the band,
 * each line starting at the block's leading edge.
 */
private fun arrange(lines: List<PictureLine>, faint: Boolean, width: Float, height: Float, top: Float): List<Pair<String, MathPlaced>> {
    val padding = CanonMetrics.panelPadding.value
    val area = Rect(padding, top + 8, width - padding, height - padding)
    if (area.width <= 0 || area.height <= 0 || lines.isEmpty()) return emptyList()
    val boxes = lines.map { line ->
        val box = MathTypesetter(line.size, line.ink).layout(line.row)
        // steady baselines while typing
        box.ascent = max(box.ascent, line.size * 0.72f)
        box.descent = max(box.descent, line.size * 0.24f)
        box
    }
    val widthFits = boxes.map { area.width / max(1f, it.width) }
    var scales = widthFits.map { min(1f, it) }
    val gaps = LINE_GAP * (boxes.size - 1)
    val total = boxes.indices.sumOf { (boxes[it].height * scales[it]).toDouble() }.toFloat()
    val fit = minOf(GROWTH, widthFits.minOf { max(1f, it) }, (area.height - gaps) / max(1f, total))
    scales = scales.map { it * fit }
    val blockWidth = boxes.indices.maxOf { boxes[it].width * scales[it] }
    val left = area.center.x - blockWidth / 2
    var y = area.center.y - (total * fit + gaps) / 2
    val alpha = if (faint) FAINT_ALPHA else 1f
    val placed = mutableListOf<Pair<String, MathPlaced>>()
    boxes.forEachIndexed { index, box ->
        val scale = scales[index]
        val baseline = y + box.ascent * scale
        val isResult = index > 0
        placed += MathMorph.keyed(box.atoms, "$index") { atom ->
            val mark = atom.mark
            val lit = isResult && mark is MathMark.Glyphs && mark.text in relationSigns
            val f = atom.frame
            val frame = Rect(left + f.left * scale, baseline + f.top * scale, left + f.right * scale, baseline + f.bottom * scale)
            MathPlaced(MathAtom(mark, frame, atom.weight * scale, if (lit) MathInk.Accent else atom.ink), frame, alpha)
        }
        y += box.height * scale + LINE_GAP
    }
    return placed
}
