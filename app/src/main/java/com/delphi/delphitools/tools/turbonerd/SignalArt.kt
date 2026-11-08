package com.delphi.delphitools.tools.turbonerd

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** International maritime signal flags and semaphore. */
object SignalArt {
    private val red = Color(0.80f, 0.169f, 0.216f)
    private val blue = Color(0.122f, 0.227f, 0.576f)
    private val yellow = Color(0.961f, 0.788f, 0.157f)
    private val black = Color(0.082f, 0.082f, 0.082f)
    private val white = Color.White

    /** Flag size in flag units; width over height is the drawn aspect. */
    private const val W = 120f
    private const val H = 90f

    // swallowtail bite, flag units
    private const val NOTCH = 25f

    fun hasFlag(letter: Char) = letter in 'A'..'Z'

    private fun polygon(vararg points: Pair<Float, Float>) = Path().apply {
        moveTo(points[0].first, points[0].second)
        for (point in points.drop(1)) lineTo(point.first, point.second)
        close()
    }

    /** The flag's outline in flag units: A and B are swallowtailed. */
    private fun outline(letter: Char) = if (letter == 'A' || letter == 'B') {
        polygon(0f to 0f, W to 0f, W - NOTCH to H / 2, W to H, 0f to H)
    } else {
        polygon(0f to 0f, W to 0f, W to H, 0f to H)
    }

    /** Draws a letter's flag into [rect], outlined in [stroke]. */
    fun DrawScope.drawFlag(letter: Char, rect: Rect, stroke: Color) {
        val path = outline(letter)
        withTransform({
            translate(rect.left, rect.top)
            scale(rect.width / W, rect.height / H, Offset.Zero)
        }) {
            clipPath(path) { paint(letter) }
            drawPath(path, stroke, style = Stroke(2 * W / max(rect.width, 1f)))
        }
    }

    private fun DrawScope.paint(letter: Char) {
        val w = W
        val h = H
        val n = NOTCH
        fun rect(x: Float, y: Float, width: Float, height: Float, colour: Color) = drawRect(colour, Offset(x, y), Size(width, height))
        fun poly(colour: Color, vararg points: Pair<Float, Float>) = drawPath(polygon(*points), colour)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, colour: Color, width: Float) =
            drawLine(colour, Offset(x1, y1), Offset(x2, y2), width)
        fun cross(colour: Color, width: Float) {
            line(w / 2, 0f, w / 2, h, colour, width)
            line(0f, h / 2, w, h / 2, colour, width)
        }
        fun saltire(colour: Color, width: Float) {
            line(0f, 0f, w, h, colour, width)
            line(w, 0f, 0f, h, colour, width)
        }
        fun checker(columns: Int, rows: Int, a: Color, b: Color) {
            val cw = w / columns
            val ch = h / rows
            for (r in 0 until rows) for (c in 0 until columns) rect(c * cw, r * ch, cw, ch, if ((r + c) % 2 == 1) b else a)
        }

        when (letter) {
            'A' -> {
                rect(0f, 0f, w / 2, h, white)
                poly(blue, w / 2 to 0f, w to 0f, w - n to h / 2, w to h, w / 2 to h)
            }
            'B' -> poly(red, 0f to 0f, w to 0f, w - n to h / 2, w to h, 0f to h)
            'C' -> {
                rect(0f, 0f, w, h, blue)
                rect(0f, h * 0.2f, w, h * 0.6f, white)
                rect(0f, h * 0.4f, w, h * 0.2f, red)
            }
            'D' -> {
                rect(0f, 0f, w, h, yellow)
                rect(0f, h / 4, w, h / 2, blue)
            }
            'E' -> {
                rect(0f, 0f, w, h / 2, blue)
                rect(0f, h / 2, w, h / 2, red)
            }
            'F' -> {
                rect(0f, 0f, w, h, white)
                poly(red, w / 2 to 0f, w to h / 2, w / 2 to h, 0f to h / 2)
            }
            'G' -> for (i in 0 until 6) rect(i * w / 6, 0f, w / 6, h, if (i % 2 == 1) blue else yellow)
            'H' -> {
                rect(0f, 0f, w / 2, h, white)
                rect(w / 2, 0f, w / 2, h, red)
            }
            'I' -> {
                rect(0f, 0f, w, h, yellow)
                drawCircle(black, 21f, Offset(w / 2, h / 2))
            }
            'J' -> {
                rect(0f, 0f, w, h / 3, blue)
                rect(0f, h / 3, w, h / 3, white)
                rect(0f, 2 * h / 3, w, h / 3, blue)
            }
            'K' -> {
                rect(0f, 0f, w / 2, h, yellow)
                rect(w / 2, 0f, w / 2, h, blue)
            }
            'L' -> checker(2, 2, yellow, black)
            'M' -> {
                rect(0f, 0f, w, h, blue)
                saltire(white, 18f)
            }
            'N' -> checker(4, 4, blue, white)
            'O' -> {
                poly(yellow, 0f to 0f, w to h, 0f to h)
                poly(red, 0f to 0f, w to 0f, w to h)
            }
            'P' -> {
                rect(0f, 0f, w, h, blue)
                rect(w / 4, h / 4, w / 2, h / 2, white)
            }
            'Q' -> rect(0f, 0f, w, h, yellow)
            'R' -> {
                rect(0f, 0f, w, h, red)
                cross(yellow, 18f)
            }
            'S' -> {
                rect(0f, 0f, w, h, white)
                rect(w / 4, h / 4, w / 2, h / 2, blue)
            }
            'T' -> {
                rect(0f, 0f, w / 3, h, red)
                rect(w / 3, 0f, w / 3, h, white)
                rect(2 * w / 3, 0f, w / 3, h, blue)
            }
            'U' -> checker(2, 2, red, white)
            'V' -> {
                rect(0f, 0f, w, h, white)
                saltire(red, 18f)
            }
            'W' -> {
                rect(0f, 0f, w, h, blue)
                rect(w / 6, h / 6, 2 * w / 3, 2 * h / 3, white)
                rect(w / 3, h / 3, w / 3, h / 3, red)
            }
            'X' -> {
                rect(0f, 0f, w, h, white)
                cross(blue, 18f)
            }
            'Y' -> rotate(-Math.toDegrees(atan2(h, w).toDouble()).toFloat(), Offset(w / 2, h / 2)) {
                for (i in -2 until 12) rect(-40f, i * 15f, 200f, 15f, if (Math.floorMod(i, 2) == 1) red else yellow)
            }
            'Z' -> {
                poly(yellow, 0f to 0f, w to 0f, w / 2 to h / 2)
                poly(black, 0f to 0f, w / 2 to h / 2, 0f to h)
                poly(red, w to 0f, w to h, w / 2 to h / 2)
                poly(blue, 0f to h, w / 2 to h / 2, w to h)
            }
        }
    }

    /** Semaphore arm positions, angles facing the receiver. */
    private val semaphore = mapOf(
        'A' to (45 to 0), 'B' to (90 to 0), 'C' to (135 to 0), 'D' to (180 to 0),
        'E' to (0 to 225), 'F' to (0 to 270), 'G' to (0 to 315), 'H' to (45 to 90),
        'I' to (45 to 135), 'J' to (180 to 270), 'K' to (45 to 180), 'L' to (45 to 225),
        'M' to (45 to 270), 'N' to (45 to 315), 'O' to (90 to 135), 'P' to (90 to 180),
        'Q' to (90 to 225), 'R' to (90 to 270), 'S' to (90 to 315), 'T' to (135 to 180),
        'U' to (135 to 225), 'V' to (180 to 315), 'W' to (225 to 270), 'X' to (225 to 315),
        'Y' to (135 to 270), 'Z' to (270 to 315),
    )

    /** A circle with the two arms, in a square [rect]. */
    fun DrawScope.drawSemaphore(letter: Char, rect: Rect, colour: Color) {
        val (first, second) = semaphore[letter] ?: return
        val scale = rect.width / 48
        val centre = rect.center
        drawCircle(colour, rect.width / 2 - 3 * scale, centre, style = Stroke(1.5f * scale))
        for (degrees in listOf(first, second)) {
            val radians = Math.toRadians(degrees.toDouble())
            val end = Offset(centre.x - sin(radians).toFloat() * 20 * scale, centre.y + cos(radians).toFloat() * 20 * scale)
            drawLine(colour, centre, end, 2 * scale, StrokeCap.Round)
        }
    }
}
