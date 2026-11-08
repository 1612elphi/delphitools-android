package com.delphi.delphitools.colour

import kotlin.math.roundToInt

/**
 * Nearest named colour by Euclidean RGB distance, as DelphiCore's ColourNames, over
 * meodai/color-names bestof (assets/colour-names.json). Memoised per colour.
 */
class ColourNames(entries: List<Pair<String, Int>>) {
    private val names = entries.map { it.first }.toTypedArray()
    private val colours = entries.map { it.second }.toIntArray()
    private val cache = HashMap<Int, String>()

    fun closest(colour: Rgb): String {
        val c = colour.clamped
        val key = ((c.r * 255).roundToInt() shl 16) or ((c.g * 255).roundToInt() shl 8) or (c.b * 255).roundToInt()
        synchronized(cache) { cache[key] }?.let { return it }
        val name = scan(key)
        synchronized(cache) { cache[key] = name }
        return name
    }

    private fun scan(rgb: Int): String {
        var best = "Unknown"
        var bestDistance = Int.MAX_VALUE
        for (i in colours.indices) {
            val dr = (rgb shr 16 and 0xFF) - (colours[i] shr 16 and 0xFF)
            val dg = (rgb shr 8 and 0xFF) - (colours[i] shr 8 and 0xFF)
            val db = (rgb and 0xFF) - (colours[i] and 0xFF)
            val distance = dr * dr + dg * dg + db * db
            if (distance < bestDistance) {
                bestDistance = distance
                best = names[i]
                if (distance == 0) break
            }
        }
        return best
    }
}
