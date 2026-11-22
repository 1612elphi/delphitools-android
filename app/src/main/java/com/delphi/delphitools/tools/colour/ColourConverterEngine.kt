package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.SolidSpace
import com.delphi.delphitools.colour.clamp01
import com.delphi.delphitools.colour.cmyk
import com.delphi.delphitools.colour.fromCmyk
import com.delphi.delphitools.colour.fromHsl
import com.delphi.delphitools.colour.hsb
import com.delphi.delphitools.colour.hsl
import com.delphi.delphitools.colour.oklabToLinear
import com.delphi.delphitools.colour.parseColour
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private typealias Matrix = Array<DoubleArray>

private fun Matrix.times(v: DoubleArray) = DoubleArray(3) { this[it][0] * v[0] + this[it][1] * v[1] + this[it][2] * v[2] }

private fun Matrix.inverse(): Matrix {
    val (a, b, c) = this[0].toList()
    val (d, e, f) = this[1].toList()
    val (g, h, i) = this[2].toList()
    val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
    return arrayOf(
        doubleArrayOf((e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det),
        doubleArrayOf((f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det),
        doubleArrayOf((d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det),
    )
}

/**
 * dt-ios ColourManagement without its ICC CMYK: RGB numbers belong to a working space; Lab, LCH, OKLab and OKLCH are
 * computed from its primaries and D65 white. An [Rgb] here holds working-space numbers, unclamped.
 */
data class ColourManagement(val working: Working = Working.SRGB) {
    enum class Working(val key: String, val title: String, internal val toXyz: Matrix) {
        // d65: iec/smpte/adobe rgb specs
        SRGB(
            "sRGB", "sRGB",
            arrayOf(doubleArrayOf(0.4124564, 0.3575761, 0.1804375), doubleArrayOf(0.2126729, 0.7151522, 0.0721750), doubleArrayOf(0.0193339, 0.1191920, 0.9503041)),
        ),
        DISPLAY_P3(
            "displayP3", "Display P3",
            arrayOf(doubleArrayOf(0.4865709486, 0.2656676932, 0.1982172852), doubleArrayOf(0.2289745641, 0.6917385218, 0.0792869141), doubleArrayOf(0.0, 0.0451133819, 1.0439443689)),
        ),
        ADOBE_RGB(
            "adobeRGB", "Adobe RGB (1998)",
            arrayOf(doubleArrayOf(0.5766690429, 0.1855582379, 0.1882286462), doubleArrayOf(0.2973449753, 0.6273635663, 0.0752914585), doubleArrayOf(0.0270313614, 0.0706888525, 0.9913375368)),
        );

        internal val fromXyz = toXyz.inverse()

        internal fun linear(v: Double): Double {
            val m = abs(v)
            val result = if (this == ADOBE_RGB) m.pow(563.0 / 256) else if (m <= 0.04045) m / 12.92 else ((m + 0.055) / 1.055).pow(2.4)
            return if (v < 0) -result else result
        }

        internal fun encoded(v: Double): Double {
            val m = abs(v)
            val result = if (this == ADOBE_RGB) m.pow(256.0 / 563) else if (m <= 0.0031308) m * 12.92 else 1.055 * m.pow(1 / 2.4) - 0.055
            return if (v < 0) -result else result
        }
    }

    fun xyz(colour: Rgb) = working.toXyz.times(doubleArrayOf(working.linear(colour.r), working.linear(colour.g), working.linear(colour.b)))

    fun colour(xyz: DoubleArray): Rgb {
        val l = working.fromXyz.times(xyz)
        return Rgb(working.encoded(l[0]), working.encoded(l[1]), working.encoded(l[2]))
    }

    /** The same colour as sRGB numbers, for views that draw sRGB. */
    fun sRgb(colour: Rgb) = if (working == Working.SRGB) colour else ColourManagement().colour(xyz(colour))

    fun lab(colour: Rgb): DoubleArray {
        val xyz = xyz(colour)
        val f = DoubleArray(3) { labF(xyz[it] / d65[it]) }
        return doubleArrayOf(116 * f[1] - 16, 500 * (f[0] - f[1]), 200 * (f[1] - f[2]))
    }

    fun colourFromLab(lab: DoubleArray): Rgb {
        val fy = (lab[0] + 16) / 116
        val f = doubleArrayOf(lab[1] / 500 + fy, fy, fy - lab[2] / 200)
        return colour(DoubleArray(3) { labFInverse(f[it]) * d65[it] })
    }

    // oklab: ottosson 2020
    fun oklab(colour: Rgb): DoubleArray {
        val lms = okLms.times(xyzToSrgb.times(xyz(colour))).map(::cbrt).toDoubleArray()
        return okLab.times(lms)
    }

    fun colourFromOklab(oklab: DoubleArray) = colour(srgbToXyz.times(oklabToLinear(oklab[0], oklab[1], oklab[2])))

    companion object {
        private val d65 = doubleArrayOf(0.95047, 1.0, 1.08883)
        private val srgbToXyz = Working.SRGB.toXyz
        private val xyzToSrgb = Working.SRGB.fromXyz
        private val okLms = arrayOf(
            doubleArrayOf(0.4122214708, 0.5363325363, 0.0514459929),
            doubleArrayOf(0.2119034982, 0.6806995451, 0.1073969566),
            doubleArrayOf(0.0883024619, 0.2817188376, 0.6299787005),
        )
        private val okLab = arrayOf(
            doubleArrayOf(0.2104542553, 0.7936177850, -0.0040720468),
            doubleArrayOf(1.9779984951, -2.4285922050, 0.4505937099),
            doubleArrayOf(0.0259040371, 0.7827717662, -0.8086757660),
        )
        private const val DELTA = 6.0 / 29

        private fun labF(t: Double) = if (t > DELTA * DELTA * DELTA) cbrt(t) else t / (3 * DELTA * DELTA) + 4.0 / 29

        private fun labFInverse(t: Double) = if (t > DELTA) t * t * t else 3 * DELTA * DELTA * (t - 4.0 / 29)

        /** Inside the working space's gamut, with a little slack for rounding. */
        fun inGamut(colour: Rgb) = listOf(colour.r, colour.g, colour.b).all { it >= -0.002 && it <= 1.002 }

        /** Lightness, chroma, hue in degrees 0 until 360, from a Lab-like triple. */
        fun polar(lab: DoubleArray): DoubleArray {
            var hue = Math.toDegrees(atan2(lab[2], lab[1]))
            if (hue < 0) hue += 360
            return doubleArrayOf(lab[0], hypot(lab[1], lab[2]), hue)
        }

        fun cartesian(lch: DoubleArray) = Math.toRadians(lch[2]).let { doubleArrayOf(lch[0], lch[1] * cos(it), lch[1] * sin(it)) }
    }
}

/**
 * dt-ios ColourConverterNotation: the notations the converter reads and writes, for every space the solid has, with
 * the web's decimals. Channel values run in [SolidSpace.channels] order and units.
 */
// ponytail: CMYK is the naive formula; android has no ICC CMYK conversion, so no press profiles
class ColourConverterNotation(val management: ColourManagement) {
    fun values(colour: Rgb, space: SolidSpace): List<Double> = when (space) {
        SolidSpace.RGB -> listOf(colour.r * 255, colour.g * 255, colour.b * 255)
        SolidSpace.HSL -> colour.hsl().toList()
        SolidSpace.HSB -> colour.hsb().let { listOf(it[0], it[1] * 100, it[2] * 100) }
        SolidSpace.OKLCH -> ColourManagement.polar(management.oklab(colour)).toList()
        SolidSpace.OKLAB -> management.oklab(colour).toList()
        SolidSpace.LCH -> ColourManagement.polar(management.lab(colour)).toList()
        SolidSpace.LAB -> management.lab(colour).toList()
        SolidSpace.CMYK -> colour.cmyk().map { it * 100 }
    }

    fun colour(values: List<Double>, space: SolidSpace): Rgb {
        val v = values.toMutableList()
        if (space == SolidSpace.HSL || space == SolidSpace.HSB) v[0] = wrapped(v[0])
        val triple = doubleArrayOf(v[0], v[1], v[2])
        return when (space) {
            SolidSpace.RGB -> Rgb(v[0] / 255, v[1] / 255, v[2] / 255)
            SolidSpace.HSL -> fromHsl(v[0], v[1], v[2])
            SolidSpace.HSB -> SolidSpace.HSB.colourAt(point(v, SolidSpace.HSB))
            SolidSpace.OKLCH -> management.colourFromOklab(ColourManagement.cartesian(triple))
            SolidSpace.OKLAB -> management.colourFromOklab(triple)
            SolidSpace.LCH -> management.colourFromLab(ColourManagement.cartesian(triple))
            SolidSpace.LAB -> management.colourFromLab(triple)
            SolidSpace.CMYK -> v.map { clamp01(it / 100) }.let { fromCmyk(it[0], it[1], it[2], it[3]) }
        }
    }

    /**
     * Channel values in `space` for a hex, a functional notation (rgb, hsl, hsb or hsv, lab, lch, oklab, oklch, cmyk
     * or device-cmyk; an alpha is ignored), or bare numbers read as `space`'s channels. Bare numbers take a decimal
     * comma when spaces separate them.
     */
    fun parse(text: String, space: SolidSpace): List<Double>? {
        val trimmed = text.trim().lowercase(Locale.ROOT)
        if (trimmed.isEmpty()) return null
        val open = trimmed.indexOf('(')
        if (open >= 0 && trimmed.endsWith(")")) {
            val named = functions[trimmed.substring(0, open).trim()] ?: return null
            val values = channels(trimmed.substring(open + 1, trimmed.length - 1), named, commasSeparate = true) ?: return null
            return if (named == space) values else values(colour(values, named), space)
        }
        val bare = trimmed.any { it == ' ' || it == ',' || it == '/' || it == '%' }
        if (bare) return channels(trimmed, space, commasSeparate = !trimmed.contains(' ') || trimmed.contains(", "))
        return parseColour(trimmed)?.let { values(it, space) }
    }

    companion object {
        private val functions = mapOf(
            "rgb" to SolidSpace.RGB, "rgba" to SolidSpace.RGB, "hsl" to SolidSpace.HSL, "hsla" to SolidSpace.HSL,
            "hsb" to SolidSpace.HSB, "hsv" to SolidSpace.HSB, "lab" to SolidSpace.LAB, "lch" to SolidSpace.LCH,
            "oklab" to SolidSpace.OKLAB, "oklch" to SolidSpace.OKLCH, "cmyk" to SolidSpace.CMYK, "device-cmyk" to SolidSpace.CMYK,
        )

        /** A hue in 0 until 360. */
        fun wrapped(hue: Double) = hue - 360 * floor(hue / 360)

        private fun isHue(min: Double, max: Double) = min == 0.0 && max == 360.0

        /** The solid's point for channel values. */
        fun point(values: List<Double>, space: SolidSpace): DoubleArray {
            val point = DoubleArray(4)
            space.channels.zip(values).forEach { (channel, value) -> point[channel.axis] = channel.pointValue(value) }
            return point
        }

        /** Channel values for a point on the solid. */
        fun values(point: DoubleArray, space: SolidSpace) = space.channels.map { channel ->
            var share = point[channel.axis]
            if (isHue(channel.min, channel.max)) share -= floor(share)
            share * channel.scale + channel.offset
        }

        fun format(values: List<Double>, space: SolidSpace): String = when (space) {
            SolidSpace.RGB -> values.map { it.coerceIn(0.0, 255.0).roundToInt() }.let { "rgb(${it[0]}, ${it[1]}, ${it[2]})" }
            SolidSpace.HSL -> "hsl(${fixed(values[0], 1)}, ${fixed(values[1], 1)}%, ${fixed(values[2], 1)}%)"
            SolidSpace.HSB -> "hsb(${fixed(values[0], 1)}, ${fixed(values[1], 1)}%, ${fixed(values[2], 1)}%)"
            SolidSpace.OKLCH -> "oklch(${fixed(values[0], 4)} ${fixed(values[1], 4)} ${fixed(values[2], 1)})"
            SolidSpace.OKLAB -> "oklab(${fixed(values[0], 4)} ${fixed(values[1], 4)} ${fixed(values[2], 4)})"
            SolidSpace.LCH -> "lch(${fixed(values[0], 2)} ${fixed(values[1], 2)} ${fixed(values[2], 1)})"
            SolidSpace.LAB -> "lab(${fixed(values[0], 2)} ${fixed(values[1], 2)} ${fixed(values[2], 2)})"
            SolidSpace.CMYK -> "cmyk(" + values.joinToString(", ") { fixed(it, 0) + "%" } + ")"
        }

        fun decimalRgb(colour: Rgb): String {
            fun channel(v: Double) = fixed((clamp01(v) * 255).roundToInt() / 255.0, 4)
            return "rgb(${channel(colour.r)}, ${channel(colour.g)}, ${channel(colour.b)})"
        }

        /** Bare numbers as the space's channels, for the field's placeholder. */
        fun example(space: SolidSpace) = when (space) {
            SolidSpace.RGB -> "59 130 246"
            SolidSpace.HSL -> "217 91 60"
            SolidSpace.HSB -> "217 76 96"
            SolidSpace.OKLCH -> "0.62 0.19 260"
            SolidSpace.OKLAB -> "0.62 -0.03 -0.19"
            SolidSpace.LCH -> "54.6 66 277.5"
            SolidSpace.LAB -> "54.6 8.6 -65.4"
            SolidSpace.CMYK -> "76 47 0 4"
        }

        private fun channels(text: String, space: SolidSpace, commasSeparate: Boolean): List<Double>? {
            var cleaned = text.replace("/", " ")
            cleaned = if (commasSeparate) cleaned.replace(",", " ") else cleaned.replace(",", ".")
            val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val channels = space.channels
            // alpha ignored
            if (tokens.size != channels.size && tokens.size != channels.size + 1) return null
            val percents = tokens.take(channels.size).map { it.endsWith("%") }
            val values = tokens.take(channels.size).map { token ->
                token.removeSuffix("%").toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
            }.toMutableList()
            // decimal rgb, fractional ink
            val fractions = values.all { it in 0.0..1.0 } && !percents.contains(true)
            if (space == SolidSpace.RGB && fractions && values.any { it != Math.rint(it) }) values.replaceAll { it * 255 }
            if (space == SolidSpace.CMYK && fractions) values.replaceAll { it * 100 }
            channels.forEachIndexed { index, channel ->
                // css: 64% is 0.64
                if (percents[index] && channel.min == 0.0) values[index] = values[index] / 100 * channel.max
                if (isHue(channel.min, channel.max)) values[index] = wrapped(values[index])
            }
            return values
        }

        /** `decimals` places, without a minus on zero. */
        fun fixed(value: Double, decimals: Int): String {
            val text = String.format(Locale.ROOT, "%.${decimals}f", value)
            return if (text.startsWith("-") && text.drop(1).all { it == '0' || it == '.' }) text.drop(1) else text
        }
    }
}
