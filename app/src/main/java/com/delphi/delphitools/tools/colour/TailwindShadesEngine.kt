package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.fromOklch
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.oklch
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * dt-ios TailwindShadesEngine: the 50 to 950 scale, as the web tool builds it. Each level keeps a share of the
 * base's saturation (chroma over the most the gamut holds at that lightness and hue), eased over two levels at each end.
 */
object TailwindShadesEngine {
    enum class Mode(val title: String) { Classic("Classic"), HueShift("Hue Shift"), LuminanceAnchored("Luminance"), Vivid("Vivid"), Muted("Muted") }

    val steps = listOf(50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950)
    val targetLightness = listOf(0.97, 0.93, 0.87, 0.78, 0.66, 0.55, 0.47, 0.40, 0.33, 0.27, 0.20)

    /** Share of the base's saturation at the light end, across the middle and at the dark end; above 1 pushes to the gamut edge. */
    fun ramp(mode: Mode): Triple<Double, Double, Double> = when (mode) {
        Mode.Classic, Mode.HueShift -> Triple(0.75, 1.0, 0.85)
        Mode.LuminanceAnchored -> Triple(0.5, 1.0, 0.6)
        Mode.Vivid -> Triple(1.0, 1.35, 1.15)
        Mode.Muted -> Triple(0.3, 0.5, 0.4)
    }

    // stepping made 100 grey
    fun rampScale(index: Int, mode: Mode): Double {
        val (light, mid, dark) = ramp(mode)
        val fromEnd = steps.size - 1 - index
        if (index < 2) return light + (mid - light) * index / 2
        if (fromEnd < 2) return dark + (mid - dark) * fromEnd / 2
        return mid
    }

    /** OKLCH of every level, lightest first. */
    fun shades(base: Rgb, mode: Mode): List<DoubleArray> {
        val (bl, bc, bh) = base.oklch().toList()
        val most = ColourAtlasEngine.maxChroma(bl, bh)
        val saturation = if (most > 0) min(1.0, bc / most) else 0.0
        return targetLightness.mapIndexed { index, target ->
            val fromEnd = steps.size - 1 - index
            val l = when {
                mode != Mode.LuminanceAnchored -> target
                index < 2 -> target + (1 - target) * 0.3
                fromEnd < 2 -> target * 0.8
                else -> target
            }
            // warm light, cool dark
            val h = if (mode == Mode.HueShift) (bh + (target - 0.5) * 2 * 15 + 360) % 360 else bh
            val share = min(1.0, saturation * rampScale(index, mode))
            doubleArrayOf(l, share * ColourAtlasEngine.maxChroma(l, h), h)
        }
    }

    fun colours(base: Rgb, mode: Mode) = shades(base, mode).map { fromOklch(it[0], it[1], it[2]) }

    private fun variable(name: String) = name.ifEmpty { "colour" }

    fun cssHex(name: String, colours: List<Rgb>) =
        ":root {\n" + steps.zip(colours).joinToString("\n") { (step, c) -> "  --${variable(name)}-$step: ${c.hex};" } + "\n}"

    fun cssOklch(name: String, colours: List<Rgb>) = ":root {\n" + steps.zip(colours).joinToString("\n") { (step, c) ->
        val ok = c.oklch()
        String.format(Locale.ROOT, "  --%s-%d: oklch(%.4f %.4f %.1f);", variable(name), step, ok[0], ok[1], ok[2])
    } + "\n}"

    fun tailwindConfig(name: String, colours: List<Rgb>) =
        "'${variable(name)}': {\n" + steps.zip(colours).joinToString("\n") { (step, c) -> "    '$step': '${c.hex}'," } + "\n}"

    /** A css custom property name: lower case ascii letters, digits and hyphens. */
    fun cleanName(text: String) = text.lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }

    /** Where lightness `l` falls on falling lightnesses: a fractional index, the level it reads as (to 10), and the nearest step. */
    data class Place(val index: Double, val level: Int, val nearest: Int)

    fun place(l: Double, scale: List<Double>): Place {
        if (scale.size != steps.size) return Place(5.0, 500, 5)
        val index = when {
            l >= scale.first() -> 0.0
            l <= scale.last() -> (scale.size - 1).toDouble()
            else -> {
                val upper = (0 until scale.size - 1).firstOrNull { scale[it] >= l && scale[it + 1] <= l }
                if (upper == null) {
                    0.0
                } else {
                    val span = scale[upper] - scale[upper + 1]
                    upper + if (span > 0) (scale[upper] - l) / span else 0.0
                }
            }
        }
        val lower = floor(index).toInt()
        val upper = min(lower + 1, steps.size - 1)
        val level = steps[lower] + (steps[upper] - steps[lower]) * (index - lower)
        return Place(index, (level / 10).roundToInt() * 10, index.roundToInt())
    }
}
