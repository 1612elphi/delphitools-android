package com.delphi.delphitools.colour

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** sRGB, each channel 0...1 and unclamped until it is shown; every conversion goes through sRGB. */
data class Rgb(val r: Double, val g: Double, val b: Double) {
    val clamped get() = Rgb(clamp01(r), clamp01(g), clamp01(b))
    val isFinite get() = r.isFinite() && g.isFinite() && b.isFinite()

    companion object {
        val black = Rgb(0.0, 0.0, 0.0)
        val white = Rgb(1.0, 1.0, 1.0)
    }
}

enum class ColourFormat { HEX, RGB, HSL, LAB, LCH, OKLAB, OKLCH }

fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)

private fun byte(v: Double) = (clamp01(v) * 255).roundToInt()
private fun turn(degrees: Double) = if (degrees < 0) degrees + 360 else degrees

/** `#RRGGBB`, upper case, clamped. */
val Rgb.hex get() = String.format(Locale.ROOT, "#%02X%02X%02X", byte(r), byte(g), byte(b))

fun Rgb.toArgb(): Int = (0xFF shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)

fun rgbOf(argb: Int) = Rgb((argb shr 16 and 0xFF) / 255.0, (argb shr 8 and 0xFF) / 255.0, (argb and 0xFF) / 255.0)

/** Six or three hex digits, with or without `#`. */
fun parseHex(text: String): Rgb? {
    var digits = text.trim().removePrefix("#")
    if (digits.length == 3) digits = digits.map { "$it$it" }.joinToString("")
    if (digits.length != 6 || !digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return rgbOf(digits.toInt(16))
}

/** Hue 0...360, saturation and lightness 0...100. */
fun Rgb.hsl(): DoubleArray {
    val (r, g, b) = clamped
    val top = max(r, max(g, b))
    val bottom = min(r, min(g, b))
    val delta = top - bottom
    val l = (top + bottom) / 2
    if (delta <= 1e-10) return doubleArrayOf(0.0, 0.0, l * 100)
    val s = delta / (1 - abs(2 * l - 1))
    val h = when (top) {
        r -> 60 * (((g - b) / delta) % 6)
        g -> 60 * ((b - r) / delta + 2)
        else -> 60 * ((r - g) / delta + 4)
    }
    return doubleArrayOf(turn(h), s * 100, l * 100)
}

fun fromHsl(h: Double, s: Double, l: Double): Rgb {
    val l1 = l / 100
    val c = (1 - abs(2 * l1 - 1)) * s / 100
    val (r, g, b) = hueRgb(h / 360)
    return Rgb((r - 0.5) * c + l1, (g - 0.5) * c + l1, (b - 0.5) * c + l1)
}

/** Hue 0...360, saturation and brightness 0...1. */
fun Rgb.hsb(): DoubleArray {
    val (r, g, b) = clamped
    val top = max(r, max(g, b))
    return doubleArrayOf(hsl()[0], if (top > 0) (top - min(r, min(g, b))) / top else 0.0, top)
}

/** Hue 0...1 to the fully saturated sRGB colour at that hue. */
fun hueRgb(h: Double): Rgb {
    fun channel(k: Double): Double {
        val wrapped = k - 6 * floor(k / 6)
        return clamp01(abs(wrapped - 3) - 1)
    }
    return Rgb(channel(h * 6), channel(h * 6 + 4), channel(h * 6 + 2))
}

fun fromHsb(h: Double, s: Double, b: Double): Rgb {
    val pure = hueRgb(h / 360)
    return Rgb(b * (1 - s + s * pure.r), b * (1 - s + s * pure.g), b * (1 - s + s * pure.b))
}

private fun toLinear(v: Double) = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

private fun fromLinear(v: Double): Double {
    val c = max(0.0, v)
    return if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
}

private fun Rgb.linear() = clamped.let { doubleArrayOf(toLinear(it.r), toLinear(it.g), toLinear(it.b)) }

private fun encoded(linear: DoubleArray) = Rgb(fromLinear(linear[0]), fromLinear(linear[1]), fromLinear(linear[2]))

private val d65 = doubleArrayOf(0.95047, 1.0, 1.08883)
private const val DELTA = 6.0 / 29
private fun labF(t: Double) = if (t > DELTA * DELTA * DELTA) cbrt(t) else t / (3 * DELTA * DELTA) + 4.0 / 29
private fun labFInverse(t: Double) = if (t > DELTA) t * t * t else 3 * DELTA * DELTA * (t - 4.0 / 29)

/** CIELAB, D65. */
fun Rgb.lab(): DoubleArray {
    val (r, g, b) = linear().toList()
    val x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
    val y = 0.2126729 * r + 0.7151522 * g + 0.072175 * b
    val z = 0.0193339 * r + 0.119192 * g + 0.9503041 * b
    val fx = labF(x / d65[0])
    val fy = labF(y / d65[1])
    val fz = labF(z / d65[2])
    return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
}

/** CIELAB to linear sRGB, unclamped: outside 0...1 is outside the gamut. */
fun labToLinear(l: Double, a: Double, b: Double): DoubleArray {
    val fy = (l + 16) / 116
    val x = d65[0] * labFInverse(a / 500 + fy)
    val y = d65[1] * labFInverse(fy)
    val z = d65[2] * labFInverse(fy - b / 200)
    return doubleArrayOf(
        3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
        -0.969266 * x + 1.8760108 * y + 0.041556 * z,
        0.0556434 * x - 0.2040259 * y + 1.0572252 * z,
    )
}

fun fromLab(l: Double, a: Double, b: Double) = encoded(labToLinear(l, a, b))

private fun polar(lab: DoubleArray) =
    doubleArrayOf(lab[0], hypot(lab[1], lab[2]), turn(Math.toDegrees(atan2(lab[2], lab[1]))))

private fun cartesian(c: Double, h: Double) = Math.toRadians(h).let { doubleArrayOf(c * cos(it), c * sin(it)) }

fun Rgb.lch() = polar(lab())

fun fromLch(l: Double, c: Double, h: Double) = cartesian(c, h).let { fromLab(l, it[0], it[1]) }

fun Rgb.oklab(): DoubleArray {
    val (r, g, b) = linear().toList()
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return doubleArrayOf(
        0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
        1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
        0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
    )
}

/** OKLab to linear sRGB, unclamped: outside 0...1 is outside the gamut. */
fun oklabToLinear(l: Double, a: Double, b: Double): DoubleArray {
    val l3 = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m3 = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s3 = (l - 0.0894841775 * a - 1.291485548 * b).pow(3)
    return doubleArrayOf(
        4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
        -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
        -0.0041960863 * l3 - 0.7034186147 * m3 + 1.707614701 * s3,
    )
}

fun fromOklab(l: Double, a: Double, b: Double) = encoded(oklabToLinear(l, a, b))

fun Rgb.oklch() = polar(oklab())

fun fromOklch(l: Double, c: Double, h: Double) = cartesian(c, h).let { fromOklab(l, it[0], it[1]) }

/** Naive CMYK, each 0...1: black takes the darkest channel, no ink limit or profile. */
fun Rgb.cmyk(): DoubleArray {
    val (r, g, b) = clamped
    val black = 1 - max(r, max(g, b))
    if (black >= 1) return doubleArrayOf(0.0, 0.0, 0.0, 1.0)
    fun ink(v: Double) = (1 - v - black) / (1 - black)
    return doubleArrayOf(ink(r), ink(g), ink(b), black)
}

fun fromCmyk(c: Double, m: Double, y: Double, k: Double) = Rgb((1 - c) * (1 - k), (1 - m) * (1 - k), (1 - y) * (1 - k))

/** WCAG 2.2 relative luminance. */
val Rgb.luminance get() = linear().let { 0.2126 * it[0] + 0.7152 * it[1] + 0.0722 * it[2] }

fun contrastRatio(one: Rgb, two: Rgb): Double {
    val a = one.luminance
    val b = two.luminance
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

/** WCAG 2.2 levels (1.4.3, 1.4.6, 1.4.11). */
object ContrastLevel {
    const val AA_LARGE = 3.0
    const val AA = 4.5
    const val AAA = 7.0
}

/** Not rounded up: 4.499 shows 4.49, which fails 4.5. No trailing zeros. */
fun contrastFigure(ratio: Double): String =
    String.format(Locale.ROOT, "%.2f", floor(ratio * 100 + 1e-9) / 100).trimEnd('0').trimEnd('.')

/** Black type has more contrast on this colour than white. */
val Rgb.takesBlackInk get() = contrastRatio(this, Rgb.black) >= contrastRatio(this, Rgb.white)

/** The colour as CSS-like text, in the decimals DelphiCore uses. */
fun Rgb.formatted(format: ColourFormat): String {
    fun fixed(values: DoubleArray, vararg digits: Int) =
        values.indices.joinToString(" ") { String.format(Locale.ROOT, "%.${digits[it]}f", values[it]) }
    return when (format) {
        ColourFormat.HEX -> hex
        ColourFormat.RGB -> "rgb(${byte(r)}, ${byte(g)}, ${byte(b)})"
        ColourFormat.HSL -> hsl().map { it.roundToInt() }.let { (h, s, l) -> "hsl($h, $s%, $l%)" }
        ColourFormat.LAB -> "lab(${fixed(lab(), 1, 1, 1)})"
        ColourFormat.LCH -> "lch(${fixed(lch(), 1, 1, 1)})"
        ColourFormat.OKLAB -> "oklab(${fixed(oklab(), 4, 4, 4)})"
        ColourFormat.OKLCH -> "oklch(${fixed(oklch(), 4, 4, 1)})"
    }
}

private val notations: Map<String, (Double, Double, Double) -> Rgb> = mapOf(
    "rgb" to { r, g, b -> Rgb(r / 255, g / 255, b / 255) },
    "hsl" to ::fromHsl,
    "lab" to ::fromLab,
    "lch" to ::fromLch,
    "oklab" to ::fromOklab,
    "oklch" to ::fromOklch,
)

private val functional = Regex("^([a-z]+)\\((.*)\\)$")

/**
 * Any notation the app reads: `#RRGGBB`, `RRGGBB`, `#RGB`, and rgb, hsl, lab,
 * lch, oklab and oklch functions with commas or spaces and optional `%`.
 */
fun parseColour(input: String): Rgb? {
    val text = input.trim()
    parseHex(text)?.let { return it }
    val match = functional.find(text.lowercase(Locale.ROOT)) ?: return null
    val convert = notations[match.groupValues[1]] ?: return null
    val parts = match.groupValues[2].replace(Regex("[,%]"), " ").split(' ').filter { it.isNotEmpty() }
        .map { it.toDoubleOrNull() ?: return null }
    if (parts.size != 3 || !parts.all { it.isFinite() }) return null
    return convert(parts[0], parts[1], parts[2]).takeIf { it.isFinite }
}
