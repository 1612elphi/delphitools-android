package com.delphi.delphitools.tools.audiovideo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class WhisperEngineTest {
    private fun bytes(text: String) = text.toByteArray()

    @Test
    fun tokenBytesReversesTheByteLevelAlphabet() {
        // gpt-2: Ġ space, Ċ newline
        assertArrayEquals(bytes(" the"), WhisperEngine.tokenBytes("Ġthe"))
        assertArrayEquals(bytes("\n"), WhisperEngine.tokenBytes("Ċ"))
        assertArrayEquals(bytes("é"), WhisperEngine.tokenBytes("Ã©"))
    }

    @Test
    fun wordsStartAtSpacesAndTakeTheirTokensTimes() {
        val tokens = listOf(" The", " quick", "-", "brown", " fox", ".").map(::bytes)
        val times = floatArrayOf(0f, 0.2f, 0.5f, 0.6f, 0.9f, 1.2f, 1.3f)
        val words = WhisperEngine.words(tokens, times, "en", 10.0)
        assertEquals(listOf("The", "quick-brown", "fox."), words.map { it.text })
        assertEquals(10.0, words[0].start, 1e-6)
        assertEquals(10.2, words[0].end, 1e-6)
        assertEquals(10.2, words[1].start, 1e-6)
        assertEquals(10.9, words[1].end, 1e-6)
        assertEquals(11.3, words[2].end, 1e-6)
    }

    @Test
    fun characterSplitAcrossTokensStaysWhole() {
        val e = "é".toByteArray()
        val tokens = listOf(bytes(" caf"), byteArrayOf(e[0]), byteArrayOf(e[1]))
        val words = WhisperEngine.words(tokens, floatArrayOf(0f, 1f, 2f, 3f), "fr", 0.0)
        assertEquals(listOf("café"), words.map { it.text })
    }

    @Test
    fun unspacedScriptsMakeAWordPerToken() {
        val tokens = listOf("你", "好").map(::bytes)
        val words = WhisperEngine.words(tokens, floatArrayOf(0f, 0.4f, 0.8f), "zh", 0.0)
        assertEquals(listOf("你", "好"), words.map { it.text })
        assertEquals(0.4, words[1].start, 1e-6)
    }

    @Test
    fun dtwFollowsTheCheapDiagonal() {
        val cost = Array(3) { i -> FloatArray(3) { j -> if (i == j) 0f else 1f } }
        val (rows, columns) = WhisperEngine.dtw(cost)
        assertArrayEquals(intArrayOf(0, 1, 2), rows)
        assertArrayEquals(intArrayOf(0, 1, 2), columns)
    }

    @Test
    fun medianRemovesASpikeAndReflectsEdges() {
        val row = floatArrayOf(1f, 1f, 1f, 9f, 1f, 1f, 1f, 1f)
        assertTrue(WhisperEngine.median(row, 7).all { it == 1f })
        assertArrayEquals(floatArrayOf(2f, 2f), WhisperEngine.median(floatArrayOf(2f, 2f), 7), 0f)
    }

    @Test
    fun rowTimesPlaceEachRowWhereItsAttentionPeaks() {
        val peaks = listOf(0, 10, 30, 50)
        val head = peaks.map { peak -> FloatArray(60) { f -> if (abs(f - peak) <= 3) 1f else 0f } }
        val times = WhisperEngine.rowTimes(listOf(head), 60, 1)
        assertEquals(3, times.size)
        assertTrue(times[0] < times[1] && times[1] < times[2])
        assertEquals(0.6f, times[1], 0.15f)
        assertEquals(1.0f, times[2], 0.15f)
    }

    @Test
    fun melFiltersCoverTheBandOnce() {
        val filters = WhisperEngine.melFilters(80)
        assertEquals(80, filters.size)
        assertEquals(201, filters[0].size)
        assertTrue(filters.all { row -> row.all { it >= 0f } && row.any { it > 0f } })
        assertTrue(filters[0].max() > filters[79].max())
    }

    @Test
    fun logMelIsScaledAndPeaksAtTheToneBand() {
        val filters = WhisperEngine.melFilters(80)
        val tone = FloatArray(16_000) { (0.5 * sin(2 * PI * 1000 * it / 16_000)).toFloat() }
        val mel = WhisperEngine.logMel(tone, filters)
        assertEquals(80 * WhisperEngine.FRAMES, mel.size)
        val max = mel.max()
        assertTrue(mel.min() >= max - 2.0001f)
        val frame = 50
        val loudest = (0 until 80).maxBy { mel[it * WhisperEngine.FRAMES + frame] }
        val centre = (0 until 201).maxBy { filters[loudest][it] } * 40
        assertEquals(1000.0, centre.toDouble(), 120.0)
        assertEquals(mel[2999], mel[2998], 0f)
    }

    @Test
    fun languagesAreWhispersHundred() {
        assertEquals(100, WhisperEngine.languages.size)
        assertEquals(100, WhisperEngine.languages.toSet().size)
    }
}
