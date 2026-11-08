package com.delphi.delphitools.canon

import android.animation.ValueAnimator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Square switch, 40 by 22. In a CanonRow, TalkBack reads the row's title as its label. */
@Composable
fun CanonSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = LocalDelphiColours.current
    val label = claimRowLabel()
    val track by animateColorAsState(if (checked) c.primary else c.muted, tween(200))
    val knob by animateColorAsState(if (checked) c.primaryForeground else c.mutedForeground, tween(200))
    val travel by animateDpAsState(if (checked) 18.dp else 0.dp, tween(200))
    Box(
        modifier
            .size(40.dp, 22.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .background(track)
            .border(CanonMetrics.hairline, if (checked) c.primary else c.border)
            .toggleable(checked, null, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { if (label != null) contentDescription = label }
            .padding(2.dp)
    ) {
        Box(Modifier.offset(x = travel).size(18.dp).background(knob))
    }
}

// CanonSlider.fineTiers: dragging this far (dp) above or below the track slows it to this speed
private val fineTiers = listOf(60 to 0.5f, 120 to 0.25f, 180 to 0.1f)

/**
 * Track with a leading fill and a marker, readout cell beside it. A drag starts where the finger is, then
 * moves relative to it, finer as the finger pulls away from the track. [step] snaps to multiples above the
 * lower bound. [fillsFromTrailing] fills from the trailing edge.
 */
@Composable
fun CanonSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    step: Float? = null,
    readout: String,
    fillsFromTrailing: Boolean = false,
) {
    val c = LocalDelphiColours.current
    val label = claimRowLabel()
    var active by remember { mutableStateOf(false) }
    val thickness by animateDpAsState(if (active) 6.dp else 2.dp, tween(150))
    val span = range.endInclusive - range.start
    val fraction = if (span > 0) ((value - range.start) / span).coerceIn(0f, 1f) else 0f
    val backwards = (LocalLayoutDirection.current == LayoutDirection.Rtl) != fillsFromTrailing
    val current by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    fun set(raw: Float) {
        var next = raw.coerceIn(range)
        if (step != null && step > 0) next = min(range.endInclusive, range.start + ((next - range.start) / step).roundToInt() * step)
        if (next != current) onChange(next)
    }
    Row(
        modifier
            .height(IntrinsicSize.Min)
            .heightIn(min = CanonMetrics.controlHeight)
            .background(c.border)
            .semantics(mergeDescendants = true) {
                if (label != null) contentDescription = label
                stateDescription = readout
                progressBarRangeInfo = ProgressBarRangeInfo(value, range, steps = step?.let { (span / it).roundToInt() - 1 } ?: 19)
                setProgress { set(it); true }
            },
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        var widthPx by remember { mutableIntStateOf(1) }
        Box(
            Modifier
                .weight(1f)
                .widthIn(min = 96.dp)
                .fillMaxHeight()
                .background(c.muted)
                .clipToBounds()
                .onSizeChanged { widthPx = it.width }
                .pointerInput(range, step, backwards) {
                    fun at(x: Float): Float {
                        val unit = (x / size.width).coerceIn(0f, 1f)
                        return range.start + (if (backwards) 1 - unit else unit) * span
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        active = true
                        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (start == null) {
                            // a tap lifts unconsumed; a page scroll consumes
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && !up.pressed && !up.isConsumed) set(at(up.position.x))
                        } else {
                            var dragged = at(start.position.x)
                            var lastX = start.position.x
                            set(dragged)
                            horizontalDrag(start.id) { change ->
                                val away = abs(change.position.y - size.height / 2f)
                                val speed = fineTiers.lastOrNull { away >= it.first.dp.toPx() }?.second ?: 1f
                                val delta = (change.position.x - lastX) / size.width * span * speed
                                lastX = change.position.x
                                dragged = (dragged + if (backwards) -delta else delta).coerceIn(range)
                                set(dragged)
                                change.consume()
                            }
                        }
                        active = false
                    }
                }
        ) {
            val trackWidth = with(LocalDensity.current) { widthPx.toDp() }
            val edge = (trackWidth * fraction - thickness).coerceAtLeast(0.dp)
            val anchor = if (fillsFromTrailing) Alignment.CenterEnd else Alignment.CenterStart
            Box(Modifier.align(anchor).fillMaxHeight().fillMaxWidth(fraction).background(c.primary))
            Box(
                Modifier
                    .align(anchor)
                    .offset(x = if (fillsFromTrailing) -edge else edge)
                    .fillMaxHeight()
                    .width(thickness)
                    .background(c.foreground)
            )
        }
        Box(
            Modifier
                .widthIn(min = 64.dp)
                .fillMaxHeight()
                .background(c.card)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                readout,
                style = CanonText.bodySemibold.copy(fontFeatureSettings = TABULAR),
                color = c.foreground,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** Minus, a typed field with its unit, plus. Minus and plus repeat while held; a tap on the middle types. */
@Composable
fun CanonStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    step: Int = 1,
) {
    val c = LocalDelphiColours.current
    val focus = remember { FocusRequester() }
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(
        modifier
            .height(IntrinsicSize.Min)
            .heightIn(min = CanonMetrics.controlHeight)
            .background(c.border),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        CanonButton(
            "",
            Modifier.width(44.dp).fillMaxHeight(),
            icon = Icons.Rounded.Remove,
            repeats = true,
            contentDescription = "Decrease",
        ) { onValueChange(value - step) }
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(c.card)
                .clickable(null, indication = null) { focus.requestFocus() }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { typed ->
                    text = typed
                    typed.toIntOrNull()?.let { if (it != value) onValueChange(it) }
                },
                singleLine = true,
                textStyle = CanonText.bodySemibold.copy(color = c.foreground, textAlign = TextAlign.End, fontFeatureSettings = TABULAR),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).widthIn(min = 48.dp).focusRequester(focus),
            )
            Text(unit, style = CanonText.caption, color = c.mutedForeground)
        }
        CanonButton(
            "",
            Modifier.width(44.dp).fillMaxHeight(),
            icon = Icons.Rounded.Add,
            repeats = true,
            contentDescription = "Increase",
        ) { onValueChange(value + step) }
    }
}

/** Leading-edge fill, no marker. */
@Composable
fun CanonBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    fill: Color = LocalDelphiColours.current.primary,
    track: Color = LocalDelphiColours.current.muted,
) {
    val share = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .height(4.dp)
            .background(track)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(share, 0f..1f) }
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(share).background(fill))
    }
}

enum class CanonLoaderMode(internal val trail: FloatArray) {
    Snake(floatArrayOf(1f, 1f, 1f, 1f, 1f)),
    Comet(floatArrayOf(1f, 0.65f, 0.4f, 0.22f, 0.1f)),
    Dot(floatArrayOf(1f)),
    Twin(floatArrayOf(1f, 1f, 1f, 0f, 1f, 1f, 1f)),
    Thinking(floatArrayOf(1f, 1f, 1f, 1f, 1f)),
}

// clockwise index of each cell, row by row; null is the always-lit centre
private val loaderOrder = listOf(0, 1, 2, 7, null, 3, 6, 5, 4)
private const val LOADER_DIM = 0.2f

/** CanonLoader.lit: how lit the outer cell at clockwise [index] is at [step]. */
internal fun canonLoaderLit(mode: CanonLoaderMode, reverse: Boolean, index: Int, step: Int): Float {
    fun since(visit: Int, cycle: Int): Float {
        val phase = ((step - visit) % cycle + cycle) % cycle
        return mode.trail.getOrElse(phase) { 0f }
    }
    // pendulum: the head visits i and 15 - i
    if (mode == CanonLoaderMode.Thinking) return max(since(index, 16), since(15 - index, 16))
    return since(if (reverse) -index else index, 8)
}

/** The 3 x 3 cell loader (NdsLoader). */
@Composable
fun CanonLoader(
    modifier: Modifier = Modifier,
    mode: CanonLoaderMode = CanonLoaderMode.Snake,
    reverse: Boolean = false,
    cell: Dp = 8.dp,
    gap: Dp = 2.dp,
    colour: Color = LocalDelphiColours.current.foreground,
) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val tick = if (ValueAnimator.areAnimatorsEnabled()) 100L else 500L
        while (true) {
            delay(tick)
            step++
        }
    }
    Canvas(modifier.size(cell * 3 + gap * 2).semantics { contentDescription = "Loading" }) {
        val side = cell.toPx()
        val pitch = side + gap.toPx()
        loaderOrder.forEachIndexed { i, order ->
            val lit = order?.let { max(LOADER_DIM, canonLoaderLit(mode, reverse, it, step)) } ?: 1f
            drawRect(colour.copy(alpha = colour.alpha * lit), Offset(i % 3 * pitch, i / 3 * pitch), Size(side, side))
        }
    }
}
