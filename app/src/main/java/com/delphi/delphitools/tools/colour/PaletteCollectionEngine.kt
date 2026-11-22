package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.ColourFormat
import com.delphi.delphitools.colour.CuratedPalette
import com.delphi.delphitools.colour.formatted
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Search and the fan deck's geometry, as dt-ios PaletteCollectionViewController and PaletteDeck have them. */
object PaletteCollectionEngine {
    /** A palette matches on its name, category, any hex as stored or any colour in `format`. `query` is lower case. */
    fun matches(palette: CuratedPalette, query: String, format: ColourFormat): Boolean {
        fun has(text: String) = text.lowercase(Locale.ROOT).contains(query)
        return has(palette.name) || has(palette.category) || palette.colours.any(::has) || palette.rgb.any { has(it.formatted(format)) }
    }

    fun query(text: String) = text.trim().lowercase(Locale.ROOT)

    /** Chip width over chip length, and chip length past the rivet over chip width. */
    const val WIDTH_SHARE = 0.19
    const val TAIL_SHARE = 0.62

    /** The cover lies this far below horizontal; the chips hang clockwise beneath it, over at most MAX_SPREAD. */
    val coverTilt = Math.toRadians(7.0)
    private val maxSpread = Math.toRadians(80.0)

    /** Share of the room left over that goes above and before the fan; the rest stays below and after it. */
    private const val CORNER_HUG = 0.3

    /** `angles` in radians clockwise from upright, the cover last; lengths in the room's units. */
    data class Fan(val pivotX: Double, val pivotY: Double, val length: Double, val width: Double, val tail: Double, val angles: List<Double>)

    /**
     * The largest fan of `count` chips (colours plus the cover) that fits the room: chips part at their tips once
     * they are a chip width apart and hang further where the room is tall; the fan keeps to the top leading corner.
     */
    fun fan(x: Double, y: Double, width: Double, height: Double, count: Int): Fan {
        val w = WIDTH_SHARE
        val tail = TAIL_SHARE * WIDTH_SHARE
        val degree = Math.toRadians(1.0)
        val cover = Math.PI / 2 + coverTilt
        fun angles(step: Double) = (1 until max(count, 1)).map { cover + step * it } + cover
        // min x, min y, max x, max y
        fun bounds(angles: List<Double>): DoubleArray {
            val box = doubleArrayOf(Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE)
            for (angle in angles) {
                for ((cx, cy) in listOf(-w / 2 to -1.0, w / 2 to -1.0, -w / 2 to tail, w / 2 to tail)) {
                    val tx = cx * cos(angle) - cy * sin(angle)
                    val ty = cx * sin(angle) + cy * cos(angle)
                    box[0] = min(box[0], tx)
                    box[1] = min(box[1], ty)
                    box[2] = max(box[2], tx)
                    box[3] = max(box[3], ty)
                }
            }
            return box
        }
        val gaps = max(count - 1, 1).toDouble()
        val highest = min(18 * degree, maxSpread / gaps)
        var best: Triple<Double, Double, Pair<List<Double>, DoubleArray>>? = null
        var step = min(5 * degree, highest)
        do {
            val candidate = angles(step)
            val box = bounds(candidate)
            val length = min(width / (box[2] - box[0]), height / (box[3] - box[1]))
            val parting = if (count < 2) 1.0 else min(1.0, step / (1.1 * w))
            val score = length * parting
            // near ties: wider fan
            if (best == null || score >= best.first * 0.999) best = Triple(score, length, candidate to box)
            step += 0.5 * degree
        } while (step <= highest)
        val (_, chosenLength, chosen) = best!!
        val (angles, box) = chosen
        val length = max(chosenLength, 0.0)
        val slackX = width - (box[2] - box[0]) * length
        val slackY = height - (box[3] - box[1]) * length
        return Fan(x + slackX * CORNER_HUG - box[0] * length, y + slackY * CORNER_HUG - box[1] * length, length, w * length, tail * length, angles)
    }

    /** easeOutBack with an overshoot of 1.5, as the fan-out uses. */
    fun easeOutBack(t: Double): Double {
        val overshoot = 1.5
        val s = t - 1
        return s * s * ((overshoot + 1) * s + overshoot) + 1
    }
}
