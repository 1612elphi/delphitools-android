package com.delphi.delphitools.tools.audiovideo

import android.media.AudioFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class AVEditAudioEngineTest {
    private fun sine(rate: Int, seconds: Double, amplitude: Double, channels: Int = 1, hz: Double = 997.0) =
        ShortArray((rate * seconds).toInt() * channels) { i -> (sin(2 * PI * hz * (i / channels) / rate) * amplitude * 32767).roundToInt().toShort() }

    @Test
    fun resampleMergesColumns() {
        val peaks = AVEditPeaks(floatArrayOf(-1f, -0.5f, -0.25f, 0f), floatArrayOf(1f, 0.5f, 0.25f, 0f))
        val halved = AVEditAudioEngine.resample(peaks, 2)
        assertArrayEquals(floatArrayOf(-1f, -0.25f), halved.min, 0f)
        assertArrayEquals(floatArrayOf(1f, 0.25f), halved.max, 0f)
        assertEquals(4, AVEditAudioEngine.resample(peaks, 8).min.size)
        assertEquals(0, AVEditAudioEngine.resample(peaks, 0).min.size)
    }

    @Test
    fun pcmResamplerHalvesWithoutSeams() {
        val source = sine(96000, 0.1, 0.5, channels = 2, hz = 1000.0)
        val whole = PcmResampler(96000, 48000, 2).process(source)
        val split = PcmResampler(96000, 48000, 2).let { r -> source.toList().chunked(998).flatMap { r.process(it.toShortArray()).toList() } }
        assertArrayEquals(whole, split.toShortArray())
        val expected = sine(48000, 0.1, 0.5, channels = 2, hz = 1000.0)
        assertEquals(expected.size, whole.size)
        whole.forEachIndexed { i, s -> assertEquals(expected[i].toFloat(), s.toFloat(), 2f) }
        val ramp = ShortArray(882) { it.toShort() }
        val down = PcmResampler(88200, 48000, 1).process(ramp)
        assertEquals(480, down.size)
        assertEquals((100 * 88200 / 48000.0).toFloat(), down[100].toFloat(), 0.5f)
    }

    @Test
    fun pcmFloatsReadsEachEncoding() {
        fun buffer(vararg bytes: Int) = ByteBuffer.wrap(ByteArray(bytes.size + 1) { if (it == 0) 99 else bytes[it - 1].toByte() }).apply { position(1) }
        assertArrayEquals(floatArrayOf(0.5f, -1f), AVEditAudioEngine.pcmFloats(buffer(0x00, 0x40, 0x00, 0x80), AudioFormat.ENCODING_PCM_16BIT), 0f)
        assertArrayEquals(floatArrayOf(0.5f, -1f), AVEditAudioEngine.pcmFloats(buffer(0, 0, 0x40, 0, 0, 0x80), AudioFormat.ENCODING_PCM_24BIT_PACKED), 0f)
        assertArrayEquals(floatArrayOf(-0.5f), AVEditAudioEngine.pcmFloats(buffer(0, 0, 0, 0xC0), AudioFormat.ENCODING_PCM_32BIT), 0f)
        val float = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(0, 0.25f)
        assertArrayEquals(floatArrayOf(0.25f), AVEditAudioEngine.pcmFloats(float, AudioFormat.ENCODING_PCM_FLOAT), 0f)
        assertNull(AVEditAudioEngine.pcmFloats(buffer(0x80), AudioFormat.ENCODING_PCM_8BIT))
    }

    @Test
    fun planGain() {
        assertEquals(AudioGainPlan(6.0, false, -4.0), AVEditAudioEngine.planGain(-20.0, -10.0, -14.0))
        assertEquals(AudioGainPlan(2.0, true, -1.0), AVEditAudioEngine.planGain(-20.0, -3.0, -14.0))
        assertEquals(AudioGainPlan(0.0, false, -3.0), AVEditAudioEngine.planGain(null, -3.0, -14.0))
        assertEquals(0.0, AVEditAudioEngine.planGain(Double.NaN, -3.0, -14.0).gainDb, 0.0)
        assertEquals(0.0, AVEditAudioEngine.planGain(-20.0, Double.NEGATIVE_INFINITY, -14.0).gainDb, 0.0)
        assertEquals(0.5012f, AVEditAudioEngine.dbToGain(-6.0), 0.001f)
        assertEquals(1f, AVEditAudioEngine.dbToGain(0.0))
    }

    @Test
    fun fitsWav() {
        assertTrue(AVEditEngine.fitsWav(48000L * 3600 * 6, 2))
        assertFalse(AVEditEngine.fitsWav(48000L * 3600 * 7, 2))
        assertTrue(AVEditEngine.fitsWav(1, 0))
    }

    @Test
    fun meterMeasuresPeakAndLoudness() {
        val rate = 48000
        // bs.1770: -3.01 lufs mono
        val meter = AudioMeter(rate, 1, rate * 2L, 10, loudness = true)
        val pcm = sine(rate, 2.0, 1.0)
        pcm.toList().chunked(4096).forEach { meter.feed(it.toShortArray()) }
        val scan = meter.finish()
        assertEquals(0.0, scan.peakDb, 0.01)
        assertEquals(-3.01, scan.rmsDb, 0.02)
        assertEquals(-3.01, scan.lufs!!, 0.1)
        assertEquals(2.0, scan.duration, 1e-9)
        assertEquals(10, scan.peaks.min.size)
        assertTrue(scan.peaks.max.all { it > 0.99f })
    }

    @Test
    fun meterHalfScaleAndSilence() {
        val rate = 44100
        val half = AudioMeter(rate, 2, rate * 1L, 4, loudness = true).apply { feed(sine(rate, 1.0, 0.5, channels = 2)) }.finish()
        assertEquals(-6.02, half.peakDb, 0.02)
        // two channels add 3 dB
        assertEquals(-9.03 + 3.01, half.lufs!!, 0.1)
        val silent = AudioMeter(rate, 1, rate * 1L, 4, loudness = true).apply { feed(ShortArray(rate)) }.finish()
        assertEquals(Double.NEGATIVE_INFINITY, silent.peakDb, 0.0)
        assertNull(silent.lufs)
        val short = AudioMeter(rate, 1, 100, 4, loudness = true).apply { feed(sine(rate, 0.2, 1.0)) }.finish()
        assertNull(short.lufs)
        assertNull(AudioMeter(rate, 1, 100, 4, loudness = false).apply { feed(sine(rate, 1.0, 1.0)) }.finish().lufs)
    }

    @Test
    fun meterBucketsAndOverrun() {
        val meter = AudioMeter(10, 1, 4, 2, loudness = false)
        meter.feed(shortArrayOf(16384, -16384, 0, 8192, -32768, 32767))
        val scan = meter.finish()
        assertArrayEquals(floatArrayOf(-0.5f, -1f), scan.peaks.min, 0.001f)
        assertArrayEquals(floatArrayOf(0.5f, 1f), scan.peaks.max, 0.001f)
        val empty = AudioMeter(10, 1, 4, 3, loudness = false).apply { feed(shortArrayOf(100)) }.finish()
        assertEquals(0f, empty.peaks.min[2])
    }

    @Test
    fun framesRoundOutwards() {
        assertEquals(4L until 11L, AVEditAudioEngine.frames(0.45..1.01, 10))
        assertEquals(0L until 1L, AVEditAudioEngine.frames(0.0..0.0, 10))
    }

    @Test
    fun shapeGainAndFades() {
        val full = 16384.toShort()
        val pcm = ShortArray(10) { full }
        AVEditAudioEngine.shape(pcm, 1, 0, 10, 1f, 4, 4)
        val expected = listOf(0.0, 0.25, 0.5, 0.75, 1.0, 1.0, 0.75, 0.5, 0.25, 0.0)
        pcm.forEachIndexed { i, s -> assertEquals(expected[i] * 0.5, s / 32767.0, 0.001) }

        val first = ShortArray(6) { full }
        val second = ShortArray(4) { full }
        AVEditAudioEngine.shape(first, 1, 0, 10, 1f, 4, 4)
        AVEditAudioEngine.shape(second, 1, 6, 10, 1f, 4, 4)
        assertArrayEquals(pcm, first + second)

        val loud = shortArrayOf(16384, -16384, 1000, -1000)
        AVEditAudioEngine.shape(loud, 2, 0, 2, 4f, 0, 0)
        assertArrayEquals(shortArrayOf(32767, -32767, 4000, -4000), loud)
    }

    @Test
    fun envelope() {
        assertEquals(0.5, AVEditAudioEngine.envelope(1.5, 1.0, 5.0, 1.0, 0.0), 1e-9)
        assertEquals(0.25, AVEditAudioEngine.envelope(4.5, 1.0, 5.0, 0.0, 2.0), 1e-9)
        assertEquals(1.0, AVEditAudioEngine.envelope(3.0, 1.0, 5.0, 1.0, 1.0), 1e-9)
        assertEquals(0.0, AVEditAudioEngine.envelope(0.0, 1.0, 5.0, 1.0, 1.0), 1e-9)
    }

    @Test
    fun barsStayInsideTheBox() {
        val peaks = AVEditPeaks(floatArrayOf(-2f, -0.5f, Float.NaN), floatArrayOf(2f, 0.5f, Float.NaN))
        val bars = AVEditAudioEngine.bars(peaks, 30.0, 10.0)
        assertEquals(AudioBar(0.0, 0.0, 10.0, 10.0), bars[0])
        assertEquals(AudioBar(10.0, 2.5, 10.0, 5.0), bars[1])
        assertEquals(AudioBar(20.0, 5.0, 10.0, 1.0), bars[2])
        assertTrue(AVEditAudioEngine.bars(peaks, 0.0, 10.0).isEmpty())
        val svg = AVEditAudioEngine.svg(AVEditPeaks(floatArrayOf(-0.5f), floatArrayOf(0.5f)), 4, 2, "#2E7D32", "#FAF5E6")
        assertEquals(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"4\" height=\"2\" viewBox=\"0 0 4 2\"><rect width=\"4\" height=\"2\" fill=\"#FAF5E6\"/>" +
                "<g fill=\"#2E7D32\"><rect x=\"0.00\" y=\"0.50\" width=\"4.00\" height=\"1.00\"/></g></svg>",
            svg,
        )
    }

    @Test
    fun fit() {
        assertArrayEquals(doubleArrayOf(0.0, 90.0, 300.0, 100.0), AVEditAudioEngine.fit(3.0, 1.0, 0.0, 40.0, 300.0, 200.0), 1e-9)
        assertArrayEquals(doubleArrayOf(50.0, 40.0, 200.0, 200.0), AVEditAudioEngine.fit(1.0, 1.0, 0.0, 40.0, 300.0, 200.0), 1e-9)
        assertArrayEquals(doubleArrayOf(0.0, 40.0, 300.0, 200.0), AVEditAudioEngine.fit(0.0, 0.0, 0.0, 40.0, 300.0, 200.0), 1e-9)
    }

    @Test
    fun formatting() {
        assertEquals("–", AVEditAudioEngine.decibels(Double.NEGATIVE_INFINITY, "dBFS"))
        assertEquals("–", AVEditAudioEngine.decibels(null, "LUFS"))
        assertEquals("-14.2 LUFS", AVEditAudioEngine.decibels(-14.24, "LUFS"))
        assertEquals("+6.0 dB", AVEditAudioEngine.signedGain(6.0))
        assertEquals("-2.5 dB", AVEditAudioEngine.signedGain(-2.5))
        assertEquals(1f, AVEditAudioEngine.levelShare(0.0))
        assertEquals(0.5f, AVEditAudioEngine.levelShare(-30.0))
        assertEquals(0f, AVEditAudioEngine.levelShare(Double.NEGATIVE_INFINITY))
        assertEquals(3 to 2, AVEditAudioEngine.merge(6, 3f))
        assertEquals(800 to 1, AVEditAudioEngine.merge(800, 1000f))
    }

    @Test
    fun shapeAppliesGainPastTheExpectedLength() {
        val pcm = ShortArray(8) { 1000 }
        AVEditAudioEngine.shape(pcm, 1, written = 4, total = 6, gain = 2f, inFrames = 0, outFrames = 0)
        assertTrue(pcm.all { it.toInt() == 2000 })
        val faded = ShortArray(8) { 1000 }
        AVEditAudioEngine.shape(faded, 1, written = 4, total = 6, gain = 1f, inFrames = 0, outFrames = 2)
        assertEquals(0, faded[7].toInt())
    }
}
