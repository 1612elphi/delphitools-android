package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.oklab
import com.delphi.delphitools.colour.parseHex
import com.delphi.delphitools.tools.colour.PaletteExtractorEngine.Strategy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PaletteExtractorEngineTest {
    @Test
    fun selfCheck() {
        val red = Rgb(1.0, 0.0, 0.0).oklab()
        val blue = Rgb(0.0, 0.0, 1.0).oklab()
        val pixels = List(30) { red } + List(10) { blue }
        val clusters = PaletteExtractorEngine.cluster(pixels, 2, Random(1))
        assertEquals(2, clusters.size)
        assertEquals(40, clusters.sumOf { it.count })
        assertEquals(30, PaletteExtractorEngine.rank(clusters, Strategy.Dominant, 1).first().count)
        assertTrue(PaletteExtractorEngine.rank(clusters, Strategy.Light, 1).first().l > blue[0])
        assertEquals(2, PaletteExtractorEngine.rank(clusters, Strategy.Dominant, 5).size)

        val flat = List(5) { doubleArrayOf(0.5, 0.0, 0.0) }
        assertEquals(1, PaletteExtractorEngine.cluster(flat, 3).size)
        assertEquals(1, PaletteExtractorEngine.cluster(listOf(doubleArrayOf(0.5, 0.0, 0.0)), PaletteExtractorEngine.MAX_CLUSTERS).size)
        assertTrue(PaletteExtractorEngine.cluster(emptyList(), PaletteExtractorEngine.MAX_CLUSTERS).isEmpty())
    }

    @Test
    fun opaquePixelsOnly() {
        assertEquals(1, PaletteExtractorEngine.oklabPixels(intArrayOf(0xFFFF0000.toInt(), 0x40FF0000)).size)
    }

    @Test
    fun painterSelfCheck() {
        fun cluster(id: Int, hex: String, count: Int) = parseHex(hex)!!.oklab().let { PaletteCluster(id, it[0], it[1], it[2], count) }
        val red = cluster(0, "FF0000", 30)
        val pink = cluster(1, "E0405A", 10)
        val blue = cluster(2, "0000FF", 60)
        val shares = PalettePainter.coverage(listOf(red, pink, blue), listOf(blue, red))
        assertEquals(2, shares.size)
        assertEquals(0.6, shares[0], 1e-9)
        assertEquals(0.4, shares[1], 1e-9)
        assertEquals(listOf(0.0), PalettePainter.coverage(emptyList(), listOf(red)))

        val grey = PalettePainter.mean(listOf(cluster(0, "000000", 1), cluster(1, "FFFFFF", 1))).oklab()
        assertEquals(0.5, grey[0], 1e-6)
        assertEquals(0.0, grey[1], 1e-6)

        val pixels = intArrayOf(0xFFE0405A.toInt(), 0xFF0000FF.toInt(), 0)
        assertTrue(PalettePainter.paint(pixels, 3, listOf(parseHex("0000FF")!!, parseHex("FF0000")!!)))
        assertArrayEquals(intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt(), 0), pixels)
        assertTrue(!PalettePainter.paint(pixels, 3, listOf(Rgb.white)) { true })
    }
}
