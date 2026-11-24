package com.delphi.delphitools.tools.colour

import java.util.Locale
import kotlin.math.roundToInt

/** dt-ios PixelPickerSampler: which pixel a point picks, the loupe's patch around it, and its Display P3 notation. */
object PixelPickerSampler {
    /** 0.1 grey, the loupe cells past the image's edge. */
    const val OUTSIDE = 0xFF1A1A1A.toInt()

    /** The pixel under a normalised coordinate along a side of `size` pixels. */
    fun index(fraction: Double, size: Int) = (fraction * size).toInt().coerceIn(0, size - 1)

    /** The square of pixels around (x, y), row by row, `radius` each side; `pixel` reads an ARGB pixel inside the image. */
    fun loupe(width: Int, height: Int, x: Int, y: Int, radius: Int, pixel: (Int, Int) -> Int): IntArray {
        val side = radius * 2 + 1
        return IntArray(side * side) { cell ->
            val px = x - radius + cell % side
            val py = y - radius + cell / side
            if (px in 0 until width && py in 0 until height) pixel(px, py) else OUTSIDE
        }
    }

    /** CSS `color()` for Display P3 channels 0...1, read to bytes first as iOS does. */
    fun displayP3(r: Float, g: Float, b: Float): String {
        fun byte(v: Float) = (v.coerceIn(0f, 1f) * 255).roundToInt() / 255.0
        return String.format(Locale.ROOT, "color(display-p3 %.4f %.4f %.4f)", byte(r), byte(g), byte(b))
    }
}
