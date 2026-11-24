package com.delphi.delphitools.tools.images

import kotlin.math.min

/** A rectangle in pixels, top-left origin. */
data class PixelRect(val x: Int, val y: Int, val width: Int, val height: Int)

/** dt-ios ArtworkEnhancerViewModel.enhance and ArtworkDetail: random colour noise overlay-blended over ARGB pixels. */
object ArtworkEnhanceEngine {
    /** Random RGBA noise, one value per [scale] × [scale] block, the same for the same [seed]. */
    fun noise(x: Int, y: Int, seed: Int, scale: Int): Int {
        val bx = x / scale
        val by = y / scale
        // murmur3 finaliser over the block coordinates
        var h = bx * -0x61c88647 xor by * 0x27d4eb2d xor seed * 0x165667b1
        h = h xor (h ushr 16)
        h *= -0x7a143595
        h = h xor (h ushr 13)
        h *= -0x3d4d51cb
        return h xor (h ushr 16)
    }

    /**
     * [pixels] (ARGB, row by row) with the noise drawn over them in overlay mode, the noise's own alpha times
     * [alpha] (0 to 1). Separable overlay blend and source-over compositing, as Core Image's overlayBlendMode.
     */
    fun enhance(pixels: IntArray, width: Int, alpha: Float, scale: Int, seed: Int): IntArray {
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val n = noise(i % width, i / width, seed, scale)
            val sa = (n ushr 24 and 0xFF) / 255f * alpha
            val p = pixels[i]
            val ba = (p ushr 24 and 0xFF) / 255f
            val oa = sa + ba * (1 - sa)
            if (oa <= 0f) continue
            var argb = (Math.round(oa * 255) shl 24)
            for (shift in intArrayOf(16, 8, 0)) {
                val cs = (n ushr shift and 0xFF) / 255f
                val cb = (p ushr shift and 0xFF) / 255f
                val blend = if (cb <= 0.5f) 2 * cs * cb else 1 - 2 * (1 - cs) * (1 - cb)
                val premultiplied = cs * sa * (1 - ba) + cb * ba * (1 - sa) + blend * sa * ba
                argb = argb or (Math.round((premultiplied / oa).coerceIn(0f, 1f) * 255) shl shift)
            }
            out[i] = argb
        }
        return out
    }

    /** The centred square of side [side] (at most the image's short side), top-left origin. */
    fun detailRect(width: Int, height: Int, side: Int): PixelRect {
        val edge = min(side, min(width, height))
        return PixelRect((width - edge) / 2, (height - edge) / 2, edge, edge)
    }

    /** The [detailRect] of [pixels] scaled up [factor] times, nearest neighbour. */
    fun magnified(pixels: IntArray, width: Int, height: Int, side: Int, factor: Int): Pair<IntArray, Int> {
        val rect = detailRect(width, height, side)
        val out = rect.width * factor
        return IntArray(out * out) { i -> pixels[(rect.y + i / out / factor) * width + rect.x + i % out / factor] } to out
    }
}
