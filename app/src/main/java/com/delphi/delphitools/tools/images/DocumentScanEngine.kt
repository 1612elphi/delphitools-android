package com.delphi.delphitools.tools.images

import kotlin.math.abs
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

    /**
     * The corners of the largest region that differs from the border's luma, clockwise from the top left in
     * [pixels] coordinates. Null when no region covers between a tenth and nine tenths of the picture.
     */
    // ponytail: luma contrast against the border only; a page on a ground of the same brightness is not found
    fun corners(width: Int, height: Int, pixels: IntArray): List<QuadPoint>? {
        if (width < 3 || height < 3 || pixels.size < width * height) return null
        val count = width * height
        val border = buildList {
            for (x in 0 until width) { add(luma(pixels[x])); add(luma(pixels[count - width + x])) }
            for (y in 0 until height) { add(luma(pixels[y * width])); add(luma(pixels[y * width + width - 1])) }
        }.sorted()
        val ground = border[border.size / 2]
        val difference = IntArray(count) { abs(luma(pixels[it]) - ground) }
        val level = otsu(difference)
        val seen = BooleanArray(count)
        val queue = IntArray(count)
        var best = IntArray(0)
        for (seed in 0 until count) {
            if (seen[seed] || difference[seed] <= level) continue
            var head = 0
            var tail = 0
            fun visit(next: Int) {
                if (next < 0 || next >= count || seen[next] || difference[next] <= level) return
                seen[next] = true
                queue[tail++] = next
            }
            visit(seed)
            while (head < tail) {
                val at = queue[head++]
                val x = at % width
                if (x > 0) visit(at - 1)
                if (x < width - 1) visit(at + 1)
                visit(at - width)
                visit(at + width)
            }
            if (tail > best.size) best = queue.copyOf(tail)
        }
        if (best.isEmpty() || best.size < count / 10 || best.size > count * 9 / 10) return null
        fun far(score: (x: Int, y: Int) -> Int): QuadPoint {
            val at = best.maxBy { score(it % width, it / width) }
            return QuadPoint(at % width + 0.5, at / width + 0.5)
        }
        return listOf(far { x, y -> -x - y }, far { x, y -> x - y }, far { x, y -> x + y }, far { x, y -> y - x })
    }

    /** Otsu's threshold over values 0 to 255. */
    private fun otsu(values: IntArray): Int {
        val histogram = IntArray(256)
        for (value in values) histogram[value]++
        val total = values.size.toDouble()
        val sum = histogram.indices.sumOf { it.toDouble() * histogram[it] }
        var below = 0.0
        var belowSum = 0.0
        var best = 0
        var bestVariance = -1.0
        for (level in 0 until 256) {
            below += histogram[level]
            belowSum += level.toDouble() * histogram[level]
            val above = total - below
            if (below == 0.0 || above == 0.0) continue
            val gap = belowSum / below - (sum - belowSum) / above
            val variance = below * above * gap * gap
            if (variance > bestVariance) {
                bestVariance = variance
                best = level
            }
        }
        return best
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
