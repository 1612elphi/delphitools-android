package com.delphi.delphitools.tools.audiovideo

import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * dt-ios AVEditGIFEncoder, a port of lib/gif.ts: GIF89a with a 256-colour median-cut palette per frame, looping forever.
 */
// ponytail: opaque RGB565 buckets, no dithering, as iOS; rgb444, rgba4444 and single-image encodeGif are not ported
object VideoToGifEncoder {
    const val MAX_FRAMES = 300
    private const val MAX_CODE = 4096

    val trailer = byteArrayOf(0x3b)

    fun frameCount(start: Double, end: Double, fps: Int): Int = max(1, (abs(end - start) * fps).roundToInt())

    fun header(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("GIF89a".toByteArray())
        out.write(byteArrayOf(width.toByte(), (width shr 8).toByte(), height.toByte(), (height shr 8).toByte(), (7 shl 4).toByte(), 0, 0))
        out.write(byteArrayOf(0x21, 0xff.toByte(), 0x0b))
        out.write("NETSCAPE2.0".toByteArray())
        out.write(byteArrayOf(0x03, 0x01, 0, 0, 0))
        return out.toByteArray()
    }

    /** One frame of [argb] pixels, row by row; alpha is ignored. */
    fun frame(argb: IntArray, width: Int, height: Int, delayMs: Double): ByteArray {
        val quantised = quantise(argb, width * height)
        // browsers clamp under 20ms
        val delay = max(2, (delayMs / 10).roundToInt())
        val bits = Integer.numberOfTrailingZeros(quantised.tableSize)
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x21, 0xf9.toByte(), 0x04, (1 shl 2).toByte(), delay.toByte(), (delay shr 8).toByte(), 0, 0))
        out.write(byteArrayOf(0x2c, 0, 0, 0, 0, width.toByte(), (width shr 8).toByte(), height.toByte(), (height shr 8).toByte(), (0x80 or (bits - 1)).toByte()))
        out.write(quantised.palette)
        out.write(ByteArray(quantised.tableSize * 3 - quantised.palette.size))
        out.write(bits)
        val data = lzw(quantised.indices, bits)
        var offset = 0
        while (offset < data.size) {
            val end = min(offset + 255, data.size)
            out.write(end - offset)
            out.write(data, offset, end - offset)
            offset = end
        }
        out.write(0)
        return out.toByteArray()
    }

    private class Bucket(val r: Int, val g: Int, val b: Int, val count: Int) {
        fun channel(axis: Int) = when (axis) {
            0 -> r
            1 -> g
            else -> b
        }
    }

    private class Box(val buckets: List<Bucket>) {
        var axis = 0
        var spread = 0
        var weight = 0

        init {
            if (buckets.isNotEmpty()) {
                val lo = IntArray(3) { buckets[0].channel(it) }
                val hi = lo.copyOf()
                for (bucket in buckets) {
                    weight += bucket.count
                    for (a in 0 until 3) {
                        val v = bucket.channel(a)
                        if (v < lo[a]) lo[a] = v else if (v > hi[a]) hi[a] = v
                    }
                }
                for (a in 0 until 3) {
                    if (hi[a] - lo[a] > spread) {
                        spread = hi[a] - lo[a]
                        axis = a
                    }
                }
            }
        }
    }

    private fun medianCut(buckets: List<Bucket>, target: Int): List<Box> {
        val boxes = mutableListOf(Box(buckets))
        while (boxes.size < target) {
            var chosen = -1
            var widest = 0
            for ((i, box) in boxes.withIndex()) {
                if (box.buckets.size >= 2 && box.spread > widest) {
                    widest = box.spread
                    chosen = i
                }
            }
            if (chosen < 0) break
            val axis = boxes[chosen].axis
            val sorted = boxes[chosen].buckets.sortedBy { it.channel(axis) }
            val total = boxes[chosen].weight
            var seen = 0
            var split = 1
            for (i in 0 until sorted.size - 1) {
                seen += sorted[i].count
                if (seen * 2 >= total) {
                    split = i + 1
                    break
                }
            }
            boxes[chosen] = Box(sorted.subList(0, split))
            boxes.add(chosen + 1, Box(sorted.subList(split, sorted.size)))
        }
        return boxes
    }

    private fun key(pixel: Int) = (pixel shr 19 and 0x1f) shl 11 or ((pixel shr 10 and 0x3f) shl 5) or (pixel shr 3 and 0x1f)

    private class Quantised(val palette: ByteArray, val indices: ByteArray, val tableSize: Int)

    private fun quantise(argb: IntArray, pixels: Int): Quantised {
        val counts = IntArray(1 shl 16)
        for (p in 0 until pixels) counts[key(argb[p])]++
        val buckets = ArrayList<Bucket>()
        for (colour in counts.indices) {
            if (counts[colour] > 0) buckets += Bucket((colour shr 11) shl 3, (colour shr 5 and 0x3f) shl 2, (colour and 0x1f) shl 3, counts[colour])
        }
        val boxes = medianCut(buckets.ifEmpty { listOf(Bucket(0, 0, 0, 1)) }, 256)

        val palette = ByteArray(boxes.size * 3)
        val colourToIndex = ByteArray(1 shl 16)
        for ((index, box) in boxes.withIndex()) {
            var r = 0L
            var g = 0L
            var b = 0L
            for (bucket in box.buckets) {
                r += bucket.r.toLong() * bucket.count
                g += bucket.g.toLong() * bucket.count
                b += bucket.b.toLong() * bucket.count
                colourToIndex[(bucket.r shr 3) shl 11 or ((bucket.g shr 2) shl 5) or (bucket.b shr 3)] = index.toByte()
            }
            palette[index * 3] = (r.toDouble() / box.weight).roundToInt().toByte()
            palette[index * 3 + 1] = (g.toDouble() / box.weight).roundToInt().toByte()
            palette[index * 3 + 2] = (b.toDouble() / box.weight).roundToInt().toByte()
        }

        // gif minimum code size
        var tableSize = 4
        while (tableSize < boxes.size) tableSize *= 2

        val indices = ByteArray(pixels) { colourToIndex[key(argb[it])] }
        return Quantised(palette, indices, tableSize)
    }

    private class BitWriter(capacity: Int) {
        val bytes = ByteArrayOutputStream(capacity)
        private var accumulator = 0
        private var bits = 0

        fun emit(code: Int, size: Int) {
            accumulator = accumulator or (code shl bits)
            bits += size
            while (bits >= 8) {
                bytes.write(accumulator and 0xff)
                accumulator = accumulator ushr 8
                bits -= 8
            }
        }

        fun flush() {
            if (bits > 0) bytes.write(accumulator and 0xff)
            bits = 0
        }
    }

    internal fun lzw(indices: ByteArray, minCodeSize: Int): ByteArray {
        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        val out = BitWriter(indices.size / 2 + 16)
        var codeSize = minCodeSize + 1
        var next = endCode + 1
        val table = ShortArray(MAX_CODE shl 8) { -1 }
        val assignedKeys = IntArray(MAX_CODE)
        var assigned = 0

        out.emit(clearCode, codeSize)
        var prefix = if (indices.isEmpty()) endCode else indices[0].toInt() and 0xff
        for (i in 1 until indices.size) {
            val suffix = indices[i].toInt() and 0xff
            val key = prefix shl 8 or suffix
            val known = table[key]
            if (known >= 0) {
                prefix = known.toInt()
                continue
            }
            out.emit(prefix, codeSize)
            if (next == MAX_CODE) {
                out.emit(clearCode, codeSize)
                for (k in 0 until assigned) table[assignedKeys[k]] = -1
                assigned = 0
                next = endCode + 1
                codeSize = minCodeSize + 1
            } else {
                table[key] = next.toShort()
                assignedKeys[assigned++] = key
                next++
                if (next > 1 shl codeSize && codeSize < 12) codeSize++
            }
            prefix = suffix
        }
        out.emit(prefix, codeSize)
        if (indices.isNotEmpty()) out.emit(endCode, codeSize)
        out.flush()
        return out.bytes.toByteArray()
    }
}
