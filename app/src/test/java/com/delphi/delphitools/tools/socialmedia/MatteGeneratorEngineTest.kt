package com.delphi.delphitools.tools.socialmedia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatteGeneratorEngineTest {
    @Test
    fun selfCheck() {
        assertEquals(1350, MatteSpec(ratioW = 4, ratioH = 5).height)
        val tall = MatteSpec(ratioW = 9, ratioH = 16)
        assertEquals(1920, tall.height)
        assertEquals(4608, tall.widthLimit)
        val wide = MatteSpec(ratioW = 16, ratioH = 9)
        assertEquals(608, wide.height)
        assertEquals(8192, wide.widthLimit)

        assertEquals(MatteRect(10.0, 30.0, 80.0, 40.0), MatteSpec.fit(200.0, 100.0, 100.0, 100.0, 10.0))
        assertEquals(0.0, MatteSpec.fit(200.0, 100.0, 100.0, 100.0, 60.0).width, 0.0)
        assertEquals(MatteRect(-50.0, 0.0, 200.0, 100.0), MatteSpec.cover(200.0, 100.0, 100.0, 100.0, 1.0))
        assertEquals(MatteRgb(0, 106, 220), MatteRgb(20, 136, 250).shifted(-30))
    }

    @Test
    fun average() {
        assertEquals(MatteRgb(188, 188, 188), MatteEngine.average(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())))
        assertEquals(null, MatteEngine.average(IntArray(0)))
    }

    @Test
    fun blur() {
        assertEquals(3, MatteEngine.boxes(8.0).size)
        val flat = IntArray(64) { 0xFF336699.toInt() }
        assertTrue(MatteEngine.blur(flat, 8, 8, 3.0).all { it == 0xFF336699.toInt() })
        // one white pixel spreads and keeps its total
        val dot = IntArray(81) { if (it == 40) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        val spread = MatteEngine.blur(dot, 9, 9, 1.0)
        assertTrue((spread[40] and 0xFF) in 1..254)
        assertTrue((spread[41] and 0xFF) > 0)
    }
}
