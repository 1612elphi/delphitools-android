package com.delphi.delphitools.tools.devtools

import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonCanvasInsets
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGlyphFlow
import com.delphi.delphitools.canon.CanonGlyphKind
import com.delphi.delphitools.canon.CanonGlyphMetrics
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.rememberCanonGlyphFlowState
import com.delphi.delphitools.tools.devtools.BitRegroup.Group
import com.delphi.delphitools.tools.devtools.BitRegroup.Reading
import com.delphi.delphitools.tools.devtools.BitRegroup.Slot
import com.delphi.delphitools.tools.devtools.BitRegroup.Unit
import com.delphi.delphitools.tools.turbonerd.MorseEngine
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp

val CIPHER_BAND_HEIGHT = 176.dp
val CIPHER_COLLAPSED_HEIGHT = 116.dp
private val captionRoom = 24.dp

/** dt-ios BitRegroup.Code: the codes BitRegroup in EncoderEngine reads as Base64 only. */
class CipherCode(val bits: Int, val alphabet: String, val bytesPerGroup: Int, val padding: Char?) {
    val charsPerGroup get() = bytesPerGroup * 8 / bits

    /** The character's value; Base64 also reads the URL-safe `-` and `_`, hex and Base32 either case. */
    fun value(of: Char): Int? {
        if (bits == 6) return BitRegroup.Base64Code.value(of)
        val index = alphabet.indexOf(of, ignoreCase = true)
        return index.takeIf { it >= 0 }
    }

    companion object {
        val Base64 = CipherCode(6, BitRegroup.Base64Code.ALPHABET, 3, '=')
        val Base32 = CipherCode(5, "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567", 5, '=')
        val Hex = CipherCode(4, "0123456789abcdef", 1, null)
        val Binary = CipherCode(1, "01", 1, null)
    }
}

/** dt-ios BitRegroup.encoding and decoding over any [CipherCode]. */
object CipherRegroup {
    private fun slots(byte: Int) = List(8) { if ((byte shr (7 - it)) and 1 == 1) Slot.One else Slot.Zero }

    /** Binary's one-bit characters read as one label per byte. */
    private fun merged(units: List<Unit>, code: CipherCode) =
        if (code.bits != 1 || units.isEmpty()) units else listOf(Unit(units.joinToString("") { it.label }, units.sumOf { it.span }))

    fun encoding(bytes: ByteArray, code: CipherCode): Reading {
        val per = code.bytesPerGroup
        val total = per * 8
        return Reading((bytes.size + per - 1) / per) { range ->
            val first = minOf(bytes.size, range.first * per)
            val last = minOf(bytes.size, (range.last + 1) * per)
            val names = BitRegroup.labels(bytes, first until last)
            (first until last step per).map { start ->
                val chunk = bytes.copyOfRange(start, minOf(start + per, bytes.size))
                val real = chunk.size * 8
                val used = (real + code.bits - 1) / code.bits * code.bits
                val slots = chunk.flatMap { slots(it.toInt() and 0xFF) } + List(used - real) { Slot.Pad } + List(total - used) { Slot.Absent }
                val top = List(per) { i -> if (i < chunk.size) Unit(names[start - first + i], 8) else Unit("", 8, faint = true) }
                val bottom = List(code.charsPerGroup) { k ->
                    val from = k * code.bits
                    if (from >= used) return@List Unit(code.padding?.toString() ?: "", code.bits, faint = true)
                    val value = (0 until code.bits).fold(0) { acc, b -> acc shl 1 or if (slots[from + b] == Slot.One) 1 else 0 }
                    Unit(code.alphabet[value].toString(), code.bits)
                }
                Group(slots, top, merged(bottom, code))
            }
        }
    }

    /** Encoded text, whitespace already removed. Reading stops at the first character the code does not have. */
    fun decoding(characters: List<Char>, code: CipherCode): Reading {
        val values = mutableListOf<Int>()
        var padding = 0
        for (character in characters) {
            val value = code.value(character)
            when {
                character == code.padding -> padding++
                padding == 0 && value != null -> values += value
                else -> break
            }
        }
        val realBits = values.size * code.bits
        val decoded = java.io.ByteArrayOutputStream()
        var accumulator = 0
        var filled = 0
        for (value in values) {
            accumulator = accumulator shl code.bits or value
            filled += code.bits
            if (filled >= 8) {
                decoded.write((accumulator shr (filled - 8)) and 0xFF)
                filled -= 8
                accumulator = accumulator and ((1 shl filled) - 1)
            }
        }
        val bytes = decoded.toByteArray()
        val kept = bytes.size * 8
        val perGroup = code.charsPerGroup
        val length = values.size + padding
        return Reading((length + perGroup - 1) / perGroup) { range ->
            val firstByte = minOf(bytes.size, range.first * code.bytesPerGroup)
            val names = BitRegroup.labels(bytes, firstByte until minOf(bytes.size, (range.last + 1) * code.bytesPerGroup))
            (range.first * perGroup until minOf(length, (range.last + 1) * perGroup) step perGroup).map { start ->
                val slots = mutableListOf<Slot>()
                val top = mutableListOf<Unit>()
                for (index in start until start + perGroup) {
                    if (index >= values.size) {
                        repeat(code.bits) { slots += Slot.Absent }
                        top += Unit(if (index < length) code.padding?.toString() ?: "" else "", code.bits, faint = true)
                        continue
                    }
                    val value = values[index]
                    val bitBase = index * code.bits
                    for (bit in 0 until code.bits) {
                        slots += when {
                            bitBase + bit in kept until realBits -> Slot.Pad
                            (value shr (code.bits - 1 - bit)) and 1 == 1 -> Slot.One
                            else -> Slot.Zero
                        }
                    }
                    top += Unit(characters[index].toString(), code.bits)
                }
                val byte = start / perGroup * code.bytesPerGroup
                val bottom = List(code.bytesPerGroup) { j ->
                    if (byte + j < bytes.size) Unit(names[byte + j - firstByte], 8) else Unit("", 8, faint = true)
                }
                Group(slots, merged(top, code), bottom)
            }
        }
    }

    /** One group of the code, all zeros, no labels: the strip's picture with no input. */
    fun ghost(code: CipherCode): Group? {
        val group = encoding(ByteArray(code.bytesPerGroup), code).all.firstOrNull() ?: return null
        return group.copy(top = group.top.map { Unit("", it.span) }, bottom = group.bottom.map { Unit("", it.span) })
    }
}

private val labelRow = 17.dp
private val bracketRoom = 7.dp
private val bitHeight = 16.dp
private val regroupRow = labelRow * 2 + bracketRoom * 2 + bitHeight
private const val PITCH_LEAST = 3.6f
private const val PITCH_MOST = 7f
private const val GROUP_GAP = 12f
private val FED_ROWS = 4..12
private val labelFont = TextStyle(fontFamily = DelphiFont, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
private val codeFont = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp, fontWeight = FontWeight.Medium)

private class CipherFeed {
    var pitch = 5f
    var firstRow = 0
    var perRow = 0
    var width = 0f
}

/**
 * dt-ios BitRegroupStrip for any [CipherCode].
 * ponytail: copy of BitRegroupStrip in EncoderPictures with the group's slot count and ghost per code; fold the two when that file may change
 */
@Composable
fun CipherBitStrip(reading: Reading, code: CipherCode, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val measurer = rememberTextMeasurer()
    val feed = remember { CipherFeed() }
    val ghost = remember(code) { CipherRegroup.ghost(code) }
    val state = rememberCanonGlyphFlowState<Group>(
        CanonGlyphMetrics(regroupRow, 12.dp, GROUP_GAP.dp),
        measure = { (it.slots.size * feed.pitch - 1).dp },
        kind = { CanonGlyphKind.Glyph },
    )
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = (maxWidth - CanonMetrics.panelPadding * 2).value
        LaunchedEffect(reading, width) {
            val slots = code.bytesPerGroup * 8
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
                val shown = ghost ?: return@CanonGlyphFlow
                val w = (shown.slots.size * feed.pitch - 1).dp.toPx()
                val h = regroupRow.toPx()
                drawGroup(shown, Rect(area.center.x - w / 2, area.center.y - h / 2, area.center.x + w / 2, area.center.y + h / 2), feed.pitch, measurer, c.foreground, c.primary, c.mutedForeground, 0.45f)
            },
        ) { group, _, frame -> drawGroup(group, frame, feed.pitch, measurer, c.foreground, c.primary, c.mutedForeground, 1f) }
    }
}

private fun fitPitch(feed: CipherFeed, width: Float, slots: Int) {
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
    group: Group,
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
            Slot.One -> drawRect(tint.a(1f), Offset(x, bitsTop), Size(cell, bitHeight.toPx()))
            Slot.Zero -> drawRect(tint.a(0.4f), Offset(x, bitsBottom - 3.dp.toPx()), Size(cell, 3.dp.toPx()))
            Slot.Pad -> drawRect(muted.a(0.6f), Offset(x + hair / 2, bitsTop + hair / 2), Size(cell - hair, bitHeight.toPx() - hair), style = Stroke(hair))
            Slot.Absent -> drawRect(muted.a(0.3f), Offset(x + cell / 2 - hair / 2, bitsBottom - hair), Size(hair, hair))
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

/** dt-ios SymbolPairStrip.Pair.Face. */
sealed interface CipherFace {
    data class Text(val text: String) : CipherFace

    /** Dots and dashes, drawn as marks to scale. */
    data class Morse(val code: String) : CipherFace

    /** Five A/B letters, drawn as open and filled cells. */
    data class Bacon(val code: String) : CipherFace
}

data class CipherPair(
    val top: CipherFace,
    val bottom: CipherFace,
    val key: String? = null,
    val changes: Boolean = true,
    val kind: CanonGlyphKind = CanonGlyphKind.Glyph,
) {
    companion object {
        val Space = CipherPair(CipherFace.Text(" "), CipherFace.Text(" "), kind = CanonGlyphKind.Space)
        val Newline = CipherPair(CipherFace.Text(""), CipherFace.Text(""), kind = CanonGlyphKind.Newline)
    }
}

private val pairFace = 24.dp
private val pairMiddle = 16.dp
private val pairText = TextStyle(fontFamily = DelphiMono, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
private val keyText = TextStyle(fontFamily = DelphiFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
private const val MORSE_UNIT = 3f
private const val BACON_CELL = 7f

private fun morseUnits(code: String) = if (code.isEmpty()) 1 else code.sumOf { if (it == '-') 3 else 1 } + code.length - 1

/**
 * dt-ios SymbolPairStrip with every face: each symbol of the input over what it becomes, in wrapped rows. A letter
 * over its shifted letter, a letter over its Morse marks or Bacon cells; an unchanged symbol is muted, and the key
 * letter that did the change (Vigenère) sits between the two.
 */
@Composable
fun CipherPairStrip(pairs: List<CipherPair>, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widths = remember { HashMap<String, Dp>() }
    fun width(face: CipherFace): Dp = when (face) {
        is CipherFace.Text -> widths.getOrPut(face.text) { with(density) { ceil(measurer.measure(face.text, pairText).size.width.toFloat()).toDp() } }
        is CipherFace.Morse -> (morseUnits(face.code) * MORSE_UNIT).dp
        is CipherFace.Bacon -> (face.code.length * (BACON_CELL + 1) - 1).dp
    }
    val state = rememberCanonGlyphFlowState<CipherPair>(
        CanonGlyphMetrics(pairFace * 2 + pairMiddle, 10.dp, 7.dp),
        measure = {
            when (it.kind) {
                CanonGlyphKind.Newline -> 0.dp
                CanonGlyphKind.Space -> 6.dp
                CanonGlyphKind.Glyph -> maxOf(width(it.top), width(it.bottom), if (it.key == null) 0.dp else 13.dp)
            }
        },
        kind = { it.kind },
    )
    LaunchedEffect(pairs) { state.show(pairs) }

    fun DrawScope.face(face: CipherFace, centre: Offset, colour: Color) {
        when (face) {
            is CipherFace.Text -> {
                val laid = measurer.measure(face.text, pairText.copy(color = colour))
                drawText(laid, topLeft = Offset(centre.x - laid.size.width / 2, centre.y - laid.size.height / 2))
            }
            is CipherFace.Morse -> {
                val unit = MORSE_UNIT.dp.toPx()
                var x = centre.x - morseUnits(face.code) * unit / 2
                for (symbol in face.code) {
                    val length = if (symbol == '-') 3 else 1
                    drawRect(colour, Offset(x, centre.y - 2.dp.toPx()), Size(length * unit, 4.dp.toPx()))
                    x += (length + 1) * unit
                }
            }
            is CipherFace.Bacon -> {
                val cell = BACON_CELL.dp.toPx()
                val hair = CanonMetrics.hairline.toPx()
                var x = centre.x - (face.code.length * (BACON_CELL + 1) - 1).dp.toPx() / 2
                for (letter in face.code) {
                    val origin = Offset(x, centre.y - cell / 2)
                    if (letter == 'B') {
                        drawRect(colour, origin, Size(cell, cell))
                    } else {
                        drawRect(colour, origin + Offset(hair / 2, hair / 2), Size(cell - hair, cell - hair), style = Stroke(hair))
                    }
                    x += cell + 1.dp.toPx()
                }
            }
        }
    }

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
        if (pair.kind != CanonGlyphKind.Glyph) return@CanonGlyphFlow
        val lit = if (pair.changes) c.primary else c.mutedForeground
        val plain = if (pair.changes) c.foreground else c.mutedForeground
        val faceHeight = pairFace.toPx()
        face(pair.top, Offset(frame.center.x, frame.top + faceHeight / 2), plain)
        face(pair.bottom, Offset(frame.center.x, frame.bottom - faceHeight / 2), lit)
        val mid = frame.top + faceHeight + pairMiddle.toPx() / 2
        val key = pair.key
        when {
            key != null -> {
                val side = 13.dp.toPx()
                drawRect(c.accentBg, Offset(frame.center.x - side / 2, mid - side / 2), Size(side, side))
                val laid = measurer.measure(key, keyText.copy(color = c.primary))
                drawText(laid, topLeft = Offset(frame.center.x - laid.size.width / 2, mid - laid.size.height / 2))
            }
            pair.changes -> drawRect(c.mutedForeground.copy(alpha = 0.45f), Offset(frame.center.x - 0.5f, mid - 5.dp.toPx()), Size(CanonMetrics.hairline.toPx(), 10.dp.toPx()))
            else -> drawRect(c.mutedForeground.copy(alpha = 0.35f), Offset(frame.center.x - 1.dp.toPx(), mid - 1.dp.toPx()), Size(2.dp.toPx(), 2.dp.toPx()))
        }
    }
}

private const val RAIL_SETTLE_MS = 500
private const val RAIL_PITCH_LEAST = 11f
private const val RAIL_PITCH_MOST = 26f

/** dt-ios RailFenceZigzag: the text on its rails, joined by the zig-zag; a change of rails springs the characters to their new heights. */
@Composable
fun RailFenceZigzag(text: String, rails: Int, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val top = LocalCanonCanvas.current.top
    val characters = remember(text) { EncoderEngine.characters(text) }
    val targets = remember(characters.size, rails) {
        val effective = maxOf(2, minOf(rails, maxOf(characters.size, 2)))
        CipherEngine.railPattern(characters.size, rails).map { it.toFloat() / (effective - 1) }
    }
    val memo = remember { CipherRailMemo() }
    val progress = remember { Animatable(1f) }
    fun eased(p: Float) = if (p >= 1f) 1f else (1 - exp(-7.0 * p) * cos(8.0 * p)).toFloat()
    fun presented(index: Int, p: Float): Float {
        val target = memo.target.getOrElse(index) { 0f }
        if (p >= 1f || index !in memo.from.indices) return target
        return memo.from[index] + (target - memo.from[index]) * eased(p)
    }
    if (targets != memo.target) {
        val p = progress.value
        val shown = memo.target.indices.map { presented(it, p) }
        val movesRails = rails != memo.rails && memo.rails != 0
        memo.target = targets
        memo.rails = rails
        if (movesRails) {
            memo.from = shown
            memo.moves++
        }
    }
    LaunchedEffect(memo.moves) {
        if (memo.moves == 0) return@LaunchedEffect
        val animates = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        if (!animates) return@LaunchedEffect progress.snapTo(1f)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(RAIL_SETTLE_MS, easing = LinearEasing))
    }

    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        val padding = CanonMetrics.panelPadding.toPx()
        val hair = CanonMetrics.hairline.toPx()
        val area = Rect(padding, top.toPx() + padding * 0.75f, size.width - padding, size.height - padding * 0.75f)
        if (area.width <= 0 || area.height <= 0) return@Canvas
        if (characters.isEmpty()) {
            for (rail in 0 until rails) {
                val y = area.top + area.height * rail / maxOf(rails - 1, 1)
                drawRect(c.mutedForeground.copy(alpha = 0.3f), Offset(area.left, y - 0.5f), Size(area.width, hair))
            }
            return@Canvas
        }
        val pitch = (area.width / characters.size).coerceIn(RAIL_PITCH_LEAST.dp.toPx(), RAIL_PITCH_MOST.dp.toPx())
        val columns = minOf(characters.size, (area.width / pitch).toInt())
        val style = TextStyle(fontFamily = DelphiMono, fontWeight = FontWeight.SemiBold, fontSize = minOf(16.dp.toPx(), pitch * 0.95f).toSp())
        val half = measurer.measure("0", style).size.height / 2f
        val track = Rect(area.left, area.top + half, area.right, area.top + half + maxOf(0f, area.height - half * 2))
        val effective = maxOf(2, minOf(rails, characters.size))
        val rail = CipherEngine.railPattern(characters.size, rails)
        for (index in 0 until effective) {
            val y = track.top + track.height * index / (effective - 1)
            drawRect(c.mutedForeground.copy(alpha = 0.14f), Offset(area.left, y - 0.5f), Size(area.width, hair))
        }
        val points = (0 until columns).map { Offset(area.left + (it + 0.5f) * pitch, track.top + track.height * presented(it, p)) }
        if (points.size > 1) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (point in points.drop(1)) lineTo(point.x, point.y)
            }
            drawPath(path, c.mutedForeground.copy(alpha = 0.4f), style = Stroke(hair))
        }
        val fades = columns < characters.size
        points.forEachIndexed { index, point ->
            val character = characters[index]
            val ink = when {
                character == " " -> c.mutedForeground
                rail[index] % 2 == 0 -> c.foreground
                else -> c.primary
            }
            val fade = if (fades) minOf(1f, (columns - index) / 4f) else 1f
            drawRect(c.card, Offset(point.x - pitch / 2 + 1, point.y - half), Size(pitch - 2, half * 2))
            val laid = measurer.measure(if (character == " ") "·" else character, style.copy(color = ink.copy(alpha = ink.alpha * fade)))
            drawText(laid, topLeft = Offset(point.x - laid.size.width / 2, point.y - laid.size.height / 2))
        }
    }
}

private class CipherRailMemo {
    var target: List<Float> = emptyList()
    var from: List<Float> = emptyList()
    var rails = 0
    var moves = 0
}

/** dt-ios CipherPicture.caption: the cipher's name and the setting that drives it. */
fun cipherCaption(cipher: CipherId, params: CipherParams): Pair<String, String> = when (cipher) {
    CipherId.Caesar -> "Caesar (shift ${params.caesarShift})" to ""
    CipherId.Vigenere -> cipher.label to if (params.vigenereKey.isEmpty()) "" else "key: ${params.vigenereKey.uppercase()}"
    CipherId.Affine -> cipher.label to "a=${params.affineA}, b=${params.affineB}"
    CipherId.RailFence -> cipher.label to "${params.rails} rails"
    else -> cipher.label to ""
}

private fun cipherCode(cipher: CipherId) = when (cipher) {
    CipherId.Base32 -> CipherCode.Base32
    CipherId.Hex -> CipherCode.Hex
    CipherId.Binary -> CipherCode.Binary
    else -> CipherCode.Base64
}

/** As the engine reads. */
private fun encoded(text: String, cipher: CipherId): List<Char> {
    var cleaned = text.filterNot { it.isWhitespace() }
    if (cipher == CipherId.Hex && cleaned.lowercase().startsWith("0x")) cleaned = cleaned.drop(2)
    if (cipher == CipherId.Binary) cleaned = cleaned.filter { it == '0' || it == '1' }
    return cleaned.toList()
}

private fun blank(character: String) = when {
    character == "\n" || character == "\r\n" || character == "\r" -> CipherPair.Newline
    character.isNotEmpty() && character.all { it.isWhitespace() } -> CipherPair.Space
    else -> null
}

/** dt-ios CipherPicture.pairs: each symbol of [input] over what it becomes. */
fun cipherPairs(cipher: CipherId, encode: Boolean, params: CipherParams, input: String, output: String): List<CipherPair> {
    fun text(s: String) = CipherFace.Text(s)
    fun passes(character: String) = CipherPair(text(character), text(""), changes = false)
    return when (cipher) {
        CipherId.Morse -> if (encode) {
            EncoderEngine.characters(input).map { character ->
                blank(character) ?: MorseEngine.table[character.uppercase().singleOrNull() ?: ' ']
                    ?.let { CipherPair(text(character), CipherFace.Morse(it)) } ?: passes(character)
            }
        } else {
            MorseEngine.words(input, minimumGap = 2).flatMapIndexed { index, word ->
                (if (index > 0) listOf(CipherPair.Space) else emptyList()) +
                    word.map { CipherPair(CipherFace.Morse(it), text(MorseEngine.reverse[it]?.toString() ?: "?")) }
            }
        }
        CipherId.A1z26 -> if (encode) {
            EncoderEngine.characters(input).map { character ->
                val letter = character.singleOrNull()?.uppercaseChar()
                blank(character) ?: if (letter != null && letter in 'A'..'Z') CipherPair(text(character), text((letter.code - 64).toString())) else passes(character)
            }
        } else {
            MorseEngine.words(input, minimumGap = 2).flatMapIndexed { index, word ->
                (if (index > 0) listOf(CipherPair.Space) else emptyList()) +
                    CipherEngine.a1z26Pieces(word).map { CipherPair(text(it), text(CipherEngine.a1z26Letter(it)?.toString() ?: "?")) }
            }
        }
        CipherId.Bacon -> if (encode) {
            EncoderEngine.characters(input).map { character ->
                blank(character) ?: CipherEngine.bacon[character.uppercase().singleOrNull() ?: ' ']
                    ?.let { CipherPair(text(character), CipherFace.Bacon(it)) } ?: passes(character)
            }
        } else {
            val letters = input.uppercase().filter { it == 'A' || it == 'B' }
            (0..letters.length - 5 step 5).map { start ->
                val code = letters.substring(start, start + 5)
                CipherPair(CipherFace.Bacon(code), text(CipherEngine.baconLetter(code)?.toString() ?: "?"))
            }
        }
        else -> {
            // scalars map one-to-one
            val from = input.codePoints().toArray()
            val to = output.codePoints().toArray()
            if (from.size != to.size) return emptyList()
            val key = params.vigenereKey.uppercase().filter { it in 'A'..'Z' }
            var keyIndex = 0
            from.indices.map { i ->
                val character = String(Character.toChars(from[i]))
                blank(character) ?: run {
                    var letter: String? = null
                    if (cipher == CipherId.Vigenere && key.isNotEmpty() && from[i] < 0x80 && Character.isLetter(from[i])) {
                        letter = key[keyIndex % key.length].toString()
                        keyIndex++
                    }
                    CipherPair(text(character), text(String(Character.toChars(to[i]))), letter, from[i] != to[i])
                }
            }
        }
    }
}

/** What the banner draws: one cipher at work on one text. */
data class CipherScene(
    val cipher: CipherId,
    val encode: Boolean,
    val params: CipherParams,
    val input: String,
    val output: String,
    val name: String,
    val detail: String,
    val score: Double?,
)

private enum class CipherStrip { Pairs, Bits, Fence }

/**
 * dt-ios CipherPicture: the cipher at work under a caption with the candidate's score. Rail fence draws its zig-zag,
 * the byte encodings their regrouped bits, every other cipher each symbol over what it becomes.
 */
@Composable
fun CipherPicture(scene: CipherScene, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val top = LocalCanonCanvas.current.top
    val strip = when (scene.cipher) {
        CipherId.RailFence -> CipherStrip.Fence
        CipherId.Base64, CipherId.Base32, CipherId.Hex, CipherId.Binary -> CipherStrip.Bits
        else -> CipherStrip.Pairs
    }
    val code = cipherCode(scene.cipher)
    // all three stay current, so the one fading out keeps its content
    val reading = remember(scene.input, scene.encode, scene.cipher) {
        if (scene.encode) CipherRegroup.encoding(scene.input.toByteArray(), code) else CipherRegroup.decoding(encoded(scene.input, scene.cipher), code)
    }
    val pairs = remember(scene) { cipherPairs(scene.cipher, scene.encode, scene.params, scene.input, scene.output) }
    val figureAlpha by animateFloatAsState(if (scene.score != null) 1f else 0f, tween(200), label = "figure")
    val figure = remember { CipherFigureMemo() }
    scene.score?.let { figure.text = Math.round(it * 100).toString() }

    Box(
        modifier
            .fillMaxSize()
            .semantics {
                contentDescription = listOf(scene.name, scene.detail).filter { it.isNotEmpty() }.joinToString(", ")
                stateDescription = scene.output
            },
    ) {
        CompositionLocalProvider(LocalCanonCanvas provides CanonCanvasInsets(top = top + captionRoom)) {
            Crossfade(strip, Modifier.fillMaxSize(), tween(250), label = "strip") { shown ->
                when (shown) {
                    CipherStrip.Pairs -> CipherPairStrip(pairs)
                    CipherStrip.Bits -> CipherBitStrip(reading, code)
                    CipherStrip.Fence -> RailFenceZigzag(if (scene.encode) scene.input else scene.output, scene.params.rails)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = CanonMetrics.panelPadding, end = CanonMetrics.panelPadding, top = top + CanonMetrics.panelPadding * 0.6f),
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = c.foreground)) { append(scene.name) }
                    if (scene.detail.isNotEmpty()) withStyle(SpanStyle(color = c.mutedForeground)) { append("  " + scene.detail) }
                },
                style = CanonText.footnoteSemibold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp).alignByBaseline(),
            )
            CanonFigure(figure.text, Modifier.alpha(figureAlpha).alignByBaseline(), unit = "%", size = 20.sp)
        }
    }
}

private class CipherFigureMemo {
    var text = ""
}
