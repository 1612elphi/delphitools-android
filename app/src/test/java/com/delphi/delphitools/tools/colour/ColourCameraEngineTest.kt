package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.Rgb
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class ColourCameraEngineTest {
    private fun frame(rotation: Int): YuvFrame {
        val y = ByteArray(8) { (10 * (it + 1)).toByte() }
        val chroma = ByteArray(4) { 128.toByte() }
        return YuvFrame(4, 2, y, 4, chroma, chroma, 2, 1, rotation)
    }

    private fun luma(frame: ColourFrame, x: Int, y: Int) = frame.pixel(x, y) and 0xff

    @Test
    fun neutralChromaIsGrey() {
        assertEquals(0xff808080.toInt(), ColourCameraEngine.yuvToArgb(128, 128, 128))
        assertEquals(0xffffffff.toInt(), ColourCameraEngine.yuvToArgb(255, 128, 128))
    }

    @Test
    fun rotatesToUpright() {
        val plain = frame(0)
        assertEquals(4 to 2, plain.width to plain.height)
        assertEquals(20, luma(plain, 1, 0))
        // clockwise turn
        val turned = frame(90)
        assertEquals(2 to 4, turned.width to turned.height)
        assertEquals(50, luma(turned, 0, 0))
        assertEquals(10, luma(turned, 1, 0))
        assertEquals(40, luma(turned, 1, 3))
        assertEquals(80, luma(frame(180), 0, 0))
        assertEquals(40, luma(frame(270), 0, 0))
    }

    @Test
    fun kmeansFindsBothColoursLargestFirst() {
        val pixels = List(30) { doubleArrayOf(1.0, 0.0, 0.0) } + List(10) { doubleArrayOf(0.0, 0.0, 1.0) }
        assertEquals(listOf(Rgb(1.0, 0.0, 0.0), Rgb(0.0, 0.0, 1.0)), ColourCameraEngine.kmeans(pixels, 6, random = Random(1)).distinct())
        assertEquals(emptyList<Rgb>(), ColourCameraEngine.kmeans(emptyList(), 6))
    }

    @Test
    fun extractsFromTheRegionOnly() {
        val split = object : ColourFrame {
            override val width = 200
            override val height = 100
            override fun pixel(x: Int, y: Int) = if (x < 100) 0xffff0000.toInt() else 0xff0000ff.toInt()
        }
        assertEquals(listOf(Rgb(0.0, 0.0, 1.0)), ColourCameraEngine.extract(split, doubleArrayOf(0.6, 0.2, 0.9, 0.8), random = Random(2)).distinct())
        assertEquals(100 * 50, ColourCameraEngine.downsample(split).size)
    }
}
