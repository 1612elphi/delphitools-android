package com.delphi.delphitools.tools.devtools

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSharedText
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonTextInput
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonMathInks
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val listingFont = TextStyle(fontFamily = DelphiMono, fontSize = 13.sp)
private val gutterFont = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp)
private val markerFont = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
private const val FULL_LINE = 72.0
private const val WIDEST = 760f
private const val FIGURE_ROW = 40f

class TextDiffModel : ViewModel() {
    var original by mutableStateOf("")
    var modified by mutableStateOf("")
    var diff by mutableStateOf<List<DiffLine>>(emptyList())
        private set

    /** Whether the texts the diff was made from were not both empty. */
    var hasText by mutableStateOf(false)
        private set
    var wraps by mutableStateOf(true)
    private var job: Job? = null

    val same get() = diff.all { it is DiffLine.Unchanged }

    fun refresh() {
        val old = original
        val new = modified
        job?.cancel()
        job = viewModelScope.launch {
            diff = withContext(Dispatchers.Default) { if (old.isEmpty() && new.isEmpty()) emptyList() else TextDiffer.diff(old, new) }
            hasText = old.isNotEmpty() || new.isNotEmpty()
        }
    }

    fun swap() {
        original = modified.also { modified = original }
        refresh()
    }
}

/** dt-ios DiffListing.build: the listing text, its marks and the gutter's width in characters. */
internal class DiffListing(val text: String, val marks: TextMarks, val gutterColumns: Int)

internal fun buildListing(diff: List<DiffLine>, muted: Color, removed: Color, added: Color): DiffListing {
    val highest = diff.maxOfOrNull {
        when (it) {
            is DiffLine.Unchanged -> maxOf(it.leftIndex, it.rightIndex) + 1
            is DiffLine.Removed -> it.leftIndex + 1
            is DiffLine.Added -> it.rightIndex + 1
        }
    } ?: 1
    val digits = highest.toString().length
    fun number(value: Int?) = (value?.toString() ?: "").padStart(digits)

    val out = StringBuilder()
    val starts = mutableListOf<Int>()
    val gutter = mutableListOf<AnnotatedString>()
    val lines = mutableListOf<TextMark>()
    diff.forEachIndexed { index, line ->
        val start = out.length
        starts += start
        out.append(line.text.ifEmpty { " " })
        if (index < diff.size - 1) out.append('\n')
        val range = TextSpan(start, out.length)
        val (left, right, marker, colour) = when (line) {
            is DiffLine.Unchanged -> Quad(line.leftIndex + 1, line.rightIndex + 1, " ", muted)
            is DiffLine.Removed -> Quad(line.leftIndex + 1, null, "−", removed)
            is DiffLine.Added -> Quad(null, line.rightIndex + 1, "+", added)
        }
        gutter += buildAnnotatedString {
            withStyle(gutterFont.toSpanStyle().copy(color = muted)) { append(number(left) + " " + number(right) + " ") }
            withStyle(markerFont.toSpanStyle().copy(color = colour)) { append(marker) }
        }
        when (line) {
            is DiffLine.Removed -> lines += TextMark(range, removed.copy(alpha = 0.1f))
            is DiffLine.Added -> lines += TextMark(range, added.copy(alpha = 0.13f))
            is DiffLine.Unchanged -> Unit
        }
    }

    val spans = mutableListOf<TextMark>()
    var index = 0
    while (index < diff.size) {
        if (diff[index] !is DiffLine.Removed) {
            index++
            continue
        }
        var end = index
        while (end < diff.size && diff[end] is DiffLine.Removed) end++
        var addedEnd = end
        while (addedEnd < diff.size && diff[addedEnd] is DiffLine.Added) addedEnd++
        for (offset in 0 until minOf(end - index, addedEnd - end)) {
            val old = index + offset
            val new = end + offset
            val (gone, came) = DiffWords.changes(diff[old].text, diff[new].text) ?: continue
            spans += gone.map { TextMark(TextSpan(starts[old] + it.start, starts[old] + it.end), removed.copy(alpha = 0.24f)) }
            spans += came.map { TextMark(TextSpan(starts[new] + it.start, starts[new] + it.end), added.copy(alpha = 0.3f)) }
        }
        index = addedEnd
    }
    spans.sortBy { it.range.start }
    return DiffListing(out.toString(), TextMarks(lines = lines, spans = spans.toList(), gutter = gutter), if (diff.isEmpty()) 0 else digits * 2 + 3)
}

private data class Quad(val left: Int?, val right: Int?, val marker: String, val colour: Color)

@Composable
fun TextDiff(model: TextDiffModel = viewModel()) {
    CanonSharedText {
        model.original = it
        model.refresh()
    }
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val green = canonMathInks().green
    val diff = model.diff
    val hasText = model.hasText
    val same = model.same
    val patch = remember(diff) { TextDiffer.patch(diff) }
    val listing = remember(diff, c, green) { buildListing(diff, c.mutedForeground, c.destructive, green) }

    val inputs = CanonSection.of {
        CanonRows {
            CanonHeading("Original")
            CanonTextInput(model.original, { model.original = it }, { model.refresh() }, height = 160.dp, label = "Original")
            CanonHeading("Modified")
            CanonTextInput(model.modified, { model.modified = it }, { model.refresh() }, height = 160.dp, label = "Modified")
            CanonGrid {
                CanonButton("Swap", Modifier.weight(1f).fillMaxHeight(), icon = pencil(R.drawable.pencil_arrow_up_down), onClick = model::swap)
            }
        }
    }

    val output = CanonSection.of {
        CanonRows {
            CanonHeading("Differences")
            CanonRow("Wrap") { CanonSwitch(model.wraps) { model.wraps = it } }
            when {
                !hasText -> CanonEmptyRow("Nothing to compare")
                same -> CanonEmptyRow("Texts are identical")
                else -> MarkedText(
                    listing.text,
                    listing.marks,
                    listingFont,
                    Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Differences" },
                    wraps = model.wraps,
                    maxHeight = 600.dp,
                    gutterColumns = listing.gutterColumns,
                    gutterStyle = gutterFont,
                )
            }
            val enabled = hasText && !same
            CanonGrid {
                CanonButton("Share", Modifier.weight(1f).fillMaxHeight(), enabled = enabled, icon = pencil(R.drawable.pencil_share)) {
                    scope.launch { FileExport.share(context, patch.toByteArray(), "changes.diff", "text/x-diff") }
                }
                CanonButton(
                    "Copy patch",
                    Modifier.weight(1f).fillMaxHeight(),
                    kind = CanonButtonKind.Primary,
                    enabled = enabled,
                    icon = pencil(R.drawable.pencil_copy),
                    oversized = true,
                    flashesCopied = true,
                ) {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("", patch))
                }
            }
        }
    }

    val banner = CanonSection.canvas(CanonCanvasSize.Fitting) { DiffRibbons(diff, green) }

    CanonSplit(sidebar = listOf(inputs), main = listOf(output), banner = banner)
}

private enum class BlockKind { Same, Removed, Added }

private class Block(val kind: BlockKind, val left: IntRange, val right: IntRange)

private class RibbonData(val blocks: List<Block>, val oldLengths: List<Int>, val newLengths: List<Int>)

private fun ribbonData(diff: List<DiffLine>): RibbonData {
    val blocks = mutableListOf<Block>()
    val old = mutableListOf<Int>()
    val new = mutableListOf<Int>()
    diff.forEach { line ->
        val count = line.text.codePointCount(0, line.text.length)
        val kind = when (line) {
            is DiffLine.Unchanged -> BlockKind.Same.also { old += count; new += count }
            is DiffLine.Removed -> BlockKind.Removed.also { old += count }
            is DiffLine.Added -> BlockKind.Added.also { new += count }
        }
        val left = if (kind == BlockKind.Added) old.size until old.size else old.size - 1 until old.size
        val right = if (kind == BlockKind.Removed) new.size until new.size else new.size - 1 until new.size
        val last = blocks.lastOrNull()
        if (last != null && last.kind == kind) {
            blocks[blocks.size - 1] = Block(kind, last.left.first until left.last + 1, last.right.first until right.last + 1)
        } else {
            blocks += Block(kind, left, right)
        }
    }
    return RibbonData(blocks, old, new)
}

private val IntRange.count get() = last - first + 1

@Composable
private fun DiffRibbons(diff: List<DiffLine>, added: Color) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val removed = c.destructive
    val data = remember(diff) { ribbonData(diff) }
    val blocks = data.blocks
    val pad = CanonMetrics.panelPadding.value
    val top = LocalCanonCanvas.current.top.value + pad * 0.75f
    val rows = maxOf(data.oldLengths.size, data.newLengths.size, 1)
    val mapHeight = minOf(220f, maxOf(72f, rows * 7f))
    val small = CanonText.captionSemibold
    val smallLine = with(density) { measurer.measure("0", small).size.height.toDp().value }
    val height = top + FIGURE_ROW + 8 + mapHeight + 6 + smallLine + pad * 0.75f
    val sameLeft = blocks.filter { it.kind == BlockKind.Same }.sumOf { it.left.count }
    val sameRight = blocks.filter { it.kind == BlockKind.Same }.sumOf { it.right.count }
    val removedCount = data.oldLengths.size - sameLeft
    val addedCount = data.newLengths.size - sameRight
    val blank = diff.isEmpty()

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clearAndSetSemantics {
                contentDescription = "Differences"
                if (!blank) stateDescription = "Removed $removedCount, Added $addedCount"
            },
    ) {
        val areaWidth = maxWidth.value - pad * 2
        val width = minOf(areaWidth, WIDEST)
        val x = pad + (areaWidth - width) / 2
        val column = width * 0.34f
        val y = top + FIGURE_ROW + 8
        val left = Rect(x, y, x + column.roundToInt(), y + mapHeight)
        val right = Rect(x + width - column.roundToInt(), y, x + width, y + mapHeight)

        Canvas(Modifier.fillMaxSize()) {
            fun r(rect: Rect) = Rect(rect.left.dp.toPx(), rect.top.dp.toPx(), rect.right.dp.toPx(), rect.bottom.dp.toPx())
            val l = r(left)
            val rr = r(right)
            fun caption(name: String, count: Int?, column: Rect, trailing: Boolean) {
                val text = buildAnnotatedString {
                    withStyle(SpanStyle(color = c.foreground)) { append(name) }
                    if (count != null) withStyle(SpanStyle(color = c.mutedForeground)) { append("  $count") }
                }
                val measured = measurer.measure(text, small)
                drawText(measured, topLeft = Offset(if (trailing) column.right - measured.size.width else column.left, column.bottom + 6.dp.toPx()))
            }
            if (blocks.isEmpty()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                val hairline = CanonMetrics.hairline.toPx()
                for (box in listOf(l, rr)) {
                    drawRect(c.mutedForeground, Offset(box.left + hairline / 2, box.top + hairline / 2), Size(box.width - hairline, box.height - hairline), style = Stroke(hairline, pathEffect = dash))
                }
                caption("Original", null, l, false)
                caption("Modified", null, rr, true)
                return@Canvas
            }
            drawRibbons(data, l, rr, rows, removed, added, c.foreground)
            caption("Original", data.oldLengths.size, l, false)
            caption("Modified", data.newLengths.size, rr, true)
        }

        if (!blank) {
            val figureTop = (top + FIGURE_ROW / 2).dp
            Row(
                Modifier.offset(x = left.left.dp).layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, figureTop.roundToPx() - placeable.height / 2) }
                },
                verticalAlignment = Alignment.Bottom,
            ) {
                CanonFigure(if (removedCount > 0) "−$removedCount" else "0", size = 26.sp, colour = if (removedCount > 0) removed else c.mutedForeground, halo = c.card)
                Text("Removed", Modifier.padding(start = 8.dp, bottom = 5.dp), style = CanonText.footnoteSemibold, color = c.mutedForeground)
            }
            Box(Modifier.fillMaxWidth().padding(end = (maxWidth.value - right.right).coerceAtLeast(0f).dp)) {
                Row(
                    Modifier.align(Alignment.TopEnd).layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, figureTop.roundToPx() - placeable.height / 2) }
                    },
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text("Added", Modifier.padding(end = 8.dp, bottom = 5.dp), style = CanonText.footnoteSemibold, color = c.mutedForeground)
                    CanonFigure(if (addedCount > 0) "+$addedCount" else "0", size = 26.sp, colour = if (addedCount > 0) added else c.mutedForeground, halo = c.card)
                }
            }
        }
    }
}

private fun ribbon(from: Float, to: Float, top: Pair<Float, Float>, bottom: Pair<Float, Float>): Path {
    val middle = (from + to) / 2
    val bottomLeft = maxOf(bottom.first, top.first + 1)
    val bottomRight = maxOf(bottom.second, top.second + 1)
    return Path().apply {
        moveTo(from, top.first)
        cubicTo(middle, top.first, middle, top.second, to, top.second)
        lineTo(to, bottomRight)
        cubicTo(middle, bottomRight, middle, bottomLeft, from, bottomLeft)
        close()
    }
}

private fun DrawScope.drawRibbons(data: RibbonData, left: Rect, right: Rect, rows: Int, removed: Color, added: Color, ink: Color) {
    val blocks = data.blocks
    val pitch = left.height / rows
    fun y(line: Int, column: Rect) = column.top + line * pitch
    val innerLeft = left.right + 6.dp.toPx()
    val innerRight = right.left - 6.dp.toPx()
    val hairline = CanonMetrics.hairline.toPx()
    val gap = 0.75.dp.toPx()

    // ribbons before bars
    blocks.forEachIndexed { index, block ->
        if (block.kind == BlockKind.Added && index > 0 && blocks[index - 1].kind == BlockKind.Removed) return@forEachIndexed
        val top = y(block.left.first, left) to y(block.right.first, right)
        var bottom = y(block.left.last + 1, left) to y(block.right.last + 1, right)
        val colours: List<Color> = when (block.kind) {
            BlockKind.Same -> listOf(ink.copy(alpha = 0.11f))
            BlockKind.Removed -> {
                val next = blocks.getOrNull(index + 1)
                if (next != null && next.kind == BlockKind.Added) {
                    bottom = bottom.first to y(next.right.last + 1, right)
                    listOf(removed.copy(alpha = 0.34f), added.copy(alpha = 0.38f))
                } else {
                    listOf(removed.copy(alpha = 0.34f), removed.copy(alpha = 0.06f))
                }
            }
            BlockKind.Added -> listOf(added.copy(alpha = 0.06f), added.copy(alpha = 0.38f))
        }
        // gap separates bands
        val path = ribbon(innerLeft, innerRight, top.first + gap to top.second + gap, bottom.first - gap to bottom.second - gap)
        clipPath(path) {
            if (colours.size == 1) {
                drawRect(colours[0])
            } else {
                drawRect(Brush.horizontalGradient(colours, startX = innerLeft, endX = innerRight))
            }
        }
        val stroke = if (block.kind == BlockKind.Same) ink.copy(alpha = 0.22f) else colours[0].copy(alpha = 0.7f)
        drawPath(path, stroke, style = Stroke(hairline))
    }

    val bar = minOf(pitch * 0.7f, 5.dp.toPx()).coerceAtLeast(1.dp.toPx())
    blocks.forEach { block ->
        val colour = when (block.kind) {
            BlockKind.Same -> ink.copy(alpha = 0.3f)
            BlockKind.Removed -> removed
            BlockKind.Added -> added
        }
        for ((column, lines, lengths) in listOf(Triple(left, block.left, data.oldLengths), Triple(right, block.right, data.newLengths))) {
            if (lines.isEmpty()) continue
            if (pitch < 2.dp.toPx()) {
                drawRect(colour, Offset(column.left, y(lines.first, column)), Size(column.width, maxOf(1.dp.toPx(), lines.count * pitch)))
                continue
            }
            for (line in lines) {
                if (lengths[line] <= 0) continue
                val share = minOf(1.0, lengths[line] / FULL_LINE).toFloat()
                val width = maxOf(3.dp.toPx(), column.width * share)
                // left column aligns right
                val x = if (column == left) column.right - width else column.left
                drawRect(colour, Offset(x, y(line, column) + (pitch - bar) / 2), Size(width, bar))
            }
        }
    }
}
