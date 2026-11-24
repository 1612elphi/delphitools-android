package com.delphi.delphitools.tools.images

import kotlin.math.max
import kotlin.math.roundToInt

enum class ScanFilter(val label: String) { Colour("Colour"), Greyscale("Greyscale"), BlackWhite("Black & White") }

/** dt-core DocumentScanText.pageSize, and the scan filters as pixel maths over ARGB. */
object DocumentScanEngine {
    /** A4 long side, points. */
    const val LONG_SIDE = 842.0

    /** Page size in points: the long side 842 pt, the short side in proportion. */
    fun pageSize(width: Int, height: Int): Pair<Double, Double> {
        val long = max(max(width, height), 1).toDouble()
        return Math.rint(width / long * LONG_SIDE) to Math.rint(height / long * LONG_SIDE)
    }

    /** Rec. 601 luma, 0 to 255. */
    fun luma(argb: Int): Int =
        ((argb shr 16 and 0xFF) * 299 + (argb shr 8 and 0xFF) * 587 + (argb and 0xFF) * 114 + 500) / 1000

    /** [contrast] 0 to 100 stretches each channel about mid-grey up to three times. */
    fun stretch(channel: Int, contrast: Int): Int =
        ((channel - 127.5) * (1 + contrast / 50.0) + 127.5).roundToInt().coerceIn(0, 255)

    /**
     * Opaque [pixels] with [contrast] applied, then [filter]; black and white is white at or above [threshold]
     * (0 to 100) of full luma.
     */
    fun filter(pixels: IntArray, filter: ScanFilter, contrast: Int, threshold: Int): IntArray {
        val level = (threshold * 255 + 50) / 100
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            val r = stretch(p shr 16 and 0xFF, contrast)
            val g = stretch(p shr 8 and 0xFF, contrast)
            val b = stretch(p and 0xFF, contrast)
            val stretched = (r shl 16) or (g shl 8) or b
            when (filter) {
                ScanFilter.Colour -> stretched
                ScanFilter.Greyscale -> luma(stretched).let { (it shl 16) or (it shl 8) or it }
                ScanFilter.BlackWhite -> if (luma(stretched) >= level) 0xFFFFFF else 0
            } or (0xFF shl 24)
        }
    }
}
