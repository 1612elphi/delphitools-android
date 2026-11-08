package com.delphi.delphitools.canon

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.min
import kotlin.math.sqrt

// DelphiMetrics; 1 pt = 1 dp
object CanonMetrics {
    val hairline = 1.dp
    val frame = 2.dp
    val controlHeight = 44.dp
    val rowHeight = 52.dp
    val primaryHeight = 68.dp
    val secondaryHeight = 60.dp
    val panelPadding = 16.dp
    val panelSpacing = 12.dp
}

internal const val TABULAR = "tnum"

// CanonMotion: release spring is UIKit damping 0.45 settling in 450 ms
private val releaseSpring = spring<Float>(dampingRatio = 0.45f, stiffness = 516f)
private const val PRESS_MS = 80
private const val FOLLOW_MS = 50
private const val PRESS_SCALE = 0.97f
private const val TILT_DEPTH = 11f
private const val MAX_TILT = 22f
private const val PERSPECTIVE = 600

// PressRecognizer: a finger this far outside still holds the press
private val pressSlop = 20.dp

// CanonButton.repeatTiming
private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_MS = 90L
private const val REPEAT_FAST_MS = 40L

/** The tilt of one control: sinks and leans under the finger, follows it while held, springs back. */
internal class CanonPress(private val scope: CoroutineScope) {
    val scale = Animatable(1f)
    val tiltX = Animatable(0f)
    val tiltY = Animatable(0f)
    var size = IntSize.Zero
    var pressed by mutableStateOf(false)
        private set

    /** True after a hold repeated the action; the release then does not click. */
    var repeated = false
    private var motion: Job? = null
    private var repeating: Job? = null

    fun press(point: Offset, density: Float, ms: Int) {
        pressed = true
        animate(point, density, ms, release = false)
    }

    /** A null point releases from the centre, with no lean. */
    fun release(point: Offset?, density: Float) {
        pressed = false
        repeating?.cancel()
        animate(point, density, PRESS_MS, release = true)
    }

    fun repeat(action: () -> Unit) {
        repeated = false
        repeating?.cancel()
        repeating = scope.launch {
            delay(REPEAT_DELAY_MS)
            var count = 0
            while (true) {
                repeated = true
                action()
                delay(if (count++ < 10) REPEAT_MS else REPEAT_FAST_MS)
            }
        }
    }

    private fun animate(point: Offset?, density: Float, ms: Int, release: Boolean) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val (rotationY, rotationX) = tilt(point, density)
        val parts = listOf(scale to PRESS_SCALE, tiltY to rotationY, tiltX to rotationX)
        motion?.cancel()
        motion = scope.launch {
            // a quick tap reaches the pressed pose before the spring
            coroutineScope { parts.forEach { (part, to) -> launch { part.animateTo(to, tween(ms)) } } }
            if (!release) return@launch
            coroutineScope { parts.forEach { (part, _) -> launch { part.animateTo(if (part === scale) 1f else 0f, releaseSpring) } } }
        }
    }

    private fun tilt(point: Offset?, density: Float): Pair<Float, Float> {
        point ?: return 0f to 0f
        val halfW = maxOf(size.width / 2f, 1f)
        val halfH = maxOf(size.height / 2f, 1f)
        val x = ((point.x - halfW) / halfW).coerceIn(-1f, 1f)
        val y = ((point.y - halfH) / halfH).coerceIn(-1f, 1f)
        val areaSide = sqrt(halfW * halfH) / density
        val angle = min(MAX_TILT, Math.toDegrees(asin(min(1f, TILT_DEPTH / areaSide)).toDouble()).toFloat())
        return x * angle to -y * angle
    }
}

@Composable
internal fun rememberCanonPress(): CanonPress {
    val scope = rememberCoroutineScope()
    return remember { CanonPress(scope) }
}

/** Click with the Canon press: shrinks and sinks the edge under the finger, follows it, springs back. */
fun Modifier.canonClickable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = composed { canonPress(rememberCanonPress(), enabled, role, onClick = onClick) }

internal fun Modifier.canonPress(
    press: CanonPress,
    enabled: Boolean,
    role: Role?,
    repeats: Boolean = false,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val latest by rememberUpdatedState(onClick)
    this
        .onSizeChanged { press.size = it }
        .pointerInput(enabled, repeats) {
            if (!enabled) return@pointerInput
            // Final pass: sees what the page scroll consumed, takes nothing from the click
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                val slop = pressSlop.toPx()
                press.press(down.position, density, PRESS_MS)
                if (repeats) press.repeat { latest() }
                var inside = true
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed || change.isConsumed) {
                        if (inside) press.release(change?.takeIf { !it.pressed }?.position, density)
                        break
                    }
                    val p = change.position
                    val near = p.x >= -slop && p.y >= -slop && p.x <= size.width + slop && p.y <= size.height + slop
                    if (near && change.position != change.previousPosition) {
                        press.press(p, density, if (inside) FOLLOW_MS else PRESS_MS)
                    } else if (!near && inside) {
                        press.release(null, density)
                    }
                    inside = near
                }
            }
        }
        .clickable(null, indication = null, enabled = enabled, onClickLabel = onClickLabel, role = role) {
            if (!press.repeated) latest()
            press.repeated = false
        }
        .graphicsLayer {
            scaleX = press.scale.value
            scaleY = press.scale.value
            rotationX = press.tiltX.value
            rotationY = press.tiltY.value
            // Skia camera units are 1/72 inch of layer pixels; CanonMotion perspective is 600 pt
            cameraDistance = PERSPECTIVE.dp.toPx() / 72f
        }
}
