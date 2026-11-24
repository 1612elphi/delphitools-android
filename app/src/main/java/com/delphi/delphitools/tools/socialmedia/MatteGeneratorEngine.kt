package com.delphi.delphitools.tools.socialmedia

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class MatteRgb(val r: Int, val g: Int, val b: Int) {
    val argb get() = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** Clamped to 0...255. */
    fun shifted(amount: Int) = MatteRgb((r + amount).coerceIn(0, 255), (g + amount).coerceIn(0, 255), (b + amount).coerceIn(0, 255))
}

data class MatteRect(val x: Double, val y: Double, val width: Double, val height: Double)

/** dt-ios MatteSpec: the web tool's matte, a photo fitted inside a padded canvas of a set ratio. */
data class MatteSpec(
    val style: Style = Style.Blurred,
    val colour: MatteRgb = MatteRgb(255, 255, 255),
    val dominant: MatteRgb = MatteRgb(136, 136, 136),
    val ratioW: Int = 1,
    val ratioH: Int = 1,
    val width: Int = 1080,
    val padding: Int = 40,
) {
    enum class Style { Blurred, Solid, Gradient }

    val height get() = Math.round(width.toDouble() * ratioH / ratioW).toInt()
    val widthLimit get() = if (ratioW >= ratioH) LONGEST_SIDE else LONGEST_SIDE * ratioW / ratioH

    companion object {
        /** Output px, web blur(50px). */
        const val BLUR = 50.0

        /** Hides blur edges. */
        const val OVERSCAN = 1.2

        /** Second gradient stop's channel offset. */
        const val FALLOFF = -30

        // ponytail: long side capped at 8192 (about 150 MB of pixels at 9:16); the web has no cap
        const val LONGEST_SIDE = 8192

        fun fit(width: Double, height: Double, boxWidth: Double, boxHeight: Double, padding: Double): MatteRect {
            if (width <= 0 || height <= 0) return MatteRect(0.0, 0.0, 0.0, 0.0)
            val scale = maxOf(0.0, minOf((boxWidth - padding * 2) / width, (boxHeight - padding * 2) / height))
            return centred(width * scale, height * scale, boxWidth, boxHeight)
        }

        fun cover(width: Double, height: Double, boxWidth: Double, boxHeight: Double, overscan: Double): MatteRect {
            if (width <= 0 || height <= 0) return MatteRect(0.0, 0.0, 0.0, 0.0)
            val scale = maxOf(boxWidth / width, boxHeight / height) * overscan
            return centred(width * scale, height * scale, boxWidth, boxHeight)
        }

        private fun centred(width: Double, height: Double, boxWidth: Double, boxHeight: Double) =
            MatteRect((boxWidth - width) / 2, (boxHeight - height) / 2, width, height)
    }
}

/** The backdrop blur and the dominant colour, on ARGB pixels. */
object MatteEngine {
    private val toLinear = DoubleArray(256) { (it / 255.0).let { v -> if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) } }

    private fun encode(v: Double) = (if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055).coerceIn(0.0, 1.0)

    /** The mean colour, averaged in linear light as Core Image's area average does. */
    fun average(pixels: IntArray): MatteRgb? {
        if (pixels.isEmpty()) return null
        var r = 0.0
        var g = 0.0
        var b = 0.0
        for (p in pixels) {
            r += toLinear[p shr 16 and 0xFF]
            g += toLinear[p shr 8 and 0xFF]
            b += toLinear[p and 0xFF]
        }
        fun byte(sum: Double) = (encode(sum / pixels.size) * 255).roundToInt()
        return MatteRgb(byte(r), byte(g), byte(b))
    }

    /** Box sizes whose three passes approximate a Gaussian of `sigma`. */
    fun boxes(sigma: Double): IntArray {
        val ideal = sqrt(12 * sigma * sigma / 3 + 1)
        var lower = floor(ideal).toInt()
        if (lower % 2 == 0) lower--
        val upper = lower + 2
        val m = Math.round((12 * sigma * sigma - 3 * lower * lower - 12 * lower - 9) / (-4.0 * lower - 4)).toInt()
        return IntArray(3) { if (it < m) lower else upper }
    }

    /** Opaque ARGB pixels blurred by three box passes each way, edges clamped. */
    fun blur(pixels: IntArray, width: Int, height: Int, sigma: Double): IntArray {
        if (sigma <= 0 || width <= 0 || height <= 0) return pixels.copyOf()
        var a = pixels.copyOf()
        var b = IntArray(a.size)
        for (box in boxes(sigma)) {
            val radius = (box - 1) / 2
            pass(a, b, width, height, radius, horizontal = true)
            pass(b, a, width, height, radius, horizontal = false)
        }
        return a
    }

    private fun pass(from: IntArray, to: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val window = radius * 2 + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val c = i.coerceIn(0, length - 1)
                return if (horizontal) line * width + c else c * width + line
            }
            var r = 0
            var g = 0
            var bl = 0
            for (i in -radius..radius) {
                val p = from[at(i)]
                r += p shr 16 and 0xFF
                g += p shr 8 and 0xFF
                bl += p and 0xFF
            }
            for (i in 0 until length) {
                to[at(i)] = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (bl / window)
                val out = from[at(i - radius)]
                val inn = from[at(i + radius + 1)]
                r += (inn shr 16 and 0xFF) - (out shr 16 and 0xFF)
                g += (inn shr 8 and 0xFF) - (out shr 8 and 0xFF)
                bl += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}
