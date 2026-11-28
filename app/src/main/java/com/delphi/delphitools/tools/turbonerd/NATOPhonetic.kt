package com.delphi.delphitools.tools.turbonerd

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonGlyphFlow
import com.delphi.delphitools.canon.CanonGlyphKind
import com.delphi.delphitools.canon.CanonGlyphMetrics
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSharedText
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTextInput
import com.delphi.delphitools.canon.CanonTextOutput
import com.delphi.delphitools.canon.rememberCanonGlyphFlowState
import com.delphi.delphitools.tools.turbonerd.SignalArt.drawFlag
import com.delphi.delphitools.tools.turbonerd.SignalArt.drawSemaphore
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiColours
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.ceil
import kotlin.math.max

private const val AUTO = 0
private const val SPELL = 1
private const val READ = 2

@Composable
fun NATOPhonetic() {
    var alphabetIndex by rememberSaveable { mutableIntStateOf(0) }
    var direction by rememberSaveable { mutableIntStateOf(AUTO) }
    var text by rememberSaveable { mutableStateOf("") }
    var settled by rememberSaveable { mutableStateOf("") }
    CanonSharedText {
        text = it
        settled = it
    }
    val alphabet = SpellingAlphabets.all[alphabetIndex]

    val reading = direction == READ || (direction == AUTO && SpellingAlphabets.looksSpelled(settled, alphabet))
    val converted = if (reading) SpellingAlphabets.unspell(settled, alphabet) else SpellingAlphabets.spellText(settled, alphabet)
    val plain = if (reading) converted else settled

    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Alphabet")
            CanonSegmented(SpellingAlphabets.all.map { it.label }, alphabetIndex, columns = 2) { alphabetIndex = it }
            CanonHeading("Direction")
            CanonSegmented(listOf("Auto", "Spell", "Read"), direction) { direction = it }
            CanonHeading("Input")
            CanonTextInput(text, { text = it }, { settled = it })
        }
    }
    val output = CanonSection.of {
        CanonTextOutput(converted, heading = if (reading) "Text" else "Spelled", fileName = "spelled.txt", style = CanonText.bodyMedium)
    }
    val chart = CanonSection.of {
        CanonRows {
            CanonHeading("Chart · Tap to spell")
            NATOChart(alphabet, used = if (reading) "" else settled.uppercase()) { letter ->
                if (reading) direction = SPELL
                text += letter
                settled = text
            }
        }
    }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(164.dp)) {
        SignalHoist(SpellingAlphabets.spell(plain, alphabet), SpellingAlphabets.spellText(plain, alphabet), Modifier.fillMaxSize())
    }

    CanonSplit(sidebar = listOf(controls), main = listOf(output, chart), banner = banner, collapsesTo = 100.dp)
}

// banner

private val flagSize = Size(44f, 33f)

/** halyard to flag top */
private const val DROP = 7f
private val wordStyle = TextStyle(fontFamily = DelphiFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
private val charStyle = TextStyle(fontFamily = DelphiFont, fontSize = 17.sp, fontWeight = FontWeight.Bold)

@Composable
private fun SignalHoist(spelled: List<SpellingAlphabets.SpelledChar>, value: String, modifier: Modifier) {
    val c = LocalDelphiColours.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val flow = rememberCanonGlyphFlowState<SpellingAlphabets.SpelledChar>(
        CanonGlyphMetrics(height = (DROP + flagSize.height + 22).dp, rowGap = 14.dp, gap = 8.dp),
        measure = { glyph ->
            val word = glyph.word?.let { ceil(measurer.measure(it, wordStyle).size.width / density.density) } ?: 0f
            max(flagSize.width, word).dp
        },
        kind = {
            when {
                it.char == "\n" -> CanonGlyphKind.Newline
                it.char.isBlank() -> CanonGlyphKind.Space
                else -> CanonGlyphKind.Glyph
            }
        },
    )
    LaunchedEffect(spelled) { flow.show(spelled) }
    CanonGlyphFlow(
        flow,
        modifier
            .background(c.card)
            .semantics {
                contentDescription = "Spelled"
                stateDescription = value
            },
        paintEmpty = { area -> drawHalyard(area.left, area.right, area.center.y - flagSize.height.dp.toPx() / 2, false, c) },
    ) { glyph, _, frame -> drawSignal(glyph, frame, c, measurer) }
}

private fun DrawScope.drawHalyard(left: Float, right: Float, y: Float, dashed: Boolean, c: DelphiColours) {
    drawLine(
        c.mutedForeground.copy(alpha = if (dashed) 0.45f else 0.6f),
        Offset(left, y),
        Offset(right, y),
        1.dp.toPx(),
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null,
    )
}

private fun DrawScope.drawSignal(glyph: SpellingAlphabets.SpelledChar, frame: Rect, c: DelphiColours, measurer: TextMeasurer) {
    val reach = 8.dp.toPx() / 2
    val line = frame.top + 0.5.dp.toPx()
    // tackline between words
    if (glyph.char.isBlank()) return drawHalyard(frame.left - reach, frame.right + reach, line, true, c)
    drawHalyard(frame.left - reach, frame.right + reach, line, false, c)

    // ß uppercases to ss
    val upper = glyph.char.uppercase()
    val letter = (if (upper.length == 1) upper else glyph.char)[0]
    val width = flagSize.width.dp.toPx()
    val height = flagSize.height.dp.toPx()
    val flag = Rect(frame.center.x - width / 2, frame.top + DROP.dp.toPx(), frame.center.x + width / 2, frame.top + DROP.dp.toPx() + height)
    // toggles to the halyard
    val hairline = 1.dp.toPx()
    for (x in listOf(flag.left + hairline / 2, flag.right - hairline / 2)) {
        drawLine(c.mutedForeground.copy(alpha = 0.6f), Offset(x, line), Offset(x, flag.top), hairline)
    }

    if (glyph.char.length == 1 && SignalArt.hasFlag(letter)) {
        drawFlag(letter, flag, c.border)
    } else {
        drawRect(c.muted, flag.topLeft, flag.size)
        drawRect(c.border, Offset(flag.left + hairline / 2, flag.top + hairline / 2), Size(flag.width - hairline, flag.height - hairline), style = Stroke(hairline))
        val laid = measurer.measure(glyph.char, charStyle.copy(color = c.foreground))
        drawText(laid, topLeft = Offset(flag.center.x - laid.size.width / 2f, flag.center.y - laid.size.height / 2f))
    }
    val word = glyph.word ?: return
    val laid = measurer.measure(word, wordStyle.copy(color = c.foreground))
    drawText(laid, topLeft = Offset(frame.center.x - laid.size.width / 2f, flag.bottom + 4.dp.toPx()))
}

// chart

private const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private val tileWidth = 108.dp
private val tileHeight = 104.dp
private val letterStyle = TextStyle(fontFamily = DelphiFont, fontSize = 22.sp, fontWeight = FontWeight.Bold)

@Composable
private fun NATOChart(alphabet: SpellingAlphabet, used: String, onLetter: (Char) -> Unit) {
    val c = LocalDelphiColours.current
    val line = CanonMetrics.hairline
    BoxWithConstraints(Modifier.fillMaxWidth().background(c.border)) {
        val columns = max(3, ((maxWidth + line) / (tileWidth + line)).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(line)) {
            LETTERS.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(tileHeight), horizontalArrangement = Arrangement.spacedBy(line)) {
                    row.forEach { letter ->
                        ChartTile(letter, alphabet.words[letter.toString()] ?: "", letter in used, Modifier.weight(1f)) { onLetter(letter) }
                    }
                    // empty trailing cells
                    val missing = columns - row.length
                    if (missing > 0) Box(Modifier.weight(missing.toFloat()).height(tileHeight).background(c.card))
                }
            }
        }
    }
}

@Composable
private fun ChartTile(letter: Char, word: String, used: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    val measurer = rememberTextMeasurer()
    Box(
        modifier
            .height(tileHeight)
            .background(if (used) c.accentBg else c.card)
            .semantics(mergeDescendants = true) {
                contentDescription = word
                selected = used
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(58.dp)) { drawChartArt(letter, c, measurer) }
        Text(
            word,
            style = CanonText.caption.copy(fontWeight = FontWeight.Medium),
            color = c.mutedForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

private fun DrawScope.drawChartArt(letter: Char, c: DelphiColours, measurer: TextMeasurer) {
    drawText(measurer.measure(letter.toString(), letterStyle.copy(color = c.foreground)), topLeft = Offset(0f, -4.dp.toPx()))

    val code = MorseEngine.table[letter] ?: ""
    val marks = code.map { (if (it == '-') 10 else 3).dp.toPx() }
    val spacing = 2.dp.toPx()
    var x = size.width - (marks.sum() + max(0, marks.size - 1) * spacing)
    for (width in marks) {
        drawRect(c.primary, Offset(x, 10.dp.toPx()), Size(width, 3.dp.toPx()))
        x += width + spacing
    }

    val side = 26.dp.toPx()
    val top = size.height - side
    drawSemaphore(letter, Rect(0f, top, side, top + side), c.foreground)
    val inset = 4.dp.toPx()
    val flagLeft = side + 8.dp.toPx()
    drawFlag(letter, Rect(flagLeft, top + inset / 2, flagLeft + (side - inset) * 4 / 3, top + inset / 2 + side - inset), c.border)
}
