package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.clamp01
import com.delphi.delphitools.colour.fromHsl
import com.delphi.delphitools.colour.fromLch
import com.delphi.delphitools.colour.fromOklch
import com.delphi.delphitools.colour.hsl
import com.delphi.delphitools.colour.lch
import com.delphi.delphitools.colour.oklch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** dt-ios HarmonyGeneratorViewModel.HarmonyMode, in segmented order. */
enum class HarmonyMode(val title: String, val angles: List<Double>) {
    Complementary("Complementary", listOf(0.0, 180.0)),
    Analogous("Analogous", listOf(-30.0, 0.0, 30.0)),
    Triadic("Triadic", listOf(0.0, 120.0, 240.0)),
    SplitComplementary("Split-Comp.", listOf(0.0, 150.0, 210.0)),
    Tetradic("Tetradic", listOf(0.0, 90.0, 180.0, 270.0)),
    Monochromatic("Monochromatic", listOf(0.0)),
    DoubleComplementary("Double-Comp.", listOf(0.0, 60.0, 180.0, 240.0)),
    Compound("Compound", listOf(0.0, 30.0, 180.0, 210.0)),
    Pentadic("Pentadic", listOf(0.0, 72.0, 144.0, 216.0, 288.0)),
    AnalogousAccent("Analogous Accent", listOf(-30.0, 0.0, 30.0, 180.0)),
    GoldenRatio("Golden Ratio", listOf(0.0, 137.5, 275.0)),
    NearComplementary("Near-Comp.", listOf(0.0, 165.0)),
}

/** Degrees in 0..<360. */
fun wrappedDegrees(angle: Double): Double {
    val value = angle % 360
    return if (value < 0) value + 360 else value
}

/**
 * A colour space the harmony wheel turns in: hue around, chroma or saturation out, lightness as the plane.
 * `l` and `r` are 0...1 of the space's own lightness and of [reach]; hue is in degrees.
 */
enum class HarmonyWheelSpace(val title: String, val reach: Double) {
    // srgb blue reaches lch chroma 134
    OKLCH("OKLCH", 0.33), LCH("LCH", 134.0), HSL("HSL", 100.0);

    val lightnessScale get() = if (this == OKLCH) 1.0 else 100.0

    fun colour(l: Double, r: Double, h: Double): Rgb = when (this) {
        OKLCH -> fromOklch(l, r * reach, h)
        LCH -> fromLch(l * 100, r * reach, h)
        HSL -> fromHsl(h, clamp01(r) * 100, clamp01(l) * 100)
    }

    /** Lightness, radius and hue of `colour`. */
    fun parts(colour: Rgb): DoubleArray = when (this) {
        OKLCH -> colour.oklch().let { doubleArrayOf(it[0], it[1] / reach, it[2]) }
        LCH -> colour.lch().let { doubleArrayOf(it[0] / 100, it[1] / reach, it[2]) }
        HSL -> colour.hsl().let { doubleArrayOf(it[2] / 100, it[1] / 100, it[0]) }
    }

    /** The largest `r` inside sRGB at lightness `l` and hue `h`. */
    fun maxRadius(l: Double, h: Double): Double = when (this) {
        OKLCH -> ColourAtlasEngine.maxChroma(l, h) / reach
        HSL -> 1.0
        LCH -> {
            var lo = 0.0
            var hi = 1.0
            repeat(22) {
                val mid = (lo + hi) / 2
                val c = colour(l, mid, h)
                // lost chroma means out-of-gamut
                val inside = c.r <= 1 + 1e-5 && c.g <= 1 + 1e-5 && c.b <= 1 + 1e-5 && abs(c.lch()[1] - mid * reach) < 0.5
                if (inside) lo = mid else hi = mid
            }
            lo
        }
    }

    /** The hue ring's colour at `h`: one lightness and chroma for every hue, inside the gamut. */
    fun ringColour(h: Double): Rgb {
        val (l, r) = when (this) {
            OKLCH -> 0.74 to 0.12 / 0.33
            LCH -> 0.74 to 48.0 / 134
            HSL -> 0.72 to 0.7
        }
        return colour(l, min(r, maxRadius(l, h)), h)
    }

    /** Offsets turn the hue; monochromatic steps the lightness and softens chroma above 0.6. */
    fun harmony(l: Double, r: Double, h: Double, mode: HarmonyMode): List<Rgb> {
        if (mode == HarmonyMode.Monochromatic) {
            return listOf(0.85, 0.70, l, 0.40, 0.25).map { step -> colour(step, if (step > 0.6) r * 0.7 else r, h) }
        }
        return mode.angles.map { colour(l, r, wrappedDegrees(h + it)) }
    }

    /** The harmony of `base`, its chroma cut to the gamut. */
    fun harmony(base: Rgb, mode: HarmonyMode): List<Rgb> {
        val (l, r, h) = parts(base).toList()
        return harmony(l, min(r, maxRadius(l, h)), h, mode)
    }
}

/** A damped spring, stepped at most 1/240 s at a time. */
class HarmonySpring(val response: Double, val damping: Double) {
    /** Returns the new value and velocity. */
    fun step(value: Double, velocity: Double, target: Double, dt: Double, reduceMotion: Boolean = false): Pair<Double, Double> {
        val w = 2 * PI / response
        val zeta = if (reduceMotion) max(damping, 1.0) else damping
        var v = value
        var speed = velocity
        var left = dt
        while (left > 0) {
            val h = min(left, 1.0 / 240)
            speed += (-(w * w) * (v - target) - 2 * zeta * w * speed) * h
            v += speed * h
            left -= h
        }
        return v to speed
    }
}

/** dt-ios HarmonyTurntable's constants and geometry. */
object HarmonyTable {
    /** Degrees between notches. */
    const val NOTCH = 15.0
    const val FRICTION = 2.4
    const val COAST_FROM = 140.0
    const val CATCH_BELOW = 90.0
    const val FASTEST = 1500.0
    val catchSpring = HarmonySpring(0.36, 0.5)

    /** Partners trailing a turn. */
    val trail = HarmonySpring(0.13, 0.72)

    /** A rule change, and a value set from outside. */
    val slide = HarmonySpring(0.5, 0.68)
    val liftSpring = HarmonySpring(0.28, 0.45)

    /** Seconds between beads starting a rule change. */
    const val STAGGER = 0.07

    /** Plane radius as a share of the ring's outer radius. */
    const val PLANE_SHARE = 0.86
    const val RENDER_SIZE = 600
    const val DRAFT_SIZE = 200

    /** The notch a spin at `speed` is caught by: the next one ahead. */
    fun catchNotch(angle: Double, speed: Double) = (if (speed >= 0) ceil(angle / NOTCH) else floor(angle / NOTCH)) * NOTCH

    fun notchIndex(angle: Double) = floor(angle / NOTCH).toInt()

    /** Each partner's offset and ladder rung, in the model's harmony order without the base, and the base's index. */
    fun slots(mode: HarmonyMode): Pair<List<Pair<Double, Double>>, Int> {
        // lighter steps outward
        if (mode == HarmonyMode.Monochromatic) return listOf(0.0 to 2.0, 0.0 to 1.0, 0.0 to -1.0, 0.0 to -2.0) to 2
        val baseIndex = mode.angles.indexOf(0.0).coerceAtLeast(0)
        return mode.angles.filterIndexed { index, _ -> index != baseIndex }.map { it to 0.0 } to baseIndex
    }

    /** Ladder beads stand `spacing` apart along the base's spoke from -2 to 2 steps, moved as one to stay on the plane. */
    fun ladder(base: Double, rung: Double, spacing: Double): Double {
        val low = base - 2 * spacing
        val high = base + 2 * spacing
        var shift = 0.0
        if (low < spacing * 0.8) shift = spacing * 0.8 - low
        if (high + shift > 0.96) shift = 0.96 - high
        return base + rung * spacing + shift
    }

    /** Hue in degrees clockwise from the top to a point `distance` from `centre`, y down. */
    fun point(hue: Double, distance: Double, cx: Double, cy: Double): Pair<Double, Double> {
        val radians = Math.toRadians(hue)
        return (cx + distance * kotlin.math.sin(radians)) to (cy - distance * kotlin.math.cos(radians))
    }

    /** The disc as ARGB pixels: the plane at `lightness` inside the sRGB gamut (clear outside it) and a hue ring round it. */
    fun plane(space: HarmonyWheelSpace, lightness: Double, size: Int): IntArray {
        val bins = 720
        val most = DoubleArray(bins) { space.maxRadius(lightness, it * 360.0 / bins) }
        val ring = Array(bins) { space.ringColour(it * 360.0 / bins) }
        val pixels = IntArray(size * size)
        val half = size / 2.0
        val share = PLANE_SHARE
        val ringFrom = share + 0.012
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x + 0.5 - half
                val dy = y + 0.5 - half
                val r = sqrt(dx * dx + dy * dy) / half
                if (r > 1) continue
                val hue = wrappedDegrees(Math.toDegrees(atan2(dx, -dy)))
                val bin = min((hue / 360 * bins).toInt(), bins - 1)
                val colour: Rgb
                val alpha: Double
                if (r <= share) {
                    val radius = r / share
                    // one pixel: soft edge
                    val pixel = 1 / (share * half)
                    val inside = minOf(1.0, (most[bin] - radius) / pixel + 0.5, (share - r) * half + 0.5)
                    if (inside <= 0) continue
                    colour = space.colour(lightness, min(radius, most[bin]), hue).clamped
                    alpha = inside
                } else if (r >= ringFrom) {
                    colour = ring[bin].clamped
                    // soft outer edge
                    alpha = min(1.0, (1 - r) * half)
                } else {
                    continue
                }
                // unpremultiplied, as Bitmap.setPixels takes
                pixels[y * size + x] = ((alpha * 255).roundToInt() shl 24) or
                    ((colour.r * 255).roundToInt() shl 16) or ((colour.g * 255).roundToInt() shl 8) or (colour.b * 255).roundToInt()
            }
        }
        return pixels
    }
}
