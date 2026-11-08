package com.delphi.delphitools.tools.colour

import android.graphics.Bitmap
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.tools.colour.HarmonyTable.NOTCH
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.IEEErem
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private class Bead(var offset: Double, var rung: Double, var angle: Double, var chroma: Double, var presence: Double) {
    var angleV = 0.0
    var chromaV = 0.0
    var presenceV = 0.0
    var lift = 0.0
    var liftV = 0.0
    var wait = 0.0
    var next: Pair<Double, Double>? = null
    var isSliding = false
    var isLeaving = false
}

private enum class Phase { Resting, Turning, Coasting, Catching }

internal enum class Haptic { Tick, Click, Clunk }

private fun remainder(a: Double, b: Double) = a.IEEErem(b)

/**
 * dt-ios HarmonyTurntable: the harmony as beads on a turntable over the wheel space's plane at the base's
 * lightness. Dragging turns the rule; a bead dragged radially changes chroma. A flick spins the table until a
 * notch catches it; a new rule slides the beads into place one by one.
 */
@Stable
class HarmonyTurntableState {
    /** Hue in degrees and the base's radius, while a finger or a spin turns the table. */
    var onTurn: (Double, Double) -> Unit = { _, _ -> }

    /** A bead was tapped: its index in the model's harmony order. */
    var onTapBead: (Int) -> Unit = {}

    /** A finger takes the table, before its first onTurn. */
    var onGrab: () -> Unit = {}
    val isTurning get() = phase != Phase.Resting

    internal var lightness by mutableStateOf(0.6)
        private set
    internal var space by mutableStateOf(HarmonyWheelSpace.OKLCH)
        private set
    internal var version by mutableIntStateOf(0)
    internal var running by mutableIntStateOf(0)
    internal var figure by mutableStateOf("0°")
        private set
    internal var figureRolls by mutableStateOf(true)
        private set
    internal var haptic: (Haptic) -> Unit = {}
    internal var reduceMotion = false

    private var phase = Phase.Resting
    private var theta = 0.0
    private var thetaV = 0.0
    private var thetaTarget = 0.0
    private var rho = 0.0
    private var rhoV = 0.0
    private var rhoTarget = 0.0
    private var spinSpeed = 0.0
    private var mode = HarmonyMode.Complementary
    private var modelColours: List<Rgb> = emptyList()
    private val base = Bead(0.0, 0.0, 0.0, 0.0, 1.0)
    private var partners = listOf<Bead>()
    private val leaving = mutableListOf<Bead>()
    private var lastNotch = 0
    private var hasShown = false
    private var lastClick = 0.0

    private var grabbed: Bead? = null
    private var lastPointer = 0.0
    private var turned = 0.0
    private var grabTheta = 0.0
    private var grabChromaOffset = 0.0
    private val samples = ArrayDeque<Pair<Double, Double>>()

    internal var centre = Offset.Zero
    internal var ringRadius = 0f
    internal var planeRadius = 0f
    internal var beadRadius = 14f

    private val now get() = System.nanoTime() / 1e9

    /** `fromTable`: the values came back from onTurn, and the table keeps its own. `radius` is 0...1 of the reach. */
    fun show(
        hue: Double,
        radius: Double,
        lightness: Double,
        space: HarmonyWheelSpace,
        mode: HarmonyMode,
        colours: List<Rgb>,
        animated: Boolean,
        fromTable: Boolean = false,
    ) {
        modelColours = colours
        if (abs(lightness - this.lightness) > 0.0005 || space != this.space) {
            this.lightness = lightness
            this.space = space
        }
        val isFirst = !hasShown
        hasShown = true
        if (mode != this.mode || isFirst) setMode(mode, animated && !isFirst)
        if (!fromTable) {
            if (phase == Phase.Coasting || phase == Phase.Catching) phase = Phase.Resting
            if (phase != Phase.Turning) {
                thetaTarget = theta + remainder(hue - theta, 360.0)
                rhoTarget = radius
                if (!animated || isFirst) {
                    theta = thetaTarget
                    thetaV = 0.0
                    rho = rhoTarget
                    rhoV = 0.0
                    snapBeads()
                    lastNotch = HarmonyTable.notchIndex(theta)
                }
            }
        }
        run()
    }

    /** Bounces the bead at `index` in the model's harmony order. */
    fun bounce(index: Int) {
        val baseIndex = HarmonyTable.slots(mode).second
        val bead = if (index == baseIndex) base else partners.getOrNull(if (index < baseIndex) index else index - 1)
        bead?.liftV = 14.0
        run()
    }

    private fun setMode(new: HarmonyMode, animated: Boolean) {
        mode = new
        val kept = partners.toMutableList()
        partners = HarmonyTable.slots(new).first.mapIndexed { index, (slotOffset, rung) ->
            val bead = kept.removeFirstOrNull() ?: Bead(0.0, 0.0, theta, base.chroma, if (animated) 0.0 else 1.0)
            // nearest turn of offset
            val relative = bead.angle - theta
            val offset = slotOffset + 360 * Math.rint((relative - slotOffset) / 360)
            if (animated) {
                bead.next = offset to rung
                bead.wait = index * HarmonyTable.STAGGER
                bead.isSliding = true
            } else {
                bead.offset = offset
                bead.rung = rung
                bead.next = null
            }
            bead
        }
        if (animated) {
            kept.forEach {
                it.isLeaving = true
                it.next = 0.0 to 0.0
                leaving += it
            }
        }
    }

    private fun snapBeads() {
        for (bead in listOf(base) + partners) {
            bead.next?.let { (offset, rung) ->
                bead.offset = offset
                bead.rung = rung
                bead.next = null
            }
            bead.angle = theta + bead.offset
            bead.chroma = targetChroma(bead)
            bead.angleV = 0.0
            bead.chromaV = 0.0
            bead.presence = 1.0
            bead.presenceV = 0.0
            bead.isSliding = false
        }
    }

    private fun run() {
        figure = "${wrappedDegrees(theta).roundToInt() % 360}°"
        figureRolls = phase == Phase.Resting
        version++
        running++
    }

    private fun spring(s: HarmonySpring, value: Double, velocity: Double, target: Double, dt: Double) =
        s.step(value, velocity, target, dt, reduceMotion)

    /** One frame; false once everything has settled. */
    internal fun tick(rawDt: Double): Boolean {
        val dt = rawDt.coerceIn(0.0, 1.0 / 20)
        step(dt)
        figure = "${wrappedDegrees(theta).roundToInt() % 360}°"
        figureRolls = phase == Phase.Resting
        version++
        return !isAtRest
    }

    private fun step(dt: Double) {
        when (phase) {
            Phase.Turning -> {}
            Phase.Coasting -> {
                theta += spinSpeed * dt
                spinSpeed *= exp(-HarmonyTable.FRICTION * dt)
                if (abs(spinSpeed) < HarmonyTable.CATCH_BELOW) {
                    thetaTarget = HarmonyTable.catchNotch(theta, spinSpeed)
                    thetaV = spinSpeed
                    phase = Phase.Catching
                }
            }
            Phase.Catching -> {
                spring(HarmonyTable.catchSpring, theta, thetaV, thetaTarget, dt).let { (v, s) -> theta = v; thetaV = s }
                if (abs(theta - thetaTarget) < 0.03 && abs(thetaV) < 2) {
                    theta = thetaTarget
                    thetaV = 0.0
                    phase = Phase.Resting
                    haptic(Haptic.Clunk)
                    report()
                }
            }
            Phase.Resting -> spring(HarmonyTable.slide, theta, thetaV, thetaTarget, dt).let { (v, s) -> theta = v; thetaV = s }
        }
        if (phase != Phase.Turning || grabbed == null) {
            spring(HarmonyTable.slide, rho, rhoV, rhoTarget, dt).let { (v, s) -> rho = v; rhoV = s }
        }

        base.angle = theta
        if (mode == HarmonyMode.Monochromatic) {
            spring(HarmonyTable.trail, base.chroma, base.chromaV, targetChroma(base), dt).let { (v, s) -> base.chroma = v; base.chromaV = s }
        } else {
            base.chroma = rho
        }
        stepLift(base, dt)

        val t = now
        for (bead in partners + leaving) {
            bead.next?.let { (offset, rung) ->
                bead.wait -= dt
                if (bead.wait <= 0) {
                    bead.offset = offset
                    bead.rung = rung
                    bead.next = null
                }
            }
            val s = if (bead.isSliding || bead.isLeaving) HarmonyTable.slide else HarmonyTable.trail
            spring(s, bead.angle, bead.angleV, theta + bead.offset, dt).let { (v, sp) -> bead.angle = v; bead.angleV = sp }
            val chromaTarget = if (bead.isLeaving) base.chroma else targetChroma(bead)
            spring(s, bead.chroma, bead.chromaV, chromaTarget, dt).let { (v, sp) -> bead.chroma = v; bead.chromaV = sp }
            spring(HarmonyTable.slide, bead.presence, bead.presenceV, if (bead.isLeaving) 0.0 else 1.0, dt)
                .let { (v, sp) -> bead.presence = v; bead.presenceV = sp }
            stepLift(bead, dt)
            if (bead.isSliding && bead.next == null && abs(bead.angle - theta - bead.offset) < 1 && abs(bead.angleV) < 30) {
                bead.isSliding = false
                if (t - lastClick > 0.03) {
                    haptic(Haptic.Click)
                    lastClick = t
                }
            }
        }
        leaving.removeAll { it.presence < 0.02 && it.wait <= 0 }

        val notch = HarmonyTable.notchIndex(theta)
        if (notch != lastNotch) {
            lastNotch = notch
            if (phase != Phase.Resting && t - lastClick > 0.025) {
                haptic(Haptic.Tick)
                lastClick = t
            }
        }
        if (phase == Phase.Coasting || phase == Phase.Catching) report()
    }

    private fun stepLift(bead: Bead, dt: Double) {
        spring(HarmonyTable.liftSpring, bead.lift, bead.liftV, if (bead === grabbed) 1.0 else 0.0, dt)
            .let { (v, s) -> bead.lift = v; bead.liftV = s }
    }

    private val isAtRest: Boolean
        get() {
            if (phase != Phase.Resting || leaving.isNotEmpty() || abs(theta - thetaTarget) >= 0.01 || abs(thetaV) >= 0.05 ||
                abs(rho - rhoTarget) >= 0.00005 || abs(rhoV) >= 0.0005
            ) return false
            return (listOf(base) + partners).all { bead ->
                bead.next == null && abs(bead.presence - 1) < 0.002 && abs(bead.lift) < 0.002 && abs(bead.liftV) < 0.02 &&
                    abs(bead.angle - theta - bead.offset) < 0.02 && abs(bead.angleV) < 0.05 &&
                    abs(bead.chroma - targetChroma(bead)) < 0.00005 && abs(bead.chromaV) < 0.0005
            }
        }

    private fun report() = onTurn(wrappedDegrees(theta), rho)

    private fun targetChroma(bead: Bead): Double {
        if (mode != HarmonyMode.Monochromatic) return rho
        return HarmonyTable.ladder(rho, bead.next?.second ?: bead.rung, ladderSpacing)
    }

    private val ladderSpacing get() = if (planeRadius > 0) beadRadius * 2.6 / planeRadius.toDouble() else 0.15

    internal fun grabs(point: Offset) = beadAt(point) != null || (point - centre).getDistance() <= ringRadius + 6 * density

    internal var density = 1f

    internal fun begin(at: Offset) {
        val bead = beadAt(at)
        onGrab()
        phase = Phase.Turning
        spinSpeed = 0.0
        grabbed = bead
        lastPointer = pointerAngle(at)
        turned = 0.0
        grabTheta = theta
        thetaTarget = theta
        thetaV = 0.0
        if (bead != null) grabChromaOffset = bead.chroma - chromaAt(at)
        samples.clear()
        samples.addLast(now to theta)
        run()
    }

    internal fun move(to: Offset) {
        // hub angle is noise
        if ((to - centre).getDistance() > 10 * density) {
            val pointer = pointerAngle(to)
            turned += remainder(pointer - lastPointer, 360.0)
            lastPointer = pointer
            theta = grabTheta + turned
            thetaTarget = theta
        }
        if (grabbed != null && mode != HarmonyMode.Monochromatic) {
            val most = space.maxRadius(lightness, wrappedDegrees(theta))
            rho = (chromaAt(to) + grabChromaOffset).coerceIn(0.0, max(most, 0.0))
            rhoTarget = rho
            rhoV = 0.0
        }
        val t = now
        samples.addLast(t to theta)
        while (samples.isNotEmpty() && t - samples.first().first > 0.1) samples.removeFirst()
        report()
        run()
    }

    internal val releaseSpeed: Double
        get() {
            val first = samples.firstOrNull() ?: return 0.0
            val last = samples.last()
            if (now - last.first >= 0.06 || last.first - first.first <= 0.008) return 0.0
            return (last.second - first.second) / (last.first - first.first)
        }

    internal fun end(speed: Double) {
        grabbed = null
        if (abs(speed) > HarmonyTable.COAST_FROM && !reduceMotion) {
            spinSpeed = speed.coerceIn(-HarmonyTable.FASTEST, HarmonyTable.FASTEST)
            phase = Phase.Coasting
        } else {
            phase = Phase.Resting
            thetaTarget = theta
            thetaV = 0.0
            rhoTarget = rho
            report()
        }
        run()
    }

    internal fun tap(at: Offset) {
        val bead = beadAt(at) ?: return
        bead.liftV = 14.0
        haptic(Haptic.Tick)
        run()
        val baseIndex = HarmonyTable.slots(mode).second
        if (bead === base) {
            onTapBead(baseIndex)
        } else {
            val index = partners.indexOf(bead)
            if (index >= 0) onTapBead(if (index < baseIndex) index else index + 1)
        }
    }

    internal fun nudge(direction: Double) {
        thetaTarget = Math.rint(theta / NOTCH) * NOTCH + direction * NOTCH
        phase = Phase.Catching
        run()
    }

    internal val degrees get() = wrappedDegrees(theta).roundToInt() % 360

    private fun beadAt(point: Offset): Bead? = (listOf(base) + partners.reversed()).firstOrNull { bead ->
        bead.presence > 0.5 && (point - position(bead)).getDistance() <= radius(bead) + 14 * density
    }

    /** Degrees clockwise from the top. */
    private fun pointerAngle(point: Offset) =
        wrappedDegrees(Math.toDegrees(atan2((point.x - centre.x).toDouble(), (centre.y - point.y).toDouble())))

    private fun chromaAt(point: Offset) = if (planeRadius > 0) (point - centre).getDistance() / planeRadius.toDouble() else 0.0

    private fun position(bead: Bead): Offset {
        val (x, y) = HarmonyTable.point(bead.angle, bead.chroma * planeRadius, centre.x.toDouble(), centre.y.toDouble())
        return Offset(x.toFloat(), y.toFloat())
    }

    private fun radius(bead: Bead) = (if (bead === base) beadRadius else beadRadius * 0.82f) * (1 + 0.14f * bead.lift.toFloat())

    private fun ladderColour(bead: Bead): Rgb {
        val baseIndex = HarmonyTable.slots(HarmonyMode.Monochromatic).second
        val index = if (bead === base) baseIndex else partners.indexOf(bead).takeIf { it >= 0 }?.let { if (it < baseIndex) it else it + 1 }
        return index?.let { modelColours.getOrNull(it) } ?: space.colour(lightness, bead.chroma, bead.angle)
    }

    internal fun DrawScope.drawTable(ink: Color, card: Color, border: Color, muted: Color, plane: ImageBitmap?) {
        if (planeRadius <= 0) return
        val hairline = 1.dp.toPx()
        drawCircle(card, planeRadius, centre)
        drawCircle(border, planeRadius, centre, style = Stroke(hairline))
        if (plane != null) {
            val side = (ringRadius * 2).roundToInt()
            drawImage(
                plane,
                dstOffset = IntOffset((centre.x - ringRadius).roundToInt(), (centre.y - ringRadius).roundToInt()),
                dstSize = IntSize(side, side),
                filterQuality = FilterQuality.Low,
            )
        }
        val ringInner = planeRadius + (ringRadius - planeRadius) * 0.3
        val cx = centre.x.toDouble()
        val cy = centre.y.toDouble()
        fun at(hue: Double, distance: Double) = HarmonyTable.point(hue, distance, cx, cy).let { Offset(it.first.toFloat(), it.second.toFloat()) }
        for (index in 0 until (360 / NOTCH).toInt()) {
            val hue = index * NOTCH
            val from = if (index % 2 == 0) ringInner else (ringInner + ringRadius) / 2
            drawLine(muted, at(hue, from), at(hue, ringRadius + 1.0), 2.dp.toPx())
        }

        val placed = listOf(base) + partners
        if (mode != HarmonyMode.Monochromatic && placed.size >= 3) {
            val corners = placed.sortedBy { wrappedDegrees(it.angle) }.map(::position)
            val outline = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                corners.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(outline, ink.copy(alpha = 0.06f))
            drawPath(outline, ink.copy(alpha = 0.3f), style = Stroke(hairline, join = StrokeJoin.Round))
        }
        for (bead in placed + leaving) {
            if (bead.presence > 0.05) drawLine(ink.copy(alpha = 0.45f), centre, position(bead), 1.5.dp.toPx())
        }
        drawCircle(ink, 4.dp.toPx(), centre)

        // base on top
        for (bead in partners + leaving + base) {
            val colour = if (mode == HarmonyMode.Monochromatic) {
                ladderColour(bead)
            } else {
                space.colour(lightness, max(bead.chroma, 0.0), wrappedDegrees(bead.angle))
            }
            drawBead(position(bead), radius(bead) * bead.presence.coerceIn(0.0, 1.2).toFloat(), colour.clamped.toCanonColor(), bead === base, ink)
        }
    }

    /** A flat token: the colour inside a white ring between two dark hairlines; the base also carries an outer ring. */
    private fun DrawScope.drawBead(at: Offset, radius: Float, colour: Color, ringed: Boolean, ink: Color) {
        if (radius <= 0) return
        val hairline = 1.dp.toPx()
        val width = 3.dp.toPx()
        val dark = Color.Black.copy(alpha = 0.55f)
        fun circle(inset: Float, paint: Color, stroke: Float?) {
            if (radius <= inset) return
            if (stroke == null) drawCircle(paint, radius - inset, at) else drawCircle(paint, radius - inset, at, style = Stroke(stroke))
        }
        circle(hairline + width, colour, null)
        circle(hairline + width + hairline / 2, dark, hairline)
        circle(hairline + width / 2, Color.White, width)
        circle(hairline / 2, dark, hairline)
        if (ringed) drawCircle(ink, radius + 4.dp.toPx(), at, style = Stroke(2.dp.toPx()))
    }
}

private fun planeBitmap(space: HarmonyWheelSpace, lightness: Double, size: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(HarmonyTable.plane(space, lightness, size), 0, size, 0, 0, size, size)
    return bitmap.asImageBitmap()
}

@Composable
fun HarmonyTurntable(state: HarmonyTurntableState, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val context = LocalContext.current
    val slop = LocalViewConfiguration.current.touchSlop
    val top = LocalCanonCanvas.current.top
    var plane by remember { mutableStateOf<ImageBitmap?>(null) }

    DisposableEffect(state, view) {
        state.reduceMotion = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        state.haptic = { kind ->
            view.performHapticFeedback(
                when (kind) {
                    Haptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
                    Haptic.Click -> HapticFeedbackConstants.KEYBOARD_TAP
                    Haptic.Clunk -> HapticFeedbackConstants.CONFIRM
                },
            )
        }
        onDispose { state.haptic = {} }
    }
    LaunchedEffect(state) {
        var shownSpace: HarmonyWheelSpace? = null
        snapshotFlow { state.space to state.lightness }.collectLatest { (space, lightness) ->
            // ponytail: a lightness change renders a draft first; iOS drafts only while the slider is dragged
            if (shownSpace == space) {
                plane = withContext(Dispatchers.Default) { planeBitmap(space, lightness, HarmonyTable.DRAFT_SIZE) }
                delay(150)
            }
            plane = withContext(Dispatchers.Default) { planeBitmap(space, lightness, HarmonyTable.RENDER_SIZE) }
            shownSpace = space
        }
    }
    LaunchedEffect(state) {
        while (true) {
            snapshotFlow { state.running }.first { it > 0 }
            var last = withFrameNanos { it }
            var moving: Boolean
            do {
                state.running = 0
                moving = withFrameNanos { now ->
                    state.tick((now - last) / 1e9).also { last = now }
                }
            } while (moving || state.running > 0)
        }
    }

    Box(
        modifier
            .clipToBounds()
            .semantics {
                stateDescription = "${state.degrees}°"
                progressBarRangeInfo = ProgressBarRangeInfo(state.degrees.toFloat(), 0f..360f, steps = (360 / NOTCH).toInt() - 1)
                setProgress { target ->
                    state.nudge(if (target >= state.degrees) 1.0 else -1.0)
                    true
                }
            }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!state.grabs(down.position)) return@awaitEachGesture
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (dragging) state.end(state.releaseSpeed) else state.tap(down.position)
                            dragging = false
                            break
                        }
                        if (!dragging) {
                            if (change.isConsumed) break
                            if ((change.position - down.position).getDistance() > slop) {
                                dragging = true
                                state.begin(down.position)
                            }
                        }
                        if (dragging) {
                            change.consume()
                            state.move(change.position)
                        }
                    }
                    if (dragging) state.end(0.0)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            state.version
            state.density = density
            val inset = 10.dp.toPx()
            val areaTop = top.toPx() + inset
            val w = size.width - inset * 2
            val h = size.height - areaTop - inset
            if (w > 40.dp.toPx() && h > 40.dp.toPx()) {
                state.centre = Offset(inset + w / 2, areaTop + h / 2)
                state.ringRadius = min(w, h) / 2
                state.planeRadius = state.ringRadius * HarmonyTable.PLANE_SHARE.toFloat()
                state.beadRadius = (state.planeRadius * 0.095f).coerceIn(13.dp.toPx(), 24.dp.toPx())
                with(state) { drawTable(c.foreground, c.card, c.border, c.muted, plane) }
            }
        }
        CanonFigure(
            state.figure,
            Modifier.offset(14.dp, top + 10.dp),
            size = 26.sp,
            colour = c.foreground,
            halo = c.muted,
            animated = state.figureRolls,
        )
    }
}
