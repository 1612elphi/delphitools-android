package com.delphi.delphitools.canon

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.SolidSpace
import com.delphi.delphitools.colour.clamp01
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.labToLinear
import com.delphi.delphitools.colour.oklabToLinear
import com.delphi.delphitools.colour.takesBlackInk
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What a [CanonColourSolid] shows: the space, the pick and how it is drawn. `point` holds
 * the picked colour's channels in `space`'s units (see [SolidSpace]); a new `space`
 * converts it. Picks on the solid change `point`, so readers of `point` and `colour`
 * follow them.
 */
@Stable
class CanonColourSolidState(space: SolidSpace = SolidSpace.HSL, colour: Rgb = Rgb(0.12, 0.4, 0.15)) {
    private var spaceValue by mutableStateOf(space)
    private var pointValue by mutableStateOf(space.pointFor(colour))
    private var sliceValue by mutableStateOf<Int?>(null)
    private var orthoValue by mutableStateOf(false)
    private var wholeValue by mutableStateOf(false)

    var space: SolidSpace
        get() = spaceValue
        set(value) = changeSpace(value)

    val point: DoubleArray get() = pointValue.copyOf()
    val colour: Rgb get() = spaceValue.colourAt(pointValue)

    /**
     * Index into `point` of the channel to slice along, 0...2, or null. The solid draws
     * faint, the slice through the pick opaque, and a tap or a drag picks on the slice.
     */
    var sliceAxis: Int?
        get() = sliceValue
        set(value) = changeSlice(value)

    /** Turns the slice to face the viewer and flattens the view; a chroma or saturation shell unrolls. */
    var isOrtho: Boolean
        get() = orthoValue
        set(value) = changeOrtho(value)

    /** Fades the solid around a slice to opaque, while CMYK's black changes. */
    var showsWhole: Boolean
        get() = wholeValue
        set(value) = changeWhole(value)

    /** Without a slice, cuts away the part between the pick and the viewer. */
    var cutsAway = true
        set(value) {
            field = value
            redraw()
        }

    /** Further colours, drawn as small squares, hollow where the solid hides them. */
    var marks: List<Rgb> = emptyList()
        set(value) {
            field = value
            markPoints = value.map { spaceValue.pointFor(it) }
            redraw()
        }

    internal var version by mutableIntStateOf(0)
    internal var running by mutableIntStateOf(0)
    internal var animates = true
    internal var width = 0f
    internal var height = 0f
    internal var density = 1f

    private var shown = pointValue.copyOf()
    private var vision = IDENTITY
    private var shownVision = IDENTITY
    private var markPoints: List<DoubleArray> = emptyList()
    private var yaw = 0.62
    private var pitch = 0.42
    private val links = mutableSetOf<Link>()
    private var pointFrom = DoubleArray(4)
    private var pointProgress = 1.0
    private var visionFrom = IDENTITY
    private var visionProgress = 1.0
    private var oldSlice: Int? = null
    private var sliceProgress = 1.0
    private var unroll = 0.0
    private var unrollTarget = 0.0
    private var afterUnroll: (() -> Unit)? = null
    private var distance = FREE_DISTANCE
    private var fitRadius = radius(space)
    private var lookAt = DoubleArray(3)
    private var freeAim: SolidAim? = null
    private var aimFrom: SolidAim? = null
    private var aimTo: SolidAim? = null
    private var aimProgress = 1.0
    private var ghost = GHOST_OPACITY
    private var spinX = 0.0
    private var spinY = 0.0

    /** Animated, the drawing eases to `next`, as a slider's fill does after a tap on its track. */
    fun setPoint(next: DoubleArray, animated: Boolean = false) {
        val padded = DoubleArray(4) { next.getOrElse(it) { 0.0 } }
        if (!animated || !animates) {
            stop(Link.Point)
            assignPoint(padded)
            return
        }
        val from = shown.copyOf()
        pointFrom = from
        pointProgress = 0.0
        assignPoint(padded)
        shown = from.copyOf()
        start(Link.Point)
    }

    /** Every drawn colour goes through `matrix` (nine values, rows on r, g, b), then clamps. */
    fun setVision(matrix: DoubleArray, animated: Boolean = true) {
        if (matrix.contentEquals(vision)) return
        vision = matrix.copyOf()
        if (!animated || !animates) {
            stop(Link.Vision)
            shownVision = vision
            redraw()
            return
        }
        visionFrom = shownVision
        visionProgress = 0.0
        start(Link.Vision)
    }

    private enum class Link { Point, Vision, Slice, Unroll, Aim, Spin, Fade }

    private fun redraw() {
        version++
    }

    private fun start(link: Link) {
        links += link
        running = links.size
        redraw()
    }

    private fun stop(link: Link) {
        links -= link
        running = links.size
    }

    private fun changeSpace(space: SolidSpace) {
        if (space == spaceValue) return
        val old = spaceValue
        spaceValue = space
        stopUnroll()
        // an ease mixes units
        stop(Link.Point)
        assignPoint(space.pointFor(old.colourAt(pointValue)))
        markPoints = marks.map { space.pointFor(it) }
        if (!orthoValue) aimTo?.fit = radius(space)
        freeAim?.fit = radius(space)
        if (orthoValue) reaim() else fitRadius = radius(space)
        redraw()
    }

    private fun assignPoint(next: DoubleArray) {
        if (next.contentEquals(pointValue)) return
        pointValue = next.copyOf()
        shown = next.copyOf()
        followLeaf()
        redraw()
    }

    private fun changeSlice(axis: Int?) {
        if (axis == sliceValue) return
        require(axis == null || axis in 0..2) { "slice axes are 0...2" }
        val previous = sliceValue
        sliceValue = axis
        if (previous != null && axis != null && animates) {
            oldSlice = previous
            sliceProgress = 0.0
            start(Link.Slice)
        }
        if (orthoValue) setUnroll(0.0) { reaim() }
        redraw()
    }

    private fun changeOrtho(ortho: Boolean) {
        if (ortho == orthoValue) return
        orthoValue = ortho
        if (ortho) {
            freeAim = currentAim
            reaim()
            fadeGhost()
        } else {
            setUnroll(0.0) {
                reaim()
                fadeGhost()
            }
        }
    }

    private fun changeWhole(whole: Boolean) {
        if (whole == wholeValue) return
        wholeValue = whole
        fadeGhost()
        // frame the largest cube
        if (orthoValue) reaim()
        redraw()
    }

    /** Advances every running animation by `dt` seconds. */
    internal fun tick(dt: Double) {
        for (link in links.toList()) if (link in links) step(link, dt)
    }

    private fun step(link: Link, dt: Double) {
        when (link) {
            Link.Point -> {
                pointProgress = min(1.0, pointProgress + dt / POINT_EASE)
                val eased = 1 - (1 - pointProgress).pow(3)
                val delta = DoubleArray(4) { pointValue[it] - pointFrom[it] }
                // hue: shortest way
                if (!spaceValue.isCartesian) delta[0] -= roundAway(delta[0])
                shown = DoubleArray(4) { pointFrom[it] + delta[it] * eased }
                followLeaf()
                if (pointProgress >= 1) {
                    shown = pointValue.copyOf()
                    stop(Link.Point)
                }
            }
            Link.Vision -> {
                visionProgress = min(1.0, visionProgress + dt / VISION_EASE)
                val e = smooth(visionProgress)
                shownVision = DoubleArray(9) { visionFrom[it] + (vision[it] - visionFrom[it]) * e }
                if (visionProgress >= 1) {
                    shownVision = vision
                    stop(Link.Vision)
                }
            }
            Link.Slice -> {
                sliceProgress = min(1.0, sliceProgress + dt / SLICE_TURN)
                if (sliceProgress >= 1) {
                    oldSlice = null
                    stop(Link.Slice)
                }
            }
            Link.Unroll -> {
                val stride = dt / UNROLL_TIME
                unroll = if (unroll < unrollTarget) min(unroll + stride, unrollTarget) else max(unroll - stride, unrollTarget)
                if (unroll == unrollTarget) finishUnroll()
            }
            Link.Aim -> {
                val from = aimFrom
                val to = aimTo
                if (from == null || to == null) return stop(Link.Aim)
                aimProgress = min(1.0, aimProgress + dt / ORTHO_TURN)
                val e = smooth(aimProgress)
                fun mix(a: Double, b: Double) = a + (b - a) * e
                apply(
                    SolidAim(
                        mix(from.yaw, to.yaw),
                        mix(from.pitch, to.pitch),
                        // log mix: even dolly
                        exp(mix(ln(from.distance), ln(to.distance))),
                        mix(from.fit, to.fit),
                        DoubleArray(3) { mix(from.lookAt[it], to.lookAt[it]) },
                    ),
                )
                if (aimProgress >= 1) {
                    stop(Link.Aim)
                    aimTo = null
                    if (wantsUnroll) setUnroll(1.0)
                }
            }
            Link.Spin -> {
                turn(spinX * dt, spinY * dt)
                val keep = FLING_KEEP.pow(dt)
                spinX *= keep
                spinY *= keep
                if (hypot(spinX, spinY) < 0.05) stop(Link.Spin)
            }
            Link.Fade -> {
                val target = ghostTarget
                val stride = dt / WHOLE_FADE
                ghost = if (ghost < target) min(ghost + stride, target) else max(ghost - stride, target)
                if (ghost == target) stop(Link.Fade)
            }
        }
        redraw()
    }

    private val wantsUnroll get() = orthoValue && sliceValue == 1 && !spaceValue.isCartesian
    private val flatness get() = smooth(unroll)

    /** The unrolling shell, null while rolled; faces the hue the camera looks at. */
    private val sheet get() = if (flatness > 0) SolidSheet(shown[1], PI / 2 - yaw, 1 - flatness) else null

    private fun setUnroll(target: Double, done: (() -> Unit)? = null) {
        unrollTarget = target
        afterUnroll = done
        if (unroll == target || !animates) {
            unroll = target
            finishUnroll()
            return
        }
        start(Link.Unroll)
    }

    private fun finishUnroll() {
        stop(Link.Unroll)
        redraw()
        val done = afterUnroll
        afterUnroll = null
        done?.invoke()
    }

    private fun stopUnroll() {
        unrollTarget = 0.0
        unroll = 0.0
        finishUnroll()
    }

    private val currentAim get() = SolidAim(yaw, pitch, distance, fitRadius, lookAt.copyOf())

    private fun reaim() {
        val target: SolidAim
        val axis = sliceValue
        if (orthoValue && axis != null) {
            target = orthoAim(axis)
        } else {
            target = (freeAim ?: currentAim).copy(distance = FREE_DISTANCE, fit = radius(spaceValue), lookAt = DoubleArray(3))
            if (!orthoValue) freeAim = null
        }
        target.yaw += 2 * PI * roundAway((yaw - target.yaw) / (2 * PI))
        stop(Link.Spin)
        if (!animates) {
            apply(target)
            if (wantsUnroll) setUnroll(1.0)
            return
        }
        aimFrom = currentAim
        aimTo = target
        aimProgress = 0.0
        start(Link.Aim)
    }

    /**
     * Faces the slice through the pick from the side the view is already on, so the turn
     * never passes edge on: a plane head on, the hue leaf square to it, the height disc
     * from above or below, the shell from outside at the pick's hue.
     */
    private fun orthoAim(axis: Int): SolidAim {
        // outer cube during black
        val size = if (wholeValue) 1.0 else geometry().size
        fun aim(yaws: List<Double>, pitch: Double, fit: Double, lookAt: DoubleArray = DoubleArray(3)): SolidAim {
            val nearest = yaws.minBy { abs(remainder(it - yaw, 2 * PI)) }
            return SolidAim(nearest, pitch, ORTHO_DISTANCE, fit, lookAt)
        }
        val overhead = if (pitch >= 0) PI / 2 else -PI / 2
        if (spaceValue.isCartesian) {
            return when (axis) {
                0 -> aim(listOf(-PI / 2, PI / 2), 0.0, size)
                1 -> aim(listOf(yaw), overhead, size)
                else -> aim(listOf(0.0, PI), 0.0, size)
            }
        }
        val angle = shown[0] * 2 * PI
        return when (axis) {
            0 -> aim(listOf(-angle, PI - angle), 0.0, 1.0, doubleArrayOf(cos(angle) * 0.5, 0.0, sin(angle) * 0.5))
            1 -> aim(listOf(PI / 2 - angle), 0.0, 1.0)
            else -> aim(listOf(yaw), overhead, 1.0)
        }
    }

    /** In ortho a hue leaf turns with the hue, so it keeps facing the viewer. */
    private fun followLeaf() {
        if (!orthoValue || sliceValue != 0 || spaceValue.isCartesian) return
        val target = orthoAim(0)
        if (Link.Aim in links) aimTo = target else apply(target)
    }

    private fun apply(aim: SolidAim) {
        yaw = aim.yaw
        pitch = aim.pitch
        distance = aim.distance
        fitRadius = aim.fit
        lookAt = aim.lookAt
        redraw()
    }

    private val ghostTarget get() = if (wholeValue) 1.0 else if (orthoValue) 0.0 else GHOST_OPACITY

    private fun fadeGhost() {
        if (!animates) {
            ghost = ghostTarget
            redraw()
            return
        }
        start(Link.Fade)
    }

    internal fun turn(dx: Double, dy: Double) {
        yaw -= dx
        pitch = (pitch + dy).coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
        redraw()
    }

    /** A fling, in radians per second. */
    internal fun startSpin(vx: Double, vy: Double) {
        if (hypot(vx, vy) <= 0.3 || !animates) return
        spinX = vx
        spinY = vy
        start(Link.Spin)
    }

    internal fun stopSpin() = stop(Link.Spin)

    /** Drags turn the view; in ortho with a slice they move the pick instead. */
    internal val dragsTurn get() = !(orthoValue && sliceValue != null)

    private fun camera(w: Float = width, h: Float = height) =
        solidCamera(currentAim, w.toDouble(), h.toDouble(), flatness, shown[1])

    private fun cut(): DoubleArray {
        if (!cutsAway || sliceValue != null) return DoubleArray(4)
        val position = camera().position
        fun side(v: Double) = if (v >= 0) 1.0 else -1.0
        if (spaceValue.isCartesian) return doubleArrayOf(side(position[0]), side(position[1]), side(position[2]), 1.0)
        var towards = atan2(position[2], position[0]) / (2 * PI) - shown[0]
        towards -= roundAway(towards)
        return doubleArrayOf(side(towards), 0.0, side(position[1]), 1.0)
    }

    private fun geometry() = SolidGeometry(
        spaceValue, shown.copyOf(3), cut(), sliceValue, if (spaceValue == SolidSpace.CMYK) shown[3] else 0.0,
    )

    private fun hit(at: Offset): DoubleArray? {
        val cam = camera()
        val r = ray(cam, width, height, at)
        return march(cam.position, r, geometry())?.let { along(cam.position, r, it) }
    }

    /** Where a pointer meets the slice, or the solid when nothing is sliced. */
    private fun pickHit(at: Offset): DoubleArray? {
        if (sliceValue == null) return hit(at)
        val cam = camera()
        val r = ray(cam, width, height, at)
        val geometry = geometry()
        sheet?.let { s -> return s.shellPoints(cam.position, r).firstOrNull { geometry.shape(it) <= 1e-4 } }
        return geometry.sliceHit(cam.position, r)?.let { along(cam.position, r, it) }
    }

    internal fun nearPick(at: Offset): Boolean {
        val centre = place(shown) ?: return false
        return hypot(at.x - centre.at.x, at.y - centre.at.y) < PICK_REACH * density
    }

    /** Whether a drag from `at` begins: on the pick, or on the solid. */
    internal fun grabs(at: Offset) = nearPick(at) || (if (sheet != null) pickHit(at) else hit(at)) != null

    /** Picks the colour under `at`; false when nothing is there. */
    internal fun pick(at: Offset): Boolean {
        val p = pickHit(at) ?: return false
        var channels = geometry().channels(p)
        if (spaceValue.isCartesian) channels = DoubleArray(3) { clamp01(channels[it]) }
        stop(Link.Point)
        val next = doubleArrayOf(channels[0], channels[1], channels[2], pointValue[3])
        sliceValue?.let { next[it] = pointValue[it] }
        assignPoint(next)
        return true
    }

    /** `colour` as the vision matrix draws it. */
    private fun seen(colour: Rgb): Rgb {
        val c = colour.clamped
        val m = shownVision
        return Rgb(
            clamp01(m[0] * c.r + m[1] * c.g + m[2] * c.b),
            clamp01(m[3] * c.r + m[4] * c.g + m[5] * c.b),
            clamp01(m[6] * c.r + m[7] * c.g + m[8] * c.b),
        )
    }

    private fun place(channels: DoubleArray, cam: SolidCamera = camera(), geometry: SolidGeometry = geometry()): Placed? {
        sheet?.let { s ->
            val (point, far) = s.place(remainder(channels[0] * 2 * PI - s.facing, 2 * PI), channels[2] * 2 - 1)
            return project(cam, width, height, point)?.let { Placed(it, far) }
        }
        val box = channels.copyOf(3)
        val p = SolidGeometry(spaceValue, box, DoubleArray(4), null, if (spaceValue == SolidSpace.CMYK) channels[3] else 0.0).display(box)
        val at = project(cam, width, height, p) ?: return null
        val toward = sub(p, cam.position)
        val r = normalize(toward)
        val front = if (sliceValue == null || wholeValue) march(cam.position, r, geometry) else geometry.sliceHit(cam.position, r)
        return Placed(at, front != null && front < length(toward) - 0.08)
    }

    /** Where a marker lands, and whether the solid or the far side of a sheet hides it. */
    internal class Placed(val at: Offset, val hidden: Boolean)

    internal class Overlays(
        val pick: Placed?,
        val pickColour: Rgb,
        val marks: List<Pair<Placed?, Rgb>>,
        val labels: List<Pair<String, Offset?>>,
        val labelsHidden: Boolean,
        val wire: List<Pair<Offset, Offset>>,
    )

    internal fun overlays(): Overlays {
        val cam = camera()
        val geometry = geometry()
        val wire = if (spaceValue != SolidSpace.CMYK) emptyList() else tesseractEdges.mapNotNull { (a, b) ->
            val from = project(cam, width, height, a) ?: return@mapNotNull null
            val to = project(cam, width, height, b) ?: return@mapNotNull null
            from to to
        }
        return Overlays(
            pick = place(shown, cam, geometry),
            pickColour = seen(spaceValue.colourAt(shown)),
            marks = marks.indices.map { place(markPoints[it], cam, geometry) to seen(marks[it]) },
            labels = axisAnchors(spaceValue, geometry).map { (letter, anchor) -> letter to project(cam, width, height, anchor) },
            labelsHidden = sheet != null,
            wire = wire,
        )
    }

    /** The 13 vec4 slots of the fragment shader's `u`, for a drawable of `w` by `h` px. */
    internal fun uniforms(w: Int, h: Int): FloatArray {
        val cam = camera(w.toFloat(), h.toFloat())
        val geometry = geometry()
        val (reach, oldReach) = sliceReaches(geometry)
        val v = shownVision
        val centre = geometry.display(geometry.pick)
        return doubleArrayOf(
            cam.position[0], cam.position[1], cam.position[2], 0.0,
            cam.right[0], cam.right[1], cam.right[2], cam.tanX,
            cam.up[0], cam.up[1], cam.up[2], cam.tanY,
            cam.forward[0], cam.forward[1], cam.forward[2], 0.0,
            geometry.pick[0], geometry.pick[1], geometry.pick[2], geometry.black,
            geometry.cut[0], geometry.cut[1], geometry.cut[2], geometry.cut[3],
            spaceValue.ordinal.let { SHADER_IDS[it] }.toDouble(), SHADING, w.toDouble(), h.toDouble(),
            (sliceValue ?: -1).toDouble(), ghost, geometry.size, flatness,
            (oldSlice ?: -1).toDouble(), reach, oldReach, PI / 2 - yaw,
            centre[0], centre[1], centre[2], 0.0,
            v[0], v[1], v[2], 0.0,
            v[3], v[4], v[5], 0.0,
            v[6], v[7], v[8], 0.0,
        ).let { values -> FloatArray(values.size) { values[it].toFloat() } }
    }

    /** How far the new slice reaches from the pick, and the old one, while they swap. */
    private fun sliceReaches(geometry: SolidGeometry): Pair<Double, Double> {
        val old = oldSlice
        val axis = sliceValue
        if (old == null || axis == null) return 1e6 to 0.0
        val eased = smooth(sliceProgress)
        return extent(axis, geometry) * eased to extent(old, geometry) * (1 - eased)
    }

    /** Distance from the pick to the slice's farthest point, so both slices of a swap change over its whole length. */
    private fun extent(axis: Int, geometry: SolidGeometry): Double {
        val reach = geometry.display(geometry.pick).map { geometry.size + abs(it) }.toDoubleArray()
        if (spaceValue.isCartesian) reach[axis] = 0.0
        return length(reach)
    }

    internal fun adjust(axis: Int, value: Double) {
        val next = pointValue.copyOf()
        next[axis] = value
        setPoint(next, animated = true)
    }

    companion object {
        /** No vision change. */
        val IDENTITY = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    }
}

/**
 * A colour space as a solid: drag to turn it, tap a face to pick the colour there, drag
 * from the pick to move it over the faces. CMYK has four channels: black is depth, and the
 * CMY cube shrinks inside a wireframe of black's range.
 */
@Composable
fun CanonColourSolid(
    state: CanonColourSolidState,
    modifier: Modifier = Modifier,
    closestName: ((Rgb) -> String)? = colourNamesOrNull()?.let { names -> names::closest },
) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val renderer = remember { ColourSolidRenderer() }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val wire by animateFloatAsState(if (state.showsWhole) 1f else 0f, tween(200))
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    LaunchedEffect(state) {
        state.density = density
        state.animates = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    LaunchedEffect(state, renderer) {
        snapshotFlow {
            state.version
            val scale = min(1f, RENDER_SIDE / max(state.width, state.height))
            val w = (state.width * scale).roundToInt()
            val h = (state.height * scale).roundToInt()
            if (w > 0 && h > 0) Triple(state.uniforms(w, h), w, h) else null
        }.filterNotNull().conflate().collect { (uniforms, w, h) ->
            withContext(renderer.dispatcher) { renderer.draw(uniforms, w, h) }?.let { image = it.asImageBitmap() }
        }
    }
    LaunchedEffect(state) {
        while (true) {
            snapshotFlow { state.running }.first { it > 0 }
            var last = withFrameNanos { it }
            while (state.running > 0) {
                withFrameNanos { now ->
                    state.tick((now - last) / 1e9)
                    last = now
                }
            }
        }
    }
    val colour = state.colour
    val axis = state.sliceAxis ?: 0
    Box(
        modifier
            .clipToBounds()
            .onSizeChanged {
                state.width = it.width.toFloat()
                state.height = it.height.toFloat()
                state.version++
            }
            .pointerInput(state) { solidGestures(state, density) }
            .semantics {
                contentDescription = state.space.title
                stateDescription = closestName?.let { "${it(colour)}, ${colour.hex}" } ?: colour.hex
                progressBarRangeInfo = ProgressBarRangeInfo(state.point[axis].toFloat().coerceIn(0f, 1f), 0f..1f)
                setProgress { value ->
                    state.adjust(axis, value.toDouble())
                    true
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            state.version
            image?.let {
                drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Low)
            }
            val overlays = state.overlays()
            if (wire > 0f) {
                val ink = c.foreground.copy(alpha = 0.3f * wire)
                overlays.wire.forEach { (a, b) -> drawLine(ink, a, b, 1.dp.toPx()) }
            }
            if (!overlays.labelsHidden) {
                overlays.labels.forEachIndexed { index, (letter, at) ->
                    if (at == null) return@forEachIndexed
                    // k fades with the wireframe
                    val alpha = if (index == 3) wire else 1f
                    if (alpha <= 0f) return@forEachIndexed
                    val text = measurer.measure(letter, CanonText.captionSemibold.copy(color = c.mutedForeground.copy(alpha = alpha)))
                    drawText(text, topLeft = at - Offset(text.size.width / 2f, text.size.height / 2f))
                }
            }
            overlays.marks.forEach { (at, mark) -> if (at != null) marker(at, mark, 10.dp.toPx(), 2.dp.toPx(), c.foreground) }
            overlays.pick?.let { marker(it, overlays.pickColour, 18.dp.toPx(), 3.dp.toPx(), c.foreground) }
        }
    }
}

private fun DrawScope.marker(placed: CanonColourSolidState.Placed, colour: Rgb, side: Float, border: Float, foreground: Color) {
    val topLeft = placed.at - Offset(side / 2, side / 2)
    val box = Size(side, side)
    val inset = Offset(border / 2, border / 2)
    val inner = Size(side - border, side - border)
    if (placed.hidden) {
        drawRect(foreground.copy(alpha = 0.5f), topLeft + inset, inner, style = Stroke(border))
        return
    }
    drawRect(Color.Black.copy(alpha = 0.25f), topLeft - Offset(1f, 1f), Size(side + 2, side + 2), style = Stroke(1f))
    drawRect(colour.toCanonColor(), topLeft, box)
    drawRect(if (colour.takesBlackInk) Color.Black else Color.White, topLeft + inset, inner, style = Stroke(border))
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.solidGestures(state: CanonColourSolidState, density: Float) {
    val slop = DRAG_SLOP * density
    val rate = TURN_RATE / density
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = down.position
        val grabbed = state.grabs(start)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, start)
        var active = false
        var picks = false
        var last = start
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (active && !picks) {
                    val velocity = tracker.calculateVelocity()
                    state.startSpin(velocity.x * rate, velocity.y * rate)
                } else if (!active && (change.position - start).getDistance() < slop && !change.isConsumed) {
                    state.pick(change.position)
                }
                break
            }
            tracker.addPosition(change.uptimeMillis, change.position)
            if (!grabbed) {
                if ((change.position - start).getDistance() >= slop) break
                continue
            }
            change.consume()
            if (!active) {
                if ((change.position - start).getDistance() < slop) continue
                active = true
                state.stopSpin()
                picks = state.nearPick(change.position) || !state.dragsTurn
            }
            if (picks) {
                // misses keep last pick
                state.pick(change.position)
            } else {
                state.turn(((change.position.x - last.x) * rate).toDouble(), ((change.position.y - last.y) * rate).toDouble())
            }
            last = change.position
        }
    }
}

// SolidState in delphicanon src/lib/colour-solid/solid.ts; timings in seconds, rates in radians
private const val POINT_EASE = 0.25
private const val VISION_EASE = 0.35
private const val SLICE_TURN = 0.4
private const val UNROLL_TIME = 0.6
private const val ORTHO_TURN = 0.55
private const val WHOLE_FADE = 0.2
private const val PITCH_LIMIT = 1.45
private const val TURN_RATE = 0.009
private const val FLING_KEEP = 0.02
private const val FREE_DISTANCE = 6.0
private const val ORTHO_DISTANCE = 80.0
private const val SHADING = 0.16
private const val GHOST_OPACITY = 0.16
private const val PICK_REACH = 26f
private const val DRAG_SLOP = 10f
private const val RENDER_SIDE = 720f

/** Share of a turn the cut takes out of a cylinder. */
private const val WEDGE = 0.25
private const val STEPS = 128
private const val NEAR_SURFACE = 0.04
private const val SUBSTEPS = 8

/** Half-size of the CMYK box at 100 % black; 1 at 0 %. */
private const val INNER_SIZE = 0.36

/** The shader's space numbers, by SolidSpace ordinal. */
private val SHADER_IDS = intArrayOf(0, 1, 2, 3, 5, 4, 6, 7)

/** Largest distance of the solid from its centre. */
private fun radius(space: SolidSpace) = when (space) {
    SolidSpace.RGB, SolidSpace.HSL, SolidSpace.CMYK -> sqrt(3.0)
    SolidSpace.HSB -> sqrt(2.0)
    else -> 1.25
}

private fun add(a: DoubleArray, b: DoubleArray) = DoubleArray(3) { a[it] + b[it] }
private fun sub(a: DoubleArray, b: DoubleArray) = DoubleArray(3) { a[it] - b[it] }
private fun scale(a: DoubleArray, s: Double) = DoubleArray(3) { a[it] * s }
private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
private fun cross(a: DoubleArray, b: DoubleArray) =
    doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
private fun length(a: DoubleArray) = sqrt(dot(a, a))
private fun normalize(a: DoubleArray) = scale(a, 1 / length(a))
private fun along(o: DoubleArray, d: DoubleArray, t: Double) = add(o, scale(d, t))
private fun smooth(t: Double) = t * t * (3 - 2 * t)

/** As Swift's `rounded()`: half away from zero. */
private fun roundAway(x: Double) = sign(x) * floor(abs(x) + 0.5)

/** As Swift's `remainder(_:_:)`: the result lies in -y/2...y/2. */
private fun remainder(x: Double, y: Double) = x - y * roundAway(x / y)

/** Linear sRGB at a point of the OKLab or CIELAB box, unclamped, as the shader's `linearAt`. */
private fun linearAt(p: DoubleArray, space: SolidSpace) =
    if (space == SolidSpace.OKLCH || space == SolidSpace.OKLAB) {
        oklabToLinear((p[1] + 1) / 2, p[0] * 0.33, p[2] * 0.33)
    } else {
        labToLinear((p[1] + 1) * 50, p[0] * 110, p[2] * 110)
    }

/** The solid in its box, x y z each -1...1, y up. Mirrors the fragment shader: change both together. */
private class SolidGeometry(
    val space: SolidSpace,
    val pick: DoubleArray,
    val cut: DoubleArray,
    val slice: Int?,
    val black: Double,
) {
    val size get() = 1 - black * (1 - INNER_SIZE)

    fun channels(p: DoubleArray): DoubleArray {
        if (space.isCartesian) return DoubleArray(3) { (p[it] / size + 1) / 2 }
        val hue = atan2(p[2], p[0]) / (2 * PI)
        return doubleArrayOf(if (hue < 0) hue + 1 else hue, hypot(p[0], p[2]), (p[1] + 1) / 2)
    }

    fun display(channels: DoubleArray): DoubleArray {
        if (space.isCartesian) return DoubleArray(3) { (channels[it] * 2 - 1) * size }
        val angle = channels[0] * 2 * PI
        return doubleArrayOf(channels[1] * cos(angle), channels[2] * 2 - 1, channels[1] * sin(angle))
    }

    /** Negative inside. */
    fun shape(p: DoubleArray): Double {
        val box = max(abs(p[0]), max(abs(p[1]), abs(p[2]))) - size
        if (space == SolidSpace.RGB || space == SolidSpace.HSL || space == SolidSpace.CMYK) return box
        val cylinder = max(hypot(p[0], p[2]) - 1, abs(p[1]) - 1)
        if (space == SolidSpace.HSB) return cylinder
        val c = linearAt(p, space)
        return max(box, max(c.max() - 1, -c.min()))
    }

    /** Positive inside the cut. */
    fun cutAway(p: DoubleArray): Double {
        val c = channels(p)
        if (space.isCartesian) return (0..2).minOf { (c[it] - pick[it]) * cut[it] }
        var turn = if (cut[0] > 0) c[0] - pick[0] else pick[0] - c[0]
        turn -= floor(turn)
        return min(min(turn, WEDGE - turn), min(c[1] - pick[1], (c[2] - pick[2]) * cut[2]))
    }

    /** Negative inside. */
    fun solid(p: DoubleArray) = if (cut[3] > 0) max(shape(p), cutAway(p)) else shape(p)

    /** Distance along the ray to the slice where it is inside the solid. */
    fun sliceHit(o: DoubleArray, d: DoubleArray): Double? {
        val axis = slice ?: return null
        val value = pick[axis]
        val hits = when {
            space.isCartesian -> listOf(planeHit(o, d, axis, (value * 2 - 1) * size))
            axis == 2 -> listOf(planeHit(o, d, 1, value * 2 - 1))
            axis == 0 -> {
                val angle = value * 2 * PI
                val normal = doubleArrayOf(-sin(angle), 0.0, cos(angle))
                val facing = dot(d, normal)
                if (abs(facing) <= 1e-9) {
                    emptyList()
                } else {
                    val t = -dot(o, normal) / facing
                    val p = along(o, d, t)
                    if (p[0] * cos(angle) + p[2] * sin(angle) >= 0) listOf(t) else emptyList()
                }
            }
            else -> {
                val a = d[0] * d[0] + d[2] * d[2]
                val b = 2 * (o[0] * d[0] + o[2] * d[2])
                val c = o[0] * o[0] + o[2] * o[2] - value * value
                val root = b * b - 4 * a * c
                if (a > 1e-12 && root >= 0) listOf((-b - sqrt(root)) / (2 * a), (-b + sqrt(root)) / (2 * a)) else emptyList()
            }
        }
        return hits.firstOrNull { it > 0 && shape(along(o, d, it)) <= 1e-4 }
    }

    private fun planeHit(o: DoubleArray, d: DoubleArray, index: Int, at: Double) =
        if (abs(d[index]) < 1e-9) -1.0 else (at - o[index]) / d[index]
}

/** Distance along `ray` to the first point inside the solid. */
private fun march(origin: DoubleArray, ray: DoubleArray, geometry: SolidGeometry): Double? {
    val a = DoubleArray(3) { (-1.001 - origin[it]) / ray[it] }
    val b = DoubleArray(3) { (1.001 - origin[it]) / ray[it] }
    val near = (0..2).maxOf { min(a[it], b[it]) }
    val far = (0..2).minOf { max(a[it], b[it]) }
    if (!(far >= max(near, 0.0))) return null
    val step = (far - near) / STEPS
    fun at(t: Double) = geometry.solid(along(origin, ray, t))
    fun refine(low: Double, high: Double): Double {
        var lo = low
        var hi = high
        repeat(10) {
            val mid = (lo + hi) / 2
            if (at(mid) < 0) hi = mid else lo = mid
        }
        return hi
    }
    var previous = near
    for (index in 0..STEPS) {
        val t = near + step * index
        val value = at(t)
        if (value < 0) return if (index == 0) t else refine(previous, t)
        if (value < NEAR_SURFACE && index < STEPS) {
            for (sub in 1 until SUBSTEPS) {
                val fine = t + step * sub / SUBSTEPS
                if (at(fine) < 0) return refine(fine - step / SUBSTEPS, fine)
            }
        }
        previous = t
    }
    return null
}

/**
 * A shell unrolled about the line facing the camera, lengths kept: an arc of radius
 * `radius / bend` through the shell's front line, flat at `bend` 0. Mirrors
 * `unrolledHit` in the fragment shader: change both together.
 */
private class SolidSheet(val radius: Double, val facing: Double, val bend: Double) {
    val normal get() = doubleArrayOf(cos(facing), 0.0, sin(facing))
    val tangent get() = doubleArrayOf(-sin(facing), 0.0, cos(facing))

    /** The sheet point of the shell point `turn` radians round from the facing hue, and whether it is on the far side. */
    fun place(turn: Double, y: Double): Pair<DoubleArray, Boolean> {
        val angle = turn * bend
        val distance = if (bend > 1e-4) radius * sin(angle) / bend else radius * turn
        val sag = if (bend > 1e-4) radius * (1 - cos(angle)) / bend else 0.0
        val point = add(add(scale(normal, radius - sag), scale(tangent, distance)), doubleArrayOf(0.0, y, 0.0))
        return point to (abs(angle) > PI / 2)
    }

    /** Shell points whose sheet points lie under the ray, near side first. Assumes the camera faces the sheet head on. */
    fun shellPoints(origin: DoubleArray, ray: DoubleArray): List<DoubleArray> {
        val facingRay = dot(ray, normal)
        if (abs(facingRay) <= 1e-9 || radius <= 1e-4) return emptyList()
        val p = along(origin, ray, dot(sub(scale(normal, radius), origin), normal) / facingRay)
        val distance = dot(p, tangent)
        val bent = distance * bend / radius
        if (abs(bent) > 1) return emptyList()
        val front = asin(bent)
        val turns = if (bend > 1e-4) {
            listOf(front, (if (front < 0) -PI else PI) - front).map { it / bend }
        } else {
            listOf(distance / radius)
        }
        return turns.filter { abs(it) <= PI }
            .map { doubleArrayOf(radius * cos(facing + it), p[1], radius * sin(facing + it)) }
    }
}

private data class SolidAim(var yaw: Double, val pitch: Double, val distance: Double, var fit: Double, val lookAt: DoubleArray)

private class SolidCamera(
    val position: DoubleArray,
    val right: DoubleArray,
    val up: DoubleArray,
    val forward: DoubleArray,
    val tanX: Double,
    val tanY: Double,
)

/** The view from `aim`; above 0, `flatness` widens it to hold a sheet unrolled from a shell of `sheetRadius`. */
private fun solidCamera(aim: SolidAim, width: Double, height: Double, flatness: Double, sheetRadius: Double): SolidCamera {
    val (yaw, pitch, distance, radius) = aim
    val forward = doubleArrayOf(-cos(pitch) * sin(yaw), -sin(pitch), -cos(pitch) * cos(yaw))
    val position = sub(aim.lookAt, scale(forward, distance))
    // yaw-based: valid overhead
    val right = doubleArrayOf(cos(yaw), 0.0, -sin(yaw))
    val up = cross(right, forward)
    // sphere fits short side
    val fit = radius * 1.1 / sqrt(distance * distance - radius * radius)
    val aspect = if (height > 0) width / height else 1.0
    var tanX = if (aspect < 1) fit else fit * aspect
    var tanY = if (aspect < 1) fit / aspect else fit
    if (flatness > 0) {
        // unrolled: half-width pi r
        val halfWidth = 1 + (max(1.0, PI * sheetRadius) - 1) * flatness
        val grow = max(1.0, max(halfWidth * 1.1 / distance / tanX, 1.1 / distance / tanY))
        tanX *= grow
        tanY *= grow
    }
    return SolidCamera(position, right, up, forward, tanX, tanY)
}

/** Where `p` lands in a view of `width` by `height`, or null behind the camera. */
private fun project(cam: SolidCamera, width: Float, height: Float, p: DoubleArray): Offset? {
    val offset = sub(p, cam.position)
    val depth = dot(offset, cam.forward)
    if (depth <= 0) return null
    val x = dot(offset, cam.right) / (depth * cam.tanX)
    val y = dot(offset, cam.up) / (depth * cam.tanY)
    return Offset(((x + 1) / 2 * width).toFloat(), ((1 - y) / 2 * height).toFloat())
}

private fun ray(cam: SolidCamera, width: Float, height: Float, at: Offset): DoubleArray {
    val x = at.x / max(width, 1f) * 2.0 - 1
    val y = 1 - at.y / max(height, 1f) * 2.0
    return normalize(add(add(cam.forward, scale(cam.right, x * cam.tanX)), scale(cam.up, y * cam.tanY)))
}

/** Letters beside the solid, at points in its box. */
private fun axisAnchors(space: SolidSpace, geometry: SolidGeometry): List<Pair<String, DoubleArray>> {
    val edge = -geometry.size
    val reach = geometry.size + 0.22
    fun at(x: Double, y: Double, z: Double) = doubleArrayOf(x, y, z)
    return when (space) {
        SolidSpace.RGB -> listOf("R" to at(reach, edge, edge), "G" to at(edge, reach, edge), "B" to at(edge, edge, reach))
        SolidSpace.HSL -> listOf("H" to at(reach, edge, edge), "L" to at(edge, reach, edge), "S" to at(edge, edge, reach))
        SolidSpace.HSB -> listOf("B" to at(0.0, reach, 0.0), "S" to at(reach, 1.0, 0.0))
        SolidSpace.OKLCH, SolidSpace.LCH -> listOf("L" to at(0.0, reach, 0.0), "C" to at(reach, 0.0, 0.0))
        SolidSpace.OKLAB, SolidSpace.LAB -> listOf("L" to at(0.0, reach, 0.0), "a" to at(reach, 0.0, 0.0), "b" to at(0.0, 0.0, reach))
        SolidSpace.CMYK -> {
            val k = (1 + INNER_SIZE) / 2 + 0.1
            listOf("C" to at(reach, edge, edge), "M" to at(edge, reach, edge), "Y" to at(edge, edge, reach), "K" to at(k, k, k))
        }
    }
}

/** Outer cube at 0 % black, inner cube at 100 %, and the edges joining their corners. */
private val tesseractEdges: List<Pair<DoubleArray, DoubleArray>> = run {
    val corners = (0 until 8).map { i ->
        doubleArrayOf(if (i and 1 != 0) 1.0 else -1.0, if (i and 2 != 0) 1.0 else -1.0, if (i and 4 != 0) 1.0 else -1.0)
    }
    val edges = mutableListOf<Pair<DoubleArray, DoubleArray>>()
    for (size in listOf(1.0, INNER_SIZE)) {
        for (a in 0 until 8) for (bit in listOf(1, 2, 4)) {
            if (a and bit == 0) edges += scale(corners[a], size) to scale(corners[a or bit], size)
        }
    }
    corners.forEach { edges += it to scale(it, INNER_SIZE) }
    edges
}
