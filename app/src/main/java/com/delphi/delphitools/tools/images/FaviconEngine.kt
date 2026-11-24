package com.delphi.delphitools.tools.images

import androidx.compose.ui.unit.IntRect
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** dt-ios FaviconEngine: the icon sizes, the crop square and the ICO container. */
object FaviconEngine {
    val sizes = listOf(16, 32, 48, 64, 128, 180, 192, 512)
    val icoSizes = setOf(16, 32, 48, 64)

    /** The web tool's snippet, word for word. */
    val snippet = """
        <link rel="icon" type="image/png" sizes="16x16" href="/favicon-16x16.png">
        <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
        <link rel="apple-touch-icon" sizes="180x180" href="/favicon-180x180.png">
        <link rel="icon" type="image/png" sizes="192x192" href="/favicon-192x192.png">
        <link rel="icon" type="image/png" sizes="512x512" href="/favicon-512x512.png">
    """.trimIndent()

    fun fileName(size: Int) = "favicon-${size}x$size.png"

    /** What a size is for, where it has a name. */
    fun role(size: Int): String? = when (size) {
        180 -> "Apple Touch"
        192 -> "Android"
        512 -> "PWA"
        in icoSizes -> "ICO"
        else -> null
    }

    /** Smallest crop: a tenth of the short side, never under one pixel per pixel of the 16 px icon. */
    fun minimumSide(width: Int, height: Int) = max(16, min(width, height) / 10)

    /** A square of [side] pixels in the middle of the image; null takes the largest. */
    fun centred(width: Int, height: Int, side: Int? = null): IntRect {
        val s = side ?: min(width, height)
        return clamp(((width - s) / 2).toFloat(), ((height - s) / 2).toFloat(), s.toFloat(), width, height)
    }

    /** The square at ([x], [y]) of [side] in whole pixels, at least [minimumSide] (or the whole short side of a smaller image), inside the image. */
    fun clamp(x: Float, y: Float, side: Float, width: Int, height: Int): IntRect {
        val full = min(width, height)
        val s = min(max(side.roundToInt(), min(minimumSide(width, height), full)), full)
        val left = min(max(x.roundToInt(), 0), width - s)
        val top = min(max(y.roundToInt(), 0), height - s)
        return IntRect(left, top, left + s, top + s)
    }

    /** An ICO file of PNG frames, smallest first; a size of 256 or more is written as 0, as the format asks. */
    fun ico(frames: List<Pair<Int, ByteArray>>): ByteArray {
        val ordered = frames.sortedBy { it.first }
        val out = ByteArrayOutputStream()
        fun put16(value: Int) {
            out.write(value and 0xFF)
            out.write(value shr 8 and 0xFF)
        }
        fun put32(value: Int) {
            put16(value and 0xFFFF)
            put16(value ushr 16 and 0xFFFF)
        }
        put16(0)
        put16(1)
        put16(ordered.size)
        var offset = 6 + 16 * ordered.size
        for ((size, png) in ordered) {
            val side = if (size < 256) size else 0
            out.write(side)
            out.write(side)
            out.write(0)
            out.write(0)
            // planes, bits per pixel
            put16(1)
            put16(32)
            put32(png.size)
            put32(offset)
            offset += png.size
        }
        ordered.forEach { out.write(it.second) }
        return out.toByteArray()
    }
}
