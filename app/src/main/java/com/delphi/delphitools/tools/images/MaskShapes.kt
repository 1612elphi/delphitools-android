package com.delphi.delphitools.tools.images

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A mask outline in a 0 to 100 box, top-left origin, as path commands: 'M' x y, 'L' x y,
 * 'C' x1 y1 x2 y2 x y, 'Z'. Subpaths wound against the outline are holes under a non-zero fill.
 */
class MaskShape(val id: String, val commands: List<Pair<Char, DoubleArray>>) {
    val family get() = id.substringBeforeLast('-')
    val variant get() = id.substringAfterLast('-').toIntOrNull() ?: 0

    /** `Squircle 4`. */
    val title get() = "${family.replaceFirstChar { it.uppercase() }} $variant"
}

/** mulberry32, matching the web tool's seeded PRNG bit for bit. */
class MaskRandom(seed: Int) {
    private var state = seed

    /** The next value in 0 until 1. */
    fun next(): Double {
        state += 0x6d2b79f5
        var t = (state xor (state ushr 15)) * (1 or state)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4_294_967_296.0
    }
}

/** dt-core MaskShapes: the web tool's `mask-shapes.ts` procedural library, 28 families × 12 variants in a 0 to 100 box. */
object MaskShapes {
    const val VARIANTS = 12

    private const val C = 100.0 / 2
    private const val R = 46.0
    private const val TAU = PI * 2
    private const val ARC = 96

    private fun polar(angle: Double, radius: Double, cx: Double = C, cy: Double = C) = QuadPoint(cx + radius * cos(angle), cy + radius * sin(angle))

    private fun ring(n: Int, start: Double = -PI / 2, cx: Double = C, cy: Double = C, radius: (Int) -> Double) =
        (0 until n).map { polar(start + (it.toDouble() / n) * TAU, radius(it), cx, cy) }

    private fun sample(n: Int, at: (Double) -> QuadPoint) = (0 until n).map { at((it.toDouble() / n) * TAU) }

    private fun rotate(points: List<QuadPoint>, turns: Double): List<QuadPoint> {
        val cosine = cos(turns * TAU)
        val sine = sin(turns * TAU)
        return points.map { QuadPoint(C + (it.x - C) * cosine - (it.y - C) * sine, C + (it.x - C) * sine + (it.y - C) * cosine) }
    }

    // one factor keeps proportions
    private fun fit(parts: List<List<QuadPoint>>): List<List<QuadPoint>> {
        val reach = parts.flatten().maxOfOrNull { hypot(it.x - C, it.y - C) } ?: 0.0
        if (reach <= 0) return parts
        val k = min(1.0, R / reach)
        return parts.map { part -> part.map { QuadPoint(C + (it.x - C) * k, C + (it.y - C) * k) } }
    }

    private fun poly(parts: List<List<QuadPoint>>): List<Pair<Char, DoubleArray>> = buildList {
        for (points in parts) {
            if (points.size <= 1) continue
            add('M' to doubleArrayOf(points[0].x, points[0].y))
            points.drop(1).forEach { add('L' to doubleArrayOf(it.x, it.y)) }
            add('Z' to DoubleArray(0))
        }
    }

    // catmull-rom to cubics
    private fun smooth(parts: List<List<QuadPoint>>): List<Pair<Char, DoubleArray>> = buildList {
        for (points in parts) {
            if (points.size <= 2) continue
            val n = points.size
            fun at(i: Int) = points[((i % n) + n) % n]
            add('M' to doubleArrayOf(at(0).x, at(0).y))
            for (i in 0 until n) {
                val p0 = at(i - 1)
                val p1 = at(i)
                val p2 = at(i + 1)
                val p3 = at(i + 2)
                add('C' to doubleArrayOf(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y))
            }
            add('Z' to DoubleArray(0))
        }
    }

    private fun spin(turns: Double, parts: List<List<QuadPoint>>) = poly(fit(parts.map { rotate(it, turns) }))

    // clockwise, safe at zero
    private fun rrect(x: Double, y: Double, w: Double, h: Double, radius: Double): List<QuadPoint> {
        val corners = listOf(
            Triple(x + w - radius, y + radius, -0.25),
            Triple(x + w - radius, y + h - radius, 0.0),
            Triple(x + radius, y + h - radius, 0.25),
            Triple(x + radius, y + radius, 0.5),
        )
        return corners.flatMap { (cx, cy, start) -> (0..12).map { polar((start + (it / 12.0) * 0.25) * TAU, radius, cx, cy) } }
    }

    private fun count(rnd: MaskRandom, from: Int, span: Int) = from + (rnd.next() * span).toInt()

    private val heart = sample(ARC) { t ->
        val s = R / 17
        QuadPoint(C + 16 * sin(t).pow(3) * s, C - (13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t) + 2) * s)
    }

    private val drop: List<QuadPoint> = run {
        val radius = 0.68 * R
        val cy = C + R - radius
        val beta = acos(radius / (2 * R - radius))
        val from = -PI / 2 + beta
        val sweep = TAU - 2 * beta
        listOf(QuadPoint(C, C - R)) + (0..64).map { polar(from + (it / 64.0) * sweep, radius, C, cy) }
    }

    private val leaf = sample(ARC) { t -> QuadPoint(C + R * cos(t), C + 0.58 * R * (if (sin(t) < 0) -1 else 1) * abs(sin(t)).pow(1.7)) }

    private val egg = sample(ARC) { t -> QuadPoint(C + 0.78 * R * cos(t) * (1 + 0.18 * sin(t)), C + 0.95 * R * sin(t)) }

    private val arrow = listOf(
        QuadPoint(C - R, C - 0.22 * R), QuadPoint(C + 0.1 * R, C - 0.22 * R), QuadPoint(C + 0.1 * R, C - 0.55 * R),
        QuadPoint(C + R, C), QuadPoint(C + 0.1 * R, C + 0.55 * R), QuadPoint(C + 0.1 * R, C + 0.22 * R), QuadPoint(C - R, C + 0.22 * R),
    )

    private val bolt = listOf(
        QuadPoint(56.0, 4.0), QuadPoint(24.0, 56.0), QuadPoint(45.0, 56.0), QuadPoint(40.0, 96.0),
        QuadPoint(76.0, 44.0), QuadPoint(55.0, 44.0), QuadPoint(62.0, 4.0),
    )

    private val bubbleBody = rrect(C - R, C - R, 2 * R, 1.5 * R, 0.3 * R)

    private val bubbleTail = listOf(QuadPoint(C - 0.35 * R, C + 0.3 * R), QuadPoint(C + 0.05 * R, C + 0.3 * R), QuadPoint(C - 0.55 * R, C + R))

    private fun cross(a: Double) = listOf(
        QuadPoint(C - a, C - R), QuadPoint(C + a, C - R), QuadPoint(C + a, C - a), QuadPoint(C + R, C - a),
        QuadPoint(C + R, C + a), QuadPoint(C + a, C + a), QuadPoint(C + a, C + R), QuadPoint(C - a, C + R),
        QuadPoint(C - a, C + a), QuadPoint(C - R, C + a), QuadPoint(C - R, C - a), QuadPoint(C - a, C - a),
    )

    private fun gear(n: Int): List<QuadPoint> {
        val step = TAU / n
        return (0 until n).flatMap { k ->
            val a = k * step - PI / 2
            listOf(polar(a - 0.3 * step, 0.76 * R), polar(a - 0.17 * step, R), polar(a + 0.17 * step, R), polar(a + 0.3 * step, 0.76 * R))
        }
    }

    private fun pie(turns: Double) = listOf(QuadPoint(C, C)) + (0..64).map { polar(-PI / 2 + (it / 64.0) * turns * TAU, R) }

    // disc minus shifted copy
    private fun moon(d: Double): List<QuadPoint> {
        val phi = acos(d / (2 * R))
        val outer = (0..64).map { polar(phi + (it / 64.0) * (TAU - 2 * phi), R) }
        val inner = (0..64).map { polar(PI + phi - (it / 64.0) * 2 * phi, R, C + d) }
        return outer + inner
    }

    private fun jittered(rnd: MaskRandom, n: Int, jitter: Double = 0.5, radius: (Int) -> Double): List<QuadPoint> {
        val start = rnd.next() * TAU
        return (0 until n).map { k ->
            val angle = start + ((k + (rnd.next() - 0.5) * jitter) / n) * TAU
            polar(angle, radius(k))
        }
    }

    private fun blob(rnd: MaskRandom, lobes: Int, floor: Double) = jittered(rnd, lobes) { R * (floor + (1 - floor) * rnd.next()) }

    private fun splat(rnd: MaskRandom): List<QuadPoint> {
        val n = 2 * count(rnd, 5, 5)
        return jittered(rnd, n) { k -> if (k % 2 != 0) R * (0.5 + 0.2 * rnd.next()) else R * (0.85 + 0.15 * rnd.next()) }
    }

    private fun shard(rnd: MaskRandom): List<QuadPoint> {
        val n = count(rnd, 4, 4)
        return jittered(rnd, n, jitter = 0.6) { R * (0.7 + 0.3 * rnd.next()) }
    }

    // bites go inward
    private fun torn(rnd: MaskRandom): List<QuadPoint> {
        val a = 0.92 * R
        val b = a * (0.65 + 0.35 * rnd.next())
        fun edge(from: QuadPoint, to: QuadPoint, nx: Double, ny: Double) = (0 until 10).map { k ->
            val t = k / 10.0
            val bite = rnd.next() * 0.12 * R
            QuadPoint(from.x + (to.x - from.x) * t + nx * bite, from.y + (to.y - from.y) * t + ny * bite)
        }
        return edge(QuadPoint(C - a, C - b), QuadPoint(C + a, C - b), 0.0, 1.0) +
            edge(QuadPoint(C + a, C - b), QuadPoint(C + a, C + b), -1.0, 0.0) +
            edge(QuadPoint(C + a, C + b), QuadPoint(C - a, C + b), 0.0, -1.0) +
            edge(QuadPoint(C - a, C + b), QuadPoint(C - a, C - b), 1.0, 0.0)
    }

    private fun cloud(rnd: MaskRandom): List<List<QuadPoint>> {
        val puffs = count(rnd, 3, 3)
        val parts = mutableListOf(rrect(C - 0.75 * R, C + 0.1 * R, 1.5 * R, 0.4 * R, 0.2 * R))
        for (k in 0 until puffs) {
            val cx = C + (k.toDouble() / (puffs - 1) - 0.5) * 1.1 * R
            val cy = C + (0.1 - 0.15 * rnd.next()) * R
            val big = if (k == puffs / 2) 0.5 else 0.3 + 0.15 * rnd.next()
            val radius = min(R * big, R - hypot(cx - C, cy - C))
            parts.add(ring(48, start = 0.0, cx = cx, cy = cy) { radius })
        }
        return parts
    }

    // holes inside radius floor
    private fun puddle(rnd: MaskRandom): List<List<QuadPoint>> {
        val lobes = count(rnd, 6, 4)
        val parts = mutableListOf(blob(rnd, lobes, 0.62))
        repeat(count(rnd, 1, 3)) {
            val angle = rnd.next() * TAU
            val centre = polar(angle, rnd.next() * 0.22 * R)
            val n = count(rnd, 5, 3)
            val start = rnd.next() * TAU
            parts.add(ring(n, start = start, cx = centre.x, cy = centre.y) { R * (0.12 + 0.12 * rnd.next()) }.reversed())
        }
        return parts
    }

    private fun pixel(rnd: MaskRandom): List<List<QuadPoint>> {
        val n = 9
        val cell = (2 * R) / n
        val radii = List(7) { 0.55 + 0.45 * rnd.next() }
        val start = rnd.next() * TAU
        fun radius(angle: Double): Double {
            val u = (((angle - start) / TAU) % 1 + 1) % 1
            val k = (u * radii.size).toInt()
            val f = u * radii.size - k
            return R * (radii[k % radii.size] * (1 - f) + radii[(k + 1) % radii.size] * f)
        }
        val squares = mutableListOf<List<QuadPoint>>()
        for (gy in 0 until n) for (gx in 0 until n) {
            val x = C - R + gx * cell
            val y = C - R + gy * cell
            val mx = x + cell / 2 - C
            val my = y + cell / 2 - C
            if (hypot(mx, my) < radius(atan2(my, mx))) {
                squares.add(listOf(QuadPoint(x, y), QuadPoint(x + cell, y), QuadPoint(x + cell, y + cell), QuadPoint(x, y + cell)))
            }
        }
        return squares
    }

    private val families: List<Pair<String, (Int, Double, MaskRandom) -> List<Pair<Char, DoubleArray>>>> = listOf(
        "polygon" to { i, _, _ -> poly(listOf(ring(i + 3) { R })) },
        "squircle" to { i, _, _ ->
            val p = 2 + i * 1.5
            val f = { v: Double -> (if (v < 0) -1.0 else 1.0) * abs(v).pow(2 / p) }
            poly(listOf(sample(ARC) { a -> QuadPoint(C + R * f(cos(a)), C + R * f(sin(a))) }))
        },
        "rounded" to { _, t, _ -> poly(listOf(rrect(C - R, C - R, 2 * R, 2 * R, t * R))) },
        "pill" to { _, t, _ ->
            val h = 2 * R * (1 - 0.7 * t)
            poly(listOf(rrect(C - R, C - h / 2, 2 * R, h, h / 2)))
        },
        "star" to { i, _, _ -> poly(listOf(ring(2 * (i + 3)) { if (it % 2 != 0) 0.5 * R else R })) },
        "burst" to { i, _, _ -> poly(listOf(ring(2 * (8 + 2 * i)) { if (it % 2 != 0) 0.8 * R else R })) },
        "crown" to { _, _, rnd ->
            val n = 2 * count(rnd, 8, 7)
            poly(listOf(jittered(rnd, n, jitter = 0.3) { if (it % 2 != 0) R * (0.6 + 0.25 * rnd.next()) else R }))
        },
        "flower" to { i, _, _ -> smooth(listOf(ring(2 * (i + 3)) { if (it % 2 != 0) 0.6 * R else R })) },
        "seal" to { i, _, _ -> smooth(listOf(ring(2 * (8 + i)) { if (it % 2 != 0) 0.86 * R else R })) },
        "gear" to { i, _, _ -> poly(listOf(gear(5 + i), ring(48) { 0.3 * R }.reversed())) },
        "cross" to { _, t, _ -> poly(listOf(cross(R * (0.12 + 0.63 * t)))) },
        "ring" to { _, t, _ -> poly(listOf(ring(ARC) { R }, ring(ARC) { 0.78 * R * t }.reversed())) },
        "pie" to { _, t, _ -> poly(listOf(pie(0.25 + 0.7 * t))) },
        "moon" to { _, t, _ -> poly(listOf(moon(2 * R * (0.35 + 0.6 * t)))) },
        "blob" to { _, _, rnd ->
            val lobes = count(rnd, 5, 4)
            smooth(listOf(blob(rnd, lobes, 0.62)))
        },
        "splat" to { _, _, rnd -> smooth(listOf(splat(rnd))) },
        "puddle" to { _, _, rnd -> smooth(puddle(rnd)) },
        "cloud" to { _, _, rnd -> poly(cloud(rnd)) },
        "shard" to { _, _, rnd -> poly(listOf(shard(rnd))) },
        "torn" to { _, _, rnd -> poly(listOf(torn(rnd))) },
        "pixel" to { _, _, rnd -> poly(pixel(rnd)) },
        "heart" to { i, _, _ -> spin(i.toDouble() / VARIANTS, listOf(heart)) },
        "drop" to { i, _, _ -> spin(i.toDouble() / VARIANTS, listOf(drop)) },
        "leaf" to { i, _, _ -> spin(i.toDouble() / VARIANTS + 1.0 / 8, listOf(leaf)) },
        "egg" to { i, _, _ -> spin(i.toDouble() / VARIANTS, listOf(egg)) },
        "arrow" to { i, _, _ -> spin(i.toDouble() / VARIANTS, listOf(arrow)) },
        "bolt" to { i, _, _ -> spin(i.toDouble() / VARIANTS, listOf(bolt)) },
        "bubble" to { i, _, _ -> spin(i.toDouble() / VARIANTS, listOf(bubbleBody, bubbleTail)) },
    )

    /** Web declaration order. */
    val familyNames = families.map { it.first }

    val all: List<MaskShape> by lazy {
        val shapes = mutableListOf<MaskShape>()
        families.forEachIndexed { f, (name, make) ->
            for (i in 0 until VARIANTS) {
                val rnd = MaskRandom(f * 131 + i * 7 + 1)
                shapes.add(MaskShape("$name-${i + 1}", make(i, i.toDouble() / (VARIANTS - 1), rnd)))
            }
        }
        // shuffle as the web
        val rnd = MaskRandom(7)
        for (i in shapes.size - 1 downTo 1) {
            val j = (rnd.next() * (i + 1)).toInt()
            val swap = shapes[i]
            shapes[i] = shapes[j]
            shapes[j] = swap
        }
        shapes
    }

    val default: MaskShape by lazy { all.firstOrNull { it.id == "squircle-4" } ?: all[0] }

    /** The shapes of each family, in web declaration order, variants ascending. */
    val grouped: List<Pair<String, List<MaskShape>>> by lazy {
        val byFamily = all.groupBy { it.family }
        familyNames.map { name -> name to byFamily[name].orEmpty().sortedBy { it.variant } }
    }

    /** The square 0 to 100 box as an outline. */
    val square = MaskShape("square", poly(listOf(listOf(QuadPoint(0.0, 0.0), QuadPoint(100.0, 0.0), QuadPoint(100.0, 100.0), QuadPoint(0.0, 100.0)))))
}

/** dt-core MaskSpec: how the image sits in the mask's square and how large the square is exported, as the web's `paint()`. */
data class MaskSpec(val zoom: Double = 1.0, val offsetX: Double = 0.0, val offsetY: Double = 0.0, val scale: Double = 1.0) {
    fun side(width: Int?, height: Int?): Int {
        val base = if (width != null && height != null) min(width, height) / zoom else EMPTY_SIDE.toDouble()
        return max(16, min(MAX_SIDE, (base * scale).roundToInt()))
    }

    /** The image's frame in a square of [side], covering it at zoom 1: left, top, width, height. */
    fun imageFrame(width: Int, height: Int, side: Double): DoubleArray {
        if (width <= 0 || height <= 0) return doubleArrayOf(0.0, 0.0, side, side)
        val cover = max(side / width, side / height) * zoom
        val w = width * cover
        val h = height * cover
        return doubleArrayOf((side - w) / 2 + offsetX * side, (side - h) / 2 + offsetY * side, w, h)
    }

    /** Whether the image's frame covers the whole square, so no placeholder grey shows. */
    fun covers(width: Int, height: Int, side: Double): Boolean {
        val (x, y, w, h) = imageFrame(width, height, side)
        return x <= 0 && y <= 0 && x + w >= side && y + h >= side
    }

    companion object {
        const val MIN_ZOOM = 0.1
        const val MAX_ZOOM = 8.0

        // ponytail: 4096 side, as the SwiftUI tool; the web allows 8192
        const val MAX_SIDE = 4096
        const val EMPTY_SIDE = 1024

        /** Grey that fills the square where the image does not reach, as ARGB. */
        const val PLACEHOLDER = 0xFF9A9A9A.toInt()
    }
}
