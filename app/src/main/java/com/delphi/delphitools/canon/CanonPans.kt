package com.delphi.delphitools.canon

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

/**
 * A drag in any direction that begins only where `grabs` finds a handle. It takes the touch from its first
 * contact, so an enclosing scroll never starts; a touch anywhere else is left to the scroll.
 */
fun Modifier.canonGrabPan(
    grabs: (Offset) -> Boolean,
    onStart: (Offset) -> Unit = {},
    onEnd: () -> Unit = {},
    onDrag: (change: PointerInputChange, delta: Offset) -> Unit,
): Modifier = composed {
    val grab by rememberUpdatedState(grabs)
    val start by rememberUpdatedState(onStart)
    val end by rememberUpdatedState(onEnd)
    val move by rememberUpdatedState(onDrag)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!grab(down.position)) return@awaitEachGesture
            down.consume()
            start(down.position)
            drag(down.id) { change ->
                move(change, change.positionChange())
                change.consume()
            }
            end()
        }
    }
}

// CanonSidewaysPan.judgement: travel before the direction is judged, and how many times sideways beats vertical
private val sidewaysTravel = 6.dp
private const val SIDEWAYS_RATIO = 2f

/** A sideways drag inside a scrolling page: it gives the touch up as soon as the drag is not clearly sideways. */
fun Modifier.canonSidewaysPan(
    onStart: (Offset) -> Unit = {},
    onEnd: () -> Unit = {},
    onDrag: (change: PointerInputChange, delta: Float) -> Unit,
): Modifier = composed {
    val start by rememberUpdatedState(onStart)
    val end by rememberUpdatedState(onEnd)
    val move by rememberUpdatedState(onDrag)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val travel = sidewaysTravel.toPx()
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed || change.isConsumed) return@awaitEachGesture
                val moved = change.position - down.position
                if (max(abs(moved.x), abs(moved.y)) < travel) continue
                if (abs(moved.x) < abs(moved.y) * SIDEWAYS_RATIO) return@awaitEachGesture
                change.consume()
                start(change.position)
                break
            }
            horizontalDrag(down.id) { change ->
                move(change, change.positionChange().x)
                change.consume()
            }
            end()
        }
    }
}
