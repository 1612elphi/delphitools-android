package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.Rgb
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Pixels in upright coordinates, as opaque ARGB. */
interface ColourFrame {
    val width: Int
    val height: Int
    fun pixel(x: Int, y: Int): Int
}

/**
 * A YUV 4:2:0 camera frame as the sensor wrote it, read upright: `rotation` is the clockwise turn, 0, 90, 180 or 270,
 * that makes the sensor image upright on the display. Planes are copies, so the camera's buffer can go back at once.
 */
class YuvFrame(
    private val sensorWidth: Int,
    private val sensorHeight: Int,
    private val y: ByteArray,
    private val yRowStride: Int,
    private val u: ByteArray,
    private val v: ByteArray,
    private val uvRowStride: Int,
    private val uvPixelStride: Int,
    val rotation: Int,
) : ColourFrame {
    override val width = if (rotation % 180 == 0) sensorWidth else sensorHeight
    override val height = if (rotation % 180 == 0) sensorHeight else sensorWidth

    override fun pixel(x: Int, y: Int): Int {
        val (sx, sy) = when (rotation) {
            90 -> y to sensorHeight - 1 - x
            180 -> sensorWidth - 1 - x to sensorHeight - 1 - y
            270 -> sensorWidth - 1 - y to x
            else -> x to y
        }
        val luma = this.y[sy * yRowStride + sx].toInt() and 0xff
        val chroma = (sy / 2) * uvRowStride + (sx / 2) * uvPixelStride
        return ColourCameraEngine.yuvToArgb(luma, u[chroma].toInt() and 0xff, v[chroma].toInt() and 0xff)
    }
}

/** dt-core KMeansColourExtractor and the camera's sampling. */
object ColourCameraEngine {
    const val LOUPE_RADIUS = 15
    private const val TARGET = 100

    /** JFIF full-range BT.601, the camera's YUV_420_888. */
    fun yuvToArgb(y: Int, u: Int, v: Int): Int {
        val d = u - 128
        val e = v - 128
        val r = (y + 1.402 * e).roundToInt().coerceIn(0, 255)
        val g = (y - 0.344136 * d - 0.714136 * e).roundToInt().coerceIn(0, 255)
        val b = (y + 1.772 * d).roundToInt().coerceIn(0, 255)
        return (0xff shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** The pixel under a normalised point. */
    fun pixelAt(frame: ColourFrame, x: Double, y: Double): Pair<Int, Int> =
        (x * frame.width).toInt().coerceIn(0, frame.width - 1) to (y * frame.height).toInt().coerceIn(0, frame.height - 1)

    /** Up to `count` colours of the frame, or of its normalised `rect` (left, top, right, bottom), most common first. */
    fun extract(frame: ColourFrame, rect: DoubleArray? = null, count: Int = 6, random: Random = Random.Default): List<Rgb> =
        kmeans(downsample(frame, rect), count, random = random)

    /** The area nearest-sampled into a grid at most 100 on its long side, as RGB triples 0...1. */
    fun downsample(frame: ColourFrame, rect: DoubleArray? = null): List<DoubleArray> {
        val area = rect ?: doubleArrayOf(0.0, 0.0, 1.0, 1.0)
        val left = (area[0] * frame.width).toInt().coerceIn(0, frame.width - 1)
        val top = (area[1] * frame.height).toInt().coerceIn(0, frame.height - 1)
        val w = max(1, min(frame.width - left, (area[2] * frame.width).toInt() - left))
        val h = max(1, min(frame.height - top, (area[3] * frame.height).toInt() - top))
        val scale = TARGET.toDouble() / max(w, h)
        val dw = max(1, (w * scale).toInt())
        val dh = max(1, (h * scale).toInt())
        return List(dw * dh) { i ->
            val px = frame.pixel(left + ((i % dw + 0.5) * w / dw).toInt().coerceAtMost(w - 1), top + ((i / dw + 0.5) * h / dh).toInt().coerceAtMost(h - 1))
            doubleArrayOf((px shr 16 and 0xff) / 255.0, (px shr 8 and 0xff) / 255.0, (px and 0xff) / 255.0)
        }
    }

    /** Lloyd's over k-means++ seeding in RGB, stopping once no pixel changes cluster; clusters by size, largest first. */
    fun kmeans(pixels: List<DoubleArray>, count: Int, maxIterations: Int = 20, random: Random = Random.Default): List<Rgb> {
        if (pixels.isEmpty() || count <= 0) return emptyList()
        val k = min(count, pixels.size)
        val centroids = seed(pixels, k, random)
        val assignments = IntArray(pixels.size)
        for (iteration in 0 until maxIterations) {
            var changed = false
            for (i in pixels.indices) {
                val nearest = centroids.indices.minBy { PaletteExtractorEngine.distance(pixels[i], centroids[it]) }
                if (assignments[i] != nearest) {
                    assignments[i] = nearest
                    changed = true
                }
            }
            if (!changed) break
            val sums = Array(k) { DoubleArray(3) }
            val counts = IntArray(k)
            for (i in pixels.indices) {
                val c = assignments[i]
                for (j in 0..2) sums[c][j] += pixels[i][j]
                counts[c]++
            }
            for (c in 0 until k) if (counts[c] > 0) centroids[c] = DoubleArray(3) { sums[c][it] / counts[c] }
        }
        val sizes = IntArray(k)
        for (a in assignments) sizes[a]++
        return (0 until k).filter { sizes[it] > 0 }.sortedByDescending { sizes[it] }.map {
            Rgb(centroids[it][0].coerceIn(0.0, 1.0), centroids[it][1].coerceIn(0.0, 1.0), centroids[it][2].coerceIn(0.0, 1.0))
        }
    }

    private fun seed(pixels: List<DoubleArray>, k: Int, random: Random): MutableList<DoubleArray> {
        val centroids = mutableListOf(pixels[random.nextInt(pixels.size)])
        while (centroids.size < k) {
            val distances = DoubleArray(pixels.size) { i -> centroids.minOf { PaletteExtractorEngine.distance(pixels[i], it) } }
            val total = distances.sum()
            if (total <= 0) {
                while (centroids.size < k) centroids += centroids[0]
                break
            }
            var threshold = random.nextDouble(total)
            var chosen = pixels.size - 1
            for (i in pixels.indices) {
                threshold -= distances[i]
                if (threshold <= 0) {
                    chosen = i
                    break
                }
            }
            centroids += pixels[chosen]
        }
        return centroids
    }
}
