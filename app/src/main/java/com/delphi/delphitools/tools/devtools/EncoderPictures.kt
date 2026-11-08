package com.delphi.delphitools.tools.devtools

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGlyphFlow
import com.delphi.delphitools.canon.CanonGlyphKind
import com.delphi.delphitools.canon.CanonGlyphMetrics
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.rememberCanonGlyphFlowState
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos

private val labelRow = 17.dp
private val bracketRoom = 7.dp
private val bitHeight = 16.dp
private val regroupRow = labelRow * 2 + bracketRoom * 2 + bitHeight
private const val PITCH_LEAST = 3.6f
private const val PITCH_MOST = 7f
private const val GROUP_GAP = 12f
private val FED_ROWS = 4..12
private val labelFont = TextStyle(fontFamily = DelphiFont, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
private val codeFont = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp)

private class RegroupFeed {
    var pitch = 5f
    var firstRow = 0
    var perRow = 0
    var width = 0f
}

/**
 * dt-ios BitRegroupStrip: the input's units over their bits, the bits, the output's units under them. A one is a full
 * bar, a zero a stub, a padding bit a grey stub; bits and output characters alternate in colour so the regrouping shows.
 */
@Composable
fun BitRegroupStrip(reading: BitRegroup.Reading, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val measurer = rememberTextMeasurer()
    val feed = remember { RegroupFeed() }
    val state = rememberCanonGlyphFlowState<BitRegroup.Group>(
        CanonGlyphMetrics(regroupRow, 12.dp, GROUP_GAP.dp),
        measure = { (it.slots.size * feed.pitch - 1).dp },
        kind = { CanonGlyphKind.Glyph },
    )
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = (maxWidth - CanonMetrics.panelPadding * 2).value
        LaunchedEffect(reading, width) {
            val slots = BitRegroup.Base64Code.BYTES_PER_GROUP * 8
            val resized = width != feed.width
            feed.width = width
            fitPitch(feed, width - 1, slots)
            val glyph = slots * feed.pitch - 1
            var x = glyph
            var fits = 1
            while (x + GROUP_GAP + glyph <= width) {
                x += GROUP_GAP + glyph
                fits++
            }
            val rows = (reading.count + fits - 1) / fits
            val oldFirst = feed.firstRow
            if (fits != feed.perRow || rows - feed.firstRow !in FED_ROWS && (feed.firstRow > 0 || rows > FED_ROWS.last)) {
                feed.firstRow = maxOf(0, rows - 8)
            }
            val dropped = if (fits == feed.perRow) (feed.firstRow - oldFirst) * fits else 0
            feed.perRow = fits
            state.show(reading.groups(feed.firstRow * fits until reading.count), dropping = dropped, animated = !resized)
        }
        CanonGlyphFlow(
            state,
            Modifier.fillMaxSize(),
            paintEmpty = { area ->
                val ghost = BitRegroup.ghost
                val w = (ghost.slots.size * feed.pitch - 1).dp.toPx()
                val h = regroupRow.toPx()
                drawGroup(ghost, Rect(area.center.x - w / 2, area.center.y - h / 2, area.center.x + w / 2, area.center.y + h / 2), feed.pitch, measurer, c.foreground, c.primary, c.mutedForeground, 0.45f)
            },
        ) { group, _, frame -> drawGroup(group, frame, feed.pitch, measurer, c.foreground, c.primary, c.mutedForeground, 1f) }
    }
}

private fun fitPitch(feed: RegroupFeed, width: Float, slots: Int) {
    if (width <= 0) return
    var count = maxOf(1, ceil((width + GROUP_GAP) / (slots * PITCH_MOST + GROUP_GAP)).toInt())
    var fitted = ((width + GROUP_GAP) / count - GROUP_GAP) / slots
    if (fitted < PITCH_LEAST && count > 1) {
        count--
        fitted = ((width + GROUP_GAP) / count - GROUP_GAP) / slots
    }
    feed.pitch = fitted.coerceIn(PITCH_LEAST, PITCH_MOST)
}

private fun DrawScope.drawGroup(
    group: BitRegroup.Group,
    frame: Rect,
    pitchDp: Float,
    measurer: TextMeasurer,
    foreground: Color,
    primary: Color,
    muted: Color,
    alpha: Float,
) {
    fun ink(unit: Int) = (if (unit % 2 == 0) foreground else primary)
    fun Color.a(value: Float) = copy(alpha = this.alpha * value * alpha)
    val pitch = pitchDp.dp.toPx()
    val hair = CanonMetrics.hairline.toPx()
    val bitsTop = frame.top + labelRow.toPx() + bracketRoom.toPx()
    val bitsBottom = bitsTop + bitHeight.toPx()
    val cell = maxOf(1f, pitch - 1.dp.toPx())

    val owner = group.bottom.flatMapIndexed { index, unit -> List(unit.span) { index } }
    group.slots.forEachIndexed { index, slot ->
        val x = frame.left + index * pitch
        val tint = ink(owner.getOrElse(index) { 0 })
        when (slot) {
            BitRegroup.Slot.One -> drawRect(tint.a(1f), Offset(x, bitsTop), Size(cell, bitHeight.toPx()))
            BitRegroup.Slot.Zero -> drawRect(tint.a(0.4f), Offset(x, bitsBottom - 3.dp.toPx()), Size(cell, 3.dp.toPx()))
            BitRegroup.Slot.Pad -> drawRect(
                muted.a(0.6f),
                Offset(x + hair / 2, bitsTop + hair / 2),
                Size(cell - hair, bitHeight.toPx() - hair),
                style = Stroke(hair),
            )
            BitRegroup.Slot.Absent -> drawRect(muted.a(0.3f), Offset(x + cell / 2 - hair / 2, bitsBottom - hair), Size(hair, hair))
        }
    }

    fun bracket(span: Rect, y: Float, down: Boolean, colour: Color) {
        val left = span.left + hair / 2
        val right = span.right - hair / 2
        val tip = if (down) y + 2.5.dp.toPx() else y - 2.5.dp.toPx()
        val path = Path().apply {
            moveTo(left, tip)
            lineTo(left, y)
            lineTo(right, y)
            lineTo(right, tip)
        }
        drawPath(path, colour, style = Stroke(hair))
    }

    fun label(text: String, span: Rect, colour: Color) {
        if (text.isEmpty()) return
        // hex and binary: mono
        val isCode = text.length > 1 && text.all { it.code < 0x80 }
        val laid = measurer.measure(text, (if (isCode) codeFont else labelFont).copy(color = colour))
        drawText(laid, topLeft = Offset(span.center.x - laid.size.width / 2, span.center.y - laid.size.height / 2))
    }

    var start = 0
    for (unit in group.top) {
        val span = Rect(frame.left + start * pitch, frame.top, frame.left + (start + unit.span) * pitch - 1.dp.toPx(), frame.top + labelRow.toPx())
        val colour = if (unit.faint) muted.a(0.6f) else foreground.a(1f)
        if (!(unit.faint && unit.label.isEmpty())) bracket(span, bitsTop - 3.dp.toPx(), true, colour.copy(alpha = colour.alpha * 0.5f))
        label(unit.label, span, colour)
        start += unit.span
    }
    start = 0
    group.bottom.forEachIndexed { index, unit ->
        val top = bitsBottom + bracketRoom.toPx()
        val span = Rect(frame.left + start * pitch, top, frame.left + (start + unit.span) * pitch - 1.dp.toPx(), top + labelRow.toPx())
        val colour = if (unit.faint) muted.a(0.6f) else ink(index).a(1f)
        if (!(unit.faint && unit.label.isEmpty())) bracket(span, bitsBottom + 3.dp.toPx(), false, colour.copy(alpha = colour.alpha * 0.6f))
        label(unit.label, span, colour)
        start += unit.span
    }
}

private val pairFace = 24.dp
private val pairMiddle = 16.dp
private val pairText = TextStyle(fontFamily = DelphiMono, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

/** dt-ios SymbolPairStrip, text faces only: each symbol of the input over what it becomes, in wrapped rows; an unchanged symbol is muted. */
@Composable
fun SymbolPairStrip(pairs: List<SymbolPair>, modifier: Modifier = Modifier) {
    // ponytail: no Morse, Bacon or key faces; they come with the cipher tool's port
    val c = LocalDelphiColours.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widths = remember { HashMap<String, Dp>() }
    fun width(text: String) = widths.getOrPut(text) { with(density) { ceil(measurer.measure(text, pairText).size.width.toFloat()).toDp() } }
    val state = rememberCanonGlyphFlowState<SymbolPair>(
        CanonGlyphMetrics(pairFace * 2 + pairMiddle, 10.dp, 7.dp),
        measure = { if (it.newline) 0.dp else maxOf(width(it.top), width(it.bottom)) },
        kind = { if (it.newline) CanonGlyphKind.Newline else CanonGlyphKind.Glyph },
    )
    LaunchedEffect(pairs) { state.show(pairs) }
    CanonGlyphFlow(
        state,
        modifier.fillMaxSize(),
        paintEmpty = { area ->
            val half = (pairFace * 2 + pairMiddle).toPx() / 2 - pairFace.toPx() / 2
            for (y in listOf(area.center.y - half, area.center.y + half)) {
                drawRect(c.mutedForeground.copy(alpha = 0.3f), Offset(area.left, y - 0.5f), Size(area.width, CanonMetrics.hairline.toPx()))
            }
        },
    ) { pair, _, frame ->
        if (pair.newline) return@CanonGlyphFlow
        val lit = if (pair.changes) c.primary else c.mutedForeground
        val plain = if (pair.changes) c.foreground else c.mutedForeground
        val face = pairFace.toPx()
        for ((text, centreY, ink) in listOf(Triple(pair.top, frame.top + face / 2, plain), Triple(pair.bottom, frame.bottom - face / 2, lit))) {
            val laid = measurer.measure(text, pairText.copy(color = ink))
            drawText(laid, topLeft = Offset(frame.center.x - laid.size.width / 2, centreY - laid.size.height / 2))
        }
        val mid = frame.top + face + pairMiddle.toPx() / 2
        if (pair.changes) {
            drawRect(c.mutedForeground.copy(alpha = 0.45f), Offset(frame.center.x - 0.5f, mid - 5.dp.toPx()), Size(CanonMetrics.hairline.toPx(), 10.dp.toPx()))
        } else {
            drawRect(c.mutedForeground.copy(alpha = 0.35f), Offset(frame.center.x - 1.dp.toPx(), mid - 1.dp.toPx()), Size(2.dp.toPx(), 2.dp.toPx()))
        }
    }
}

private const val FLIP_MS = 320
private const val FLIP_STAGGER_MS = 6.0
private val digestHeader = 32.dp
private val digestBase = TextStyle(fontFamily = DelphiMono)

private class DigestMemo {
    var digits: List<Int> = emptyList()
    var previous: List<Int> = emptyList()
    var name = ""
    var flipped: Int? = null
    var figure = ""
    var changes = 0
}

/** Columns and cell size for [count] digits in [width] by [height]: 16 to a row, 32 when the digest is long and the row wide. */
internal fun digestGrid(count: Int, width: Float, height: Float): Pair<Int, Size> {
    val columns = if (count > 64 && width / 32 >= 18) 32 else 16
    val rows = maxOf(1, (count + columns - 1) / columns)
    val cell = width / columns
    return columns to Size(cell, minOf(cell, height / rows))
}

/**
 * dt-ios DigestGrid: the digest as its hex digits in a grid, each cell shaded by the digit's value. A new input turns
 * over the digits that changed, and the figure counts the bits that flipped. Null [bytes] shows the digest's size, empty.
 */
@Composable
fun DigestGrid(name: String, bytes: ByteArray?, bits: Int, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val top = LocalCanonCanvas.current.top
    val nibbles = remember(bytes) { bytes?.flatMap { listOf((it.toInt() shr 4) and 0xF, it.toInt() and 0xF) } ?: emptyList() }
    val memo = remember { DigestMemo() }
    if (nibbles != memo.digits || name != memo.name) {
        val sameShape = name == memo.name && memo.digits.isNotEmpty() && nibbles.isNotEmpty()
        memo.previous = if (sameShape) memo.digits else emptyList()
        memo.flipped = if (sameShape) memo.digits.zip(nibbles).sumOf { (a, b) -> Integer.bitCount(a xor b) } else null
        memo.flipped?.let { memo.figure = it.toString() }
        memo.digits = nibbles
        memo.name = name
        memo.changes++
    }
    val count = bits / 4
    var began by remember { mutableStateOf<Long?>(null) }
    var clock by remember { mutableLongStateOf(0L) }
    LaunchedEffect(memo.changes) {
        began = null
        val animates = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        if (memo.previous.isEmpty() || !animates) return@LaunchedEffect
        val start = withFrameMillis { it }
        began = start
        clock = start
        while (clock - start <= FLIP_MS + FLIP_STAGGER_MS * count) clock = withFrameMillis { it }
        began = null
    }
    val figureAlpha by animateFloatAsState(if (memo.flipped != null) 1f else 0f, tween(200), label = "figure")
    val digits = memo.digits
    val previous = memo.previous

    BoxWithConstraints(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = CanonMetrics.panelPadding, end = CanonMetrics.panelPadding, top = top + CanonMetrics.panelPadding * 0.75f + 6.dp),
        ) {
            Text(
                "$name · $bits bits",
                style = CanonText.footnoteSemibold,
                color = c.mutedForeground,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(end = 8.dp).alignByBaseline(),
            )
            CanonFigure(memo.figure, Modifier.alpha(figureAlpha).alignByBaseline(), unit = "bits changed", size = 24.sp)
        }
        Canvas(Modifier.fillMaxSize()) {
            val pad = CanonMetrics.panelPadding.toPx()
            val gridTop = top.toPx() + pad * 0.75f + digestHeader.toPx()
            val area = Rect(pad, gridTop, pad + maxOf(0f, size.width - pad * 2), gridTop + maxOf(0f, size.height - gridTop - pad * 0.75f))
            if (area.width <= 0 || area.height <= 0) return@Canvas
            val (columns, cell) = digestGrid(count, area.width, area.height)
            val rows = (count + columns - 1) / columns
            val originY = area.top + (area.height - cell.height * rows) / 2
            val style = digestBase.copy(fontSize = minOf(cell.height * 0.62f, cell.width * 0.8f, 17.dp.toPx()).toSp())
            val hair = CanonMetrics.hairline.toPx()
            val from = began
            for (index in 0 until count) {
                val box = Rect(
                    area.left + (index % columns) * cell.width + hair / 2,
                    originY + (index / columns) * cell.height + hair / 2,
                    area.left + (index % columns + 1) * cell.width - hair / 2,
                    originY + (index / columns + 1) * cell.height - hair / 2,
                )
                if (index !in digits.indices) {
                    drawRect(
                        c.mutedForeground.copy(alpha = 0.3f),
                        box.topLeft + Offset(hair / 2, hair / 2),
                        Size(box.width - hair, box.height - hair),
                        style = Stroke(hair, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))),
                    )
                    continue
                }
                var digit = digits[index]
                var squash = 1f
                if (from != null && index in previous.indices && previous[index] != digit) {
                    val p = ((clock - from - FLIP_STAGGER_MS * index) / FLIP_MS).toFloat().coerceIn(0f, 1f)
                    squash = abs(cos(p * PI.toFloat()))
                    if (p < 0.5f) digit = previous[index]
                }
                scale(1f, maxOf(squash, 0.02f), box.center) {
                    drawRect(c.primary.copy(alpha = 0.06f + 0.5f * digit / 15), box.topLeft, box.size)
                    val laid = measurer.measure(digit.toString(16), style.copy(color = c.foreground))
                    drawText(laid, topLeft = Offset(box.center.x - laid.size.width / 2, box.center.y - laid.size.height / 2))
                }
            }
        }
    }
}
