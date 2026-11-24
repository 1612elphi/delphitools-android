package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.fromOklab
import com.delphi.delphitools.colour.fromOklch
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.oklab
import com.delphi.delphitools.colour.oklch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** dt-ios GradientPaint: CSS gradient geometry, colour mixing and code. Sizes and points are in pixels, y down. */
object GradientPaint {
    enum class Space(val title: String, val css: String?) {
        Srgb("sRGB", null),
        Oklab("OKLab", "oklab"),
        Oklch("OKLCH", "oklch"),
    }

    sealed interface Shape {
        /** CSS: 0 up, clockwise. */
        data class Linear(val angle: Double) : Shape

        /** Farthest-corner circle; unit centre. */
        data class Radial(val x: Double, val y: Double) : Shape

        data class Conic(val angle: Double, val x: Double, val y: Double) : Shape
    }

    /** `position` 0...1. */
    data class Stop(val colour: Rgb, val position: Double)

    // css4 powerless hue
    private const val ACHROMATIC = 0.0005

    fun mix(a: Rgb, b: Rgb, t: Double, space: Space): Rgb = when (space) {
        Space.Srgb -> Rgb(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t)
        Space.Oklab -> {
            val p = a.oklab()
            val q = b.oklab()
            fromOklab(p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t, p[2] + (q[2] - p[2]) * t)
        }
        Space.Oklch -> {
            val p = a.oklch()
            val q = b.oklch()
            var from = p[2]
            var to = q[2]
            if (p[1] < ACHROMATIC) from = to
            if (q[1] < ACHROMATIC) to = from
            // shorter hue, css default
            var turn = (to - from) % 360
            if (turn > 180) turn -= 360 else if (turn < -180) turn += 360
            fromOklch(p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t, from + turn * t)
        }
    }

    /** The stops padded to 0 and 1; OKLab and OKLCH sampled into sRGB segments. */
    fun ramp(stops: List<Stop>, space: Space, samples: Int = 24): List<Stop> {
        val sorted = stops.sortedBy { it.position }
        if (sorted.isEmpty()) return emptyList()
        val padded = sorted.toMutableList()
        if (sorted.first().position > 0) padded.add(0, Stop(sorted.first().colour, 0.0))
        if (sorted.last().position < 1) padded += Stop(sorted.last().colour, 1.0)
        if (space == Space.Srgb) return padded
        val out = mutableListOf(padded[0])
        for ((a, b) in padded.zipWithNext()) {
            if (b.position > a.position && a.colour != b.colour) {
                for (step in 1 until samples) {
                    val t = step.toDouble() / samples
                    out += Stop(mix(a.colour, b.colour, t, space), a.position + (b.position - a.position) * t)
                }
            }
            out += b
        }
        return out
    }

    fun colour(t: Double, stops: List<Stop>, space: Space): Rgb {
        val sorted = stops.sortedBy { it.position }
        val first = sorted.firstOrNull() ?: return Rgb(0.5, 0.5, 0.5)
        if (t <= first.position) return first.colour
        val upper = sorted.indexOfFirst { it.position >= t }
        if (upper < 0) return sorted.last().colour
        val a = sorted[upper - 1]
        val b = sorted[upper]
        val span = b.position - a.position
        return if (span > 0) mix(a.colour, b.colour, (t - a.position) / span, space) else b.colour
    }

    /** The CSS gradient line across a `width` by `height` box: start and end as x0, y0, x1, y1. */
    fun line(angle: Double, width: Double, height: Double): DoubleArray {
        val radians = Math.toRadians(angle)
        val dx = sin(radians)
        val dy = -cos(radians)
        val half = (abs(width * dx) + abs(height * dy)) / 2
        val cx = width / 2
        val cy = height / 2
        return doubleArrayOf(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half)
    }

    fun farthestCorner(x: Double, y: Double, width: Double, height: Double) = hypot(max(x, width - x), max(y, height - y))

    const val MAX_EDGE = 8192

    /** The export size, longest side capped at MAX_EDGE. */
    fun capped(width: Int, height: Int): Pair<Int, Int> {
        val cap = minOf(1.0, MAX_EDGE.toDouble() / maxOf(width, height, 1))
        return Math.round(width * cap).toInt() to Math.round(height * cap).toInt()
    }

    private fun percent(value: Double): String {
        val rounded = Math.round(value * 1000) / 10.0
        return if (rounded == Math.round(rounded).toDouble()) "${Math.round(rounded)}%" else "$rounded%"
    }

    private fun degrees(angle: Double) = "${Math.round(angle)}deg"

    fun css(shape: Shape, stops: List<Stop>, space: Space) = "background: ${function(shape, stops, space, ",\n  ")};"

    fun function(shape: Shape, stops: List<Stop>, space: Space, separator: String = ", "): String {
        val list = stops.sortedBy { it.position }.map { "${it.colour.hex} ${percent(it.position)}" }
        val method = space.css?.let { " in $it" } ?: ""
        val head = when (shape) {
            is Shape.Linear -> "linear-gradient(${degrees(shape.angle)}$method"
            is Shape.Radial -> "radial-gradient(circle at ${percent(shape.x)} ${percent(shape.y)}$method"
            is Shape.Conic -> "conic-gradient(from ${degrees(shape.angle)} at ${percent(shape.x)} ${percent(shape.y)}$method"
        }
        return "$head$separator${list.joinToString(separator)})"
    }

    /** SwiftUI blends in sRGB, so the ramp is sampled. */
    fun swiftUI(shape: Shape, stops: List<Stop>, space: Space, width: Double, height: Double): String {
        fun number(value: Double): String {
            val rounded = Math.round(value * 1000) / 1000.0
            return if (rounded == Math.round(rounded).toDouble()) "${Math.round(rounded)}" else "$rounded"
        }
        fun unit(x: Double, y: Double) = "UnitPoint(x: ${number(x)}, y: ${number(y)})"
        val lines = ramp(stops, space, samples = 6).map { stop ->
            val c = stop.colour
            "        .init(color: Color(red: ${number(c.r)}, green: ${number(c.g)}, blue: ${number(c.b)}), location: ${number(stop.position)}),"
        }
        val list = "    stops: [\n${lines.joinToString("\n")}\n    ],"
        val body = when (shape) {
            is Shape.Linear -> {
                val l = line(shape.angle, width, height)
                "LinearGradient(\n$list\n    startPoint: ${unit(l[0] / width, l[1] / height)},\n    endPoint: ${unit(l[2] / width, l[3] / height)}\n)"
            }
            is Shape.Radial -> {
                val radius = farthestCorner(shape.x * width, shape.y * height, width, height)
                "RadialGradient(\n$list\n    center: ${unit(shape.x, shape.y)},\n    startRadius: 0,\n    endRadius: ${number(radius)}\n)"
            }
            is Shape.Conic -> "AngularGradient(\n$list\n    center: ${unit(shape.x, shape.y)},\n    angle: .degrees(${number(shape.angle - 90)})\n)"
        }
        return body + "\n.frame(width: ${width.toInt()}, height: ${height.toInt()})"
    }

    const val NOISE_SIDE = 256

    /** The grain tile's grey levels: xorshift, so the grain is the same every time. */
    val noise: ByteArray by lazy {
        var state = 0x9E3779B9.toInt()
        ByteArray(NOISE_SIDE * NOISE_SIDE) {
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            state.toByte()
        }
    }
}

/** A mesh point at unit coordinates. */
data class MeshPoint(val id: Long, val x: Float, val y: Float, val colour: Rgb)

/** dt-ios GradientGeneratorViewModel's mesh: inverse-distance blending in OKLab. */
object GradientMesh {
    /** Distance cubed, so each point holds a defined blob. */
    fun weight(dx: Double, dy: Double): Double {
        val distSq = dx * dx + dy * dy
        return 1 / (distSq * sqrt(distSq) + 1e-10)
    }

    fun colour(points: List<MeshPoint>, x: Double, y: Double): Rgb {
        var total = 0.0
        val sum = DoubleArray(3)
        for (point in points) {
            val w = weight(x - point.x, y - point.y)
            val ok = point.colour.oklab()
            total += w
            for (i in 0..2) sum[i] += w * ok[i]
        }
        return fromOklab(sum[0] / total, sum[1] / total, sum[2] / total)
    }

    /** Opaque ARGB pixels, row by row; null when `cancelled` stops it between rows. */
    fun render(points: List<MeshPoint>, width: Int, height: Int, cancelled: () -> Boolean = { false }): IntArray? {
        val labs = points.map { it.colour.oklab() }
        val out = IntArray(width * height)
        fun byte(v: Double) = (v * 255).coerceIn(0.0, 255.0).toInt()
        for (py in 0 until height) {
            if (cancelled()) return null
            val ny = py.toDouble() / maxOf(height - 1, 1)
            for (px in 0 until width) {
                val nx = px.toDouble() / maxOf(width - 1, 1)
                var total = 0.0
                var l = 0.0
                var a = 0.0
                var b = 0.0
                for (i in points.indices) {
                    val w = weight(nx - points[i].x, ny - points[i].y)
                    total += w
                    l += w * labs[i][0]
                    a += w * labs[i][1]
                    b += w * labs[i][2]
                }
                val rgb = fromOklab(l / total, a / total, b / total)
                out[py * width + px] = (0xFF shl 24) or (byte(rgb.r) shl 16) or (byte(rgb.g) shl 8) or byte(rgb.b)
            }
        }
        return out
    }

    fun css(points: List<MeshPoint>): String {
        val layers = points.joinToString(",\n  ") { "radial-gradient(circle at ${(it.x * 100).toInt()}% ${(it.y * 100).toInt()}%, ${it.colour.hex} 0%, transparent 70%)" }
        return "background: $layers;"
    }
}

/** dt-ios GradientGeneratorViewModel's stop editing. */
object GradientStops {
    /** Stops Auto Step adds between each pair, evenly spaced. */
    const val AUTO_STEPS = 3

    /** Auto Step is off once its result would pass this many stops. */
    const val AUTO_STEP_LIMIT = 17

    fun canAutoStep(count: Int) = count >= 2 && (count - 1) * (AUTO_STEPS + 1) + 1 <= AUTO_STEP_LIMIT

    /** The middle of the widest gap between stops. */
    fun widestGap(positions: List<Double>): Double {
        val sorted = positions.sorted()
        return sorted.zipWithNext().maxByOrNull { (a, b) -> b - a }?.let { (a, b) -> (a + b) / 2 } ?: 0.5
    }

    /**
     * AUTO_STEPS new stops between each pair, sampled from the gradient in its space, so an sRGB ramp through
     * them keeps the look. `make` builds a stop from a colour and position.
     */
    fun <T> autoStep(sorted: List<T>, position: (T) -> Double, paint: List<GradientPaint.Stop>, space: GradientPaint.Space, make: (Rgb, Double) -> T): List<T> {
        val result = mutableListOf<T>()
        for ((a, b) in sorted.zipWithNext()) {
            result += a
            if (position(b) <= position(a)) continue
            for (step in 1..AUTO_STEPS) {
                val at = position(a) + (position(b) - position(a)) * step / (AUTO_STEPS + 1)
                result += make(GradientPaint.colour(at, paint, space), at)
            }
        }
        result += sorted.last()
        return result
    }
}
