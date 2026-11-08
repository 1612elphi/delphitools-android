package com.delphi.delphitools.tools.typography

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonBigNumberField
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDecimalField
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val pictureHeight = 220.dp
private val captionMono = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

// LineHeightPicture: room for the bracket, and for the bracket with the figure beside it
private const val BRACKET_COLUMN = 20f
private const val FIGURE_COLUMN = 132f

// widest text column, least comparison column, gap between columns, caption above a comparison
private const val MEASURE = 560f
private const val COMPARISON_WIDTH = 150f
private const val COMPARISON_GAP = 32f
private const val CAPTION_HEIGHT = 22f

private val pangram = "The quick brown fox jumps over the lazy dog. Pack my box with five dozen liquor jugs. How vexingly quick daft zebras jump."
    .split(" ")

private fun format(value: Double) = PxToRemEngine.format(value)

class LineHeightCalcModel : ViewModel() {
    var fontSize by mutableDoubleStateOf(16.0)
        private set
    var sizeText by mutableStateOf(format(16.0))
        private set
    var ratio by mutableStateOf(LineHeightRatio.Normal)
        private set
    private var customMultiplier = 1.5
    private var typing: Job? = null

    val multiplier get() = if (ratio == LineHeightRatio.Custom) customMultiplier else ratio.multiplier
    val lineHeight get() = fontSize * multiplier
    val leading get() = lineHeight - fontSize

    fun type(text: String) {
        sizeText = text
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            commitTyped()
        }
    }

    fun endEditing() {
        commitTyped()
        sizeText = format(fontSize)
    }

    fun step(direction: Int) = set(Math.round(fontSize + direction).toDouble())

    fun set(size: Double) {
        typing?.cancel()
        fontSize = size.coerceIn(LineHeightEngine.sizeRange)
        sizeText = format(fontSize)
    }

    fun select(chosen: LineHeightRatio) {
        // custom keeps ratio
        if (chosen == LineHeightRatio.Custom) customMultiplier = multiplier
        ratio = chosen
    }

    fun use(multiplier: Double) {
        ratio = LineHeightRatio.matching(multiplier)
        customMultiplier = multiplier
    }

    private fun commitTyped() {
        typing?.cancel()
        val typed = PxToRemEngine.parse(sizeText)?.takeIf { it > 0 } ?: return
        fontSize = typed.coerceIn(LineHeightEngine.sizeRange)
    }
}

@Composable
fun LineHeightCalc(model: LineHeightCalcModel = viewModel()) {
    val c = LocalDelphiColours.current
    val ratios = LineHeightRatio.entries
    val multiplier = model.multiplier
    val lineHeight = format(model.lineHeight)
    val leading = format(model.leading)

    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Font Size")
            CanonBigNumberField(
                model.sizeText,
                model::type,
                onStep = model::step,
                placeholder = "16",
                unit = "PX",
                label = "Font Size",
                onEndEditing = model::endEditing,
            )
            CanonHeading("Line Height")
            CanonSegmented(ratios.map { it.label }, ratios.indexOf(model.ratio), columns = 3) { model.select(ratios[it]) }
            CanonRow("Ratio", flush = true) {
                CanonDecimalField(
                    multiplier,
                    model::use,
                    Modifier.fillMaxSize(),
                    unit = "×",
                    step = 0.05,
                    decimals = 3,
                    range = LineHeightEngine.ratioRange,
                    label = "Ratio",
                )
            }
        }
    }

    val results = CanonSection.of {
        CanonRows {
            CanonHeading("Result")
            CanonValueLine("Line Height", lineHeight + "px", labelWidth = 84.dp)
            CanonValueLine("Leading", leading + "px", labelWidth = 84.dp)
            CanonValueLine("CSS", "line-height: ${format(multiplier)};", labelWidth = 84.dp)
            CanonValueLine("SwiftUI", ".lineSpacing($leading)", labelWidth = 84.dp)
        }
    }

    val table = CanonSection.of {
        CanonRows {
            CanonHeading("Common Sizes")
            ReferenceHeader(listOf("Font", "Line Height", "Leading"), CanonText.footnoteSemibold)
            LineHeightEngine.commonSizes.forEach { size ->
                val height = format(size * multiplier)
                val gap = format(size * multiplier - size)
                ReferenceRow(
                    listOf("${size.toInt()}px" to c.foreground, height to c.primary, gap to c.mutedForeground),
                    "${size.toInt()}px, ${height}px, ${gap}px",
                    isCurrent = size == model.fontSize,
                ) { model.set(size) }
            }
        }
    }

    val others = LineHeightRatio.presets.filter { it.multiplier != multiplier }.map { format(it.multiplier) to it.multiplier }
    val picture = CanonSection.canvas(CanonCanvasSize.Fixed(pictureHeight), background = c.card) {
        LineHeightPicture(model.fontSize.toFloat(), model.lineHeight.toFloat(), lineHeight, others)
    }

    CanonSplit(sidebar = listOf(controls), main = listOf(results, table), banner = picture)
}

/**
 * Text set at the chosen size in line boxes of the chosen height, drawn as CSS lays them out: the glyphs' content area
 * centred in each box with half the leading above and half below. A bracket spans the first box; the figure is its height.
 * [others] are preset ratios drawn in narrower muted columns where the width allows, labelled with their multiplier.
 */
@Composable
private fun LineHeightPicture(fontSize: Float, lineHeight: Float, figure: String, others: List<Pair<String, Double>>) {
    val c = LocalDelphiColours.current
    val topInset = LocalCanonCanvas.current.top
    val measurer = rememberTextMeasurer()
    val shownSize by animateFloatAsState(fontSize, settleTween, label = "size")
    val shownLineHeight by animateFloatAsState(lineHeight, settleTween, label = "lineHeight")

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics {
                contentDescription = "Line Height"
                stateDescription = figure + "px"
            },
    ) {
        val padding = CanonMetrics.panelPadding.value
        val textWidth = minOf(MEASURE, maxWidth.value - padding - FIGURE_COLUMN - padding)
        val bracketX = padding + textWidth + FIGURE_COLUMN - BRACKET_COLUMN / 2

        Canvas(Modifier.fillMaxSize()) {
            val top = topInset.toPx() + padding.dp.toPx()
            val bracket = bracketX.dp.toPx()
            val box0 = maxOf(1f, shownLineHeight).dp.toPx()
            // two boxes always fit
            val scale = minOf(1f, (size.height - top - padding.dp.toPx()) / (2 * box0))
            // whole sizes cache fonts
            val fontPx = maxOf(1f, (maxOf(1f, shownSize).dp.toPx() * scale).roundToInt().toFloat())
            val style = TextStyle(fontFamily = FontFamily.Default, fontSize = fontPx.toSp())
            val box = box0 * scale
            paragraph(measurer, style.copy(color = c.foreground), padding.dp.toPx(), textWidth.dp.toPx(), top, box, c.border, c.primary.copy(alpha = 0.1f))

            val edge = CanonMetrics.frame.toPx()
            drawRect(c.primary, Offset(bracket - edge / 2, top), Size(edge, box))
            for (tickY in listOf(top, top + box - edge)) {
                drawRect(c.primary, Offset(bracket - 6.dp.toPx(), tickY), Size(12.dp.toPx(), edge))
            }

            var x = bracket + (BRACKET_COLUMN + COMPARISON_GAP).dp.toPx()
            val gap = COMPARISON_GAP.dp.toPx()
            val room = size.width - padding.dp.toPx() - x
            val count = minOf(others.size, ((room + gap) / (COMPARISON_WIDTH.dp.toPx() + gap)).toInt())
            if (count <= 0) return@Canvas
            val width = (room - gap * (count - 1)) / count
            for ((label, ratio) in others.take(count)) {
                drawText(measurer, "×$label", Offset(x, top), captionMono.copy(color = c.mutedForeground))
                val comparison = maxOf(1f, shownSize).dp.toPx() * ratio.toFloat() * scale
                paragraph(measurer, style.copy(color = c.mutedForeground), x, width, top + CAPTION_HEIGHT.dp.toPx(), comparison, c.border)
                x += width + gap
            }
        }

        // flush with bracket ticks
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .width(maxOf(0f, bracketX + 6).dp)
                .padding(bottom = CanonMetrics.panelPadding - 4.dp),
        ) {
            CanonFigure(figure, Modifier.align(Alignment.BottomEnd), unit = "px", size = 34.sp, halo = c.card)
        }
    }
}

/**
 * The pangram in line boxes [box] high from [top] to the bottom edge, each ruled at its top in [rule].
 * A [band] shades each glyph content area, centred in its box as CSS centres it, and runs the rules from the leading edge.
 */
private fun DrawScope.paragraph(
    measurer: TextMeasurer,
    style: TextStyle,
    x: Float,
    width: Float,
    top: Float,
    box: Float,
    rule: Color,
    band: Color? = null,
) {
    val lit = band != null
    val space = measurer.measure(" ", style).size.width
    val lines = LineHeightEngine.wrap(pangram, width) { measurer.measure(it, style).size.width + space.toFloat() }
    val content = measurer.measure("Ag", style).size.height.toFloat()
    val halfLeading = (box - content) / 2
    val hairline = CanonMetrics.hairline.toPx()
    // rules span lit paragraph
    val ruleLeft = if (lit) 0f else x
    val ruleWidth = if (lit) x + width else width
    var y = top
    var index = 0
    while (y < size.height && box > 0f) {
        drawRect(rule, Offset(ruleLeft, y), Size(ruleWidth, hairline))
        if (band != null) drawRect(band, Offset(x, y + halfLeading), Size(width, content))
        val line = if (lines.isEmpty()) "" else lines[index % lines.size]
        if (line.isNotEmpty()) drawText(measurer, line, Offset(x, y + halfLeading), style, softWrap = false)
        y += box
        index++
    }
}
