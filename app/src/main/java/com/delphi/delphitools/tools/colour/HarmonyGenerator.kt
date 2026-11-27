package com.delphi.delphitools.tools.colour

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonColourNotationInput
import com.delphi.delphitools.canon.CanonColourNotationState
import com.delphi.delphitools.canon.CanonColourStrip
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonPaletteCard
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.colour.LocalColourFormats
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.formatted
import com.delphi.delphitools.tools.ToolUndoActions
import com.delphi.delphitools.tools.UndoGrouping
import com.delphi.delphitools.tools.UndoStack
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

class HarmonyGeneratorModel : ViewModel() {
    val turntable = HarmonyTurntableState()
    val notation = CanonColourNotationState()
    var space by mutableStateOf(HarmonyWheelSpace.OKLCH)
        private set
    var mode by mutableStateOf(HarmonyMode.Complementary)
        private set
    var base by mutableStateOf(Rgb(0.231, 0.510, 0.965))
        private set
    var colours by mutableStateOf(emptyList<Rgb>())
        private set

    /** The colours the rows, sliders and CSS show: while the table turns, updated per notch. */
    var shown by mutableStateOf(emptyList<Rgb>())
        private set
    var selected by mutableStateOf<Int?>(null)

    /** Lightness and radius, 0...1 of the space's scale, and hue as asked for; the base is this with radius cut to the gamut. */
    var asked by mutableStateOf(space.parts(base).toList())
        private set
    private var shownNotch: Int? = null
    val undoStack = UndoStack<Pair<Rgb, HarmonyMode>>()
    val grouping = UndoGrouping()

    init {
        turntable.onGrab = ::pushUndo
        turntable.onTurn = { hue, radius ->
            asked = listOf(asked[0], radius, hue)
            apply(animated = false, fromTable = true)
        }
        turntable.onTapBead = { selected = it }
        apply(animated = false)
    }

    /** `colour` in the wheel's space; a grey keeps the hue it had. */
    private fun take(colour: Rgb) {
        val (l, r, h) = space.parts(colour).toList()
        asked = listOf(l, r, if (r < 0.002) asked[2] else h)
    }

    private fun pushUndo() = undoStack.push(base to mode)

    private fun restore(step: Pair<Rgb, HarmonyMode>?) {
        val (colour, mode) = step ?: return
        take(colour)
        this.mode = mode
        selected = null
        apply(animated = true)
    }

    fun undo() = restore(undoStack.undo(base to mode))

    fun redo() = restore(undoStack.redo(base to mode))

    fun use(colour: Rgb) {
        grouping.record(::pushUndo)
        take(colour)
        apply(animated = true)
    }

    fun select(space: HarmonyWheelSpace) {
        val (l, r, h) = asked
        val base = this.space.colour(l, min(r, this.space.maxRadius(l, h)), h)
        this.space = space
        take(base)
        apply(animated = true)
    }

    fun select(mode: HarmonyMode) {
        grouping.record(::pushUndo)
        this.mode = mode
        selected = null
        apply(animated = true)
    }

    /** Channel 0 lightness, 1 radius, 2 hue, in the slider's units. */
    fun slide(channel: Int, value: Double) {
        grouping.record(::pushUndo)
        asked = asked.toMutableList().also {
            it[channel] = when (channel) {
                0 -> value / space.lightnessScale
                1 -> value / space.reach
                else -> value
            }
        }
        // ponytail: canon sliders report no drag state, so slider moves follow instead of easing on a tap
        apply(animated = false)
    }

    private fun apply(animated: Boolean, fromTable: Boolean = false) {
        val (l, r, rawHue) = asked
        val h = wrappedDegrees(rawHue)
        asked = listOf(l, r, h)
        val radius = min(r, space.maxRadius(l, h))
        base = space.colour(l, radius, h)
        colours = space.harmony(l, radius, h, mode)
        turntable.show(h, radius, l, space, mode, colours, animated, fromTable)
        // turning: update per notch
        val notch = floor(h / HarmonyTable.NOTCH).toInt()
        if (fromTable && turntable.isTurning && notch == shownNotch) return
        shownNotch = notch
        notation.show(base)
        shown = colours
    }
}

@Composable
fun HarmonyGenerator(model: HarmonyGeneratorModel = viewModel()) {
    val space = model.space
    val oklch = space == HarmonyWheelSpace.OKLCH
    val format = LocalColourFormats.current.primary
    val (l, r, h) = model.asked
    ToolUndoActions(model.undoStack.canUndo, model.undoStack.canRedo, model::undo, model::redo)

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(1f), background = LocalDelphiColours.current.muted, maxHeight = 640.dp) {
        Column(Modifier.fillMaxSize()) {
            HarmonyTurntable(model.turntable, Modifier.fillMaxWidth().weight(1f))
            CanonColourStrip(model.base, palette = model.colours)
        }
    }

    val controls = CanonSection.of {
        CanonRows {
            CanonRow("Base", stacked = true) { CanonColourNotationInput(model::use, state = model.notation) }
            CanonRow("Space", flush = true) {
                CanonSegmented(HarmonyWheelSpace.entries.map { it.title }, space.ordinal) { model.select(HarmonyWheelSpace.entries[it]) }
            }
            CanonRow("Lightness", flush = true) {
                val value = l * space.lightnessScale
                CanonSlider(
                    value.toFloat(),
                    { model.slide(0, it.toDouble()) },
                    Modifier.fillMaxSize(),
                    range = 0f..space.lightnessScale.toFloat(),
                    readout = String.format(Locale.ROOT, if (oklch) "%.2f" else "%.0f", value),
                )
            }
            CanonRow(if (space == HarmonyWheelSpace.HSL) "Saturation" else "Chroma", flush = true) {
                val value = min(r, 1.0) * space.reach
                CanonSlider(
                    value.toFloat(),
                    { model.slide(1, it.toDouble()) },
                    Modifier.fillMaxSize(),
                    range = 0f..space.reach.toFloat(),
                    readout = String.format(Locale.ROOT, if (oklch) "%.3f" else "%.0f", value),
                )
            }
            CanonRow("Hue", flush = true) {
                CanonSlider(
                    h.toFloat(),
                    { model.slide(2, it.toDouble()) },
                    Modifier.fillMaxSize(),
                    range = 0f..360f,
                    readout = String.format(Locale.ROOT, "%.0f°", h),
                )
            }
        }
    }

    val modes = HarmonyMode.entries
    // ponytail: no Save or Export cells; Android has no user palette store or palette exporter yet
    val palette = CanonSection.of {
        CanonPaletteCard(
            "Harmony",
            model.colours,
            height = 96.dp,
            selected = model.selected,
            onSelect = { index ->
                model.selected = index
                if (index != null) model.turntable.bounce(index)
            },
            picker = { CanonSegmented(modes.map { it.title }, model.mode.ordinal, columns = 2) { model.select(modes[it]) } },
        )
    }

    val shown = model.shown
    val baseIndex = HarmonyTable.slots(model.mode).second
    val offsets = if (model.mode == HarmonyMode.Monochromatic) null else model.mode.angles
    val lines = CanonSection.of {
        CanonRows {
            CanonHeading("Colours")
            shown.forEachIndexed { index, colour ->
                val label = when {
                    index == baseIndex -> "Base"
                    offsets != null -> offsets[index].let { (if (it > 0) "+" else "−") + degrees(abs(it)) + "°" }
                    else -> String.format(Locale.ROOT, if (oklch) "L %.2f" else "L %.0f", space.parts(colour)[0] * space.lightnessScale)
                }
                CanonValueLine(label, colour.formatted(format), labelWidth = 56.dp)
            }
        }
    }

    val css = ":root {\n" + shown.withIndex().joinToString("\n") { "  --harmony-${it.index + 1}: ${it.value.formatted(format)};" } + "\n}"
    val code = CanonSection.of { CanonCodeBlock(css, language = "CSS") }

    CanonSplit(sidebar = listOf(canvas), main = listOf(controls, palette, lines, code), model.grouping.modifier, halves = true, collapsesTo = 240.dp)
}

/** Up to one decimal, no trailing zero. */
private fun degrees(value: Double) = String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")
