package com.delphi.delphitools.tools.devtools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.ui.theme.LocalDelphiColours

data class TextMark(val range: TextSpan, val colour: Color)

/** [level] 1 is the rail nearest the text. */
data class TextRail(val range: TextSpan, val level: Int, val colour: Color)

data class TextCaret(val location: Int, val colour: Color)

/**
 * Marks drawn under a [MarkedText]'s glyphs; ranges are UTF-16. [lines] fill every line of their range across the
 * whole view, [spans] the glyphs' box, [rails] bracket a range a level below the text (a regex group), [carets]
 * stand between two characters (a zero-width match), and [gutter] has one label per paragraph beside its first line.
 */
data class TextMarks(
    val lines: List<TextMark> = emptyList(),
    val spans: List<TextMark> = emptyList(),
    val rails: List<TextRail> = emptyList(),
    val carets: List<TextCaret> = emptyList(),
    val gutter: List<AnnotatedString> = emptyList(),
)

private const val RAIL_THICKNESS = 2f
private const val RAIL_PITCH = 5f
private val gutterGap = 12.dp

/** Paragraph start offsets: 0, then one past each newline. */
internal fun paragraphStarts(text: String): List<Int> = listOf(0) + text.indices.filter { text[it] == '\n' }.map { it + 1 }

/**
 * dt-ios MarkedTextView: monospaced, selectable text with [TextMarks] under it, in a card. Taller than [maxHeight],
 * or taller than its parent allows, it scrolls inside. Without [wraps] lines run on and the text scrolls sideways.
 * [gutterColumns] characters of [gutterStyle] are kept for the gutter. [reveal] scrolls that offset into view.
 * [lineSpacing] is added under every line, as NSParagraphStyle.lineSpacing.
 */
@Composable
fun MarkedText(
    text: String,
    marks: TextMarks,
    style: TextStyle,
    modifier: Modifier = Modifier,
    wraps: Boolean = true,
    maxHeight: Dp? = null,
    lineSpacing: Dp = 0.dp,
    padding: PaddingValues = PaddingValues(horizontal = CanonMetrics.panelPadding, vertical = 12.dp),
    gutterColumns: Int = 0,
    gutterStyle: TextStyle = style,
    reveal: Int? = null,
) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val measurer = rememberTextMeasurer()
    val vertical = rememberScrollState()
    val sideways = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val natural = remember(style, density) { measurer.measure("0", style) }
    val ascent = natural.firstBaseline
    val descent = natural.size.height - natural.firstBaseline
    val lineHeight = with(density) { (natural.size.height.toDp() + lineSpacing).toSp() }
    val shown = style.copy(
        color = c.foreground,
        lineHeight = lineHeight,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.None),
    )
    val gutterWidth = if (gutterColumns > 0) with(density) { measurer.measure("0".repeat(gutterColumns), gutterStyle).size.width.toDp() } else 0.dp
    val lead = padding.calculateLeftPadding(direction) + if (gutterWidth > 0.dp) gutterWidth + gutterGap else 0.dp
    val top = padding.calculateTopPadding()
    val starts = remember(text) { paragraphStarts(text) }

    LaunchedEffect(reveal, layout) {
        val result = layout ?: return@LaunchedEffect
        val at = reveal?.takeIf { it <= text.length } ?: return@LaunchedEffect
        val line = result.getLineForOffset(at)
        val pad = with(density) { top.toPx() }
        val boxTop = result.getLineTop(line) + pad
        val middle = (boxTop + result.getLineBottom(line) + pad) / 2
        val visible = vertical.value + pad..vertical.value + vertical.viewportSize - pad
        if (middle !in visible) vertical.scrollTo((boxTop - pad).toInt().coerceIn(0, vertical.maxValue))
    }

    BoxWithConstraints(
        modifier
            .background(c.card)
            .then(if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier)
            .verticalScroll(vertical),
    ) {
        val viewport = maxWidth
        Box(
            (if (wraps) Modifier else Modifier.horizontalScroll(sideways))
                .widthIn(min = viewport)
                .drawBehind {
                    val result = layout ?: return@drawBehind
                    val origin = Offset(lead.toPx(), top.toPx())
                    drawLines(result, marks.lines, text, origin)
                    if (gutterWidth > 0.dp && marks.gutter.isNotEmpty()) {
                        val left = padding.calculateLeftPadding(direction).toPx()
                        starts.forEachIndexed { index, start ->
                            val label = marks.gutter.getOrNull(index)?.takeIf { it.isNotEmpty() } ?: return@forEachIndexed
                            if (start > text.length) return@forEachIndexed
                            val measured = measurer.measure(label, gutterStyle)
                            val baseline = result.getLineBaseline(result.getLineForOffset(start))
                            drawText(measured, topLeft = Offset(left, origin.y + baseline - measured.firstBaseline))
                        }
                        val x = origin.x - gutterGap.toPx() / 2 - CanonMetrics.hairline.toPx() / 2
                        drawRect(c.border, Offset(x, origin.y), Size(CanonMetrics.hairline.toPx(), result.size.height.toFloat()))
                    }
                }
                .padding(start = lead, top = top, end = padding.calculateRightPadding(direction), bottom = padding.calculateBottomPadding()),
        ) {
            SelectionContainer {
                Text(
                    text,
                    style = shown,
                    softWrap = wraps,
                    onTextLayout = { layout = it },
                    modifier = Modifier.drawBehind {
                        val result = layout ?: return@drawBehind
                        drawSpans(result, marks, text, ascent, descent)
                    },
                )
            }
        }
    }
}

private class Piece(val left: Float, val right: Float, val line: Int)

/** The range's glyph extent on each line it touches; a line break itself has no width. */
private fun pieces(result: TextLayoutResult, range: TextSpan, text: String): List<Piece> {
    if (range.length <= 0 || range.start >= text.length) return emptyList()
    val end = minOf(range.end, text.length)
    val first = result.getLineForOffset(range.start)
    val last = result.getLineForOffset(end - 1)
    return (first..last).map { line ->
        val from = maxOf(range.start, result.getLineStart(line))
        var to = minOf(end, result.getLineEnd(line))
        while (to > from && text[to - 1] == '\n') to--
        if (to <= from) {
            val edge = result.getLineRight(line)
            Piece(edge, edge, line)
        } else {
            Piece(result.getBoundingBox(from).left, result.getBoundingBox(to - 1).right, line)
        }
    }
}

private fun DrawScope.drawLines(result: TextLayoutResult, lines: List<TextMark>, text: String, origin: Offset) {
    lines.forEach { mark ->
        if (mark.range.start >= text.length) return@forEach
        val first = result.getLineForOffset(mark.range.start)
        val last = result.getLineForOffset(maxOf(mark.range.start, minOf(mark.range.end, text.length) - 1))
        val lineTop = result.getLineTop(first)
        drawRect(mark.colour, Offset(0f, origin.y + lineTop), Size(size.width, result.getLineBottom(last) - lineTop))
    }
}

private fun DrawScope.drawSpans(result: TextLayoutResult, marks: TextMarks, text: String, ascent: Float, descent: Float) {
    val least = 3.dp.toPx()
    marks.spans.forEach { mark ->
        pieces(result, mark.range, text).forEach { piece ->
            val top = result.getLineBaseline(piece.line) - ascent - 1.dp.toPx()
            drawRect(mark.colour, Offset(piece.left, top), Size(maxOf(piece.right - piece.left, least), ascent + descent + 2.dp.toPx()))
        }
    }
    marks.rails.forEach { rail ->
        val shown = pieces(result, rail.range, text)
        val tick = 1.5.dp.toPx()
        shown.forEachIndexed { index, piece ->
            val y = result.getLineBaseline(piece.line) + descent + 3.dp.toPx() + ((rail.level - 1) * RAIL_PITCH).dp.toPx()
            val right = piece.left + maxOf(piece.right - piece.left, least)
            drawRect(rail.colour, Offset(piece.left, y), Size(right - piece.left, RAIL_THICKNESS.dp.toPx()))
            if (index == 0) drawRect(rail.colour, Offset(piece.left, y - 4.dp.toPx()), Size(tick, 4.dp.toPx()))
            if (index == shown.size - 1) drawRect(rail.colour, Offset(right - tick, y - 4.dp.toPx()), Size(tick, 4.dp.toPx()))
        }
    }
    marks.carets.forEach { caret ->
        if (caret.location > text.length) return@forEach
        val line = result.getLineForOffset(caret.location)
        val x = result.getHorizontalPosition(caret.location, usePrimaryDirection = true)
        val baseline = result.getLineBaseline(line)
        drawRect(caret.colour, Offset(x - 1.dp.toPx(), baseline - ascent - 1.dp.toPx()), Size(2.dp.toPx(), ascent + descent + 2.dp.toPx()))
    }
}
