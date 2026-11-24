package com.delphi.delphitools.tools.devtools

import androidx.compose.ui.graphics.ImageBitmap
import com.delphi.delphitools.colour.Rgb
import java.util.Base64
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A rectangle in sheet units; [x] and [y] are its top-left corner. */
data class SheetRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right get() = x + width
    val bottom get() = y + height
    val midX get() = x + width / 2
    val midY get() = y + height / 2

    fun inset(dx: Float, dy: Float) = SheetRect(x + dx, y + dy, width - dx * 2, height - dy * 2)
}

/**
 * A path as a list of commands, so the same outline draws on screen, into a PNG and into SVG path data.
 * Corners are quarter circles drawn as cubics.
 */
class SheetPath {
    sealed interface Op {
        data class Move(val x: Float, val y: Float) : Op
        data class Line(val x: Float, val y: Float) : Op
        data class Cubic(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : Op
        data object Close : Op
    }

    val ops = mutableListOf<Op>()

    fun moveTo(x: Float, y: Float) = apply { ops += Op.Move(x, y) }
    fun lineTo(x: Float, y: Float) = apply { ops += Op.Line(x, y) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = apply { ops += Op.Cubic(x1, y1, x2, y2, x, y) }
    fun close() = apply { ops += Op.Close }
    fun add(other: SheetPath) = apply { ops += other.ops }

    fun rect(r: SheetRect) = moveTo(r.x, r.y).lineTo(r.right, r.y).lineTo(r.right, r.bottom).lineTo(r.x, r.bottom).close()

    /** Corner radii clockwise from the top left, each capped at half the shorter side. */
    fun roundRect(r: SheetRect, topLeft: Float, topRight: Float = topLeft, bottomRight: Float = topLeft, bottomLeft: Float = topLeft): SheetPath {
        val cap = min(r.width, r.height) / 2
        val tl = min(topLeft, cap)
        val tr = min(topRight, cap)
        val br = min(bottomRight, cap)
        val bl = min(bottomLeft, cap)
        val k = 1 - KAPPA
        moveTo(r.x + tl, r.y)
        lineTo(r.right - tr, r.y)
        if (tr > 0) cubicTo(r.right - tr * k, r.y, r.right, r.y + tr * k, r.right, r.y + tr)
        lineTo(r.right, r.bottom - br)
        if (br > 0) cubicTo(r.right, r.bottom - br * k, r.right - br * k, r.bottom, r.right - br, r.bottom)
        lineTo(r.x + bl, r.bottom)
        if (bl > 0) cubicTo(r.x + bl * k, r.bottom, r.x, r.bottom - bl * k, r.x, r.bottom - bl)
        lineTo(r.x, r.y + tl)
        if (tl > 0) cubicTo(r.x, r.y + tl * k, r.x + tl * k, r.y, r.x + tl, r.y)
        return close()
    }

    fun ellipse(r: SheetRect): SheetPath {
        val rx = r.width / 2
        val ry = r.height / 2
        val cx = r.midX
        val cy = r.midY
        moveTo(cx + rx, cy)
        cubicTo(cx + rx, cy + ry * KAPPA, cx + rx * KAPPA, cy + ry, cx, cy + ry)
        cubicTo(cx - rx * KAPPA, cy + ry, cx - rx, cy + ry * KAPPA, cx - rx, cy)
        cubicTo(cx - rx, cy - ry * KAPPA, cx - rx * KAPPA, cy - ry, cx, cy - ry)
        cubicTo(cx + rx * KAPPA, cy - ry, cx + rx, cy - ry * KAPPA, cx + rx, cy)
        return close()
    }

    fun diamond(r: SheetRect) = moveTo(r.midX, r.y).lineTo(r.right, r.midY).lineTo(r.midX, r.bottom).lineTo(r.x, r.midY).close()

    /** Every point scaled by [sx], [sy], then moved by [tx], [ty]. */
    fun transformed(sx: Float, sy: Float, tx: Float, ty: Float): SheetPath {
        val out = SheetPath()
        for (op in ops) out.ops += when (op) {
            is Op.Move -> Op.Move(op.x * sx + tx, op.y * sy + ty)
            is Op.Line -> Op.Line(op.x * sx + tx, op.y * sy + ty)
            is Op.Cubic -> Op.Cubic(op.x1 * sx + tx, op.y1 * sy + ty, op.x2 * sx + tx, op.y2 * sy + ty, op.x * sx + tx, op.y * sy + ty)
            Op.Close -> Op.Close
        }
        return out
    }

    fun svgData(): String = buildString {
        for (op in ops) when (op) {
            is Op.Move -> append("M").append(CodeSheet.number(op.x)).append(' ').append(CodeSheet.number(op.y))
            is Op.Line -> append("L").append(CodeSheet.number(op.x)).append(' ').append(CodeSheet.number(op.y))
            is Op.Cubic -> append("C").append(listOf(op.x1, op.y1, op.x2, op.y2, op.x, op.y).chunked(2).joinToString(" ") { "${CodeSheet.number(it[0])} ${CodeSheet.number(it[1])}" })
            Op.Close -> append("Z")
        }
    }

    companion object {
        /** Cubic control distance of a quarter circle, per unit radius. */
        const val KAPPA = 0.5522848f
    }
}

/**
 * dt-ios CodeSheet: a machine-readable code as it will be written: its modules on a sheet, a quiet zone round them,
 * marks drawn over them. Lengths are in modules from the code's top-left module; the PNG and SVG write it at a pixel
 * size per module, CodeSheetPicture draws it on screen. qr-genny and code-genny.
 */
class CodeSheet(
    val modules: List<BooleanArray>,
    /** Height of one row of modules. */
    val rowHeight: Float = 1f,
    val margin: Margin,
    val footer: Float = 0f,
    val ink: Rgb,
    /** Null: transparent. */
    val paper: Rgb?,
    /** Least margin a scanner needs; the picture marks a smaller one. */
    val quietZone: Float = 0f,
    val sweep: Sweep,
    val anchor: Anchor,
    /** Every module is a rectangle: the SVG draws runs with crisp edges. */
    val isRectilinear: Boolean = true,
    /** The dark module at row and column filling a rect; null fills the rect. It must scale with the rect. */
    val shape: ((SheetRect, Int, Int) -> SheetPath)? = null,
    /** Marks in module units from the code's top-left corner. */
    val marks: List<Mark> = emptyList(),
    /** Equal keys mean equal marks, so a change of modules alone does not fade them. */
    val marksKey: String = "",
) {
    data class Margin(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        constructor(all: Float) : this(all, all, all, all)
    }

    /** The order a change runs through the modules. */
    enum class Sweep {
        /** A QR code's bit placement: two-module columns from the bottom right, up and down in turn. */
        Zigzag,

        /** Left to right, as a scanner reads a linear code. */
        Columns,

        /** From the middle outwards, as an Aztec code's layers grow. */
        Rings,
    }

    /** Which module of a code of another size a module matches, so a resized code slides instead of redrawing. */
    enum class Anchor { None, Leading, Centre }

    sealed interface Mark {
        val colour: Rgb

        data class Shape(val path: SheetPath, val evenOdd: Boolean, override val colour: Rgb) : Mark

        /** [png] is the image as written into the SVG; [image] is whatever the screen and PNG draw. */
        class Image(val image: ImageBitmap, val png: ByteArray, val frame: SheetRect, override val colour: Rgb) : Mark

        /** Centred in the rect, one line; [size] is the font size. */
        data class Text(val text: String, val frame: SheetRect, val size: Float, val mono: Boolean, override val colour: Rgb) : Mark
    }

    val rows get() = modules.size
    val columns get() = modules.firstOrNull()?.size ?: 0
    val codeWidth get() = columns.toFloat()
    val codeHeight get() = rows * rowHeight
    val width get() = codeWidth + margin.left + margin.right
    val height get() = codeHeight + margin.top + margin.bottom + footer

    fun isDark(row: Int, column: Int) = row in modules.indices && column in 0 until modules[row].size && modules[row][column]

    fun modulePath(rect: SheetRect, row: Int, column: Int): SheetPath = shape?.invoke(rect, row, column) ?: SheetPath().rect(rect)

    fun rect(row: Int, column: Int, originX: Float, originY: Float, pitch: Float) =
        SheetRect(originX + column * pitch, originY + row * rowHeight * pitch, pitch, rowHeight * pitch)

    /** Sweep position of a module, 0 first to 1 last. */
    fun order(row: Int, column: Int): Double {
        val r = row.coerceIn(0, max(rows - 1, 0))
        val c = column.coerceIn(0, max(columns - 1, 0))
        return when (sweep) {
            Sweep.Columns -> if (columns > 1) c.toDouble() / (columns - 1) else 0.0
            Sweep.Rings -> {
                val midRow = (rows - 1) / 2.0
                val midColumn = (columns - 1) / 2.0
                val far = maxOf(midRow, midColumn, 1.0)
                max(abs(r - midRow), abs(c - midColumn)) / far
            }
            Sweep.Zigzag -> {
                val table = zigzag(rows)
                if (table.isEmpty() || columns != rows) 0.0 else table[r * rows + c].toDouble() / (rows * rows - 1)
            }
        }
    }

    /** Pixel size of the written image at [pixels] per module. */
    fun pixelSize(pixels: Float) = (width * pixels).roundToInt() to (height * pixels).roundToInt()

    /** Where the code's top-left module lands in an image of [pixels] per module, on whole pixels. */
    fun codeOrigin(pixels: Float) = (margin.left * pixels).roundToInt().toFloat() to (margin.top * pixels).roundToInt().toFloat()

    fun svg(pixels: Float): String {
        val (w, h) = pixelSize(pixels)
        val (ox, oy) = codeOrigin(pixels)
        val out = StringBuilder()
        out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$w\" height=\"$h\" viewBox=\"0 0 $w $h\">\n")
        paper?.let { out.append("<rect width=\"100%\" height=\"100%\" fill=\"${hex(it)}\"/>\n") }
        val data = StringBuilder()
        if (isRectilinear && shape == null) {
            // merged runs avoid seams
            for (row in 0 until rows) {
                var column = 0
                while (column < columns) {
                    if (!modules[row][column]) {
                        column++
                        continue
                    }
                    var end = column
                    while (end + 1 < columns && modules[row][end + 1]) end++
                    val r = rect(row, column, ox, oy, pixels)
                    val run = number((end - column + 1) * pixels)
                    data.append("M${number(r.x)} ${number(r.y)}h${run}v${number(r.height)}h-${run}z")
                    column = end + 1
                }
            }
        } else {
            for (row in 0 until rows) for (column in 0 until columns) {
                if (modules[row][column]) data.append(modulePath(rect(row, column, ox, oy, pixels), row, column).svgData())
            }
        }
        val inkHex = hex(ink)
        // stroke hides antialias seams
        val edges = if (isRectilinear) " shape-rendering=\"crispEdges\"" else " stroke=\"$inkHex\" stroke-width=\"0.5\""
        if (data.isNotEmpty()) out.append("<path fill=\"$inkHex\"$edges d=\"$data\"/>\n")
        for (mark in marks) {
            when (mark) {
                is Mark.Shape -> {
                    val moved = mark.path.transformed(pixels, pixels, ox, oy)
                    out.append("<path fill=\"${hex(mark.colour)}\"${if (mark.evenOdd) " fill-rule=\"evenodd\"" else ""} d=\"${moved.svgData()}\"/>\n")
                }
                is Mark.Image -> {
                    val f = mark.frame
                    out.append(
                        "<image x=\"${number(ox + f.x * pixels)}\" y=\"${number(oy + f.y * pixels)}\" width=\"${number(f.width * pixels)}\" " +
                            "height=\"${number(f.height * pixels)}\" href=\"data:image/png;base64,${Base64.getEncoder().encodeToString(mark.png)}\"/>\n",
                    )
                }
                is Mark.Text -> {
                    val f = mark.frame
                    val family = if (mark.mono) "ui-monospace, Menlo, monospace" else "system-ui, -apple-system, sans-serif"
                    out.append(
                        "<text x=\"${number(ox + f.midX * pixels)}\" y=\"${number(oy + f.midY * pixels)}\" font-family=\"$family\" " +
                            "font-size=\"${number(mark.size * pixels)}\" font-weight=\"500\" text-anchor=\"middle\" dominant-baseline=\"central\" " +
                            "fill=\"${hex(mark.colour)}\">${escapeXml(mark.text)}</text>\n",
                    )
                }
            }
        }
        return out.append("</svg>\n").toString()
    }

    companion object {
        private val zigzagTables = HashMap<Int, IntArray>()

        /**
         * Placement index of every module of an n × n QR code, ignoring which modules hold function patterns:
         * column pairs from the right edge, the vertical timing column skipped, upwards then downwards.
         */
        @Synchronized
        fun zigzag(n: Int): IntArray {
            zigzagTables[n]?.let { return it }
            if (n <= 1) return IntArray(0)
            val table = IntArray(n * n)
            val visited = BooleanArray(n * n)
            var index = 0
            var right = n - 1
            var upwards = true
            while (right >= 0) {
                if (right == 6) right = 5
                for (step in 0 until n) {
                    val row = if (upwards) n - 1 - step else step
                    for (column in listOf(right, right - 1)) {
                        if (column < 0) continue
                        table[row * n + column] = index
                        visited[row * n + column] = true
                        index++
                    }
                }
                upwards = !upwards
                right -= 2
            }
            // timing column 6 last
            for (row in 0 until n) {
                if (n > 6 && !visited[row * n + 6]) {
                    table[row * n + 6] = index
                    index++
                }
            }
            zigzagTables[n] = table
            return table
        }

        /** Up to two decimals, no trailing zeros. */
        fun number(value: Float): String {
            var text = String.format(Locale.ROOT, "%.2f", value)
            while (text.endsWith("0")) text = text.dropLast(1)
            if (text.endsWith(".")) text = text.dropLast(1)
            return if (text == "-0") "0" else text
        }

        fun hex(colour: Rgb): String {
            fun byte(v: Double) = (v.coerceIn(0.0, 1.0) * 255).roundToInt()
            return String.format(Locale.ROOT, "#%02x%02x%02x", byte(colour.r), byte(colour.g), byte(colour.b))
        }

        fun escapeXml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}

/** Sheet frame, code origin and points per module for a sheet placed in an area. */
data class SheetPlacement(val sheet: SheetRect, val originX: Float, val originY: Float, val pitch: Float)

/**
 * dt-ios CodeSheetPicture.place: [sheet] fitted in [area] on whole device pixels per module when a module is two
 * pixels or more, the code's corner on a pixel. Units are device pixels, so the scale is 1.
 */
fun placeSheet(sheet: CodeSheet, area: SheetRect): SheetPlacement? {
    if (sheet.width <= 0 || sheet.height <= 0 || area.width <= 0 || area.height <= 0) return null
    var pitch = min(area.width / sheet.width, area.height / sheet.height)
    if (pitch >= 2) pitch = kotlin.math.floor(pitch)
    val fittedWidth = sheet.width * pitch
    val fittedHeight = sheet.height * pitch
    val x = (area.midX - fittedWidth / 2).roundToInt().toFloat()
    val y = (area.midY - fittedHeight / 2).roundToInt().toFloat()
    return SheetPlacement(
        SheetRect(x, y, fittedWidth, fittedHeight),
        (x + sheet.margin.left * pitch).roundToInt().toFloat(),
        (y + sheet.margin.top * pitch).roundToInt().toFloat(),
        pitch,
    )
}
