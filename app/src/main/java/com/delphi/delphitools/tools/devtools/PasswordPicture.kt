package com.delphi.delphitools.tools.devtools

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiColours
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

private val BANDS = listOf(40.0, 60.0, 95.0)
private const val SCALE_END = 128.0
private const val SETTLE_FIRST_MS = 160
private const val SETTLE_STEP_MS = 20
private const val SETTLE_WORD_MS = 100
private const val FRAME_MS = 33L
private const val DIE_GAP = 3f

private const val LEAST_DIE = 9f
private val POOL_SETS = listOf(PasswordEngine.LOWERCASE_SET, PasswordEngine.UPPERCASE_SET, PasswordEngine.DIGIT_SET, PasswordEngine.SYMBOL_SET)
private val monoBase = TextStyle(fontFamily = DelphiMono, fontWeight = FontWeight.SemiBold)
private val wordBase = TextStyle(fontFamily = DelphiFont, fontWeight = FontWeight.Bold)

// 3x3 pip grid per face
private val PIPS = listOf(
    listOf(1 to 1), listOf(0 to 0, 2 to 2), listOf(0 to 0, 1 to 1, 2 to 2), listOf(0 to 0, 2 to 0, 0 to 2, 2 to 2),
    listOf(0 to 0, 2 to 0, 1 to 1, 0 to 2, 2 to 2), listOf(0 to 0, 2 to 0, 0 to 1, 2 to 1, 0 to 2, 2 to 2),
)

/** Dice per row under a word: five where they fit at [least], else three (two rows). */
internal fun diceColumns(wordWidth: Float, least: Float) = if (wordWidth >= least * 5 + DIE_GAP * 4) 5 else 3

fun passwordTint(c: DelphiColours, char: Char) = when (PasswordEngine.kind(char)) {
    PasswordEngine.CharKind.Digit -> c.primary
    PasswordEngine.CharKind.Symbol -> c.destructive
    PasswordEngine.CharKind.Letter -> c.foreground
}

/** A passphrase's separators are not symbols the user chose. */
fun passphraseTint(c: DelphiColours, char: Char) = when {
    char.isLetter() -> c.foreground
    char.isDigit() -> c.primary
    else -> c.mutedForeground
}

private fun bandColour(c: DelphiColours, bits: Double) = when {
    bits < BANDS[0] -> c.destructive
    bits < BANDS[1] -> c.mutedForeground
    else -> c.primary
}

private class PasswordWord(val text: String, val roll: String?, val x: Float, val width: Float, val die: Float, val columns: Int) {
    var y = 0f
    fun diceHeight(gap: Float): Float {
        val rows = (5 + columns - 1) / columns
        return rows * die + (rows - 1) * gap
    }
}

/**
 * dt-ios PasswordPicture: the password large, its characters coloured by class, over the four character sets with
 * the characters it uses lit, classes that are off faded and the ambiguous ones struck out. A passphrase is its words,
 * each over the five dice that pick it from the EFF list. Under both, the entropy against the strength bands.
 * A change of [scrambleKey] scrambles the characters or rolls the dice, settling left to right.
 */
@Composable
fun PasswordPicture(
    text: String?,
    phrase: Boolean,
    spec: PasswordEngine.PasswordSpec,
    phraseSpec: PasswordEngine.PassphraseSpec,
    rolls: Map<String, String>,
    bits: Double,
    scrambleKey: Int,
    modifier: Modifier = Modifier,
) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val top = LocalCanonCanvas.current.top
    val shown = text.orEmpty()
    val settleCount = if (phrase) shown.split(phraseSpec.separator).size else shown.length
    val settleStep = if (phrase) SETTLE_WORD_MS else SETTLE_STEP_MS

    var settleFrom by remember { mutableStateOf<Long?>(null) }
    var clock by remember { mutableLongStateOf(0L) }
    val count by rememberUpdatedState(settleCount)
    val step by rememberUpdatedState(settleStep)
    LaunchedEffect(scrambleKey) {
        val animates = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        if (scrambleKey == 0 || !animates) return@LaunchedEffect
        val start = withFrameMillis { it }
        settleFrom = start
        clock = start
        val end = start + SETTLE_FIRST_MS + (count - 1) * step
        while (clock <= end) {
            delay(FRAME_MS)
            clock = withFrameMillis { it }
        }
        settleFrom = null
    }

    val band = PasswordEngine.strengthBand(bits)
    val figure = String.format(Locale.ROOT, "%.1f", bits)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = shown
                stateDescription = "$figure Bits, $band"
            },
    ) {
        val dp = density.density
        val pad = CanonMetrics.panelPadding.value * dp
        val room = maxOf(0f, constraints.maxWidth - pad * 2)
        val roomPt = room / dp
        fun sized(base: TextStyle, pt: Float) = base.copy(fontSize = with(density) { pt.dp.toSp() })
        fun TextMeasurer.width(s: String, style: TextStyle) = measure(s, style).size.width.toFloat()

        val chars = shown.length.coerceAtLeast(if (text == null) 16 else 1)
        val pitchPt = minOf(28f, maxOf(13f, floor(roomPt / chars)))
        val pitch = pitchPt * dp
        val perLine = maxOf(1, minOf(chars, (room / pitch).toInt()))
        val lines = (chars + perLine - 1) / perLine
        val advance = remember(measurer) { measurer.width("0", sized(monoBase, 100f)) / (100f * dp) }
        val bigStyle = sized(monoBase, minOf(40f, pitchPt * 0.94f / advance))
        val bigLine = measurer.measure("0", bigStyle).size.height.toFloat()
        val poolPitchPt = minOf(24f, floor(roomPt / POOL_SETS.maxOf { it.length }))
        val poolPitch = poolPitchPt * dp

        val wordPt = minOf(40f, maxOf(24f, (roomPt / 16).roundToInt().toFloat()))
        val wordStyle = sized(wordBase, wordPt)
        val wordLine = measurer.measure("A", wordStyle).size.height.toFloat()
        val dieLeast = 10f
        val dieMost = (wordPt * 0.62f).roundToInt().toFloat()
        var digit: String? = null
        val words = mutableListOf<PasswordWord>()
        var phraseHeight = 0f
        if (phrase) {
            var body = shown
            if (phraseSpec.trailingDigit && body.lastOrNull()?.isDigit() == true) {
                digit = body.last().toString()
                body = body.dropLast(1)
            }
            val sepWidth = measurer.width(if (phraseSpec.separator == " ") "  " else " ${phraseSpec.separator} ", wordStyle)
            val lineGroups = mutableListOf(mutableListOf<PasswordWord>())
            var x = 0f
            for (part in body.split(phraseSpec.separator)) {
                val w = measurer.width(part, wordStyle)
                val columns = diceColumns(w / dp, dieLeast)
                val die = minOf(dieMost, maxOf(LEAST_DIE, floor((w / dp - DIE_GAP * (columns - 1)) / columns))) * dp
                if (x > 0 && x + w > room) {
                    x = 0f
                    lineGroups += mutableListOf<PasswordWord>()
                }
                val word = PasswordWord(part, rolls[part.lowercase()], x, w, die, columns)
                words += word
                lineGroups.last() += word
                x += w + sepWidth
            }
            var y = 0f
            for (line in lineGroups) {
                val dice = line.maxOfOrNull { it.diceHeight(DIE_GAP * dp) } ?: 0f
                line.forEach { it.y = y }
                y += wordLine + 4 * dp + dice + 14 * dp
            }
            phraseHeight = y - 14 * dp
        }

        val contentHeight = if (phrase) phraseHeight else lines * bigLine + 16 * dp + poolPitch * 4
        val topPx = top.value * dp + pad * 0.75f
        val canvasHeight = topPx + contentHeight + 18 * dp + 4 * dp

        Column {
            Canvas(Modifier.fillMaxWidth().height(with(density) { ceil(canvasHeight).toDp() })) {
                val left = pad
                val hair = CanonMetrics.hairline.toPx()
                val from = settleFrom
                fun settled(index: Int) = from == null || clock >= from + SETTLE_FIRST_MS + index * settleStep
                var y = topPx

                if (!phrase) {
                    val pool = PasswordEngine.charset(spec)
                    shown.forEachIndexed { index, char ->
                        val ok = settled(index)
                        val glyphChar = if (ok || pool.isEmpty()) char else pool.random()
                        val glyph = measurer.measure(glyphChar.toString(), bigStyle.copy(color = if (ok) passwordTint(c, char) else c.mutedForeground))
                        val x = left + (index % perLine) * pitch
                        drawText(glyph, topLeft = Offset(x + (pitch - glyph.size.width) / 2, y + (index / perLine) * bigLine))
                    }
                    y += lines * bigLine + 16 * dp
                    drawPool(measurer, c, spec, shown.toSet(), left, y, poolPitch, hair)
                    y += poolPitch * 4
                } else {
                    val separator = if (phraseSpec.separator == " ") "" else phraseSpec.separator
                    val gap = measurer.width(" ${phraseSpec.separator} ", wordStyle)
                    words.forEachIndexed { index, word ->
                        val origin = Offset(left + word.x, y + word.y)
                        drawText(measurer.measure(word.text, wordStyle.copy(color = c.foreground)), topLeft = origin)
                        if (index < words.size - 1 && separator.isNotEmpty()) {
                            val mark = measurer.measure(separator, wordStyle.copy(color = c.mutedForeground))
                            drawText(mark, topLeft = Offset(origin.x + word.width + (gap - mark.size.width) / 2, origin.y))
                        }
                        val faces = if (settled(index)) word.roll?.map { it.digitToInt() } else List(5) { Random.nextInt(1, 7) }
                        drawDice(c, faces, word.die, word.columns, Offset(origin.x, origin.y + wordLine + 4 * dp), hair)
                    }
                    val last = words.lastOrNull()
                    if (digit != null && last != null) {
                        val glyph = measurer.measure(digit, wordStyle.copy(color = c.primary))
                        drawText(glyph, topLeft = Offset(left + last.x + last.width + 1 * dp, y + last.y))
                    }
                    y += phraseHeight
                }

                y += 18 * dp
                val scale = 4 * dp
                drawRect(c.muted, Offset(left, y), Size(room, scale))
                drawRect(bandColour(c, bits), Offset(left, y), Size(room * (minOf(bits, SCALE_END) / SCALE_END).toFloat(), scale))
                for (mark in BANDS) {
                    drawRect(c.card, Offset((left + room * (mark / SCALE_END).toFloat()).roundToInt().toFloat(), y), Size(2 * dp, scale))
                }
            }
            Row(
                Modifier.fillMaxWidth().height(40.dp).padding(horizontal = CanonMetrics.panelPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CanonFigure(figure, unit = "Bits", size = 26.sp, colour = bandColour(c, bits), halo = c.card)
                Spacer(Modifier.weight(1f))
                Text(band, style = CanonText.footnoteSemibold, color = bandColour(c, bits))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun DrawScope.drawPool(
    measurer: TextMeasurer,
    c: DelphiColours,
    spec: PasswordEngine.PasswordSpec,
    used: Set<Char>,
    left: Float,
    y: Float,
    pitch: Float,
    hair: Float,
) {
    val cell = pitch - 2 * density
    val style = monoBase.copy(fontSize = (cell * 0.62f / density).dp.toSp())
    val enabled = listOf(spec.lowercase, spec.uppercase, spec.digits, spec.symbols)
    POOL_SETS.forEachIndexed { row, set ->
        set.forEachIndexed { column, char ->
            val box = Offset(left + column * pitch, y + row * pitch)
            val struck = spec.excludeAmbiguous && char in PasswordEngine.AMBIGUOUS
            val on = enabled[row] && !struck
            var ink = c.mutedForeground.copy(alpha = if (on) 1f else 0.3f)
            if (on && char in used) {
                drawRect(passwordTint(c, char), box, Size(cell, cell))
                ink = c.card
            } else if (on) {
                drawRect(c.muted, box, Size(cell, cell))
            }
            val glyph = measurer.measure(char.toString(), style.copy(color = ink))
            drawText(glyph, topLeft = Offset(box.x + (cell - glyph.size.width) / 2, box.y + (cell - glyph.size.height) / 2))
            if (struck && enabled[row]) {
                val inset = 2 * density
                drawLine(c.mutedForeground, Offset(box.x + inset, box.y + cell - inset), Offset(box.x + cell - inset, box.y + inset), hair)
            }
        }
    }
}

private fun DrawScope.drawDice(c: DelphiColours, faces: List<Int>?, die: Float, columns: Int, origin: Offset, hair: Float) {
    // pips stay apart
    val pip = maxOf(1.5f * density, die / 6)
    val gap = DIE_GAP * density
    for (slot in 0 until 5) {
        val box = Offset(origin.x + (slot % columns) * (die + gap), origin.y + (slot / columns) * (die + gap))
        drawRect(c.mutedForeground, box + Offset(hair / 2, hair / 2), Size(die - hair, die - hair), style = Stroke(hair))
        val face = faces?.getOrNull(slot)?.takeIf { it in 1..6 } ?: continue
        for ((gx, gy) in PIPS[face - 1]) {
            val centre = Offset(box.x + die * (0.25f + 0.25f * gx), box.y + die * (0.25f + 0.25f * gy))
            drawRect(c.foreground, centre - Offset(pip / 2, pip / 2), Size(pip, pip))
        }
    }
}
