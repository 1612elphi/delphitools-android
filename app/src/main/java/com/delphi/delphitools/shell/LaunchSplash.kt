package com.delphi.delphitools.shell

import android.animation.ValueAnimator
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** A mascot that swaps into the launch grid now and then, in its own colour. */
data class LaunchGuest(val name: String, @DrawableRes val drawable: Int, val colour: Color)

// dt-ios Shell/LaunchSplash.swift guests, colours as drawn
val launchGuests: List<LaunchGuest> = listOf(
    LaunchGuest("mascot-emma", R.drawable.mascot_emma, Color(97, 61, 125)),
    LaunchGuest("mascot-vito", R.drawable.mascot_vito, Color(212, 84, 0)),
    LaunchGuest("mascot-alien-delphi", R.drawable.mascot_alien_delphi, Color(23, 75, 18)),
)

/** The colour as drawn; on a dark ground lifted halfway to cream so the green reads. */
fun LaunchGuest.drawnColour(dark: Boolean): Color {
    if (!dark) return colour
    return Color(colour.red * 0.5f + 0.922f * 0.5f, colour.green * 0.5f + 0.894f * 0.5f, colour.blue * 0.5f + 0.824f * 0.5f)
}

private val word = listOf("DELPHI", ".TOOLS")
private val glyphDrawables = mapOf(
    "D" to R.drawable.launch_letter_d,
    "E" to R.drawable.launch_letter_e,
    "L" to R.drawable.launch_letter_l,
    "P" to R.drawable.launch_letter_p,
    "H" to R.drawable.launch_letter_h,
    "I" to R.drawable.launch_letter_i,
    "T" to R.drawable.launch_letter_t,
    "O" to R.drawable.launch_letter_o,
    "O-2" to R.drawable.launch_letter_o_2,
    "L-2" to R.drawable.launch_letter_l_2,
    "S" to R.drawable.launch_letter_s,
    "dot" to R.drawable.mascot_delphi,
)

// share of the grid that swaps each second; the rest stays still
private const val SWAP_SHARE = 0.08f

// share of swaps that bring in a mascot: about 1 launch in 5 on an iPhone
private const val MASCOT_CHANCE = 1f / 80
private const val SWAP_END = 1.8f
private const val CONTRACT_AT = SWAP_END + 0.45f
private const val END_AT = SWAP_END + 1.8f
private const val REDUCED_END_MS = 800L
private const val FADE_MS = 350

/** Glyph names per line: the letter, "dot" for a full stop, "" for a space; a letter's second appearance is "<letter>-2". */
internal fun glyphNames(lines: List<String>): List<List<String>> {
    val seen = mutableSetOf<Char>()
    return lines.map { line ->
        line.map {
            when (it) {
                ' ' -> ""
                '.' -> "dot"
                else -> if (seen.add(it)) "$it" else "$it-2"
            }
        }
    }
}

/**
 * Square cells across the width, even counts both ways so the 6 × 2 block sits dead centre; rows overflow top and bottom
 * equally. Sizes in px.
 */
internal class SplashGrid(width: Float, height: Float, minSide: Float) {
    val columns: Int
    val rows: Int
    val side: Float
    val top: Float

    init {
        val target = max(minSide, min(width, height) / 9)
        var c = max(8, (width / target).toInt())
        c -= c % 2
        side = width / c
        var r = ceil(height / side).toInt()
        r += r % 2
        columns = c
        rows = r
        top = (height - r * side) / 2
    }

    fun centre(index: Int) = Offset((index % columns + 0.5f) * side, top + (index / columns + 0.5f) * side)

    /** Row and column inside the 6 × 2 block, if the cell is in it. */
    fun blockPosition(index: Int): Pair<Int, Int>? {
        val column = index % columns - (columns - 6) / 2
        val row = index / columns - (rows - 2) / 2
        return if (column in 0 until 6 && row in 0 until 2) row to column else null
    }
}

/**
 * Cold-launch splash. A still grid of tool symbols swaps a few cells at a time while the centre 6 × 2 block locks into
 * DELPHI / .TOOLS, Delphi standing in for the dot; the words close up, then the splash fades out. Now and then a guest
 * swaps in. A tap skips to the fade.
 */
@Composable
fun LaunchSplash(onFinished: () -> Unit) {
    val c = LocalDelphiColours.current
    val dark = c.background.luminance() < 0.5f
    val finished by rememberUpdatedState(onFinished)
    val resources = LocalResources.current
    val symbols = remember { allTools.map { it.icon }.distinct() }
    val painters = symbols.map { rememberVectorPainter(pencil(it)) }
    val letters = remember { glyphDrawables.mapValues { ImageBitmap.imageResource(resources, it.value) } }
    val guests = remember { launchGuests.map { ImageBitmap.imageResource(resources, it.drawable) } }
    val scope = rememberCoroutineScope()
    val fade = remember { Animatable(1f) }
    val contract = remember { Animatable(0f) }
    var now by remember { mutableFloatStateOf(0f) }
    var ending by remember { mutableStateOf(false) }
    val end: () -> Unit = {
        if (!ending) {
            ending = true
            scope.launch {
                fade.animateTo(0f, tween(FADE_MS, easing = EaseIn))
                finished()
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(c.background)
            .clickable(interactionSource = null, indication = null) { end() }
            .semantics { contentDescription = "delphitools" }
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val minSide = with(LocalDensity.current) { 44.dp.toPx() }
        val grid = remember(width, height) { SplashGrid(width, height, minSide) }
        val count = grid.columns * grid.rows
        val names = remember { glyphNames(word) }
        // cell → glyph it locks to, and the word cells per line, left to right
        val cellGlyph = remember(grid) { Array(count) { index -> grid.blockPosition(index)?.let { (row, column) -> names[row].getOrElse(column) { "" } } } }
        val lines = remember(grid) { List(2) { row -> (0 until count).filter { grid.blockPosition(it)?.first == row } } }
        // a symbol index, or -2 - guest for a guest
        val shown = remember(grid) { IntArray(count) { Random.nextInt(symbols.size) } }
        val lockAt = remember(grid) { FloatArray(count) { Random.nextFloat() + 0.7f } }
        val fadeDelay = remember(grid) { FloatArray(count) { Random.nextFloat() * 0.35f } }
        val reduced = remember { !ValueAnimator.areAnimatorsEnabled() }
        LaunchedEffect(grid) {
            if (reduced) {
                now = END_AT
                contract.snapTo(1f)
                delay(REDUCED_END_MS)
                end()
                return@LaunchedEffect
            }
            launch {
                delay((CONTRACT_AT * 1000).toLong())
                contract.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 120f))
            }
            var budget = 0f
            val start = withFrameNanos { it }
            var last = start
            while (now < END_AT) {
                val frame = withFrameNanos { it }
                val t = (frame - start) / 1e9f
                if (t < SWAP_END) {
                    budget += SWAP_SHARE * count * (frame - last) / 1e9f
                    while (budget >= 1) {
                        budget -= 1
                        val index = Random.nextInt(count)
                        // locked letters stay put
                        if (cellGlyph[index] != null && t >= lockAt[index]) continue
                        shown[index] = if (Random.nextFloat() < MASCOT_CHANCE) -2 - Random.nextInt(guests.size) else Random.nextInt(symbols.size)
                    }
                }
                last = frame
                now = t
            }
            end()
        }

        Canvas(Modifier.fillMaxSize()) {
            val t = now
            val letterSize = grid.side * 0.8f
            fun glyphSize(name: String): IntSize {
                val image = letters.getValue(name)
                val h = if (name == "dot") letterSize * 0.8f else letterSize
                return IntSize((image.width * h / image.height).roundToInt(), h.roundToInt())
            }
            // closed up: each line's glyphs side by side, centred, the two lines a line apart
            val closed = arrayOfNulls<Offset>(count)
            for ((row, line) in lines.withIndex()) {
                val glyphs = line.filter { cellGlyph[it]!!.isNotEmpty() }
                val gap = letterSize * 0.08f
                val widths = glyphs.map { glyphSize(cellGlyph[it]!!).width.toFloat() }
                var x = size.width / 2 - (widths.sum() + gap * (glyphs.size - 1)) / 2
                val y = size.height / 2 + (row - 0.5f) * letterSize * 1.1f
                for ((index, w) in glyphs.zip(widths)) {
                    closed[index] = Offset(x + w / 2, y)
                    x += w + gap
                }
            }
            for (index in 0 until count) {
                val centre = grid.centre(index)
                val glyph = cellGlyph[index]
                if (glyph != null && (t >= lockAt[index] || reduced)) {
                    if (glyph.isEmpty()) continue
                    val target = closed[index] ?: centre
                    val at = centre + (target - centre) * contract.value
                    val glyphSize = glyphSize(glyph)
                    drawImage(
                        letters.getValue(glyph),
                        dstOffset = IntOffset((at.x - glyphSize.width / 2).roundToInt(), (at.y - glyphSize.height / 2).roundToInt()),
                        dstSize = glyphSize,
                        colorFilter = ColorFilter.tint(c.foreground),
                    )
                    continue
                }
                val alpha = if (reduced) 0f else 1f - ((t - SWAP_END - fadeDelay[index]) / 0.25f).coerceIn(0f, 1f)
                if (alpha <= 0f) continue
                val pick = shown[index]
                if (pick >= 0) {
                    val side = grid.side * 0.45f
                    translate(centre.x - side / 2, centre.y - side / 2) {
                        with(painters[pick]) { draw(Size(side, side), alpha, ColorFilter.tint(c.mutedForeground)) }
                    }
                } else {
                    val guest = -2 - pick
                    val image = guests[guest]
                    val h = grid.side * 0.6f
                    val w = image.width * h / image.height
                    drawImage(
                        image,
                        dstOffset = IntOffset((centre.x - w / 2).roundToInt(), (centre.y - h / 2).roundToInt()),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                        alpha = alpha,
                        colorFilter = ColorFilter.tint(launchGuests[guest].drawnColour(dark)),
                    )
                }
            }
        }
    }
}
