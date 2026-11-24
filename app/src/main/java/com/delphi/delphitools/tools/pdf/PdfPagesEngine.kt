package com.delphi.delphitools.tools.pdf

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A rectangle by origin and size; points unless said otherwise. */
data class PdfRect(val x: Double, val y: Double, val width: Double, val height: Double) {
    val maxX get() = x + width
    val maxY get() = y + height

    fun offset(dx: Double, dy: Double) = copy(x = x + dx, y = y + dy)

    fun contains(other: PdfRect) = other.x >= x && other.y >= y && other.maxX <= maxX && other.maxY <= maxY

    /** CGRect.integral: the smallest whole-number rectangle around this one. */
    fun integral() = PdfRect(
        kotlin.math.floor(x), kotlin.math.floor(y),
        kotlin.math.ceil(maxX) - kotlin.math.floor(x), kotlin.math.ceil(maxY) - kotlin.math.floor(y),
    )

    companion object {
        fun of(box: PdfBox) = PdfRect(box.x0, box.y0, box.width, box.height)
    }
}

data class PdfSize(val width: Double, val height: Double)

/** dt-ios PDFPagesEngine: page ranges, rotation steps and image placement. */
object PdfPagesEngine {
    enum class FitMode { Contain, Cover, Stretch }

    enum class PageOrientation { Auto, Portrait, Landscape }

    sealed interface RangeError {
        data object Empty : RangeError
        data class Malformed(val part: String) : RangeError

        /** "4-": the start page. */
        data class OpenEnded(val start: Int) : RangeError
        data class OutOfRange(val page: Int, val count: Int) : RangeError

        fun message(pageCount: Int): String = when (this) {
            Empty -> "empty range"
            is Malformed -> "bad range \"$part\""
            is OpenEnded -> "Add a last page: $start-$pageCount"
            is OutOfRange -> "There is no page $page, only $count pages"
        }
    }

    class RangeException(val error: RangeError) : Exception()

    const val PX_TO_POINTS = 72.0 / 96.0
    const val MM_TO_POINTS = 72.0 / 25.4

    private val openPattern = Regex("""(\d+)\s*-""")
    private val rangePattern = Regex("""(\d+)(?:\s*-\s*(\d+))?""")

    /** 1-based inclusive spec, 0-based groups. "5-3" counts down. */
    fun parsePageRanges(spec: String, pageCount: Int): List<List<Int>> {
        val groups = ArrayList<List<Int>>()
        for (part in spec.split(",")) {
            val trimmed = part.trim(' ', '\t')
            if (trimmed.isEmpty()) throw RangeException(RangeError.Empty)
            openPattern.matchEntire(trimmed)?.let { throw RangeException(RangeError.OpenEnded(it.groupValues[1].toInt())) }
            val match = rangePattern.matchEntire(trimmed) ?: throw RangeException(RangeError.Malformed(trimmed))
            val lo = match.groupValues[1].toIntOrNull() ?: throw RangeException(RangeError.Malformed(trimmed))
            val hi = match.groupValues[2].takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: throw RangeException(RangeError.Malformed(trimmed)) } ?: lo
            if (minOf(lo, hi) < 1) throw RangeException(RangeError.OutOfRange(minOf(lo, hi), pageCount))
            if (maxOf(lo, hi) > pageCount) throw RangeException(RangeError.OutOfRange(maxOf(lo, hi), pageCount))
            groups.add(if (hi < lo) (lo - 1 downTo hi - 1).toList() else (lo - 1..hi - 1).toList())
        }
        if (groups.isEmpty()) throw RangeException(RangeError.Empty)
        return groups
    }

    fun everyPageAlone(pageCount: Int): List<List<Int>> = (0 until maxOf(0, pageCount)).map { listOf(it) }

    fun normaliseRotation(degrees: Int): Int {
        val steps = degrees / 90.0
        // swift rounded() behaviour
        val snapped = (if (steps >= 0) kotlin.math.floor(steps + 0.5) else kotlin.math.ceil(steps - 0.5)).toInt() * 90
        return ((snapped % 360) + 360) % 360
    }

    /** [scale] lowered until [size] renders within [maxPixels]. */
    fun cappedScale(scale: Double, size: PdfSize, maxPixels: Double): Double {
        val area = size.width * size.height
        if (area <= 0 || scale <= 0) return scale
        return minOf(scale, sqrt(maxPixels / area))
    }

    fun fitPlacement(image: PdfSize, area: PdfSize, mode: FitMode): PdfRect {
        if (mode == FitMode.Stretch) return PdfRect(0.0, 0.0, area.width, area.height)
        val scale = if (mode == FitMode.Cover) {
            maxOf(area.width / image.width, area.height / image.height)
        } else {
            minOf(area.width / image.width, area.height / image.height)
        }
        val width = image.width * scale
        val height = image.height * scale
        return PdfRect((area.width - width) / 2, (area.height - height) / 2, width, height)
    }

    /** [paperMm] null matches the image at 96 px per inch plus margins. */
    fun pageSetup(paperMm: PdfSize?, orientation: PageOrientation, image: PdfSize, marginPt: Double): PdfSize {
        if (paperMm == null) return PdfSize(image.width * PX_TO_POINTS + marginPt * 2, image.height * PX_TO_POINTS + marginPt * 2)
        val landscape = orientation == PageOrientation.Landscape || (orientation == PageOrientation.Auto && image.width > image.height)
        val long = maxOf(paperMm.width, paperMm.height) * MM_TO_POINTS
        val short = minOf(paperMm.width, paperMm.height) * MM_TO_POINTS
        return if (landscape) PdfSize(long, short) else PdfSize(short, long)
    }

    /** Pixels for a page of [size] points at [scale], at least one each way. */
    fun pixels(size: PdfSize, scale: Double): Pair<Int, Int> =
        maxOf(1, (size.width * scale).roundToInt()) to maxOf(1, (size.height * scale).roundToInt())
}

/**
 * dt-ios PDFCropEngine. Display space: the page as viewed, rotation applied, top-left origin, points.
 * Page space: PDF user space, bottom-left origin.
 */
object PdfCropEngine {
    data class Insets(val left: Double = 0.0, val top: Double = 0.0, val right: Double = 0.0, val bottom: Double = 0.0)

    fun displaySize(box: PdfRect, rotation: Int): PdfSize =
        if (rotation % 180 == 0) PdfSize(box.width, box.height) else PdfSize(box.height, box.width)

    fun boxFromPoints(ax: Double, ay: Double, bx: Double, by: Double) =
        PdfRect(minOf(ax, bx), minOf(ay, by), abs(ax - bx), abs(ay - by))

    /** PDF boxes need area. */
    fun intersectBox(a: PdfRect, b: PdfRect): PdfRect? {
        val x = maxOf(a.x, b.x)
        val y = maxOf(a.y, b.y)
        val width = minOf(a.maxX, b.maxX) - x
        val height = minOf(a.maxY, b.maxY) - y
        return if (width <= 0 || height <= 0) null else PdfRect(x, y, width, height)
    }

    fun cropFromInsets(size: PdfSize, insets: Insets): PdfRect? {
        if (insets.left < 0 || insets.top < 0 || insets.right < 0 || insets.bottom < 0) return null
        val width = size.width - insets.left - insets.right
        val height = size.height - insets.top - insets.bottom
        if (width <= 0 || height <= 0) return null
        return PdfRect(insets.left, insets.top, width, height)
    }

    fun insets(rect: PdfRect, size: PdfSize) = Insets(rect.x, rect.y, size.width - rect.maxX, size.height - rect.maxY)

    /** Callers clip overflow. */
    fun cropToPaper(size: PdfSize, paper: PdfSize) =
        PdfRect((size.width - paper.width) / 2, (size.height - paper.height) / 2, paper.width, paper.height)

    private fun toPage(px: Double, py: Double, box: PdfRect, rotation: Int): Pair<Double, Double> {
        val (w, h) = box.width to box.height
        val (lx, ly) = when (rotation) {
            90 -> py to px
            180 -> (w - px) to py
            270 -> (w - py) to (h - px)
            else -> px to (h - py)
        }
        return (lx + box.x) to (ly + box.y)
    }

    private fun toDisplay(px: Double, py: Double, box: PdfRect, rotation: Int): Pair<Double, Double> {
        val (w, h) = box.width to box.height
        val (u, v) = (px - box.x) to (py - box.y)
        return when (rotation) {
            90 -> v to u
            180 -> (w - u) to v
            270 -> (h - v) to (w - u)
            else -> u to (h - v)
        }
    }

    fun toPage(rect: PdfRect, box: PdfRect, rotation: Int): PdfRect {
        val a = toPage(rect.x, rect.y, box, rotation)
        val b = toPage(rect.maxX, rect.maxY, box, rotation)
        return boxFromPoints(a.first, a.second, b.first, b.second)
    }

    fun toDisplay(rect: PdfRect, box: PdfRect, rotation: Int): PdfRect {
        val a = toDisplay(rect.x, rect.y, box, rotation)
        val b = toDisplay(rect.maxX, rect.maxY, box, rotation)
        return boxFromPoints(a.first, a.second, b.first, b.second)
    }
}
