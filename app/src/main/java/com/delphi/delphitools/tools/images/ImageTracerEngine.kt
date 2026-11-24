package com.delphi.delphitools.tools.images

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/** dt-core TracerPreset: imagetracerjs 1.2.6 `optionpresets` keys, in the tool's menu order. */
enum class TracerPreset {
    Default, Posterized1, Posterized2, Posterized3, Curvy, Sharp, Detailed, Smoothed,
    Grayscale, FixedPalette, RandomSampling1, RandomSampling2, Artistic1, Artistic2, Artistic3, Artistic4,
}

/** The options imagetracerjs reads with sequential layering; the defaults are its `default` preset. */
data class TraceOptions(
    val ltres: Double = 1.0,
    val qtres: Double = 1.0,
    val pathOmit: Int = 8,
    val rightAngleEnhance: Boolean = true,
    val colourSampling: Int = 2,
    val numberOfColours: Int = 16,
    val minColourRatio: Double = 0.0,
    val colourQuantCycles: Int = 3,
    val strokeWidth: Double = 1.0,
    val lineFilter: Boolean = false,
    val scale: Double = 1.0,
    val roundCoords: Int = 1,
    val blurRadius: Int = 0,
    val blurDelta: Int = 20,
    /** RGBA entries; set, it replaces the sampled palette. */
    val palette: List<IntArray>? = null,
)

/** dt-core TraceSettings: the tracer's options as the tool shows them. */
data class TraceSettings(
    val preset: TracerPreset = TracerPreset.Default,
    val colours: Int = 16,
    val lineThreshold: Double = 1.0,
    val curveThreshold: Double = 1.0,
    val pathOmit: Double = 8.0,
    val strokeWidth: Double = 1.0,
    val scale: Double = 1.0,
    val blurRadius: Double = 0.0,
    val blurDelta: Double = 20.0,
    val colourSampling: Int = 2,
    val minColourRatio: Double = 0.0,
    val roundCoords: Double = 1.0,
) {
    /** A preset's values from imagetracerjs `optionpresets`; the scale is kept. */
    fun apply(chosen: TracerPreset): TraceSettings {
        val base = TraceSettings(preset = chosen, scale = scale)
        val least = LEAST_THRESHOLD
        return when (chosen) {
            TracerPreset.Default -> base
            TracerPreset.Posterized1 -> base.copy(colourSampling = 0, colours = 2)
            TracerPreset.Posterized2 -> base.copy(colours = 4, blurRadius = 5.0)
            TracerPreset.Posterized3 -> base.copy(pathOmit = 20.0, colourSampling = 0, colours = 3, blurRadius = 3.0, strokeWidth = 0.0)
            TracerPreset.Curvy -> base.copy(lineThreshold = least)
            TracerPreset.Sharp -> base.copy(curveThreshold = least)
            TracerPreset.Detailed -> base.copy(pathOmit = 0.0, roundCoords = 2.0, lineThreshold = 0.5, curveThreshold = 0.5, colours = 64)
            TracerPreset.Smoothed -> base.copy(blurRadius = 5.0, blurDelta = 64.0)
            TracerPreset.Grayscale -> base.copy(colourSampling = 0, colours = 7)
            TracerPreset.FixedPalette -> base.copy(colourSampling = 0, colours = 27)
            TracerPreset.RandomSampling1 -> base.copy(colourSampling = 1, colours = 8)
            TracerPreset.RandomSampling2 -> base.copy(colourSampling = 1, colours = 64)
            TracerPreset.Artistic1 -> base.copy(
                colourSampling = 0, pathOmit = 0.0, blurRadius = 5.0, blurDelta = 64.0, lineThreshold = least, strokeWidth = 2.0,
            )
            TracerPreset.Artistic2 -> base.copy(curveThreshold = least, colourSampling = 0, colours = 4, strokeWidth = 0.0)
            TracerPreset.Artistic3 -> base.copy(curveThreshold = 10.0, lineThreshold = 10.0, colours = 8)
            TracerPreset.Artistic4 -> base.copy(
                curveThreshold = 10.0, lineThreshold = 10.0, colours = 64, blurRadius = 5.0, blurDelta = 256.0, strokeWidth = 2.0,
            )
        }
    }

    /** The preset's own options with the tool's overrides on top, as dt-ios hands them to imagetracerjs. */
    val options: TraceOptions
        get() {
            val preset = presetOptions(preset)
            return preset.copy(
                numberOfColours = colours,
                ltres = maxOf(lineThreshold, LEAST_THRESHOLD),
                qtres = maxOf(curveThreshold, LEAST_THRESHOLD),
                pathOmit = pathOmit.toInt(),
                strokeWidth = strokeWidth,
                scale = scale,
                blurRadius = blurRadius.toInt(),
                blurDelta = blurDelta.toInt(),
                colourSampling = colourSampling,
                minColourRatio = minColourRatio,
                roundCoords = roundCoords.toInt(),
            )
        }

    companion object {
        val strokes = listOf(0.0, 0.5, 1.0, 2.0, 3.0, 5.0)
        val scales = listOf(0.5, 1.0, 2.0, 3.0, 5.0)

        /** imagetracer's zero is 0.01. */
        const val LEAST_THRESHOLD = 0.01

        val presets = listOf(
            TracerPreset.Default to "Default", TracerPreset.Posterized1 to "Monoposto", TracerPreset.Posterized2 to "Saul's World",
            TracerPreset.Posterized3 to "Powders", TracerPreset.Curvy to "Bouba", TracerPreset.Sharp to "Kiki",
            TracerPreset.Detailed to "Intricate", TracerPreset.Smoothed to "Papered", TracerPreset.Grayscale to "Mono",
            TracerPreset.FixedPalette to "Genlock", TracerPreset.RandomSampling1 to "Logan", TracerPreset.RandomSampling2 to "Clockwise",
            TracerPreset.Artistic1 to "Theories", TracerPreset.Artistic2 to "Dustbowl", TracerPreset.Artistic3 to "Warrant",
            TracerPreset.Artistic4 to "Sparkle",
        )

        fun number(value: Double): String =
            if (Math.rint(value) == value) value.toLong().toString() else String.format(Locale.ROOT, "%g", value).let {
                if ('e' in it) it else it.trimEnd('0').trimEnd('.')
            }

        fun strokeLabel(width: Double) = if (width == 0.0) "None" else number(width)

        fun smoothing(value: Double): String = if (value < 0.05) "0.01" else String.format(Locale.ROOT, "%.1f", value)

        // the preset keys dt-ios leaves to imagetracerjs
        private fun presetOptions(preset: TracerPreset) = when (preset) {
            TracerPreset.Curvy -> TraceOptions(lineFilter = true, rightAngleEnhance = false)
            TracerPreset.Grayscale, TracerPreset.FixedPalette, TracerPreset.Artistic2 -> TraceOptions(colourQuantCycles = 1)
            TracerPreset.Artistic1 -> TraceOptions(colourQuantCycles = 1, lineFilter = true)
            TracerPreset.Posterized3 -> TraceOptions(palette = listOf(intArrayOf(0, 0, 100, 255), intArrayOf(255, 255, 255, 255)))
            else -> TraceOptions()
        }
    }
}

/**
 * imagetracerjs 1.2.6 (Unlicense, András Jankovics), sequential layering, in Kotlin: colour quantisation, marching
 * squares path scan, interpolation, line and quadratic spline fitting, SVG output. Its quirks are kept, so a trace
 * matches what dt-ios produces through JavaScriptCore up to the random palette entries.
 */
object ImageTraceEngine {
    private class Point(val x: Double, val y: Double, val type: Int)

    private class ScanPath(val points: MutableList<Point>, val box: IntArray, val holes: MutableList<Int>) {
        var isHole = false
    }

    private class Segment(val type: Char, val x1: Double, val y1: Double, val x2: Double, val y2: Double, val x3: Double = 0.0, val y3: Double = 0.0)

    private class Traced(val segments: List<Segment>, val holes: List<Int>, val isHole: Boolean)

    /** Traces [rgba], premultiplied RGBA bytes of a [width] × [height] image, to SVG markup. */
    fun trace(rgba: ByteArray, width: Int, height: Int, options: TraceOptions, random: Random = Random.Default): String {
        val data = IntArray(rgba.size) { rgba[it].toInt() and 0xFF }
        val (array, palette) = quantise(data, width, height, options, random)
        val layers = palette.indices.map { colour ->
            val paths = interpolate(scan(layer(array, width + 2, height + 2, colour), width + 2, height + 2, options.pathOmit), options)
            paths.map { trace(it, options.ltres, options.qtres) }
        }
        return svg(layers, palette, width, height, options)
    }

    private fun quantise(source: IntArray, width: Int, height: Int, options: TraceOptions, random: Random): Pair<IntArray, MutableList<IntArray>> {
        var data = source
        val pixels = width * height
        val array = IntArray((width + 2) * (height + 2)) { -1 }
        val palette = (
            options.palette?.map { it.copyOf() } ?: when (options.colourSampling) {
                0 -> generatePalette(options.numberOfColours, random)
                1 -> List(options.numberOfColours) {
                    val at = floor(random.nextDouble() * data.size / 4).toInt() * 4
                    intArrayOf(data[at], data[at + 1], data[at + 2], data[at + 3])
                }
                else -> samplePalette(options.numberOfColours, data, width, height)
            }
            ).toMutableList()
        if (options.blurRadius > 0) data = blur(data, width, height, options.blurRadius, options.blurDelta)
        val acc = Array(palette.size) { LongArray(5) }
        for (cycle in 0 until options.colourQuantCycles) {
            if (cycle > 0) {
                for (k in palette.indices) {
                    val a = acc[k]
                    if (a[4] > 0) palette[k] = IntArray(4) { (a[it] / a[4]).toInt() }
                    if (a[4].toDouble() / pixels < options.minColourRatio && cycle < options.colourQuantCycles - 1) {
                        palette[k] = IntArray(4) { floor(random.nextDouble() * 255).toInt() }
                    }
                }
            }
            acc.forEach { it.fill(0) }
            for (j in 0 until height) {
                for (i in 0 until width) {
                    val idx = (j * width + i) * 4
                    var best = 0
                    var least = 1024
                    for (k in palette.indices) {
                        val p = palette[k]
                        val distance = abs(p[0] - data[idx]) + abs(p[1] - data[idx + 1]) + abs(p[2] - data[idx + 2]) + abs(p[3] - data[idx + 3])
                        if (distance < least) {
                            least = distance
                            best = k
                        }
                    }
                    val a = acc[best]
                    for (c in 0 until 4) a[c] += data[idx + c].toLong()
                    a[4]++
                    array[(j + 1) * (width + 2) + i + 1] = best
                }
            }
        }
        return array to palette
    }

    private fun samplePalette(count: Int, data: IntArray, width: Int, height: Int): List<IntArray> {
        val ni = ceil(sqrt(count.toDouble())).toInt()
        val nj = ceil(count.toDouble() / ni).toInt()
        val vx = width.toDouble() / (ni + 1)
        val vy = height.toDouble() / (nj + 1)
        val palette = mutableListOf<IntArray>()
        for (j in 0 until nj) for (i in 0 until ni) {
            if (palette.size == count) break
            val at = floor((j + 1) * vy * width + (i + 1) * vx).toInt() * 4
            palette.add(intArrayOf(data[at], data[at + 1], data[at + 2], data[at + 3]))
        }
        return palette
    }

    private fun generatePalette(count: Int, random: Random): List<IntArray> {
        if (count < 8) {
            val step = floor(255.0 / (count - 1)).toInt()
            return List(count) { intArrayOf(it * step, it * step, it * step, 255) }
        }
        val edge = floor(count.toDouble().pow(1.0 / 3)).toInt()
        val step = floor(255.0 / (edge - 1)).toInt()
        val palette = mutableListOf<IntArray>()
        for (r in 0 until edge) for (g in 0 until edge) for (b in 0 until edge) palette.add(intArrayOf(r * step, g * step, b * step, 255))
        repeat(count - edge * edge * edge) { palette.add(IntArray(4) { floor(random.nextDouble() * 255).toInt() }) }
        return palette
    }

    private val kernels = listOf(
        doubleArrayOf(0.27901, 0.44198, 0.27901),
        doubleArrayOf(0.135336, 0.228569, 0.272192, 0.228569, 0.135336),
        doubleArrayOf(0.086776, 0.136394, 0.178908, 0.195843, 0.178908, 0.136394, 0.086776),
        doubleArrayOf(0.063327, 0.093095, 0.122589, 0.144599, 0.152781, 0.144599, 0.122589, 0.093095, 0.063327),
        doubleArrayOf(0.049692, 0.069304, 0.089767, 0.107988, 0.120651, 0.125194, 0.120651, 0.107988, 0.089767, 0.069304, 0.049692),
    )

    // selective gaussian; the js skips index 0 of each row and column
    private fun blur(data: IntArray, width: Int, height: Int, blurRadius: Int, blurDelta: Int): IntArray {
        if (blurRadius < 1) return data
        val radius = minOf(blurRadius, 5)
        val delta = minOf(abs(blurDelta), 1024)
        val kernel = kernels[radius - 1]
        val horizontal = IntArray(data.size)
        val acc = DoubleArray(4)
        fun pass(from: IntArray, into: IntArray, vertical: Boolean) {
            for (j in 0 until height) for (i in 0 until width) {
                acc.fill(0.0)
                var weight = 0.0
                for (k in -radius..radius) {
                    val along = if (vertical) j + k else i + k
                    if (along <= 0 || along >= if (vertical) height else width) continue
                    val idx = if (vertical) ((j + k) * width + i) * 4 else (j * width + i + k) * 4
                    for (c in 0 until 4) acc[c] += from[idx + c] * kernel[k + radius]
                    weight += kernel[k + radius]
                }
                val idx = (j * width + i) * 4
                for (c in 0 until 4) into[idx + c] = floor(acc[c] / weight).toInt()
            }
        }
        pass(data, horizontal, vertical = false)
        val out = IntArray(data.size)
        pass(horizontal, out, vertical = true)
        for (idx in 0 until width * height * 4 step 4) {
            val d = (0 until 4).sumOf { abs(out[idx + it] - data[idx + it]) }
            if (d > delta) for (c in 0 until 4) out[idx + c] = data[idx + c]
        }
        return out
    }

    private fun layer(array: IntArray, w: Int, h: Int, colour: Int): IntArray {
        val layer = IntArray(w * h)
        for (j in 1 until h) for (i in 1 until w) {
            layer[j * w + i] = (if (array[(j - 1) * w + i - 1] == colour) 1 else 0) +
                (if (array[(j - 1) * w + i] == colour) 2 else 0) +
                (if (array[j * w + i - 1] == colour) 8 else 0) +
                (if (array[j * w + i] == colour) 4 else 0)
        }
        return layer
    }

    private val lookup = arrayOf(
        arrayOf(intArrayOf(-1, -1, -1, -1), intArrayOf(-1, -1, -1, -1), intArrayOf(-1, -1, -1, -1), intArrayOf(-1, -1, -1, -1)),
        arrayOf(intArrayOf(0, 1, 0, -1), intArrayOf(-1, -1, -1, -1), intArrayOf(-1, -1, -1, -1), intArrayOf(0, 2, -1, 0)),
        arrayOf(intArrayOf(-1, -1, -1, -1), intArrayOf(-1, -1, -1, -1), intArrayOf(0, 1, 0, -1), intArrayOf(0, 0, 1, 0)),
        arrayOf(intArrayOf(0, 0, 1, 0), intArrayOf(-1, -1, -1, -1), intArrayOf(0, 2, -1, 0), intArrayOf(-1, -1, -1, -1)),
        arrayOf(intArrayOf(-1, -1, -1, -1), intArrayOf(0, 0, 1, 0), intArrayOf(0, 3, 0, 1), intArrayOf(-1, -1, -1, -1)),
        arrayOf(intArrayOf(13, 3, 0, 1), intArrayOf(13, 2, -1, 0), intArrayOf(7, 1, 0, -1), intArrayOf(7, 0, 1, 0)),
        arrayOf(intArrayOf(-1, -1, -1, -1), intArrayOf(0, 1, 0, -1), intArrayOf(-1, -1, -1, -1), intArrayOf(0, 3, 0, 1)),
        arrayOf(intArrayOf(0, 3, 0, 1), intArrayOf(0, 2, -1, 0), intArrayOf(-1, -1, -1, -1), intArrayOf(-1, -1, -1, -1)),
        arrayOf(intArrayOf(0, 3, 0, 1), intArrayOf(0, 2, -1, 0), intArrayOf(-1, -1, -1, -1), intArrayOf(-1, -1, -1, -1)),
        arrayOf(intArrayOf(-1, -1, -1, -1), intArrayOf(0, 1, 0, -1), intArrayOf(-1, -1, -1, -1), intArrayOf(0, 3, 0, 1)),
        arrayOf(intArrayOf(11, 1, 0, -1), intArrayOf(14, 0, 1, 0), intArrayOf(14, 3, 0, 1), intArrayOf(11, 2, -1, 0)),
        arrayOf(intArrayOf(-1, -1, -1, -1), intArrayOf(0, 0, 1, 0), intArrayOf(0, 3, 0, 1), intArrayOf(-1, -1, -1, -1)),
        arrayOf(intArrayOf(0, 0, 1, 0), intArrayOf(-1, -1, -1, -1), intArrayOf(0, 2, -1, 0), intArrayOf(-1, -1, -1, -1)),
        arrayOf(intArrayOf(-1, -1, -1, -1), intArrayOf(-1, -1, -1, -1), intArrayOf(0, 1, 0, -1), intArrayOf(0, 0, 1, 0)),
        arrayOf(intArrayOf(0, 1, 0, -1), intArrayOf(-1, -1, -1, -1), intArrayOf(-1, -1, -1, -1), intArrayOf(0, 2, -1, 0)),
        arrayOf(intArrayOf(-1, -1, -1, -1), intArrayOf(-1, -1, -1, -1), intArrayOf(-1, -1, -1, -1), intArrayOf(-1, -1, -1, -1)),
    )

    private fun boxIncludes(parent: IntArray, child: IntArray) =
        parent[0] < child[0] && parent[1] < child[1] && parent[2] > child[2] && parent[3] > child[3]

    private fun pointInPoly(p: Point, polygon: List<Point>): Boolean {
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

    private fun scan(arr: IntArray, w: Int, h: Int, pathOmit: Int): List<ScanPath> {
        val paths = mutableListOf<ScanPath>()
        for (j in 0 until h) for (i in 0 until w) {
            val start = arr[j * w + i]
            if (start != 4 && start != 11) continue
            var px = i
            var py = j
            val path = ScanPath(mutableListOf(), intArrayOf(px, py, px, py), mutableListOf())
            val hole = start == 11
            var dir = 1
            while (true) {
                val value = arr[py * w + px]
                path.points.add(Point((px - 1).toDouble(), (py - 1).toDouble(), value))
                val box = path.box
                if (px - 1 < box[0]) box[0] = px - 1
                if (px - 1 > box[2]) box[2] = px - 1
                if (py - 1 < box[1]) box[1] = py - 1
                if (py - 1 > box[3]) box[3] = py - 1
                val row = lookup[value][dir]
                arr[py * w + px] = row[0]
                dir = row[1]
                px += row[2]
                py += row[3]
                if (px - 1 == path.points[0].x.toInt() && py - 1 == path.points[0].y.toInt()) break
            }
            if (path.points.size < pathOmit) continue
            path.isHole = hole
            if (hole) {
                var parent = 0
                var parentBox = intArrayOf(-1, -1, w + 1, h + 1)
                for (k in paths.indices) {
                    val candidate = paths[k]
                    if (!candidate.isHole && boxIncludes(candidate.box, path.box) && boxIncludes(parentBox, candidate.box) &&
                        pointInPoly(path.points[0], candidate.points)
                    ) {
                        parent = k
                        parentBox = candidate.box
                    }
                }
                // js: a first path that is a hole is its own parent
                (if (paths.isEmpty()) path else paths[parent]).holes.add(paths.size)
            }
            paths.add(path)
        }
        return paths
    }

    private fun direction(x1: Double, y1: Double, x2: Double, y2: Double): Int = when {
        x1 < x2 -> if (y1 < y2) 1 else if (y1 > y2) 7 else 0
        x1 > x2 -> if (y1 < y2) 3 else if (y1 > y2) 5 else 4
        else -> if (y1 < y2) 2 else if (y1 > y2) 6 else 8
    }

    private fun rightAngle(points: List<Point>, i1: Int, i2: Int, i3: Int, i4: Int, i5: Int): Boolean {
        val p = points
        return (p[i3].x == p[i1].x && p[i3].x == p[i2].x && p[i3].y == p[i4].y && p[i3].y == p[i5].y) ||
            (p[i3].y == p[i1].y && p[i3].y == p[i2].y && p[i3].x == p[i4].x && p[i3].x == p[i5].x)
    }

    private fun interpolate(paths: List<ScanPath>, options: TraceOptions): List<ScanPath> = paths.map { path ->
        val src = path.points
        val n = src.size
        val out = mutableListOf<Point>()
        for (i in 0 until n) {
            val next = (i + 1) % n
            val next2 = (i + 2) % n
            val prev = (i - 1 + n) % n
            val prev2 = (i - 2 + n) % n
            val midX = (src[i].x + src[next].x) / 2
            val midY = (src[i].y + src[next].y) / 2
            if (options.rightAngleEnhance && rightAngle(src, prev2, prev, i, next, next2)) {
                if (out.isNotEmpty()) {
                    val last = out.removeAt(out.lastIndex)
                    out.add(Point(last.x, last.y, direction(last.x, last.y, src[i].x, src[i].y)))
                }
                out.add(Point(src[i].x, src[i].y, direction(src[i].x, src[i].y, midX, midY)))
            }
            val nextMidX = (src[next].x + src[next2].x) / 2
            val nextMidY = (src[next].y + src[next2].y) / 2
            out.add(Point(midX, midY, direction(midX, midY, nextMidX, nextMidY)))
        }
        ScanPath(out, path.box, path.holes).also { it.isHole = path.isHole }
    }

    private fun trace(path: ScanPath, ltres: Double, qtres: Double): Traced {
        val points = path.points
        val segments = mutableListOf<Segment>()
        var pcnt = 0
        while (pcnt < points.size) {
            val type1 = points[pcnt].type
            var type2 = -1
            var seqEnd = pcnt + 1
            while (seqEnd < points.size &&
                (points[seqEnd].type == type1 || points[seqEnd].type == type2 || type2 == -1) && seqEnd < points.size - 1
            ) {
                if (points[seqEnd].type != type1 && type2 == -1) type2 = points[seqEnd].type
                seqEnd++
            }
            if (seqEnd == points.size - 1) seqEnd = 0
            segments.addAll(fit(points, ltres, qtres, pcnt, seqEnd))
            pcnt = if (seqEnd > 0) seqEnd else points.size
        }
        return Traced(segments, path.holes, path.isHole)
    }

    private fun fit(points: List<Point>, ltres: Double, qtres: Double, start: Int, end: Int): List<Segment> {
        val n = points.size
        if (end > n || end < 0) return emptyList()
        var errorPoint = start
        var errorValue = 0.0
        var pass = true
        var tl = (end - start).toDouble()
        if (tl < 0) tl += n
        val vx = (points[end].x - points[start].x) / tl
        val vy = (points[end].y - points[start].y) / tl
        var p = (start + 1) % n
        while (p != end) {
            var pl = (p - start).toDouble()
            if (pl < 0) pl += n
            val px = points[start].x + vx * pl
            val py = points[start].y + vy * pl
            val d2 = (points[p].x - px) * (points[p].x - px) + (points[p].y - py) * (points[p].y - py)
            if (d2 > ltres) pass = false
            if (d2 > errorValue) {
                errorPoint = p
                errorValue = d2
            }
            p = (p + 1) % n
        }
        if (pass) return listOf(Segment('L', points[start].x, points[start].y, points[end].x, points[end].y))

        val fitPoint = errorPoint
        pass = true
        errorValue = 0.0
        var t = (fitPoint - start) / tl
        var t1 = (1 - t) * (1 - t)
        var t2 = 2 * (1 - t) * t
        var t3 = t * t
        val cpx = (t1 * points[start].x + t3 * points[end].x - points[fitPoint].x) / -t2
        val cpy = (t1 * points[start].y + t3 * points[end].y - points[fitPoint].y) / -t2
        // js starts this walk unwrapped; an index past the end reads undefined, NaN, and changes nothing
        p = start + 1
        while (p != end) {
            if (p < n) {
                t = (p - start) / tl
                t1 = (1 - t) * (1 - t)
                t2 = 2 * (1 - t) * t
                t3 = t * t
                val px = t1 * points[start].x + t2 * cpx + t3 * points[end].x
                val py = t1 * points[start].y + t2 * cpy + t3 * points[end].y
                val d2 = (points[p].x - px) * (points[p].x - px) + (points[p].y - py) * (points[p].y - py)
                if (d2 > qtres) pass = false
                if (d2 > errorValue) {
                    errorPoint = p
                    errorValue = d2
                }
            }
            p = (p + 1) % n
        }
        if (pass) return listOf(Segment('Q', points[start].x, points[start].y, cpx, cpy, points[end].x, points[end].y))
        val split = fitPoint
        return fit(points, ltres, qtres, start, split) + fit(points, ltres, qtres, split, end)
    }

    /** JavaScript's `+value.toFixed(places)` printed as JavaScript prints a number. */
    private fun js(value: Double, places: Int? = null): String {
        val rounded = if (places == null) value else BigDecimal(value).setScale(places, RoundingMode.HALF_UP).toDouble()
        if (rounded.isNaN()) return "NaN"
        if (rounded.isInfinite()) return if (rounded > 0) "Infinity" else "-Infinity"
        if (rounded == 0.0) return "0"
        return BigDecimal(rounded.toString()).stripTrailingZeros().toPlainString()
    }

    private fun svg(layers: List<List<Traced>>, palette: List<IntArray>, width: Int, height: Int, options: TraceOptions): String {
        val out = StringBuilder()
        val s = options.scale
        out.append("<svg width=\"${js(width * s)}\" height=\"${js(height * s)}\" version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" desc=\"Created with imagetracer.js version 1.2.6\" >")
        for ((index, layer) in layers.withIndex()) {
            val c = palette[index]
            for (path in layer) {
                if (path.isHole) continue
                if (options.lineFilter && path.segments.size < 3) continue
                if (path.segments.isEmpty()) continue
                val r = options.roundCoords
                out.append("<path fill=\"rgb(${c[0]},${c[1]},${c[2]})\" stroke=\"rgb(${c[0]},${c[1]},${c[2]})\" stroke-width=\"${js(options.strokeWidth)}\" opacity=\"${js(c[3] / 255.0)}\" d=\"")
                val first = path.segments[0]
                out.append("M ${js(first.x1 * s, r)} ${js(first.y1 * s, r)} ")
                for (seg in path.segments) {
                    out.append("${seg.type} ${js(seg.x2 * s, r)} ${js(seg.y2 * s, r)} ")
                    if (seg.type == 'Q') out.append("${js(seg.x3 * s, r)} ${js(seg.y3 * s, r)} ")
                }
                out.append("Z ")
                // js rounds hole coordinates with toFixed(), no places
                for (hole in path.holes) {
                    val segments = layer[hole].segments
                    if (segments.isEmpty()) continue
                    val last = segments.last()
                    if (last.type == 'Q') out.append("M ${js(last.x3 * s, 0)} ${js(last.y3 * s, 0)} ") else out.append("M ${js(last.x2 * s, 0)} ${js(last.y2 * s, 0)} ")
                    for (seg in segments.asReversed()) {
                        out.append("${seg.type} ")
                        if (seg.type == 'Q') out.append("${js(seg.x2 * s, 0)} ${js(seg.y2 * s, 0)} ")
                        out.append("${js(seg.x1 * s, 0)} ${js(seg.y1 * s, 0)} ")
                    }
                    out.append("Z ")
                }
                out.append("\" />")
            }
        }
        out.append("</svg>")
        return out.toString()
    }
}

/**
 * dt-core TraceVectors: the SVG imagetracerjs writes, read back into paths, layer after layer in palette order.
 * Each layer's [fill] is commands: 'M' x y, 'L' x y, 'Q' cx cy x y, 'Z'; [lines] and [curves] are their segments,
 * 4 and 6 numbers each, in SVG units.
 */
class TraceVectors(val width: Double, val height: Double, val layers: List<Layer>) {
    class Layer(
        val red: Int,
        val green: Int,
        val blue: Int,
        val opacity: Double,
        val strokeWidth: Double,
        val fill: List<Pair<Char, DoubleArray>>,
        val lines: List<DoubleArray>,
        val curves: List<DoubleArray>,
        val nodes: List<DoubleArray>,
        val paths: Int,
    )

    val paths get() = layers.sumOf { it.paths }
    val lineCount get() = layers.sumOf { it.lines.size }
    val curveCount get() = layers.sumOf { it.curves.size }

    /** The layer colours that draw anything, as 0xRRGGBB. */
    val palette get() = layers.filter { it.opacity > 0 && it.paths > 0 }.map { (it.red shl 16) or (it.green shl 8) or it.blue }

    companion object {
        /** Null when the root has no size. */
        fun parse(svg: String): TraceVectors? {
            val (width, height) = SvgMarkup.size(svg) ?: return null
            class Builder(val red: Int, val green: Int, val blue: Int, val opacity: Double, val strokeWidth: Double) {
                val fill = mutableListOf<Pair<Char, DoubleArray>>()
                val lines = mutableListOf<DoubleArray>()
                val curves = mutableListOf<DoubleArray>()
                val nodes = mutableListOf<DoubleArray>()
                var paths = 0
            }
            val builders = LinkedHashMap<String, Builder>()
            for (element in svg.split("<path ").drop(1)) {
                val end = element.indexOf("/>").takeIf { it >= 0 } ?: continue
                val tag = " " + element.substring(0, end)
                val fill = SvgMarkup.attribute("fill", tag) ?: continue
                val data = SvgMarkup.attribute("d", tag) ?: continue
                val opacityText = SvgMarkup.attribute("opacity", tag)
                // palette entries can share an RGB and differ in alpha
                val builder = builders.getOrPut(fill + "|" + (opacityText ?: "1")) {
                    val rgb = Regex("\\d+").findAll(fill).map { it.value.toInt() }.toList()
                    Builder(
                        rgb.getOrElse(0) { 0 }, rgb.getOrElse(1) { 0 }, rgb.getOrElse(2) { 0 },
                        opacityText?.toDoubleOrNull() ?: 1.0,
                        SvgMarkup.attribute("stroke-width", tag)?.toDoubleOrNull() ?: 0.0,
                    )
                }
                builder.paths++
                val tokens = data.split(' ').filter { it.isNotEmpty() }
                var at = 0
                var penX = 0.0
                var penY = 0.0
                fun point(): DoubleArray? {
                    if (at + 1 >= tokens.size) return null
                    val x = tokens[at].toDoubleOrNull() ?: return null
                    val y = tokens[at + 1].toDoubleOrNull() ?: return null
                    at += 2
                    return doubleArrayOf(x, y)
                }
                while (at < tokens.size) {
                    val command = tokens[at++]
                    when (command) {
                        "M" -> point()?.let {
                            builder.fill.add('M' to it)
                            builder.nodes.add(it)
                            penX = it[0]
                            penY = it[1]
                        }
                        "L" -> point()?.let {
                            builder.fill.add('L' to it)
                            builder.lines.add(doubleArrayOf(penX, penY, it[0], it[1]))
                            builder.nodes.add(it)
                            penX = it[0]
                            penY = it[1]
                        }
                        "Q" -> {
                            val control = point() ?: continue
                            val to = point() ?: continue
                            builder.fill.add('Q' to doubleArrayOf(control[0], control[1], to[0], to[1]))
                            builder.curves.add(doubleArrayOf(penX, penY, control[0], control[1], to[0], to[1]))
                            builder.nodes.add(to)
                            penX = to[0]
                            penY = to[1]
                        }
                        "Z" -> builder.fill.add('Z' to DoubleArray(0))
                    }
                }
            }
            val layers = builders.values.map {
                Layer(it.red, it.green, it.blue, it.opacity, it.strokeWidth, it.fill, it.lines, it.curves, it.nodes, it.paths)
            }
            return TraceVectors(width, height, layers)
        }
    }
}
