package com.delphi.delphitools.tools.images

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageDeskewEngineTest {
    private val square = listOf(QuadPoint(0.0, 0.0), QuadPoint(100.0, 0.0), QuadPoint(100.0, 100.0), QuadPoint(0.0, 100.0))

    // dt-core DeskewGrab.selfCheck
    @Test
    fun grabs() {
        assertEquals(DeskewGrab.Corner(0), DeskewGrab.at(QuadPoint(10.0, 12.0), square))
        assertEquals(DeskewGrab.Corner(2), DeskewGrab.at(QuadPoint(95.0, 104.0), square))
        assertEquals(DeskewGrab.Edge(0), DeskewGrab.at(QuadPoint(50.0, -8.0), square))
        assertEquals(DeskewGrab.Edge(1), DeskewGrab.at(QuadPoint(108.0, 50.0), square))
        assertEquals(DeskewGrab.Whole, DeskewGrab.at(QuadPoint(50.0, 50.0), square))
        assertNull(DeskewGrab.at(QuadPoint(200.0, 200.0), square))

        assertEquals(QuadPoint(120.0, 105.0), DeskewGrab.move(square, DeskewGrab.Corner(2), 50.0, 5.0, 120.0, 120.0)[2])
        val edge = DeskewGrab.move(square, DeskewGrab.Edge(3), -5.0, 10.0, 120.0, 120.0)
        assertTrue(edge[3] == QuadPoint(0.0, 110.0) && edge[0] == QuadPoint(0.0, 10.0) && edge[1] == square[1])
        assertEquals(square.map { QuadPoint(it.x + 20, it.y) }, DeskewGrab.move(square, DeskewGrab.Whole, 30.0, -3.0, 120.0, 120.0))
    }

    @Test
    fun projects() {
        val skewed = listOf(QuadPoint(20.0, 10.0), QuadPoint(190.0, 5.0), QuadPoint(180.0, 95.0), QuadPoint(10.0, 80.0))
        for ((corner, unit) in skewed.zip(listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0))) {
            val point = ImageDeskewEngine.project(unit.first, unit.second, skewed)
            assertEquals(corner.x, point.x, 1e-9)
            assertEquals(corner.y, point.y, 1e-9)
        }
        val centre = ImageDeskewEngine.project(0.5, 0.5, skewed)
        val (a, b, c, d) = listOf(skewed[0], skewed[2], skewed[1], skewed[3])
        val t = ((c.x - a.x) * (d.y - c.y) - (c.y - a.y) * (d.x - c.x)) / ((b.x - a.x) * (d.y - c.y) - (b.y - a.y) * (d.x - c.x))
        assertEquals(a.x + t * (b.x - a.x), centre.x, 1e-9)
        assertEquals(a.y + t * (b.y - a.y), centre.y, 1e-9)
        val middle = ImageDeskewEngine.project(0.5, 0.5, square)
        assertEquals(50.0, middle.x, 1e-9)
        assertEquals(50.0, middle.y, 1e-9)
        assertEquals(6, ImageDeskewEngine.grid(square, 4).size)
    }

    @Test
    fun sizes() {
        assertTrue(ImageDeskewEngine.isConvex(square))
        assertFalse(ImageDeskewEngine.isConvex(listOf(square[0], square[2], square[1], square[3])))
        assertFalse(ImageDeskewEngine.isConvex(square.reversed()))
        assertEquals(100.0 to 100.0, ImageDeskewEngine.outputSize(square))
        assertEquals(1000.0 to 707.0, ImageDeskewEngine.fitAspect(1000.0 to 300.0, DeskewAspect.all[1].ratio))
        assertEquals(750.0 to 1000.0, ImageDeskewEngine.fitAspect(200.0 to 1000.0, 4.0 / 3))
        assertEquals(8192.0 to 4096.0, ImageDeskewEngine.capped(16384.0 to 8192.0))
        assertEquals(1024 to 768, ImageDeskewEngine.target(ImageDeskewEngine.insetQuad(2000.0, 1000.0), DeskewAspect.all[4], 1024.0))
        val inset = ImageDeskewEngine.insetQuad(100.0, 50.0)
        assertEquals(QuadPoint(12.0, 6.0), inset[0])
        assertEquals(QuadPoint(88.0, 44.0), inset[2])
    }
}
