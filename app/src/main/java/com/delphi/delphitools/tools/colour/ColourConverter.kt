package com.delphi.delphitools.tools.colour

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourControls
import com.delphi.delphitools.canon.CanonColourControlsState
import com.delphi.delphitools.canon.CanonColourNotationInput
import com.delphi.delphitools.canon.CanonColourNotationState
import com.delphi.delphitools.canon.CanonColourSolid
import com.delphi.delphitools.canon.CanonColourSolidState
import com.delphi.delphitools.canon.CanonColourStrip
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMenuCell
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.colourNamesOrNull
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.SolidSpace
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.parseHex
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.launch
import kotlin.math.floor

// dt-ios ColourManagement key; the value is the working space's key
private const val WORKING = "colourManagementWorking"

class ColourConverterModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("delphitools", Context.MODE_PRIVATE)
    val solid = CanonColourSolidState(SolidSpace.RGB, Rgb.black)

    // sliced row, rgb blue
    val controls = CanonColourControlsState(solid, activeRow = 2)
    val input = CanonColourNotationState()

    /** Working-space numbers, unclamped. */
    var colour by mutableStateOf(parseHex("3B82F6")!!)
        private set
    var management by mutableStateOf(
        ColourManagement(ColourManagement.Working.entries.firstOrNull { it.key == prefs.getString(WORKING, null) } ?: ColourManagement.Working.SRGB),
    )
        private set
    val notation get() = ColourConverterNotation(management)

    /** The solid's space as last shown, to tell a space change from a pick. */
    private var solidSpace = solid.space

    /** The point last set here; any other point is a pick. */
    private var expected = emptyList<Double>()

    init {
        showOnSolid(animated = false)
        viewModelScope.launch {
            snapshotFlow { solid.space to solid.point.toList() }.collect { (space, point) ->
                if (space != solidSpace) {
                    solidSpace = space
                    showOnSolid(animated = false)
                } else if (point != expected) {
                    expected = point
                    colour = notation.colour(ColourConverterNotation.values(point.toDoubleArray(), space), space)
                }
            }
        }
    }

    private fun setPoint(point: DoubleArray, animated: Boolean) {
        expected = point.toList()
        solid.setPoint(point, animated)
    }

    /** The solid's point for the colour: its channels as the value lines give them. */
    private fun showOnSolid(animated: Boolean) =
        setPoint(ColourConverterNotation.point(notation.values(colour, solid.space), solid.space), animated)

    /** Blank text is not marked as unread. */
    fun read(typed: String): Boolean {
        val space = solid.space
        val values = notation.parse(typed, space) ?: return typed.isBlank()
        colour = notation.colour(values, space)
        setPoint(ColourConverterNotation.point(values, space), animated = true)
        return true
    }

    fun pick(srgb: Rgb) {
        colour = management.colour(ColourManagement().xyz(srgb))
        showOnSolid(animated = true)
    }

    fun set(values: List<Double>, space: SolidSpace, animated: Boolean) {
        colour = notation.colour(values, space)
        showOnSolid(animated)
    }

    fun choose(working: ColourManagement.Working) {
        val before = management
        management = ColourManagement(working)
        prefs.edit { putString(WORKING, working.key) }
        // same colour, new numbers
        colour = management.colour(before.xyz(colour))
        showOnSolid(animated = true)
    }
}

private val labelWidth = 76.dp

@Composable
fun ColourConverter(model: ColourConverterModel = viewModel()) {
    val colour = model.colour
    val management = model.management
    val notation = model.notation
    val space = model.solid.space
    val display = management.sRgb(colour)
    val names = colourNamesOrNull()
    val pace = remember { ChangeClock() }
    val animated = pace.note(colour)
    val text = ColourConverterNotation.format(notation.values(colour, space), space)
    LaunchedEffect(text) { model.input.show(text) }

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(1f), background = LocalDelphiColours.current.muted, maxHeight = 640.dp) {
        Column(Modifier.fillMaxSize()) {
            CanonColourSolid(model.solid, Modifier.fillMaxWidth().weight(1f).padding(top = LocalCanonCanvas.current.top))
            CanonColourStrip(display.clamped)
        }
    }

    val controls = CanonSection.of {
        CanonRows {
            CanonRow("Any notation", stacked = true) {
                CanonColourNotationInput(
                    model::pick,
                    state = model.input,
                    parse = model::read,
                    pickerColour = { model.management.sRgb(model.colour).clamped },
                    placeholder = ColourConverterNotation.example(space),
                )
            }
            CanonColourControls(model.controls, hidesNotation = true)
        }
    }

    val formats = CanonSection.of {
        CanonRows {
            CanonHeading("All Formats")
            CanonValueLine("Name", names?.closest(display.clamped), labelWidth)
            SolidSpace.entries.forEach { each ->
                when (each) {
                    SolidSpace.RGB -> {
                        CanonHeading(management.working.title)
                        CanonValueLine("HEX", colour.hex, labelWidth)
                    }
                    SolidSpace.OKLCH -> CanonHeading("D65")
                    // ponytail: iOS heads this with the CMYK output profile; android has no ICC CMYK, so the space name
                    SolidSpace.CMYK -> CanonHeading("CMYK")
                    else -> {}
                }
                var values = notation.values(colour, each)
                CanonValueLine(each.title.uppercase(), ColourConverterNotation.format(values, each), labelWidth)
                if (each == SolidSpace.RGB) CanonValueLine("Decimal RGB", ColourConverterNotation.decimalRgb(colour), labelWidth)
                val active = each == space
                // solid keeps grey hue
                if (active && each != SolidSpace.CMYK) values = ColourConverterNotation.values(model.solid.point, each)
                ColourChannelRibbons(
                    each,
                    values,
                    animated,
                    lit = if (active) minOf(model.controls.activeRow, 2) else null,
                    paint = { notation.colour(it, each).let { c -> management.sRgb(c.clamped).clamped to ColourManagement.inGamut(c) } },
                ) { picked, eased -> model.set(picked, each, eased) }
            }
        }
    }

    val workings = ColourManagement.Working.entries
    // ponytail: no CMYK input and output profile rows; android has no ICC CMYK conversion
    val profiles = CanonSection.of {
        CanonRows {
            CanonHeading("Colour management")
            CanonRow("Working space", flush = true) {
                CanonMenuCell(workings.map { it.title }, management.working.ordinal, { model.choose(workings[it]) })
            }
        }
    }

    CanonSplit(sidebar = listOf(canvas), main = listOf(controls, formats, profiles), halves = true, collapsesTo = 240.dp)
}

/** Changes that come close together, a drag's, follow at once; the rest ease. */
private class ChangeClock {
    private var last = 0L
    private var key: Any? = null
    private var animated = false

    fun note(next: Any): Boolean {
        if (next == key) return animated
        key = next
        val now = SystemClock.uptimeMillis()
        animated = now - last > 120
        last = now
        return animated
    }
}

private const val RIBBON = 12
private const val GAP = 8
private const val PADDING = 12
private const val STOPS = 40
private const val POINT_EASE = 250
private val easeOut = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

private val letters = mapOf(
    SolidSpace.RGB to listOf("R", "G", "B"), SolidSpace.HSL to listOf("H", "S", "L"), SolidSpace.HSB to listOf("H", "S", "B"),
    SolidSpace.OKLCH to listOf("L", "C", "H"), SolidSpace.OKLAB to listOf("L", "a", "b"), SolidSpace.LCH to listOf("L", "C", "H"),
    SolidSpace.LAB to listOf("L", "a", "b"), SolidSpace.CMYK to listOf("C", "M", "Y", "K"),
)

private fun isHue(space: SolidSpace, index: Int) = space.channels[index].let { it.min == 0.0 && it.max == 360.0 }

/**
 * One space's channels as ribbons: each runs its channel's range with the other channels held at the colour, and a
 * tick marks the colour. Outside the working space's gamut a ribbon dims. A tap or a sideways drag sets that channel.
 * `paint` gives the sRGB colour to draw for channel values and whether they are in the working gamut.
 */
@Composable
private fun ColourChannelRibbons(
    space: SolidSpace,
    values: List<Double>,
    animated: Boolean,
    lit: Int?,
    paint: (List<Double>) -> Pair<Rgb, Boolean>,
    onPick: (List<Double>, Boolean) -> Unit,
) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val measurer = rememberTextMeasurer()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val channels = space.channels
    var held by remember { mutableStateOf<List<Double>?>(null) }
    val target = held ?: values
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    val pick by rememberUpdatedState(onPick)
    val base by rememberUpdatedState(target)

    fun blend(t: Float) = from.indices.map { i ->
        var delta = to[i] - from[i]
        // hue: shortest way
        if (isHue(space, i)) delta -= 360 * Math.rint(delta / 360)
        from[i] + delta * t
    }

    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        if (animated && held == null) {
            from = blend(progress.value)
            to = target
            progress.snapTo(0f)
            progress.animateTo(1f, tween(POINT_EASE, easing = easeOut))
        } else {
            progress.snapTo(1f)
            from = target
            to = target
        }
    }

    fun withChannel(values: List<Double>, index: Int, share: Double): List<Double> {
        val channel = channels[index]
        return values.toMutableList().also { it[index] = channel.min + (channel.max - channel.min) * share }
    }

    // ponytail: stops are recomputed each composition, about a thousand conversions; cache by colour if it shows in traces
    val stops = channels.indices.map { index ->
        (0..STOPS).map { stop ->
            val share = stop.toFloat() / STOPS
            val (rgb, inGamut) = paint(withChannel(target, index, share.toDouble()))
            share to rgb.toCanonColor().copy(alpha = if (inGamut) 1f else 0.22f)
        }.toTypedArray()
    }
    val height = PADDING * 2 + channels.size * RIBBON + (channels.size - 1) * GAP

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(c.card)
            .semantics {
                contentDescription = space.title
                stateDescription = ColourConverterNotation.format(target, space)
            }
            .pointerInput(space) {
                detectTapGestures { at ->
                    val track = Track(size.width.toFloat(), this, rtl)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    pick(withChannel(base, track.channel(at.y, channels.size), track.share(at.x)), true)
                }
            }
            .pointerInput(space) {
                var dragged = 0
                fun move(x: Float) {
                    val next = withChannel(held ?: return, dragged, Track(size.width.toFloat(), this, rtl).share(x))
                    held = next
                    pick(next, false)
                }
                detectHorizontalDragGestures(
                    onDragStart = { at ->
                        held = base
                        dragged = Track(size.width.toFloat(), this, rtl).channel(at.y, channels.size)
                        move(at.x)
                    },
                    onDragEnd = { held = null },
                    onDragCancel = { held = null },
                ) { change, _ -> move(change.position.x) }
            },
    ) {
        val track = Track(size.width, this, rtl)
        val shown = if (held != null) target else blend(progress.value)
        val inset = CanonMetrics.panelPadding.toPx()
        val ticks = channels.indices.map { index ->
            val top = (PADDING + index * (RIBBON + GAP)).dp.toPx()
            val band = Size(track.width, RIBBON.dp.toPx())
            val letter = measurer.measure(letters[space]?.getOrNull(index) ?: "", CanonText.captionSemibold.copy(color = if (index == lit) c.primary else c.mutedForeground))
            val letterX = if (rtl) size.width - inset - letter.size.width else inset
            drawText(letter, topLeft = Offset(letterX, top + band.height / 2 - letter.size.height / 2))
            drawRect(c.muted, Offset(track.left, top), band)
            drawRect(Brush.horizontalGradient(*stops[index], startX = track.x(0.0), endX = track.x(1.0)), Offset(track.left, top), band)
            val channel = channels[index]
            var value = shown[index]
            if (isHue(space, index)) value -= 360 * floor(value / 360)
            Offset(track.x(((value - channel.min) / (channel.max - channel.min)).coerceIn(0.0, 1.0)), top + band.height / 2)
        }
        val ink = paint(shown).first.toCanonColor()
        val mark = Size(5.dp.toPx(), (RIBBON + 4).dp.toPx())
        ticks.forEach { tick ->
            val topLeft = Offset(tick.x - mark.width / 2, tick.y - mark.height / 2)
            drawRect(ink, topLeft, mark)
            drawRect(c.foreground, topLeft, mark, style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** The ribbons' span, in the value column of the lines above, and the rows they sit in. */
private class Track(width: Float, density: Density, private val rtl: Boolean) {
    private val inset = with(density) { CanonMetrics.panelPadding.toPx() }
    private val labels = with(density) { (CanonMetrics.panelPadding + labelWidth + 8.dp).toPx() }
    private val pitch = with(density) { (RIBBON + GAP).dp.toPx() }
    private val firstTop = with(density) { (PADDING - GAP / 2f).dp.toPx() }
    val width = width - labels - inset
    val left = if (rtl) inset else labels
    private val right = left + this.width

    fun x(share: Double) = (if (rtl) right - width * share else left + width * share).toFloat()

    fun share(x: Float) = ((if (rtl) right - x else x - left) / width).coerceIn(0f, 1f).toDouble()

    fun channel(y: Float, count: Int) = floor((y - firstTop) / pitch).toInt().coerceIn(0, count - 1)
}
