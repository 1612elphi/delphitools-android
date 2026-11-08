package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.ColourBlindness

/** dt-ios ColourBlindnessImage: the vision matrix over every pixel, alpha kept. */
object ColourBlindnessImage {
    /** `pixels` are ARGB, not premultiplied; channels truncate to bytes as iOS does. */
    fun simulate(pixels: IntArray, type: ColourBlindness): IntArray {
        val m = type.matrix
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16 and 0xFF) / 255.0
            val g = (p shr 8 and 0xFF) / 255.0
            val b = (p and 0xFF) / 255.0
            val nr = ((m[0] * r + m[1] * g + m[2] * b).coerceIn(0.0, 1.0) * 255).toInt()
            val ng = ((m[3] * r + m[4] * g + m[5] * b).coerceIn(0.0, 1.0) * 255).toInt()
            val nb = ((m[6] * r + m[7] * g + m[8] * b).coerceIn(0.0, 1.0) * 255).toInt()
            out[i] = (p and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
        }
        return out
    }
}
