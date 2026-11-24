package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.clamp01
import com.delphi.delphitools.colour.fromOklab
import com.delphi.delphitools.colour.oklab
import com.delphi.delphitools.colour.rgbOf
import kotlin.math.atan2
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/** An OKLab centroid and how many sampled pixels it holds. */
data class PaletteCluster(val id: Int, val l: Double, val a: Double, val b: Double, val count: Int) {
    val colour get() = fromOklab(l, a, b)
}

/** dt-ios PaletteExtractorEngine: k-means in OKLab over a downsampled image, ranked by strategy. */
object PaletteExtractorEngine {
    enum class Strategy(val title: String, val detail: String) {
        Dominant("Dominant", "The most prominent colours by area — what the image is mostly made of"),
        Vibrant("Vibrant", "The most saturated, punchy colours — the ones that pop"),
        Muted("Muted", "Understated, desaturated tones — the quiet palette"),
        Light("Light", "The brightest colours — highlights, glows, and washed-out tones"),
        Dark("Dark", "Deep shadows and rich darks — moody and grounding"),
        Warm("Warm", "Reds, oranges, yellows, and earthy tones"),
        Cool("Cool", "Blues, greens, and icy tones"),
        Accent("Accent", "Rare standout colours that are far from the dominant palette"),
    }

    const val MIN_COLOURS = 3
    const val MAX_COLOURS = 11
    const val MAX_CLUSTERS = 32

    /** Long side the image is sampled at. */
    const val SAMPLE_EDGE = 150
    private const val ITERATIONS = 20

    /** OKLab triples of the opaque pixels; `argb` is not premultiplied. */
    fun oklabPixels(argb: IntArray): List<DoubleArray> =
        argb.filter { (it ushr 24) / 255.0 >= 0.5 }.map { rgbOf(it).oklab() }

    /** Lloyd's over k-means++ seeding. */
    fun cluster(pixels: List<DoubleArray>, k: Int, random: Random = Random.Default): List<PaletteCluster> {
        if (pixels.isEmpty()) return emptyList()
        val centroids = seed(pixels, minOf(k, pixels.size), random)
        val assignments = IntArray(pixels.size)
        repeat(ITERATIONS) {
            for (i in pixels.indices) {
                var best = 0
                var bestDistance = Double.POSITIVE_INFINITY
                for (c in centroids.indices) {
                    val d = distance(pixels[i], centroids[c])
                    if (d < bestDistance) {
                        bestDistance = d
                        best = c
                    }
                }
                assignments[i] = best
            }
            val sums = Array(centroids.size) { DoubleArray(3) }
            val counts = IntArray(centroids.size)
            for (i in pixels.indices) {
                val c = assignments[i]
                for (j in 0..2) sums[c][j] += pixels[i][j]
                counts[c]++
            }
            for (c in centroids.indices) if (counts[c] > 0) centroids[c] = DoubleArray(3) { sums[c][it] / counts[c] }
        }
        val sizes = IntArray(centroids.size)
        for (a in assignments) sizes[a]++
        return centroids.indices.filter { sizes[it] > 0 }.map { PaletteCluster(it, centroids[it][0], centroids[it][1], centroids[it][2], sizes[it]) }
    }

    fun rank(clusters: List<PaletteCluster>, strategy: Strategy, count: Int): List<PaletteCluster> {
        val large = if (strategy == Strategy.Accent) dominantCentroids(clusters) else emptyList()
        return clusters.sortedByDescending { score(it, strategy, large) }.take(count)
    }

    private fun dominantCentroids(clusters: List<PaletteCluster>): List<DoubleArray> {
        val sorted = clusters.sortedByDescending { it.count }
        val threshold = if (sorted.isEmpty()) 0 else sorted[sorted.size / 3].count
        return sorted.filter { it.count >= threshold }.map { doubleArrayOf(it.l, it.a, it.b) }
    }

    private fun score(cluster: PaletteCluster, strategy: Strategy, large: List<DoubleArray>): Double {
        val chroma = sqrt(cluster.a * cluster.a + cluster.b * cluster.b)
        var hue = Math.toDegrees(atan2(cluster.b, cluster.a))
        if (hue < 0) hue += 360
        return when (strategy) {
            Strategy.Dominant -> cluster.count.toDouble()
            Strategy.Vibrant -> chroma
            Strategy.Muted -> 1 / (chroma + 0.001)
            Strategy.Light -> cluster.l
            Strategy.Dark -> 1 - cluster.l
            Strategy.Warm -> if (hue <= 70 || hue >= 320) chroma + 0.5 else chroma * 0.1
            Strategy.Cool -> if (hue in 150.0..300.0) chroma + 0.5 else chroma * 0.1
            // rare colours outrank common
            Strategy.Accent -> {
                val point = doubleArrayOf(cluster.l, cluster.a, cluster.b)
                (large.minOfOrNull { distance(point, it) } ?: Double.POSITIVE_INFINITY) / ln(cluster.count + 2.0)
            }
        }
    }

    private fun seed(pixels: List<DoubleArray>, k: Int, random: Random): MutableList<DoubleArray> {
        val centroids = mutableListOf(pixels[random.nextInt(pixels.size)])
        val nearest = DoubleArray(pixels.size) { Double.POSITIVE_INFINITY }
        while (centroids.size < k) {
            val last = centroids.last()
            var total = 0.0
            for (i in pixels.indices) {
                val d = distance(pixels[i], last)
                if (d < nearest[i]) nearest[i] = d
                total += nearest[i]
            }
            if (total <= 0) {
                while (centroids.size < k) centroids += centroids[0]
                break
            }
            var r = random.nextDouble(total)
            var index = 0
            for (i in pixels.indices) {
                r -= nearest[i]
                if (r <= 0) {
                    index = i
                    break
                }
            }
            centroids += pixels[index]
        }
        return centroids
    }

    internal fun distance(a: DoubleArray, b: DoubleArray): Double {
        val dl = a[0] - b[0]
        val da = a[1] - b[1]
        val db = a[2] - b[2]
        return dl * dl + da * da + db * db
    }
}

/** dt-ios PalettePainter: every pixel to its nearest palette colour in OKLab. */
object PalettePainter {
    fun nearest(colour: DoubleArray, palette: List<DoubleArray>): Int {
        var best = 0
        var bestDistance = Double.POSITIVE_INFINITY
        palette.forEachIndexed { index, entry ->
            val d = PaletteExtractorEngine.distance(colour, entry)
            if (d < bestDistance) {
                bestDistance = d
                best = index
            }
        }
        return best
    }

    /** Each palette colour's share of the clustered pixels; the shares sum to 1. */
    fun coverage(clusters: List<PaletteCluster>, palette: List<PaletteCluster>): List<Double> {
        val total = clusters.sumOf { it.count }.toDouble()
        if (total <= 0 || palette.isEmpty()) return palette.map { 0.0 }
        val targets = palette.map { doubleArrayOf(it.l, it.a, it.b) }
        val sums = DoubleArray(palette.size)
        for (cluster in clusters) sums[nearest(doubleArrayOf(cluster.l, cluster.a, cluster.b), targets)] += cluster.count.toDouble()
        return sums.map { it / total }
    }

    /** Pixel-weighted mean of the clusters. */
    fun mean(clusters: List<PaletteCluster>): Rgb {
        val total = maxOf(clusters.sumOf { it.count }, 1).toDouble()
        return fromOklab(
            clusters.sumOf { it.l * it.count } / total,
            clusters.sumOf { it.a * it.count } / total,
            clusters.sumOf { it.b * it.count } / total,
        )
    }

    // 5-bit rgb oklab grid
    private val grid by lazy {
        Array(1 shl 15) { index -> Rgb((index shr 10) / 31.0, (index shr 5 and 31) / 31.0, (index and 31) / 31.0).oklab() }
    }

    /** Repaints non-premultiplied ARGB `pixels` in place, alpha kept; false when `cancelled` stops it between rows. */
    fun paint(pixels: IntArray, width: Int, palette: List<Rgb>, cancelled: () -> Boolean = { false }): Boolean {
        if (palette.isEmpty()) return false
        val targets = palette.map { it.oklab() }
        val inks = palette.map { colour ->
            fun byte(v: Double) = (clamp01(v) * 255).roundToInt()
            (byte(colour.r) shl 16) or (byte(colour.g) shl 8) or byte(colour.b)
        }
        val lookup = IntArray(grid.size) { nearest(grid[it], targets) }
        for (start in pixels.indices step maxOf(width, 1)) {
            if (cancelled()) return false
            for (i in start until minOf(start + width, pixels.size)) {
                val p = pixels[i]
                val alpha = p and 0xFF000000.toInt()
                if (alpha == 0) continue
                val key = (p shr 19 and 31) shl 10 or ((p shr 11 and 31) shl 5) or (p shr 3 and 31)
                pixels[i] = alpha or inks[lookup[key]]
            }
        }
        return true
    }
}
