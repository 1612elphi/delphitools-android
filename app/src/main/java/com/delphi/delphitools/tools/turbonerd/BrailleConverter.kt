package com.delphi.delphitools.tools.turbonerd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonGlyphFlow
import com.delphi.delphitools.canon.CanonGlyphKind
import com.delphi.delphitools.canon.CanonGlyphMetrics
import com.delphi.delphitools.canon.CanonGlyphRows
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTextInput
import com.delphi.delphitools.canon.CanonTextOutput
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.rememberCanonGlyphFlowState
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiColours
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.LocalDelphiColours

private const val PAGE_CELLS = 40
private const val PAGE_LINES = 25
private const val DASH = "—"
private val brailleStyle = TextStyle(fontSize = 34.sp)

@Composable
fun BrailleConverter() {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") }
    var settled by rememberSaveable { mutableStateOf("") }

    val toText = mode == 2 || (mode == 0 && BrailleEngine.looksLikeBraille(settled))
    val converted = if (toText) BrailleEngine.fromBraille(settled) else BrailleEngine.toBraille(settled)
    val braille = if (toText) settled else converted

    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Mode")
            CanonSegmented(listOf("Auto", "To braille", "To text"), mode) { mode = it }
            CanonHeading("Input")
            CanonTextInput(text, { text = it }, { settled = it })
        }
    }
    val output = CanonSection.of {
        CanonTextOutput(
            converted,
            heading = if (toText) "Text" else "Braille",
            fileName = "braille.txt",
            style = if (toText) CanonText.body else brailleStyle,
        )
    }
    val page = CanonSection.of {
        val cells = braille.count { it != '\n' }
        val lines = CanonGlyphRows.layout(
            braille.map { 1f },
            braille.map { if (it == '\n') CanonGlyphKind.Newline else if (it == ' ' || it == '⠀') CanonGlyphKind.Space else CanonGlyphKind.Glyph },
            PAGE_CELLS.toFloat(), 0f, 1f, 1f,
        ).rows
        fun shown(n: Int) = if (cells > 0) "%,d".format(n) else DASH
        CanonRows {
            CanonHeading("Page size · $PAGE_CELLS × $PAGE_LINES")
            CanonTiles(
                listOf(
                    CanonTile("Cells", shown(cells)),
                    CanonTile("Lines", shown(lines)),
                    CanonTile("Pages", shown((lines + PAGE_LINES - 1) / PAGE_LINES)),
                ),
            )
        }
    }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(BAND_HEIGHT.dp)) {
        BrailleEmbossing(BrailleEngine.read(braille), if (toText) converted else settled, Modifier.fillMaxSize())
    }

    CanonSplit(sidebar = listOf(controls), main = listOf(output, page), banner = banner, collapsesTo = COLLAPSED_HEIGHT.dp)
}

private const val BAND_HEIGHT = 150
private const val COLLAPSED_HEIGHT = 84
private const val DOT_PITCH = 10f
private const val DOT_RADIUS = 3.2f
private const val DOT_GAP = 8f
private const val LABEL_ROOM = 18f
private const val CELL_WIDTH = DOT_PITCH + DOT_RADIUS * 2
private fun cellHeight(rows: Int) = DOT_PITCH * (rows - 1) + DOT_RADIUS * 2
private val labelStyle = TextStyle(fontFamily = DelphiFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
private val plainStyle = TextStyle(fontFamily = DelphiFont, fontSize = 17.sp, fontWeight = FontWeight.Medium)

private enum class Tone { Cell, Indicator, Plain, Space, Newline }

private data class Glyph(val cell: String, val label: String, val tone: Tone)

/** dot 1 lowest bit */
private fun mask(cell: String): Int? {
    if (cell.length != 1 || cell[0].code !in 0x2800..0x28ff) return null
    return cell[0].code - 0x2800
}

private fun indicatorLabel(cells: String) = when (BrailleEngine.dots(cells.take(1))) {
    "6" -> "⇧"
    "3-4-5-6" -> "#"
    else -> ""
}

private fun glyphs(readings: List<BrailleEngine.Reading>): List<Glyph> = buildList {
    for (reading in readings) {
        BrailleEngine.codePoints(reading.cells).forEachIndexed { index, cell ->
            val tone = when {
                cell == "\n" -> Tone.Newline
                cell == " " || cell == "⠀" -> Tone.Space
                mask(cell) == null -> Tone.Plain
                reading.kind == BrailleEngine.Reading.Kind.Indicator -> Tone.Indicator
                else -> Tone.Cell
            }
            val label = if (tone == Tone.Indicator) indicatorLabel(reading.cells) else if (index == 0) reading.text else ""
            add(Glyph(cell, label, tone))
        }
    }
}

private fun metrics(rows: Int) = CanonGlyphMetrics(height = (cellHeight(rows) + LABEL_ROOM).dp, rowGap = 10.dp, gap = DOT_GAP.dp)

@Composable
private fun BrailleEmbossing(readings: List<BrailleEngine.Reading>, value: String, modifier: Modifier) {
    val c = LocalDelphiColours.current
    val measurer = rememberTextMeasurer()
    val glyphs = remember(readings) { glyphs(readings) }
    val rows = if (glyphs.any { (mask(it.cell) ?: 0) > 0x3f }) 4 else 3
    val flow = rememberCanonGlyphFlowState<Glyph>(
        metrics(rows),
        measure = { if (it.tone == Tone.Newline) 0.dp else CELL_WIDTH.dp },
        kind = {
            when (it.tone) {
                Tone.Space -> CanonGlyphKind.Space
                Tone.Newline -> CanonGlyphKind.Newline
                else -> CanonGlyphKind.Glyph
            }
        },
    )
    LaunchedEffect(glyphs) { flow.show(glyphs) }
    CanonGlyphFlow(
        flow,
        modifier
            .background(c.card)
            .semantics {
                contentDescription = "Braille"
                stateDescription = value
            },
        paintEmpty = { area -> drawBlank(area, c) },
    ) { glyph, _, frame -> drawGlyph(glyph, frame, rows, c, measurer) }
}

/** unicode braille dot order */
private fun position(dot: Int): Pair<Int, Int> = when (dot) {
    0, 1, 2 -> 0 to dot
    3, 4, 5 -> 1 to dot - 3
    6 -> 0 to 3
    else -> 1 to 3
}

private fun DrawScope.drawCell(mask: Int, rows: Int, origin: Offset, ink: Color, c: DelphiColours) {
    val r = DOT_RADIUS.dp.toPx()
    val pitch = DOT_PITCH.dp.toPx()
    for (bit in 0 until if (rows == 4) 8 else 6) {
        val (column, row) = position(bit)
        val centre = Offset(origin.x + r + column * pitch, origin.y + r + row * pitch)
        if (mask and (1 shl bit) != 0) {
            drawCircle(ink, r, centre)
        } else {
            drawCircle(c.mutedForeground.copy(alpha = 0.35f), 1.1.dp.toPx(), centre)
        }
    }
}

private fun DrawScope.drawGlyph(glyph: Glyph, frame: Rect, rows: Int, c: DelphiColours, measurer: TextMeasurer) {
    val cellFrame = Rect(frame.left, frame.top, frame.right, frame.top + cellHeight(rows).dp.toPx())
    when (glyph.tone) {
        Tone.Plain -> {
            val laid = measurer.measure(glyph.cell, plainStyle.copy(color = c.mutedForeground))
            drawText(laid, topLeft = Offset(cellFrame.center.x - laid.size.width / 2f, cellFrame.center.y - laid.size.height / 2f))
        }
        Tone.Cell, Tone.Indicator -> {
            val ink = if (glyph.tone == Tone.Indicator) c.primary else c.foreground
            drawCell(mask(glyph.cell) ?: 0, rows, cellFrame.topLeft, ink, c)
        }
        Tone.Space, Tone.Newline -> return
    }
    if (glyph.label.isEmpty()) return
    val colour = if (glyph.tone == Tone.Indicator) c.primary else c.mutedForeground
    val laid = measurer.measure(glyph.label, labelStyle.copy(color = colour))
    drawText(laid, topLeft = Offset(frame.center.x - laid.size.width / 2f, cellFrame.bottom + 4.dp.toPx()))
}

private fun DrawScope.drawBlank(area: Rect, c: DelphiColours) {
    val gap = DOT_GAP.dp.toPx()
    val pitch = CELL_WIDTH.dp.toPx() + gap
    val count = maxOf(1, ((area.width + gap) / pitch).toInt())
    val x = area.left + (area.width - (count * pitch - gap)) / 2
    val y = area.center.y - cellHeight(3).dp.toPx() / 2
    for (index in 0 until count) drawCell(0, 3, Offset(x + index * pitch, y), Color.Transparent, c)
}
