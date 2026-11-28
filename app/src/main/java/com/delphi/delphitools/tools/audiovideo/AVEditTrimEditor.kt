package com.delphi.delphitools.tools.audiovideo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.max
import kotlin.math.roundToInt

internal val TRIM_GRIP = 14.dp

/**
 * dt-ios AVEditTrimEditor: [strip] between two grips that drag the in and out points, with the selection framed.
 * [strip] gets the grip width as its inset.
 */
// ponytail: grips are not TalkBack-adjustable; the Start and End fields are the accessible way in
@Composable
internal fun AVEditTrimEditor(
    duration: Double,
    start: Double,
    end: Double,
    enabled: Boolean,
    onChange: (Double, Double) -> Unit,
    strip: @Composable (inset: Dp) -> Unit,
) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(Modifier.fillMaxSize()) {
        strip(TRIM_GRIP)
        if (!enabled) return@BoxWithConstraints
        val width = constraints.maxWidth.toFloat()
        val grip = with(density) { TRIM_GRIP.toPx() }
        val target = with(density) { CanonMetrics.controlHeight.toPx() }
        val axis = AVEditAxis(duration, width, grip, rightToLeft)
        val (left, right) = listOf(axis.x(start), axis.x(end)).sorted()
        with(density) {
            Box(
                Modifier
                    .offset { IntOffset(left.roundToInt(), 0) }
                    .width((right - left).toDp())
                    .fillMaxHeight()
                    .border(CanonMetrics.frame, c.primary)
            )
        }
        val change by rememberUpdatedState(onChange)
        val selection by rememberUpdatedState(start to end)
        val span by rememberUpdatedState(duration)
        val stripWidth = max(1f, width - grip * 2)
        for (isStart in listOf(true, false)) {
            val onLeft = isStart != rightToLeft
            val barX = if (onLeft) left - grip else right
            val targetX = (if (onLeft) barX - (target - grip) else barX).coerceIn(0f, max(0f, width - target))
            val seconds = if (isStart) start else end
            Box(
                Modifier
                    .offset { IntOffset(targetX.roundToInt(), 0) }
                    .width(with(density) { target.toDp() })
                    .fillMaxHeight()
                    .semantics {
                        contentDescription = if (isStart) "Start" else "End"
                        stateDescription = AVEditEngine.timestamp(seconds)
                    }
                    .pointerInput(isStart, stripWidth, rightToLeft) {
                        var origin = 0.0
                        var moved = 0f
                        detectHorizontalDragGestures(onDragStart = {
                            origin = if (isStart) selection.first else selection.second
                            moved = 0f
                        }) { pointer, amount ->
                            pointer.consume()
                            moved += amount
                            val delta = moved / stripWidth * span * if (rightToLeft) -1 else 1
                            val next = ((origin + delta) * 1000).roundToInt() / 1000.0
                            if (isStart) change(next, selection.second) else change(selection.first, next)
                        }
                    }
            ) {
                Box(
                    Modifier
                        .offset { IntOffset((barX - targetX).roundToInt(), 0) }
                        .width(TRIM_GRIP)
                        .fillMaxHeight()
                        .background(c.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (onLeft) pencil(R.drawable.pencil_chevron_left) else pencil(R.drawable.pencil_chevron_right),
                        contentDescription = null,
                        tint = c.primaryForeground,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
