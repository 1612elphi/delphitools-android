package com.delphi.delphitools.tools.images

import androidx.compose.ui.unit.IntRect
import kotlin.math.roundToInt

/** dt-core ClipMargins: the transparent edges a clip removes, in pixels, and the share of the area it saves. */
data class ClipMargins(val top: Int, val right: Int, val bottom: Int, val left: Int, val savedPercent: Int) {
    val isEmpty get() = top == 0 && right == 0 && bottom == 0 && left == 0

    companion object {
        /** [kept]: top-left origin, inside a [width] × [height] image. */
        fun of(kept: IntRect, width: Int, height: Int): ClipMargins {
            val area = width.toDouble() * height
            val saved = if (area > 0) ((1 - kept.width.toDouble() * kept.height / area) * 100).roundToInt() else 0
            return ClipMargins(kept.top, width - kept.right, height - kept.bottom, kept.left, saved)
        }
    }
}

/** dt-core AlphaTrimmer. */
object ImageClipEngine {
    /**
     * The smallest rect covering every pixel with alpha above [threshold], or null when all are transparent.
     * [readRow] fills its array with row y's ARGB pixels, so only one row is held at a time.
     */
    fun boundingRect(width: Int, height: Int, threshold: Int = 0, readRow: (y: Int, into: IntArray) -> Unit): IntRect? {
        if (width <= 0 || height <= 0) return null
        val row = IntArray(width)
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            readRow(y, row)
            var first = -1
            for (x in 0 until width) {
                if (row[x] ushr 24 > threshold) {
                    first = x
                    break
                }
            }
            if (first < 0) continue
            var last = width - 1
            while (row[last] ushr 24 <= threshold) last--
            if (first < minX) minX = first
            if (last > maxX) maxX = last
            if (minY == height) minY = y
            maxY = y
        }
        if (maxX < minX || maxY < minY) return null
        return IntRect(minX, minY, maxX + 1, maxY + 1)
    }
}
