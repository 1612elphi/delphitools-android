package com.delphi.delphitools.tools.calculators

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMathField
import com.delphi.delphitools.canon.CanonMathFieldHandle
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonSwitchGrid
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.MenuCellItem
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.takesBlackInk
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

private val examples = listOf("x^2", "sin(x)", "sqrt(x)", "log(x)", "abs(x)", "2x + 1", "exp(x)", "tan(x)")

/** Table steps run between these, 1, 2 and 5 times a power of ten. */
private const val STEP_MIN = 1e-4
private const val STEP_MAX = 1e4
private const val RANGE_DELAY_MS = 600L
private const val PLANE_MS = 500
private const val TRACE_MS = 320

/** Share of a coasting pan's speed left after one second. */
private const val COAST_DECAY = 0.02

// grid: target spacing of labelled lines along x and y, and the closest minor lines may get, in dp
private const val SPACING_X = 72.0
private const val SPACING_Y = 48.0
private const val SPACING_MINOR = 9.0
private val grab = 24.dp
private val tagHeight = 22.dp
private val tagGap = 3.dp
private const val REACH = 3
private val tableRow = 34.dp

private val labelFont = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp, fontWeight = FontWeight.Medium)
private val tagFont = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
private val fieldFont = TextStyle(fontFamily = DelphiMono, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
private val rangeFont = TextStyle(fontFamily = DelphiMono, fontSize = 17.sp)
private val tableFont = TextStyle(fontFamily = DelphiMono, fontSize = 15.sp)

/** The palette with the ink that reads on each colour. */
private val palette: List<Pair<Color, Color>> = GraphPlot.palette.map { hex ->
    val rgb = Rgb(((hex shr 16) and 0xFF) / 255.0, ((hex shr 8) and 0xFF) / 255.0, (hex and 0xFF) / 255.0)
    Color(0xFF000000.toInt() or hex) to if (rgb.takesBlackInk) Color.Black else Color.White
}

private fun paint(index: Int) = palette[index % palette.size]

private val easeOut = Easing { 1 - (1 - it).pow(3) }

class PlotCurve(val name: String, val colour: Int, val relation: GraphRelation, val expression: MathExpression) {
    fun evaluate(x: Double) = expression.evaluate(x)
}

/** dt-ios GraphCalcViewController's state: the functions, the trace and its step, and the plane. */
class GraphCalcModel : ViewModel() {
    var curves by mutableStateOf(listOf(GraphCurve(0, "x^2", 0)))
        private set
    var plotted by mutableStateOf<List<PlotCurve>>(emptyList())
        private set
    var editing by mutableStateOf(setOf<Long>())
        private set
    var trace by mutableDoubleStateOf(1.0)
    var step by mutableDoubleStateOf(1.0)
        private set
    var plane by mutableStateOf<GraphWindow?>(null)
    var showsGrid by mutableStateOf(true)
    var showsAxes by mutableStateOf(true)
    var showsNumbers by mutableStateOf(true)

    /** Bumped by a step that runs the table's rows; [slide] is the direction. */
    var slides by mutableIntStateOf(0)
        private set
    var slide = 0
        private set
    var pendingEdit by mutableStateOf<Long?>(null)

    /** Plot size in pixels, for a squared window and resizes. */
    var plotWidth = 0.0
        private set
    var plotHeight = 0.0
        private set
    private var nextId = 1L
    private var typing: Job? = null

    init {
        refresh()
    }

    private fun update(id: Long, change: (GraphCurve) -> GraphCurve) {
        curves = curves.map { if (it.id == id) change(it) else it }
    }

    fun type(id: Long, text: String) {
        update(id) { it.copy(text = text) }
        editing = editing + id
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            refresh()
        }
    }

    fun done(id: Long) {
        editing = editing - id
        refresh()
    }

    fun colour(id: Long, colour: Int) {
        update(id) { it.copy(colour = colour) }
        refresh()
    }

    fun relation(id: Long, relation: GraphRelation) {
        update(id) { it.copy(relation = relation) }
        refresh()
    }

    fun toggleVisible(id: Long) {
        update(id) { it.copy(isVisible = !it.isVisible) }
        refresh()
    }

    fun remove(id: Long) {
        if (curves.size <= 1) return
        curves = curves.filter { it.id != id }
        refresh()
    }

    fun add(text: String) {
        val used = curves.map { it.colour }.toSet()
        val colour = palette.indices.firstOrNull { it !in used } ?: (curves.size % palette.size)
        val curve = GraphCurve(nextId++, text, colour)
        curves = curves + curve
        refresh()
        if (text.isEmpty()) pendingEdit = curve.id
    }

    /** An example fills the last empty function, or adds one. */
    fun use(example: String) {
        val typed = GraphSyntax.typed(example)
        val empty = curves.lastOrNull { it.isEmpty }
        if (empty != null) {
            update(empty.id) { it.copy(text = typed) }
            refresh()
        } else {
            add(typed)
        }
    }

    fun refresh() {
        typing?.cancel()
        plotted = curves.withIndex().filter { it.value.isPlotted }.map { (index, curve) ->
            PlotCurve(GraphCurve.name(index), curve.colour, curve.relation, curve.expression)
        }
    }

    fun fit(width: Double, height: Double) {
        if (width <= 0 || height <= 0) return
        val current = plane
        plane = when {
            current == null -> GraphWindow.square(width, height)
            plotWidth > 0 && (plotWidth != width || plotHeight != height) -> current.resized(plotWidth, plotHeight, width, height)
            else -> current
        }
        plotWidth = width
        plotHeight = height
    }

    /** Where the trace steps to; bumps [slides] when the rows run. */
    fun steppedTrace(direction: Int): Double {
        val (next, onGrid) = GraphPlot.steppedTrace(trace, step, direction)
        if (onGrid) {
            slide = direction
            slides++
        }
        return next
    }

    fun stepStep(direction: Int) {
        val next = if (direction > 0) GraphPlot.niceCeil(step * 1.0001) else GraphPlot.niceFloor(step * 0.9999)
        step = next.coerceIn(STEP_MIN, STEP_MAX)
    }

    fun typeStep(text: String) {
        val value = GraphPlot.number(text) ?: return
        if (value > 0) step = value.coerceIn(STEP_MIN, STEP_MAX)
    }

    /** The plane with one axis set from typed ends; null when they do not make a span a pinch could reach. */
    fun typedPlane(axis: Int, low: String, high: String): GraphWindow? {
        val plane = plane ?: return null
        val a = GraphPlot.number(low) ?: return null
        val b = GraphPlot.number(high) ?: return null
        if (a == b) return null
        val (from, to) = minOf(a, b) to maxOf(a, b)
        if (to - from !in GraphWindow.SPAN_MIN..GraphWindow.SPAN_MAX) return null
        return if (axis == 0) plane.copy(xMin = from, xMax = to) else plane.copy(yMin = from, yMax = to)
    }
}

/** Window and trace tweens and a coasting pan, on the composition's frame clock. */
private class GraphMotion(private val scope: CoroutineScope, private val model: GraphCalcModel) {
    private var window: Job? = null
    private var traceJob: Job? = null

    fun stop() {
        window?.cancel()
    }

    fun window(target: GraphWindow) {
        stop()
        val from = model.plane ?: return run { model.plane = target }
        window = scope.launch {
            val progress = Animatable(0f)
            progress.animateTo(1f, tween(PLANE_MS, easing = easeOut)) { model.plane = GraphWindow.blend(from, target, value.toDouble()) }
            model.plane = target
        }
    }

    fun trace(target: Double, animated: Boolean = true) {
        traceJob?.cancel()
        val from = model.trace
        if (!animated || model.plane == null || from == target) {
            model.trace = target
            return
        }
        traceJob = scope.launch {
            val progress = Animatable(0f)
            progress.animateTo(1f, tween(TRACE_MS, easing = easeOut)) { model.trace = from + (target - from) * value }
            model.trace = target
        }
    }

    fun stopTrace() {
        traceJob?.cancel()
    }

    /** [vx] and [vy] in pixels per second; [perX] and [perY] plane units per pixel. */
    fun coast(vx: Float, vy: Float, perX: Double, perY: Double) {
        stop()
        window = scope.launch {
            var velocityX = vx.toDouble()
            var velocityY = vy.toDouble()
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = minOf((now - last) / 1e9, 1.0 / 30)
                last = now
                val plane = model.plane ?: return@launch
                model.plane = plane.moved(-velocityX * dt * perX, velocityY * dt * perY)
                val keep = COAST_DECAY.pow(dt)
                velocityX *= keep
                velocityY *= keep
                if (hypot(velocityX, velocityY) < 20) return@launch
            }
        }
    }
}

@Composable
fun GraphCalc(model: GraphCalcModel = viewModel()) {
    val scope = rememberCoroutineScope()
    val motion = remember(model) { GraphMotion(scope, model) }
    val focus = LocalFocusManager.current
    val handles = remember { mutableMapOf<Long, CanonMathFieldHandle>() }

    LaunchedEffect(model.pendingEdit) {
        val id = model.pendingEdit ?: return@LaunchedEffect
        handles[id]?.edit()
        model.pendingEdit = null
    }

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(1f), maxHeight = 640.dp) { GraphPlotView(model, motion) }

    val functions = CanonSection.of {
        CanonRows {
            CanonHeading("Functions")
            val removable = model.curves.size > 1
            model.curves.forEachIndexed { index, curve ->
                androidx.compose.runtime.key(curve.id) {
                    FunctionRow(curve, GraphCurve.name(index), removable, curve.id in model.editing, model, handles.getOrPut(curve.id) { CanonMathFieldHandle() })
                }
            }
            CanonButton("Add function", Modifier.fillMaxWidth(), icon = Icons.Outlined.Add) { model.add("") }
        }
    }

    val traceBlock = CanonSection.of {
        CanonRows {
            CanonHeading("Trace")
            Stepper(
                GraphPlot.format(model.trace),
                { text -> GraphPlot.number(text)?.let { if (it != model.trace) motion.trace(it) } },
                { direction -> motion.trace(model.steppedTrace(direction)) },
                "Trace",
                Modifier.height(CanonMetrics.rowHeight),
                lead = "x",
            )
            CanonRow("Step", flush = true) {
                Stepper(GraphPlot.format(model.step), model::typeStep, model::stepStep, "Step")
            }
            ValueTable(model.trace, model.step, model.plotted, model.slides, model.slide)
        }
    }

    val viewBlock = CanonSection.of {
        CanonRows {
            CanonHeading("View")
            val plane = model.plane
            CanonRow("x", flush = true) {
                RangePair(plane?.xMin, plane?.xMax) { low, high -> commitRange(model, motion, 0, low, high) }
            }
            CanonRow("y", flush = true) {
                RangePair(plane?.yMin, plane?.yMax) { low, high -> commitRange(model, motion, 1, low, high) }
            }
            CanonGrid(columns = 4) {
                val presets = listOf<Pair<String, () -> GraphWindow?>>(
                    "Standard" to { GraphWindow.standard },
                    "Trig" to { GraphWindow.trig },
                    "Decimal" to { GraphWindow.decimal },
                    "Square" to { model.plane?.squared(model.plotWidth, model.plotHeight) },
                )
                presets.forEach { (title, plane) ->
                    CanonButton(title, kind = CanonButtonKind.Segment) {
                        focus.clearFocus()
                        plane()?.let(motion::window)
                    }
                }
            }
            CanonSwitchGrid(listOf("Grid" to model.showsGrid, "Axes" to model.showsAxes, "Numbers" to model.showsNumbers), columns = 3) { index, on ->
                when (index) {
                    0 -> model.showsGrid = on
                    1 -> model.showsAxes = on
                    else -> model.showsNumbers = on
                }
            }
        }
    }

    val examplesBlock = CanonSection.of {
        CanonRows {
            CanonHeading("Examples")
            CanonGrid(columns = 4) {
                examples.forEach { example -> CanonButton(example) { model.use(example) } }
            }
        }
    }

    CanonSplit(
        sidebar = listOf(canvas),
        main = listOf(functions, traceBlock, viewBlock, examplesBlock),
        halves = true,
        collapsesTo = 240.dp,
    )
}

/** A typed range: animated to when it makes a span, else the fields go back to the plane's. */
private fun commitRange(model: GraphCalcModel, motion: GraphMotion, axis: Int, low: String, high: String): Boolean {
    val plane = model.typedPlane(axis, low, high) ?: return false
    motion.window(plane)
    return true
}

@Composable
private fun FunctionRow(curve: GraphCurve, name: String, removable: Boolean, editing: Boolean, model: GraphCalcModel, handle: CanonMathFieldHandle) {
    val c = LocalDelphiColours.current
    Column(Modifier.fillMaxWidth().background(c.border), verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = CanonMetrics.rowHeight),
            horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
        ) {
            SwatchCell(curve, name) { model.colour(curve.id, it) }
            RelationCell(curve.relation) { model.relation(curve.id, it) }
            CanonMathField(
                curve.text,
                { model.type(curve.id, it) },
                Modifier.weight(1f),
                placeholder = "sin(x)",
                handle = handle,
                size = 20.dp,
                onDone = { model.done(curve.id) },
            )
            CanonButton(
                "",
                Modifier.width(44.dp).fillMaxHeight(),
                icon = if (curve.isVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                contentDescription = if (curve.isVisible) "Hide" else "Show",
            ) { model.toggleVisible(curve.id) }
            if (removable) {
                CanonButton("", Modifier.width(44.dp).fillMaxHeight(), icon = Icons.Outlined.Delete, contentDescription = "Remove") { model.remove(curve.id) }
            }
        }
        // no error while editing
        if (curve.isInvalid && !editing) CanonStatusRow("Invalid expression", CanonTone.Warn)
    }
}

/** The curve's colour as a cell with its name on it; a tap opens the palette as a grid of swatches. */
@Composable
private fun SwatchCell(curve: GraphCurve, name: String, onPick: (Int) -> Unit) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    var open by remember { mutableStateOf(false) }
    val (fill, ink) = paint(curve.colour)
    val shown by animateColorAsState(if (curve.isVisible) fill else fill.copy(alpha = 0.25f), tween(250))
    Box(Modifier.width(52.dp).fillMaxHeight()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(shown)
                .clickable { open = true }
                .semantics {
                    contentDescription = "Colour"
                    stateDescription = name
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(name, style = CanonText.bodySemibold, color = if (curve.isVisible) ink else c.foreground)
        }
        MenuCellPopup(open, { open = false }) {
            Column(Modifier.background(c.border).padding(CanonMetrics.frame), verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
                palette.indices.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
                        row.forEach { index ->
                            val (swatch, check) = palette[index]
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .background(swatch)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        open = false
                                        onPick(index)
                                    }
                                    .semantics { contentDescription = "Colour ${index + 1}" },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (index == curve.colour) Icon(Icons.Outlined.Check, null, tint = check, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationCell(relation: GraphRelation, onPick: (GraphRelation) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.width(44.dp).fillMaxHeight(), propagateMinConstraints = true) {
        CanonButton(relation.symbol, Modifier.fillMaxHeight()) { open = true }
        MenuCellPopup(open, { open = false }) {
            GraphRelation.entries.forEach { choice ->
                MenuCellItem(choice.symbol, checked = choice == relation) {
                    open = false
                    onPick(choice)
                }
            }
        }
    }
}

/** A typed number, committed after a typing pause or when it loses focus; it shows [value] whenever it is not focused. */
@Composable
private fun NumberField(
    value: String,
    onCommit: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    style: TextStyle = fieldFont,
    align: TextAlign = TextAlign.End,
    pause: Long = TYPING_DELAY_MS,
) {
    val c = LocalDelphiColours.current
    val focus = LocalFocusManager.current
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    val commit by rememberUpdatedState(onCommit)
    LaunchedEffect(value, focused) { if (!focused) text = value }
    LaunchedEffect(text) {
        if (!focused) return@LaunchedEffect
        delay(pause)
        commit(text)
    }
    BasicTextField(
        text,
        { text = it },
        modifier
            .onFocusChanged {
                if (focused && !it.isFocused) commit(text)
                focused = it.isFocused
            }
            .semantics { contentDescription = label },
        textStyle = style.copy(color = c.foreground, textAlign = align),
        singleLine = true,
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
    )
}

/** dt-ios CanonStepper with a typed centre: minus, the field, plus; minus and plus repeat while held. */
@Composable
private fun Stepper(value: String, onCommit: (String) -> Unit, onStep: (Int) -> Unit, label: String, modifier: Modifier = Modifier, lead: String? = null) {
    val c = LocalDelphiColours.current
    Row(
        modifier.height(IntrinsicSize.Min).heightIn(min = CanonMetrics.controlHeight).background(c.border),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        CanonButton("", Modifier.width(44.dp).fillMaxHeight(), icon = Icons.Rounded.Remove, repeats = true, contentDescription = "Decrease") { onStep(-1) }
        Row(
            Modifier.weight(1f).fillMaxHeight().background(c.card).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (lead != null) Text(lead, style = CanonText.bodySemibold, color = c.mutedForeground)
            NumberField(value, onCommit, label, Modifier.weight(1f))
        }
        CanonButton("", Modifier.width(44.dp).fillMaxHeight(), icon = Icons.Outlined.Add, repeats = true, contentDescription = "Increase") { onStep(1) }
    }
}

/** Minimum and maximum of one axis, either side of "to"; [onCommit] says whether the typed pair was taken. */
@Composable
private fun RangePair(low: Double?, high: Double?, onCommit: (String, String) -> Boolean) {
    val c = LocalDelphiColours.current
    val (shownLow, shownHigh) = if (low != null && high != null) {
        // rounded to about a thousandth of the span
        val grain = GraphPlot.niceFloor((high - low) / 1000)
        GraphPlot.format((low / grain).roundedAway() * grain) to GraphPlot.format((high / grain).roundedAway() * grain)
    } else {
        "" to ""
    }
    var typedLow by remember { mutableStateOf<String?>(null) }
    var typedHigh by remember { mutableStateOf<String?>(null) }
    fun commit() {
        onCommit(typedLow ?: shownLow, typedHigh ?: shownHigh)
    }
    Row(
        Modifier.height(IntrinsicSize.Min).heightIn(min = CanonMetrics.controlHeight).background(c.border),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        val cell = Modifier.weight(1f).fillMaxHeight().background(c.card).padding(horizontal = 6.dp)
        Box(cell, contentAlignment = Alignment.Center) {
            NumberField(shownLow, { typedLow = it; commit() }, "Minimum", Modifier.fillMaxWidth(), rangeFont, TextAlign.Center, RANGE_DELAY_MS)
        }
        Box(Modifier.width(36.dp).fillMaxHeight().background(c.card), contentAlignment = Alignment.Center) {
            Text("to", style = CanonText.caption.copy(fontSize = 13.sp), color = c.mutedForeground)
        }
        Box(cell, contentAlignment = Alignment.Center) {
            NumberField(shownHigh, { typedHigh = it; commit() }, "Maximum", Modifier.fillMaxWidth(), rangeFont, TextAlign.Center, RANGE_DELAY_MS)
        }
    }
    LaunchedEffect(shownLow, shownHigh) {
        typedLow = null
        typedHigh = null
    }
}

/** Values around the trace: x and each plotted curve, the trace's row in a lit band while stepped rows slide through it. */
@Composable
private fun ValueTable(trace: Double, step: Double, curves: List<PlotCurve>, slides: Int, slide: Int) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val pitch = tableRow + CanonMetrics.hairline
    val shift = remember { Animatable(0f) }
    LaunchedEffect(slides) {
        if (slides == 0) return@LaunchedEffect
        shift.snapTo(slide * with(density) { pitch.toPx() })
        shift.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 250f))
    }
    val centre = curves.joinToString(", ", prefix = "x ${GraphPlot.format(trace)}, ") { "${it.name} ${GraphPlot.format(it.evaluate(trace))}" }
    Column(
        Modifier.fillMaxWidth().background(c.border).semantics(mergeDescendants = true) {
            contentDescription = "Table"
            stateDescription = centre
        },
        verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
            HeaderCell("x", c.muted, c.foreground)
            curves.forEach { curve ->
                val (fill, ink) = paint(curve.colour)
                HeaderCell(curve.name, fill, ink)
            }
        }
        Box(Modifier.fillMaxWidth().height(pitch * (REACH * 2 + 1) - CanonMetrics.hairline).background(c.card).clipToBounds()) {
            Box(
                Modifier
                    .offset(y = pitch * REACH - CanonMetrics.hairline)
                    .fillMaxWidth()
                    .height(pitch + CanonMetrics.hairline)
                    .background(c.accentBg)
                    .border(CanonMetrics.hairline, c.primary.copy(alpha = 0.5f)),
            )
            for (offset in 0 until REACH * 2 + 3) {
                val x = GraphPlot.tableX(trace, step, offset - REACH - 1)
                Row(
                    Modifier
                        .offset { IntOffset(0, ((offset - 1) * pitch.toPx() + shift.value).roundToInt()) }
                        .fillMaxWidth()
                        .height(pitch),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TableCell(GraphPlot.format(x), FontWeight.SemiBold, c.foreground)
                    curves.forEach { TableCell(GraphPlot.format(it.evaluate(x)), FontWeight.Normal, c.secondaryForeground) }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, fill: Color, ink: Color) {
    Box(Modifier.weight(1f).fillMaxHeight().background(fill), contentAlignment = Alignment.Center) {
        Text(text, style = CanonText.footnoteSemibold.copy(fontSize = 13.sp), color = ink, maxLines = 1)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableCell(text: String, weight: FontWeight, colour: Color) {
    // ponytail: a value too wide for its column is clipped; iOS shrinks it to 60%
    Text(text, Modifier.weight(1f), style = tableFont.copy(fontWeight = weight), color = colour, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
}

/** The plane with its curves: pan, pinch, a draggable trace line with a tag per curve. */
@Composable
private fun GraphPlotView(model: GraphCalcModel, motion: GraphMotion) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val topInset = LocalCanonCanvas.current.top
    val topPx = with(density) { topInset.toPx() }
    val top by rememberUpdatedState(topPx)
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(size, topPx) { model.fit(size.width.toDouble(), size.height - topPx.toDouble()) }

    fun plotRect() = Rect(0f, top, size.width.toFloat(), size.height.toFloat())
    fun screenX(x: Double, plane: GraphWindow, plot: Rect) = plot.left + ((x - plane.xMin) / plane.spanX * plot.width).toFloat()
    fun screenY(y: Double, plane: GraphWindow, plot: Rect) = plot.top + ((plane.yMax - y) / plane.spanY * plot.height).toFloat()
    fun planeX(px: Float, plane: GraphWindow, plot: Rect) = plane.xMin + (px - plot.left) / plot.width * plane.spanX
    fun planeY(py: Float, plane: GraphWindow, plot: Rect) = plane.yMax - (py - plot.top) / plot.height * plane.spanY

    /** Where a drag rests: the nearest multiple of a round value about two dp wide. */
    fun snapped(x: Double, plane: GraphWindow, plot: Rect): Double {
        val grain = GraphPlot.niceFloor(plane.spanX / plot.width * density.density * 2)
        return (x / grain).roundedAway() * grain
    }

    val trace = model.trace
    val plotted = model.plotted
    val description = (listOf("x " + GraphPlot.format(trace)) + plotted.map { "${it.name} " + GraphPlot.format(it.evaluate(trace)) }).joinToString(", ")

    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .semantics {
                contentDescription = "Graph"
                stateDescription = description
            }
            .pointerInput(model) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = model.plane ?: return@awaitEachGesture
                    val plot = plotRect()
                    if (plot.width <= 0 || plot.height <= 0) return@awaitEachGesture
                    val traceAt = screenX(model.trace, start, plot)
                    val grabbed = abs(down.position.x - traceAt) < grab.toPx() || xTagRect(traceAt, plot, model.trace, measurer, density).let { rect ->
                        rect != null && rect.inflate(12.dp.toPx()).contains(down.position)
                    }
                    if (grabbed) {
                        motion.stop()
                        motion.stopTrace()
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        fun follow(point: Offset) {
                            val plane = model.plane ?: return
                            val x = snapped(planeX(point.x.coerceIn(plot.left, plot.right), plane, plot), plane, plot)
                            if (x != model.trace) model.trace = x
                        }
                        follow(down.position)
                        down.consume()
                        drag(down.id) { change ->
                            follow(change.position)
                            change.consume()
                        }
                        return@awaitEachGesture
                    }
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var moved = false
                    var travel = Offset.Zero
                    var fingers = 1
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        fingers = maxOf(fingers, pressed.size)
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (!moved) {
                            travel += pan
                            if (travel.getDistance() > viewConfiguration.touchSlop || zoom != 1f) {
                                moved = true
                                motion.stop()
                            }
                        }
                        if (moved) {
                            val plane = model.plane ?: break
                            val next = if (zoom != 1f && zoom > 0f) {
                                val centroid = event.calculateCentroid()
                                val previous = centroid - pan
                                val zoomed = plane.zoomed(1.0 / maxOf(zoom, 0.01f), planeX(previous.x, plane, plot), planeY(previous.y, plane, plot))
                                zoomed.moved(-pan.x * zoomed.spanX / plot.width, pan.y * zoomed.spanY / plot.height)
                            } else {
                                plane.moved(-pan.x * plane.spanX / plot.width, pan.y * plane.spanY / plot.height)
                            }
                            model.plane = next
                            event.changes.forEach { it.consume() }
                        }
                        if (pressed.size == 1) tracker.addPosition(pressed[0].uptimeMillis, pressed[0].position) else tracker.resetTracking()
                    }
                    if (!moved) {
                        val x = snapped(planeX(down.position.x, start, plot), start, plot)
                        motion.trace(x)
                    } else if (fingers == 1) {
                        val velocity = tracker.calculateVelocity()
                        val plane = model.plane ?: return@awaitEachGesture
                        if (hypot(velocity.x, velocity.y) > 120.dp.toPx()) {
                            motion.coast(velocity.x, velocity.y, plane.spanX / plot.width, plane.spanY / plot.height)
                        }
                    }
                }
            },
    ) {
        val plane = model.plane ?: return@Canvas
        val plot = Rect(0f, topPx, size.width.toFloat(), size.height.toFloat())
        if (plot.width <= 0 || plot.height <= 0) return@Canvas
        drawGrid(plane, plot, model, c.foreground, c.mutedForeground, measurer, ::screenX, ::screenY, ::planeX, ::planeY)
        drawCurves(plotted, plane, plot, ::screenX, ::screenY, ::planeX)
        drawTrace(model.trace, plotted, plane, plot, c.primary, c.primaryForeground, c.card, measurer, ::screenX, ::screenY)
    }
}

/** Where the x tag sits for a trace at [x] on screen, or null when the trace is off the plot. */
private fun xTagRect(x: Float, plot: Rect, trace: Double, measurer: androidx.compose.ui.text.TextMeasurer, density: androidx.compose.ui.unit.Density): Rect? {
    if (x < plot.left || x > plot.right) return null
    with(density) {
        val width = measurer.measure(GraphPlot.format(trace), tagFont).size.width + 12.dp.toPx()
        val height = tagHeight.toPx()
        val left = (x - width / 2).coerceIn(plot.left, maxOf(plot.left, plot.right - width))
        val top = plot.bottom - height - 8.dp.toPx()
        return Rect(left, top, left + width, top + height)
    }
}

private fun DrawScope.drawGrid(
    plane: GraphWindow,
    plot: Rect,
    model: GraphCalcModel,
    foreground: Color,
    muted: Color,
    measurer: androidx.compose.ui.text.TextMeasurer,
    screenX: (Double, GraphWindow, Rect) -> Float,
    screenY: (Double, GraphWindow, Rect) -> Float,
    planeX: (Float, GraphWindow, Rect) -> Double,
    planeY: (Float, GraphWindow, Rect) -> Double,
) {
    val perX = plane.spanX / plot.width * density
    val perY = plane.spanY / plot.height * density
    val stepX = GraphPlot.niceCeil(perX * SPACING_X)
    val stepY = GraphPlot.niceCeil(perY * SPACING_Y)
    if (model.showsGrid) {
        val minor = Path()
        val major = Path()
        for (vertical in listOf(true, false)) {
            val step = if (vertical) stepX else stepY
            // whole view, not plot
            val seen = if (vertical) planeX(0f, plane, plot) to planeX(size.width, plane, plot) else planeY(size.height, plane, plot) to planeY(0f, plane, plot)
            val divisions = GraphPlot.minorDivisions(step)
            val fine = step / divisions
            val showsMinor = fine / (if (vertical) perX else perY) >= SPACING_MINOR
            for (count in GraphPlot.ticks(seen.first, seen.second, fine)) {
                if (!showsMinor && count % divisions != 0L) continue
                val path = if (count % divisions == 0L) major else minor
                val value = count * fine
                if (vertical) {
                    val x = screenX(value, plane, plot)
                    path.moveTo(x, 0f)
                    path.lineTo(x, size.height)
                } else {
                    val y = screenY(value, plane, plot)
                    path.moveTo(0f, y)
                    path.lineTo(size.width, y)
                }
            }
        }
        drawPath(minor, foreground.copy(alpha = 0.07f), style = Stroke(CanonMetrics.hairline.toPx() / 2))
        drawPath(major, foreground.copy(alpha = 0.14f), style = Stroke(CanonMetrics.hairline.toPx()))
    }
    val originX = screenX(0.0, plane, plot)
    val originY = screenY(0.0, plane, plot)
    if (model.showsAxes) {
        val axis = foreground.copy(alpha = 0.7f)
        val width = 1.5.dp.toPx()
        if (originY in 0f..size.height) drawLine(axis, Offset(0f, originY), Offset(size.width, originY), width)
        if (originX in 0f..size.width) drawLine(axis, Offset(originX, 0f), Offset(originX, size.height), width)
    }
    if (!model.showsNumbers) return
    val style = labelFont.copy(color = muted)
    val height = measurer.measure("0", style).size.height.toFloat()
    val inner = Rect(plot.left + 4.dp.toPx(), plot.top + 3.dp.toPx(), plot.right - 4.dp.toPx(), plot.bottom - 3.dp.toPx())
    val rowY = (originY + 4.dp.toPx()).coerceIn(inner.top, maxOf(inner.top, inner.bottom - height))
    for (count in GraphPlot.ticks(plane.xMin, plane.xMax, stepX)) {
        if (count == 0L) continue
        val label = measurer.measure(GraphPlot.tickLabel(count, stepX), style)
        val x = screenX(count * stepX, plane, plot)
        val left = (x - label.size.width / 2).coerceIn(inner.left, maxOf(inner.left, inner.right - label.size.width))
        drawText(label, topLeft = Offset(left, rowY))
    }
    for (count in GraphPlot.ticks(plane.yMin, plane.yMax, stepY)) {
        if (count == 0L) continue
        val y = screenY(count * stepY, plane, plot)
        if (y - height / 2 <= inner.top || y + height / 2 >= inner.bottom) continue
        val label = measurer.measure(GraphPlot.tickLabel(count, stepY), style)
        val leftOf = originX - 5.dp.toPx() - label.size.width
        val left = if (leftOf < inner.left) maxOf(originX + 5.dp.toPx(), inner.left) else minOf(leftOf, inner.right - label.size.width)
        drawText(label, topLeft = Offset(left, y - height / 2))
    }
    val margin = 12.dp.toPx()
    if (originX in plot.left + margin..plot.right - margin && originY in plot.top + margin..plot.bottom - margin) {
        val zero = measurer.measure("0", style)
        drawText(zero, topLeft = Offset(originX - 5.dp.toPx() - zero.size.width, originY + 4.dp.toPx()))
    }
}

// ponytail: curves are sampled on the main thread every frame; 8 curves of 1,600 samples took 1.7 ms on a JVM laptop. Cache runs per plane if a phone misses frames
private fun DrawScope.drawCurves(
    curves: List<PlotCurve>,
    plane: GraphWindow,
    plot: Rect,
    screenX: (Double, GraphWindow, Rect) -> Float,
    screenY: (Double, GraphWindow, Rect) -> Float,
    planeX: (Float, GraphWindow, Rect) -> Double,
) {
    val samples = minOf(size.width.toInt(), 1600)
    val low = -size.height * 4
    val high = size.height * 5
    for (curve in curves) {
        val colour = paint(curve.colour).first
        val runs = GraphPlot.runs(samples, 0f, size.width, plane.spanY, { planeX(it, plane, plot) }, { screenY(it, plane, plot) }, low, high, curve::evaluate)
        val line = Path()
        val region = Path()
        val edge = when (curve.relation) {
            GraphRelation.Equal -> null
            GraphRelation.LessThan, GraphRelation.LessOrEqual -> size.height
            GraphRelation.GreaterThan, GraphRelation.GreaterOrEqual -> 0f
        }
        for (run in runs) {
            line.moveTo(run[0], run[1])
            for (index in 2 until run.size step 2) line.lineTo(run[index], run[index + 1])
            if (edge != null) {
                region.moveTo(run[0], edge)
                for (index in run.indices step 2) region.lineTo(run[index], run[index + 1])
                region.lineTo(run[run.size - 2], edge)
                region.close()
            }
        }
        if (edge != null) drawPath(region, colour.copy(alpha = 0.14f))
        val strict = curve.relation == GraphRelation.LessThan || curve.relation == GraphRelation.GreaterThan
        val dash = if (strict) {
            // dashes travel with plane
            val period = 12.dp.toPx()
            PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()), -(screenX(0.0, plane, plot) % period))
        } else {
            null
        }
        drawPath(line, colour.copy(alpha = if (strict) 0.75f else 1f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash))
    }
}

private fun DrawScope.drawTrace(
    traceX: Double,
    curves: List<PlotCurve>,
    plane: GraphWindow,
    plot: Rect,
    primary: Color,
    primaryInk: Color,
    card: Color,
    measurer: androidx.compose.ui.text.TextMeasurer,
    screenX: (Double, GraphWindow, Rect) -> Float,
    screenY: (Double, GraphWindow, Rect) -> Float,
) {
    val x = screenX(traceX, plane, plot)
    val onScreen = x in plot.left..plot.right
    if (onScreen) drawLine(primary.copy(alpha = 0.8f), Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
    val tag = tagHeight.toPx()
    val pad = 6.dp.toPx()
    val xTag = xTagRect(x, plot, traceX, measurer, this)
    val xTagHeight = tag

    // tags away from line
    val leading = x > plot.center.x + plot.width * 0.2f
    class Placed(val curve: PlotCurve, val y: Float, val dotY: Float?)
    val placed = curves.map { curve ->
        val value = curve.evaluate(traceX)
        val y = if (value.isFinite()) screenY(value, plane, plot) else null
        val visible = y != null && y in plot.top..plot.bottom && onScreen
        val clamped = y?.coerceIn(plot.top + tag / 2 + 4.dp.toPx(), maxOf(plot.top + tag / 2 + 4.dp.toPx(), plot.bottom - tag / 2 - xTagHeight - 16.dp.toPx()))
        Placed(curve, clamped ?: plot.center.y, if (visible) y else null) to (onScreen && y != null)
    }
    val highest = plot.top + 4.dp.toPx() + tag / 2
    val centres = GraphPlot.spread(placed.map { it.first.y }, tag + tagGap.toPx(), highest, maxOf(highest, plot.bottom - xTagHeight - 16.dp.toPx() - tag / 2))
    placed.forEachIndexed { index, (entry, shown) ->
        val (fill, ink) = paint(entry.curve.colour)
        val centre = centres[index]
        if (!shown) return@forEachIndexed
        val text = measurer.measure(GraphPlot.format(entry.curve.evaluate(traceX)), tagFont.copy(color = ink))
        val width = text.size.width + pad * 2
        val left = if (leading) plot.left else plot.right - width
        val dotY = entry.dotY
        if (dotY != null) {
            val end = if (leading) left + width else left
            drawLine(fill.copy(alpha = 0.6f), Offset(x, dotY), Offset(end, centre), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
        }
        drawRect(fill, Offset(left, centre - tag / 2), Size(width, tag))
        drawText(text, topLeft = Offset(left + pad, centre - text.size.height / 2))
        if (dotY != null) {
            drawCircle(fill, 5.dp.toPx(), Offset(x, dotY))
            drawCircle(card, 5.dp.toPx(), Offset(x, dotY), style = Stroke(2.dp.toPx()))
        }
    }
    if (xTag != null) {
        drawRect(primary, xTag.topLeft, xTag.size)
        val text = measurer.measure(GraphPlot.format(traceX), tagFont.copy(color = primaryInk))
        drawText(text, topLeft = Offset(xTag.left + pad, xTag.center.y - text.size.height / 2))
    }
}
