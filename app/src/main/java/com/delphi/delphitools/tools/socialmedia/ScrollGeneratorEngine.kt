package com.delphi.delphitools.tools.socialmedia

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** dt-ios ScrollEngine: a wide image laid across carousel slides, never cropped; edge slides fill the gap. */
object ScrollGeneratorEngine {
    /** Width over height, in shape row order. */
    val ratios = listOf(4.0 / 5.0, 1.0)

    /** Output px, the web value. */
    const val BLUR = 30.0

    /** 8192² pixels, any shape. */
    const val MAX_AREA = 8192L * 8192L

    data class Geometry(
        val tileWidth: Int,
        val tileHeight: Int,
        val slideCount: Int,
        val needsFill: Boolean,
        val imageWidth: Int,
        /** Strip pixels. */
        val imageStart: Double,
    )

    /** Image pixels. */
    data class Slice(val sourceX: Double, val sourceWidth: Double, val drawX: Double, val isFillEdge: Boolean)

    data class Placement(val scale: Double, val stripX: Double, val y: Double)

    fun geometry(width: Int, height: Int, ratio: Double): Geometry? {
        if (width <= 0 || height <= 0) return null
        val tileWidth = max(1, (height * ratio).roundToInt())
        val slideCount = max(1, (width + tileWidth - 1) / tileWidth)
        val fill = tileWidth * slideCount - width
        // whole pixels: no half-pixel seam
        return Geometry(tileWidth, height, slideCount, fill > 0, width, (fill / 2).toDouble())
    }

    /** The written slide size for a decode `imageHeight` tall, which may be capped below the file's size. */
    fun slideSize(geometry: Geometry, imageHeight: Int): Pair<Int, Int> {
        val scale = imageHeight.toDouble() / geometry.tileHeight
        return max(1, (geometry.tileWidth * scale).roundToInt()) to imageHeight
    }

    fun slices(geometry: Geometry): List<Slice> {
        val imageEnd = geometry.imageStart + geometry.imageWidth
        return (0 until geometry.slideCount).map { index ->
            val tileStart = (index * geometry.tileWidth).toDouble()
            val start = max(tileStart, geometry.imageStart)
            val end = min(tileStart + geometry.tileWidth, imageEnd)
            val hasContent = end > start
            Slice(
                if (hasContent) start - geometry.imageStart else 0.0,
                if (hasContent) end - start else 0.0,
                if (hasContent) start - tileStart else 0.0,
                geometry.needsFill && (index == 0 || index == geometry.slideCount - 1),
            )
        }
    }

    /** The longest side to decode a file of this size at, so its area stays within [MAX_AREA]. */
    fun longestSide(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 8192
        val scale = min(1.0, sqrt(MAX_AREA.toDouble() / (width.toLong() * height)))
        return (max(width, height) * scale).toInt()
    }

    /** The strip fitted into an area, in points per image pixel. */
    fun place(geometry: Geometry, x: Double, y: Double, width: Double, height: Double): Placement {
        val stripWidth = (geometry.tileWidth * geometry.slideCount).toDouble()
        val stripHeight = geometry.tileHeight.toDouble()
        val scale = min(width / stripWidth, height / stripHeight)
        return Placement(scale, x + width / 2 - stripWidth * scale / 2, y + height / 2 - stripHeight * scale / 2)
    }
}
