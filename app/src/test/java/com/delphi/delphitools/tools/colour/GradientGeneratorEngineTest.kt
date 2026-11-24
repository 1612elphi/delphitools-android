package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.oklch
import com.delphi.delphitools.tools.colour.GradientPaint.Shape
import com.delphi.delphitools.tools.colour.GradientPaint.Space
import com.delphi.delphitools.tools.colour.GradientPaint.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradientGeneratorEngineTest {
    private val red = Rgb(1.0, 0.0, 0.0)
    private val blue = Rgb(0.0, 0.0, 1.0)
    private val stops = listOf(Stop(red, 0.0), Stop(blue, 1.0))

    @Test
    fun paintSelfCheck() {
        val yellow = Rgb(1.0, 1.0, 0.0)
        assertTrue(GradientPaint.mix(yellow, blue, 0.5, Space.Oklch).oklch()[1] > GradientPaint.mix(yellow, blue, 0.5, Space.Srgb).oklch()[1] + 0.05)
        assertEquals(5, GradientPaint.ramp(stops, Space.Oklab, samples = 4).size)
        assertEquals(3, GradientPaint.ramp(listOf(Stop(red, 0.3)), Space.Srgb).size)
        assertEquals(
            "background: conic-gradient(from 45deg at 25% 50% in oklch,\n  #FF0000 0%,\n  #0000FF 100%);",
            GradientPaint.css(Shape.Conic(45.0, 0.25, 0.5), stops, Space.Oklch),
        )
        assertEquals(0.5, GradientPaint.colour(0.5, stops, Space.Srgb).r, 0.0)
        assertEquals(8192 to 91, GradientPaint.capped(9000, 100))
        assertEquals(8192 to 20, GradientPaint.capped(12000, 30))
    }

    @Test
    fun line() {
        val across = GradientPaint.line(90.0, 64.0, 32.0)
        assertEquals(0.0, across[0], 1e-9)
        assertEquals(16.0, across[1], 1e-9)
        assertEquals(64.0, across[2], 1e-9)
        val down = GradientPaint.line(180.0, 64.0, 32.0)
        assertEquals(0.0, down[1], 1e-9)
        assertEquals(32.0, down[3], 1e-9)
        assertEquals("radial-gradient(circle at 33.3% 50%, #FF0000 0%, #0000FF 100%)", GradientPaint.function(Shape.Radial(1.0 / 3, 0.5), stops, Space.Srgb))
    }

    @Test
    fun autoStep() {
        val start = listOf(Stop(Rgb(1.0, 1.0, 0.0), 0.0), Stop(blue, 1.0))
        val middle = GradientPaint.colour(0.5, start, Space.Oklch)
        val once = GradientStops.autoStep(start, { it.position }, start, Space.Oklch, ::Stop)
        assertEquals(5, once.size)
        assertTrue(GradientStops.canAutoStep(once.size))
        val sampled = GradientPaint.colour(0.5, once, Space.Srgb)
        assertTrue(kotlin.math.abs(sampled.r - middle.r) + kotlin.math.abs(sampled.g - middle.g) + kotlin.math.abs(sampled.b - middle.b) < 1e-9)
        val twice = GradientStops.autoStep(once, { it.position }, once, Space.Oklch, ::Stop)
        assertEquals(17, twice.size)
        assertFalse(GradientStops.canAutoStep(twice.size))
        assertEquals(0.75, GradientStops.widestGap(listOf(0.0, 0.4, 0.5, 1.0)), 1e-12)
    }

    @Test
    fun mesh() {
        val points = listOf(MeshPoint(0, 0f, 0f, red), MeshPoint(1, 1f, 1f, blue))
        val pixels = GradientMesh.render(points, 2, 2)!!
        assertTrue((pixels[0] shr 16 and 0xFF) >= 254 && (pixels[0] and 0xFF) <= 1)
        assertTrue((pixels[3] and 0xFF) >= 254 && (pixels[3] shr 16 and 0xFF) <= 1)
        assertEquals(0xFF, pixels[1] ushr 24)
        assertEquals(null, GradientMesh.render(points, 2, 2) { true })
        assertEquals("background: radial-gradient(circle at 0% 0%, #FF0000 0%, transparent 70%),\n  radial-gradient(circle at 100% 100%, #0000FF 0%, transparent 70%);", GradientMesh.css(points))
    }
}
