package com.delphi.delphitools.canon

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Where a released wheel is, in rows. `target` is the row a spring pulls it onto, `null` while it coasts.
 */
data class CanonDetentMotion(val position: Float, val velocity: Float = 0f, val target: Float? = null) {
    val isSettled get() = target == position && velocity == 0f

    /** One frame: coast under friction, then a spring onto the caught row. `range` is null when the wheel loops. */
    fun advance(seconds: Float, range: ClosedFloatingPointRange<Float>?): CanonDetentMotion {
        fun clamped(value: Float) = range?.let { value.coerceIn(it) } ?: value
        var position = position
        var velocity = velocity
        var target = target
        if (target != null) {
            val pull = -STIFFNESS * (position - target) - DAMPING * velocity
            velocity += pull * seconds
            position += velocity * seconds
            if (abs(position - target) < 0.001f && abs(velocity) < 0.02f) {
                position = target
                velocity = 0f
            }
        } else {
            position += velocity * seconds
            velocity *= exp(-FRICTION * seconds)
            if (range != null && position !in range) {
                target = clamped(position.roundToInt().toFloat())
            } else if (abs(velocity) < CATCH_SPEED) {
                target = clamped((position + velocity * CATCH_LEAD).roundToInt().toFloat())
            }
        }
        return CanonDetentMotion(position, velocity, target)
    }

    private companion object {
        // 1/s, exponential decay of a fling
        const val FRICTION = 4.5f

        // rows per second below which the nearest row catches the wheel, and seconds looked ahead
        const val CATCH_SPEED = 2.5f
        const val CATCH_LEAD = 0.12f

        // about 2 % overshoot
        const val STIFFNESS = 320f
        const val DAMPING = 28f
    }
}

private const val FLING_CARRY = 0.85f
private const val FLING_LIMIT = 36f
private const val RUBBER = 0.3f
private const val REACH = (PI / 2 * 0.94).toFloat()

/**
 * Rows on a drum, turned by a vertical drag with inertia, resting on a row that is the selection.
 * A row crossing the centre ticks. It loops when the rows overflow the half-turn; otherwise the ends
 * pull back like rubber. `row` draws one row, given its index and focus: 1 on the centre line, 0 a row
 * or more away. A change of `selected` from outside turns the wheel there the shorter way round.
 */
// ponytail: no spin-in on reload (iOS `reload(spin:)`)
@Composable
fun CanonDetentWheel(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 40.dp,
    accessibilityText: (Int) -> String = { "${it + 1}" },
    row: @Composable (index: Int, focus: Float) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val select by rememberUpdatedState(onSelect)
    var motion by remember { mutableStateOf(CanonDetentMotion(selected.toFloat(), target = selected.toFloat())) }
    var current by remember { mutableStateOf(selected) }
    var userDriven by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf<Job?>(null) }

    fun wrapped(index: Int) = if (count == 0) 0 else ((index % count) + count) % count

    BoxWithConstraints(modifier.clipToBounds()) {
        val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
        val heightPx = constraints.maxHeight.toFloat()
        val radius = max(heightPx / 2, rowPx)
        val step = rowPx / radius
        val loops = count > 2 * (REACH / step).toInt() + 1
        val range = if (loops) null else 0f..max(count - 1, 0).toFloat()

        fun run() {
            if (running?.isActive == true) return
            running = scope.launch {
                var last = withFrameNanos { it }
                while (!motion.isSettled) {
                    val now = withFrameNanos { it }
                    motion = motion.advance(((now - last) / 1e9f).coerceAtMost(1f / 30), range)
                    last = now
                }
                if (userDriven) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                userDriven = false
            }
        }

        fun turnTo(index: Int) {
            var goal = index.toFloat()
            if (loops) {
                val here = motion.position.roundToInt()
                val ahead = wrapped(index - here)
                goal = (here + if (ahead > count / 2f) ahead - count else ahead).toFloat()
            }
            motion = motion.copy(target = goal)
            run()
        }

        val nearest = wrapped(motion.position.roundToInt())
        LaunchedEffect(nearest) {
            if (nearest == current || count == 0) return@LaunchedEffect
            current = nearest
            if (userDriven) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            select(nearest)
        }
        LaunchedEffect(selected, count) {
            if (count > 0 && selected != current) {
                current = selected
                turnTo(selected)
            }
        }

        val tracker = remember { VelocityTracker() }
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(count, rowPx, radius) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (count == 0) return@awaitEachGesture
                        val start = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            ?: return@awaitEachGesture
                        running?.cancel()
                        userDriven = true
                        motion = CanonDetentMotion(motion.position)
                        tracker.resetTracking()
                        tracker.addPosition(start.uptimeMillis, start.position)
                        verticalDrag(start.id) { change ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            var rows = -(change.position.y - change.previousPosition.y) / rowPx
                            if (range != null && motion.position !in range) rows *= RUBBER
                            motion = motion.copy(position = motion.position + rows)
                            change.consume()
                        }
                        val speed = -tracker.calculateVelocity().y / rowPx * FLING_CARRY
                        motion = motion.copy(velocity = speed.coerceIn(-FLING_LIMIT, FLING_LIMIT))
                        run()
                    }
                }
                .pointerInput(count, rowPx, radius) {
                    detectTapGestures { tap ->
                        if (count == 0) return@detectTapGestures
                        val lift = ((tap.y - size.height / 2f) / radius).coerceIn(-1f, 1f)
                        val rows = (asin(lift) / step).roundToInt()
                        if (rows == 0) return@detectTapGestures
                        var goal = (motion.position.roundToInt() + rows).toFloat()
                        if (range != null) goal = goal.coerceIn(range)
                        userDriven = true
                        motion = motion.copy(target = goal)
                        run()
                    }
                }
                .semantics {
                    if (count == 0) return@semantics
                    stateDescription = accessibilityText(current)
                    progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), 0f..max(count - 1, 1).toFloat(), max(count - 2, 0))
                    setProgress {
                        val index = it.roundToInt().coerceIn(0, count - 1)
                        motion = CanonDetentMotion(index.toFloat(), target = index.toFloat())
                        true
                    }
                }
        )
        if (count > 0 && heightPx > 0) {
            val centre = motion.position.roundToInt()
            val half = (REACH / step).toInt() + 1
            for (index in centre - half..centre + half) {
                if (!loops && index !in 0 until count) continue
                val offset = index - motion.position
                val angle = offset * step
                if (abs(angle) > REACH) continue
                val lean = cos(angle)
                key(index) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(rowHeight)
                            .graphicsLayer {
                                translationY = radius * sin(angle)
                                scaleY = max(lean, 0.01f)
                                alpha = max(lean, 0f).pow(1.4f)
                            },
                        contentAlignment = Alignment.Center,
                    ) { row(wrapped(index), max(0f, 1 - abs(offset))) }
                }
            }
        }
    }
}
