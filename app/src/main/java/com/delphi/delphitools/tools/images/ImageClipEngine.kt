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

/** The highest alpha in each row and column, so the bounds at any threshold need no second scan. */
class AlphaProfile(private val rows: IntArray, private val columns: IntArray) {
    /** The smallest rect covering every pixel with alpha above [threshold], or null when all are at or below it. */
    fun rect(threshold: Int): IntRect? {
        val top = rows.indexOfFirst { it > threshold }
        if (top < 0) return null
        return IntRect(columns.indexOfFirst { it > threshold }, top, columns.indexOfLast { it > threshold } + 1, rows.indexOfLast { it > threshold } + 1)
    }
}

/** dt-core AlphaTrimmer. */
object ImageClipEngine {
    /** [readRow] fills its array with row y's ARGB pixels, so only one row is held at a time. */
    fun profile(width: Int, height: Int, readRow: (y: Int, into: IntArray) -> Unit): AlphaProfile {
        val row = IntArray(maxOf(width, 0))
        val rows = IntArray(maxOf(height, 0))
        val columns = IntArray(row.size)
        for (y in rows.indices) {
            readRow(y, row)
            var most = 0
            for (x in row.indices) {
                val alpha = row[x] ushr 24
                if (alpha > most) most = alpha
                if (alpha > columns[x]) columns[x] = alpha
            }
            rows[y] = most
        }
        return AlphaProfile(rows, columns)
    }

    /** The smallest rect covering every pixel with alpha above [threshold], or null when all are transparent. */
    fun boundingRect(width: Int, height: Int, threshold: Int = 0, readRow: (y: Int, into: IntArray) -> Unit): IntRect? =
        profile(width, height, readRow).rect(threshold)
}
