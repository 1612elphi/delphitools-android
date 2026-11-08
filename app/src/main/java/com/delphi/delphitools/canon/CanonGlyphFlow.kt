package com.delphi.delphitools.canon

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class CanonGlyphKind { Glyph, Space, Newline }

/** Glyph frames in a block whose first row starts at y 0, and the number of rows. */
data class CanonGlyphLayout(val frames: List<Rect>, val rows: Int)

/** Glyph widths wrapped into rows, words kept whole unless one is wider than a row. */
object CanonGlyphRows {
    fun layout(widths: List<Float>, kinds: List<CanonGlyphKind>, width: Float, gap: Float, height: Float, pitch: Float): CanonGlyphLayout {
        val frames = MutableList(widths.size) { Rect.Zero }
        var row = 0
        var x = 0f
        var filled = false
        fun place(k: Int, left: Float) {
            frames[k] = Rect(left, row * pitch, left + widths[k], row * pitch + height)
            x = left + widths[k]
            filled = true
        }
        fun wrap() {
            row += 1
            x = 0f
            filled = false
        }
        var i = 0
        while (i < widths.size) {
            when (kinds[i]) {
                CanonGlyphKind.Newline -> {
                    frames[i] = Rect(x, row * pitch, x, row * pitch + height)
                    wrap()
                    i += 1
                }
                CanonGlyphKind.Space -> {
                    // no leading row space
                    if (filled) place(i, x + gap) else frames[i] = Rect(0f, row * pitch, 0f, row * pitch + height)
                    i += 1
                }
                CanonGlyphKind.Glyph -> {
                    var end = i
                    var word = 0f
                    while (end < widths.size && kinds[end] == CanonGlyphKind.Glyph) {
                        word += widths[end] + if (end > i) gap else 0f
                        end += 1
                    }
                    if (filled && x + gap + word > width) wrap()
                    for (k in i until end) {
                        if (filled && x + gap + widths[k] > width) wrap()
                        place(k, if (filled) x + gap else 0f)
                    }
                    i = end
                }
            }
        }
        return CanonGlyphLayout(frames, if (widths.isEmpty()) 0 else row + 1)
    }
}

@Stable
data class CanonGlyphMetrics(val height: Dp, val rowGap: Dp, val gap: Dp)

private const val SETTLE_NANOS = 450_000_000L

/**
 * Glyphs in wrapped rows, the last row in view, or `focus` with a row above it; a single row is centred.
 * On `show`, glyphs matching at the start and the end slide to their new places, new ones grow in, removed ones fade.
 */
@Stable
class CanonGlyphFlowState<G>(
    metrics: CanonGlyphMetrics,
    private val measure: (G) -> Dp,
    private val kind: (G) -> CanonGlyphKind,
) {
    var metrics: CanonGlyphMetrics = metrics
        set(value) {
            if (value != field) {
                field = value
                relayout()
            }
        }

    var glyphs: List<G> by mutableStateOf(emptyList())
        private set

    /** Glyph kept in view with a row of context above it; null keeps the last row in view. */
    var focus: Int? = null
        set(value) {
            if (value != field) {
                field = value
                settle(presentedFrames())
            }
        }

    internal var density = 1f
    internal var size = IntSize.Zero
    internal var topInset = 0f
    private var target: List<Rect> = emptyList()
    private var start: List<Rect> = emptyList()
    private var appearing: List<Boolean> = emptyList()
    internal var ghosts: List<Pair<G, Rect>> = emptyList()
        private set
    internal var rowWindow = Rect.Zero
        private set
    internal var progress by mutableFloatStateOf(1f)
    internal var run by mutableIntStateOf(0)
        private set
    internal var version by mutableIntStateOf(0)
        private set
    internal var attached = false

    private val padding get() = CanonMetrics.panelPadding.value * density

    // ponytail: no canvasOverscroll hold of the resting rows
    /** Room the glyphs get: under the canvas top inset and inside the panel padding. */
    val contentArea: Rect
        get() {
            val top = topInset + padding * 0.75f
            val height = max(0f, size.height - topInset - padding * 1.5f)
            return Rect(padding, top, padding + max(0f, size.width - padding * 2), top + height)
        }

    /** Replaces the glyphs. `dropping` counts glyphs cut from the front since the last call (negative: added there). */
    fun show(glyphs: List<G>, dropping: Int = 0, animated: Boolean = true) {
        val old = this.glyphs.drop(max(0, dropping))
        val shown = presentedFrames().drop(max(0, dropping))
        val added = min(glyphs.size, max(0, -dropping))
        val new = glyphs.drop(added)
        val prefix = old.zip(new).takeWhile { it.first == it.second }.size
        val limit = min(old.size, new.size) - prefix
        var suffix = 0
        while (suffix < limit && old[old.size - 1 - suffix] == glyphs[glyphs.size - 1 - suffix]) suffix += 1
        this.glyphs = glyphs
        target = layoutFrames()
        if (!(animated && attached && ValueAnimator.areAnimatorsEnabled())) return stop()
        start = glyphs.indices.map { i ->
            when {
                i < added -> target[i]
                i < added + prefix -> shown[i - added]
                i >= glyphs.size - suffix -> shown[i - glyphs.size + old.size]
                else -> target[i]
            }
        }
        appearing = glyphs.indices.map { it >= added + prefix && it < glyphs.size - suffix }
        val area = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
        ghosts = (prefix until old.size - suffix).mapNotNull { i -> if (shown[i].overlaps(area)) old[i] to shown[i] else null }
        animate()
    }

    // ponytail: no keep-out band round a CanonSplit crease
    /** The glyph under `point`, in the flow's own pixels, or within `slop` of it beside it in its row. */
    fun glyphIndex(at: Offset, slop: Dp = 0.dp): Int? {
        if (!rowWindow.contains(at)) return null
        val half = metrics.rowGap.value * density / 2
        val slopPx = slop.value * density
        fun distance(i: Int) = max(0f, max(target[i].left - at.x, at.x - target[i].right))
        return target.indices
            .filter { i -> kind(glyphs[i]) == CanonGlyphKind.Glyph && target[i].width > 0 && at.y in target[i].top - half..target[i].bottom + half }
            .minByOrNull(::distance)
            ?.takeIf { distance(it) <= slopPx }
    }

    internal fun resize(size: IntSize, topInset: Float, density: Float) {
        if (size == this.size && topInset == this.topInset && density == this.density) return
        this.size = size
        this.topInset = topInset
        this.density = density
        relayout()
    }

    // damped spring, one overshoot
    private val eased get() = if (progress >= 1f) 1f else 1f - exp(-7f * progress) * cos(8f * progress)

    private fun presentedFrames() = target.indices.map(::presented)

    internal fun presented(i: Int): Rect {
        if (progress >= 1f || i !in start.indices || appearing[i]) return target[i]
        val e = eased
        val from = start[i]
        val to = target[i]
        return Rect(
            from.left + (to.left - from.left) * e,
            from.top + (to.top - from.top) * e,
            from.right + (to.right - from.right) * e,
            from.bottom + (to.bottom - from.bottom) * e,
        )
    }

    internal fun isAppearing(i: Int) = progress < 1f && i in appearing.indices && appearing[i]

    internal val grow get() = eased

    private fun relayout() {
        if (progress >= 1f) {
            target = layoutFrames()
            start = target
            version += 1
        } else {
            settle(presentedFrames())
        }
    }

    private fun settle(shown: List<Rect>) {
        target = layoutFrames()
        if (shown == target) {
            version += 1
            return
        }
        if (!attached || !ValueAnimator.areAnimatorsEnabled() || shown.size != target.size) return stop()
        start = shown
        appearing = List(target.size) { false }
        ghosts = emptyList()
        animate()
    }

    private fun layoutFrames(): List<Rect> {
        val area = contentArea
        rowWindow = area
        if (area.width <= 0f || glyphs.isEmpty()) return glyphs.map { Rect.Zero }
        val height = metrics.height.value * density
        val rowGap = metrics.rowGap.value * density
        val pitch = height + rowGap
        val (frames, rows) = CanonGlyphRows.layout(
            glyphs.map { measure(it).value * density }, glyphs.map(kind), area.width, metrics.gap.value * density, height, pitch,
        )
        val fit = max(1, ((area.height + rowGap) / pitch).toInt())
        val focusRow = focus?.takeIf { it in frames.indices }?.let { (frames[it].top / pitch).roundToInt() - if (fit > 2) 1 else 0 }
        val first = if (rows <= fit) 0 else (focusRow ?: (rows - fit)).coerceIn(0, rows - fit)
        val block = min(rows, fit) * pitch - rowGap
        val y = area.top + max(0f, (area.height - block) / 2) - first * pitch
        rowWindow = Rect(0f, y + first * pitch - rowGap / 2, size.width.toFloat(), y + first * pitch + block + rowGap / 2)
        val x = if (rows == 1) area.left + (area.width - (frames.maxOfOrNull { it.right } ?: 0f)) / 2 else area.left
        return frames.map { it.translate(x, y) }
    }

    private fun animate() {
        progress = 0f
        run += 1
    }

    internal fun stop() {
        progress = 1f
        ghosts = emptyList()
        start = target
        appearing = List(target.size) { false }
        version += 1
    }
}

@Composable
fun <G> rememberCanonGlyphFlowState(
    metrics: CanonGlyphMetrics,
    measure: (G) -> Dp,
    kind: (G) -> CanonGlyphKind,
): CanonGlyphFlowState<G> = remember { CanonGlyphFlowState(metrics, measure, kind) }.also { it.metrics = metrics }

/**
 * Draws the state's glyphs; `paint` draws one glyph into its frame, with a null index for a glyph fading out.
 * `paintEmpty` draws the picture with no glyphs into the content area. Inside a CanonCanvas it keeps clear of the canvas top inset.
 */
@Composable
fun <G> CanonGlyphFlow(
    state: CanonGlyphFlowState<G>,
    modifier: Modifier = Modifier,
    paintEmpty: (DrawScope.(area: Rect) -> Unit)? = null,
    paint: DrawScope.(glyph: G, index: Int?, frame: Rect) -> Unit,
) {
    val density = LocalDensity.current
    val topInset = with(density) { LocalCanonCanvas.current.top.toPx() }
    DisposableEffect(state) {
        state.attached = true
        onDispose {
            state.attached = false
            state.stop()
        }
    }
    LaunchedEffect(state.run) {
        if (state.progress >= 1f) return@LaunchedEffect
        val began = withFrameNanos { it }
        while (state.progress < 1f) {
            withFrameNanos { now -> state.progress = min(1f, (now - began).toFloat() / SETTLE_NANOS) }
        }
        state.stop()
    }
    val layer = remember { Paint() }
    Spacer(
        modifier
            .drawWithContent {
                state.resize(IntSize(size.width.toInt(), size.height.toInt()), topInset, density.density)
                val progress = state.progress
                state.version
                fun faded(alpha: Float, frame: Rect, body: DrawScope.() -> Unit) {
                    if (alpha >= 1f) return body()
                    layer.alpha = alpha
                    drawIntoCanvas { it.saveLayer(frame.inflate(frame.height), layer) }
                    body()
                    drawIntoCanvas { it.restore() }
                }
                clipRect(state.rowWindow.left, state.rowWindow.top, state.rowWindow.right, state.rowWindow.bottom) {
                    if (state.glyphs.isEmpty() && state.ghosts.isEmpty()) {
                        paintEmpty?.invoke(this, state.contentArea)
                        return@clipRect
                    }
                    val fade = max(0f, 1f - progress * 3f)
                    if (fade > 0f) {
                        state.ghosts.forEach { (glyph, frame) -> faded(fade, frame) { paint(glyph, null, frame) } }
                    }
                    val rise = min(1f, progress * 2.5f)
                    val clip = state.rowWindow
                    state.glyphs.forEachIndexed { i, glyph ->
                        val frame = state.presented(i)
                        if (frame.width <= 0f || !frame.overlaps(clip)) return@forEachIndexed
                        if (state.isAppearing(i)) {
                            faded(rise, frame) {
                                scale(0.55f + 0.45f * state.grow, frame.center) { paint(glyph, i, frame) }
                            }
                        } else {
                            paint(glyph, i, frame)
                        }
                    }
                }
            },
    )
}
