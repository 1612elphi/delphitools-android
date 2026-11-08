package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.fromHsl
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.hsb
import com.delphi.delphitools.colour.hsl
import com.delphi.delphitools.colour.lab
import com.delphi.delphitools.colour.luminance
import com.delphi.delphitools.colour.oklab
import com.delphi.delphitools.colour.oklabToLinear
import com.delphi.delphitools.colour.oklch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** dt-ios ColourAtlasEngine. */
object ColourAtlasEngine {
    data class Notation(val label: String, val value: String)

    private fun f(format: String, vararg values: Double) = String.format(Locale.ROOT, format, *values.toTypedArray())

    private fun bytes(colour: Rgb) = colour.clamped.let { intArrayOf((it.r * 255).roundToInt(), (it.g * 255).roundToInt(), (it.b * 255).roundToInt()) }

    fun notations(colour: Rgb): List<Notation> {
        val (r, g, b) = bytes(colour).toList()
        val hsl = colour.hsl()
        val hsv = colour.hsb()
        val lab = colour.lab()
        val oklab = colour.oklab()
        val oklch = colour.oklch()
        val (y, cb, cr) = ycbcr(colour).toList()
        return listOf(
            Notation("HEX", colour.hex),
            Notation("RGB", "rgb($r, $g, $b)"),
            Notation("Decimal RGB", f("rgb(%.4f, %.4f, %.4f)", r / 255.0, g / 255.0, b / 255.0)),
            Notation("HSL", f("hsl(%.1f, %.1f%%, %.1f%%)", hsl[0], hsl[1], hsl[2])),
            Notation("HSV", f("hsv(%.1f, %.1f%%, %.1f%%)", hsv[0], hsv[1] * 100, hsv[2] * 100)),
            Notation("LAB", f("lab(%.2f %.2f %.2f)", lab[0], lab[1], lab[2])),
            Notation("OKLAB", f("oklab(%.4f %.4f %.4f)", oklab[0], oklab[1], oklab[2])),
            Notation("OKLCH", f("oklch(%.4f %.4f %.1f)", oklch[0], oklch[1], oklch[2])),
            Notation("YCbCr", "ycbcr($y, $cb, $cr)"),
            Notation("Luminance", f("%.3f", colour.luminance)),
        )
    }

    /** BT.601 studio range, from the 8-bit channels. */
    fun ycbcr(colour: Rgb): IntArray {
        val (r, g, b) = bytes(colour).map { it.toDouble() }
        return intArrayOf(
            (16 + (65.481 * r + 128.553 * g + 24.966 * b) / 255).roundToInt(),
            (128 + (-37.797 * r - 74.203 * g + 112.0 * b) / 255).roundToInt(),
            (128 + (112.0 * r - 93.786 * g - 18.214 * b) / 255).roundToInt(),
        )
    }

    /** The largest OKLCH chroma inside sRGB at lightness `l` and hue `h`, by bisection. */
    fun maxChroma(l: Double, h: Double): Double {
        val angle = Math.toRadians(h)
        var lo = 0.0
        var hi = 0.5
        repeat(24) {
            val mid = (lo + hi) / 2
            val inside = oklabToLinear(l, mid * cos(angle), mid * sin(angle)).all { it >= -1e-5 && it <= 1 + 1e-5 }
            if (inside) lo = mid else hi = mid
        }
        return lo
    }

    /** The base colour followed by `steps` mixes towards `target` (0 or 1). */
    fun scale(colour: Rgb, target: Double, steps: Int = 8): List<Rgb> = listOf(colour) + (1..steps).map { i ->
        val t = i.toDouble() / (steps + 1)
        Rgb(colour.r + (target - colour.r) * t, colour.g + (target - colour.g) * t, colour.b + (target - colour.b) * t)
    }

    /** Hue rotations in HSL, as the web atlas does them. */
    fun harmony(colour: Rgb, offsets: List<Double>): List<Rgb> {
        val (h, s, l) = colour.hsl().toList()
        return offsets.map { fromHsl(((h + it) % 360 + 360) % 360, s, l) }
    }
}
