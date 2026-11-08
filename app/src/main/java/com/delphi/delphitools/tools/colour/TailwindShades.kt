package com.delphi.delphitools.tools.colour

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonColourNotationInput
import com.delphi.delphitools.canon.CanonColourNotationState
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.LocalColourFormats
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.formatted
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.oklch
import com.delphi.delphitools.colour.takesBlackInk
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.max

private typealias Mode = TailwindShadesEngine.Mode

class TailwindShadesModel : ViewModel() {
    val notation = CanonColourNotationState()
    var base by mutableStateOf(Rgb(0.231, 0.510, 0.965))
        private set
    var mode by mutableStateOf(Mode.Classic)
    var name by mutableStateOf("brand")
        private set
    val colours get() = TailwindShadesEngine.colours(base, mode)

    init {
        notation.show(base)
    }

    fun use(colour: Rgb) {
        base = colour
        notation.show(colour)
    }

    fun rename(text: String) {
        name = TailwindShadesEngine.cleanName(text)
    }
}

@Composable
fun TailwindShades(model: TailwindShadesModel = viewModel()) {
    val colours = model.colours
    val format = LocalColourFormats.current.primary

    val sidebar = CanonSection.of {
        CanonRows {
            CanonRow("Base", stacked = true) { CanonColourNotationInput(model::use, state = model.notation) }
            CanonRow("Name", flush = true) {
                CanonTextCell(
                    model.name,
                    model::rename,
                    Modifier.fillMaxSize(),
                    placeholder = "brand",
                    textStyle = TextStyle(fontFamily = DelphiMono, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
            CanonRow("Mode", stacked = true) {
                CanonSegmented(Mode.entries.map { it.title }, model.mode.ordinal, columns = 3) { model.mode = Mode.entries[it] }
            }
        }
    }

    val main = listOf(
        CanonSection.of {
            CanonRows {
                CanonHeading("Shades")
                TailwindShadesEngine.steps.zip(colours).forEach { (step, colour) ->
                    CanonValueLine("$step", colour.formatted(format), labelWidth = 40.dp)
                }
            }
        },
        CanonSection.of { CanonCodeBlock(TailwindShadesEngine.cssHex(model.name, colours), language = "CSS Hex") },
        CanonSection.of { CanonCodeBlock(TailwindShadesEngine.cssOklch(model.name, colours), language = "CSS OKLCH") },
        CanonSection.of { CanonCodeBlock(TailwindShadesEngine.tailwindConfig(model.name, colours), language = "Tailwind Config") },
    )

    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(250.dp), background = LocalDelphiColours.current.muted) {
        TailwindRamp(colours, model.base, Modifier.fillMaxSize())
    }

    CanonSplit(sidebar = listOf(sidebar), main = main, banner = banner)
}

private val levelStyle = CanonText.footnoteSemibold
private val hexStyle = TextStyle(fontFamily = DelphiMono, fontSize = 9.sp)
private val tagStyle = CanonText.captionSemibold
private val layoutSpring = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow)

/**
 * The scale as a row of swatches from 50 to 950, each shade's OKLCH lightness plotted over it, and the base colour
 * placed on that curve at its own lightness, with the level it would be.
 */
@Composable
private fun TailwindRamp(colours: List<Rgb>, base: Rgb, modifier: Modifier) {
    val top = LocalCanonCanvas.current.top
    val measurer = rememberTextMeasurer()
    val lightness = colours.map { it.oklch()[0] }
    val baseL = base.oklch()[0]
    val place = TailwindShadesEngine.place(baseL, lightness)
    val swatches = colours.map { animateColorAsState(it.toCanonColor(), tween(350)).value }
    val markerIndex by animateFloatAsState(place.index.toFloat(), layoutSpring)
    val markerL by animateFloatAsState(baseL.toFloat(), layoutSpring)
    val tag = "Base ≈ ${place.level}"

    Canvas(
        modifier.clearAndSetSemantics {
            contentDescription = tag
            stateDescription = base.hex
        },
    ) {
        if (colours.size != TailwindShadesEngine.steps.size || size.width <= 0) return@Canvas
        val width = size.width / colours.size
        val showsHex = width >= 64.dp.toPx()
        val labelsHeight = (if (showsHex) 44 else 30).dp.toPx()
        val plotTop = top.toPx() + 22.dp.toPx()
        val plotBottom = size.height - labelsHeight - 16.dp.toPx()
        fun y(l: Double) = plotTop + (1 - l.coerceIn(0.0, 1.0)).toFloat() * max(plotBottom - plotTop, 0f)
        fun x(index: Double) = (index.toFloat() + 0.5f) * width

        swatches.forEachIndexed { index, colour -> drawRect(colour, Offset(index * width, 0f), Size(width + 0.5f, size.height)) }

        val labelTop = size.height - labelsHeight - 4.dp.toPx()
        val nearest = place.nearest
        val plateInset = max((width - 40.dp.toPx()) / 2, 3.dp.toPx())
        val plateHeight = 22.dp.toPx() - 2.dp.toPx()
        drawRoundRect(
            if (colours[nearest].takesBlackInk) Color.Black else Color.White,
            Offset(nearest * width + plateInset, labelTop + 1.dp.toPx()),
            Size(width - plateInset * 2, plateHeight),
            CornerRadius(plateHeight / 2),
        )
        colours.forEachIndexed { index, colour ->
            val ink = if (colour.takesBlackInk) Color.Black else Color.White
            val levelInk = if (index == nearest) (if (colour.takesBlackInk) Color.White else Color.Black) else ink
            val cell = Constraints.fixedWidth(width.toInt())
            val level = measurer.measure("${TailwindShadesEngine.steps[index]}", levelStyle.copy(color = levelInk, textAlign = TextAlign.Center), constraints = cell)
            drawText(level, topLeft = Offset(index * width, labelTop + (22.dp.toPx() - level.size.height) / 2))
            if (showsHex) {
                val hex = measurer.measure(colour.hex, hexStyle.copy(color = ink.copy(alpha = 0.75f), textAlign = TextAlign.Center), constraints = cell)
                drawText(hex, topLeft = Offset(index * width, labelTop + 20.dp.toPx() + (16.dp.toPx() - hex.size.height) / 2))
            }
        }

        val points = lightness.mapIndexed { index, l -> Offset(x(index.toDouble()), y(l)) }
        val curve = Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val haloStroke = Stroke(4.dp.toPx())
        drawPath(curve, Color.White.copy(alpha = 0.75f), style = haloStroke)
        points.forEach { drawCircle(Color.White.copy(alpha = 0.75f), 6.dp.toPx(), it, style = haloStroke) }
        drawPath(curve, Color.Black.copy(alpha = 0.8f), style = Stroke(1.5.dp.toPx()))
        points.forEachIndexed { index, at ->
            drawCircle(colours[index].toCanonColor(), 4.5.dp.toPx(), at)
            drawCircle(Color.Black.copy(alpha = 0.8f), 4.5.dp.toPx(), at, style = Stroke(1.5.dp.toPx()))
        }

        val at = Offset(x(markerIndex.toDouble()), y(markerL.toDouble()))
        val dash = 4.dp.toPx()
        drawLine(Color.Black.copy(alpha = 0.7f), Offset(0f, at.y), Offset(size.width, at.y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))

        val side = 22.dp.toPx()
        drawCircle(Color.Black.copy(alpha = 0.35f), side / 2 + 1.dp.toPx(), at + Offset(0f, 1.dp.toPx()))
        drawCircle(base.clamped.toCanonColor(), side / 2, at)
        drawCircle(Color.White, side / 2 - 1.25.dp.toPx(), at, style = Stroke(2.5.dp.toPx()))

        val label = measurer.measure(tag, tagStyle.copy(color = Color.White))
        val plate = Size(label.size.width + 12.dp.toPx(), label.size.height + 6.dp.toPx())
        var plateX = at.x + side / 2 + 6.dp.toPx()
        if (plateX + plate.width > size.width - 6.dp.toPx()) plateX = at.x - side / 2 - 6.dp.toPx() - plate.width
        val plateY = (at.y - plate.height / 2).coerceAtLeast(plotTop - 12.dp.toPx()).coerceAtMost(plotBottom - plate.height / 2)
        drawRoundRect(Color.Black.copy(alpha = 0.72f), Offset(plateX, plateY), plate, CornerRadius(4.dp.toPx()))
        drawText(label, topLeft = Offset(plateX + 6.dp.toPx(), plateY + 3.dp.toPx()))
    }
}
