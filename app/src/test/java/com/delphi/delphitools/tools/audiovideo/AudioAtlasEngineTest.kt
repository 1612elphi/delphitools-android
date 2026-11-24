package com.delphi.delphitools.tools.audiovideo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class AudioAtlasEngineTest {
    @Test
    fun fftFindsTheBin() {
        val n = 256
        val bin = 8
        val wave = DoubleArray(n) { sin(2 * PI * bin * it / n) }
        val magnitudes = AudioAtlasEngine.fftMagnitudes(wave)
        assertEquals(n / 2, magnitudes.size)
        assertEquals(bin, magnitudes.indices.maxBy { magnitudes[it] })
        assertEquals(0, AudioAtlasEngine.fftMagnitudes(doubleArrayOf(1.0, 2.0, 3.0)).size)
    }

    @Test
    fun rulerStepsAndLabels() {
        assertEquals(1.0, AudioAtlasEngine.rulerStep(10.0, 640.0), 0.0)
        assertEquals(0.01, AudioAtlasEngine.rulerStep(0.05, 320.0), 0.0)
        assertEquals(3600.0, AudioAtlasEngine.rulerStep(100_000.0, 320.0), 0.0)
        assertEquals("1.5", AudioAtlasEngine.rulerLabel(1.5, 0.5))
        assertEquals("1:15", AudioAtlasEngine.rulerLabel(75.0, 15.0))
        assertEquals("1:01.25", AudioAtlasEngine.rulerLabel(61.25, 0.05))
        assertEquals("5.5 kHz", AudioAtlasEngine.kilohertz(44100, 1))
        assertEquals("12 kHz", AudioAtlasEngine.kilohertz(48000, 2))
    }

    @Test
    fun scanOfAFullScaleSquare() {
        val rate = 48000
        val scanner = AudioScanner(rate, 2, 0, rate.toLong(), 4, open = true, measureLoudness = true)
        val samples = FloatArray(rate * 2) { if (it / 2 % 2 == 0) 1f else -1f }
        scanner.feed(samples, rate, 0)
        val scan = scanner.finish()
        assertEquals(0.0, scan.peakDb, 1e-9)
        assertEquals(0.0, scan.rmsDb, 1e-9)
        assertEquals(1.0, scan.duration, 1e-9)
        assertArrayEquals(floatArrayOf(-1f, -1f, -1f, -1f), scan.peaks.min, 0f)
        assertNotNull(scan.lufs)
    }

    @Test
    fun windowSkipsFramesOutsideIt() {
        val scanner = AudioScanner(100, 1, 50, 10, 2, open = false, measureLoudness = false)
        scanner.feed(FloatArray(100) { if (it in 50 until 55) 0.5f else if (it in 55 until 60) -0.25f else 1f }, 100, 0)
        val scan = scanner.finish()
        assertArrayEquals(floatArrayOf(0.5f, -0.25f), scan.peaks.max, 0f)
        assertNull(scan.lufs)
        assertTrue(abs(scan.peakDb - 20 * kotlin.math.log10(0.5)) < 1e-6)
    }

    @Test
    fun fivePointOneSkipsLfeAndWeightsSurrounds() {
        val rate = 48000
        fun lufs(channel: Int): Double {
            val scanner = AudioScanner(rate, 6, 0, rate * 2L, 1, open = true, measureLoudness = true)
            scanner.feed(FloatArray(rate * 2 * 6) { if (it % 6 == channel) sin(2 * PI * 997 * (it / 6) / rate).toFloat() else 0f }, rate * 2, 0)
            return scanner.finish().lufs!!
        }
        assertEquals(-3.01, lufs(0), 0.1)
        assertEquals(-3.01 + 10 * kotlin.math.log10(1.41), lufs(4), 0.1)
        assertNull(AudioScanner(rate, 6, 0, rate * 2L, 1, open = true, measureLoudness = true).apply {
            feed(FloatArray(rate * 2 * 6) { if (it % 6 == 3) 1f else 0f }, rate * 2, 0)
        }.finish().lufs)
    }

    @Test
    fun silenceIsMinusInfinity() {
        val scanner = AudioScanner(100, 1, 0, 100, 1, open = true, measureLoudness = true)
        scanner.feed(FloatArray(100), 100, 0)
        val scan = scanner.finish()
        assertEquals(Double.NEGATIVE_INFINITY, scan.peakDb, 0.0)
        assertNull(scan.lufs)
        assertEquals("-∞ dBFS", AudioAtlasEngine.loudnessSection(scan).tiles[0].value)
    }

    @Test
    fun spectrogramFillsEveryColumn() {
        val span = 4096L
        val builder = SpectrogramBuilder(8, 4, span)
        for (frame in 0 until span) builder.feed(sin(2 * PI * 100 * frame / 1000).toFloat(), frame)
        assertTrue(builder.pixels.none { it == -1 })
    }

    @Test
    fun coarseWindowCutsTheOverview() {
        val overview = AVEditPeaks(FloatArray(10) { -it.toFloat() }, FloatArray(10) { it.toFloat() })
        val window = AudioAtlasEngine.coarseWindow(overview, 10.0, 2.0, 4.0)!!
        assertArrayEquals(floatArrayOf(2f, 3f), window.max, 0f)
    }
}
