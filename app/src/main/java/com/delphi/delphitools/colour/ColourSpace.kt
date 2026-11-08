package com.delphi.delphitools.colour

import kotlin.math.floor

const val OKLAB_REACH = 0.33
const val LAB_REACH = 110.0

/**
 * The spaces of CanonColourSolid, in picker order. A pick's channels are each 0...1
 * (chroma may pass 1). Order: RGB r g b; HSL h l s; Lab and OKLab a L b; CMYK c m y k;
 * the rest hue, radius, height. Only CMYK uses the fourth.
 */
enum class SolidSpace(val title: String) {
    RGB("RGB"), HSL("HSL"), HSB("HSB"), OKLCH("OKLCH"), OKLAB("OKLab"), LCH("LCH"), LAB("Lab"), CMYK("CMYK");

    /** Channels run along the box's edges; the rest are hue, radius and height. */
    val isCartesian get() = this == RGB || this == HSL || this == OKLAB || this == LAB || this == CMYK

    /** The channels in the order a tool lists them. */
    val channels: List<SolidChannel>
        get() = when (this) {
            RGB -> listOf(channel("Red", 255.0, 0), channel("Green", 255.0, 1), channel("Blue", 255.0, 2))
            HSL -> listOf(hue, channel("Saturation", 100.0, 2), channel("Lightness", 100.0, 1))
            HSB -> listOf(hue, channel("Saturation", 100.0, 1), channel("Brightness", 100.0, 2))
            OKLCH -> listOf(channel("Lightness", 1.0, 2, 2), channel("Chroma", OKLAB_REACH, 1, 3), hue)
            OKLAB -> listOf(
                channel("Lightness", 1.0, 1, 2),
                opponent("Component A", "Pink-Green", 0, OKLAB_REACH, 3),
                opponent("Component B", "Blue-Yellow", 2, OKLAB_REACH, 3),
            )
            LAB -> listOf(
                channel("Lightness", 100.0, 1),
                opponent("Component A", "Pink-Green", 0, LAB_REACH, 0),
                opponent("Component B", "Blue-Yellow", 2, LAB_REACH, 0),
            )
            CMYK -> listOf(
                channel("Cyan", 100.0, 0), channel("Magenta", 100.0, 1),
                channel("Yellow", 100.0, 2), channel("Black", 100.0, 3),
            )
            // srgb blue reaches 134
            LCH -> listOf(channel("Lightness", 100.0, 2), channel("Chroma", 134.0, 1, 0, LAB_REACH), hue)
        }

    fun colourAt(p: DoubleArray): Rgb {
        val (x, y, z) = p.toList()
        val h = (x - floor(x)) * 360
        return when (this) {
            CMYK -> fromCmyk(x, y, z, p[3])
            RGB -> Rgb(x, y, z)
            HSL -> fromHsl(h, z * 100, y * 100)
            HSB -> fromHsb(h, y, z)
            OKLCH -> fromOklch(z, y * OKLAB_REACH, h)
            LCH -> fromLch(z * 100, y * LAB_REACH, h)
            OKLAB -> fromOklab(y, (x * 2 - 1) * OKLAB_REACH, (z * 2 - 1) * OKLAB_REACH)
            LAB -> fromLab(y * 100, (x * 2 - 1) * LAB_REACH, (z * 2 - 1) * LAB_REACH)
        }
    }

    fun pointFor(colour: Rgb): DoubleArray = when (this) {
        CMYK -> colour.cmyk()
        RGB -> colour.clamped.let { doubleArrayOf(it.r, it.g, it.b, 0.0) }
        HSL -> colour.hsl().let { (h, s, l) -> doubleArrayOf(h / 360, l / 100, s / 100, 0.0) }
        HSB -> colour.hsb().let { (h, s, b) -> doubleArrayOf(h / 360, s, b, 0.0) }
        OKLCH -> colour.oklch().let { (l, c, h) -> doubleArrayOf(h / 360, c / OKLAB_REACH, l, 0.0) }
        LCH -> colour.lch().let { (l, c, h) -> doubleArrayOf(h / 360, c / LAB_REACH, l / 100, 0.0) }
        OKLAB -> colour.oklab().let { (l, a, b) -> doubleArrayOf((a / OKLAB_REACH + 1) / 2, l, (b / OKLAB_REACH + 1) / 2, 0.0) }
        LAB -> colour.lab().let { (l, a, b) -> doubleArrayOf((a / LAB_REACH + 1) / 2, l / 100, (b / LAB_REACH + 1) / 2, 0.0) }
    }
}

/**
 * One slider of a space: `min...max` in the channel's own units, `axis` its index into the
 * point, `scale` units per 1 of the point, `offset` the value at 0 of the point.
 */
data class SolidChannel(
    val name: String,
    val min: Double,
    val max: Double,
    val axis: Int,
    val scale: Double,
    val decimals: Int,
    val offset: Double,
    val detail: String? = null,
) {
    fun value(point: DoubleArray) = point[axis] * scale + offset
    fun pointValue(value: Double) = (value - offset) / scale
}

private fun channel(name: String, max: Double, axis: Int, decimals: Int = 0, scale: Double = max) =
    SolidChannel(name, 0.0, max, axis, scale, decimals, 0.0)

// a green/pink, b blue/yellow
private fun opponent(name: String, detail: String, axis: Int, reach: Double, decimals: Int) =
    SolidChannel(name, -reach, reach, axis, reach * 2, decimals, -reach, detail)

private val hue = channel("Hue", 360.0, 0)
