package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.SolidChannel
import com.delphi.delphitools.colour.SolidSpace
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import java.util.Locale
import kotlin.math.min

/**
 * What [CanonColourControls] change beside the solid: the active slider row, which is the
 * slice, whether the solid slices at all, and the View plane switch.
 */
@Stable
class CanonColourControlsState(val solid: CanonColourSolidState, activeRow: Int = 0) {
    private var rowValue by mutableIntStateOf(activeRow)
    private var slicesValue by mutableStateOf(true)
    private var planeValue by mutableStateOf(false)
    val notation = CanonColourNotationState()

    /** The slider row that is the solid's slice; row 3, CMYK's black, never slices. */
    var activeRow: Int
        get() = rowValue
        set(value) {
            rowValue = value
            apply()
        }

    /** Slices along the active row's channel; false cuts the solid away instead. */
    var slices: Boolean
        get() = slicesValue
        set(value) {
            slicesValue = value
            apply()
        }

    var viewPlane: Boolean
        get() = planeValue
        set(value) {
            planeValue = value
            apply()
        }

    init {
        apply()
    }

    fun select(space: SolidSpace) {
        solid.space = space
        apply()
    }

    /** Eases the solid to `colour`. */
    fun use(colour: Rgb) = solid.setPoint(solid.space.pointFor(colour), animated = true)

    private fun apply() {
        solid.isOrtho = slicesValue && planeValue
        solid.sliceAxis = if (slicesValue) solid.space.channels[min(rowValue, 2)].axis else null
    }
}

/**
 * Rows that drive a CanonColourSolid: any notation beside the picker, the space, one slider
 * per channel and the View plane switch. The active slider row is the solid's slice.
 * `between` goes between the sliders and View plane. Place it inside a CanonRows.
 */
@Composable
fun CanonColourControls(
    state: CanonColourControlsState,
    notationTitle: String = "Any notation",
    hidesNotation: Boolean = false,
    between: @Composable () -> Unit = {},
) {
    val solid = state.solid
    val colour = solid.colour
    LaunchedEffect(colour.hex) { state.notation.show(colour) }
    if (!hidesNotation) {
        CanonRow(notationTitle, stacked = true) {
            CanonColourNotationInput(state::use, state = state.notation, pickerColour = { solid.colour })
        }
    }
    CanonRow("Space", stacked = true) {
        CanonSegmented(SolidSpace.entries.map { it.title }, solid.space.ordinal, columns = 4) { state.select(SolidSpace.entries[it]) }
    }
    val point = solid.point
    val lit = if (state.slices) min(state.activeRow, 2) else -1
    solid.space.channels.forEachIndexed { index, channel ->
        ChannelRow(channel, index == lit, point, onTouch = { down ->
            when {
                index == 3 -> solid.showsWhole = down
                down -> state.activeRow = index
            }
        }) { value, animated ->
            if (index < 3) state.activeRow = index
            val next = solid.point
            next[channel.axis] = channel.pointValue(value)
            // taps ease, drags follow
            solid.setPoint(next, animated)
        }
    }
    between()
    CanonRow("View plane") { CanonSwitch(state.viewPlane) { state.viewPlane = it } }
}

@Composable
private fun ChannelRow(
    channel: SolidChannel,
    lit: Boolean,
    point: DoubleArray,
    onTouch: (Boolean) -> Unit,
    onValue: (Double, Boolean) -> Unit,
) {
    val c = LocalDelphiColours.current
    val slop = LocalViewConfiguration.current.touchSlop
    var moved by remember { mutableStateOf(false) }
    val touch by rememberUpdatedState(onTouch)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = CanonMetrics.rowHeight)
            .background(c.border),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(c.card)
                .padding(start = CanonMetrics.panelPadding, end = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(channel.name, style = if (lit) CanonText.bodySemibold else CanonText.body, color = if (lit) c.primary else c.foreground)
            channel.detail?.let { Text(it, style = CanonText.caption, color = c.mutedForeground) }
        }
        Box(
            Modifier
                .weight(1.4f)
                .fillMaxHeight()
                .background(c.card)
                // CanonSlider has no touch-down event: watch the pointer before it does
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        moved = false
                        touch(true)
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                            if ((change.position - down.position).getDistance() > slop) moved = true
                            if (!change.pressed) break
                        }
                        touch(false)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            CanonSlider(
                value = min(channel.value(point), channel.max).toFloat(),
                onValueChange = { onValue(it.toDouble(), !moved) },
                modifier = Modifier.fillMaxSize(),
                range = channel.min.toFloat()..channel.max.toFloat(),
                readout = String.format(Locale.ROOT, "%.${channel.decimals}f", channel.value(point)),
            )
        }
    }
}
