package com.delphi.delphitools.canon

import android.graphics.Paint
import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.delphi.delphitools.R
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.platform.ComposeView
import android.view.ViewGroup
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.canon.math.CanonMathExpression
import com.delphi.delphitools.canon.math.CanonMathKey
import com.delphi.delphitools.canon.math.CanonMathPlace
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG)

@Composable
internal fun mathInk(): (MathInk) -> Color {
    val c = LocalDelphiColours.current
    return { ink ->
        when (ink) {
            MathInk.Input -> c.mutedForeground
            MathInk.Result -> c.foreground
            MathInk.Accent -> c.primary
        }
    }
}

/** Draws [atom] at [frame] in pixels, a frame the atom may be moving through; [scale] is pixels per dp for stroke weights. */
internal fun DrawScope.drawMathAtom(atom: MathAtom, frame: Rect, scale: Float, colour: Color) {
    val weight = atom.weight * scale
    when (val mark = atom.mark) {
        is MathMark.Glyphs -> {
            if (frame.height <= 0f) return
            glyphPaint.typeface = mathTypeface(mark.italic)
            glyphPaint.textSize = frame.height
            glyphPaint.color = colour.toArgb()
            drawIntoCanvas { it.nativeCanvas.drawText(mark.text, frame.left, frame.top, glyphPaint) }
        }
        MathMark.Rule -> drawRect(colour, frame.topLeft, frame.size)
        is MathMark.Paren -> {
            val near = if (mark.open) frame.right else frame.left
            val far = if (mark.open) frame.left else frame.right
            val inset = frame.width * 0.15f * (if (mark.open) -1 else 1)
            val path = Path().apply {
                moveTo(near + inset, frame.top)
                quadraticTo(far - inset * 2, frame.center.y, near + inset, frame.bottom)
            }
            drawPath(path, colour, style = Stroke(weight, cap = StrokeCap.Round))
        }
        is MathMark.Corner -> {
            val stem = if (mark.open) frame.left + frame.width * 0.3f else frame.right - frame.width * 0.3f
            val foot = if (mark.open) frame.right else frame.left
            val start = if (mark.floor) frame.top else frame.bottom
            val end = if (mark.floor) frame.bottom else frame.top
            val path = Path().apply {
                moveTo(stem, start)
                lineTo(stem, end)
                lineTo(foot, end)
            }
            drawPath(path, colour, style = Stroke(weight, join = StrokeJoin.Miter))
        }
        MathMark.Radical -> {
            val w = frame.width
            val h = frame.height
            val path = Path().apply {
                moveTo(frame.left, frame.top + h * 0.6f)
                lineTo(frame.left + w * 0.22f, frame.top + h * 0.5f)
                lineTo(frame.left + w * 0.5f, frame.bottom)
                lineTo(frame.right, frame.top)
            }
            drawPath(path, colour, style = Stroke(weight, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        MathMark.Hole -> {
            val inner = frame.deflate(weight / 2)
            drawRect(
                colour, inner.topLeft, inner.size,
                style = Stroke(weight, pathEffect = PathEffect.dashPathEffect(floatArrayOf(weight * 2, weight * 1.5f))),
            )
        }
    }
}

private fun Rect.scaled(k: Float, origin: Offset) =
    Rect(origin.x + left * k, origin.y + top * k, origin.x + right * k, origin.y + bottom * k)

/** A typeset box drawn at its own size, scaled down to fit a smaller canvas, centred. */
internal fun DrawScope.drawMathBox(box: MathBox, colour: (MathInk) -> Color) {
    if (box.width <= 0f || box.height <= 0f) return
    val fit = minOf(1f, size.width / (box.width * density), size.height / (box.height * density))
    val k = density * fit
    val origin = Offset((size.width - box.width * k) / 2, (size.height - box.height * k) / 2 + box.ascent * k)
    for (atom in box.atoms) drawMathAtom(atom, atom.frame.scaled(k, origin), k, colour(atom.ink))
}

/** Expression [text] in nerdamer's syntax, typeset at [size]; scaled down to fit a narrower space. */
@Composable
fun CanonMathView(text: String, modifier: Modifier = Modifier, size: Dp = 22.dp, variable: String = "x", alpha: Float = 1f) {
    val box = remember(text, variable, size) {
        MathTypesetter(size.value).layout(CanonMathExpression(text, variable).root, variable = variable)
    }
    val ink = mathInk()
    Canvas(modifier.size(box.width.dp, box.height.dp).graphicsLayer { this.alpha = alpha }) { drawMathBox(box, ink) }
}

internal class MathPlaced(val atom: MathAtom, val frame: Rect, val alpha: Float)

/**
 * Typeset atoms moving from one arrangement to the next: an atom whose key stays slides and resizes on a damped spring,
 * a new one grows in, a removed one fades.
 */
internal class MathMorph {
    private class State(val order: List<String>, val from: Map<String, MathPlaced>, val target: Map<String, MathPlaced>)

    private var state by mutableStateOf(State(emptyList(), emptyMap(), emptyMap()))
    // ponytail: does not check reduced motion; iOS lands at once there
    private val progress = Animatable(1f)

    suspend fun show(next: List<Pair<String, MathPlaced>>, animated: Boolean) {
        val target = LinkedHashMap<String, MathPlaced>().apply { next.forEach { (key, placed) -> putIfAbsent(key, placed) } }
        if (!animated) {
            progress.snapTo(1f)
            state = State(next.map { it.first }, emptyMap(), target)
            return
        }
        val from = shown()
        state = State(from.keys.filter { it !in target }.sorted() + next.map { it.first }, from, target)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(SETTLE_MILLIS, easing = LinearEasing))
    }

    /** Re-keys what is shown, so the next [show] matches atoms under their new keys. */
    fun rename(transform: (String) -> String) {
        val s = state
        state = State(s.order.map(transform), s.from.mapKeys { transform(it.key) }, s.target.mapKeys { transform(it.key) })
    }

    /** Placed atoms in drawing order at the current progress: leaving atoms first, then the target's. */
    fun shown(): Map<String, MathPlaced> {
        val s = state
        val p = progress.value
        if (p >= 1f) return s.order.mapNotNull { key -> s.target[key]?.let { key to it } }.toMap()
        // damped spring, one overshoot
        val eased = 1 - exp(-7 * p) * cos(9 * p)
        val appear = ((p - 0.12f) / 0.5f).coerceIn(0f, 1f)
        val vanish = max(0f, 1 - p * 2.5f)
        val result = LinkedHashMap<String, MathPlaced>()
        for (key in s.order) {
            val old = s.from[key]
            val new = s.target[key]
            result[key] = when {
                old != null && new != null ->
                    MathPlaced(new.atom, mix(old.frame, new.frame, eased), old.alpha + (new.alpha - old.alpha) * p)
                new != null -> MathPlaced(new.atom, grown(new, 0.6f + 0.4f * eased), new.alpha * appear)
                old != null -> MathPlaced(old.atom, grown(old, 1 - 0.2f * p), old.alpha * vanish)
                else -> continue
            }
        }
        return result
    }

    fun draw(scope: DrawScope, colour: (MathInk) -> Color) {
        val k = scope.density
        for (placed in shown().values) {
            if (placed.alpha <= 0.01f) continue
            scope.drawMathAtom(placed.atom, placed.frame.scaled(k, Offset.Zero), k, colour(placed.atom.ink).copy(alpha = placed.alpha))
        }
    }

    companion object {
        const val SETTLE_MILLIS = 550

        /** Keys each atom by its mark and the count of that mark before it, so a glyph that stays keeps its key. */
        fun keyed(atoms: List<MathAtom>, prefix: String, place: (MathAtom) -> MathPlaced): List<Pair<String, MathPlaced>> {
            val seen = HashMap<String, Int>()
            return atoms.map { atom ->
                val base = prefix + "|" + key(atom.mark)
                val count = seen[base] ?: 0
                seen[base] = count + 1
                "$base#$count" to place(atom)
            }
        }

        private fun key(mark: MathMark) = when (mark) {
            is MathMark.Glyphs -> "g" + (if (mark.italic) "i" else "u") + mark.text
            MathMark.Rule -> "rule"
            is MathMark.Paren -> if (mark.open) "(" else ")"
            is MathMark.Corner -> (if (mark.floor) "floor" else "ceil") + (if (mark.open) "(" else ")")
            MathMark.Radical -> "root"
            MathMark.Hole -> "hole"
        }

        private fun mix(a: Rect, b: Rect, t: Float) = Rect(
            a.left + (b.left - a.left) * t,
            a.top + (b.top - a.top) * t,
            a.left + (b.left - a.left) * t + max(0f, a.width + (b.width - a.width) * t),
            a.top + (b.top - a.top) * t + max(0f, a.height + (b.height - a.height) * t),
        )

        /** A glyph run grows about its centre on the baseline, a shape about its centre. */
        private fun grown(placed: MathPlaced, scale: Float): Rect {
            val f = placed.frame
            if (placed.atom.mark is MathMark.Glyphs) {
                val left = f.left + f.width * (1 - scale) / 2
                return Rect(left, f.top, left + f.width * scale, f.top + f.height * scale)
            }
            val w = f.width * scale / 2
            val h = f.height * scale / 2
            return Rect(f.center.x - w, f.center.y - h, f.center.x + w, f.center.y + h)
        }
    }
}

/** The expression being edited and its change count, which the editor redraws on. */
internal class MathEditing(val expression: CanonMathExpression, private val onChange: (String) -> Unit) {
    var version by mutableIntStateOf(0)
        private set
    var animateNext = false

    /** False when the key changed neither the text nor the caret. */
    fun apply(key: CanonMathKey): Boolean {
        val text = expression.text
        val caret = expression.caret
        expression.apply(key)
        val changed = expression.text != text
        if (!changed && expression.caret == caret) return false
        animateNext = true
        version++
        if (changed) onChange(expression.text)
        return true
    }

    fun place(caret: CanonMathPlace) {
        expression.caret = caret
        version++
    }
}

private const val EDITOR_PADDING = 16f
private const val BLINK_MILLIS = 530L

/** The expression typeset with a blinking caret, scrolling sideways. A tap moves the caret to the nearest place; held, it follows the finger. */
@Composable
private fun MathEditor(editing: MathEditing, height: Dp, size: Float) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val ink = mathInk()
    val expression = editing.expression
    val version = editing.version
    val box = remember(version) { MathTypesetter(size).layout(expression.root, caret = expression.caret.row, variable = expression.variable) }
    val origin = Offset(EDITOR_PADDING, (height.value - box.height) / 2 + box.ascent)
    val stop = box.carets.firstOrNull { it.row === expression.caret.row && it.index == expression.caret.index }?.rect ?: Rect.Zero
    val caret = stop.translate(origin).let { if (it.width > 0f) it else Rect(it.left - 1, it.top, it.right + 1, it.bottom) }
    val morph = remember { MathMorph() }
    LaunchedEffect(box, height) {
        val animated = editing.animateNext
        editing.animateNext = false
        morph.show(MathMorph.keyed(box.atoms, "edit") { MathPlaced(it, it.frame.translate(origin), 1f) }, animated)
    }
    var caretShown by remember { mutableStateOf(true) }
    LaunchedEffect(version) {
        caretShown = true
        while (true) {
            delay(BLINK_MILLIS)
            caretShown = !caretShown
        }
    }
    val scroll = rememberScrollState()
    val density = LocalDensity.current.density
    val latestBox by rememberUpdatedState(box)
    val latestOrigin by rememberUpdatedState(origin)
    fun moveCaret(point: Offset) {
        val p = point / density
        fun distance(rect: Rect): Float {
            val f = rect.translate(latestOrigin)
            val dy = if (p.y < f.top) f.top - p.y else max(0f, p.y - f.bottom)
            val dx = if (p.x < f.left) f.left - p.x else max(0f, p.x - f.right)
            // vertical counts double, so a tap picks its own line of a fraction
            return dx + dy * 2
        }
        val target = latestBox.carets.minByOrNull { distance(it.rect) } ?: return
        val place = CanonMathPlace(target.row, target.index)
        if (place == expression.caret) return
        editing.place(place)
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(c.card)
            .semantics {
                contentDescription = "Expression"
                stateDescription = expression.text
            }
    ) {
        val viewport = maxWidth
        LaunchedEffect(caret, viewport) {
            val left = ((caret.left - 24) * density).roundToInt()
            val right = ((caret.right + 24) * density).roundToInt()
            val width = (viewport.value * density).roundToInt()
            if (left < scroll.value) scroll.animateScrollTo(left)
            else if (right > scroll.value + width) scroll.animateScrollTo(right - width)
        }
        // ponytail: no vertical scroll; an expression taller than the display is clipped
        Box(Modifier.horizontalScroll(scroll)) {
            Canvas(
                Modifier
                    .size(max(viewport.value, box.width + EDITOR_PADDING * 2 + 2).dp, height)
                    .pointerInput(Unit) { detectTapGestures { moveCaret(it) } }
                    .pointerInput(Unit) { detectDragGesturesAfterLongPress(onDragStart = { moveCaret(it) }) { change, _ -> moveCaret(change.position) } }
            ) {
                morph.draw(this, ink)
                if (caretShown) {
                    // empty slot fills faintly
                    drawRect(c.primary.copy(alpha = if (caret.width > 2) 0.22f else 1f), caret.topLeft * density, caret.size * density)
                }
            }
        }
    }
}

/** Lets an insert key beside a [CanonMathField] open its keypad and type. */
class CanonMathFieldHandle {
    internal var open: ((List<CanonMathKey>) -> Unit)? = null

    /** Opens the field's keypad sheet, then types [keys]. */
    fun edit(keys: List<CanonMathKey> = emptyList()) {
        open?.invoke(keys)
    }
}

/**
 * An expression typeset in a flush cell; a tap opens the keypad sheet. [onValueChange] gets every edit made there and
 * [onDone] the text when the sheet closes. [text] is nerdamer's syntax; [variable] is the letter the keypad offers first
 * and the variable of derivatives, integrals and limits; [placeholder] shows faint while [text] is empty.
 */
@Composable
fun CanonMathField(
    text: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    variable: String = "x",
    placeholder: String = "",
    handle: CanonMathFieldHandle? = null,
    size: Dp = 22.dp,
    onDone: (String) -> Unit = {},
) {
    val c = LocalDelphiColours.current
    // ponytail: an open sheet closes on a configuration change; no keyboard input, no debug key scripts
    var session by remember { mutableStateOf<List<CanonMathKey>?>(null) }
    var centre by remember { mutableFloatStateOf(Float.NaN) }
    DisposableEffect(handle) {
        handle?.open = { keys -> if (session == null) session = keys }
        onDispose { handle?.open = null }
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = CanonMetrics.rowHeight + 12.dp)
            .onGloballyPositioned { centre = it.boundsInWindow().center.x }
            .canonClickable { if (session == null) session = emptyList() }
            .background(if (session != null) c.accentBg else c.card)
            .semantics {
                contentDescription = "Expression"
                stateDescription = text
            }
            .padding(horizontal = CanonMetrics.panelPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            CanonMathView(text.ifEmpty { placeholder }, size = size, variable = variable, alpha = if (text.isEmpty()) 0.35f else 1f)
        }
        Icon(pencil(R.drawable.pencil_keyboard), contentDescription = null, tint = c.mutedForeground, modifier = Modifier.size(16.dp))
    }
    session?.let { keys ->
        MathSheet(text, variable, keys, centre, onValueChange) { final ->
            session = null
            onDone(final)
        }
    }
}

private const val SHORT_SCREEN = 800f
private const val WIDE_SCREEN = 600f
private const val MAX_SHEET_WIDTH = 560f
private const val SHEET_GAP = 12f
private const val CLOSING_DISTANCE = 100f
private const val CLOSING_SPEED = 900f

/** How far the sheet sinks over a whole back gesture, in dp. */
private const val BACK_TRAVEL = 120f

/**
 * Content over the whole window, in an overlay view added to the activity's own content view: a back gesture that starts
 * over a popup window reports no progress (API 37). Touches outside any pointer input fall through to the tool below.
 */
@Composable
internal fun MathOverlay(content: @Composable BoxScope.() -> Unit) {
    val view = LocalView.current
    val parent = rememberCompositionContext()
    val latest by rememberUpdatedState(content)
    DisposableEffect(view, parent) {
        val root = view.rootView.findViewById<ViewGroup>(android.R.id.content)
        val overlay = ComposeView(view.context).apply {
            setParentCompositionContext(parent)
            setContent { Box(Modifier.fillMaxSize()) { latest(this) } }
        }
        root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        onDispose { root.removeView(overlay) }
    }
}

/** A clear layer that calls [onTouch] on a touch: the tap outside of a sheet or panel. */
@Composable
internal fun MathScrim(onTouch: () -> Unit) {
    val touch by rememberUpdatedState(onTouch)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    touch()
                }
            }
    )
}

private fun editorHeight(short: Boolean) = if (short) 84f else 112f

/** Height of the sheet's content, without the bottom inset. */
private fun sheetContentHeight(short: Boolean) =
    CanonMetrics.panelPadding.value * 2 + CanonMetrics.frame.value * 4 + editorHeight(short) + CanonMetrics.hairline.value +
        mathRailHeight(short) + SHEET_GAP + mathKeypadHeight(short)

/**
 * The expression over its caret rail and the keypad, as a sheet. Compact width: the tool above stays usable, a drag up
 * gives the expression room. Wide: up to 560 dp, a tap outside closes it. Done, a drag down or the back gesture close either.
 */
@Composable
private fun MathSheet(text: String, variable: String, keys: List<CanonMathKey>, centre: Float, onChange: (String) -> Unit, onClose: (String) -> Unit) {
    val c = LocalDelphiColours.current
    val densityScope = LocalDensity.current
    val density = densityScope.density
    val window = LocalWindowInfo.current.containerSize
    val windowWidth = window.width / density
    val windowHeight = window.height / density
    val wide = windowWidth >= WIDE_SCREEN
    val short = windowHeight < SHORT_SCREEN
    val latestChange by rememberUpdatedState(onChange)
    val editing = remember { MathEditing(CanonMathExpression(text, variable)) { latestChange(it) }.also { e -> keys.forEach { e.expression.apply(it) } } }
    LaunchedEffect(Unit) { if (keys.isNotEmpty()) latestChange(editing.expression.text) }
    val bottomInset = WindowInsets.navigationBars.getBottom(densityScope) / density
    val topInset = WindowInsets.statusBars.getTop(densityScope) / density
    val small = sheetContentHeight(short) + bottomInset
    val large = if (wide) small else max(small, windowHeight - topInset - bottomInset - 32f)
    var height by remember { mutableFloatStateOf(small) }
    var offset by remember { mutableFloatStateOf(small) }
    var travel by remember { mutableFloatStateOf(0f) }
    var backFade by remember { mutableFloatStateOf(0f) }
    var finished by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val latestClose by rememberUpdatedState(onClose)
    LaunchedEffect(Unit) { animate(offset, 0f, animationSpec = spring(0.86f, 400f)) { v, _ -> offset = v } }
    fun close() {
        if (finished) return
        finished = true
        settling?.cancel()
        scope.launch {
            animate(offset, height, animationSpec = tween(220)) { v, _ -> offset = v }
            latestClose(editing.expression.text)
        }
    }
    PredictiveBackHandler(enabled = !finished) { events ->
        settling?.cancel()
        try {
            events.collect {
                offset = it.progress * BACK_TRAVEL
                backFade = it.progress
            }
            close()
        } catch (cancelled: CancellationException) {
            settling = scope.launch {
                launch { animate(backFade, 0f, animationSpec = spring(0.8f, 300f)) { v, _ -> backFade = v } }
                animate(offset, 0f, animationSpec = spring(0.8f, 300f)) { v, _ -> offset = v }
            }
            throw cancelled
        }
    }
    // ponytail: docks under the field's centre, not its pane, and is not kept clear of a crease
    val width = if (wide) min(MAX_SHEET_WIDTH, windowWidth) else windowWidth
    val room = (window.width - width * density).roundToInt().coerceAtLeast(0)
    val left = if (wide && !centre.isNaN()) (centre - width * density / 2).roundToInt().coerceIn(0, room) else room / 2
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    MathOverlay {
        if (wide) MathScrim { close() }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset(left, 0) }
                .width(width.dp)
                .height(height.dp)
                .graphicsLayer {
                    translationY = offset * density
                    alpha = 1 - 0.4f * backFade
                }
                .clip(shape)
                .background(c.background)
                .border(CanonMetrics.hairline, c.border, shape)
                .draggable(
                    rememberDraggableState { delta ->
                        val dy = delta / density
                        travel += dy
                        when {
                            // resists upwards
                            wide -> offset = if (travel > 0) travel else travel / 6
                            offset > 0f || (height <= small && dy > 0) -> offset = max(0f, offset + dy)
                            else -> height = (height - dy).coerceIn(small, large)
                        }
                    },
                    Orientation.Vertical,
                    onDragStarted = {
                        travel = 0f
                        settling?.cancel()
                    },
                    onDragStopped = { velocity ->
                        val speed = velocity / density
                        if (offset > CLOSING_DISTANCE || (speed > CLOSING_SPEED && height <= small + 1)) {
                            close()
                        } else {
                            val detent = when {
                                speed < -500 -> large
                                speed > 500 -> small
                                else -> if (height - small < large - height) small else large
                            }
                            settling = scope.launch {
                                launch { animate(offset, 0f, animationSpec = spring(0.8f, 300f)) { v, _ -> offset = v } }
                                animate(height, detent, animationSpec = spring(0.86f, 300f)) { v, _ -> height = v }
                            }
                        }
                    },
                )
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 5.dp)
                    .size(36.dp, 5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(c.border)
            )
            Column(
                Modifier.padding(
                    start = CanonMetrics.panelPadding,
                    end = CanonMetrics.panelPadding,
                    top = CanonMetrics.panelPadding,
                    bottom = CanonMetrics.panelPadding + bottomInset.dp,
                )
            ) {
                CanonRows {
                    MathEditor(editing, (editorHeight(short) + height - small).dp, if (short) 26f else 30f)
                    MathRail(short) { editing.apply(it) }
                }
                Spacer(Modifier.height(SHEET_GAP.dp))
                CanonRows {
                    CanonMathKeypad(onKey = { editing.apply(it) }, onDone = ::close, variable = variable, isShort = short)
                }
            }
        }
    }
}
