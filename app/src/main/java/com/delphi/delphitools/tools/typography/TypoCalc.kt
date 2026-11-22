package com.delphi.delphitools.tools.typography

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonBigNumberField
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDecimalField
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.ln

private val units = TypoUnit.entries
private const val GAUGE_ROW = 22f
private const val GAUGE_LABEL = 36f
private val gaugeHeight = (GAUGE_ROW * TypoCalcEngine.gaugeScales.size + 16 + 12).dp
private val symbolMono = TextStyle(fontFamily = DelphiMono, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
private val valueMono = TextStyle(fontFamily = DelphiMono, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
private val tickMono = TextStyle(fontFamily = DelphiMono, fontSize = 9.sp)
private val plateMono = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
private val readingMono = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

private fun format(value: Double) = PxToRemEngine.format(value)

class TypoCalcModel : ViewModel() {
    var input by mutableStateOf("16")
        private set

    /** The input as the rows and the gauge last took it. */
    var shown by mutableStateOf("16")
        private set
    var unit by mutableStateOf(TypoUnit.Px)
        private set
    var base by mutableDoubleStateOf(16.0)
        private set
    var ppi by mutableDoubleStateOf(96.0)
        private set
    private var typing: Job? = null

    val pxValue get() = unit.toPx(PxToRemEngine.parse(shown) ?: 0.0, base, ppi)

    fun convert(to: TypoUnit) = to.fromPx(pxValue, base, ppi)

    fun type(text: String) {
        input = text.replace(',', '.')
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

    fun nudge(direction: Int) = set(TypoCalcEngine.nudge(PxToRemEngine.parse(input) ?: 0.0, unit, direction))

    fun changeBase(value: Double) {
        base = value.coerceIn(TypoCalcEngine.baseRange)
        refresh()
    }

    /** Converted values follow a new resolution; the typed value stays. */
    fun usePpi(value: Double) {
        ppi = value.coerceIn(TypoCalcEngine.ppiRange)
        refresh()
    }

    fun convertTo(next: TypoUnit) {
        if (next == unit) return
        refresh()
        val value = convert(next)
        unit = next
        set(value)
    }

    private fun set(value: Double) {
        input = format(value)
        refresh()
    }
}

@Composable
fun TypoCalc(model: TypoCalcModel = viewModel()) {
    val c = LocalDelphiColours.current
    val ppiText = format(model.ppi)

    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Convert From")
            CanonBigNumberField(
                model.input,
                model::type,
                onStep = model::nudge,
                placeholder = "0",
                unit = model.unit.symbol.uppercase(),
                label = "Value",
                onEndEditing = model::refresh,
            )
            CanonSegmented(units.map { it.symbol }, units.indexOf(model.unit), columns = 5) { model.convertTo(units[it]) }
            CanonRow("Base Font Size", detail = "Used for em and rem", stacked = true) {
                CanonDecimalField(
                    model.base,
                    model::changeBase,
                    unit = "px",
                    step = 1.0,
                    decimals = 2,
                    range = TypoCalcEngine.baseRange,
                    label = "Base Font Size",
                )
            }
            CanonRow("Resolution", stacked = true) {
                CanonDecimalField(
                    model.ppi,
                    model::usePpi,
                    unit = "ppi",
                    step = 1.0,
                    decimals = 0,
                    range = TypoCalcEngine.ppiRange,
                    label = "Resolution",
                )
            }
            CanonGrid(columns = 4) {
                TypoCalcEngine.ppiPresets.forEach { value ->
                    CanonButton(format(value), kind = CanonButtonKind.Segment, selected = value == model.ppi) { model.usePpi(value) }
                }
            }
        }
    }

    val table = CanonSection.of {
        CanonRows {
            CanonHeading("Converted Values")
            units.forEach { unit ->
                val detail = if (unit == TypoUnit.Px && model.ppi != 96.0) "$ppiText ppi" else unit.description
                TypoUnitRow(unit, format(model.convert(unit)), detail, isInput = unit == model.unit) { model.convertTo(unit) }
            }
        }
    }

    val basePx = format(model.base)
    val quick = CanonSection.of {
        CanonRows {
            CanonHeading("Quick Reference")
            CanonTiles(
                listOf(
                    CanonTile("1 in", "$ppiText px · 72 pt · 25.4 mm"),
                    CanonTile("1 pc", "12 pt · 1/6 in"),
                    CanonTile("1 pt", "1/72 in"),
                    CanonTile("1 ag", "1/14 in · 5.14 pt"),
                    CanonTile("1 cc", "12 Didot pt · 4.512 mm"),
                    CanonTile("1 em · rem", "$basePx px"),
                ),
            )
        }
    }

    val gauge = CanonSection.canvas(CanonCanvasSize.Fixed(gaugeHeight), background = c.card) {
        TypoGauge(maxOf(0.0, model.pxValue), model.base, model.ppi, model.unit)
    }

    CanonSplit(sidebar = listOf(controls), main = listOf(table, quick), banner = gauge)
}

// ponytail: the value keeps its size; iOS shrinks it to 60 % in a narrow row, add a fitting text when values clip
@Composable
private fun TypoUnitRow(unit: TypoUnit, value: String, detail: String, isInput: Boolean, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    Row(
        Modifier
            .fillMaxWidth()
            .canonClickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = unit.title
                stateDescription = value + " " + unit.symbol
                selected = isInput
            }
            .background(if (isInput) c.accentBg else c.card)
            .heightIn(min = CanonMetrics.rowHeight)
            .padding(start = 8.dp, top = 8.dp, end = CanonMetrics.panelPadding, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(unit.symbol, Modifier.width(40.dp), style = symbolMono, color = if (isInput) c.primary else c.mutedForeground)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(unit.title, style = CanonText.footnoteSemibold, color = c.foreground)
            Text(detail, style = CanonText.caption, color = c.mutedForeground)
        }
        Text(value, style = valueMono, color = if (isInput) c.primary else c.foreground, maxLines = 1, softWrap = false)
    }
}

/**
 * The measure as a length on a typesetter's line gauge: one scale per unit on a shared zero, the length shaded,
 * a cursor across every scale at its end and each scale's reading beside the cursor. The gauge zooms in 1-2-5 steps.
 */
@Composable
private fun TypoGauge(px: Double, base: Double, ppi: Double, input: TypoUnit) {
    val c = LocalDelphiColours.current
    val topInset = LocalCanonCanvas.current.top
    val measurer = rememberTextMeasurer()
    val scales = TypoCalcEngine.gaugeScales
    val shownInput = TypoCalcEngine.gaugeUnit(input)
    fun reading(value: Double, unit: TypoUnit) = format(unit.fromPx(value, base, ppi)) + " " + unit.symbol

    val target = px to TypoCalcEngine.span(px)
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    fun pose(t: Float): Pair<Double, Double> {
        val eased = PxToRemEngine.settle(t).toDouble()
        val shownPx = maxOf(0.0, from.first + (to.first - from.first) * eased)
        // zoom eases in log space
        val span = exp(ln(from.second) + (ln(to.second) - ln(from.second)) * minOf(1.0, eased))
        return shownPx to span
    }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        from = pose(progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(600, easing = LinearEasing))
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics {
                contentDescription = "Converted Values"
                stateDescription = scales.joinToString(", ") { reading(px, it) }
            },
    ) {
        val (shownPx, span) = pose(progress.value)
        val padding = CanonMetrics.panelPadding.toPx()
        val rowHeight = GAUGE_ROW.dp.toPx()
        val top = topInset.toPx() + 12.dp.toPx()
        val zero = padding + GAUGE_LABEL.dp.toPx() + 8.dp.toPx()
        val right = size.width - padding
        val width = right - zero
        if (width <= 0f || span <= 0) return@Canvas
        val scale = width / span
        val cursor = (zero + shownPx * scale).toFloat()
        val rowsHeight = rowHeight * scales.size
        val hairline = CanonMetrics.hairline.toPx()

        drawRect(c.primary.copy(alpha = 0.1f), Offset(zero, top), Size(maxOf(0f, cursor - zero), rowsHeight))

        val readings = scales.map { measurer.measure(reading(shownPx, it), readingMono.copy(color = c.primary)) }
        // readings share one side
        val widest = readings.maxOf { it.size.width }
        val flips = cursor + 4.dp.toPx() + widest + 6.dp.toPx() > right

        scales.forEachIndexed { index, unit ->
            val y = top + index * rowHeight
            val bottom = y + rowHeight
            val isInput = unit == shownInput
            drawRect(c.border, Offset(0f, bottom - hairline), Size(size.width, hairline))

            val plateTop = y + 2.dp.toPx()
            val plateWidth = GAUGE_LABEL.dp.toPx()
            val plateHeight = rowHeight - 4.dp.toPx()
            drawRect(if (isInput) c.primary else c.muted, Offset(padding, plateTop), Size(plateWidth, plateHeight))
            val symbol = measurer.measure(unit.symbol, plateMono.copy(color = if (isInput) c.primaryForeground else c.mutedForeground))
            drawText(symbol, topLeft = Offset(padding + (plateWidth - symbol.size.width) / 2, plateTop + (plateHeight - symbol.size.height) / 2))

            val perUnit = unit.toPx(1.0, base, ppi) * scale
            val major = TypoCalcEngine.niceStep(44.dp.toPx() / perUnit)
            val minor = major / TypoCalcEngine.subdivisions(major)
            // overflow skips ticks
            val ticks = (major * perUnit).isFinite() && major * perUnit > 1
            val ink = if (isInput) c.foreground else c.mutedForeground
            val slack = 0.5.dp.toPx()
            if (ticks && minor * perUnit >= 4.dp.toPx()) {
                var value = 0.0
                while (zero + value * perUnit <= right + slack) {
                    drawRect(ink, Offset((zero + value * perUnit).toFloat(), bottom - 5.dp.toPx()), Size(hairline, 5.dp.toPx()))
                    value += minor
                }
            }
            var value = 0.0
            while (ticks && zero + value * perUnit <= right + slack) {
                val x = (zero + value * perUnit).toFloat()
                drawRect(ink, Offset(x, bottom - 11.dp.toPx()), Size(hairline, 11.dp.toPx()))
                val number = measurer.measure(format(value), tickMono.copy(color = ink))
                // no clipped numbers
                if (x + 2.dp.toPx() + number.size.width <= size.width) drawText(number, topLeft = Offset(x + 2.dp.toPx(), y + 1.dp.toPx()))
                value += major
            }

            // reading on roomy side
            val text = readings[index]
            val boxWidth = text.size.width + 8.dp.toPx()
            val boxHeight = rowHeight - 5.dp.toPx()
            val boxX = if (flips) cursor - text.size.width - 10.dp.toPx() else cursor + 2.dp.toPx()
            val boxY = y + 2.dp.toPx()
            drawRect(c.card, Offset(boxX, boxY), Size(boxWidth, boxHeight))
            drawText(text, topLeft = Offset(boxX + 4.dp.toPx(), boxY + (boxHeight - text.size.height) / 2))
        }

        val frame = CanonMetrics.frame.toPx()
        drawRect(c.primary, Offset(cursor - frame / 2, top - 4.dp.toPx()), Size(frame, rowsHeight + 4.dp.toPx()))
    }
}
