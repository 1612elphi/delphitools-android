package com.delphi.delphitools.tools.images

import java.nio.FloatBuffer
import kotlin.math.roundToInt

/** The arithmetic around briaai/RMBG-1.4: its input planes and the matte it returns. */
object BackgroundRemoveEngine {
    /** The model's input and output side, from its preprocessor_config.json. */
    const val SIDE = 1024

    /** ARGB pixels as planar RGB, each value / 255 - 0.5 (image_mean 0.5, image_std 1). */
    fun planes(argb: IntArray): FloatArray {
        val count = argb.size
        val planes = FloatArray(count * 3)
        for (i in 0 until count) {
            val pixel = argb[i]
            planes[i] = ((pixel shr 16) and 0xff) / 255f - 0.5f
            planes[count + i] = ((pixel shr 8) and 0xff) / 255f - 0.5f
            planes[2 * count + i] = (pixel and 0xff) / 255f - 0.5f
        }
        return planes
    }

    /** The matte (0 to 1 per pixel) as ARGB pixels that carry it in alpha, and the share of the picture it keeps. */
    fun alpha(matte: FloatBuffer): Pair<IntArray, Float> {
        val pixels = IntArray(matte.remaining())
        var sum = 0L
        for (i in pixels.indices) {
            val value = (matte.get().coerceIn(0f, 1f) * 255f).roundToInt()
            sum += value
            pixels[i] = value shl 24
        }
        return pixels to if (pixels.isEmpty()) 0f else sum / (255f * pixels.size)
    }
}
