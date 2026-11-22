package com.delphi.delphitools.shell

import android.graphics.BlurMaskFilter
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiDark
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.IEEErem
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

// dt-ios Shell/FanMenu.swift FanMenuView: the column for a bottom bar, the wheel for a side bar
private val columnRadius = 2000.dp
private val columnSpacing = 30.dp
private val wheelPadding = 24.dp
private val arcSpacing = 26.dp
private val wheelLightMargin = 8.dp
private const val LIGHT_FEATHER = 1.35f
private const val EDGE_FADE = 8f * PI.toFloat() / 180f
private const val LAMP_DELAY = 0.18f
private const val LAMP_RISE = 0.1f
private const val SELECTION_SHARE = 0.55f
private const val INFO_SHARE = 0.4f
private val besideWidth = 700.dp
private val flyIn = 80.dp
private const val EDGE_FADE_ROWS = 2.5f
private val columnLightMargin = 20.dp
private val columnLightFeather = 7.dp
private const val SELECTED_GAP = 0.6f
private const val SCRIM = 0.9f
private const val DARKEN = 0.12f
private const val LAMP_HOLD = 0.16f
private const val LAMP_SETTLE = 0.45f
private val symbolSlot = 34.dp
private val selectedSlide = 14.dp
private val selectedTextGap = 12.dp
private const val DIM_PER_SPOKE = 0.09f
private const val DIM_FLOOR = 0.2f
private const val FRICTION = 6.5f
private const val FLING_CARRY = 0.55f
private const val FLING_LIMIT = 20f
private const val CATCH_SPEED = 4f
private val screenMargin = 12.dp
private const val FLY_DURATION = 0.34f
private const val FLY_STAGGER = 0.022f
private const val FOLD_DURATION = 0.16f
private const val FADE_DURATION = 0.16f
private const val FADE_STAGGER = 0.015f
private const val FADE_REACH = 10f
private const val PICK_HOLD = FADE_REACH * FADE_STAGGER + 0.06f
private const val MAX_ANGLE = 85f * Math.PI.toFloat() / 180f

private val restStyle = CanonText.bodyMedium
private val kicker = CanonText.captionSemibold.copy(letterSpacing = 1.4.sp)

/** A category header (`tool` null) or a tool, in fan order. */
private class FanEntry(val category: ToolCategory, val tool: Tool?)

private fun fanEntries(order: List<ToolCategory>): List<FanEntry> = shownCategories(order).flatMap { (category, inCategory) ->
    listOf(FanEntry(category, null)) + inCategory.map { FanEntry(category, it) }
}

private fun List<FanEntry>.wrapped(index: Int) = index.mod(size)

private fun List<FanEntry>.nearestTool(value: Float): Float {
    val index = value.roundToInt()
    return if (this[wrapped(index)].tool == null) index + 1f else index.toFloat()
}

private fun easeOutBack(t: Float): Float {
    val x = t.coerceIn(0f, 1f) - 1f
    val overshoot = 1.9f
    return x * x * ((overshoot + 1) * x + overshoot) + 1
}

private fun easeIn(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x
}

@Stable
private class FanState(start: Float, private val entries: List<FanEntry>) {
    // unbounded; index modulo entry count
    var position by mutableFloatStateOf(start)
    // seconds since the fan opened or began closing
    var clock by mutableFloatStateOf(0f)
    var settling by mutableStateOf(false)
    var closing by mutableStateOf(false)
    var picked by mutableStateOf<Int?>(null)
    // back gesture progress while the fan is still open
    var back by mutableFloatStateOf(0f)
    var velocity = 0f
    var snapTarget: Float? = null

    val closeDuration get() = if (picked == null) FOLD_DURATION else PICK_HOLD + FADE_DURATION
    val busy get() = closing || settling || clock < entries.size / 2f * FLY_STAGGER + FLY_DURATION

    // `folded`: the share of the fold a back gesture already drew
    fun dismiss(index: Int? = null, folded: Float = 0f) {
        if (closing) return
        settling = false
        closing = true
        picked = index
        clock = folded * FOLD_DURATION
    }

    fun advance(elapsed: Float) {
        clock += elapsed
        if (!settling) return
        val target = snapTarget
        if (target != null) {
            val remaining = target - position
            if (abs(remaining) < 0.002f) {
                position = target
                settling = false
            } else {
                position += remaining * min(1f, elapsed * 14f)
            }
        } else {
            position += velocity * elapsed
            velocity *= exp(-FRICTION * elapsed)
            if (abs(velocity) < CATCH_SPEED) snapTarget = entries.nearestTool(position)
        }
    }
}

/** The fan over the whole window; `item` is the pill's frame in window pixels, `vertical` when it sits in a side bar. */
@Composable
internal fun FanMenu(item: Rect, vertical: Boolean, current: Tool?, onOpen: (Tool) -> Unit, onDismissed: () -> Unit) {
    val context = LocalContext.current
    val talkBack = remember { context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true }
    // iOS: overrideUserInterfaceStyle = .dark
    CompositionLocalProvider(LocalDelphiColours provides DelphiDark) {
        val entries = fanEntries(LocalShellStores.current.categoryOrder)
        if (talkBack) FanList(entries, onOpen, onDismissed) else FanWheel(entries, item, vertical, current, onOpen, onDismissed)
    }
}

/** TalkBack: the fan's entries as a plain list. */
@Composable
private fun FanList(entries: List<FanEntry>, onOpen: (Tool) -> Unit, onDismissed: () -> Unit) {
    val c = DelphiDark
    var back by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    PredictiveBackHandler { events ->
        try {
            events.collect { back = it.progress }
            onDismissed()
        } catch (cancelled: CancellationException) {
            scope.launch { animate(back, 0f, animationSpec = spring()) { value, _ -> back = value } }
            throw cancelled
        }
    }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - back }
            .background(Color.Black.copy(alpha = SCRIM)),
        contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
    ) {
        item { CanonButton("Close", Modifier.padding(16.dp), kind = CanonButtonKind.Secondary, onClick = onDismissed) }
        entries.forEach { entry ->
            item {
                val tool = entry.tool
                if (tool == null) {
                    Text(
                        entry.category.title.uppercase(),
                        style = kicker,
                        color = c.mutedForeground,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
                    )
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .canonClickable {
                                onOpen(tool)
                                onDismissed()
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(tool.icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(20.dp))
                        Text(tool.name, style = restStyle, color = c.foreground)
                    }
                }
            }
        }
    }
}

@Composable
private fun FanWheel(entries: List<FanEntry>, item: Rect, vertical: Boolean, current: Tool?, onOpen: (Tool) -> Unit, onDismissed: () -> Unit) {
    val c = DelphiDark
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val dismissed by rememberUpdatedState(onDismissed)
    val state = remember {
        FanState(entries.indexOfFirst { it.tool != null && it.tool.id == current?.id }.takeIf { it >= 0 }?.toFloat() ?: 1f, entries)
    }
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = !state.closing) { events ->
        try {
            events.collect { state.back = it.progress }
            state.dismiss(folded = state.back)
        } catch (cancelled: CancellationException) {
            scope.launch { animate(state.back, 0f, animationSpec = spring()) { value, _ -> state.back = value } }
            throw cancelled
        }
    }

    LaunchedEffect(state) {
        var last = withFrameNanos { it }
        while (true) {
            if (!state.busy) {
                snapshotFlow { state.busy }.first { it }
                last = withFrameNanos { it }
            }
            val now = withFrameNanos { it }
            state.advance(((now - last) / 1e9f).coerceAtMost(0.05f))
            last = now
            if (state.closing && state.clock >= state.closeDuration) {
                dismissed()
                return@LaunchedEffect
            }
        }
    }

    val selected by remember { derivedStateOf { entries.wrapped(entries.nearestTool(state.position).toInt()) } }
    var ticked by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (ticked) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        ticked = true
    }

    val lamp = remember {
        val direction = Random.nextFloat() * 2f * Math.PI.toFloat()
        val distance = with(density) { (14 + Random.nextFloat() * 18).dp.toPx() }
        floatArrayOf(cos(direction) * distance, sin(direction) * distance, 6f + Random.nextFloat() * 2.5f, 11f + Random.nextFloat() * 5f, 0.8f + Random.nextFloat() * 0.45f)
    }

    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val safe = WindowInsets.safeDrawing.asPaddingValues()
        val geometry = with(density) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            val column = !vertical
            val lightRadius = if (column) 0f else (hypot(item.width, item.height) / 2 + wheelLightMargin.toPx()) * LIGHT_FEATHER
            val r = if (column) columnRadius.toPx() else lightRadius + wheelPadding.toPx()
            val reach = r + symbolSlot.toPx()
            val beside = w > h || maxWidth >= besideWidth
            val side = if (item.center.x < w / 2) -1f else 1f
            var hub = item.center
            var top = screenMargin.toPx()
            var bottom = h - screenMargin.toPx()
            if (column) {
                val litHalf = item.height / 2 + (columnLightMargin + columnLightFeather * 2).toPx()
                // ponytail: assumes the bar item is at the bottom of the window, as on iOS
                top = if (beside) top + safe.calculateTopPadding().toPx() else h * INFO_SHARE
                bottom = max(top + 1, item.center.y - litHalf - 4.dp.toPx())
                hub = Offset(hub.x, top + (bottom - top) * SELECTION_SHARE)
            }
            var low = -asin(((bottom - hub.y) / reach).coerceIn(-1f, 1f))
            var high = asin(((hub.y - top) / reach).coerceIn(-1f, 1f))
            if (vertical) high = min(high, acos(min(1f, (item.width / 2 + 20.dp.toPx()) / reach)))
            low = max(low, -MAX_ANGLE)
            high = min(high, MAX_ANGLE)
            if (column) hub = Offset((if (side > 0) item.right else item.left) + side * r * cos(max(-low, high)), hub.y)
            val room = (if (side > 0) hub.x else w - hub.x) - screenMargin.toPx() - reach
            val step = (if (column) columnSpacing else arcSpacing).toPx() / r
            FanGeometry(hub, min(low, high), max(low, high), step, r, side, column, beside, lightRadius, room)
        }
        val styles = remember(geometry.room) { fitStyles(geometry.room, density.density) { text, style -> measurer.measure(text, style).size.width } }
        val labelLength = styles.third
        val labelPx = with(density) { labelLength.toPx() }

        // up positive, mirrored for a left-hand fan
        fun angleTo(point: Offset) = atan2(-(point.y - geometry.hub.y), -geometry.side * (point.x - geometry.hub.x))

        fun overItem(point: Offset) = with(density) {
            if (geometry.column) {
                item.inflate(8.dp.toPx()).contains(point)
            } else {
                hypot(point.x - geometry.hub.x, point.y - geometry.hub.y) < geometry.radius - 10.dp.toPx()
            }
        }

        fun entryIndex(point: Offset): Int? {
            val distance = hypot(point.x - geometry.hub.x, point.y - geometry.hub.y)
            val touched = angleTo(point)
            val slack = with(density) { 10.dp.toPx() }
            val reach = geometry.radius + with(density) { (symbolSlot + selectedSlide).toPx() } + labelPx
            if (overItem(point) || distance < geometry.radius - slack || distance > reach || touched !in geometry.low..geometry.high) return null
            val displaced = touched / geometry.step
            val offset = if (abs(displaced) >= 1 + SELECTED_GAP) displaced - SELECTED_GAP * sign(displaced) else displaced / (1 + SELECTED_GAP)
            return entries.wrapped((state.position - offset).roundToInt())
        }

        val scrimAlpha = if (state.closing) 1f - (state.clock / state.closeDuration).coerceIn(0f, 1f) else (state.clock / DARKEN).coerceIn(0f, 1f) * (1f - state.back)
        val infoAlpha = if (state.closing) scrimAlpha else ((state.clock - 0.06f) / 0.22f).coerceIn(0f, 1f) * (1f - state.back)
        val lampDelay = if (geometry.column) 0f else LAMP_DELAY
        val wobble = if (state.closing) Offset.Zero else {
            val t = (state.clock - lampDelay) / LAMP_SETTLE
            val s = max(0f, t - LAMP_HOLD) / (1 - LAMP_HOLD)
            val envelope = if (t >= 1f) 0f else exp(-lamp[2] * s)
            Offset(lamp[0] * envelope * cos(lamp[3] * s), lamp[1] * envelope * cos(lamp[3] * lamp[4] * s))
        }
        val lit = if (state.closing) 1f else ((state.clock - lampDelay) / LAMP_RISE).coerceIn(0f, 1f)
        val hole = remember {
            Paint().apply {
                blendMode = BlendMode.DstOut
                asFrameworkPaint().maskFilter = BlurMaskFilter(with(density) { columnLightFeather.toPx() }, BlurMaskFilter.Blur.NORMAL)
            }
        }

        // ponytail: no dust motes drifting in the light
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = scrimAlpha
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .pointerInput(geometry) {
                    detectTapGestures { point ->
                        if (state.closing) return@detectTapGestures
                        val index = entryIndex(point)
                        val tool = index?.let { entries[it].tool }
                        when {
                            index == null -> state.dismiss()
                            tool != null -> {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                state.dismiss(index)
                                onOpen(tool)
                            }
                        }
                    }
                }
                .pointerInput(geometry) {
                    val rowPx = columnSpacing.toPx()
                    var ignored = false
                    detectDragGestures(
                        onDragStart = { start ->
                            state.settling = false
                            state.snapTarget = null
                            state.velocity = 0f
                            // drags on the wheel's hub are noise
                            ignored = !geometry.column && overItem(start)
                        },
                        onDragEnd = {
                            state.velocity = (state.velocity * FLING_CARRY).coerceIn(-FLING_LIMIT, FLING_LIMIT)
                            state.settling = !state.closing
                        },
                        onDragCancel = { state.settling = !state.closing },
                    ) { change, drag ->
                        if (state.closing || ignored) return@detectDragGestures
                        val moved = if (geometry.column) {
                            -drag.y / rowPx
                        } else {
                            var delta = angleTo(change.position) - angleTo(change.previousPosition)
                            if (delta > PI) delta -= 2 * PI.toFloat() else if (delta < -PI) delta += 2 * PI.toFloat()
                            delta / geometry.step
                        }
                        state.position += moved
                        val elapsed = max((change.uptimeMillis - change.previousUptimeMillis) / 1000f, 1f / 240f)
                        state.velocity = 0.6f * state.velocity + 0.4f * moved / elapsed
                    }
                }
        ) {
            if (geometry.column) {
                drawRect(Color.Black.copy(alpha = SCRIM))
                val pool = item.inflate(columnLightMargin.toPx()).translate(wobble)
                drawIntoCanvas { it.drawRoundRect(pool.left, pool.top, pool.right, pool.bottom, pool.height / 2, pool.height / 2, hole) }
            } else {
                val inside = Color.Black.copy(alpha = SCRIM * (1 - lit))
                drawRect(
                    Brush.radialGradient(
                        0f to inside,
                        1f / LIGHT_FEATHER to inside,
                        1f to Color.Black.copy(alpha = SCRIM),
                        center = item.center + wobble,
                        radius = geometry.lightRadius,
                    )
                )
            }
        }

        val infoTop = safe.calculateTopPadding()
        val wheelReach = geometry.radius + with(density) { symbolSlot.toPx() } + labelPx
        val infoRegion = with(density) {
            if (geometry.beside) {
                val near = safe.calculateLeftPadding(LayoutDirection.Ltr).toPx()
                val far = constraints.maxWidth - safe.calculateRightPadding(LayoutDirection.Ltr).toPx()
                val wheelEdge = geometry.hub.x - geometry.side * (wheelReach + 24.dp.toPx())
                val bottom = constraints.maxHeight - safe.calculateBottomPadding().toPx()
                if (geometry.side > 0) Rect(near, infoTop.toPx(), wheelEdge, bottom) else Rect(wheelEdge, infoTop.toPx(), far, bottom)
            } else {
                val wheelTop = geometry.hub.y - wheelReach * sin(geometry.high)
                Rect(0f, infoTop.toPx(), constraints.maxWidth.toFloat(), wheelTop - 24.dp.toPx())
            }
        }
        val shownTool = entries[selected].tool
        if (shownTool != null && infoRegion.width >= with(density) { 200.dp.toPx() } && infoRegion.height >= with(density) { 150.dp.toPx() }) {
            with(density) {
                FanInfo(
                    shownTool,
                    Modifier
                        .offset { IntOffset(infoRegion.left.roundToInt(), infoRegion.top.roundToInt()) }
                        .size(infoRegion.width.toDp(), infoRegion.height.toDp())
                        .graphicsLayer { alpha = infoAlpha },
                )
            }
        }

        val n = entries.size.toFloat()
        val spokePx = with(density) { symbolSlot.toPx() } + labelPx
        val rowHeight = 40.dp
        val rowHeightPx = with(density) { rowHeight.toPx() }
        val flyInPx = with(density) { flyIn.toPx() }
        val slidePx = with(density) { selectedSlide.toPx() }
        val edgeFade = if (geometry.column) EDGE_FADE_ROWS * geometry.step else EDGE_FADE
        entries.forEachIndexed { index, entry ->
            val offset = (state.position - index).IEEErem(n)
            val angle = (offset + SELECTED_GAP * offset.coerceIn(-1f, 1f)) * geometry.step
            val fromEdge = min(angle - geometry.low, geometry.high - angle)
            val focus = max(0f, 1 - abs(offset))
            if (fromEdge > 0 || focus > 0) {
                val picked = state.picked
                val flight: Float
                var presence: Float
                when {
                    picked != null -> {
                        val away = abs((index - picked).toFloat().IEEErem(n))
                        val delay = if (away == 0f) PICK_HOLD else min(away, FADE_REACH) * FADE_STAGGER
                        flight = 1f
                        presence = 1 - easeIn((state.clock - delay) / FADE_DURATION)
                    }
                    state.closing -> {
                        flight = 1 - easeIn(state.clock / FOLD_DURATION)
                        presence = (flight * 1.6f).coerceIn(0f, 1f)
                    }
                    else -> {
                        flight = min(easeOutBack((state.clock - abs(offset) * FLY_STAGGER) / FLY_DURATION), 1 - easeIn(state.back))
                        presence = (flight * 1.6f).coerceIn(0f, 1f)
                    }
                }
                val distance = max(DIM_FLOOR, 1 - DIM_PER_SPOKE * abs(offset))
                val edge = (fromEdge / edgeFade).coerceIn(0f, 1f)
                if (index == picked) presence /= max(max(focus, edge * distance), 0.01f)
                val alpha = min(1f, presence * max(focus, edge * distance))
                // the column slides rows in from the side; the wheel spins and grows them out of the hub
                val turn = if (geometry.column) angle else angle + (1 - min(flight, 1f)) * 0.7f
                val scale = if (geometry.column) 1f else max(flight, 0.001f)
                val inner = if (geometry.column) geometry.radius - (flyInPx * (1 - flight) - slidePx * focus) else (geometry.radius + slidePx * focus) * scale
                val x = geometry.hub.x - geometry.side * inner * cos(turn)
                val y = geometry.hub.y - inner * sin(turn)
                key(index) {
                    Spoke(
                        entry,
                        focus,
                        geometry.side,
                        styles.first,
                        styles.second,
                        labelLength,
                        Modifier
                            .offset { IntOffset((if (geometry.side > 0) x - spokePx else x).roundToInt(), (y - rowHeightPx / 2).roundToInt()) }
                            .size(symbolSlot + labelLength, rowHeight)
                            .graphicsLayer {
                                rotationZ = geometry.side * Math.toDegrees(turn.toDouble()).toFloat()
                                transformOrigin = TransformOrigin(if (geometry.side > 0) 1f else 0f, 0.5f)
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            },
                    )
                }
            }
        }
    }
}

private data class FanGeometry(
    val hub: Offset,
    val low: Float,
    val high: Float,
    val step: Float,
    val radius: Float,
    // 1: the fan opens to the left of a right-hand item; -1 mirrored
    val side: Float,
    val column: Boolean,
    val beside: Boolean,
    val lightRadius: Float,
    val room: Float,
)

/** Rest and selected label styles that fit `room` px, and the label length in dp. */
private fun fitStyles(room: Float, density: Float, measure: (String, TextStyle) -> Int): Triple<TextStyle, TextStyle, Dp> {
    val longest = tools.maxBy { measure(it.name, restStyle) }.name
    val rest = listOf(15, 14, 13, 12).map { restStyle.copy(fontSize = it.sp) }
        .firstOrNull { measure(longest, it) <= room } ?: restStyle.copy(fontSize = 12.sp)
    val selectedRoom = room - (selectedSlide + selectedTextGap).value * density
    // ponytail: iOS widens the selected label to the expanded width; Roboto Flex stays condensed here
    val selected = listOf(23, 21, 20, 18, 17, 16).map { TextStyle(fontFamily = restStyle.fontFamily, fontSize = it.sp, fontWeight = FontWeight.Bold) }
        .firstOrNull { measure(longest, it) <= selectedRoom } ?: TextStyle(fontFamily = restStyle.fontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val length = (max(measure(longest, rest), measure(longest, selected)) / density).dp + 10.dp
    return Triple(rest, selected, length)
}

// ponytail: no press squash on the touched spoke, no label shrink near the screen edge
@Composable
private fun Spoke(entry: FanEntry, focus: Float, side: Float, rest: TextStyle, selected: TextStyle, labelLength: Dp, modifier: Modifier) {
    val c = DelphiDark
    val tool = entry.tool
    val mirrored = side < 0
    val align = if (mirrored) Alignment.Start else Alignment.End
    Box(modifier) {
        Box(
            Modifier
                .offset(x = (if (mirrored) symbolSlot + 4.dp else 0.dp) - selectedTextGap * focus * side)
                .width(labelLength - 4.dp)
                .fillMaxHeight(),
            contentAlignment = if (mirrored) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            if (tool == null) {
                Text(entry.category.title.uppercase(), style = kicker, color = c.mutedForeground, maxLines = 1, softWrap = false)
            } else {
                Text(
                    tool.name,
                    style = lerp(rest, selected, focus),
                    color = lerp(c.foreground, c.primary, focus),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.wrapContentWidth(align, unbounded = true),
                )
            }
        }
        if (tool != null) {
            Icon(
                tool.icon,
                contentDescription = null,
                tint = lerp(c.mutedForeground, c.primary, focus),
                modifier = Modifier
                    .offset(x = if (mirrored) 2.dp else labelLength + 2.dp, y = 5.dp)
                    .size(30.dp)
                    .padding(5.dp)
                    .graphicsLayer {
                        scaleX = 1 + 0.4f * focus
                        scaleY = 1 + 0.4f * focus
                    },
            )
        }
    }
}

// dt-ios Shell/FanInfoView.swift: the selected tool's kicker, name and description over its drifting symbol
// ponytail: no "Also try" cell
@Composable
private fun FanInfo(tool: Tool, modifier: Modifier) {
    val c = DelphiDark
    BoxWithConstraints(modifier) {
        val tier = when {
            maxHeight >= 320.dp -> 2
            maxHeight >= 230.dp -> 1
            else -> 0
        }
        val nameSize = listOf(19, 24, 32)[tier].sp
        val inset = listOf(16, 22, 28)[tier].dp
        val painter = rememberVectorPainter(tool.icon)
        val drift by rememberInfiniteTransition(label = "drift").animateFloat(
            0f, 1f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "drift",
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 0.1f
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            val tile = 92.dp.toPx()
            val glyph = 24.dp.toPx()
            val shift = -drift * tile
            var y = shift
            while (y < size.height) {
                var x = shift
                while (x < size.width) {
                    for (spot in listOf(0.25f, 0.75f)) {
                        translate(x + tile * spot - glyph / 2, y + tile * spot - glyph / 2) {
                            with(painter) { draw(Size(glyph, glyph), colorFilter = ColorFilter.tint(Color.White)) }
                        }
                    }
                    x += tile
                }
                y += tile
            }
            drawRect(
                Brush.radialGradient(0f to Color.Black, 0.4f to Color.Black, 1f to Color.Transparent, center = center, radius = size.maxDimension / 2),
                blendMode = BlendMode.DstIn,
            )
        }
        Crossfade(tool, animationSpec = tween(180), label = "info", modifier = Modifier.align(Alignment.CenterStart)) { shown ->
            Column(Modifier.padding(inset), verticalArrangement = Arrangement.spacedBy(if (tier > 0) 6.dp else 3.dp)) {
                Text(shown.category.title.uppercase(), style = kicker, color = c.primary)
                Text(
                    shown.name,
                    style = TextStyle(fontFamily = restStyle.fontFamily, fontSize = nameSize, fontWeight = FontWeight.Bold),
                    color = c.foreground,
                    maxLines = if (tier > 0) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(if (tier > 0) 4.dp else 2.dp))
                Text(
                    shown.description,
                    style = if (tier > 0) CanonText.body else CanonText.body.copy(fontSize = 12.sp),
                    color = c.mutedForeground,
                    maxLines = if (tier == 2) 4 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
