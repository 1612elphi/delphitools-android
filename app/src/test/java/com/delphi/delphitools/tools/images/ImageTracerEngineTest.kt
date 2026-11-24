package com.delphi.delphitools.tools.images

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageTracerEngineTest {
    // expected file: this picture through dt-core imagetracer_v1.2.6.js under node, the same options
    private fun image(w: Int, h: Int) = ByteArray(w * h * 4).also { d ->
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            val r2 = (x - 9) * (x - 9) + (y - 7) * (y - 7)
            val c = when {
                r2 < 9 -> intArrayOf(255, 255, 255)
                r2 < 36 -> intArrayOf(220, 30, 40)
                (x + y) % 7 < 2 -> intArrayOf(20, 160, 60)
                else -> intArrayOf(30, 40, 200)
            }
            for (k in 0 until 3) d[i + k] = c[k].toByte()
            d[i + 3] = 255.toByte()
        }
    }

    @Test
    fun matchesImageTracerJs() {
        val expected = javaClass.getResource("/imagetracer-expected.txt")!!.readText().trim().lines()
        val cases = listOf(
            TraceOptions(numberOfColours = 4, colourSampling = 2),
            TraceOptions(numberOfColours = 4, colourSampling = 2, qtres = 0.01, roundCoords = 2, scale = 2.0, blurRadius = 2, blurDelta = 40),
            TraceOptions(
                numberOfColours = 3, colourSampling = 2, ltres = 0.01, lineFilter = true, rightAngleEnhance = false,
                pathOmit = 0, strokeWidth = 0.5,
            ),
        )
        cases.forEachIndexed { index, options ->
            assertEquals("case $index", expected[index], ImageTraceEngine.trace(image(20, 16), 20, 16, options))
        }
    }

    @Test
    fun settings() {
        val settings = TraceSettings(scale = 2.0).apply(TracerPreset.Sharp)
        assertTrue(settings.scale == 2.0 && settings.curveThreshold == TraceSettings.LEAST_THRESHOLD && settings.preset == TracerPreset.Sharp)
        assertEquals(TraceSettings.LEAST_THRESHOLD, settings.copy(lineThreshold = 0.0).options.ltres, 0.0)
        assertEquals(TracerPreset.entries.size, TraceSettings.presets.size)
        assertEquals("0.5", TraceSettings.number(0.5))
        assertEquals("3", TraceSettings.number(3.0))
        assertEquals(1, TraceSettings().apply(TracerPreset.Grayscale).options.colourQuantCycles)
        assertEquals(2, TraceSettings().apply(TracerPreset.Posterized3).options.palette?.size)
    }

    @Test
    fun vectors() {
        val svg = ImageTraceEngine.trace(image(20, 16), 20, 16, TraceOptions(numberOfColours = 4, colourSampling = 2))
        val vectors = TraceVectors.parse(svg)
        assertNotNull(vectors)
        vectors!!
        assertEquals(20.0, vectors.width, 0.0)
        assertTrue(vectors.paths > 0)
        assertEquals(svg.split("<path ").size - 1, vectors.paths)
        assertEquals(svg.split(" L ").size - 1, vectors.lineCount)
        assertEquals(svg.split(" Q ").size - 1, vectors.curveCount)
        assertTrue(0xFFFFFF in vectors.palette)
    }
}
