package com.delphi.delphitools.tools.print

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiColours
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import com.delphi.delphitools.ui.theme.selectedTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * dt-ios PrintSheet.draw: [sheet] in [rect], [paper] the theme's light colours (paper is always light).
 * [pictures] by 1-based page; [lit] pages get a primary outline.
 */
fun DrawScope.drawPrintSheet(
    sheet: PrintSheet,
    rect: Rect,
    paper: DelphiColours,
    measurer: TextMeasurer,
    pictures: Map<Int, ImageBitmap> = emptyMap(),
    lit: Set<Int> = emptySet(),
) {
    if (sheet.width <= 0 || rect.width <= 0) return
    val scale = rect.width / sheet.width.toFloat()
    fun map(r: SheetRect) = Rect(
        Offset(rect.left + r.x.toFloat() * scale, rect.top + r.y.toFloat() * scale),
        Size(r.width.toFloat() * scale, r.height.toFloat() * scale),
    )
    fun point(x: Double, y: Double) = Offset(rect.left + x.toFloat() * scale, rect.top + y.toFloat() * scale)
    val foreground = paper.foreground
    val primary = paper.primary
    drawRect(Color.White, rect.topLeft, rect.size)
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        val tint = if (sheet.isBack) paper.muted else primary.copy(alpha = 0.06f)
        for (cell in sheet.cells) {
            val frame = map(cell.frame)
            if (frame.width <= 0.5f || frame.height <= 0.5f) continue
            if (cell.page == 0) {
                hatch(frame, foreground.copy(alpha = 0.14f))
            } else {
                drawRect(tint, frame.topLeft, frame.size)
                val picture = pictures[cell.page]
                if (picture != null) {
                    turned(frame, cell.turn) { box ->
                        val aspect = picture.width.toFloat() / maxOf(picture.height, 1)
                        val drawn = when (val fit = cell.fit) {
                            is PrintSheet.Fit.Actual -> Size(fit.width.toFloat() * scale, fit.height.toFloat() * scale)
                            else -> {
                                val wide = aspect > box.width / maxOf(box.height, 1f)
                                val byWidth = (fit == PrintSheet.Fit.Fit) == wide
                                if (byWidth) Size(box.width, box.width / aspect) else Size(box.height * aspect, box.height)
                            }
                        }
                        drawImage(
                            picture,
                            dstOffset = IntOffset((-drawn.width / 2).roundToInt(), (-drawn.height / 2).roundToInt()),
                            dstSize = IntSize(drawn.width.roundToInt(), drawn.height.roundToInt()),
                        )
                    }
                    tag(cell.page, cell.turn, frame, primary, measurer)
                } else {
                    turned(frame, cell.turn) { box ->
                        val size = minOf(120f, maxOf(6f, minOf(box.width, box.height) * 0.46f))
                        val text = measurer.measure("${cell.page}", TextStyle(fontSize = size.toSp(), fontWeight = FontWeight.Bold, color = foreground.copy(alpha = 0.82f)))
                        drawText(text, topLeft = Offset(-text.size.width / 2f, -text.size.height / 2f))
                    }
                }
            }
            drawRect(foreground.copy(alpha = 0.16f), frame.topLeft, frame.size, style = Stroke(0.5f))
        }
        for ((line, kind) in sheet.creases) {
            val dashes = when (kind) {
                PrintSheet.Crease.Fold -> floatArrayOf(4f, 3f)
                PrintSheet.Crease.Valley -> floatArrayOf(6f, 4f)
                PrintSheet.Crease.Mountain -> floatArrayOf(8f, 3f, 1.5f, 3f)
            }.map { it.dp.toPx() }.toFloatArray()
            drawLine(primary, point(line.x1, line.y1), point(line.x2, line.y2), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(dashes))
        }
        for (cut in sheet.cuts) drawLine(paper.destructive, point(cut.x1, cut.y1), point(cut.x2, cut.y2), 1.5.dp.toPx())
        sheet.cuts.firstOrNull()?.let { scissors(point(it.x1, it.y1), point(it.x2, it.y2), paper.destructive) }
        for (mark in sheet.marks) drawLine(foreground.copy(alpha = 0.7f), point(mark.x1, mark.y1), point(mark.x2, mark.y2), 0.75.dp.toPx())
        val ring = 2.5.dp.toPx()
        for (cell in sheet.cells) {
            if (cell.page <= 0 || cell.page !in lit) continue
            val frame = map(cell.frame)
            drawRect(primary, Offset(frame.left + ring / 2, frame.top + ring / 2), Size(frame.width - ring, frame.height - ring), style = Stroke(ring))
        }
    }
    drawRect(paper.border, Offset(rect.left + 0.5f, rect.top + 0.5f), Size(rect.width - 1, rect.height - 1), style = Stroke(1.dp.toPx()))
}

// points² at scale 1
private const val STRIP_BUDGET = 12_000_000.0
private const val STRIP_HEIGHT = 140.0

/**
 * dt-ios PrintSheet.image for a strip of [count] sheet sides: 140 points high, the scale lowered to keep the run within
 * a fixed pixel budget. [measurer] must not be shared with composition, since this runs off the main thread.
 * ponytail: fixed pixel budget; a long run gets softer pictures, render only visible cells if that shows
 */
fun renderPrintSheet(
    sheet: PrintSheet,
    count: Int,
    displayScale: Float,
    paper: DelphiColours,
    measurer: TextMeasurer,
    pictures: Map<Int, ImageBitmap>,
): ImageBitmap {
    val area = STRIP_HEIGHT * STRIP_HEIGHT * sheet.width / maxOf(sheet.height, 1.0)
    val scale = minOf(displayScale.toDouble(), maxOf(0.75, kotlin.math.sqrt(STRIP_BUDGET / maxOf(1.0, area * count))))
    val height = maxOf(1, (STRIP_HEIGHT * scale).roundToInt())
    val width = maxOf(1, (STRIP_HEIGHT * sheet.width / maxOf(sheet.height, 1.0) * scale).roundToInt())
    val image = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(scale.toFloat()), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(width.toFloat(), height.toFloat())) {
        drawPrintSheet(sheet, Rect(0f, 0f, width.toFloat(), height.toFloat()), paper, measurer, pictures)
    }
    return image
}

private fun DrawScope.turned(frame: Rect, turn: Int, body: DrawScope.(Size) -> Unit) {
    clipRect(frame.left, frame.top, frame.right, frame.bottom) {
        translate(frame.center.x, frame.center.y) {
            rotate(turn.toFloat(), pivot = Offset.Zero) {
                val sideways = (turn / 90) % 2 != 0
                body(if (sideways) Size(frame.height, frame.width) else frame.size)
            }
        }
    }
}

private fun DrawScope.tag(page: Int, turn: Int, frame: Rect, fill: Color, measurer: TextMeasurer) {
    turned(frame, turn) { box ->
        val size = minOf(15.dp.toPx(), maxOf(6.dp.toPx(), minOf(box.width, box.height) * 0.12f))
        val text = measurer.measure("$page", TextStyle(fontSize = size.toSp(), fontWeight = FontWeight.Bold, color = Color.White))
        val pad = size * 0.3f
        val chip = Rect(Offset(-box.width / 2, -box.height / 2), Size(text.size.width + pad * 2, text.size.height + pad * 0.6f))
        drawRect(fill, chip.topLeft, chip.size)
        drawText(text, topLeft = Offset(chip.left + pad, chip.top + pad * 0.3f))
    }
}

private fun DrawScope.hatch(frame: Rect, colour: Color) {
    clipRect(frame.left, frame.top, frame.right, frame.bottom) {
        val pitch = 6.dp.toPx()
        var x = frame.left - frame.height
        while (x < frame.right) {
            drawLine(colour, Offset(x, frame.bottom), Offset(x + frame.height, frame.top), 1.dp.toPx())
            x += pitch
        }
    }
}

private fun DrawScope.scissors(start: Offset, towards: Offset, colour: Color) {
    val angle = Math.toDegrees(kotlin.math.atan2((towards.y - start.y).toDouble(), (towards.x - start.x).toDouble())).toFloat()
    val r = 8.dp.toPx()
    drawCircle(Color.White, r, start)
    translate(start.x, start.y) {
        rotate(angle, pivot = Offset.Zero) {
            val blade = Path().apply {
                moveTo(-r * 0.6f, -r * 0.45f)
                lineTo(r * 0.6f, r * 0.2f)
                moveTo(-r * 0.6f, r * 0.45f)
                lineTo(r * 0.6f, -r * 0.2f)
            }
            drawPath(blade, colour, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(colour, r * 0.22f, Offset(-r * 0.6f, -r * 0.45f), style = Stroke(1.dp.toPx()))
            drawCircle(colour, r * 0.22f, Offset(-r * 0.6f, r * 0.45f), style = Stroke(1.dp.toPx()))
        }
    }
}

private val sheetHeader = 62.dp
private val sheetFooter = 16.dp
private val hintRow = 26.dp
private val maxPaper = 560.dp

/**
 * dt-ios PrintSheetView: legend, detail and a figure over the sheet, which turns over to [back] when [showsBack].
 * [horizontal] turns it top over bottom. [sheetKey] changing shows the new side at once instead of turning.
 * ponytail: no cross-fade when the sheet's cells change; the faces swap at once
 */
@Composable
fun PrintSheetView(
    front: PrintSheet?,
    back: PrintSheet?,
    showsBack: Boolean,
    horizontal: Boolean,
    legend: String,
    detail: String?,
    figure: String,
    figureUnit: String,
    hint: String?,
    pictures: Map<Int, ImageBitmap>,
    lit: Set<Int> = emptySet(),
    sheetKey: Any? = null,
    onTap: () -> Unit,
) {
    val c = LocalDelphiColours.current
    val paper = selectedTheme().colours(false)
    val measurer = rememberTextMeasurer()
    val tap by rememberUpdatedState(onTap)
    val turn = remember { Animatable(if (showsBack) 180f else 0f) }
    val shownKey = remember { arrayOf(sheetKey) }
    LaunchedEffect(showsBack, sheetKey) {
        val target = if (showsBack) 180f else 0f
        if (shownKey[0] != sheetKey) {
            shownKey[0] = sheetKey
            turn.snapTo(target)
        } else {
            turn.animateTo(target, tween(500))
        }
    }
    val aspect = (front ?: back)?.let { (it.width / maxOf(it.height, 1.0)).toFloat() } ?: 1.414f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val paperHeight = minOf(maxPaper, (maxWidth - CanonMetrics.panelPadding * 2) / maxOf(aspect, 0.1f))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = LocalCanonCanvas.current.top + 10.dp, start = CanonMetrics.panelPadding, end = CanonMetrics.panelPadding)
                .pointerInput(Unit) { detectTapGestures { tap() } }
                .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(legend, detail).joinToString(", ") },
        ) {
            Row(Modifier.fillMaxWidth().height(sheetHeader - 10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(legend, style = CanonText.footnoteSemibold.copy(fontWeight = FontWeight.Bold), color = c.foreground, maxLines = 1)
                    if (!detail.isNullOrEmpty()) Text(detail, style = CanonText.caption, color = c.mutedForeground, maxLines = 1)
                }
                CanonFigure(figure, Modifier.padding(start = 12.dp), unit = figureUnit, size = 30.sp, halo = c.muted)
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(paperHeight)
                    .graphicsLayer {
                        val angle = turn.value
                        val shown = if (angle < 90) angle else angle - 180
                        if (horizontal) rotationX = shown else rotationY = shown
                        cameraDistance = 16 * density
                    },
            ) {
                val sheet = (if (turn.value < 90) front else back) ?: return@Canvas
                val area = SheetRect(2.0, 3.0, size.width - 4.0, size.height - 6.0)
                val fitted = sheet.paperRect(area)
                val rect = Rect(Offset(fitted.x.toFloat(), fitted.y.toFloat()), Size(fitted.width.toFloat(), fitted.height.toFloat()))
                drawRect(Color.Black.copy(alpha = 0.08f), rect.topLeft + Offset(0f, 1.dp.toPx()), rect.size)
                drawPrintSheet(sheet, rect, paper, measurer, pictures, lit)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = sheetFooter).height(hintRow - 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hint != null) {
                    Icon(Icons.Outlined.TouchApp, null, tint = c.mutedForeground, modifier = Modifier.size(14.dp))
                    Text(" $hint", style = CanonText.captionSemibold, color = c.mutedForeground, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private val blockGap = 14.dp
private val blockLabel = 18.dp

/** dt-ios SignatureBlockView: each signature as nested folded sheets, its page range below; a tap picks its first sheet. */
@Composable
fun SignatureBlock(signatures: List<ImpositionEngine.Signature>, lit: Int, glued: Boolean, onSelect: (Int) -> Unit) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val select by rememberUpdatedState(onSelect)
    var offset by remember { mutableFloatStateOf(0f) }
    val top = LocalCanonCanvas.current.top + 14.dp
    BoxWithConstraints(Modifier.fillMaxSize().semantics { contentDescription = signatures.joinToString(", ") { "${it.pages.first}–${it.pages.last}" } }) {
        val view = constraints.maxWidth.toFloat()
        val frames = with(density) {
            var x = CanonMetrics.panelPadding.toPx()
            signatures.map { signature ->
                val width = minOf(120f, maxOf(44f, 20f + signature.sheets * 7f)).dp.toPx()
                (x to width).also { x += width + blockGap.toPx() }
            }
        }
        val total = (frames.lastOrNull()?.let { it.first + it.second } ?: 0f) + with(density) { CanonMetrics.panelPadding.toPx() }
        val lead = maxOf(0f, (view - total) / 2)
        val limit = maxOf(0f, total - view)
        offset = offset.coerceIn(0f, limit)
        val scroll = rememberScrollableState { delta ->
            val next = (offset - delta).coerceIn(0f, limit)
            (offset - next).also { offset = next }
        }
        var litSignature: Int? = null
        var first = 0
        for ((index, signature) in signatures.withIndex()) {
            if (lit < first + signature.sheets) {
                litSignature = index
                break
            }
            first += signature.sheets
        }
        LaunchedEffect(litSignature, frames) {
            val (x, width) = frames.getOrNull(litSignature ?: return@LaunchedEffect) ?: return@LaunchedEffect
            val margin = with(density) { 24.dp.toPx() }
            val target = when {
                x - margin < offset -> x - margin
                x + width + margin > offset + view -> x + width + margin - view
                else -> return@LaunchedEffect
            }
            scroll.animateScrollBy(offset - target.coerceIn(0f, limit))
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .scrollable(scroll, Orientation.Horizontal)
                .pointerInput(frames, lead) {
                    detectTapGestures { point ->
                        val x = point.x + offset - lead
                        val index = frames.indices.minByOrNull { abs(frames[it].first + frames[it].second / 2 - x) } ?: return@detectTapGestures
                        select(signatures.take(index).sumOf { it.sheets })
                    }
                },
        ) {
            val spine = size.height - 14.dp.toPx() - blockLabel.toPx() - 8.dp.toPx()
            val topY = top.toPx()
            if (spine - topY <= 12.dp.toPx() || frames.isEmpty()) return@Canvas
            val ink = c.foreground.copy(alpha = 0.45f)
            val caption = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            translate(lead - offset, 0f) {
                var sheet = 0
                for ((index, signature) in signatures.withIndex()) {
                    val (x, width) = frames[index]
                    val count = maxOf(signature.sheets, 1)
                    val step = minOf(4.dp.toPx(), (width / 2 - 6.dp.toPx()) / count, (spine - topY - 8.dp.toPx()) / count)
                    for (inner in 0 until signature.sheets) {
                        val inset = inner * step
                        val left = x + inset
                        val right = x + width - inset
                        val bottom = spine - inset
                        val radius = minOf(8.dp.toPx(), (right - left) / 2)
                        val path = Path().apply {
                            moveTo(left, topY)
                            lineTo(left, bottom - radius)
                            quadraticTo(left, bottom, left + radius, bottom)
                            lineTo(right - radius, bottom)
                            quadraticTo(right, bottom, right, bottom - radius)
                            lineTo(right, topY)
                        }
                        val isLit = sheet + inner == lit
                        drawPath(path, if (isLit) c.primary else ink, style = Stroke(if (isLit) 2.5.dp.toPx() else 1.dp.toPx()))
                    }
                    sheet += signature.sheets
                    val text = measurer.measure("${signature.pages.first}–${signature.pages.last}", caption.copy(color = if (index == litSignature) c.primary else c.mutedForeground))
                    drawText(text, topLeft = Offset(x + width / 2 - text.size.width / 2, spine + 8.dp.toPx() + if (glued) 3.dp.toPx() else 0f))
                }
                if (glued) {
                    val (firstX, _) = frames.first()
                    val (lastX, lastWidth) = frames.last()
                    drawRect(c.foreground, Offset(firstX - 4.dp.toPx(), spine + 1.dp.toPx()), Size(lastX + lastWidth - firstX + 8.dp.toPx(), 3.dp.toPx()))
                }
            }
        }
    }
}
