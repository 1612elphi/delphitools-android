package com.delphi.delphitools.tools.devtools

import android.graphics.BlurMaskFilter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** A transform the class applies, as parameters, so a motion can run part of the way. Angle in radians. */
@Immutable
data class TailwindTurn(val scale: Float = 1f, val angle: Float = 0f, val tx: Float = 0f, val ty: Float = 0f, val skewX: Float = 0f, val skewY: Float = 0f) {
    /** The transform [share] of the way from identity, as a Compose matrix. */
    fun matrix(share: Float = 1f): Matrix {
        val s = 1 + (scale - 1) * share
        val a = angle * share
        return Matrix().apply {
            values[Matrix.ScaleX] = s * cos(a)
            values[Matrix.SkewY] = s * sin(a) + skewY * share
            values[Matrix.SkewX] = -s * sin(a) + skewX * share
            values[Matrix.ScaleY] = s * cos(a)
            values[Matrix.TranslateX] = tx * share
            values[Matrix.TranslateY] = ty * share
        }
    }
}

/** What the stage animates, in design coordinates. */
sealed interface TailwindMotion {
    /** The box turns, scales or moves from where it was to where the class puts it, about [anchor]. */
    data class Transform(val box: Rect, val anchor: Offset, val to: TailwindTurn) : TailwindMotion
    data object Spin : TailwindMotion
    data object Ping : TailwindMotion
    data object Pulse : TailwindMotion
    data object Bounce : TailwindMotion

    /** A dot runs the track in [duration] seconds after [delay], eased by the control points. */
    data class Timing(val curve: List<Float>, val duration: Double, val delay: Double) : TailwindMotion

    /** A dot rides the easing curve drawn in the plot. */
    data class Curve(val curve: List<Float>) : TailwindMotion
}

/**
 * dt-ios TailwindSpecimen: what a Tailwind class does, drawn in an 88 × 60 design box scaled to fit,
 * so a row thumbnail and the stage share one drawing.
 */
object TailwindSpecimen {
    const val DESIGN_WIDTH = 88f
    const val DESIGN_HEIGHT = 60f

    /** The container most specimens draw in. */
    internal val box = rect(8f, 7f, 72f, 46f)
    val trackFrom = Offset(12f, 30f)
    val trackTo = Offset(76f, 30f)
    val plot = rect(24f, 8f, 40f, 44f)

    /** Tailwind's default easing, cubic-bezier(0.4, 0, 0.2, 1). */
    val standard = listOf(0.4f, 0f, 0.2f, 1f)
    val easing = mapOf(
        "ease-linear" to listOf(0f, 0f, 1f, 1f),
        "ease-in" to listOf(0.4f, 0f, 1f, 1f),
        "ease-out" to listOf(0f, 0f, 0.2f, 1f),
        "ease-in-out" to listOf(0.4f, 0f, 0.2f, 1f),
    )

    /** Origin and scale that centre the design box in [rect]. */
    fun fit(rect: Rect): Pair<Offset, Float> {
        val scale = min(rect.width / DESIGN_WIDTH, rect.height / DESIGN_HEIGHT)
        return Offset(rect.center.x - DESIGN_WIDTH * scale / 2, rect.center.y - DESIGN_HEIGHT * scale / 2) to scale
    }

    fun number(name: String): Double = name.split('-').last().toDoubleOrNull() ?: 0.0

    /** The pixel figure in a CSS value: "(16px)" or "16px", else a rem value times 16. */
    fun pixels(css: String): Float? {
        Regex("(-?[\\d.]+)px").find(css)?.let { return it.groupValues[1].toFloatOrNull() ?: 0f }
        Regex("(-?[\\d.]+)rem").find(css)?.let { return (it.groupValues[1].toFloatOrNull() ?: 0f) * 16 }
        return null
    }

    /** Transform classes: the box before, dashed, and its place after. */
    fun transform(name: String): TailwindMotion.Transform? {
        val box = if ("translate" in name) rect(26f, 18f, 24f, 24f) else rect(30f, 16f, 28f, 28f)
        var anchor = box.center
        val value = number(name).toFloat()
        val sign = if (name.startsWith("-")) -1f else 1f
        val rem = 16 * 0.72f
        val turn = when {
            name.startsWith("scale-") -> TailwindTurn(scale = max(0.001f, value / 100))
            "rotate-" in name -> TailwindTurn(angle = sign * value * PI.toFloat() / 180)
            name == "translate-x-full" -> TailwindTurn(tx = box.width)
            "translate-x-" in name -> TailwindTurn(tx = sign * value * rem / 4)
            "translate-y-" in name -> TailwindTurn(ty = sign * value * rem / 4)
            name == "skew-x-3" -> TailwindTurn(skewX = tan(3 * PI / 180).toFloat())
            name == "skew-y-3" -> TailwindTurn(skewY = tan(3 * PI / 180).toFloat())
            name == "origin-center" || name == "origin-top" -> {
                if (name == "origin-top") anchor = Offset(box.center.x, box.top)
                TailwindTurn(angle = 20 * PI.toFloat() / 180)
            }
            else -> return null
        }
        return TailwindMotion.Transform(box, anchor, turn)
    }

    /** A mark in a box's top leading corner, so a turned square reads as turned. */
    fun corner(box: Rect) = rect(box.left + 3.5f, box.top + 3.5f, 4.5f, 4.5f)

    fun motion(item: TailwindClass, category: String): TailwindMotion? {
        val name = item.name
        return when (category) {
            "Transforms" -> transform(name)
            "Transitions" -> when {
                name == "animate-spin" -> TailwindMotion.Spin
                name == "animate-ping" -> TailwindMotion.Ping
                name == "animate-pulse" -> TailwindMotion.Pulse
                name == "animate-bounce" -> TailwindMotion.Bounce
                name in easing -> TailwindMotion.Curve(easing.getValue(name))
                name.startsWith("duration-") -> TailwindMotion.Timing(standard, number(name) / 1000, 0.0)
                name.startsWith("delay-") -> TailwindMotion.Timing(standard, 0.15, number(name) / 1000)
                else -> null
            }
            else -> null
        }
    }
}

internal fun rect(x: Float, y: Float, width: Float, height: Float) = Rect(x, y, x + width, y + height)

private fun Rect.inset(dx: Float, dy: Float) = Rect(left + dx, top + dy, right - dx, bottom - dy)

private fun Rect.union(other: Rect) = Rect(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))

/** The colours a specimen draws with. */
@Immutable
data class TailwindInks(
    val primary: Color,
    val primaryForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val foreground: Color,
    val card: Color,
    val destructive: Color,
)

/** SF Symbols of the iOS drawings, as Material icons. */
@Composable
fun rememberTailwindSymbols(): Map<String, Painter> = mapOf(
    "desktopcomputer" to rememberVectorPainter(Icons.Outlined.DesktopWindows),
    "iphone" to rememberVectorPainter(Icons.Outlined.PhoneIphone),
    "hand.point.up.left.fill" to rememberVectorPainter(Icons.Filled.TouchApp),
    "nosign" to rememberVectorPainter(Icons.Filled.Block),
    "hourglass" to rememberVectorPainter(Icons.Filled.HourglassEmpty),
    "hand.raised.fill" to rememberVectorPainter(Icons.Filled.PanTool),
    "keyboard" to rememberVectorPainter(Icons.Filled.Keyboard),
    "arrow.left.and.right" to rememberVectorPainter(Icons.Filled.SwapHoriz),
    "arrow.up.and.down" to rememberVectorPainter(Icons.Filled.SwapVert),
    "arrow.up.left.and.arrow.down.right" to rememberVectorPainter(Icons.Filled.OpenInFull),
    "lock.fill" to rememberVectorPainter(Icons.Filled.Lock),
)

/** Draws [item]'s specimen fitted in [rect]. [omitsMotion] leaves out what the stage animates. */
fun DrawScope.drawTailwindSpecimen(
    item: TailwindClass,
    category: String,
    rect: Rect,
    inks: TailwindInks,
    measurer: TextMeasurer,
    symbols: Map<String, Painter>,
    omitsMotion: Boolean = false,
) {
    if (rect.width <= 0 || rect.height <= 0) return
    val (origin, scale) = TailwindSpecimen.fit(rect)
    translate(origin.x, origin.y) {
        scale(scale, scale, Offset.Zero) {
            val omits = omitsMotion && TailwindSpecimen.motion(item, category) != null
            SpecimenPainter(this, scale, omits, inks, measurer, symbols).paint(item, category)
        }
    }
}

private class SpecimenPainter(
    val scope: DrawScope,
    val scale: Float,
    val omitsMotion: Boolean,
    val inks: TailwindInks,
    val measurer: TextMeasurer,
    val symbols: Map<String, Painter>,
) {
    private val c = TailwindSpecimen.box
    private val lit = inks.primary
    private val dim = inks.primary.copy(alpha = 0.38f)
    private val tint = inks.primary.copy(alpha = 0.16f)
    private val well = inks.muted
    private val frame = inks.mutedForeground.copy(alpha = 0.55f)
    private val faint = inks.mutedForeground.copy(alpha = 0.45f)
    private val ink = inks.foreground

    private val point = 1 / scale


    fun fill(rect: Rect, colour: Color, radius: Float = 1.2f) {
        scope.drawRoundRect(colour, rect.topLeft, rect.size, CornerRadius(radius))
    }

    fun stroke(rect: Rect, colour: Color, width: Float? = null, dash: FloatArray? = null, radius: Float = 1.2f, cap: StrokeCap = StrokeCap.Butt) {
        scope.drawRoundRect(
            colour,
            rect.topLeft,
            rect.size,
            CornerRadius(radius),
            style = Stroke(width ?: point, cap = cap, pathEffect = dash?.let { PathEffect.dashPathEffect(it) }),
        )
    }

    fun container(rect: Rect = c) {
        fill(rect, well, 2f)
        stroke(rect, frame, radius = 2f)
    }

    fun dashed(rect: Rect, colour: Color = frame) = stroke(rect, colour, dash = floatArrayOf(2.5f, 2f))

    fun line(from: Offset, to: Offset, colour: Color, width: Float? = null, dash: FloatArray? = null, round: Boolean = true) {
        scope.drawLine(colour, from, to, width ?: point, if (round) StrokeCap.Round else StrokeCap.Butt, dash?.let { PathEffect.dashPathEffect(it) })
    }

    /** A line of text as a bar. */
    fun words(x: Float, y: Float, width: Float, colour: Color = faint, height: Float = 3f) {
        fill(rect(x, y - height / 2, max(0f, width), height), colour, height / 2)
    }

    fun style(size: Float, weight: FontWeight = FontWeight.SemiBold, family: FontFamily = FontFamily.Default, colour: Color = ink, italic: Boolean = false) =
        with(scope) {
            TextStyle(color = colour, fontSize = size.toSp(), fontWeight = weight, fontFamily = family, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal)
        }

    /** Returns the drawn text's box. */
    fun text(string: String, style: TextStyle, centre: Offset): Rect {
        val measured = measurer.measure(string, style)
        val topLeft = Offset(centre.x - measured.size.width / 2f, centre.y - measured.size.height / 2f)
        scope.drawText(measured, topLeft = topLeft)
        return Rect(topLeft, Size(measured.size.width.toFloat(), measured.size.height.toFloat()))
    }

    fun text(string: String, size: Float, weight: FontWeight = FontWeight.SemiBold, mono: Boolean = false, colour: Color = ink, centre: Offset) =
        text(string, style(size, weight, if (mono) FontFamily.Monospace else FontFamily.Default, colour), centre)

    fun symbol(name: String, size: Float, colour: Color = ink, centre: Offset) {
        if (name == "cursorarrow") return cursor(size, colour, centre)
        val painter = symbols[name] ?: return
        // sf symbols run 1.2×
        val side = size * 1.2f
        scope.translate(centre.x - side / 2, centre.y - side / 2) {
            with(painter) { draw(Size(side, side), colorFilter = ColorFilter.tint(colour)) }
        }
    }

    private fun cursor(size: Float, colour: Color, centre: Offset) {
        val unit = size * 1.1f / 18f
        val points = listOf(0f to 0f, 0f to 16f, 4f to 12.5f, 7f to 18f, 9.5f to 17f, 6.5f to 11.5f, 11.5f to 11.5f)
        val path = Path().apply {
            points.forEachIndexed { index, (x, y) ->
                val px = centre.x + (x - 5.75f) * unit
                val py = centre.y + (y - 9f) * unit
                if (index == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        scope.drawPath(path, inks.card, style = Stroke(unit * 1.6f))
        scope.drawPath(path, colour)
    }

    /** Diagonal lines over [outer] outside [inner]: margin, as against padding's flat tint. */
    fun hatch(outer: Rect, inner: Rect? = null) {
        val clip = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(outer)
            if (inner != null) addRect(inner)
        }
        scope.clipPath(clip) {
            var x = outer.left - outer.height
            while (x < outer.right) {
                line(Offset(x, outer.bottom), Offset(x + outer.height, outer.top), dim, 0.8f, round = false)
                x += 3
            }
        }
    }

    fun checker(rect: Rect) {
        val clip = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(2f))) }
        scope.clipPath(clip) {
            scope.drawRect(inks.card, rect.topLeft, rect.size)
            val side = 4f
            val square = inks.mutedForeground.copy(alpha = 0.18f)
            for (row in 0..(rect.height / side).toInt()) {
                for (column in 0..(rect.width / side).toInt()) {
                    if ((row + column) % 2 == 0) scope.drawRect(square, Offset(rect.left + column * side, rect.top + row * side), Size(side, side))
                }
            }
        }
    }

    /** [count] boxes along an axis inside [rect], [gap] apart. */
    fun row(rect: Rect, count: Int, gap: Float = 3f, vertical: Boolean = false): List<Rect> {
        val length = ((if (vertical) rect.height else rect.width) - gap * (count - 1)) / count
        return (0 until count).map { index ->
            val offset = index * (length + gap)
            if (vertical) rect(rect.left, rect.top + offset, rect.width, length) else rect(rect.left + offset, rect.top, length, rect.height)
        }
    }

    fun numbered(boxes: List<Rect>) {
        boxes.forEachIndexed { index, box ->
            fill(box, lit)
            text("${index + 1}", min(9f, box.height * 0.7f), FontWeight.Bold, colour = inks.primaryForeground, centre = box.center)
        }
    }

    /** A picture for background classes: sky, sun and hills, square. */
    fun picture(rect: Rect) {
        scope.clipRect(rect.left, rect.top, rect.right, rect.bottom) {
            fill(rect, tint, 0f)
            val side = rect.width
            scope.drawOval(lit.copy(alpha = 0.7f), Offset(rect.left + side * 0.58f, rect.top + side * 0.14f), Size(side * 0.24f, side * 0.24f))
            val hills = Path().apply {
                moveTo(rect.left, rect.bottom)
                lineTo(rect.left + side * 0.32f, rect.top + side * 0.45f)
                lineTo(rect.left + side * 0.55f, rect.top + side * 0.7f)
                lineTo(rect.left + side * 0.75f, rect.top + side * 0.55f)
                lineTo(rect.right, rect.bottom)
                close()
            }
            scope.drawPath(hills, lit)
        }
    }

    /** Fills [paths] blurred by [radius] design units. */
    fun blurred(radius: Float, colour: Color, paths: List<Path>) {
        if (radius <= 0f) return paths.forEach { scope.drawPath(it, colour) }
        scope.drawIntoCanvas { canvas ->
            val paint = Paint().apply { this.color = colour }
            paint.asFrameworkPaint().maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            paths.forEach { canvas.drawPath(it, paint) }
        }
    }

    private fun roundRectPath(rect: Rect, radius: Float) = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(radius))) }


    fun paint(item: TailwindClass, category: String) {
        when (category) {
            "Breakpoints" -> breakpoint(item)
            "Display" -> display(item.name)
            "Position" -> position(item.name)
            "Flexbox" -> flexbox(item.name)
            "Grid" -> grid(item.name)
            "Alignment" -> alignment(item.name)
            "Spacing" -> spacing(item)
            "Sizing" -> sizing(item)
            "Typography" -> typography(item)
            "Text" -> textClass(item.name)
            "Backgrounds" -> background(item.name)
            "Borders" -> border(item)
            "Effects" -> effect(item)
            "Transitions" -> transition(item.name)
            "Transforms" -> transformed(item.name)
            "Interactivity" -> interactivity(item.name)
            "States" -> state(item.name)
            "Overflow & Z-Index" -> overflow(item.name)
            "Arbitrary Values" -> arbitrary(item.name)
            else -> container()
        }
    }

    private fun breakpoint(item: TailwindClass) {
        val widths = listOf(640f, 768f, 1024f, 1280f, 1536f)
        val most = 1800f
        val x = { px: Float -> 8 + 72 * px / most }
        val px = TailwindSpecimen.pixels(item.css) ?: 0f
        line(Offset(8f, 30f), Offset(80f, 30f), faint, 1.5f)
        for (width in widths) line(Offset(x(width), 27f), Offset(x(width), 33f), faint)
        fill(rect(x(px), 20f, 80 - x(px), 20f), tint, 0f)
        line(Offset(x(px), 30f), Offset(80f, 30f), lit, 3f)
        line(Offset(x(px), 16f), Offset(x(px), 44f), lit, 1.5f)
        text("${px.toInt()}", 7f, mono = true, colour = lit, centre = Offset(x(px), 50f))
        symbol("desktopcomputer", 8f, faint, Offset(78f, 12f))
        symbol("iphone", 8f, faint, Offset(10f, 12f))
    }

    private fun display(name: String) {
        when (name) {
            "block" -> {
                container()
                fill(rect(c.left + 5, c.top + 6, c.width - 10, 14f), lit)
                fill(rect(c.left + 5, c.top + 26, c.width - 10, 14f), dim)
            }
            "inline-block" -> {
                words(8f, 22f, 20f)
                fill(rect(31f, 13f, 24f, 18f), lit)
                words(58f, 22f, 22f)
                words(8f, 40f, 50f)
            }
            "inline" -> {
                words(8f, 22f, 34f)
                words(45f, 22f, 35f, lit, 5f)
                words(8f, 36f, 24f, lit, 5f)
                words(35f, 36f, 30f)
            }
            "flex" -> {
                container()
                numbered(row(c.inset(4f, 8f), 3))
            }
            "inline-flex" -> {
                words(6f, 30f, 14f)
                val hug = rect(23f, 19f, 40f, 22f)
                container(hug)
                numbered(row(hug.inset(3f, 3f), 3, 2f))
                words(66f, 30f, 16f)
            }
            "grid", "inline-grid" -> {
                val inline = name == "inline-grid"
                val box = if (inline) rect(25f, 16f, 32f, 28f) else c
                if (inline) {
                    words(6f, 30f, 15f)
                    words(60f, 30f, 20f)
                }
                container(box)
                val columns = if (inline) 2 else 3
                row(box.inset(3f, 3f), 2, 2f, vertical = true).forEachIndexed { rowIndex, band ->
                    row(band, columns, 2f).forEachIndexed { columnIndex, cell -> fill(cell, if ((rowIndex + columnIndex) % 2 == 0) lit else dim) }
                }
            }
            "hidden" -> {
                dashed(c)
                line(Offset(c.left + 10, c.bottom - 8), Offset(c.right - 10, c.top + 8), faint, 1.2f)
            }
            "contents" -> {
                dashed(c)
                for (box in row(c.inset(6f, 10f), 2, 6f)) fill(box, lit)
            }
            else -> container()
        }
    }

    private fun position(name: String) {
        val sw = 22f
        val sh = 14f
        when (name) {
            "static" -> {
                container()
                row(c.inset(5f, 5f), 3, 3f, vertical = true).forEachIndexed { index, band -> fill(band, if (index == 1) lit else dim) }
            }
            "relative" -> {
                container()
                val ghost = rect(18f, 14f, 28f, 18f)
                dashed(ghost)
                fill(ghost.translate(12f, 10f), lit)
            }
            "absolute" -> {
                container()
                for (band in row(c.inset(5f, 5f), 3, 3f, vertical = true)) fill(band, dim)
                fill(rect(c.right - 3 - sw, c.top + 3, sw, sh), lit)
            }
            "fixed" -> {
                val screen = rect(22f, 3f, 44f, 54f)
                stroke(screen, frame, 1.5f, radius = 5f)
                for (index in 0 until 6) words(27f, 10f + index * 8, 34f - (index % 3) * 6)
                fill(rect(screen.right - 16, screen.bottom - 14, 12f, 10f), lit, 2f)
            }
            "sticky" -> {
                container()
                scope.clipRect(c.left, c.top, c.right, c.bottom) {
                    for (index in 0 until 5) words(c.left + 5, c.top + 11 + index * 8, 50f - (index % 2) * 12)
                }
                fill(rect(c.left + 2, c.top + 2, c.width - 4, 11f), lit)
            }
            "inset-0" -> { container(); fill(c.inset(3f, 3f), lit) }
            "inset-x-0" -> { container(); fill(rect(c.left + 3, c.center.y - 7, c.width - 6, 14f), lit) }
            "inset-y-0" -> { container(); fill(rect(c.center.x - 10, c.top + 3, 20f, c.height - 6), lit) }
            "top-0" -> { container(); fill(rect(c.center.x - sw / 2, c.top, sw, sh), lit) }
            "right-0" -> { container(); fill(rect(c.right - sw, c.center.y - sh / 2, sw, sh), lit) }
            "bottom-0" -> { container(); fill(rect(c.center.x - sw / 2, c.bottom - sh, sw, sh), lit) }
            "left-0" -> { container(); fill(rect(c.left, c.center.y - sh / 2, sw, sh), lit) }
            else -> container()
        }
    }

    private fun flexbox(name: String) {
        val inner = c.inset(4f, 8f)
        container()
        when (name) {
            "flex-row" -> numbered(row(inner, 3))
            "flex-col" -> numbered(row(c.inset(12f, 4f), 3, 2f, vertical = true))
            "flex-wrap" -> {
                val top = rect(inner.left, c.top + 4, inner.width, 17f)
                for (box in row(top, 3)) fill(box, lit)
                for (box in row(rect(inner.left, top.bottom + 4, inner.width * 2 / 3 - 1, 17f), 2)) fill(box, dim)
            }
            "flex-nowrap" -> for (index in 0 until 5) {
                val box = rect(inner.left + index * 19, inner.top, 16f, inner.height)
                fill(box, if (box.right > c.right) dim else lit)
            }
            "flex-1" -> for (box in row(inner, 3)) fill(box, lit)
            "flex-auto" -> {
                var x = inner.left
                val total = inner.width - 6
                for (share in listOf(0.24f, 0.46f, 0.3f)) {
                    fill(rect(x, inner.top, total * share, inner.height), lit)
                    words(x + 3, inner.center.y, total * share - 6, inks.primaryForeground.copy(alpha = 0.6f))
                    x += total * share + 3
                }
            }
            "flex-none", "grow-0" -> {
                for (index in 0 until 3) {
                    fill(rect(inner.left + index * 17, inner.top, 14f, inner.height), if (name == "grow-0" && index != 1) dim else lit)
                }
                dashed(rect(inner.left + 51, inner.top, inner.width - 51, inner.height))
            }
            "grow" -> {
                fill(rect(inner.left, inner.top, 14f, inner.height), dim)
                fill(rect(inner.left + 17, inner.top, inner.width - 34, inner.height), lit)
                fill(rect(inner.right - 14, inner.top, 14f, inner.height), dim)
            }
            "shrink" -> {
                val boxes = listOf(
                    rect(inner.left, inner.top, 26f, inner.height),
                    rect(inner.left + 29, inner.top, 8f, inner.height),
                    rect(inner.left + 40, inner.top, inner.width - 40, inner.height),
                )
                boxes.forEachIndexed { index, box -> fill(box, if (index == 1) lit else dim) }
                for ((from, to) in listOf(
                    Offset(boxes[1].left - 7, c.top + 4) to Offset(boxes[1].left - 1, c.top + 4),
                    Offset(boxes[1].right + 7, c.top + 4) to Offset(boxes[1].right + 1, c.top + 4),
                )) {
                    val back = if (from.x < to.x) -2f else 2f
                    line(from, to, lit, 1.2f)
                    line(to, Offset(to.x + back, to.y - 2), lit, 1.2f)
                    line(to, Offset(to.x + back, to.y + 2), lit, 1.2f)
                }
            }
            "shrink-0" -> {
                fill(rect(inner.left, inner.top, 16f, inner.height), dim)
                fill(rect(inner.left + 19, inner.top, 34f, inner.height), lit)
                fill(rect(inner.left + 56, inner.top, inner.width - 56, inner.height), dim)
            }
        }
    }

    private fun grid(name: String) {
        container()
        val inner = c.inset(3f, 3f)
        when {
            name.startsWith("grid-cols-") -> {
                val columns = max(1, TailwindSpecimen.number(name).toInt())
                val gap = if (columns > 6) 1f else 2f
                for (band in row(inner, 2, gap, vertical = true)) for (cell in row(band, columns, gap)) fill(cell, lit, 0.6f)
            }
            name.startsWith("grid-rows-") -> {
                val rows = max(1, TailwindSpecimen.number(name).toInt())
                for (band in row(inner, rows, 2f, vertical = true)) for (cell in row(band, 3, 2f)) fill(cell, lit)
            }
            else -> {
                val bands = row(inner, 2, 2f, vertical = true)
                val cells = row(bands[0], 3, 2f)
                val span = when (name) {
                    "col-span-full" -> 3
                    "col-span-2" -> 2
                    else -> 1
                }
                fill(cells[0].union(cells[span - 1]), lit)
                for (cell in cells.drop(span)) fill(cell, dim)
                for (cell in row(bands[1], 3, 2f)) fill(cell, dim)
            }
        }
    }

    private fun alignment(name: String) {
        container()
        val inner = c.inset(4f, 4f)
        val width = 12f
        if (name.startsWith("justify-")) {
            val free = inner.width - width * 3
            val steps = listOf(0f, 1f, 2f)
            val xs = when (name) {
                "justify-center" -> steps.map { free / 2 + it * width }
                "justify-end" -> steps.map { free + it * width }
                "justify-between" -> listOf(0f, free / 2 + width, free + width * 2)
                "justify-around" -> steps.map { free / 6 + it * (width + free / 3) }
                "justify-evenly" -> steps.map { free / 4 * (it + 1) + it * width }
                else -> steps.map { it * width }
            }
            xs.forEachIndexed { index, x -> fill(rect(inner.left + x, inner.top + 8, width - 1, inner.height - 16), if (index == 1) lit else dim) }
            dashed(rect(inner.left, inner.center.y, inner.width, 0.01f), faint)
            return
        }
        val heights = listOf(16f, 30f, 22f)
        val start = inner.center.x - (3 * 14 + 2 * 9) / 2f
        val xs = listOf(start, start + 23, start + 46)
        if (name == "items-baseline") {
            val baseline = inner.top + 24
            val above = listOf(8f, 16f, 12f)
            line(Offset(inner.left, baseline), Offset(inner.right, baseline), lit, 0.8f, floatArrayOf(2f, 2f))
            for (index in 0 until 3) {
                val box = rect(xs[index], baseline - above[index], 14f, heights[index] * 0.8f)
                fill(box, if (index == 1) lit else dim)
                words(box.left + 2, baseline - 1, 10f, inks.primaryForeground, 2f)
            }
            return
        }
        for (index in 0 until 3) {
            val isSelf = name.startsWith("self-")
            val mode = if (isSelf) (if (index == 1) name.drop(5) else "stretch") else name.drop(6)
            val height = if (mode == "stretch" || mode == "auto") inner.height else heights[index]
            val y = when (mode) {
                "center" -> inner.center.y - height / 2
                "end" -> inner.bottom - height
                else -> inner.top
            }
            val colour = if (!isSelf || index == 1) (if (index == 1) lit else dim) else dim.copy(alpha = 0.2f)
            fill(rect(xs[index], y, 14f, height), colour)
        }
    }

    private fun spacing(item: TailwindClass) {
        val name = item.name
        val px = TailwindSpecimen.pixels(item.css) ?: (if (name.endsWith("-*")) 16f else 0f)
        val amount = min(14f, px * 0.45f)
        when {
            name.startsWith("p") -> {
                val (top, left, bottom, right) = when (name.take(3)) {
                    "px-" -> listOf(0f, 9f, 0f, 9f)
                    "py-" -> listOf(7f, 0f, 7f, 0f)
                    "pt/" -> listOf(8f, 0f, 0f, 0f)
                    else -> listOf(amount, amount, amount, amount)
                }
                fill(c, tint, 2f)
                stroke(c, lit, radius = 2f)
                val content = Rect(c.left + left, c.top + top, c.right - right, c.bottom - bottom)
                fill(content, dim)
                words(content.left + 4, content.center.y, content.width - 8, inks.primaryForeground.copy(alpha = 0.7f))
            }
            name.startsWith("gap") -> {
                val gap = if (name.endsWith("-*")) 6f else min(12f, px * 0.6f)
                container()
                val inner = c.inset(3f, 3f)
                if (name == "gap-x-*" || name == "gap-y-*") {
                    val vertical = name == "gap-y-*"
                    val bands = row(inner, 2, if (vertical) gap else 2f, vertical = true)
                    fill(
                        if (vertical) rect(inner.left, bands[0].bottom, inner.width, gap) else rect(inner.center.x - gap / 2, inner.top, gap, inner.height),
                        tint,
                        0f,
                    )
                    for (band in bands) for (cell in row(band, 2, if (vertical) 2f else gap)) fill(cell, lit)
                } else {
                    val boxes = row(inner, 3, gap)
                    boxes.zipWithNext().forEach { (left, right) -> fill(Rect(left.right, inner.top, right.left, inner.bottom), tint, 0f) }
                    for (box in boxes) fill(box, lit)
                }
            }
            else -> {
                dashed(c)
                val signed = if (name.startsWith("-")) -(TailwindSpecimen.pixels(item.css)?.let { abs(it) } ?: 4f) * 0.8f else amount
                val box = when (name) {
                    "m-auto" -> rect(c.center.x - 16, c.center.y - 10, 32f, 20f)
                    "mx-auto" -> rect(c.center.x - 18, c.top, 36f, c.height)
                    "mt-4" -> rect(c.left, c.top + 10, c.width, c.height - 10)
                    else -> c.inset(signed, signed)
                }
                if (signed > 0 || name.endsWith("auto") || name == "mt-4") hatch(c, box)
                fill(box, lit)
                if (signed < 0) dashed(c, inks.primaryForeground)
            }
        }
    }

    private fun sizing(item: TailwindClass) {
        val name = item.name
        val css = item.css
        val percent = Regex("([\\d.]+)%").find(css)?.let { (it.groupValues[1].toFloatOrNull() ?: 0f) / 100 }
        val px = TailwindSpecimen.pixels(css)
        val screenWide = "100vw" in css || "100vh" in css
        if (name.startsWith("size-")) {
            container()
            val side = if (percent != null) c.height - 6 else (px ?: 16f) * 1.2f
            val full = name == "size-full"
            fill(rect(if (full) c.left + 3 else c.center.x - side / 2, c.center.y - side / 2, if (full) c.width - 6 else side, side), lit)
            return
        }
        if (name.startsWith("h-") || name.startsWith("min-h-")) {
            if (screenWide) {
                dashed(rect(4f, 2f, 80f, 56f), faint)
                container(c.inset(0f, 8f))
                fill(rect(c.center.x - 7, 2f, 14f, 56f), lit)
                return
            }
            container()
            val height = percent?.let { c.height * it } ?: (if ("auto" in css) 20f else (px ?: 0f) * 0.72f)
            val bar = rect(c.center.x - 7, c.bottom - height, 14f, height)
            if (height > 0) fill(bar, lit) else line(Offset(bar.left - 3, c.bottom - 1), Offset(bar.right + 3, c.bottom - 1), lit, 1.5f)
            if ("auto" in css) for (index in 0 until 3) words(bar.left + 3, bar.top + 5 + index * 5, 8f, inks.primaryForeground.copy(alpha = 0.7f), 2f)
            if (name.startsWith("min-")) line(Offset(c.left + 4, bar.top), Offset(c.right - 4, bar.top), lit, 0.8f, floatArrayOf(2f, 2f))
            return
        }
        if (screenWide) {
            dashed(rect(2f, 8f, 84f, 44f), faint)
            container(c.inset(10f, 0f))
            fill(rect(2f, c.center.y - 7, 84f, 14f), lit)
            return
        }
        container()
        val inner = c.inset(4f, 0f)
        val fits = "auto" in css || "fit-content" in css
        val width = when {
            name.startsWith("max-w-") -> percent?.let { inner.width * it } ?: (min(1f, (px ?: 0f) / 1024) * inner.width)
            fits -> 34f
            else -> percent?.let { inner.width * it } ?: min(inner.width, (px ?: 0f) * inner.width / 256)
        }
        val bar = rect(inner.left, c.center.y - 8, width, 16f)
        if (width > 0) fill(bar, lit) else line(Offset(bar.left, bar.top - 3), Offset(bar.left, bar.bottom + 3), lit, 1.5f)
        if (fits) words(bar.left + 4, bar.center.y, width - 8, inks.primaryForeground.copy(alpha = 0.7f))
        if (name.startsWith("max-w-") || name.startsWith("min-w-")) {
            val x = bar.right
            line(Offset(x, c.top + 4), Offset(x, c.bottom - 4), lit, 1.2f)
            if (name.startsWith("max-w-")) dashed(rect(x, bar.top, inner.right - x, bar.height), faint)
        }
    }

    private fun typography(item: TailwindClass) {
        val name = item.name
        val centre = Offset(44f, 30f)
        when {
            name.startsWith("text-") -> text("Aa", (TailwindSpecimen.pixels(item.css) ?: 16f) * 0.95f, FontWeight.Medium, centre = centre)
            name.startsWith("font-") -> {
                val weights = mapOf(
                    "font-thin" to FontWeight.ExtraLight, "font-light" to FontWeight.Light, "font-normal" to FontWeight.Normal,
                    "font-medium" to FontWeight.Medium, "font-semibold" to FontWeight.SemiBold, "font-bold" to FontWeight.Bold,
                )
                val style = when (name) {
                    "font-serif" -> style(30f, FontWeight.Normal, FontFamily.Serif)
                    "font-mono" -> style(28f, FontWeight.Normal, FontFamily.Monospace)
                    else -> style(30f, weights[name] ?: FontWeight.Normal)
                }
                text("Aa", style, centre)
            }
            name == "italic" || name == "not-italic" -> text("Aa", style(30f, FontWeight.Normal, italic = name == "italic"), centre)
            name.startsWith("leading-") -> {
                val heights = mapOf("leading-none" to 1f, "leading-tight" to 1.25f, "leading-normal" to 1.5f, "leading-relaxed" to 1.625f, "leading-loose" to 2f)
                val pitch = 8 * (heights[name] ?: 1.5f)
                val top = 30 - pitch * 1.5f
                for (index in 0 until 3) {
                    val band = rect(14f, top + index * pitch, 60f, pitch)
                    fill(band, if (index % 2 == 0) tint else tint.copy(alpha = 0.08f), 0f)
                    words(16f, band.center.y, if (index == 2) 36f else 56f, ink.copy(alpha = 0.7f), 5f)
                }
            }
            name.startsWith("tracking-") -> {
                val em = mapOf("tracking-tight" to -0.025f, "tracking-normal" to 0f, "tracking-wide" to 0.025f)
                val size = 20f
                // kerning ×4 for visibility
                val spacing = with(scope) { ((em[name] ?: 0f) * size * 4).toSp() }
                text("Abc", style(size, FontWeight.Medium).copy(letterSpacing = spacing), centre)
                line(Offset(18f, 42f), Offset(70f, 42f), faint, 0.8f, floatArrayOf(2f, 2f))
            }
        }
    }

    private fun textClass(name: String) {
        val centre = Offset(44f, 30f)
        val sample = "hi there"
        when (name) {
            "text-left", "text-center", "text-right", "text-justify" -> {
                container()
                val lengths = listOf(0.92f, 0.64f, 0.84f, 0.46f)
                val inner = c.inset(6f, 0f)
                lengths.forEachIndexed { index, share ->
                    val full = name == "text-justify" && index < 3
                    val width = if (full) inner.width else inner.width * share
                    val x = when (name) {
                        "text-center" -> inner.center.x - width / 2
                        "text-right" -> inner.right - width
                        else -> inner.left
                    }
                    words(x, c.top + 10 + index * 9, width, if (index == 1) lit else ink.copy(alpha = 0.55f))
                }
            }
            "uppercase" -> text(sample.uppercase(), 13f, centre = centre)
            "lowercase" -> text(sample.lowercase(), 13f, centre = centre)
            "capitalize" -> text("Hi There", 13f, centre = centre)
            "normal-case" -> text("hi There", 13f, centre = centre)
            "underline", "line-through" -> {
                val box = text("Abc", 24f, FontWeight.Medium, centre = centre)
                val y = if (name == "underline") box.bottom - box.height * 0.2f else box.center.y + box.height * 0.04f
                line(Offset(box.left, y), Offset(box.right, y), lit, 1.2f, round = false)
            }
            "no-underline" -> text("Abc", 24f, FontWeight.Medium, centre = centre)
            "truncate", "text-ellipsis", "text-clip" -> {
                val box = rect(14f, 20f, 58f, 20f)
                stroke(box, lit)
                val inner = box.inset(3f, 3f)
                val measured = measurer.measure(
                    "hi there, friend",
                    style(11f),
                    overflow = if (name == "text-clip") TextOverflow.Clip else TextOverflow.Ellipsis,
                    softWrap = false,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = ceil(inner.width).toInt()),
                )
                scope.drawText(measured, topLeft = inner.topLeft)
            }
            "whitespace-normal", "whitespace-nowrap", "whitespace-pre" -> {
                val box = rect(14f, 10f, 46f, 40f)
                stroke(box, frame)
                when (name) {
                    "whitespace-nowrap" -> words(box.left + 3, box.top + 8, 68f, lit)
                    "whitespace-pre" -> listOf(0f, 8f, 8f, 0f).forEachIndexed { index, indent -> words(box.left + 3 + indent, box.top + 8 + index * 8, 22f, lit) }
                    else -> listOf(40f, 40f, 18f).forEachIndexed { index, width -> words(box.left + 3, box.top + 8 + index * 8, width, lit) }
                }
            }
            "break-normal", "break-words", "break-all" -> {
                val box = rect(14f, 10f, 46f, 40f)
                stroke(box, frame)
                when (name) {
                    "break-normal" -> {
                        words(box.left + 3, box.top + 8, 12f)
                        words(box.left + 3, box.top + 16, 64f, lit)
                    }
                    "break-words" -> {
                        words(box.left + 3, box.top + 8, 12f)
                        words(box.left + 3, box.top + 16, 40f, lit)
                        words(box.left + 3, box.top + 24, 24f, lit)
                    }
                    else -> {
                        words(box.left + 3, box.top + 8, 12f)
                        words(box.left + 17, box.top + 8, 26f, lit)
                        words(box.left + 3, box.top + 16, 38f, lit)
                    }
                }
            }
            else -> {
                val lines = max(1, TailwindSpecimen.number(name).toInt())
                val box = rect(14f, 8f, 60f, 44f)
                for (index in 0 until 4) {
                    val y = box.top + 6 + index * 9
                    if (index < lines) {
                        val last = index == lines - 1
                        words(box.left, y, if (last) 42f else 60f, lit)
                        if (last) text("…", 10f, FontWeight.Bold, colour = lit, centre = Offset(box.left + 48, y - 2))
                    } else {
                        dashed(rect(box.left, y - 1.5f, 60f, 3f), faint)
                    }
                }
            }
        }
    }

    private fun background(name: String) {
        val colours = mapOf(
            "bg-white" to Color.White,
            "bg-black" to Color.Black,
            "bg-slate-500" to Color(0xFF64748B),
            "bg-[#hex]" to Color(0xFF1DA1F2),
            "bg-current" to ink,
        )
        if (name == "bg-transparent") {
            checker(c)
            stroke(c, frame, radius = 2f)
            return
        }
        colours[name]?.let {
            fill(c, it, 2f)
            stroke(c, frame, radius = 2f)
            return
        }
        if (name.startsWith("from-") || name.startsWith("via-") || name.startsWith("to-") || name.startsWith("bg-gradient")) {
            val stops = if (name.startsWith("bg-gradient")) listOf(lit, lit.copy(alpha = 0.08f)) else listOf(Color(0xFF3B82F6), Color(0xFFA855F7), Color(0xFFEC4899))
            val down = name == "bg-gradient-to-b"
            val brush = Brush.linearGradient(stops, c.topLeft, if (down) Offset(c.left, c.bottom) else Offset(c.right, c.top))
            scope.drawRoundRect(brush, c.topLeft, c.size, CornerRadius(2f))
            stroke(c, frame, radius = 2f)
            val at = when {
                name.startsWith("from-") -> 0f
                name.startsWith("via-") -> 0.5f
                name.startsWith("to-") -> 1f
                else -> null
            }
            if (at != null) {
                val marker = Offset(c.left + c.width * at, c.bottom)
                scope.drawCircle(Color.White, 4f, marker)
                scope.drawCircle(ink, 4f, marker, style = Stroke(1.5f))
            }
            return
        }
        container()
        scope.clipPath(roundRectPath(c, 2f)) {
            when (name) {
                "bg-cover" -> picture(rect(c.left, c.center.y - c.width / 2, c.width, c.width))
                "bg-contain" -> picture(rect(c.center.x - c.height / 2, c.top, c.height, c.height))
                "bg-center" -> picture(rect(c.center.x - 13, c.center.y - 13, 26f, 26f))
                "bg-no-repeat" -> picture(rect(c.left, c.top, 22f, 22f))
                "bg-repeat" -> {
                    var y = c.top
                    while (y < c.bottom) {
                        var x = c.left
                        while (x < c.right) {
                            picture(rect(x, y, 22f, 22f))
                            x += 22
                        }
                        y += 22
                    }
                }
                "bg-fixed" -> {
                    picture(rect(c.left, c.center.y - c.width / 2, c.width, c.width))
                    for (index in 0 until 4) words(c.left + 6, c.top + 8 + index * 10, 40f, inks.card)
                }
            }
        }
        stroke(c, frame, radius = 2f)
    }

    private fun border(item: TailwindClass) {
        val name = item.name
        val box = c.inset(6f, 6f)
        if (name.startsWith("rounded")) {
            val px = if (name == "rounded-full") box.height / 2 else (TailwindSpecimen.pixels(item.css) ?: 0f) * 1.1f
            val path = Path().apply {
                if (name == "rounded-t-lg") {
                    addRoundRect(RoundRect(box, topLeft = CornerRadius(8.8f), topRight = CornerRadius(8.8f), bottomRight = CornerRadius.Zero, bottomLeft = CornerRadius.Zero))
                } else {
                    addRoundRect(RoundRect(box, CornerRadius(min(px, box.height / 2))))
                }
            }
            scope.drawPath(path, dim)
            scope.drawPath(path, lit, style = Stroke(1.5f))
            return
        }
        if (name.startsWith("divide-")) {
            container()
            val vertical = name == "divide-y"
            for (part in row(c, 3, 0f, vertical).drop(1)) {
                val from = if (vertical) Offset(c.left, part.top) else Offset(part.left, c.top)
                val to = if (vertical) Offset(c.right, part.top) else Offset(part.left, c.bottom)
                line(from, to, lit, 1.5f)
            }
            return
        }
        fill(box, tint)
        when (name) {
            "border-t", "border-r", "border-b", "border-l" -> {
                dashed(box)
                val (from, to) = when (name) {
                    "border-t" -> box.topLeft to box.topRight
                    "border-r" -> box.topRight to box.bottomRight
                    "border-b" -> box.bottomLeft to box.bottomRight
                    else -> box.topLeft to box.bottomLeft
                }
                line(from, to, lit, 2.4f, round = false)
            }
            "border-dashed" -> stroke(box, lit, 2f, floatArrayOf(5f, 3f))
            "border-dotted" -> stroke(box, lit, 2f, floatArrayOf(0.01f, 4f), cap = StrokeCap.Round)
            "border-none", "border-0" -> dashed(box)
            "border-solid" -> stroke(box, lit, 2f)
            else -> {
                val px = TailwindSpecimen.pixels(item.css) ?: 1f
                stroke(box.inset(px * 0.6f, px * 0.6f), lit, px * 1.2f)
            }
        }
    }

    private fun effect(item: TailwindClass) {
        val name = item.name
        if (name.startsWith("shadow")) {
            val card = rect(22f, 12f, 44f, 30f)
            // shadows need light page
            fill(rect(6f, 3f, 76f, 54f), Color(0xFFF5F5F5), 2f)
            val numbers = Regex("(-?[\\d.]+)(px)?").findAll(item.css).mapNotNull { it.groupValues[1].toFloatOrNull() }.toList()
            val edge = Color.Black.copy(alpha = 0.08f)
            if (name == "shadow-inner") {
                fill(card, Color.White, 3f)
                scope.clipPath(roundRectPath(card, 3f)) {
                    val ring = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(card.inset(-20f, -20f).translate(0f, 2f))
                        addRoundRect(RoundRect(card.translate(0f, 2f), CornerRadius(3f)))
                    }
                    blurred(2f, Color.Black.copy(alpha = 0.25f), listOf(ring))
                }
                stroke(card, edge, radius = 3f)
                return
            }
            if (name != "shadow-none" && numbers.size >= 4) {
                // x y blur spread
                val dy = numbers[1]
                val blur = numbers[2]
                val spread = numbers[3]
                val alpha = numbers.last()
                val shadow = card.inset(-spread * 0.5f, -spread * 0.5f).translate(0f, dy * 0.5f)
                blurred(blur * 0.25f, Color.Black.copy(alpha = min(0.6f, alpha * 3)), listOf(roundRectPath(shadow, 3f)))
            }
            fill(card, Color.White, 3f)
            stroke(card, edge, radius = 3f)
            return
        }
        if (name.startsWith("opacity-")) {
            checker(c)
            fill(c.inset(10f, 8f), lit.copy(alpha = (TailwindSpecimen.number(name) / 100).toFloat()))
            stroke(c.inset(10f, 8f), frame, dash = floatArrayOf(2f, 2f))
            return
        }
        if (name.startsWith("blur")) {
            val radius = (TailwindSpecimen.pixels(item.css) ?: 0f) * 0.5f
            val shapes = listOf(
                Path().apply { addOval(rect(22f, 14f, 26f, 26f)) },
                Path().apply { addRect(rect(42f, 22f, 24f, 20f)) },
            )
            blurred(radius, lit, shapes)
            return
        }
        val stripes = (0 until 7).map { index -> Path().apply { addRect(rect(10f + index * 10, 8f, 5f, 44f)) } }
        stripes.forEach { scope.drawPath(it, lit) }
        val panel = rect(20f, 18f, 48f, 24f)
        scope.clipPath(roundRectPath(panel, 3f)) {
            fill(panel, inks.card, 0f)
            blurred(if (name == "backdrop-blur-sm") 2f else 4f, lit, stripes)
            fill(panel, inks.card.copy(alpha = 0.35f), 0f)
        }
        stroke(panel, frame, radius = 3f)
    }

    private fun transition(name: String) {
        when (name) {
            "animate-spin" -> {
                if (omitsMotion) return
                spinArc(scope, lit, 0f)
            }
            "animate-ping" -> {
                if (omitsMotion) return
                for ((radius, alpha) in listOf(18f to 0.2f, 12f to 0.4f)) {
                    stroke(rect(44 - radius, 30 - radius, radius * 2, radius * 2), lit.copy(alpha = alpha), 1.5f, radius = radius)
                }
                fill(rect(38f, 24f, 12f, 12f), lit, 6f)
            }
            "animate-pulse" -> {
                if (omitsMotion) return
                listOf(1f, 0.5f, 1f).forEachIndexed { index, alpha -> fill(rect(14f + index * 22, 20f, 16f, 20f), lit.copy(alpha = alpha), 2f) }
            }
            "animate-bounce" -> {
                line(Offset(20f, 50f), Offset(68f, 50f), faint, 1.2f)
                if (omitsMotion) return
                val path = Path().apply {
                    moveTo(24f, 44f)
                    quadraticTo(34f, 4f, 44f, 44f)
                    quadraticTo(54f, 4f, 64f, 44f)
                }
                scope.drawPath(path, faint, style = Stroke(0.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f))))
                fill(rect(38f, 14f, 12f, 12f), lit, 6f)
            }
            else -> {
                TailwindSpecimen.easing[name]?.let { return plot(it) }
                val from = TailwindSpecimen.trackFrom
                val to = TailwindSpecimen.trackTo
                val x = { ms: Double -> from.x + (to.x - from.x) * (ms / 500).toFloat() }
                for (ms in 0..500 step 100) line(Offset(x(ms.toDouble()), 42f), Offset(x(ms.toDouble()), 45f), faint)
                line(Offset(from.x, 42f), Offset(to.x, 42f), faint)
                val label = { ms: Double ->
                    text("${ms.toInt()}ms", 7f, mono = true, colour = lit, centre = Offset(min(to.x - 8, max(from.x + 8, x(ms))), 51f))
                }
                when {
                    name.startsWith("duration-") -> {
                        val ms = TailwindSpecimen.number(name)
                        fill(rect(from.x, 14f, x(ms) - from.x, 8f), lit)
                        label(ms)
                    }
                    name.startsWith("delay-") -> {
                        val ms = TailwindSpecimen.number(name)
                        line(Offset(from.x, 18f), Offset(x(ms), 18f), faint, 1.2f, floatArrayOf(2f, 2f))
                        fill(rect(x(ms), 14f, x(ms + 150) - x(ms), 8f), lit)
                        label(ms)
                    }
                    else -> {
                        // tailwind default: 150 ms
                        val ms = if (name == "transition-none") 0.0 else 150.0
                        if (ms > 0) fill(rect(from.x, 14f, x(ms) - from.x, 8f), dim)
                        label(ms)
                        val start = rect(18f, 26f, 10f, 10f)
                        val end = rect(60f, 26f, 10f, 10f)
                        when (name) {
                            "transition-colors" -> { fill(start, dim); fill(end, lit) }
                            "transition-opacity" -> {
                                fill(start, lit)
                                fill(end, lit.copy(alpha = 0.15f))
                                stroke(end, frame, dash = floatArrayOf(2f, 2f))
                            }
                            "transition-transform" -> {
                                dashed(start)
                                scope.rotate(45f, end.center) { fill(end, lit) }
                            }
                            else -> { fill(start, dim); fill(end.inset(-2f, -2f), lit) }
                        }
                        line(Offset(32f, 31f), Offset(54f, 31f), faint, 1f, if (name == "transition-none") floatArrayOf(2f, 2f) else null)
                    }
                }
                if (!omitsMotion && !name.startsWith("transition")) fill(rect(from.x - 3, from.y - 3, 6f, 6f), lit, 3f)
            }
        }
    }

    private fun plot(curve: List<Float>) {
        val box = TailwindSpecimen.plot
        line(box.topLeft, box.bottomLeft, faint)
        line(box.bottomLeft, box.bottomRight, faint)
        line(box.bottomLeft, box.topRight, faint, 0.6f, floatArrayOf(2f, 2f))
        val path = Path().apply {
            moveTo(box.left, box.bottom)
            cubicTo(
                box.left + box.width * curve[0], box.bottom - box.height * curve[1],
                box.left + box.width * curve[2], box.bottom - box.height * curve[3],
                box.right, box.top,
            )
        }
        scope.drawPath(path, lit, style = Stroke(2f, cap = StrokeCap.Round))
    }

    private fun transformed(name: String) {
        val motion = TailwindSpecimen.transform(name) ?: return container()
        dashed(motion.box)
        if (!omitsMotion) turnedBox(scope, motion, 1f, lit, inks.primaryForeground)
        if (name.startsWith("origin-")) fill(rect(motion.anchor.x - 2.5f, motion.anchor.y - 2.5f, 5f, 5f), ink, 2.5f)
    }

    private fun interactivity(name: String) {
        val cursors = mapOf(
            "cursor-pointer" to "hand.point.up.left.fill", "cursor-default" to "cursorarrow", "cursor-not-allowed" to "nosign",
            "cursor-wait" to "hourglass", "cursor-grab" to "hand.raised.fill",
        )
        cursors[name]?.let {
            symbol(it, 26f, if (name == "cursor-not-allowed") inks.destructive else ink, Offset(44f, 30f))
            return
        }
        when (name) {
            "pointer-events-none", "pointer-events-auto" -> {
                val below = rect(16f, 18f, 40f, 30f)
                val above = rect(32f, 10f, 40f, 30f)
                val passes = name == "pointer-events-none"
                fill(below, if (passes) lit else dim)
                if (passes) dashed(above) else fill(above, lit)
                symbol("cursorarrow", 12f, ink, Offset(46f, 36f))
            }
            "select-none", "select-text", "select-all" -> {
                for (index in 0 until 3) {
                    val y = 18f + index * 10
                    if (name == "select-all") fill(rect(14f, y - 4, 60f, 8f), tint, 0f)
                    if (name == "select-text" && index == 1) fill(rect(26f, y - 4, 30f, 8f), tint, 0f)
                    words(14f, y, if (index == 2) 40f else 60f, ink.copy(alpha = 0.6f))
                }
                if (name == "select-none") symbol("nosign", 12f, faint, Offset(70f, 46f))
            }
            "resize", "resize-none", "resize-x", "resize-y" -> {
                val box = rect(14f, 10f, 52f, 36f)
                container(box)
                if (name != "resize-none") {
                    for (offset in listOf(4f, 8f)) line(Offset(box.right - offset, box.bottom - 1.5f), Offset(box.right - 1.5f, box.bottom - offset), ink, 1f)
                }
                val arrow = when (name) {
                    "resize-x" -> "arrow.left.and.right"
                    "resize-y" -> "arrow.up.and.down"
                    "resize" -> "arrow.up.left.and.arrow.down.right"
                    else -> "lock.fill"
                }
                symbol(arrow, 10f, if (name == "resize-none") faint else lit, Offset(box.right + 8, box.bottom + 6))
            }
            "scroll-smooth", "scroll-auto" -> {
                val track = rect(56f, 8f, 4f, 44f)
                fill(track, well, 2f)
                val positions = if (name == "scroll-smooth") listOf(0f, 0.04f, 0.14f, 0.3f, 0.52f, 0.72f, 0.86f, 0.95f, 1f) else listOf(0f, 1f)
                positions.forEachIndexed { index, share ->
                    val y = track.top + 4 + (track.height - 8) * share
                    fill(rect(27f, y - 3, 6f, 6f), if (index == positions.lastIndex) lit else dim, 3f)
                }
                fill(rect(track.left, track.bottom - 12, 4f, 12f), lit, 2f)
                if (name == "scroll-auto") line(Offset(30f, 16f), Offset(30f, 44f), faint, 0.8f, floatArrayOf(2f, 3f))
            }
            else -> container()
        }
    }

    private fun button(rect: Rect, colour: Color) {
        fill(rect, colour, 3f)
        words(rect.left + 8, rect.center.y, rect.width - 16, inks.primaryForeground.copy(alpha = 0.8f))
    }

    private fun state(name: String) {
        val pill = rect(22f, 20f, 44f, 20f)
        when (name) {
            "hover:" -> {
                button(pill, lit)
                symbol("cursorarrow", 13f, ink, Offset(pill.right - 4, pill.bottom + 3))
            }
            "focus:", "focus-visible:" -> {
                button(pill, dim)
                stroke(pill.inset(-3f, -3f), lit, 1.8f, radius = 5f)
                if (name == "focus-visible:") symbol("keyboard", 10f, lit, Offset(44f, 51f))
            }
            "active:" -> button(pill.inset(2f, 1.5f), lit.copy(alpha = 0.75f))
            "disabled:" -> button(pill, lit.copy(alpha = 0.25f))
            "first:", "last:", "odd:", "even:" -> row(c.inset(10f, 3f), 4, 2f, vertical = true).forEachIndexed { index, band ->
                val on = when (name) {
                    "first:" -> index == 0
                    "last:" -> index == 3
                    "odd:" -> index % 2 == 0
                    else -> index % 2 == 1
                }
                fill(band, if (on) lit else dim.copy(alpha = 0.2f))
            }
            "group-hover:" -> {
                val parent = rect(14f, 10f, 60f, 40f)
                fill(parent, tint, 3f)
                stroke(parent, lit, radius = 3f)
                fill(rect(20f, 16f, 20f, 28f), lit, 2f)
                words(46f, 22f, 22f)
                words(46f, 30f, 16f)
                symbol("cursorarrow", 12f, ink, Offset(62f, 42f))
            }
            "peer-focus:" -> {
                val input = rect(12f, 20f, 32f, 20f)
                fill(input, inks.card, 2f)
                stroke(input.inset(-2.5f, -2.5f), lit, 1.6f, radius = 4f)
                line(Offset(input.left + 5, input.top + 5), Offset(input.left + 5, input.bottom - 5), lit, 1f)
                fill(rect(52f, 20f, 24f, 20f), lit, 2f)
            }
            "dark:" -> {
                val left = rect(12f, 12f, 32f, 36f)
                val right = rect(44f, 12f, 32f, 36f)
                fill(left, Color.White, 0f)
                fill(right, Color(0xFF1A1A1A), 0f)
                for (index in 0 until 3) {
                    words(left.left + 5, left.top + 10 + index * 8, 20f, Color(0xFF333333))
                    words(right.left + 5, right.top + 10 + index * 8, 20f, Color(0xFFD9D9D9))
                }
                stroke(right, lit, 1.6f, radius = 0f)
                stroke(left.union(right), frame, radius = 0f)
            }
            "motion-safe:", "motion-reduce:" -> {
                val safe = name == "motion-safe:"
                for (index in 0 until 5) {
                    val x = 18f + index * 12
                    if (safe || index == 4) fill(rect(x - 4, 26f, 8f, 8f), lit.copy(alpha = if (safe) 0.2f + 0.2f * index else 1f), 4f)
                }
                if (!safe) line(Offset(18f, 30f), Offset(60f, 30f), faint, 1f, floatArrayOf(2f, 3f))
            }
            else -> button(pill, lit)
        }
    }

    private fun overflow(name: String) {
        if (name.startsWith("z-") || name.startsWith("-z-")) {
            val z = if (name == "z-auto") 0.0 else TailwindSpecimen.number(name) * if (name.startsWith("-")) -1 else 1
            val a = rect(14f, 8f, 30f, 24f)
            val b = rect(44f, 8f, 30f, 24f)
            val mine = rect(29f, 20f, 30f, 30f)
            val draw = { box: Rect, colour: Color ->
                fill(box, inks.card, 2f)
                fill(box, colour, 2f)
                stroke(box, inks.card, 1.2f, radius = 2f)
            }
            if (z < 0) {
                draw(mine, lit)
                val sheet = rect(8f, 14f, 72f, 34f)
                fill(sheet, well.copy(alpha = 0.9f), 2f)
                stroke(sheet, frame, radius = 2f)
                draw(a, dim)
                draw(b, dim)
                return
            }
            // ties: later element wins
            val planes = listOf(Triple(10.0, a, dim), Triple(30.0, b, dim), Triple(z, mine, lit))
            planes.withIndex().sortedWith(compareBy({ it.value.first }, { it.index })).forEach { draw(it.value.second, it.value.third) }
            return
        }
        val visible = name == "overflow-visible"
        val horizontal = "-x-" in name
        val box = rect(18f, 8f, 52f, if (visible) 30f else 44f)
        container(box)
        val lines = {
            for (index in 0 until 6) words(box.left + 5, box.top + 6 + index * 8, if (horizontal) 70f else 38f, if (index < 3 || visible) lit else dim)
        }
        if (visible) lines() else scope.clipRect(box.left, box.top, box.right, box.bottom) { lines() }
        if (name.endsWith("auto") || name == "overflow-scroll") {
            if (name == "overflow-scroll") fill(rect(box.right - 5, box.top + 2, 3f, box.height - 4), well.copy(alpha = 0.6f), 1.5f)
            if (horizontal) {
                fill(rect(box.left + 3, box.bottom - 4, 22f, 2.5f), ink.copy(alpha = 0.5f), 1.2f)
            } else {
                fill(rect(box.right - 5, box.top + 3, 3f, 16f), ink.copy(alpha = 0.5f), 1.5f)
            }
        }
    }

    private fun arbitrary(name: String) {
        when (name) {
            "text-[#1da1f2]" -> text("Aa", 30f, FontWeight.SemiBold, colour = Color(0xFF1DA1F2), centre = Offset(44f, 30f))
            "grid-cols-[1fr_2fr]" -> {
                container()
                for (band in row(c.inset(3f, 3f), 2, 2f, vertical = true)) {
                    val first = (band.width - 2) / 3
                    fill(rect(band.left, band.top, first, band.height), dim)
                    fill(rect(band.left + first + 2, band.top, band.width - first - 2, band.height), lit)
                }
            }
            else -> {
                container()
                val inner = c.inset(4f, 0f)
                fill(rect(inner.left, c.center.y - 8, inner.width * 200 / 256, 16f), lit)
            }
        }
    }
}

private fun spinArc(scope: DrawScope, colour: Color, degrees: Float) {
    val centre = Offset(44f, 30f)
    scope.rotate(degrees, centre) {
        scope.drawArc(colour, -90f, 306f, false, Offset(28f, 14f), Size(32f, 32f), style = Stroke(3f, cap = StrokeCap.Round))
    }
}

private fun turnedBox(scope: DrawScope, motion: TailwindMotion.Transform, share: Float, lit: Color, mark: Color) {
    scope.withTransform({
        translate(motion.anchor.x, motion.anchor.y)
        transform(motion.to.matrix(share))
        translate(-motion.anchor.x, -motion.anchor.y)
    }) {
        val box = motion.box
        drawRoundRect(lit.copy(alpha = 0.9f), box.topLeft, box.size, CornerRadius(1.2f))
        val corner = TailwindSpecimen.corner(box)
        drawRoundRect(mark, corner.topLeft, corner.size, CornerRadius(1.3f))
    }
}

/** Keyframe value at [t] (0 to 1): [values] at [times], each segment eased by its [easings] entry. */
internal fun keyframe(t: Float, times: List<Float>, values: List<Float>, easings: List<(Float) -> Float>): Float {
    for (index in 0 until times.size - 1) {
        if (t <= times[index + 1] || index == times.size - 2) {
            val span = times[index + 1] - times[index]
            val local = if (span <= 0f) 1f else ((t - times[index]) / span).coerceIn(0f, 1f)
            return values[index] + (values[index + 1] - values[index]) * easings.getOrElse(index) { { x: Float -> x } }(local)
        }
    }
    return values.last()
}

/** dt-ios TailwindStageSpecimen's animation layer: what [motion] moves, [millis] into its loop, fitted in [rect]. */
fun DrawScope.drawTailwindMotion(motion: TailwindMotion, rect: Rect, inks: TailwindInks, millis: Long) {
    val (origin, scale) = TailwindSpecimen.fit(rect)
    val lit = inks.primary
    val linear = { x: Float -> x }
    fun bezier(points: List<Float>): (Float) -> Float {
        val easing = androidx.compose.animation.core.CubicBezierEasing(points[0], points[1], points[2], points[3])
        return easing::transform
    }
    fun loop(seconds: Double) = ((millis % (seconds * 1000).toLong()) / (seconds * 1000)).toFloat()
    val centre = Offset(44f, 30f)
    translate(origin.x, origin.y) {
        scale(scale, scale, Offset.Zero) {
            when (motion) {
                is TailwindMotion.Transform -> {
                    val share = keyframe(
                        loop(2.8),
                        listOf(0f, 0.2f, 0.46f, 0.84f, 1f),
                        listOf(0f, 0f, 1f, 1f, 0f),
                        listOf(linear, bezier(listOf(0.2f, 0.9f, 0.3f, 1.1f)), linear, bezier(listOf(0.42f, 0f, 0.58f, 1f))),
                    )
                    turnedBox(this, motion, share, lit, inks.primaryForeground)
                }
                TailwindMotion.Spin -> spinArc(this, lit, loop(1.0) * 360)
                TailwindMotion.Ping -> {
                    drawCircle(lit, 6f, centre)
                    val t = bezier(listOf(0f, 0f, 0.2f, 1f))(loop(1.0))
                    val grow = keyframe(t, listOf(0f, 0.75f, 1f), listOf(1f, 2f, 2f), listOf(linear, linear))
                    val fade = keyframe(t, listOf(0f, 0.75f, 1f), listOf(1f, 0f, 0f), listOf(linear, linear))
                    drawCircle(lit.copy(alpha = fade), 6f * grow, centre)
                }
                TailwindMotion.Pulse -> {
                    val alpha = keyframe(loop(2.0), listOf(0f, 0.5f, 1f), listOf(1f, 0.5f, 1f), List(2) { bezier(listOf(0.4f, 0f, 0.6f, 1f)) })
                    drawRoundRect(lit.copy(alpha = alpha), Offset(24f, 16f), Size(40f, 28f), CornerRadius(3f))
                }
                TailwindMotion.Bounce -> {
                    val dy = keyframe(loop(1.0), listOf(0f, 0.5f, 1f), listOf(-26f, 0f, -26f), listOf(bezier(listOf(0.8f, 0f, 1f, 1f)), bezier(listOf(0f, 0f, 0.2f, 1f))))
                    drawCircle(lit, 6f, Offset(centre.x, 44f + dy))
                }
                is TailwindMotion.Timing -> {
                    val from = TailwindSpecimen.trackFrom
                    val distance = TailwindSpecimen.trackTo.x - from.x
                    val period = max(1.2, motion.delay + motion.duration + 0.9)
                    val dx = keyframe(
                        loop(period),
                        listOf(0f, (motion.delay / period).toFloat(), ((motion.delay + motion.duration) / period).toFloat(), 1f),
                        listOf(0f, 0f, distance, distance),
                        listOf(linear, bezier(motion.curve), linear),
                    )
                    drawCircle(lit, 3f, Offset(from.x + dx, from.y))
                }
                is TailwindMotion.Curve -> {
                    val plot = TailwindSpecimen.plot
                    val period = 1.8f
                    val t = loop(period.toDouble())
                    val times = listOf(0f, 1 / period, 1f)
                    val dx = keyframe(t, times, listOf(0f, plot.width, plot.width), listOf(linear, linear))
                    val dy = keyframe(t, times, listOf(0f, -plot.height, -plot.height), listOf(bezier(motion.curve), linear))
                    drawCircle(lit, 3.5f, Offset(plot.left + dx, plot.bottom + dy))
                }
            }
        }
    }
}
