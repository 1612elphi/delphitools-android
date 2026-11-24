package com.delphi.delphitools.tools.calculators

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonBigNumberField
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonFromTo
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.tools.typography.PxToRemEngine
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiColours
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val categories = UnitConverterEngine.categories
private val PICTURE_HEIGHT = 204.dp
private const val SETTLE_MILLIS = 600
private val symbolMono = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
private val valueMono = TextStyle(fontFamily = DelphiMono, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)

/** dt-ios UnitConverterViewModel and the controller's state. */
class UnitConverterModel : ViewModel() {
    var category by mutableStateOf("Length")
        private set
    var input by mutableStateOf("1")
        private set

    /** The input as the rows and the picture last took it. */
    var shown by mutableStateOf("1")
        private set
    var fromIndex by mutableIntStateOf(0)
        private set
    var toIndex by mutableIntStateOf(1)
        private set
    var ppi by mutableIntStateOf(300)
        private set

    /** Row taps fill from, then to, then from again. */
    private var tapsFrom = true
    private var typing: Job? = null

    val units get() = UnitConverterEngine.units(category, ppi)
    val from get() = units[fromIndex]
    val to get() = units[toIndex]
    val value get() = shown.toDoubleOrNull()?.takeIf { it.isFinite() }

    fun converted(index: Int) = value?.let { UnitConverterEngine.convert(it, from, units[index]) }

    init {
        choose("Length")
    }

    private fun later() {
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            refresh()
        }
    }

    fun refresh() {
        typing?.cancel()
        shown = input
    }

    fun type(text: String) {
        input = clean(text)
        later()
    }

    private fun set(text: String) {
        input = clean(text)
        refresh()
    }

    private fun clean(text: String) = text.replace(',', '.').replace('−', '-')

    fun nudge(direction: Int) {
        val floor = if (category == "Temperature") from.value(0.0) else 0.0
        set(PxToRemEngine.format(UnitConverterEngine.nudge(input.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0, direction, floor)))
    }

    fun choose(category: String) {
        this.category = category
        val units = units
        val opening = UnitConverterEngine.pairs[category]
        fromIndex = units.indexOfFirst { it.id == opening?.first }.takeIf { it >= 0 } ?: 0
        toIndex = units.indexOfFirst { it.id == opening?.second }.takeIf { it >= 0 } ?: min(1, units.size - 1)
        tapsFrom = true
        refresh()
    }

    /** A unit picked twice swaps the pair. */
    fun pickFrom(index: Int) {
        if (index == toIndex) toIndex = fromIndex
        fromIndex = index
        refresh()
    }

    fun pickTo(index: Int) {
        if (index == fromIndex) fromIndex = toIndex
        toIndex = index
        refresh()
    }

    fun tap(index: Int) {
        if (tapsFrom) pickFrom(index) else pickTo(index)
        tapsFrom = !tapsFrom
    }

    fun swap() {
        val from = fromIndex
        fromIndex = toIndex
        toIndex = from
        refresh()
    }

    fun changePpi(value: Int) {
        ppi = value
        later()
    }
}

private enum class UnitRole { From, To, Other }

@Composable
fun UnitConverter(model: UnitConverterModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val units = model.units
    val from = model.from
    val to = model.to
    val value = model.value
    val result = model.converted(model.toIndex)

    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Category")
            CanonSegmented(categories, categories.indexOf(model.category), columns = 3) { model.choose(categories[it]) }
        }
    }

    val convert = CanonSection.of {
        CanonRows {
            CanonBigNumberField(
                model.input,
                model::type,
                onStep = model::nudge,
                placeholder = "0",
                unit = from.symbol,
                label = "Value",
                onEndEditing = model::refresh,
                // minus sign for temperatures
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (model.category == "Temperature") KeyboardType.Ascii else KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
            )
            CanonFromTo(units.map { it.label }, model.fromIndex, model.toIndex, model::pickFrom, model::pickTo)
            CanonGrid(columns = 2) {
                CanonButton("Swap", icon = Icons.Rounded.SwapHoriz, onClick = model::swap)
                CanonButton("Copy", enabled = value != null, icon = Icons.Rounded.ContentCopy, flashesCopied = true) {
                    result ?: return@CanonButton
                    val text = AlgebraFormat.significant(result)
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", text))
                }
            }
            if (model.category == "Length") {
                CanonRow("Pixel Density", flush = true) {
                    CanonNumberField(model.ppi, model::changePpi, range = 1..10000, unit = "PPI")
                }
            }
        }
    }

    val table = CanonSection.of {
        CanonRows {
            CanonHeading("Units")
            units.forEachIndexed { index, unit ->
                val role = when (index) {
                    model.fromIndex -> UnitRole.From
                    model.toIndex -> UnitRole.To
                    else -> UnitRole.Other
                }
                UnitRow(unit, model.converted(index)?.let(UnitConverterEngine::format) ?: "", role) { model.tap(index) }
            }
        }
    }

    val reference = if (model.category == "Temperature") {
        listOf(CanonTile(to.symbol, UnitConverterEngine.formula(from, to)), CanonTile(from.symbol, UnitConverterEngine.formula(to, from)))
    } else {
        val there = to.value(from.base(1.0))
        val back = from.value(to.base(1.0))
        listOf(
            CanonTile("1 " + from.symbol, UnitConverterEngine.format(there) + " " + to.symbol),
            CanonTile("1 " + to.symbol, UnitConverterEngine.format(back) + " " + from.symbol),
        )
    }
    val quick = CanonSection.of {
        CanonRows {
            CanonHeading("Quick Reference")
            CanonTiles(reference)
        }
    }

    val picture = CanonSection.canvas(CanonCanvasSize.Fixed(PICTURE_HEIGHT), background = c.card) {
        UnitPicture(UnitConverterEngine.instrument(model.category), value, from, to)
    }

    CanonSplit(sidebar = listOf(controls, convert), main = listOf(table, quick), banner = picture)
}

/** Both picked rows are lit; the plate tells them apart, as the banner's scales do: ink for the from unit, primary for the result. */
@Composable
private fun UnitRow(unit: UnitScale, value: String, role: UnitRole, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    val background by animateColorAsState(if (role != UnitRole.Other) c.accentBg else c.card, tween(200))
    val plate by animateColorAsState(
        when (role) {
            UnitRole.From -> c.foreground
            UnitRole.To -> c.primary
            UnitRole.Other -> Color.Transparent
        },
        tween(200),
    )
    val symbol = when (role) {
        UnitRole.From -> c.background
        UnitRole.To -> c.primaryForeground
        UnitRole.Other -> c.mutedForeground
    }
    Row(
        Modifier
            .fillMaxWidth()
            .canonClickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = unit.label
                if (value.isNotEmpty()) stateDescription = value + " " + unit.symbol
                selected = role != UnitRole.Other
            }
            .background(background)
            .heightIn(min = CanonMetrics.rowHeight)
            .padding(start = 10.dp, top = 8.dp, end = CanonMetrics.panelPadding, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            unit.symbol,
            Modifier
                .width(56.dp)
                .background(plate)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            style = symbolMono,
            color = symbol,
            maxLines = 1,
        )
        Text(unit.label, Modifier.weight(1f), style = CanonText.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.foreground, maxLines = 2)
        Text(value, style = valueMono, color = if (role == UnitRole.To) c.primary else c.foreground, maxLines = 1, softWrap = false)
    }
}

private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

/**
 * The quantity on an instrument graduated twice, in the from unit (ink) and in the result unit (primary), with the
 * converted figure beside it. Each category has its own instrument; all of them ease to a new value.
 */
@Composable
private fun UnitPicture(instrument: UnitInstrument, value: Double?, from: UnitScale, to: UnitScale) {
    val c = LocalDelphiColours.current
    val top = LocalCanonCanvas.current.top.value
    val density = LocalDensity.current.density
    val resolver = LocalFontFamilyResolver.current
    // measures in dp
    val measurer = remember(resolver) { TextMeasurer(resolver, Density(1f), LayoutDirection.Ltr) }
    val result = value?.let { to.value(from.base(it)) }
    val target = UnitConverterEngine.pose(instrument, value ?: 0.0, from, to)

    var start by remember { mutableStateOf(target) }
    var goal by remember { mutableStateOf(target) }
    var shownInstrument by remember { mutableStateOf(instrument) }
    val progress = remember { Animatable(1f) }
    fun pose(t: Float): UnitPose {
        val eased = PxToRemEngine.settle(t).toDouble()
        return UnitPose(
            lerp(start.value, goal.value, eased),
            lerp(start.lo, goal.lo, eased),
            // zoom eases logarithmically
            exp(ln(start.span) + (ln(goal.span) - ln(start.span)) * min(1.0, eased)),
        )
    }
    LaunchedEffect(target, instrument) {
        if (target == goal && instrument == shownInstrument) return@LaunchedEffect
        // ponytail: a new instrument snaps; iOS cross-fades it
        if (instrument != shownInstrument) {
            shownInstrument = instrument
            start = target
            goal = target
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        start = pose(progress.value)
        goal = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(SETTLE_MILLIS, easing = LinearEasing))
    }

    var readoutWidth by remember { mutableFloatStateOf(0f) }
    var readoutHeight by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics {
                contentDescription = "${value?.let(UnitConverterEngine::format) ?: "—"} ${from.symbol}"
                stateDescription = "${result?.let(UnitConverterEngine::format) ?: "—"} ${to.symbol}"
            }
    ) {
        val width = maxWidth.value
        val padding = CanonMetrics.panelPadding.value
        val areaTop = top + 12
        val area = Rect(padding, areaTop, padding + max(0f, width - padding * 2), areaTop + max(0f, maxHeight.value - areaTop - padding))
        Canvas(Modifier.fillMaxSize()) {
            val readout = Rect(width - padding - readoutWidth, areaTop - 6, width - padding, areaTop - 6 + readoutHeight)
            scale(density, Offset.Zero) {
                InstrumentDrawing(this, measurer, c, instrument, from, to, pose(progress.value), value != null).draw(area, readout)
            }
        }
        // balance sits under readout
        val lead = when (instrument) {
            UnitInstrument.Tape, UnitInstrument.Thermometer, UnitInstrument.Balance -> padding
            else -> padding + instrumentWidth(instrument, area) + 12
        }
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(start = lead.dp, top = (top + 6).dp, end = CanonMetrics.panelPadding)
                .onSizeChanged {
                    readoutWidth = it.width / density
                    readoutHeight = it.height / density
                },
            horizontalAlignment = Alignment.End,
        ) {
            CanonFigure(
                value?.let(UnitConverterEngine::format) ?: "—",
                unit = if (value == null) "" else " " + from.symbol,
                size = 18.sp,
                colour = c.mutedForeground,
                halo = c.card,
            )
            CanonFigure(result?.let(UnitConverterEngine::format) ?: "—", unit = if (result == null) "" else " " + to.symbol, size = 38.sp, halo = c.card)
        }
    }
}

private const val STEM_HEIGHT = 14f
private const val BALANCE_WIDTH = 560f

private fun gaugeRadius(manometer: Boolean, height: Float, width: Float) =
    max(20f, min((height - if (manometer) STEM_HEIGHT else 0f) / 2, width / 2))

/** Width the square instruments take on the leading side; the readout keeps to the rest. */
private fun instrumentWidth(instrument: UnitInstrument, area: Rect): Float {
    val most = area.width * 0.55f
    return when (instrument) {
        UnitInstrument.Speedometer, UnitInstrument.Manometer -> gaugeRadius(instrument == UnitInstrument.Manometer, area.height, most) * 2
        UnitInstrument.Jug -> min(area.height * 0.8f + 16, most)
        UnitInstrument.Squares -> min(area.height, most)
        else -> area.width
    }
}

private val tickStyle = TextStyle(fontFamily = DelphiMono, fontSize = 9.sp, fontWeight = FontWeight.Medium)
private val plateStyle = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)

/** One frame of the picture, in dp. */
private class InstrumentDrawing(
    val scope: DrawScope,
    val measurer: TextMeasurer,
    val c: DelphiColours,
    val instrument: UnitInstrument,
    val from: UnitScale,
    val to: UnitScale,
    val shown: UnitPose,
    val hasValue: Boolean,
) {
    private val hairline = CanonMetrics.hairline.value
    private val fromInk get() = c.foreground
    private val toInk get() = c.primary
    private val metal get() = listOf(c.mutedForeground.copy(alpha = 0.55f), c.mutedForeground.copy(alpha = 0.2f))

    fun draw(area: Rect, readout: Rect) {
        if (area.width <= 40 || area.height <= 40) return
        when (instrument) {
            UnitInstrument.Balance -> {
                // from left, result right
                val region = Rect(area.left, area.top, area.left + min(area.width, BALANCE_WIDTH), area.bottom)
                val underReadout = readout.left < region.right && readout.right > region.left
                drawBalance(region, if (underReadout) readout.bottom + 6 else area.top)
            }
            UnitInstrument.Tape, UnitInstrument.Thermometer -> {
                val top = readout.bottom + 6
                val strip = Rect(area.left, top, area.right, max(top, area.bottom))
                if (instrument == UnitInstrument.Tape) drawTape(strip) else drawThermometer(strip)
            }
            else -> {
                val box = Rect(area.left, area.top, area.left + instrumentWidth(instrument, area), area.bottom)
                when (instrument) {
                    UnitInstrument.Jug -> drawJug(box)
                    UnitInstrument.Squares -> drawSquares(box)
                    else -> drawGauge(box, instrument == UnitInstrument.Manometer)
                }
            }
        }
    }

    private fun text(string: String, style: TextStyle = tickStyle) = measurer.measure(string, style)

    private fun width(string: String, style: TextStyle = tickStyle) = text(string, style).size.width.toFloat()

    /** [anchor] is the share of the text's size the point sits at; [halo] outlines it first, for a label over a fill line. */
    private fun label(string: String, at: Offset, colour: Color, anchor: Offset = Offset.Zero, style: TextStyle = tickStyle, halo: Color? = null) {
        val layout: TextLayoutResult = text(string, style)
        val origin = Offset(at.x - layout.size.width * anchor.x, at.y - layout.size.height * anchor.y)
        if (halo != null) scope.drawText(layout, halo, origin, drawStyle = Stroke(style.fontSize.value * 0.6f, join = StrokeJoin.Round))
        scope.drawText(layout, colour, origin)
    }

    private fun plateWidth() = max(36f, ceil(max(width(from.symbol, plateStyle), width(to.symbol, plateStyle))) + 12)

    /** The unit's name plate at the start of its scale: ink for the from unit, primary for the result, as in the units list. */
    private fun plate(scale: UnitScale, isResult: Boolean, rect: Rect) {
        scope.drawRect(if (isResult) c.primary else c.foreground, rect.topLeft, rect.size)
        label(scale.symbol, rect.center, if (isResult) c.primaryForeground else c.background, Offset(0.5f, 0.5f), plateStyle)
    }

    private fun ticks(scale: UnitScale, length: Float, spacing: Float, style: TextStyle = tickStyle) =
        UnitConverterEngine.ticks(scale, shown.lo, shown.span, length, spacing) { width(it, style) }

    /** Graduates a rule from [x0] to [x1] at [edge]; [down] hangs the ticks below the edge, else they rise above it. */
    private fun rule(scale: UnitScale, x0: Float, x1: Float, edge: Float, down: Boolean, ink: Color, labelsOutside: Boolean) {
        for (tick in ticks(scale, x1 - x0, 40f)) {
            val x = x0 + tick.offset
            val length = if (tick.isMajor) 10f else 5f
            scope.drawRect(ink, Offset(x - hairline / 2, if (down) edge else edge - length), Size(hairline, length))
            val name = tick.label ?: continue
            val size = text(name).size
            // no clipped numbers
            if (x + 3 + size.width > x1 + 2) continue
            // inside clears minor ticks
            val y = if (labelsOutside) {
                if (down) edge + length + 1 else edge - length - 1 - size.height
            } else {
                if (down) edge + 5 else edge - 5 - size.height
            }
            label(name, Offset(if (labelsOutside) x - size.width / 2f else x + 3, y), ink)
        }
    }

    private fun drawTape(strip: Rect) {
        val plate = plateWidth()
        val height = min(52f, strip.height)
        val tape = Rect(strip.left + plate + 8, strip.center.y - height / 2, strip.right, strip.center.y + height / 2)
        if (tape.width <= 20) return
        plate(from, false, Rect(strip.left, tape.top, strip.left + plate, tape.top + height / 2 - 1))
        plate(to, true, Rect(strip.left, tape.center.y + 1, strip.left + plate, tape.center.y + height / 2))
        scope.drawRect(c.card, tape.topLeft, tape.size)
        val end = tape.left + (min(1.0, max(0.0, (shown.value - shown.lo) / shown.span)) * tape.width).toFloat()
        scope.drawRect(c.primary.copy(alpha = 0.14f), tape.topLeft, Size(end - tape.left, tape.height))
        scope.drawRect(c.mutedForeground.copy(alpha = 0.6f), tape.deflate(0.5f).topLeft, tape.deflate(0.5f).size, style = Stroke(hairline))
        rule(from, tape.left, tape.right, tape.top, down = true, ink = fromInk, labelsOutside = false)
        rule(to, tape.left, tape.right, tape.bottom, down = false, ink = toInk, labelsOutside = false)
        if (hasValue) {
            val frame = CanonMetrics.frame.value
            scope.drawRect(c.primary, Offset(end - frame / 2, tape.top - 6), Size(frame, tape.height + 12))
        }
    }

    private fun drawThermometer(strip: Rect) {
        val plate = plateWidth()
        val tubeHeight = 12f
        val bulb = 13f
        val centreY = strip.center.y
        val bulbCentre = Offset(strip.left + plate + 10 + bulb, centreY)
        val x0 = bulbCentre.x + bulb + 8
        val x1 = strip.right - 10
        if (x1 - x0 <= 40) return
        plate(from, false, Rect(strip.left, centreY - 26, strip.left + plate, centreY - 6))
        plate(to, true, Rect(strip.left, centreY + 6, strip.left + plate, centreY + 26))

        val tube = Path().apply {
            addRoundRect(RoundRect(Rect(bulbCentre.x, centreY - tubeHeight / 2, strip.right, centreY + tubeHeight / 2), CornerRadius(tubeHeight / 2)))
            addOval(Rect(bulbCentre, bulb))
        }
        fun xFor(base: Double) = x0 + ((base - shown.lo) / shown.span).toFloat() * (x1 - x0)
        scope.clipPath(tube) {
            drawRect(c.card, strip.topLeft, strip.size)
            // below absolute zero: muted
            if (shown.lo < 0) {
                val edge = min(xFor(0.0), strip.right)
                drawRect(c.muted, Offset(bulbCentre.x, centreY - tubeHeight), Size(edge - bulbCentre.x, tubeHeight * 2))
            }
            if (hasValue) {
                val level = min(x1 + 6, max(x0 - 6, xFor(shown.value)))
                drawRect(c.primary, Offset(bulbCentre.x - bulb, centreY - bulb), Size(level - bulbCentre.x + bulb, bulb * 2))
            }
        }
        scope.drawPath(tube, c.mutedForeground.copy(alpha = 0.6f), style = Stroke(1f))

        // water freezes and boils
        val dash = PathEffect.dashPathEffect(floatArrayOf(2f, 2f))
        for (x in listOf(273.15, 373.15).map(::xFor).filter { it in x0..x1 }) {
            scope.drawLine(c.mutedForeground.copy(alpha = 0.45f), Offset(x, centreY - 30), Offset(x, centreY + 30), 1f, pathEffect = dash)
        }
        rule(from, x0, x1, centreY - tubeHeight / 2 - 3, down = false, ink = fromInk, labelsOutside = true)
        rule(to, x0, x1, centreY + tubeHeight / 2 + 3, down = true, ink = toInk, labelsOutside = true)
    }

    private fun drawGauge(box: Rect, manometer: Boolean) {
        val radius = gaugeRadius(manometer, box.height, box.width)
        val centre = Offset(box.center.x, box.top + radius)
        val bezel = max(5f, radius * 0.08f)
        val face = radius - bezel
        val startAngle = if (manometer) PI.toFloat() * 3 / 4 else PI.toFloat() * 5 / 6
        val sweep = if (manometer) PI.toFloat() * 3 / 2 else PI.toFloat() * 4 / 3
        fun point(angle: Float, distance: Float) = Offset(centre.x + cos(angle) * distance, centre.y + sin(angle) * distance)
        fun degrees(radians: Float) = radians * 180 / PI.toFloat()

        if (manometer) {
            val neck = Rect(centre.x - 5, centre.y + radius - 2, centre.x + 5, centre.y + radius + 4)
            val nut = Rect(centre.x - 13, neck.bottom, centre.x + 13, neck.bottom + STEM_HEIGHT - 5)
            val brush = Brush.horizontalGradient(metal, nut.left, nut.right)
            scope.drawRect(brush, neck.topLeft, neck.size)
            scope.drawRect(brush, nut.topLeft, nut.size)
            for (x in listOf(nut.left + 8, nut.right - 8)) {
                scope.drawRect(c.mutedForeground.copy(alpha = 0.5f), Offset(x, nut.top), Size(hairline, nut.height))
            }
        }
        scope.drawCircle(Brush.verticalGradient(metal, centre.y - radius, centre.y + radius), radius, centre)
        scope.drawCircle(c.card, face, centre)
        scope.drawCircle(c.mutedForeground.copy(alpha = 0.5f), face, centre, style = Stroke(hairline))

        val outer = face - 3
        val inner = face * 0.56f
        val fraction = if (hasValue) min(1.02, max(0.0, (shown.value - shown.lo) / shown.span)).toFloat() else 0f
        val needle = startAngle + sweep * fraction
        if (hasValue && fraction > 0) {
            val r = inner + 2
            scope.drawArc(
                c.primary.copy(alpha = 0.25f), degrees(startAngle), degrees(min(needle, startAngle + sweep) - startAngle), false,
                Offset(centre.x - r, centre.y - r), Size(r * 2, r * 2), style = Stroke(4f),
            )
        }
        scope.drawArc(
            c.primary, degrees(startAngle), degrees(sweep), false,
            Offset(centre.x - inner, centre.y - inner), Size(inner * 2, inner * 2), style = Stroke(hairline),
        )

        val style = tickStyle.copy(fontSize = (if (face > 70) 9 else 8).sp)
        for ((scale, ink, rim) in listOf(Triple(from, fromInk, outer), Triple(to, toInk, inner))) {
            val isOuter = rim == outer
            val labelRadius = if (isOuter) rim - 14 else rim - 11
            val length = labelRadius * sweep
            for (tick in ticks(scale, length, if (isOuter) 26f else 20f, style)) {
                val angle = startAngle + sweep * tick.offset / length
                val long = if (tick.isMajor) (if (isOuter) 7f else 5f) else (if (isOuter) 3.5f else 2.5f)
                val weight = if (tick.isMajor) (if (!manometer && isOuter) 2f else 1.2f) else hairline
                scope.drawLine(ink, point(angle, rim), point(angle, rim - long), weight)
                val name = tick.label ?: continue
                val size = text(name, style).size
                // inset by label extent
                val reach = abs(cos(angle)) * size.width / 2 + abs(sin(angle)) * size.height / 2
                label(name, point(angle, rim - long - 3 - reach), ink, Offset(0.5f, 0.5f), style)
            }
        }

        val symbolStyle = plateStyle.copy(fontSize = (if (face > 70) 11 else 10).sp)
        label(from.symbol, Offset(centre.x, centre.y + face * 0.42f), fromInk, Offset(0.5f, 0f), symbolStyle)
        label(to.symbol, Offset(centre.x, centre.y + face * 0.42f + 13), toInk, Offset(0.5f, 0f), symbolStyle)

        if (hasValue) {
            val tip = point(needle, outer - 4)
            val tail = point(needle + PI.toFloat(), face * 0.18f)
            val side = Offset(cos(needle + PI.toFloat() / 2), sin(needle + PI.toFloat() / 2))
            val path = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(centre.x + side.x * 2.6f, centre.y + side.y * 2.6f)
                lineTo(tail.x + side.x * 3.4f, tail.y + side.y * 3.4f)
                lineTo(tail.x - side.x * 3.4f, tail.y - side.y * 3.4f)
                lineTo(centre.x - side.x * 2.6f, centre.y - side.y * 2.6f)
                close()
            }
            // ponytail: shadow unblurred; iOS blurs it 2.5 pt
            scope.translate(1f, 2f) { drawPath(path, c.foreground.copy(alpha = 0.15f)) }
            scope.drawPath(path, c.primary)
        }
        scope.drawCircle(Brush.verticalGradient(metal, centre.y - 6, centre.y + 6), 6f, centre)
        scope.drawCircle(c.mutedForeground, 6f, centre, style = Stroke(hairline))
    }

    private class Side(val scale: UnitScale, val ink: Color, val weights: UnitWeights?, val block: Double)

    private class Shape(val width: Float, val height: Float, val blocks: Int)

    /** Block frames per stack, bottom layer first, centred on [midX] and standing on [floor]; an upper layer is centred on the one below. */
    private fun stack(shape: Shape, perLayer: Int, unit: Float, midX: Float, floor: Float): List<Rect> {
        val stackWidth = shape.width * unit * min(perLayer, shape.blocks)
        val x = midX - stackWidth / 2
        val width = shape.width * unit
        val height = shape.height * unit
        return (0 until shape.blocks).map { index ->
            val layer = index / perLayer
            val place = index % perLayer
            val inLayer = min(perLayer, shape.blocks - layer * perLayer)
            val left = x + (stackWidth - width * inLayer) / 2 + width * place
            val topY = floor - height * (layer + 1) + 1
            Rect(left + 1, topY, left + width - 1, topY + height - 1)
        }
    }

    /** [top] is where the cords fade in and the weights stop, clear of the readout. */
    private fun drawBalance(region: Rect, top: Float) {
        val wire = c.mutedForeground.copy(alpha = 0.7f)
        val panWidth = region.width * 0.47f
        val reach = region.width * 0.25f
        val centres = listOf(region.center.x - reach, region.center.x + reach)
        val rim = region.bottom - 12
        // cords meet far above
        val hookY = region.top - region.height * 2.5f
        if (rim - top <= 40 || panWidth <= 40) return

        val quantity = if (hasValue) max(0.0, shown.value) else 0.0
        val sides = listOf(from to fromInk, to to toInk).map { (scale, ink) ->
            val weights = UnitConverterEngine.weights(scale.value(quantity))
            // block mass, base units
            Side(scale, ink, weights, weights?.let { scale.base(it.denomination) } ?: 0.0)
        }
        // 1.6:1 ingots, side ∛mass
        val heaviest = max(sides.maxOf { it.block }, Double.MIN_VALUE)
        val shapes = sides.map { side ->
            val size = cbrt(side.block / heaviest).toFloat()
            Shape(1.6f * size, size, side.weights?.let { it.full + if (it.part > 0) 1 else 0 } ?: 0)
        }
        // within cords, label room
        fun fits(frames: List<List<Rect>>) = frames.zip(centres).all { (stack, apex) ->
            stack.all { frame ->
                val half = (panWidth / 2 - 4) * (frame.top - hookY) / (rim - hookY)
                frame.top >= top + 16 && frame.left >= apex - half + 3 && frame.right <= apex + half - 3
            }
        }
        fun layout(perLayer: Int, unit: Float) = shapes.zip(centres).map { (shape, apex) -> stack(shape, perLayer, unit, apex, rim) }
        var best = layout(1, 0f)
        var bestScore = 0f
        for (perLayer in 1..4) {
            var low = 2f
            var high = 80f
            repeat(12) {
                val unit = (low + high) / 2
                if (fits(layout(perLayer, unit))) low = unit else high = unit
            }
            // tall towers score lower
            val layers = shapes.maxOf { (it.blocks + perLayer - 1) / perLayer }
            val score = low * if (layers > 4) 0.75f else 1f
            if (score > bestScore) {
                best = layout(perLayer, low)
                bestScore = score
            }
        }

        val fade = Brush.verticalGradient(listOf(wire.copy(alpha = 0f), wire), top, top + 40)
        for (apex in centres) {
            for (x in listOf(apex - panWidth / 2 + 4, apex + panWidth / 2 - 4)) {
                scope.drawLine(fade, Offset(apex, hookY), Offset(x, rim), 1f)
            }
        }

        val style = tickStyle.copy(fontWeight = FontWeight.SemiBold)
        for ((side, frames) in sides.zip(best)) {
            val weights = side.weights ?: continue
            val name = UnitConverterEngine.tickLabel(weights.denomination) + " " + side.scale.symbol
            val labelFits = frames.firstOrNull()?.let { width(name, style) + 6 <= it.width * 0.95f && it.height >= 11 } ?: false
            frames.forEachIndexed { index, rect ->
                val isPart = index == weights.full
                val lip = min(4f, rect.width * 0.1f)
                val ingot = Path().apply {
                    moveTo(rect.left, rect.bottom)
                    lineTo(rect.left + lip, rect.top)
                    lineTo(rect.right - lip, rect.top)
                    lineTo(rect.right, rect.bottom)
                    close()
                }
                scope.drawPath(ingot, c.card)
                scope.clipPath(ingot) {
                    val fill = if (isPart) Size(rect.width * weights.part.toFloat(), rect.height) else rect.size
                    drawRect(side.ink.copy(alpha = 0.22f), rect.topLeft, fill)
                }
                scope.drawPath(ingot, side.ink, style = Stroke(1.2f, pathEffect = if (isPart) PathEffect.dashPathEffect(floatArrayOf(3f, 2f)) else null))
                val shownText = if (isPart) UnitConverterEngine.tickLabel(Math.round(weights.part * 100) / 100.0) else name
                val shows = if (isPart) width(shownText, style) + 6 <= rect.width && rect.height >= 11 else labelFits
                if (shows) label(shownText, rect.center, side.ink, Offset(0.5f, 0.5f), style)
            }
            if (!labelFits && frames.isNotEmpty()) {
                val stack = frames.reduce { a, b -> Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom)) }
                label(name, Offset(stack.center.x, stack.top - 3), side.ink, Offset(0.5f, 1f), style, halo = c.card)
            }
        }

        for (apex in centres) {
            val pan = Rect(apex - panWidth / 2, rim, apex + panWidth / 2, rim + 10)
            val dish = Path().apply {
                moveTo(pan.left, rim)
                lineTo(pan.right, rim)
                cubicTo(pan.right - pan.width * 0.15f, pan.bottom + 1, pan.left + pan.width * 0.15f, pan.bottom + 1, pan.left, rim)
                close()
            }
            scope.drawPath(dish, Brush.verticalGradient(metal, rim, pan.bottom))
            scope.drawLine(c.mutedForeground.copy(alpha = 0.8f), Offset(pan.left - 1, rim), Offset(pan.right + 1, rim), 1.5f)
        }
    }

    private fun drawJug(box: Rect) {
        val body = Rect(box.left + 10, box.top + 8, box.right - 4, box.bottom)
        if (body.width <= 40 || body.height <= 40) return
        val corner = 12f
        fun outline() = Path().apply {
            moveTo(body.left - 9, body.top - 6)
            lineTo(body.left, body.top + 8)
            lineTo(body.left, body.bottom - corner)
            quadraticTo(body.left, body.bottom, body.left + corner, body.bottom)
            lineTo(body.right - corner, body.bottom)
            quadraticTo(body.right, body.bottom, body.right, body.bottom - corner)
            lineTo(body.right, body.top)
        }
        val inside = outline().apply {
            lineTo(body.left - 9, body.top - 6)
            close()
        }
        val zero = body.bottom - 4
        val full = body.top + 22
        val fraction = if (hasValue) min(1.04, max(0.0, (shown.value - shown.lo) / shown.span)).toFloat() else 0f
        val level = zero - fraction * (zero - full)
        scope.clipPath(inside) {
            val wide = body.inflate(10f)
            drawRect(c.card, wide.topLeft, wide.size)
            if (hasValue) {
                drawRect(c.primary.copy(alpha = 0.16f), Offset(body.left - 10, level), Size(body.width + 20, body.bottom - level + 2))
                drawRect(c.primary, Offset(body.left, level - 1), Size(body.width, 2f))
            }
        }
        scope.drawPath(outline(), c.mutedForeground.copy(alpha = 0.7f), style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val symbolStyle = plateStyle.copy(fontSize = 10.sp)
        for ((scale, ink, left) in listOf(Triple(from, fromInk, true), Triple(to, toInk, false))) {
            val wallX = if (left) body.left + 2 else body.right - 2
            for (tick in ticks(scale, zero - full, 22f)) {
                val y = zero - tick.offset
                val long = if (tick.isMajor) 9f else 5f
                scope.drawRect(ink, Offset(if (left) wallX else wallX - long, y - 0.5f), Size(long, 1f))
                val name = tick.label ?: continue
                if (tick.offset <= 0) continue
                label(name, Offset(if (left) wallX + long + 3 else wallX - long - 3, y), ink, Offset(if (left) 0f else 1f, 0.5f), halo = c.card)
            }
            label(scale.symbol, Offset(if (left) wallX + 3 else wallX - 3, body.top + 4), if (left) c.mutedForeground else ink, Offset(if (left) 0f else 1f, 0f), symbolStyle)
        }
    }

    private fun drawSquares(box: Rect) {
        val quantity = if (hasValue) max(0.0, shown.value) else 0.0
        val unit = shown.span
        val largest = max(quantity, unit)
        if (largest <= 0 || !largest.isFinite()) return
        val side = min(box.width, box.height) - 2
        val perRoot = side / sqrt(largest).toFloat()
        val quantitySide = sqrt(quantity).toFloat() * perRoot
        val unitSide = max(2f, sqrt(unit).toFloat() * perRoot)
        fun square(length: Float) = Rect(box.left + 1, box.bottom - 1 - length, box.left + 1 + length, box.bottom - 1)
        val q = square(quantitySide)
        val u = square(unitSide)
        if (quantitySide > 0.5f) {
            scope.drawRect(c.foreground.copy(alpha = 0.06f), q.topLeft, q.size)
            // ruled in result units
            val cells = quantitySide / unitSide
            if (unitSide >= 5 && cells > 1 && cells < 200) {
                val ruling = c.primary.copy(alpha = 0.3f)
                var offset = unitSide
                while (offset < quantitySide - 0.5f) {
                    scope.drawRect(ruling, Offset(q.left + offset, q.top), Size(hairline, q.height))
                    scope.drawRect(ruling, Offset(q.left, q.bottom - offset), Size(q.width, hairline))
                    offset += unitSide
                }
            }
            scope.drawRect(c.foreground, q.topLeft, q.size, style = Stroke(1.5f))
        }
        scope.drawRect(c.primary.copy(alpha = 0.14f), u.topLeft, u.size)
        scope.drawRect(c.primary, u.topLeft, u.size, style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))))
        val style = plateStyle
        val name = "1 " + to.symbol
        val size = text(name, style).size
        if (unitSide >= size.width + 10 && unitSide >= size.height + 8) {
            label(name, Offset(u.left + 5, u.top + 4), toInk, style = style)
        } else {
            label(name, Offset(u.right + 5, u.top - 2), toInk, Offset(0f, 1f), style)
        }
    }
}
