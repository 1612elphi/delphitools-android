package com.delphi.delphitools.tools.audiovideo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoToGifEncoderTest {
    private fun ByteArray.u(i: Int) = this[i].toInt() and 0xff

    private fun encode(frames: List<IntArray>, width: Int, height: Int, delayMs: Double): ByteArray {
        var bytes = VideoToGifEncoder.header(width, height)
        for (argb in frames) bytes += VideoToGifEncoder.frame(argb, width, height, delayMs)
        return bytes + VideoToGifEncoder.trailer
    }

    private class Walk(val extensions: List<Int>, val frames: Int, val trailed: Boolean, val images: List<Pair<Int, ByteArray>>)

    // 0x2c occurs in data
    private fun walk(bytes: ByteArray): Walk {
        val extensions = mutableListOf<Int>()
        val images = mutableListOf<Pair<Int, ByteArray>>()
        var i = 13
        fun subBlocks(): ByteArray {
            val data = mutableListOf<Byte>()
            while (bytes.u(i) != 0) {
                val size = bytes.u(i)
                for (k in 1..size) data += bytes[i + k]
                i += size + 1
            }
            i++
            return data.toByteArray()
        }
        while (i < bytes.size) {
            when (bytes.u(i)) {
                0x3b -> return Walk(extensions, images.size, true, images)
                0x21 -> {
                    extensions += bytes.u(i + 1)
                    i += 2
                    subBlocks()
                }
                0x2c -> {
                    val flags = bytes.u(i + 9)
                    i += 10
                    if (flags and 0x80 != 0) i += 3 * (2 shl (flags and 0x07))
                    val minCodeSize = bytes.u(i)
                    i++
                    images += minCodeSize to subBlocks()
                }
                else -> error("unknown block at $i")
            }
        }
        return Walk(extensions, images.size, false, images)
    }

    private fun unlzw(data: ByteArray, minCodeSize: Int): ByteArray {
        val clearCode = 1 shl minCodeSize
        val table = mutableListOf<ByteArray>()
        var previous: ByteArray? = null
        val out = mutableListOf<Byte>()
        var size = 0
        var accumulator = 0
        var bits = 0
        fun reset() {
            table.clear()
            for (k in 0 until clearCode) table += byteArrayOf(k.toByte())
            table += ByteArray(0)
            table += ByteArray(0)
            size = minCodeSize + 1
            previous = null
        }
        reset()
        for (byte in data) {
            accumulator = accumulator or ((byte.toInt() and 0xff) shl bits)
            bits += 8
            while (bits >= size) {
                val code = accumulator and ((1 shl size) - 1)
                accumulator = accumulator ushr size
                bits -= size
                if (code == clearCode) {
                    reset()
                    continue
                }
                if (code == clearCode + 1) return out.toByteArray()
                val last = previous
                val entry = if (code < table.size) table[code] else last?.let { it + it[0] } ?: return ByteArray(0)
                out += entry.toList()
                if (last != null && table.size < 4096) table += last + entry[0]
                if (table.size == 1 shl size && size < 12) size++
                previous = entry
            }
        }
        return out.toByteArray()
    }

    @Test
    fun lzwRoundTrips() {
        var seed = 1
        val noise = ByteArray(20000) {
            seed = seed * 1664525 + 1013904223
            (seed ushr 24).toByte()
        }
        // past 4096 codes
        assertArrayEquals(noise, unlzw(VideoToGifEncoder.lzw(noise, 8), 8))
        val run = ByteArray(5000) { 3 }
        assertArrayEquals(run, unlzw(VideoToGifEncoder.lzw(run, 2), 2))
    }

    @Test
    fun structure() {
        val red = IntArray(4) { 0xffff0000.toInt() }
        val blue = IntArray(4) { 0xff0000ff.toInt() }
        val bytes = encode(listOf(red, blue), 2, 2, 100.0)
        assertEquals("GIF89a", String(bytes, 0, 6))
        assertArrayEquals(byteArrayOf(2, 0, 2, 0, (7 shl 4).toByte()), bytes.copyOfRange(6, 11))
        assertEquals("NETSCAPE2.0", String(bytes, 16, 11))
        val walk = walk(bytes)
        assertEquals(2, walk.frames)
        assertTrue(walk.trailed)
        assertEquals(listOf(0xff, 0xf9, 0xf9), walk.extensions)

        val clamped = encode(listOf(intArrayOf(0xff000000.toInt())), 1, 1, 5.0)
        assertArrayEquals(byteArrayOf(0x21, 0xf9.toByte(), 0x04, (1 shl 2).toByte(), 2, 0), clamped.copyOfRange(32, 38))
    }

    @Test
    fun pixelsDecodeToTheirPaletteColours() {
        val colours = intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt(), 0xffffffff.toInt(), 0xff000000.toInt())
        val width = 5
        val height = 3
        val argb = IntArray(width * height) { colours[it % colours.size] }
        val frame = VideoToGifEncoder.frame(argb, width, height, 40.0)
        // gce 8, descriptor 10
        assertEquals(4, frame.u(4))
        val flags = frame.u(17)
        val tableSize = 2 shl (flags and 0x07)
        assertEquals(8, tableSize)
        val palette = frame.copyOfRange(18, 18 + tableSize * 3)
        val minCodeSize = frame.u(18 + tableSize * 3)
        assertEquals(3, minCodeSize)
        val bytes = VideoToGifEncoder.header(width, height) + frame + VideoToGifEncoder.trailer
        val (size, data) = walk(bytes).images.single()
        val indices = unlzw(data, size)
        assertEquals(width * height, indices.size)
        for ((p, index) in indices.withIndex()) {
            val i = index.toInt() * 3
            val rgb = (palette.u(i) shl 16) or (palette.u(i + 1) shl 8) or palette.u(i + 2)
            // rgb565: 255 reads 248
            val want = argb[p] and 0xffffff
            for (shift in listOf(16, 8, 0)) assertTrue(abs((rgb shr shift and 0xff) - (want shr shift and 0xff)) <= 7)
        }
    }

    private fun abs(v: Int) = kotlin.math.abs(v)

    @Test
    fun frameCount() {
        assertEquals(50, VideoToGifEncoder.frameCount(0.0, 5.0, 10))
        assertEquals(50, VideoToGifEncoder.frameCount(5.0, 0.0, 10))
        assertEquals(1, VideoToGifEncoder.frameCount(1.0, 1.0, 10))
        assertTrue(VideoToGifEncoder.frameCount(0.0, 30.1, 10) > VideoToGifEncoder.MAX_FRAMES)
    }

    @Test
    fun manyColoursFitInOneTable() {
        val argb = IntArray(64 * 64) { (0xff shl 24) or (it * 4099) }
        val frame = VideoToGifEncoder.frame(argb, 64, 64, 100.0)
        assertEquals(256, 2 shl (frame.u(17) and 0x07))
    }
}
