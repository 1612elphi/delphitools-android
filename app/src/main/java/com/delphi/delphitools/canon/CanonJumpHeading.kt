package com.delphi.delphitools.canon

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A group in the jump grid: its title and, under it, a detail such as its item count. */
data class CanonJumpSection(val title: String, val detail: String = "")

/** A group of a lazy list: the item its sticky header is. */
data class CanonJumpGroup(val title: String, val itemIndex: Int, val detail: String = "")

/** The jump headings inside a CanonJumpColumn, and how to scroll it. */
@Stable
class CanonJumpTargets internal constructor(private val scroll: ScrollableState) {
    internal class Heading(var section: CanonJumpSection) {
        var coordinates: LayoutCoordinates? = null
    }

    internal var viewport: LayoutCoordinates? = null
    internal var top = 0f
    internal val headings = mutableListOf<Heading>()

    /** Headings in page order with their distance below the top of the viewport. */
    internal fun placed(): List<Pair<Heading, Float>> {
        val viewport = viewport?.takeIf { it.isAttached } ?: return emptyList()
        return headings.mapNotNull { heading ->
            heading.coordinates?.takeIf { it.isAttached }?.let { heading to viewport.localPositionOf(it, Offset.Zero).y }
        }.sortedBy { it.second }
    }

    internal fun current(placed: List<Pair<Heading, Float>>) = placed.indexOfLast { it.second <= top + 1 }.coerceAtLeast(0)

    internal suspend fun jump(heading: Heading) {
        val y = placed().firstOrNull { it.first === heading }?.second ?: return
        scroll.animateScrollBy(y - top)
    }
}

private val LocalCanonJumpTargets = staticCompositionLocalOf<CanonJumpTargets?> { null }

/**
 * A scrolling column whose CanonJumpHeadings open a grid of every jump heading in it.
 * `top` is the room at the top a header or status bar covers; a pick scrolls its heading to just under it.
 */
@Composable
fun CanonJumpColumn(
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    top: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val targets = remember(scroll) { CanonJumpTargets(scroll) }
    targets.top = with(LocalDensity.current) { top.toPx() }
    CompositionLocalProvider(LocalCanonJumpTargets provides targets) {
        Column(modifier.onGloballyPositioned { targets.viewport = it }.verticalScroll(scroll), content = content)
    }
}

/** A CanonHeading with a chevron; inside a CanonJumpColumn (every CanonSplit pane) it opens the jump grid of its column. */
@Composable
fun CanonJumpHeading(text: String, modifier: Modifier = Modifier, detail: String = "") {
    val targets = LocalCanonJumpTargets.current
    val heading = remember { CanonJumpTargets.Heading(CanonJumpSection(text, detail)) }
    heading.section = CanonJumpSection(text, detail)
    DisposableEffect(targets, heading) {
        targets?.headings?.add(heading)
        onDispose { targets?.headings?.remove(heading) }
    }
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf<List<CanonJumpTargets.Heading>?>(null) }
    var current by remember { mutableStateOf(0) }
    JumpBar(text, modifier.onGloballyPositioned { heading.coordinates = it }) {
        val placed = targets?.placed() ?: return@JumpBar
        current = targets.current(placed)
        shown = placed.map { it.first }
    }
    shown?.let { headings ->
        CanonJumpGrid(
            headings.map { it.section },
            current,
            onPick = { index -> scope.launch { targets?.jump(headings[index]) } },
            onDismiss = { shown = null },
        )
    }
}

/** A sticky header of a lazy list: the grid shows `groups`, lit at the group in view, and a pick scrolls to it. */
@Composable
fun CanonJumpHeading(text: String, list: LazyListState, groups: List<CanonJumpGroup>, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    CanonJumpHeading(
        text,
        groups.map { CanonJumpSection(it.title, it.detail) },
        current = { groups.indexOfLast { it.itemIndex <= list.firstVisibleItemIndex }.coerceAtLeast(0) },
        onPick = { index -> scope.launch { list.animateScrollToItem(groups[index].itemIndex) } },
        modifier,
    )
}

/** A jump heading over any sections: `current` is read when the grid opens. */
@Composable
fun CanonJumpHeading(
    text: String,
    sections: List<CanonJumpSection>,
    current: () -> Int?,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lit by remember { mutableStateOf<Int?>(null) }
    var open by remember { mutableStateOf(false) }
    JumpBar(text, modifier) {
        lit = current()
        open = true
    }
    if (open) CanonJumpGrid(sections, lit, onPick, onDismiss = { open = false })
}

@Composable
private fun JumpBar(text: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    Text(
        "$text ›",
        style = CanonText.footnoteSemibold,
        color = c.mutedForeground,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() }
            .canonClickable(role = Role.Button, onClick = onClick)
            .background(c.muted)
            .padding(horizontal = CanonMetrics.panelPadding, vertical = 8.dp),
    )
}

private const val BACKDROP_ALPHA = 0.94f

/** Rows of `perRow` cells that differ by at most one, larger rows first. */
private fun balanced(count: Int, perRow: Int): List<Int> {
    if (count <= 0) return emptyList()
    val per = perRow.coerceIn(1, count)
    val rows = (count + per - 1) / per
    return List(rows) { count / rows + if (it < count % rows) 1 else 0 }
}

/** Windows Phone jump list: a tile per section over the dimmed page; a pick calls `onPick` and closes it. */
@Composable
fun CanonJumpGrid(sections: List<CanonJumpSection>, current: Int?, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    // ponytail: no hover tint
    val c = LocalDelphiColours.current
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false)) {
        val view = LocalView.current
        (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        val backdrop = remember { Animatable(0f) }
        val back = remember { Animatable(0f) }
        LaunchedEffect(Unit) { backdrop.animateTo(1f, tween(200)) }
        val owner = view.findViewTreeOnBackPressedDispatcherOwner()
        if (owner != null) {
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                LayoutBackDismiss(true, back) {
                    backdrop.animateTo(0f, tween(150))
                    onDismiss()
                }
            }
        }
        val scroll = rememberScrollState()
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backdrop.value * (1f - back.value * 0.5f) }
                .background(c.background.copy(alpha = BACKDROP_ALPHA))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val columns = if (maxWidth >= 560.dp) 3 else 2
            val viewport = constraints.maxHeight
            var content by remember { mutableStateOf<LayoutCoordinates?>(null) }
            var lit by remember { mutableStateOf<Float?>(null) }
            var revealed by remember { mutableStateOf(false) }
            LaunchedEffect(lit) {
                val centre = lit ?: return@LaunchedEffect
                if (revealed) return@LaunchedEffect
                revealed = true
                if (centre > viewport * 0.75f) scroll.scrollTo((centre - viewport / 2).toInt())
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1f - back.value * 0.1f
                        scaleY = 1f - back.value * 0.1f
                    }
                    .verticalScroll(scroll)
                    .onGloballyPositioned { content = it }
                    .padding(start = CanonMetrics.panelPadding, end = CanonMetrics.panelPadding, top = CanonMetrics.panelPadding * 2, bottom = CanonMetrics.panelPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                val rows = balanced(sections.size, columns)
                val widest = rows.maxOrNull() ?: 0
                // long lists open there
                val origin = if (sections.size > 30) current ?: 0 else 0
                Column(
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .background(c.border)
                        .padding(CanonMetrics.hairline),
                    verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
                ) {
                    var start = 0
                    rows.forEach { size ->
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
                        ) {
                            for (k in 0 until size) {
                                val index = start + k
                                JumpTile(
                                    sections[index],
                                    lit = index == current,
                                    delayMillis = 30L * minOf(abs(index - origin), 24),
                                    modifier = Modifier
                                        .weight(if (k == size - 1) (widest - size + 1).toFloat() else 1f)
                                        .fillMaxHeight()
                                        .then(
                                            if (index == current) Modifier.onGloballyPositioned { tile ->
                                                val holder = content ?: return@onGloballyPositioned
                                                lit = holder.localPositionOf(tile, Offset.Zero).y + tile.size.height / 2f
                                            } else Modifier,
                                        ),
                                ) {
                                    onPick(index)
                                    onDismiss()
                                }
                            }
                        }
                        start += size
                    }
                }
            }
        }
    }
}

@Composable
private fun JumpTile(section: CanonJumpSection, lit: Boolean, delayMillis: Long, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        shown.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }
    val ink = if (lit) c.primaryForeground else c.foreground
    Column(
        modifier
            .graphicsLayer {
                val t = shown.value
                alpha = t.coerceIn(0f, 1f)
                scaleX = 0.8f + 0.2f * t
                scaleY = 0.8f + 0.2f * t
                // UIKit turns tiles in from -0.05 rad
                rotationZ = -2.86f * (1f - t)
            }
            .semantics { selected = lit }
            .canonClickable(onClick = onClick)
            .background(if (lit) c.primary else c.card)
            .heightIn(min = 84.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(section.title, style = LayoutText.title3, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (section.detail.isNotEmpty()) {
            Text(section.detail, style = CanonText.caption, color = if (lit) c.primaryForeground else c.mutedForeground)
        }
    }
}

/**
 * Follows a predictive back gesture into `progress` (0 at rest, 1 at a full swipe), runs `onCommit` when the
 * gesture finishes and springs `progress` back to 0 when it is cancelled.
 */
@Composable
internal fun LayoutBackDismiss(enabled: Boolean, progress: Animatable<Float, AnimationVector1D>, onCommit: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    val commit by rememberUpdatedState(onCommit)
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { progress.snapTo(it.progress) }
            commit()
        } catch (cancelled: CancellationException) {
            scope.launch { progress.animateTo(0f, spring()) }
            throw cancelled
        }
    }
}
