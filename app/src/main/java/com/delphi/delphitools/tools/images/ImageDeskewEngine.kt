package com.delphi.delphitools.tools.images

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class QuadPoint(val x: Double, val y: Double)

/** Corner order is clockwise from the top left, as the web tool's `Quad`. */
enum class DeskewCorner(val label: String) { TopLeft("Top left"), TopRight("Top right"), BottomRight("Bottom right"), BottomLeft("Bottom left") }

/** [ratio] is long side over short; null keeps the quad's own proportions. */
data class DeskewAspect(val label: String, val ratio: Double?) {
    companion object {
        val all = listOf(
            DeskewAspect("Auto", null),
            DeskewAspect("A4", sqrt(2.0)),
            DeskewAspect("Letter", 11 / 8.5),
            DeskewAspect("1:1", 1.0),
            DeskewAspect("4:3", 4.0 / 3),
            DeskewAspect("3:2", 3.0 / 2),
            DeskewAspect("16:9", 16.0 / 9),
        )
    }
}

/** dt-core ImageDeskewEngine: the quad's output size, its validity, and the square-to-quad projection. */
object ImageDeskewEngine {
    /** Longest output edge; a 48 MP sensor is 8064 px wide. */
    const val MAX_EDGE = 8192.0

    /** A starting quad [inset] in from each side, clockwise from the top left. */
    fun insetQuad(width: Double, height: Double, inset: Double = 0.12): List<QuadPoint> {
        val dx = width * inset
        val dy = height * inset
        return listOf(QuadPoint(dx, dy), QuadPoint(width - dx, dy), QuadPoint(width - dx, height - dy), QuadPoint(dx, height - dy))
    }

    /** The longer of each pair of opposite edges, width by height. */
    fun outputSize(quad: List<QuadPoint>): Pair<Double, Double> {
        if (quad.size != 4) return 1.0 to 1.0
        val (tl, tr, br, bl) = quad
        return max(1.0, Math.rint(max(distance(tl, tr), distance(bl, br)))) to max(1.0, Math.rint(max(distance(tl, bl), distance(tr, br))))
    }

    /** [size] at [ratio], long side over short, keeping its long side; a null ratio keeps [size]. */
    fun fitAspect(size: Pair<Double, Double>, ratio: Double?): Pair<Double, Double> {
        if (ratio == null || ratio <= 0) return size
        val (width, height) = size
        return if (width >= height) width to max(1.0, Math.rint(width / ratio)) else max(1.0, Math.rint(height / ratio)) to height
    }

    /** [size] scaled down so no side exceeds [maxEdge]. */
    fun capped(size: Pair<Double, Double>, maxEdge: Double = MAX_EDGE): Pair<Double, Double> {
        val scale = min(1.0, maxEdge / max(size.first, max(size.second, 1.0)))
        return max(1.0, Math.rint(size.first * scale)) to max(1.0, Math.rint(size.second * scale))
    }

    /** True for a convex quad wound clockwise in top-left coordinates; a bow-tie, mirrored or collapsed quad has no correction. */
    fun isConvex(quad: List<QuadPoint>): Boolean {
        if (quad.size != 4) return false
        return (0 until 4).all { i ->
            val a = quad[i]
            val b = quad[(i + 1) % 4]
            val c = quad[(i + 2) % 4]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) > 1e-6
        }
    }

    /**
     * The point at ([u], [v]) of the unit square under the projective map that takes the square's corners to [quad]
     * (Heckbert 1989, square to quad). A line of the output is a straight line of the quad, foreshortened.
     */
    fun project(u: Double, v: Double, quad: List<QuadPoint>): QuadPoint {
        if (quad.size != 4) return QuadPoint(0.0, 0.0)
        val (p0, p1, p2, p3) = quad
        val sx = p0.x - p1.x + p2.x - p3.x
        val sy = p0.y - p1.y + p2.y - p3.y
        val dx1 = p1.x - p2.x
        val dx2 = p3.x - p2.x
        val dy1 = p1.y - p2.y
        val dy2 = p3.y - p2.y
        val den = dx1 * dy2 - dx2 * dy1
        val g = if (abs(den) < 1e-9) 0.0 else (sx * dy2 - dx2 * sy) / den
        val h = if (abs(den) < 1e-9) 0.0 else (dx1 * sy - sx * dy1) / den
        val a = p1.x - p0.x + g * p1.x
        val b = p3.x - p0.x + h * p3.x
        val d = p1.y - p0.y + g * p1.y
        val e = p3.y - p0.y + h * p3.y
        val w = g * u + h * v + 1
        return QuadPoint((a * u + b * v + p0.x) / w, (d * u + e * v + p0.y) / w)
    }

    /** Line ends through the quad at each 1/[divisions] of its sides, foreshortened as the output will be. */
    fun grid(quad: List<QuadPoint>, divisions: Int): List<Pair<QuadPoint, QuadPoint>> {
        if (quad.size != 4 || divisions <= 1) return emptyList()
        return (1 until divisions).flatMap { step ->
            val share = step.toDouble() / divisions
            listOf(project(share, 0.0, quad) to project(share, 1.0, quad), project(0.0, share, quad) to project(1.0, share, quad))
        }
    }

    /** Integer output pixels for [quad] at [aspect], capped at [maxEdge]. */
    fun target(quad: List<QuadPoint>, aspect: DeskewAspect, maxEdge: Double = MAX_EDGE): Pair<Int, Int> =
        capped(fitAspect(outputSize(quad), aspect.ratio), maxEdge).let { it.first.roundToInt() to it.second.roundToInt() }

    private fun distance(a: QuadPoint, b: QuadPoint) = hypot(b.x - a.x, b.y - a.y)
}

/** dt-core DeskewGrab: what a pointer takes hold of on a quad, a corner, an edge (two corners) or the whole quad. */
sealed interface DeskewGrab {
    data class Corner(val index: Int) : DeskewGrab
    data class Edge(val index: Int) : DeskewGrab
    data object Whole : DeskewGrab

    companion object {
        /** Reach in points. */
        const val CORNER_REACH = 30.0
        const val EDGE_REACH = 18.0

        /** [point] and [quad] in view points; [scale] is pixels per point. */
        fun at(point: QuadPoint, quad: List<QuadPoint>, scale: Double = 1.0): DeskewGrab? {
            if (quad.size != 4) return null
            val nearest = quad.indices.minBy { hypot(quad[it].x - point.x, quad[it].y - point.y) }
            if (hypot(quad[nearest].x - point.x, quad[nearest].y - point.y) <= CORNER_REACH * scale) return Corner(nearest)
            val distances = (0 until 4).map { distance(point, quad[it], quad[(it + 1) % 4]) }
            val edge = distances.indices.minBy { distances[it] }
            if (distances[edge] <= EDGE_REACH * scale) return Edge(edge)
            return if (contains(quad, point)) Whole else null
        }

        /** [quad] with the grabbed corners moved by [dx], [dy], held inside [width] × [height] together. */
        fun move(quad: List<QuadPoint>, grab: DeskewGrab, dx: Double, dy: Double, width: Double, height: Double): List<QuadPoint> {
            val moving = when (grab) {
                is Corner -> listOf(grab.index)
                is Edge -> listOf(grab.index, (grab.index + 1) % 4)
                Whole -> listOf(0, 1, 2, 3)
            }
            var mx = dx
            var my = dy
            for (index in moving) {
                mx = min(max(mx, -quad[index].x), width - quad[index].x)
                my = min(max(my, -quad[index].y), height - quad[index].y)
            }
            return quad.mapIndexed { index, p -> if (index in moving) QuadPoint(p.x + mx, p.y + my) else p }
        }

        // even-odd, as CGPath.contains
        private fun contains(polygon: List<QuadPoint>, p: QuadPoint): Boolean {
            var inside = false
            var j = polygon.size - 1
            for (i in polygon.indices) {
                val a = polygon[i]
                val b = polygon[j]
                if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
                j = i
            }
            return inside
        }

        private fun distance(point: QuadPoint, a: QuadPoint, b: QuadPoint): Double {
            val dx = b.x - a.x
            val dy = b.y - a.y
            val length = dx * dx + dy * dy
            val t = if (length > 0) min(max(((point.x - a.x) * dx + (point.y - a.y) * dy) / length, 0.0), 1.0) else 0.0
            return hypot(point.x - (a.x + t * dx), point.y - (a.y + t * dy))
        }
    }
}
