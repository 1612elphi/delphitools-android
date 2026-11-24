package com.delphi.delphitools.tools.devtools

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Seconds from the first module's turn to the last's, then one module's own turn. */
private const val SPREAD = 0.34
private const val FLIP = 0.2

/** Past this many modules a change swaps at once instead of sweeping. */
private const val SWEEP_LIMIT = 12_000
private val captionRow = 36.dp
private val cropGap = 5.dp
private val cropLength = 10.dp

fun SheetPath.toPath(into: Path = Path()): Path {
    for (op in ops) when (op) {
        is SheetPath.Op.Move -> into.moveTo(op.x, op.y)
        is SheetPath.Op.Line -> into.lineTo(op.x, op.y)
        is SheetPath.Op.Cubic -> into.cubicTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
        SheetPath.Op.Close -> into.close()
    }
    return into
}

private fun Rgb.color() = toCanonColor()

/** Paints the modules and marks with the code's top-left module at the origin and [pitch] pixels per module. */
fun DrawScope.paintSheet(sheet: CodeSheet, originX: Float, originY: Float, pitch: Float, measurer: TextMeasurer) {
    val path = Path()
    for (row in 0 until sheet.rows) for (column in 0 until sheet.columns) {
        if (sheet.modules[row][column]) sheet.modulePath(sheet.rect(row, column, originX, originY, pitch), row, column).toPath(path)
    }
    drawPath(path, sheet.ink.color())
    paintMarks(sheet, originX, originY, pitch, 1f, measurer)
}

fun DrawScope.paintMarks(sheet: CodeSheet, originX: Float, originY: Float, pitch: Float, alpha: Float, measurer: TextMeasurer) {
    if (alpha <= 0f) return
    for (mark in sheet.marks) {
        val colour = mark.colour.color()
        when (mark) {
            is CodeSheet.Mark.Shape -> {
                val path = mark.path.transformed(pitch, pitch, originX, originY).toPath()
                path.fillType = if (mark.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
                drawPath(path, colour, alpha = alpha)
            }
            is CodeSheet.Mark.Image -> {
                val f = mark.frame
                drawImage(
                    mark.image,
                    dstOffset = androidx.compose.ui.unit.IntOffset((originX + f.x * pitch).toInt(), (originY + f.y * pitch).toInt()),
                    dstSize = IntSize(max(1, (f.width * pitch).toInt()), max(1, (f.height * pitch).toInt())),
                    alpha = alpha,
                )
            }
            is CodeSheet.Mark.Text -> {
                val style = TextStyle(
                    color = colour.copy(alpha = alpha),
                    fontSize = (mark.size * pitch).toSp(),
                    fontWeight = FontWeight.Medium,
                    fontFamily = if (mark.mono) FontFamily.Monospace else FontFamily.Default,
                )
                val measured = measurer.measure(mark.text, style, softWrap = false, maxLines = 1)
                val f = mark.frame
                drawText(measured, topLeft = Offset(originX + f.midX * pitch - measured.size.width / 2f, originY + f.midY * pitch - measured.size.height / 2f))
            }
        }
    }
}

/** The sheet as a PNG at [pixels] per module; transparent where it has no paper. */
fun CodeSheet.png(context: Context, pixels: Float): ByteArray? {
    val (w, h) = pixelSize(pixels)
    if (w <= 0 || h <= 0) return null
    val image = ImageBitmap(w, h)
    val density = Density(1f)
    val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(w.toFloat(), h.toFloat())) {
        paper?.let { drawRect(it.color()) }
        val (ox, oy) = codeOrigin(pixels)
        paintSheet(this@png, ox, oy, pixels, measurer)
    }
    return ByteArrayOutputStream().also { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}

/** What a sweep reads for every module on every frame, built once per sheet. */
private class Swept(val sheet: CodeSheet) {
    val orders = DoubleArray(sheet.rows * sheet.columns).also { out ->
        for (row in 0 until sheet.rows) for (column in 0 until sheet.columns) out[row * sheet.columns + column] = sheet.order(row, column)
    }

    fun order(row: Int, column: Int): Double {
        if (orders.isEmpty()) return 0.0
        return orders[row.coerceIn(0, sheet.rows - 1) * sheet.columns + column.coerceIn(0, sheet.columns - 1)]
    }
}

private fun easeOut(t: Double) = (1 - (1 - t).pow(3)).toFloat()
private fun easeInOut(t: Double) = (if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2).toFloat()
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun lerp(a: SheetRect, b: SheetRect, t: Float) = SheetRect(lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.width, b.width, t), lerp(a.height, b.height, t))

/**
 * dt-ios CodeSheetPicture: the code as it will be written, fitted to the pane on whole pixels per module, with crop
 * marks at the sheet's corners, its name and its pixel size under it. A change of modules sweeps through the code in
 * its own order: new dark modules grow in, old ones shrink away, and a code that changes size slides to its new layout.
 */
@Composable
fun CodeSheetPicture(sheet: CodeSheet?, caption: String, pixels: Pair<Int, Int>?, emptySymbol: ImageVector, description: String?) {
    val c = LocalDelphiColours.current
    val measurer = rememberTextMeasurer()
    val symbol = rememberVectorPainter(emptySymbol)
    val top = LocalCanonCanvas.current.top
    var previous by remember { mutableStateOf<Swept?>(null) }
    var current by remember { mutableStateOf(sheet?.let(::Swept)) }
    val clock = remember { Animatable(1f) }

    LaunchedEffect(sheet) {
        val before = current?.sheet
        val moved = before == null || sheet == null || before.rowHeight != sheet.rowHeight || before.rows != sheet.rows ||
            before.modules.indices.any { !before.modules[it].contentEquals(sheet.modules[it]) }
        val animated = ValueAnimator.areAnimatorsEnabled() && before != null && sheet != null && moved &&
            max(before.rows, sheet.rows) * max(before.columns, sheet.columns) <= SWEEP_LIMIT
        if (animated) {
            // resume in-flight sweep
            previous = current
            current = Swept(sheet!!)
            clock.snapTo(0f)
            clock.animateTo(1f, tween(((SPREAD + FLIP) * 1000).toInt(), easing = LinearEasing))
            previous = null
        } else {
            clock.snapTo(1f)
            previous = null
            current = sheet?.let(::Swept)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().semantics { if (description != null) contentDescription = description }) {
        val elapsed = clock.value * (SPREAD + FLIP)
        Canvas(Modifier.fillMaxSize()) {
            val pad = CanonMetrics.panelPadding.toPx()
            val inset = (cropGap + cropLength + 6.dp).toPx()
            val area = SheetRect(pad + inset, top.toPx() + pad + inset, size.width - (pad + inset) * 2, size.height - top.toPx() - pad - inset * 2 - captionRow.toPx())
            val shown = current?.sheet
            val now = shown?.let { placeSheet(it, area) }
            if (shown == null || now == null) return@Canvas drawEmpty(area, c.mutedForeground, symbol)
            val layout = easeInOut(elapsed / (SPREAD + FLIP))
            val old = previous
            val before = old?.let { o -> placeSheet(o.sheet, area)?.let { o to it } }
            val frame = before?.let { lerp(it.second.sheet, now.sheet, layout) } ?: now.sheet
            drawPaper(shown, frame, c.border)
            drawCropMarks(frame, c.mutedForeground)
            if (before == null) {
                paintSheet(shown, now.originX, now.originY, now.pitch, measurer)
                drawQuietZone(shown, now, c.destructive)
                return@Canvas
            }
            val (was, placed) = before
            val sheetBefore = was.sheet
            val swept = current!!
            fun local(order: Double) = easeOut(((elapsed - order * SPREAD) / FLIP).coerceIn(0.0, 1.0))
            val rises = shown.rows == 1 && sheetBefore.rows == 1
            val path = Path()
            fun fill(owner: CodeSheet, rect: SheetRect, row: Int, column: Int, share: Float) {
                if (share <= 0.001f) return
                val r = if (rises) SheetRect(rect.x, rect.bottom - rect.height * share, rect.width, rect.height * share)
                else rect.inset(rect.width * (1 - share) / 2, rect.height * (1 - share) / 2)
                owner.modulePath(r, row, column).toPath(path)
            }
            val sameSize = sheetBefore.rows == shown.rows && sheetBefore.columns == shown.columns
            if (shown.anchor == CodeSheet.Anchor.None && !sameSize) {
                for (row in 0 until sheetBefore.rows) for (column in 0 until sheetBefore.columns) {
                    if (sheetBefore.modules[row][column]) {
                        fill(sheetBefore, sheetBefore.rect(row, column, placed.originX, placed.originY, placed.pitch), row, column, 1 - local(was.order(row, column)))
                    }
                }
                for (row in 0 until shown.rows) for (column in 0 until shown.columns) {
                    if (shown.modules[row][column]) fill(shown, shown.rect(row, column, now.originX, now.originY, now.pitch), row, column, local(swept.order(row, column)))
                }
            } else {
                val shiftRows = if (shown.anchor == CodeSheet.Anchor.Centre) (shown.rows - sheetBefore.rows) / 2 else 0
                val shiftColumns = if (shown.anchor == CodeSheet.Anchor.Centre) (shown.columns - sheetBefore.columns) / 2 else 0
                for (row in min(0, shiftRows) until max(shown.rows, sheetBefore.rows + shiftRows)) {
                    for (column in min(0, shiftColumns) until max(shown.columns, sheetBefore.columns + shiftColumns)) {
                        val wasDark = sheetBefore.isDark(row - shiftRows, column - shiftColumns)
                        val isDark = shown.isDark(row, column)
                        if (!wasDark && !isDark) continue
                        val r = lerp(
                            sheetBefore.rect(row - shiftRows, column - shiftColumns, placed.originX, placed.originY, placed.pitch),
                            shown.rect(row, column, now.originX, now.originY, now.pitch),
                            layout,
                        )
                        when {
                            wasDark && isDark -> fill(shown, r, row, column, 1f)
                            isDark -> fill(shown, r, row, column, local(swept.order(row, column)))
                            else -> fill(sheetBefore, r, row - shiftRows, column - shiftColumns, 1 - local(swept.order(row, column)))
                        }
                    }
                }
            }
            drawPath(path, shown.ink.color())
            val ox = lerp(placed.originX, now.originX, layout)
            val oy = lerp(placed.originY, now.originY, layout)
            val pitch = lerp(placed.pitch, now.pitch, layout)
            if (sheetBefore.marksKey == shown.marksKey) {
                paintMarks(shown, ox, oy, pitch, 1f, measurer)
            } else {
                paintMarks(sheetBefore, ox, oy, pitch, 1 - layout, measurer)
                paintMarks(shown, ox, oy, pitch, layout, measurer)
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(captionRow).padding(horizontal = CanonMetrics.panelPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(caption, Modifier.weight(1f), style = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp), color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            if (pixels != null) {
                Spacer(Modifier.width(12.dp))
                CanonFigure("${pixels.first} × ${pixels.second}", unit = "px", size = 17.sp, colour = c.mutedForeground, halo = c.card)
            }
        }
    }
}

private fun DrawScope.drawPaper(sheet: CodeSheet, frame: SheetRect, border: Color) {
    val hairline = CanonMetrics.hairline.toPx()
    drawRect(border, Offset(frame.x - hairline, frame.y - hairline), Size(frame.width + hairline * 2, frame.height + hairline * 2))
    val paper = sheet.paper
    if (paper != null) {
        drawRect(paper.color(), Offset(frame.x, frame.y), Size(frame.width, frame.height))
        return
    }
    clipRect(frame.x, frame.y, frame.right, frame.bottom) {
        drawRect(Color.White, Offset(frame.x, frame.y), Size(frame.width, frame.height))
        val check = 8.dp.toPx()
        val grey = Color(0xFFE6E6E6)
        var y = frame.y
        var odd = false
        while (y < frame.bottom) {
            var x = frame.x + if (odd) check else 0f
            while (x < frame.right) {
                drawRect(grey, Offset(x, y), Size(check, check))
                x += check * 2
            }
            y += check
            odd = !odd
        }
    }
}

private fun DrawScope.drawCropMarks(frame: SheetRect, ink: Color) {
    val gap = cropGap.toPx()
    val length = cropLength.toPx()
    val width = CanonMetrics.hairline.toPx()
    for ((x, y, dx, dy) in listOf(
        listOf(frame.x, frame.y, -1f, -1f), listOf(frame.right, frame.y, 1f, -1f),
        listOf(frame.x, frame.bottom, -1f, 1f), listOf(frame.right, frame.bottom, 1f, 1f),
    )) {
        drawLine(ink, Offset(x, y + dy * gap), Offset(x, y + dy * (gap + length)), width)
        drawLine(ink, Offset(x + dx * gap, y), Offset(x + dx * (gap + length), y), width)
    }
}

/** A margin under the code's quiet zone: the zone it needs, dashed in red. */
private fun DrawScope.drawQuietZone(sheet: CodeSheet, placement: SheetPlacement, colour: Color) {
    val m = sheet.margin
    if (minOf(m.left, m.right, m.top, m.bottom) >= sheet.quietZone - 0.01f) return
    val zone = sheet.quietZone * placement.pitch
    drawRect(
        colour,
        Offset(placement.originX - zone, placement.originY - zone),
        Size(sheet.codeWidth * placement.pitch + zone * 2, sheet.codeHeight * placement.pitch + zone * 2),
        style = Stroke(CanonMetrics.hairline.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

private fun DrawScope.drawEmpty(area: SheetRect, ink: Color, symbol: androidx.compose.ui.graphics.painter.Painter) {
    val side = min(area.width, area.height) * 0.62f
    if (side <= 0) return
    val frame = SheetRect(area.midX - side / 2, area.midY - side / 2, side, side)
    drawRect(
        ink.copy(alpha = 0.5f),
        Offset(frame.x, frame.y),
        Size(side, side),
        style = Stroke(CanonMetrics.hairline.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
    )
    drawCropMarks(frame, ink)
    val glyph = side * 0.36f
    translate(frame.midX - glyph / 2, frame.midY - glyph / 2) {
        with(symbol) { draw(Size(glyph, glyph), colorFilter = ColorFilter.tint(ink.copy(alpha = 0.35f))) }
    }
}
